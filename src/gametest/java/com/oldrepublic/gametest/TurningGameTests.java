package com.oldrepublic.gametest;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.CrossingStage;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.quest.story.Turnings;
import com.oldrepublic.quest.turning.RankFour;
import com.oldrepublic.quest.turning.RankFourChecklistPayload;
import com.oldrepublic.quest.turning.TurningBalance;
import com.oldrepublic.quest.turning.TurningItems;
import com.oldrepublic.quest.turning.TurningRules;
import com.oldrepublic.quest.turning.TurningService;
import com.oldrepublic.quest.turning.WatchHolocronItem;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.vendor.hub.HubNpc;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Fall, the Return and rank 4 (spec MILESTONE_5.md 8, 19.1; W5 paths and ranks): the offer's band and refusals, once
 * each per character, the restarts and resets of the steps, what a rite changes and costs, the cold mentor, the rank 4
 * checklist and its three ceremonies, the Darth name, the robes' daily reissue, the Return's landing at the Greenwatch and
 * the Peaceful confrontation.
 *
 * <p>Test rules (M3 15.1, {@code WORKER_M5.md}): every test declares {@code maxTicks} (at most 400); players are listed
 * mock players removed at the end; the default empty structure; days are explicit (the pure rules take the day, and a
 * player test that reads the world's day compares with {@link Clocks#day} on the same tick); nothing writes world, global
 * or server state: the turning's people a test makes (a messenger, landed prisoners) are removed at its end, and a mentor
 * on its way is called off. Everything runs synchronously in the first tick, before the turning's slow tick can touch the
 * players. The game-test server has no planets: the Return's landing is the frozen {@code onPlanetArrived} hook's call,
 * and the confrontation's duel is W8's (here it is skipped as if the mentor had yielded, the seam {@code begin} offers).
 */
public class TurningGameTests {
	private static final TurningBalance B = new TurningBalance();
	/** The explicit day of the pure rules. */
	private static final long DAY = 40L;
	/** Mastery points that reach mastery rank 3 ({@link Progression#masteryRank}). */
	private static final int MASTERED = 400;
	private static final String REQ = "turning.oldrepublic.rank4.req.";
	private static final String LAST_LESSON = "turn_last_lesson";
	private static final String KNEEL = "turn_kneel";

	// ---------------------------------------------------------------- helpers

	private static Component msg(String text) {
		return Component.literal(text);
	}

	/**
	 * A listed player on {@code path} (story and identity agree, the Crossing decided) with Chapters Two to Four complete,
	 * at {@code rank} and {@code alignment}, with Chapter Four's placements marked done and its back pay settled.
	 */
	private static ServerPlayer at(GameTestHelper helper, BlockPos relative, Path path, int rank, int alignment) {
		ServerPlayer p = M3TestPlayers.listed(helper, relative);
		IdentityData identity = Identity.get(p);
		identity.path = path;
		identity.rank = rank;
		identity.alignment = alignment;
		Identity.commit(p, identity);
		StoryData data = Story.get(p);
		data.crossingStage = CrossingStage.DECIDED.ordinal();
		data.path = path.id();
		data.pathStage = (path == Path.JEDI || path == Path.SITH) ? PathStage.COMPLETE.ordinal() : data.pathStage;
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, ChapterThreeStage.COMPLETE);
		data.chapter4 = data.chapter4.withStage(ChapterFourStage.COMPLETE, 0L).withBackPay();
		data.flags.add(ChapterFourRules.HULK_PLACED);
		data.flags.add(ChapterFourRules.CHALLENGE_PLAYED);
		Story.commit(p, data);
		return p;
	}

	/**
	 * A listed player who meets every rank 4 requirement on {@code path} (spec 8.5): Chapter Five complete, level 20, two
	 * powers at mastery rank 3 and two deeds (five bounties, five contracts); a Jedi or a Sith at rank 3 with trust 90,
	 * their order at 800 and the alignment past 50 on their side; an Independent with the Pathfinder title and three
	 * known factions at Trusted.
	 */
	private static ServerPlayer ready(GameTestHelper helper, BlockPos relative, Path path) {
		TurningBalance live = TurningService.balance();
		boolean ordered = path == Path.JEDI || path == Path.SITH;
		int lean = path == Path.SITH ? -1 : 1;
		ServerPlayer p = at(helper, relative, path, ordered ? 3 : 0, ordered ? lean * (live.rank4Alignment + 10) : 0);
		IdentityData identity = Identity.get(p);

		if (ordered) {
			identity.trust.put(MentorRole.of(path).id(), live.rank4Trust);
		} else {
			identity.choices.add(RankThree.TITLE_PATHFINDER);
		}

		Identity.commit(p, identity);
		Faction own = TurningRules.factionOf(path);

		if (own != null) {
			Identity.setReputation(p, own, live.rank4Rep);
		} else {
			for (Faction faction : List.of(Faction.REPUBLIC, Faction.FRONTIER, Faction.JEDI_ORDER)) {
				Identity.setReputation(p, faction, live.wayfarerTier);
			}
		}

		ForceData force = Progression.get(p);
		force.level = live.rank4Level;
		force.mastery.put(SkillIds.FORCE_PUSH, MASTERED);
		force.mastery.put(SkillIds.FORCE_SPEED, MASTERED);
		Progression.commit(p, force);
		StoryData data = Story.get(p);
		data.chapter5 = data.chapter5.withStage(ChapterFiveStage.COMPLETE, -1L);
		data.counters.put(ChapterFive.BOUNTIES, live.deedBounties);
		data.counters.put(ChapterFive.CONTRACTS, live.deedContracts);
		Story.commit(p, data);
		return p;
	}

	private static void setTurning(ServerPlayer p, TurningKind kind, TurningStage stage, int task) {
		StoryData data = Story.get(p);
		data.turning = data.turning.withStage(kind, stage).withTask(task);
		Story.commit(p, data);
	}

	private static StoryData.Turning turning(ServerPlayer p) {
		return Story.get(p).turning;
	}

	private static void flag(ServerPlayer p, String flag) {
		StoryData data = Story.get(p);
		data.flags.add(flag);
		Story.commit(p, data);
	}

	private static void choice(ServerPlayer p, String choice) {
		IdentityData identity = Identity.get(p);
		identity.choices.add(choice);
		Identity.commit(p, identity);
	}

	private static void trust(ServerPlayer p, MentorRole role, int value) {
		IdentityData identity = Identity.get(p);
		identity.trust.put(role.id(), value);
		Identity.commit(p, identity);
	}

	private static void level(ServerPlayer p, int level) {
		ForceData force = Progression.get(p);
		force.level = level;
		Progression.commit(p, force);
	}

	/** All XP the player has earned: the levels behind them plus the XP into the current one. */
	private static long totalXp(ServerPlayer p) {
		ForceData data = Progression.get(p);
		long total = data.xp;

		for (int level = 1; level < data.level; level++) {
			total += Progression.xpToNext(level);
		}

		return total;
	}

	/**
	 * Skill points gained since {@code sp} at {@code level}, without the ones the levels gained since paid: a reward's XP
	 * can level a test player, and each level pays its own point.
	 */
	private static int storySp(ServerPlayer p, int sp, int level) {
		ForceData data = Progression.get(p);
		return data.skillPoints - sp - (data.level - level) * Progression.balance().skillPointsPerLevel;
	}

	private static boolean entry(ServerPlayer p, String id) {
		return Progression.get(p).flags.contains(JournalEntries.entryFlag(id));
	}

	/** Gives the player a skill node directly (owned at {@code rank}, bought for {@code spent} points), as purchases would. */
	private static void own(ServerPlayer p, String node, int rank, int spent) {
		SkillData data = SkillService.data(p);
		data.nodes.put(node, rank);

		if (spent > 0) {
			data.spent.put(node, spent);
		}

		SkillService.commit(p, data);
	}

	private static boolean owns(ServerPlayer p, String node) {
		return SkillService.data(p).owns(node);
	}

	private static int count(ServerPlayer p, Item item) {
		Inventory inventory = p.getInventory();
		int n = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				n += inventory.getItem(i).getCount();
			}
		}

		return n;
	}

	private static void removeAll(ServerPlayer p, Item item) {
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/** One of W7's rank 4 robes by id; fails the test when it is not registered. */
	private static Item robe(GameTestHelper helper, String id) {
		Item item = BuiltInRegistries.ITEM.getOptional(OldRepublicMod.id(id)).orElse(null);
		helper.assertTrue(item != null, msg("the rank 4 robe " + id + " must be registered (W7, spec 8.5)"));
		return item;
	}

	private static void action(ServerPlayer p, String id) {
		DialogueActions.Action a = DialogueActions.get(id);

		if (a == null) {
			throw new IllegalStateException("No dialogue action " + id);
		}

		a.run(p, null);
	}

	private static void action(ServerPlayer p, String id, String arg) {
		DialogueActions.ArgAction a = DialogueActions.getArg(id);

		if (a == null) {
			throw new IllegalStateException("No dialogue argument action " + id);
		}

		a.run(p, null, arg);
	}

	private static @Nullable String condition(ServerPlayer p, String id, String arg) {
		DialogueConditions.Condition c = DialogueConditions.get(id);

		if (c == null) {
			throw new IllegalStateException("No dialogue condition " + id);
		}

		return c.failReason(p, null, arg);
	}

	/** The open dialogue's id path, or null when none is open. */
	private static @Nullable String openDialogue(ServerPlayer p) {
		DialogueSession session = Dialogues.session(p);
		return session == null ? null : session.dialogue().getPath();
	}

	/**
	 * A mentor of {@code role} owned by the player, two blocks from them. It is never added to the level: only its use and
	 * its dialogue are tested, and an unremembered mentor added to the level would remove itself as a stray copy.
	 */
	private static MentorEntity mentor(GameTestHelper helper, ServerPlayer p, MentorRole role) {
		MentorEntity mentor = MentorEntities.type(role).create(helper.getLevel(), EntitySpawnReason.COMMAND);

		if (mentor == null) {
			throw new IllegalStateException("The " + role.id() + " mentor could not be created");
		}

		mentor.snapTo(p.getX() + 2, p.getY(), p.getZ(), 90f, 0f);
		mentor.setOwner(p.getUUID());
		return mentor;
	}

	/** The Return's prisoners put down for the player (tagged {@link TurningService#LANDED_TAG}). */
	private static List<HubNpc> landed(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(HubNpc.class, p.getBoundingBox().inflate(16),
				npc -> npc.isAlive() && npc.entityTags().contains(TurningService.LANDED_TAG) && StoryTags.isOwnedBy(npc, p.getUUID()));
	}

	/** Removes the turning's people made for the player (a messenger, landed prisoners) and calls off a mentor on the way. */
	private static void cleanUp(GameTestHelper helper, ServerPlayer p) {
		Dialogues.close(p);
		StoryMentors.cancelComing(p);
		helper.getLevel().getEntitiesOfClass(HubNpc.class, p.getBoundingBox().inflate(32), npc -> StoryTags.isOwnedBy(npc, p.getUUID()))
				.forEach(Entity::discard);
		M3TestPlayers.remove(p);
	}

	/** A rank 4 standing in which everything is met for {@code path}, as a base the matrix changes one value of. */
	private static TurningRules.Standing readyStanding(Path path) {
		int alignment = path == Path.SITH ? -B.rank4Alignment : path == Path.JEDI ? B.rank4Alignment : 0;
		return new TurningRules.Standing(path, ChapterFiveStage.COMPLETE, B.rank4Level, path == Path.INDEPENDENT ? 0 : 3, path == Path.INDEPENDENT,
				path == Path.INDEPENDENT ? 0 : B.rank4Trust, path == Path.INDEPENDENT ? 0 : B.rank4Rep, B.wayfarerFactions, alignment,
				B.rank4MasteryPowers, new ChapterFive.Deeds(true, B.deedEchoes, 0, 0, false));
	}

	private static TurningRules.Standing with(TurningRules.Standing s, ChapterFiveStage stage, int level, int rank, boolean pathfinder, int trust,
			int ownRep, int trustedFactions, int alignment, int mastered, ChapterFive.Deeds deeds) {
		return new TurningRules.Standing(s.path(), stage, level, rank, pathfinder, trust, ownRep, trustedFactions, alignment, mastered, deeds);
	}

	// ---------------------------------------------------------------- the offer (8.2)

	@GameTest(maxTicks = 20)
	public void offerAfterOneDayInTheBand(GameTestHelper helper) {
		helper.assertTrue(TurningRules.candidate(Path.JEDI, Path.JEDI, true, false, false) == TurningKind.FALL, msg("a Jedi after Chapter Four may Fall"));
		helper.assertTrue(TurningRules.candidate(Path.SITH, Path.SITH, true, false, false) == TurningKind.RETURN, msg("a Sith after Chapter Four may Return"));
		helper.assertTrue(TurningRules.candidate(Path.JEDI, Path.JEDI, false, false, false) == TurningKind.NONE, msg("never before Chapter Four is complete"));
		helper.assertTrue(TurningRules.candidate(Path.INDEPENDENT, Path.INDEPENDENT, true, false, false) == TurningKind.NONE,
				msg("an Independent has no questline"));
		helper.assertTrue(TurningRules.candidate(Path.JEDI, Path.SITH, true, false, false) == TurningKind.NONE,
				msg("identity and story paths that disagree (an operator's change under way) get no offer"));

		helper.assertTrue(TurningRules.inBand(TurningKind.FALL, B.fallAlignment, B) && !TurningRules.inBand(TurningKind.FALL, B.fallAlignment + 1, B),
				msg("the Fall's band: at or below " + B.fallAlignment));
		helper.assertTrue(TurningRules.inBand(TurningKind.RETURN, B.returnAlignment, B) && !TurningRules.inBand(TurningKind.RETURN, B.returnAlignment - 1, B),
				msg("the Return's band: at or above " + B.returnAlignment));
		helper.assertTrue(!TurningRules.inBand(TurningKind.NONE, -1000, B), msg("no band without a candidate"));

		// The first day in the band is kept; the offer is due one whole day later.
		StoryData.Turning none = StoryData.Turning.NEW;
		StoryData.Turning seen = TurningRules.track(none, TurningKind.FALL, true, DAY, TurningRules.resting(none, false, false));
		helper.assertTrue(seen.bandSince() == DAY, msg("the first day seen in the band is stored, got " + seen.bandSince()));
		helper.assertTrue(TurningRules.track(seen, TurningKind.FALL, true, DAY + 5, seen).bandSince() == DAY, msg("later looks keep that day"));
		helper.assertTrue(!TurningRules.offerDue(seen, DAY + B.bandDays - 1, B) && TurningRules.offerDue(seen, DAY + B.bandDays, B),
				msg("the offer is due after " + B.bandDays + " whole day(s) in the band"));

		// Leaving the band forgets the day; coming back starts it again.
		StoryData.Turning left = TurningRules.track(seen, TurningKind.FALL, false, DAY + 1, TurningRules.resting(seen, false, false));
		helper.assertTrue(left.bandSince() < 0 && !TurningRules.offerDue(left, DAY + 1, B), msg("out of the band the day is forgotten"));
		StoryData.Turning back = TurningRules.track(left, TurningKind.FALL, true, DAY + 2, left);
		helper.assertTrue(back.bandSince() == DAY + 2 && !TurningRules.offerDue(back, DAY + 2, B) && TurningRules.offerDue(back, DAY + 2 + B.bandDays, B),
				msg("back in the band, a whole day again"));

		// A questline under way is never touched by the band; a pending offer is withdrawn when the band is left.
		StoryData.Turning busy = seen.withStage(TurningKind.FALL, TurningStage.TASK);
		helper.assertTrue(TurningRules.track(busy, TurningKind.FALL, false, DAY + 3, busy) == busy, msg("the band does not touch a questline under way"));
		helper.assertTrue(!TurningRules.offerDue(busy, DAY + 30, B), msg("no offer while a questline is under way"));
		StoryData.Turning offered = TurningRules.offered(seen, TurningKind.FALL);
		StoryData.Turning withdrawn = TurningRules.track(offered, TurningKind.FALL, false, DAY + 3, TurningRules.resting(offered, false, false));
		helper.assertTrue(withdrawn.stage() == TurningStage.NONE && withdrawn.bandSince() < 0, msg("leaving the band withdraws the offer"));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);

		try {
			helper.assertTrue(TurningService.offer(p, TurningKind.FALL), msg("the offer is made"));
			StoryData.Turning made = turning(p);
			helper.assertTrue(made.kind() == TurningKind.FALL && made.stage() == TurningStage.OFFERED && made.task() == 0, msg("OFFERED for the Fall"));
			helper.assertTrue(condition(p, "turn:offered", "fall") == null && condition(p, "turn:offered", "return") != null,
					msg("turn:offered names the offer that stands"));

			// The slow tick withdraws a pending offer whose band was left.
			IdentityData identity = Identity.get(p);
			identity.alignment = 0;
			Identity.commit(p, identity);
			TurningService.tickPlayer(p);
			helper.assertTrue(turning(p).stage() == TurningStage.NONE && turning(p).bandSince() < 0,
					msg("out of the band the offer is withdrawn, got " + turning(p).stage()));

			helper.assertTrue(TurningService.offer(p, TurningKind.FALL), msg("offered again"));
			action(p, "turn:accept");
			StoryData.Turning task = turning(p);
			helper.assertTrue(task.kind() == TurningKind.FALL && task.stage() == TurningStage.TASK && task.task() == TurningRules.FALL_TO_ANSA,
					msg("accepted: the task, on the way to Ansa"));
			helper.assertTrue(entry(p, JournalEntries.THE_FALL_OFFERED), msg("entry the_fall_offered"));
			helper.assertTrue(!TurningService.offer(p, TurningKind.FALL), msg("no offer while a questline is under way"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void reofferAfterThreeDaysAndStopAfterThird(GameTestHelper helper) {
		StoryData.Turning seen = StoryData.Turning.NEW.withOffer(DAY, 0, -1L);
		StoryData.Turning resting = TurningRules.resting(seen, false, false);
		StoryData.Turning once = TurningRules.decline(TurningRules.offered(seen, TurningKind.RETURN), DAY + 1, B, resting);
		helper.assertTrue(once.stage() == TurningStage.NONE && once.refusals() == 1 && once.bandSince() == DAY && once.reofferDay() == DAY + 1 + B.reofferDays,
				msg("a refusal: back to rest, one refusal, the band's day kept, the offer back in " + B.reofferDays + " days"));
		helper.assertTrue(!TurningRules.finalRefusal(once, B), msg("the first refusal is not the last"));
		helper.assertTrue(!TurningRules.offerDue(once, DAY + B.reofferDays, B) && TurningRules.offerDue(once, DAY + 1 + B.reofferDays, B),
				msg("the offer comes back " + B.reofferDays + " days after the refusal"));

		StoryData.Turning twice = TurningRules.decline(TurningRules.offered(once, TurningKind.RETURN), DAY + 4, B, resting);
		StoryData.Turning thrice = TurningRules.decline(TurningRules.offered(twice, TurningKind.RETURN), DAY + 7, B, resting);
		helper.assertTrue(twice.refusals() == 2 && thrice.refusals() == B.refusals && TurningRules.finalRefusal(thrice, B),
				msg("the third refusal is the last"));
		helper.assertTrue(!TurningRules.offerDue(thrice, DAY + 1000, B), msg("after the third, no offer while the band holds"));

		StoryData.Turning left = TurningRules.track(thrice, TurningKind.RETURN, false, DAY + 20, resting);
		helper.assertTrue(left.refusals() == 0 && left.bandSince() < 0 && left.reofferDay() < 0, msg("leaving the band forgets the refusals"));
		StoryData.Turning back = TurningRules.track(left, TurningKind.RETURN, true, DAY + 21, resting);
		helper.assertTrue(TurningRules.offerDue(back, DAY + 21 + B.bandDays, B), msg("back in the band, the offers start again"));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.SITH, 2, B.returnAlignment + 10);
		TurningBalance live = TurningService.balance();

		try {
			long day = Clocks.day(p.level().getServer());

			for (int refusal = 1; refusal <= live.refusals; refusal++) {
				helper.assertTrue(TurningService.offer(p, TurningKind.RETURN), msg("offer " + refusal + " is made"));
				action(p, "turn:decline");
				StoryData.Turning t = turning(p);
				helper.assertTrue(t.stage() == TurningStage.NONE && t.refusals() == refusal && t.reofferDay() == day + live.reofferDays,
						msg("refusal " + refusal + ": at rest, counted, the offer back on day " + (day + live.reofferDays) + "; got " + t));
			}

			helper.assertTrue(TurningRules.finalRefusal(turning(p), live), msg("the third refusal ends the offers"));
			StoryData.Turning before = turning(p);
			action(p, "turn:decline");
			action(p, "turn:accept");
			helper.assertTrue(turning(p).equals(before), msg("with no offer standing, accepting or declining changes nothing"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void fallOnceReturnOnce(GameTestHelper helper) {
		helper.assertTrue(TurningRules.candidate(Path.SITH, Path.SITH, true, true, false) == TurningKind.RETURN, msg("a fallen Jedi may Return"));
		helper.assertTrue(TurningRules.candidate(Path.JEDI, Path.JEDI, true, true, true) == TurningKind.NONE,
				msg("a Jedi who Fell and Returned is a Jedi for good"));
		helper.assertTrue(TurningRules.candidate(Path.JEDI, Path.JEDI, true, false, true) == TurningKind.FALL, msg("a returned Sith may Fall once"));
		helper.assertTrue(TurningRules.candidate(Path.SITH, Path.SITH, true, true, true) == TurningKind.NONE, msg("both done: nothing more"));
		StoryData.Turning rested = TurningRules.resting(StoryData.Turning.NEW, true, false);
		helper.assertTrue(rested.stage() == TurningStage.COMPLETE && rested.kind() == TurningKind.FALL, msg("between questlines the record rests at the last one"));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);

		try {
			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");
			helper.assertTrue(Identity.path(p) == Path.SITH && Turnings.done(p, TurningKind.FALL) && !Turnings.done(p, TurningKind.RETURN),
					msg("the Fall's rite: a Sith, turn.fall.done recorded"));
			helper.assertTrue(TurningRules.candidate(Identity.path(p), Story.get(p).storyPath(), true, Turnings.done(p, TurningKind.FALL),
					Turnings.done(p, TurningKind.RETURN)) == TurningKind.RETURN, msg("the Return may come next"));

			setTurning(p, TurningKind.RETURN, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");
			helper.assertTrue(Identity.path(p) == Path.JEDI && Turnings.done(p, TurningKind.FALL) && Turnings.done(p, TurningKind.RETURN),
					msg("the Return's rite: a Jedi again, both turnings recorded"));
			helper.assertTrue(TurningRules.candidate(Identity.path(p), Story.get(p).storyPath(), true, true, true) == TurningKind.NONE,
					msg("no third turning"));
			helper.assertTrue(turning(p).stage() == TurningStage.COMPLETE && turning(p).kind() == TurningKind.RETURN,
					msg("the record rests at the Return, COMPLETE"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the steps (8.3)

	@GameTest(maxTicks = 20)
	public void deliveryRestartsWhenStruckDown(GameTestHelper helper) {
		ServerPlayer carrier = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		ServerPlayer dropper = at(helper, new BlockPos(4, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		ServerPlayer courier = at(helper, new BlockPos(7, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		ServerPlayer escort = at(helper, new BlockPos(1, 1, 4), Path.SITH, 2, B.returnAlignment + 10);
		TurningBalance live = TurningService.balance();

		try {
			// Struck down while carrying the holocron: Ansa takes it back.
			setTurning(carrier, TurningKind.FALL, TurningStage.TASK, TurningRules.FALL_CARRYING);
			carrier.getInventory().add(new ItemStack(TurningItems.WATCH_HOLOCRON));
			helper.assertTrue(WatchHolocronItem.carried(carrier) && condition(carrier, "turn:holocron", "") == null, msg("carrying the holocron"));
			TurningService.onPlayerDied(carrier);
			helper.assertTrue(turning(carrier).stage() == TurningStage.TASK && turning(carrier).task() == TurningRules.FALL_TO_ANSA,
					msg("struck down: the step starts again at Ansa"));
			helper.assertTrue(!WatchHolocronItem.carried(carrier) && condition(carrier, "turn:holocron", "") != null, msg("the holocron is gone"));

			// Dropped (or stored): the slow tick sends the step back to Ansa.
			setTurning(dropper, TurningKind.FALL, TurningStage.TASK, TurningRules.FALL_CARRYING);
			TurningService.tickPlayer(dropper);
			helper.assertTrue(turning(dropper).task() == TurningRules.FALL_TO_ANSA, msg("without the holocron the step restarts"));

			// Delivered: Kaddor takes it, and Saelen will come.
			setTurning(courier, TurningKind.FALL, TurningStage.TASK, TurningRules.FALL_CARRYING);
			courier.getInventory().add(new ItemStack(TurningItems.WATCH_HOLOCRON));
			Identity.setReputation(courier, Faction.SITH_EMPIRE, -200);
			Identity.setReputation(courier, Faction.JEDI_ORDER, 300);
			long xp = totalXp(courier);
			int alignment = Identity.alignment(courier);
			action(courier, "turn:deliver");
			helper.assertTrue(turning(courier).stage() == TurningStage.CONFRONTATION && turning(courier).task() == TurningRules.CONFRONT_WAITING,
					msg("delivered: the confrontation waits"));
			helper.assertTrue(!WatchHolocronItem.carried(courier), msg("Kaddor keeps the holocron"));
			helper.assertTrue(totalXp(courier) - xp == live.taskXp, msg("the task's XP, got " + (totalXp(courier) - xp)));
			helper.assertTrue(Identity.reputation(courier, Faction.SITH_EMPIRE) == -200 + live.taskRepGain
					&& Identity.reputation(courier, Faction.JEDI_ORDER) == 300 + live.taskRepLoss, msg("the Empire gains, the Order loses"));
			helper.assertTrue(Identity.alignment(courier) == alignment + live.fallDark, msg("dark " + live.fallDark));
			helper.assertTrue(entry(courier, JournalEntries.A_GIFT_FOR_THE_EMPIRE), msg("entry a_gift_for_the_empire"));

			// The Return: struck down with the prisoners following, they are back in their cells.
			setTurning(escort, TurningKind.RETURN, TurningStage.TASK, TurningRules.returnTask(TurningRules.RETURN_ESCORT, TurningRules.ALL_GUARDS, 0b011));
			TurningService.onPlayerDied(escort);
			int task = turning(escort).task();
			helper.assertTrue(turning(escort).stage() == TurningStage.TASK && TurningRules.phase(task) == TurningRules.RETURN_HELD
					&& TurningRules.guardsDown(task) == 0 && TurningRules.boarded(task) == 0, msg("struck down: back in the cells, the guards back, got " + task));
			helper.assertTrue(condition(escort, "turn:stage", "task:0") == null && condition(escort, "turn:held", "") == null,
					msg("the warden's conditions see the prisoners held"));
		} finally {
			cleanUp(helper, carrier);
			cleanUp(helper, dropper);
			cleanUp(helper, courier);
			cleanUp(helper, escort);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void confrontationResetsAfterDeath(GameTestHelper helper) {
		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		ServerPlayer q = at(helper, new BlockPos(4, 1, 1), Path.SITH, 2, B.returnAlignment + 10);
		TurningBalance live = TurningService.balance();

		try {
			MinecraftServer server = p.level().getServer();
			setTurning(p, TurningKind.FALL, TurningStage.CONFRONTATION, TurningRules.CONFRONT_DUEL);
			long xp = totalXp(p);
			int alignment = Identity.alignment(p);
			long time = Clocks.time(server);
			TurningService.onPlayerDied(p);
			helper.assertTrue(turning(p).stage() == TurningStage.CONFRONTATION && turning(p).task() == TurningRules.CONFRONT_WAITING,
					msg("a death in the duel: back to the confrontation's start"));
			helper.assertTrue(TurningService.confrontationRetryAt(p) == time + live.confrontationResetTicks,
					msg("Saelen comes again after " + live.confrontationResetTicks + " ticks, got " + TurningService.confrontationRetryAt(p)));
			helper.assertTrue(totalXp(p) == xp && Identity.alignment(p) == alignment && turning(p).kind() == TurningKind.FALL, msg("nothing is lost"));

			// A choice already won stays won.
			setTurning(p, TurningKind.FALL, TurningStage.CONFRONTATION, TurningRules.CONFRONT_YIELDED);
			TurningService.onPlayerDied(p);
			helper.assertTrue(turning(p).task() == TurningRules.CONFRONT_YIELDED, msg("a yielded mentor stays yielded"));
			action(p, "turn:resolve", "release");
			helper.assertTrue(turning(p).stage() == TurningStage.RITE && TurningService.confrontationRetryAt(p) < 0, msg("resolved: the retry is forgotten"));

			setTurning(q, TurningKind.RETURN, TurningStage.CONFRONTATION, TurningRules.CONFRONT_DUEL);
			TurningService.onPlayerDied(q);
			helper.assertTrue(turning(q).task() == TurningRules.CONFRONT_WAITING && TurningService.confrontationRetryAt(q) >= time,
					msg("the Overseer's Due resets the same way"));
		} finally {
			cleanUp(helper, p);
			cleanUp(helper, q);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void peacefulConfrontationIsATalk(GameTestHelper helper) {
		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		ServerPlayer q = at(helper, new BlockPos(4, 1, 1), Path.SITH, 2, B.returnAlignment + 10);
		ServerPlayer r = at(helper, new BlockPos(7, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		MentorEntity saelen = mentor(helper, p, MentorRole.JEDI);
		TurningBalance live = TurningService.balance();

		try {
			// Peaceful: Saelen talks and yields at once; the choice opens with her.
			setTurning(p, TurningKind.FALL, TurningStage.CONFRONTATION, TurningRules.CONFRONT_WAITING);
			TurningService.beginConfrontation(p, saelen, true);
			helper.assertTrue(turning(p).task() == TurningRules.CONFRONT_TALKED, msg("the confrontation is a talk"));
			helper.assertTrue(LAST_LESSON.equals(openDialogue(p)), msg("the Last Lesson's choice opens at once, got " + openDialogue(p)));
			helper.assertTrue(condition(p, "turn:stage", "confrontation:3") == null, msg("turn:stage sees the talk"));
			Dialogues.close(p);
			long xp = totalXp(p);
			action(p, "turn:resolve", "release");
			helper.assertTrue(turning(p).stage() == TurningStage.RITE && turning(p).task() == TurningRules.RITE_WAITING, msg("let go: the rite waits"));
			helper.assertTrue(totalXp(p) - xp == live.confrontationXp && entry(p, JournalEntries.THE_LAST_LESSON), msg("the confrontation's XP and entry"));
			helper.assertTrue(!Story.get(p).hasFlag(TurningRules.CRYSTAL_TAKEN), msg("letting her go takes nothing"));

			// The Overseer's Due as a talk, and Sorn's blade broken.
			setTurning(q, TurningKind.RETURN, TurningStage.CONFRONTATION, TurningRules.CONFRONT_WAITING);
			TurningService.beginConfrontation(q, null, true);
			helper.assertTrue(turning(q).task() == TurningRules.CONFRONT_TALKED, msg("Sorn talks too"));
			int light = Identity.alignment(q);
			action(q, "turn:resolve", "trophy");
			helper.assertTrue(turning(q).stage() == TurningStage.RITE && Story.get(q).hasFlag(TurningRules.BLADE_BROKEN)
					&& Identity.alignment(q) == light + live.bladeLight && entry(q, JournalEntries.THE_OVERSEERS_DUE), msg("the blade broken: light +3"));

			// Off Peaceful the duel comes first (W8's); without a mentor it is skipped as if he had yielded: the same choice.
			setTurning(r, TurningKind.FALL, TurningStage.CONFRONTATION, TurningRules.CONFRONT_WAITING);
			TurningService.beginConfrontation(r, null, false);
			helper.assertTrue(turning(r).task() == TurningRules.CONFRONT_YIELDED, msg("the duel won: the mentor yielded"));
			int dark = Identity.alignment(r);
			action(r, "turn:resolve", "trophy");
			Inventory inventory = r.getInventory();
			boolean crystal = false;

			for (int i = 0; i < inventory.getContainerSize(); i++) {
				crystal |= Component.translatable("turning.oldrepublic.crystal.name").equals(inventory.getItem(i).get(DataComponents.ITEM_NAME));
			}

			helper.assertTrue(crystal && Story.get(r).hasFlag(TurningRules.CRYSTAL_TAKEN) && Identity.alignment(r) == dark + live.crystalDark,
					msg("her crystal taken: dark " + live.crystalDark + " and the crystal in hand"));
		} finally {
			cleanUp(helper, p);
			cleanUp(helper, q);
			cleanUp(helper, r);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void returnEscortLandsPrisonersAtGreenwatch(GameTestHelper helper) {
		int task = TurningRules.returnTask(TurningRules.RETURN_ESCORT, 0b0101, 0b011);
		helper.assertTrue(TurningRules.phase(task) == TurningRules.RETURN_ESCORT && TurningRules.guardsDown(task) == 0b0101 && TurningRules.boarded(task) == 0b011,
				msg("the task packs the phase, the guards down and the prisoners aboard"));
		helper.assertTrue(TurningRules.allGuardsDown(TurningRules.returnTask(TurningRules.RETURN_HELD, TurningRules.ALL_GUARDS))
				&& !TurningRules.allGuardsDown(TurningRules.returnTask(TurningRules.RETURN_HELD, 0b0111)), msg("the cells open when all four guards are down"));

		List<BlockPos> bays = List.of(new BlockPos(100, 64, 100), new BlockPos(140, 64, 100));
		helper.assertTrue(TurningRules.onGreenwatchBay(new Vec3(104.5, 70, 100.5), bays, null), msg("a ship on a pad bay"));
		helper.assertTrue(!TurningRules.onGreenwatchBay(new Vec3(120.5, 64, 100.5), bays, BlockPos.ZERO), msg("between the bays is not on one"));
		helper.assertTrue(TurningRules.onGreenwatchBay(new Vec3(30, 64, 30), List.of(), BlockPos.ZERO)
				&& !TurningRules.onGreenwatchBay(new Vec3(55, 64, 55), List.of(), BlockPos.ZERO), msg("without bays, near the hub's centre"));
		helper.assertTrue(TurningRules.onGreenwatchBay(new Vec3(5000, 64, 5000), List.of(), null), msg("with no Greenwatch at all, anywhere"));

		ServerPlayer p = at(helper, new BlockPos(2, 1, 2), Path.SITH, 2, B.returnAlignment + 10);
		TurningBalance live = TurningService.balance();
		int aboard = TurningRules.returnTask(TurningRules.RETURN_ABOARD, TurningRules.ALL_GUARDS, TurningRules.ALL_PRISONERS);

		try {
			MinecraftServer server = p.level().getServer();
			setTurning(p, TurningKind.RETURN, TurningStage.TASK, aboard);
			TurningService.onPlanetArrived(p, Planets.Planet.VARKHAST, false);
			helper.assertTrue(turning(p).stage() == TurningStage.TASK && turning(p).task() == aboard, msg("a touchdown elsewhere keeps them aboard"));

			setTurning(p, TurningKind.RETURN, TurningStage.TASK, TurningRules.returnTask(TurningRules.RETURN_ESCORT, TurningRules.ALL_GUARDS, 0b001));
			TurningService.onPlanetArrived(p, Planets.Planet.SELVAREN, false);
			helper.assertTrue(turning(p).stage() == TurningStage.TASK, msg("nobody is put down before all three are aboard"));

			setTurning(p, TurningKind.RETURN, TurningStage.TASK, aboard);
			Identity.setReputation(p, Faction.JEDI_ORDER, -100);
			Identity.setReputation(p, Faction.SITH_EMPIRE, 400);
			long xp = totalXp(p);
			int alignment = Identity.alignment(p);
			// The game-test server has no Selvaren: with no Greenwatch placed every touchdown there is on it (the rule's last case).
			boolean onBay = TurningRules.onGreenwatchBay(p.position(), PlanetHubs.padBays(server, Planets.Planet.SELVAREN),
					PlanetHubs.hub(server, Planets.Planet.SELVAREN));
			TurningService.onPlanetArrived(p, Planets.Planet.SELVAREN, false);

			if (!onBay) {
				helper.assertTrue(turning(p).stage() == TurningStage.TASK && turning(p).task() == aboard, msg("off the pad bays they stay aboard"));
			} else {
				StoryData.Turning t = turning(p);
				helper.assertTrue(t.kind() == TurningKind.RETURN && t.stage() == TurningStage.CONFRONTATION && t.task() == TurningRules.CONFRONT_WAITING,
						msg("landed at the Greenwatch: Amends is done, Sorn will come; got " + t));
				helper.assertTrue(landed(helper, p).size() == TurningRules.PRISONERS, msg("three prisoners step off, got " + landed(helper, p).size()));
				helper.assertTrue(totalXp(p) - xp == live.taskXp, msg("the task's XP"));
				helper.assertTrue(Identity.reputation(p, Faction.JEDI_ORDER) == -100 + live.taskRepGain
						&& Identity.reputation(p, Faction.SITH_EMPIRE) == 400 + live.taskRepLoss, msg("the Order gains, the Empire loses"));
				helper.assertTrue(Identity.alignment(p) == alignment + live.returnLight && entry(p, JournalEntries.AMENDS), msg("light +5, entry amends"));

				TurningService.onPlanetArrived(p, Planets.Planet.SELVAREN, false);
				helper.assertTrue(landed(helper, p).size() == TurningRules.PRISONERS, msg("a second touchdown puts nobody down again"));
			}
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the rite and its costs (8.3, 8.4)

	@GameTest(maxTicks = 20)
	public void riteChangesPathWithoutRestartingLessons(GameTestHelper helper) {
		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);

		try {
			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			long day = Clocks.day(p.level().getServer());
			action(p, "turn:rite");
			StoryData after = Story.get(p);
			helper.assertTrue(Identity.path(p) == Path.SITH && after.storyPath() == Path.SITH, msg("the identity and the story are Sith"));
			helper.assertTrue(after.stage() == PathStage.COMPLETE, msg("Chapter Two's path stage stays COMPLETE (no lessons again), got " + after.stage()));
			helper.assertTrue(after.chapter3.stage() == ChapterThreeStage.COMPLETE && after.chapter4.stage() == ChapterFourStage.COMPLETE,
					msg("Chapters Three and Four keep their records"));
			StoryData.Turning t = after.turning;
			helper.assertTrue(t.kind() == TurningKind.FALL && t.stage() == TurningStage.COMPLETE && t.task() == 0 && t.turnedDay() == day,
					msg("the record rests at COMPLETE with the day of the rite; got " + t));
			helper.assertTrue(t.bandSince() < 0 && t.refusals() == 0 && t.reofferDay() < 0, msg("the band starts afresh for the other turning"));
			helper.assertTrue(entry(p, JournalEntries.KNEEL) && Progression.get(p).flags.contains("resonance:the_fall"), msg("entry kneel, Resonance the_fall"));
			helper.assertTrue(condition(p, "turn:stage", "complete") == null, msg("turn:stage sees COMPLETE"));

			action(p, "turn:rite");
			helper.assertTrue(Identity.path(p) == Path.SITH && Story.get(p).turning.turnedDay() == day, msg("a second rite changes nothing"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void rankCappedAtTwo(GameTestHelper helper) {
		helper.assertTrue(TurningRules.rankAfter(1, B) == 1 && TurningRules.rankAfter(2, B) == 2 && TurningRules.rankAfter(3, B) == B.rankCap
				&& TurningRules.rankAfter(4, B) == B.rankCap, msg("min(rank, " + B.rankCap + ")"));

		ServerPlayer knight = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 3, B.fallAlignment - 10);
		ServerPlayer darth = at(helper, new BlockPos(4, 1, 1), Path.SITH, 4, B.returnAlignment + 10);
		ServerPlayer initiate = at(helper, new BlockPos(7, 1, 1), Path.JEDI, 1, B.fallAlignment - 10);

		try {
			flag(knight, ChapterThreeRules.RANK3_DONE);
			setTurning(knight, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(knight, "turn:rite");
			helper.assertTrue(Identity.rank(knight) == 2 && Identity.path(knight) == Path.SITH, msg("a Knight who Falls is an Apprentice"));
			helper.assertTrue(!Story.get(knight).hasFlag(ChapterThreeRules.RANK3_DONE) && Story.get(knight).hasFlag(RankThree.RANK3_PAID),
					msg("the Knighting is paid: the Naming opens again without its points"));

			choice(darth, TurningRules.RANK4_DONE);
			choice(darth, TurningRules.darthChoice(3));
			level(darth, 20);
			SkillService.refresh(darth);
			helper.assertTrue(Skills.isOpen(darth, 7), msg("a Darth has slot 8"));
			setTurning(darth, TurningKind.RETURN, TurningStage.RITE, TurningRules.RITE_HERE);
			action(darth, "turn:rite");
			helper.assertTrue(Identity.rank(darth) == 2 && Identity.path(darth) == Path.JEDI, msg("a Darth who Returns is a Padawan"));
			helper.assertTrue(!Identity.hasChoice(darth, TurningRules.RANK4_DONE) && Identity.hasChoice(darth, TurningRules.RANK4_PAID),
					msg("rank 4 is paid: the Master's ceremony opens again without its points"));
			helper.assertTrue(Skills.isOpen(darth, 5) && !Skills.isOpen(darth, 6) && !Skills.isOpen(darth, 7), msg("slots 7 and 8 close with rank 4"));

			setTurning(initiate, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(initiate, "turn:rite");
			helper.assertTrue(Identity.rank(initiate) == 1, msg("a lower rank stays as it was, got " + Identity.rank(initiate)));
		} finally {
			cleanUp(helper, knight);
			cleanUp(helper, darth);
			cleanUp(helper, initiate);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void oldSignaturesRemovedNewGranted(GameTestHelper helper) {
		helper.assertTrue(TurningRules.signatures(Path.JEDI).equals(List.of("force_heal", "force_barrier"))
				&& TurningRules.signatures(Path.SITH).equals(List.of("force_lightning", "force_choke")), msg("the two signature powers of each path"));
		helper.assertTrue(TurningRules.rankGrants(Path.JEDI).equals(List.of(SkillIds.KNIGHTS_VIGIL, TurningRules.FORCE_VALOR))
				&& TurningRules.rankGrants(Path.SITH).equals(List.of(SkillIds.LORDS_DOMINION, TurningRules.FORCE_RAGE)), msg("the rank grants of each path"));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 3, B.fallAlignment - 10);

		try {
			for (String node : List.of("force_heal", "force_barrier", SkillIds.KNIGHTS_VIGIL, TurningRules.FORCE_VALOR, SkillIds.WATCHERS_INSIGHT)) {
				Skills.grant(p, node);
			}

			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");

			for (String node : List.of("force_heal", "force_barrier", SkillIds.KNIGHTS_VIGIL, TurningRules.FORCE_VALOR)) {
				helper.assertTrue(!owns(p, node), msg("the old path's " + node + " is removed"));
			}

			helper.assertTrue(owns(p, SkillIds.WATCHERS_INSIGHT), msg("the old tree's other granted nodes (holocron passives) stay"));
			SkillData skills = SkillService.data(p);
			helper.assertTrue(skills.owns("force_lightning") && skills.isGranted("force_lightning") && skills.owns("force_choke")
					&& skills.isGranted("force_choke"), msg("Lightning and Choke are granted"));
			Set<String> abilities = Progression.get(p).abilities;
			helper.assertTrue(abilities.contains("force_lightning") && abilities.contains("force_choke") && !abilities.contains("force_heal")
					&& !abilities.contains("force_barrier"), msg("the powers known follow: " + abilities));

			setTurning(p, TurningKind.RETURN, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");
			helper.assertTrue(owns(p, "force_heal") && owns(p, "force_barrier") && !owns(p, "force_lightning") && !owns(p, "force_choke"),
					msg("the Return takes Lightning and Choke and gives Heal and Barrier back"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void boughtNodesHalfRefunded(GameTestHelper helper) {
		helper.assertTrue(TurningRules.refund(7, B) == 3 && TurningRules.refund(10, B) == 5 && TurningRules.refund(1, B) == 0
				&& TurningRules.refund(0, B) == 0 && TurningRules.refund(-4, B) == 0, msg("half of all points spent, rounded down"));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		TurningBalance live = TurningService.balance();

		try {
			level(p, 14);
			// Bought in the Jedi tree: 2 + 2 + 1 + 2 points; Lift is also known through the exploration tree.
			own(p, SkillIds.CENTERED_MIND, 2, 2);
			own(p, SkillIds.CALM_FOCUS, 1, 2);
			own(p, "heal_shared_light", 1, 1);
			Skills.grant(p, SkillIds.FORCE_LIFT);
			own(p, "lift_learned_jedi", 1, 2);
			// Bought outside it: stays.
			own(p, SkillIds.DEEP_RESERVES, 1, 1);
			int sp = Progression.get(p).skillPoints;
			int level = Progression.get(p).level;
			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");

			for (String node : List.of(SkillIds.CENTERED_MIND, SkillIds.CALM_FOCUS, "heal_shared_light", "lift_learned_jedi")) {
				helper.assertTrue(!owns(p, node), msg("the bought Jedi node " + node + " is removed"));
			}

			int refunded = storySp(p, sp, level);
			helper.assertTrue(refunded == TurningRules.refund(7, live), msg("half of the 7 points back, got " + refunded));
			SkillData skills = SkillService.data(p);
			helper.assertTrue(skills.owns(SkillIds.DEEP_RESERVES) && skills.spent.getOrDefault(SkillIds.DEEP_RESERVES, 0) == 1,
					msg("a node bought in another tree stays bought"));
			helper.assertTrue(Progression.get(p).abilities.contains(SkillIds.FORCE_LIFT), msg("Lift is still known through the exploration tree"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void reputationAndTrustAfterTurning(GameTestHelper helper) {
		helper.assertTrue(TurningRules.oldFactionEnd(600, B) == B.oldFactionCap && TurningRules.oldFactionEnd(-700, B) == -700,
				msg("the old order ends Hostile at best: min(before, " + B.oldFactionCap + ")"));
		helper.assertTrue(TurningRules.newFactionEnd(-300, B) == B.newFactionBonus && TurningRules.newFactionEnd(400, B) == 400 + B.newFactionBonus,
				msg("the new order: max(before, 0) + " + B.newFactionBonus));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		ServerPlayer q = at(helper, new BlockPos(4, 1, 1), Path.JEDI, 2, B.fallAlignment - 10);
		TurningBalance live = TurningService.balance();

		try {
			Identity.setReputation(p, Faction.JEDI_ORDER, 600);
			Identity.setReputation(p, Faction.SITH_EMPIRE, -300);
			trust(p, MentorRole.JEDI, 85);
			trust(p, MentorRole.SITH, 10);
			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");
			helper.assertTrue(Identity.reputation(p, Faction.JEDI_ORDER) == TurningRules.oldFactionEnd(600, live),
					msg("the Order ends at " + TurningRules.oldFactionEnd(600, live) + ", got " + Identity.reputation(p, Faction.JEDI_ORDER)));
			helper.assertTrue(Identity.reputation(p, Faction.SITH_EMPIRE) == TurningRules.newFactionEnd(-300, live),
					msg("the Empire ends at " + TurningRules.newFactionEnd(-300, live) + " (the join bonus not added twice), got "
							+ Identity.reputation(p, Faction.SITH_EMPIRE)));
			helper.assertTrue(Identity.trust(p, MentorRole.SITH.id()) == live.newTrust && Identity.trust(p, MentorRole.JEDI.id()) == 0,
					msg("Sorn's trust " + live.newTrust + ", Saelen's 0"));

			Identity.setReputation(q, Faction.JEDI_ORDER, -700);
			Identity.setReputation(q, Faction.SITH_EMPIRE, 400);
			setTurning(q, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			action(q, "turn:rite");
			helper.assertTrue(Identity.reputation(q, Faction.JEDI_ORDER) == -700 && Identity.reputation(q, Faction.SITH_EMPIRE) == 400 + live.newFactionBonus,
					msg("a worse standing stays; a good one gains " + live.newFactionBonus));
		} finally {
			cleanUp(helper, p);
			cleanUp(helper, q);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void coldMentorLine(GameTestHelper helper) {
		ServerPlayer p = at(helper, new BlockPos(2, 1, 2), Path.JEDI, 2, B.fallAlignment - 10);
		MentorEntity sorn = mentor(helper, p, MentorRole.SITH);
		MentorEntity saelen = mentor(helper, p, MentorRole.JEDI);

		try {
			helper.assertTrue(!Turnings.done(p, TurningKind.FALL) && !Turnings.done(p, TurningKind.RETURN),
					msg("before any turning the other path's mentor says the M2 \"not now\""));

			// At the rite the new mentor's use reopens the rite.
			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			StoryMentors.onUsed(p, sorn);
			helper.assertTrue(KNEEL.equals(openDialogue(p)), msg("Sorn's use reopens the rite, got " + openDialogue(p)));
			Dialogues.close(p);
			action(p, "turn:rite");

			// After it, the mentor turned from: one cold line, no dialogue, no sessions.
			helper.assertTrue(Turnings.done(p, TurningKind.FALL), msg("the Fall is recorded: the lead's line is the cold one (turning.oldrepublic.cold.jedi)"));
			StoryMentors.onUsed(p, saelen);
			helper.assertTrue(openDialogue(p) == null, msg("Saelen opens no dialogue for a fallen Jedi"));
			helper.assertTrue(Identity.trust(p, MentorRole.JEDI.id()) == 0, msg("and her trust is gone"));

			// The new mentor is the player's own now.
			StoryMentors.onUsed(p, sorn);
			String own = openDialogue(p);
			helper.assertTrue(own != null && !KNEEL.equals(own), msg("Sorn opens his own dialogue, got " + own));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- rank 4 (8.5)

	@GameTest(maxTicks = 20)
	public void rankFourChecklistRules(GameTestHelper helper) {
		for (Path path : new Path[] {Path.JEDI, Path.SITH}) {
			TurningRules.Standing s = readyStanding(path);
			int lean = path == Path.SITH ? -1 : 1;
			List<TurningRules.Requirement> list = TurningRules.rankFour(s, B);
			helper.assertTrue(TurningRules.rankFourBlocker(s, B) == null, msg(path + ": everything met"));
			helper.assertTrue(list.stream().map(TurningRules.Requirement::id).toList()
					.equals(List.of("story", "level", "rank", "trust", "reputation", "alignment", "mastery", "deeds")), msg(path + ": the eight requirements, in order"));

			ChapterFive.Deeds deeds = s.deeds();
			String[][] cases = {
					{"story", REQ + "story|1|0"},
					{"level", REQ + "level|" + B.rank4Level + "|" + (B.rank4Level - 1)},
					{"rank", REQ + "rank|3|2"},
					{"trust", REQ + "trust|" + B.rank4Trust + "|" + (B.rank4Trust - 1)},
					{"reputation", REQ + "reputation|" + B.rank4Rep + "|" + (B.rank4Rep - 1)},
					{"alignment", REQ + "alignment|" + B.rank4Alignment + "|" + (B.rank4Alignment - 1)},
					{"mastery", REQ + "mastery|" + B.rank4MasteryPowers + "|" + (B.rank4MasteryPowers - 1)},
					{"deeds", REQ + "deeds|" + B.rank4Deeds + "|1"}};

			for (String[] c : cases) {
				TurningRules.Standing one = switch (c[0]) {
					case "story" -> with(s, ChapterFiveStage.AFTERMATH, s.level(), s.rank(), false, s.trust(), s.ownRep(), 0, s.alignment(), s.masteredPowers(), deeds);
					case "level" -> with(s, s.stage(), B.rank4Level - 1, s.rank(), false, s.trust(), s.ownRep(), 0, s.alignment(), s.masteredPowers(), deeds);
					case "rank" -> with(s, s.stage(), s.level(), 2, false, s.trust(), s.ownRep(), 0, s.alignment(), s.masteredPowers(), deeds);
					case "trust" -> with(s, s.stage(), s.level(), s.rank(), false, B.rank4Trust - 1, s.ownRep(), 0, s.alignment(), s.masteredPowers(), deeds);
					case "reputation" -> with(s, s.stage(), s.level(), s.rank(), false, s.trust(), B.rank4Rep - 1, 0, s.alignment(), s.masteredPowers(), deeds);
					case "alignment" -> with(s, s.stage(), s.level(), s.rank(), false, s.trust(), s.ownRep(), 0, lean * (B.rank4Alignment - 1),
							s.masteredPowers(), deeds);
					case "mastery" -> with(s, s.stage(), s.level(), s.rank(), false, s.trust(), s.ownRep(), 0, s.alignment(), B.rank4MasteryPowers - 1, deeds);
					default -> with(s, s.stage(), s.level(), s.rank(), false, s.trust(), s.ownRep(), 0, s.alignment(), s.masteredPowers(),
							new ChapterFive.Deeds(true, B.deedEchoes - 1, B.deedBounties - 1, B.deedContracts - 1, false));
				};
				String reason = TurningRules.rankFourBlocker(one, B);
				helper.assertTrue(c[1].equals(reason), msg(path + " missing " + c[0] + ": expected " + c[1] + ", got " + reason));
			}

			TurningRules.Standing wrongSide = with(s, s.stage(), s.level(), s.rank(), false, s.trust(), s.ownRep(), 0, -s.alignment(), s.masteredPowers(), deeds);
			helper.assertTrue((REQ + "alignment|" + B.rank4Alignment + "|" + (-B.rank4Alignment)).equals(TurningRules.rankFourBlocker(wrongSide, B)),
					msg(path + ": the other side's alignment fails"));
		}

		// Ulvaine's writ counts for a Sith only.
		ChapterFive.Deeds contractsAndWrit = new ChapterFive.Deeds(false, 0, 0, B.deedContracts, true);
		helper.assertTrue(TurningRules.deeds(contractsAndWrit, Path.SITH, B) == 2 && TurningRules.deeds(contractsAndWrit, Path.JEDI, B) == 1
				&& TurningRules.deeds(contractsAndWrit, Path.INDEPENDENT, B) == 1, msg("the writ is a Sith's deed"));

		// The Wayfarer: the title and three Trusted factions instead of rank, trust, reputation and alignment.
		TurningRules.Standing w = readyStanding(Path.INDEPENDENT);
		helper.assertTrue(TurningRules.rankFour(w, B).stream().map(TurningRules.Requirement::id).toList()
				.equals(List.of("story", "level", "title", "factions", "mastery", "deeds")), msg("the Wayfarer's six requirements, in order"));
		helper.assertTrue(TurningRules.rankFourBlocker(w, B) == null, msg("the Wayfarer: everything met"));
		helper.assertTrue(TurningRules.rankFourBlocker(with(w, w.stage(), w.level(), 0, true, 0, -900, w.trustedFactions(), -100, w.masteredPowers(), w.deeds()),
				B) == null, msg("no trust, own order or alignment is asked of an Independent"));
		helper.assertTrue((REQ + "title|1|0").equals(TurningRules.rankFourBlocker(with(w, w.stage(), w.level(), 0, false, 0, 0, w.trustedFactions(), 0,
				w.masteredPowers(), w.deeds()), B)), msg("the Pathfinder title"));
		helper.assertTrue((REQ + "factions|" + B.wayfarerFactions + "|" + (B.wayfarerFactions - 1)).equals(TurningRules.rankFourBlocker(with(w, w.stage(),
				w.level(), 0, true, 0, 0, B.wayfarerFactions - 1, 0, w.masteredPowers(), w.deeds()), B)), msg("three Trusted factions"));
		TurningRules.Standing nobody = new TurningRules.Standing(Path.NONE, ChapterFiveStage.COMPLETE, 30, 4, true, 100, 1000, 6, 100, 9,
				new ChapterFive.Deeds(true, 9, 9, 9, true));
		helper.assertTrue((REQ + "no_path").equals(TurningRules.rankFourBlocker(nobody, B)), msg("no path, no rank 4"));
		helper.assertTrue(new TurningRules.Requirement("level", false, 19, 20).failReason().equals(REQ + "level|20|19"),
				msg("a fail reason is the lang key, the need and what the player has"));
		helper.assertTrue(TurningRules.checklistVisible(Path.JEDI, ChapterFiveStage.AFTERMATH) && TurningRules.checklistVisible(Path.INDEPENDENT,
				ChapterFiveStage.COMPLETE) && !TurningRules.checklistVisible(Path.SITH, ChapterFiveStage.THE_HYMNAL)
				&& !TurningRules.checklistVisible(Path.NONE, ChapterFiveStage.COMPLETE), msg("the checklist shows from Chapter Five's AFTERMATH"));

		ServerPlayer p = at(helper, new BlockPos(1, 1, 1), Path.JEDI, 3, B.rank4Alignment + 10);

		try {
			StoryData data = Story.get(p);
			data.chapter5 = data.chapter5.withStage(ChapterFiveStage.AFTERMATH, -1L);
			Story.commit(p, data);
			String reason = condition(p, "rank4:ready", "");
			helper.assertTrue((REQ + "story|1|0").equals(reason), msg("the dialogue names the story first, got " + reason));
			RankFourChecklistPayload list = RankFour.checklist(p);
			helper.assertTrue(list.visible() && list.kind() == RankFourChecklistPayload.MASTER && !list.done() && list.entries().size() == 8
					&& "story".equals(list.entries().getFirst().id()) && !list.entries().getFirst().met(), msg("the Standing checklist reads the same rules"));

			setTurning(p, TurningKind.FALL, TurningStage.TASK, TurningRules.FALL_TO_ANSA);
			helper.assertTrue((REQ + "turning").equals(condition(p, "rank4:ready", "")), msg("halfway between two paths nobody is named Master"));
			setTurning(p, TurningKind.FALL, TurningStage.OFFERED, 0);
			helper.assertTrue((REQ + "story|1|0").equals(condition(p, "rank4:ready", "")), msg("an offer standing does not block"));
			choice(p, TurningRules.RANK4_DONE);
			helper.assertTrue((REQ + "done").equals(condition(p, "rank4:ready", "")), msg("held already"));

			StoryData early = Story.get(p);
			early.chapter5 = early.chapter5.withStage(ChapterFiveStage.VERSES, -1L);
			Story.commit(p, early);
			helper.assertTrue(!RankFour.checklist(p).visible(), msg("hidden before AFTERMATH"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void ceremonyGrantsPowerSlotsRobeAndPoints(GameTestHelper helper) {
		ServerPlayer master = ready(helper, new BlockPos(1, 1, 1), Path.JEDI);
		ServerPlayer darth = ready(helper, new BlockPos(4, 1, 1), Path.SITH);
		TurningBalance live = TurningService.balance();

		try {
			Item masterRobe = robe(helper, RankFour.MASTER_ROBE);
			Item darthRobe = robe(helper, RankFour.DARTH_ROBE);
			helper.assertTrue(condition(master, "rank4:ready", "") == null, msg("ready for the Master's ceremony: " + condition(master, "rank4:ready", "")));
			SkillService.refresh(master);
			helper.assertTrue(Skills.isOpen(master, 5) && !Skills.isOpen(master, 6), msg("level 20: slots 5 and 6 of the second bar, not 7"));
			int sp = Progression.get(master).skillPoints;
			int level = Progression.get(master).level;
			long xp = totalXp(master);
			action(master, "rank4:ceremony");
			helper.assertTrue(Identity.rank(master) == 4 && RankFour.done(master), msg("a Master: rank 4, rank4.done"));
			helper.assertTrue(owns(master, TurningRules.FORCE_VALOR) && Progression.get(master).abilities.contains(TurningRules.FORCE_VALOR), msg("Force Valor"));
			helper.assertTrue(Skills.isOpen(master, 6) && Skills.isOpen(master, 7), msg("slots 7 and 8 open"));
			helper.assertTrue(count(master, masterRobe) == 1, msg("the Master's robe"));
			helper.assertTrue(storySp(master, sp, level) == live.ceremonySp && totalXp(master) - xp == live.ceremonyXp,
					msg("+" + live.ceremonySp + " SP and " + live.ceremonyXp + " XP, got " + storySp(master, sp, level) + " and " + (totalXp(master) - xp)));
			helper.assertTrue(Progression.get(master).flags.contains("resonance:third_rank") && entry(master, JournalEntries.MASTER),
					msg("Resonance third_rank, entry master"));

			int spAfter = Progression.get(master).skillPoints;
			action(master, "rank4:ceremony");
			helper.assertTrue(count(master, masterRobe) == 1 && Progression.get(master).skillPoints == spAfter, msg("held once"));

			// A Darth whose rank 4 was paid on an earlier path: the rank, the power and the robe, no points and no XP.
			choice(darth, TurningRules.RANK4_PAID);
			sp = Progression.get(darth).skillPoints;
			level = Progression.get(darth).level;
			xp = totalXp(darth);
			action(darth, "rank4:ceremony", "1");
			helper.assertTrue(Identity.rank(darth) == 4 && owns(darth, TurningRules.FORCE_RAGE) && count(darth, darthRobe) == 1 && entry(darth, JournalEntries.DARTH),
					msg("a Darth: rank 4, Force Rage, the robe, the entry"));
			helper.assertTrue(storySp(darth, sp, level) == 0 && totalXp(darth) == xp, msg("paid already: no SP and no XP"));
		} finally {
			cleanUp(helper, master);
			cleanUp(helper, darth);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void wayfarerTitleAndPassive(GameTestHelper helper) {
		ServerPlayer p = ready(helper, new BlockPos(1, 1, 1), Path.INDEPENDENT);
		ServerPlayer far = ready(helper, new BlockPos(4, 1, 1), Path.INDEPENDENT);
		TurningBalance live = TurningService.balance();

		try {
			Item mantle = robe(helper, RankFour.WAYFARER_MANTLE);
			helper.assertTrue(condition(p, "rank4:ready", "") == null, msg("ready for the Wayfarer title: " + condition(p, "rank4:ready", "")));
			// The Compass at Courier's Fall is where the player stands.
			StoryData data = Story.get(p);
			data.chapter3 = ChapterThreeRules.withFall(data.chapter3, p.blockPosition());
			Story.commit(p, data);
			int sp = Progression.get(p).skillPoints;
			int level = Progression.get(p).level;
			action(p, "rank4:wayfarer");
			helper.assertTrue(Identity.hasChoice(p, SkillService.WAYFARER_TITLE) && RankFour.done(p), msg("the title title.wayfarer, rank4.done"));
			helper.assertTrue(Identity.rank(p) == 0, msg("a title, not a rank"));
			SkillData skills = SkillService.data(p);
			helper.assertTrue(skills.owns(SkillIds.WAYFARERS_STRIDE) && skills.isGranted(SkillIds.WAYFARERS_STRIDE), msg("Wayfarer's Grace (wayfarers_stride)"));
			helper.assertTrue(Skills.isOpen(p, 7), msg("the whole second bar opens"));
			helper.assertTrue(count(p, mantle) == 1 && entry(p, JournalEntries.WAYFARER), msg("the mantle and the entry"));
			helper.assertTrue(storySp(p, sp, level) == live.ceremonySp, msg("+" + live.ceremonySp + " SP"));

			// Away from the Compass nothing happens.
			StoryData away = Story.get(far);
			away.chapter3 = ChapterThreeRules.withFall(away.chapter3, far.blockPosition().offset(100, 0, 0));
			Story.commit(far, away);
			action(far, "rank4:wayfarer");
			helper.assertTrue(!Identity.hasChoice(far, SkillService.WAYFARER_TITLE) && !RankFour.done(far), msg("the ceremony is held at the Compass only"));
		} finally {
			cleanUp(helper, p);
			cleanUp(helper, far);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void darthNameRecorded(GameTestHelper helper) {
		helper.assertTrue(TurningRules.darthChoice(2).equals("rank4.darth.2") && TurningRules.darthName(Set.of("rank4.darth.3", "x")) == 3
				&& TurningRules.darthName(Set.of()) == 0, msg("the name is the choice rank4.darth.<n>"));

		ServerPlayer p = ready(helper, new BlockPos(1, 1, 1), Path.SITH);
		ServerPlayer q = ready(helper, new BlockPos(4, 1, 1), Path.SITH);

		try {
			action(p, "rank4:ceremony", "5");
			helper.assertTrue(Identity.rank(p) == 4 && Identity.hasChoice(p, TurningRules.darthChoice(5))
					&& TurningRules.darthName(Identity.get(p).choices) == 5, msg("Darth name 5 recorded"));

			for (int n = 1; n <= TurningRules.DARTH_NAMES; n++) {
				helper.assertTrue(n == 5 || !Identity.hasChoice(p, TurningRules.darthChoice(n)), msg("one name only"));
			}

			// A Darth who Returns loses the name; the ceremony stays paid.
			setTurning(p, TurningKind.RETURN, TurningStage.RITE, TurningRules.RITE_HERE);
			action(p, "turn:rite");
			helper.assertTrue(TurningRules.darthName(Identity.get(p).choices) == 0 && Identity.hasChoice(p, TurningRules.RANK4_PAID)
					&& !RankFour.done(p) && Identity.rank(p) == 2, msg("returned: no Darth name, rank4.paid, rank 2"));

			// No name, or one off the list: no ceremony.
			action(q, "rank4:ceremony");
			action(q, "rank4:ceremony", String.valueOf(TurningRules.DARTH_NAMES + 1));
			action(q, "rank4:ceremony", "Valcrane");
			helper.assertTrue(Identity.rank(q) == 3 && !RankFour.done(q) && TurningRules.darthName(Identity.get(q).choices) == 0,
					msg("a Darth must take one of the six names"));
		} finally {
			cleanUp(helper, p);
			cleanUp(helper, q);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void robesReissuedOnceADay(GameTestHelper helper) {
		helper.assertTrue(TurningRules.reissueDue(-1, DAY) && !TurningRules.reissueDue((int) DAY, DAY) && TurningRules.reissueDue((int) DAY - 1, DAY),
				msg("never given, or given before today"));

		ServerPlayer p = ready(helper, new BlockPos(1, 1, 1), Path.JEDI);
		String key = TurningRules.REISSUE_PREFIX + RankFour.MASTER_ROBE;

		try {
			Item robe = robe(helper, RankFour.MASTER_ROBE);
			helper.assertTrue(!RankFour.reissue(p), msg("no rank 4: no robe"));
			action(p, "rank4:ceremony");
			int day = (int) Clocks.day(p.level().getServer());
			helper.assertTrue(count(p, robe) == 1 && Story.get(p).counter(key) == day, msg("the ceremony's robe, noted today"));
			helper.assertTrue(!RankFour.reissue(p), msg("carried: not again"));

			removeAll(p, robe);
			helper.assertTrue(!RankFour.reissue(p) && count(p, robe) == 0, msg("lost, but given today: not again today"));

			StoryData data = Story.get(p);
			data.counters.put(key, day - 1);
			Story.commit(p, data);
			p.getEnderChestInventory().setItem(0, new ItemStack(robe));
			helper.assertTrue(!RankFour.reissue(p), msg("kept in the ender chest: not again"));
			p.getEnderChestInventory().setItem(0, ItemStack.EMPTY);
			p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(robe));
			helper.assertTrue(!RankFour.reissue(p), msg("worn: not again"));
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

			helper.assertTrue(RankFour.reissue(p) && count(p, robe) == 1 && Story.get(p).counter(key) == day, msg("lost since yesterday: given again"));
			removeAll(p, robe);
			helper.assertTrue(!RankFour.reissue(p), msg("once a day"));
		} finally {
			cleanUp(helper, p);
		}

		helper.succeed();
	}
}

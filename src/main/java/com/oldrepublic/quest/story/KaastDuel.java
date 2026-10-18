package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.kaast.HuntingCamps;
import com.oldrepublic.boss.kaast.KaastBalance;
import com.oldrepublic.boss.kaast.KaastFight;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.vendor.VendorItems;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The end of Chapter Four (spec MILESTONE_4.md 8.6, 10.6): the marker beacon and Kaast's recorded
 * challenge, the hunting camp (placed by W6's {@link HuntingCamps}, retried until it fits), the ring
 * that starts the duel, the yield dialogue and its three endings, the fight's rewards for the owner,
 * helpers and the daily rematch, the camp's stash, and the report that completes the chapter.
 *
 * <p>W6 runs the fight and calls {@code QuestHooks.onBossYielded} / {@code onBossDefeated};
 * {@link KaastFight#resolve} stages the ending scene. Every reward here is guarded by a one-time flag
 * or a per-day counter, so a hook that fires twice (or a fight resolved on Peaceful, where no defeat
 * hook may come at all) pays exactly once.
 */
public final class KaastDuel {
	static final Identifier CHALLENGE = OldRepublicMod.id("c4_kaast_challenge");
	static final Identifier YIELD = OldRepublicMod.id("c4_kaast_yield");
	/** W4's named items (16.1); looked up by id so this class loads before W4's merge. */
	private static final Identifier KAAST_HELMET = OldRepublicMod.id("kaast_helmet");
	private static final Identifier KAAST_CHEST = OldRepublicMod.id("kaast_chest");
	private static final Identifier KAAST_LONGSHOT = OldRepublicMod.id("kaast_longshot");
	/** Duel.boss values (12.1). */
	static final int BOSS_NONE = 0;
	static final int BOSS_YIELDED = 1;
	static final int BOSS_RESOLVED = 2;
	/** The owner standing this close to the camp's anchor is in its ring, when no site piece reported it. */
	private static final String TASK_CAMP = "camp";

	/**
	 * W6's {@code kaast} section; W2 reads only its reward keys (14: {@code ownerXp}, {@code ownerSp},
	 * {@code ownerCredits}, {@code resonance}, {@code helperXp}, {@code rematchXp},
	 * {@code rematchSchematicPity}, {@code rematchCredits}). The same name and class as W6's own
	 * call, so the section cache holds one instance.
	 */
	static KaastBalance kaast() {
		return BalanceConfig.section("kaast", KaastBalance.class, KaastBalance::new);
	}

	// ---------------------------------------------------------------- CHALLENGED

	/**
	 * CHALLENGED begins: once, the marker beacon lands near the player with Kaast's recorded
	 * challenge, the {@code hunting_camp} destination unlocks for good and the entry is written; then
	 * the camp is placed on the path world (retried by {@link #tickChallenged} until it fits).
	 */
	static void onChallenged(ServerPlayer p) {
		if (ChapterFourService.once(p, ChapterFourRules.CHALLENGE_PLAYED)) {
			placeBeacon(p);
			TravelService.unlock(p, Destination.HUNTING_CAMP);
			Journal.unlockEntry(p, JournalEntries.THE_CHALLENGE);
			Journal.unlockCodex(p, JournalEntries.CODEX_TORVIN_KAAST);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.beacon"));
			p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective.challenged", ChapterFourService.balance().recommendedLevel));
			openUnlessShowing(p, CHALLENGE);
		}

		placeCamp(p);
	}

	/**
	 * The marker beacon: a block {@code beaconDistance} ahead of the player on the surface, where
	 * there is room; else at their feet. Its own light and motes are the red pulse. Remembered as the
	 * {@code beacon} site.
	 */
	static @Nullable BlockPos placeBeacon(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 look = p.getLookAngle().horizontal();
		Vec3 ahead = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		int distance = Math.max(1, ChapterFourService.balance().beaconDistance);

		for (int step = distance; step >= 0; step--) {
			int x = (int) Math.floor(p.getX() + ahead.x * step);
			int z = (int) Math.floor(p.getZ() + ahead.z * step);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);

			if (Math.abs(y - p.getBlockY()) <= 4 && level.getBlockState(at).canBeReplaced() && level.setBlockAndUpdate(at, VendorItems.MARKER_BEACON.defaultBlockState())) {
				StoryData data = Story.get(p);
				data.chapter4 = data.chapter4.withSites(data.chapter4.sites().with(ChapterFourRules.SITE_BEACON, GlobalPos.of(level.dimension(), at)));
				Story.commit(p, data);
				return at;
			}
		}

		return null;
	}

	/**
	 * The player used a marker beacon: their own (the {@code beacon} site) replays Kaast's challenge
	 * while it is open; anyone else's is scrap with a red lamp.
	 */
	static void onMarkerUsed(ServerPlayer p, BlockPos pos) {
		StoryData data = Story.get(p);
		GlobalPos beacon = data.chapter4.sites().get(ChapterFourRules.SITE_BEACON);
		boolean own = beacon != null && beacon.dimension().equals(p.level().dimension()) && beacon.pos().equals(pos);

		if (own && data.chapter4.stage().ordinal() >= ChapterFourStage.CHALLENGED.ordinal() && data.chapter4.stage().ordinal() < ChapterFourStage.AFTERMATH.ordinal()) {
			openUnlessShowing(p, CHALLENGE);
			return;
		}

		p.sendOverlayMessage(Component.translatable(own ? "message.oldrepublic.story.c4.beacon_spent" : "message.oldrepublic.story.c4.beacon_other"));
	}

	/** Places the owner's camp through W6 when it is not placed yet; a failure retries after {@code retryTicks}. */
	static @Nullable GlobalPos placeCamp(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter4.duel().camp() != null) {
			return data.chapter4.duel().camp();
		}

		if (!ChapterFourService.due(p, TASK_CAMP)) {
			return null;
		}

		GlobalPos camp = HuntingCamps.place(p);

		if (camp == null) {
			ChapterFourService.retryLater(p, TASK_CAMP, ChapterFourService.balance().retryTicks);
			return null;
		}

		data = Story.get(p);
		data.chapter4 = data.chapter4.withDuel(data.chapter4.duel().withCamp(camp));
		Story.commit(p, data);
		ChapterFourService.clearRetry(p, TASK_CAMP);
		return camp;
	}

	/** CHALLENGED's wait: the camp placement retry, and the ring when no site piece reported the entry. */
	static void tickChallenged(ServerPlayer p) {
		GlobalPos camp = placeCamp(p);

		if (camp != null && inRing(p, camp)) {
			enterRing(p);
		}
	}

	/** Whether the player stands within {@code campRing} blocks of the camp's anchor, in its dimension. */
	static boolean inRing(ServerPlayer p, GlobalPos camp) {
		int ring = ChapterFourService.balance().campRing;
		return p.level().dimension().equals(camp.dimension()) && p.position().distanceToSqr(Vec3.atCenterOf(camp.pos())) <= (double) ring * ring;
	}

	/** A site piece was entered: the hunting camp's starts the duel (its owner's only). */
	static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		if (piece.kind() != SiteKind.HUNTING_CAMP) {
			return;
		}

		GlobalPos camp = Story.get(p).chapter4.duel().camp();

		// Another player's camp is not this player's duel: the ring check needs the owner's anchor inside the piece.
		if (camp != null && piece.box().isInside(camp.pos())) {
			enterRing(p);
		}
	}

	/** The owner entered the ring: DUEL; on Peaceful Kaast arrives, talks and yields at once (8.8). */
	static void enterRing(ServerPlayer p) {
		enterRing(p, ChapterFourService.peaceful(p));
	}

	/**
	 * {@link #enterRing(ServerPlayer)} with the difficulty given ({@code peaceful}), so the game
	 * tests can play the Peaceful duel without changing the server's difficulty.
	 */
	public static void enterRing(ServerPlayer p, boolean peaceful) {
		StoryData data = Story.get(p);

		if (data.chapter4.stage() != ChapterFourStage.CHALLENGED) {
			return;
		}

		ChapterFourService.enterStage(p, ChapterFourStage.DUEL);

		if (peaceful) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.peaceful_duel"));
			onYielded(p);
		}
	}

	// ---------------------------------------------------------------- the yield and the choice

	/** Kaast yielded to his owner (W6's hook, or Peaceful): the choice dialogue opens, owner only. */
	static void onYielded(ServerPlayer owner) {
		StoryData data = Story.get(owner);

		if (data.chapter4.stage() == ChapterFourStage.CHALLENGED) {
			ChapterFourService.enterStage(owner, ChapterFourStage.DUEL);
			data = Story.get(owner);
		}

		if (data.chapter4.stage() != ChapterFourStage.DUEL || data.chapter4.duel().boss() == BOSS_RESOLVED) {
			return;
		}

		if (data.chapter4.duel().boss() != BOSS_YIELDED) {
			data.chapter4 = data.chapter4.withDuel(data.chapter4.duel().withBoss(BOSS_YIELDED, -1));
			Story.commit(owner, data);
		}

		openUnlessShowing(owner, YIELD);
	}

	/** Whether the owner's yield dialogue may be answered now (the {@code c4:yield} condition). */
	static boolean choiceOpen(StoryData data) {
		return data.chapter4.stage() == ChapterFourStage.DUEL && data.chapter4.duel().boss() == BOSS_YIELDED && data.chapter4.duel().outcome() < 0;
	}

	/**
	 * {@code kaast:resolve <spare|strip|end>}: the owner's choice. Checked again on the server; W6
	 * stages the scene ({@link KaastFight#resolve}); the choice's rewards, the fight's first-clear
	 * rewards and the stash are paid once each; the chapter moves to AFTERMATH.
	 */
	static void resolve(ServerPlayer owner, String arg) {
		KaastOutcome outcome = KaastOutcome.byId(arg.trim());
		StoryData data = Story.get(owner);

		if (outcome == null || !choiceOpen(data)) {
			return;
		}

		data.chapter4 = data.chapter4.withDuel(data.chapter4.duel().withBoss(BOSS_RESOLVED, outcome.ordinal()));
		Story.commit(owner, data);
		KaastFight.resolve(owner, outcome);
		applyChoice(owner, outcome);
		firstClear(owner);
		stash(owner);
		ChapterFourService.enterStage(owner, ChapterFourStage.AFTERMATH);
	}

	/**
	 * One ending's rewards (8.6), once per ending flag: Spare (+10 light, {@code kaast_pattern}),
	 * Take his armour (his helmet and chest, Masterwork and named), End him (-10 dark, Kaast's
	 * Longshot, Jedi trust -15, Sith trust +10). Each gives its entry. No XP: the fight's is 10.6's.
	 *
	 * @return whether the rewards were given now
	 */
	public static boolean applyChoice(ServerPlayer p, KaastOutcome outcome) {
		if (!ChapterFourService.once(p, ChapterFourRules.CHOICE_PREFIX + outcome.id())) {
			return false;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		String source = "kaast." + outcome.id();

		switch (outcome) {
			case SPARED -> {
				StoryService.shift(p, source, b.spareLight, "quest.oldrepublic.c4.reason.spare");
				Rewards.give(p, new ItemStack(VendorItems.KAAST_PATTERN));
			}
			case STRIPPED -> {
				give(p, named(KAAST_HELMET, Quality.MASTERWORK));
				give(p, named(KAAST_CHEST, Quality.MASTERWORK));
			}
			case ENDED -> {
				StoryService.shift(p, source, b.endDark, "quest.oldrepublic.c4.reason.end");
				give(p, named(KAAST_LONGSHOT, Quality.MASTERWORK));
				StoryService.trust(p, MentorRole.JEDI, b.endJediTrust, "quest.oldrepublic.c4.reason.end");
				StoryService.trust(p, MentorRole.SITH, b.endSithTrust, "quest.oldrepublic.c4.reason.end");
			}
		}

		Identity.recordChoice(p, source);
		Journal.unlockEntry(p, ChapterFourRules.outcomeEntry(outcome));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.ending." + outcome.id()));
		return true;
	}

	/** A named Masterwork item (W4's id, the {@code named} component, the quality); empty when W4's item is not registered. */
	static ItemStack named(Identifier id, Quality quality) {
		Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);

		if (item == null) {
			OldRepublicMod.LOGGER.warn("{} is not registered; the Kaast reward is skipped", id);
			return ItemStack.EMPTY;
		}

		ItemStack stack = Crafting.withQuality(new ItemStack(item), quality);
		stack.set(M4Components.NAMED, id.getPath());
		return stack;
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!stack.isEmpty()) {
			Rewards.give(p, stack);
		}
	}

	// ---------------------------------------------------------------- the fight's rewards (10.6)

	/**
	 * A Kaast fight ended for this participant. An echo (or any clear after the chapter) is the
	 * owner's daily rematch; another player is a helper; the owner's own first clear is paid here or
	 * at the choice, whichever comes first.
	 */
	static void onDefeated(ServerPlayer p, BossOutcome outcome, UUID owner, boolean firstClear) {
		boolean isOwner = p.getUUID().equals(owner);

		if (!isOwner) {
			helper(p);
		} else if (outcome == BossOutcome.ECHO || Story.get(p).chapter4.stage() == ChapterFourStage.COMPLETE) {
			rematch(p);
		} else {
			firstClear(p);
		}
	}

	/**
	 * The owner's first clear, once (flag {@code c4.kaast_paid}): 4,000 XP, 2 SP, 300 credits and
	 * Resonance {@code kaast} +500 ({@code kaast.*}), the codex and the boss banner.
	 *
	 * @return whether the rewards were given now
	 */
	static boolean firstClear(ServerPlayer p) {
		if (!ChapterFourService.once(p, ChapterFourRules.KAAST_PAID)) {
			return false;
		}

		KaastBalance kb = kaast();
		Progression.addXp(p, kb.ownerXp, "kaast");
		Progression.grantSkillPoints(p, kb.ownerSp);
		Credits.give(p, kb.ownerCredits);
		Progression.grantResonanceMilestone(p, "kaast", kb.resonance);
		Journal.unlockCodex(p, JournalEntries.CODEX_TORVIN_KAAST);
		StoryService.milestone(p, Story.get(p), QuestEffects.LESSON_BOSS);
		return true;
	}

	/**
	 * The camp's stash after the choice (10.6), once: 2 alloy plates, 4 hyperfuel cells, and an
	 * Armstech schematic the player lacks (or 200 credits). Given straight to the player; W6's
	 * {@code kaast_stash} crate is the scene's dressing.
	 */
	static boolean stash(ServerPlayer p) {
		if (!ChapterFourService.once(p, ChapterFourRules.STASH_OPENED)) {
			return false;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		Rewards.give(p, new ItemStack(M4Items.ALLOY_PLATE, Math.max(1, b.stashAlloy)));
		Rewards.give(p, new ItemStack(M4Items.HYPERFUEL_CELL, Math.max(1, b.stashFuel)));
		ItemStack schematic = lackedSchematic(p, Discipline.ARMSTECH, p.getRandom());

		if (schematic.isEmpty()) {
			Credits.give(p, b.stashCredits);
		} else {
			Rewards.give(p, schematic);
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.stash"));
		return true;
	}

	/** A {@code schematic} item for a random item-taught schematic of {@code discipline} the player does not know; empty when there is none. */
	static ItemStack lackedSchematic(ServerPlayer p, Discipline discipline, RandomSource random) {
		List<Schematic> lacked = new ArrayList<>();

		for (Schematic s : Schematics.all()) {
			if (s.discipline().orElse(null) == discipline && s.learned() != Schematic.Learned.AUTO && !Crafting.knows(p, s.id())) {
				lacked.add(s);
			}
		}

		return lacked.isEmpty() ? ItemStack.EMPTY : TheHunt.schematicItem(lacked.get(random.nextInt(lacked.size())).id());
	}

	/**
	 * A helper in another player's duel: 1,000 XP ({@code kaast.helperXp}) and a hunter's
	 * schematic roll, once per in-game day (8.8).
	 */
	static boolean helper(ServerPlayer p) {
		long day = ChapterFourService.day(p);
		StoryData data = Story.get(p);

		if (!ChapterFourRules.helperDue(data, day)) {
			return false;
		}

		data.counters.put(ChapterFourRules.HELPER_DAY, (int) day);
		Story.commit(p, data);
		Progression.addXp(p, (long) kaast().helperXp, "kaast_helper");
		ItemStack roll = lackedSchematic(p, Discipline.ARMSTECH, p.getRandom());
		give(p, roll.isEmpty() ? TheHunt.schematicPiece(p.getRandom()) : roll);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.helper_reward"));
		return true;
	}

	/**
	 * The owner's rematch with the hunter's echo, once per in-game day: 800 XP, 100 credits and a
	 * Mandalorian schematic piece on a 1-in-{@code pity} roll, guaranteed on the {@code pity}-th
	 * clear without one.
	 */
	static boolean rematch(ServerPlayer p) {
		long day = ChapterFourService.day(p);
		StoryData data = Story.get(p);

		if (!ChapterFourRules.rematchDue(data, day)) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.rematch_done_today"));
			return false;
		}

		KaastBalance kb = kaast();
		int pity = Math.max(1, kb.rematchSchematicPity);
		int misses = data.counter(ChapterFourRules.REMATCH_PITY);
		boolean piece = ChapterFourRules.pityDue(misses, p.getRandom().nextInt(pity) == 0, pity);
		data.counters.put(ChapterFourRules.REMATCH_DAY, (int) day);
		data.counters.put(ChapterFourRules.REMATCH_PITY, piece ? 0 : misses + 1);
		Story.commit(p, data);
		Progression.addXp(p, kb.rematchXp, "kaast_echo");
		Credits.give(p, kb.rematchCredits);

		if (piece) {
			give(p, TheHunt.schematicPiece(p.getRandom()));
		}

		return true;
	}

	// ---------------------------------------------------------------- AFTERMATH and the report

	/** AFTERMATH begins: who to report to. */
	static void onAftermath(ServerPlayer p) {
		Path path = Story.get(p).storyPath();
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective.report." + pathId(path)));
	}

	/** AFTERMATH's wait for an Independent: the report goes through the Compass when the journal is held. */
	static void tickAftermath(ServerPlayer p) {
		if (Story.get(p).storyPath() == Path.INDEPENDENT && QuestItems.holdsJournal(p)) {
			report(p);
		}
	}

	/** Whether the report is open: AFTERMATH, not yet reported. */
	static boolean reportReady(StoryData data) {
		return data.chapter4.stage() == ChapterFourStage.AFTERMATH && !data.hasFlag(ChapterFourRules.REPORTED);
	}

	/**
	 * {@code c4:report}: the report to the mentor (Saelen, Sorn; an Independent's Compass). The
	 * mentor reacts to the choice; 800 XP, trust +10, Resonance {@code chapter_four} +300, the
	 * {@code chapter4} banner, entry {@code report_c4}; every destination unlocks; COMPLETE.
	 */
	static void report(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!reportReady(data) || !ChapterFourService.once(p, ChapterFourRules.REPORTED)) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		Path path = data.storyPath();
		KaastOutcome outcome = data.chapter4.duel().kaastOutcome();
		ChapterFourService.mentorLine(p, "report." + (outcome == null ? "spare" : outcome.id()));
		ChapterFourService.xp(p, b.reportXp);

		if (path == Path.JEDI || path == Path.SITH) {
			StoryService.trust(p, MentorRole.of(path), b.reportTrust, "quest.oldrepublic.c4.reason.report");
		}

		Progression.grantResonanceMilestone(p, "chapter_four", b.reportResonance);
		Journal.unlockEntry(p, JournalEntries.REPORT_C4);

		for (Destination d : Destination.values()) {
			TravelService.unlock(p, d);
		}

		ChapterFourService.banner(p, QuestEffects.LESSON_CHAPTER_FOUR, path.ordinal());
		ChapterFourService.enterStage(p, ChapterFourStage.COMPLETE);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.complete"));
	}

	static String pathId(Path path) {
		return path == Path.JEDI ? "jedi" : path == Path.SITH ? "sith" : "independent";
	}

	private static void openUnlessShowing(ServerPlayer p, Identifier dialogue) {
		DialogueSession open = Dialogues.session(p);

		if (open == null || !dialogue.equals(open.dialogue())) {
			Dialogues.open(p, dialogue, null);
		}
	}

	private KaastDuel() {
	}
}

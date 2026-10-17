package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueDefinition;
import com.oldrepublic.dialogue.DialogueLoader;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityBalance;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.KillReputation;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.RepublicSeed;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yielding;
import com.oldrepublic.mission.Commendations;
import com.oldrepublic.mission.MissionBalance;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.MissionDefinition.Kind;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.mission.MissionFeature;
import com.oldrepublic.mission.MissionGuidance;
import com.oldrepublic.mission.MissionItems;
import com.oldrepublic.mission.MissionLoader;
import com.oldrepublic.mission.MissionRules;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.mission.Objectives;
import com.oldrepublic.mission.StoryMissions;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.CaptainFate;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.world.site.SiteKind;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mission game tests (spec 17.1, W2 part B): board offers and their daily rules, the soft cap and
 * contract pay, own-side counting, the contract lead, the seven objectives, story choices and the
 * Jedi Order share, the new yield outcomes and the I2 hand-over, the saved data, the tracker bands,
 * the Calling priorities, the Republic seed and the commendations. The eight kill-reputation and
 * arrival cases that {@code StoryM3GameTests} declares live here as {@code case<Name>} helpers
 * (W2 plan 4.9): each finishes within 40 ticks in the default empty structure and calls
 * {@code helper.succeed()} itself.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); players are listed mock
 * players, removed at the end; fixtures are the lead's ({@code oldrepublic_test:arena_24x7x24}) or the
 * default empty structure; every daily rule gets its day explicitly ({@link #DAY}), never from world
 * time, and Peaceful goes through {@code Objectives.resolveOnArrival}, never world difficulty; every
 * seed is fixed. Mission state lives on the mock players only. The one global change is an inert
 * Attitudes rule registered once that answers only for zombies carrying an
 * {@code oldrepublic_test.mission_*} tag.
 */
public class MissionGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";

	/** The day index every daily rule here is given. */
	private static final int DAY = 40;
	/** The spec's numbers: tests of a rule's shape use the defaults, live play the configured balance. */
	private static final MissionBalance MISSIONS = new MissionBalance();
	private static final IdentityBalance IDENTITY = new IdentityBalance();

	private static final UUID PLAYER_ONE = new UUID(0x0123456789ABCDEFL, 0x0FEDCBA987654321L);
	private static final UUID PLAYER_TWO = new UUID(0x1111222233334444L, 0x5555666677778888L);
	private static final BlockPos TERMINAL = new BlockPos(120, 70, -48);
	private static final BlockPos OTHER_TERMINAL = new BlockPos(-300, 64, 512);

	/** The mission feature's entity tags (see {@code MissionSites}): every member, its mission, and its role. */
	private static final String MISSION_TAG = "oldrepublic.mission";
	private static final String MISSION_PREFIX = MISSION_TAG + ".";
	private static final String ROLE_PREFIX = "oldrepublic.mission_role.";
	private static final String I2 = "i2_border_incident";

	/** The test Attitudes rule's tags: a standing attitude, engagement or provocation for a fixture zombie. */
	private static final String TAG_HOSTILE = "oldrepublic_test.mission_hostile";
	private static final String TAG_WARY = "oldrepublic_test.mission_wary";
	private static final String TAG_NEUTRAL = "oldrepublic_test.mission_neutral";
	private static final String TAG_ENGAGED = "oldrepublic_test.mission_engaged";
	private static final String TAG_PROVOKED = "oldrepublic_test.mission_provoked";

	private static boolean ruleRegistered;

	// ---------------------------------------------------------------- helpers

	private static Component msg(String text) {
		return Component.literal(text);
	}

	/**
	 * Registers the test Attitudes rule once. It has an opinion only about entities carrying one of
	 * the {@code oldrepublic_test.mission_*} tags, so nothing else in the game changes.
	 */
	private static void registerRule() {
		if (ruleRegistered) {
			return;
		}

		ruleRegistered = true;
		Attitudes.register(new Attitudes.Rule() {
			@Override
			public @Nullable Attitude toward(LivingEntity self, Player p) {
				Set<String> tags = self.entityTags();
				return tags.contains(TAG_HOSTILE) ? Attitude.HOSTILE
						: tags.contains(TAG_WARY) ? Attitude.WARY
						: tags.contains(TAG_NEUTRAL) ? Attitude.NEUTRAL
						: null;
			}

			@Override
			public boolean engaged(LivingEntity self, Player p) {
				return self.entityTags().contains(TAG_ENGAGED);
			}

			@Override
			public boolean provokedBy(LivingEntity self, Player p) {
				return self.entityTags().contains(TAG_PROVOKED);
			}
		});
	}

	/** A listed player at (1, 1, 1) on {@code path}. */
	private static ServerPlayer player(GameTestHelper helper, Path path) {
		return player(helper, new BlockPos(1, 1, 1), path);
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos relative, Path path) {
		ServerPlayer p = M3TestPlayers.listed(helper, relative);

		if (path != Path.NONE) {
			IdentityData identity = Identity.get(p);
			identity.path = path;
			Identity.commit(p, identity);
		}

		return p;
	}

	/** A listed player who has awakened (Chapter One's FOCUS stage), so kills can pay XP. */
	private static ServerPlayer awakened(GameTestHelper helper) {
		ServerPlayer p = M3TestPlayers.listed(helper);
		ForceData data = Progression.get(p);
		data.questStage = QuestStage.FOCUS.ordinal();
		Progression.commit(p, data);
		return p;
	}

	/** Moves the player's Chapter Three to {@code stage} directly (no entry logic runs). */
	private static void chapter(ServerPlayer p, ChapterThreeStage stage) {
		StoryData data = Story.get(p);
		data.chapter3 = ChapterThreeRules.withStage(data.chapter3, stage);
		Story.commit(p, data);
	}

	/** A loaded mission definition; fails the test when the datapack did not load it. */
	private static MissionDefinition definition(GameTestHelper helper, String id) {
		MissionDefinition def = MissionLoader.get(id);
		helper.assertTrue(def != null, msg("the mission definition " + id + " must be loaded from data/oldrepublic/mission/"));
		return def;
	}

	/** An active mission, its objective already at the site. */
	private static Active active(String id, String base, Kind kind, String issuer, @Nullable BlockPos site, @Nullable BlockPos board) {
		Active active = new Active(id, base, kind, issuer, DAY, 0x5EEDL, board);
		active.stage = Stage.ACTIVE;
		active.site = site;
		return active;
	}

	private static Active contract(String type, String issuer) {
		return active(type, "", Kind.CONTRACT, issuer, null, TERMINAL);
	}

	/** Makes {@code active} the player's mission. */
	private static void give(ServerPlayer p, Active active) {
		MissionData d = MissionService.get(p);
		d.active = active;
		MissionService.commit(p, d);
	}

	/** Gives the player {@code active} and completes it on {@code day}. */
	private static void completeOn(ServerPlayer p, Active active, @Nullable String choice, int day) {
		give(p, active);
		MissionService.complete(p, choice, day);
	}

	private static MissionData missions(ServerPlayer p) {
		return MissionService.get(p);
	}

	private static int rep(ServerPlayer p, Faction faction) {
		return Identity.reputation(p, faction);
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

	/** Every item the player carries, counted. */
	private static int carried(ServerPlayer p) {
		Inventory inventory = p.getInventory();
		int n = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			n += inventory.getItem(i).getCount();
		}

		return n;
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

	/** A vanilla stand-in for a trooper without AI (it never wanders or attacks), carrying the given test tags. */
	private static Mob fixture(GameTestHelper helper, BlockPos relative, String... tags) {
		Mob mob = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, relative);

		for (String tag : tags) {
			mob.addTag(tag);
		}

		return mob;
	}

	/** One lethal blow from {@code p}: the damage hook takes the hostility snapshot, then kill XP is paid or not. */
	private static void killAsPlayer(GameTestHelper helper, ServerPlayer p, LivingEntity victim) {
		ServerLevel level = helper.getLevel();
		victim.hurtServer(level, level.damageSources().playerAttack(p), 1000f);
		helper.assertTrue(!victim.isAlive(), msg(victim.entityTags() + " must die to the player's blow"));
	}

	/** The alignment change {@code KILLED_SOLDIER} makes with the live balance. */
	private static int killedSoldierShift() {
		IdentityBalance b = Identity.balance();
		return b.contextualAlignment ? b.killedSoldier : 0;
	}

	/** Ties an entity to the player's mission the way the mission feature's own spawns are tagged. */
	private static void tagMember(Entity entity, UUID owner, String missionId, String role) {
		StoryTags.setOwner(entity, owner);
		entity.addTag(MISSION_TAG);
		entity.addTag(MISSION_PREFIX + missionId);
		entity.addTag(ROLE_PREFIX + role);
	}

	/** Option actions of a loaded dialogue ({@code "id arg"} strings). */
	private static Set<String> actions(GameTestHelper helper, String dialogue) {
		DialogueDefinition def = DialogueLoader.get(OldRepublicMod.id(dialogue));
		helper.assertTrue(def != null, msg("the dialogue oldrepublic:" + dialogue + " must be loaded"));
		Set<String> actions = new HashSet<>();

		for (DialogueDefinition.Node node : def.nodes().values()) {
			for (DialogueDefinition.Option option : node.options()) {
				if (option.action() != null) {
					actions.add(option.action());
				}
			}
		}

		return actions;
	}

	private static void action(ServerPlayer p, String id, @Nullable Entity speaker) {
		DialogueActions.Action a = DialogueActions.get(id);

		if (a == null) {
			throw new IllegalStateException("No dialogue action " + id);
		}

		a.run(p, speaker);
	}

	// ---------------------------------------------------------------- board offers and daily rules

	@GameTest(maxTicks = 20)
	public void offerSeedingStable(GameTestHelper helper) {
		List<String> types = MissionRules.BOARD_TYPES;
		List<MissionRules.Offer> first = MissionRules.offers(DAY, PLAYER_ONE, TERMINAL, types, t -> true, i -> false, MISSIONS.offersPerBoard);
		List<MissionRules.Offer> again = MissionRules.offers(DAY, PLAYER_ONE, TERMINAL, types, t -> true, i -> false, MISSIONS.offersPerBoard);
		helper.assertTrue(first.equals(again), msg("the same day, player and terminal always give the same offers"));
		helper.assertTrue(first.size() == 2 && !first.get(0).type().equals(first.get(1).type()), msg("two different contracts per board, got " + first));
		helper.assertTrue(first.get(0).index() == 0 && first.get(1).index() == 1, msg("with every site available the first two of the order are offered"));

		List<String> order = MissionRules.shuffled(MissionRules.boardSeed(DAY, PLAYER_ONE, TERMINAL), types);
		helper.assertTrue(order.size() == types.size() && new HashSet<>(order).equals(new HashSet<>(types)), msg("the seeded order is a permutation"));
		helper.assertTrue(order.get(0).equals(first.get(0).type()) && order.get(1).equals(first.get(1).type()), msg("offers follow the seeded order"));

		Set<List<String>> byDay = new HashSet<>();
		Set<List<String>> byTerminal = new HashSet<>();
		Set<List<String>> byPlayer = new HashSet<>();

		for (int day = 0; day < 32; day++) {
			byDay.add(MissionRules.shuffled(MissionRules.boardSeed(day, PLAYER_ONE, TERMINAL), types));
			byTerminal.add(MissionRules.shuffled(MissionRules.boardSeed(DAY, PLAYER_ONE, TERMINAL.offset(day * 97, 0, -day * 31)), types));
			byPlayer.add(MissionRules.shuffled(MissionRules.boardSeed(DAY, new UUID(day * 0x9E3779B97F4A7C15L, ~day), TERMINAL), types));
		}

		helper.assertTrue(byDay.size() > 1 && byTerminal.size() > 1 && byPlayer.size() > 1,
				msg("the order changes with the day, the terminal and the player (" + byDay.size() + ", " + byTerminal.size() + ", " + byPlayer.size() + ")"));
		helper.assertTrue(MissionRules.boardSeed(DAY, PLAYER_ONE, TERMINAL) != MissionRules.boardSeed(DAY, PLAYER_TWO, TERMINAL)
				&& MissionRules.boardSeed(DAY, PLAYER_ONE, TERMINAL) != MissionRules.boardSeed(DAY, PLAYER_ONE, OTHER_TERMINAL),
				msg("each player and terminal has its own seed"));

		// Only contracts whose site exists are offered, still in the seeded order.
		Set<String> available = Set.of("survey", "clear_camp");
		List<MissionRules.Offer> some = MissionRules.offers(DAY, PLAYER_ONE, TERMINAL, types, available::contains, i -> false, 2);
		List<String> expected = order.stream().filter(available::contains).toList();
		helper.assertTrue(some.size() == 2 && some.get(0).type().equals(expected.get(0)) && some.get(1).type().equals(expected.get(1)),
				msg("only available contracts are offered, in seeded order: " + some));
		helper.assertTrue(MissionRules.offers(DAY, PLAYER_ONE, TERMINAL, types, t -> false, i -> false, 2).isEmpty(), msg("no site, no offer"));

		int variant = MissionRules.briefingVariant(0x5EEDL, 3);
		helper.assertTrue(variant >= 0 && variant < 3 && variant == MissionRules.briefingVariant(0x5EEDL, 3), msg("the briefing variant is stable and in range"));
		List<String> duties = MissionRules.dutyOrder(DAY, PLAYER_ONE);
		helper.assertTrue(duties.equals(MissionRules.dutyOrder(DAY, PLAYER_ONE)) && new HashSet<>(duties).equals(new HashSet<>(MissionRules.DUTY_TYPES)),
				msg("today's duty order is seeded and draws from the duty types"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void usedOffersNotOfferedAgainThatDay(GameTestHelper helper) {
		// Accept: the offer is used for the rest of the day.
		MissionData d = new MissionData();
		MissionRules.rollDay(d, DAY);
		helper.assertTrue(MissionRules.useOffer(d, TERMINAL, 0, MISSIONS.usedOffersMax), msg("accepting marks the offer used"));
		helper.assertTrue(!MissionRules.useOffer(d, TERMINAL, 0, MISSIONS.usedOffersMax), msg("a used offer cannot be accepted again that day"));
		helper.assertTrue(MissionRules.offerUsed(d, TERMINAL, 0) && !MissionRules.offerUsed(d, OTHER_TERMINAL, 0) && !MissionRules.offerUsed(d, TERMINAL, 1),
				msg("an offer is one index at one terminal"));
		List<MissionRules.Offer> shown = MissionRules.offers(DAY, PLAYER_ONE, TERMINAL, MissionRules.BOARD_TYPES, t -> false,
				i -> MissionRules.offerUsed(d, TERMINAL, i), 2);
		helper.assertTrue(shown.size() == 1 && shown.get(0).index() == 0, msg("a used offer keeps its place on the board (shown as taken), never replaced: " + shown));

		ServerPlayer p = player(helper, Path.INDEPENDENT);

		try {
			// Complete: still used.
			MissionData data = missions(p);
			MissionRules.rollDay(data, DAY);
			MissionRules.useOffer(data, TERMINAL, 0, MISSIONS.usedOffersMax);
			MissionService.commit(p, data);
			completeOn(p, contract("clear_camp", Faction.FRONTIER.id()), null, DAY);
			helper.assertTrue(missions(p).active == null && MissionRules.offerUsed(missions(p), TERMINAL, 0), msg("completing does not free the offer"));

			// Abandon: still used, nothing paid.
			data = missions(p);
			MissionRules.useOffer(data, TERMINAL, 1, MISSIONS.usedOffersMax);
			data.active = contract("survey", Faction.REPUBLIC.id());
			MissionService.commit(p, data);
			int republic = rep(p, Faction.REPUBLIC);
			helper.assertTrue(MissionService.abandon(p), msg("an active mission can be abandoned"));
			data = missions(p);
			helper.assertTrue(data.active == null && MissionRules.offerUsed(data, TERMINAL, 1) && MissionRules.offerUsed(data, TERMINAL, 0),
					msg("abandoning frees nothing"));
			helper.assertTrue(rep(p, Faction.REPUBLIC) == republic && data.ownSide(Faction.REPUBLIC.id()) == 0, msg("an abandoned contract pays nothing"));
			helper.assertTrue(!MissionService.abandon(p), msg("nothing left to abandon"));

			// The next day the board is new.
			MissionRules.rollDay(data, DAY + 1);
			helper.assertTrue(data.usedOffers.isEmpty() && MissionRules.useOffer(data, TERMINAL, 0, MISSIONS.usedOffersMax), msg("a new day frees the offers"));
		} finally {
			M3TestPlayers.remove(p);
		}

		// The list is bounded.
		MissionData full = new MissionData();
		MissionRules.rollDay(full, DAY);

		for (int i = 0; i < MissionData.MAX_USED_OFFERS; i++) {
			helper.assertTrue(MissionRules.useOffer(full, TERMINAL.offset(i, 0, 0), 0, MISSIONS.usedOffersMax), msg("offer " + i + " fits"));
		}

		helper.assertTrue(!MissionRules.useOffer(full, OTHER_TERMINAL, 0, MISSIONS.usedOffersMax) && full.usedOffers.size() == MissionData.MAX_USED_OFFERS,
				msg("at most " + MissionData.MAX_USED_OFFERS + " used offers are remembered"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void fullPayThreePerDayThenQuarterXpOnly(GameTestHelper helper) {
		MissionData d = new MissionData();
		MissionRules.rollDay(d, DAY);

		for (int i = 0; i < 3; i++) {
			helper.assertTrue(MissionRules.fullPay(d, MISSIONS), msg("contract " + (i + 1) + " of the day pays in full"));
			d.contractsToday++;
		}

		helper.assertTrue(!MissionRules.fullPay(d, MISSIONS), msg("the fourth contract of the day is past the soft cap"));
		helper.assertTrue(MissionRules.paidXp(400, false, MISSIONS) == 100 && MissionRules.paidXp(290, false, MISSIONS) == 73
				&& MissionRules.paidXp(900, true, MISSIONS) == 900, msg("past the cap XP is a quarter, rounded"));
		MissionRules.rollDay(d, DAY);
		helper.assertTrue(d.contractsToday == 3, msg("the same day keeps its count"));
		MissionRules.rollDay(d, DAY + 1);
		helper.assertTrue(d.contractsToday == 0 && d.contractDay == DAY + 1 && MissionRules.fullPay(d, MISSIONS), msg("a new day pays in full again"));

		// Through the service with the configured balance: Frontier contracts (+rep and the loot) on one day.
		MissionBalance b = MissionFeature.balance();
		ServerPlayer p = player(helper, Path.INDEPENDENT);

		try {
			for (int i = 0; i <= b.fullPayPerDay; i++) {
				boolean full = i < b.fullPayPerDay;
				int level = Progression.get(p).level;
				long xp = totalXp(p);
				int frontier = rep(p, Faction.FRONTIER);
				int items = carried(p);
				completeOn(p, contract("clear_camp", Faction.FRONTIER.id()), null, DAY);
				long paid = totalXp(p) - xp;
				long expected = MissionRules.paidXp(MissionRules.contractXp(level, b), full, b);
				helper.assertTrue(paid == expected, msg("contract " + (i + 1) + " pays " + expected + " XP, got " + paid));
				helper.assertTrue(rep(p, Faction.FRONTIER) - frontier == (full ? b.contractRep : 0),
						msg("contract " + (i + 1) + (full ? " pays the issuer's standing" : " pays no standing past the cap")));
				helper.assertTrue(full ? carried(p) > items : carried(p) == items, msg("contract " + (i + 1) + (full ? " pays the Frontier loot" : " pays no loot past the cap")));
			}

			helper.assertTrue(missions(p).contractsToday == b.fullPayPerDay + 1, msg("every completion counts toward the day"));
			int frontier = rep(p, Faction.FRONTIER);
			completeOn(p, contract("clear_camp", Faction.FRONTIER.id()), null, DAY + 1);
			helper.assertTrue(rep(p, Faction.FRONTIER) - frontier == b.contractRep && missions(p).contractsToday == 1, msg("the next day pays in full again"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void contractXpFormulaCapped900(GameTestHelper helper) {
		int[][] cases = {{0, 250}, {1, 290}, {5, 450}, {10, 650}, {16, 890}, {17, 900}, {20, 900}, {60, 900}, {-3, 250}};

		for (int[] c : cases) {
			int xp = MissionRules.contractXp(c[0], MISSIONS);
			helper.assertTrue(xp == c[1], msg("contract XP at level " + c[0] + " is min(900, 250 + 40 x level) = " + c[1] + ", got " + xp));
		}

		helper.assertTrue(MissionRules.paidXp(MissionRules.contractXp(17, MISSIONS), false, MISSIONS) == 225, msg("the capped 900 is 225 past the soft cap"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void ownSideCountingPerPath(GameTestHelper helper) {
		helper.assertTrue(MissionRules.ownSideKey(Kind.DUTY, MissionDefinition.MENTOR).equals(MissionData.OWN_SIDE_MENTOR)
				&& MissionRules.ownSideKey(Kind.CONTRACT, Faction.REPUBLIC.id()).equals(Faction.REPUBLIC.id()), msg("contracts count by issuer, duties as mentor"));
		Map<String, Integer> ownSide = new HashMap<>();
		ownSide.put(Faction.REPUBLIC.id(), 2);
		ownSide.put(Faction.SITH_EMPIRE.id(), 1);
		ownSide.put(Faction.FRONTIER.id(), 1);
		ownSide.put(MissionData.OWN_SIDE_MENTOR, 1);
		helper.assertTrue(MissionRules.ownSideCount(Path.JEDI, ownSide) == 3, msg("a Jedi counts Republic contracts and duties"));
		helper.assertTrue(MissionRules.ownSideCount(Path.SITH, ownSide) == 2, msg("a Sith counts Imperial contracts and duties"));
		helper.assertTrue(MissionRules.ownSideCount(Path.INDEPENDENT, ownSide) == 5, msg("an Independent counts every contract"));
		helper.assertTrue(MissionRules.ownSideCount(Path.NONE, ownSide) == 0, msg("no path, no rank 3 count"));

		ServerPlayer p = player(helper, Path.JEDI);

		try {
			completeOn(p, contract("survey", Faction.REPUBLIC.id()), null, DAY);
			completeOn(p, contract("survey", Faction.SITH_EMPIRE.id()), null, DAY);
			completeOn(p, contract("clear_camp", Faction.FRONTIER.id()), null, DAY);
			MissionData d = missions(p);
			d.dutyDay = DAY;
			MissionService.commit(p, d);
			completeOn(p, active(MissionRules.WATCH_DUTY, "clear_camp", Kind.DUTY, MissionDefinition.MENTOR, null, null), null, DAY);
			d = missions(p);
			helper.assertTrue(d.ownSide(Faction.REPUBLIC.id()) == 1 && d.ownSide(Faction.SITH_EMPIRE.id()) == 1 && d.ownSide(Faction.FRONTIER.id()) == 1
					&& d.ownSide(MissionData.OWN_SIDE_MENTOR) == 1, msg("each completion counts under its issuer, past the soft cap too: " + d.ownSide));
			helper.assertTrue(Missions.ownSideContracts(p) == 2, msg("the Jedi's own side: the Republic contract and the duty, got " + Missions.ownSideContracts(p)));
			helper.assertTrue(MissionRules.ownSideCount(Path.SITH, d.ownSide) == 2 && MissionRules.ownSideCount(Path.INDEPENDENT, d.ownSide) == 4,
					msg("the same record counts per path"));
			helper.assertTrue(!MissionRules.dutyOpen(d, DAY, 1) && MissionRules.dutyOpen(d, DAY + 1, 1), msg("one mentor duty per day"));
			helper.assertTrue(MissionRules.dutyOpen(d, DAY, 2) && MissionRules.dutiesOn(d, DAY) == 1, msg("missions.dutiesPerDay 2 leaves a second duty"));
			MissionData twice = d.copy();
			twice.dutiesToday = 2;
			helper.assertTrue(!MissionRules.dutyOpen(twice, DAY, 2) && MissionRules.dutiesOn(twice, DAY + 1) == 0, msg("two taken close it; a new day resets"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void contractLeadOnce(GameTestHelper helper) {
		ServerPlayer early = player(helper, Path.INDEPENDENT);
		ServerPlayer p = player(helper, new BlockPos(3, 1, 1), Path.INDEPENDENT);

		try {
			chapter(early, ChapterThreeStage.SUMMONS);
			completeOn(early, contract("clear_camp", Faction.FRONTIER.id()), null, DAY);
			helper.assertTrue(!Story.get(early).chapter3.leads().contains(ChapterThreeRules.LEAD_CONTRACT), msg("a contract before LEADS is no lead"));

			chapter(p, ChapterThreeStage.LEADS);
			completeOn(p, contract("clear_camp", Faction.FRONTIER.id()), null, DAY);
			completeOn(p, contract("survey", Faction.REPUBLIC.id()), null, DAY);
			Set<String> leads = Story.get(p).chapter3.leads();
			helper.assertTrue(leads.contains(ChapterThreeRules.LEAD_CONTRACT) && leads.size() == 1, msg("two contracts during LEADS make one contract lead: " + leads));
		} finally {
			M3TestPlayers.remove(early);
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- objectives

	@GameTest(maxTicks = 20)
	public void eachObjectiveCompletesOnItsTrigger(GameTestHelper helper) {
		Objectives.Trigger[] all = Objectives.Trigger.values();

		// clear and secure: every fighter down.
		for (Objective objective : List.of(Objective.CLEAR, Objective.SECURE)) {
			Objectives.Step step = new Objectives.Step(Stage.ACTIVE, 0, 0, false);

			for (int kill = 1; kill <= 3; kill++) {
				step = Objectives.advance(objective, Objectives.Trigger.KILL, step.stage(), step.progress(), 3, 0, 0, false);
				helper.assertTrue(step.progress() == kill && step.complete() == (kill == 3), msg(objective + " after " + kill + " kills: " + step));
			}

			for (Objectives.Trigger trigger : all) {
				if (trigger != Objectives.Trigger.KILL) {
					helper.assertTrue(!Objectives.advance(objective, trigger, Stage.ACTIVE, 0, 3, 0, 0, false).complete(), msg(objective + " ignores " + trigger));
				}
			}
		}

		// rescue: the guards, then the wounded.
		Objectives.Step rescue = new Objectives.Step(Stage.ACTIVE, 0, 0, false);

		for (int kill = 1; kill <= 3; kill++) {
			rescue = Objectives.advance(Objective.RESCUE, Objectives.Trigger.KILL, rescue.stage(), rescue.progress(), 3, rescue.extra(), 3, false);
		}

		helper.assertTrue(rescue.stage() == Stage.DECIDE && !rescue.complete(), msg("rescue: the guards down leads to the wounded, not completion"));

		for (int tended = 1; tended <= 3; tended++) {
			rescue = Objectives.advance(Objective.RESCUE, Objectives.Trigger.TEND, rescue.stage(), rescue.progress(), 3, rescue.extra(), 3, false);
			helper.assertTrue(rescue.extra() == tended && rescue.complete() == (tended == 3), msg("rescue after " + tended + " tended: " + rescue));
		}

		// recover: taking the item heads back to the officer; the turn-in completes it.
		Objectives.Step recover = Objectives.advance(Objective.RECOVER, Objectives.Trigger.ITEM, Stage.ACTIVE, 0, 1, 0, 0, false);
		helper.assertTrue(recover.stage() == Stage.RETURN && !recover.complete(), msg("recover: the item taken means RETURN"));
		helper.assertTrue(!Objectives.advance(Objective.RECOVER, Objectives.Trigger.KILL, Stage.ACTIVE, 0, 1, 0, 0, false).complete()
				&& Objectives.advance(Objective.RECOVER, Objectives.Trigger.KILL, Stage.ACTIVE, 0, 1, 0, 0, false).stage() == Stage.ACTIVE,
				msg("recover ignores kills"));

		// survey: Sense at the heart.
		helper.assertTrue(Objectives.advance(Objective.SURVEY, Objectives.Trigger.SENSE, Stage.ACTIVE, 0, 1, 0, 0, false).complete(), msg("survey completes on Sense"));
		helper.assertTrue(!Objectives.advance(Objective.SURVEY, Objectives.Trigger.KILL, Stage.ACTIVE, 0, 1, 0, 0, false).complete(), msg("survey ignores kills"));

		// intercept: three kills and the sergeant decided, in either order.
		Objectives.Step intercept = new Objectives.Step(Stage.ACTIVE, 0, 0, false);

		for (int kill = 1; kill <= 3; kill++) {
			intercept = Objectives.advance(Objective.INTERCEPT, Objectives.Trigger.KILL, intercept.stage(), intercept.progress(), 3, 0, 0, false);
		}

		helper.assertTrue(intercept.progress() == 3 && !intercept.complete(), msg("intercept: kills alone leave the sergeant to decide"));
		helper.assertTrue(Objectives.advance(Objective.INTERCEPT, Objectives.Trigger.SERGEANT, Stage.ACTIVE, 3, 3, 0, 0, true).complete(),
				msg("intercept completes when the sergeant is decided after the kills"));
		helper.assertTrue(!Objectives.advance(Objective.INTERCEPT, Objectives.Trigger.SERGEANT, Stage.ACTIVE, 1, 3, 0, 0, true).complete()
				&& Objectives.advance(Objective.INTERCEPT, Objectives.Trigger.KILL, Stage.ACTIVE, 2, 3, 0, 0, true).complete(),
				msg("intercept: the sergeant first, then the last kill completes it"));

		// yield: the sergeant's decision ends it.
		helper.assertTrue(!Objectives.advance(Objective.YIELD, Objectives.Trigger.KILL, Stage.ACTIVE, 0, 3, 0, 0, false).complete()
				&& Objectives.advance(Objective.YIELD, Objectives.Trigger.SERGEANT, Stage.ACTIVE, 1, 3, 0, 0, true).complete(),
				msg("yield completes on the sergeant's decision"));

		// Nothing counts away from the site or after the objective.
		for (Stage stage : List.of(Stage.PLACING, Stage.TRAVEL, Stage.RETURN)) {
			for (Objective objective : Objective.values()) {
				for (Objectives.Trigger trigger : all) {
					Objectives.Step step = Objectives.advance(objective, trigger, stage, 0, 1, 0, 1, true);
					helper.assertTrue(!step.complete() && step.stage() == stage && step.progress() == 0, msg(objective + " does not move at " + stage + " on " + trigger));
				}
			}
		}

		// Targets from the definitions.
		MissionDefinition clear = definition(helper, "clear_camp");
		MissionDefinition secure = definition(helper, "secure_site");
		MissionDefinition r1 = definition(helper, "r1_silent_patrol");
		MissionDefinition intercepts = definition(helper, "patrol_intercept");
		MissionDefinition i2 = definition(helper, I2);
		helper.assertTrue(Objectives.target(clear, Objective.CLEAR, 1, null) == 3 && Objectives.target(clear, Objective.CLEAR, 9, null) == 4,
				msg("clear_camp: 3 raiders, 4 from level 9"));
		helper.assertTrue(Objectives.target(secure, Objective.SECURE, 1, SiteKind.JEDI_RUIN) == 3 && Objectives.target(secure, Objective.SECURE, 1, SiteKind.WRECK_FIELD) == 4,
				msg("secure_site: 3 sentinels at a ruin, 4 scavengers at a wreck"));
		helper.assertTrue(Objectives.target(r1, Objective.RESCUE, 1, null) == 3 && Objectives.extraTarget(r1, Objective.RESCUE) == 3, msg("R1: 3 guards, 3 wounded"));
		helper.assertTrue(Objectives.target(intercepts, Objective.INTERCEPT, 1, null) == 3, msg("patrol_intercept: 3 kills"));
		helper.assertTrue(Objectives.target(i2, Objective.YIELD, 1, null) == 3 && Objectives.target(i2, Objective.YIELD, 11, null) == 4,
				msg("I2: the squad without its sergeant, a rifleman more from level 11"));
		helper.assertTrue(Objectives.counterKey(Objective.SECURE, SiteKind.WRECK_FIELD).endsWith(".wreck"), msg("the wreck counter names scavengers"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- story missions

	@GameTest(maxTicks = 20)
	public void storyChoicesApplyOnce(GameTestHelper helper) {
		MissionDefinition r1 = definition(helper, "r1_silent_patrol");
		MissionDefinition r2 = definition(helper, "r2_credits_from_the_core");
		MissionDefinition i2 = definition(helper, I2);
		MissionDefinition.Choice tend = r1.choices().get(StoryMissions.TEND);
		MissionDefinition.Choice keep = r2.choices().get("keep");
		MissionDefinition.Choice execute = i2.choices().get(StoryMissions.EXECUTE);
		helper.assertTrue(tend != null && keep != null && execute != null, msg("R1 tend, R2 keep and I2 execute are defined"));
		ServerPlayer p = player(helper, Path.INDEPENDENT);
		ServerPlayer q = player(helper, new BlockPos(3, 1, 1), Path.INDEPENDENT);

		try {
			int republic = rep(p, Faction.REPUBLIC);
			helper.assertTrue(StoryMissions.applyChoice(p, r1, tend), msg("the first application applies"));
			helper.assertTrue(rep(p, Faction.REPUBLIC) == republic + tend.rep().get(Faction.REPUBLIC.id()) && Identity.hasChoice(p, "mission.r1.tend"),
					msg("R1 tend: its Republic standing and the recorded choice mission.r1.tend"));
			int afterTend = rep(p, Faction.REPUBLIC);
			int alignment = Identity.alignment(p);
			helper.assertTrue(!StoryMissions.applyChoice(p, r1, tend) && rep(p, Faction.REPUBLIC) == afterTend && Identity.alignment(p) == alignment,
					msg("the same choice never applies twice"));

			int sith = rep(p, Faction.SITH_EMPIRE);
			republic = rep(p, Faction.REPUBLIC);
			alignment = Identity.alignment(p);
			helper.assertTrue(StoryMissions.applyChoice(p, i2, execute), msg("I2 execute applies"));
			helper.assertTrue(rep(p, Faction.SITH_EMPIRE) - sith == execute.rep().get(Faction.SITH_EMPIRE.id())
					&& rep(p, Faction.REPUBLIC) - republic == execute.rep().get(Faction.REPUBLIC.id()), msg("I2 execute: both armies' standing"));
			helper.assertTrue(Identity.alignment(p) - alignment == execute.alignment(), msg("I2 execute: its major shift " + execute.alignment()
					+ ", got " + (Identity.alignment(p) - alignment)));
			sith = rep(p, Faction.SITH_EMPIRE);
			alignment = Identity.alignment(p);
			helper.assertTrue(!StoryMissions.applyChoice(p, i2, execute) && rep(p, Faction.SITH_EMPIRE) == sith && Identity.alignment(p) == alignment,
					msg("I2 execute never applies twice"));

			helper.assertTrue(StoryMissions.applyChoice(p, r2, keep) && !Journal.unlockCodex(p, "vasch_records"), msg("R2 keep opens the Vasch records codex page"));

			// A whole story completion: XP, commendations, the entry, done once.
			MissionBalance b = MissionFeature.balance();
			long xp = totalXp(q);
			completeOn(q, active("r1_silent_patrol", "", Kind.STORY, Faction.REPUBLIC.id(), null, TERMINAL), StoryMissions.TEND, DAY);
			MissionData d = missions(q);
			helper.assertTrue(d.active == null && d.storyDone("r1_silent_patrol") && Identity.hasChoice(q, "mission.r1.tend"), msg("R1 is done with its choice"));
			helper.assertTrue(totalXp(q) - xp == b.storyXp, msg("a story mission pays " + b.storyXp + " XP, got " + (totalXp(q) - xp)));
			helper.assertTrue(count(q, M3Items.REPUBLIC_COMMENDATION) == b.storyCommendations, msg("and the giver's commendations"));
			helper.assertTrue(!d.markStoryDone("r1_silent_patrol") && d.storyDone.size() == 1, msg("a story mission is done once"));
			helper.assertTrue(Story.get(q).chapter3.leads().contains(ChapterThreeRules.LEAD_MISSION_PREFIX + "r1_silent_patrol"),
					msg("a story mission is a Chapter Three lead"));
		} finally {
			M3TestPlayers.remove(p);
			M3TestPlayers.remove(q);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void jediOrderShareOnRepublicGains(GameTestHelper helper) {
		helper.assertTrue(MissionRules.jediShare(Path.JEDI, 100, MISSIONS) == 50 && MissionRules.jediShare(Path.JEDI, 25, MISSIONS) == 12,
				msg("a Jedi earns half of a Republic gain with the Order, rounded down"));
		helper.assertTrue(MissionRules.jediShare(Path.SITH, 100, MISSIONS) == 0 && MissionRules.jediShare(Path.INDEPENDENT, 100, MISSIONS) == 0
				&& MissionRules.jediShare(Path.NONE, 100, MISSIONS) == 0, msg("only a Jedi earns the share"));
		helper.assertTrue(MissionRules.jediShare(Path.JEDI, -100, MISSIONS) == 0 && MissionRules.jediShare(Path.JEDI, 0, MISSIONS) == 0,
				msg("a loss costs no Order standing"));

		MissionBalance b = MissionFeature.balance();
		ServerPlayer jedi = player(helper, Path.JEDI);
		ServerPlayer sith = player(helper, new BlockPos(3, 1, 1), Path.SITH);

		try {
			int order = rep(jedi, Faction.JEDI_ORDER);
			completeOn(jedi, contract("survey", Faction.REPUBLIC.id()), null, DAY);
			int share = MissionRules.jediShare(Path.JEDI, b.contractRep, b);
			helper.assertTrue(rep(jedi, Faction.JEDI_ORDER) - order == share, msg("a Republic contract pays a Jedi " + share + " Order standing"));

			order = rep(jedi, Faction.JEDI_ORDER);
			completeOn(jedi, contract("survey", Faction.SITH_EMPIRE.id()), null, DAY);
			helper.assertTrue(rep(jedi, Faction.JEDI_ORDER) == order, msg("Imperial work pays no Order share"));

			order = rep(jedi, Faction.JEDI_ORDER);
			MissionDefinition r1 = definition(helper, "r1_silent_patrol");
			StoryMissions.applyChoice(jedi, r1, r1.choices().get(StoryMissions.TEND));
			int gain = r1.choices().get(StoryMissions.TEND).rep().get(Faction.REPUBLIC.id());
			helper.assertTrue(rep(jedi, Faction.JEDI_ORDER) - order == MissionRules.jediShare(Path.JEDI, gain, b), msg("a story gain with the Republic pays the share too"));

			order = rep(jedi, Faction.JEDI_ORDER);
			MissionDefinition i2 = definition(helper, I2);
			StoryMissions.applyChoice(jedi, i2, i2.choices().get(StoryMissions.EXECUTE));
			helper.assertTrue(rep(jedi, Faction.JEDI_ORDER) == order, msg("a Republic loss pays no share"));

			order = rep(jedi, Faction.JEDI_ORDER);
			int republic = rep(jedi, Faction.REPUBLIC);
			StoryMissions.applyChoice(jedi, i2, i2.choices().get(StoryMissions.SPARE));
			helper.assertTrue(rep(jedi, Faction.REPUBLIC) > republic && rep(jedi, Faction.JEDI_ORDER) == order,
					msg("a Republic gain from Imperial work (sparing Brannick) pays no share"));

			int sithOrder = rep(sith, Faction.JEDI_ORDER);
			completeOn(sith, contract("survey", Faction.REPUBLIC.id()), null, DAY);
			helper.assertTrue(rep(sith, Faction.JEDI_ORDER) == sithOrder, msg("a Sith doing Republic work earns no Order standing"));
		} finally {
			M3TestPlayers.remove(jedi);
			M3TestPlayers.remove(sith);
		}

		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void yieldDialoguesMapToNewOutcomes(GameTestHelper helper) {
		Set<String> intercept = actions(helper, "yield_intercept");
		Set<String> border = actions(helper, "yield_border");
		helper.assertTrue(intercept.containsAll(List.of("identity:yield_spare", "yield:interrogate", "identity:yield_execute")) && !intercept.contains("yield:hand_over"),
				msg("yield_intercept offers Spare, Interrogate and Execute: " + intercept));
		helper.assertTrue(border.containsAll(List.of("identity:yield_spare", "yield:hand_over", "identity:yield_execute")) && !border.contains("yield:interrogate"),
				msg("yield_border offers Spare, Hand over and Execute: " + border));

		helper.assertTrue(StoryMissions.SPARE.equals(StoryMissions.yieldChoice(YieldOutcome.SPARED))
				&& StoryMissions.HANDED_OVER.equals(StoryMissions.yieldChoice(YieldOutcome.HANDED_OVER))
				&& StoryMissions.EXECUTE.equals(StoryMissions.yieldChoice(YieldOutcome.EXECUTED))
				&& StoryMissions.yieldChoice(YieldOutcome.INTERROGATED) == null && StoryMissions.yieldChoice(YieldOutcome.FLED) == null,
				msg("I2's choices: spare, handed_over, execute; an interrogation or a flight is no choice"));

		// W2's side only: the M2 poacher captain (a Yieldable since M2) stands in for W4's sergeants, whose own
		// yield dialogue, walk-off and binding are W4's to test (TrooperGameTests).
		ServerPlayer p = player(helper, new BlockPos(2, 1, 2), Path.INDEPENDENT);
		BlockPos at = new BlockPos(6, 1, 6);

		try {
			PoacherCaptain questioned = helper.spawnWithNoFreeWill(EnemyEntities.POACHER_CAPTAIN, at);
			PoacherCaptain prisoner = helper.spawnWithNoFreeWill(EnemyEntities.POACHER_CAPTAIN, new BlockPos(10, 1, 6));
			give(p, active("patrol_intercept", "", Kind.CONTRACT, Faction.REPUBLIC.id(), helper.absolutePos(at), TERMINAL));
			tagMember(questioned, p.getUUID(), "patrol_intercept", "sergeant");

			// The chief's and the fence's yield fraction (missions.chiefYield / fenceYield) is a saved per-entity override.
			Yielding.setYieldFraction(prisoner, 0.4);
			Yielding.setYieldFraction(prisoner, MISSIONS.fenceYield);
			long overrides = prisoner.entityTags().stream().filter(tag -> tag.startsWith(Yielding.FRACTION_TAG_PREFIX)).count();
			helper.assertTrue(Math.abs(Yielding.fraction(prisoner, prisoner) - MISSIONS.fenceYield) < 1.0e-3 && overrides == 1,
					msg("the last fraction set wins and is stored once"));
			helper.assertTrue(Math.abs(Yielding.fraction(questioned, questioned) - questioned.yieldHealthFraction()) < 1.0e-6,
					msg("without the override an entity keeps its own fraction"));

			helper.assertTrue(Yielding.forceYield(questioned) && Yielding.forceYield(prisoner) && Yielding.isPending(questioned) && Yielding.isPending(prisoner),
					msg("both yield"));

			action(p, "yield:interrogate", questioned);
			helper.assertTrue(!Yielding.isPending(questioned) && questioned.isAlive(), msg("Interrogate resolves the yield and spares his life"));
			helper.assertTrue(missions(p).pullUntil > helper.getLevel().getGameTime() && questioned.entityTags().contains(ROLE_PREFIX + "escort"),
					msg("the mission hook saw INTERROGATED: the pull toward an unused chamber starts and he is let go"));

			action(p, "yield:hand_over", prisoner);
			helper.assertTrue(!Yielding.isPending(prisoner) && prisoner.isAlive(), msg("Hand over resolves the yield and keeps him alive for the escort"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/**
	 * The I2 hand-over end to end, W2's side: Hand over completes the mission with
	 * {@code handed_over}, marks the prisoner for the escort and tags whatever escorts
	 * {@code Troopers.spawnEscort} brings; once the owner can no longer see them (here the owner
	 * leaves), the prisoner and the escorts are removed. The escorts themselves (two Imperial
	 * troopers that bind him) are W4's, so the test holds with the Phase 0 stub (no escorts) too.
	 */
	@GameTest(structure = ARENA, maxTicks = 160, skyAccess = true)
	public void handOverEscortsDespawn(GameTestHelper helper) {
		helper.assertTrue(!MissionRules.escortGone(0, false, MISSIONS) && !MissionRules.escortGone(39, false, MISSIONS) && MissionRules.escortGone(40, false, MISSIONS),
				msg("out of sight, escorts go after a short walk (40 ticks)"));
		helper.assertTrue(!MissionRules.escortGone(299, true, MISSIONS) && MissionRules.escortGone(300, true, MISSIONS),
				msg("in sight, they go at escortDespawnTicks (300)"));

		ServerPlayer p = player(helper, new BlockPos(2, 1, 2), Path.SITH);
		BlockPos site = helper.absolutePos(new BlockPos(12, 1, 12));
		// The M2 poacher captain stands in for W4's Republic sergeant: the hand-over is W2's flow.
		PoacherCaptain sergeant = helper.spawnWithNoFreeWill(EnemyEntities.POACHER_CAPTAIN, new BlockPos(12, 1, 12));
		List<UUID> escorts = new ArrayList<>();

		try {
			Active active = active(I2, "", Kind.STORY, Faction.SITH_EMPIRE.id(), site, TERMINAL);
			active.target = 3;
			active.progress = 3;
			give(p, active);
			tagMember(sergeant, p.getUUID(), I2, "sergeant");
			helper.assertTrue(sergeant.entityTags().contains(TrooperEntity.TAG_BORDER_INCIDENT), msg("the I2 mission tag is W4's border-incident tag"));
			helper.assertTrue(Yielding.forceYield(sergeant), msg("the sergeant yields"));

			action(p, "yield:hand_over", sergeant);
			MissionData d = missions(p);
			helper.assertTrue(sergeant.isAlive() && sergeant.entityTags().contains(ROLE_PREFIX + "escort"),
					msg("the handed-over sergeant is alive and marked for the escort"));
			helper.assertTrue(d.active == null && d.storyDone(I2) && Identity.hasChoice(p, "mission.i2.handed_over"),
					msg("Hand over completes I2 with the choice mission.i2.handed_over"));

			ServerLevel level = helper.getLevel();
			UUID owner = p.getUUID();

			// Whatever escorts W4 brings are tagged as the owner's mission escorts (none with the Phase 0 stub).
			for (Entity escort : level.getEntities((Entity) null, new AABB(site).inflate(48),
					candidate -> candidate != sergeant && candidate.entityTags().contains(ROLE_PREFIX + "escort") && StoryTags.isOwnedBy(candidate, owner))) {
				helper.assertTrue(escort.entityTags().contains(MISSION_PREFIX + I2), msg("an escort belongs to the I2 mission"));
				escorts.add(escort.getUUID());
			}
		} finally {
			// The owner leaves: nobody of the hand-over is in their sight any more.
			M3TestPlayers.remove(p);
		}

		helper.succeedWhen(() -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(sergeant.isRemoved(), msg("the prisoner is removed once out of the owner's sight"));

			for (UUID id : escorts) {
				Entity escort = level.getEntity(id);
				helper.assertTrue(escort == null || escort.isRemoved(), msg("every escort is removed with him"));
			}
		});
	}

	// ---------------------------------------------------------------- saved data, tracker, Calling

	@GameTest(maxTicks = 20)
	public void missionDataCodecRoundTrip(GameTestHelper helper) {
		MissionData d = new MissionData();
		Active active = new Active(MissionRules.TRIBUTE, "secure_site", Kind.DUTY, MissionDefinition.MENTOR, DAY, -77L, new BlockPos(3, 70, -9));
		active.stage = Stage.DECIDE;
		active.site = new BlockPos(500, 64, -260);
		active.progress = 2;
		active.target = 4;
		active.flags = MissionData.FLAG_SPAWNED | MissionData.FLAG_CHIEF_DONE;
		active.siteKind = SiteKind.WRECK_FIELD.id();
		active.extra = 1;
		d.active = active;
		d.storyDone.add("r1_silent_patrol");
		d.contractDay = DAY;
		d.contractsToday = 2;
		d.boardDay = DAY;
		d.usedOffers.add(new MissionData.UsedOffer(TERMINAL, 1));
		d.ownSide.put(Faction.REPUBLIC.id(), 3);
		d.ownSide.put(MissionData.OWN_SIDE_MENTOR, 1);
		d.dutyDay = DAY;
		d.dutiesToday = 2;
		d.discovered.add(SiteKind.REPUBLIC_OUTPOST.id());
		d.surveyed.add(new BlockPos(10, 60, 10));
		d.pullUntil = 123456L;
		d.cameos.add("settler");

		JsonObject json = MissionData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow().getAsJsonObject();
		MissionData fromJson = MissionData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		Tag nbt = MissionData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
		MissionData fromNbt = MissionData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();

		for (MissionData loaded : List.of(fromJson, fromNbt)) {
			Active a = loaded.active;
			helper.assertTrue(a != null && a.id.equals(MissionRules.TRIBUTE) && a.base.equals("secure_site") && a.kind == Kind.DUTY && a.stage == Stage.DECIDE
					&& active.site.equals(a.site) && a.progress == 2 && a.target == 4 && a.startedDay == DAY && a.seed == -77L
					&& a.issuer.equals(MissionDefinition.MENTOR) && active.board.equals(a.board) && a.flags == active.flags
					&& a.siteKind.equals(SiteKind.WRECK_FIELD.id()) && a.extra == 1, msg("the active mission round-trips"));
			helper.assertTrue(loaded.storyDone.equals(d.storyDone) && loaded.contractDay == DAY && loaded.contractsToday == 2 && loaded.boardDay == DAY
					&& loaded.usedOffers.equals(d.usedOffers) && loaded.ownSide.equals(d.ownSide) && loaded.dutyDay == DAY && loaded.dutiesToday == 2
					&& loaded.discovered.equals(d.discovered) && loaded.surveyed.equals(d.surveyed) && loaded.pullUntil == 123456L
					&& loaded.cameos.equals(d.cameos), msg("every field round-trips"));
		}

		MissionData empty = MissionData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		helper.assertTrue(empty.active == null && empty.storyDone.isEmpty() && empty.contractDay == -1 && empty.boardDay == -1 && empty.dutyDay == -1
				&& empty.usedOffers.isEmpty() && empty.ownSide.isEmpty() && empty.pullUntil == 0L, msg("a save without mission data loads empty"));

		MissionData copy = d.copy();
		copy.active.progress = 4;
		copy.storyDone.add("i1_names");
		copy.ownSide.put(Faction.FRONTIER.id(), 1);
		helper.assertTrue(d.active.progress == 2 && d.storyDone.size() == 1 && !d.ownSide.containsKey(Faction.FRONTIER.id()), msg("a copy shares nothing"));

		// Bounded lists: a crafted save cannot grow them.
		MissionData big = new MissionData();

		for (int i = 0; i < 40; i++) {
			big.storyDone.add("story_" + i);
			big.surveyed.add(new BlockPos(i, 64, 0));
		}

		MissionData bounded = MissionData.CODEC.parse(JsonOps.INSTANCE, MissionData.CODEC.encodeStart(JsonOps.INSTANCE, big).getOrThrow()).getOrThrow();
		helper.assertTrue(bounded.storyDone.size() == MissionData.MAX_STORY_DONE && bounded.surveyed.size() == MissionData.MAX_SURVEYED
				&& bounded.surveyed.get(MissionData.MAX_SURVEYED - 1).equals(new BlockPos(39, 64, 0)), msg("lists load bounded, the newest surveyed hearts kept"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void trackerBandThresholds(GameTestHelper helper) {
		helper.assertTrue(MissionRules.band(0, MISSIONS) == MissionRules.Band.HERE && MissionRules.band(47.9, MISSIONS) == MissionRules.Band.HERE,
				msg("closer than 48 blocks is HERE"));
		helper.assertTrue(MissionRules.band(48, MISSIONS) == MissionRules.Band.NEAR && MissionRules.band(160, MISSIONS) == MissionRules.Band.NEAR,
				msg("48 to 160 blocks is NEAR"));
		helper.assertTrue(MissionRules.band(160.5, MISSIONS) == MissionRules.Band.FAR && MissionRules.band(5000, MISSIONS) == MissionRules.Band.FAR,
				msg("beyond 160 blocks is FAR"));
		helper.assertTrue(MissionRules.Band.FAR.ordinal() == 0 && MissionRules.Band.NEAR.ordinal() == 1 && MissionRules.Band.HERE.ordinal() == 2
				&& MissionRules.Band.NONE.ordinal() == 3, msg("the synced band ordinals never change"));
		helper.assertTrue(Math.abs(MissionRules.yaw(Vec3.ZERO, new Vec3(0, 0, 10))) < 0.01f && Math.abs(MissionRules.yaw(Vec3.ZERO, new Vec3(10, 0, 0)) + 90f) < 0.01f
				&& Math.abs(MissionRules.yaw(Vec3.ZERO, new Vec3(-10, 0, 0)) - 90f) < 0.01f, msg("the chevron's yaw follows Minecraft's convention (0 faces +Z)"));

		ServerPlayer p = player(helper, Path.INDEPENDENT);

		try {
			BlockPos site = helper.absolutePos(new BlockPos(4, 1, 4));
			Active active = active("clear_camp", "", Kind.CONTRACT, Faction.FRONTIER.id(), site, TERMINAL);
			active.stage = Stage.TRAVEL;
			helper.assertTrue(Vec3.atCenterOf(site).equals(MissionService.target(p, active)), msg("travelling, the tracker points at the site"));
			active.stage = Stage.RETURN;
			helper.assertTrue(Vec3.atCenterOf(TERMINAL).equals(MissionService.target(p, active)), msg("returning, it points at the giver"));
			active.stage = Stage.PLACING;
			helper.assertTrue(MissionService.target(p, active) == null, msg("while the site is placed there is no target (band NONE)"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void guidancePriorities(GameTestHelper helper) {
		helper.assertTrue(Guidance.STORY == 100 && Guidance.MISSION == 50 && Guidance.INTERROGATION == 40 && Guidance.OUTPOST == 20,
				msg("story 100, mission 50, interrogation 40, outpost 20"));
		ServerPlayer p = player(helper, Path.INDEPENDENT);

		try {
			helper.assertTrue(MissionGuidance.missionRequest(p) == null, msg("no mission, no mission Calling"));
			helper.assertTrue(MissionGuidance.interrogationRequest(p) == null, msg("no pull, no interrogation Calling"));
			helper.assertTrue(MissionGuidance.outpostRequest(p) == null, msg("before the summons, no outpost Calling"));

			BlockPos site = helper.absolutePos(new BlockPos(6, 1, 6));
			Active active = active("clear_camp", "", Kind.CONTRACT, Faction.FRONTIER.id(), site, TERMINAL);
			active.stage = Stage.TRAVEL;
			give(p, active);
			Guidance.Request mission = MissionGuidance.missionRequest(p);
			helper.assertTrue(mission != null && mission.priority() == Guidance.MISSION && mission.callingVariant() == QuestEffects.CALLING_CONTRACT
					&& mission.target().equals(Vec3.atCenterOf(site)) && mission.source().equals("mission"), msg("the mission Calls toward its site at 50"));

			Guidance.Request current = Guidance.current(p);
			helper.assertTrue(current != null && current.priority() >= Guidance.MISSION, msg("the mission outranks the pulls and the outposts"));

			if (current.priority() == Guidance.MISSION) {
				helper.assertTrue(current.target().equals(mission.target()), msg("at priority 50 the Calling points at the mission"));
			}

			chapter(p, ChapterThreeStage.SUMMONS);
			MissionData d = missions(p);
			d.markDiscovered(SiteKind.REPUBLIC_OUTPOST.id());
			d.markDiscovered(SiteKind.IMPERIAL_OUTPOST.id());
			MissionService.commit(p, d);
			helper.assertTrue(MissionGuidance.outpostRequest(p) == null, msg("both outposts discovered: no outpost Calling"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- identity: the seed and the commendations

	@GameTest(maxTicks = 20)
	public void republicSeedAtSummons(GameTestHelper helper) {
		helper.assertTrue(RepublicSeed.amount(Path.JEDI, IDENTITY) == 100 && RepublicSeed.amount(Path.SITH, IDENTITY) == -100
				&& RepublicSeed.amount(Path.INDEPENDENT, IDENTITY) == 0 && RepublicSeed.amount(Path.NONE, IDENTITY) == 0,
				msg("the seed: Jedi +100, Sith -100, Independent 0"));
		IdentityBalance b = Identity.balance();
		Map<Path, ServerPlayer> players = new HashMap<>();

		try {
			int x = 1;

			for (Path path : List.of(Path.JEDI, Path.SITH, Path.INDEPENDENT)) {
				ServerPlayer p = player(helper, new BlockPos(x, 1, 1), path);
				players.put(path, p);
				x += 2;
				int before = rep(p, Faction.REPUBLIC);
				helper.assertTrue(RepublicSeed.apply(p) && Identity.hasChoice(p, RepublicSeed.CHOICE), msg(path + ": the seed applies once at the summons"));
				helper.assertTrue(rep(p, Faction.REPUBLIC) - before == RepublicSeed.amount(path, b), msg(path + ": Republic standing seeded by path"));
				int seeded = rep(p, Faction.REPUBLIC);
				helper.assertTrue(!RepublicSeed.apply(p) && rep(p, Faction.REPUBLIC) == seeded, msg(path + ": and never again"));
			}
		} finally {
			players.values().forEach(M3TestPlayers::remove);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void commendationsOf(GameTestHelper helper) {
		ItemStack republic = Commendations.of(Faction.REPUBLIC, 3);
		ItemStack imperial = Commendations.of(Faction.SITH_EMPIRE, 1);
		helper.assertTrue(republic.is(M3Items.REPUBLIC_COMMENDATION) && republic.getCount() == 3, msg("three Republic commendations"));
		helper.assertTrue(imperial.is(M3Items.IMPERIAL_COMMENDATION) && imperial.getCount() == 1, msg("one Imperial commendation"));
		helper.assertTrue(Commendations.of(Faction.FRONTIER, 2).isEmpty() && Commendations.of(Faction.JEDI_ORDER, 2).isEmpty(),
				msg("only the armies have commendations"));

		MissionBalance b = MissionFeature.balance();
		ServerPlayer p = player(helper, Path.INDEPENDENT);

		try {
			completeOn(p, contract("survey", Faction.SITH_EMPIRE.id()), null, DAY);
			helper.assertTrue(count(p, M3Items.IMPERIAL_COMMENDATION) == b.contractCommendations, msg("an Imperial contract pays the Imperial commendation"));

			// R2 sold to the Empire: Imperial commendations instead of the giver's, and the record handed over.
			p.getInventory().add(new ItemStack(MissionItems.ARCHIVE_RECORD));
			int imperialBefore = count(p, M3Items.IMPERIAL_COMMENDATION);
			completeOn(p, active("r2_credits_from_the_core", "", Kind.STORY, Faction.REPUBLIC.id(), null, TERMINAL), "sell", DAY);
			helper.assertTrue(count(p, M3Items.IMPERIAL_COMMENDATION) - imperialBefore == 3 && count(p, M3Items.REPUBLIC_COMMENDATION) == 0,
					msg("R2 sell pays 3 Imperial commendations and none of the Republic's"));
			helper.assertTrue(count(p, MissionItems.ARCHIVE_RECORD) == 0, msg("the archive record is handed over"));

			// I1's book burned: no commendations.
			p.getInventory().add(new ItemStack(MissionItems.FENCE_BOOK));
			int imperial2 = count(p, M3Items.IMPERIAL_COMMENDATION);
			completeOn(p, active("i1_names", "", Kind.STORY, Faction.SITH_EMPIRE.id(), null, TERMINAL), StoryMissions.BURN, DAY);
			helper.assertTrue(count(p, M3Items.IMPERIAL_COMMENDATION) == imperial2 && count(p, MissionItems.FENCE_BOOK) == 0,
					msg("I1 burn pays no commendations and the book is gone"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- StoryM3GameTests delegations (W2 plan 4.9)

	/** Arriving at a site on Peaceful resolves the fighting at once; elsewhere everyone spawns (6.9). Pure: never world difficulty. */
	public static void casePeacefulResolvesCombatObjectivesOnArrival(GameTestHelper helper) {
		for (Objective objective : Objective.values()) {
			Objectives.Arrival normal = Objectives.resolveOnArrival(objective, 3, false);
			helper.assertTrue(normal.stage() == Stage.ACTIVE && normal.progress() == 0 && !normal.complete() && !normal.yieldAtOnce() && !normal.guardsDone()
					&& normal.spawnFighters() == (objective != Objective.SURVEY), msg(objective + " off Peaceful: everyone spawns and the objective counts"));
		}

		Objectives.Arrival clear = Objectives.resolveOnArrival(Objective.CLEAR, 3, true);
		Objectives.Arrival secure = Objectives.resolveOnArrival(Objective.SECURE, 4, true);
		helper.assertTrue(clear.complete() && clear.progress() == 3 && !clear.spawnFighters() && secure.complete() && secure.progress() == 4 && !secure.spawnFighters(),
				msg("Peaceful clear and secure complete on arrival"));

		Objectives.Arrival rescue = Objectives.resolveOnArrival(Objective.RESCUE, 3, true);
		helper.assertTrue(rescue.stage() == Stage.DECIDE && rescue.progress() == 3 && !rescue.spawnFighters() && !rescue.complete(),
				msg("Peaceful R1 goes straight to the wounded"));
		Objectives.Step tended = new Objectives.Step(rescue.stage(), rescue.progress(), 0, false);

		for (int i = 0; i < 3; i++) {
			tended = Objectives.advance(Objective.RESCUE, Objectives.Trigger.TEND, tended.stage(), tended.progress(), 3, tended.extra(), 3, false);
		}

		helper.assertTrue(tended.complete(), msg("and tending the wounded still completes it"));

		Objectives.Arrival recover = Objectives.resolveOnArrival(Objective.RECOVER, 1, true);
		helper.assertTrue(recover.guardsDone() && !recover.spawnFighters() && !recover.complete()
				&& Objectives.advance(Objective.RECOVER, Objectives.Trigger.ITEM, recover.stage(), recover.progress(), 1, 0, 0, true).stage() == Stage.RETURN,
				msg("Peaceful recover: the chief is gone, the item can be taken"));

		for (Objective objective : List.of(Objective.INTERCEPT, Objective.YIELD)) {
			Objectives.Arrival squad = Objectives.resolveOnArrival(objective, 3, true);
			helper.assertTrue(squad.spawnFighters() && squad.yieldAtOnce() && squad.progress() == 3 && !squad.complete(),
					msg(objective + " on Peaceful: the squad stands down and the sergeant yields at once"));
			helper.assertTrue(Objectives.advance(objective, Objectives.Trigger.SERGEANT, squad.stage(), squad.progress(), 3, 0, 0, true).complete(),
					msg(objective + " on Peaceful completes with the sergeant's fate"));
		}

		Objectives.Arrival survey = Objectives.resolveOnArrival(Objective.SURVEY, 1, true);
		helper.assertTrue(!survey.complete() && !survey.spawnFighters() && survey.equals(Objectives.resolveOnArrival(Objective.SURVEY, 1, false)),
				msg("a survey is never combat: Peaceful changes nothing"));
		helper.succeed();
	}

	/** A trooper that counted as hostile: kill XP, -10 with its army, no alignment. */
	public static void caseHostileKillCostsTenAndPaysXp(GameTestHelper helper) {
		KillReputation.Result rule = KillReputation.decide(true, 0, IDENTITY);
		helper.assertTrue(rule.repDelta() == -10 && rule.paysXp() && !rule.killedSoldier(), msg("the rule: -10, XP, no KILLED_SOLDIER"));
		registerRule();
		ServerPlayer p = awakened(helper);

		try {
			LivingEntity trooper = fixture(helper, new BlockPos(3, 1, 3), TAG_HOSTILE);
			long xp = totalXp(p);
			killAsPlayer(helper, p, trooper);
			boolean hostile = Attitudes.hostileSnapshot(trooper, p);
			helper.assertTrue(hostile, msg("a HOSTILE trooper counted as hostile when the blow landed"));
			helper.assertTrue(totalXp(p) > xp, msg("its kill pays XP"));
			int rep = rep(p, Faction.REPUBLIC);
			int alignment = Identity.alignment(p);
			KillReputation.Result result = KillReputation.apply(p, Faction.REPUBLIC, hostile, DAY);
			helper.assertTrue(result.paysXp() && !result.killedSoldier(), msg("a hostile kill is no KILLED_SOLDIER"));
			helper.assertTrue(rep(p, Faction.REPUBLIC) - rep == Identity.balance().hostileKillRep, msg("it costs hostileKillRep with the army"));
			helper.assertTrue(Identity.alignment(p) == alignment, msg("and no alignment"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** A trooper that did not count as hostile: no XP, -40, and KILLED_SOLDIER. */
	public static void caseNonHostileKillCostsFortyAndKilledSoldier(GameTestHelper helper) {
		KillReputation.Result rule = KillReputation.decide(false, 0, IDENTITY);
		helper.assertTrue(rule.repDelta() == -40 && !rule.paysXp() && rule.killedSoldier(), msg("the rule: -40, no XP, KILLED_SOLDIER"));
		registerRule();
		ServerPlayer p = awakened(helper);

		try {
			LivingEntity trooper = fixture(helper, new BlockPos(3, 1, 3), TAG_NEUTRAL);
			long xp = totalXp(p);
			killAsPlayer(helper, p, trooper);
			boolean hostile = Attitudes.hostileSnapshot(trooper, p);
			helper.assertTrue(!hostile && totalXp(p) == xp, msg("a NEUTRAL trooper's kill is not hostile and pays no XP"));
			int rep = rep(p, Faction.REPUBLIC);
			int alignment = Identity.alignment(p);
			KillReputation.Result result = KillReputation.apply(p, Faction.REPUBLIC, hostile, DAY);
			helper.assertTrue(result.killedSoldier() && !result.paysXp(), msg("it is KILLED_SOLDIER"));
			helper.assertTrue(rep(p, Faction.REPUBLIC) - rep == Identity.balance().neutralKillRep, msg("it costs neutralKillRep with the army"));
			helper.assertTrue(Identity.alignment(p) - alignment == killedSoldierShift(), msg("and the minor KILLED_SOLDIER shift"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** Striking first makes the trooper fight back, but its death still counts as not hostile. */
	public static void caseProvokeThenKillCountsAsNotHostile(GameTestHelper helper) {
		registerRule();
		ServerPlayer p = awakened(helper);

		try {
			LivingEntity trooper = fixture(helper, new BlockPos(3, 1, 3), TAG_NEUTRAL, TAG_PROVOKED);
			helper.assertTrue(Attitudes.provokedBy(trooper, p) && !Attitudes.countsAsHostile(p, trooper), msg("provoked, and still not hostile"));
			long xp = totalXp(p);
			killAsPlayer(helper, p, trooper);
			boolean hostile = Attitudes.hostileSnapshot(trooper, p);
			helper.assertTrue(!hostile && totalXp(p) == xp, msg("killing a trooper you provoked pays no XP"));
			int rep = rep(p, Faction.SITH_EMPIRE);
			int alignment = Identity.alignment(p);
			KillReputation.Result result = KillReputation.apply(p, Faction.SITH_EMPIRE, hostile, DAY);
			helper.assertTrue(result.killedSoldier() && rep(p, Faction.SITH_EMPIRE) - rep == Identity.balance().neutralKillRep,
					msg("it costs neutralKillRep and is KILLED_SOLDIER"));
			helper.assertTrue(Identity.alignment(p) - alignment == killedSoldierShift(), msg("with the minor dark shift"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** A Wary trooper that fired first (engaged) is self-defence: hostile, XP, -10, no alignment. */
	public static void caseWaryEngagedKillIsSelfDefence(GameTestHelper helper) {
		registerRule();
		ServerPlayer p = awakened(helper);

		try {
			LivingEntity trooper = fixture(helper, new BlockPos(3, 1, 3), TAG_WARY, TAG_ENGAGED);
			helper.assertTrue(Attitudes.toward(trooper, p) == Attitude.WARY && Attitudes.countsAsHostile(p, trooper), msg("Wary but engaged counts as hostile"));
			long xp = totalXp(p);
			killAsPlayer(helper, p, trooper);
			boolean hostile = Attitudes.hostileSnapshot(trooper, p);
			helper.assertTrue(hostile && totalXp(p) > xp, msg("self-defence pays kill XP"));
			int rep = rep(p, Faction.REPUBLIC);
			int alignment = Identity.alignment(p);
			KillReputation.Result result = KillReputation.apply(p, Faction.REPUBLIC, hostile, DAY);
			helper.assertTrue(!result.killedSoldier() && rep(p, Faction.REPUBLIC) - rep == Identity.balance().hostileKillRep && Identity.alignment(p) == alignment,
					msg("self-defence costs hostileKillRep and no alignment"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** The daily cap (200 per army and day) covers only the -10 losses; -40 kills are never capped. */
	public static void caseDailyCapCoversOnlyTenLosses(GameTestHelper helper) {
		int taken = 0;
		int total = 0;

		for (int i = 0; i < 25; i++) {
			KillReputation.Result r = KillReputation.decide(true, taken, IDENTITY);
			total += r.repDelta();
			taken = r.cappedToday();
		}

		helper.assertTrue(total == -200 && taken == 200, msg("25 hostile kills cost 200 in a day, got " + total));
		helper.assertTrue(KillReputation.decide(true, 195, IDENTITY).repDelta() == -5, msg("the last loss is cut to the cap"));
		helper.assertTrue(KillReputation.decide(false, 200, IDENTITY).repDelta() == -40, msg("a non-hostile kill costs 40 past the cap"));

		IdentityBalance b = Identity.balance();
		int step = Math.max(1, -b.hostileKillRep);
		int kills = Math.min(1000, b.killRepDailyCap / step + 2);
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			int republic = rep(p, Faction.REPUBLIC);

			for (int i = 0; i < kills; i++) {
				KillReputation.apply(p, Faction.REPUBLIC, true, DAY);
			}

			int capped = Math.min(b.killRepDailyCap, kills * Math.max(0, -b.hostileKillRep));
			helper.assertTrue(republic - rep(p, Faction.REPUBLIC) == capped, msg("hostile kills stop costing at the cap: lost " + (republic - rep(p, Faction.REPUBLIC))));
			IdentityData data = Identity.get(p);
			helper.assertTrue(data.killRepDay == DAY && data.killRepToday.getOrDefault(Faction.REPUBLIC.id(), 0) == capped, msg("today's capped losses are kept"));

			int before = rep(p, Faction.REPUBLIC);
			KillReputation.apply(p, Faction.REPUBLIC, false, DAY);
			helper.assertTrue(rep(p, Faction.REPUBLIC) - before == b.neutralKillRep, msg("a non-hostile kill still costs past the cap"));

			int sith = rep(p, Faction.SITH_EMPIRE);
			KillReputation.apply(p, Faction.SITH_EMPIRE, true, DAY);
			helper.assertTrue(rep(p, Faction.SITH_EMPIRE) - sith == b.hostileKillRep, msg("each army has its own cap"));

			before = rep(p, Faction.REPUBLIC);
			KillReputation.apply(p, Faction.REPUBLIC, true, DAY + 1);
			helper.assertTrue(rep(p, Faction.REPUBLIC) - before == b.hostileKillRep && Identity.get(p).killRepDay == DAY + 1, msg("a new day starts a new cap"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** No kill ever raises reputation, whatever the balance file says. */
	public static void caseKillsNeverGainReputation(GameTestHelper helper) {
		IdentityBalance perverse = new IdentityBalance();
		perverse.hostileKillRep = 10;
		perverse.neutralKillRep = 40;
		perverse.killRepDailyCap = -5;

		for (IdentityBalance b : List.of(IDENTITY, perverse)) {
			for (boolean hostile : new boolean[] {true, false}) {
				for (int taken : new int[] {-50, 0, 5, 195, 200, 500}) {
					KillReputation.Result r = KillReputation.decide(hostile, taken, b);
					helper.assertTrue(r.repDelta() <= 0 && r.cappedToday() >= 0, msg("hostile=" + hostile + ", taken=" + taken + ": " + r.repDelta() + " never gains"));
				}
			}
		}

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			Identity.setReputation(p, Faction.REPUBLIC, 500);
			Identity.setReputation(p, Faction.SITH_EMPIRE, -500);

			for (int i = 0; i < 3; i++) {
				for (Faction army : List.of(Faction.REPUBLIC, Faction.SITH_EMPIRE)) {
					int before = rep(p, army);
					KillReputation.apply(p, army, i % 2 == 0, DAY);
					helper.assertTrue(rep(p, army) <= before, msg(army.id() + ": a kill never raises standing"));
				}
			}
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** Hessa Dray's Chapter Two fate picks who runs the R2 cache; the settler cameo plays once. */
	public static void caseCameoFlagsChooseR2Chief(GameTestHelper helper) {
		for (CaptainFate fate : CaptainFate.values()) {
			MissionRules.Chief expected = fate == CaptainFate.SPARED ? MissionRules.Chief.HESSA
					: fate == CaptainFate.EXECUTED ? MissionRules.Chief.LIEUTENANT
					: MissionRules.Chief.RELL;
			helper.assertTrue(MissionRules.r2Chief(fate) == expected, msg(fate + " gives " + expected + ", got " + MissionRules.r2Chief(fate)));
		}

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			for (CaptainFate fate : List.of(CaptainFate.SPARED, CaptainFate.EXECUTED, CaptainFate.FLED)) {
				StoryData data = Story.get(p);
				data.captain = fate.ordinal();
				Story.commit(p, data);
				helper.assertTrue(MissionRules.r2Chief(Story.get(p).captainFate()) == MissionRules.r2Chief(fate), msg("the saved fate " + fate + " is read back"));
			}

			MissionData d = MissionService.get(p);
			helper.assertTrue(d.markCameo("settler") && !d.markCameo("settler") && d.cameos.size() == 1, msg("a cameo plays once"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}
}

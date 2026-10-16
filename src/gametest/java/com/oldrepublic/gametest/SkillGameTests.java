package com.oldrepublic.gametest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityCosts;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.skill.CombatTracker;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillContext;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillRules.LearnResult;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTreeSet;
import com.oldrepublic.skill.SkillTrees;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * Server game tests of the skills feature: tree data, the purchase rules, group switches, dormant
 * nodes, respec, slots and the combat lock, the Force Push migration and the energy modifiers.
 * Pure rule tests run {@link SkillRules} on the real trees with hand-made contexts; service tests
 * use a fake player.
 */
public class SkillGameTests {
	private static final Set<String> ALL_TREES = Set.of(SkillIds.TREE_UNIVERSAL, SkillIds.TREE_SABER, SkillIds.TREE_JEDI, SkillIds.TREE_SITH);

	private static SkillContext ctx(int level, int points, int alignment, Path path) {
		return new SkillContext(true, level, points, alignment, path, ALL_TREES, false, false);
	}

	private static SkillTreeSet trees(GameTestHelper helper) {
		SkillTreeSet trees = SkillTrees.server();
		helper.assertTrue(!trees.nodes().isEmpty(), Component.literal("skill trees did not load"));
		return trees;
	}

	private static void expect(GameTestHelper helper, LearnResult expected, SkillRules.LearnCheck check, String what) {
		helper.assertTrue(check.result() == expected, Component.literal(what + ": expected " + expected + ", got " + check.result()));
	}

	/** An awakened fake player at {@code level} with {@code points} skill points who knows Force Push. */
	private static FakePlayer player(GameTestHelper helper, int level, int points) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "skill_test"));
		ForceData force = Progression.get(player);
		force.questStage = QuestStage.COMPLETE.ordinal();
		force.level = level;
		force.skillPoints = points;
		force.abilities.add(SkillIds.FORCE_PUSH);
		Progression.commit(player, force);
		SkillService.migrate(player);
		SkillService.refresh(player);
		return player;
	}

	// ---------------------------------------------------------------- data

	@GameTest
	public void everyTreeLoadsWithoutErrors(GameTestHelper helper) {
		SkillTreeSet trees = trees(helper);

		for (String id : ALL_TREES) {
			helper.assertTrue(trees.tree(id) != null, Component.literal("missing tree " + id));
		}

		helper.assertTrue(trees.errors().isEmpty(), Component.literal("tree errors: " + trees.errors()));

		for (SkillNode node : trees.nodes()) {
			if (node.isAbility()) {
				String ability = node.ability() != null ? node.ability() : node.id();
				helper.assertTrue(Abilities.get(ability) != null, Component.literal("node " + node.id() + " names unregistered ability " + ability));
			}
		}

		helper.succeed();
	}

	@GameTest
	public void cyclesAndBadRequirementsAreReported(GameTestHelper helper) {
		SkillTreeSet cyclic = SkillTrees.fromJson(Map.of("t", """
				{"order": 0, "visible": "push", "nodes": [
				  {"id": "a", "kind": "passive", "cost": [1], "min_level": [1], "requires": ["b"], "pos": [0, 0]},
				  {"id": "b", "kind": "passive", "cost": [1], "min_level": [1], "requires": ["a"], "pos": [1, 0]},
				  {"id": "c", "kind": "passive", "cost": [1], "min_level": [1], "requires": ["nowhere"], "pos": [2, 0]}
				]}"""));
		helper.assertTrue(cyclic.errors().stream().anyMatch(e -> e.toLowerCase().contains("cycle")), Component.literal("cycle not reported: " + cyclic.errors()));
		helper.assertTrue(cyclic.errors().size() >= 2, Component.literal("unknown requirement not reported: " + cyclic.errors()));
		helper.succeed();
	}

	@GameTest
	public void skillDataRoundTrips(GameTestHelper helper) {
		SkillData data = new SkillData();
		data.nodes.put("deep_reserves", 2);
		data.spent.put("deep_reserves", 2);
		data.granted.add("force_push");
		data.slots.set(1, "force_pull");
		data.openSlots = 4;
		data.activeForm = "form_soresu";
		data.lastRespec = 1234L;
		data.migrated = true;
		var json = SkillData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		SkillData back = SkillData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.rank("deep_reserves") == 2 && back.spentTotal() == 2 && back.isGranted("force_push"), Component.literal("nodes lost: " + json));
		helper.assertTrue("force_pull".equals(back.slot(1)) && back.openSlots == 4 && "form_soresu".equals(back.activeForm), Component.literal("slots lost: " + json));
		helper.assertTrue(back.lastRespec == 1234L && back.migrated, Component.literal("respec state lost: " + json));
		helper.succeed();
	}

	// ---------------------------------------------------------------- purchase rules

	@GameTest
	public void purchaseValidationMatrix(GameTestHelper helper) {
		SkillTreeSet trees = trees(helper);
		int penalty = new SkillBalance().switchPenalty;
		SkillData none = new SkillData();
		SkillData push = new SkillData();
		push.nodes.put(SkillIds.FORCE_PUSH, 1);
		push.granted.add(SkillIds.FORCE_PUSH);

		SkillContext asleep = new SkillContext(false, 10, 10, 0, Path.NONE, ALL_TREES, false, false);
		expect(helper, LearnResult.NOT_AWAKENED, SkillRules.check(trees, push, asleep, "force_pull", penalty), "not awakened");
		SkillContext noTabs = new SkillContext(true, 10, 10, 0, Path.NONE, Set.of(SkillIds.TREE_UNIVERSAL), false, false);
		expect(helper, LearnResult.TAB_HIDDEN, SkillRules.check(trees, push, noTabs, "centered_mind", penalty), "hidden tab");
		expect(helper, LearnResult.GRANTED_ONLY, SkillRules.check(trees, push, ctx(10, 10, 0, Path.INDEPENDENT), SkillIds.UNBOUND, penalty), "granted only");
		expect(helper, LearnResult.LEVEL_TOO_LOW, SkillRules.check(trees, push, ctx(3, 10, 0, Path.NONE), "force_pull", penalty), "level");
		expect(helper, LearnResult.MISSING_REQUIREMENT, SkillRules.check(trees, none, ctx(4, 10, 0, Path.NONE), "force_pull", penalty), "prerequisite");
		expect(helper, LearnResult.NOT_ENOUGH_POINTS, SkillRules.check(trees, push, ctx(4, 1, 0, Path.NONE), "force_pull", penalty), "points");
		SkillRules.LearnCheck ok = SkillRules.check(trees, push, ctx(4, 2, 0, Path.NONE), "force_pull", penalty);
		expect(helper, LearnResult.OK, ok, "force_pull at level 4 with 2 points");
		helper.assertTrue(ok.cost() == 2 && ok.nextRank() == 1, Component.literal("force_pull should cost 2 for rank 1, got " + ok.cost()));
		expect(helper, LearnResult.ALIGNMENT, SkillRules.check(trees, none, ctx(5, 5, 10, Path.JEDI), "centered_mind", penalty), "alignment +10 < +15");
		expect(helper, LearnResult.OK, SkillRules.check(trees, none, ctx(5, 5, 15, Path.SITH), "centered_mind", penalty), "alignment +15 on any path");
		expect(helper, LearnResult.ALIGNMENT, SkillRules.check(trees, none, ctx(5, 5, -10, Path.SITH), "fury", penalty), "fury needs -15");

		SkillData reserves = new SkillData();
		reserves.nodes.put(SkillIds.DEEP_RESERVES, 3);
		expect(helper, LearnResult.MAXED, SkillRules.check(trees, reserves, ctx(20, 10, 0, Path.NONE), SkillIds.DEEP_RESERVES, penalty), "maxed");
		reserves.nodes.put(SkillIds.DEEP_RESERVES, 2);
		SkillRules.LearnCheck third = SkillRules.check(trees, reserves, ctx(9, 10, 0, Path.NONE), SkillIds.DEEP_RESERVES, penalty);
		helper.assertTrue(third.ok() && third.cost() == 2 && third.nextRank() == 3, Component.literal("deep_reserves rank 3 costs 2 at level 9: " + third));
		expect(helper, LearnResult.LEVEL_TOO_LOW, SkillRules.check(trees, reserves, ctx(8, 10, 0, Path.NONE), SkillIds.DEEP_RESERVES, penalty), "rank 3 level");

		// Path: a synthetic tree, since every real path node is granted only.
		SkillTreeSet pathTree = SkillTrees.fromJson(Map.of("p", """
				{"order": 0, "visible": "push", "nodes": [
				  {"id": "jedi_only", "kind": "passive", "path": "jedi", "cost": [1], "min_level": [1], "pos": [0, 0]}
				]}"""));
		SkillContext pathCtx = new SkillContext(true, 5, 5, 0, Path.SITH, Set.of("p"), false, false);
		expect(helper, LearnResult.WRONG_PATH, SkillRules.check(pathTree, none, pathCtx, "jedi_only", penalty), "wrong path");
		helper.succeed();
	}

	@GameTest
	public void groupSwitchCostsPenaltyAndRemovesSibling(GameTestHelper helper) {
		trees(helper);
		FakePlayer player = player(helper, 3, 5);
		helper.assertTrue(SkillService.learn(player, "push_repulse_wave") == LearnResult.OK, Component.literal("first branch refused"));
		helper.assertTrue(Progression.get(player).skillPoints == 4, Component.literal("a first branch costs 1"));
		helper.assertTrue("repulse_wave".equals(Progression.get(player).pushBranch), Component.literal("pushBranch mirror not written"));

		helper.assertTrue(SkillService.learn(player, "push_focused_blast") == LearnResult.OK, Component.literal("switch refused"));
		SkillData data = SkillService.data(player);
		helper.assertTrue(Progression.get(player).skillPoints == 1, Component.literal("a switch costs 1 + 2, left " + Progression.get(player).skillPoints));
		helper.assertTrue(!data.owns("push_repulse_wave") && data.owns("push_focused_blast"), Component.literal("sibling not removed"));
		helper.assertTrue(data.spentTotal() == 1, Component.literal("the old branch must not stay as spent points: " + data.spent));
		helper.assertTrue("focused_blast".equals(Progression.get(player).pushBranch), Component.literal("mirror not updated"));

		helper.assertTrue(SkillService.learn(player, "push_deflecting_pulse") == LearnResult.NOT_ENOUGH_POINTS, Component.literal("switch with 1 point allowed"));
		helper.succeed();
	}

	@GameTest
	public void dormantOnAlignmentLossAndBack(GameTestHelper helper) {
		SkillTreeSet trees = trees(helper);
		SkillData data = new SkillData();
		data.nodes.put("centered_mind", 1);
		data.nodes.put("calm_focus", 1);
		SkillBalance b = new SkillBalance();

		SkillContext light = ctx(9, 0, 45, Path.JEDI);
		helper.assertTrue(SkillRules.active(trees, data, light, "calm_focus"), Component.literal("calm_focus active at +45"));
		helper.assertTrue(SkillRules.statMods(trees, data, light, b).maxEnergyPct() == b.calmFocusMaxEnergyPct, Component.literal("calm_focus bonus missing"));

		SkillContext drifted = ctx(9, 0, 20, Path.JEDI);
		helper.assertTrue(!SkillRules.active(trees, data, drifted, "calm_focus") && SkillRules.active(trees, data, drifted, "centered_mind"),
				Component.literal("at +20 calm_focus is dormant, centered_mind active"));
		helper.assertTrue(SkillRules.statMods(trees, data, drifted, b).maxEnergyPct() == 0, Component.literal("dormant node still gives its bonus"));
		helper.assertTrue(data.owns("calm_focus"), Component.literal("dormant nodes stay owned"));

		SkillContext dark = ctx(9, 0, 5, Path.JEDI);
		helper.assertTrue(!SkillRules.active(trees, data, dark, "centered_mind"), Component.literal("centered_mind dormant at +5"));
		helper.assertTrue(SkillRules.active(trees, data, light, "centered_mind") && SkillRules.active(trees, data, light, "calm_focus"),
				Component.literal("nodes wake again when alignment recovers"));

		SkillData unbound = new SkillData();
		unbound.nodes.put(SkillIds.UNBOUND, 1);
		helper.assertTrue(SkillRules.active(trees, unbound, ctx(5, 0, 0, Path.INDEPENDENT), SkillIds.UNBOUND), Component.literal("unbound active for Independents"));
		helper.assertTrue(!SkillRules.active(trees, unbound, ctx(5, 0, 0, Path.JEDI), SkillIds.UNBOUND), Component.literal("unbound dormant on a path"));

		// Side powers follow the opposed / refused rule instead of going dormant.
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_JEDI, -20) == AbilityCosts.Opposition.OPPOSED, Component.literal("Jedi power opposed at -20"));
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_JEDI, -60) == AbilityCosts.Opposition.REFUSED, Component.literal("Jedi power refused at -60"));
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_SITH, 20) == AbilityCosts.Opposition.OPPOSED, Component.literal("Sith power opposed at +20"));
		helper.assertTrue(Math.abs(AbilityCosts.costMultiplier(true, SkillIds.TREE_UNIVERSAL, 0, b) - b.unboundCostMult) < 1e-4, Component.literal("unbound cost"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- respec

	@GameTest
	public void respecRefundCooldownAndGrantedNodes(GameTestHelper helper) {
		helper.assertTrue(SkillRules.respecLoss(0, 0.1f) == 0 && SkillRules.respecLoss(1, 0.1f) == 1, Component.literal("loss minimum"));
		helper.assertTrue(SkillRules.respecLoss(10, 0.1f) == 1 && SkillRules.respecLoss(11, 0.1f) == 2 && SkillRules.respecLoss(25, 0.1f) == 3,
				Component.literal("loss rounds up"));
		helper.assertTrue(SkillRules.respecRefund(11, 0.1f) == 9, Component.literal("refund of 11 is 9"));

		SkillBalance b = new SkillBalance();
		SkillData spent = new SkillData();
		spent.spent.put("deep_reserves", 1);
		helper.assertTrue(SkillRules.respecBlock(new SkillData(), 0, false, true, b) != null, Component.literal("nothing to respec"));
		helper.assertTrue(SkillRules.respecBlock(spent, 0, false, false, b) != null, Component.literal("needs an anchor"));
		helper.assertTrue(SkillRules.respecBlock(spent, 0, true, true, b) != null, Component.literal("blocked in combat"));
		helper.assertTrue(SkillRules.respecBlock(spent, 0, false, true, b) == null, Component.literal("first respec allowed"));
		spent.lastRespec = 1000;
		helper.assertTrue(SkillRules.respecBlock(spent, 1000 + b.respecCooldownTicks - 1, false, true, b) != null, Component.literal("cooldown"));
		helper.assertTrue(SkillRules.respecBlock(spent, 1000 + b.respecCooldownTicks, false, true, b) == null, Component.literal("cooldown over"));

		trees(helper);
		FakePlayer player = player(helper, 9, 12);
		SkillService.learn(player, "push_repulse_wave");
		SkillService.learn(player, SkillIds.DEEP_RESERVES);
		SkillService.learn(player, SkillIds.DEEP_RESERVES);
		SkillService.learn(player, "force_pull");
		int before = Progression.get(player).skillPoints;
		int total = SkillService.data(player).spentTotal();
		helper.assertTrue(total == 5, Component.literal("expected 5 points spent, got " + total));
		helper.assertTrue(SkillService.respec(player, true), Component.literal("forced respec refused"));

		SkillData data = SkillService.data(player);
		helper.assertTrue(Progression.get(player).skillPoints == before + 4, Component.literal("5 spent refunds 4, got " + (Progression.get(player).skillPoints - before)));
		helper.assertTrue(data.owns(SkillIds.FORCE_PUSH) && "force_push".equals(data.slot(0)), Component.literal("granted Push and its slot must stay"));
		helper.assertTrue(!data.owns("force_pull") && data.slotOf("force_pull") < 0 && !Progression.get(player).hasAbility("force_pull"),
				Component.literal("bought power not removed"));
		helper.assertTrue(data.spentTotal() == 0 && data.lastRespec != SkillData.NEVER, Component.literal("respec state not recorded"));
		helper.assertTrue(Progression.get(player).counter(Progression.MOD_MAX_ENERGY) == 0, Component.literal("energy modifier not cleared"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- slots

	@GameTest
	public void slotRulesAndCombatLock(GameTestHelper helper) {
		trees(helper);
		FakePlayer player = player(helper, 5, 4);
		SkillData data = SkillService.data(player);
		helper.assertTrue(data.openSlots == 3 && SkillIds.FORCE_PUSH.equals(data.slot(0)), Component.literal("3 slots with Push in slot 1"));
		helper.assertTrue(!SkillService.assignSlot(player, 3, SkillIds.FORCE_PUSH), Component.literal("slot 4 is locked before the first rank"));
		helper.assertTrue(!SkillService.assignSlot(player, 1, "force_pull"), Component.literal("an unknown power cannot be slotted"));

		SkillService.learn(player, "force_pull");
		data = SkillService.data(player);
		helper.assertTrue("force_pull".equals(data.slot(1)), Component.literal("a new power takes the first free slot, got " + data.slots));
		helper.assertTrue(SkillService.assignSlot(player, 1, SkillIds.FORCE_PUSH), Component.literal("swap refused"));
		data = SkillService.data(player);
		helper.assertTrue(SkillIds.FORCE_PUSH.equals(data.slot(1)) && "force_pull".equals(data.slot(0)), Component.literal("slots did not swap: " + data.slots));
		helper.assertTrue(SkillService.assignSlot(player, 0, ""), Component.literal("clearing refused"));

		CombatTracker.mark(player);
		helper.assertTrue(!SkillService.assignSlot(player, 0, "force_pull"), Component.literal("slots must not change in combat"));

		ForceData force = Progression.get(player);
		force.flags.add(SkillService.FIRST_RANK_FLAG);
		Progression.commit(player, force);
		SkillService.refresh(player);
		helper.assertTrue(SkillService.data(player).openSlots == 4, Component.literal("the first rank opens slot 4"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- migration and modifiers

	@GameTest
	public void pushMigrationFromBranch(GameTestHelper helper) {
		trees(helper);
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "skill_migrate"));
		ForceData force = Progression.get(player);
		force.questStage = QuestStage.COMPLETE.ordinal();
		force.level = 4;
		force.abilities.add(SkillIds.FORCE_PUSH);
		force.pushBranch = "deflecting_pulse";
		Progression.commit(player, force);

		SkillService.migrate(player);
		SkillData data = SkillService.data(player);
		helper.assertTrue(data.migrated, Component.literal("not marked migrated"));
		helper.assertTrue(data.owns(SkillIds.FORCE_PUSH) && data.isGranted(SkillIds.FORCE_PUSH), Component.literal("Push not granted"));
		helper.assertTrue(data.owns("push_deflecting_pulse") && data.spentTotal() == 1, Component.literal("branch not migrated as one spent point: " + data.spent));
		helper.assertTrue(SkillIds.FORCE_PUSH.equals(data.slot(0)), Component.literal("Push not in slot 1"));

		SkillService.migrate(player);
		helper.assertTrue(SkillService.data(player).nodes.size() == 2, Component.literal("migration must run once"));
		SkillService.refresh(player);
		helper.assertTrue("deflecting_pulse".equals(Progression.get(player).pushBranch), Component.literal("mirror lost after refresh"));
		helper.succeed();
	}

	@GameTest
	public void statModifiersReachProgression(GameTestHelper helper) {
		trees(helper);
		FakePlayer player = player(helper, 5, 4);
		float base = Progression.get(player).maxEnergy;
		SkillService.learn(player, SkillIds.DEEP_RESERVES);
		SkillService.learn(player, SkillIds.DEEP_RESERVES);
		ForceData force = Progression.get(player);
		int per = new SkillBalance().deepReservesPerRank;
		helper.assertTrue(force.counter(Progression.MOD_MAX_ENERGY) == 2 * per, Component.literal("counter is " + force.counter(Progression.MOD_MAX_ENERGY)));
		helper.assertTrue(Math.abs(force.maxEnergy - (base + 2 * per)) < 0.01f, Component.literal("max energy " + base + " -> " + force.maxEnergy));

		SkillService.learn(player, SkillIds.QUICKENED_FLOW);
		force = Progression.get(player);
		helper.assertTrue(force.counter(Progression.MOD_REGEN_PCT) == new SkillBalance().quickenedRegenPctPerRank, Component.literal("regen modifier missing"));
		helper.succeed();
	}
}

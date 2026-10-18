package com.oldrepublic.skill;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityRuntime;
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.equipment.blaster.PlayerBolts;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.network.OpenSkillsPayload;
import com.oldrepublic.world.WorldBlocks;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Server-side skill rules applied to real players: buying and switching nodes, grants, slots,
 * respec, forms, the Force Push migration, dormant nodes and the energy modifiers they write into
 * {@code ForceData.counters}. Every change is validated here, committed (and so synced) and given
 * feedback in one place. The checks themselves are in {@link SkillRules}.
 */
public final class SkillService {
	/** Resonance milestone flag that also opens the fourth slot (Padawan / Apprentice / No Master). */
	public static final String FIRST_RANK_FLAG = "resonance:first_rank";
	/** Cooldown key for {@code hungering_dark}. */
	private static final String HUNGERING_COOLDOWN = "hungering_dark";
	private static final String MENTOR_JEDI = "oldrepublic:jedi_mentor";
	private static final String MENTOR_SITH = "oldrepublic:sith_mentor";

	public static SkillBalance balance() {
		return BalanceConfig.section("skills", SkillBalance.class, SkillBalance::new);
	}

	/** The live server-side data. Call {@link #commit} after changing it. */
	public static SkillData data(ServerPlayer player) {
		return player.getAttachedOrCreate(SkillAttachments.SKILLS);
	}

	/** Stores and syncs the data to the owner, and re-applies the attribute passives it may have changed. */
	public static void commit(ServerPlayer player, SkillData data) {
		player.setAttached(SkillAttachments.SKILLS, data.copy().seal());
		SkillAttributes.refresh(player);
	}

	// ---------------------------------------------------------------- buying

	/** Validates and applies a purchase (or a switch inside a group) requested by the player. */
	public static SkillRules.LearnResult learn(ServerPlayer player, String nodeId) {
		SkillTreeSet trees = SkillTrees.server();
		SkillData data = data(player);
		SkillBalance b = balance();
		SkillRules.LearnCheck check = SkillRules.check(trees, data, Skills.context(player), nodeId, b.switchPenalty);

		if (!check.ok()) {
			player.sendOverlayMessage(Component.translatable(check.result().messageKey(), messageArgs(check)));
			return check.result();
		}

		SkillNode node = check.node();
		ForceData force = Progression.get(player);
		force.skillPoints -= check.cost();

		if (check.replaces() != null) {
			removeNode(player, force, data, check.replaces());
		}

		data.nodes.put(node.id(), check.nextRank());
		data.spent.merge(node.id(), node.costOf(check.nextRank()), Integer::sum);
		nodeAdded(force, data, node);
		applyDerived(player, force, data);
		Progression.commit(player, force);
		commit(player, data);

		Component name = Component.translatable(node.nameKey());

		if (check.replaces() != null) {
			player.sendSystemMessage(Component.translatable("message.oldrepublic.skill.switched", name,
					Component.translatable(check.replaces().nameKey()), check.cost()));
		} else if (node.maxRank() > 1) {
			player.sendSystemMessage(Component.translatable("message.oldrepublic.skill.learned_rank", name, check.nextRank(), check.cost()));
		} else {
			player.sendSystemMessage(Component.translatable("message.oldrepublic.skill.learned", name, check.cost()));
		}

		learnedEffect(player, trees, node, check.nextRank());
		QuestHooks.onNodeLearned(player, node.id());
		return check.result();
	}

	/** Arguments for a learn failure message: level, cost, the missing node's name, the path's name. */
	static Object[] messageArgs(SkillRules.LearnCheck check) {
		SkillNode node = check.node();
		Object missing = check.missing() == null ? "" : Component.translatable("skill.oldrepublic.node." + check.missing().node() + ".name");
		Object path = node == null || node.path() == null ? "" : Component.translatable("skill.oldrepublic.path." + node.path().id());
		return new Object[] {node == null ? 0 : node.levelFor(check.nextRank()), check.cost(), missing, path};
	}

	/**
	 * Grants a node at no cost (lessons, trials, the Crossing). A node the player already bought
	 * becomes granted without a refund; siblings in its group are removed (not refunded).
	 */
	public static void grant(ServerPlayer player, String nodeId) {
		SkillTreeSet trees = SkillTrees.server();
		SkillNode node = trees.node(nodeId);

		if (node == null) {
			OldRepublicMod.LOGGER.warn("Cannot grant unknown skill node {} to {}", nodeId, player.getName().getString());
			return;
		}

		SkillData data = data(player);

		if (data.isGranted(nodeId) && data.owns(nodeId)) {
			return;
		}

		ForceData force = Progression.get(player);
		SkillNode sibling = SkillRules.sibling(trees, data, node);

		if (sibling != null) {
			removeNode(player, force, data, sibling);
		}

		boolean wasOwned = data.owns(nodeId);
		data.nodes.put(nodeId, Math.max(1, data.rank(nodeId)));
		data.granted.add(nodeId);
		data.spent.remove(nodeId);
		nodeAdded(force, data, node);
		applyDerived(player, force, data);
		Progression.commit(player, force);
		commit(player, data);

		if (!wasOwned) {
			player.sendSystemMessage(Component.translatable("message.oldrepublic.skill.granted", Component.translatable(node.nameKey())));
			learnedEffect(player, trees, node, 1);
		}

		QuestHooks.onNodeLearned(player, nodeId);
	}

	/** Removes a node without refund (operator command). Granted nodes too. */
	public static boolean revoke(ServerPlayer player, String nodeId) {
		SkillTreeSet trees = SkillTrees.server();
		SkillNode node = trees.node(nodeId);
		SkillData data = data(player);

		if (node == null || !data.owns(nodeId)) {
			return false;
		}

		ForceData force = Progression.get(player);
		removeNode(player, force, data, node);
		applyDerived(player, force, data);
		Progression.commit(player, force);
		commit(player, data);
		return true;
	}

	private static void learnedEffect(ServerPlayer player, SkillTreeSet trees, SkillNode node, int rank) {
		SkillTree tree = trees.tree(node.tree());
		EffectEvents.toPlayer(player, SkillEffects.NODE_LEARNED, player.getEyePosition(), null, player.getId(), rank, tree == null ? 0 : tree.order());
	}

	/** A new ability node teaches the ability and takes the first free slot (never a slotless power). */
	private static void nodeAdded(ForceData force, SkillData data, SkillNode node) {
		if (!node.isAbility()) {
			return;
		}

		String ability = node.ability() != null ? node.ability() : node.id();
		force.abilities.add(ability);

		if (!SkillRules.slotless(ability) && data.slotOf(ability) < 0) {
			int free = SkillRules.firstFreeSlot(data);

			if (free >= 0) {
				data.slots.set(free, ability);
			}
		}
	}

	/** Takes a node away: its ability, slot and form go with it. Nothing is refunded here. */
	private static void removeNode(ServerPlayer player, ForceData force, SkillData data, SkillNode node) {
		data.nodes.remove(node.id());
		data.spent.remove(node.id());
		data.granted.remove(node.id());

		if (node.isAbility()) {
			String ability = node.ability() != null ? node.ability() : node.id();
			AbilityRuntime.cancel(player, ability);
			force.abilities.remove(ability);
			int slot = data.slotOf(ability);

			if (slot >= 0) {
				data.slots.set(slot, "");
			}
		}

		if (node.id().equals(data.activeForm)) {
			data.activeForm = "";
		}
	}

	// ---------------------------------------------------------------- slots and forms

	/** Puts an owned power into a slot (swapping with its old slot), or clears the slot for {@code ""}. */
	public static boolean assignSlot(ServerPlayer player, int slot, String abilityId) {
		SkillData data = data(player);

		if (slot < 0 || slot >= Math.min(SkillData.SLOT_COUNT, data.openSlots)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.slot.locked", slot + 1));
			return false;
		}

		if (SkillRules.slotless(abilityId)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.slot.slotless", Component.keybind("key.oldrepublic.force_sense")));
			return false;
		}

		if (!abilityId.isEmpty()) {
			SkillNode node = SkillTrees.server().abilityNode(abilityId);

			if (node == null || !data.owns(node.id())) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.slot.not_owned"));
				return false;
			}
		}

		if (CombatTracker.inCombat(player)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.slot.combat"));
			return false;
		}

		String previous = data.slot(slot);

		if (previous.equals(abilityId)) {
			return true;
		}

		int from = abilityId.isEmpty() ? -1 : data.slotOf(abilityId);

		if (from >= 0) {
			// The power moves: its old slot takes what was here (a swap).
			data.slots.set(from, previous);
		}

		data.slots.set(slot, abilityId);

		if (!previous.isEmpty()) {
			AbilityRuntime.cancel(player, previous);
		}

		if (!abilityId.isEmpty()) {
			AbilityRuntime.cancel(player, abilityId);
		}

		commit(player, data);
		return true;
	}

	/** Sets the active form directly (no timing; the saber feature validates first). */
	public static boolean setActiveForm(ServerPlayer player, String formNodeId) {
		SkillData data = data(player);

		if (!formNodeId.isEmpty()) {
			SkillNode node = SkillTrees.server().node(formNodeId);

			if (node == null || node.kind() != NodeKind.FORM || !Skills.active(player, formNodeId)) {
				return false;
			}
		}

		if (data.activeForm.equals(formNodeId)) {
			return false;
		}

		data.activeForm = formNodeId;
		commit(player, data);
		return true;
	}

	/** A form change asked for in the Force screen: through the saber feature's handler when present. */
	public static void requestForm(ServerPlayer player, String formNodeId) {
		if (!formNodeId.isEmpty()) {
			SkillNode node = SkillTrees.server().node(formNodeId);

			if (node == null || node.kind() != NodeKind.FORM || !Skills.active(player, formNodeId)) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.form.not_owned"));
				return;
			}
		}

		FormChanges.Handler handler = FormChanges.handler();

		if (handler != null) {
			handler.request(player, formNodeId);
			return;
		}

		if (CombatTracker.inCombat(player)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.form.combat"));
			return;
		}

		if (setActiveForm(player, formNodeId)) {
			Component name = formNodeId.isEmpty() ? Component.translatable("skill.oldrepublic.form.unformed")
					: Component.translatable("skill.oldrepublic.node." + formNodeId + ".name");
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.form.changed", name));
		}
	}

	// ---------------------------------------------------------------- respec

	/** Why a respec is refused right now (lang key), or null. */
	public static @Nullable String respecBlock(ServerPlayer player) {
		return SkillRules.respecBlock(data(player), player.level().getGameTime(), CombatTracker.inCombat(player), nearRespecAnchor(player), balance());
	}

	/**
	 * Full respec: refunds every bought point minus the loss, keeps granted nodes, clears slots of
	 * removed powers and an unbought form.
	 *
	 * @param force skips the anchor, combat and cooldown checks (operator command)
	 */
	public static boolean respec(ServerPlayer player, boolean force) {
		SkillBalance b = balance();
		SkillData data = data(player);
		String block = force ? (data.spentTotal() <= 0 ? "message.oldrepublic.skill.respec.nothing" : null) : respecBlock(player);

		if (block != null) {
			long wait = Math.max(0, SkillRules.respecReadyAt(data, b) - player.level().getGameTime());
			player.sendOverlayMessage(Component.translatable(block, durationText(wait)));
			return false;
		}

		SkillTreeSet trees = SkillTrees.server();
		int spent = data.spentTotal();
		int refund = SkillRules.respecRefund(spent, b.respecLoss);
		AbilityRuntime.cancelAll(player, true);
		ForceData forceData = Progression.get(player);
		List<String> owned = new ArrayList<>(data.nodes.keySet());

		for (String id : owned) {
			if (data.isGranted(id)) {
				continue;
			}

			SkillNode node = trees.node(id);

			if (node != null) {
				removeNode(player, forceData, data, node);
			} else {
				data.nodes.remove(id);
				data.spent.remove(id);
			}
		}

		data.spent.clear();
		forceData.skillPoints += refund;
		data.lastRespec = player.level().getGameTime();
		applyDerived(player, forceData, data);
		Progression.commit(player, forceData);
		commit(player, data);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.skill.respec.done", refund, spent - refund));
		EffectEvents.toPlayer(player, SkillEffects.RESPEC, player.getEyePosition(), null, player.getId(), refund, 0);
		return true;
	}

	/** The mentor's "Unburden" option: opens the Force screen with the confirmation, or says why not. */
	static void offerRespec(ServerPlayer player) {
		SkillData data = data(player);
		SkillBalance b = balance();
		String block = SkillRules.respecBlock(data, player.level().getGameTime(), CombatTracker.inCombat(player), true, b);

		if (block != null) {
			long wait = Math.max(0, SkillRules.respecReadyAt(data, b) - player.level().getGameTime());
			player.sendSystemMessage(Component.translatable(block, durationText(wait)));
			return;
		}

		ServerPlayNetworking.send(player, new OpenSkillsPayload(true));
	}

	/** "2 days 4 h" style text for a wait in ticks. */
	public static Component durationText(long ticks) {
		long hours = (ticks + 999) / 1000;
		return hours >= 24 ? Component.translatable("message.oldrepublic.skill.duration.days", hours / 24, hours % 24)
				: Component.translatable("message.oldrepublic.skill.duration.hours", Math.max(1, hours));
	}

	/**
	 * A respec needs the player's mentor nearby (Jedi or Sith path), or a meditation stone
	 * (Independents, and anyone before the Crossing).
	 */
	public static boolean nearRespecAnchor(ServerPlayer player) {
		return nearRespecAnchor(player, balance().respecAnchorRadius);
	}

	/** {@link #nearRespecAnchor(ServerPlayer)} with an explicit radius; works on both sides. */
	public static boolean nearRespecAnchor(Player player, int r) {
		Path path = Identity.path(player);

		if (path == Path.JEDI || path == Path.SITH) {
			String type = path == Path.JEDI ? MENTOR_JEDI : MENTOR_SITH;
			return !player.level().getEntities(player, player.getBoundingBox().inflate(r),
					e -> e.isAlive() && type.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())).isEmpty();
		}

		BlockPos center = player.blockPosition();

		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r / 2, -r), center.offset(r, r / 2, r))) {
			if (player.level().getBlockState(pos).is(WorldBlocks.MEDITATION_STONE)) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------- derived state

	/**
	 * Recomputes what follows from the owned nodes and the player's progress, and commits whatever
	 * changed: nodes for abilities learned outside the tree (Force Push from the quest), open slots,
	 * the energy modifiers, the Force Push branch mirror and the attribute passives
	 * ({@link SkillAttributes}).
	 */
	public static void refresh(ServerPlayer player) {
		SkillData data = data(player);
		ForceData force = Progression.get(player);
		SkillData before = data.copy();
		String forceBefore = signature(force);
		applyDerived(player, force, data);

		if (!signature(force).equals(forceBefore)) {
			Progression.commit(player, force);
		}

		if (!sameData(before, data)) {
			commit(player, data);
		} else {
			// Alignment, path or combat may have woken or silenced an attribute passive.
			SkillAttributes.refresh(player);
		}
	}

	private static void applyDerived(ServerPlayer player, ForceData force, SkillData data) {
		SkillTreeSet trees = SkillTrees.server();
		SkillBalance b = balance();

		if (trees.nodes().isEmpty()) {
			return;
		}

		// Abilities taught outside the tree (the quest's Force Push, an operator command) get their node.
		for (String ability : List.copyOf(force.abilities)) {
			SkillNode node = trees.abilityNode(ability);

			if (node != null && !data.owns(node.id())) {
				data.nodes.put(node.id(), 1);
				data.granted.add(node.id());
				nodeAdded(force, data, node);
			}
		}

		int open = SkillRules.openSlots(Identity.rank(player) >= 2 || force.hasFlag(FIRST_RANK_FLAG), b);

		if (open != data.openSlots) {
			data.openSlots = open;

			// A newly opened slot takes a power that had none (never a slotless one).
			for (SkillNode node : trees.nodes()) {
				String ability = node.isAbility() ? (node.ability() != null ? node.ability() : node.id()) : null;

				if (ability != null && !SkillRules.slotless(ability) && data.owns(node.id()) && data.slotOf(ability) < 0
						&& SkillRules.firstFreeSlot(data) >= 0) {
					data.slots.set(SkillRules.firstFreeSlot(data), ability);
				}
			}
		}

		SkillRules.StatMods mods = SkillRules.statMods(trees, data, Skills.context(player), b);
		setCounter(force, Progression.MOD_MAX_ENERGY, mods.maxEnergy());
		setCounter(force, Progression.MOD_MAX_ENERGY_PCT, mods.maxEnergyPct());
		setCounter(force, Progression.MOD_REGEN_PCT, mods.regenPct());
		setCounter(force, Progression.MOD_REGEN_DELAY, mods.regenDelay());
		force.pushBranch = SkillRules.pushBranchMirror(trees, data);
	}

	private static void setCounter(ForceData force, String key, int value) {
		if (value == 0) {
			force.counters.remove(key);
		} else {
			force.counters.put(key, value);
		}
	}

	private static String signature(ForceData force) {
		return force.pushBranch + "|" + force.abilities + "|" + force.counter(Progression.MOD_MAX_ENERGY) + "|" + force.counter(Progression.MOD_MAX_ENERGY_PCT)
				+ "|" + force.counter(Progression.MOD_REGEN_PCT) + "|" + force.counter(Progression.MOD_REGEN_DELAY);
	}

	private static boolean sameData(SkillData a, SkillData b) {
		return a.nodes.equals(b.nodes) && a.spent.equals(b.spent) && a.granted.equals(b.granted) && a.slots.equals(b.slots)
				&& a.openSlots == b.openSlots && a.activeForm.equals(b.activeForm) && a.lastRespec == b.lastRespec && a.migrated == b.migrated;
	}

	/**
	 * Milestone 1 saves: Force Push known → its node (granted), a chosen {@code pushBranch} → the
	 * matching branch node at no cost (recorded as the 1 point it cost, so a respec refunds it),
	 * and Push in slot 1. Runs once per player, after the trees have loaded.
	 */
	public static void migrate(ServerPlayer player) {
		SkillData data = data(player);
		SkillTreeSet trees = SkillTrees.server();

		if (data.migrated || trees.nodes().isEmpty()) {
			return;
		}

		ForceData force = Progression.get(player);
		SkillNode push = trees.abilityNode(SkillIds.FORCE_PUSH);

		if (push != null && force.hasAbility(SkillIds.FORCE_PUSH) && !data.owns(push.id())) {
			data.nodes.put(push.id(), 1);
			data.granted.add(push.id());
		}

		SkillNode branch = force.pushBranch.isEmpty() ? null : trees.node(SkillIds.PUSH_PREFIX + force.pushBranch);
		boolean hasBranch = trees.group(SkillIds.PUSH_GROUP).stream().anyMatch(n -> data.owns(n.id()));

		if (branch != null && !hasBranch) {
			data.nodes.put(branch.id(), 1);
			data.spent.put(branch.id(), branch.costOf(1));
		}

		if (data.owns(SkillIds.FORCE_PUSH) && data.slotOf(SkillIds.FORCE_PUSH) < 0) {
			int slot = data.slot(0).isEmpty() ? 0 : SkillRules.firstFreeSlot(data);

			if (slot >= 0) {
				data.slots.set(slot, SkillIds.FORCE_PUSH);
			}
		}

		data.migrated = true;
		commit(player, data);
		OldRepublicMod.LOGGER.debug("Migrated skills of {} (push branch '{}')", player.getName().getString(), force.pushBranch);
	}

	// ---------------------------------------------------------------- reactions

	/** Tells the player which owned nodes fell dormant or woke when their alignment changed. */
	static void onAlignmentChanged(ServerPlayer player, int before, int after) {
		SkillData data = data(player);
		SkillTreeSet trees = SkillTrees.server();
		Path path = Identity.path(player);

		for (String id : data.nodes.keySet()) {
			SkillNode node = trees.node(id);

			if (node == null || node.isAbility() || (node.path() != null && node.path() != path)) {
				continue;
			}

			boolean was = node.alignmentMet(before);
			boolean now = node.alignmentMet(after);

			if (was != now) {
				player.sendSystemMessage(Component.translatable(now ? "message.oldrepublic.skill.awake" : "message.oldrepublic.skill.dormant",
						Component.translatable(node.nameKey())));
			}
		}

		refresh(player);
	}

	/** Tells the player which owned nodes fell dormant or woke because their path changed. */
	static void onPathChanged(ServerPlayer player, Path before, Path after) {
		SkillData data = data(player);
		SkillTreeSet trees = SkillTrees.server();
		int alignment = Identity.alignment(player);

		for (String id : data.nodes.keySet()) {
			SkillNode node = trees.node(id);

			if (node == null || node.isAbility() || node.path() == null || !node.alignmentMet(alignment)) {
				continue;
			}

			boolean was = node.path() == before;
			boolean now = node.path() == after;

			if (was != now) {
				player.sendSystemMessage(Component.translatable(now ? "message.oldrepublic.skill.awake" : "message.oldrepublic.skill.dormant",
						Component.translatable(node.nameKey())));
			}
		}

		refresh(player);
	}

	/**
	 * {@code hungering_dark}: killing a creature that counted as hostile when the killer's damage
	 * landed ({@link Attitudes#hostileSnapshot}; never an Allied, Neutral or provoked kill) restores
	 * Force Energy, at most once per cooldown. The refund goes through {@link EnergyRefunds}, so it
	 * shares the per-kill cap with {@code tomb_hunger} and the armour set bonus.
	 */
	static void onKill(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || entity == player || !Attitudes.hostileSnapshot(entity, player)) {
			return;
		}

		// Blaster kills never refund Force Energy (MILESTONE_4.md 5.1).
		if (PlayerBolts.isPlayerBlaster(source)) {
			return;
		}

		if (!Skills.active(player, SkillIds.HUNGERING_DARK) || Progression.isOnCooldown(player, HUNGERING_COOLDOWN)) {
			return;
		}

		SkillBalance b = balance();
		EnergyRefunds.forKill(player, entity, b.hungeringEnergy);
		Progression.setCooldown(player, HUNGERING_COOLDOWN, b.hungeringCooldownTicks);
	}

	private SkillService() {
	}
}

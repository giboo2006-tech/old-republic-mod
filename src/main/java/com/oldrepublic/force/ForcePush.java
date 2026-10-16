package com.oldrepublic.force;

import java.util.List;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Force Push, server side: charge tracking, validation, cost, targeting and feedback. The ability
 * runtime routes the slot key to {@link PushAbility}, which calls in here; clients only send
 * intents and everything else is decided here.
 */
public final class ForcePush {
	/** Ability id stored in {@link ForceData#abilities}, also used for mastery and cooldown. */
	public static final String ABILITY_ID = "force_push";
	/** One-shot Resonance milestone flag for the first successful push. */
	public static final String FIRST_PUSH_MILESTONE = "first_push";

	public static PushBalance balance() {
		return BalanceConfig.section("force_push", PushBalance.class, PushBalance::new);
	}

	/**
	 * Teaches Force Push (idempotent) and tells the player how to use it. Called by the quest when
	 * the first meditation completes.
	 *
	 * @return true if the player did not know it before
	 */
	public static boolean grant(ServerPlayer player) {
		ForceData data = Progression.get(player);

		if (!data.abilities.add(ABILITY_ID)) {
			return false;
		}

		Progression.commit(player, data);
		Skills.grant(player, ABILITY_ID);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.force.push_learned",
				Component.keybind("key.oldrepublic.use_force_power")));
		EffectEvents.toPlayer(player, ForceEffects.PUSH_LEARNED, player.getEyePosition(), player.getLookAngle(), player.getId(), 1f, 0);
		return true;
	}

	/** The shape a cast by {@code player} would have at {@code charge}. */
	public static PushShape shapeFor(ServerPlayer player, float charge) {
		ForceData data = Progression.get(player);
		int rank = Progression.masteryRank(data.mastery(ABILITY_ID));
		return PushShape.of(balance(), branch(player), rank, charge);
	}

	/**
	 * The player's push branch: the active {@code push_*} skill node, or the
	 * {@link ForceData#pushBranch} mirror the skills feature keeps (also set by the test command).
	 */
	public static PushBranch branch(ServerPlayer player) {
		ForceData data = Progression.get(player);

		for (PushBranch branch : PushBranch.values()) {
			if (branch != PushBranch.NONE && ForcePowers.hasNode(player, data, branch.nodeId())) {
				return branch;
			}
		}

		return PushBranch.byId(data.pushBranch);
	}

	/** Force Energy cost of a cast with {@code shape} (mastery included), after skills, alignment and forms. */
	static float cost(ServerPlayer player, PushShape shape) {
		return shape.cost() * ForcePowers.costMultiplier(player, ABILITY_ID);
	}

	static void onInput(ServerPlayer player, AbilityAction action) {
		switch (action) {
			case START -> startCharge(player);
			case RELEASE -> release(player);
			case CANCEL -> cancel(player, true);
		}
	}

	private static void startCharge(ServerPlayer player) {
		if (PushCharges.isCharging(player)) {
			return;
		}

		if (player.isUsingItem()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.cannot_now"));
			sendStop(player);
			return;
		}

		if (!ForcePowers.mayStart(player, ABILITY_ID) || !ForcePowers.canAfford(player, cost(player, shapeFor(player, 0f)))) {
			sendStop(player);
			return;
		}

		PushCharges.start(player);
		EffectEvents.toTracking(player, ForceEffects.PUSH_CHARGE, player.getEyePosition(), player.getLookAngle(), player.getId(), 0f, ForceEffects.CHARGE_START);
	}

	private static void release(ServerPlayer player) {
		long held = PushCharges.finish(player);

		if (held < 0) {
			return;
		}

		float charge = PushShape.chargeFromHold(balance(), held);

		if (charge < 0f || !mayRelease(player) || !cast(player, charge)) {
			// Released below the minimum, or no longer allowed: cancel silently, no cost.
			sendStop(player);
		}
	}

	/** Cancels a charge without cost. {@code notify} sends the stop event (false when disconnecting). */
	static void cancel(ServerPlayer player, boolean notify) {
		if (PushCharges.finish(player) >= 0 && notify) {
			sendStop(player);
		}
	}

	static void sendStop(ServerPlayer player) {
		EffectEvents.toTracking(player, ForceEffects.PUSH_CHARGE, player.getEyePosition(), player.getLookAngle(), player.getId(), 0f, ForceEffects.CHARGE_STOP);
	}

	/** Whether a charged push may still fire (nothing changed while it was held). */
	private static boolean mayRelease(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isUsingItem() && !Progression.isOnCooldown(player, ABILITY_ID)
				&& ForcePowers.knows(player, Progression.get(player), ABILITY_ID);
	}

	/**
	 * Fires a push at {@code charge} (0 to 1). Validation of awakening, ability and cooldown has
	 * already happened; this spends the energy and applies everything.
	 *
	 * @return false if the energy could not be spent (the fail feedback has been played)
	 */
	static boolean cast(ServerPlayer player, float charge) {
		PushBranch branch = branch(player);
		PushShape shape = shapeFor(player, charge);

		if (!Progression.trySpendEnergy(player, cost(player, shape))) {
			return false;
		}

		Progression.setCooldown(player, ABILITY_ID, ForcePowers.cooldown(player, ABILITY_ID, shape.cooldownTicks()));
		EffectEvents.toTracking(player, ForceEffects.PUSH_RELEASE, player.getEyePosition(), player.getLookAngle(), player.getId(), charge,
				ForceEffects.packRelease(branch, shape));

		List<PushTargeting.Target> targets = PushTargeting.find(player, shape, balance());
		int hostileHits = PushEffects.apply(player, shape, branch, charge, targets);
		ForcePowers.addMastery(player, ABILITY_ID, hostileHits);

		Progression.grantResonanceMilestone(player, FIRST_PUSH_MILESTONE, balance().firstPushResonance);
		QuestHooks.onAbilityUsed(player, ABILITY_ID, hostileHits);
		return true;
	}

	private ForcePush() {
	}
}

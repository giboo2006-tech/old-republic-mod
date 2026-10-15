package com.oldrepublic.force;

import java.util.List;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.force.network.PushInputPayload;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Force Push, server side: charge tracking, validation, cost, targeting and feedback. Clients only
 * send {@link PushInputPayload} intents; everything else is decided here.
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
		player.sendSystemMessage(Component.translatable("message.oldrepublic.force.push_learned",
				Component.keybind("key.oldrepublic.use_force_power")));
		EffectEvents.toPlayer(player, ForceEffects.PUSH_LEARNED, player.getEyePosition(), player.getLookAngle(), player.getId(), 1f, 0);
		return true;
	}

	/** The shape a cast by {@code player} would have at {@code charge}. */
	public static PushShape shapeFor(ServerPlayer player, float charge) {
		ForceData data = Progression.get(player);
		int rank = Progression.masteryRank(data.mastery(ABILITY_ID));
		return PushShape.of(balance(), PushBranch.byId(data.pushBranch), rank, charge);
	}

	static void onInput(ServerPlayer player, PushInputPayload.Action action) {
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

		String refusal = refusal(player);

		if (refusal != null) {
			player.sendOverlayMessage(Component.translatable(refusal));
			sendStop(player);
			return;
		}

		ForceData data = Progression.get(player);
		float minimumCost = shapeFor(player, 0f).cost();

		if (!player.isCreative() && data.energy + 1.0e-3f < minimumCost) {
			// Fails and plays the shared "not enough energy" feedback.
			Progression.trySpendEnergy(player, minimumCost);
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

		if (charge < 0f || refusal(player) != null || !cast(player, charge)) {
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

	/** Translation key of the reason the player may not push right now, or null. */
	private static String refusal(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			return "message.oldrepublic.force.cannot_now";
		}

		ForceData data = Progression.get(player);

		if (!data.isAwakened()) {
			return "message.oldrepublic.not_awakened";
		}

		if (!data.hasAbility(ABILITY_ID)) {
			return "message.oldrepublic.force.not_learned";
		}

		if (Progression.isOnCooldown(player, ABILITY_ID)) {
			return "message.oldrepublic.force.cooldown";
		}

		if (player.isUsingItem()) {
			return "message.oldrepublic.force.cannot_now";
		}

		return null;
	}

	/**
	 * Fires a push at {@code charge} (0 to 1). Validation of awakening, ability and cooldown has
	 * already happened; this spends the energy and applies everything.
	 *
	 * @return false if the energy could not be spent (the fail feedback has been played)
	 */
	static boolean cast(ServerPlayer player, float charge) {
		ForceData data = Progression.get(player);
		PushBranch branch = PushBranch.byId(data.pushBranch);
		PushShape shape = shapeFor(player, charge);

		if (!Progression.trySpendEnergy(player, shape.cost())) {
			return false;
		}

		Progression.setCooldown(player, ABILITY_ID, shape.cooldownTicks());
		EffectEvents.toTracking(player, ForceEffects.PUSH_RELEASE, player.getEyePosition(), player.getLookAngle(), player.getId(), charge,
				ForceEffects.packRelease(branch, shape));

		List<PushTargeting.Target> targets = PushTargeting.find(player, shape, balance());
		int hostileHits = PushEffects.apply(player, shape, branch, charge, targets);

		if (hostileHits > 0) {
			Progression.addMastery(player, ABILITY_ID, hostileHits);
		}

		Progression.grantResonanceMilestone(player, FIRST_PUSH_MILESTONE, balance().firstPushResonance);
		QuestHooks.onAbilityUsed(player, ABILITY_ID, hostileHits);
		return true;
	}

	private ForcePush() {
	}
}

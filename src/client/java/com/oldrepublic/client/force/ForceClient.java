package com.oldrepublic.client.force;

import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.network.SenseRevealPayload;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client initialisation for the force feature. Called from OldRepublicModClient. The slot keys
 * belong to the skills feature; this registers the powers' {@link ForceAbilityHooks}, every effect
 * handler, the {@code sense_reveal} receiver, the Danger Sense listener and the per-tick animation
 * and effect state.
 */
public final class ForceClient {
	private static ClientLevel lastLevel;
	private static LocalPlayer lastPlayer;

	public static void init() {
		ForceParticleProviders.init();
		ForceAbilityHooks.init();
		EnergyVfx.init();
		KineticVfx.init();

		ClientEffects.register(ForceEffects.PUSH_CHARGE, PushFx::onCharge);
		ClientEffects.register(ForceEffects.PUSH_RELEASE, PushFx::onRelease);
		ClientEffects.register(ForceEffects.PUSH_IMPACT, PushFx::onImpact);
		ClientEffects.register(ForceEffects.PUSH_SLAM, PushFx::onSlam);
		ClientEffects.register(ForceEffects.PUSH_LEARNED, PushFx::onLearned);
		ClientEffects.register(Progression.ENERGY_FAIL, PushFx::onEnergyFail);

		ClientEffects.register(ForceEffects.PULL, PullFx::onCast);
		ClientEffects.register(ForceEffects.PULL_HIT, PullFx::onHit);
		ClientEffects.register(ForceEffects.PULL_DISARM, PullFx::onDisarm);

		ClientEffects.register(ForceEffects.JUMP_GATHER, JumpFx::onGather);
		ClientEffects.register(ForceEffects.JUMP_LAUNCH, JumpFx::onLaunch);
		ClientEffects.register(ForceEffects.JUMP_GLIDE, JumpFx::onGlide);
		ClientEffects.register(ForceEffects.JUMP_LAND, JumpFx::onLand);
		ClientEffects.register(ForceEffects.JUMP_SLAM, JumpFx::onSlam);

		ClientEffects.register(ForceEffects.HEAL, HealFx::onHeal);
		ClientEffects.register(ForceEffects.HEAL_ALLY, HealFx::onAlly);
		ClientEffects.register(ForceEffects.HEAL_WARD, HealFx::onWard);

		ClientEffects.register(ForceEffects.LIGHTNING, LightningFx::onPhase);
		ClientEffects.register(ForceEffects.LIGHTNING_TARGET, LightningFx::onTarget);
		ClientEffects.register(ForceEffects.LIGHTNING_HIT, LightningFx::onHit);

		ClientEffects.register(ForceEffects.SENSE, SenseFx::onSense);
		ClientPlayNetworking.registerGlobalReceiver(SenseRevealPayload.TYPE, (payload, context) -> SenseFx.onReveal(context.client(), payload));
		ClientEffects.addListener(DangerSense::onEvent);

		ClientEffects.register(ForceEffects.LIFT, LiftFx::onLift);
		ClientEffects.register(ForceEffects.LIFT_TARGET, LiftFx::onTarget);

		ClientTickEvents.END_CLIENT_TICK.register(ForceClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// New level (dimension change or join): entity ids are no longer valid.
			lastLevel = mc.level;
			clearAll();
		}

		if (mc.player != lastPlayer || mc.player != null && mc.player.isDeadOrDying()) {
			// Death or respawn (a new LocalPlayer, maybe in the same level): the Sense visuals end (spec 12.2).
			lastPlayer = mc.player;
			clearSense();
		}

		PushAnimations.tick(mc);
		PowerAnimations.tick(mc);
		ClientLevel level = mc.level;

		if (level == null || mc.isPaused()) {
			return;
		}

		PullFx.tick(level);
		JumpFx.tick(mc, level);
		HealFx.tick(level);
		LightningFx.tick(mc, level);
		SenseFx.tick(mc, level);
		LiftFx.tick(level);
	}

	private static void clearAll() {
		PushAnimations.clear();
		PowerAnimations.clear();
		PullFx.clear();
		JumpFx.clear();
		HealFx.clear();
		LightningFx.clear();
		clearSense();
		DangerSense.clear();
		LiftFx.clear();
	}

	/** Ends the caster's reveal and its pulses. */
	private static void clearSense() {
		SenseFx.clear();
		SenseReveal.clear();
	}

	private static void reset() {
		clearAll();
		lastLevel = null;
		lastPlayer = null;
	}

	private ForceClient() {
	}
}

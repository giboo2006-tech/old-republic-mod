package com.oldrepublic.client.force;

import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client initialisation for the force feature. Called from OldRepublicModClient. The slot keys
 * belong to the skills feature; this registers the powers' {@link ForceAbilityHooks}, every effect
 * handler and the per-tick animation and effect state.
 */
public final class ForceClient {
	private static ClientLevel lastLevel;

	public static void init() {
		ForceParticleProviders.init();
		ForceAbilityHooks.init();

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

		ClientTickEvents.END_CLIENT_TICK.register(ForceClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// New level (dimension change or join): entity ids are no longer valid.
			lastLevel = mc.level;
			clearAll();
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
	}

	private static void clearAll() {
		PushAnimations.clear();
		PowerAnimations.clear();
		PullFx.clear();
		JumpFx.clear();
		HealFx.clear();
		LightningFx.clear();
	}

	private static void reset() {
		clearAll();
		lastLevel = null;
	}

	private ForceClient() {
	}
}

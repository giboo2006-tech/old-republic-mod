package com.oldrepublic.client.force;

import com.oldrepublic.client.force.particle.ForceParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.network.PushInfoPayload;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Client initialisation for the force feature. Called from OldRepublicModClient. */
public final class ForceClient {
	private static ClientLevel lastLevel;

	public static void init() {
		ForceKeys.init();
		ForceParticleProviders.init();

		ClientEffects.register(ForceEffects.PUSH_CHARGE, PushFx::onCharge);
		ClientEffects.register(ForceEffects.PUSH_RELEASE, PushFx::onRelease);
		ClientEffects.register(ForceEffects.PUSH_IMPACT, PushFx::onImpact);
		ClientEffects.register(ForceEffects.PUSH_SLAM, PushFx::onSlam);
		ClientEffects.register(ForceEffects.PUSH_LEARNED, PushFx::onLearned);
		ClientEffects.register(Progression.ENERGY_FAIL, PushFx::onEnergyFail);

		ClientPlayNetworking.registerGlobalReceiver(PushInfoPayload.TYPE, (payload, context) -> ClientPushInfo.set(payload));
		ClientTickEvents.END_CLIENT_TICK.register(ForceClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// New level (dimension change or join): entity ids are no longer valid.
			lastLevel = mc.level;
			PushAnimations.clear();
			PushInput.reset();
		}

		PushInput.tick(mc);
		PushAnimations.tick(mc);
	}

	private static void reset() {
		PushAnimations.clear();
		PushInput.reset();
		ClientPushInfo.set(null);
		lastLevel = null;
	}

	private ForceClient() {
	}
}

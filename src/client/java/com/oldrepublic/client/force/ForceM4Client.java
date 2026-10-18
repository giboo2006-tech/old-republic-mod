package com.oldrepublic.client.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.barrier.BarrierFx;
import com.oldrepublic.client.force.barrier.HeldDustParticle;
import com.oldrepublic.client.force.choke.ChokeFx;
import com.oldrepublic.client.force.speed.SpeedFx;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.force.barrier.BarrierParticles;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.speed.ForceSpeed;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Milestone 4 client initialisation for the Milestone 4 powers' effects (Force Speed, Force Barrier
 * and Force Choke, spec MILESTONE_4.md 9 and 13). Owned by W6 Force and foes; the lead calls it from
 * {@code OldRepublicModClient}, so the worker never edits the client entry point.
 *
 * <p>Every loop and held state also ends here when the level changes or the client disconnects.
 */
public final class ForceM4Client {
	private static @Nullable ClientLevel lastLevel;

	public static void init() {
		ParticleProviderRegistry.PendingParticleProvider<SimpleParticleType> heldDust = sprites -> {
			ParticleProvider<SimpleParticleType> provider = (options, level, x, y, z, dx, dy, dz, random) ->
					new HeldDustParticle(level, x, y, z, dx, dy, dz, sprites);
			return provider;
		};
		ParticleProviderRegistry.getInstance().register(BarrierParticles.HELD_DUST, heldDust);

		ClientEffects.register(ForceSpeed.EFFECT, SpeedFx::onEvent);
		ClientEffects.register(ForceBarrier.EFFECT, BarrierFx::onEvent);
		ClientEffects.register(ForceBarrier.IMPACT, BarrierFx::onImpact);
		ClientEffects.register(ForceChoke.EFFECT, ChokeFx::onEvent);
		ClientEffects.register(ForceChoke.TARGET_EFFECT, ChokeFx::onTarget);

		ClientTickEvents.END_CLIENT_TICK.register(ForceM4Client::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			clear();
			lastLevel = null;
		});
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// A new level (dimension change or join): entity ids are no longer valid.
			lastLevel = mc.level;
			clear();
		}

		ClientLevel level = mc.level;

		if (level == null || mc.isPaused()) {
			return;
		}

		SpeedFx.tick(mc, level);
		BarrierFx.tick(level);
		ChokeFx.tick(level);
	}

	private static void clear() {
		SpeedFx.clear();
		BarrierFx.clear();
		ChokeFx.clear();
	}

	private ForceM4Client() {
	}
}

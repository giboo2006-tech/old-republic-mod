package com.oldrepublic.client.enemy.hask;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.hymn.FoeFx;
import com.oldrepublic.client.enemy.hymn.FoeTimers;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.hask.HaskEffects;
import com.oldrepublic.network.EffectEventPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the {@code hask_warrior} event (spec MILESTONE_5.md 15.1, 15.3). The server plays
 * the sounds; this draws the looks. Tells (never thinned): the jet-slam's 8 {@code engine_exhaust}
 * puffing from the pack over the tell, and the shield bash's 4 {@code spark_shower} off the shield's
 * rim over its tell. The slam lands in flame and a burst of the ground's dust (uneven, with gaps); a
 * turned bolt or blow throws sparks off the shield; the bash lands with a crunch of grit.
 */
public final class HaskFx {
	public static void init() {
		ClientEffects.register(HaskEffects.EVENT, HaskFx::onEvent);
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		int ticks = Math.max(1, Math.round(p.intensity()));

		switch (p.variant()) {
			case HaskEffects.SLAM_TELL -> {
				if (entity != null) {
					FoeTimers.spread(Vfx.tellCount(8), ticks, entity, (lvl, n, age) -> exhaust(lvl, entity, n));
				}
			}
			case HaskEffects.SLAM -> slam(mc, level, at);
			case HaskEffects.SHIELD_BLOCK -> {
				for (int i = 0; i < Vfx.count(6); i++) {
					FoeFx.add(level, FoeFx.sparkShower(), at, FoeFx.jitterVec(0.15).add(0, 0.12, 0));
				}

				for (int i = 0; i < Vfx.count(3); i++) {
					FoeFx.add(level, ParticleTypes.ELECTRIC_SPARK, at.add(FoeFx.jitterVec(0.1)), FoeFx.jitterVec(0.1));
				}
			}
			case HaskEffects.BASH_TELL -> {
				if (entity != null) {
					FoeTimers.spread(Vfx.tellCount(4), ticks, entity, (lvl, n, age) -> rimSparks(lvl, entity, n));
				}
			}
			case HaskEffects.BASH -> {
				ParticleOptions grit = FoeFx.ground(level, at);

				for (int i = 0; i < Vfx.count(6); i++) {
					FoeFx.add(level, ParticleTypes.CRIT, at.add(FoeFx.jitterVec(0.2)), FoeFx.jitterVec(0.25));
				}

				for (int i = 0; i < Vfx.count(5); i++) {
					FoeFx.add(level, grit, at.x + FoeFx.jitter(0.4), at.y - 0.6, at.z + FoeFx.jitter(0.4), FoeFx.jitter(0.1), 0.15, FoeFx.jitter(0.1));
				}

				FoeFx.shake(mc, at, 3, 0.2f);
			}
			default -> {
			}
		}
	}

	/** Puffs from the jet pack's two nozzles, down and back. */
	private static void exhaust(ClientLevel level, Entity warrior, int count) {
		Vec3 forward = FoeFx.facing(warrior);
		Vec3 side = new Vec3(forward.z, 0, -forward.x);
		Vec3 pack = warrior.position().add(0, 0.85, 0).subtract(forward.scale(0.3));

		for (int i = 0; i < count; i++) {
			Vec3 nozzle = pack.add(side.scale(i % 2 == 0 ? 0.12 : -0.12));
			FoeFx.add(level, FoeFx.engineExhaust(), nozzle, forward.scale(-0.04).add(FoeFx.jitter(0.02), -0.1, FoeFx.jitter(0.02)));
		}
	}

	/** Sparks off the shield's rim (the champion's left side). */
	private static void rimSparks(ClientLevel level, Entity warrior, int count) {
		Vec3 forward = FoeFx.facing(warrior);
		Vec3 left = new Vec3(forward.z, 0, -forward.x);
		Vec3 rim = warrior.position().add(0, 1.15 + FoeFx.RANDOM.nextDouble() * 0.5, 0).add(left.scale(0.55)).subtract(forward.scale(0.15));

		for (int i = 0; i < count; i++) {
			FoeFx.add(level, FoeFx.sparkShower(), rim, left.scale(0.05).add(FoeFx.jitter(0.04), 0.06, FoeFx.jitter(0.04)));
		}
	}

	/** The jet-slam landing: a gout of flame under the pack and the ground's dust thrown out unevenly. */
	private static void slam(Minecraft mc, ClientLevel level, Vec3 at) {
		ParticleOptions flame = FoeFx.byId("flame_jet", ParticleTypes.FLAME);
		ParticleOptions ground = FoeFx.ground(level, at);

		for (int i = 0; i < Vfx.count(6); i++) {
			FoeFx.add(level, flame, at.add(FoeFx.jitter(0.3), 0.2, FoeFx.jitter(0.3)), new Vec3(FoeFx.jitter(0.05), 0.08, FoeFx.jitter(0.05)));
		}

		// Uneven: a few arcs of the circle get the dust, the rest stay clear.
		double start = FoeFx.RANDOM.nextDouble() * Math.PI * 2;

		for (int i = 0; i < Vfx.count(16); i++) {
			double angle = start + (i % 4) * 1.4 + FoeFx.jitter(0.35);
			double speed = 0.12 + FoeFx.RANDOM.nextDouble() * 0.2;
			FoeFx.add(level, i % 3 == 0 ? ParticleTypes.POOF : ground, at.x, at.y + 0.1, at.z, Math.cos(angle) * speed, 0.1 + FoeFx.RANDOM.nextDouble() * 0.15,
					Math.sin(angle) * speed);
		}

		FoeFx.shake(mc, at, 6, 0.35f);
	}

	private HaskFx() {
	}
}

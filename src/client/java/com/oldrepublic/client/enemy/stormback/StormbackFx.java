package com.oldrepublic.client.enemy.stormback;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.hymn.FoeFx;
import com.oldrepublic.client.enemy.hymn.FoeLoops;
import com.oldrepublic.client.enemy.hymn.FoeTimers;
import com.oldrepublic.client.enemy.hymn.FoeWatch;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.stormback.Stormback;
import com.oldrepublic.enemy.stormback.StormbackEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the {@code stormback} event (spec MILESTONE_5.md 15.1, 15.3). The server plays the
 * one-shot sounds; this draws the looks and keeps the crackle:
 *
 * <ul>
 * <li>Charged: a burst of static over its plates; then, while the synced charge lasts, one
 * {@code lightning_spark} per 4 ticks over the plates and the {@code stormback.charged} loop.</li>
 * <li>Charge tell (a tell): 10 {@code gather_dust} pawed up at its forehoof over the tell.</li>
 * <li>Impact: a heavy thud of the ground's dust and grit.</li>
 * <li>Roar tell (a tell): 12 {@code lightning_spark} gathering over its plates over the tell, as it
 * rears and calls the bolt down.</li>
 * <li>Spent: the crackle dies in a few falling sparks and a wisp of smoke.</li>
 * </ul>
 */
public final class StormbackFx {
	private static final double LOOP_RANGE = 24.0;

	public static void init() {
		ClientEffects.register(StormbackEffects.EVENT, StormbackFx::onEvent);
		FoeWatch.watch(Stormback.class, 1, StormbackFx::watchCharged);
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		int ticks = Math.max(1, Math.round(p.intensity()));

		switch (p.variant()) {
			case StormbackEffects.CHARGED -> {
				if (entity != null) {
					plateSparks(level, entity, Vfx.count(8), 0.12);
				}
			}
			case StormbackEffects.CHARGE_TELL -> {
				if (entity != null) {
					FoeTimers.spread(Vfx.tellCount(10), ticks, entity, (lvl, n, age) -> paw(lvl, entity, n));
				}
			}
			case StormbackEffects.IMPACT -> impact(mc, level, at);
			case StormbackEffects.ROAR_TELL -> {
				if (entity != null) {
					FoeTimers.spread(Vfx.tellCount(12), ticks, entity, (lvl, n, age) -> plateSparks(lvl, entity, n, 0.05));
				}
			}
			case StormbackEffects.SPENT -> {
				if (entity != null) {
					for (int i = 0, spawned = Vfx.count(4); i < spawned; i++) {
						FoeFx.add(level, FoeFx.lightningSpark(), plate(entity), new Vec3(FoeFx.jitter(0.03), -0.05, FoeFx.jitter(0.03)));
					}

					FoeFx.add(level, ParticleTypes.SMOKE, plate(entity), new Vec3(0, 0.03, 0));
				}
			}
			default -> {
			}
		}
	}

	/** A random point on its row of plates. */
	private static Vec3 plate(Entity beast) {
		Vec3 forward = FoeFx.facing(beast);
		double along = FoeFx.jitter(0.6);
		return beast.position().add(forward.scale(along)).add(FoeFx.jitter(0.15), beast.getBbHeight() * (0.92 + FoeFx.RANDOM.nextDouble() * 0.08),
				FoeFx.jitter(0.15));
	}

	/** Static over the plates. */
	private static void plateSparks(ClientLevel level, Entity beast, int count, double speed) {
		for (int i = 0; i < count; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), plate(beast), FoeFx.jitterVec(speed).add(0, speed * 0.5, 0));
		}
	}

	/** Grit pawed up at the forehoof (part of the charge tell). */
	private static void paw(ClientLevel level, Entity beast, int count) {
		Vec3 forward = FoeFx.facing(beast);
		Vec3 side = new Vec3(-forward.z, 0, forward.x);
		Vec3 hoof = beast.position().add(forward.scale(0.55)).add(side.scale(0.3)).add(0, 0.05, 0);

		for (int i = 0; i < count; i++) {
			Vec3 v = forward.scale(-0.05 - FoeFx.RANDOM.nextDouble() * 0.05).add(FoeFx.jitter(0.03), 0.08 + FoeFx.RANDOM.nextDouble() * 0.06, FoeFx.jitter(0.03));
			FoeFx.add(level, FoeFx.gatherDust(), hoof.add(FoeFx.jitter(0.15), 0, FoeFx.jitter(0.15)), v);
		}
	}

	/** The charge lands: the ground's dust and grit thrown up, a jolt up close. */
	private static void impact(Minecraft mc, ClientLevel level, Vec3 at) {
		ParticleOptions ground = FoeFx.ground(level, at.subtract(0, 0.9, 0));

		for (int i = 0, spawned = Vfx.count(14); i < spawned; i++) {
			FoeFx.add(level, i % 3 == 0 ? ParticleTypes.POOF : ground, at.add(FoeFx.jitter(0.6), FoeFx.jitter(0.5), FoeFx.jitter(0.6)), FoeFx.jitterVec(0.18));
		}

		for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
			FoeFx.add(level, ParticleTypes.CRIT, at.add(FoeFx.jitterVec(0.3)), FoeFx.jitterVec(0.3));
		}

		FoeFx.shake(mc, at, 5, 0.3f);
	}

	/** While charged: one spark per 4 ticks over the plates, and the crackle's loop. */
	private static void watchCharged(Minecraft mc, ClientLevel level, Stormback beast) {
		if (!beast.isCharged()) {
			return;
		}

		if ((beast.tickCount + beast.getId()) % 4 == 0 && Vfx.inRange(beast.position())) {
			FoeFx.add(level, FoeFx.lightningSpark(), plate(beast), FoeFx.jitterVec(0.05).add(0, 0.03, 0));
		}

		FoeLoops.start("stormback.charged:" + beast.getId(), ModSounds.STORMBACK_CHARGED, SoundSource.NEUTRAL, beast, beast.position(), 0.6f, 1f, LOOP_RANGE,
				beast::isCharged);
	}

	private StormbackFx() {
	}
}

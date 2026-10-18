package com.oldrepublic.client.enemy.droid;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.droid.DroidEffects;
import com.oldrepublic.enemy.droid.SecurityDroid;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of {@code droid_tell} (spec MILESTONE_4.md 13.3): the spin-up's rising whir and a
 * breath of hot air from the barrels (variant 0, a tell, never thinned; the barrels themselves turn
 * in the model); sparks where a bolt strikes the frontal shield, and a clank as it folds out
 * (variant 1); the dying droid's sparking crackle until its small burst (variant 2).
 */
public final class DroidFx {
	private static final RandomSource RANDOM = RandomSource.create();

	/** Dying droids still sparking, to the client tick their burst is due. */
	private static final Map<Integer, Long> SPARKING = new HashMap<>();
	private static long ticks;

	public static void init() {
		ClientEffects.register(DroidEffects.TELL, DroidFx::onTell);
		ClientTickEvents.END_CLIENT_TICK.register(DroidFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SPARKING.clear());
	}

	private static void onTell(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		switch (p.variant()) {
			case DroidEffects.SPIN -> {
				play(level, at, ModSounds.DROID_SPIN_UP, 1f, 1f);

				if (entity instanceof SecurityDroid droid) {
					for (boolean left : new boolean[] {false, true}) {
						Vec3 barrel = droid.barrel(left);

						for (int i = 0; i < Vfx.tellCount(2); i++) {
							level.addParticle(ParticleTypes.SMOKE, barrel.x, barrel.y, barrel.z, jitter(0.01), 0.03, jitter(0.01));
						}
					}
				}
			}
			case DroidEffects.SHIELD -> {
				boolean raised = p.intensity() > 1f;
				play(level, at, ModSounds.DROID_SHIELD, raised ? 1f : 0.6f, raised ? 0.9f : 1.2f + RANDOM.nextFloat() * 0.2f);

				if (Vfx.inRange(at)) {
					for (int i = 0; i < Vfx.count(raised ? 10 : 6); i++) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + jitter(raised ? 0.4 : 0.1), at.y + jitter(raised ? 0.5 : 0.1),
								at.z + jitter(raised ? 0.4 : 0.1), jitter(0.15), RANDOM.nextDouble() * 0.15, jitter(0.15));
					}
				}
			}
			case DroidEffects.DEATH -> {
				if (p.intensity() > 0f) {
					// The tell: sparking and crackling until the burst.
					SPARKING.put(p.entityId(), ticks + Math.round(p.intensity()));
					play(level, at, ModSounds.DROID_HURT, 0.8f, 0.6f);
					return;
				}

				SPARKING.remove(p.entityId());
				level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 0, 0, 0);

				for (int i = 0; i < Vfx.count(14); i++) {
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, jitter(0.4), RANDOM.nextDouble() * 0.4, jitter(0.4));
				}

				for (int i = 0; i < Vfx.count(6); i++) {
					level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + jitter(0.3), at.y, at.z + jitter(0.3), 0, 0.05, 0);
				}
			}
			default -> {
			}
		}
	}

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level == null) {
			SPARKING.clear();
			return;
		}

		for (Iterator<Map.Entry<Integer, Long>> it = SPARKING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity droid = level.getEntity(entry.getKey());

			if (droid == null || ticks > entry.getValue() + 5) {
				it.remove();
				continue;
			}

			// A tell (the burst hurts): never thinned.
			if (ticks % 2 == 0 && Vfx.tellCount(1) > 0) {
				Vec3 at = droid.position().add(jitter(0.4), 0.8 + RANDOM.nextDouble() * 1.2, jitter(0.4));
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, jitter(0.1), 0.1, jitter(0.1));
			}
		}
	}

	private static double jitter(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private DroidFx() {
	}
}

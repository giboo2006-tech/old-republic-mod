package com.oldrepublic.client.enemy.creature;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.creature.BeastEffects;
import com.oldrepublic.enemy.creature.Hollowhorn;
import com.oldrepublic.network.EffectEventPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of {@code beast_tell} (spec MILESTONE_4.md 13.3); the sounds are the server's.
 *
 * <ul>
 * <li>Hollowhorn charge tell (a tell, never thinned): the forehoof scrapes and kicks clods of the
 * ground it stands on, every few ticks for the whole tell. The charge: dust thrown up behind the
 * hooves while it runs. The impact: a burst of dust and clods, and a short shake up close.</li>
 * <li>Ash hound lunge tell (never thinned): ash puffs from under the crouched body. The leap: ash
 * kicked back at take-off. The howl: a breath of ash rising from the muzzle.</li>
 * </ul>
 * Dust spreads unevenly, with gaps: no rings.
 */
public final class BeastFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int SCRAPE_EVERY = 4;

	/** Hollowhorns in a charge tell or a charge: entity id to the client tick the effect ends. */
	private static final Map<Integer, Long> SCRAPING = new HashMap<>();
	private static final Map<Integer, Long> RUNNING = new HashMap<>();
	private static long ticks;

	public static void init() {
		ClientEffects.register(BeastEffects.TELL, BeastFx::onEvent);
		ClientTickEvents.END_CLIENT_TICK.register(BeastFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SCRAPING.clear();
			RUNNING.clear();
		});
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		switch (p.variant()) {
			case BeastEffects.HOLLOWHORN_CHARGE -> SCRAPING.put(p.entityId(), ticks + Math.max(1, Math.round(p.intensity())));
			case BeastEffects.HOLLOWHORN_RUN -> {
				SCRAPING.remove(p.entityId());
				RUNNING.put(p.entityId(), ticks + 60);
			}
			case BeastEffects.HOLLOWHORN_IMPACT -> {
				RUNNING.remove(p.entityId());

				if (!Vfx.inRange(at)) {
					return;
				}

				ParticleOptions clods = ground(level, at);

				for (int i = 0, n = Vfx.count(14); i < n; i++) {
					level.addParticle(ParticleTypes.POOF, at.x + jitter(0.8), at.y + RANDOM.nextDouble() * 0.8, at.z + jitter(0.8), jitter(0.05), 0.04,
							jitter(0.05));
				}

				for (int i = 0, n = Vfx.count(10); i < n; i++) {
					level.addParticle(clods, at.x + jitter(0.6), at.y + 0.2, at.z + jitter(0.6), jitter(0.2), 0.25, jitter(0.2));
				}

				if (mc.player != null && mc.player.position().distanceTo(at) < 6) {
					CameraShake.add(0.25f);
				}
			}
			case BeastEffects.HOUND_LUNGE -> {
				Vec3 feet = entity != null ? entity.position() : at;

				for (int i = 0, n = Vfx.tellCount(5); i < n; i++) {
					level.addParticle(ParticleTypes.WHITE_ASH, feet.x + jitter(0.4), feet.y + 0.1, feet.z + jitter(0.4), jitter(0.02), 0.02, jitter(0.02));
				}
			}
			case BeastEffects.HOUND_LEAP -> {
				Vec3 back = p.dir().lengthSqr() < 1.0e-4 ? Vec3.ZERO : p.dir().normalize().scale(-0.1);

				for (int i = 0, n = Vfx.count(8); i < n; i++) {
					level.addParticle(ParticleTypes.WHITE_ASH, at.x + jitter(0.3), at.y + 0.1, at.z + jitter(0.3), back.x + jitter(0.03), 0.05, back.z + jitter(0.03));
				}
			}
			case BeastEffects.HOUND_HOWL -> {
				if (entity instanceof LivingEntity hound) {
					Vec3 muzzle = hound.getEyePosition().add(hound.getLookAngle().scale(0.4));

					for (int i = 0, n = Vfx.count(8); i < n; i++) {
						level.addParticle(ParticleTypes.ASH, muzzle.x + jitter(0.1), muzzle.y, muzzle.z + jitter(0.1), jitter(0.02), 0.08, jitter(0.02));
					}
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
			SCRAPING.clear();
			RUNNING.clear();
			return;
		}

		for (Iterator<Map.Entry<Integer, Long>> it = SCRAPING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> entry = it.next();

			if (!(level.getEntity(entry.getKey()) instanceof Hollowhorn beast) || ticks > entry.getValue() || !beast.isAlive()) {
				it.remove();
			} else if (ticks % SCRAPE_EVERY == 0) {
				scrape(level, beast);
			}
		}

		for (Iterator<Map.Entry<Integer, Long>> it = RUNNING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> entry = it.next();

			if (!(level.getEntity(entry.getKey()) instanceof Hollowhorn beast) || ticks > entry.getValue() || beast.state() != Hollowhorn.STATE_CHARGING) {
				it.remove();
			} else if (ticks % 2 == 0 && Vfx.inRange(beast.position())) {
				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					level.addParticle(ParticleTypes.POOF, beast.getX() + jitter(0.6), beast.getY() + 0.1, beast.getZ() + jitter(0.6), 0, 0.03, 0);
				}
			}
		}
	}

	/** One scrape of the right forehoof: clods of the ground flicked back (a tell, never thinned). */
	private static void scrape(ClientLevel level, Hollowhorn beast) {
		float yaw = beast.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 hoof = beast.position().add(forward.scale(0.5 * beast.getScale())).add(right.scale(0.25 * beast.getScale()));
		ParticleOptions clods = ground(level, hoof);

		for (int i = 0, n = Vfx.tellCount(4); i < n; i++) {
			Vec3 v = forward.scale(-0.12 - RANDOM.nextDouble() * 0.08).add(jitter(0.04), 0.12 + RANDOM.nextDouble() * 0.08, jitter(0.04));
			level.addParticle(clods, hoof.x, hoof.y + 0.05, hoof.z, v.x, v.y, v.z);
		}
	}

	/** Clods of the block under {@code at}, or plain dirt over air. */
	private static ParticleOptions ground(ClientLevel level, Vec3 at) {
		BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
		return below.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, below);
	}

	private static double jitter(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	private BeastFx() {
	}
}

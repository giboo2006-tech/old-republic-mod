package com.oldrepublic.client.enemy.mandalorian;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.enemy.mandalorian.HunterEffects;
import com.oldrepublic.enemy.mandalorian.HunterRocket;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the hunters' effect events (spec MILESTONE_4.md 13.3), Kaast's included.
 *
 * <ul>
 * <li>{@code hunter_tell}, every variant a tell (never thinned): the jet's rising whine with a
 * flicker of flame at the nozzles; the wrist's pilot flame and hiss; the rocket's targeting beep and
 * the small red dot that sits on the target for the whole tell (the only red light on the field);
 * the net launcher's click; Kaast's shoulder-charge scrape and the glint on his axe.</li>
 * <li>{@code hunter_attack}: the jet hop's gout of flame and smoke (W4's {@code flame_jet} particle,
 * by id); the flamer's cone and its loop, which follows the hunter's synced flaming flag and stops
 * the moment the flame ends, the hunter is staggered or dies, or leaves this client; the rocket's
 * smoky burst; the net's cords flying apart around the caught player.</li>
 * </ul>
 * Rockets in flight trail smoke. Everything ends on disconnect.
 */
public final class HunterFx {
	private static final DustParticleOptions RED_DOT = new DustParticleOptions(0xFF1E1E, 0.45f);
	private static final DustParticleOptions CORD = new DustParticleOptions(0x8C7552, 0.8f);
	private static final RandomSource RANDOM = RandomSource.create();
	/** The flamer cone's reach and half-angle on screen (the server's damage cone is its own). */
	private static final double FLAME_REACH = 3.0;
	private static final double FLAME_SPREAD = 0.28;
	/** Flame motes per tick (30 per second, 13.1; a tell, never thinned). */
	private static final int FLAME_PER_TICK = 2;
	private static final double ROCKET_TRAIL_RANGE = 48;

	/** A red dot on {@code target}, aimed from {@code from} (the hunter's wrist), until client tick {@code until}. */
	private record Dot(int target, Vec3 from, long until) {
	}

	private static final List<Dot> DOTS = new ArrayList<>();
	private static final Map<Integer, PowerLoop> FLAMES = new HashMap<>();
	private static long ticks;

	public static void init() {
		ClientEffects.register(HunterEffects.TELL, HunterFx::onTell);
		ClientEffects.register(HunterEffects.ATTACK, HunterFx::onAttack);
		ClientTickEvents.END_CLIENT_TICK.register(HunterFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	// ---------------------------------------------------------------- tells

	private static void onTell(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		switch (p.variant()) {
			case HunterEffects.TELL_JET -> {
				play(level, at, ModSounds.MANDALORIAN_JET_TELL, 1f, 1f);

				if (entity != null) {
					Vec3 pack = packPosition(entity);

					for (int i = 0, n = Vfx.tellCount(5); i < n; i++) {
						level.addParticle(flameJet(), pack.x + jitter(0.15), pack.y, pack.z + jitter(0.15), 0, -0.04, 0);
					}
				}
			}
			case HunterEffects.TELL_FLAMER -> {
				play(level, at, ModSounds.MANDALORIAN_FLAMER_TELL, 0.9f, 1f);

				for (int i = 0, n = Vfx.tellCount(4); i < n; i++) {
					level.addParticle(ParticleTypes.SMALL_FLAME, at.x + jitter(0.05), at.y + 0.05, at.z + jitter(0.05), 0, 0.01, 0);
				}
			}
			case HunterEffects.TELL_ROCKET -> {
				play(level, at, ModSounds.MANDALORIAN_ROCKET_LOCK, 1f, 1f);
				DOTS.add(new Dot(p.entityId(), at, ticks + Math.max(1, Math.round(p.intensity()))));
			}
			case HunterEffects.TELL_NET -> {
				play(level, at, ModSounds.MANDALORIAN_NET_TELL, 1f, 1f);

				for (int i = 0, n = Vfx.tellCount(3); i < n; i++) {
					level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, jitter(0.1), 0.05, jitter(0.1));
				}
			}
			case HunterEffects.TELL_CHARGE -> {
				// Kaast lowers a shoulder and scrapes the ground: dust kicked back from his boots.
				play(level, at, ModSounds.MANDALORIAN_JET_TELL, 0.7f, 0.6f);
				Vec3 back = p.dir().lengthSqr() < 1.0e-4 ? Vec3.ZERO : p.dir().normalize().scale(-0.12);
				Vec3 feet = entity != null ? entity.position() : at;

				for (int i = 0, n = Vfx.tellCount(8); i < n; i++) {
					level.addParticle(ParticleTypes.POOF, feet.x + jitter(0.4), feet.y + 0.1, feet.z + jitter(0.4), back.x, 0.03, back.z);
				}
			}
			case HunterEffects.TELL_AXE -> {
				// The axe drawn back: a whine and a glint along the raised blade.
				play(level, at, ModSounds.BOSS_KAAST_COMBO, 0.6f, 0.5f);

				if (entity != null) {
					Vec3 blade = entity.position().add(0, entity.getBbHeight() + 0.6, 0);
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, blade.x, blade.y, blade.z, 0, 0.02, 0);
					level.addParticle(ParticleTypes.END_ROD, blade.x, blade.y, blade.z, 0, 0.01, 0);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- attacks

	private static void onAttack(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		switch (p.variant()) {
			case HunterEffects.ATTACK_JET -> {
				play(level, at, ModSounds.MANDALORIAN_JET, 1.1f, 0.95f + RANDOM.nextFloat() * 0.1f);
				Vec3 pack = entity != null ? packPosition(entity) : at;

				if (!Vfx.inRange(pack)) {
					return;
				}

				// 14 flame and smoke, pushed down and back from the nozzles (13.1).
				for (int i = 0, n = Vfx.count(9); i < n; i++) {
					level.addParticle(flameJet(), pack.x + jitter(0.12), pack.y, pack.z + jitter(0.12), jitter(0.05), -0.25 - RANDOM.nextDouble() * 0.15,
							jitter(0.05));
				}

				for (int i = 0, n = Vfx.count(5); i < n; i++) {
					level.addParticle(ParticleTypes.LARGE_SMOKE, pack.x + jitter(0.2), pack.y - 0.2, pack.z + jitter(0.2), jitter(0.03), -0.05, jitter(0.03));
				}
			}
			case HunterEffects.ATTACK_FLAME -> {
				if (entity instanceof MandalorianHunter hunter && !FLAMES.containsKey(hunter.getId())) {
					FLAMES.put(hunter.getId(), PowerLoop.start(ModSounds.MANDALORIAN_FLAMER, SoundSource.HOSTILE, hunter, 0.9f, 1f,
							() -> hunter.isFlaming() && !hunter.isRemoved(), () -> 0));
				}
			}
			case HunterEffects.ATTACK_ROCKET -> {
				play(level, at, ModSounds.MANDALORIAN_EXPLODE, 1.4f, 0.95f + RANDOM.nextFloat() * 0.1f);
				float radius = Math.max(1f, p.intensity());
				level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 0, 0, 0);

				if (Vfx.inRange(at)) {
					for (int i = 0, n = Vfx.count(16); i < n; i++) {
						Vec3 v = new Vec3(jitter(1), RANDOM.nextDouble() * 0.6, jitter(1)).normalize().scale(0.08 * radius);
						level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, v.x, v.y, v.z);
					}
				}

				if (mc.player != null && mc.player.position().distanceTo(at) < radius + 3) {
					CameraShake.add(0.3f);
				}
			}
			case HunterEffects.ATTACK_NET -> {
				play(level, at, ModSounds.MANDALORIAN_NET, 1f, 1f);

				for (int i = 0, n = Vfx.count(12); i < n; i++) {
					level.addParticle(CORD, at.x + jitter(0.5), at.y + jitter(0.8), at.z + jitter(0.5), jitter(0.05), 0.02, jitter(0.05));
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- per tick

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level == null || mc.isPaused()) {
			if (level == null) {
				clear();
			}

			return;
		}

		tickDots(level);
		tickFlames(level);
		tickRockets(mc, level);
	}

	/** The red dot: one mote a tick on the target's chest, on the side facing the hunter. */
	private static void tickDots(ClientLevel level) {
		for (Iterator<Dot> it = DOTS.iterator(); it.hasNext();) {
			Dot dot = it.next();
			Entity target = level.getEntity(dot.target());

			if (target == null || !target.isAlive() || ticks > dot.until()) {
				it.remove();
				continue;
			}

			Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
			Vec3 toward = dot.from().subtract(chest).multiply(1, 0, 1);
			Vec3 front = toward.lengthSqr() < 1.0e-4 ? chest : chest.add(toward.normalize().scale(target.getBbWidth() * 0.55));

			if (Vfx.tellCount(1) > 0) {
				level.addParticle(RED_DOT, front.x, front.y, front.z, 0, 0, 0);
			}
		}
	}

	/** Each lit flamer: its cone of fire from the wrist along the look. The loop ends itself with the flame. */
	private static void tickFlames(ClientLevel level) {
		for (Iterator<Map.Entry<Integer, PowerLoop>> it = FLAMES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, PowerLoop> entry = it.next();

			if (!(level.getEntity(entry.getKey()) instanceof MandalorianHunter hunter) || !hunter.isFlaming() || !hunter.isAlive()) {
				entry.getValue().end();
				it.remove();
				continue;
			}

			Vec3 wrist = hunter.wristPosition();
			Vec3 look = hunter.getLookAngle();

			for (int i = 0, n = Vfx.tellCount(FLAME_PER_TICK); i < n; i++) {
				Vec3 dir = look.add(jitter(FLAME_SPREAD), jitter(FLAME_SPREAD) * 0.6, jitter(FLAME_SPREAD)).normalize();
				Vec3 v = dir.scale(FLAME_REACH / 10.0 * (0.8 + RANDOM.nextDouble() * 0.4));
				level.addParticle(flameJet(), wrist.x, wrist.y, wrist.z, v.x, v.y, v.z);
			}

			if (RANDOM.nextInt(3) == 0) {
				Vec3 end = wrist.add(look.scale(FLAME_REACH));
				level.addParticle(ParticleTypes.SMOKE, end.x + jitter(0.3), end.y + 0.2, end.z + jitter(0.3), 0, 0.04, 0);
			}
		}
	}

	/** Rockets in flight trail a thin line of smoke. */
	private static void tickRockets(Minecraft mc, ClientLevel level) {
		if (mc.player == null) {
			return;
		}

		for (HunterRocket rocket : level.getEntitiesOfClass(HunterRocket.class, mc.player.getBoundingBox().inflate(ROCKET_TRAIL_RANGE))) {
			Vec3 back = rocket.getDeltaMovement().lengthSqr() < 1.0e-6 ? Vec3.ZERO : rocket.getDeltaMovement().normalize().scale(-0.3);

			if (Vfx.count(1) > 0) {
				level.addParticle(ParticleTypes.SMOKE, rocket.getX() + back.x, rocket.getY() + 0.15 + back.y, rocket.getZ() + back.z, 0, 0.01, 0);
			}
		}
	}

	private static void clear() {
		DOTS.clear();
		FLAMES.values().forEach(PowerLoop::end);
		FLAMES.clear();
	}

	// ---------------------------------------------------------------- helpers

	/** Between the jet pack's nozzles, behind and below the shoulders. */
	static Vec3 packPosition(Entity entity) {
		float yaw = (entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot()) * Mth.DEG_TO_RAD;
		double bx = Math.sin(yaw) * 0.32;
		double bz = -Math.cos(yaw) * 0.32;
		return new Vec3(entity.getX() + bx, entity.getY() + entity.getBbHeight() * 0.58, entity.getZ() + bz);
	}

	/** W4's {@code flame_jet} particle, looked up by id (13.2), or vanilla flame if it is missing. */
	private static ParticleOptions flameJet() {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id("flame_jet")) instanceof SimpleParticleType jet ? jet : ParticleTypes.FLAME;
	}

	private static double jitter(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private HunterFx() {
	}
}

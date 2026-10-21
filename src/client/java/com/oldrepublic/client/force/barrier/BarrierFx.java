package com.oldrepublic.client.force.barrier;

import static com.oldrepublic.client.force.kinetic.KineticKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.KineticNoise;
import com.oldrepublic.client.force.kinetic.KineticRender;
import com.oldrepublic.client.force.kinetic.PebbleParticle;
import com.oldrepublic.client.force.kinetic.PressureFront;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.force.KineticParticles;
import com.oldrepublic.force.barrier.BarrierParticles;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Force Barrier visuals and sounds (spec MILESTONE_4.md 9.2, 13.1, 13.3). The sphere is shown only by what it does to the
 * air and the dust: there is no dome, no shell texture and no glow.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_barrier} 0, intensity = radius): {@code force.barrier.start}; the air is shoved out from
 * the body to the sphere (a broken front of bent air) and dust is blown off the ground where the sphere meets it, in uneven
 * puffs. The {@code force.barrier.loop} hum, which for the own caster sags and wavers as Force Energy runs low and stops on
 * every end path (end, pulse and break events, the watchdog, death, removal, level change, disconnect).</li>
 * <li><b>Held</b>: up to 30 {@code held_dust} motes (pale dust and dark grit) and, over grass, a few leaves ride the sphere
 * slowly round the caster wherever it is above the ground, more of them low, where they were lifted from; streaks of air run
 * along its surface and thin dust drifts with them, so the sphere shows as wind wrapped round the caster.</li>
 * <li><b>Impact</b> ({@code force_barrier_impact}, pos = the point on the sphere, dir = outward): the bent-air ripple at the
 * point, a patch of the sphere's air shuddering out from it in broken lines that never close into a ring (kept at Reduced,
 * which shows impact ripples only), dust and streaks thrown along the surface away from the point, bolts fading into
 * sparks, {@code force.barrier.impact}.</li>
 * <li><b>Pulse</b> (2): the held dust and leaves are thrown outward with a front of bent air round the body, dust and grit
 * are blasted off the ground in uneven puffs, streaks race outward and the caster's view kicks a little;
 * {@code force.barrier.pulse}. <b>End</b> (1): the dust falls. <b>Break</b> (3): the dust scatters and falls with a crack
 * of air and {@code force.barrier.break}.</li>
 * </ul>
 */
public final class BarrierFx {
	/** Held motes at Standard. */
	private static final int HELD_MOTES = 30;
	/** Leaves among them over grass. */
	private static final int HELD_LEAVES = 8;
	/** The longest channel (120 ticks and 3 mastery ranks of 10) plus slack. */
	private static final int WATCHDOG = 120 + 30 + 40;
	private static final int TOP_UP_INTERVAL = 8;
	/** Streaks of air along the sphere: a few every this many ticks. */
	private static final int STREAK_INTERVAL = 2;
	private static final int WISP_INTERVAL = 4;
	/** Below this share of the own Force Energy the hum starts to strain. */
	private static final float STRAIN_BELOW = 0.35f;
	private static final float PULSE_SHAKE = 0.1f;

	/** One raised barrier as this client sees it. */
	private static final class Barrier {
		final double radius;
		final long until;
		final int wanted;
		final int leaves;
		/** Streaks per streak interval and dust wisps per wisp interval, counted once when it went up. */
		final int streaks;
		final int wisps;
		/** Which way round the caster the air runs: 1 or -1. */
		final double turn;
		final float[] dust;
		final boolean leafy;
		/** The lowest point of the sphere above the ground, as a pitch from its centre (radians, up positive). */
		final double floor;
		final List<HeldDustParticle> motes = new ArrayList<>();
		@Nullable PowerLoop loop;

		Barrier(double radius, long until, int wanted, int leaves, int streaks, int wisps, float[] dust, boolean leafy, double rise) {
			this.radius = radius;
			this.until = until;
			this.wanted = wanted;
			this.leaves = leaves;
			this.streaks = streaks;
			this.wisps = wisps;
			this.turn = RANDOM.nextBoolean() ? 1 : -1;
			this.dust = dust;
			this.leafy = leafy;
			// The sphere is centred on the chest, so its lower part is in the ground: nothing rides below a hand above it.
			this.floor = Math.asin(Math.max(-1, Math.min(1, -(rise - 0.2) / radius)));
		}
	}

	private static final Map<Integer, Barrier> BARRIERS = new HashMap<>();
	private static long ticks;

	/** {@code force_barrier}: start, end, pulse or break. Entity = the caster, pos = the centre. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		Vec3 pos = payload.pos();

		switch (payload.variant()) {
			case ForceBarrier.VARIANT_START -> {
				FxKit.play(level, pos, ModSounds.FORCE_BARRIER_START, 0.85f, FxKit.pitch(0.04f));

				if (caster != null) {
					start(mc, level, caster, payload.intensity());
				}
			}
			case ForceBarrier.VARIANT_END -> end(level, payload.entityId(), pos, 0.02, false);
			case ForceBarrier.VARIANT_PULSE -> {
				FxKit.play(level, pos, ModSounds.FORCE_BARRIER_PULSE, 1f, FxKit.pitch(0.04f));
				pulse(mc, level, payload.entityId(), caster, pos, payload.intensity());
			}
			case ForceBarrier.VARIANT_BREAK -> {
				FxKit.play(level, pos, ModSounds.FORCE_BARRIER_BREAK, 1f, FxKit.pitch(0.05f));
				end(level, payload.entityId(), pos, 0.09, true);
			}
			default -> {
			}
		}
	}

	/** {@code force_barrier_impact}: something stopped at the sphere. */
	public static void onImpact(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		Vec3 pos = payload.pos();
		Vec3 out = payload.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : payload.dir().normalize();
		boolean heavy = payload.intensity() >= 1f;
		int variant = payload.variant();
		float pitch = variant == ForceBarrier.IMPACT_MELEE ? 0.8f : heavy ? 0.85f : 1.05f;
		FxKit.play(level, pos, ModSounds.FORCE_BARRIER_IMPACT, variant == ForceBarrier.IMPACT_MELEE ? 0.6f : heavy ? 1f : 0.75f, pitch * FxKit.pitch(0.05f));

		if (!Vfx.inRange(pos)) {
			return;
		}

		Barrier barrier = caster == null ? null : BARRIERS.get(caster.getId());
		boolean onSphere = variant != ForceBarrier.IMPACT_MELEE && barrier != null && caster != null;
		float light = KineticKit.light(level, pos);

		// The bent-air ripple at the point of impact (also at Reduced, which shows only this): the sphere's air shudders out
		// from the point in broken lines round it, briefly, in a few sectors so it never closes into a ring.
		if (onSphere) {
			Vec3 centre = FxKit.chest(caster);
			float r = (float) barrier.radius;
			KineticRender.add(new PressureFront(centre, pos.subtract(centre)).spread(heavy ? 34f : 26f).sectors(3, 70f).radius(r * 0.96f, r * 1.12f)
					.life(heavy ? 9 : 7).burst(1.6f).strength(heavy ? 1f : 0.85f).thickness(0.03f).light(light));
		} else {
			KineticRender.add(new PressureFront(pos.subtract(out.scale(0.3)), out).spread(45f).sectors(3, 70f).radius(0.2f, heavy ? 1.1f : 0.8f).life(6)
					.burst(2f).strength(0.8f).thickness(0.03f).light(light));
		}

		if (ClientConfig.get().preset == ClientConfig.Preset.REDUCED) {
			return;
		}

		if (variant == ForceBarrier.IMPACT_BOLT || variant == ForceBarrier.IMPACT_RETURN) {
			// The bolt slows and fades into sparks where it stopped.
			for (int i = 0, n = Vfx.count(heavy ? 6 : 4); i < n; i++) {
				double speed = 0.03 + RANDOM.nextDouble() * 0.06;
				level.addParticle(EnemyParticles.BOLT_IMPACT, pos.x, pos.y, pos.z,
						out.x * speed + FxKit.jitter1(0.05), out.y * speed + FxKit.jitter1(0.05) - 0.02, out.z * speed + FxKit.jitter1(0.05));
			}
		} else if (variant == ForceBarrier.IMPACT_BLAST) {
			// A rocket or grenade bursting at the surface: smoke rolling outward.
			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				Vec3 v = out.scale(0.05 + RANDOM.nextDouble() * 0.05).add(FxKit.jitter(0.03));
				level.addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, v.x, v.y, v.z);
			}
		}

		// Dust and air thrown along the surface away from the point.
		float[] dust = barrier != null ? barrier.dust
				: KineticKit.dustColor(level, caster == null ? null : KineticKit.ground(level, caster.position().subtract(0, 0.2, 0), 2));
		float[] haze = KineticKit.airDust(dust);
		int clouds = Vfx.count(heavy ? 9 : 6);
		int streaks = Vfx.count(heavy ? 6 : 4);

		for (int i = 0; i < clouds; i++) {
			Vec3 along = tangent(out);
			KineticKit.dust(pos.add(KineticKit.jitter(0.12)), along.scale(0.05 + RANDOM.nextDouble() * 0.07).add(out.scale(0.012)), haze,
					0.16f + RANDOM.nextFloat() * 0.12f, 2.4f, 0.5f + RANDOM.nextFloat() * 0.15f, 16 + RANDOM.nextInt(10));
		}

		for (int i = 0; i < streaks; i++) {
			Vec3 along = tangent(out);
			AirStreakParticle streak = KineticKit.streak(pos.add(along.scale(0.15)), along.scale(0.18 + RANDOM.nextDouble() * 0.1), 0.22f, 0.5f, 6);

			if (streak != null) {
				streak.stretch(0.6f).drag(0.86f);
			}
		}
	}

	/** A random direction square to {@code normal}: along the sphere's surface at a point. */
	private static Vec3 tangent(Vec3 normal) {
		Vec3 side = normal.cross(Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 up = side.cross(normal).normalize();
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		return side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
	}

	/** Whether {@code entityId} holds a Barrier, as far as this client knows (for the caster's pose). */
	public static boolean isUp(int entityId) {
		Barrier barrier = BARRIERS.get(entityId);
		return barrier != null && ticks <= barrier.until;
	}

	public static void tick(ClientLevel level) {
		ticks++;

		Iterator<Map.Entry<Integer, Barrier>> it = BARRIERS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Barrier> entry = it.next();
			Barrier barrier = entry.getValue();
			Entity caster = level.getEntity(entry.getKey());

			if (ticks > barrier.until || caster == null || caster.isRemoved() || !caster.isAlive()) {
				dropAll(barrier, 0.02);
				it.remove();
				continue;
			}

			if (!Vfx.inRange(caster.position())) {
				continue;
			}

			if (ticks % TOP_UP_INTERVAL == 0) {
				barrier.motes.removeIf(p -> !p.held());
				spawnMotes(caster, barrier, Math.min(3, barrier.wanted - barrier.motes.size()), false);
			}

			if (ticks % STREAK_INTERVAL == 0) {
				windStreaks(caster, barrier);
			}

			if (ticks % WISP_INTERVAL == 0) {
				windDust(caster, barrier);
			}
		}
	}

	private static void start(Minecraft mc, ClientLevel level, Entity caster, float radius) {
		Barrier old = BARRIERS.remove(caster.getId());

		if (old != null) {
			stop(old, 0.02);
		}

		boolean reduced = ClientConfig.get().preset == ClientConfig.Preset.REDUCED;
		BlockPos ground = KineticKit.ground(level, caster.position().subtract(0, 0.2, 0), 2);
		boolean leafy = FxKit.leafy(level, ground);
		Barrier barrier = new Barrier(Math.max(1, radius), ticks + WATCHDOG, reduced ? 0 : Vfx.count(HELD_MOTES), reduced || !leafy ? 0 : Vfx.count(HELD_LEAVES),
				reduced ? 0 : Vfx.count(2), reduced ? 0 : Vfx.count(1), KineticKit.dustColor(level, ground), leafy, FxKit.chest(caster).y - caster.getY());
		BARRIERS.put(caster.getId(), barrier);
		int id = caster.getId();
		// Both palms out at chest height until the end, pulse or break event (or the same watchdog).
		PowerAnimations.start(id, PowerAnimations.Kind.BARRIER, -1, WATCHDOG);
		boolean own = caster == mc.player;
		barrier.loop = PowerLoop.start(ModSounds.FORCE_BARRIER_LOOP, caster, 0.5f, 1f, () -> isUp(id), () -> own ? strain(mc) : 0);

		if (!Vfx.inRange(caster.position()) || reduced) {
			return;
		}

		spawnMotes(caster, barrier, barrier.wanted, true);
		Vec3 centre = FxKit.chest(caster);
		float light = KineticKit.light(level, centre);
		// The air shoved out from the body to the sphere.
		KineticRender.add(new PressureFront(centre, new Vec3(0, 1, 0)).band(30f, 150f).sectors(4, 60f).radius(0.35f, (float) barrier.radius).life(6)
				.burst(2f).strength(0.75f).light(light));
		// Dust blown off the ground where the sphere meets it, in uneven puffs.
		double rise = centre.y - caster.getY();
		double reach = Math.sqrt(Math.max(0.25, barrier.radius * barrier.radius - rise * rise));
		float[] haze = KineticKit.airDust(barrier.dust);
		float seed = RANDOM.nextFloat() * 40f;
		int puffs = Vfx.count(12);

		for (int i = 0; i < puffs; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 1.8f + seed, (float) Math.sin(angle) * 1.8f, seed) < 0.4f) {
				continue;
			}

			Vec3 flat = KineticKit.flat(angle);
			Vec3 at = caster.position().add(flat.scale(reach * (0.75 + RANDOM.nextDouble() * 0.3))).add(0, 0.12, 0);
			Vec3 v = flat.scale(0.05 + RANDOM.nextDouble() * 0.05).add(flat.cross(new Vec3(0, barrier.turn, 0)).scale(0.03)).add(0, 0.02 + RANDOM.nextDouble() * 0.03, 0);
			DustCloudParticle cloud = KineticKit.dust(at, v, haze, 0.28f + RANDOM.nextFloat() * 0.16f, 2.4f, 0.5f + RANDOM.nextFloat() * 0.15f,
					26 + RANDOM.nextInt(12));

			if (cloud != null) {
				cloud.hold(0.2f).drag(0.9f);
			}
		}
	}

	/** Spawns up to {@code count} held motes on the caster's sphere ({@code withLeaves}: the barrier's leaves too). */
	private static void spawnMotes(Entity caster, Barrier barrier, int count, boolean withLeaves) {
		if (count > 0) {
			for (int i = 0; i < count; i++) {
				HeldDustParticle mote = held(BarrierParticles.HELD_DUST, caster);

				if (mote == null) {
					break;
				}

				hold(mote, caster, barrier, 60 + RANDOM.nextInt(50));
				float shade = 0.85f + RANDOM.nextFloat() * 0.2f;
				// Pale dust and dark grit, so the motes show against the sky and the ground alike.
				float[] rgb = RANDOM.nextInt(3) == 0 ? KineticKit.gritColor(barrier.dust) : KineticKit.airDust(barrier.dust);
				mote.color(Math.min(1f, rgb[0] * shade), Math.min(1f, rgb[1] * shade), Math.min(1f, rgb[2] * shade)).alpha(0.85f);
				float size = 0.09f + RANDOM.nextFloat() * 0.06f;
				mote.size(size, size);
				barrier.motes.add(mote);
			}
		}

		if (!withLeaves) {
			return;
		}

		for (int i = 0; i < barrier.leaves; i++) {
			HeldDustParticle leaf = held(KineticParticles.KINETIC_LEAF, caster);

			if (leaf == null) {
				break;
			}

			leaf.flutter(0.012f);
			// Leaves stay up for the whole channel; they fall with the dust when it ends.
			hold(leaf, caster, barrier, WATCHDOG);
			leaf.color(0.38f + RANDOM.nextFloat() * 0.2f, 0.48f + RANDOM.nextFloat() * 0.12f, 0.2f + RANDOM.nextFloat() * 0.08f).alpha(0.95f);
			float size = 0.11f + RANDOM.nextFloat() * 0.05f;
			leaf.size(size, size);
			barrier.motes.add(leaf);
		}
	}

	private static @Nullable HeldDustParticle held(SimpleParticleType type, Entity caster) {
		Vec3 centre = FxKit.chest(caster);
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(type, centre.x, centre.y, centre.z, 0, 0, 0);
		return particle instanceof HeldDustParticle mote ? mote : null;
	}

	/**
	 * Puts {@code mote} on the sphere above the ground, more of the dust low on it where it was lifted from, drifting the
	 * barrier's way round.
	 */
	private static void hold(HeldDustParticle mote, Entity caster, Barrier barrier, int life) {
		double yaw = RANDOM.nextDouble() * Math.PI * 2;
		double pitch = barrier.floor + (1.15 - barrier.floor) * Math.pow(RANDOM.nextDouble(), 1.6);
		double drift = (0.008 + RANDOM.nextDouble() * 0.014) * barrier.turn;
		mote.hold(caster, barrier.radius * (0.94 + RANDOM.nextDouble() * 0.1), yaw, pitch, drift);
		mote.floor(barrier.floor);
		mote.life(life);
	}

	/** Streaks of air running round the sphere's surface, the barrier's way round, mostly on its lower half. */
	private static void windStreaks(Entity caster, Barrier barrier) {
		Vec3 centre = FxKit.chest(caster);

		for (int i = 0; i < barrier.streaks; i++) {
			Vec3 point = spherePoint(barrier, barrier.floor, barrier.floor + 0.9);
			Vec3 round = new Vec3(-point.z, 0, point.x).normalize().scale(barrier.turn);
			// A little up or down along the surface too, so the lines do not stack into bands.
			Vec3 up = point.normalize().cross(round).normalize().scale((RANDOM.nextDouble() - 0.5) * 0.6);
			Vec3 v = round.add(up).normalize().scale(0.13 + RANDOM.nextDouble() * 0.05);
			AirStreakParticle streak = KineticKit.streak(centre.add(point), v, 0.34f, 0.5f, 7);

			if (streak != null) {
				streak.stretch(0.7f).drag(0.98f);
			}
		}
	}

	/** Thin dust riding the lower sphere with the air. */
	private static void windDust(Entity caster, Barrier barrier) {
		Vec3 centre = FxKit.chest(caster);
		float[] haze = KineticKit.airDust(barrier.dust);

		for (int i = 0; i < barrier.wisps; i++) {
			Vec3 point = spherePoint(barrier, barrier.floor, barrier.floor + 0.35);
			Vec3 round = new Vec3(-point.z, 0, point.x).normalize().scale(barrier.turn);
			DustCloudParticle cloud = KineticKit.dust(centre.add(point), round.scale(0.05), haze, 0.22f + RANDOM.nextFloat() * 0.1f, 2f,
					0.26f + RANDOM.nextFloat() * 0.1f, 22 + RANDOM.nextInt(8));

			if (cloud != null) {
				cloud.hold(0.35f).drag(0.97f);
			}
		}
	}

	/** A point on the barrier's sphere, relative to its centre, between the two pitches (radians, up positive). */
	private static Vec3 spherePoint(Barrier barrier, double pitchFrom, double pitchTo) {
		double yaw = RANDOM.nextDouble() * Math.PI * 2;
		double pitch = pitchFrom + (pitchTo - pitchFrom) * RANDOM.nextDouble();
		double r = barrier.radius * (0.97 + RANDOM.nextDouble() * 0.06);
		return new Vec3(Math.cos(yaw) * Math.cos(pitch) * r, Math.sin(pitch) * r, Math.sin(yaw) * Math.cos(pitch) * r);
	}

	/** The pulse: every held mote is thrown outward with a front of bent air round the body, and the ground is blasted. */
	private static void pulse(Minecraft mc, ClientLevel level, int casterId, @Nullable Entity caster, Vec3 centre, float radius) {
		Barrier barrier = BARRIERS.remove(casterId);
		PowerAnimations.stop(casterId, PowerAnimations.Kind.BARRIER);

		if (barrier != null) {
			for (HeldDustParticle mote : barrier.motes) {
				if (mote.held()) {
					mote.fling(0.25 + RANDOM.nextDouble() * 0.2);
				}
			}

			stop(barrier, 0);
		}

		if (caster != null && caster == mc.player) {
			CameraShake.add(PULSE_SHAKE);
		}

		if (!Vfx.inRange(centre)) {
			return;
		}

		float r = Math.max(1f, radius);
		float light = KineticKit.light(level, centre);
		KineticRender.add(new PressureFront(centre, new Vec3(0, 1, 0)).band(50f, 130f).sectors(4, 65f).flatten(0.8f).radius(r * 0.8f, r + 2.5f).life(8)
				.burst(2.4f).wobble(0.04f).strength(1f).light(light));
		Vec3 feet = caster != null ? caster.position() : centre.subtract(0, 1.1, 0);
		BlockPos ground = KineticKit.ground(level, feet.subtract(0, 0.2, 0), 2);
		float[] dust = barrier != null ? barrier.dust : KineticKit.dustColor(level, ground);
		float[] haze = KineticKit.airDust(dust);
		float[] grit = KineticKit.gritColor(dust);
		BlockState state = ground == null ? null : level.getBlockState(ground);
		float seed = RANDOM.nextFloat() * 40f;
		int clouds = Vfx.count(16);
		int pebbles = Vfx.count(7);
		int streaks = Vfx.count(12);
		int bits = state == null ? 0 : Vfx.count(5);

		for (int i = 0; i < clouds; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2f + seed, (float) Math.sin(angle) * 2f, seed) < 0.36f) {
				continue;
			}

			Vec3 flat = KineticKit.flat(angle);
			Vec3 at = feet.add(flat.scale(r * (0.6 + RANDOM.nextDouble() * 0.5))).add(0, 0.15, 0);
			DustCloudParticle cloud = KineticKit.dust(at, flat.scale(0.14 + RANDOM.nextDouble() * 0.1).add(0, 0.03 + RANDOM.nextDouble() * 0.04, 0), haze,
					0.36f + RANDOM.nextFloat() * 0.2f, 2.6f, 0.55f + RANDOM.nextFloat() * 0.15f, 30 + RANDOM.nextInt(14));

			if (cloud != null) {
				cloud.hold(0.2f).drag(0.86f);
			}
		}

		for (int i = 0; i < pebbles; i++) {
			Vec3 flat = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			KineticKit.pebble(feet.add(flat.scale(r * (0.5 + RANDOM.nextDouble() * 0.4))).add(0, 0.1, 0),
					flat.scale(0.18 + RANDOM.nextDouble() * 0.12).add(0, 0.12 + RANDOM.nextDouble() * 0.12, 0), grit, 0.05f + RANDOM.nextFloat() * 0.05f,
					26 + RANDOM.nextInt(10));
		}

		for (int i = 0; i < bits; i++) {
			Vec3 flat = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			KineticKit.blockBit(level, state, feet.add(flat.scale(r * 0.6)).add(0, 0.1, 0), flat.scale(0.15).add(0, 0.15, 0));
		}

		for (int i = 0; i < streaks; i++) {
			Vec3 flat = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = feet.add(flat.scale(r * 0.9)).add(0, 0.3 + RANDOM.nextDouble() * 1.5, 0);
			AirStreakParticle streak = KineticKit.streak(at, flat.scale(0.4 + RANDOM.nextDouble() * 0.15).add(0, KineticKit.spread(0.04), 0), 0.3f, 0.55f, 7);

			if (streak != null) {
				streak.stretch(0.5f).drag(0.84f);
			}
		}
	}

	/** End or break: the held dust falls (scattering on a break), and a break cracks the air once. */
	private static void end(ClientLevel level, int casterId, Vec3 centre, double scatter, boolean broken) {
		Barrier barrier = BARRIERS.remove(casterId);
		PowerAnimations.stop(casterId, PowerAnimations.Kind.BARRIER);

		if (barrier != null) {
			stop(barrier, scatter);
		}

		if (!broken || !Vfx.inRange(centre)) {
			return;
		}

		// The crack: the sphere's air bursts in broken lines, and a few streaks fly off it.
		float r = barrier != null ? (float) barrier.radius : 2f;
		KineticRender.add(new PressureFront(centre, new Vec3(0, 1, 0)).band(20f, 160f).sectors(4, 60f).radius(r * 0.9f, r + 1.4f).life(5).burst(2.8f)
				.strength(0.9f).light(KineticKit.light(level, centre)));

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 out = KineticKit.jitter(1).normalize();
			AirStreakParticle streak = KineticKit.streak(centre.add(out.scale(r)), out.scale(0.3), 0.25f, 0.5f, 6);

			if (streak != null) {
				streak.stretch(0.5f).drag(0.84f);
			}
		}
	}

	private static void stop(Barrier barrier, double scatter) {
		dropAll(barrier, scatter);

		if (barrier.loop != null) {
			barrier.loop.end();
			barrier.loop = null;
		}
	}

	private static void dropAll(Barrier barrier, double scatter) {
		for (HeldDustParticle mote : barrier.motes) {
			if (mote.held()) {
				mote.drop(scatter * (0.5 + RANDOM.nextDouble()));
			}
		}

		barrier.motes.clear();
	}

	/** 0 while the own Force Energy is above 35%, rising to 1 when it is empty. */
	private static double strain(Minecraft mc) {
		Player player = mc.player;
		ForceData data = player == null ? null : player.getAttached(ForceAttachments.FORCE);

		if (data == null || data.maxEnergy <= 0f) {
			return 0;
		}

		float share = data.energy / data.maxEnergy;
		return share >= STRAIN_BELOW ? 0 : 1 - share / STRAIN_BELOW;
	}

	/** Forgets every barrier and stops every hum (level change, disconnect). */
	public static void clear() {
		for (Barrier barrier : BARRIERS.values()) {
			stop(barrier, 0.02);
		}

		BARRIERS.clear();
	}

	private BarrierFx() {
	}
}

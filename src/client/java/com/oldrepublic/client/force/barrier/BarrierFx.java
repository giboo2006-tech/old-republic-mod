package com.oldrepublic.client.force.barrier;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.EnemyParticles;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Force Barrier visuals and sounds (spec MILESTONE_4.md 9.2, 13.1, 13.3). The sphere is shown only
 * by what it does; there is no dome, shell texture or glow.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_barrier} 0, intensity = radius): {@code force.barrier.start}; up to
 * 24 {@code held_dust} motes (dust from the ground below, a few leaves on grass) drifting slowly
 * around the caster on the sphere; the {@code force.barrier.loop} hum, which for the own caster
 * sags and wavers as Force Energy runs low and stops on every end path (end, pulse and break
 * events, the watchdog, death, removal, level change, disconnect).</li>
 * <li><b>Impact</b> ({@code force_barrier_impact}, pos = the point on the sphere, dir = outward):
 * a bent-air ripple at the point (kept at Reduced, which shows impact ripples only), bolts fading
 * into sparks, 6 dust motes shaken along the surface, {@code force.barrier.impact}.</li>
 * <li><b>Pulse</b> (2): the held dust thrown outward with a ring-free scatter of ripples and
 * {@code force.barrier.pulse}. <b>End</b> (1): the dust falls. <b>Break</b> (3): the dust scatters
 * and falls with a crack of air and {@code force.barrier.break}.</li>
 * </ul>
 */
public final class BarrierFx {
	/** Held motes at Standard. */
	private static final int HELD_MOTES = 24;
	/** The longest channel (120 ticks and 3 mastery ranks of 10) plus slack. */
	private static final int WATCHDOG = 120 + 30 + 40;
	private static final int TOP_UP_INTERVAL = 8;
	/** Below this share of the own Force Energy the hum starts to strain. */
	private static final float STRAIN_BELOW = 0.35f;

	/** One raised barrier as this client sees it. */
	private static final class Barrier {
		final double radius;
		final long until;
		final int wanted;
		final List<HeldDustParticle> motes = new ArrayList<>();
		@Nullable PowerLoop loop;

		Barrier(double radius, long until, int wanted) {
			this.radius = radius;
			this.until = until;
			this.wanted = wanted;
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
			case ForceBarrier.VARIANT_END -> end(payload.entityId(), pos, 0.02, false);
			case ForceBarrier.VARIANT_PULSE -> {
				FxKit.play(level, pos, ModSounds.FORCE_BARRIER_PULSE, 1f, FxKit.pitch(0.04f));
				pulse(payload.entityId(), pos, payload.intensity());
			}
			case ForceBarrier.VARIANT_BREAK -> {
				FxKit.play(level, pos, ModSounds.FORCE_BARRIER_BREAK, 1f, FxKit.pitch(0.05f));
				end(payload.entityId(), pos, 0.09, true);
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

		// The ripple at the point of impact (also at Reduced).
		FxKit.ripple(pos, out.scale(0.01), heavy ? 0.35f : 0.25f, heavy ? 1.2f : 0.8f, 0.2f, 9);

		if (ClientConfig.get().preset == ClientConfig.Preset.REDUCED) {
			return;
		}

		if (variant == ForceBarrier.IMPACT_BOLT || variant == ForceBarrier.IMPACT_RETURN) {
			// The bolt slows and fades into sparks where it stopped.
			for (int i = 0; i < Vfx.count(heavy ? 6 : 4); i++) {
				double speed = 0.03 + RANDOM.nextDouble() * 0.06;
				level.addParticle(EnemyParticles.BOLT_IMPACT, pos.x, pos.y, pos.z,
						out.x * speed + FxKit.jitter1(0.05), out.y * speed + FxKit.jitter1(0.05) - 0.02, out.z * speed + FxKit.jitter1(0.05));
			}
		} else if (variant == ForceBarrier.IMPACT_BLAST) {
			// A rocket or grenade bursting at the surface: smoke rolling outward.
			for (int i = 0; i < Vfx.count(6); i++) {
				Vec3 v = out.scale(0.05 + RANDOM.nextDouble() * 0.05).add(FxKit.jitter(0.03));
				level.addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, v.x, v.y, v.z);
			}
		}

		// Dust shaken along the surface around the point.
		Vec3 side = out.cross(new Vec3(0, 1, 0));

		if (side.lengthSqr() < 1.0e-4) {
			side = new Vec3(1, 0, 0);
		}

		side = side.normalize();
		float[] rgb = FxKit.dustColor(level, caster == null ? null : FxKit.groundBelow(level, caster.position().subtract(0, 0.2, 0), 2));

		for (int i = 0; i < Vfx.count(6); i++) {
			Vec3 v = side.scale(FxKit.jitter1(0.08)).add(0, FxKit.jitter1(0.04), 0).add(out.scale(0.01));
			FxKit.dust(pos.add(FxKit.jitter(0.15)), v, rgb, 0.06f, 0.6f, 14 + RANDOM.nextInt(6), 0.01f);
		}
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

			if (ticks % TOP_UP_INTERVAL == 0) {
				barrier.motes.removeIf(p -> !p.held());

				if (Vfx.inRange(caster.position())) {
					spawnMotes(level, caster, barrier, Math.min(3, barrier.wanted - barrier.motes.size()));
				}
			}
		}
	}

	private static void start(Minecraft mc, ClientLevel level, Entity caster, float radius) {
		Barrier old = BARRIERS.remove(caster.getId());

		if (old != null) {
			stop(old, 0.02);
		}

		boolean reduced = ClientConfig.get().preset == ClientConfig.Preset.REDUCED;
		Barrier barrier = new Barrier(Math.max(1, radius), ticks + WATCHDOG, reduced ? 0 : Vfx.count(HELD_MOTES));
		BARRIERS.put(caster.getId(), barrier);
		int id = caster.getId();
		// Both palms out at chest height until the end, pulse or break event (or the same watchdog).
		PowerAnimations.start(id, PowerAnimations.Kind.BARRIER, -1, WATCHDOG);
		boolean own = caster == mc.player;
		barrier.loop = PowerLoop.start(ModSounds.FORCE_BARRIER_LOOP, caster, 0.5f, 1f, () -> isUp(id), () -> own ? strain(mc) : 0);

		if (Vfx.inRange(caster.position())) {
			spawnMotes(level, caster, barrier, barrier.wanted);
		}
	}

	/** Spawns up to {@code count} held motes on the caster's sphere. */
	private static void spawnMotes(ClientLevel level, Entity caster, Barrier barrier, int count) {
		if (count <= 0) {
			return;
		}

		BlockPos ground = FxKit.groundBelow(level, caster.position().subtract(0, 0.2, 0), 2);
		float[] rgb = FxKit.dustColor(level, ground);
		boolean leafy = FxKit.leafy(level, ground);
		Vec3 centre = FxKit.chest(caster);

		for (int i = 0; i < count; i++) {
			Particle particle = Minecraft.getInstance().particleEngine.createParticle(BarrierParticles.HELD_DUST, centre.x, centre.y, centre.z, 0, 0, 0);

			if (!(particle instanceof HeldDustParticle mote)) {
				return;
			}

			double yaw = RANDOM.nextDouble() * Math.PI * 2;
			// Most of the dust low on the sphere, where it was lifted from.
			double pitch = -0.9 + RANDOM.nextDouble() * RANDOM.nextDouble() * 2.0;
			double drift = (0.006 + RANDOM.nextDouble() * 0.012) * (RANDOM.nextBoolean() ? 1 : -1);
			mote.hold(caster, barrier.radius * (0.94 + RANDOM.nextDouble() * 0.1), yaw, pitch, drift);
			mote.life(60 + RANDOM.nextInt(50));

			if (leafy && RANDOM.nextInt(4) == 0) {
				// A leaf: larger, green-brown.
				mote.color(0.42f + RANDOM.nextFloat() * 0.12f, 0.5f + RANDOM.nextFloat() * 0.1f, 0.24f).size(0.1f, 0.1f);
			} else {
				float shade = 0.85f + RANDOM.nextFloat() * 0.2f;
				mote.color(rgb[0] * shade, rgb[1] * shade, rgb[2] * shade).size(0.05f + RANDOM.nextFloat() * 0.03f, 0.06f);
			}

			barrier.motes.add(mote);
		}
	}

	/** The pulse: every held mote is thrown outward, with a scatter of ripples (never a ring). */
	private static void pulse(int casterId, Vec3 centre, float radius) {
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

		if (!Vfx.inRange(centre)) {
			return;
		}

		for (int i = 0; i < Vfx.count(8); i++) {
			Vec3 out = FxKit.randomFlat();
			double reach = radius * (0.6 + RANDOM.nextDouble() * 0.5);
			FxKit.ripple(centre.add(out.scale(reach)).add(0, FxKit.jitter1(0.6), 0), out.scale(0.08 + RANDOM.nextDouble() * 0.05), 0.3f, 1.1f, 0.14f, 10);
		}
	}

	/** End or break: the held dust falls (scattering on a break), and a break cracks the air once. */
	private static void end(int casterId, Vec3 centre, double scatter, boolean broken) {
		Barrier barrier = BARRIERS.remove(casterId);
		PowerAnimations.stop(casterId, PowerAnimations.Kind.BARRIER);

		if (barrier != null) {
			stop(barrier, scatter);
		}

		if (broken && Vfx.inRange(centre)) {
			for (int i = 0; i < Vfx.count(5); i++) {
				FxKit.ripple(centre.add(FxKit.jitter(0.8)), FxKit.jitter(0.05), 0.4f, 1.0f, 0.18f, 7);
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

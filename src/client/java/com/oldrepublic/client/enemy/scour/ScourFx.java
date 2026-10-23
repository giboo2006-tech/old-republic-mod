package com.oldrepublic.client.enemy.scour;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.hymn.FoeFx;
import com.oldrepublic.client.enemy.hymn.FoeLoops;
import com.oldrepublic.client.enemy.hymn.FoeTimers;
import com.oldrepublic.client.enemy.hymn.FoeWatch;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.scour.SandScour;
import com.oldrepublic.enemy.scour.ScourEffects;
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
 * Client side of the {@code scour} event (spec MILESTONE_5.md 15.1, 15.3). The server plays the
 * one-shot sounds; this draws the looks and keeps the hiss:
 *
 * <ul>
 * <li>Ripple: a faint stir of sand along an uneven line over the buried scour (never a ring). The
 * {@code scour.hiss} loop plays while it lies burrowed within 6 blocks of the listener.</li>
 * <li>Burst tell (a tell): 15 {@code sand_burst} boiling up over its spot across the tell.</li>
 * <li>Burst: 30 {@code sand_burst} thrown up and out with the ground's dust, a jolt up close.</li>
 * <li>Burrow: the sand slumps in over it from all sides, in clumps.</li>
 * <li>The matriarch's sweep tell (a tell): 12 {@code sand_burst} rising along the arc her tail will
 * cover (behind and beside her) over the tell; the sweep: dust thrown out along that arc.</li>
 * <li>The brood call: the sand churns in two or three spots around her.</li>
 * </ul>
 */
public final class ScourFx {
	/** The hiss is heard within this many blocks of a buried scour (12.1). */
	private static final double HISS_RANGE = 6.0;

	public static void init() {
		ClientEffects.register(ScourEffects.EVENT, ScourFx::onEvent);
		FoeWatch.watch(SandScour.class, 5, ScourFx::watchHiss);
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		int ticks = Math.max(1, Math.round(p.intensity()));
		float size = entity instanceof SandScour scour && scour.isMatriarch() ? 1.6f : 1f;

		switch (p.variant()) {
			case ScourEffects.RIPPLE -> ripple(level, at, size);
			case ScourEffects.BURST_TELL -> FoeTimers.spread(Vfx.tellCount(15), ticks, entity, (lvl, n, age) -> boil(lvl, entity != null ? entity.position() : at, size, n));
			case ScourEffects.BURST -> burst(mc, level, at, size);
			case ScourEffects.BURROW -> burrow(level, at, size);
			case ScourEffects.SWEEP_TELL -> {
				Vec3 facing = FoeFx.flat(p.dir(), entity != null ? FoeFx.facing(entity) : new Vec3(0, 0, 1));
				FoeTimers.spread(Vfx.tellCount(12), ticks, entity, (lvl, n, age) -> arcSand(lvl, entity != null ? entity.position() : at, facing, n, 0.12, 0.08));
			}
			case ScourEffects.SWEEP -> {
				Vec3 facing = FoeFx.flat(p.dir(), entity != null ? FoeFx.facing(entity) : new Vec3(0, 0, 1));
				arcSand(level, at, facing, Vfx.count(24), 0.2, 0.22);
				FoeFx.shake(mc, at, 6, 0.2f);
			}
			case ScourEffects.CALL -> call(level, at);
			default -> {
			}
		}
	}

	/** A faint stir along a short, wavering line over it. */
	private static void ripple(ClientLevel level, Vec3 at, float size) {
		double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
		Vec3 along = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		Vec3 across = new Vec3(-along.z, 0, along.x);

		for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
			double t = (FoeFx.RANDOM.nextDouble() * 2 - 1) * 0.9 * size;
			Vec3 spot = at.add(along.scale(t)).add(across.scale(Math.sin(t * 3) * 0.2)).add(0, 0.05, 0);
			FoeFx.add(level, FoeFx.sandBurst(), spot, new Vec3(FoeFx.jitter(0.01), 0.04 + FoeFx.RANDOM.nextDouble() * 0.04, FoeFx.jitter(0.01)));
		}
	}

	/** The sand boiling over its spot (part of the tell). */
	private static void boil(ClientLevel level, Vec3 at, float size, int count) {
		for (int i = 0; i < count; i++) {
			Vec3 spot = at.add(FoeFx.jitter(0.6 * size), 0.05, FoeFx.jitter(0.6 * size));
			FoeFx.add(level, FoeFx.sandBurst(), spot, new Vec3(FoeFx.jitter(0.04), 0.14 + FoeFx.RANDOM.nextDouble() * 0.16, FoeFx.jitter(0.04)));
		}
	}

	/** It erupts: sand thrown up and out, the ground's dust, a jolt for a player up close. */
	private static void burst(Minecraft mc, ClientLevel level, Vec3 at, float size) {
		ParticleOptions ground = FoeFx.ground(level, at);

		for (int i = 0, spawned = Vfx.count(30); i < spawned; i++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = (0.06 + FoeFx.RANDOM.nextDouble() * 0.16) * size;
			FoeFx.add(level, i % 4 == 0 ? ground : FoeFx.sandBurst(), at.x + FoeFx.jitter(0.3), at.y + 0.1, at.z + FoeFx.jitter(0.3),
					Math.cos(angle) * speed, 0.25 + FoeFx.RANDOM.nextDouble() * 0.25, Math.sin(angle) * speed);
		}

		FoeFx.shake(mc, at, 4 * size, 0.25f);
	}

	/** The sand slumps in over it from all sides, in clumps. */
	private static void burrow(ClientLevel level, Vec3 at, float size) {
		for (int i = 0, spawned = Vfx.count(14); i < spawned; i++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double r = (0.7 + FoeFx.RANDOM.nextDouble() * 0.5) * size;
			Vec3 from = at.add(Math.cos(angle) * r, 0.25, Math.sin(angle) * r);
			Vec3 inward = at.subtract(from).scale(0.08);
			FoeFx.add(level, FoeFx.sandBurst(), from, new Vec3(inward.x, 0.05, inward.z));
		}

		for (int i = 0, spawned = Vfx.count(4); i < spawned; i++) {
			FoeFx.add(level, ParticleTypes.POOF, at.add(FoeFx.jitter(0.4 * size), 0.1, FoeFx.jitter(0.4 * size)), new Vec3(0, 0.01, 0));
		}
	}

	/**
	 * Sand rising along the arc behind and beside her (the tail's reach, 4 blocks): from beside her
	 * on one side, around the back, to the other side.
	 */
	private static void arcSand(ClientLevel level, Vec3 centre, Vec3 facing, int count, double spread, double lift) {
		double base = Math.atan2(facing.z, facing.x);

		for (int i = 0; i < count; i++) {
			// From 90 to 270 degrees off her facing: beside, behind, beside.
			double angle = base + Math.PI * 0.5 + FoeFx.RANDOM.nextDouble() * Math.PI;
			double r = 1.5 + FoeFx.RANDOM.nextDouble() * 2.5;
			Vec3 spot = centre.add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(spread * FoeFx.RANDOM.nextDouble());
			FoeFx.add(level, FoeFx.sandBurst(), spot, new Vec3(out.x, lift + FoeFx.RANDOM.nextDouble() * 0.12, out.z));
		}
	}

	/** The brood called up: the sand churns in two or three spots around her. */
	private static void call(ClientLevel level, Vec3 at) {
		int spots = 2 + FoeFx.RANDOM.nextInt(2);

		for (int s = 0; s < spots; s++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			Vec3 spot = at.add(Math.cos(angle) * 3, 0.05, Math.sin(angle) * 3);

			for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
				FoeFx.add(level, FoeFx.sandBurst(), spot.add(FoeFx.jitter(0.5), 0, FoeFx.jitter(0.5)), new Vec3(FoeFx.jitter(0.03), 0.12 + FoeFx.RANDOM.nextDouble() * 0.1,
						FoeFx.jitter(0.03)));
			}
		}
	}

	/** The hiss of a scour lying burrowed near the player (it stops when it bursts out or the player moves off). */
	private static void watchHiss(Minecraft mc, ClientLevel level, SandScour scour) {
		if (mc.player == null || !scour.isBurrowed() || mc.player.distanceToSqr(scour) > HISS_RANGE * HISS_RANGE) {
			return;
		}

		FoeLoops.start("scour.hiss:" + scour.getId(), ModSounds.SCOUR_HISS, SoundSource.HOSTILE, scour, scour.position(), 0.6f,
				scour.isMatriarch() ? 0.7f : 1f, HISS_RANGE + 0.5, scour::isBurrowed);
	}

	private ScourFx() {
	}
}

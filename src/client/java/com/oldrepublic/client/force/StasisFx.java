package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.kinetic.KineticKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.PebbleParticle;
import com.oldrepublic.client.force.particle.StasisRimeParticle;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.KineticParticles;
import com.oldrepublic.force.stasis.ForceStasis;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Stasis visuals and sounds (MILESTONE_5.md 9.3, 15.1, 15.3): rime and still air, never a shell, a ring or a glow.
 * The moment round the held creature stops.
 *
 * <ul>
 * <li><b>Cast</b> ({@code force_stasis} 0 on the target, the caster packed in the variant): the caster's palm goes out and
 * holds ({@link PowerAnimations.Kind#STASIS}); {@code force.stasis.cast}, a glassy chime, rings at the target.</li>
 * <li><b>Held</b> (1, on each creature held; intensity = seconds): hoarfrost grows over the body in uneven crusts,
 * thickest on the head, the shoulders and the upper body, where frost settles; the dust the jolt knocked up round its feet
 * stops dead in the air, grit and pebbles hang where they were thrown, and fine frost motes hang motionless round it; two
 * small patches of bent air on its body stop moving (the frozen heat haze); the {@code force.stasis.loop} hum plays on it
 * until the stasis ends.</li>
 * <li><b>Break</b> (2: the stasis ended for any reason the server saw: a hit, its time, milk, {@code /effect clear}): the
 * frost cracks off in splinters, what is left loses its frost and falls as grey grit, a breath of frost dust puffs off
 * the body, the frozen dust and pebbles fall again, and {@code force.stasis.break} cracks. The creature's death or
 * removal, a level change, a disconnect or a lost event (the watchdog) let everything fall too, without the sound or the
 * splinters.</li>
 * </ul>
 */
public final class StasisFx {
	/** Crusts of rime on a held creature at Standard. */
	private static final int RIME = 40;
	/** Frost motes hanging in the air round it. */
	private static final int MOTES = 14;
	/** Dust clouds and pebbles caught in mid-air round its feet. */
	private static final int DUST = 7;
	private static final int PEBBLES = 6;
	/** Still patches of bent air on a held creature (the frozen haze). */
	private static final int HAZE = 2;
	private static final int CAST_POSE_TICKS = 20;
	private static final int SETTLE_TICKS = 6;
	/** Ticks past the stasis's length before a lost break event is assumed. */
	private static final int WATCHDOG_SLACK = 20;
	/** The frost's colour: a cold white, lit by the world. */
	private static final float[] FROST = {0.88f, 0.94f, 1f};

	/** A creature held now: the watchdog end, what hangs on it and round it, and its loop. */
	private static final class Held {
		long until;
		final List<StasisRimeParticle> rime = new ArrayList<>();
		final List<DustCloudParticle> dust = new ArrayList<>();
		final List<PebbleParticle> pebbles = new ArrayList<>();
		@Nullable PowerLoop loop;

		Held(long until) {
			this.until = until;
		}

		/** Keeps everything up for {@code ticks} more (a fresh stasis on the same creature). */
		void extend(int ticks) {
			for (StasisRimeParticle mote : rime) {
				mote.extend(ticks);
			}

			for (DustCloudParticle cloud : dust) {
				if (cloud.frozen()) {
					cloud.freeze(0, ticks);
				}
			}

			for (PebbleParticle pebble : pebbles) {
				pebble.extend(ticks);
			}
		}

		/**
		 * Lets everything go and stops the hum: {@code cracked} on a break the server saw, when a share of the frost flies
		 * off from {@code centre} as splinters; the rest loses its frost and falls as grit.
		 */
		void end(boolean cracked, Vec3 centre) {
			for (StasisRimeParticle mote : rime) {
				if (cracked && RANDOM.nextInt(3) == 0) {
					Vec3 out = KineticKit.horizontal(mote.at().subtract(centre)).scale(0.06 + RANDOM.nextDouble() * 0.08);
					mote.burst(out.x, 0.04 + RANDOM.nextDouble() * 0.06, out.z);
				} else {
					mote.shatter();
				}
			}

			for (DustCloudParticle cloud : dust) {
				cloud.thaw();
			}

			for (PebbleParticle pebble : pebbles) {
				pebble.drop(KineticKit.spread(0.02), 0, KineticKit.spread(0.02));
			}

			rime.clear();
			dust.clear();
			pebbles.clear();

			if (loop != null) {
				loop.end();
				loop = null;
			}
		}
	}

	private static final Map<Integer, Held> HELD = new HashMap<>();
	private static long ticks;

	/** {@code force_stasis}: cast, held or break. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		switch (ForceEffects.valueOf(payload.variant())) {
			case ForceStasis.VARIANT_CAST -> cast(level, payload, target);
			case ForceStasis.VARIANT_HELD -> hold(level, payload, target);
			case ForceStasis.VARIANT_BREAK -> release(level, payload, target);
			default -> {
			}
		}
	}

	private static void cast(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		int caster = ForceEffects.casterOf(payload.variant());

		if (caster > 0) {
			PowerAnimations.start(caster, PowerAnimations.Kind.STASIS, CAST_POSE_TICKS, CAST_POSE_TICKS);
		}

		FxKit.play(level, target != null ? FxKit.chest(target) : payload.pos(), ModSounds.FORCE_STASIS_CAST, 0.9f, FxKit.pitch(0.03f));
	}

	private static void hold(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		int id = payload.entityId();
		int length = Math.max(1, Math.round(payload.intensity() * 20f));
		Held held = HELD.get(id);

		if (held != null) {
			// A fresh stasis on a creature already held: everything stays where it hangs, for the new length.
			held.until = ticks + length + WATCHDOG_SLACK;
			held.extend(length + WATCHDOG_SLACK);
			return;
		}

		held = new Held(ticks + length + WATCHDOG_SLACK);
		HELD.put(id, held);

		if (target == null) {
			return;
		}

		if (Vfx.inRange(target.position())) {
			settle(level, target, held, length + WATCHDOG_SLACK);
		}

		held.loop = PowerLoop.start(ModSounds.FORCE_STASIS_LOOP, target, 0.45f, 1f, () -> isHeld(id), () -> 0);
	}

	/** The moment stops: frost grows over the body, the dust and grit round the feet stop dead, frost motes hang still. */
	private static void settle(ClientLevel level, Entity target, Held held, int ticks) {
		int rime = Vfx.count(RIME);
		int motes = Vfx.count(MOTES);
		int clouds = Vfx.count(DUST);
		int pebbles = Vfx.count(PEBBLES);

		for (int i = 0; i < rime; i++) {
			Vec3 offset = frostPoint(target);
			Vec3 from = target.position().add(offset).add(KineticKit.jitter(0.25)).add(0, 0.15, 0);

			if (KineticKit.spawn(KineticParticles.KINETIC_RIME, from, Vec3.ZERO) instanceof StasisRimeParticle crust) {
				crust.hold(target, offset, SETTLE_TICKS + RANDOM.nextInt(8), ticks);
				float size = 0.08f + RANDOM.nextFloat() * 0.08f + (offset.y > target.getBbHeight() * 0.7 ? 0.03f : 0f);
				float shade = 0.92f + RANDOM.nextFloat() * 0.08f;
				crust.color(FROST[0] * shade, FROST[1] * shade, FROST[2] * shade).size(size, size).alpha(0.92f);
				held.rime.add(crust);
			}
		}

		for (int i = 0; i < motes; i++) {
			// Fine frost hanging in the still air a little way off the body.
			Vec3 offset = bodyPoint(target, 0.12 + RANDOM.nextDouble() * 0.5);

			if (KineticKit.spawn(KineticParticles.KINETIC_SHARD, target.position().add(offset).add(0, 0.3, 0), Vec3.ZERO) instanceof StasisRimeParticle mote) {
				mote.hold(target, offset, SETTLE_TICKS + RANDOM.nextInt(10), ticks);
				float size = 0.035f + RANDOM.nextFloat() * 0.035f;
				mote.color(FROST[0], FROST[1], FROST[2]).size(size, size).alpha(0.85f);
				held.rime.add(mote);
			}
		}

		BlockPos ground = KineticKit.ground(level, target.position().add(0, 0.3, 0), 3);

		if (ground != null) {
			// The jolt knocks dust and grit up off the ground round its feet, and they stop dead in mid-air.
			float[] dust = KineticKit.dustColor(level, ground);
			float[] haze = KineticKit.airDust(dust);
			float[] grit = KineticKit.gritColor(dust);
			double top = ground.getY() + 1.02;
			double reach = target.getBbWidth() * 0.5 + 0.2;

			for (int i = 0; i < clouds; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				Vec3 at = new Vec3(target.getX() + out.x * (reach + RANDOM.nextDouble() * 0.5), top + 0.05, target.getZ() + out.z * (reach + RANDOM.nextDouble() * 0.5));
				DustCloudParticle cloud = KineticKit.dust(at, out.scale(0.04 + RANDOM.nextDouble() * 0.04).add(0, 0.06 + RANDOM.nextDouble() * 0.07, 0), haze,
						0.2f + RANDOM.nextFloat() * 0.14f, 2.4f, 0.5f + RANDOM.nextFloat() * 0.12f, 40);

				if (cloud != null) {
					cloud.freeze(3 + RANDOM.nextInt(4), ticks).hold(0.3f).drag(0.86f);
					held.dust.add(cloud);
				}
			}

			for (int i = 0; i < pebbles; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				Vec3 at = new Vec3(target.getX() + out.x * (reach + RANDOM.nextDouble() * 0.6), top + 0.04, target.getZ() + out.z * (reach + RANDOM.nextDouble() * 0.6));
				PebbleParticle pebble = KineticKit.pebble(at, Vec3.ZERO, grit, 0.045f + RANDOM.nextFloat() * 0.05f, ticks + 30);

				if (pebble != null) {
					pebble.hover(ticks, 0.2 + RANDOM.nextDouble() * 0.9).still();
					held.pebbles.add(pebble);
				}
			}
		}

		for (int i = 0, n = Vfx.count(HAZE); i < n; i++) {
			// A small ripple that does not grow or move: the haze has stopped.
			float size = 0.18f + RANDOM.nextFloat() * 0.12f;
			FxKit.ripple(target.position().add(bodyPoint(target, 0.04)), Vec3.ZERO, size, size, 0.12f, Math.min(ticks, 60));
		}
	}

	/**
	 * Where a crust of rime sits on {@code target}, relative to its feet: on the top of the head and the shoulders, where
	 * frost settles, or anywhere on its sides, mostly the upper body.
	 */
	private static Vec3 frostPoint(Entity target) {
		double width = target.getBbWidth();
		double height = target.getBbHeight();

		if (RANDOM.nextInt(3) == 0) {
			// The top of the head or the shoulders.
			boolean head = RANDOM.nextBoolean();
			double y = head ? height * (0.98 + RANDOM.nextDouble() * 0.04) : height * (0.74 + RANDOM.nextDouble() * 0.04);
			double side = head ? KineticKit.spread(width * 0.35) : (RANDOM.nextBoolean() ? 1 : -1) * width * (0.45 + RANDOM.nextDouble() * 0.25);
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 flat = KineticKit.flat(angle);
			Vec3 across = new Vec3(-flat.z, 0, flat.x);
			return flat.scale(KineticKit.spread(width * 0.2)).add(across.scale(side)).add(0, y, 0);
		}

		double y = height * (0.1 + 0.88 * Math.sqrt(RANDOM.nextDouble()));
		return bodyPoint(target, 0.03).multiply(1, 0, 1).add(0, y, 0);
	}

	/** A point just off the surface of {@code target}'s box ({@code off} blocks out), relative to its feet. */
	private static Vec3 bodyPoint(Entity target, double off) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double radius = target.getBbWidth() * (0.5 + RANDOM.nextDouble() * 0.12) + off;
		double height = target.getBbHeight() * (0.08 + RANDOM.nextDouble() * 0.88);
		return new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
	}

	private static void release(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Held held = HELD.remove(payload.entityId());
		Vec3 at = target != null ? FxKit.chest(target) : payload.pos();
		boolean drawn = held != null && !held.rime.isEmpty();

		if (held != null) {
			held.end(true, at);
		}

		if (target != null && Vfx.inRange(at)) {
			crack(target, drawn);
		}

		FxKit.play(level, at, ModSounds.FORCE_STASIS_BREAK, 0.85f, FxKit.pitch(0.05f));
	}

	/**
	 * The break: splinters of frost fly off the body and a breath of frost dust puffs off it; when this client never saw
	 * the frost settle (the creature came into range meanwhile), grey grit still falls from it.
	 */
	private static void crack(Entity target, boolean drawn) {
		int splinters = Vfx.count(10);
		int breath = Vfx.count(5);

		for (int i = 0; i < splinters; i++) {
			Vec3 offset = frostPoint(target);
			Vec3 out = KineticKit.horizontal(offset.multiply(1, 0, 1)).scale(0.08 + RANDOM.nextDouble() * 0.1);

			if (KineticKit.spawn(KineticParticles.KINETIC_SHARD, target.position().add(offset), Vec3.ZERO) instanceof StasisRimeParticle splinter) {
				float size = 0.07f + RANDOM.nextFloat() * 0.06f;
				splinter.color(FROST[0], FROST[1], FROST[2]).size(size, size).alpha(0.95f);
				splinter.burst(out.x, 0.05 + RANDOM.nextDouble() * 0.08, out.z);
			}
		}

		for (int i = 0; i < breath; i++) {
			Vec3 offset = bodyPoint(target, 0.05);
			Vec3 out = KineticKit.horizontal(offset).scale(0.02 + RANDOM.nextDouble() * 0.03);
			DustCloudParticle cloud = KineticKit.dust(target.position().add(offset), out.add(0, 0.008, 0), FROST, 0.25f + RANDOM.nextFloat() * 0.15f, 2.4f,
					0.32f + RANDOM.nextFloat() * 0.1f, 22 + RANDOM.nextInt(8));

			if (cloud != null) {
				cloud.hold(0.2f).lift(0.002f).drag(0.9f);
			}
		}

		if (!drawn) {
			for (int i = 0, n = Vfx.count(RIME / 2); i < n; i++) {
				if (KineticKit.spawn(KineticParticles.KINETIC_RIME, target.position().add(frostPoint(target)), Vec3.ZERO) instanceof StasisRimeParticle grit) {
					float size = 0.05f + RANDOM.nextFloat() * 0.05f;
					grit.size(size, size);
					grit.shatter();
				}
			}
		}
	}

	/** Whether {@code entityId} is held in stasis, as far as this client knows (for the hum). */
	public static boolean isHeld(int entityId) {
		Held held = HELD.get(entityId);
		return held != null && ticks <= held.until;
	}

	public static void tick(ClientLevel level) {
		ticks++;

		if (HELD.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Held>> it = HELD.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Held> entry = it.next();
			Entity target = level.getEntity(entry.getKey());

			if (ticks > entry.getValue().until || target == null || target.isRemoved() || !target.isAlive()) {
				entry.getValue().end(false, target != null ? FxKit.chest(target) : Vec3.ZERO);
				it.remove();
			}
		}
	}

	/** Forgets every stasis and stops every hum (level change, disconnect). */
	public static void clear() {
		for (Held held : HELD.values()) {
			held.end(false, Vec3.ZERO);
		}

		HELD.clear();
	}

	private StasisFx() {
	}
}

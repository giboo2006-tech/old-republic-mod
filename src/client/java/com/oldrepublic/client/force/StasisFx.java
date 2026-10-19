package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.StasisRimeParticle;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.stasis.ForceStasis;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Stasis visuals and sounds (MILESTONE_5.md 9.3, 15.1, 15.3): rime and still air, never a shell, a ring or a glow.
 *
 * <ul>
 * <li><b>Cast</b> ({@code force_stasis} 0 on the target, the caster packed in the variant): the caster's palm goes out and
 * holds ({@link PowerAnimations.Kind#STASIS}); {@code force.stasis.cast}, a glassy chime, rings at the target.</li>
 * <li><b>Held</b> (1, on each creature held; intensity = seconds): 16 {@code stasis_rime} motes drift in, settle on it
 * and hang perfectly still; two small patches of bent air on its body stop moving (the frozen heat haze); the
 * {@code force.stasis.loop} hum plays on it until the stasis ends.</li>
 * <li><b>Break</b> (2: the stasis ended for any reason the server saw: a hit, its time, milk, {@code /effect clear}): the
 * motes lose their frost and fall as grit, and {@code force.stasis.break} cracks. The creature's death or removal, a
 * level change, a disconnect or a lost event (the watchdog) end it here too, without the sound.</li>
 * </ul>
 */
public final class StasisFx {
	/** Rime motes per held creature at Standard (15.1). */
	private static final int MOTES = 16;
	/** Still patches of bent air on a held creature (the frozen haze). */
	private static final int HAZE = 2;
	private static final int CAST_POSE_TICKS = 20;
	private static final int SETTLE_TICKS = 6;
	/** Ticks past the stasis's length before a lost break event is assumed. */
	private static final int WATCHDOG_SLACK = 20;

	/** A creature held now: the watchdog end, its motes and its loop. */
	private static final class Held {
		long until;
		final List<StasisRimeParticle> motes = new ArrayList<>();
		@Nullable PowerLoop loop;

		Held(long until) {
			this.until = until;
		}

		/** Lets every mote fall and stops the hum. */
		void end() {
			for (StasisRimeParticle mote : motes) {
				mote.shatter();
			}

			motes.clear();

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
			case ForceStasis.VARIANT_HELD -> hold(payload, target);
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

	private static void hold(EffectEventPayload payload, @Nullable Entity target) {
		int id = payload.entityId();
		int length = Math.max(1, Math.round(payload.intensity() * 20f));
		Held held = HELD.get(id);

		if (held != null) {
			// A fresh stasis on a creature already held: the motes stay where they hang, for the new length.
			held.until = ticks + length + WATCHDOG_SLACK;

			for (StasisRimeParticle mote : held.motes) {
				mote.extend(length + WATCHDOG_SLACK);
			}

			return;
		}

		held = new Held(ticks + length + WATCHDOG_SLACK);
		HELD.put(id, held);

		if (target == null) {
			return;
		}

		if (Vfx.inRange(target.position())) {
			settle(target, held, length);
		}

		held.loop = PowerLoop.start(ModSounds.FORCE_STASIS_LOOP, target, 0.45f, 1f, () -> isHeld(id), () -> 0);
	}

	/** The rime drifts in and settles on the body; two patches of the air around it bend and freeze. */
	private static void settle(Entity target, Held held, int length) {
		for (int i = 0; i < Vfx.count(MOTES); i++) {
			Vec3 offset = bodyPoint(target);
			Vec3 spot = target.position().add(offset);
			Vec3 from = spot.add(FxKit.jitter(0.6)).add(0, 0.25, 0);

			if (FxKit.spawn(ForceM5Feature.STASIS_RIME, from, Vec3.ZERO) instanceof StasisRimeParticle mote) {
				mote.hold(target, offset, SETTLE_TICKS + RANDOM.nextInt(5), length + WATCHDOG_SLACK);
				mote.size(0.035f + RANDOM.nextFloat() * 0.03f, 0.035f + RANDOM.nextFloat() * 0.03f);
				held.motes.add(mote);
			}
		}

		for (int i = 0; i < Vfx.count(HAZE); i++) {
			// A small ripple that does not grow or move: the haze has stopped.
			float size = 0.18f + RANDOM.nextFloat() * 0.12f;
			FxKit.ripple(target.position().add(bodyPoint(target)), Vec3.ZERO, size, size, 0.12f, Math.min(length, 60));
		}
	}

	/** A point on the surface of {@code target}'s box, relative to its feet. */
	private static Vec3 bodyPoint(Entity target) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double radius = target.getBbWidth() * (0.5 + RANDOM.nextDouble() * 0.15) + 0.04;
		double height = target.getBbHeight() * (0.08 + RANDOM.nextDouble() * 0.88);
		return new Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
	}

	private static void release(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Held held = HELD.remove(payload.entityId());
		Vec3 at = target != null ? FxKit.chest(target) : payload.pos();

		boolean drawn = held != null && !held.motes.isEmpty();

		if (held != null) {
			held.end();
		}

		if (!drawn && target != null && Vfx.inRange(at)) {
			// A stasis this client never saw settle (it came into range meanwhile): the grit still falls.
			for (int i = 0; i < Vfx.count(MOTES); i++) {
				if (FxKit.spawn(ForceM5Feature.STASIS_RIME, target.position().add(bodyPoint(target)), Vec3.ZERO) instanceof StasisRimeParticle mote) {
					mote.shatter();
				}
			}
		}

		FxKit.play(level, at, ModSounds.FORCE_STASIS_BREAK, 0.85f, FxKit.pitch(0.05f));
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
				entry.getValue().end();
				it.remove();
			}
		}
	}

	/** Forgets every stasis and stops every hum (level change, disconnect). */
	public static void clear() {
		for (Held held : HELD.values()) {
			held.end();
		}

		HELD.clear();
	}

	private StasisFx() {
	}
}

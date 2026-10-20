package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.DrainMoteParticle;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Drain visuals and sounds (MILESTONE_5.md 9.5, 15.1, 15.3): a thin stream of dark-red motes drifting out of the
 * target to the caster's hand while the channel lasts. No beam, no glow, nothing on the ground.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_drain} 0 on the caster; the target's id packed in the variant; intensity = the longest
 * channel in seconds): the free hand reaches out, fingers curled, and draws slowly back
 * ({@link PowerAnimations.Kind#DRAIN}); the {@code force.drain.loop} plays on the caster.</li>
 * <li><b>Pulse</b> (1): the stream thickens for a moment (its motes are larger for a few ticks).</li>
 * <li><b>End</b> (2): the pose and the loop stop and {@code force.drain.end} sounds. The caster's death or removal, a
 * level change, a disconnect or the watchdog end it too.</li>
 * </ul>
 * Every tick of a channel, 3 {@code drain_mote} leave the target, each steering for the caster's hand.
 */
public final class DrainFx {
	/** Motes per tick along the stream, at Standard (15.1). */
	private static final int MOTES_PER_TICK = 3;
	/** Ticks past the longest channel before a lost end event is assumed. */
	private static final int WATCHDOG_SLACK = 30;
	/** Ticks a pulse thickens the stream. */
	private static final int PULSE_TICKS = 4;
	/** How fast a mote drifts toward the hand, blocks a tick. */
	private static final double SPEED = 0.22;

	/** A channel seen by this client: the caster's target, where it was last told to be, and the watchdog end. */
	private static final class Stream {
		final int targetId;
		Vec3 lastTarget;
		final long until;
		long pulseUntil = -1;

		Stream(int targetId, Vec3 lastTarget, long until) {
			this.targetId = targetId;
			this.lastTarget = lastTarget;
			this.until = until;
		}
	}

	private static final Map<Integer, Stream> STREAMS = new HashMap<>();
	private static long ticks;

	/** {@code force_drain}: start, pulse or end on the caster. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		int id = payload.entityId();
		int targetId = ForceEffects.casterOf(payload.variant());

		switch (ForceEffects.valueOf(payload.variant())) {
			case ForceDrain.VARIANT_START -> {
				int watchdog = Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK;
				STREAMS.put(id, new Stream(targetId, payload.pos(), ticks + watchdog));
				PowerAnimations.start(id, PowerAnimations.Kind.DRAIN, -1, watchdog);

				if (caster instanceof LivingEntity living) {
					// Tied to the pose: it stops with it, on every end path.
					PowerAnimations.loop(living, PowerAnimations.Kind.DRAIN, ModSounds.FORCE_DRAIN_LOOP, 0.55f, 1f);
				}
			}
			case ForceDrain.VARIANT_PULSE -> {
				Stream stream = STREAMS.get(id);

				if (stream != null) {
					stream.lastTarget = payload.pos();
					stream.pulseUntil = ticks + PULSE_TICKS;
				}
			}
			case ForceDrain.VARIANT_END -> {
				STREAMS.remove(id);
				PowerAnimations.stop(id, PowerAnimations.Kind.DRAIN);
				Vec3 at = caster != null ? FxKit.chest(caster) : payload.pos();
				FxKit.play(level, at, ModSounds.FORCE_DRAIN_END, 0.75f, FxKit.pitch(0.04f));
			}
			default -> {
			}
		}
	}

	/** Whether {@code casterId} is draining, as far as this client knows. */
	public static boolean isDraining(int casterId) {
		Stream stream = STREAMS.get(casterId);
		return stream != null && ticks <= stream.until;
	}

	public static void tick(ClientLevel level) {
		ticks++;

		if (STREAMS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Stream>> it = STREAMS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Stream> entry = it.next();
			Stream stream = entry.getValue();

			if (ticks > stream.until || !(level.getEntity(entry.getKey()) instanceof LivingEntity caster) || caster.isRemoved() || !caster.isAlive()) {
				PowerAnimations.stop(entry.getKey(), PowerAnimations.Kind.DRAIN);
				it.remove();
				continue;
			}

			Entity target = level.getEntity(stream.targetId);
			Vec3 from = target != null && target.isAlive() ? FxKit.chest(target) : stream.lastTarget;
			boolean left = ForceFxUtil.freeArmIsLeft(caster);

			if (Vfx.inRange(from) || Vfx.inRange(caster.position())) {
				flow(caster, target, from, left, ticks <= stream.pulseUntil);
			}
		}
	}

	/** One tick of the stream: motes leave the target's body and steer for the caster's hand. */
	private static void flow(LivingEntity caster, @Nullable Entity target, Vec3 from, boolean left, boolean pulse) {
		double spread = target != null ? target.getBbWidth() * 0.35 : 0.25;
		double distance = from.distanceTo(ForceFxUtil.handPos(caster, left, 0.6f));

		for (int i = 0, n = Vfx.count(MOTES_PER_TICK); i < n; i++) {
			Vec3 at = from.add(FxKit.jitter(spread));
			// A slow start out of the body, then the pull takes it.
			Vec3 drift = FxKit.jitter(0.02);

			if (FxKit.spawn(ForceM5Feature.DRAIN_MOTE, at, drift) instanceof DrainMoteParticle mote) {
				float size = (pulse ? 0.075f : 0.05f) + RANDOM.nextFloat() * 0.02f;
				mote.seek(() -> ForceFxUtil.handPos(caster, left, 0.6f), SPEED * (0.8 + RANDOM.nextDouble() * 0.4));
				mote.size(size, size * 0.5f).life((int) Math.ceil(distance / SPEED) + 12);
			}
		}
	}

	/** Forgets every stream (level change, disconnect); the poses and loops end with {@link PowerAnimations#clear}. */
	public static void clear() {
		STREAMS.clear();
	}

	private DrainFx() {
	}
}

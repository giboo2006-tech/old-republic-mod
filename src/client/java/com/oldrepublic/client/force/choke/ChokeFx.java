package com.oldrepublic.client.force.choke;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Choke visuals and sounds (spec MILESTONE_4.md 9.3, 13.1, 13.3). No glow and no tendrils.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_choke} 0 on the caster, pos = the target's chest, intensity = the
 * hold in seconds): {@code force.choke.start} and the {@code force.choke.loop} creak on the caster,
 * which stops on the end or crush event, the watchdog, the caster's death or removal, a level change
 * or a disconnect.</li>
 * <li><b>Held target</b> ({@code force_choke_target}, intensity 1): 10 grit motes a second lifted
 * from the ground under it, a slight bent-air tremor around its neck and shoulders, its hands at its
 * throat and its feet hanging ({@link #THROAT}, read by the humanoid model).</li>
 * <li><b>Release</b> (target intensity 0; caster 1 or 2): a puff of dust as it drops,
 * {@code force.choke.release}, or {@code force.choke.crush} and a heavier puff on a Crush.</li>
 * </ul>
 */
public final class ChokeFx {
	/**
	 * How far a choked creature's hands are at its throat (0 to 1), on any living render state; null
	 * when it is not choked. Set by {@code mixin.foes.LivingEntityRendererChokeMixin}, drawn by
	 * {@code mixin.foes.HumanoidModelChokeMixin}.
	 */
	public static final RenderStateDataKey<Float> THROAT = RenderStateDataKey.create(() -> "oldrepublic:choke_throat");
	/** The longest hold (60 ticks) plus slack, for a lost release event. */
	private static final int WATCHDOG = 60 + 30;
	/** One grit mote every this many ticks: 10 a second. */
	private static final int GRIT_INTERVAL = 2;
	private static final int TREMOR_INTERVAL = 4;
	/** Ticks the hands take to reach the throat. */
	private static final float THROAT_IN = 5f;

	/** A target held now: when it started and the watchdog end, in client ticks. */
	private record Held(long start, long until) {
	}

	/** A caster choking now: the watchdog end and the loop. */
	private static final class Choking {
		final long until;
		@Nullable PowerLoop loop;

		Choking(long until) {
			this.until = until;
		}
	}

	private static final Map<Integer, Held> HELD = new HashMap<>();
	private static final Map<Integer, Choking> CHOKING = new HashMap<>();
	private static long ticks;

	/** {@code force_choke}: start, end or crush on the caster. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		Vec3 pos = payload.pos();

		switch (payload.variant()) {
			case ForceChoke.VARIANT_START -> {
				Vec3 at = caster != null ? FxKit.chest(caster) : pos;
				FxKit.play(level, at, ModSounds.FORCE_CHOKE_START, 0.8f, FxKit.pitch(0.04f));
				stopCaster(payload.entityId());
				int watchdog = Math.round(payload.intensity() * 20f) + 30;
				Choking choking = new Choking(ticks + watchdog);
				CHOKING.put(payload.entityId(), choking);

				if (caster != null) {
					int id = caster.getId();
					// The free arm reaching for the target until the end or crush event (or the same watchdog).
					PowerAnimations.start(id, PowerAnimations.Kind.CHOKE, -1, watchdog);
					choking.loop = PowerLoop.start(ModSounds.FORCE_CHOKE_LOOP, caster, 0.5f, 1f, () -> isChoking(id), () -> 0);
				}
			}
			case ForceChoke.VARIANT_END -> {
				stopCaster(payload.entityId());
				FxKit.play(level, pos, ModSounds.FORCE_CHOKE_RELEASE, 0.75f, FxKit.pitch(0.05f));
			}
			case ForceChoke.VARIANT_CRUSH -> {
				stopCaster(payload.entityId());
				FxKit.play(level, pos, ModSounds.FORCE_CHOKE_CRUSH, 1f, FxKit.pitch(0.05f));
			}
			default -> {
			}
		}
	}

	/** {@code force_choke_target}: intensity 1 while held, 0 when dropped (variant 1 after a Crush). */
	public static void onTarget(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		if (payload.intensity() > 0f) {
			HELD.put(payload.entityId(), new Held(ticks, ticks + WATCHDOG));
			return;
		}

		HELD.remove(payload.entityId());
		Vec3 feet = target != null ? target.position() : payload.pos().subtract(0, 1, 0);

		if (Vfx.inRange(feet)) {
			drop(level, feet, payload.variant() == 1);
		}
	}

	/** Whether {@code entityId} is choking someone, as far as this client knows (for the caster's pose). */
	public static boolean isChoking(int entityId) {
		Choking choking = CHOKING.get(entityId);
		return choking != null && ticks <= choking.until;
	}

	/** How far {@code entityId}'s hands are at its throat: 0 when not choked, rising to 1. */
	public static float throat(int entityId, float partialTick) {
		if (HELD.isEmpty()) {
			return 0f;
		}

		Held held = HELD.get(entityId);
		return held == null ? 0f : Mth.clamp((ticks - held.start() + partialTick) / THROAT_IN, 0f, 1f);
	}

	public static void tick(ClientLevel level) {
		ticks++;
		CHOKING.entrySet().removeIf(e -> {
			boolean over = ticks > e.getValue().until;

			if (over && e.getValue().loop != null) {
				e.getValue().loop.end();
			}

			return over;
		});

		Iterator<Map.Entry<Integer, Held>> it = HELD.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Held> entry = it.next();
			Entity target = level.getEntity(entry.getKey());

			if (ticks > entry.getValue().until() || target == null || target.isRemoved() || !target.isAlive()) {
				it.remove();
				continue;
			}

			if (Vfx.inRange(target.position())) {
				held(level, target);
			}
		}
	}

	/** Grit lifted from the ground under the target, and the tremor at its neck. */
	private static void held(ClientLevel level, Entity target) {
		Vec3 feet = target.position();

		if (ticks % GRIT_INTERVAL == 0) {
			BlockPos ground = FxKit.groundBelow(level, feet.subtract(0, 0.3, 0), 3);

			if (ground != null) {
				float[] rgb = FxKit.dustColor(level, ground);
				double width = target.getBbWidth() * 0.7;

				for (int i = 0; i < Vfx.count(1); i++) {
					Vec3 at = new Vec3(feet.x + FxKit.jitter1(width), ground.getY() + 1.02, feet.z + FxKit.jitter1(width));
					// Grit rising a little toward the feet, then sifting back down.
					FxKit.dust(at, new Vec3(FxKit.jitter1(0.01), 0.04 + RANDOM.nextDouble() * 0.04, FxKit.jitter1(0.01)), rgb, 0.05f, 0.7f,
							16 + RANDOM.nextInt(8), 0.03f);
				}
			}
		}

		if (ticks % TREMOR_INTERVAL == 0) {
			for (int i = 0; i < Vfx.count(1); i++) {
				Vec3 neck = FxKit.neck(target).add(FxKit.jitter(target.getBbWidth() * 0.3));
				FxKit.ripple(neck, FxKit.jitter(0.004), 0.12f, 0.3f, 0.1f, 5);
			}
		}
	}

	/** The drop: a puff of dust at the feet, heavier after a Crush. */
	private static void drop(ClientLevel level, Vec3 feet, boolean crushed) {
		float[] rgb = FxKit.dustColor(level, FxKit.groundBelow(level, feet.subtract(0, 0.3, 0), 3));
		int count = crushed ? 10 : 6;

		for (int i = 0; i < Vfx.count(count); i++) {
			Vec3 out = FxKit.randomFlat();
			double speed = 0.04 + RANDOM.nextDouble() * (crushed ? 0.12 : 0.07);
			FxKit.dust(feet.add(out.scale(0.15 + RANDOM.nextDouble() * 0.3)).add(0, 0.05 + RANDOM.nextDouble() * 0.2, 0),
					out.scale(speed).add(0, 0.02 + RANDOM.nextDouble() * 0.04, 0), rgb, 0.09f, 0.55f, 16 + RANDOM.nextInt(8), 0.02f);
		}
	}

	private static void stopCaster(int casterId) {
		Choking choking = CHOKING.remove(casterId);
		PowerAnimations.stop(casterId, PowerAnimations.Kind.CHOKE);

		if (choking != null && choking.loop != null) {
			choking.loop.end();
		}
	}

	/** Forgets every hold and stops every loop (level change, disconnect). */
	public static void clear() {
		for (Choking choking : CHOKING.values()) {
			if (choking.loop != null) {
				choking.loop.end();
			}
		}

		CHOKING.clear();
		HELD.clear();
	}

	private ChokeFx() {
	}
}

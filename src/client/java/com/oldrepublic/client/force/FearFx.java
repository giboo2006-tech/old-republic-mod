package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Fear visuals and sounds (MILESTONE_5.md 9.6, 15.1, 15.3): a ripple of bent air through the cone, wisps of dark
 * smoke curling at each victim's feet, and a shiver in their pose. No glow, nothing drawn on the ground.
 *
 * <ul>
 * <li><b>Cast</b> ({@code force_fear} 0 on the caster; pos = the eyes; dir = the cone's axis, zero for Terror; intensity =
 * the range or the radius): a cupped hand thrust out and twisted ({@link PowerAnimations.Kind#FEAR}),
 * {@code force.fear.cast} (a whispering rush) and one ripple sweeping down the cone. Terror's goes out all round in
 * broken, uneven arcs instead.</li>
 * <li><b>A victim</b> (1, on the victim; intensity = seconds): 6 {@code shadow_wisp} curl up at its feet, then one per 10
 * ticks while it is afraid; it shivers ({@link #SHIVER}, read by the humanoid model).</li>
 * </ul>
 */
public final class FearFx {
	/**
	 * How hard a frightened creature shivers (0 to 1), on any living render state; null when it is not afraid. Set by
	 * {@code mixin.force.LivingEntityRendererLiftMixin}, drawn by {@code mixin.force.HumanoidModelMixin}.
	 */
	public static final RenderStateDataKey<Float> SHIVER = RenderStateDataKey.create(() -> "oldrepublic:fear_shiver");
	private static final int CAST_POSE_TICKS = 14;
	/** Wisps per victim at the cast, at Standard (15.1). */
	private static final int WISPS = 6;
	private static final int WISP_INTERVAL = 10;
	/** Terror's ripple: this many arcs, one of them missing. */
	private static final int TERROR_ARCS = 6;
	private static final float SHIVER_IN = 4f;
	private static final float SHIVER_OUT = 8f;

	/** A victim: when its fear started and ends, in client ticks. */
	private record Afraid(long start, long until) {
	}

	private static final Map<Integer, Afraid> AFRAID = new HashMap<>();
	private static long ticks;

	/** {@code force_fear}: the cast on the caster, or a victim. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case ForceFear.VARIANT_CAST -> cast(level, payload, entity);
			case ForceFear.VARIANT_VICTIM -> victim(payload, entity);
			default -> {
			}
		}
	}

	private static void cast(ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		PowerAnimations.start(payload.entityId(), PowerAnimations.Kind.FEAR, CAST_POSE_TICKS, CAST_POSE_TICKS);
		Vec3 eye = payload.pos();
		FxKit.play(level, eye, ModSounds.FORCE_FEAR_CAST, 0.9f, FxKit.pitch(0.04f));

		if (!Vfx.inRange(eye)) {
			return;
		}

		float reach = Math.max(2f, payload.intensity());
		Vec3 dir = payload.dir();

		if (dir.lengthSqr() < 1.0e-4) {
			terror(caster != null ? FxKit.chest(caster) : eye.subtract(0, 0.5, 0), reach);
			return;
		}

		// One shell of bent air, travelling down the cone and opening as it goes.
		Vec3 axis = dir.normalize();
		double speed = 0.75;
		int life = Math.max(6, (int) Math.ceil(reach / speed));
		FxKit.ripple(eye.add(axis.scale(0.8)), axis.scale(speed), 0.35f, 0.35f + reach * 0.28f, 0.2f, life);
	}

	/** Terror: arcs of bent air going out all round, uneven in height, speed and size, with a gap. */
	private static void terror(Vec3 centre, float radius) {
		double base = RANDOM.nextDouble() * Math.PI * 2;
		int gap = RANDOM.nextInt(TERROR_ARCS);

		for (int i = 0; i < Vfx.count(TERROR_ARCS); i++) {
			if (i % TERROR_ARCS == gap) {
				continue;
			}

			double angle = base + i * (Math.PI * 2 / TERROR_ARCS) + FxKit.jitter1(0.35);
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double speed = 0.45 + RANDOM.nextDouble() * 0.25;
			int life = Math.max(6, (int) Math.ceil(radius / speed));
			Vec3 at = centre.add(out.scale(0.7)).add(0, FxKit.jitter1(0.4), 0);
			FxKit.ripple(at, out.scale(speed), 0.3f, 0.3f + radius * (0.15f + RANDOM.nextFloat() * 0.1f), 0.16f, life);
		}
	}

	private static void victim(EffectEventPayload payload, @Nullable Entity victim) {
		int length = Math.max(1, Math.round(payload.intensity() * 20f));
		AFRAID.put(payload.entityId(), new Afraid(ticks, ticks + length));

		if (victim != null && Vfx.inRange(victim.position())) {
			for (int i = 0; i < Vfx.count(WISPS); i++) {
				wisp(victim);
			}
		}
	}

	/** A wisp of dark smoke curling up from the ground at the victim's feet. */
	private static void wisp(Entity victim) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			return;
		}

		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double radius = victim.getBbWidth() * (0.3 + RANDOM.nextDouble() * 0.5);
		Vec3 at = victim.position().add(Math.cos(angle) * radius, 0.05 + RANDOM.nextDouble() * 0.2, Math.sin(angle) * radius);
		// Curling: round the feet as it rises.
		Vec3 curl = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.015 + RANDOM.nextDouble() * 0.015);
		mc.level.addParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, curl.x, 0.012 + RANDOM.nextDouble() * 0.015, curl.z);
	}

	/** How hard {@code entityId} shivers now: 0 when not afraid, easing in and out. */
	public static float shiver(int entityId, float partialTick) {
		if (AFRAID.isEmpty()) {
			return 0f;
		}

		Afraid afraid = AFRAID.get(entityId);

		if (afraid == null) {
			return 0f;
		}

		float in = Mth.clamp((ticks - afraid.start() + partialTick) / SHIVER_IN, 0f, 1f);
		float out = Mth.clamp((afraid.until() - ticks - partialTick) / SHIVER_OUT, 0f, 1f);
		return in * out;
	}

	public static void tick(ClientLevel level) {
		ticks++;

		if (AFRAID.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Afraid>> it = AFRAID.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Afraid> entry = it.next();
			Entity victim = level.getEntity(entry.getKey());

			if (ticks > entry.getValue().until() || victim == null || victim.isRemoved() || !victim.isAlive()) {
				it.remove();
				continue;
			}

			if ((ticks - entry.getValue().start()) % WISP_INTERVAL == 0 && Vfx.inRange(victim.position())) {
				for (int i = 0; i < Vfx.count(1); i++) {
					wisp(victim);
				}
			}
		}
	}

	public static void clear() {
		AFRAID.clear();
	}

	private FearFx() {
	}
}

package com.oldrepublic.client.force;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Mind Trick visuals and sounds (MILESTONE_5.md 9.8, 15.1, 15.3): a faint ripple of bent air at the target's head, and
 * the tricked creature lowers its weapon and bows its head a little while the calm lasts ({@link #CALM}). No glow.
 *
 * <ul>
 * <li><b>Success</b> ({@code force_mind_trick} 0 on the target, the caster packed in the variant; intensity = seconds, 0
 * for a dialogue use): two fingers raised beside the caster's face ({@link PowerAnimations.Kind#MIND_TRICK}),
 * {@code force.mind_trick.cast} (a soft, low tone) at the target, one ripple at its head, and the calm.</li>
 * <li><b>Under Confusion</b> (3): the same, without the calm (the creature turns on its own side).</li>
 * <li><b>Refused</b> (1): the caster's gesture and {@code force.mind_trick.fail}; the air does not bend.</li>
 * <li><b>Ended</b> (2): a hit broke the calm; the creature raises its guard.</li>
 * </ul>
 */
public final class MindTrickFx {
	/**
	 * How calm a tricked creature is (0 to 1), on any living render state; null when it is not. Set by
	 * {@code mixin.force.LivingEntityRendererLiftMixin}, drawn by {@code mixin.force.HumanoidModelMixin}.
	 */
	public static final RenderStateDataKey<Float> CALM = RenderStateDataKey.create(() -> "oldrepublic:mind_trick_calm");
	private static final int CAST_POSE_TICKS = 18;
	/** Ticks the weapon takes to lower, and to come back up. */
	private static final float CALM_IN = 10f;
	private static final float CALM_OUT = 5f;

	/** A calm creature: when it was tricked and when the calm ends, in client ticks. */
	private record Calm(long start, long until) {
	}

	private static final Map<Integer, Calm> CALMED = new HashMap<>();
	private static long ticks;

	/** {@code force_mind_trick} on the target. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		int caster = ForceEffects.casterOf(payload.variant());
		int value = ForceEffects.valueOf(payload.variant());
		Vec3 head = target != null ? target.getEyePosition() : payload.pos().add(0, 0.5, 0);

		if (value == MindTrick.VARIANT_ENDED) {
			Calm calm = CALMED.get(payload.entityId());

			if (calm != null) {
				CALMED.put(payload.entityId(), new Calm(calm.start(), Math.min(calm.until(), ticks + (long) CALM_OUT)));
			}

			return;
		}

		if (caster > 0) {
			PowerAnimations.start(caster, PowerAnimations.Kind.MIND_TRICK, CAST_POSE_TICKS, CAST_POSE_TICKS);
		}

		if (value == MindTrick.VARIANT_REFUSED) {
			FxKit.play(level, head, ModSounds.FORCE_MIND_TRICK_FAIL, 0.75f, FxKit.pitch(0.03f));
			return;
		}

		FxKit.play(level, head, ModSounds.FORCE_MIND_TRICK_CAST, 0.8f, FxKit.pitch(0.03f));

		if (Vfx.inRange(head)) {
			for (int i = 0; i < Vfx.count(1); i++) {
				FxKit.ripple(head, Vec3.ZERO, 0.2f, 0.55f, 0.16f, 12);
			}
		}

		int length = Math.round(payload.intensity() * 20f);

		if (value == MindTrick.VARIANT_SUCCESS && length > 0) {
			CALMED.put(payload.entityId(), new Calm(ticks, ticks + length));
		}
	}

	/** How calm {@code entityId} is now: 0 when it is not, easing in and out. */
	public static float calm(int entityId, float partialTick) {
		if (CALMED.isEmpty()) {
			return 0f;
		}

		Calm calm = CALMED.get(entityId);

		if (calm == null) {
			return 0f;
		}

		float in = Mth.clamp((ticks - calm.start() + partialTick) / CALM_IN, 0f, 1f);
		float out = Mth.clamp((calm.until() - ticks - partialTick) / CALM_OUT, 0f, 1f);
		return in * out;
	}

	public static void tick(ClientLevel level) {
		ticks++;

		if (CALMED.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Calm>> it = CALMED.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Calm> entry = it.next();
			Entity target = level.getEntity(entry.getKey());

			if (ticks > entry.getValue().until() || target == null || target.isRemoved() || !target.isAlive()) {
				it.remove();
			}
		}
	}

	public static void clear() {
		CALMED.clear();
	}

	private MindTrickFx() {
	}
}

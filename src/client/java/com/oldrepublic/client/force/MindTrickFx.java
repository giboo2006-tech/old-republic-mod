package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Glint;
import com.oldrepublic.client.force.energy.Mote;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Tendril;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Mind Trick visuals and sounds (MILESTONE_5.md 9.8, 15.1, 15.3): a suggestion carried on the air. A pale thread runs from
 * the caster's raised fingers to the target's head, the air bends round the head, the eyes glaze (a pale glint opens and
 * closes in each), a thin pale haze drifts across the face and motes of pale light wander round the head, each at its own
 * height and pace; while the calm lasts the motes and the haze keep coming and the eyes glaze again now and then. The
 * tricked creature lowers its weapon and bows its head a little ({@link #CALM}). No halo, no glow on the body.
 *
 * <ul>
 * <li><b>Success</b> ({@code force_mind_trick} 0 on the target, the caster packed in the variant; intensity = seconds, 0
 * for a dialogue use): two fingers raised beside the caster's face ({@link PowerAnimations.Kind#MIND_TRICK}),
 * {@code force.mind_trick.cast} (a soft, low tone) at the target, the thread, the bent air, the glazed eyes and the haze,
 * and the calm.</li>
 * <li><b>Under Confusion</b> (3): the same, without the calm (the creature turns on its own side).</li>
 * <li><b>Refused</b> (1): the caster's gesture and {@code force.mind_trick.fail}; the air does not bend.</li>
 * <li><b>Ended</b> (2): a hit broke the calm; the creature raises its guard and the motes round its head scatter.</li>
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
	private static final int MOTE_INTERVAL = 6;
	private static final int HAZE_INTERVAL = 10;
	/** While the calm lasts the eyes glaze again this often. */
	private static final int GLAZE_INTERVAL = 36;
	/** {@link EnergyRender} owner kind of a trick's light. */
	private static final int OWNER_KIND = 10;
	/** The haze: pale, a little blue. */
	private static final float[] HAZE = {0.86f, 0.89f, 1f};

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

			if (target != null && Vfx.inRange(head)) {
				EnergyRender.kill(EnergyRender.owner(OWNER_KIND, target.getId()));
				snap(target);
			}

			return;
		}

		if (caster > 0) {
			PowerAnimations.start(caster, PowerAnimations.Kind.MIND_TRICK, CAST_POSE_TICKS, CAST_POSE_TICKS);
		}

		if (value == MindTrick.VARIANT_REFUSED) {
			EnergyKit.play(level, head, ModSounds.FORCE_MIND_TRICK_FAIL, 0.75f, EnergyKit.pitch(0.03f));
			return;
		}

		EnergyKit.play(level, head, ModSounds.FORCE_MIND_TRICK_CAST, 0.8f, EnergyKit.pitch(0.03f));

		if (Vfx.inRange(head)) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				ForceFxUtil.ripple(head, Vec3.ZERO, 0.2f, 0.6f, 0.18f, 12);
			}

			if (target != null) {
				trick(level, caster, target);
			}
		}

		int length = Math.round(payload.intensity() * 20f);

		if (value == MindTrick.VARIANT_SUCCESS && length > 0) {
			CALMED.put(payload.entityId(), new Calm(ticks, ticks + length));
		}
	}

	/** The thread from the caster's fingers, the glazed eyes, the haze across the face and the motes that start to wander. */
	private static void trick(ClientLevel level, int casterId, Entity target) {
		long owner = EnergyRender.owner(OWNER_KIND, target.getId());

		if (level.getEntity(casterId) instanceof LivingEntity caster && !caster.isRemoved()) {
			Anchor fingers = Anchor.hand(casterId, EnergyKit.freeArmIsLeft(caster), 0.35f).offset(new Vec3(0, 0.22, 0));
			EnergyRender.add(Tendril.between(owner, fingers, head(target.getId(), 0f, 0f), Palette.PALE).life(22, 1f, 10f, 5f).width(0.85f, 0.5f)
					.strength(1f).writhe(0.09f, 1f, 0.03f).beads(2.2f, 0.06f, 1.8f));
		}

		glaze(target, 22, 0.15f, 1f);

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			haze(target);
		}

		for (int i = 0, n = Vfx.count(7); i < n; i++) {
			daze(target, 34 + RANDOM.nextInt(16));
		}
	}

	/** The eyes glaze: a pale glint opens and closes in each. */
	private static void glaze(Entity target, int life, float size, float strength) {
		long owner = EnergyRender.owner(OWNER_KIND, target.getId());

		for (int side = -1; side <= 1; side += 2) {
			EnergyRender.add(new Glint(owner, eye(target.getId(), side), Palette.PALE, life, size, strength));
		}
	}

	/** A dim mote wandering round the head, never in a ring: each at its own height and distance, turning its own way. */
	private static void daze(Entity target, int life) {
		float width = target.getBbWidth();
		EnergyRender.add(Mote.at(EnergyRender.owner(OWNER_KIND, target.getId()), target.getEyePosition(), Vec3.ZERO, Palette.PALE)
				.orbit(head(target.getId(), 0f, 0f), width * 0.55f + 0.08f + 0.22f * RANDOM.nextFloat(), RANDOM.nextFloat() * 6.2832f,
						-0.12f + 0.4f * RANDOM.nextFloat(), (RANDOM.nextBoolean() ? 1f : -1f) * (0.06f + 0.07f * RANDOM.nextFloat()),
						0.004f * (RANDOM.nextFloat() - 0.3f), 0f)
				.life(life, 6f, 12f).size(1.1f, 0.8f).strength(0.95f).twinkle());
	}

	/** A wisp of pale haze drifting across the face. */
	private static void haze(Entity target) {
		Vec3 look = target.getViewVector(1f);
		Vec3 across = EnergyKit.side(look).scale(RANDOM.nextBoolean() ? 1 : -1);
		Vec3 at = target.getEyePosition().add(look.scale(target.getBbWidth() * 0.5 + 0.08)).add(across.scale(-0.25)).add(EnergyKit.jitter(0.06));
		ForceParticle p = EnergyKit.spawn(EnergyParticles.MIST, at, across.scale(0.012 + 0.008 * RANDOM.nextDouble()).add(0, 0.003, 0));

		if (p != null) {
			p.color(HAZE[0], HAZE[1], HAZE[2]).size(0.16f, 0.5f).alpha(0.4f).life(26 + RANDOM.nextInt(10)).drag(0.95f).fullBright();
		}
	}

	/** The calm broken: a few pale motes scatter off the head and die. */
	private static void snap(Entity target) {
		Vec3 head = target.getEyePosition();

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			Vec3 out = EnergyKit.randomUnit();
			EnergyRender.add(Mote.at(0L, head.add(out.scale(0.25)), out.scale(0.08), Palette.PALE).life(9 + RANDOM.nextInt(4), 0f, 7f).size(0.45f, 0.2f)
					.strength(0.8f).drift(0.004f, 0.86f));
		}
	}

	/** The middle of the head of {@code entityId}, moved {@code up} and {@code forward} (along its face) blocks. */
	private static Anchor head(int entityId, float up, float forward) {
		return partial -> {
			Entity entity = Anchor.entity(entityId);

			if (entity == null) {
				return null;
			}

			Vec3 at = entity.getEyePosition(partial).add(0, up, 0);
			return forward == 0f ? at : at.add(entity.getViewVector(partial).scale(forward));
		};
	}

	/** An eye of {@code entityId} ({@code side} -1 left, 1 right): on the face, a little either side of its middle. */
	private static Anchor eye(int entityId, int side) {
		return partial -> {
			if (!(Anchor.entity(entityId) instanceof LivingEntity entity)) {
				return null;
			}

			float yaw = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot) * Mth.DEG_TO_RAD;
			Vec3 face = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 right = new Vec3(-face.z, 0, face.x);
			return entity.getEyePosition(partial).add(face.scale(entity.getBbWidth() * 0.42 + 0.03)).add(right.scale(0.12 * side));
		};
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
			Calm calm = entry.getValue();
			Entity target = level.getEntity(entry.getKey());

			if (ticks > calm.until() || target == null || target.isRemoved() || !target.isAlive()) {
				it.remove();
				continue;
			}

			long age = ticks - calm.start();

			if (!Vfx.inRange(target.position()) || calm.until() - ticks < 12) {
				continue;
			}

			// While the calm lasts motes wander round the head, the haze drifts and now and then the eyes glaze again.
			if (age % MOTE_INTERVAL == MOTE_INTERVAL - 1) {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					daze(target, 28 + RANDOM.nextInt(10));
				}
			}

			if (age % HAZE_INTERVAL == HAZE_INTERVAL - 1) {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					haze(target);
				}
			}

			if (age % GLAZE_INTERVAL == GLAZE_INTERVAL - 1) {
				glaze(target, 18, 0.1f, 0.8f);
			}
		}
	}

	public static void clear() {
		CALMED.clear();
	}

	private MindTrickFx() {
	}
}

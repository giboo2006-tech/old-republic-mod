package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Tendril;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Fear visuals and sounds (MILESTONE_5.md 9.6, 15.1, 15.3): darkness thrown down the cone. Fingers of black smoke
 * reach out of the caster's hand, a bank of dark vapour rolls out after them and a ripple of bent air sweeps through; on each
 * victim black smoke winds up round the legs, wisps of shadow curl at the feet, its breath shows cold, and it shivers.
 * Nothing drawn on the ground, no ring or dome. The smoke is dark against the day; a dim violet light at its heart shows it
 * at night.
 *
 * <ul>
 * <li><b>Cast</b> ({@code force_fear} 0 on the caster; pos = the eyes; dir = the cone's axis, zero for Terror; intensity =
 * the range or the radius): a cupped hand thrust out and twisted ({@link PowerAnimations.Kind#FEAR}),
 * {@code force.fear.cast} (a whispering rush), the fingers of smoke, the rolling vapour and the ripple down the cone.
 * Terror's vapour and ripples go out all round in broken, uneven arcs instead.</li>
 * <li><b>A victim</b> (1, on the victim; intensity = seconds): smoke winds up its legs and stays while it is afraid, 6
 * {@code shadow_wisp} curl up at its feet, then one per 10 ticks, with a cold breath every so often; it shivers
 * ({@link #SHIVER}, read by the humanoid model).</li>
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
	private static final int BREATH_INTERVAL = 16;
	/** Terror's ripple: this many arcs, one of them missing. */
	private static final int TERROR_ARCS = 6;
	private static final float SHIVER_IN = 4f;
	private static final float SHIVER_OUT = 8f;
	/** Half the cone's width, radians (the server's cone is 60 degrees across). */
	private static final double CONE = Math.toRadians(30);
	/** {@link EnergyRender} owner kinds: a cast's smoke, a victim's. */
	private static final int OWNER_CAST = 8;
	private static final int OWNER_VICTIM = 9;
	/** The vapour: near black, cold. */
	private static final float[] VAPOUR = {0.07f, 0.045f, 0.1f};
	/** A frightened breath: pale. */
	private static final float[] BREATH = {0.86f, 0.88f, 0.94f};

	/** A victim: when its fear started and ends, in client ticks, and the smoke round its legs. */
	private static final class Afraid {
		final long start;
		final long until;
		final List<Tendril> coils = new ArrayList<>();

		Afraid(long start, long until) {
			this.start = start;
			this.until = until;
		}
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
		EnergyKit.play(level, eye, ModSounds.FORCE_FEAR_CAST, 0.9f, EnergyKit.pitch(0.04f));

		if (!Vfx.inRange(eye)) {
			return;
		}

		float reach = Math.max(2f, payload.intensity());
		Vec3 dir = payload.dir();

		if (dir.lengthSqr() < 1.0e-4) {
			terror(caster != null ? EnergyKit.chest(caster) : eye.subtract(0, 0.5, 0), caster != null ? caster.position() : eye.subtract(0, 1.6, 0), reach);
			return;
		}

		// One shell of bent air, travelling down the cone and opening as it goes.
		Vec3 axis = dir.normalize();
		double speed = 0.75;
		int life = Math.max(6, (int) Math.ceil(reach / speed));
		ForceFxUtil.ripple(eye.add(axis.scale(0.8)), axis.scale(speed), 0.35f, 0.35f + reach * 0.28f, 0.2f, life);
		Vec3 flat = ForceFxUtil.horizontal(axis);
		Vec3 feet = caster != null ? caster.position() : eye.subtract(0, 1.6, 0);

		// Dark vapour rolls out down the cone, low and spreading, slowing as it goes.
		for (int i = 0, n = Vfx.count(16); i < n; i++) {
			Vec3 way = EnergyKit.cone(flat, CONE);
			way = new Vec3(way.x, way.y * 0.3, way.z).normalize();
			Vec3 at = feet.add(flat.scale(0.6 + 0.6 * RANDOM.nextDouble())).add(0, 0.2 + 1.3 * RANDOM.nextDouble(), 0);
			vapour(at, way.scale(reach * (0.05 + 0.04 * RANDOM.nextDouble())), 0.3f, 1.4f, 22 + RANDOM.nextInt(10));
		}

		// Fingers of black smoke reach out of the hand into the cone.
		if (caster instanceof LivingEntity living) {
			long owner = EnergyRender.owner(OWNER_CAST, living.getId());
			Anchor hand = Anchor.hand(living.getId(), EnergyKit.freeArmIsLeft(living), 1f);
			Vec3 from = hand.at(1f);

			if (from != null) {
				for (int i = 0, n = Vfx.count(4); i < n; i++) {
					Vec3 way = EnergyKit.cone(axis, CONE * 0.8);
					Vec3 to = from.add(way.scale(reach * (0.45 + 0.35 * RANDOM.nextDouble()))).add(0, -0.3 * RANDOM.nextDouble(), 0);
					EnergyRender.add(Tendril.between(owner, hand, Anchor.fixed(to), Palette.SHADOW).life(15 + RANDOM.nextInt(5), 1f, 8f, 5f)
							.width(1.1f, 0.25f).strength(0.9f).writhe(0.3f + 0.15f * RANDOM.nextFloat(), 1.4f + RANDOM.nextFloat(), 0.07f));
				}
			}
		}
	}

	/** Terror: vapour and arcs of bent air going out all round, uneven in height, speed and size, with a gap. */
	private static void terror(Vec3 centre, Vec3 feet, float radius) {
		double base = RANDOM.nextDouble() * Math.PI * 2;
		int gap = RANDOM.nextInt(TERROR_ARCS);

		for (int i = 0, n = Vfx.count(TERROR_ARCS); i < n; i++) {
			if (i % TERROR_ARCS == gap) {
				continue;
			}

			double angle = base + i * (Math.PI * 2 / TERROR_ARCS) + EnergyKit.jitter1(0.35);
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double speed = 0.45 + RANDOM.nextDouble() * 0.25;
			int life = Math.max(6, (int) Math.ceil(radius / speed));
			Vec3 at = centre.add(out.scale(0.7)).add(0, EnergyKit.jitter1(0.4), 0);
			ForceFxUtil.ripple(at, out.scale(speed), 0.3f, 0.3f + radius * (0.15f + RANDOM.nextFloat() * 0.1f), 0.16f, life);
		}

		for (int i = 0, n = Vfx.count(18); i < n; i++) {
			double sector = RANDOM.nextDouble() * TERROR_ARCS;

			// The same gap: nothing goes out that way.
			if ((int) sector == gap) {
				continue;
			}

			double angle = base + sector * (Math.PI * 2 / TERROR_ARCS);
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = feet.add(out.scale(0.6)).add(0, 0.2 + 1.2 * RANDOM.nextDouble(), 0);
			vapour(at, out.scale(radius * (0.04 + 0.03 * RANDOM.nextDouble())), 0.3f, 1.3f, 20 + RANDOM.nextInt(10));
		}
	}

	private static void victim(EffectEventPayload payload, @Nullable Entity victim) {
		int length = Math.max(1, Math.round(payload.intensity() * 20f));
		Afraid old = AFRAID.remove(payload.entityId());

		if (old != null) {
			for (Tendril coil : old.coils) {
				coil.release(6);
			}
		}

		Afraid afraid = new Afraid(ticks, ticks + length);
		AFRAID.put(payload.entityId(), afraid);

		if (victim == null || !Vfx.inRange(victim.position())) {
			return;
		}

		for (int i = 0, n = Vfx.count(WISPS); i < n; i++) {
			wisp(victim);
		}

		// Black smoke winds up round the legs, each strand its own way.
		long owner = EnergyRender.owner(OWNER_VICTIM, victim.getId());
		float width = victim.getBbWidth();

		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			float top = 0.42f + 0.18f * RANDOM.nextFloat();
			Tendril coil = Tendril.between(owner, Anchor.body(victim.getId(), 0.5f, 0f, 0.5f), Anchor.body(victim.getId(), 0.5f, top, 0.5f), Palette.SHADOW)
					.life(Math.min(length, 40), 2f, 10f, 9f + 4f * i).width(0.9f, 0.3f).strength(0.85f).writhe(0.05f, 2f, 0.05f)
					.coil((i % 2 == 0 ? 1.4f : -1.2f) + 0.3f * RANDOM.nextFloat(), width * 0.55f + 0.1f, 0.025f + 0.015f * RANDOM.nextFloat());
			afraid.coils.add(coil);
			EnergyRender.add(coil);
		}

		breath(victim);
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

	/** A short, cold breath out of a frightened mouth. */
	private static void breath(Entity victim) {
		if (EnergyKit.firstPerson(victim)) {
			return;
		}

		Vec3 look = victim.getViewVector(1f);
		Vec3 mouth = victim.getEyePosition().add(look.scale(victim.getBbWidth() * 0.5 + 0.05)).add(0, -0.12, 0);

		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			ForceParticle p = EnergyKit.spawn(EnergyParticles.MIST, mouth.add(EnergyKit.jitter(0.03)), look.scale(0.03 + 0.02 * RANDOM.nextDouble()).add(0, 0.006, 0));

			if (p != null) {
				p.color(BREATH[0], BREATH[1], BREATH[2]).size(0.05f, 0.24f).alpha(0.32f).life(16 + RANDOM.nextInt(6)).drag(0.9f);
			}
		}
	}

	/** A puff of the dark vapour, growing as it rolls on. */
	private static void vapour(Vec3 at, Vec3 velocity, float size, float endSize, int life) {
		ForceParticle p = EnergyKit.spawn(EnergyParticles.MIST, at, velocity);

		if (p != null) {
			p.color(VAPOUR[0], VAPOUR[1], VAPOUR[2]).size(size, endSize).alpha(0.62f).life(life).drag(0.9f);
		}
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

		float in = Mth.clamp((ticks - afraid.start + partialTick) / SHIVER_IN, 0f, 1f);
		float out = Mth.clamp((afraid.until - ticks - partialTick) / SHIVER_OUT, 0f, 1f);
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
			Afraid afraid = entry.getValue();
			Entity victim = level.getEntity(entry.getKey());

			if (ticks > afraid.until || victim == null || victim.isRemoved() || !victim.isAlive()) {
				for (Tendril coil : afraid.coils) {
					coil.release(8);
				}

				it.remove();
				continue;
			}

			for (Tendril coil : afraid.coils) {
				coil.keep((int) Math.min(10, afraid.until - ticks + 2));
			}

			long age = ticks - afraid.start;

			if (age % WISP_INTERVAL == 0 && Vfx.inRange(victim.position())) {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					wisp(victim);
				}
			}

			if (age % BREATH_INTERVAL == BREATH_INTERVAL - 1 && Vfx.inRange(victim.position())) {
				breath(victim);
			}
		}
	}

	public static void clear() {
		AFRAID.clear();
	}

	private FearFx() {
	}
}

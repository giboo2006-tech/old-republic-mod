package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.dustColor;
import static com.oldrepublic.client.force.ForceFxUtil.groundBelow;
import static com.oldrepublic.client.force.ForceFxUtil.jitter;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;
import static com.oldrepublic.client.force.ForceFxUtil.ripple;
import static com.oldrepublic.client.force.ForceFxUtil.spawn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Pull visuals and sounds (spec 8.1): a contracting ripple at the clawed hand, streaks that
 * peel off the target and stream toward the caster, dust kicked from under a dragged target, a
 * dust puff on arrival, metal sparks on a disarm and short dust trails behind pulled items. No glow
 * outline on the target.
 */
final class PullFx {
	/** Ticks a dragged target keeps kicking up dust. */
	private static final int DRAG_DUST_TICKS = 14;

	/** Dragged entity id to the client tick its dust ends. */
	private static final Map<Integer, Long> DRAGGED = new HashMap<>();

	/** {@code force_pull}: the reach. Entity = caster, pos = hand, dir = look. */
	static void onCast(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 hand = payload.pos();
		play(level, hand, ModSounds.FORCE_PULL_CAST, 0.9f, pitchVariance(0.05f));

		if (entity != null) {
			if (entity == mc.player && PowerAnimations.isActive(entity.getId(), PowerAnimations.Kind.PULL)) {
				PowerAnimations.confirm(entity.getId(), PowerAnimations.Kind.PULL);
			} else {
				PowerAnimations.start(entity.getId(), PowerAnimations.Kind.PULL, PowerAnimations.PULL_TICKS, 0);
			}
		}

		if (!Vfx.inRange(hand)) {
			return;
		}

		// Anticipation: bent air contracting into the palm.
		ripple(hand, Vec3.ZERO, 0.55f, 0.08f, 0.14f, 6);
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			Vec3 from = hand.add(dir.scale(1.2 + RANDOM.nextDouble())).add(jitter(0.35));
			ForceParticle p = spawn(ForceParticles.PULL_STREAK, from, hand.subtract(from).scale(1 / 5.0));

			if (p != null) {
				p.alpha(0.3f).size(0.18f, 0.08f).life(5);
			}
		}
	}

	/** {@code force_pull_hit}: launched, arrived, or items. */
	static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case ForceEffects.PULL_LAUNCHED -> launched(level, payload, entity);
			case ForceEffects.PULL_ARRIVED -> arrived(level, payload, entity);
			case ForceEffects.PULL_ITEMS -> items(level, payload);
			default -> {
			}
		}
	}

	private static void launched(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = target != null ? chest(target) : payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;
		float speed = Math.max(0.3f, payload.intensity());
		play(level, pos, ModSounds.FORCE_PULL_DRAG, 0.8f, 0.9f + 0.15f * Math.min(1f, speed / 1.6f));

		if (target != null) {
			DRAGGED.put(target.getId(), PowerAnimations.ticks() + DRAG_DUST_TICKS);
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		// Streaks peel off the target's outline and race ahead of it toward the caster.
		AABB box = target != null ? target.getBoundingBox() : new AABB(pos, pos).inflate(0.4);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 at = new Vec3(box.minX + RANDOM.nextDouble() * box.getXsize(), box.minY + RANDOM.nextDouble() * box.getYsize(),
					box.minZ + RANDOM.nextDouble() * box.getZsize());
			ForceParticle p = spawn(ForceParticles.PULL_STREAK, at, dir.scale(speed * (0.9 + RANDOM.nextDouble() * 0.4)).add(jitter(0.03)));

			if (p != null) {
				p.alpha(0.4f).size(0.22f, 0.32f).life(6 + RANDOM.nextInt(4)).drag(0.92f);
			}
		}

		if (target != null) {
			groundKick(level, target, dir, Vfx.count(6));
		}
	}

	private static void arrived(ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 feet = payload.pos();
		play(level, feet, ModSounds.FORCE_PULL_CATCH, 0.8f, pitchVariance(0.06f));

		if (target != null) {
			DRAGGED.remove(target.getId());
		}

		if (!Vfx.inRange(feet)) {
			return;
		}

		BlockPos ground = groundBelow(level, feet.add(0, 0.5, 0));
		float[] rgb = ground != null ? dustColor(level, ground) : new float[] {ForceFxUtil.DEFAULT_DUST_R, ForceFxUtil.DEFAULT_DUST_G, ForceFxUtil.DEFAULT_DUST_B};

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, feet.add(out.scale(0.3)).add(0, 0.1, 0), out.scale(0.06).add(0, 0.03, 0));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.09f, 0.07f).alpha(0.6f).life(18 + RANDOM.nextInt(6)).drag(0.9f).fall(0.01f, true);
			}
		}
	}

	private static void items(ClientLevel level, EffectEventPayload payload) {
		Vec3 point = payload.pos();
		Vec3 toHand = payload.dir();
		play(level, point, ModSounds.FORCE_PULL_DRAG, 0.5f, 1.15f);

		if (!Vfx.inRange(point)) {
			return;
		}

		int items = Math.max(1, Math.round(payload.intensity()));
		Vec3 dir = toHand.lengthSqr() > 1.0e-6 ? toHand.normalize() : Vec3.ZERO;

		for (int i = 0, n = Vfx.count(Math.min(12, 3 * items)); i < n; i++) {
			Vec3 at = point.add(jitterScalar(0.8), 0.15 + RANDOM.nextDouble() * 0.2, jitterScalar(0.8));
			ForceParticle p = spawn(ForceParticles.PULL_STREAK, at, dir.scale(0.5 + RANDOM.nextDouble() * 0.3));

			if (p != null) {
				p.alpha(0.3f).size(0.15f, 0.2f).life(6);
			}
		}

		BlockPos ground = groundBelow(level, point.add(0, 0.5, 0));

		if (ground != null) {
			float[] rgb = dustColor(level, ground);

			for (int i = 0, n = Vfx.count(4); i < n; i++) {
				ForceParticle p = spawn(ForceParticles.GATHER_DUST, point.add(jitterScalar(0.6), 0.1, jitterScalar(0.6)), dir.scale(0.1).add(0, 0.04, 0));

				if (p != null) {
					p.color(rgb[0], rgb[1], rgb[2]).size(0.07f, 0.06f).alpha(0.55f).life(16).drag(0.9f);
				}
			}
		}
	}

	/** {@code force_pull_disarm}: the weapon wrenched out of line. Entity = target, pos = weapon hand. */
	static void onDisarm(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Vec3 pos = payload.pos();
		play(level, pos, ModSounds.FORCE_PULL_DISARM, 0.9f, pitchVariance(0.05f));

		if (!Vfx.inRange(pos)) {
			return;
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 vel = new Vec3(jitterScalar(0.12), 0.06 + RANDOM.nextDouble() * 0.1, jitterScalar(0.12));
			ForceParticle p = spawn(ForceParticles.LIGHTNING_SPARK, pos.add(jitter(0.08)), vel);

			if (p != null) {
				// Hot metal, not lightning: warm white to orange, falling.
				p.color(1f, 0.78f + RANDOM.nextFloat() * 0.15f, 0.45f).size(0.03f, 0.01f).life(8 + RANDOM.nextInt(5)).fall(0.6f, true).drag(0.94f);
			}
		}
	}

	/** Dust kicked up under a dragged target's feet. */
	private static void groundKick(ClientLevel level, Entity target, Vec3 dir, int count) {
		BlockPos ground = groundBelow(level, target.position().add(0, 0.4, 0));

		if (ground == null) {
			return;
		}

		BlockState state = level.getBlockState(ground);
		float[] rgb = dustColor(level, ground);
		Vec3 feet = new Vec3(target.getX(), ground.getY() + 1.05, target.getZ());

		for (int i = 0; i < count; i++) {
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, feet.add(jitterScalar(0.35), 0, jitterScalar(0.35)),
					dir.scale(-0.04).add(jitterScalar(0.03), 0.04 + RANDOM.nextDouble() * 0.04, jitterScalar(0.03)));

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.08f, 0.06f).alpha(0.55f).life(16 + RANDOM.nextInt(6)).drag(0.9f).fall(0.02f, true);
			}

			if (i % 2 == 0) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), feet.x, feet.y, feet.z, -dir.x * 0.1, 0.1, -dir.z * 0.1);
			}
		}
	}

	/** Dust trail under dragged targets while they move. */
	static void tick(ClientLevel level) {
		if (DRAGGED.isEmpty()) {
			return;
		}

		long now = PowerAnimations.ticks();
		Iterator<Map.Entry<Integer, Long>> it = DRAGGED.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity target = level.getEntity(entry.getKey());

			if (now > entry.getValue() || !(target instanceof LivingEntity) || target.isRemoved()) {
				it.remove();
				continue;
			}

			Vec3 motion = target.getDeltaMovement();

			if (now % 2 == 0 && target.onGround() && motion.horizontalDistanceSqr() > 0.01 && Vfx.inRange(target.position())) {
				groundKick(level, target, motion.normalize(), Vfx.count(2));
			}
		}
	}

	static void clear() {
		DRAGGED.clear();
	}

	private PullFx() {
	}
}

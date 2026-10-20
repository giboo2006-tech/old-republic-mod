package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.jitter;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
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
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Heal visuals and sounds (spec 8.4): warm {@code force_mote} particles drifting in and
 * streaming into the chest, a faint ripple at each heal tick, the end drift upward, the scatter
 * when interrupted, thin streams to Shared Light allies and Serene Ward glints; with Mending Breath
 * (spec 10.4), dark grey-green wisps drawn out of a cleansed body that thin out upward. No halo,
 * ring or column of light.
 */
final class HealFx {
	/** Start-up, longest channel and the spec's 10-tick grace: the watchdog end of a channel. */
	private static final int CHANNEL_WATCHDOG = 6 + 60 + 10;
	private static final int STARTUP_TICKS = 6;
	private static final int HEAL_INTERVAL = 5;
	/** Warmer than the meditation motes: pale gold-white. */
	private static final float MOTE_R = 1f;
	private static final float MOTE_G = 0.9f;
	private static final float MOTE_B = 0.68f;

	/** Ally id to the caster id whose stream reaches it. */
	private static final Map<Integer, Integer> ALLIES = new HashMap<>();
	/** Warded entity id to the client tick the ward glints end. */
	private static final Map<Integer, Long> WARDS = new HashMap<>();

	/** {@code force_heal}: start, end or interrupted (entity = caster), or cleansed (entity = the cleansed). */
	static void onHeal(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		Vec3 chest = chest(caster);

		switch (payload.variant()) {
			case ForceEffects.CHANNEL_START -> {
				play(level, chest, ModSounds.FORCE_HEAL_START, 0.8f, 1f);

				// For the own prediction this confirms the running channel.
				startChannel(caster, false);

				if (Vfx.inRange(chest)) {
					inwardMotes(chest, Vfx.count(10), 2.0);
				}
			}
			case ForceEffects.CHANNEL_END -> {
				endChannel(caster);
				play(level, chest, ModSounds.FORCE_HEAL_END, 0.8f, 1f);

				if (Vfx.inRange(chest)) {
					for (int i = 0, n = Vfx.count(10); i < n; i++) {
						mote(chest.add(jitter(0.3)), new Vec3(jitterScalar(0.01), 0.025 + RANDOM.nextDouble() * 0.02, jitterScalar(0.01)), 20);
					}
				}
			}
			case ForceEffects.CHANNEL_INTERRUPTED -> {
				endChannel(caster);
				play(level, chest, ModSounds.FORCE_HEAL_INTERRUPT, 0.9f, 1f);

				if (Vfx.inRange(chest)) {
					scatter(chest, Vfx.count(10));
				}
			}
			case ForceEffects.HEAL_CLEANSED -> {
				// A soft, higher exhale of the heal's end sound as the sickness leaves.
				play(level, chest, ModSounds.FORCE_HEAL_END, 0.4f, 1.25f);

				if (Vfx.inRange(chest)) {
					cleansedWisps(caster, Vfx.count(8));
				}
			}
			default -> {
			}
		}
	}

	/** Mending Breath: dull grey-green wisps leave the body, drift outward and thin out as they rise. */
	private static void cleansedWisps(LivingEntity entity, int count) {
		AABB box = entity.getBoundingBox();

		for (int i = 0; i < count; i++) {
			Vec3 at = new Vec3(box.minX + RANDOM.nextDouble() * box.getXsize(), box.minY + (0.3 + RANDOM.nextDouble() * 0.6) * box.getYsize(),
					box.minZ + RANDOM.nextDouble() * box.getZsize());
			Vec3 out = at.subtract(box.getCenter().x, at.y, box.getCenter().z);
			Vec3 drift = (out.lengthSqr() < 1.0e-4 ? jitter(1).multiply(1, 0, 1) : out).normalize().scale(0.015).add(0, 0.03 + RANDOM.nextDouble() * 0.02, 0);
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, at, drift);

			if (p != null) {
				p.color(0.36f, 0.42f, 0.33f).size(0.05f, 0.14f).alpha(0.45f).life(20 + RANDOM.nextInt(8)).drag(0.93f);
			}
		}
	}

	/** Starts the pose and the loop: from the event, or as the own prediction ({@code predicted}). */
	static void startChannel(LivingEntity caster, boolean predicted) {
		if (predicted) {
			PowerAnimations.startPredicted(caster.getId(), PowerAnimations.Kind.HEAL, -1, CHANNEL_WATCHDOG);
		} else {
			PowerAnimations.start(caster.getId(), PowerAnimations.Kind.HEAL, -1, CHANNEL_WATCHDOG);
		}

		PowerAnimations.loop(caster, PowerAnimations.Kind.HEAL, ModSounds.FORCE_HEAL_LOOP, 0.5f, 1f);
	}

	private static void endChannel(LivingEntity caster) {
		PowerAnimations.stop(caster.getId(), PowerAnimations.Kind.HEAL);
		ALLIES.values().removeIf(id -> id == caster.getId());
	}

	/** {@code force_heal_ally}: a Shared Light stream starts or stops. Entity = ally. */
	static void onAlly(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity ally) {
		if (ally == null) {
			return;
		}

		if (ForceEffects.valueOf(payload.variant()) == ForceEffects.CHANNEL_START) {
			ALLIES.put(ally.getId(), ForceEffects.casterOf(payload.variant()));
		} else {
			ALLIES.remove(ally.getId());
		}
	}

	/** {@code force_heal_ward}: Serene Ward granted. Entity = caster, intensity = duration in ticks. */
	static void onWard(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (entity == null) {
			return;
		}

		play(level, chest(entity), ModSounds.FORCE_HEAL_WARD, 0.7f, 1f);
		WARDS.put(entity.getId(), PowerAnimations.ticks() + Math.max(1, Math.round(payload.intensity())));
	}

	/** Channel streams, ally streams and ward glints. Called every client tick. */
	static void tick(ClientLevel level) {
		long now = PowerAnimations.ticks();

		for (int id : PowerAnimations.active(PowerAnimations.Kind.HEAL)) {
			if (level.getEntity(id) instanceof LivingEntity caster && Vfx.inRange(caster.position())) {
				channelTick(caster, PowerAnimations.age(id, PowerAnimations.Kind.HEAL, 0f));
			}
		}

		tickAllies(level, now);
		tickWards(level, now);
	}

	/** Sustain: motes stream into the chest and rise loosely along the body; a faint pulse per heal tick. */
	private static void channelTick(LivingEntity caster, float age) {
		Vec3 chest = chest(caster);
		int tick = (int) age;

		if (tick % 2 == 0) {
			inwardMotes(chest, Vfx.count(2), 1.4);
		}

		if (tick % 3 == 0) {
			AABB box = caster.getBoundingBox();
			Vec3 at = new Vec3(box.minX + RANDOM.nextDouble() * box.getXsize(), box.minY + RANDOM.nextDouble() * box.getYsize() * 0.6,
					box.minZ + RANDOM.nextDouble() * box.getZsize());
			mote(at, new Vec3(0, 0.03, 0), 18);
		}

		if (tick >= STARTUP_TICKS && (tick - STARTUP_TICKS) % HEAL_INTERVAL == 0) {
			ripple(chest, Vec3.ZERO, 0.15f, 0.4f, 0.07f, 6);
		}
	}

	private static void tickAllies(ClientLevel level, long now) {
		if (ALLIES.isEmpty() || now % 2 != 0) {
			return;
		}

		Iterator<Map.Entry<Integer, Integer>> it = ALLIES.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			Entity ally = level.getEntity(entry.getKey());
			Entity caster = level.getEntity(entry.getValue());

			if (ally == null || caster == null || ally.isRemoved() || !PowerAnimations.isActive(caster.getId(), PowerAnimations.Kind.HEAL)) {
				it.remove();
				continue;
			}

			Vec3 from = chest(caster);
			Vec3 to = chest(ally);

			if (!Vfx.inRange(from) && !Vfx.inRange(to)) {
				continue;
			}

			// A thin stream: motes leave the caster and reach the ally in about 14 ticks.
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 start = from.add(jitter(0.1));
				mote(start, to.subtract(start).scale(1 / 14.0), 14);
			}
		}
	}

	private static void tickWards(ClientLevel level, long now) {
		if (WARDS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Long>> it = WARDS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());

			if (now > entry.getValue() || entity == null || entity.isRemoved() || !entity.isAlive()) {
				it.remove();
				continue;
			}

			if (now % 4 != 0 || !Vfx.inRange(entity.position())) {
				continue;
			}

			// Sparse specks on the body's outline: a point on the bounding box's side faces.
			AABB box = entity.getBoundingBox().inflate(0.05);
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double rx = box.getXsize() * 0.5;
			double rz = box.getZsize() * 0.5;
			Vec3 at = new Vec3(box.getCenter().x + Math.cos(angle) * rx, box.minY + RANDOM.nextDouble() * box.getYsize(), box.getCenter().z + Math.sin(angle) * rz);
			spawn(ForceParticles.WARD_GLINT, at, new Vec3(0, 0.005, 0));
		}
	}

	/** Motes that drift in from about {@code radius} blocks around the chest. */
	private static void inwardMotes(Vec3 chest, int count, double radius) {
		for (int i = 0; i < count; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double r = radius * (0.7 + RANDOM.nextDouble() * 0.3);
			Vec3 from = chest.add(Math.cos(angle) * r, (RANDOM.nextDouble() - 0.4) * 1.2, Math.sin(angle) * r);
			int life = 16 + RANDOM.nextInt(6);
			mote(from, chest.subtract(from).scale(1.0 / life), life);
		}
	}

	/** Interrupted: the gathered motes scatter outward and fall. */
	private static void scatter(Vec3 chest, int count) {
		for (int i = 0; i < count; i++) {
			Vec3 out = jitter(1).normalize();
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, chest.add(out.scale(0.2)), out.scale(0.12).add(0, 0.05, 0));

			if (p != null) {
				p.color(MOTE_R, MOTE_G, MOTE_B).size(0.05f, 0.03f).alpha(0.8f).life(18 + RANDOM.nextInt(6)).drag(0.9f).fall(0.4f, true).fullBright();
			}
		}
	}

	/** One warm {@code force_mote} (the quest feature's particle) with an exact lifetime. */
	private static void mote(Vec3 pos, Vec3 velocity, int life) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(QuestParticles.FORCE_MOTE, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);

		if (particle instanceof SingleQuadParticle quad) {
			quad.setColor(MOTE_R, MOTE_G, MOTE_B);
		}

		if (particle != null) {
			particle.setLifetime(life);
		}
	}

	static void clear() {
		ALLIES.clear();
		WARDS.clear();
	}

	private HealFx() {
	}
}

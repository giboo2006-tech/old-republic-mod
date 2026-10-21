package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Flare;
import com.oldrepublic.client.force.energy.Glint;
import com.oldrepublic.client.force.energy.Mote;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Tendril;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Force Heal visuals and sounds (spec 8.4): warm light gathered out of the air into the cupped hands. Motes of gold light
 * spiral in from all round and vanish into the chest, trailing light; the hands hold a small warm light that breathes; each
 * heal tick it swells and a few motes rise off the body; at the end the light is breathed out, rising and spreading, and
 * an interrupted heal scatters it to fall and die. Shared Light runs a strand of the same light from the hands to each
 * ally, beads of it flowing across; Serene Ward glints on the body's outline, star-points of light that open and close, for
 * as long as the ward lasts (on the own player, until its absorption is used up); with Mending Breath (spec 10.4) dull
 * grey-green wisps are drawn out of a cleansed body. No halo, ring or column of light.
 */
final class HealFx {
	/** Start-up, longest channel and the spec's 10-tick grace: the watchdog end of a channel. */
	private static final int CHANNEL_WATCHDOG = 6 + 60 + 10;
	private static final int STARTUP_TICKS = 6;
	private static final int HEAL_INTERVAL = 5;
	/** {@link EnergyRender} owner kinds: a channel's light, a Shared Light strand, a ward. */
	private static final int OWNER_CHANNEL = 3;
	private static final int OWNER_ALLY = 4;
	private static final int OWNER_WARD = 5;
	/** Ticks after a ward starts before the own player's missing absorption ends its glints. */
	private static final int WARD_GRACE = 5;
	/** Mending Breath's wisps: dull grey-green. */
	private static final float[] SICKNESS = {0.36f, 0.42f, 0.33f};

	/** A Shared Light strand: whose it is, and its light. */
	private static final class Link {
		final int casterId;
		final Tendril strand;
		final Flare glow;

		Link(int casterId, Tendril strand, Flare glow) {
			this.casterId = casterId;
			this.strand = strand;
			this.glow = glow;
		}
	}

	/** Ally id to its Shared Light strand. */
	private static final Map<Integer, Link> ALLIES = new HashMap<>();
	/** A ward's glints: the client ticks they start and end. */
	private record Ward(long start, long until) {
	}

	/** Warded entity id to its ward's glints. */
	private static final Map<Integer, Ward> WARDS = new HashMap<>();
	/** Caster id to the light in its hands while it heals. */
	private static final Map<Integer, Flare> GLOWS = new HashMap<>();

	/** {@code force_heal}: start, end or interrupted (entity = caster), or cleansed (entity = the cleansed). */
	static void onHeal(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		Vec3 chest = EnergyKit.chest(caster);

		switch (payload.variant()) {
			case ForceEffects.CHANNEL_START -> {
				EnergyKit.play(level, chest, ModSounds.FORCE_HEAL_START, 0.8f, 1f);

				// For the own prediction this confirms the running channel.
				startChannel(caster, false);

				if (Vfx.inRange(chest)) {
					gather(caster, Vfx.count(16), 2.1f);
				}
			}
			case ForceEffects.CHANNEL_END -> {
				endChannel(caster, 10);
				EnergyKit.play(level, chest, ModSounds.FORCE_HEAL_END, 0.8f, 1f);

				if (Vfx.inRange(chest)) {
					exhale(caster, Vfx.count(12));
				}
			}
			case ForceEffects.CHANNEL_INTERRUPTED -> {
				endChannel(caster, 3);
				EnergyKit.play(level, chest, ModSounds.FORCE_HEAL_INTERRUPT, 0.9f, 1f);

				if (Vfx.inRange(chest)) {
					scatter(caster, Vfx.count(12));
				}
			}
			case ForceEffects.HEAL_CLEANSED -> {
				// A soft, higher exhale of the heal's end sound as the sickness leaves.
				EnergyKit.play(level, chest, ModSounds.FORCE_HEAL_END, 0.4f, 1.25f);

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
			Vec3 drift = (out.lengthSqr() < 1.0e-4 ? EnergyKit.randomFlat() : out.normalize()).scale(0.015).add(0, 0.03 + RANDOM.nextDouble() * 0.02, 0);
			ForceParticle p = EnergyKit.spawn(EnergyParticles.MIST, at, drift);

			if (p != null) {
				p.color(SICKNESS[0], SICKNESS[1], SICKNESS[2]).size(0.08f, 0.26f).alpha(0.5f).life(20 + RANDOM.nextInt(8)).drag(0.93f);
			}
		}
	}

	/** Starts the pose, the loop and the light in the hands: from the event, or as the own prediction ({@code predicted}). */
	static void startChannel(LivingEntity caster, boolean predicted) {
		if (predicted) {
			PowerAnimations.startPredicted(caster.getId(), PowerAnimations.Kind.HEAL, -1, CHANNEL_WATCHDOG);
		} else {
			PowerAnimations.start(caster.getId(), PowerAnimations.Kind.HEAL, -1, CHANNEL_WATCHDOG);
		}

		PowerAnimations.loop(caster, PowerAnimations.Kind.HEAL, ModSounds.FORCE_HEAL_LOOP, 0.5f, 1f);
		Flare glow = GLOWS.get(caster.getId());

		if (glow == null && Vfx.inRange(caster.position())) {
			// Drawn a little toward the camera, so the cupped hands do not hide it.
			glow = Flare.on(EnergyRender.owner(OWNER_CHANNEL, caster.getId()), cupped(caster.getId()), Palette.WARM).life(10, 4f, 8f).radius(0.48f)
					.strength(0.8f).core(0.05f).lift(0.22f);
			GLOWS.put(caster.getId(), glow);
			EnergyRender.add(glow);
		}
	}

	/** Ends the pose and the strands; the light in the hands dies away over {@code fade} ticks. */
	private static void endChannel(LivingEntity caster, int fade) {
		PowerAnimations.stop(caster.getId(), PowerAnimations.Kind.HEAL);
		Flare glow = GLOWS.remove(caster.getId());

		if (glow != null) {
			glow.release(fade);
		}

		for (Iterator<Link> it = ALLIES.values().iterator(); it.hasNext();) {
			Link link = it.next();

			if (link.casterId == caster.getId()) {
				link.strand.release(6);
				link.glow.release(8);
				it.remove();
			}
		}
	}

	/** {@code force_heal_ally}: a Shared Light stream starts or stops. Entity = ally. */
	static void onAlly(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity ally) {
		if (ally == null) {
			return;
		}

		Link old = ALLIES.remove(ally.getId());

		if (old != null) {
			old.strand.release(6);
			old.glow.release(8);
		}

		if (ForceEffects.valueOf(payload.variant()) != ForceEffects.CHANNEL_START) {
			return;
		}

		int casterId = ForceEffects.casterOf(payload.variant());
		long owner = EnergyRender.owner(OWNER_ALLY, ally.getId());
		Anchor at = Anchor.body(ally.getId(), 0.5f, 0.6f, 0.5f);
		Tendril strand = Tendril.between(owner, cupped(casterId), at, Palette.WARM).life(10, 3f, 6f, 8f).width(0.85f, 1f).strength(0.9f)
				.writhe(0.1f, 1.3f, 0.04f).beads(2.5f, 0.05f, 1.4f);
		Flare glow = Flare.on(owner, at, Palette.WARM).life(10, 6f, 8f).radius(Math.max(0.6f, ally.getBbHeight() * 0.42f)).strength(0.22f).lift(0.35f);
		ALLIES.put(ally.getId(), new Link(casterId, strand, glow));
		EnergyRender.add(strand);
		EnergyRender.add(glow);
	}

	/** {@code force_heal_ward}: Serene Ward granted. Entity = caster, intensity = duration in ticks. */
	static void onWard(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (entity == null) {
			return;
		}

		EnergyKit.play(level, EnergyKit.chest(entity), ModSounds.FORCE_HEAL_WARD, 0.7f, 1f);
		long now = PowerAnimations.ticks();
		WARDS.put(entity.getId(), new Ward(now, now + Math.max(1, Math.round(payload.intensity()))));

		if (Vfx.inRange(entity.position())) {
			// The ward settles: a run of glints over the body and a breath of warm light.
			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				glint(entity, 8 + RANDOM.nextInt(6), 0.2f + 0.1f * RANDOM.nextFloat(), 1f);
			}

			EnergyRender.add(Flare.on(EnergyRender.owner(OWNER_WARD, entity.getId()), Anchor.body(entity.getId(), 0.5f, 0.55f, 0.5f), Palette.WARM)
					.life(14, 3f, 10f).radius(Math.max(0.8f, entity.getBbHeight() * 0.55f)).strength(0.28f).lift(0.4f));
		}
	}

	/** Channel light, Shared Light strands and ward glints. Called every client tick. */
	static void tick(ClientLevel level) {
		long now = PowerAnimations.ticks();

		for (Iterator<Map.Entry<Integer, Flare>> it = GLOWS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Flare> entry = it.next();

			if (!PowerAnimations.isActive(entry.getKey(), PowerAnimations.Kind.HEAL) || level.getEntity(entry.getKey()) == null) {
				entry.getValue().release(6);
				it.remove();
			}
		}

		for (int id : PowerAnimations.active(PowerAnimations.Kind.HEAL)) {
			if (level.getEntity(id) instanceof LivingEntity caster && Vfx.inRange(caster.position())) {
				channelTick(caster, PowerAnimations.age(id, PowerAnimations.Kind.HEAL, 0f));
			}
		}

		tickAllies(level);
		tickWards(level, now);
	}

	/**
	 * Sustain: motes spiral into the chest from all round and rise loosely along the body; the light in the hands breathes;
	 * each heal tick it swells and a few motes rise off the body.
	 */
	private static void channelTick(LivingEntity caster, float age) {
		int tick = (int) age;
		Flare glow = GLOWS.get(caster.getId());

		if (glow != null) {
			glow.keep(6);
		}

		gather(caster, Vfx.count(1), 1.5f);

		if (tick % 3 == 0) {
			AABB box = caster.getBoundingBox();
			Vec3 at = new Vec3(box.minX + RANDOM.nextDouble() * box.getXsize(), box.minY + RANDOM.nextDouble() * box.getYsize() * 0.5,
					box.minZ + RANDOM.nextDouble() * box.getZsize());
			EnergyRender.add(Mote.at(EnergyRender.owner(OWNER_CHANNEL, caster.getId()), at, new Vec3(0, 0.035, 0), Palette.WARM).life(20, 4f, 8f)
					.size(0.7f, 0.4f).strength(0.85f).drift(-0.0008f, 0.98f).wander(0.004f).twinkle());
		}

		if (tick >= STARTUP_TICKS && (tick - STARTUP_TICKS) % HEAL_INTERVAL == 0) {
			// The light swells with each heal tick, and the warmth rises off the body.
			EnergyRender.add(Flare.on(EnergyRender.owner(OWNER_CHANNEL, caster.getId()), cupped(caster.getId()), Palette.WARM).life(6, 1f, 5f)
					.radius(0.5f, 0.75f).strength(0.4f).lift(0.22f));
			Vec3 chest = EnergyKit.chest(caster);

			for (int i = 0, n = Vfx.count(2); i < n; i++) {
				Vec3 out = EnergyKit.randomFlat().scale(0.025);
				EnergyRender.add(Mote.at(0L, chest.add(EnergyKit.jitter(0.15)), out.add(0, 0.05 + 0.02 * RANDOM.nextDouble(), 0), Palette.WARM)
						.life(22 + RANDOM.nextInt(8), 2f, 10f).size(0.75f, 0.4f).strength(0.9f).drift(-0.0005f, 0.96f).twinkle());
			}
		}
	}

	private static void tickAllies(ClientLevel level) {
		if (ALLIES.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Link>> it = ALLIES.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Link> entry = it.next();
			Link link = entry.getValue();
			Entity ally = level.getEntity(entry.getKey());
			Entity caster = level.getEntity(link.casterId);

			if (ally == null || caster == null || ally.isRemoved() || !PowerAnimations.isActive(caster.getId(), PowerAnimations.Kind.HEAL)) {
				link.strand.release(6);
				link.glow.release(8);
				it.remove();
				continue;
			}

			link.strand.keep(6);
			link.glow.keep(6);
			Vec3 from = EnergyKit.chest(caster);
			Vec3 to = EnergyKit.chest(ally);

			if (PowerAnimations.ticks() % 3 != 0 || !Vfx.inRange(from) && !Vfx.inRange(to)) {
				continue;
			}

			// Now and then a mote runs across beside the strand.
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 start = from.add(EnergyKit.jitter(0.12));
				EnergyRender.add(Mote.at(EnergyRender.owner(OWNER_ALLY, ally.getId()), start, to.subtract(start).normalize().scale(0.1), Palette.WARM)
						.life(40, 2f, 3f).size(0.7f, 0.45f).strength(0.9f)
						.seek(Anchor.body(ally.getId(), 0.5f, 0.6f, 0.5f), 0.2f, 0.2f, RANDOM.nextBoolean() ? 0.35f : -0.35f, 0.18f));
			}
		}
	}

	private static void tickWards(ClientLevel level, long now) {
		if (WARDS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Ward>> it = WARDS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Ward> entry = it.next();
			Ward ward = entry.getValue();
			Entity entity = level.getEntity(entry.getKey());

			if (now > ward.until() || entity == null || entity.isRemoved() || !entity.isAlive() || spent(entity, now - ward.start())) {
				it.remove();
				continue;
			}

			if (now % 3 != 0 || !Vfx.inRange(entity.position()) || EnergyKit.firstPerson(entity)) {
				continue;
			}

			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				glint(entity, 9 + RANDOM.nextInt(6), 0.15f + 0.08f * RANDOM.nextFloat(), 0.95f);
			}
		}
	}

	/**
	 * Whether the ward on {@code entity} is gone before its time: the camera's own player knows its own effects, so its ward
	 * ends when the absorption it gave is used up or taken away (the effect arrives just before the event, a few ticks' grace
	 * all the same). Anyone else's lasts its length.
	 */
	private static boolean spent(Entity entity, long age) {
		return age > WARD_GRACE && entity == Minecraft.getInstance().player && !((LivingEntity) entity).hasEffect(MobEffects.ABSORPTION);
	}

	/** A glint of the ward on the body's outline, where the light catches it. */
	private static void glint(Entity entity, int life, float size, float strength) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		float fx = 0.5f + 0.56f * (float) Math.cos(angle);
		float fz = 0.5f + 0.56f * (float) Math.sin(angle);
		float fy = 0.08f + 0.88f * RANDOM.nextFloat();
		EnergyRender.add(new Glint(EnergyRender.owner(OWNER_WARD, entity.getId()), Anchor.body(entity.getId(), fx, fy, fz), Palette.WARM, life, size,
				strength));
	}

	/** Motes drift in from about {@code radius} blocks round the body and spiral into the chest. */
	private static void gather(LivingEntity caster, int count, float radius) {
		long owner = EnergyRender.owner(OWNER_CHANNEL, caster.getId());
		Vec3 chest = EnergyKit.chest(caster);
		Anchor into = cupped(caster.getId());

		for (int i = 0; i < count; i++) {
			Vec3 out = EnergyKit.randomFlat();
			double r = radius * (0.7 + RANDOM.nextDouble() * 0.3);
			Vec3 from = chest.add(out.scale(r)).add(0, (RANDOM.nextDouble() - 0.45) * 1.6, 0);
			float swirl = (i % 2 == 0 ? 1f : -1f) * (0.6f + 0.5f * RANDOM.nextFloat());
			EnergyRender.add(Mote.at(owner, from, new Vec3(-out.z, 0.01, out.x).scale(0.04 * swirl), Palette.WARM).life(34, 3f, 3f)
					.size(0.9f + 0.35f * RANDOM.nextFloat(), 0.45f).strength(0.95f).twinkle()
					.seek(into, 0.09f + 0.04f * RANDOM.nextFloat(), 0.12f, swirl, 0.16f));
		}
	}

	/** The end: the gathered light is breathed out, rising and spreading as it fades. */
	private static void exhale(LivingEntity caster, int count) {
		Vec3 chest = EnergyKit.chest(caster);

		for (int i = 0; i < count; i++) {
			Vec3 out = EnergyKit.randomFlat().scale(0.03 + 0.04 * RANDOM.nextDouble());
			EnergyRender.add(Mote.at(0L, chest.add(EnergyKit.jitter(0.2)), out.add(0, 0.05 + 0.04 * RANDOM.nextDouble(), 0), Palette.WARM)
					.life(24 + RANDOM.nextInt(12), 1f, 12f).size(0.8f, 0.3f).strength(0.9f).drift(-0.0006f, 0.95f).wander(0.003f).twinkle());
		}

		EnergyRender.add(Flare.on(0L, Anchor.body(caster.getId(), 0.5f, 0.62f, 0.5f), Palette.WARM).life(10, 1f, 9f)
				.radius(Math.max(0.7f, caster.getBbHeight() * 0.45f), 1.1f).strength(0.24f).lift(0.35f));
	}

	/** Interrupted: the gathered light scatters outward, falls and dies. */
	private static void scatter(LivingEntity caster, int count) {
		Vec3 chest = EnergyKit.chest(caster);

		for (int i = 0; i < count; i++) {
			Vec3 out = EnergyKit.randomUnit();
			EnergyRender.add(Mote.at(0L, chest.add(out.scale(0.2)), out.scale(0.1 + 0.06 * RANDOM.nextDouble()).add(0, 0.04, 0), Palette.WARM)
					.life(12 + RANDOM.nextInt(8), 0f, 8f).size(0.75f, 0.25f).strength(0.95f).drift(0.012f, 0.88f));
		}
	}

	/**
	 * Where the cupped hands hold the light: just in front of the chest, or low in the middle of the view for the camera's own
	 * player in first person.
	 */
	private static Anchor cupped(int entityId) {
		return partial -> {
			if (!(Anchor.entity(entityId) instanceof LivingEntity entity)) {
				return null;
			}

			if (EnergyKit.firstPerson(entity)) {
				Vec3 look = entity.getViewVector(partial);
				Vec3 up = EnergyKit.side(look).cross(look).normalize();
				return entity.getEyePosition(partial).add(look.scale(0.62)).add(up.scale(-0.36));
			}

			float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			return entity.getPosition(partial).add(0, entity.getBbHeight() * 0.56, 0).add(forward.scale(entity.getBbWidth() * 0.5 + 0.12));
		};
	}

	static void clear() {
		ALLIES.clear();
		WARDS.clear();
		GLOWS.clear();
	}

	private HealFx() {
	}
}

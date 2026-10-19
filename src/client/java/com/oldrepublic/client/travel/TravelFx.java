package com.oldrepublic.client.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.TravelEffects;
import com.oldrepublic.travel.TravelParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The ship's looks and sounds on the client (spec MILESTONE_4.md 4.1, 4.6, 4.8, 13.3): the
 * {@code ship_engine}, {@code ship_jump}, {@code ship_repair}, {@code ship_damage} and
 * {@code landing_beacon} handlers, the {@code engine_exhaust} and {@code spark_shower} particles,
 * and every ship's running effects, ticked here: nacelle exhaust by phase, ground dust blown out in
 * uneven gusts at take-off and landing, sparks and smoke as the hull wears, the idle hum and the
 * damaged crackle. Every count goes through {@link Vfx}; shakes and blinks respect the settings.
 * No effect forms a shape on the ground.
 */
final class TravelFx {
	/** Ship space (right, up, forward) of each nozzle's mouth, in blocks. */
	private static final double NOZZLE_SIDE = 2.625;
	private static final double NOZZLE_UP = 1.875;
	private static final double NOZZLE_BACK = -5.6;
	/** Effects and loops play for ships this close to the player. */
	private static final double LOOP_RANGE = 40;

	/** A timed effect: it runs once per tick until {@code left} reaches 0. */
	private record Timed(@Nullable Entity ship, Vec3 at, int kind, int[] left) {
	}

	private static final int GUST = 0;
	private static final int BLINK_SET = 1;
	private static final int BLINK_REFUSE = 2;
	private static final int BLINK_RECALL = 3;

	private static final List<Timed> TIMED = new ArrayList<>();
	private static final Map<Integer, ShipLoop> IDLE = new HashMap<>();
	private static final Map<Integer, ShipLoop> CRACKLE = new HashMap<>();
	private static @Nullable Object lastLevel;

	static void init() {
		ParticleProviderRegistry.getInstance().register(TravelParticles.ENGINE_EXHAUST,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Exhaust(level, x, y, z, dx, dy, dz, sprites));
		ParticleProviderRegistry.getInstance().register(TravelParticles.SPARK_SHOWER,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Spark(level, x, y, z, dx, dy, dz, sprites));

		ClientEffects.register(TravelEffects.SHIP_ENGINE, TravelFx::onEngine);
		ClientEffects.register(TravelEffects.SHIP_JUMP, TravelFx::onJump);
		ClientEffects.register(TravelEffects.SHIP_REPAIR, TravelFx::onRepair);
		ClientEffects.register(TravelEffects.SHIP_DAMAGE, TravelFx::onDamage);
		ClientEffects.register(TravelEffects.LANDING_BEACON, TravelFx::onBeacon);
	}

	// ---------------------------------------------------------------- event handlers

	private static void onEngine(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = entity != null ? entity.position() : payload.pos();
		boolean riding = entity != null && mc.player != null && mc.player.getVehicle() == entity;

		switch (payload.variant()) {
			case TravelEffects.ENGINE_SPOOL -> {
				play(level, pos, ModSounds.SHIP_SPOOL, 2.5f, 1f);
				play(level, ramp(entity, pos), ModSounds.SHIP_RAMP, 1f, 1f);
				gusts(entity, pos, 40);
				shake(riding, 0.12f);
			}
			case TravelEffects.ENGINE_CLIMB -> {
				play(level, pos, ModSounds.SHIP_CLIMB, 3f, 1f);
				gusts(entity, pos, 14);
				shake(riding, 0.35f);
			}
			case TravelEffects.ENGINE_DESCENT -> play(level, pos, ModSounds.SHIP_DESCENT, 3f, 1f);
			case TravelEffects.ENGINE_SETTLE -> {
				play(level, pos, ModSounds.SHIP_LAND, 2f, 1f);
				play(level, ramp(entity, pos), ModSounds.SHIP_RAMP, 1f, 0.9f);
				gusts(entity, pos, 18);
				shake(riding, 0.3f);
			}
			default -> {
			}
		}
	}

	private static void onJump(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = entity != null ? entity.position().add(0, 2, 0) : payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0E-4 ? payload.dir().normalize() : new Vec3(0, 0, 1);
		boolean out = payload.variant() == TravelEffects.JUMP_OUT;
		play(level, pos, out ? ModSounds.SHIP_JUMP_OUT : ModSounds.SHIP_JUMP_IN, 4f, 1f);
		shake(entity != null && mc.player != null && mc.player.getVehicle() == entity, out ? 0.5f : 0.3f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		// The streak: exhaust pulled into a line ahead of the ship (out) or trailing from where it came (in).
		RandomSource random = level.getRandom();
		int count = Vfx.count(36);

		for (int i = 0; i < count; i++) {
			double along = random.nextDouble() * 24;
			Vec3 p = out ? pos.add(dir.scale(along)) : pos.add(dir.scale(-along)).add(0, along * 0.4, 0);
			Vec3 v = dir.scale(out ? 1.2 + random.nextDouble() : -(0.4 + random.nextDouble() * 0.4));
			level.addParticle(TravelParticles.ENGINE_EXHAUST, p.x + jitter(random, 0.6), p.y + jitter(random, 0.6), p.z + jitter(random, 0.6), v.x, v.y, v.z);
		}
	}

	private static void onRepair(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		int variant = payload.variant();

		switch (variant) {
			case TravelEffects.REPAIR_WELD -> {
				play(level, pos, ModSounds.SHIP_WELD, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);
				sparks(level, pos, 14, 0.18);
				smoke(level, pos, 2);
			}
			case TravelEffects.REPAIR_STAND_UP -> {
				play(level, pos, ModSounds.SHIP_POWER_UP, 2f, 1f);
				play(level, pos, ModSounds.SHIP_RAMP, 1.2f, 0.7f);
				// The supports hiss away: steam from under each wing, and a few sparks as the lights come on.
				if (entity instanceof ShipBody ship) {
					for (int side = -1; side <= 1; side += 2) {
						Vec3 jack = ship.position().add(ShipBody.local(side * 3.1, 0.6, -0.75, ship.getYRot()));
						steam(level, jack, 10);
					}
				}

				sparks(level, pos, 10, 0.12);
			}
			case TravelEffects.REPAIR_FUEL -> {
				// A cell into the tank: a short vent of exhaust from the filler.
				play(level, pos, ModSounds.SHIP_FIT_PART, 0.9f, 0.75f);
				exhaustPuff(level, pos, 4);
			}
			case TravelEffects.REPAIR_UPGRADE -> {
				// An upgrade fitted or taken out: a lighter clank.
				play(level, pos, ModSounds.SHIP_FIT_PART, 1f, 1.1f);
				sparks(level, pos, 6, 0.1);
			}
			default -> {
				// A part into its socket: a clank, sparks, and the core's hum or the nav computer's chirp in the pitch.
				float pitch = switch (variant) {
					case ShipRules.SOCKET_CORE_INDEX -> 0.8f;
					case ShipRules.SOCKET_NAV_INDEX -> 1.3f;
					default -> 1f;
				};
				play(level, pos, ModSounds.SHIP_FIT_PART, 1f, pitch);
				sparks(level, pos, variant == ShipRules.SOCKET_HULL ? 12 : 8, 0.14);
			}
		}
	}

	private static void onDamage(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof ShipBody ship)) {
			return;
		}

		Vec3 nacelle = ship.position().add(ShipBody.local(NOZZLE_SIDE, NOZZLE_UP, -2.5, ship.getYRot()));
		play(level, nacelle, ModSounds.SHIP_DAMAGED, 1.2f, 1f);
		sparks(level, nacelle, Math.round(8 + 10 * payload.intensity()), 0.2);
		smoke(level, nacelle, 4);
		shake(mc.player != null && mc.player.getVehicle() == ship, 0.4f);
	}

	private static void onBeacon(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 lamp = payload.pos().add(0, 0.35, 0);

		switch (payload.variant()) {
			case TravelEffects.BEACON_REFUSE -> {
				play(level, lamp, ModSounds.BEACON_REFUSE, 1f, 1f);
				TIMED.add(new Timed(null, lamp, BLINK_REFUSE, new int[] {20}));
			}
			case TravelEffects.BEACON_RECALL -> {
				play(level, lamp, ModSounds.BEACON_RECALL, 1.5f, 1f);
				TIMED.add(new Timed(null, lamp, BLINK_RECALL, new int[] {16}));
			}
			default -> {
				play(level, lamp, ModSounds.BEACON_SET, 1f, 1f);
				TIMED.add(new Timed(null, lamp, BLINK_SET, new int[] {18}));
			}
		}
	}

	// ---------------------------------------------------------------- ticking

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}

		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}

		tickTimed(level);

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof ShipBody ship && ship.distanceToSqr(mc.player) <= LOOP_RANGE * LOOP_RANGE) {
				tickShip(level, ship);
				loops(ship);
			}
		}

		IDLE.values().removeIf(ShipLoop::finished);
		CRACKLE.values().removeIf(ShipLoop::finished);
	}

	private static void tickShip(ClientLevel level, ShipBody ship) {
		ShipState state = ship.state();
		int phase = ship.phase();

		if (state == ShipState.HULK || phase == ShipRules.PHASE_HYPERSPACE || !Vfx.inRange(ship.position())) {
			return;
		}

		RandomSource random = level.getRandom();
		float yaw = ship.getYRot();
		int age = ship.tickCount;
		float rate = switch (phase) {
			case ShipRules.PHASE_GROUND -> state == ShipState.READY && age % 14 == 0 ? 1f : 0f;
			case ShipRules.PHASE_SPOOL -> 0.3f + 0.6f * Math.min(1f, ship.phaseAge() / 40f);
			case ShipRules.PHASE_CLIMB, ShipRules.PHASE_JUMP -> 2f;
			case ShipRules.PHASE_DESCENT, ShipRules.PHASE_HELM -> 1.2f;
			case ShipRules.PHASE_SETTLE -> Math.max(0f, 0.8f - ship.phaseAge() / 25f);
			default -> 0f;
		};

		if (rate > 0f) {
			Vec3 back = ShipBody.local(0, 0, -1, yaw);
			double speed = phase == ShipRules.PHASE_GROUND ? 0.03 : 0.12 + 0.1 * rate;

			for (int side = -1; side <= 1; side += 2) {
				int puffs = (int) rate + (random.nextFloat() < rate - (int) rate ? 1 : 0);

				for (int i = 0; i < Vfx.count(puffs); i++) {
					if (puffs == 0) {
						break;
					}

					Vec3 p = ship.position().add(ShipBody.local(side * NOZZLE_SIDE, NOZZLE_UP, NOZZLE_BACK, yaw));
					level.addParticle(TravelParticles.ENGINE_EXHAUST, p.x + jitter(random, 0.3), p.y + jitter(random, 0.3), p.z + jitter(random, 0.3),
							back.x * speed + jitter(random, 0.02), 0.01 + jitter(random, 0.02), back.z * speed + jitter(random, 0.02));
				}
			}
		}

		// Dust kicked up as the descending ship nears the ground.
		if (phase == ShipRules.PHASE_DESCENT && altitude(level, ship) < 8 && random.nextFloat() < 0.5f) {
			gust(level, ship.position(), random);
		}

		int wear = ship.wearLevel();

		if (wear >= ShipRules.WEAR_WORN) {
			// One nacelle sparks now and then and trails thin smoke.
			Vec3 nacelle = ship.position().add(ShipBody.local(NOZZLE_SIDE, NOZZLE_UP, -3, yaw));

			if (random.nextInt(phase == ShipRules.PHASE_GROUND ? 24 : 8) == 0) {
				sparks(level, nacelle, 3, 0.12);
			}

			if (age % (phase == ShipRules.PHASE_GROUND ? 10 : 2) == 0) {
				smoke(level, nacelle, 1);
			}
		}

		if (wear >= ShipRules.WEAR_FAILING && age % 6 == 0) {
			// Smoke from the hull seam.
			smoke(level, ship.position().add(ShipBody.local(jitter(random, 1.2), 3.1, jitter(random, 3), yaw)), 1);
		}
	}

	/** The idle hum while the engines are on, louder in flight; the crackle below the jump minimum. */
	private static void loops(ShipBody ship) {
		boolean powered = ship.state() != ShipState.HULK && ship.phase() != ShipRules.PHASE_HYPERSPACE
				&& (ship.phase() != ShipRules.PHASE_GROUND || ship.state() == ShipState.READY);
		ShipLoop idle = IDLE.get(ship.getId());

		if (powered && (idle == null || idle.finished())) {
			IDLE.put(ship.getId(), ShipLoop.play(ModSounds.SHIP_IDLE, ship, 0.5f, 20, () -> isPowered(ship)));
		} else if (idle != null && !idle.finished()) {
			idle.setTarget(ship.phase() == ShipRules.PHASE_GROUND ? 0.5f : 1.4f);
		}

		boolean damaged = isFailing(ship);
		ShipLoop crackle = CRACKLE.get(ship.getId());

		if (damaged && (crackle == null || crackle.finished())) {
			CRACKLE.put(ship.getId(), ShipLoop.play(ModSounds.SHIP_DAMAGED, ship, 0.6f, 20,
					() -> isFailing(ship)));
		}
	}

	private static boolean isFailing(ShipBody ship) {
		return ship.wearLevel() >= ShipRules.WEAR_FAILING && ship.phase() != ShipRules.PHASE_HYPERSPACE;
	}

	private static boolean isPowered(ShipBody ship) {
		return ship.state() != ShipState.HULK && ship.phase() != ShipRules.PHASE_HYPERSPACE
				&& (ship.phase() != ShipRules.PHASE_GROUND || ship.state() == ShipState.READY);
	}

	private static void tickTimed(ClientLevel level) {
		RandomSource random = level.getRandom();

		for (Iterator<Timed> it = TIMED.iterator(); it.hasNext();) {
			Timed timed = it.next();
			int left = --timed.left()[0];

			if (timed.ship() != null && timed.ship().isRemoved() || left < 0) {
				it.remove();
				continue;
			}

			switch (timed.kind()) {
				case GUST -> {
					// Irregular: some ticks a puff or two, some none, each its own way.
					if (random.nextFloat() < 0.45f) {
						gust(level, timed.ship() != null ? timed.ship().position() : timed.at(), random);
					}
				}
				case BLINK_SET -> blink(level, timed.at(), left, new int[] {17, 11, 5}, 0x9CF0B4);
				case BLINK_REFUSE -> blink(level, timed.at(), left, new int[] {19, 9}, 0xE05A4A);
				default -> blink(level, timed.at(), left, new int[] {15, 12, 9, 6, 3}, 0xFFC060);
			}
		}
	}

	/** One beacon blink at each listed tick (counting down); with flashing off, one steady glow instead. */
	private static void blink(ClientLevel level, Vec3 lamp, int left, int[] at, int color) {
		boolean flashing = Vfx.flashing();

		for (int tick : at) {
			if (left == tick && (flashing || tick == at[0])) {
				int count = flashing ? 3 : 5;

				for (int i = 0; i < count; i++) {
					level.addParticle(new DustParticleOptions(color, flashing ? 1.1f : 0.8f), lamp.x, lamp.y + i * 0.02, lamp.z, 0, 0.002, 0);
				}
			}
		}
	}

	// ---------------------------------------------------------------- pieces

	private static void gusts(@Nullable Entity ship, Vec3 pos, int ticks) {
		TIMED.add(new Timed(ship, pos, GUST, new int[] {ticks}));
	}

	/** A puff of ground dust blown outward from under the ship, in one random direction. */
	private static void gust(ClientLevel level, Vec3 centre, RandomSource random) {
		if (!Vfx.inRange(centre)) {
			return;
		}

		BlockPos column = BlockPos.containing(centre);
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		BlockState ground = level.getBlockState(new BlockPos(column.getX(), groundY - 1, column.getZ()));
		ParticleOptions crumbs = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
		double angle = random.nextDouble() * Mth.TWO_PI;
		double spread = 0.5 + random.nextDouble() * 0.6;
		int count = Vfx.count(3 + random.nextInt(4));

		for (int i = 0; i < count; i++) {
			double a = angle + jitter(random, spread);
			double r = 2 + random.nextDouble() * 3.5;
			double speed = 0.15 + random.nextDouble() * 0.25;
			double x = centre.x + Math.cos(a) * r;
			double z = centre.z + Math.sin(a) * r;
			level.addParticle(i % 2 == 0 ? ParticleTypes.POOF : crumbs, x, groundY + 0.15, z, Math.cos(a) * speed, 0.02 + random.nextDouble() * 0.05,
					Math.sin(a) * speed);
		}
	}

	private static void sparks(ClientLevel level, Vec3 at, int base, double spread) {
		if (!Vfx.inRange(at)) {
			return;
		}

		RandomSource random = level.getRandom();
		int count = Vfx.count(base);

		for (int i = 0; i < count; i++) {
			level.addParticle(TravelParticles.SPARK_SHOWER, at.x + jitter(random, 0.2), at.y + jitter(random, 0.2), at.z + jitter(random, 0.2),
					jitter(random, spread), 0.05 + random.nextDouble() * spread, jitter(random, spread));
		}
	}

	private static void smoke(ClientLevel level, Vec3 at, int base) {
		if (!Vfx.inRange(at)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < Vfx.count(base); i++) {
			level.addParticle(ParticleTypes.SMOKE, at.x + jitter(random, 0.25), at.y + jitter(random, 0.15), at.z + jitter(random, 0.25),
					jitter(random, 0.01), 0.03 + random.nextDouble() * 0.03, jitter(random, 0.01));
		}
	}

	private static void steam(ClientLevel level, Vec3 at, int base) {
		if (!Vfx.inRange(at)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < Vfx.count(base); i++) {
			level.addParticle(ParticleTypes.CLOUD, at.x + jitter(random, 0.3), at.y + jitter(random, 0.3), at.z + jitter(random, 0.3),
					jitter(random, 0.08), 0.02 + random.nextDouble() * 0.06, jitter(random, 0.08));
		}
	}

	private static void exhaustPuff(ClientLevel level, Vec3 at, int base) {
		if (!Vfx.inRange(at)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < Vfx.count(base); i++) {
			level.addParticle(TravelParticles.ENGINE_EXHAUST, at.x, at.y, at.z, jitter(random, 0.05), 0.04 + random.nextDouble() * 0.03, jitter(random, 0.05));
		}
	}

	/** The ship's height above the ground under it. */
	private static double altitude(ClientLevel level, Entity ship) {
		BlockPos column = ship.blockPosition();
		return ship.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
	}

	/** Where the ramp's foot is: behind the tail. */
	private static Vec3 ramp(@Nullable Entity ship, Vec3 fallback) {
		return ship == null ? fallback : ship.position().add(ShipBody.local(0, 0.5, -4.5, ship.getYRot()));
	}

	private static void shake(boolean riding, float amount) {
		if (riding) {
			CameraShake.add(amount);
		}
	}

	private static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private static double jitter(RandomSource random, double spread) {
		return (random.nextDouble() * 2 - 1) * spread;
	}

	/** Stops every loop and timed effect (the level changed or the client left). */
	static void clear() {
		TIMED.clear();
		IDLE.values().forEach(ShipLoop::release);
		CRACKLE.values().forEach(ShipLoop::release);
		IDLE.clear();
		CRACKLE.clear();
	}

	// ---------------------------------------------------------------- particles

	/** A soft puff of nacelle exhaust: blue-white at the nozzle, cooling to grey as it spreads and slows. */
	private static final class Exhaust extends SingleQuadParticle {
		private final float startSize;

		Exhaust(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = -0.01f;
			this.friction = 0.9f;
			this.hasPhysics = false;
			this.lifetime = 22 + random.nextInt(14);
			this.startSize = 0.18f + random.nextFloat() * 0.12f;
			this.quadSize = startSize;
			this.alpha = 0.7f;
			setColor(0.85f, 0.93f, 1f);
		}

		@Override
		public void tick() {
			super.tick();
			float t = (float) age / lifetime;
			quadSize = startSize * (1f + 2.2f * t);
			alpha = 0.7f * (1f - t);
			float grey = Mth.lerp(Math.min(1f, t * 2.5f), 1f, 0.62f);
			setColor(Mth.lerp(t, 0.85f, grey), Mth.lerp(t, 0.93f, grey), Mth.lerp(t, 1f, grey));
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}
	}

	/** A bright orange spark that falls, bounces once off the ground and dies out. */
	private static final class Spark extends SingleQuadParticle {
		private boolean bounced;

		Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0.9f;
			this.friction = 0.96f;
			this.hasPhysics = true;
			this.lifetime = 14 + random.nextInt(12);
			this.quadSize = 0.035f + random.nextFloat() * 0.025f;
			setColor(1f, 0.72f + random.nextFloat() * 0.2f, 0.32f);
		}

		@Override
		public void tick() {
			super.tick();

			if (onGround && !bounced) {
				bounced = true;
				yd = 0.08;
				xd *= 0.5;
				zd *= 0.5;
			}

			float t = (float) age / lifetime;
			setColor(1f, Mth.lerp(t, 0.85f, 0.35f), Mth.lerp(t, 0.35f, 0.1f));
			alpha = Mth.clamp((lifetime - age) / 6f, 0f, 1f);
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
		}

		@Override
		protected int getLightCoords(float partialTick) {
			return 0xF000F0;
		}
	}

	private TravelFx() {
	}
}

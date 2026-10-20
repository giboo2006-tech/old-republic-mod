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
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.TravelEffects;
import com.oldrepublic.travel.TravelParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Free flight's looks and sounds on the client (spec MILESTONE_5.md 4.7, 4.11, 5.4, 15.1, 15.3):
 * the {@code ship_flight}, {@code atmosphere}, {@code ship_refit} and {@code ship_disabled}
 * handlers, the {@code atmosphere_streak} particle, and the running effects of every ship in the
 * air at its owner's helm, ticked here: the engine trail from each nozzle (2 puffs a tick at a hover
 * to 8 at full speed, 12 for the boost's first 10 ticks), thruster puffs while a strafe, climb or
 * descent key is held, the engine loop whose pitch follows the ship's speed (the courier's or the
 * interceptor's), the tumble's sparks and smoke with the pilot's alarm, the refit's sparks and steam,
 * and the streaks around the hull as it breaks the atmosphere.
 *
 * <p>Every count goes through {@link Vfx}; shakes follow the settings and reach only the ship's
 * riders. Nothing in an orbit raises dust, and no effect forms a shape on the ground. Every loop
 * stops when its condition fails, when its ship is gone and when the level changes.
 */
final class FlightFx {
	/** Ships this close to the camera get their trail, puffs and engine loop. */
	private static final double RANGE = 64;
	/** A ship flown faster than this (blocks per tick) counts as full thrust for the trail and the engine's pitch. */
	private static final double FULL_SPEED = 1.2;
	/** The boost's long plume lasts this many ticks (15.1). */
	private static final int BOOST_TICKS = 10;
	/** Thruster puffs seen by everyone last this long after a key goes down (the pilot's own client puffs while it is held). */
	private static final int THRUST_TICKS = 10;
	/** The refit's sparks run this long (15.1: 20 sparks over 40 ticks). */
	private static final int REFIT_TICKS = 40;
	/** The streaks around the hull at the atmosphere line (15.1: 30 over 30 ticks), half as the ship reaches it and half once across. */
	private static final int STREAK_TICKS = 15;
	/** The tumble's sparks and smoke, 5.4 (the server's {@code flight.tumbleTicks} default). */
	private static final int TUMBLE_TICKS = 40;

	/** A timed effect on one ship: it runs once per tick until {@code left} reaches 0. */
	private record Timed(int ship, Vec3 at, int kind, int arg, int[] left) {
	}

	private static final int BOOST = 0;
	private static final int THRUST = 1;
	private static final int TUMBLE = 2;
	private static final int REFIT = 3;

	private static final List<Timed> TIMED = new ArrayList<>();
	private static final Map<Integer, ShipLoop> ENGINES = new HashMap<>();
	private static @Nullable ShipLoop alarm;
	/** The ship whose alarm sounds (the local player's own, disabled), by entity id. */
	private static int alarmShip = -1;
	/** Ticks left of streaks around the local player's ship, and whether it is going up. */
	private static int streaks;
	private static boolean streaksUp;
	private static @Nullable Object lastLevel;

	static void init() {
		ParticleProviderRegistry.getInstance().register(TravelParticles.ATMOSPHERE_STREAK,
				sprites -> (type, level, x, y, z, dx, dy, dz, random) -> new Streak(level, x, y, z, dx, dy, dz, sprites));

		ClientEffects.register(TravelEffects.SHIP_FLIGHT, FlightFx::onFlight);
		ClientEffects.register(TravelEffects.ATMOSPHERE, FlightFx::onAtmosphere);
		ClientEffects.register(TravelEffects.SHIP_REFIT, FlightFx::onRefit);
		ClientEffects.register(TravelEffects.SHIP_DISABLED, FlightFx::onDisabled);
	}

	// ---------------------------------------------------------------- event handlers

	private static void onFlight(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof ShipBody ship)) {
			return;
		}

		Vec3 centre = ship.getBoundingBox().getCenter();
		boolean riding = mc.player != null && mc.player.getVehicle() == ship;

		switch (payload.variant()) {
			case TravelEffects.FLIGHT_HELM -> {
				// The struts fold and the ramp shuts (the model), the engines flare.
				play(level, centre, ModSounds.SHIP_FLIGHT_HELM, 2f, 1f);
				nozzleBurst(level, ship, 6);
			}
			case TravelEffects.FLIGHT_BOOST -> {
				play(level, centre, ModSounds.SHIP_FLIGHT_BOOST, 2.5f, 1f);
				TIMED.add(new Timed(ship.getId(), centre, BOOST, 0, new int[] {BOOST_TICKS}));
				shake(riding, 0.2f);
			}
			case TravelEffects.FLIGHT_THRUSTER -> {
				play(level, centre, ModSounds.SHIP_FLIGHT_THRUSTER, 0.8f, 0.95f + level.getRandom().nextFloat() * 0.1f);
				TIMED.add(new Timed(ship.getId(), centre, THRUST, Math.round(payload.intensity()), new int[] {THRUST_TICKS}));
			}
			case TravelEffects.FLIGHT_IMPACT -> impact(level, ship, payload.pos(), payload.intensity(), riding);
			case TravelEffects.FLIGHT_AUTOPILOT -> play(level, centre, ModSounds.SHIP_FLIGHT_HELM, 1.5f, 0.8f);
			case TravelEffects.FLIGHT_EDGE -> play(level, centre, ModSounds.SHIP_FLIGHT_EDGE, 1.5f, 1f);
			case TravelEffects.FLIGHT_LAND_REFUSED -> {
				if (riding) {
					play(level, centre, ModSounds.SHIP_FLIGHT_LAND_WARN, 1f, 1f);
				}
			}
			default -> {
			}
		}
	}

	/** A crash (4.4): sparks off the struck point and a puff of the struck block's dust (none in an orbit), and a jolt for the riders. */
	private static void impact(ClientLevel level, ShipBody ship, Vec3 at, float lost, boolean riding) {
		play(level, at, ModSounds.SHIP_FLIGHT_IMPACT, 2f, 0.9f + level.getRandom().nextFloat() * 0.2f);
		shake(riding, Mth.clamp(0.3f + lost * 0.4f, 0.3f, 0.9f));

		if (!Vfx.inRange(at)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < Vfx.count(10); i++) {
			level.addParticle(TravelParticles.SPARK_SHOWER, at.x + jitter(random, 0.3), at.y + jitter(random, 0.3), at.z + jitter(random, 0.3),
					jitter(random, 0.25), 0.05 + random.nextDouble() * 0.2, jitter(random, 0.25));
		}

		BlockState struck = struckBlock(level, ship, at);

		if (struck == null || Planets.isOrbit(level)) {
			return;
		}

		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, struck);

		for (int i = 0; i < Vfx.count(10); i++) {
			level.addParticle(dust, at.x + jitter(random, 0.6), at.y + jitter(random, 0.4), at.z + jitter(random, 0.6), jitter(random, 0.15),
					0.05 + random.nextDouble() * 0.15, jitter(random, 0.15));
		}
	}

	/** The block the ship ran into: at the struck point, or just beyond it, away from the ship's centre. */
	private static @Nullable BlockState struckBlock(ClientLevel level, ShipBody ship, Vec3 at) {
		Vec3 out = at.subtract(ship.getBoundingBox().getCenter());
		Vec3 step = out.lengthSqr() > 1.0e-6 ? out.normalize().scale(0.3) : Vec3.ZERO;

		for (Vec3 probe : new Vec3[] {at, at.add(step)}) {
			BlockState state = level.getBlockState(BlockPos.containing(probe));

			if (!state.isAir()) {
				return state;
			}
		}

		return null;
	}

	/**
	 * {@code atmosphere} (to this player): as the ship reaches the line, the overlay and the roar; once
	 * it is across (or turned back), the overlay's end. Streaks rush past the hull after each.
	 */
	private static void onAtmosphere(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		boolean up = payload.variant() == TravelEffects.ATMOSPHERE_UP;
		streaks = STREAK_TICKS;
		streaksUp = up;

		if (payload.intensity() >= TravelEffects.ATMOSPHERE_END) {
			AtmosphereOverlay.end();
			return;
		}

		AtmosphereOverlay.start(up);

		if (mc.player != null) {
			play(level, mc.player.position(), ModSounds.SHIP_FLIGHT_ATMOSPHERE, 1.5f, up ? 1f : 0.9f);
		}
	}

	/** {@code ship_refit}: a puff of steam that hides the body swap, then welding sparks for 40 ticks. */
	private static void onRefit(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		boolean upgrade = payload.variant() == TravelEffects.REFIT_UPGRADE;
		play(level, at, upgrade ? ModSounds.SHIP_UPGRADE_FIT : ModSounds.SHIP_REFIT, 1.5f, 1f);

		if (!upgrade && Vfx.inRange(at)) {
			RandomSource random = level.getRandom();

			for (int i = 0; i < Vfx.count(10); i++) {
				level.addParticle(TravelParticles.ENGINE_EXHAUST, at.x + jitter(random, 2), at.y + jitter(random, 1), at.z + jitter(random, 2),
						jitter(random, 0.05), 0.03 + random.nextDouble() * 0.04, jitter(random, 0.05));
			}
		}

		TIMED.add(new Timed(entity != null ? entity.getId() : -1, at, REFIT, upgrade ? 1 : 0, new int[] {REFIT_TICKS}));
	}

	/** {@code ship_disabled}: the tumble's sparks and smoke and the pilot's alarm; at the rescue's arrival, its sound, and the alarm stops. */
	private static void onDisabled(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof ShipBody ship)) {
			return;
		}

		Vec3 centre = ship.getBoundingBox().getCenter();

		if (payload.variant() == TravelEffects.DISABLED_RESCUED) {
			play(level, centre, ModSounds.SHIP_FLIGHT_RESCUE, 1.5f, 1f);
			stopAlarm();
			return;
		}

		play(level, centre, ModSounds.SHIP_DAMAGED, 2f, 0.8f);
		TIMED.add(new Timed(ship.getId(), centre, TUMBLE, 0, new int[] {TUMBLE_TICKS}));
		LocalPlayer player = mc.player;

		if (player != null && player.getVehicle() == ship && player.getUUID().equals(ship.ownerId())) {
			shake(true, 0.6f);
			stopAlarm();
			alarmShip = ship.getId();
			alarm = ShipLoop.play(ModSounds.SHIP_FLIGHT_ALARM, null, 0.7f, 4, FlightFx::alarmHolds);
		}
	}

	/** The alarm sounds while the local player still rides the disabled ship; the rescue's arrival stops it. */
	private static boolean alarmHolds() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isAlive() && player.getVehicle() != null && player.getVehicle().getId() == alarmShip;
	}

	private static void stopAlarm() {
		if (alarm != null) {
			alarm.release();
			alarm = null;
		}

		alarmShip = -1;
	}

	// ---------------------------------------------------------------- ticking

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level != lastLevel) {
			// Every loop and timed effect of the old level stops; the streaks around the hull carry on across a crossing.
			int keep = streaks;
			clear();
			streaks = keep;
			lastLevel = level;
		}

		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}

		tickTimed(level);

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof ShipBody ship && ship.distanceToSqr(mc.player) <= RANGE * RANGE) {
				if (ship.phase() == ShipRules.PHASE_HELM) {
					trail(level, ship);
				}

				engineLoop(ship);
			}
		}

		if (Piloting.isPiloting()) {
			heldThrusters(level, mc.player);
		}

		if (streaks > 0) {
			streaks--;

			if (mc.player.getVehicle() instanceof ShipBody ship) {
				streak(level, ship, streaksUp);
			}
		}

		ENGINES.values().removeIf(ShipLoop::finished);

		if (alarm != null && alarm.finished()) {
			alarm = null;
			alarmShip = -1;
		}
	}

	/** The engine trail of a ship in the air at its owner's helm: more exhaust the faster it flies, the boost's plume on top. */
	private static void trail(ClientLevel level, ShipBody ship) {
		if (!Vfx.inRange(ship.position())) {
			return;
		}

		float thrust = (float) Mth.clamp(speed(ship) / FULL_SPEED, 0.0, 1.0);
		int perTick = Math.round(2 + 6 * thrust) + (boosting(ship) ? 12 : 0);
		double[][] nozzles = ship.nozzles();
		RandomSource random = level.getRandom();
		Vec3 back = ShipBody.local(0, 0, -1, ship.getYRot(), ship.getXRot());
		int count = Vfx.count(perTick);

		for (int i = 0; i < count; i++) {
			double[] n = nozzles[i % nozzles.length];
			Vec3 p = ship.position().add(ShipBody.local(n[0], n[1], n[2], ship.getYRot(), ship.getXRot()));
			double push = 0.15 + 0.25 * thrust + random.nextDouble() * 0.1;
			level.addParticle(TravelParticles.ENGINE_EXHAUST, p.x + jitter(random, 0.2), p.y + jitter(random, 0.2), p.z + jitter(random, 0.2),
					back.x * push + jitter(random, 0.02), back.y * push + jitter(random, 0.02), back.z * push + jitter(random, 0.02));
		}
	}

	/** The engine loop of a ship flown by hand, its pitch and loudness following the speed; the M4 idle hum takes over once it stops. */
	private static void engineLoop(ShipBody ship) {
		ShipLoop loop = ENGINES.get(ship.getId());

		if (flownByHand(ship) && (loop == null || loop.finished())) {
			SoundEvent sound = ship.interceptor() ? ModSounds.SHIP_INTERCEPTOR_ENGINE : ModSounds.SHIP_FLIGHT_ENGINE;
			loop = ShipLoop.play(sound, ship, 1f, 15, () -> flownByHand(ship));
			ENGINES.put(ship.getId(), loop);
		}

		if (loop != null && !loop.finished()) {
			float thrust = (float) Mth.clamp(speed(ship) / FULL_SPEED, 0.0, 1.0);
			loop.setTarget(0.8f + 0.6f * thrust);
			loop.setPitch(0.75f + 0.6f * thrust + (boosting(ship) ? 0.1f : 0f));
		}
	}

	private static boolean flownByHand(ShipBody ship) {
		return !ship.isRemoved() && ship.piloted() && ship.phase() == ShipRules.PHASE_HELM;
	}

	/** The pilot's own puffs: two a tick from each thruster whose key is held (15.1). */
	private static void heldThrusters(ClientLevel level, LocalPlayer pilot) {
		if (!(pilot.getVehicle() instanceof ShipBody ship)) {
			return;
		}

		Input keys = pilot.input.keyPresses;
		int held = (keys.left() ? TravelEffects.THRUST_LEFT : 0) | (keys.right() ? TravelEffects.THRUST_RIGHT : 0)
				| (keys.jump() ? TravelEffects.THRUST_UP : 0) | (keys.shift() ? TravelEffects.THRUST_DOWN : 0);
		puffs(level, ship, held);
	}

	/**
	 * Short white jets from the hull for each thruster in {@code mask}: a strafe left fires from the
	 * right side, a climb from the belly, a descent from the back.
	 */
	private static void puffs(ClientLevel level, ShipBody ship, int mask) {
		if (mask == 0 || !Vfx.inRange(ship.position())) {
			return;
		}

		double side = ShipBody.footprintHalf(ship.interceptor()) + 0.2;
		double top = ShipBody.footprintHeight(ship.interceptor());
		RandomSource random = level.getRandom();
		float yaw = ship.getYRot();
		float pitch = ship.getXRot();

		for (int bit = TravelEffects.THRUST_LEFT; bit <= TravelEffects.THRUST_DOWN; bit <<= 1) {
			if ((mask & bit) == 0) {
				continue;
			}

			// Where the jet comes from (ship space) and which way it blows.
			double[] from = switch (bit) {
				case TravelEffects.THRUST_LEFT -> new double[] {side, top * 0.45, 0, 1, 0, 0};
				case TravelEffects.THRUST_RIGHT -> new double[] {-side, top * 0.45, 0, -1, 0, 0};
				case TravelEffects.THRUST_UP -> new double[] {0, 0.2, 0, 0, -1, 0};
				default -> new double[] {0, top, 0, 0, 1, 0};
			};
			Vec3 p = ship.position().add(ShipBody.local(from[0], from[1], from[2], yaw, pitch));
			Vec3 out = ShipBody.local(from[3], from[4], from[5], yaw, pitch);

			for (int i = 0; i < Vfx.count(2); i++) {
				level.addParticle(ParticleTypes.CLOUD, p.x + jitter(random, 0.3), p.y + jitter(random, 0.2), p.z + jitter(random, 0.3),
						out.x * 0.25 + jitter(random, 0.03), out.y * 0.25 + jitter(random, 0.03), out.z * 0.25 + jitter(random, 0.03));
			}
		}
	}

	/** One {@code atmosphere_streak} on a shell around the hull, rushing past against the ship's way (down going up, up going down). */
	private static void streak(ClientLevel level, ShipBody ship, boolean up) {
		Vec3 centre = ship.getBoundingBox().getCenter();

		if (!Vfx.inRange(centre)) {
			return;
		}

		RandomSource random = level.getRandom();
		double radius = ShipBody.footprintHalf(ship.interceptor()) + 1.5;

		for (int i = 0; i < Vfx.count(1); i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double x = centre.x + Math.cos(angle) * radius;
			double z = centre.z + Math.sin(angle) * radius;
			double y = centre.y + (up ? 3 : -3) + jitter(random, 1.5);
			level.addParticle(TravelParticles.ATMOSPHERE_STREAK, x, y, z, 0, up ? -1.2 : 1.2, 0);
		}
	}

	private static void tickTimed(ClientLevel level) {
		RandomSource random = level.getRandom();

		for (Iterator<Timed> it = TIMED.iterator(); it.hasNext();) {
			Timed timed = it.next();
			int left = --timed.left()[0];
			Entity entity = timed.ship() >= 0 ? level.getEntity(timed.ship()) : null;

			if (left < 0 || timed.ship() >= 0 && timed.kind() != REFIT && !(entity instanceof ShipBody)) {
				it.remove();
				continue;
			}

			switch (timed.kind()) {
				case THRUST -> puffs(level, (ShipBody) entity, timed.arg());
				case TUMBLE -> tumble(level, (ShipBody) entity, left, random);
				case REFIT -> refitSparks(level, entity instanceof ShipBody ship ? ship.getBoundingBox().getCenter() : timed.at(), timed.arg() == 1, left,
						random);
				default -> {
					// BOOST: read by trail() while it runs.
				}
			}
		}
	}

	/** The tumble (5.4): a spark every 4 ticks and smoke every 2 from a random point of the hull. */
	private static void tumble(ClientLevel level, ShipBody ship, int left, RandomSource random) {
		Vec3 centre = ship.getBoundingBox().getCenter();

		if (!Vfx.inRange(centre)) {
			return;
		}

		double half = ShipBody.footprintHalf(ship.interceptor());
		Vec3 at = centre.add(jitter(random, half), jitter(random, 0.8), jitter(random, half));

		if (left % 4 == 0) {
			level.addParticle(TravelParticles.SPARK_SHOWER, at.x, at.y, at.z, jitter(random, 0.2), 0.1 + random.nextDouble() * 0.1, jitter(random, 0.2));
		}

		if (left % 2 == 0) {
			level.addParticle(EnemyParticles.GRENADE_SMOKE, at.x, at.y, at.z, jitter(random, 0.02), 0.04, jitter(random, 0.02));
		}
	}

	/** The refit's welding: a spark every other tick from somewhere on the hull (at the fitting point for an upgrade). */
	private static void refitSparks(ClientLevel level, Vec3 at, boolean upgrade, int left, RandomSource random) {
		if (left % 2 != 0 || !Vfx.inRange(at)) {
			return;
		}

		double spread = upgrade ? 0.4 : 2.5;
		level.addParticle(TravelParticles.SPARK_SHOWER, at.x + jitter(random, spread), at.y + jitter(random, upgrade ? 0.4 : 1.2),
				at.z + jitter(random, spread), jitter(random, 0.15), 0.08 + random.nextDouble() * 0.12, jitter(random, 0.15));
	}

	/** A flare from every nozzle at once (the helm taken). */
	private static void nozzleBurst(ClientLevel level, ShipBody ship, int perNozzle) {
		if (!Vfx.inRange(ship.position())) {
			return;
		}

		RandomSource random = level.getRandom();
		Vec3 back = ShipBody.local(0, 0, -1, ship.getYRot(), ship.getXRot());

		for (double[] n : ship.nozzles()) {
			Vec3 p = ship.position().add(ShipBody.local(n[0], n[1], n[2], ship.getYRot(), ship.getXRot()));

			for (int i = 0; i < Vfx.count(perNozzle); i++) {
				level.addParticle(TravelParticles.ENGINE_EXHAUST, p.x, p.y, p.z, back.x * 0.3 + jitter(random, 0.05), jitter(random, 0.05),
						back.z * 0.3 + jitter(random, 0.05));
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** How far the ship moved this tick (blocks per tick), as every client sees it. */
	private static double speed(ShipBody ship) {
		return new Vec3(ship.getX() - ship.xo, ship.getY() - ship.yo, ship.getZ() - ship.zo).length();
	}

	/** Whether the boost's plume still runs for this ship. */
	private static boolean boosting(ShipBody ship) {
		for (Timed timed : TIMED) {
			if (timed.kind() == BOOST && timed.ship() == ship.getId()) {
				return true;
			}
		}

		return false;
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
		ENGINES.values().forEach(ShipLoop::release);
		ENGINES.clear();
		stopAlarm();
		streaks = 0;
	}

	// ---------------------------------------------------------------- particles

	/** A thin streak of hot air rushing past the hull at the atmosphere line: white-hot going down, blue-white going up. */
	private static final class Streak extends SingleQuadParticle {
		Streak(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
			super(level, x, y, z, dx, dy, dz, sprites.get(level.getRandom()));
			this.xd = dx;
			this.yd = dy;
			this.zd = dz;
			this.gravity = 0f;
			this.friction = 1f;
			this.hasPhysics = false;
			this.lifetime = 6 + random.nextInt(5);
			this.quadSize = 0.5f + random.nextFloat() * 0.4f;
			this.alpha = 0.85f;

			if (dy > 0) {
				setColor(1f, 0.82f, 0.6f);
			} else {
				setColor(0.82f, 0.9f, 1f);
			}
		}

		@Override
		public void tick() {
			super.tick();
			alpha = 0.85f * (1f - (float) age / lifetime);
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

	private FlightFx() {
	}
}

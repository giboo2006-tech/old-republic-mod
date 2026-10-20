package com.oldrepublic.client.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.space.HaskFighter;
import com.oldrepublic.space.HymnInterceptor;
import com.oldrepublic.space.ShipMissile;
import com.oldrepublic.space.SpaceEffects;
import com.oldrepublic.space.SpaceParticles;
import com.oldrepublic.travel.TravelParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Space's effect events on the client (spec MILESTONE_5.md 15.1, 15.3): muzzle light and the guns'
 * reports, hits on shields (a ripple of bent space and three sparks), on hulls (sparks and plating
 * fragments) and on rock (sparks only), shields going down and coming back, fighters, the generator
 * and the gunship blowing apart, the gunship's bay, the comm lines, the scan and the nav buoy's ping.
 *
 * <p>Space has no air and no ground: no dust, smoke that thins fast and never rises, fragments that
 * keep flying. Every flash is a steady glow with flashing effects off; every count goes through
 * {@link Vfx}; the camera shakes only for the player's own ship and for blasts close by. The sounds
 * of distant fights carry (the films' convention): a loudness above 1 widens their reach.
 */
final class SpaceFx {
	/** Muzzle light colours: the players' guns, the Hymn's violet, the kajidic's orange, the clan's amber. */
	private static final int MUZZLE_PLAYER = 0xFFE6B4;
	private static final int MUZZLE_HYMN = 0xD6A8FF;
	private static final int MUZZLE_RAIDER = 0xFFB070;
	private static final int MUZZLE_HASK = 0xFFD27A;
	private static final int FLASH = 0xFFF0D6;
	private static final int BUOY_GREEN = 0x9CFFB0;
	/** A blast this close shakes the camera, less with distance. */
	private static final double SHAKE_RANGE = 32;

	/** A short effect spread over ticks, following its entity: the overheat's smoke. */
	private static final class Timed {
		final int entityId;
		final Vec3 offset;
		final int length;
		int age;

		Timed(int entityId, Vec3 offset, int length) {
			this.entityId = entityId;
			this.offset = offset;
			this.length = length;
		}
	}

	/** The shield's recharge shimmer takes this long to crawl along the hull. */
	private static final int SHIMMER_TICKS = 20;
	private static final List<Timed> TIMED = new ArrayList<>();
	/** When each nav buoy last pinged (client ticks), so its lamp blinks with its sound. */
	private static final Map<Integer, Long> BLINKS = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	static void init() {
		ClientEffects.register(SpaceEffects.SHIP_WEAPON, SpaceFx::onWeapon);
		ClientEffects.register(SpaceEffects.SHIP_HIT, SpaceFx::onHit);
		ClientEffects.register(SpaceEffects.SHIP_SHIELD, SpaceFx::onShield);
		ClientEffects.register(SpaceEffects.MISSILE_LOCK, SpaceFx::onLock);
		ClientEffects.register(SpaceEffects.SHIP_DESTROYED, SpaceFx::onDestroyed);
		ClientEffects.register(SpaceEffects.GUNSHIP, SpaceFx::onGunship);
		ClientEffects.register(SpaceEffects.SPACE_COMM, SpaceFx::onComm);
		ClientEffects.register(SpaceEffects.SPACE_SCAN, SpaceFx::onScan);
		ClientEffects.register(SpaceEffects.NAV_BUOY, SpaceFx::onBuoy);
	}

	// ---------------------------------------------------------------- weapons

	private static void onWeapon(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		Vec3 dir = payload.dir();
		Vec3 drift = motion(entity);

		switch (payload.variant()) {
			case SpaceEffects.WEAPON_CANNONS -> {
				muzzle(level, at, dir, drift, MUZZLE_PLAYER, 0.45f);
				sound(level, at, ModSounds.SPACE_CANNON_FIRE, SoundSource.PLAYERS, 1.6f, vary(0.05f));
			}
			case SpaceEffects.WEAPON_TURRET -> {
				muzzle(level, at, dir, drift, MUZZLE_PLAYER, 0.35f);
				sound(level, at, ModSounds.SPACE_TURRET_FIRE, SoundSource.PLAYERS, 1.4f, vary(0.05f));
			}
			case SpaceEffects.WEAPON_MISSILE -> {
				smoke(at, drift, 3, 0.04);
				sound(level, at, ModSounds.SPACE_MISSILE_LAUNCH, SoundSource.PLAYERS, 2f, vary(0.04f));
			}
			case SpaceEffects.WEAPON_OVERHEAT -> {
				if (entity != null) {
					TIMED.add(new Timed(entity.getId(), at.subtract(entity.position()), 20));
				}

				sound(level, at, ModSounds.SPACE_CANNON_OVERHEAT, SoundSource.PLAYERS, 1.2f, 1f);
			}
			case SpaceEffects.WEAPON_FIGHTER_LASERS -> {
				muzzle(level, at, dir, drift, muzzleOf(entity), 0.4f);
				sound(level, at, ModSounds.SPACE_FIGHTER_FIRE, SoundSource.HOSTILE, 3f, vary(0.06f));
			}
			case SpaceEffects.WEAPON_FIGHTER_MISSILE -> {
				smoke(at, drift, 3, 0.04);
				sound(level, at, ModSounds.SPACE_MISSILE_LAUNCH, SoundSource.HOSTILE, 3f, 0.9f);
			}
			case SpaceEffects.WEAPON_GUNSHIP_TURRET -> {
				muzzle(level, at, dir, drift, MUZZLE_HYMN, 0.6f);
				sound(level, at, ModSounds.SPACE_FIGHTER_FIRE, SoundSource.HOSTILE, 4f, 0.75f * vary(0.04f));
			}
			default -> {
			}
		}
	}

	/** A short muzzle glow riding with the ship, and the two muzzle sparks of every shot (15.1). */
	private static void muzzle(ClientLevel level, Vec3 at, Vec3 dir, Vec3 drift, int rgb, float size) {
		if (!Vfx.inRange(at)) {
			return;
		}

		EnemyParticleProviders.EnemyParticle glow = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, drift);

		if (glow != null) {
			glow.tint(rgb).size(size).life(3);
		}

		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			Vec3 v = drift.add(dir.scale(0.15)).add(jitter(0.08));
			level.addParticle(TravelParticles.SPARK_SHOWER, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/** The muzzle light of a fighter by who flies it. */
	private static int muzzleOf(@Nullable Entity fighter) {
		if (fighter instanceof HymnInterceptor) {
			return MUZZLE_HYMN;
		}

		return fighter instanceof HaskFighter ? MUZZLE_HASK : MUZZLE_RAIDER;
	}

	// ---------------------------------------------------------------- hits and shields

	private static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		Vec3 out = payload.dir();
		float damage = Math.max(1f, payload.intensity());
		Vec3 drift = motion(entity);
		boolean ours = entity != null && mc.player != null && mc.player.getVehicle() == entity;

		switch (payload.variant()) {
			case SpaceEffects.HIT_SHIELD -> {
				// One ripple of bent space spreading over the struck side, and three sparks (15.1).
				float size = Mth.clamp(1.2f + damage * 0.12f, 1.4f, 4.5f);
				ripple(at, drift.add(out.scale(0.04)), size, size * 2.6f, 0.22f, 10);
				sparks(level, at, drift, out, 3, 0.12);
				sound(level, at, ModSounds.SPACE_BOLT_HIT_SHIELD, SoundSource.NEUTRAL, 2f, vary(0.08f));

				if (ours) {
					CameraShake.add(0.06f);
				}
			}
			case SpaceEffects.HIT_HULL -> {
				// Eight sparks and two plating fragments (15.1); the damage sets only the shake.
				sparks(level, at, drift, out, 8, 0.16);
				debris(level, at, drift, out, 2, 0.18);
				sound(level, at, ModSounds.SPACE_BOLT_HIT_HULL, SoundSource.NEUTRAL, 2f, vary(0.08f));

				if (ours) {
					CameraShake.add(Mth.clamp(damage / 40f, 0.1f, 0.35f));
				}
			}
			case SpaceEffects.HIT_BLOCK -> {
				// Sparks only (15.1): rock in space throws up no dust.
				sparks(level, at, Vec3.ZERO, out, 3, 0.14);
				sound(level, at, ModSounds.BLASTER_IMPACT, SoundSource.NEUTRAL, 1.2f, vary(0.1f));
			}
			default -> {
			}
		}
	}

	private static void onShield(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 centre = entity != null ? entity.getBoundingBox().getCenter() : payload.pos();
		Vec3 drift = motion(entity);
		float reach = entity != null ? (float) Math.min(8, entity.getBbWidth()) : 3f;

		switch (payload.variant()) {
			case SpaceEffects.SHIELD_DOWN -> {
				// A last ripple over the whole hull that tears into eight sparks (15.1).
				ripple(centre, drift, reach, reach * 2.4f, 0.3f, 12);

				for (int i = 0, n = Vfx.count(8); i < n; i++) {
					Vec3 out = jitter(1).normalize();
					Vec3 from = centre.add(out.scale(reach * 0.6));
					Vec3 v = drift.add(out.scale(0.2 + RANDOM.nextDouble() * 0.2));
					level.addParticle(TravelParticles.SPARK_SHOWER, from.x, from.y, from.z, v.x, v.y, v.z);
				}

				sound(level, centre, ModSounds.SPACE_SHIELD_DOWN, SoundSource.NEUTRAL, 2.5f, 1f);
			}
			case SpaceEffects.SHIELD_RECHARGING -> {
				// One faint shimmer (a single ripple, 15.1) crawling from stern to bow along the hull over a second.
				if (entity != null) {
					Vec3 along = Vec3.directionFromRotation(0f, entity.getYRot());
					double length = Math.min(8, Math.max(entity.getBbWidth(), 2.0));
					Vec3 from = centre.subtract(along.scale(length / 2));
					ripple(from, drift.add(along.scale(length / SHIMMER_TICKS)), 0.8f, 2.2f, 0.12f, SHIMMER_TICKS);
				}

				sound(level, centre, ModSounds.SPACE_SHIELD_RECHARGE, SoundSource.NEUTRAL, 1.2f, 1f);
			}
			case SpaceEffects.SHIELD_FULL -> ripple(centre, drift, reach * 0.8f, reach * 1.6f, 0.1f, 10);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- the lock and the warning

	private static void onLock(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case SpaceEffects.LOCK_LOCKING -> SpaceLoops.lockStarted();
			case SpaceEffects.LOCK_LOCKED -> {
				SpaceLoops.lockHeld();
				QuestSounds.ui(ModSounds.SPACE_MISSILE_LOCKED, 1f, 0.7f);
			}
			case SpaceEffects.LOCK_LOST -> SpaceLoops.lockEnded();
			case SpaceEffects.LOCK_INCOMING -> SpaceLoops.incomingStarted();
			case SpaceEffects.LOCK_INCOMING_END -> SpaceLoops.incomingEnded();
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- destruction

	private static void onDestroyed(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		Vec3 drift = motion(entity);

		switch (payload.variant()) {
			case SpaceEffects.DESTROYED_FIGHTER -> {
				burst(level, at, drift, 24, 12, 3f);
				sound(level, at, ModSounds.SPACE_FIGHTER_EXPLODE, SoundSource.NEUTRAL, 4f, vary(0.08f));
				shake(mc, at, 0.25f);
			}
			case SpaceEffects.DESTROYED_GENERATOR -> {
				burst(level, at, drift, 24, 12, 3.5f);
				sound(level, at, ModSounds.SPACE_GUNSHIP_GENERATOR_DOWN, SoundSource.NEUTRAL, 5f, 1f);
				shake(mc, at, 0.25f);
			}
			case SpaceEffects.DESTROYED_GUNSHIP_BLAST -> {
				burst(level, at, drift, 24, 12, 4f);
				sound(level, at, ModSounds.SPACE_FIGHTER_EXPLODE, SoundSource.NEUTRAL, 5f, 0.75f);
				shake(mc, at, 0.3f);
			}
			case SpaceEffects.DESTROYED_GUNSHIP_END -> {
				// The hull breaking in two (the renderer splits it): one last flash and 60 fragments (15.1).
				burst(level, at, drift, 60, 0, 7f);
				sound(level, at, ModSounds.SPACE_GUNSHIP_EXPLODE, SoundSource.NEUTRAL, 8f, 1f);
				shake(mc, at, 0.5f);
			}
			default -> {
			}
		}
	}

	/** A ship blown apart: one flash (a steady glow with flashing off), plating tumbling outward and burning out, and sparks. */
	private static void burst(ClientLevel level, Vec3 at, Vec3 drift, int debris, int sparks, float flash) {
		if (!Vfx.inRange(at)) {
			return;
		}

		EnemyParticleProviders.EnemyParticle glow = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, drift);

		if (glow != null) {
			if (Vfx.flashing()) {
				glow.tint(FLASH).size(flash).life(5);
			} else {
				glow.tint(FLASH).size(flash * 0.6f).life(12).alpha(0.45f);
			}
		}

		for (int i = 0, n = Vfx.count(debris); i < n; i++) {
			Vec3 v = drift.add(jitter(1).normalize().scale(0.12 + RANDOM.nextDouble() * 0.45));
			level.addParticle(SpaceParticles.HULL_DEBRIS, at.x, at.y, at.z, v.x, v.y, v.z);
		}

		for (int i = 0, n = Vfx.count(sparks); i < n; i++) {
			Vec3 v = drift.add(jitter(1).normalize().scale(0.25 + RANDOM.nextDouble() * 0.35));
			level.addParticle(TravelParticles.SPARK_SHOWER, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/** A blast this close to the camera jolts it, less with distance. */
	private static void shake(Minecraft mc, Vec3 at, float strength) {
		if (mc.player == null) {
			return;
		}

		double distance = mc.player.position().distanceTo(at);

		if (distance < SHAKE_RANGE) {
			CameraShake.add(strength * (float) (1 - distance / SHAKE_RANGE));
		}
	}

	// ---------------------------------------------------------------- the gunship, comms, scans and buoys

	private static void onGunship(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		Vec3 drift = motion(entity);

		if (payload.variant() == SpaceEffects.GUNSHIP_BAY_OPENS) {
			// The doors part (the renderer opens them) with a breath of venting gas.
			exhaust(level, at, drift, 6);
			sound(level, at, ModSounds.SPACE_GUNSHIP_LAUNCH, SoundSource.HOSTILE, 2f, 0.6f);
		} else if (payload.variant() == SpaceEffects.GUNSHIP_LAUNCH) {
			exhaust(level, at, drift, 10);
			sound(level, at, ModSounds.SPACE_GUNSHIP_LAUNCH, SoundSource.HOSTILE, 4f, 1f);
		}
	}

	private static void onComm(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		SpaceClientState.comm(payload.variant(), Math.round(payload.intensity()));
		QuestSounds.ui(ModSounds.SPACE_COMM, vary(0.05f), 0.7f);
	}

	private static void onScan(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.variant() == SpaceEffects.SCAN_SCANNING) {
			SpaceLoops.scanStarted();
		} else {
			SpaceLoops.scanEnded();
		}
	}

	private static void onBuoy(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();

		if (payload.variant() == SpaceEffects.BUOY_REACHED) {
			EnemyParticleProviders.EnemyParticle glow = Vfx.inRange(at) ? EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, Vec3.ZERO) : null;

			if (glow != null) {
				glow.tint(BUOY_GREEN).size(1.6f).life(14).alpha(Vfx.flashing() ? 1f : 0.6f);
			}

			sound(level, at, ModSounds.SPACE_BUOY, SoundSource.NEUTRAL, 3f, 1.35f);
		} else {
			if (entity != null) {
				BLINKS.put(entity.getId(), SpaceClientState.ticks());
			}

			sound(level, at, ModSounds.SPACE_BUOY, SoundSource.NEUTRAL, 3f, 1f);
		}
	}

	/** When the buoy {@code id} last pinged (client ticks), or {@link Long#MIN_VALUE} when no ping has come. */
	static long lastBlink(int id) {
		return BLINKS.getOrDefault(id, Long.MIN_VALUE);
	}

	// ---------------------------------------------------------------- ticking

	/** The timed effects, and the missiles' smoke (one puff every two ticks of flight, 15.1). */
	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null || mc.isPaused()) {
			if (level == null) {
				clear();
			}

			return;
		}

		for (Iterator<Timed> it = TIMED.iterator(); it.hasNext();) {
			Timed timed = it.next();
			Entity entity = level.getEntity(timed.entityId);

			if (entity == null || entity.isRemoved() || ++timed.age > timed.length) {
				it.remove();
				continue;
			}

			if (timed.age % 3 == 0) {
				// Six thin puffs from the barrels over twenty ticks (15.1).
				smoke(entity.position().add(timed.offset), motion(entity), 1, 0.03);
			}
		}

		if (SpaceClientState.ticks() % 200 == 0) {
			long now = SpaceClientState.ticks();
			BLINKS.values().removeIf(t -> now - t > 200);
		}

		if (SpaceClientState.ticks() % 2 == 0 && mc.player != null) {
			AABB near = mc.player.getBoundingBox().inflate(ClientConfig.get().preset.effectDistance);

			for (ShipMissile missile : level.getEntitiesOfClass(ShipMissile.class, near, Entity::isAlive)) {
				Vec3 tail = missile.position().subtract(missile.velocity().normalize().scale(0.4));
				smoke(tail, missile.velocity().scale(0.1), 1, 0.01);
			}
		}
	}

	static void clear() {
		TIMED.clear();
		BLINKS.clear();
	}

	// ---------------------------------------------------------------- helpers

	/** The entity's movement over the last tick (what an effect riding with it inherits); zero without one. */
	static Vec3 motion(@Nullable Entity entity) {
		return entity == null ? Vec3.ZERO : new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
	}

	private static void sparks(ClientLevel level, Vec3 at, Vec3 drift, Vec3 out, int base, double speed) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			Vec3 v = drift.add(out.scale(speed)).add(jitter(speed));
			level.addParticle(TravelParticles.SPARK_SHOWER, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void debris(ClientLevel level, Vec3 at, Vec3 drift, Vec3 out, int base, double speed) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			Vec3 v = drift.add(out.scale(speed)).add(jitter(speed * 0.8));
			level.addParticle(SpaceParticles.HULL_DEBRIS, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/** Smoke that thins fast and never rises (no air in an orbit). */
	private static void smoke(Vec3 at, Vec3 drift, int base, double spread) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			EnemyParticleProviders.EnemyParticle puff = EnemyParticleProviders.spawn(EnemyParticles.GRENADE_SMOKE, at, drift.add(jitter(spread)));

			if (puff != null) {
				puff.gravity(0f).life(12 + RANDOM.nextInt(6)).alpha(0.55f);
			}
		}
	}

	/** Venting gas from the gunship's bay: the engines' exhaust, falling away below it. */
	private static void exhaust(ClientLevel level, Vec3 at, Vec3 drift, int base) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			Vec3 v = drift.add(jitter(0.06)).add(0, -0.08, 0);
			level.addParticle(TravelParticles.ENGINE_EXHAUST, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/** One ripple of bent space (the Force push's air-ripple language applied to a hull, 15). */
	private static void ripple(Vec3 at, Vec3 velocity, float startSize, float endSize, float alpha, int life) {
		if (!Vfx.inRange(at)) {
			return;
		}

		Particle particle = Minecraft.getInstance().particleEngine.createParticle(ForceParticles.AIR_RIPPLE, at.x, at.y, at.z, velocity.x, velocity.y,
				velocity.z);

		if (particle instanceof ForceParticle ripple) {
			ripple.size(startSize, endSize).alpha(alpha).life(life);
		}
	}

	/** A positional one-shot at the mod's sound volume. */
	private static void sound(ClientLevel level, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
		level.playLocalSound(at.x, at.y, at.z, sound, source, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Pitch 1 with a small random spread. */
	private static float vary(float spread) {
		return 1f + (RANDOM.nextFloat() * 2f - 1f) * spread;
	}

	private static Vec3 jitter(double amount) {
		return new Vec3((RANDOM.nextDouble() * 2 - 1) * amount, (RANDOM.nextDouble() * 2 - 1) * amount, (RANDOM.nextDouble() * 2 - 1) * amount);
	}

	private SpaceFx() {
	}
}

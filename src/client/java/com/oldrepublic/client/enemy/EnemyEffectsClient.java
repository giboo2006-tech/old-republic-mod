package com.oldrepublic.client.enemy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.CrystalPoacher;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the poacher and its blaster: shot flashes and bolt impacts from effect events,
 * plus per-tick work driven by synced state: the charge whine and muzzle particles during each
 * tell, and bolt fly-by sounds for the local player.
 */
final class EnemyEffectsClient {
	private static final double FLYBY_RADIUS = 3.0;
	/** Bolts are checked for a fly-by within this distance of the player. */
	private static final double FLYBY_SCAN = 8.0;

	/** Charge whine per charging poacher (entity id). Stopped as soon as the tell ends. */
	private static final Map<Integer, EntityBoundSoundInstance> CHARGE_SOUNDS = new HashMap<>();
	/** Last known position of nearby bolts, for fly-by segment tests. */
	private static final Map<Integer, Vec3> BOLT_LAST_POS = new HashMap<>();
	/** Bolts that already played their fly-by. */
	private static final Set<Integer> FLOWN_BY = new HashSet<>();

	static void init() {
		ClientEffects.register(EnemyEffects.BLASTER_FIRE, EnemyEffectsClient::onFire);
		ClientEffects.register(EnemyEffects.BOLT_IMPACT, EnemyEffectsClient::onImpact);
		ClientTickEvents.END_CLIENT_TICK.register(EnemyEffectsClient::tick);
	}

	private static void onFire(Minecraft client, ClientLevel level, EffectEventPayload payload, Entity entity) {
		Vec3 pos = payload.pos();

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();
		Vec3 dir = payload.dir();
		int count = Vfx.count(5);

		for (int i = 0; i < count; i++) {
			double speed = 0.02 + random.nextDouble() * 0.08;
			level.addParticle(EnemyParticles.MUZZLE_GLOW,
					pos.x + jitter(random, 0.05), pos.y + jitter(random, 0.05), pos.z + jitter(random, 0.05),
					dir.x * speed + jitter(random, 0.02), dir.y * speed + jitter(random, 0.02), dir.z * speed + jitter(random, 0.02));
		}
	}

	private static void onImpact(Minecraft client, ClientLevel level, EffectEventPayload payload, Entity entity) {
		Vec3 pos = payload.pos();
		RandomSource random = level.getRandom();
		level.playLocalSound(pos.x, pos.y, pos.z, ModSounds.BLASTER_IMPACT, SoundSource.HOSTILE, 0.9f, 0.9f + random.nextFloat() * 0.2f, false);

		if (!Vfx.inRange(pos)) {
			return;
		}

		Vec3 normal = payload.dir();
		int sparks = Vfx.count(payload.variant() == EnemyEffects.IMPACT_BLOCK ? 10 : 6);

		for (int i = 0; i < sparks; i++) {
			double speed = 0.08 + random.nextDouble() * 0.14;
			level.addParticle(EnemyParticles.BOLT_IMPACT, pos.x, pos.y, pos.z,
					normal.x * speed + jitter(random, 0.12), normal.y * speed + jitter(random, 0.12) + 0.03, normal.z * speed + jitter(random, 0.12));
		}

		// Two quick flashes at the point of impact.
		for (int i = 0; i < Vfx.count(2); i++) {
			level.addParticle(EnemyParticles.MUZZLE_GLOW, pos.x + normal.x * 0.05, pos.y + normal.y * 0.05, pos.z + normal.z * 0.05, 0, 0, 0);
		}

		if (payload.variant() == EnemyEffects.IMPACT_BLOCK) {
			int wisps = Vfx.count(3);

			for (int i = 0; i < wisps; i++) {
				level.addParticle(EnemyParticles.BOLT_SCORCH,
						pos.x + normal.x * 0.08 + jitter(random, 0.1), pos.y + normal.y * 0.08 + jitter(random, 0.1), pos.z + normal.z * 0.08 + jitter(random, 0.1),
						normal.x * 0.01, 0.01 + normal.y * 0.01, normal.z * 0.01);
			}
		}
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		Player player = client.player;

		if (level == null || player == null) {
			clear(client);
			return;
		}

		Set<Integer> chargingSeen = new HashSet<>();
		Set<Integer> boltsSeen = new HashSet<>();
		Vec3 eye = player.getEyePosition();

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof CrystalPoacher poacher) {
				if (poacher.isCharging() && poacher.isAlive()) {
					chargingSeen.add(poacher.getId());
					tickTell(client, level, poacher);
				}
			} else if (entity instanceof BlasterBolt bolt && bolt.distanceToSqr(player) < FLYBY_SCAN * FLYBY_SCAN) {
				boltsSeen.add(bolt.getId());
				tickFlyby(level, player, eye, bolt);
			}
		}

		// Tells that ended (shot fired, cancelled, poacher died or unloaded): stop the whine now.
		Iterator<Map.Entry<Integer, EntityBoundSoundInstance>> sounds = CHARGE_SOUNDS.entrySet().iterator();

		while (sounds.hasNext()) {
			Map.Entry<Integer, EntityBoundSoundInstance> entry = sounds.next();

			if (!chargingSeen.contains(entry.getKey())) {
				client.getSoundManager().stop(entry.getValue());
				sounds.remove();
			}
		}

		BOLT_LAST_POS.keySet().retainAll(boltsSeen);
		FLOWN_BY.retainAll(boltsSeen);
	}

	private static void tickTell(Minecraft client, ClientLevel level, CrystalPoacher poacher) {
		if (!CHARGE_SOUNDS.containsKey(poacher.getId())) {
			EntityBoundSoundInstance whine = new EntityBoundSoundInstance(ModSounds.BLASTER_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f, poacher, level.getRandom().nextLong());
			client.getSoundManager().play(whine);
			CHARGE_SOUNDS.put(poacher.getId(), whine);
		}

		Vec3 muzzle = poacher.muzzlePosition();

		if (!Vfx.inRange(muzzle)) {
			return;
		}

		// The tell is never thinned below one particle per tick; it intensifies near the shot.
		RandomSource random = level.getRandom();
		int count = poacher.clientChargeTicks() > 8 ? Vfx.count(2) : Vfx.count(1);

		for (int i = 0; i < count; i++) {
			level.addParticle(EnemyParticles.MUZZLE_GLOW,
					muzzle.x + jitter(random, 0.06), muzzle.y + jitter(random, 0.06), muzzle.z + jitter(random, 0.06),
					jitter(random, 0.01), 0.004, jitter(random, 0.01));
		}
	}

	private static void tickFlyby(ClientLevel level, Player player, Vec3 eye, BlasterBolt bolt) {
		Vec3 now = bolt.position();
		Vec3 before = BOLT_LAST_POS.put(bolt.getId(), now);

		if (before == null || FLOWN_BY.contains(bolt.getId()) || bolt.getOwner() == player) {
			return;
		}

		if (distanceToSegment(eye, before, now) <= FLYBY_RADIUS) {
			FLOWN_BY.add(bolt.getId());
			level.playLocalSound(now.x, now.y, now.z, ModSounds.BLASTER_BOLT_FLYBY, SoundSource.HOSTILE, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f, false);
		}
	}

	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSqr = ab.lengthSqr();
		double t = lengthSqr < 1.0e-9 ? 0 : Math.max(0, Math.min(1, point.subtract(a).dot(ab) / lengthSqr));
		return point.distanceTo(a.add(ab.scale(t)));
	}

	private static void clear(Minecraft client) {
		if (!CHARGE_SOUNDS.isEmpty()) {
			CHARGE_SOUNDS.values().forEach(client.getSoundManager()::stop);
			CHARGE_SOUNDS.clear();
		}

		BOLT_LAST_POS.clear();
		FLOWN_BY.clear();
	}

	private static double jitter(RandomSource random, double amount) {
		return (random.nextDouble() - 0.5) * 2 * amount;
	}

	private EnemyEffectsClient() {
	}
}

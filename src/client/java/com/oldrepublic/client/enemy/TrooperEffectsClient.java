package com.oldrepublic.client.enemy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.enemy.trooper.BarkCategory;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperEffects;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of every trooper and grenade effect event (spec 13.3) with its sound, plus the
 * per-tick work driven by synced trooper state. Each moment has its phases: the tell or wind-up
 * (anticipation), the moment itself (activation), what travels (the flare, a redirected grenade),
 * the hit (impact) and what lingers (aftermath).
 *
 * <p>Tells ({@code trooper_charge}, {@code grenade_tell}, {@code grenade_fuse}) spend
 * {@link Vfx#tellCount} and are never thinned by the preset; everything else uses
 * {@link Vfx#count}. With flashing effects off, blinks and flickers hold steady. Budgets: 8 or fewer
 * particles per shot, 40 or fewer per grenade burst; nothing draws a ring on the ground. Sound limits:
 * {@code grenade.burst} 2 or fewer per 10 ticks, {@code grenade.fuse} 2 or fewer at once.
 */
final class TrooperEffectsClient {
	/** Muzzle tints per bolt style (0xRRGGBB): orange, blue-white, crimson, amber. */
	private static final int[] STYLE_TINT = {0xFF6A2A, 0x8CC4FF, 0xFF3A4A, 0xFFC060};
	private static final int KOLTO = 0x6CF2D0;
	private static final int RALLY_GOLD = 0xFFD27A;
	/** Allies a completed Rally lights up: those within the spec's 12 blocks (7.2, {@code trooper.rallyRadius}). */
	private static final double RALLY_RADIUS = 12.0;
	private static final int FLARE_RED = 0xFF4A24;
	private static final int BURST_SOUND_WINDOW = 10;
	private static final int BURST_SOUNDS_PER_WINDOW = 2;
	private static final int MAX_FUSE_SOUNDS = 2;
	/** The flare climbs for this many ticks, hangs for {@link #FLARE_HANG} and fades over {@link #FLARE_FADE}. */
	private static final int FLARE_CLIMB = 20;
	private static final int FLARE_HANG = 40;
	private static final int FLARE_FADE = 10;
	/** Most particles a grenade burst may spend, whatever the preset. */
	private static final int BURST_BUDGET = 40;

	private record Flare(Vec3 from, double climb, int start) {
	}

	private record Timed(int start, int length) {
	}

	private static final Map<Integer, EntityBoundSoundInstance> CHARGE_SOUNDS = new HashMap<>();
	private static final Map<Integer, EntityBoundSoundInstance> VENT_SOUNDS = new HashMap<>();
	private static final Map<Integer, EntityBoundSoundInstance> FUSE_SOUNDS = new HashMap<>();
	/** Kolto channels by patient id. */
	private static final Map<Integer, Timed> HEALS = new HashMap<>();
	/** Rally tells by sergeant id, so the end of a completed tell can be shown. */
	private static final Map<Integer, Timed> RALLIES = new HashMap<>();
	/** Last tend sound per downed trooper, so one tending player gives one sound at a time. */
	private static final Map<Integer, Integer> TEND_SOUNDS = new HashMap<>();
	private static final Deque<Integer> BURST_SOUNDS = new ArrayDeque<>();
	private static final List<Flare> FLARES = new ArrayList<>();
	private static int ticks;
	/** The level the tracked effects belong to; a change (dimension, relog) forgets them all. */
	private static @Nullable ClientLevel trackedLevel;

	static void init() {
		ClientEffects.register(TrooperEffects.TROOPER_CHARGE, TrooperEffectsClient::onCharge);
		ClientEffects.register(TrooperEffects.TROOPER_VENT, TrooperEffectsClient::onVent);
		ClientEffects.register(TrooperEffects.KOLTO_HEAL, TrooperEffectsClient::onKoltoHeal);
		ClientEffects.register(TrooperEffects.TROOPER_TEND, TrooperEffectsClient::onTend);
		ClientEffects.register(TrooperEffects.TROOPER_RALLY, TrooperEffectsClient::onRally);
		ClientEffects.register(TrooperEffects.FLARE_CALL, TrooperEffectsClient::onFlare);
		ClientEffects.register(TrooperEffects.GRENADE_TELL, TrooperEffectsClient::onGrenadeTell);
		ClientEffects.register(TrooperEffects.GRENADE_FUSE, TrooperEffectsClient::onGrenadeFuse);
		ClientEffects.register(TrooperEffects.GRENADE_BURST, TrooperEffectsClient::onGrenadeBurst);
		ClientEffects.register(TrooperEffects.GRENADE_RETURN, TrooperEffectsClient::onGrenadeReturn);
		ClientEffects.register(TrooperEffects.TROOPER_BARK, TrooperEffectsClient::onBark);
		ClientEffects.register(TrooperEffects.TROOPER_SALUTE, TrooperEffectsClient::onSalute);
		ClientTickEvents.END_CLIENT_TICK.register(TrooperEffectsClient::tick);
	}

	// ---------------------------------------------------------------- shared helpers

	/** The tint of a bolt style's muzzle glow, by ordinal. */
	static int styleTint(int ordinal) {
		return STYLE_TINT[Mth.clamp(ordinal, 0, STYLE_TINT.length - 1)];
	}

	private static float volume(float base) {
		return base * ClientConfig.get().modSoundVolume;
	}

	private static float pitch(RandomSource random, float spread) {
		return 1f + (random.nextFloat() * 2f - 1f) * spread;
	}

	private static void playAt(ClientLevel level, Vec3 pos, SoundEvent sound, float vol, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume(vol), pitch, false);
	}

	private static EntityBoundSoundInstance playOn(Minecraft client, ClientLevel level, Entity entity, SoundEvent sound, float vol, float pitch) {
		EntityBoundSoundInstance instance = new EntityBoundSoundInstance(sound, SoundSource.HOSTILE, volume(vol), pitch, entity, level.getRandom().nextLong());
		client.getSoundManager().play(instance);
		return instance;
	}

	private static double jitter(RandomSource random, double amount) {
		return (random.nextDouble() - 0.5) * 2 * amount;
	}

	private static Vec3 jitter(RandomSource random, Vec3 pos, double amount) {
		return pos.add(jitter(random, amount), jitter(random, amount), jitter(random, amount));
	}

	private static void particle(SimpleParticleType type, Vec3 pos, Vec3 velocity, int tint, float size, int life) {
		EnemyParticleProviders.EnemyParticle p = EnemyParticleProviders.spawn(type, pos, velocity);

		if (p != null) {
			p.tint(tint).size(size).life(life);
		}
	}

	/** The unit vector from a body toward the given hand's side (body yaw). */
	private static Vec3 side(LivingEntity entity, boolean mainHand) {
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		boolean rightHand = (entity.getMainArm() == HumanoidArm.RIGHT) == mainHand;
		return rightHand ? right : right.scale(-1);
	}

	private static Vec3 forward(LivingEntity entity) {
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	/** Where the off hand is while it is raised (the flare) or drawn back (the grenade). */
	private static Vec3 raisedOffHand(LivingEntity entity, boolean drawnBack) {
		Vec3 eye = entity.getEyePosition();
		Vec3 side = side(entity, false).scale(0.38);
		return drawnBack ? eye.add(side).add(forward(entity).scale(-0.25)).add(0, 0.45, 0) : eye.add(side).add(0, 0.72, 0);
	}

	// ---------------------------------------------------------------- trooper_charge (tell)

	/** The charge tell starts: the army's whine follows the trooper; energy gathers at the muzzle. */
	private static void onCharge(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (entity == null) {
			return;
		}

		boolean republic = payload.variant() == BlasterBolt.Style.REPUBLIC.ordinal();
		stopSound(client, CHARGE_SOUNDS.remove(entity.getId()));
		CHARGE_SOUNDS.put(entity.getId(), playOn(client, level, entity,
				republic ? ModSounds.TROOPER_REPUBLIC_CHARGE : ModSounds.TROOPER_IMPERIAL_CHARGE, 1f, pitch(level.getRandom(), 0.04f)));

		Vec3 muzzle = payload.pos();

		if (!Vfx.inRange(muzzle)) {
			return;
		}

		// Anticipation: a few motes drawn in to the muzzle from around it.
		RandomSource random = level.getRandom();
		int tint = styleTint(payload.variant());

		for (int i = 0, n = Vfx.tellCount(3); i < n; i++) {
			Vec3 from = jitter(random, muzzle, 0.35);
			particle(EnemyParticles.MUZZLE_GLOW, from, muzzle.subtract(from).scale(0.18), tint, 0.08f, 6);
		}
	}

	private static void tickCharge(Minecraft client, ClientLevel level, TrooperEntity trooper) {
		if (!CHARGE_SOUNDS.containsKey(trooper.getId())) {
			// Joined mid-tell (or the event was missed): start the whine from state.
			CHARGE_SOUNDS.put(trooper.getId(), playOn(client, level, trooper,
					trooper.isRepublic() ? ModSounds.TROOPER_REPUBLIC_CHARGE : ModSounds.TROOPER_IMPERIAL_CHARGE, 1f, 1f));
		}

		Vec3 muzzle = trooper.muzzlePosition();

		if (!Vfx.inRange(muzzle)) {
			return;
		}

		RandomSource random = level.getRandom();
		int tint = styleTint(trooper.boltStyle().ordinal());
		int count = trooper.actionTicks() > 8 ? Vfx.tellCount(2) : Vfx.tellCount(1);

		for (int i = 0; i < count; i++) {
			particle(EnemyParticles.MUZZLE_GLOW, jitter(random, muzzle, 0.06), new Vec3(jitter(random, 0.01), 0.004, jitter(random, 0.01)), tint, 0.1f, 6);
		}
	}

	// ---------------------------------------------------------------- trooper_vent

	/** A Republic rifle vents: a hiss, then a jet of steam from the barrel that thins out over the vent. */
	private static void onVent(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 muzzle = payload.pos();
		RandomSource random = level.getRandom();

		if (entity != null) {
			stopSound(client, VENT_SOUNDS.remove(entity.getId()));
			VENT_SOUNDS.put(entity.getId(), playOn(client, level, entity, ModSounds.TROOPER_VENT, 0.9f, pitch(random, 0.06f)));
		} else {
			playAt(level, muzzle, ModSounds.TROOPER_VENT, 0.9f, pitch(random, 0.06f));
		}

		if (!Vfx.inRange(muzzle)) {
			return;
		}

		// Activation: a sideways jet from the heat sink, out of both sides of the barrel.
		Vec3 out = entity instanceof LivingEntity living ? side(living, true) : new Vec3(1, 0, 0);

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 dir = (i % 2 == 0 ? out : out.scale(-1)).scale(0.12 + random.nextDouble() * 0.08).add(0, 0.03 + random.nextDouble() * 0.04, 0);
			particle(EnemyParticles.VENT_STEAM, jitter(random, muzzle, 0.05), dir, 0xEEF2F6, 0.07f, 14 + random.nextInt(6));
		}
	}

	private static void tickVent(ClientLevel level, TrooperEntity trooper) {
		// Aftermath: a thinning plume while the rifle cools.
		if (ticks % 3 != 0) {
			return;
		}

		Vec3 muzzle = trooper.muzzlePosition();

		if (!Vfx.inRange(muzzle)) {
			return;
		}

		RandomSource random = level.getRandom();
		float left = 1f - Mth.clamp(trooper.actionTicks() / 40f, 0f, 1f);

		if (random.nextFloat() < 0.35f + 0.65f * left) {
			particle(EnemyParticles.VENT_STEAM, jitter(random, muzzle, 0.04), new Vec3(jitter(random, 0.01), 0.04, jitter(random, 0.01)), 0xE8ECF0, 0.06f, 16);
		}
	}

	// ---------------------------------------------------------------- kolto_heal

	/** A medic's channel starts on a patient: a soft hiss of kolto and a mist that wraps the patient. */
	private static void onKoltoHeal(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		playAt(level, payload.pos(), ModSounds.TROOPER_KOLTO, 0.9f, pitch(random, 0.05f));

		if (entity != null) {
			HEALS.put(entity.getId(), new Timed(ticks, Math.max(1, (int) payload.intensity())));
		}

		if (!Vfx.inRange(payload.pos())) {
			return;
		}

		for (int i = 0, n = Vfx.count(5); i < n; i++) {
			particle(EnemyParticles.KOLTO_MIST, jitter(random, payload.pos(), 0.3), new Vec3(jitter(random, 0.02), 0.01, jitter(random, 0.02)), KOLTO, 0.1f, 20);
		}
	}

	private static void tickHeals(ClientLevel level) {
		Iterator<Map.Entry<Integer, Timed>> it = HEALS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Timed> entry = it.next();
			Entity patient = level.getEntity(entry.getKey());
			Timed heal = entry.getValue();
			int t = ticks - heal.start();
			TrooperEntity medic = patient == null ? null : healingMedic(level, patient);

			if (patient == null || !patient.isAlive() || t > heal.length() + 2 || t > 3 && medic == null) {
				if (patient != null && t >= heal.length() - 2) {
					healed(level, patient);
				}

				it.remove();
				continue;
			}

			Vec3 chest = patient.position().add(0, patient.getBbHeight() * 0.6, 0);

			if (!Vfx.inRange(chest)) {
				continue;
			}

			RandomSource random = level.getRandom();
			// Mist circles the patient's chest...
			double angle = t * 0.55;
			Vec3 orbit = new Vec3(Math.cos(angle) * 0.45, Math.sin(t * 0.3) * 0.25, Math.sin(angle) * 0.45);
			particle(EnemyParticles.KOLTO_MIST, chest.add(orbit), new Vec3(0, 0.01, 0), KOLTO, 0.08f, 16);

			// ...fed by a thin stream from the medic's hand.
			if (medic != null && t % 2 == 0) {
				Vec3 hand = medic.getEyePosition().add(forward(medic).scale(0.5)).add(0, -0.55, 0);
				Vec3 toward = chest.subtract(hand);
				particle(EnemyParticles.KOLTO_MIST, jitter(random, hand, 0.04), toward.scale(0.09), KOLTO, 0.05f, 11);
			}
		}
	}

	/** The medic channelling on {@code patient}: a trooper within 4 blocks in the HEALING action. */
	private static @Nullable TrooperEntity healingMedic(ClientLevel level, Entity patient) {
		for (TrooperEntity trooper : level.getEntitiesOfClass(TrooperEntity.class, patient.getBoundingBox().inflate(4.0),
				t -> t.action() == TrooperAction.HEALING)) {
			return trooper;
		}

		return null;
	}

	/** The channel completed: the mist lifts off the patient with a few bright motes. */
	private static void healed(ClientLevel level, Entity patient) {
		Vec3 chest = patient.position().add(0, patient.getBbHeight() * 0.6, 0);

		if (!Vfx.inRange(chest)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			particle(EnemyParticles.KOLTO_MIST, jitter(random, chest, 0.35), new Vec3(jitter(random, 0.01), 0.05 + random.nextDouble() * 0.03, jitter(random, 0.01)),
					KOLTO, 0.07f, 18);
		}
	}

	// ---------------------------------------------------------------- trooper_tend

	/** Tending a downed trooper: bandage and kolto every 20 ticks; it stands with a scuff of dust. */
	private static void onTend(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		Vec3 pos = payload.pos();

		if (payload.variant() == TrooperEffects.TEND_STANDS) {
			playAt(level, pos, ModSounds.TROOPER_STAND, 1f, pitch(random, 0.05f));

			if (!Vfx.inRange(pos)) {
				return;
			}

			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				Vec3 at = pos.add(Math.cos(angle) * (0.2 + random.nextDouble() * 0.4), 0.05 + random.nextDouble() * 0.25, Math.sin(angle) * (0.2 + random.nextDouble() * 0.4));
				particle(EnemyParticles.GRENADE_SMOKE, at, new Vec3(Math.cos(angle) * 0.02, 0.01, Math.sin(angle) * 0.02), 0x8A7E6E, 0.12f, 24);
			}

			for (int i = 0, n = Vfx.count(3); i < n; i++) {
				particle(EnemyParticles.KOLTO_MIST, jitter(random, pos.add(0, 1.0, 0), 0.3), new Vec3(0, 0.05, 0), KOLTO, 0.07f, 16);
			}

			return;
		}

		int key = entity != null ? entity.getId() : payload.entityId();
		Integer last = TEND_SOUNDS.get(key);

		if (last == null || ticks - last >= 10) {
			TEND_SOUNDS.put(key, ticks);
			playAt(level, pos, ModSounds.TROOPER_TEND, 0.8f, pitch(random, 0.08f));
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		Vec3 chest = entity != null ? entity.position().add(0, 0.3, 0) : pos.add(0, 0.3, 0);

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			particle(EnemyParticles.KOLTO_MIST, jitter(random, chest, 0.35), new Vec3(jitter(random, 0.01), 0.02, jitter(random, 0.01)), KOLTO, 0.07f, 16);
		}
	}

	// ---------------------------------------------------------------- trooper_rally

	/** A Republic sergeant's Rally: a call, gold motes rising round the raised fist, and the squad lit when it lands. */
	private static void onRally(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		playAt(level, payload.pos(), ModSounds.TROOPER_RALLY, 1f, pitch(random, 0.03f));

		if (entity != null) {
			RALLIES.put(entity.getId(), new Timed(ticks, Math.max(1, (int) payload.intensity())));
		}

		// Activation: the sergeant plants his feet, a scuff of dust off the boots.
		Vec3 feet = entity != null ? entity.position() : payload.pos();

		if (!Vfx.inRange(feet)) {
			return;
		}

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			Vec3 at = feet.add(Math.cos(angle) * 0.3, 0.05 + random.nextDouble() * 0.15, Math.sin(angle) * 0.3);
			particle(EnemyParticles.GRENADE_SMOKE, at, new Vec3(Math.cos(angle) * 0.03, 0.01, Math.sin(angle) * 0.03), 0x8A7E6E, 0.09f, 18);
		}
	}

	private static void tickRally(ClientLevel level, TrooperEntity sergeant) {
		if (ticks % 2 != 0) {
			return;
		}

		Vec3 fist = raisedOffHand(sergeant, false);

		if (!Vfx.inRange(fist)) {
			return;
		}

		RandomSource random = level.getRandom();
		EnemyParticleProviders.EnemyParticle p = EnemyParticleProviders.spawn(EnemyParticles.FLARE_EMBER, jitter(random, fist, 0.2),
				new Vec3(jitter(random, 0.01), 0.04, jitter(random, 0.01)));

		if (p != null) {
			p.tint(RALLY_GOLD).size(0.04f).life(14).gravity(-0.02f);
		}
	}

	/** Rally tells that ended: a completed one lights each nearby Republic trooper with rising gold motes. */
	private static void tickRallyEnds(ClientLevel level) {
		Iterator<Map.Entry<Integer, Timed>> it = RALLIES.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Timed> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());
			Timed rally = entry.getValue();
			boolean telling = entity instanceof TrooperEntity trooper && trooper.isAlive() && trooper.action() == TrooperAction.RALLY_TELL;

			if (telling && ticks - rally.start() <= rally.length() + 10) {
				continue;
			}

			it.remove();

			if (entity instanceof TrooperEntity sergeant && ticks - rally.start() >= rally.length() - 2) {
				RandomSource random = level.getRandom();

				for (TrooperEntity ally : level.getEntitiesOfClass(TrooperEntity.class, sergeant.getBoundingBox().inflate(RALLY_RADIUS),
						t -> t.isAlive() && t.isRepublic() && !t.isDowned() && t.distanceToSqr(sergeant) <= RALLY_RADIUS * RALLY_RADIUS)) {
					Vec3 at = ally.position().add(0, 0.2, 0);

					if (!Vfx.inRange(at)) {
						continue;
					}

					for (int i = 0, n = Vfx.count(3); i < n; i++) {
						EnemyParticleProviders.EnemyParticle p = EnemyParticleProviders.spawn(EnemyParticles.FLARE_EMBER,
								at.add(jitter(random, 0.35), random.nextDouble() * 1.6, jitter(random, 0.35)), new Vec3(0, 0.05, 0));

						if (p != null) {
							p.tint(RALLY_GOLD).size(0.04f).life(18).gravity(-0.03f);
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- flare_call

	/** The Imperial flare fires: a pop, then a red flare climbs, hangs over the fight and burns out. */
	private static void onFlare(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		playAt(level, payload.pos(), ModSounds.TROOPER_FLARE, 1.2f, pitch(random, 0.04f));
		FLARES.add(new Flare(payload.pos(), Math.max(4, payload.intensity()), ticks));

		if (!Vfx.inRange(payload.pos())) {
			return;
		}

		// Activation: a muzzle puff at the hand.
		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			particle(EnemyParticles.GRENADE_SMOKE, jitter(random, payload.pos(), 0.1), new Vec3(jitter(random, 0.02), 0.06, jitter(random, 0.02)), 0x9A948C, 0.1f, 20);
		}
	}

	private static void tickFlares(ClientLevel level) {
		Iterator<Flare> it = FLARES.iterator();
		RandomSource random = level.getRandom();

		while (it.hasNext()) {
			Flare flare = it.next();
			int t = ticks - flare.start();

			if (t > FLARE_CLIMB + FLARE_HANG + FLARE_FADE) {
				it.remove();
				continue;
			}

			Vec3 pos;
			float brightness;

			if (t <= FLARE_CLIMB) {
				// Travel: an eased climb trailing smoke.
				float k = UiEase.outQuad(t / (float) FLARE_CLIMB);
				pos = flare.from().add(0, flare.climb() * k, 0);
				brightness = 1f;

				if (Vfx.inRange(pos) && t % 2 == 0) {
					particle(EnemyParticles.GRENADE_SMOKE, jitter(random, pos.add(0, -0.3, 0), 0.05), new Vec3(0, -0.01, 0), 0x8C8680, 0.09f, 26);
				}
			} else {
				// Hang: drifting down slowly, flickering, shedding embers; then it fades.
				int hang = t - FLARE_CLIMB;
				pos = flare.from().add(0, flare.climb() - hang * 0.03, 0);
				float fade = hang > FLARE_HANG ? 1f - (hang - FLARE_HANG) / (float) FLARE_FADE : 1f;
				float flicker = Vfx.flashing() ? 0.8f + 0.2f * Mth.sin(t * 1.9f) * Mth.sin(t * 0.7f + 1f) : 1f;
				brightness = fade * flicker;

				if (Vfx.inRange(pos) && hang % 3 == 0 && hang <= FLARE_HANG) {
					particle(EnemyParticles.FLARE_EMBER, jitter(random, pos, 0.1), new Vec3(jitter(random, 0.02), -0.02, jitter(random, 0.02)), FLARE_RED, 0.05f, 20);
				}
			}

			if (!Vfx.inRange(pos) || brightness <= 0.02f) {
				continue;
			}

			// The burning head: a bright core in a red halo, refreshed every tick.
			EnemyParticleProviders.EnemyParticle halo = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, pos, Vec3.ZERO);

			if (halo != null) {
				halo.tint(FLARE_RED).size(0.55f * brightness).life(2).alpha(0.55f * brightness);
			}

			EnemyParticleProviders.EnemyParticle core = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, pos, Vec3.ZERO);

			if (core != null) {
				core.tint(0xFFE0C8).size(0.18f * brightness).life(2).alpha(brightness);
			}
		}
	}

	// ---------------------------------------------------------------- grenades

	/** The grenadier arms a grenade: a click and whine, and a spark at the hand (the light blinks on the model). */
	private static void onGrenadeTell(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		RandomSource random = level.getRandom();

		if (entity != null) {
			playOn(client, level, entity, ModSounds.GRENADE_ARM, 1f, pitch(random, 0.04f));
		} else {
			playAt(level, payload.pos(), ModSounds.GRENADE_ARM, 1f, pitch(random, 0.04f));
		}

		if (!Vfx.inRange(payload.pos())) {
			return;
		}

		for (int i = 0, n = Vfx.tellCount(2); i < n; i++) {
			particle(EnemyParticles.MUZZLE_GLOW, jitter(random, payload.pos(), 0.05), Vec3.ZERO, 0xFF3020, 0.07f, 5);
		}
	}

	/** A blinking spark at the arming hand; with flashing off the glow layer's steady light is the whole tell. */
	private static void tickGrenadeTell(ClientLevel level, TrooperEntity grenadier) {
		if (!Vfx.flashing() || ticks % 4 != 0) {
			return;
		}

		Vec3 hand = raisedOffHand(grenadier, true);

		if (Vfx.inRange(hand)) {
			particle(EnemyParticles.MUZZLE_GLOW, hand, Vec3.ZERO, 0xFF2A18, 0.06f, 3);
		}
	}

	/** A grenade lands and its fuse burns: a rising tick that follows it (2 at most at once). */
	private static void onGrenadeFuse(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (entity == null || FUSE_SOUNDS.containsKey(entity.getId())) {
			return;
		}

		FUSE_SOUNDS.values().removeIf(sound -> !client.getSoundManager().isActive(sound));

		if (FUSE_SOUNDS.size() < MAX_FUSE_SOUNDS) {
			FUSE_SOUNDS.put(entity.getId(), playOn(client, level, entity, ModSounds.GRENADE_FUSE, 1f, 1f));
		}

		Vec3 pos = payload.pos();

		if (Vfx.inRange(pos)) {
			RandomSource random = level.getRandom();

			for (int i = 0, n = Vfx.tellCount(2); i < n; i++) {
				particle(EnemyParticles.GRENADE_SMOKE, jitter(random, pos.add(0, 0.15, 0), 0.05), new Vec3(0, 0.015, 0), 0x8A847C, 0.05f, 18);
			}
		}
	}

	private static void tickFuse(ClientLevel level, FragGrenade grenade) {
		// Thin smoke curling off the fuse, quicker as it burns down (the blink is on the model).
		float progress = Mth.clamp(grenade.fuseTicks() / (float) Math.max(1, grenade.fuseLength()), 0f, 1f);
		int every = progress > 0.75f ? 1 : progress > 0.4f ? 2 : 4;

		if (ticks % every != 0) {
			return;
		}

		Vec3 top = grenade.position().add(0, 0.2, 0);

		if (!Vfx.inRange(top)) {
			return;
		}

		RandomSource random = level.getRandom();
		double curl = ticks * 0.4;
		particle(EnemyParticles.GRENADE_SMOKE, top, new Vec3(Math.cos(curl) * 0.008, 0.018, Math.sin(curl) * 0.008), 0x8A847C, 0.035f + 0.02f * random.nextFloat(), 16);
	}

	/**
	 * The burst: a white-orange flash, sparks and earth thrown up from the ground block, irregular
	 * smoke puffs at varied heights with gaps (never a ring), and a brief pressure shimmer. 40 particles
	 * or fewer; a shake for the local player when close.
	 */
	private static void onGrenadeBurst(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();
		RandomSource random = level.getRandom();
		float radius = Math.max(1f, payload.intensity());

		while (!BURST_SOUNDS.isEmpty() && ticks - BURST_SOUNDS.peekFirst() >= BURST_SOUND_WINDOW) {
			BURST_SOUNDS.removeFirst();
		}

		if (BURST_SOUNDS.size() < BURST_SOUNDS_PER_WINDOW) {
			BURST_SOUNDS.addLast(ticks);
			playAt(level, pos, ModSounds.GRENADE_BURST, 1.6f, pitch(random, 0.06f));
		}

		Player player = client.player;

		if (player != null) {
			double distance = player.position().distanceTo(pos);
			double reach = radius * 3.0;

			if (distance < reach) {
				CameraShake.add((float) (0.45 * (1.0 - distance / reach)));
			}
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		int budget = BURST_BUDGET;

		// Flash: a hot core in an orange bloom; with flashing off a dimmer glow that fades slowly instead.
		boolean flash = Vfx.flashing();

		for (int i = 0, n = Math.min(3, Vfx.count(3)); i < n && budget > 0; i++, budget--) {
			EnemyParticleProviders.EnemyParticle glow = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, jitter(random, pos, 0.15), Vec3.ZERO);

			if (glow != null) {
				glow.tint(i == 0 ? 0xFFF2DC : 0xFF8A3A).size(i == 0 ? 0.45f : 0.7f).life(flash ? 4 + i : 12 + i);

				if (!flash) {
					glow.alpha(0.5f);
				}
			}
		}

		// Sparks: thrown outward and up, cooling as they fall.
		for (int i = 0, n = Math.min(12, Vfx.count(12)); i < n && budget > 0; i++, budget--) {
			double angle = random.nextDouble() * Math.PI * 2;
			double speed = 0.2 + random.nextDouble() * 0.25;
			Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.15 + random.nextDouble() * 0.3, Math.sin(angle) * speed);
			particle(EnemyParticles.FLARE_EMBER, pos.add(0, 0.2, 0), velocity, 0xFFB060, 0.04f, 10 + random.nextInt(8));
		}

		// Debris of the ground block.
		BlockPos below = BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
		BlockState ground = level.getBlockState(below);

		if (!ground.isAir()) {
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);

			for (int i = 0, n = Math.min(10, Vfx.count(10)); i < n && budget > 0; i++, budget--) {
				double angle = random.nextDouble() * Math.PI * 2;
				double speed = 0.08 + random.nextDouble() * 0.18;
				level.addParticle(debris, pos.x + jitter(random, 0.3), pos.y + 0.1, pos.z + jitter(random, 0.3),
						Math.cos(angle) * speed, 0.25 + random.nextDouble() * 0.3, Math.sin(angle) * speed);
			}
		}

		// Smoke: irregular puffs at varied distances and heights, with gaps; never a ring.
		for (int i = 0, n = Math.min(10, Vfx.count(10)); i < n && budget > 0; i++, budget--) {
			double angle = (i + random.nextDouble() * 0.7) * (Math.PI * 2 / n) + random.nextDouble();
			double distance = radius * (0.1 + random.nextDouble() * 0.6);
			Vec3 at = pos.add(Math.cos(angle) * distance, 0.1 + random.nextDouble() * 1.4, Math.sin(angle) * distance);
			Vec3 drift = new Vec3(Math.cos(angle) * 0.03, 0.01 + random.nextDouble() * 0.02, Math.sin(angle) * 0.03);
			particle(EnemyParticles.GRENADE_SMOKE, at, drift, 0x5E5850, 0.25f + random.nextFloat() * 0.2f, 30 + random.nextInt(20));
		}

		// Pressure shimmer: two faint, wide, short-lived glows.
		for (int i = 0, n = Math.min(2, Vfx.count(2)); i < n && budget > 0; i++, budget--) {
			EnemyParticleProviders.EnemyParticle p = EnemyParticleProviders.spawn(EnemyParticles.VENT_STEAM, pos.add(0, 0.6 + i * 0.4, 0), Vec3.ZERO);

			if (p != null) {
				p.tint(0xFFFFFF).size(radius * 0.45f).life(4).alpha(0.18f);
			}
		}
	}

	/** Push or Pull redirects a grenade: a bent-air streak along its new path (the power plays its own sound). */
	private static void onGrenadeReturn(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();

		if (!Vfx.inRange(pos)) {
			return;
		}

		Vec3 velocity = payload.dir();
		Vec3 dir = velocity.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : velocity.normalize();
		RandomSource random = level.getRandom();
		int tint = payload.variant() == TrooperEffects.RETURN_PULL ? 0xD8E6FF : 0xEAF2FF;

		for (int i = 0, n = Math.min(6, Vfx.count(6)); i < n; i++) {
			Vec3 at = pos.add(dir.scale(0.3 * i)).add(jitter(random, 0.05), jitter(random, 0.05), jitter(random, 0.05));
			EnemyParticleProviders.EnemyParticle p = EnemyParticleProviders.spawn(EnemyParticles.VENT_STEAM, at, dir.scale(0.05));

			if (p != null) {
				p.tint(tint).size(0.12f - i * 0.012f).life(7 + i).alpha(0.3f);
			}
		}
	}

	// ---------------------------------------------------------------- barks and salutes

	/** A radio bark: a squelch at the speaker, a tiny comm light, and the line in the bark HUD when near enough. */
	private static void onBark(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof TrooperEntity speaker)) {
			return;
		}

		Player player = client.player;
		double range = Math.max(1f, payload.intensity());

		if (player == null || player.position().distanceToSqr(payload.pos()) > range * range) {
			return;
		}

		RandomSource random = level.getRandom();
		playAt(level, payload.pos(), ModSounds.TROOPER_RADIO, 0.7f, pitch(random, 0.05f));
		BarkCategory category = BarkCategory.byVariant(payload.variant());

		if (category != null) {
			BarkHud.show(speaker, category, payload.variant() % 4);
		}

		Vec3 comm = speaker.getEyePosition().add(side(speaker, false).scale(0.28));

		if (Vfx.inRange(comm)) {
			particle(EnemyParticles.MUZZLE_GLOW, comm, Vec3.ZERO, speaker.isRepublic() ? 0x8CC4FF : 0xFF3A4A, 0.04f, 4);
		}
	}

	/** A salute to an Allied player: a glint at the brow as the hand comes up (the pose is on the model). */
	private static void onSalute(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof TrooperEntity trooper)) {
			return;
		}

		Vec3 brow = trooper.getEyePosition().add(forward(trooper).scale(0.3)).add(side(trooper, true).scale(0.12)).add(0, 0.12, 0);

		if (Vfx.inRange(brow)) {
			particle(EnemyParticles.MUZZLE_GLOW, brow, new Vec3(0, 0.005, 0), 0xFFF0C8, 0.05f, 6);
		}
	}

	// ---------------------------------------------------------------- tick

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null || client.player == null) {
			clear(client);
			trackedLevel = null;
			return;
		}

		if (level != trackedLevel) {
			clear(client);
			trackedLevel = level;
		}

		ticks++;
		Set<Integer> charging = new HashSet<>();
		Set<Integer> venting = new HashSet<>();
		Set<Integer> fusing = new HashSet<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof TrooperEntity trooper && trooper.isAlive() && !trooper.isDowned()) {
				switch (trooper.action()) {
					case CHARGING -> {
						charging.add(trooper.getId());
						tickCharge(client, level, trooper);
					}
					case VENTING -> {
						venting.add(trooper.getId());
						tickVent(level, trooper);
					}
					case RALLY_TELL -> tickRally(level, trooper);
					case GRENADE_TELL -> tickGrenadeTell(level, trooper);
					case FLARE_TELL -> {
						if (ticks % 3 == 0) {
							Vec3 hand = raisedOffHand(trooper, false);

							if (Vfx.inRange(hand)) {
								particle(EnemyParticles.FLARE_EMBER, hand, new Vec3(0, -0.01, 0), FLARE_RED, 0.03f, 6);
							}
						}
					}
					default -> {
					}
				}
			} else if (entity instanceof FragGrenade grenade && grenade.isAlive() && grenade.fuseStarted()) {
				fusing.add(grenade.getId());
				tickFuse(level, grenade);
			}
		}

		stopMissing(client, CHARGE_SOUNDS, charging);
		stopMissing(client, VENT_SOUNDS, venting);
		stopMissing(client, FUSE_SOUNDS, fusing);
		tickHeals(level);
		tickRallyEnds(level);
		tickFlares(level);
		TEND_SOUNDS.values().removeIf(last -> ticks - last > 200);
	}

	/** Stops and forgets each sound whose entity no longer shows its tell (it fired, stopped, died or unloaded). */
	private static void stopMissing(Minecraft client, Map<Integer, EntityBoundSoundInstance> sounds, Set<Integer> live) {
		Iterator<Map.Entry<Integer, EntityBoundSoundInstance>> it = sounds.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, EntityBoundSoundInstance> entry = it.next();

			if (!live.contains(entry.getKey())) {
				client.getSoundManager().stop(entry.getValue());
				it.remove();
			}
		}
	}

	private static void stopSound(Minecraft client, @Nullable EntityBoundSoundInstance sound) {
		if (sound != null) {
			client.getSoundManager().stop(sound);
		}
	}

	private static void clear(Minecraft client) {
		for (Map<Integer, EntityBoundSoundInstance> sounds : List.of(CHARGE_SOUNDS, VENT_SOUNDS, FUSE_SOUNDS)) {
			sounds.values().forEach(client.getSoundManager()::stop);
			sounds.clear();
		}

		HEALS.clear();
		RALLIES.clear();
		TEND_SOUNDS.clear();
		BURST_SOUNDS.clear();
		FLARES.clear();
	}

	/** Easing used by the flare's climb. */
	private static final class UiEase {
		static float outQuad(float t) {
			float c = Mth.clamp(t, 0f, 1f);
			return 1f - (1f - c) * (1f - c);
		}
	}

	private TrooperEffectsClient() {
	}
}

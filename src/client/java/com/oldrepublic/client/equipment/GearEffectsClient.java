package com.oldrepublic.client.equipment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.saber.SaberSparks;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.equipment.EquipmentParticles;
import com.oldrepublic.equipment.GearEffects;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.ElectrostaffItem;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.mixin.equipment.LivingEntityAttackStrengthAccessor;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The client side of the gear's effect events (spec MILESTONE_4.md 13.3), phase by phase:
 * <ul>
 * <li>{@code player_blaster_fire}: the shot's crack ({@code blaster.pistol_fire}, {@code blaster.fire},
 * {@code blaster.repeater.fire}, {@code blaster.rifle.charged}) and a green-white muzzle flash with
 * a few sparks (4 per shot at most; the bolt itself is the M1 renderer); the shooter's recoil kick.
 * The rifle's charge: a rising whine for everyone near ({@code blaster.charge}) that stops when the
 * charge is dropped, swapped away or fired, and a latch click with a steady glow at the muzzle for
 * the shooter when it is loaded. Hit and deflect markers for the reticle;</li>
 * <li>{@code blaster_heat}: overheat, a scream of steam from the vents (12 puffs over 20 ticks that
 * follow the gun); vent, a short hiss and a burst of steam; perfect vent, one bright clean puff;</li>
 * <li>{@code repeater_spin}: the spin-up and wind-down sounds, and while spinning a faint blur of
 * steel motes circling the muzzle;</li>
 * <li>{@code vibro_hit}: the flurry's whine and a grit flick, the cleave's arc of sparks, a guard
 * block's clang, a notch's orange sparks, a guard break;</li>
 * <li>{@code electrostaff}: the rising crackle of the charge, then arcs crawling at both ends while
 * it is loaded; the shock's snap; the clash sparks of a saber held off; the sweep's dust;</li>
 * <li>{@code jet_pack}: a gout of flame and smoke from the pack (14 over 10 ticks, following the
 * wearer) and the roar; the empty click with a wisp of smoke.</li>
 * </ul>
 * Every loop and follower stops when its entity dies or is removed, the level changes, the item
 * leaves the hand, or its own end event arrives, with a hard cap as a watchdog. Particle counts go
 * through {@link Vfx}.
 */
public final class GearEffectsClient {
	private static final RandomSource RANDOM = RandomSource.create();
	/** A Storm Cell arc's longest drawn span and most sparks (MILESTONE_5.md 11.2). */
	private static final double STORM_ARC_MAX = 8.0;
	private static final int STORM_ARC_STEPS = 14;
	/** Player bolt colours (the renderer's PLAYER palette): glow and core. */
	private static final int BOLT_GLOW = 0x5CF07A;
	private static final int BOLT_CORE = 0xEAFFEA;
	private static final int STEEL = 0xDCE4EE;
	private static final int HOT_ORANGE = 0xFFA040;
	/** An electrostaff blocking a saber: sparks of pale electric light. */
	private static final int STAFF_CLASH = 0xC8D8FF;
	/** Watchdogs: the longest a whine, a staff charge or a spin may run without its end event. */
	private static final int WHINE_MAX = 60;
	private static final int STAFF_CHARGE_MAX = 60;
	private static final int STAFF_LOADED_MAX = 140;
	private static final int SPIN_IDLE_MAX = 60;

	/** A short-lived particle source that follows an entity (overheat steam, jet exhaust). */
	private record Emitter(int entityId, int kind, int every, int[] left) {
	}

	private static final int EMIT_STEAM = 0;
	private static final int EMIT_FLAME = 1;

	private static final Map<Integer, Integer> LAST_FIRE = new HashMap<>();
	private static final Map<Integer, HeldSound> WHINES = new HashMap<>();
	private static final Map<Integer, HeldSound> STAFF_SOUNDS = new HashMap<>();
	/** Entity id to the tick its staff charge loaded. */
	private static final Map<Integer, Integer> STAFF_LOADED = new HashMap<>();
	/** Entity id to the tick its repeater was last seen spinning. */
	private static final Map<Integer, Integer> SPINNING = new HashMap<>();
	private static final List<Emitter> EMITTERS = new ArrayList<>();

	private static int ticks;
	private static @Nullable ClientLevel seenLevel;
	private static float recoil;
	private static float recoilO;
	private static int hitTick = Integer.MIN_VALUE / 2;
	private static boolean hitKill;
	private static int deflectTick = Integer.MIN_VALUE / 2;
	private static int overheatTick = Integer.MIN_VALUE / 2;
	private static int perfectTick = Integer.MIN_VALUE / 2;
	private static boolean loaded;
	/** When the local player's rifle charge began (client ticks), or -1. */
	private static int chargeStart = -1;

	static void init() {
		ClientEffects.register(GearEffects.PLAYER_BLASTER_FIRE, GearEffectsClient::onFire);
		ClientEffects.register(GearEffects.BLASTER_HEAT, GearEffectsClient::onHeat);
		ClientEffects.register(GearEffects.REPEATER_SPIN, GearEffectsClient::onSpin);
		ClientEffects.register(GearEffects.VIBRO_HIT, GearEffectsClient::onVibro);
		ClientEffects.register(GearEffects.ELECTROSTAFF, GearEffectsClient::onStaff);
		ClientEffects.register(GearEffects.JET_PACK, GearEffectsClient::onJet);
		ClientTickEvents.END_CLIENT_TICK.register(GearEffectsClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	// ---------------------------------------------------------------- what the HUD and poses read

	/** Client ticks (unpaused). */
	static int ticks() {
		return ticks;
	}

	/** Ticks since {@code entityId} last fired a blaster, or a large number. */
	static int sinceFire(int entityId) {
		Integer at = LAST_FIRE.get(entityId);
		return at == null ? Integer.MAX_VALUE / 2 : ticks - at;
	}

	/** The local player's recoil kick, 0 to 1. */
	static float recoil(float partialTick) {
		return Mth.lerp(partialTick, recoilO, recoil);
	}

	/** Ticks since the last hit marker, and whether it was a kill. */
	static int sinceHit() {
		return ticks - hitTick;
	}

	static boolean hitWasKill() {
		return hitKill;
	}

	static int sinceDeflect() {
		return ticks - deflectTick;
	}

	static int sinceOverheat() {
		return ticks - overheatTick;
	}

	/** Ticks since the local player's last perfect vent. */
	static int sincePerfect() {
		return ticks - perfectTick;
	}

	/** Whether the local player's rifle has its charged shot loaded. */
	static boolean chargeLoaded() {
		return loaded;
	}

	/** Client ticks since the local player's rifle charge began, or -1 while not charging. */
	static int chargeTicks() {
		return chargeStart < 0 ? -1 : ticks - chargeStart;
	}

	// ---------------------------------------------------------------- player_blaster_fire

	private static void onFire(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int variant = payload.variant();
		boolean self = entity != null && entity == client.player;

		switch (variant) {
			case GearEffects.HIT -> {
				hitTick = ticks;
				hitKill = payload.intensity() >= 2f;
			}
			case GearEffects.DEFLECTED -> deflectTick = ticks;
			case GearEffects.CHARGE_READY -> {
				cutWhine(payload.entityId());

				if (self) {
					loaded = true;
					chargeStart = -1;
				}

				if (entity instanceof LivingEntity shooter) {
					QuestSounds.at(level, payload.pos(), ModSounds.WORKBENCH_SLOT, 0.5f, 1.5f);
					EnemyParticleProviders.EnemyParticle glow = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, muzzle(client, shooter, payload.pos()), Vec3.ZERO);

					if (glow != null) {
						glow.tint(BOLT_GLOW).size(0.1f).life(Vfx.flashing() ? 8 : 14).alpha(0.7f).bright();
					}
				}
			}
			case GearEffects.CHARGE_START -> {
				cutWhine(payload.entityId());

				if (payload.intensity() > 0f && entity instanceof LivingEntity shooter) {
					WHINES.put(payload.entityId(), HeldSound.play(ModSounds.BLASTER_CHARGE, 0.7f, 1.15f, shooter,
							e -> e.getMainHandItem().getItem() instanceof BlasterItem, WHINE_MAX));
				}

				if (self) {
					chargeStart = payload.intensity() > 0f ? ticks : -1;
					loaded = loaded && payload.intensity() > 0f;
				}
			}
			case GearEffects.STORM_ARC -> stormArc(level, payload);
			default -> shot(client, level, payload, entity, variant, self);
		}
	}

	/**
	 * A Storm Cell's arc (MILESTONE_5.md 11.2): a jagged line of lightning sparks from the struck
	 * target to the next one, a crackle where it lands, and a short snap.
	 */
	private static void stormArc(ClientLevel level, EffectEventPayload payload) {
		Vec3 from = payload.pos();
		double length = Math.min(STORM_ARC_MAX, Math.max(0.0, payload.intensity()));
		Vec3 to = from.add(payload.dir().scale(length));
		QuestSounds.at(level, to, ModSounds.ELECTROSTAFF_SHOCK, 0.6f, 1.25f + RANDOM.nextFloat() * 0.15f);

		if (!Vfx.inRange(from) && !Vfx.inRange(to)) {
			return;
		}

		int steps = Math.max(2, Math.min(STORM_ARC_STEPS, (int) Math.ceil(length / 0.3)));
		int shown = Vfx.count(steps);

		for (int i = 0; i <= shown; i++) {
			Vec3 at = from.lerp(to, shown == 0 ? 1.0 : i / (double) shown);
			// The middle of the arc wanders more than its ends.
			double wander = 0.16 * Math.sin(Math.PI * (shown == 0 ? 1.0 : i / (double) shown));
			level.addParticle(ForceParticles.LIGHTNING_SPARK, at.x + jitter(wander), at.y + jitter(wander), at.z + jitter(wander), 0, 0, 0);
		}

		arcs(level, to, Vfx.count(4), 0.25);
	}

	/** A shot: sound, flash and sparks at the muzzle, recoil for the shooter. */
	private static void shot(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity, int variant, boolean self) {
		boolean charged = variant == GearEffects.FIRE_CHARGED;
		SoundEvent sound = switch (variant) {
			case GearEffects.FIRE_PISTOL -> ModSounds.BLASTER_PISTOL_FIRE;
			case GearEffects.FIRE_REPEATER -> ModSounds.BLASTER_REPEATER_FIRE;
			case GearEffects.FIRE_CHARGED -> ModSounds.BLASTER_RIFLE_CHARGED;
			default -> ModSounds.BLASTER_FIRE;
		};
		float pitch = 0.95f + RANDOM.nextFloat() * 0.1f;
		QuestSounds.at(level, payload.pos(), sound, charged ? 1.2f : 0.9f, pitch);
		LAST_FIRE.put(payload.entityId(), ticks);

		if (charged) {
			cutWhine(payload.entityId());
		}

		if (self) {
			recoil = Math.min(1f, recoil + (charged ? 1f : variant == GearEffects.FIRE_REPEATER ? 0.35f : 0.7f));
			loaded = loaded && !charged;
			chargeStart = charged ? -1 : chargeStart;
		}

		Vec3 at = entity instanceof LivingEntity shooter ? muzzle(client, shooter, payload.pos()) : payload.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		// Activation: the flash at the barrel, then a few sparks thrown forward.
		EnemyParticleProviders.EnemyParticle flash = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, Vec3.ZERO);

		if (flash != null) {
			flash.tint(charged ? BOLT_CORE : BOLT_GLOW).size(charged ? 0.24f : 0.13f).life(3).bright();
		}

		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;

		for (int i = 0, n = Vfx.count(charged ? 3 : 2); i < n; i++) {
			Vec3 velocity = dir.scale(0.12 + RANDOM.nextDouble() * 0.1).add(jitter(0.03), jitter(0.03), jitter(0.03));
			EnemyParticleProviders.EnemyParticle spark = EnemyParticleProviders.spawn(EnemyParticles.BOLT_IMPACT, at, velocity);

			if (spark != null) {
				spark.tint(BOLT_CORE).size(0.025f).life(3 + RANDOM.nextInt(2)).gravity(0f);
			}
		}
	}

	private static void cutWhine(int entityId) {
		HeldSound whine = WHINES.remove(entityId);

		if (whine != null) {
			whine.cut();
		}
	}

	// ---------------------------------------------------------------- blaster_heat

	private static void onHeat(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = entity instanceof LivingEntity holder ? muzzle(client, holder, payload.pos()) : payload.pos();
		SPINNING.remove(payload.entityId());

		switch (payload.variant()) {
			case GearEffects.HEAT_OVERHEAT -> {
				QuestSounds.at(level, at, ModSounds.BLASTER_OVERHEAT, 1f, 1f);

				if (entity == client.player) {
					overheatTick = ticks;
					loaded = false;
					chargeStart = -1;
				}

				// Impact: four hard jets now, then the vents keep screaming for 20 ticks (12 in all).
				steam(at, Vfx.count(4), 0.12f, 1f);
				EMITTERS.add(new Emitter(payload.entityId(), EMIT_STEAM, 2, new int[] {Vfx.count(8), 0}));
			}
			case GearEffects.HEAT_PERFECT -> {
				QuestSounds.at(level, at, ModSounds.BLASTER_VENT_PERFECT, 1f, 1f);

				if (entity == client.player) {
					perfectTick = ticks;
				}

				// One bright clean puff: the tell that the timing was right.
				GearParticleProviders.GearParticle puff = GearParticleProviders.spawn(EquipmentParticles.STEAM_VENT, at, new Vec3(0, 0.05, 0));

				if (puff != null) {
					puff.size(0.22f).alpha(0.85f).growth(1.6f).life(16);
				}

				steam(at, Vfx.count(2), 0.06f, 0.6f);
			}
			default -> {
				QuestSounds.at(level, at, ModSounds.BLASTER_VENT, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
				steam(at, Vfx.count(5), 0.09f, 0.8f);
			}
		}
	}

	/** {@code n} steam puffs thrown out sideways from the vents, rising. */
	private static void steam(Vec3 at, int n, float speed, float size) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			Vec3 velocity = new Vec3(Math.cos(angle) * speed, 0.02 + RANDOM.nextDouble() * 0.04, Math.sin(angle) * speed);
			GearParticleProviders.GearParticle puff = GearParticleProviders.spawn(EquipmentParticles.STEAM_VENT, at, velocity);

			if (puff != null) {
				puff.size((0.08f + RANDOM.nextFloat() * 0.05f) * size);
			}
		}
	}

	// ---------------------------------------------------------------- repeater_spin

	private static void onSpin(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.variant() == GearEffects.SPIN_UP) {
			QuestSounds.at(level, payload.pos(), ModSounds.BLASTER_REPEATER_SPIN_UP, 0.9f, 1f);
			SPINNING.put(payload.entityId(), ticks);
		} else {
			QuestSounds.at(level, payload.pos(), ModSounds.BLASTER_REPEATER_SPIN_DOWN, 0.8f, 1f);
			SPINNING.remove(payload.entityId());
		}
	}

	// ---------------------------------------------------------------- vibro_hit

	private static void onVibro(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;

		switch (payload.variant()) {
			case GearEffects.VIBRO_FLURRY -> {
				QuestSounds.at(level, at, ModSounds.VIBRO_FLURRY, 1f, 1f);

				// The flurry's hit leaves the swing ready: the wielder's own client resets it too.
				if (entity == client.player && client.player != null) {
					((LivingEntityAttackStrengthAccessor) client.player).oldrepublic$setAttackStrengthTicker(100);
				}

				sparks(at, dir, Vfx.count(3), STEEL, 0.18f);
				grit(level, at, Vfx.count(2));
			}
			case GearEffects.VIBRO_CLEAVE -> {
				QuestSounds.at(level, at, ModSounds.VIBRO_CLEAVE, 1f, 1f);
				// An arc of sparks along the sweep in front of the wielder.
				Vec3 side = new Vec3(-dir.z, 0, dir.x);

				for (int i = 0, n = Vfx.count(5); i < n; i++) {
					double t = n == 1 ? 0 : (i / (double) (n - 1)) * 2 - 1;
					Vec3 point = at.add(side.scale(t * 1.1)).add(dir.scale(-Math.abs(t) * 0.4));
					sparks(point, dir.add(side.scale(t)), 1, STEEL, 0.12f);
				}
			}
			case GearEffects.VIBRO_GUARD -> {
				QuestSounds.at(level, at, ModSounds.VIBROBLADE_HIT, 0.8f, 0.7f + 0.2f * payload.intensity());
				sparks(at, dir.scale(-1), Vfx.count(3), STEEL, 0.14f);
			}
			default -> {
				if (payload.intensity() <= 0f) {
					// The guard broke.
					QuestSounds.at(level, at, ModSounds.SABER_GUARD_BREAK, 0.9f, 1.1f);
					sparks(at, dir, Vfx.count(4), HOT_ORANGE, 0.2f);
					grit(level, at, Vfx.count(3));
				} else {
					// A saber bit into the blade: hot metal flicks off.
					QuestSounds.at(level, at, ModSounds.SABER_CLASH, 0.8f, 0.75f);
					sparks(at, dir, Vfx.count(4), HOT_ORANGE, 0.16f);
				}
			}
		}
	}

	private static void sparks(Vec3 at, Vec3 dir, int n, int tint, float speed) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < n; i++) {
			Vec3 velocity = dir.scale(speed * (0.5 + RANDOM.nextDouble())).add(jitter(0.06), 0.04 + RANDOM.nextDouble() * 0.05, jitter(0.06));
			EnemyParticleProviders.EnemyParticle spark = EnemyParticleProviders.spawn(EnemyParticles.BOLT_IMPACT, at, velocity);

			if (spark != null) {
				spark.tint(tint);
			}
		}
	}

	private static void grit(ClientLevel level, Vec3 at, int n) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < n; i++) {
			level.addParticle(WorldParticles.VENT_GRIT, at.x, at.y, at.z, jitter(0.08), 0.06 + RANDOM.nextDouble() * 0.05, jitter(0.08));
		}
	}

	// ---------------------------------------------------------------- electrostaff

	private static void onStaff(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int id = payload.entityId();

		switch (payload.variant()) {
			case GearEffects.STAFF_CHARGE -> {
				cutStaff(id);

				if (entity instanceof LivingEntity wielder) {
					STAFF_SOUNDS.put(id, HeldSound.play(ModSounds.ELECTROSTAFF_CHARGE, 0.9f, 1f, wielder, GearEffectsClient::holdingStaff, STAFF_CHARGE_MAX));
				}
			}
			case GearEffects.STAFF_CHARGED -> STAFF_LOADED.put(id, ticks);
			case GearEffects.STAFF_SHOCK -> {
				cutStaff(id);
				STAFF_LOADED.remove(id);
				QuestSounds.at(level, payload.pos(), ModSounds.ELECTROSTAFF_SHOCK, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
				arcs(level, payload.pos(), Vfx.count(8), 0.35);
			}
			case GearEffects.STAFF_BLOCK_SABER -> {
				QuestSounds.at(level, payload.pos(), ModSounds.ELECTROSTAFF_CLASH, 1f, 1f);
				clash(payload.pos(), Vfx.count(6));
			}
			case GearEffects.STAFF_SWEEP -> {
				QuestSounds.at(level, payload.pos(), ModSounds.VIBROBLADE_SWING, 0.7f, 0.7f);
				grit(level, payload.pos().add(0, -0.6, 0), Vfx.count(3));
			}
			default -> {
				// Spent: the crackle dies with a weak fizz.
				boolean wasLive = STAFF_LOADED.remove(id) != null || STAFF_SOUNDS.containsKey(id);
				cutStaff(id);

				if (wasLive && entity instanceof LivingEntity wielder) {
					QuestSounds.at(level, staffEnd(client, wielder, true), ModSounds.ELECTROSTAFF_SHOCK, 0.25f, 0.55f);
				}
			}
		}
	}

	private static boolean holdingStaff(LivingEntity e) {
		return e.getMainHandItem().getItem() instanceof ElectrostaffItem;
	}

	private static void cutStaff(int entityId) {
		HeldSound sound = STAFF_SOUNDS.remove(entityId);

		if (sound != null) {
			sound.cut();
		}
	}

	private static void arcs(ClientLevel level, Vec3 at, int n, double spread) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < n; i++) {
			level.addParticle(ForceParticles.LIGHTNING_SPARK, at.x + jitter(spread), at.y + jitter(spread), at.z + jitter(spread),
					jitter(0.08), jitter(0.08), jitter(0.08));
		}
	}

	private static void clash(Vec3 at, int n) {
		if (!Vfx.inRange(at)) {
			return;
		}

		SaberSparks.flash(at, STAFF_CLASH);
		SaberSparks.sparks(at, new Vec3(0, 1, 0), 1.0, n, STAFF_CLASH, 0.15);
	}

	// ---------------------------------------------------------------- jet_pack

	private static void onJet(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.variant() == GearEffects.JET_EMPTY) {
			QuestSounds.at(level, payload.pos(), ModSounds.JETPACK_EMPTY, 0.8f, 1f);
			GearParticleProviders.GearParticle wisp = GearParticleProviders.spawn(EquipmentParticles.STEAM_VENT, payload.pos(), new Vec3(0, -0.02, 0));

			if (wisp != null) {
				wisp.tint(0x8A8A8A).size(0.08f).alpha(0.4f);
			}

			return;
		}

		QuestSounds.at(level, payload.pos(), ModSounds.JETPACK_BURST, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : new Vec3(0, -1, 0);
		flames(payload.pos(), dir, Vfx.count(6));
		// Travel: the rest of the 14 over the next 8 ticks, from wherever the pack now is.
		EMITTERS.add(new Emitter(payload.entityId(), EMIT_FLAME, 1, new int[] {Vfx.count(8), 0}));
	}

	private static void flames(Vec3 at, Vec3 dir, int n) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < n; i++) {
			Vec3 velocity = dir.scale(0.25 + RANDOM.nextDouble() * 0.2).add(jitter(0.05), jitter(0.03), jitter(0.05));
			GearParticleProviders.spawn(EquipmentParticles.FLAME_JET, at.add(jitter(0.12), 0, jitter(0.12)), velocity);
		}
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level != seenLevel) {
			reset();
			seenLevel = level;
		}

		if (level == null || client.isPaused()) {
			return;
		}

		ticks++;
		recoilO = recoil;
		recoil = Math.max(0f, recoil - 0.25f);

		if (client.player != null && !(client.player.getMainHandItem().getItem() instanceof BlasterItem)) {
			loaded = false;
			chargeStart = -1;
		}

		tickEmitters(client, level);
		tickStaffs(client, level);
		tickSpins(client, level);
		WHINES.values().removeIf(HeldSound::isStopped);
		STAFF_SOUNDS.values().removeIf(HeldSound::isStopped);

		if (ticks % 200 == 0) {
			LAST_FIRE.values().removeIf(at -> ticks - at > 200);
		}
	}

	private static void tickEmitters(Minecraft client, ClientLevel level) {
		for (Iterator<Emitter> it = EMITTERS.iterator(); it.hasNext();) {
			Emitter emitter = it.next();
			int[] state = emitter.left();

			if (!(level.getEntity(emitter.entityId()) instanceof LivingEntity holder) || !holder.isAlive() || state[0] <= 0) {
				it.remove();
				continue;
			}

			if (state[1]++ % emitter.every() != 0) {
				continue;
			}

			state[0]--;

			if (emitter.kind() == EMIT_STEAM) {
				steam(muzzle(client, holder, holder.getEyePosition()), 1, 0.08f, 0.9f);
			} else {
				Vec3 back = flatLook(holder).scale(-0.3);
				Vec3 pack = holder.position().add(back.x, 1.05, back.z);
				flames(pack, new Vec3(0, -1, 0).add(back), 1);
			}
		}
	}

	/** Arcs crawl along both staff ends while a charge is loaded. */
	private static void tickStaffs(Minecraft client, ClientLevel level) {
		for (Iterator<Map.Entry<Integer, Integer>> it = STAFF_LOADED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Integer> entry = it.next();

			if (!(level.getEntity(entry.getKey()) instanceof LivingEntity wielder) || !wielder.isAlive() || !holdingStaff(wielder)
					|| ticks - entry.getValue() > STAFF_LOADED_MAX) {
				it.remove();
				continue;
			}

			if ((ticks + entry.getKey()) % 3 == 0) {
				arcs(level, staffEnd(client, wielder, true), 1, 0.08);
				arcs(level, staffEnd(client, wielder, false), 1, 0.08);
			}
		}

		// A charge still building: a faint crawl at the top end only.
		for (Map.Entry<Integer, HeldSound> entry : STAFF_SOUNDS.entrySet()) {
			if ((ticks + entry.getKey()) % 6 == 0 && level.getEntity(entry.getKey()) instanceof LivingEntity wielder && holdingStaff(wielder)) {
				arcs(level, staffEnd(client, wielder, true), 1, 0.05);
			}
		}
	}

	/** The repeater's barrel blur: a few steel motes circling the muzzle while it spins. */
	private static void tickSpins(Minecraft client, ClientLevel level) {
		for (Iterator<Map.Entry<Integer, Integer>> it = SPINNING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Integer> entry = it.next();

			if (!(level.getEntity(entry.getKey()) instanceof LivingEntity holder) || !holder.isAlive()
					|| !(holder.getMainHandItem().getItem() instanceof BlasterItem item) || item.kind() != BlasterItem.Kind.REPEATER) {
				it.remove();
				continue;
			}

			if (holder.isUsingItem()) {
				entry.setValue(ticks);
			} else if (ticks - entry.getValue() > SPIN_IDLE_MAX) {
				it.remove();
				continue;
			}

			if (ticks % 4 == 0) {
				Vec3 at = muzzle(client, holder, holder.getEyePosition());

				if (Vfx.inRange(at)) {
					double angle = ticks * 1.3;
					Vec3 look = holder.getLookAngle();
					Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(Math.cos(angle) * 0.06);
					EnemyParticleProviders.EnemyParticle mote = EnemyParticleProviders.spawn(EnemyParticles.FLARE_EMBER,
							at.add(side).add(0, Math.sin(angle) * 0.06, 0), Vec3.ZERO);

					if (mote != null) {
						mote.tint(STEEL).size(0.018f).life(3).gravity(0f).alpha(0.5f);
					}
				}
			}
		}
	}

	private static void reset() {
		LAST_FIRE.clear();
		WHINES.values().forEach(HeldSound::cut);
		WHINES.clear();
		STAFF_SOUNDS.values().forEach(HeldSound::cut);
		STAFF_SOUNDS.clear();
		STAFF_LOADED.clear();
		SPINNING.clear();
		EMITTERS.clear();
		recoil = 0f;
		recoilO = 0f;
		loaded = false;
		chargeStart = -1;
	}

	// ---------------------------------------------------------------- positions

	/**
	 * The muzzle of {@code holder}'s gun: the server's point for others, moved to where the barrel
	 * shows in first person for the local player.
	 */
	static Vec3 muzzle(Minecraft client, LivingEntity holder, Vec3 server) {
		if (holder != client.getCameraEntity() || !client.options.getCameraType().isFirstPerson()) {
			return server;
		}

		Vec3 look = holder.getLookAngle();
		Vec3 right = rightOf(look).scale(holder.getMainArm() == HumanoidArm.LEFT ? -1 : 1);
		Vec3 up = right.cross(look).normalize().scale(holder.getMainArm() == HumanoidArm.LEFT ? -1 : 1);
		return holder.getEyePosition().add(look.scale(0.8)).add(right.scale(0.24)).add(up.scale(-0.16));
	}

	/** One end of a held electrostaff: the top end ahead of the shoulder, the bottom behind the hip. */
	private static Vec3 staffEnd(Minecraft client, LivingEntity wielder, boolean top) {
		Vec3 look = flatLook(wielder);
		Vec3 right = rightOf(look);

		if (wielder == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
			Vec3 eye = wielder.getEyePosition();
			return top ? eye.add(wielder.getLookAngle().scale(0.9)).add(right.scale(0.3)).add(0, 0.05, 0)
					: eye.add(look.scale(0.2)).add(right.scale(0.45)).add(0, -0.9, 0);
		}

		Vec3 hand = wielder.position().add(right.scale(0.35)).add(0, 0.85, 0).add(look.scale(0.3));
		return top ? hand.add(look.scale(0.45)).add(0, 0.85, 0) : hand.add(look.scale(-0.35)).add(0, -0.6, 0);
	}

	private static Vec3 flatLook(LivingEntity e) {
		Vec3 flat = e.getLookAngle().horizontal();
		return flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static Vec3 rightOf(Vec3 look) {
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		return right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
	}

	private static double jitter(double spread) {
		return (RANDOM.nextDouble() - 0.5) * 2 * spread;
	}

	private GearEffectsClient() {
	}
}

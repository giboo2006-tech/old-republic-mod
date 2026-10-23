package com.oldrepublic.client.equipment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.saber.SaberSparks;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.melee.MeleeBalance;
import com.oldrepublic.equipment.melee.MeleeM7Effects;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Milestone 7 melee weapons' client side (docs/MILESTONE_7.md 9.1, 9.2 and 11): the events of
 * {@link MeleeM7Effects#MELEE_M7} and the vibro-axe's swing.
 * <ul>
 * <li>the force pike: a rising buzz while its charge builds ({@code force_pike.charge}, stopped when the
 * charge loads, is spent or leaves the hand), then arcs crawling at the head while it is loaded; the
 * stun thrust's electric snap ({@code force_pike.stun}) with arcs over the struck foe, or on a droid a
 * spray of hot sparks; a weak fizz when an unspent charge dies;</li>
 * <li>the vibro-axe: a heavy whoosh on every swing of anyone holding one ({@code vibro_axe.swing}, heard
 * from the vanilla swing state, so a miss whooshes too), and Sunder's crack on a guard
 * ({@code vibro_axe.sunder}): loud with a flash, hot sparks and grit when the guard broke, a dull knock
 * when it only drained it;</li>
 * <li>a Clan Iron Edge holding a saber: a ringing clash with cold blue-grey sparks and no notch;</li>
 * <li>the Starfall and Clan Iron Edges' tooltip lines ({@link #modEffect}).</li>
 * </ul>
 * Every loop stops when its entity dies or is removed, the level changes, the pike leaves the hand,
 * or its own end event arrives, with a hard cap as a watchdog. Particle counts go through {@link Vfx}.
 * A Phase 0 stub that WM filled; called once from {@link EquipmentClient#init}.
 */
public final class MeleeM7Client {
	private static final RandomSource RANDOM = RandomSource.create();
	/** The tooltips' numbers: the registration defaults, as {@code GearTooltips} reads its legendary mods. */
	private static final MeleeBalance NUMBERS = new MeleeBalance();
	/** The pike's arcs: pale electric blue; Sunder's sparks: hot orange; a Clan Iron Edge's: cold blue-grey. */
	private static final int PIKE_ARC = 0xB8D4FF;
	private static final int HOT_ORANGE = 0xFFA040;
	private static final int CLAN_IRON = 0x9FB4C8;
	/** Watchdogs: the longest a charge buzz or a loaded crackle may run without its end event. */
	private static final int PIKE_CHARGE_MAX = 50;
	private static final int PIKE_LOADED_MAX = 140;
	/** How far away an axe swing is heard, blocks. */
	private static final double SWING_RANGE = 24;
	private static final float SWING_VOLUME = 0.85f;
	private static final float SWING_PITCH_SPREAD = 0.1f;

	private static final Map<Integer, HeldSound> CHARGE_SOUNDS = new HashMap<>();
	/** Entity id to the client tick its pike charge loaded. */
	private static final Map<Integer, Integer> LOADED = new HashMap<>();
	/** Entity id to the swing clock seen last tick, or -2 when not swinging. */
	private static final Map<Integer, Integer> WAS_SWINGING = new HashMap<>();

	private static int ticks;
	private static @Nullable ClientLevel seenLevel;

	static void init() {
		ClientEffects.register(MeleeM7Effects.MELEE_M7, MeleeM7Client::onEffect);
		ClientTickEvents.END_CLIENT_TICK.register(MeleeM7Client::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(MeleeM7Client::reset));
	}

	/**
	 * The tooltip line of a Milestone 7 mod (the Starfall and Clan Iron Edges): the translation {@code key} with
	 * its numbers, or null for any other item. Called by {@link GearTooltips} for a mod and for a fitted one.
	 */
	static @Nullable Component modEffect(Item mod, String key) {
		if (mod == M7Items.MOD_STARFALL_EDGE) {
			return Component.translatable(key, ArmourItem.percent(NUMBERS.starfallEdgeDamagePct / 100f),
					ArmourItem.percent(NUMBERS.starfallEdgeDurabilityPct / 100f));
		}

		if (mod == M7Items.MOD_CLAN_IRON_EDGE) {
			return Component.translatable(key, ArmourItem.percent(-NUMBERS.clanIronEdgeDrainPct / 100f));
		}

		return null;
	}

	// ---------------------------------------------------------------- events

	private static void onEffect(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int id = payload.entityId();
		Vec3 at = payload.pos();
		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;

		switch (payload.variant()) {
			case MeleeM7Effects.PIKE_CHARGE -> {
				cutCharge(id);

				if (entity instanceof LivingEntity wielder) {
					CHARGE_SOUNDS.put(id, HeldSound.play(ModSounds.FORCE_PIKE_CHARGE, 0.9f, 1f, wielder, MeleeM7Client::holdingPike, PIKE_CHARGE_MAX));
				}
			}
			case MeleeM7Effects.PIKE_CHARGED -> {
				LOADED.put(id, ticks);

				if (entity instanceof LivingEntity wielder && holdingPike(wielder)) {
					arcs(level, pikeHead(client, wielder), Vfx.count(4), 0.12);
				}
			}
			case MeleeM7Effects.PIKE_STUN -> {
				cutCharge(id);
				LOADED.remove(id);
				boolean droid = payload.intensity() <= 0f;
				QuestSounds.at(level, at, ModSounds.FORCE_PIKE_STUN, 1f, (droid ? 0.85f : 1f) + RANDOM.nextFloat() * 0.1f);
				arcs(level, at, Vfx.count(droid ? 4 : 9), droid ? 0.25 : 0.4);

				if (droid) {
					// No stun: the charge shorts out across the plating in a spray of hot metal.
					sparks(at, dir.scale(-1), Vfx.count(5), HOT_ORANGE, 0.2f);
				}
			}
			case MeleeM7Effects.SUNDER -> {
				boolean broke = payload.intensity() > 0f;

				if (broke) {
					QuestSounds.at(level, at, ModSounds.VIBRO_AXE_SUNDER, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);

					if (Vfx.inRange(at)) {
						SaberSparks.flash(at, HOT_ORANGE);
					}

					sparks(at, dir, Vfx.count(7), HOT_ORANGE, 0.24f);
					grit(level, at, Vfx.count(4));
				} else {
					// Drained, not broken: a dull knock of the heavy head on the guard.
					QuestSounds.at(level, at, ModSounds.VIBROBLADE_HIT, 0.8f, 0.75f);
					sparks(at, dir, Vfx.count(3), HOT_ORANGE, 0.16f);
				}
			}
			case MeleeM7Effects.EDGE_HOLD -> {
				QuestSounds.at(level, at, ModSounds.SABER_CLASH, 0.8f, 0.6f);
				sparks(at, dir, Vfx.count(4), CLAN_IRON, 0.16f);
			}
			default -> {
				// Spent: the crackle dies with a weak fizz.
				boolean wasLive = LOADED.remove(id) != null || CHARGE_SOUNDS.containsKey(id);
				cutCharge(id);

				if (wasLive && entity instanceof LivingEntity wielder) {
					QuestSounds.at(level, pikeHead(client, wielder), ModSounds.FORCE_PIKE_STUN, 0.25f, 0.55f);
				}
			}
		}
	}

	private static boolean holdingPike(LivingEntity e) {
		return e.getMainHandItem().getItem() instanceof VibroWeaponItem item && item.kind() == VibroWeaponItem.Kind.PIKE;
	}

	private static boolean holdingAxe(LivingEntity e) {
		return e.getMainHandItem().getItem() instanceof VibroWeaponItem item && item.kind() == VibroWeaponItem.Kind.AXE;
	}

	private static void cutCharge(int entityId) {
		HeldSound sound = CHARGE_SOUNDS.remove(entityId);

		if (sound != null) {
			sound.cut();
		}
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level != seenLevel) {
			reset();
			seenLevel = level;
		}

		if (level == null || client.isPaused() || client.player == null) {
			return;
		}

		ticks++;
		tickPikes(client, level);
		tickSwings(client, level);
		CHARGE_SOUNDS.values().removeIf(HeldSound::isStopped);
	}

	/** Arcs crawl over the pike's head while its charge is loaded; a faint crawl while it builds. */
	private static void tickPikes(Minecraft client, ClientLevel level) {
		for (Iterator<Map.Entry<Integer, Integer>> it = LOADED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Integer> entry = it.next();

			if (!(level.getEntity(entry.getKey()) instanceof LivingEntity wielder) || !wielder.isAlive() || !holdingPike(wielder)
					|| ticks - entry.getValue() > PIKE_LOADED_MAX) {
				it.remove();
				continue;
			}

			if ((ticks + entry.getKey()) % 3 == 0) {
				arcs(level, pikeHead(client, wielder), 1, 0.07);
			}
		}

		for (Map.Entry<Integer, HeldSound> entry : CHARGE_SOUNDS.entrySet()) {
			if ((ticks + entry.getKey()) % 6 == 0 && level.getEntity(entry.getKey()) instanceof LivingEntity wielder && holdingPike(wielder)) {
				arcs(level, pikeHead(client, wielder), 1, 0.04);
			}
		}
	}

	/**
	 * The vibro-axe's whoosh: a swing counts as new when the vanilla swing starts or restarts (its clock
	 * goes back), as the saber's swing sound does.
	 */
	private static void tickSwings(Minecraft client, ClientLevel level) {
		AABB area = client.player.getBoundingBox().inflate(SWING_RANGE);

		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator())) {
			int clock = entity.swinging ? entity.swingTime : -2;
			Integer before = WAS_SWINGING.put(entity.getId(), clock);
			boolean started = entity.swinging && (before == null || before == -2 || clock < before);

			if (started && holdingAxe(entity)) {
				float pitch = 1f + (RANDOM.nextFloat() - 0.5f) * SWING_PITCH_SPREAD;
				SoundSource source = entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
				level.playLocalSound(entity.getX(), entity.getY() + 1.2, entity.getZ(), ModSounds.VIBRO_AXE_SWING, source,
						SWING_VOLUME * ClientConfig.get().modSoundVolume, pitch, false);
			}
		}

		if (ticks % 100 == 0) {
			WAS_SWINGING.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	private static void reset() {
		CHARGE_SOUNDS.values().forEach(HeldSound::cut);
		CHARGE_SOUNDS.clear();
		LOADED.clear();
		WAS_SWINGING.clear();
	}

	// ---------------------------------------------------------------- particles and positions

	private static void arcs(ClientLevel level, Vec3 at, int n, double spread) {
		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < n; i++) {
			level.addParticle(ForceParticles.LIGHTNING_SPARK, at.x + jitter(spread), at.y + jitter(spread), at.z + jitter(spread),
					jitter(0.08), jitter(0.08), jitter(0.08));
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

	/**
	 * The head of a held force pike, where its in-hand model draws it (tools/assets/gen_weapons_melee_m7.py):
	 * in first person about 1.8 blocks ahead of the eyes and half a block right, at eye level; else
	 * upright above the wielder's right hand, about a block over it.
	 */
	private static Vec3 pikeHead(Minecraft client, LivingEntity wielder) {
		Vec3 look = flatLook(wielder);
		Vec3 right = rightOf(look);

		if (wielder == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
			return wielder.getEyePosition().add(wielder.getLookAngle().scale(1.8)).add(right.scale(0.5));
		}

		Vec3 hand = wielder.position().add(right.scale(0.31)).add(0, 0.7, 0).add(look.scale(0.25));
		return hand.add(look.scale(0.05)).add(0, 1.05, 0);
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

	private MeleeM7Client() {
	}
}

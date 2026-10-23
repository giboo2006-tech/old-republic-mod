package com.oldrepublic.client.equipment;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.EquipmentParticles;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.equipment.blaster.BlasterBalance;
import com.oldrepublic.equipment.blaster.BlasterEffects;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Milestone 7 blasters' client side (docs/MILESTONE_7.md 9.1 to 9.3 and 11):
 * <ul>
 * <li>{@code blaster_m7_fire} ({@link BlasterEffects#M7_FIRE}): the scatter blaster's blast, a short
 * wide crack ({@code blaster.scatter.fire}), a broad green-white flash and a fan of sparks and a puff of
 * smoke at the mouth; each bolt of the carbine's burst, a sharp light crack ({@code blaster.carbine.fire})
 * and a small flash; the shooter's recoil (a hard shove for the scatter, small kicks through a burst)
 * and, for the poses, when each holder last fired;</li>
 * <li>the local player's carbine burst for the HUD's pips: bolts fired in the burst in progress and when
 * it began;</li>
 * <li>the muzzle point of each blaster's in-hand model, in the held item's frame, for the NPC glow
 * layers ({@link #heldMuzzle});</li>
 * <li>the Aurelite Barrel's and the Duskglass Cell's tooltip lines ({@link #modEffect}).</li>
 * </ul>
 * The hit and deflect markers, overheat and venting are the Milestone 4 events every blaster shares
 * ({@link GearEffectsClient}). Particle counts go through {@link Vfx}. A Phase 0 stub that WB filled;
 * called once from {@link EquipmentClient#init}.
 */
public final class BlasterM7Client {
	private static final RandomSource RANDOM = RandomSource.create();
	/** The tooltips' and the HUD's numbers: the registration defaults (the server's file is not synced). */
	private static final BlasterBalance DEFAULTS = new BlasterBalance();
	/** Player bolt colours (the renderer's PLAYER palette): glow and core; the scatter's hot sparks. */
	private static final int BOLT_GLOW = 0x5CF07A;
	private static final int BOLT_CORE = 0xEAFFEA;
	private static final int HOT_SPARK = 0xFFD890;
	private static final int SMOKE = 0x9A9A96;
	/** Recoil each shot adds for the shooter (0 to 1; it falls by {@link #RECOIL_FALL} a tick). */
	private static final float SCATTER_RECOIL = 1f;
	private static final float CARBINE_RECOIL = 0.4f;
	private static final float RECOIL_FALL = 0.25f;
	/** The scatter's sparks: how many, and the half-angle (radians) of the fan they fly in. */
	private static final int SCATTER_SPARKS = 6;
	private static final double SCATTER_FAN = 0.32;

	/**
	 * Muzzle points of the blasters' in-hand models in the held item's frame (vanilla ItemInHandLayer's
	 * frame, then the model's third-person transform): blocks up from the hand and along the barrel
	 * (negative is forward). tools/assets/gen_weapons_blasters_m7.py builds all six models and prints these; the
	 * pistol's, rifle's, repeater's and longshot's are the points their older models had.
	 */
	private static final Vec3 PISTOL_MUZZLE = new Vec3(0, 0.12, -0.32);
	private static final Vec3 RIFLE_MUZZLE = new Vec3(0, 0.0656, -0.7125);
	private static final Vec3 REPEATER_MUZZLE = new Vec3(0, 0.0703, -0.75);
	private static final Vec3 LONGSHOT_MUZZLE = new Vec3(0, 0.0656, -0.9);
	private static final Vec3 SCATTER_MUZZLE = new Vec3(0, 0.075, -0.6375);
	private static final Vec3 CARBINE_MUZZLE = new Vec3(0, 0.0656, -0.6563);

	private static final Map<Integer, Integer> LAST_FIRE = new HashMap<>();
	private static int ticks;
	private static @Nullable ClientLevel seenLevel;
	private static float recoil;
	private static float recoilO;
	/** The local player's carbine burst: bolts fired in the latest burst and the tick it began. */
	private static int burstBolts;
	private static int burstTick = Integer.MIN_VALUE / 2;

	static void init() {
		ClientEffects.register(BlasterEffects.M7_FIRE, BlasterM7Client::onFire);
		ClientTickEvents.END_CLIENT_TICK.register(BlasterM7Client::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	// ---------------------------------------------------------------- what the HUD, poses and layers read

	/** Ticks since {@code entityId} last fired a scatter blaster or a carbine, or a large number. */
	static int sinceFire(int entityId) {
		Integer at = LAST_FIRE.get(entityId);
		return at == null ? Integer.MAX_VALUE / 2 : ticks - at;
	}

	/** The local player's recoil kick from these blasters, 0 to 1. */
	static float recoil(float partialTick) {
		return Mth.lerp(partialTick, recoilO, recoil);
	}

	/** Bolts the local player's latest carbine burst has fired. */
	static int burstBolts() {
		return burstBolts;
	}

	/** Client ticks since the local player's latest carbine burst began, or a large number. */
	static int sinceBurst() {
		return ticks - burstTick;
	}

	/**
	 * Where the muzzle of {@code held}'s in-hand model is in the held item's frame (x 0, y up from the
	 * hand, z along the barrel, negative forward); the rifle's for anything that is not a blaster, as the
	 * glow layers have always assumed.
	 */
	public static Vec3 heldMuzzle(ItemStack held) {
		if (!(held.getItem() instanceof BlasterItem blaster)) {
			return RIFLE_MUZZLE;
		}

		if (held.is(GearItems.KAAST_LONGSHOT)) {
			return LONGSHOT_MUZZLE;
		}

		return switch (blaster.kind()) {
			case PISTOL -> PISTOL_MUZZLE;
			case RIFLE -> RIFLE_MUZZLE;
			case REPEATER -> REPEATER_MUZZLE;
			case SCATTER -> SCATTER_MUZZLE;
			case CARBINE -> CARBINE_MUZZLE;
		};
	}

	/**
	 * The tooltip line of a Milestone 7 mod (the Aurelite Barrel and the Duskglass Cell): the translation {@code key} with
	 * its numbers, or null for any other item. Called by {@link GearTooltips} for a mod and for a fitted one.
	 */
	static @Nullable Component modEffect(Item mod, String key) {
		if (mod == M7Items.MOD_AURELITE_BARREL) {
			return Component.translatable(key, ArmourItem.percent(DEFAULTS.aureliteSpreadPct / 100f), ArmourItem.percent(DEFAULTS.aureliteDamagePct / 100f));
		}

		if (mod == M7Items.MOD_DUSKGLASS_CELL) {
			return Component.translatable(key, ArmourItem.percent(DEFAULTS.duskglassHeatPct / 100f));
		}

		return null;
	}

	// ---------------------------------------------------------------- blaster_m7_fire

	private static void onFire(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		boolean scatter = payload.variant() == BlasterEffects.SCATTER_BLAST;
		boolean self = entity != null && entity == client.player;
		QuestSounds.at(level, payload.pos(), scatter ? ModSounds.BLASTER_SCATTER_FIRE : ModSounds.BLASTER_CARBINE_FIRE,
				scatter ? 1.1f : 0.85f, 0.95f + RANDOM.nextFloat() * 0.1f);
		LAST_FIRE.put(payload.entityId(), ticks);

		if (self) {
			recoil = Math.min(1f, recoil + (scatter ? SCATTER_RECOIL : CARBINE_RECOIL));

			if (payload.variant() == BlasterEffects.CARBINE_BURST) {
				burstBolts = 1;
				burstTick = ticks;
			} else if (!scatter) {
				burstBolts++;
			}
		}

		Vec3 at = entity instanceof LivingEntity shooter ? GearEffectsClient.muzzle(client, shooter, payload.pos()) : payload.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		Vec3 dir = payload.dir().lengthSqr() > 1.0e-6 ? payload.dir().normalize() : Vec3.ZERO;

		if (scatter) {
			blast(at, dir);
		} else {
			bolt(at, dir);
		}
	}

	/** The scatter's blast: a broad flash with a hot core, a fan of sparks thrown forward, a puff of smoke. */
	private static void blast(Vec3 at, Vec3 dir) {
		EnemyParticleProviders.EnemyParticle flash = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, Vec3.ZERO);

		if (flash != null) {
			flash.tint(BOLT_GLOW).size(0.3f).life(3).bright();
		}

		EnemyParticleProviders.EnemyParticle core = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, Vec3.ZERO);

		if (core != null) {
			core.tint(BOLT_CORE).size(0.14f).life(2).bright();
		}

		Vec3 flat = new Vec3(-dir.z, 0, dir.x);
		Vec3 side = flat.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : flat.normalize();
		Vec3 up = side.cross(dir);

		for (int i = 0, n = Vfx.count(SCATTER_SPARKS); i < n; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double spread = SCATTER_FAN * Math.sqrt(RANDOM.nextDouble());
			Vec3 out = dir.add(side.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread)).normalize();
			EnemyParticleProviders.EnemyParticle spark = EnemyParticleProviders.spawn(EnemyParticles.BOLT_IMPACT, at,
					out.scale(0.25 + RANDOM.nextDouble() * 0.2));

			if (spark != null) {
				spark.tint(i % 2 == 0 ? HOT_SPARK : BOLT_CORE).size(0.03f).life(3 + RANDOM.nextInt(3)).gravity(0.02f);
			}
		}

		GearParticleProviders.GearParticle smoke = GearParticleProviders.spawn(EquipmentParticles.STEAM_VENT, at.add(dir.scale(0.2)), dir.scale(0.04));

		if (smoke != null) {
			smoke.tint(SMOKE).size(0.12f).alpha(0.45f).growth(1.5f).life(14);
		}
	}

	/** One carbine bolt: a small flash and a spark. */
	private static void bolt(Vec3 at, Vec3 dir) {
		EnemyParticleProviders.EnemyParticle flash = EnemyParticleProviders.spawn(EnemyParticles.MUZZLE_GLOW, at, Vec3.ZERO);

		if (flash != null) {
			flash.tint(BOLT_GLOW).size(0.1f).life(2).bright();
		}

		for (int i = 0, n = Vfx.count(1); i < n; i++) {
			EnemyParticleProviders.EnemyParticle spark = EnemyParticleProviders.spawn(EnemyParticles.BOLT_IMPACT, at,
					dir.scale(0.14 + RANDOM.nextDouble() * 0.08).add(jitter(0.03), jitter(0.03), jitter(0.03)));

			if (spark != null) {
				spark.tint(BOLT_CORE).size(0.022f).life(3).gravity(0f);
			}
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
		recoil = Math.max(0f, recoil - RECOIL_FALL);

		if (ticks % 200 == 0) {
			LAST_FIRE.values().removeIf(at -> ticks - at > 200);
		}
	}

	private static void reset() {
		LAST_FIRE.clear();
		recoil = 0f;
		recoilO = 0f;
		burstBolts = 0;
		burstTick = Integer.MIN_VALUE / 2;
	}

	private static double jitter(double spread) {
		return (RANDOM.nextDouble() - 0.5) * 2 * spread;
	}

	private BlasterM7Client() {
	}
}

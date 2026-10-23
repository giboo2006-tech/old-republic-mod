package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentBalance;
import com.oldrepublic.equipment.EquipmentItems;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.equipment.EquipmentModifiers;
import com.oldrepublic.equipment.EquipmentTraits;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.equipment.SetBonusRules;
import com.oldrepublic.equipment.SetBonuses;
import com.oldrepublic.equipment.blaster.BlasterBalance;
import com.oldrepublic.equipment.blaster.BlasterHeat;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.blaster.BlasterRules;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.blaster.PlayerBolts;
import com.oldrepublic.equipment.jet.JetPack;
import com.oldrepublic.equipment.melee.MeleeBalance;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.equipment.melee.MeleeRules;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.equipment.mod.FittedMods;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mixin.equipment.LivingEntityAttackStrengthAccessor;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 gear (spec MILESTONE_4.md 5.1 to 5.4, 6 and test plan 17.1, W4): blaster heat,
 * venting, the charged shot, player bolts, aim spread, the repeater; the vibroweapon guard, flurry
 * and cleave; the electrostaff against a saber; quality; the per-ordinal {@code Worn} counts; the
 * four new sets with their traits, bonuses and alignment gates; the {@code Extra} traits codec; mod
 * slots and the modifier clamps; the jet pack's charges and its deference to Force Jump.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); players are listed mock
 * players ({@link M3TestPlayers}) removed at the end, which also drops their blaster, melee, jet
 * and Warden state through the leave event; randoms are seeded; nothing global is written. The
 * numbers come from the live balance sections, so a tuned file still passes. The pure rules are
 * driven with explicit ticks; the in-world checks call the same hooks the inputs use
 * ({@code Blasters.tryFire}, {@code Blasters.action}, {@code MeleeCombat.swing}). Mock players do
 * not run the vanilla equipment tick, so the tests apply worn and held modifiers the way vanilla
 * does (remove by id, then add).
 */
public class GearGameTests {
	private static final double EPS = 1.0e-3;
	/** How far around the shooter fired bolts are looked for (and discarded at the end). */
	private static final double BOLT_SEARCH = 24.0;
	/** A full attack ticker: far past any weapon's cooldown. */
	private static final int FULL_TICKER = 100;
	/** A tick far from zero for the pure rules, so no subtraction goes negative. */
	private static final long T0 = 10_000L;
	/** The player's spot in the fixtures; targets stand south of it (+z), the way it faces. */
	private static final BlockPos PLAYER_AT = new BlockPos(2, 1, 1);

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	private static double pow(double base, int n) {
		return Math.pow(base, n);
	}

	/** {@code 1 + pct/100}. */
	private static double ratio(float pct) {
		return 1.0 + pct / 100.0;
	}

	// ---------------------------------------------------------------- fixtures

	/** A listed player at {@link #PLAYER_AT}, on the ground, facing south (+z) and level. */
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper, PLAYER_AT);
		player.setGameMode(GameType.SURVIVAL);
		face(player);
		player.setOnGround(true);
		player.xo = player.getX();
		player.zo = player.getZ();
		return player;
	}

	private static void face(ServerPlayer player) {
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
	}

	/** Puts {@code stack} in the main hand with its modifiers applied as vanilla's equipment tick does; returns the held stack. */
	private static ItemStack hold(ServerPlayer player, ItemStack stack) {
		player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			AttributeInstance instance = player.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
			}
		});
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			AttributeInstance instance = player.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
		return player.getMainHandItem();
	}

	/** Holds a blaster drawn long ago (no draw delay); returns the held stack. */
	private static ItemStack drawn(ServerPlayer player, ItemStack gun) {
		ItemStack held = hold(player, gun);
		Blasters.drawnLongAgo(player, held);
		return held;
	}

	/** Puts {@code stack} on and applies its modifiers as vanilla's equipment tick does (remove by id, then add). */
	private static void wear(LivingEntity entity, ItemStack stack) {
		EquipmentSlot slot = ((ArmourItem) stack.getItem()).type().getSlot();
		takeOff(entity, slot);
		entity.setItemSlot(slot, stack);
		stack.forEachModifier(slot, (attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
	}

	private static void takeOff(LivingEntity entity, EquipmentSlot slot) {
		entity.getItemBySlot(slot).forEachModifier(slot, (attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
			}
		});
		entity.setItemSlot(slot, ItemStack.EMPTY);
	}

	private static void strip(LivingEntity entity) {
		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			takeOff(entity, slot);
		}
	}

	/** Wears the first {@code pieces} pieces of {@code set}, head first, and nothing else. */
	private static void wearSet(LivingEntity entity, ArmourSet set, int pieces) {
		strip(entity);

		for (int i = 0; i < pieces; i++) {
			wear(entity, ArmourSets.piece(set, ArmourSets.SLOTS[i]));
		}
	}

	/** Wears all four pieces of {@code set}, each with one {@code mod} of quality {@code q} fitted. */
	private static void wearModded(LivingEntity entity, ArmourSet set, Item mod, Quality q) {
		strip(entity);

		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			ItemStack piece = ArmourSets.piece(set, slot);
			Mods.fit(piece, ModSlot.ARMOUR, Crafting.withQuality(new ItemStack(mod), q));
			wear(entity, piece);
		}
	}

	/** A spacer's jacket with a jet pack fitted, worn. */
	private static void wearJetPack(ServerPlayer player) {
		ItemStack jacket = new ItemStack(EquipmentItems.SPACER_JACKET);
		Mods.fit(jacket, ModSlot.PACK, new ItemStack(M4Items.JET_PACK));
		player.setItemSlot(EquipmentSlot.CHEST, jacket);
	}

	/** Sets the player's alignment and path directly (no shift caps, no log). */
	private static void identity(ServerPlayer player, int alignment, Path path) {
		IdentityData data = Identity.get(player);
		data.alignment = alignment;
		data.path = path;
		Identity.commit(player, data);
	}

	/** The bolts {@code shooter} owns near them. */
	private static List<BlasterBolt> bolts(ServerPlayer shooter) {
		return shooter.level().getEntitiesOfClass(BlasterBolt.class, shooter.getBoundingBox().inflate(BOLT_SEARCH), bolt -> bolt.getOwner() == shooter);
	}

	/** The one bolt {@code shooter} just fired (fails when there is not exactly one). */
	private static BlasterBolt onlyBolt(GameTestHelper helper, ServerPlayer shooter) {
		List<BlasterBolt> found = bolts(shooter);
		helper.assertTrue(found.size() == 1, msg("expected exactly one bolt in flight, found " + found.size()));
		return found.get(0);
	}

	/** Drops the player's blaster state and bolts, then logs them out. */
	private static void done(ServerPlayer player) {
		Blasters.forget(player);
		bolts(player).forEach(Entity::discard);
		M3TestPlayers.remove(player);
	}

	private static DamageSource boltSource(ServerLevel level, BlasterBolt bolt, Entity shooter) {
		return level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, bolt, shooter);
	}

	/** One player attack on {@code target} with a full attack charge, as the attack packet does (the callback's swing first). */
	private static void strike(ServerPlayer player, LivingEntity target, VibroWeaponItem.Kind kind) {
		((LivingEntityAttackStrengthAccessor) player).oldrepublic$setAttackStrengthTicker(FULL_TICKER);
		MeleeCombat.swing(player, kind, target.getId(), 1f, player.level().getGameTime());
		player.attack(target);
	}

	// ---------------------------------------------------------------- blasters (5.1)

	@GameTest(maxTicks = 20)
	public void heatRisesLocksAndCools(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();

		for (BlasterItem.Kind kind : BlasterItem.Kind.values()) {
			float per = BlasterBalance.of(b.heatPerShot, kind);
			float cooling = BlasterBalance.of(b.cooling, kind);
			int lock = BlasterBalance.of(b.overheatLock, kind);
			String k = kind + ": ";

			BlasterHeat one = BlasterRules.afterShot(BlasterHeat.COLD, T0, per, kind, b);
			helper.assertTrue(near(BlasterRules.heatAt(one, T0, kind, b), per), msg(k + "one shot adds its heat"));
			helper.assertTrue(near(BlasterRules.heatAt(one, T0 + b.coolDelay, kind, b), per), msg(k + "no cooling for coolDelay ticks"));
			helper.assertTrue(near(BlasterRules.heatAt(one, T0 + b.coolDelay + 2, kind, b), Math.max(0f, per - 2 * cooling)),
					msg(k + "then it cools by the rate per tick"));
			helper.assertTrue(near(BlasterRules.heatAt(one, T0 + b.coolDelay + 1000, kind, b), 0), msg(k + "and stops at 0"));

			// Back to back shots, one per tick (inside the cool delay): heat climbs until it overheats.
			BlasterHeat h = BlasterHeat.COLD;
			int shots = 0;
			long now = T0;

			while (shots < 200) {
				h = BlasterRules.afterShot(h, now, per, kind, b);
				shots++;

				if (BlasterRules.overheated(h, now)) {
					break;
				}

				now++;
			}

			helper.assertTrue(shots == (int) Math.ceil(BlasterRules.MAX_HEAT / per), msg(k + "overheats on shot " + Math.ceil(BlasterRules.MAX_HEAT / per) + ", got " + shots));
			helper.assertTrue(near(h.heat(), BlasterRules.MAX_HEAT) && h.lockedUntil() == now + lock, msg(k + "an overheat stores 100 and the lock"));
			helper.assertTrue(BlasterRules.locked(h, now + lock - 1) && !BlasterRules.locked(h, now + lock), msg(k + "locked for overheatLock ticks exactly"));
			helper.assertTrue(near(BlasterRules.heatAt(h, now + lock / 2, kind, b), BlasterRules.MAX_HEAT * (lock - lock / 2) / (float) lock),
					msg(k + "the heat bleeds off in a straight line during the lock"));
			helper.assertTrue(near(BlasterRules.heatAt(h, now + lock, kind, b), 0), msg(k + "cold when the lock ends"));
			helper.assertTrue(!BlasterRules.locked(BlasterRules.afterShot(h, now + lock, per, kind, b), now + lock), msg(k + "fires again after the lock"));
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void ventBelow30DoesNothing(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();
		BlasterItem.Kind kind = BlasterItem.Kind.PISTOL;
		BlasterHeat cool = new BlasterHeat(b.ventMin - 1f, T0, 0L, 0);
		helper.assertTrue(BlasterRules.ventResult(cool, T0, kind, b) == BlasterRules.VentResult.NONE && BlasterRules.vent(cool, T0, kind, b) == null,
				msg("below ventMin a vent does nothing"));
		BlasterHeat warm = new BlasterHeat(b.ventMin, T0, 0L, 0);
		BlasterHeat vented = BlasterRules.vent(warm, T0, kind, b);
		helper.assertTrue(BlasterRules.ventResult(warm, T0, kind, b) == BlasterRules.VentResult.VENT && vented != null,
				msg("at ventMin it vents"));
		helper.assertTrue(near(vented.heat(), 0) && vented.lockedUntil() == T0 + BlasterBalance.of(b.ventLock, kind) && vented.freeShots() == 0,
				msg("a vent empties the heat with the full vent lock and no free shots"));
		BlasterHeat overheated = new BlasterHeat(BlasterRules.MAX_HEAT, T0, T0 + 50, 0);
		helper.assertTrue(BlasterRules.vent(overheated, T0 + 1, kind, b) == null, msg("an overheated blaster cannot vent"));

		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, new ItemStack(EnemyItems.BLASTER_PISTOL));
			long now = helper.getLevel().getGameTime();
			BlasterHeat low = new BlasterHeat(b.ventMin - 5f, now, 0L, 0);
			gun.set(M4Components.BLASTER_HEAT, low);
			Blasters.action(player, true);
			Blasters.action(player, false);
			helper.assertTrue(Blasters.heat(gun).equals(low), msg("Weapon Action below ventMin leaves the heat alone: " + Blasters.heat(gun)));
			helper.assertTrue(Blasters.tryFire(player, gun, (BlasterItem) gun.getItem(), now), msg("and the pistol still fires"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void perfectVentHalvesLockAndFreeShots(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();

		for (BlasterItem.Kind kind : BlasterItem.Kind.values()) {
			int lock = BlasterBalance.of(b.ventLock, kind);
			float per = BlasterBalance.of(b.heatPerShot, kind);
			String k = kind + ": ";

			for (float heat : new float[] {b.perfectLow(), (b.perfectLow() + b.perfectHigh()) / 2f, b.perfectHigh()}) {
				helper.assertTrue(BlasterRules.ventResult(new BlasterHeat(heat, T0, 0L, 0), T0, kind, b) == BlasterRules.VentResult.PERFECT,
						msg(k + heat + " is inside the perfect band"));
			}

			if (b.perfectHigh() + 1f < BlasterRules.MAX_HEAT) {
				BlasterHeat hot = BlasterRules.vent(new BlasterHeat(b.perfectHigh() + 1f, T0, 0L, 0), T0, kind, b);
				helper.assertTrue(hot != null && hot.lockedUntil() == T0 + lock && hot.freeShots() == 0, msg(k + "above the band is a plain vent"));
			}

			BlasterHeat perfect = BlasterRules.vent(new BlasterHeat(b.perfectLow(), T0, 0L, 0), T0, kind, b);
			helper.assertTrue(perfect != null && perfect.lockedUntil() == T0 + lock / 2 && perfect.freeShots() == b.perfectVentFreeShots,
					msg(k + "a perfect vent halves the lock and grants the free shots"));

			// The free shots add nothing; the next one heats as usual.
			BlasterHeat s = perfect;
			long now = perfect.lockedUntil();

			for (int i = 0; i < b.perfectVentFreeShots; i++) {
				s = BlasterRules.afterShot(s, now + i, per, kind, b);
				helper.assertTrue(near(BlasterRules.heatAt(s, now + i, kind, b), 0), msg(k + "free shot " + (i + 1) + " adds no heat"));
			}

			s = BlasterRules.afterShot(s, now + b.perfectVentFreeShots, per, kind, b);
			helper.assertTrue(near(BlasterRules.heatAt(s, now + b.perfectVentFreeShots, kind, b), per) && s.freeShots() == 0,
					msg(k + "after the free shots a shot heats again"));
		}

		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, new ItemStack(EnemyItems.BLASTER_PISTOL));
			BlasterItem item = (BlasterItem) gun.getItem();
			long now = helper.getLevel().getGameTime();
			gun.set(M4Components.BLASTER_HEAT, new BlasterHeat(b.perfectLow() + 5f, now, 0L, 0));
			Blasters.action(player, true);
			Blasters.action(player, false);
			BlasterHeat after = Blasters.heat(gun);
			long until = now + BlasterBalance.of(b.ventLock, item.kind()) / 2;
			helper.assertTrue(near(after.heat(), 0) && after.lockedUntil() == until && after.freeShots() == b.perfectVentFreeShots,
					msg("Weapon Action in the band vents perfectly: " + after));
			helper.assertTrue(!Blasters.tryFire(player, gun, item, until - 1), msg("no shot during the halved lock"));
			helper.assertTrue(Blasters.tryFire(player, gun, item, until), msg("a shot when it ends"));
			helper.assertTrue(near(Blasters.heat(gun).heat(), 0) && Blasters.heat(gun).freeShots() == b.perfectVentFreeShots - 1,
					msg("which spends a free shot: " + Blasters.heat(gun)));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chargedShotIsHeavy(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();
		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, new ItemStack(EnemyItems.BLASTER_RIFLE));
			BlasterItem item = (BlasterItem) gun.getItem();
			long now = helper.getLevel().getGameTime();
			helper.assertTrue(!BlasterRules.chargeLoaded(now - b.chargedTicks + 1, now, b) && BlasterRules.chargeLoaded(now - b.chargedTicks, now, b)
					&& !BlasterRules.chargeLoaded(-1, now, b), msg("the charge loads after chargedTicks"));

			// A tap of Weapon Action is a vent.
			gun.set(M4Components.BLASTER_HEAT, new BlasterHeat(b.ventMin + 10f, now, 0L, 0));
			Blasters.action(player, true);
			Blasters.action(player, false);
			helper.assertTrue(near(Blasters.heat(gun).heat(), 0) && BlasterRules.locked(Blasters.heat(gun), now) && !Blasters.loaded(player),
					msg("a tap vents the rifle"));
			gun.set(M4Components.BLASTER_HEAT, BlasterHeat.COLD);

			// Holding it loads the heavy bolt; the trigger waits while the charge builds.
			Blasters.action(player, true);
			helper.assertTrue(!Blasters.tryFire(player, gun, item, now), msg("no shot while the charge is held"));
			Blasters.chargeSince(player, b.chargedTicks - 1);
			Blasters.tickFor(player);
			helper.assertTrue(!Blasters.loaded(player), msg("not loaded one tick early"));
			Blasters.chargeSince(player, b.chargedTicks);
			Blasters.tickFor(player);
			helper.assertTrue(Blasters.loaded(player), msg("loaded after chargedTicks"));
			Blasters.action(player, false);
			helper.assertTrue(Blasters.loaded(player) && near(Blasters.heat(gun).heat(), 0), msg("letting go after loading keeps the bolt and does not vent"));

			helper.assertTrue(Blasters.tryFire(player, gun, item, now), msg("the loaded rifle fires"));
			BlasterBolt heavy = onlyBolt(helper, player);
			float quality = Crafting.quality(gun).weapon();
			helper.assertTrue(heavy.heavy() && heavy.style() == BlasterBolt.Style.PLAYER, msg("the charged shot is a heavy player bolt"));
			helper.assertTrue(near(heavy.damage(), b.chargedDamage * quality), msg("charged damage " + b.chargedDamage + ", got " + heavy.damage()));
			helper.assertTrue(near(Blasters.heat(gun).heat(), b.chargedHeat), msg("charged heat " + b.chargedHeat + ", got " + Blasters.heat(gun).heat()));
			helper.assertTrue(!Blasters.loaded(player), msg("the heavy bolt is spent"));
			heavy.discard();

			int rate = BlasterBalance.of(b.rate, item.kind());
			helper.assertTrue(Blasters.tryFire(player, gun, item, now + rate), msg("the next shot at the rate"));
			BlasterBolt plain = onlyBolt(helper, player);
			helper.assertTrue(!plain.heavy() && near(plain.damage(), BlasterRules.boltDamage(item.kind(), quality, b)), msg("and it is a standard bolt"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void boltStylePlayerAndAllyPass(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlasterBalance b = BlasterBalance.get();
		ServerPlayer player = player(helper);
		ServerPlayer other = M3TestPlayers.listed(helper, new BlockPos(4, 1, 1));
		Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 4));
		TrooperEntity trooper = EnemyEntities.REPUBLIC_TROOPER.create(level, EntitySpawnReason.LOAD);

		try {
			helper.assertTrue(trooper != null, msg("a Republic trooper can be made"));
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 4)));
			trooper.snapTo(at.x, at.y, at.z);

			ItemStack gun = drawn(player, new ItemStack(EnemyItems.BLASTER_PISTOL));
			BlasterItem item = (BlasterItem) gun.getItem();
			helper.assertTrue(Blasters.tryFire(player, gun, item, level.getGameTime()), msg("the pistol fires"));
			BlasterBolt bolt = onlyBolt(helper, player);
			helper.assertTrue(bolt.style() == BlasterBolt.Style.PLAYER && !bolt.heavy(), msg("a player's bolt has the PLAYER style"));
			helper.assertTrue(near(bolt.damage(), BlasterBalance.of(b.damage, item.kind())), msg("at the pistol's damage"));
			helper.assertTrue(PlayerBolts.isPlayerBlaster(boltSource(level, bolt, player)), msg("its damage counts as a player's blaster"));
			BlasterBolt poacher = BlasterBolt.create(level, zombie, zombie.position(), 4f, 60);
			helper.assertTrue(!PlayerBolts.isPlayerBlaster(boltSource(level, poacher, zombie)), msg("a poacher bolt does not"));
			helper.assertTrue(!PlayerBolts.isPlayerBlaster(level.damageSources().playerAttack(player)), msg("nor does a punch"));

			helper.assertTrue(!PlayerBolts.passesThrough(player, player), msg("never through the shooter itself"));
			helper.assertTrue(!PlayerBolts.passesThrough(player, zombie), msg("a hostile mob is hit"));
			helper.assertTrue(!PlayerBolts.passesThrough(player, other), msg("another player is left to the PvP rules"));

			Identity.setReputation(player, Faction.REPUBLIC, Identity.balance().repTrusted);
			helper.assertTrue(Attitudes.toward(trooper, player) == Attitude.ALLIED, msg("precondition: a Trusted player's trooper is Allied"));
			helper.assertTrue(PlayerBolts.passesThrough(player, trooper), msg("bolts pass through an Allied trooper"));
			Identity.setReputation(player, Faction.REPUBLIC, 0);
			helper.assertTrue(!PlayerBolts.passesThrough(player, trooper), msg("but not a Neutral one"));
		} finally {
			if (trooper != null) {
				trooper.discard();
			}

			zombie.discard();
			M3TestPlayers.remove(other);
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void steadySpreadAndMoveMultipliers(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();
		ModNumbers m = EquipmentM4Balance.mods();

		for (BlasterItem.Kind kind : BlasterItem.Kind.values()) {
			float hip = BlasterBalance.of(b.hipSpread, kind);
			String k = kind + ": ";
			helper.assertTrue(near(BlasterRules.spread(hip, false, false, false, 1f, b), hip), msg(k + "hip fire"));
			helper.assertTrue(near(BlasterRules.spread(hip, true, false, false, 1f, b), hip * b.steadyMultiplier), msg(k + "steadied"));
			helper.assertTrue(near(BlasterRules.spread(hip, false, true, false, 1f, b), hip * b.moveMultiplier), msg(k + "moving fast"));
			helper.assertTrue(near(BlasterRules.spread(hip, false, false, true, 1f, b), hip * b.airMultiplier), msg(k + "airborne"));
			helper.assertTrue(near(BlasterRules.spread(hip, true, true, true, 1f, b), hip * b.steadyMultiplier * b.moveMultiplier * b.airMultiplier),
					msg(k + "they multiply"));
		}

		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, new ItemStack(EnemyItems.BLASTER_RIFLE));
			Mods.fit(gun, ModSlot.BARREL, Crafting.withQuality(new ItemStack(M4Items.MOD_FOCUSED_BARREL), Quality.MASTERWORK));
			helper.assertTrue(near(ModEffects.spread(gun, m), ratio(m.focusedSpreadPct[Quality.MASTERWORK.ordinal()])),
					msg("a Masterwork focused barrel narrows the spread by its percentage"));

			helper.assertTrue(!Blasters.steadied(player) && !Blasters.fast(player), msg("standing still: neither steadied nor fast"));
			player.setShiftKeyDown(true);
			helper.assertTrue(Blasters.steadied(player), msg("sneaking on the ground steadies"));
			player.setOnGround(false);
			helper.assertTrue(!Blasters.steadied(player), msg("sneaking in the air does not"));
			player.setOnGround(true);
			player.setShiftKeyDown(false);
			player.setSprinting(true);
			helper.assertTrue(Blasters.fast(player), msg("sprinting is moving fast"));
			player.setSprinting(false);

			// Seeded cone samples stay inside the half-angle; the steadied cone is the narrower one.
			BlasterItem.Kind kind = BlasterItem.Kind.RIFLE;
			float hip = BlasterRules.spread(BlasterBalance.of(b.hipSpread, kind), false, false, false, 1f, b);
			float steady = BlasterRules.spread(BlasterBalance.of(b.hipSpread, kind), true, false, false, 1f, b);
			double widestHip = widest(player, hip, RandomSource.create(4242L));
			double widestSteady = widest(player, steady, RandomSource.create(4242L));
			helper.assertTrue(widestHip <= hip + EPS && widestSteady <= steady + EPS, msg("every shot inside its cone: " + widestHip + " / " + widestSteady));
			helper.assertTrue(widestSteady < widestHip, msg("steadying narrows the shots"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	/** The widest angle from the look, degrees, among 64 seeded cone samples. */
	private static double widest(ServerPlayer player, float degrees, RandomSource random) {
		Vec3 look = player.getLookAngle().normalize();
		double widest = 0;

		for (int i = 0; i < 64; i++) {
			Vec3 dir = Blasters.cone(player, degrees, random).normalize();
			widest = Math.max(widest, Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(dir))))));
		}

		return widest;
	}

	@GameTest(maxTicks = 20)
	public void repeaterSpinUpAndSuppression(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();
		helper.assertTrue(!BlasterRules.spunUp(-1, T0, b) && !BlasterRules.spunUp(T0, T0 + b.spinUp - 1, b) && BlasterRules.spunUp(T0, T0 + b.spinUp, b),
				msg("the barrel is up to speed after spinUp ticks"));
		long[] three = new long[b.suppressHits - 1];
		long[] all = new long[b.suppressHits];
		long[] stale = new long[b.suppressHits];

		for (int i = 0; i < all.length; i++) {
			all[i] = T0 + i;
			stale[i] = i == 0 ? T0 - b.suppressWindow : T0 + i;

			if (i < three.length) {
				three[i] = T0 + i;
			}
		}

		long last = T0 + b.suppressHits - 1;
		helper.assertTrue(!BlasterRules.suppressed(three, last, b) && BlasterRules.suppressed(all, last, b) && !BlasterRules.suppressed(stale, last, b),
				msg("suppressHits hits inside the window suppress, fewer or older do not"));

		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 1, 5));

		try {
			ItemStack gun = drawn(player, new ItemStack(GearItems.HEAVY_REPEATER));
			BlasterItem item = (BlasterItem) gun.getItem();
			long now = level.getGameTime();
			helper.assertTrue(!Blasters.tryFire(player, gun, item, now) && Blasters.spinStart(player) == now, msg("the first pull only spins the barrel"));
			helper.assertTrue(!Blasters.tryFire(player, gun, item, now + b.spinUp - 1), msg("no bolt before spinUp"));
			helper.assertTrue(Blasters.tryFire(player, gun, item, now + b.spinUp), msg("bolts once it is spun up"));
			BlasterBolt bolt = onlyBolt(helper, player);

			// The same bolt's hits on one mob, as its flight would land them.
			for (int hit = 1; hit <= b.suppressHits; hit++) {
				pig.invulnerableTime = 0;
				pig.hurtServer(level, boltSource(level, bolt, player), 0.25f);

				if (hit < b.suppressHits) {
					helper.assertTrue(!pig.hasEffect(MobEffects.SLOWNESS), msg(hit + " hits do not suppress yet"));
				}
			}

			helper.assertTrue(pig.hasEffect(MobEffects.SLOWNESS), msg(b.suppressHits + " repeater hits suppress (slow) the mob"));
			helper.assertTrue(bolts(player).size() > 1, msg("the repeater's bolt pierces on through what it hits"));
		} finally {
			pig.discard();
			done(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- vibroweapons and the electrostaff (5.2, 5.3)

	@GameTest(maxTicks = 20)
	public void vibroGuardReducesFrontMeleeOnly(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();

		for (VibroWeaponItem.Kind kind : VibroWeaponItem.Kind.values()) {
			// The Milestone 7 kinds (the vibro-axe and the force pike) have their own guards, tested in MeleeM7GameTests.
			if (kind != VibroWeaponItem.Kind.BLADE && kind != VibroWeaponItem.Kind.SWORD && kind != VibroWeaponItem.Kind.STAFF) {
				continue;
			}
			float pct = switch (kind) {
				case BLADE -> b.guardBladePct;
				case SWORD -> b.guardSwordPct;
				default -> b.guardStaffPct;
			};
			helper.assertTrue(near(MeleeRules.guardThrough(kind, b), 1.0 - pct / 100.0), msg(kind + " guard lets " + (100 - pct) + "% through"));
		}

		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob front = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 3));
		Mob behind = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 0));

		try {
			hold(player, new ItemStack(EnemyItems.VIBROBLADE));
			DamageSource fromFront = level.damageSources().mobAttack(front);
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, fromFront, 4f), 1), msg("no guard raised: everything goes through"));

			player.startUsingItem(InteractionHand.MAIN_HAND);
			helper.assertTrue(player.isUsingItem(), msg("precondition: the guard is up"));
			float full = MeleeCombat.stamina(player);
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, fromFront, 4f), MeleeRules.guardThrough(VibroWeaponItem.Kind.BLADE, b)),
					msg("a front blow is cut to the blade's share"));
			helper.assertTrue(MeleeCombat.stamina(player) < full, msg("and drains the guard"));
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, level.damageSources().mobAttack(behind), 4f), 1), msg("a blow from behind is not guarded"));

			BlasterBolt bolt = BlasterBolt.create(level, front, front.getEyePosition(), 4f, 60);
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, boltSource(level, bolt, front), 4f), 1), msg("a vibroweapon never blocks a bolt"));
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, level.damageSources().magic(), 4f), 1), msg("nor anything that is not a blow"));
		} finally {
			player.stopUsingItem();
			front.discard();
			behind.discard();
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void flurryEveryThirdHit(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		int chain = 0;
		long last = Long.MIN_VALUE / 2;

		for (int hit = 1; hit <= b.flurryHits * 2; hit++) {
			long now = T0 + hit * 10L;
			chain = MeleeRules.nextChain(chain, last, now, b);
			last = now;
			int expected = (hit - 1) % b.flurryHits + 1;
			helper.assertTrue(chain == expected && MeleeRules.isFlurry(chain, b) == (expected == b.flurryHits),
					msg("hit " + hit + " is link " + expected + " of the chain, got " + chain));
		}

		helper.assertTrue(MeleeRules.nextChain(1, T0, T0 + b.flurryWindow + 1, b) == 1 && MeleeRules.nextChain(1, T0, T0 + b.flurryWindow, b) == 2,
				msg("a gap longer than flurryWindow starts a new chain"));

		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		List<Mob> pigs = new ArrayList<>();

		for (int i = 0; i < b.flurryHits + 1; i++) {
			pigs.add(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(i * 2, 1, 3)));
		}

		Runnable cleanUp = () -> {
			pigs.forEach(Entity::discard);
			done(player);
		};

		try {
			hold(player, new ItemStack(EnemyItems.VIBROBLADE));
			float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

			// The bonus the item adds, swing by swing.
			for (int hit = 1; hit <= b.flurryHits; hit++) {
				Mob pig = pigs.get(hit - 1);
				MeleeCombat.swing(player, VibroWeaponItem.Kind.BLADE, pig.getId(), 1f, level.getGameTime());
				float bonus = MeleeCombat.attackBonus(player, pig, VibroWeaponItem.Kind.BLADE);
				helper.assertTrue(near(bonus, hit == b.flurryHits ? b.flurryBonus : 0f), msg("swing " + hit + " bonus " + bonus));
			}

			helper.assertTrue(near(MeleeCombat.attackBonus(player, pigs.get(b.flurryHits), VibroWeaponItem.Kind.BLADE), 0),
					msg("the bonus belongs to the swing's own target"));

			// Real hits, one pig each: the third takes the bonus and leaves the attack charged.
			for (int hit = 1; hit <= b.flurryHits; hit++) {
				Mob pig = pigs.get(hit - 1);
				pig.setHealth(pig.getMaxHealth());
				strike(player, pig, VibroWeaponItem.Kind.BLADE);
				float expected = Math.min(pig.getMaxHealth(), damage + (hit == b.flurryHits ? b.flurryBonus : 0f));
				helper.assertTrue(near(pig.getMaxHealth() - pig.getHealth(), expected),
						msg("hit " + hit + " deals " + expected + ", got " + (pig.getMaxHealth() - pig.getHealth())));
			}
		} catch (RuntimeException | Error e) {
			cleanUp.run();
			throw e;
		}

		// The flurry hit's cooldown is restored at the end of its tick.
		helper.runAfterDelay(1, () -> {
			try {
				helper.assertTrue(player.getAttackStrengthScale(0.5f) > 0.99f, msg("after the flurry hit the attack is charged again"));
				strike(player, pigs.get(b.flurryHits), VibroWeaponItem.Kind.BLADE);
			} catch (RuntimeException | Error e) {
				cleanUp.run();
				throw e;
			}
		});
		helper.runAfterDelay(2, () -> {
			try {
				helper.assertTrue(player.getAttackStrengthScale(0.5f) < 0.5f, msg("an ordinary hit (a new chain) leaves the cooldown as it is"));
				helper.succeed();
			} finally {
				cleanUp.run();
			}
		});
	}

	@GameTest(maxTicks = 20)
	public void cleaveHitsTwoMore(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		helper.assertTrue(!MeleeRules.fullyCharged(b.cleaveMinStrength - 0.01f, b) && MeleeRules.fullyCharged(b.cleaveMinStrength, b),
				msg("only a fully charged swing cleaves"));
		helper.assertTrue(near(MeleeRules.cleaveDamage(10f, b), 10f * b.cleaveMultiplier), msg("cleave hits deal cleaveMultiplier of the swing"));

		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob struck = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 1, 4));
		Mob left = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(1, 1, 2));
		Mob right = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 2));
		Mob farther = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 1, 3));
		Mob behind = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 1, 0));
		List<Mob> all = List.of(struck, left, right, farther, behind);

		try {
			hold(player, new ItemStack(GearItems.VIBROSWORD));
			int hit = MeleeCombat.cleave(player, level, struck, b);
			helper.assertTrue(hit == Math.min(2, b.cleaveTargets), msg("a cleave hits cleaveTargets more, got " + hit));
			helper.assertTrue(left.getHealth() < left.getMaxHealth() && right.getHealth() < right.getMaxHealth(), msg("the two nearest in front are hit"));
			helper.assertTrue(b.cleaveTargets > 2 || farther.getHealth() == farther.getMaxHealth(), msg("a third enemy in front is left alone"));
			helper.assertTrue(behind.getHealth() == behind.getMaxHealth() && struck.getHealth() == struck.getMaxHealth(),
					msg("nothing behind is hit, and the struck one is not hit twice"));
			float expected = MeleeRules.cleaveDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), b);
			helper.assertTrue(near(left.getMaxHealth() - left.getHealth(), Math.min(left.getMaxHealth(), expected)),
					msg("each extra hit deals " + expected + ", got " + (left.getMaxHealth() - left.getHealth())));
		} finally {
			all.forEach(Entity::discard);
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void electrostaffBlocksSaberWithoutDurability(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob duelist = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 3));

		try {
			ItemStack saber = new ItemStack(SaberItems.LIGHTSABER);
			SaberToggle.setIgnited(saber, true, level.getGameTime());
			duelist.setItemSlot(EquipmentSlot.MAINHAND, saber);
			DamageSource blow = level.damageSources().mobAttack(duelist);

			ItemStack staff = hold(player, new ItemStack(GearItems.ELECTROSTAFF));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, blow, 6f), MeleeRules.guardThrough(VibroWeaponItem.Kind.STAFF, b)),
					msg("the staff guards a saber blow"));
			helper.assertTrue(staff.getDamageValue() == 0, msg("and its emitters lose no durability: " + staff.getDamageValue()));
			player.stopUsingItem();

			ItemStack blade = hold(player, new ItemStack(EnemyItems.VIBROBLADE));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, blow, 6f), MeleeRules.guardThrough(VibroWeaponItem.Kind.BLADE, b)),
					msg("a vibroblade guards it too"));
			helper.assertTrue(blade.getDamageValue() == b.saberNotch, msg("but notches by saberNotch: " + blade.getDamageValue()));
		} finally {
			player.stopUsingItem();
			duelist.discard();
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void qualityMultipliesDamage(GameTestHelper helper) {
		BlasterBalance b = BlasterBalance.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);

		try {
			for (Quality q : Quality.values()) {
				ItemStack gun = drawn(player, Crafting.applyQuality(new ItemStack(EnemyItems.BLASTER_PISTOL), q));
				BlasterItem item = (BlasterItem) gun.getItem();
				helper.assertTrue(Blasters.tryFire(player, gun, item, level.getGameTime()), msg(q + " pistol fires"));
				BlasterBolt bolt = onlyBolt(helper, player);
				float expected = BlasterBalance.of(b.damage, item.kind()) * q.weapon();
				helper.assertTrue(near(bolt.damage(), expected), msg(q + " bolt deals " + expected + ", got " + bolt.damage()));
				bolt.discard();
			}

			MeleeBalance registered = new MeleeBalance();

			for (Quality q : Quality.values()) {
				hold(player, Crafting.applyQuality(new ItemStack(EnemyItems.VIBROBLADE), q));
				double expected = registered.vibrobladeDamage * q.weapon();
				helper.assertTrue(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), expected),
						msg(q + " vibroblade hits for " + expected + ", got " + player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
			}

			ItemStack longshot = new ItemStack(GearItems.KAAST_LONGSHOT);
			ModNumbers m = EquipmentM4Balance.mods();
			helper.assertTrue(Crafting.quality(longshot) == Quality.MASTERWORK
					&& near(ModEffects.boltDamage(longshot, m), ratio(m.heavyDamagePct[Quality.MASTERWORK.ordinal()])),
					msg("Kaast's Longshot is Masterwork with its fixed heavy barrel at that quality"));
			ItemAttributeModifiers modifiers = Crafting.applyQuality(new ItemStack(GearItems.VIBROSWORD), Quality.FINE)
					.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			double swordDamage = 1.0;

			for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
				if (entry.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
					swordDamage += entry.modifier().amount();
				}
			}

			helper.assertTrue(near(swordDamage, registered.vibroswordDamage * Quality.FINE.weapon()), msg("a Fine vibrosword's damage modifier, got " + swordDamage));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- armour (6.1, 6.2)

	@GameTest(maxTicks = 20)
	public void setBonusWornCountsEveryOrdinal(GameTestHelper helper) {
		ArmourSet[] sets = ArmourSet.values();
		String[] order = {"republic_trooper", "sith_trooper", "jedi_robes", "sith_robes", "watch_battle", "sith_war", "mandalorian", "spacer"};
		helper.assertTrue(sets.length >= order.length, msg("the eight sets exist"));

		for (int i = 0; i < order.length; i++) {
			helper.assertTrue(sets[i].id().equals(order[i]) && sets[i].isM4() == (i >= 4) && ArmourSet.byIndex(i) == sets[i],
					msg("append only: ordinal " + i + " is " + order[i]));
		}

		for (ArmourSet set : sets) {
			SetBonusRules.Worn worn = SetBonusRules.Worn.NONE;

			for (int pieces = 1; pieces <= SetBonusRules.FULL; pieces++) {
				worn = worn.plus(set);
				helper.assertTrue(worn.count(set) == pieces && worn.full(set) == (pieces == SetBonusRules.FULL), msg(set + ": " + pieces + " counted"));

				for (ArmourSet other : sets) {
					helper.assertTrue(other == set || worn.count(other) == 0, msg(set + " pieces leave " + other + " at 0"));
				}
			}
		}

		SetBonusRules.Worn m3 = new SetBonusRules.Worn(1, 2, 3, 4);
		helper.assertTrue(m3.republicTrooper() == 1 && m3.sithTrooper() == 2 && m3.jediRobes() == 3 && m3.sithRobes() == 4,
				msg("the four-count constructor feeds the M3 accessors"));
		helper.assertTrue(m3.count(ArmourSet.WATCH_BATTLE) == 0 && m3.count(ArmourSet.SPACER) == 0, msg("and leaves the M4 sets at 0"));
		SetBonusRules.Worn built = SetBonusRules.Worn.NONE.plus(ArmourSet.REPUBLIC_TROOPER).plus(ArmourSet.SITH_TROOPER).plus(ArmourSet.SITH_TROOPER)
				.plus(ArmourSet.JEDI_ROBES).plus(ArmourSet.JEDI_ROBES).plus(ArmourSet.JEDI_ROBES)
				.plus(ArmourSet.SITH_ROBES).plus(ArmourSet.SITH_ROBES).plus(ArmourSet.SITH_ROBES).plus(ArmourSet.SITH_ROBES);
		helper.assertTrue(built.equals(m3) && built.hashCode() == m3.hashCode(), msg("counts compare by value"));
		helper.assertTrue(!SetBonusRules.Worn.NONE.plus(ArmourSet.SPACER).equals(SetBonusRules.Worn.NONE), msg("an M4 piece makes a difference"));

		ServerPlayer player = player(helper);

		try {
			for (ArmourSet set : sets) {
				wearSet(player, set, SetBonusRules.FULL);
				SetBonusRules.Worn worn = ArmourSets.worn(player);
				helper.assertTrue(worn.count(set) == SetBonusRules.FULL && ArmourSets.fullSet(player, set), msg("wearing " + set + ": " + worn));
			}

			strip(player);
			wear(player, ArmourSets.piece(ArmourSet.WATCH_BATTLE, EquipmentSlot.HEAD));
			wear(player, ArmourSets.piece(ArmourSet.WATCH_BATTLE, EquipmentSlot.CHEST));
			wear(player, ArmourSets.piece(ArmourSet.SPACER, EquipmentSlot.LEGS));
			wear(player, ArmourSets.piece(ArmourSet.SPACER, EquipmentSlot.FEET));
			SetBonusRules.Worn mixed = ArmourSets.worn(player);
			helper.assertTrue(mixed.count(ArmourSet.WATCH_BATTLE) == 2 && mixed.count(ArmourSet.SPACER) == 2
					&& !mixed.full(ArmourSet.WATCH_BATTLE) && !mixed.full(ArmourSet.SPACER), msg("2 + 2 of two M4 sets is no set: " + mixed));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void newSetsTraitsAndBonuses(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EquipmentBalance eb = EquipmentBalance.get();
		EquipmentM4Balance m = EquipmentM4Balance.get();
		ModNumbers mods = EquipmentM4Balance.mods();
		EquipmentModifiers modifiers = new EquipmentModifiers();
		ServerPlayer player = player(helper);
		Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 3));

		try {
			DamageSource melee = level.damageSources().mobAttack(zombie);
			BlasterBolt bolt = BlasterBolt.create(level, zombie, zombie.getEyePosition(), 4f, 60);
			DamageSource blaster = boltSource(level, bolt, zombie);
			DamageSource fall = level.damageSources().fall();

			// Watch battle armour: cheaper powers, melee resistance; Warden's Resolve after a parry, light gate.
			identity(player, 0, Path.JEDI);
			wearSet(player, ArmourSet.WATCH_BATTLE, SetBonusRules.FULL);
			double watchCost = pow(ratio(m.watchCostPct), 4);
			double watchMelee = pow(ratio(m.watchMeleePct), 4);
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), watchCost), msg("Watch: cost per piece"));
			helper.assertTrue(near(modifiers.modify(player, melee, 10f), 10 * watchMelee), msg("Watch: melee per piece"));
			SetBonuses.parried(player);
			helper.assertTrue(!SetBonuses.warden(player), msg("Warden's Resolve needs the light gate"));
			identity(player, eb.setAlignment, Path.JEDI);
			helper.assertTrue(SetBonuses.warden(player), msg("Warden's Resolve after a parry at +" + eb.setAlignment));
			helper.assertTrue(near(modifiers.costMultiplier(player, SetBonusRules.BARRIER), watchCost * ratio(m.watchSetBarrierCostPct)),
					msg("Warden: Barrier cheaper"));
			helper.assertTrue(near(modifiers.modify(player, melee, 10f), 10 * watchMelee * ratio(m.watchSetParryResistPct)), msg("Warden: melee resist"));
			identity(player, 0, Path.INDEPENDENT);
			helper.assertTrue(SetBonuses.warden(player), msg("a Balanced Independent passes the gate"));
			identity(player, -eb.setAlignment, Path.SITH);
			helper.assertTrue(!SetBonuses.warden(player), msg("a dark wearer does not"));
			identity(player, eb.setAlignment, Path.JEDI);
			wearSet(player, ArmourSet.WATCH_BATTLE, 3);
			helper.assertTrue(!SetBonuses.warden(player), msg("3/4 is no Warden"));

			// Quality strengthens a trait that helps (a Masterwork helmet), never a drawback.
			wearSet(player, ArmourSet.WATCH_BATTLE, SetBonusRules.FULL);
			wear(player, Crafting.applyQuality(ArmourSets.piece(ArmourSet.WATCH_BATTLE, EquipmentSlot.HEAD), Quality.MASTERWORK));
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), pow(ratio(m.watchCostPct), 3) * ratio(m.watchCostPct * Quality.MASTERWORK.armour())),
					msg("a Masterwork piece's trait is scaled by its quality"));

			// Sith war armour: max energy, slower regen, melee resistance; Wrath Unbound below half health, dark gate.
			identity(player, 0, Path.SITH);
			wearSet(player, ArmourSet.SITH_WAR, SetBonusRules.FULL);
			helper.assertTrue(modifiers.maxEnergyBonus(player) == 4 * m.sithWarMaxEnergy, msg("Sith war: max energy per piece"));
			helper.assertTrue(near(modifiers.regenMultiplier(player), pow(ratio(m.sithWarRegenPct), 4)), msg("Sith war: regen per piece"));
			helper.assertTrue(near(modifiers.modify(player, melee, 10f), 10 * pow(ratio(m.sithWarMeleePct), 4)), msg("Sith war: melee per piece"));
			player.setHealth(player.getMaxHealth() * m.sithWarSetHealthFraction - 1f);
			helper.assertTrue(!SetBonuses.wrath(player), msg("Wrath Unbound needs the dark gate"));
			identity(player, -eb.setAlignment, Path.SITH);
			helper.assertTrue(SetBonuses.wrath(player), msg("Wrath Unbound below half health at -" + eb.setAlignment));
			helper.assertTrue(near(modifiers.costMultiplier(player, SetBonusRules.LIGHTNING), ratio(m.sithWarSetCostPct))
					&& near(modifiers.costMultiplier(player, SetBonusRules.CHOKE), ratio(m.sithWarSetCostPct))
					&& near(modifiers.costMultiplier(player, "force_push"), 1), msg("Wrath: Lightning and Choke cheaper, nothing else"));
			DamageSource lightning = level.damageSources().source(ForceDamage.LIGHTNING, player, player);
			helper.assertTrue(near(modifiers.modify(zombie, lightning, 10f), 10 * ratio(m.sithWarSetDamagePct)), msg("Wrath: Lightning hits harder"));
			player.setHealth(player.getMaxHealth());
			helper.assertTrue(!SetBonuses.wrath(player) && near(modifiers.modify(zombie, lightning, 10f), 10), msg("no Wrath at full health"));

			// Mandalorian armour: blaster resistance, dearer powers; Hunter's Tenacity, no gate.
			identity(player, eb.setAlignment, Path.JEDI);
			wearSet(player, ArmourSet.MANDALORIAN, SetBonusRules.FULL);
			double mandoCost = pow(ratio(m.mandoCostPct), 4);
			helper.assertTrue(near(modifiers.modify(player, blaster, 10f), 10 * pow(ratio(m.mandoBlasterPct), 4)), msg("Mandalorian: blaster per piece"));
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), mandoCost), msg("Mandalorian: cost per piece"));
			helper.assertTrue(near(modifiers.costMultiplier(player, SetBonusRules.SPEED), mandoCost * ratio(m.mandoSetSpeedCostPct)), msg("Hunter: Speed dearer"));
			helper.assertTrue(near(modifiers.multiplier(player, ControlEffects.STAGGER), Math.max(m.controlMin, ratio(m.mandoSetControlPct))),
					msg("Hunter: shorter control"));
			helper.assertTrue(JetPack.maxCharges(player, mods) == mods.jetCharges + m.mandoSetJetCharges, msg("Hunter: one more jet charge"));
			ItemStack masterHelmet = Crafting.applyQuality(ArmourSets.piece(ArmourSet.MANDALORIAN, EquipmentSlot.HEAD), Quality.MASTERWORK);
			wear(player, masterHelmet);
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), mandoCost), msg("a Masterwork piece keeps a drawback as it is"));
			wearSet(player, ArmourSet.MANDALORIAN, 3);
			helper.assertTrue(near(modifiers.multiplier(player, ControlEffects.STAGGER), 1) && JetPack.maxCharges(player, mods) == mods.jetCharges,
					msg("3/4 is no Hunter's Tenacity"));

			// Spacer's gear: regen, lighter falls; Light on Your Feet.
			wearSet(player, ArmourSet.SPACER, SetBonusRules.FULL);
			helper.assertTrue(near(modifiers.regenMultiplier(player), pow(ratio(m.spacerRegenPct), 4)), msg("Spacer: regen per piece"));
			helper.assertTrue(near(modifiers.modify(player, fall, 10f), 10 * pow(ratio(m.spacerFallPct), 4) * ratio(m.spacerSetFallPct)),
					msg("Spacer: falls per piece and the set's further cut"));
			helper.assertTrue(near(SetBonuses.forceSpeedDurationMultiplier(player), ratio(m.spacerSetSpeedDurationPct)), msg("Light on Your Feet: longer Speed"));
			helper.assertTrue(near(SetBonusRules.terrainEfficiency(ArmourSets.worn(player), m), Math.min(1f, m.spacerSetTerrainEfficiency)),
					msg("Light on Your Feet: sure footing"));
			wearSet(player, ArmourSet.SPACER, 3);
			helper.assertTrue(near(SetBonuses.forceSpeedDurationMultiplier(player), 1) && near(modifiers.modify(player, fall, 10f), 10 * pow(ratio(m.spacerFallPct), 3)),
					msg("3/4 is no Light on Your Feet"));

			// The M3 robe gates are unchanged by the new sets.
			SetBonusRules.Wearer light = new SetBonusRules.Wearer(eb.setAlignment, Identity.band(player), Path.JEDI);
			helper.assertTrue(SetBonusRules.active(ArmourSet.JEDI_ROBES, SetBonusRules.Worn.NONE.plus(ArmourSet.JEDI_ROBES).plus(ArmourSet.JEDI_ROBES)
					.plus(ArmourSet.JEDI_ROBES).plus(ArmourSet.JEDI_ROBES), light, eb), msg("Calm of the Watch still opens at the light gate"));
		} finally {
			player.setHealth(player.getMaxHealth());
			zombie.discard();
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void extraTraitsDecodeAbsent(GameTestHelper helper) {
		JsonObject old = new JsonObject();
		old.addProperty("set", "watch_battle");
		old.addProperty("cost_pct", -0.03f);
		EquipmentTraits parsed = EquipmentTraits.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(parsed.set() == ArmourSet.WATCH_BATTLE && near(parsed.costPct(), -0.03) && parsed.extra().equals(EquipmentTraits.Extra.NONE),
				msg("traits without \"extra\" decode with Extra.NONE"));

		JsonObject partial = old.deepCopy();
		JsonObject extra = new JsonObject();
		extra.addProperty("fall_pct", -0.1f);
		partial.add("extra", extra);
		helper.assertTrue(EquipmentTraits.CODEC.parse(JsonOps.INSTANCE, partial).getOrThrow().extra().equals(new EquipmentTraits.Extra(0f, 0f, -0.1f)),
				msg("missing Extra fields default to 0"));

		EquipmentTraits m3 = EquipmentItems.defaultTraits(ArmourSet.SITH_ROBES);
		JsonElement m3Json = EquipmentTraits.CODEC.encodeStart(JsonOps.INSTANCE, m3).getOrThrow();
		helper.assertTrue(!m3Json.getAsJsonObject().has("extra"), msg("an M3 piece saves no \"extra\", as before M4: " + m3Json));

		List<String> problems = new ArrayList<>();

		for (ArmourItem item : EquipmentItems.M4_ARMOUR) {
			EquipmentTraits traits = new ItemStack(item).get(ModComponents.EQUIPMENT_TRAITS);

			if (traits == null || !traits.equals(EquipmentItems.defaultTraits(item.set())) || !item.set().isM4()) {
				problems.add(item + " carries " + traits);
				continue;
			}

			JsonElement json = EquipmentTraits.CODEC.encodeStart(JsonOps.INSTANCE, traits).getOrThrow();

			if (!EquipmentTraits.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(traits)) {
				problems.add(item + " JSON " + json);
			}

			ByteBuf buf = Unpooled.buffer();

			try {
				EquipmentTraits.STREAM_CODEC.encode(buf, traits);

				if (!EquipmentTraits.STREAM_CODEC.decode(buf).equals(traits)) {
					problems.add(item + " stream");
				}
			} finally {
				buf.release();
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("every M4 piece's traits survive both codecs: " + problems));
		EquipmentTraits spacer = EquipmentItems.defaultTraits(ArmourSet.SPACER);
		helper.assertTrue(spacer.extra().fallPct() < 0f && spacer.extra().speedPct() > 0f, msg("spacer's gear carries its speed and fall traits in Extra"));

		// A modded, Masterwork piece saved and loaded keeps traits, mods and quality.
		ItemStack chest = Crafting.applyQuality(new ItemStack(EquipmentItems.MANDALORIAN_CHEST), Quality.MASTERWORK);
		Mods.fit(chest, ModSlot.ARMOUR, Crafting.withQuality(new ItemStack(M4Items.MOD_PLATING), Quality.FINE));
		Mods.fit(chest, ModSlot.PACK, new ItemStack(M4Items.JET_PACK));
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, chest).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.is(EquipmentItems.MANDALORIAN_CHEST) && EquipmentItems.defaultTraits(ArmourSet.MANDALORIAN).equals(loaded.get(ModComponents.EQUIPMENT_TRAITS))
				&& Crafting.quality(loaded) == Quality.MASTERWORK, msg("a saved M4 piece keeps its traits and quality: " + saved));
		helper.assertTrue(Mods.fittedQuality(loaded, M4Items.MOD_PLATING) == Quality.FINE.ordinal() && Mods.has(loaded, M4Items.JET_PACK),
				msg("and its mods: " + Mods.fitted(loaded)));
		helper.succeed();
	}

	// ---------------------------------------------------------------- mods (6.3)

	@GameTest(maxTicks = 20)
	public void modSlotsPerGear(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<ModSlot> armour = List.of(ModSlot.ARMOUR);
		List<ModSlot> chest = List.of(ModSlot.ARMOUR, ModSlot.PACK);
		List<ArmourItem> pieces = new ArrayList<>(List.of(EquipmentItems.ALL));
		pieces.addAll(List.of(EquipmentItems.M4_ARMOUR));

		for (ArmourItem piece : pieces) {
			List<ModSlot> expected = piece.type() == ArmorType.CHESTPLATE ? chest : armour;

			if (!Mods.slotsOf(new ItemStack(piece)).equals(expected)) {
				problems.add(piece + " has " + Mods.slotsOf(new ItemStack(piece)));
			}
		}

		List<ModSlot> gun = List.of(ModSlot.BARREL, ModSlot.CELL);

		for (ItemStack blaster : List.of(new ItemStack(EnemyItems.BLASTER_PISTOL), new ItemStack(EnemyItems.BLASTER_RIFLE), new ItemStack(GearItems.HEAVY_REPEATER))) {
			if (!Mods.slotsOf(blaster).equals(gun)) {
				problems.add(blaster + " has " + Mods.slotsOf(blaster));
			}
		}

		for (ItemStack weapon : List.of(new ItemStack(EnemyItems.VIBROBLADE), new ItemStack(GearItems.VIBROSWORD), new ItemStack(GearItems.ELECTROSTAFF))) {
			if (!Mods.slotsOf(weapon).equals(List.of(ModSlot.EDGE))) {
				problems.add(weapon + " has " + Mods.slotsOf(weapon));
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("slots per gear: " + problems));
		helper.assertTrue(Mods.slotsOf(new ItemStack(GearItems.KAAST_LONGSHOT)).equals(List.of(ModSlot.CELL)), msg("the Longshot's barrel is fixed"));
		helper.assertTrue(Mods.slotsOf(new ItemStack(SaberItems.LIGHTSABER)).isEmpty() && Mods.slotsOf(new ItemStack(Items.STICK)).isEmpty()
				&& Mods.slotsOf(ItemStack.EMPTY).isEmpty(), msg("sabers and other items take no mods"));
		helper.assertTrue(FittedMods.MAX >= 2, msg("the component holds every slot of one piece"));

		// Each mod fits exactly one slot.
		ModSlot[] expected = {ModSlot.ARMOUR, ModSlot.ARMOUR, ModSlot.ARMOUR, ModSlot.ARMOUR, ModSlot.ARMOUR, ModSlot.PACK,
				ModSlot.BARREL, ModSlot.BARREL, ModSlot.CELL, ModSlot.CELL, ModSlot.EDGE, ModSlot.EDGE};
		Item[] mods = {M4Items.MOD_PLATING, M4Items.MOD_WEAVE, M4Items.MOD_ATTUNEMENT, M4Items.MOD_CONDUIT, M4Items.MOD_STABILISER,
				M4Items.JET_PACK, M4Items.MOD_FOCUSED_BARREL, M4Items.MOD_HEAVY_BARREL, M4Items.MOD_COOLING_CELL, M4Items.MOD_OVERCHARGED_CELL,
				M4Items.MOD_KEEN_EDGE, M4Items.MOD_SHOCK_EDGE};

		for (int i = 0; i < mods.length; i++) {
			helper.assertTrue(Mods.slotOf(mods[i]) == expected[i] && M4Items.MODS.contains(mods[i]), msg(mods[i] + " fits " + expected[i]));
		}

		helper.assertTrue(Mods.slotOf(Items.STICK) == null, msg("a stick is no mod"));

		// Fitting: the right slot only, one mod per slot, the stack shrinks by one.
		ItemStack helmet = new ItemStack(EquipmentItems.WATCH_BATTLE_HELMET);
		ItemStack plating = new ItemStack(M4Items.MOD_PLATING, 2);
		Mods.fit(helmet, ModSlot.ARMOUR, plating);
		helper.assertTrue(Mods.has(helmet, M4Items.MOD_PLATING) && plating.getCount() == 1, msg("plating fits a helmet"));
		Mods.fit(helmet, ModSlot.ARMOUR, plating);
		helper.assertTrue(plating.getCount() == 1, msg("a taken slot refuses a second mod"));
		ItemStack jet = new ItemStack(M4Items.JET_PACK);
		Mods.fit(helmet, ModSlot.PACK, jet);
		helper.assertTrue(!Mods.has(helmet, M4Items.JET_PACK) && jet.getCount() == 1, msg("a helmet has no pack slot"));
		ItemStack jacket = new ItemStack(EquipmentItems.SPACER_JACKET);
		Mods.fit(jacket, ModSlot.PACK, jet);
		ItemStack barrel = new ItemStack(M4Items.MOD_FOCUSED_BARREL);
		Mods.fit(jacket, ModSlot.ARMOUR, barrel);
		helper.assertTrue(Mods.has(jacket, M4Items.JET_PACK) && jet.isEmpty() && !Mods.has(jacket, M4Items.MOD_FOCUSED_BARREL) && barrel.getCount() == 1,
				msg("the jet pack fits a chest piece; a barrel never fits armour"));

		ItemStack pistol = new ItemStack(EnemyItems.BLASTER_PISTOL);
		Mods.fit(pistol, ModSlot.BARREL, barrel);
		Mods.fit(pistol, ModSlot.CELL, new ItemStack(M4Items.MOD_COOLING_CELL));
		helper.assertTrue(Mods.fitted(pistol).mods().size() == 2, msg("a blaster takes a barrel and a cell"));
		ItemStack longshot = new ItemStack(GearItems.KAAST_LONGSHOT);
		ItemStack heavy = new ItemStack(M4Items.MOD_HEAVY_BARREL);
		Mods.fit(longshot, ModSlot.BARREL, heavy);
		helper.assertTrue(heavy.getCount() == 1 && Mods.has(longshot, M4Items.MOD_HEAVY_BARREL)
				&& Mods.fittedQuality(longshot, M4Items.MOD_HEAVY_BARREL) == Quality.MASTERWORK.ordinal(),
				msg("the Longshot refuses a barrel and counts its own heavy barrel at Masterwork"));

		ItemStack removed = Mods.remove(helmet, ModSlot.ARMOUR);
		helper.assertTrue(removed.is(M4Items.MOD_PLATING) && !helmet.has(M4Components.MODS), msg("removing the only mod clears the component"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void modsRespectClamps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EquipmentBalance eb = EquipmentBalance.get();
		EquipmentM4Balance m = EquipmentM4Balance.get();
		ModNumbers mods = EquipmentM4Balance.mods();
		ProgressionBalance pb = Progression.balance();
		EquipmentModifiers modifiers = new EquipmentModifiers();
		int mw = Quality.MASTERWORK.ordinal();
		ServerPlayer player = player(helper);
		Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 3));

		try {
			helper.assertTrue(near(SetBonusRules.controlTotal(0.1f, m), m.controlMin) && near(SetBonusRules.controlTotal(1.2f, m), 1.2f),
					msg("control is floored at controlMin, never raised"));

			// Control: Hunter's Tenacity and four Masterwork stabilisers would pass controlMin.
			wearModded(player, ArmourSet.MANDALORIAN, M4Items.MOD_STABILISER, Quality.MASTERWORK);
			double control = ratio(m.mandoSetControlPct) * pow(ratio(mods.stabiliserControlPct[mw]), 4);
			helper.assertTrue(control < m.controlMin, msg("precondition: the raw control product " + control + " is below " + m.controlMin));
			helper.assertTrue(near(modifiers.multiplier(player, ControlEffects.STAGGER), m.controlMin), msg("control stops at controlMin"));

			// Force cost: Wrath Unbound and four Masterwork attunements would pass the registry's floor.
			identity(player, -eb.setAlignment, Path.SITH);
			wearModded(player, ArmourSet.SITH_WAR, M4Items.MOD_ATTUNEMENT, Quality.MASTERWORK);
			player.setHealth(player.getMaxHealth() * m.sithWarSetHealthFraction - 1f);
			float cost = modifiers.costMultiplier(player, SetBonusRules.LIGHTNING);
			helper.assertTrue(near(cost, ratio(m.sithWarSetCostPct) * pow(ratio(mods.attunementCostPct[mw]), 4)) && cost < pb.modifierCostMin,
					msg("precondition: the gear's own cost product " + cost + " is below " + pb.modifierCostMin));
			helper.assertTrue(ForceModifiers.cost(player, SetBonusRules.LIGHTNING) >= pb.modifierCostMin - EPS, msg("the cost registry clamps it"));
			player.setHealth(player.getMaxHealth());

			// Damage taken: a raised vibrosword, Warden's Resolve and four Masterwork weaves would pass the damage floor.
			identity(player, eb.setAlignment, Path.JEDI);
			wearModded(player, ArmourSet.WATCH_BATTLE, M4Items.MOD_WEAVE, Quality.MASTERWORK);
			SetBonuses.parried(player);
			hold(player, new ItemStack(GearItems.VIBROSWORD));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			DamageSource blow = level.damageSources().mobAttack(zombie);
			float taken = modifiers.modify(player, blow, 10f);
			helper.assertTrue(taken < 10f * pb.damageModifierMin, msg("precondition: the gear's own damage product " + taken / 10f + " is below " + pb.damageModifierMin));
			helper.assertTrue(DamageModifiers.apply(player, blow, 10f) >= 10f * pb.damageModifierMin - EPS, msg("the damage registry clamps it"));
		} finally {
			player.stopUsingItem();
			player.setHealth(player.getMaxHealth());
			zombie.discard();
			done(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the jet pack (6.4)

	@GameTest(maxTicks = 300)
	public void jetChargesRefillOnGround(GameTestHelper helper) {
		ModNumbers m = EquipmentM4Balance.mods();
		helper.assertTrue(JetPack.groundTicks(5, true) == 6 && JetPack.groundTicks(5, false) == 0, msg("ground ticks count on the ground, reset in the air"));
		helper.assertTrue(JetPack.usedAfter(2, m.jetRefillTicks - 1, m.jetRefillTicks) == 2 && JetPack.usedAfter(2, m.jetRefillTicks, m.jetRefillTicks) == 0,
				msg("every charge comes back together after jetRefillTicks"));
		helper.assertTrue(JetPack.inLandingWindow(T0, T0 + m.jetLandWindow, m.jetLandWindow) && !JetPack.inLandingWindow(T0, T0 + m.jetLandWindow + 1, m.jetLandWindow),
				msg("the landing window"));
		helper.assertTrue(m.jetRefillTicks + 4 < 300, msg("precondition: jetRefillTicks fits the test (" + m.jetRefillTicks + ")"));

		ServerPlayer player = player(helper);
		int max;

		try {
			wearJetPack(player);
			max = JetPack.maxCharges(player, m);
			helper.assertTrue(max == m.jetCharges && JetPack.charges(player) == max, msg("a full pack has jetCharges"));
			helper.assertTrue(!JetPack.burst(player), msg("no burst on the ground"));
			player.setOnGround(false);
			helper.assertTrue(JetPack.burst(player), msg("a burst in the air"));
			helper.assertTrue(JetPack.charges(player) == max - 1 && near(player.getDeltaMovement().y, m.jetUp), msg("spends a charge and lifts by jetUp"));
			helper.assertTrue(near(JetPack.fallMultiplier(player), Math.max(0f, 1f + m.jetFallPct / 100f)), msg("a landing right after a burst falls softer"));
			helper.assertTrue(m.jetBurstGapTicks <= 0 || !JetPack.burst(player), msg("a second burst the same tick waits for the gap"));
			JetPack.spend(player, max, 100);
			helper.assertTrue(JetPack.charges(player) == 0 && !JetPack.burst(player), msg("an empty pack only clicks"));
			helper.assertTrue(m.jetLandWindow >= 100 || near(JetPack.fallMultiplier(player), 1), msg("an old burst does not soften a fall"));
			player.setOnGround(true);
		} catch (RuntimeException | Error e) {
			M3TestPlayers.remove(player);
			throw e;
		}

		int full = max;
		helper.runAfterDelay(m.jetRefillTicks / 2, () -> {
			try {
				helper.assertTrue(JetPack.charges(player) == 0, msg("half the refill time on the ground: still empty"));
			} catch (RuntimeException | Error e) {
				M3TestPlayers.remove(player);
				throw e;
			}
		});
		helper.runAfterDelay(m.jetRefillTicks + 2, () -> {
			try {
				helper.assertTrue(JetPack.charges(player) == full, msg("after jetRefillTicks on the ground every charge is back"));
				helper.assertTrue(new JetPack.JetView(full, full).equals(player.getAttached(JetPack.JET)), msg("and the pips say so: " + player.getAttached(JetPack.JET)));
				helper.succeed();
			} finally {
				M3TestPlayers.remove(player);
			}
		});
	}

	@GameTest(maxTicks = 20)
	public void jetDefersToForceJumpGlide(GameTestHelper helper) {
		ModNumbers m = EquipmentM4Balance.mods();
		ServerPlayer player = player(helper);

		try {
			wearJetPack(player);
			int max = JetPack.maxCharges(player, m);
			player.setOnGround(false);
			ForceJump.leap(player, 0f);
			helper.assertTrue(ForceJump.isLeaping(player), msg("precondition: a Force Jump leap is under way"));
			helper.assertTrue(!JetPack.burst(player) && JetPack.charges(player) == max, msg("the jump key belongs to the leap's glide: no burst, no charge spent"));
			ForceJump.forget(player);
			player.getAbilities().flying = true;
			helper.assertTrue(!JetPack.burst(player), msg("no burst while flying"));
			player.getAbilities().flying = false;
			helper.assertTrue(!ForceJump.isLeaping(player) && JetPack.burst(player) && JetPack.charges(player) == max - 1, msg("after the leap the pack fires"));
		} finally {
			ForceJump.forget(player);
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}
}

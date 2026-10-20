package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.space.BlockadeGunship;
import com.oldrepublic.space.GunshipGenerator;
import com.oldrepublic.space.NavBuoy;
import com.oldrepublic.space.OrbitFloor;
import com.oldrepublic.space.ShipBolt;
import com.oldrepublic.space.ShipCombat;
import com.oldrepublic.space.ShipMissile;
import com.oldrepublic.space.ShipWeapons;
import com.oldrepublic.space.SilentSpindle;
import com.oldrepublic.space.SpaceBalance;
import com.oldrepublic.space.SpaceData;
import com.oldrepublic.space.SpaceEncounters;
import com.oldrepublic.space.SpaceEncounters.End;
import com.oldrepublic.space.SpaceEntities;
import com.oldrepublic.space.SpaceMissions;
import com.oldrepublic.space.SpaceRules;
import com.oldrepublic.space.SpaceRules.Side;
import com.oldrepublic.space.Starfighter;
import com.oldrepublic.travel.CourierShuttle;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipEntities;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 space tests (spec MILESTONE_5.md 19.1, W2). The rules are checked as pure functions
 * with the default balance ({@code new SpaceBalance()}); bolts, missiles, shields, encounters,
 * objectives, the vault and the orbit floor run for real in the lead's {@code arena_24x7x24} fixture
 * (its dais, pylons and columns cleared where shots fly) with listed mock players and the live
 * balance.
 *
 * <p>The test server has no orbits and no helm a test can take (the helm is W1's, reached only from a
 * pilot's client), so a ship "in flight" is a ship whose record is {@code FLYING} on its pad, and
 * the space code's own test hooks stand in for the orbit: {@code SpaceEncounters.startPatrolAt} and
 * {@code startBlockadeAt} begin an encounter where a test wants it, as a world that is not Peaceful;
 * {@code SpaceMissions.stepForTest} runs one tick of an objective with a given body standing in for
 * the piloted ship. Fighters a test does not watch fly are held still ({@code NoAI}) the tick they
 * appear, so nothing shoots in a shared sky; a hit is dealt as a real bolt or missile would deal it
 * ({@code ShipBolt.strikeOf}).
 *
 * <p>Each test removes its own players, bodies, fighters, shots and ship records when it ends, pass or
 * fail, and only those (a removed player's encounter ends with them), so tests running alongside
 * keep theirs.
 */
public class SpaceGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** The pads ships stand on: west and east of the middle, and by the north edge. A courier is 7 blocks square, 4 high. */
	private static final BlockPos PAD_A = new BlockPos(6, 1, 12);
	private static final BlockPos PAD_B = new BlockPos(18, 1, 12);
	private static final BlockPos PAD_C = new BlockPos(12, 1, 4);
	/** Where the players stand, clear of every pad and line of fire. */
	private static final BlockPos STAND_A = new BlockPos(3, 1, 3);
	private static final BlockPos STAND_B = new BlockPos(21, 1, 3);
	private static final BlockPos STAND_C = new BlockPos(12, 1, 21);
	/** Hull yaws ({@code ShipBody.local}: a hull faces (-sin, 0, cos)): toward +X, -X and +Z. */
	private static final float EAST = -90f;
	private static final float WEST = 90f;
	private static final float SOUTH = 0f;
	/** More than any ship target's shield and hull together: one strike destroys it. */
	private static final float LETHAL = 10_000f;
	private static final float EPSILON = 1.0e-3f;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- bolts and missiles (5.3)

	/** A bolt ten blocks a tick still strikes a fighter its step would jump over: the sweep along its path decides, not where it ends. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void boltSweepHitsFastTarget(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.open();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		Starfighter raider = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(17.5, 2.8, 12.5), Side.HOSTILE, true, SpaceBalance.get());
		float shield = raider.shield();
		float health = raider.getHealth();
		ShipBolt[] bolt = new ShipBolt[1];

		scene.then(() -> {
			// From the nose at x 10.7 the first step ends at x 20.7, past the raider's far side (19.2): only a sweep sees it.
			Vec3 from = ship.position().add(ShipBody.local(0, 1.8, 4.2, ship.getYRot()));
			bolt[0] = scene.track(ShipBolt.fire(scene.level, ship, owner.getUUID(), ShipBolt.Team.PLAYERS, ShipBolt.Style.INDEPENDENT, from,
					new Vec3(10, 0, 0), 6f, 96));
		}).await("the bolt strikes", 5, () -> bolt[0].isRemoved()).then(() -> {
			helper.assertTrue(Math.abs(raider.shield() - (shield - 6f)) < EPSILON, msg("the raider's shield took the 6 damage, shield " + raider.shield()));
			helper.assertTrue(raider.getHealth() == health, msg("its hull is untouched while the shield holds, health " + raider.getHealth()));
		}).run();
	}

	/** A bolt never hits the ship or fighter that fired it, nor anyone aboard; a fighter's bolt reaches the ship and spares its rider. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void boltNeverHitsItsShipOrRiders(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.open();
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		helper.assertTrue(owner.startRiding(ship, true, false), msg("the owner boards"));
		Starfighter raider = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(17.5, 2.8, 12.5), Side.HOSTILE, true, live);

		for (ShipBolt.Team team : ShipBolt.Team.values()) {
			helper.assertFalse(ShipBolt.mayHit(ship, team, ship.getId()), msg(team + ": never the ship that fired"));
			helper.assertFalse(ShipBolt.mayHit(owner, team, ship.getId()), msg(team + ": never a rider of the ship that fired"));
			helper.assertFalse(ShipBolt.mayHit(owner, team, raider.getId()), msg(team + ": never a player"));
			helper.assertFalse(ShipBolt.mayHit(raider, team, raider.getId()), msg(team + ": never the fighter that fired"));
		}

		helper.assertTrue(ShipBolt.mayHit(ship, ShipBolt.Team.HOSTILE, raider.getId()), msg("a fighter's bolt may hit a player's ship"));
		helper.assertFalse(ShipBolt.mayHit(ship, ShipBolt.Team.PLAYERS, -1), msg("a player's bolt never hits a player's ship"));

		ShipRecord armed = scene.flying(owner, ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD);
		float max = ShipCombat.maxShield(armed, live);
		int hull = armed.hull();
		ShipBolt[] bolt = new ShipBolt[1];

		scene.then(() -> {
			// Fired from inside the raider itself, straight at the ship with its rider.
			Vec3 from = raider.getBoundingBox().getCenter();
			bolt[0] = scene.track(ShipBolt.fire(scene.level, raider, null, ShipBolt.Team.HOSTILE, ShipBolt.Style.RAIDER, from,
					new Vec3(-live.boltSpeed, 0, 0), 6f, live.boltRange));
		}).await("the bolt strikes", 10, () -> bolt[0].isRemoved()).then(() -> {
			helper.assertTrue(Math.abs(ShipCombat.shieldPoints(owner) - (max - 6f)) < EPSILON,
					msg("the ship's shield took the hit, shield " + ShipCombat.shieldPoints(owner) + " of " + max));
			helper.assertTrue(scene.record(owner).hull() == hull, msg("the shield kept it off the hull"));
			helper.assertTrue(raider.shield() == raider.shieldMax() && raider.getHealth() == raider.getMaxHealth(), msg("the raider that fired is untouched"));
			helper.assertTrue(owner.getVehicle() == ship && owner.getHealth() == owner.getMaxHealth(), msg("the rider rides on, unhurt"));
		}).run();
	}

	/** Ship weapons hurt ship targets only: a bolt passes a pig and strikes the raider behind it; anything but a ship weapon is refused. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void shipWeaponsHurtOnlyShipTargets(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.open();
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		Mob pig = scene.track(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(13, 1, 12)));
		Starfighter raider = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(19.5, 1.8, 12.5), Side.HOSTILE, true, live);
		ServerLevel level = scene.level;

		helper.assertFalse(ShipBolt.mayHit(pig, ShipBolt.Team.PLAYERS, ship.getId()), msg("a pig is no ship target"));
		helper.assertFalse(ShipBolt.mayHit(new ShipBolt(SpaceEntities.SHIP_BOLT, level), ShipBolt.Team.PLAYERS, ship.getId()), msg("nor is a projectile"));
		helper.assertTrue(ShipBolt.mayHit(raider, ShipBolt.Team.PLAYERS, ship.getId()), msg("a hostile fighter is"));

		BlockadeGunship gunship = SpaceEntities.BLOCKADE_GUNSHIP.create(level, EntitySpawnReason.EVENT);
		GunshipGenerator generator = SpaceEntities.GUNSHIP_GENERATOR.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(gunship != null && generator != null, msg("the gunship and its generator are created"));
		gunship.setup(live);
		helper.assertTrue(ShipBolt.mayHit(generator, ShipBolt.Team.PLAYERS, ship.getId()), msg("the generator is a ship target"));
		helper.assertTrue(ShipBolt.mayHitGunship(ShipBolt.Team.PLAYERS) && !ShipBolt.mayHitGunship(ShipBolt.Team.HOSTILE),
				msg("the gunship's hull takes the players' fire, never its own side's"));

		for (DamageSource other : List.of(level.damageSources().generic(), level.damageSources().playerAttack(owner), level.damageSources().magic())) {
			helper.assertFalse(raider.hurtServer(level, other, 5f), msg("a fighter refuses " + other.getMsgId()));
			helper.assertFalse(gunship.hurtServer(level, other, 5f), msg("the gunship refuses " + other.getMsgId()));
			helper.assertFalse(generator.hurtServer(level, other, 5f), msg("the generator refuses " + other.getMsgId()));
		}

		helper.assertTrue(raider.getHealth() == raider.getMaxHealth() && raider.shield() == raider.shieldMax(), msg("nothing reached the raider"));
		ShipBolt[] bolt = new ShipBolt[1];

		scene.then(() -> {
			// Low, through the pig standing in the line of fire.
			Vec3 from = ship.position().add(ShipBody.local(0, 0.5, 4.2, ship.getYRot()));
			bolt[0] = scene.track(ShipBolt.fire(level, ship, owner.getUUID(), ShipBolt.Team.PLAYERS, ShipBolt.Style.INDEPENDENT, from,
					new Vec3(live.boltSpeed, 0, 0), 6f, live.boltRange));
		}).await("the bolt strikes", 10, () -> bolt[0].isRemoved()).then(() -> {
			helper.assertTrue(pig.isAlive() && pig.getHealth() == pig.getMaxHealth(), msg("the pig in the way is untouched"));
			helper.assertTrue(Math.abs(raider.shield() - (raider.shieldMax() - 6f)) < EPSILON, msg("the raider behind it took the bolt, shield " + raider.shield()));
		}).run();
	}

	/** The cannons' heat (5.3): 15 shots of 7 lock them at 100, the lock cools them to 40, and they hold, then cool, after firing. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void heatLocksAt100AndCoolsTo40(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		SpaceRules.Heat heat = SpaceRules.Heat.COLD;
		long t = 1000;
		int shots = 0;

		while (shots < 100) {
			helper.assertTrue(heat.ready(t, b.cannonInterval), msg("the guns are ready at shot " + (shots + 1)));
			heat = heat.fire(t, b.cannonHeat, b);
			shots++;

			if (heat.locked(t)) {
				break;
			}

			helper.assertTrue(heat.at(t + b.cannonInterval, b) == heat.at(t, b), msg("the heat holds between shots fired every interval"));
			t += b.cannonInterval;
		}

		helper.assertTrue(shots == (int) Math.ceil(100f / b.cannonHeat) && shots == 15, msg("15 shots of 7 lock the guns, took " + shots));
		long until = t + b.overheatLockTicks;
		helper.assertTrue(heat.at(t, b) == 100f, msg("the lock starts at 100, got " + heat.at(t, b)));
		helper.assertFalse(heat.ready(t + 1, b.cannonInterval) || heat.ready(until - 1, b.cannonInterval), msg("no shot while locked"));
		helper.assertTrue(Math.abs(heat.at(t + b.overheatLockTicks / 2, b) - (100f + b.overheatCoolTo) / 2f) < EPSILON,
				msg("halfway through the lock it has cooled halfway to 40, got " + heat.at(t + b.overheatLockTicks / 2, b)));
		helper.assertTrue(heat.at(until, b) == b.overheatCoolTo && b.overheatCoolTo == 40f, msg("the lock ends at 40, got " + heat.at(until, b)));
		helper.assertTrue(heat.ready(until, b.cannonInterval) && !heat.locked(until), msg("the guns fire again when the lock ends"));
		helper.assertTrue(Math.abs(heat.at(until + 20, b) - (b.overheatCoolTo - b.heatCoolPerSecond)) < EPSILON,
				msg("then it cools 20 a second, got " + heat.at(until + 20, b)));
		SpaceRules.Heat again = heat.fire(until, b.cannonHeat, b);
		helper.assertTrue(again.at(until, b) == b.overheatCoolTo + b.cannonHeat && !again.locked(until), msg("a shot as the lock ends adds to 40"));

		SpaceRules.Heat one = SpaceRules.Heat.COLD.fire(0, b.cannonHeat, b);
		helper.assertTrue(one.at(b.heatCoolDelay, b) == b.cannonHeat, msg("one shot's heat holds for the cool delay"));
		helper.assertTrue(Math.abs(one.at(b.heatCoolDelay + 1, b) - (b.cannonHeat - b.heatCoolPerSecond / 20f)) < EPSILON, msg("then it cools"));
		helper.assertTrue(one.at(b.heatCoolDelay + 200, b) == 0f, msg("never below cold"));
		helper.assertTrue(SpaceRules.Heat.COLD.at(0, b) == 0f && SpaceRules.Heat.COLD.ready(0, b.cannonInterval), msg("a gun that never fired is cold and ready"));
		helper.succeed();
	}

	/** The missile lock (5.3): a ship target in the 20 degree cone within 80 blocks, held 30 ticks; never an ally. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void missileLockConeRangeAndTicks(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		ServerLevel level = helper.getLevel();
		Vec3 eye = helper.absoluteVec(new Vec3(12, 3, 12));
		Vec3 nose = new Vec3(1, 0, 0);
		double near = 40;

		helper.assertTrue(ShipWeapons.lockable(detached(level, SpaceEntities.HYMN_INTERCEPTOR, ahead(eye, 0, near), Side.HOSTILE, false, b), eye, nose, b),
				msg("a fighter dead ahead"));
		helper.assertTrue(ShipWeapons.lockable(detached(level, SpaceEntities.HYMN_INTERCEPTOR, ahead(eye, b.lockCone - 1, near), Side.HOSTILE, false, b), eye,
				nose, b), msg("a fighter just inside the cone"));
		helper.assertFalse(ShipWeapons.lockable(detached(level, SpaceEntities.HYMN_INTERCEPTOR, ahead(eye, b.lockCone + 5, near), Side.HOSTILE, false, b), eye,
				nose, b), msg("a fighter outside the cone"));
		helper.assertTrue(ShipWeapons.lockable(detached(level, SpaceEntities.KAJIDIC_RAIDER, ahead(eye, 0, b.lockRange - 5), Side.HOSTILE, false, b), eye, nose,
				b), msg("a raider inside the range"));
		helper.assertFalse(ShipWeapons.lockable(detached(level, SpaceEntities.KAJIDIC_RAIDER, ahead(eye, 0, b.lockRange + 5), Side.HOSTILE, false, b), eye,
				nose, b), msg("a raider beyond the range"));
		helper.assertTrue(ShipWeapons.lockable(detached(level, SpaceEntities.KAJIDIC_RAIDER, ahead(eye, 0, near), Side.NEUTRAL, false, b), eye, nose, b),
				msg("a neutral patrol may be locked (firing on it starts the fight)"));
		helper.assertFalse(ShipWeapons.lockable(detached(level, SpaceEntities.HASK_FIGHTER, ahead(eye, 0, near), Side.ALLIED, false, b), eye, nose, b),
				msg("never an allied fighter"));

		BlockadeGunship gunship = SpaceEntities.BLOCKADE_GUNSHIP.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(gunship != null, msg("the gunship is created"));
		Vec3 middle = ahead(eye, 0, near);
		gunship.snapTo(middle.x, middle.y - gunship.getBbHeight() / 2, middle.z, 0f, 0f);
		helper.assertTrue(ShipWeapons.lockable(gunship, eye, nose, b), msg("the gunship may be locked"));
		Mob pig = EntityTypes.PIG.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(pig != null, msg("the pig is created"));
		pig.snapTo(middle.x, middle.y, middle.z, 0f, 0f);
		helper.assertFalse(ShipWeapons.lockable(pig, eye, nose, b), msg("never anything but a ship target"));

		helper.assertFalse(SpaceRules.lockHeld(b.lockTicks - 1, b), msg("a lock held one tick short is not complete"));
		helper.assertTrue(SpaceRules.lockHeld(b.lockTicks, b) && b.lockTicks == 30, msg("held 30 ticks, it is"));
		helper.assertTrue(SpaceRules.lockProgress(0, b) == 0f && Math.abs(SpaceRules.lockProgress(b.lockTicks / 2, b) - 0.5f) < EPSILON
				&& SpaceRules.lockProgress(b.lockTicks, b) == 1f && SpaceRules.lockProgress(b.lockTicks * 3, b) == 1f, msg("the lock's progress runs 0 to 1"));
		helper.succeed();
	}

	/** A missile is a concussion missile out of the ship's hold: one fired, the hold empty, nothing more; it strikes shield, then hull. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void missilesComeFromTheHold(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.open();
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		scene.change(owner, r -> r.withUpgrades(r.upgrades() | ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_MISSILES)
				.withHold(List.of(new ItemStack(M5Items.CONCUSSION_MISSILE, 1))));
		Starfighter raider = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(19.5, 1.8, 12.5), Side.HOSTILE, true, live);
		float shield = raider.shield();
		float health = raider.getHealth();
		ShipMissile[] missile = new ShipMissile[1];

		scene.then(() -> {
			helper.assertTrue(ShipWeapons.launch(scene.level, ship, owner.getUUID(), owner.getUUID(), raider, live), msg("the missile in the hold flies"));
			helper.assertTrue(holdCount(scene.record(owner), M5Items.CONCUSSION_MISSILE) == 0, msg("it came out of the hold"));
			helper.assertFalse(ShipWeapons.launch(scene.level, ship, owner.getUUID(), owner.getUUID(), raider, live), msg("an empty hold fires nothing"));
			List<ShipMissile> flying = scene.level.getEntitiesOfClass(ShipMissile.class, helper.getBounds().inflate(16), m -> m.shipId() == ship.getId());
			helper.assertTrue(flying.size() == 1, msg("exactly one missile flies, found " + flying.size()));
			missile[0] = scene.track(flying.getFirst());
			helper.assertTrue(raider.getUUID().equals(missile[0].target()) && owner.getUUID().equals(missile[0].shooter()), msg("it homes on the raider for its pilot"));
		}).await("the missile strikes", 40, () -> missile[0].isRemoved()).then(() -> {
			float expectedShield = Math.max(0f, shield - live.missileDamage);
			float expectedHealth = health - Math.max(0f, live.missileDamage - shield);
			helper.assertTrue(Math.abs(raider.shield() - expectedShield) < EPSILON, msg("the warhead took the shield, shield " + raider.shield()));
			helper.assertTrue(Math.abs(raider.getHealth() - expectedHealth) < EPSILON,
					msg("and the rest of its " + live.missileDamage + " off the hull, health " + raider.getHealth() + ", expected " + expectedHealth));
		}).run();
	}

	// ---------------------------------------------------------------- shields and the hull (5.4)

	/** Hits take the shield first, then whole hull points (fractions carried over); only a ship in flight takes them. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void shieldFirstThenHull(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.now(() -> {
			SpaceBalance live = SpaceBalance.get();
			ServerPlayer a = scene.player(STAND_A);
			ServerPlayer bare = scene.player(STAND_B);
			ServerPlayer grounded = scene.player(STAND_C);
			scene.give(a, PAD_A, EAST);
			scene.give(bare, PAD_B, WEST);
			scene.give(grounded, PAD_C, SOUTH);

			ShipRecord shielded = scene.flying(a, ShipRecord.UPGRADE_SHIELD);
			float max = ShipCombat.maxShield(shielded, live);
			int hull = shielded.hull();
			helper.assertTrue(max == live.courierShield && max > 10f, msg("a courier's shield holds " + live.courierShield + ", got " + max));
			helper.assertTrue(ShipCombat.shield(a) == Math.round(max) && !ShipCombat.underFire(a), msg("a ship nobody hit: a full shield, not under fire"));

			ShipCombat.hitShip(a, max - 10f, null);
			helper.assertTrue(Math.abs(ShipCombat.shieldPoints(a) - 10f) < EPSILON && scene.record(a).hull() == hull, msg("the shield takes the hit whole"));
			ShipCombat.hitShip(a, 15f, null);
			helper.assertTrue(ShipCombat.shieldPoints(a) == 0f && scene.record(a).hull() == hull - 5,
					msg("what the shield cannot hold reaches the hull, hull " + scene.record(a).hull()));
			ShipCombat.hitShip(a, 2.5f, null);
			helper.assertTrue(scene.record(a).hull() == hull - 7, msg("2.5 takes 2 whole points, hull " + scene.record(a).hull()));
			ShipCombat.hitShip(a, 0.5f, null);
			helper.assertTrue(scene.record(a).hull() == hull - 8, msg("the half point carried over makes one more, hull " + scene.record(a).hull()));
			helper.assertTrue(ShipCombat.underFire(a) && !ShipCombat.disabled(a), msg("under fire, not disabled"));
			ShipCombat.hitShip(a, 0f, null);
			ShipCombat.hitShip(a, -5f, null);
			helper.assertTrue(scene.record(a).hull() == hull - 8, msg("a hit of nothing does nothing"));

			ShipRecord open = scene.flying(bare, 0);
			helper.assertTrue(ShipCombat.maxShield(open, live) == 0f && ShipCombat.shield(bare) == 0, msg("no shield upgrade, no shield"));
			ShipCombat.hitShip(bare, 10f, null);
			helper.assertTrue(scene.record(bare).hull() == open.hull() - 10, msg("without a shield every point reaches the hull"));

			ShipRecord resting = scene.change(grounded, r -> r.withUpgrades(r.upgrades() | ShipRecord.UPGRADE_SHIELD));
			helper.assertTrue(resting.state() != ShipState.FLYING, msg("this one rests on its pad"));
			ShipCombat.hitShip(grounded, 10f, null);
			helper.assertTrue(scene.record(grounded).hull() == resting.hull() && !ShipCombat.underFire(grounded)
					&& ShipCombat.shieldPoints(grounded) == ShipCombat.maxShield(resting, live), msg("a ship on the ground takes no hits"));
		});
	}

	/** The shield waits {@code shieldDelay} ticks after the last hit, then recharges 4 points a second to full; a new hit starts the wait again. */
	@GameTest(structure = ARENA, maxTicks = 300)
	public void shieldRechargeAfterDelay(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		scene.give(owner, PAD_A, EAST);
		float[] max = new float[1];
		float[] seen = new float[1];

		scene.then(() -> {
			max[0] = ShipCombat.maxShield(scene.flying(owner, ShipRecord.UPGRADE_SHIELD), live);
			ShipCombat.hitShip(owner, 10f, null);
			seen[0] = ShipCombat.shieldPoints(owner);
			helper.assertTrue(Math.abs(seen[0] - (max[0] - 10f)) < EPSILON, msg("the hit took 10"));
		}).idle(live.shieldDelay - 10).then(() -> {
			helper.assertTrue(ShipCombat.shieldPoints(owner) == seen[0], msg("nothing comes back during the delay, got " + ShipCombat.shieldPoints(owner)));
		}).idle(30).then(() -> {
			float now = ShipCombat.shieldPoints(owner);
			helper.assertTrue(now > seen[0] && now < max[0], msg("after the delay it recharges, got " + now));
			ShipCombat.hitShip(owner, 1f, null);
			seen[0] = ShipCombat.shieldPoints(owner);
		}).idle(live.shieldDelay - 10).then(() -> {
			helper.assertTrue(ShipCombat.shieldPoints(owner) == seen[0], msg("a new hit starts the delay again, got " + ShipCombat.shieldPoints(owner)));
		}).await("the shield is full again", 160, () -> ShipCombat.shieldPoints(owner) >= max[0] - EPSILON).then(() -> {
			helper.assertTrue(ShipCombat.shield(owner) == Math.round(max[0]), msg("it stops at full"));
			helper.assertTrue(scene.record(owner).hull() == 100, msg("the shield held every hit off the hull"));
		}).run();
	}

	/** At 0 hull the ship is disabled ({@code Ships.disable}, W1) and its encounter ends; a disabled ship takes no more hits. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void hullZeroCallsDisable(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(STAND_A);
		scene.give(owner, PAD_A, EAST);

		scene.then(() -> {
			scene.flying(owner, 0);
			scene.change(owner, r -> r.withHull(5));
			helper.assertTrue(SpaceEncounters.startPatrolAt(owner, scene.at(12.5, 14, 12.5), 2), msg("a patrol finds the ship"));
			List<Starfighter> patrol = scene.hold(owner);
			helper.assertTrue(patrol.size() == 2, msg("two fighters, got " + patrol.size()));
			ShipCombat.hitShip(owner, 10f, null);
			helper.assertTrue(scene.record(owner).hull() == 0, msg("the hull is spent, got " + scene.record(owner).hull()));
			helper.assertTrue(ShipCombat.disabled(owner), msg("the ship is disabled"));
			helper.assertTrue(SpaceEncounters.lastEnd(owner.getUUID()) == End.DISABLED && SpaceEncounters.snapshot(owner.getUUID()) == null,
					msg("its encounter ended DISABLED, got " + SpaceEncounters.lastEnd(owner.getUUID())));

			for (Starfighter fighter : patrol) {
				helper.assertTrue(fighter.isRemoved(), msg("the patrol is gone with it"));
			}

			ShipCombat.hitShip(owner, 10f, null);
			helper.assertTrue(scene.record(owner).hull() == 0 && ShipCombat.disabled(owner), msg("a disabled ship takes no more hits"));
		}).run();
	}

	/** Players' ships never damage each other: one ship's bolt and missile fly through another to their target; another player's battle never reaches it. */
	@GameTest(structure = ARENA, maxTicks = 60, skyAccess = true)
	public void playersShipsNeverDamaged(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.open();
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer a = scene.player(STAND_A);
		ServerPlayer b = scene.player(STAND_B);
		ShipBody shipA = scene.give(a, PAD_A, EAST);
		ShipBody shipB = scene.give(b, PAD_B, WEST);
		scene.flying(a, ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD);
		scene.change(b, r -> r.withUpgrades(r.upgrades() | ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD | ShipRecord.UPGRADE_MISSILES)
				.withState(ShipState.FLYING).withHold(List.of(new ItemStack(M5Items.CONCUSSION_MISSILE, 1))));
		// Just above A's ship as B sees it: every shot B fires at it crosses A's hull first.
		Starfighter target = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(6.5, 6.4, 12.5), Side.HOSTILE, true, live);
		float targetPoints = target.shield() + target.getHealth();
		ShipBolt[] bolt = new ShipBolt[1];
		ShipMissile[] missile = new ShipMissile[1];

		helper.assertFalse(ShipBolt.mayHit(shipA, ShipBolt.Team.PLAYERS, shipB.getId()) || ShipBolt.mayHit(shipB, ShipBolt.Team.PLAYERS, shipA.getId()),
				msg("a player's weapons never hit another player's ship"));
		helper.assertFalse(ShipBolt.mayHit(shipA, ShipBolt.Team.ALLIED, shipB.getId()), msg("nor do their allies'"));

		scene.then(() -> {
			Vec3 from = shipB.position().add(ShipBody.local(0, 1.1, 4.2, shipB.getYRot()));
			Vec3 toward = target.getBoundingBox().getCenter().subtract(from).normalize();
			bolt[0] = scene.track(ShipBolt.fire(scene.level, shipB, b.getUUID(), ShipBolt.Team.PLAYERS, ShipBolt.Style.INDEPENDENT, from,
					toward.scale(live.boltSpeed), live.cannonDamage, live.boltRange));
			helper.assertTrue(ShipWeapons.launch(scene.level, shipB, b.getUUID(), b.getUUID(), target, live), msg("B's missile flies"));
			List<ShipMissile> flying = scene.level.getEntitiesOfClass(ShipMissile.class, helper.getBounds().inflate(16), m -> m.shipId() == shipB.getId());
			helper.assertTrue(flying.size() == 1, msg("one missile, found " + flying.size()));
			missile[0] = scene.track(flying.getFirst());
		}).await("the bolt and the missile have struck", 40, () -> bolt[0].isRemoved() && missile[0].isRemoved()).then(() -> {
			helper.assertTrue(target.shield() + target.getHealth() < targetPoints, msg("B's fire went through A's ship and struck the raider"));

			for (ServerPlayer p : List.of(a, b)) {
				ShipRecord record = scene.record(p);
				helper.assertTrue(ShipCombat.shieldPoints(p) == ShipCombat.maxShield(record, live) && record.hull() == 100 && !ShipCombat.underFire(p),
						msg(p.getPlainTextName() + "'s ship took nothing"));
			}

			helper.assertTrue(SpaceEncounters.startPatrolAt(a, scene.at(12.5, 16, 12.5), 2), msg("a patrol finds A"));
			Starfighter enemy = scene.hold(a).getFirst();
			helper.assertTrue(ShipBolt.mayHit(shipB, ShipBolt.Team.HOSTILE, enemy.getId()), msg("a fighter's guns may point at any player's ship"));
			helper.assertTrue(ShipBolt.lands(scene.level, shipA, ShipBolt.Team.HOSTILE, enemy.getId()), msg("A's patrol's fire lands on A's ship"));
			helper.assertFalse(ShipBolt.lands(scene.level, shipB, ShipBolt.Team.HOSTILE, enemy.getId()), msg("never on B's, which is not in A's battle"));
		}).run();
	}

	// ---------------------------------------------------------------- patrols and encounters (5.6)

	/** A patrol finds only an armed ship, on the check tick, by chance, outside the gap; a fighter of no encounter fires only on armed ships. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void patrolsOnlyForArmedShips(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		long now = 1_000_000L;
		long check = b.patrolCheckTicks;
		long longAgo = now - b.patrolGapTicks;
		float lucky = b.patrolChance / 2f;

		helper.assertTrue(SpaceRules.patrolFinds(true, check, now, longAgo, lucky, b), msg("an armed ship, on the check, with luck"));
		helper.assertFalse(SpaceRules.patrolFinds(false, check, now, longAgo, lucky, b), msg("an unarmed courier is never found"));
		helper.assertTrue(SpaceRules.patrolFinds(true, check * 3, now, longAgo, lucky, b), msg("every check tick"));
		helper.assertFalse(SpaceRules.patrolFinds(true, check - 1, now, longAgo, lucky, b), msg("only on a check tick"));
		helper.assertFalse(SpaceRules.patrolFinds(true, 0, now, longAgo, lucky, b), msg("not before the ship has flown armed"));
		helper.assertFalse(SpaceRules.patrolFinds(true, check, now, longAgo, b.patrolChance, b), msg("a roll at the chance misses"));
		SpaceBalance off = new SpaceBalance();
		off.patrols = false;
		helper.assertFalse(SpaceRules.patrolFinds(true, check, now, longAgo, lucky, off), msg("patrols switched off"));
		helper.assertTrue(b.patrolCheckTicks == 2400 && b.patrolChance == 0.25f, msg("one chance in four every two minutes armed"));

		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerPlayer armedOwner = scene.player(STAND_A);
			ServerPlayer unarmedOwner = scene.player(STAND_B);
			ShipBody armed = scene.give(armedOwner, PAD_A, EAST);
			ShipBody unarmed = scene.give(unarmedOwner, PAD_B, WEST);
			scene.flying(armedOwner, ShipRecord.UPGRADE_CANNONS);
			scene.flying(unarmedOwner, 0);
			Starfighter stray = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(12.5, 4, 20.5), Side.HOSTILE, true, SpaceBalance.get());
			helper.assertTrue(SpaceEncounters.participants(stray) == null, msg("a fighter of no encounter (a spawn egg's)"));
			helper.assertTrue(ShipBolt.lands(scene.level, armed, ShipBolt.Team.HOSTILE, stray.getId()), msg("its fire reaches an armed ship"));
			helper.assertFalse(ShipBolt.lands(scene.level, unarmed, ShipBolt.Team.HOSTILE, stray.getId()), msg("never an unarmed courier"));
		});
	}

	/** A patrol destroyed pays its kills and the bonus; the next can come only {@code patrolGapTicks} after it ended. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void patrolGapRespected(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.awakened(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS);
			helper.assertTrue(SpaceEncounters.startPatrolAt(owner, scene.at(12.5, 14, 12.5), 2), msg("a patrol finds the ship"));
			helper.assertFalse(SpaceEncounters.startPatrolAt(owner, scene.at(12.5, 14, 12.5), 2), msg("one encounter at a time"));
			SpaceEncounters.Snapshot s = SpaceEncounters.snapshot(owner.getUUID());
			helper.assertTrue(s != null && s.type().equals("PATROL") && s.participants().contains(owner.getUUID()), msg("the owner's patrol"));
			List<Starfighter> patrol = scene.hold(owner);
			helper.assertTrue(patrol.size() == 2, msg("two fighters, got " + patrol.size()));
			SpaceRules.Kind kind = patrol.getFirst().kind();
			int xp = SpaceRules.killXp(kind, live);
			float fatigue = Progression.get(owner).killFatigue.getOrDefault(typeId(patrol.getFirst()), 0f);
			long xpBefore = totalXp(owner);
			int creditsBefore = Credits.count(owner, false);

			for (Starfighter fighter : patrol) {
				scene.destroy(fighter, ship, owner.getUUID());
			}

			helper.assertTrue(SpaceEncounters.lastEnd(owner.getUUID()) == End.CLEARED && SpaceEncounters.snapshot(owner.getUUID()) == null,
					msg("the patrol is cleared, got " + SpaceEncounters.lastEnd(owner.getUUID())));
			long expected = fatigued(xp, fatigue) + fatigued(xp, fatigue + 1f) + live.patrolBonusXp;
			helper.assertTrue(totalXp(owner) - xpBefore == expected, msg("two kills with fatigue and the patrol bonus: expected " + expected + ", got "
					+ (totalXp(owner) - xpBefore)));
			helper.assertTrue(Credits.count(owner, false) - creditsBefore == 2 * SpaceRules.killCredits(kind, live), msg("each kill's credits"));

			Long ended = SpaceEncounters.lastEndTime(owner.getUUID());
			helper.assertTrue(ended != null, msg("the end is timed"));
			helper.assertFalse(SpaceRules.patrolFinds(true, live.patrolCheckTicks, ended + live.patrolGapTicks - 1, ended, 0f, live),
					msg("no patrol within the gap"));
			helper.assertTrue(SpaceRules.patrolFinds(true, live.patrolCheckTicks, ended + live.patrolGapTicks, ended, 0f, live) == (live.patrols
					&& live.patrolChance > 0f), msg("one may come once it is over"));
		}).run();
	}

	/** An encounter ends when its owner logs out (LOGOUT), when their ship leaves its orbit (LEFT) and when the ship is disabled (DISABLED). */
	@GameTest(structure = ARENA, maxTicks = 60, skyAccess = true)
	public void encounterEndsOnLeaveJumpOrDisable(GameTestHelper helper) {
		helper.assertTrue(SpaceEncounters.leaving(false, true, false, false) == End.LOGOUT, msg("the owner gone: LOGOUT"));
		helper.assertTrue(SpaceEncounters.leaving(false, false, true, true) == End.LOGOUT, msg("a logout counts first"));
		helper.assertTrue(SpaceEncounters.leaving(true, false, false, false) == End.LEFT, msg("the ship gone from the orbit: LEFT"));
		helper.assertTrue(SpaceEncounters.leaving(true, false, true, false) == End.LEFT, msg("leaving counts before Peaceful"));
		helper.assertTrue(SpaceEncounters.leaving(true, true, true, false) == End.PEACEFUL, msg("the world turned Peaceful"));
		helper.assertTrue(SpaceEncounters.leaving(true, true, true, true) == null, msg("the Peaceful pass goes on"));
		helper.assertTrue(SpaceEncounters.leaving(true, true, false, false) == null, msg("otherwise it goes on"));

		Scene scene = new Scene(helper);
		ServerPlayer leaver = scene.player(STAND_A);
		ServerPlayer jumper = scene.player(STAND_B);
		ServerPlayer wreck = scene.player(STAND_C);
		scene.give(leaver, PAD_A, EAST);
		ShipBody jumped = scene.give(jumper, PAD_B, WEST);
		scene.give(wreck, PAD_C, SOUTH);
		List<Starfighter> jumperPatrol = new ArrayList<>();

		scene.then(() -> {
			List<List<Starfighter>> patrols = new ArrayList<>();
			double x = 6.5;

			for (ServerPlayer p : List.of(leaver, jumper, wreck)) {
				scene.flying(p, ShipRecord.UPGRADE_CANNONS);
				helper.assertTrue(SpaceEncounters.startPatrolAt(p, scene.at(x, 16, 12.5), 2), msg("a patrol finds " + p.getPlainTextName()));
				patrols.add(scene.hold(p));
				x += 6;
			}

			jumperPatrol.addAll(patrols.get(1));
			M3TestPlayers.remove(leaver);
			helper.assertTrue(SpaceEncounters.lastEnd(leaver.getUUID()) == End.LOGOUT && SpaceEncounters.snapshot(leaver.getUUID()) == null,
					msg("a logout ends it, got " + SpaceEncounters.lastEnd(leaver.getUUID())));
			helper.assertTrue(patrols.getFirst().stream().allMatch(Entity::isRemoved), msg("its fighters go with it"));

			// The ship gone from the level stands for a jump, a dive or a rescue: found at the encounter's next check.
			jumped.discard();

			scene.change(wreck, r -> r.withHull(5));
			ShipCombat.hitShip(wreck, 10f, null);
			helper.assertTrue(SpaceEncounters.lastEnd(wreck.getUUID()) == End.DISABLED, msg("the disabled ship's encounter ends DISABLED"));
			helper.assertTrue(patrols.get(2).stream().allMatch(Entity::isRemoved), msg("its fighters go with it"));
		}).await("the encounter of the ship that left ends", 25, () -> SpaceEncounters.lastEnd(jumper.getUUID()) == End.LEFT).then(() -> {
			helper.assertTrue(SpaceEncounters.snapshot(jumper.getUUID()) == null && jumperPatrol.stream().allMatch(Entity::isRemoved),
					msg("its fighters are gone"));
		}).run();
	}

	// ---------------------------------------------------------------- the blockade (5.7)

	/** Wave one destroyed brings wave two; the gunship's shield holds until its generator falls; the gunship destroyed breaks the blockade. */
	@GameTest(structure = ARENA, maxTicks = 320, skyAccess = true, padding = 16)
	public void blockadeWavesAndGeneratorShield(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		BlockadeGunship[] gunship = new BlockadeGunship[1];

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD);
			SpaceEncounters.startBlockadeAt(owner, scene.at(12.5, 20, 12.5), false, false);
			SpaceEncounters.Snapshot s = SpaceEncounters.snapshot(owner.getUUID());
			helper.assertTrue(s != null && s.type().equals("BLOCKADE") && !s.clan() && s.waveIndex() == 0 && SpaceEncounters.blockadeRunning(owner),
					msg("the blockade battle begins"));
			helper.assertTrue(s.wave().size() == live.blockadeWaves[0], msg("wave one: " + live.blockadeWaves[0] + " interceptors, got " + s.wave().size()));
			gunship[0] = scene.gunship(owner);

			for (Starfighter fighter : scene.hold(owner)) {
				helper.assertTrue(fighter.kind() == SpaceRules.Kind.HYMN_INTERCEPTOR && fighter.side() == Side.HOSTILE, msg("Hymn interceptors, hostile"));
			}
		}).await("the gunship puts out its shield generator", 20, () -> gunship[0].generator(scene.level) != null).then(() -> {
			gunship[0].setNoAi(true);
			GunshipGenerator generator = scene.track(gunship[0].generator(scene.level));
			ServerLevel level = scene.level;
			SpaceEncounters.Snapshot s = scene.snapshot(owner);
			helper.assertTrue(s.members().contains(generator.getUUID()), msg("the generator is part of the blockade"));
			helper.assertTrue(gunship[0].shielded(), msg("the gunship is shielded"));
			float hull = gunship[0].getHealth();
			helper.assertFalse(gunship[0].hurtServer(level, ShipBolt.strikeOf(level, ship, owner.getUUID()), 50f) || gunship[0].getHealth() != hull,
					msg("its shield turns every shot while the generator stands"));

			Set<UUID> first = s.wave();

			for (UUID id : first) {
				scene.destroy(level.getEntity(id), ship, owner.getUUID());
			}

			SpaceEncounters.Snapshot two = scene.snapshot(owner);
			helper.assertTrue(two.waveIndex() == 1 && two.wave().size() == live.blockadeWaves[1] && Collections.disjoint(two.wave(), first),
					msg("wave two (" + live.blockadeWaves[1] + ") comes out when wave one is down, got " + two.wave().size()));
			scene.hold(owner);

			scene.destroy(generator, ship, owner.getUUID());
			helper.assertFalse(gunship[0].shielded(), msg("the generator down, the shield is down"));
			helper.assertTrue(gunship[0].hurtServer(level, ShipBolt.strikeOf(level, ship, owner.getUUID()), 10f) && gunship[0].getHealth() == hull - 10f,
					msg("now the hull takes hits, health " + gunship[0].getHealth()));
			helper.assertTrue(SpaceEncounters.blockadeRunning(owner), msg("the battle goes on until the gunship falls"));

			scene.destroy(gunship[0], ship, owner.getUUID());
			SpaceEncounters.Snapshot broken = scene.snapshot(owner);
			helper.assertTrue(broken.finished() && !SpaceEncounters.blockadeRunning(owner), msg("the gunship destroyed breaks the blockade"));

			for (UUID id : broken.wave()) {
				helper.assertTrue(level.getEntity(id) instanceof Starfighter fighter && fighter.departing(), msg("the surviving interceptors flee"));
			}
		}).await("the broken blockade closes after its wind-down", 200, () -> SpaceEncounters.lastEnd(owner.getUUID()) == End.BROKEN).then(() -> {
			helper.assertTrue(SpaceEncounters.snapshot(owner.getUUID()) == null, msg("nothing of it is left"));
		}).run();
	}

	/** With the clan's standing, Clan Hask's fighters fly for the player against the blockade, and only in that battle. */
	@GameTest(structure = ARENA, maxTicks = 60, skyAccess = true, padding = 16)
	public void blockadeClanFightersSideWithPlayer(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ServerPlayer other = scene.player(STAND_B);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		ShipBody otherShip = scene.give(other, PAD_B, WEST);
		List<Starfighter> clan = new ArrayList<>();
		List<Starfighter> wave = new ArrayList<>();
		List<Starfighter> foreign = new ArrayList<>();
		BlockadeGunship[] gunship = new BlockadeGunship[1];

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD);
			scene.flying(other, ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD);
			SpaceEncounters.startBlockadeAt(owner, scene.at(12.5, 20, 12.5), false, true);
			helper.assertTrue(scene.snapshot(owner).clan(), msg("Clan Hask stands with the owner"));
			gunship[0] = scene.gunship(owner);

			for (Entity member : scene.members(owner)) {
				if (member instanceof Starfighter fighter && fighter.kind() == SpaceRules.Kind.HASK_FIGHTER) {
					helper.assertTrue(fighter.side() == Side.ALLIED, msg("the clan's fighters are allies"));
					clan.add(fighter);
				} else if (member instanceof Starfighter fighter) {
					fighter.setNoAi(true);
					wave.add(fighter);
				}
			}

			helper.assertTrue(clan.size() == live.clanFighters, msg(live.clanFighters + " clan fighters, got " + clan.size()));

			// They arrive at the gunship's flank; brought over the arena, where the chunks tick, to pick their targets.
			for (int i = 0; i < clan.size(); i++) {
				Vec3 at = scene.at(6.5 + 6 * i, 12, 19.5);
				clan.get(i).snapTo(at.x, at.y, at.z, 180f, 0f);
			}

			SpaceEncounters.startBlockadeAt(other, scene.at(12.5, 34, 12.5), false, false);
			helper.assertFalse(scene.snapshot(other).clan(), msg("without the standing, no escort"));

			for (Entity member : scene.members(other)) {
				if (member instanceof Mob mob) {
					mob.setNoAi(true);
				}

				if (member instanceof Starfighter fighter) {
					helper.assertTrue(fighter.kind() != SpaceRules.Kind.HASK_FIGHTER, msg("no Hask fighter in the other battle"));
					foreign.add(fighter);
				}
			}
		}).await("the gunship puts out its shield generator", 20, () -> gunship[0].generator(scene.level) != null).then(() -> {
			gunship[0].setNoAi(true);
			scene.track(gunship[0].generator(scene.level));
		}).await("the escort picks its targets", 20, () -> clan.stream().allMatch(f -> f.targetId() != null)).then(() -> {
			Set<UUID> enemies = new HashSet<>(scene.snapshot(owner).members());
			clan.forEach(f -> enemies.remove(f.getUUID()));

			for (Starfighter fighter : clan) {
				fighter.setNoAi(true);
				helper.assertTrue(enemies.contains(fighter.targetId()), msg("a Hask fighter targets a ship of the blockade, got " + fighter.targetId()));
			}

			ServerLevel level = scene.level;
			Starfighter hask = clan.getFirst();
			Starfighter interceptor = wave.getFirst();
			Starfighter stranger = foreign.getFirst();
			helper.assertTrue(ShipBolt.mayHit(hask, ShipBolt.Team.HOSTILE, interceptor.getId()) && ShipBolt.lands(level, hask, ShipBolt.Team.HOSTILE, interceptor.getId()),
					msg("the interceptors fire on the escort"));
			helper.assertTrue(ShipBolt.mayHit(interceptor, ShipBolt.Team.ALLIED, hask.getId()) && ShipBolt.lands(level, interceptor, ShipBolt.Team.ALLIED, hask.getId()),
					msg("the escort fires on the interceptors"));
			helper.assertTrue(ShipBolt.mayHitGunship(ShipBolt.Team.ALLIED), msg("and on the gunship"));
			helper.assertFalse(ShipBolt.mayHit(hask, ShipBolt.Team.PLAYERS, ship.getId()), msg("the owner's guns never hit the escort"));
			helper.assertFalse(ShipBolt.mayHit(clan.get(1), ShipBolt.Team.ALLIED, hask.getId()), msg("nor does the escort hit its own"));
			helper.assertFalse(ShipBolt.lands(level, stranger, ShipBolt.Team.ALLIED, hask.getId()), msg("the escort keeps to its own battle"));
			helper.assertFalse(ShipBolt.lands(level, hask, ShipBolt.Team.HOSTILE, stranger.getId()), msg("another battle's interceptors never reach it"));
			helper.assertFalse(ShipBolt.lands(level, ship, ShipBolt.Team.HOSTILE, stranger.getId()), msg("nor the owner's ship"));
			helper.assertTrue(ShipBolt.lands(level, otherShip, ShipBolt.Team.HOSTILE, stranger.getId()), msg("only their own pilot's"));
		}).run();
	}

	/** The ship disabled during the blockade resets it: the battle ends DISABLED, and the next try starts again from wave one. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true, padding = 16)
	public void blockadeResetsOnDisable(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		scene.give(owner, PAD_A, EAST);
		BlockadeGunship[] gunship = new BlockadeGunship[1];
		UUID[] first = new UUID[1];

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS);
			scene.change(owner, r -> r.withHull(5));
			SpaceEncounters.startBlockadeAt(owner, scene.at(12.5, 20, 12.5), false, false);
			gunship[0] = scene.gunship(owner);
			scene.hold(owner);
		}).await("the gunship puts out its shield generator", 20, () -> gunship[0].generator(scene.level) != null).then(() -> {
			gunship[0].setNoAi(true);
			scene.track(gunship[0].generator(scene.level));
			SpaceEncounters.Snapshot before = scene.snapshot(owner);
			first[0] = before.gunship();
			ShipCombat.hitShip(owner, 10f, null);
			helper.assertTrue(scene.record(owner).hull() == 0 && ShipCombat.disabled(owner), msg("the ship is disabled"));
			helper.assertTrue(SpaceEncounters.lastEnd(owner.getUUID()) == End.DISABLED && !SpaceEncounters.blockadeRunning(owner)
					&& SpaceEncounters.snapshot(owner.getUUID()) == null, msg("the blockade resets"));

			for (UUID id : before.members()) {
				Entity member = scene.level.getEntity(id);
				helper.assertTrue(member == null || member.isRemoved(), msg("every ship of the battle is gone"));
			}

			// The rescue brings the ship home and it is repaired.
			scene.change(owner, r -> r.withHull(100));
		}).await("the repaired ship is no longer disabled", 5, () -> !ShipCombat.disabled(owner)).then(() -> {
			SpaceEncounters.startBlockadeAt(owner, scene.at(12.5, 20, 12.5), false, false);
			SpaceEncounters.Snapshot again = scene.snapshot(owner);
			helper.assertTrue(again.waveIndex() == 0 && again.wave().size() == live.blockadeWaves[0] && again.destroyed() == 0
					&& again.gunship() != null && !again.gunship().equals(first[0]), msg("the next try starts from wave one with a new gunship"));
			scene.gunship(owner).setNoAi(true);
			scene.hold(owner);
		}).run();
	}

	/** On Peaceful the blockade is a pass: neutral interceptors, a passive gunship, no fight; they turn away and the way opens. */
	@GameTest(structure = ARENA, maxTicks = 300, skyAccess = true, padding = 16)
	public void peacefulBlockadeIsAPass(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		BlockadeGunship[] gunship = new BlockadeGunship[1];
		List<Starfighter> wave = new ArrayList<>();

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD);
			// The clan asked for too: a pass has no escort.
			SpaceEncounters.startBlockadeAt(owner, scene.at(12.5, 20, 12.5), true, true);
			SpaceEncounters.Snapshot s = scene.snapshot(owner);
			helper.assertTrue(s.type().equals("PASS") && !s.clan() && SpaceEncounters.blockadeRunning(owner), msg("the Peaceful pass begins"));
			gunship[0] = scene.gunship(owner);
			helper.assertTrue(gunship[0].passive(), msg("its gunship holds its fire"));
			wave.addAll(scene.hold(owner));
			helper.assertTrue(wave.size() == live.blockadeWaves[0], msg("the interceptors fly out all the same"));

			for (Starfighter fighter : wave) {
				helper.assertTrue(fighter.side() == Side.NEUTRAL, msg("and they are neutral"));
			}

			helper.assertFalse(SpaceEncounters.joinCoop(owner, owner), msg("nobody joins a pass"));
		}).await("the gunship puts out its shield generator", 20, () -> gunship[0].generator(scene.level) != null).then(() -> {
			gunship[0].setNoAi(true);
			ServerLevel level = scene.level;
			GunshipGenerator generator = scene.track(gunship[0].generator(level));
			float health = generator.getHealth();
			helper.assertFalse(generator.hurtServer(level, ShipBolt.strikeOf(level, ship, owner.getUUID()), 20f) || generator.getHealth() != health,
					msg("the passive gunship's shield covers its generator"));
			helper.assertFalse(gunship[0].hurtServer(level, ShipBolt.strikeOf(level, ship, owner.getUUID()), 20f), msg("and the gunship"));
			Starfighter first = wave.getFirst();
			first.hurtServer(level, ShipBolt.strikeOf(level, ship, owner.getUUID()), 1f);
			helper.assertTrue(first.side() == Side.NEUTRAL && !scene.snapshot(owner).provoked(), msg("fired on, the pass's pilots still never fight"));
		}).await("the interceptors turn away", 80, () -> wave.stream().allMatch(Starfighter::departing))
				.await("the way opens", 220, () -> SpaceEncounters.snapshot(owner.getUUID()) != null && SpaceEncounters.snapshot(owner.getUUID()).finished())
				.then(() -> {
					helper.assertFalse(SpaceEncounters.blockadeRunning(owner), msg("the pass is done"));
					helper.assertTrue(SpaceEncounters.lastEnd(owner.getUUID()) == null, msg("it lingers until the ship is past, it did not end PEACEFUL"));
				}).run();
	}

	// ---------------------------------------------------------------- space objectives (5.8, 5.9)

	/** The run's scan meter fills a tick at a time while a neutral patrol fighter in range sees the ship, drains out of range; full fails the run. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void scanMeterFillsAndDrains(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		helper.assertTrue(SpaceRules.scanStep(0, true, b) == 1 && SpaceRules.scanStep(5, false, b) == 4, msg("one tick up while scanned, one down after"));
		helper.assertTrue(SpaceRules.scanStep(0, false, b) == 0 && SpaceRules.scanStep(b.scanTicks, true, b) == b.scanTicks, msg("between empty and full"));

		Scene scene = new Scene(helper);
		scene.open();
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		String mission = "test_space_scan";
		// The same courier far above, out of the patrol's scanning range.
		CourierShuttle away = scene.standIn(ship.position().add(0, live.scanRange + 16, 0));
		int filled = 10;
		int drained = 4;

		scene.then(() -> {
			scene.flying(owner, 0);
			helper.assertTrue(SpaceMissions.start(owner, mission, MissionDefinition.Objective.SPACE_RUN, Planets.Planet.HOME, false), msg("the run starts"));
			SpaceMissions.movePointForTest(owner, mission, ship.position().add(600, 0, 0));
			SpaceMissions.stepForTest(owner, ship);
			helper.assertTrue(SpaceMissions.scanMeter(owner.getUUID()) == -1f, msg("no patrol scans the ship yet"));
			helper.assertTrue(SpaceMissions.scanPatrolForTest(owner, mission, ship, scene.at(17.5, 1.5, 12.5)), msg("the run's patrol arrives"));
			SpaceEncounters.Snapshot s = scene.snapshot(owner);
			helper.assertTrue(s.type().equals("RUN_PATROL") && mission.equals(s.missionId()), msg("the run's patrol"));

			for (Starfighter fighter : scene.hold(owner)) {
				helper.assertTrue(fighter.side() == Side.NEUTRAL, msg("it shadows the ship, neutral"));
			}

			helper.assertTrue(SpaceMissions.scanMeter(owner.getUUID()) == 0f, msg("the meter starts empty"));
		}).each(filled, t -> {
			SpaceMissions.stepForTest(owner, ship);
			float expected = (t + 1) / (float) live.scanTicks;
			helper.assertTrue(Math.abs(SpaceMissions.scanMeter(owner.getUUID()) - expected) < EPSILON,
					msg("scanned: the meter fills one tick at a time, " + SpaceMissions.scanMeter(owner.getUUID()) + " for " + expected));
		}).each(drained, t -> {
			SpaceMissions.stepForTest(owner, away);
			float expected = (filled - t - 1) / (float) live.scanTicks;
			helper.assertTrue(Math.abs(SpaceMissions.scanMeter(owner.getUUID()) - expected) < EPSILON,
					msg("out of range: it drains, " + SpaceMissions.scanMeter(owner.getUUID()) + " for " + expected));
		}).then(() -> {
			helper.assertTrue(SpaceMissions.isRunning(owner, mission) && SpaceMissions.lastResult(owner.getUUID(), mission) == null, msg("the run goes on"));
		}).each(live.scanTicks - (filled - drained), t -> SpaceMissions.stepForTest(owner, ship)).then(() -> {
			helper.assertTrue(Boolean.FALSE.equals(SpaceMissions.lastResult(owner.getUUID(), mission)), msg("a full scan fails the run"));
			helper.assertTrue(!SpaceMissions.isRunning(owner, mission) && SpaceMissions.scanMeter(owner.getUUID()) == -1f, msg("the run is over"));
			helper.assertTrue(holdCount(scene.record(owner), M5Items.SEALED_CARGO) == 0, msg("the crate is taken"));
			SpaceEncounters.Snapshot s = SpaceEncounters.snapshot(owner.getUUID());
			helper.assertTrue(s != null && s.finished(), msg("the patrol turns away with what it found"));
		}).run();
	}

	/** The run's crate rides in the hold; the ship near the buoy places it, reaching it completes the run; without the crate it fails. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void spaceRunCompletesAtTheBuoy(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.open();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		String mission = "test_space_run";
		Vec3 point = scene.at(12.5, 4, 12.5);
		// Stand-ins for the piloted ship: their middle (2 above their feet) 15 and 8 blocks from the buoy's point.
		CourierShuttle approaching = scene.standIn(point.add(15, -2, 0));
		CourierShuttle arriving = scene.standIn(point.add(8, -2, 0));

		scene.then(() -> {
			scene.flying(owner, 0);
			helper.assertTrue(SpaceMissions.start(owner, mission, MissionDefinition.Objective.SPACE_RUN, Planets.Planet.HOME, false), msg("the run starts"));
			helper.assertTrue(holdCount(scene.record(owner), M5Items.SEALED_CARGO) == 1, msg("the sealed crate is stowed in the hold"));
			SpaceMissions.movePointForTest(owner, mission, point);
			SpaceMissions.stepForTest(owner, approaching);
			NavBuoy buoy = scene.buoy(owner);
			helper.assertTrue(buoy != null && !buoy.reached() && buoy.position().distanceTo(point) < 2, msg("near the point, the buoy is placed"));
			helper.assertTrue(SpaceMissions.lastResult(owner.getUUID(), mission) == null, msg("not yet reached"));

			SpaceMissions.stepForTest(owner, arriving);
			helper.assertTrue(buoy.reached(), msg("the buoy shows it was reached"));
			helper.assertTrue(Boolean.TRUE.equals(SpaceMissions.lastResult(owner.getUUID(), mission)) && !SpaceMissions.isRunning(owner, mission),
					msg("reaching it with the crate completes the run"));
			helper.assertTrue(holdCount(scene.record(owner), M5Items.SEALED_CARGO) == 0, msg("the crate is delivered"));

			helper.assertTrue(SpaceMissions.start(owner, mission, MissionDefinition.Objective.SPACE_RUN, Planets.Planet.HOME, false), msg("a second run"));
			helper.assertTrue(SpaceMissions.lastResult(owner.getUUID(), mission) == null, msg("a new attempt has no result yet"));
			scene.change(owner, r -> r.withHold(List.of()));
			SpaceMissions.movePointForTest(owner, mission, point);
			SpaceMissions.stepForTest(owner, arriving);
			helper.assertTrue(Boolean.FALSE.equals(SpaceMissions.lastResult(owner.getUUID(), mission)), msg("without the crate, reaching the buoy fails the run"));
		}).run();
	}

	/** A group to clear waits at its point; destroying it to the last ship completes the objective and ends its encounter. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void spaceClearCompletesWhenGroupDies(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		String mission = "test_space_clear";

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS);
			helper.assertTrue(SpaceMissions.start(owner, mission, MissionDefinition.Objective.SPACE_CLEAR, Planets.Planet.HOME, false), msg("the objective starts"));
			SpaceData.Run run = SpaceData.get(owner).run(mission).orElse(null);
			helper.assertTrue(run != null && run.clear() && !run.ace() && run.point().isPresent() && SpaceRules.insideOrbit(run.point().get()),
					msg("its group waits at a point in the orbit's band"));
			SpaceMissions.movePointForTest(owner, mission, scene.at(16.5, 3, 12.5));
			SpaceMissions.stepForTest(owner, ship);
			SpaceEncounters.Snapshot s = scene.snapshot(owner);
			helper.assertTrue(s.type().equals("CLEAR") && mission.equals(s.missionId()), msg("the ship near the point meets the group"));
			List<Starfighter> group = scene.hold(owner);
			int min = Math.min(live.clearGroupSize[0], live.clearGroupSize[1]);
			int max = Math.max(live.clearGroupSize[0], live.clearGroupSize[1]);
			helper.assertTrue(group.size() >= min && group.size() <= max, msg("a group of " + min + " to " + max + ", got " + group.size()));

			for (Starfighter fighter : group) {
				helper.assertTrue(fighter.kind() == SpaceRules.Kind.RAIDER && fighter.side() == Side.HOSTILE && !fighter.ace(), msg("pirates outside Tyrvann"));
			}

			for (int i = 0; i < group.size(); i++) {
				helper.assertTrue(SpaceMissions.lastResult(owner.getUUID(), mission) == null, msg("not done while a ship of the group flies"));
				scene.destroy(group.get(i), ship, owner.getUUID());
			}

			helper.assertTrue(Boolean.TRUE.equals(SpaceMissions.lastResult(owner.getUUID(), mission)) && !SpaceMissions.isRunning(owner, mission),
					msg("the last ship down completes it"));
			helper.assertTrue(SpaceEncounters.lastEnd(owner.getUUID()) == End.MISSION && SpaceEncounters.snapshot(owner.getUUID()) == null,
					msg("and ends its encounter, got " + SpaceEncounters.lastEnd(owner.getUUID())));
		}).run();
	}

	/** A bounty's ace has twice its kind's shield and hull, two wingmen, a name; the group destroyed pays the bounty table. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void bountyAceHasDoubleShieldHullAndTwoWingmen(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		helper.assertTrue(b.aceMultiplier == 2f && b.aceWingmen == 2, msg("double, with two wingmen"));
		Vec3 somewhere = helper.absoluteVec(new Vec3(12, 4, 12));

		for (EntityType<? extends Starfighter> type : List.of(SpaceEntities.HYMN_INTERCEPTOR, SpaceEntities.KAJIDIC_RAIDER, SpaceEntities.HASK_FIGHTER)) {
			Starfighter ace = detached(helper.getLevel(), type, somewhere, Side.HOSTILE, true, b);
			Starfighter plain = detached(helper.getLevel(), type, somewhere, Side.HOSTILE, false, b);
			SpaceBalance.Fighter n = SpaceRules.fighter(ace.kind(), b);
			helper.assertTrue(ace.ace() && Math.abs(ace.shieldMax() - n.shield * 2f) < EPSILON && Math.abs(ace.getMaxHealth() - n.hull * 2f) < EPSILON,
					msg(ace.kind() + " ace: shield " + ace.shieldMax() + ", hull " + ace.getMaxHealth()));
			helper.assertTrue(!plain.ace() && plain.shieldMax() == n.shield && Math.abs(plain.getMaxHealth() - n.hull) < EPSILON, msg(ace.kind() + " plain"));
		}

		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		String mission = "test_space_bounty";

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS);
			helper.assertTrue(SpaceMissions.start(owner, mission, MissionDefinition.Objective.SPACE_CLEAR, Planets.Planet.HOME, true), msg("the bounty starts"));
			SpaceMissions.movePointForTest(owner, mission, scene.at(14.5, 3, 12.5));
			SpaceMissions.stepForTest(owner, ship);
			SpaceEncounters.Snapshot s = scene.snapshot(owner);
			helper.assertTrue(s.type().equals("CLEAR") && !s.ace().isEmpty(), msg("the ace's group, under the ace's name"));
			List<Starfighter> group = scene.hold(owner);
			helper.assertTrue(group.size() == 1 + live.aceWingmen, msg("the ace and " + live.aceWingmen + " wingmen, got " + group.size()));
			List<Starfighter> aces = group.stream().filter(Starfighter::ace).toList();
			helper.assertTrue(aces.size() == 1, msg("one ace, got " + aces.size()));
			Starfighter leader = aces.getFirst();
			SpaceBalance.Fighter n = SpaceRules.fighter(leader.kind(), live);
			helper.assertTrue(leader.hasCustomName(), msg("the ace is named"));
			helper.assertTrue(Math.abs(leader.shieldMax() - n.shield * live.aceMultiplier) < EPSILON
					&& Math.abs(leader.getMaxHealth() - Math.max(1f, n.hull * live.aceMultiplier)) < EPSILON, msg("the ace's shield and hull are multiplied"));

			for (Starfighter wingman : group) {
				if (wingman != leader) {
					helper.assertTrue(wingman.shieldMax() == n.shield && Math.abs(wingman.getMaxHealth() - n.hull) < EPSILON && wingman.kind() == leader.kind(),
							msg("its wingmen fly its kind, plain"));
				}
			}

			int sunstone = count(owner, M5Items.SUNSTONE);
			int stormglass = count(owner, M5Items.STORMGLASS_SHARD);

			for (Starfighter fighter : group) {
				scene.destroy(fighter, ship, owner.getUUID());
			}

			helper.assertTrue(Boolean.TRUE.equals(SpaceMissions.lastResult(owner.getUUID(), mission)), msg("the bounty is complete"));
			int gotSunstone = count(owner, M5Items.SUNSTONE) - sunstone;
			int gotStormglass = count(owner, M5Items.STORMGLASS_SHARD) - stormglass;
			helper.assertTrue(gotSunstone == 2 && gotStormglass == 0 || gotSunstone == 0 && gotStormglass == 2,
					msg("the bounty's table: two sunstone or two stormglass, got " + gotSunstone + " and " + gotStormglass));
		}).run();
	}

	// ---------------------------------------------------------------- rewards and parts (5.5, 11.2)

	/** Kill XP (with the M1 fatigue, after the awakening) and credits go to the player whose shot destroyed the fighter, and only to them. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void killXpAndCreditsToShooter(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.now(() -> {
			SpaceBalance live = SpaceBalance.get();
			ServerPlayer shooter = scene.awakened(STAND_A);
			ServerPlayer gunner = scene.player(STAND_B);
			ShipBody ship = scene.give(shooter, PAD_A, EAST);
			Starfighter first = scene.fighter(SpaceEntities.HYMN_INTERCEPTOR, scene.at(12.5, 3, 4.5), Side.HOSTILE, true, live);
			Starfighter second = scene.fighter(SpaceEntities.HYMN_INTERCEPTOR, scene.at(16.5, 3, 4.5), Side.HOSTILE, true, live);
			Starfighter raider = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(12.5, 3, 20.5), Side.HOSTILE, true, live);
			Starfighter nobodys = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(16.5, 3, 20.5), Side.HOSTILE, true, live);
			Starfighter gunners = scene.fighter(SpaceEntities.HYMN_INTERCEPTOR, scene.at(20.5, 3, 4.5), Side.HOSTILE, true, live);
			String interceptor = typeId(first);
			ForceData data = Progression.get(shooter);
			float fi = data.killFatigue.getOrDefault(interceptor, 0f);
			float fr = data.killFatigue.getOrDefault(typeId(raider), 0f);
			long xp = totalXp(shooter);
			int credits = Credits.count(shooter, false);

			scene.destroy(first, ship, shooter.getUUID());
			long expected = fatigued(SpaceRules.killXp(SpaceRules.Kind.HYMN_INTERCEPTOR, live), fi);
			int paid = SpaceRules.killCredits(SpaceRules.Kind.HYMN_INTERCEPTOR, live);
			helper.assertTrue(totalXp(shooter) - xp == expected && expected == 60, msg("an interceptor: 60 XP, got " + (totalXp(shooter) - xp)));
			helper.assertTrue(Credits.count(shooter, false) - credits == paid && paid == 10, msg("and 10 credits"));

			scene.destroy(second, ship, shooter.getUUID());
			expected += fatigued(SpaceRules.killXp(SpaceRules.Kind.HYMN_INTERCEPTOR, live), fi + 1f);
			paid += SpaceRules.killCredits(SpaceRules.Kind.HYMN_INTERCEPTOR, live);
			helper.assertTrue(totalXp(shooter) - xp == expected && expected < 120, msg("the second one tires: " + expected + " in all"));
			helper.assertTrue(Math.abs(Progression.get(shooter).killFatigue.getOrDefault(interceptor, 0f) - (fi + 2f)) < EPSILON, msg("each kill adds fatigue"));

			scene.destroy(raider, ship, shooter.getUUID());
			expected += fatigued(SpaceRules.killXp(SpaceRules.Kind.RAIDER, live), fr);
			paid += SpaceRules.killCredits(SpaceRules.Kind.RAIDER, live);
			helper.assertTrue(totalXp(shooter) - xp == expected, msg("a raider: 80 XP of its own fatigue"));
			helper.assertTrue(Credits.count(shooter, false) - credits == paid && SpaceRules.killCredits(SpaceRules.Kind.RAIDER, live) == 15, msg("and 15 credits"));

			scene.destroy(nobodys, ship, null);
			helper.assertTrue(totalXp(shooter) - xp == expected && Credits.count(shooter, false) - credits == paid, msg("a kill with no shooter pays nobody"));

			long gunnerXp = totalXp(gunner);
			int gunnerCredits = Credits.count(gunner, false);
			scene.destroy(gunners, ship, gunner.getUUID());
			helper.assertTrue(totalXp(gunner) == gunnerXp, msg("no XP before the awakening"));
			helper.assertTrue(Credits.count(gunner, false) - gunnerCredits == SpaceRules.killCredits(SpaceRules.Kind.HYMN_INTERCEPTOR, live),
					msg("but the credits all the same"));
			helper.assertTrue(totalXp(shooter) - xp == expected && Credits.count(shooter, false) - credits == paid, msg("the pilot gets nothing for the gunner's kill"));
		});
	}

	/** The Spindle Capacitor: +20 shield and recharge ×1.5 (11.2). */
	@GameTest(structure = ARENA, maxTicks = 140)
	public void capacitorShieldAndRecharge(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		helper.assertTrue(SpaceRules.maxShield(true, false, false, b) == 40f && SpaceRules.maxShield(true, false, true, b) == 60f,
				msg("a courier: 40, with the capacitor 60"));
		helper.assertTrue(SpaceRules.maxShield(true, true, false, b) == 60f && SpaceRules.maxShield(true, true, true, b) == 80f,
				msg("an interceptor: 60, with the capacitor 80"));
		helper.assertTrue(SpaceRules.maxShield(false, true, true, b) == 0f, msg("no shield upgrade, no shield"));
		helper.assertTrue(SpaceRules.rechargePerSecond(false, b) == 4f && SpaceRules.rechargePerSecond(true, b) == 6f, msg("4 a second, 6 with the capacitor"));
		helper.assertTrue(SpaceRules.recharge(10f, 40f, b.shieldDelay - 1, b.shieldDelay, 4f) == 10f, msg("nothing during the delay"));
		helper.assertTrue(Math.abs(SpaceRules.recharge(10f, 40f, b.shieldDelay, b.shieldDelay, 4f) - 10.2f) < EPSILON, msg("a twentieth of a second's worth a tick"));
		helper.assertTrue(SpaceRules.recharge(39.9f, 40f, 1000, b.shieldDelay, 6f) == 40f, msg("never past full"));

		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer fitted = scene.player(STAND_A);
		ServerPlayer plain = scene.player(STAND_B);
		scene.give(fitted, PAD_A, EAST);
		scene.give(plain, PAD_B, WEST);
		float[] max = new float[2];
		float[] base = new float[2];

		scene.then(() -> {
			max[0] = ShipCombat.maxShield(scene.flying(fitted, ShipRecord.UPGRADE_SHIELD | ShipRecord.PART_SPINDLE_CAPACITOR), live);
			max[1] = ShipCombat.maxShield(scene.flying(plain, ShipRecord.UPGRADE_SHIELD), live);
			helper.assertTrue(max[0] == max[1] + live.capacitorShield, msg("the capacitor adds " + live.capacitorShield + ", got " + max[0] + " and " + max[1]));
			ShipCombat.hitShip(fitted, 30f, null);
			ShipCombat.hitShip(plain, 30f, null);
			base[0] = ShipCombat.shieldPoints(fitted);
			base[1] = ShipCombat.shieldPoints(plain);
		}).idle(live.shieldDelay - 10).then(() -> {
			helper.assertTrue(ShipCombat.shieldPoints(fitted) == base[0] && ShipCombat.shieldPoints(plain) == base[1], msg("both wait out the delay"));
		}).idle(48).then(() -> {
			float fittedGain = ShipCombat.shieldPoints(fitted) - base[0];
			float plainGain = ShipCombat.shieldPoints(plain) - base[1];
			helper.assertTrue(plainGain > 0f && fittedGain > 0f, msg("both recharge"));
			helper.assertTrue(Math.abs(fittedGain - plainGain * live.capacitorRechargeMult) < 0.01f,
					msg("the capacitor's recharges " + live.capacitorRechargeMult + " times as fast: " + fittedGain + " against " + plainGain));
			helper.assertTrue(ShipCombat.shieldPoints(fitted) < max[0] && ShipCombat.shieldPoints(plain) < max[1], msg("neither is full yet"));
		}).run();
	}

	// ---------------------------------------------------------------- the Silent Spindle and the orbit floor (5.1, 5.7)

	/** The station is placed once per server, when someone first comes near; its vault opens once per player per in-game day. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void spindlePlacedOnceAndVaultOncePerDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldSites.SiteRecord station = new WorldSites.SiteRecord(SiteKind.DERELICT_STATION, level.dimension(), new BlockPos(420, 150, -380), Rotation.NONE,
				SilentSpindle.TEMPLATE, null, 0L);
		WorldSites.SiteRecord hymnal = new WorldSites.SiteRecord(SiteKind.HYMNAL, level.dimension(), new BlockPos(100, 64, 100), Rotation.NONE,
				OldRepublicMod.id("test/hymnal"), null, 0L);
		helper.assertTrue(SilentSpindle.placeNow(List.of(), true), msg("someone near and no station yet: it is placed"));
		helper.assertFalse(SilentSpindle.placeNow(List.of(), false), msg("nobody near: not yet"));
		helper.assertFalse(SilentSpindle.placeNow(List.of(hymnal, station), true), msg("once per server"));
		helper.assertTrue(SilentSpindle.placeNow(List.of(hymnal), true), msg("other sites do not count"));
		helper.assertTrue(SilentSpindle.recordIn(List.of(hymnal, station)) == station && SilentSpindle.recordIn(List.of(hymnal)) == null,
				msg("the station's record is the derelict station's"));
		helper.assertTrue(SpaceRules.vaultOpens(-1, 0) && SpaceRules.vaultOpens(7, 8) && !SpaceRules.vaultOpens(7, 7) && !SpaceRules.vaultOpens(8, 7),
				msg("once per in-game day"));

		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerPlayer p = scene.player(STAND_A);
			BlockPos vault = helper.absolutePos(new BlockPos(12, 1, 12));
			long day = 10;
			helper.assertTrue(SpaceData.get(p).vaultDay() < day, msg("never opened"));
			helper.assertTrue(SilentSpindle.openVault(p, vault, day), msg("the first visit of the day opens it"));
			int cores = count(p, M4Items.POWER_CORE);
			int plates = count(p, M4Items.ALLOY_PLATE);
			int fragments = count(p, M4Items.MASTERWORK_FRAGMENT);
			helper.assertTrue(cores == 1 && plates >= 2 && plates <= 3 && fragments >= 1 && fragments <= 2,
					msg("a power core, 2-3 alloy plates and a masterwork fragment or two, got " + cores + ", " + plates + ", " + fragments));
			helper.assertTrue(SpaceData.get(p).vaultDay() == day, msg("the day is kept"));
			helper.assertFalse(SilentSpindle.openVault(p, vault, day), msg("a second visit the same day finds it empty"));
			helper.assertTrue(count(p, M4Items.POWER_CORE) == cores && count(p, M4Items.ALLOY_PLATE) == plates && count(p, M4Items.MASTERWORK_FRAGMENT) == fragments,
					msg("and gives nothing"));
			helper.assertTrue(SilentSpindle.openVault(p, vault, day + 1), msg("the next day it opens again"));
			helper.assertTrue(count(p, M4Items.POWER_CORE) == cores + 1 && SpaceData.get(p).vaultDay() == day + 1, msg("with a new roll"));
		});
	}

	/** A player caught below the orbit floor goes back aboard their ship; with no ship to board, beside its landing spot, drifting meanwhile. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void fallBelowOrbitFloorReturnsToShip(GameTestHelper helper) {
		int floor = new SpaceBalance().orbitCatchY;
		helper.assertTrue(floor == -56, msg("the floor is at -56"));
		helper.assertTrue(SpaceRules.caughtByFloor(true, floor - 1, floor, true, false, false, false), msg("below the floor in an orbit: caught"));
		helper.assertFalse(SpaceRules.caughtByFloor(true, floor, floor, true, false, false, false), msg("at the floor: not yet"));
		helper.assertFalse(SpaceRules.caughtByFloor(false, floor - 1, floor, true, false, false, false), msg("only in an orbit"));
		helper.assertFalse(SpaceRules.caughtByFloor(true, floor - 1, floor, false, false, false, false), msg("not the dead"));
		helper.assertFalse(SpaceRules.caughtByFloor(true, floor - 1, floor, true, true, false, false), msg("not a spectator"));
		helper.assertFalse(SpaceRules.caughtByFloor(true, floor - 1, floor, true, false, true, false), msg("not one flying"));
		helper.assertFalse(SpaceRules.caughtByFloor(true, floor - 1, floor, true, false, false, true), msg("not one riding"));

		Scene scene = new Scene(helper);
		scene.open();
		ServerPlayer owner = scene.player(STAND_C);
		ShipBody ship = scene.give(owner, PAD_A, EAST);
		Vec3 pad = Vec3.atBottomCenterOf(helper.absolutePos(PAD_A));

		scene.then(() -> {
			OrbitFloor.catchPlayer(scene.server, owner, scene.level.getGameTime());
			helper.assertTrue(owner.getVehicle() == ship, msg("caught: back aboard their own ship"));
			owner.stopRiding();
			Vec3 far = scene.at(20.5, 2, 21.5);
			owner.teleportTo(far.x, far.y, far.z);
			ship.discard();
			OrbitFloor.catchPlayer(scene.server, owner, scene.level.getGameTime());
			helper.assertTrue(!owner.isPassenger() && owner.hasEffect(MobEffects.SLOW_FALLING), msg("no ship to board: they drift with slow falling"));
		}).await("set down beside the ship's landing spot", 40, () -> {
			Vec3 at = owner.position();
			return Math.hypot(at.x - pad.x, at.z - pad.z) < 9 && Math.abs(at.y - pad.y) < 1.01;
		}).then(() -> {
			helper.assertFalse(owner.isPassenger(), msg("standing on the ground"));
		}).run();
	}

	/** The Spindle's template: its size, its sidecar, every anchor inside it, the log terminal and the vault in place. */
	@GameTest(maxTicks = 20)
	public void spindleTemplateHasSidecarAnchorsInsideSize(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Identifier id = SilentSpindle.TEMPLATE;
		StructureTemplate template = level.getStructureManager().get(id).orElse(null);
		helper.assertTrue(template != null, msg("missing structure template " + id));
		Vec3i size = template.getSize();
		helper.assertTrue(size.equals(new Vec3i(64, 40, 48)), msg(id + " has size " + size + ", expected 64 x 40 x 48"));
		helper.assertTrue(SiteAnchors.templates().contains(id), msg(id + " has no sidecar"));

		for (SiteAnchors.Anchor anchor : List.of(SiteAnchors.Anchor.SPINDLE_DECK, SiteAnchors.Anchor.SPINDLE_LOG, SiteAnchors.Anchor.SPINDLE_VAULT,
				SiteAnchors.Anchor.SPINDLE_POST_1, SiteAnchors.Anchor.SPINDLE_POST_2, SiteAnchors.Anchor.SPINDLE_POST_3, SiteAnchors.Anchor.SPINDLE_POST_4)) {
			List<BlockPos> cells = SiteAnchors.of(id, anchor);
			helper.assertTrue(cells.size() == 1, msg(id + " names " + anchor + " once, got " + cells.size()));
			BlockPos cell = cells.getFirst();
			helper.assertTrue(cell.getX() >= 0 && cell.getY() >= 0 && cell.getZ() >= 0 && cell.getX() < size.getX() && cell.getY() < size.getY()
					&& cell.getZ() < size.getZ(), msg(anchor + " " + cell.toShortString() + " lies outside " + size));
		}

		helper.assertTrue(holds(template, SiteAnchors.of(id, SiteAnchors.Anchor.SPINDLE_LOG).getFirst(), Blocks.OBSERVER), msg("the log terminal stands at SPINDLE_LOG"));
		helper.assertTrue(holds(template, SiteAnchors.of(id, SiteAnchors.Anchor.SPINDLE_VAULT).getFirst(), Blocks.CHEST), msg("the vault stands at SPINDLE_VAULT"));
		helper.succeed();
	}

	/** Another player's armed ship joins an owner's battle; its kills count for the owner's objective and pay the one who shot. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void coopKillsCountForOwner(GameTestHelper helper) {
		SpaceBalance b = new SpaceBalance();
		double range = b.coopRange;
		helper.assertTrue(SpaceRules.coopJoins(true, (range - 1) * (range - 1), b), msg("an armed ship near the owner's joins"));
		helper.assertFalse(SpaceRules.coopJoins(false, 1, b), msg("an unarmed one never does"));
		helper.assertFalse(SpaceRules.coopJoins(true, range * range, b), msg("nor one at the range"));
		helper.assertTrue(SpaceRules.coopLeaves(4 * range * range + 1, b) && !SpaceRules.coopLeaves(4 * range * range, b)
				&& !SpaceRules.coopLeaves((range + 10) * (range + 10), b), msg("a pilot leaves only beyond twice the range"));

		Scene scene = new Scene(helper);
		SpaceBalance live = SpaceBalance.get();
		ServerPlayer owner = scene.player(STAND_A);
		ServerPlayer pilot = scene.awakened(STAND_B);
		ShipBody ownShip = scene.give(owner, PAD_A, EAST);
		ShipBody pilotShip = scene.give(pilot, PAD_B, WEST);
		String mission = "test_space_coop";

		scene.then(() -> {
			scene.flying(owner, ShipRecord.UPGRADE_CANNONS);
			scene.flying(pilot, ShipRecord.UPGRADE_CANNONS);
			helper.assertTrue(SpaceMissions.start(owner, mission, MissionDefinition.Objective.SPACE_CLEAR, Planets.Planet.HOME, false), msg("the owner's objective"));
			SpaceMissions.movePointForTest(owner, mission, scene.at(12.5, 4, 20.5));
			SpaceMissions.stepForTest(owner, ownShip);
			List<Starfighter> group = scene.hold(owner);
			helper.assertTrue(SpaceEncounters.joinCoop(owner, pilot) && scene.snapshot(owner).participants().contains(pilot.getUUID()), msg("the pilot joins"));
			int ownerCredits = Credits.count(owner, false);
			int pilotCredits = Credits.count(pilot, false);
			long pilotXp = totalXp(pilot);
			int bounty = 0;

			for (Starfighter fighter : group) {
				bounty += SpaceRules.killCredits(fighter.kind(), live);
				scene.destroy(fighter, pilotShip, pilot.getUUID());
			}

			helper.assertTrue(Boolean.TRUE.equals(SpaceMissions.lastResult(owner.getUUID(), mission)), msg("the pilot's kills complete the owner's objective"));
			helper.assertTrue(SpaceMissions.lastResult(pilot.getUUID(), mission) == null, msg("the pilot had none of their own"));
			helper.assertTrue(Credits.count(pilot, false) - pilotCredits == bounty && totalXp(pilot) > pilotXp, msg("the one who shot is paid"));
			helper.assertTrue(Credits.count(owner, false) == ownerCredits, msg("the owner is paid nothing for kills they did not make"));
		}).run();
	}

	// ---------------------------------------------------------------- flight (5.5)

	/** A fighter in the overworld sky circles its mark, makes an attack run, breaks away: never faster than its kind, never far off. */
	@GameTest(structure = ARENA, maxTicks = 300, skyAccess = true)
	public void fighterFlightAndRuns(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		SpaceBalance slow = new SpaceBalance();
		SpaceBalance.Fighter numbers = new SpaceBalance.Fighter();
		numbers.speed = 0.25;
		numbers.shield = 10f;
		numbers.hull = 20f;
		// It fires, but its bolts carry nothing (and hostile bolts never hit a neutral fighter).
		numbers.damage = 0f;
		numbers.rate = 1f;
		numbers.burst = 1;
		slow.hymnInterceptor = numbers;
		Starfighter mark = scene.fighter(SpaceEntities.KAJIDIC_RAIDER, scene.at(12.5, 16, 12.5), Side.NEUTRAL, true, SpaceBalance.get());
		Vec3 centre = mark.getBoundingBox().getCenter();
		Starfighter fighter = SpaceEntities.HYMN_INTERCEPTOR.create(scene.level, EntitySpawnReason.EVENT);
		helper.assertTrue(fighter != null, msg("the interceptor is created"));
		Vec3 start = scene.at(12.5, 17, 7.5);
		fighter.snapTo(start.x, start.y, start.z, SpaceRules.yawOf(new Vec3(1, 0, 0)), 0f);
		fighter.setup(Side.HOSTILE, false, slow);
		// A small sky: its circle, height and break distance at 8 % of an interceptor's; its timing and turn rate its own.
		fighter.scaleFlight(0.08);
		fighter.engage(mark);
		fighter.launchAlong(new Vec3(1, 0, 0), 0.5);
		helper.assertTrue(scene.level.addFreshEntity(fighter), msg("the interceptor is added"));
		scene.track(fighter);
		List<Starfighter.Phase> phases = new ArrayList<>(List.of(fighter.phase()));
		float markPoints = mark.shield() + mark.getHealth();

		scene.await("a circle, an attack run and a break", 280, () -> {
			double speed = fighter.getDeltaMovement().length();
			helper.assertTrue(speed <= numbers.speed + EPSILON, msg("never faster than its kind's speed, got " + speed));
			helper.assertTrue(fighter.getY() >= centre.y - 6, msg("it never dives away below its mark, y " + (fighter.getY() - centre.y)));
			helper.assertTrue(fighter.position().distanceTo(centre) <= 24, msg("it stays on its mark, " + fighter.position().distanceTo(centre)));
			helper.assertTrue(mark.getUUID().equals(fighter.targetId()), msg("it keeps its mark"));
			Starfighter.Phase now = fighter.phase();

			if (now != phases.getLast()) {
				phases.add(now);
			}

			int run = phases.indexOf(Starfighter.Phase.RUN);
			return run > 0 && phases.lastIndexOf(Starfighter.Phase.BREAK) > run;
		}).then(() -> {
			helper.assertTrue(phases.getFirst() == Starfighter.Phase.CIRCLE, msg("it began circling, phases " + phases));
			helper.assertTrue(mark.shield() + mark.getHealth() == markPoints, msg("its fire never reaches a neutral fighter"));
		}).run();
	}

	// ---------------------------------------------------------------- helpers

	/** A fighter set up with {@code b}, its middle at {@code middle}, never added to the level. */
	private static <F extends Starfighter> F detached(ServerLevel level, EntityType<F> type, Vec3 middle, Side side, boolean ace, SpaceBalance b) {
		F fighter = type.create(level, EntitySpawnReason.EVENT);

		if (fighter == null) {
			throw new IllegalStateException("could not create " + type);
		}

		fighter.setup(side, ace, b);
		fighter.snapTo(middle.x, middle.y - fighter.getBbHeight() / 2, middle.z, 0f, 0f);
		return fighter;
	}

	/** The point {@code distance} from {@code eye}, {@code degrees} off +X. */
	private static Vec3 ahead(Vec3 eye, double degrees, double distance) {
		double rad = Math.toRadians(degrees);
		return eye.add(Math.cos(rad) * distance, 0, Math.sin(rad) * distance);
	}

	private static boolean holds(StructureTemplate template, BlockPos cell, Block block) {
		// With BlockPos.ZERO and default settings, filterBlocks gives template-local positions.
		return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).stream().anyMatch(info -> info.pos().equals(cell));
	}

	private static String typeId(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	/** Every XP point {@code p} ever earned: this level's and the levels' before it. */
	private static long totalXp(ServerPlayer p) {
		ForceData data = Progression.get(p);
		long total = data.xp;

		for (int level = 1; level < data.level; level++) {
			total += Progression.xpToNext(level);
		}

		return total;
	}

	/** A kill's {@code xp} after {@code fatigue} earlier kills of its type, as M1's anti-grind rule pays it. */
	private static long fatigued(int xp, float fatigue) {
		ProgressionBalance b = Progression.balance();
		float multiplier = Math.max(b.fatigueFloor, (float) Math.pow(b.fatigueFactor, fatigue));
		return Math.max(1, Math.round(xp * multiplier));
	}

	private static int holdCount(ShipRecord record, Item item) {
		int total = 0;

		for (ItemStack stack : record.hold()) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static int count(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/**
	 * One test's world: the players, ship bodies, fighters and records it made, and the shots and buoys
	 * of its ships and players, removed together when it ends, pass or fail (records only of its own
	 * owners, so tests running alongside keep theirs). Its steps are polled once per tick by a single
	 * {@link GameTestHelper#onEachTick} registration.
	 */
	private static final class Scene {
		/** One step: polled with the ticks it has been running, true when done. */
		@FunctionalInterface
		private interface Step {
			boolean poll(int ticks);
		}

		final GameTestHelper helper;
		final ServerLevel level;
		final MinecraftServer server;
		private final List<ServerPlayer> players = new ArrayList<>();
		private final List<UUID> owners = new ArrayList<>();
		private final List<Entity> entities = new ArrayList<>();
		private final List<Step> steps = new ArrayList<>();
		private int index;
		private int ticks;
		private boolean finished;

		Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.server = level.getServer();
		}

		/** A listed player standing at {@code rel} (creative). */
		ServerPlayer player(BlockPos rel) {
			ServerPlayer player = M3TestPlayers.listed(helper, rel);
			players.add(player);
			return player;
		}

		/** A listed player past the awakening, so kills pay XP. */
		ServerPlayer awakened(BlockPos rel) {
			ServerPlayer player = player(rel);
			ForceData data = Progression.get(player);
			data.questStage = QuestStage.FOCUS.ordinal();
			Progression.commit(player, data);
			return player;
		}

		<E extends Entity> E track(E entity) {
			if (!entities.contains(entity)) {
				entities.add(entity);
			}

			return entity;
		}

		/** The absolute point at arena-relative ({@code x}, {@code y}, {@code z}). */
		Vec3 at(double x, double y, double z) {
			return helper.absoluteVec(new Vec3(x, y, z));
		}

		/** Clears the arena above its floor (the dais, pylons and columns), so shots, scans and players move freely. */
		void open() {
			for (int x = 0; x < 24; x++) {
				for (int z = 0; z < 24; z++) {
					for (int y = 1; y < 7; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
					}
				}
			}
		}

		/** The command path's repaired ship for {@code owner} at {@code pad}, facing {@code yaw}: READY, full hull and tank. */
		ShipBody give(ServerPlayer owner, BlockPos pad, float yaw) {
			if (!owners.contains(owner.getUUID())) {
				owners.add(owner.getUUID());
			}

			Ships.giveAt(owner, helper.absolutePos(pad), yaw);
			ShipBody ship = Ships.ship(server, owner.getUUID());
			helper.assertTrue(ship != null, msg("the ship body is placed"));
			return ship;
		}

		ShipRecord change(ServerPlayer owner, UnaryOperator<ShipRecord> change) {
			return Ships.update(server, owner.getUUID(), change).orElseThrow(() -> helper.assertionException(msg("no ship record for " + owner.getPlainTextName())));
		}

		/** The ship in flight as far as the space code can tell (its record {@code FLYING}; no helm on the test server), with {@code upgrades} added. */
		ShipRecord flying(ServerPlayer owner, int upgrades) {
			return change(owner, r -> r.withUpgrades(r.upgrades() | upgrades).withState(ShipState.FLYING));
		}

		ShipRecord record(ServerPlayer owner) {
			return ShipRegistry.of(server, owner.getUUID()).orElseThrow(() -> helper.assertionException(msg("the ship record of " + owner.getPlainTextName() + " is gone")));
		}

		/** A fighter of {@code type} with its middle at {@code middle}, set up with {@code b}; held still when {@code still}. */
		<F extends Starfighter> F fighter(EntityType<F> type, Vec3 middle, Side side, boolean still, SpaceBalance b) {
			F fighter = type.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(fighter != null, msg("the fighter is created"));
			fighter.snapTo(middle.x, middle.y - fighter.getBbHeight() / 2, middle.z, 0f, 0f);
			fighter.setup(side, false, b);
			fighter.setNoAi(still);
			helper.assertTrue(level.addFreshEntity(fighter), msg("the fighter is added"));
			return track(fighter);
		}

		/** A courier body standing in for a piloted ship at {@code feet}, never added to the level (so never ticked). */
		CourierShuttle standIn(Vec3 feet) {
			CourierShuttle body = ShipEntities.COURIER_SHUTTLE.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(body != null, msg("the stand-in is created"));
			body.setPos(feet.x, feet.y, feet.z);
			return body;
		}

		SpaceEncounters.Snapshot snapshot(ServerPlayer owner) {
			SpaceEncounters.Snapshot s = SpaceEncounters.snapshot(owner.getUUID());
			helper.assertTrue(s != null, msg(owner.getPlainTextName() + " has no encounter"));
			return s;
		}

		/** Every ship of {@code owner}'s encounter (each loaded), tracked. */
		List<Entity> members(ServerPlayer owner) {
			List<Entity> out = new ArrayList<>();

			for (UUID id : snapshot(owner).members()) {
				Entity member = level.getEntity(id);
				helper.assertTrue(member != null, msg("a ship of the encounter is not loaded: " + id));
				out.add(track(member));
			}

			return out;
		}

		/** The fighters of {@code owner}'s encounter, held still (the tick they appear, before they ever fly), tracked with the rest. */
		List<Starfighter> hold(ServerPlayer owner) {
			List<Starfighter> out = new ArrayList<>();

			for (Entity member : members(owner)) {
				if (member instanceof Starfighter fighter) {
					fighter.setNoAi(true);
					out.add(fighter);
				}
			}

			return out;
		}

		/** The gunship of {@code owner}'s blockade, tracked. */
		BlockadeGunship gunship(ServerPlayer owner) {
			UUID id = snapshot(owner).gunship();
			Entity gunship = id == null ? null : level.getEntity(id);
			helper.assertTrue(gunship instanceof BlockadeGunship, msg("the blockade has its gunship"));
			return track((BlockadeGunship) gunship);
		}

		/** The nav buoy marking {@code runner}'s run, or null. */
		@Nullable NavBuoy buoy(ServerPlayer runner) {
			List<NavBuoy> found = level.getEntitiesOfClass(NavBuoy.class, helper.getBounds().inflate(32), b -> runner.getUUID().equals(b.runner()) && !b.isRemoved());
			return found.isEmpty() ? null : found.getFirst();
		}

		/** One strike from {@code ship}'s guns for {@code shooter}, more than {@code target} can take, as a bolt would deal it. */
		void destroy(@Nullable Entity target, Entity ship, @Nullable UUID shooter) {
			helper.assertTrue(target instanceof LivingEntity, msg("a ship target to destroy, got " + target));
			LivingEntity living = (LivingEntity) target;
			living.hurtServer(level, ShipBolt.strikeOf(level, ship, shooter), LETHAL);
			helper.assertTrue(living.isDeadOrDying(), msg(typeId(living) + " is destroyed"));
		}

		Scene then(Runnable action) {
			steps.add(t -> {
				action.run();
				return true;
			});
			return this;
		}

		/** Runs {@code action} once a tick, {@code count} times, with the call's index. */
		Scene each(int count, IntConsumer action) {
			steps.add(t -> {
				action.accept(t);
				return t + 1 >= count;
			});
			return this;
		}

		/** Waits until {@code condition} holds, failing after {@code limit} ticks. */
		Scene await(String what, int limit, BooleanSupplier condition) {
			steps.add(t -> {
				if (condition.getAsBoolean()) {
					return true;
				}

				helper.assertTrue(t < limit, msg("timed out after " + limit + " ticks waiting until " + what));
				return false;
			});
			return this;
		}

		/** Lets {@code count} ticks pass. */
		Scene idle(int count) {
			steps.add(t -> t >= count);
			return this;
		}

		/** Runs the steps, one tick after another. */
		void run() {
			helper.onEachTick(this::tick);
		}

		/** A test with nothing to wait for: runs {@code body}, cleans up (pass or fail) and succeeds. */
		void now(Runnable body) {
			try {
				body.run();
			} finally {
				clean();
			}

			helper.succeed();
		}

		private void tick() {
			if (finished) {
				return;
			}

			try {
				if (steps.get(index).poll(ticks++)) {
					index++;
					ticks = 0;
				}

				if (index >= steps.size()) {
					finished = true;
					clean();
					helper.succeed();
				}
			} catch (RuntimeException | Error e) {
				finished = true;
				clean();
				throw e;
			}
		}

		/**
		 * The shots of this test's ships and fighters and its players' buoys first; then the players
		 * (a removal is a logout: their encounters end and take their fighters), the bodies, the other
		 * entities and this test's records.
		 */
		private void clean() {
			Set<Integer> firing = new HashSet<>();
			Set<UUID> runners = new HashSet<>();

			for (UUID owner : owners) {
				ShipBody ship = Ships.ship(server, owner);

				if (ship != null) {
					firing.add(ship.getId());
				}
			}

			entities.forEach(e -> firing.add(e.getId()));
			players.forEach(p -> runners.add(p.getUUID()));
			AABB around = helper.getBounds().inflate(128);

			for (Entity stray : level.getEntitiesOfClass(Entity.class, around, e -> e instanceof ShipBolt bolt && firing.contains(bolt.shipId())
					|| e instanceof ShipMissile missile && firing.contains(missile.shipId()) || e instanceof NavBuoy buoy && runners.contains(buoy.runner()))) {
				stray.discard();
			}

			players.forEach(M3TestPlayers::remove);

			for (UUID owner : owners) {
				ShipBody ship = Ships.ship(server, owner);

				if (ship != null) {
					ship.discard();
				}
			}

			entities.forEach(Entity::discard);
			ShipRegistry.Data data = ShipRegistry.snapshot(server);
			owners.forEach(data::remove);
			ShipRegistry.commit(server, data);
		}
	}
}

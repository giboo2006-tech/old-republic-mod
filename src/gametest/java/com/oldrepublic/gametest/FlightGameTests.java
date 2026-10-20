package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.crafting.LegendaryItems;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.gametest.TravelGameTests.Scene;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.CourierShuttle;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.FlightModel;
import com.oldrepublic.travel.FlightRules;
import com.oldrepublic.travel.FlightRules.Fit;
import com.oldrepublic.travel.FlightRules.Ground;
import com.oldrepublic.travel.FlightRules.Landing;
import com.oldrepublic.travel.FlightRules.Refit;
import com.oldrepublic.travel.FlightRules.ReturnCost;
import com.oldrepublic.travel.FlightRules.Transition;
import com.oldrepublic.travel.Interceptor;
import com.oldrepublic.travel.ShipActions;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipEntities;
import com.oldrepublic.travel.ShipMenu;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipRules.Recovery;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelService.Refusal;
import com.oldrepublic.travel.TravelTickets;
import com.oldrepublic.travel.network.ShipActionPayload;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 free flight tests (spec MILESTONE_5.md 19.1, W1). The rules of {@link FlightRules}
 * and {@link FlightModel} are checked as pure functions with the default balance
 * ({@code new FlightBalance()}, {@code new ShipBalance()}); the helm, landings, the rescue, the
 * shipwright and the refit run for real in the Milestone 4 travel tests' fixture
 * ({@link TravelGameTests#ARENA} opened to the sky, its {@link Scene}, records and helpers), with
 * listed mock players and the live balance.
 *
 * <p>Nothing here has a client: the Helm key's and the ship screen's intents go through
 * {@link ShipActions#handle} as their {@code ship_action} would, and a pilot's flying is played by
 * calling {@link ShipBody#move} with {@link MoverType#PLAYER}, the way vanilla replays the moves a
 * pilot's client sends. The game-test server has no planets and no orbits, so the sky and orbit
 * bands, the edge and the jumps are rule tests; the Nether stands in for a landing spot in another
 * world.
 */
public class FlightGameTests {
	private static final String ARENA = TravelGameTests.ARENA;
	private static final BlockPos START = TravelGameTests.START;
	private static final BlockPos PAD = TravelGameTests.PAD;
	/** Where the owner, a crew member and a third player stand before boarding: clear of the hull at {@link #START}. */
	private static final BlockPos OWNER_AT = new BlockPos(12, 1, 20);
	private static final BlockPos CREW_AT = new BlockPos(12, 1, 21);
	private static final BlockPos THIRD_AT = new BlockPos(11, 1, 21);
	/** A field landing spot in the arena, clear of the hull at {@link #START} and of the players. */
	private static final BlockPos FIELD = new BlockPos(9, 1, 9);
	/** The top of the Nether's bedrock roof, where a landing spot in another world is put. */
	private static final int ROOF_Y = 128;
	/** The Spool and the rise (60 ticks by default), and a margin. */
	private static final int HELM_WAIT = 80;
	/** A landing by hand from a few blocks up: its descent (at least the Settle's 20 ticks), then the Settle. */
	private static final int LAND_WAIT = 70;
	/** The rescue from the air: the Jump (10 ticks), Hyperspace (80), the Descent (80) and the Settle (20), and a margin. */
	private static final int RESCUE_WAIT = 210;
	/** The push into a wall of {@link #impactHullFromSpeedLost}: blocks per tick, under the cap. */
	private static final double PUSH = 1.5;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- the helm (4.2)

	/** Every helm refusal and its order, as a pure rule; then live: the lock, a crew member, the owner off the ship, and no room to rise. */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void helmRefusals(GameTestHelper helper) {
		ShipRecord ready = TravelGameTests.detached(ShipState.READY, 100, 4);
		ShipRecord hulk = ready.withState(ShipState.HULK).withHull(0);

		helper.assertTrue(FlightRules.helmRefusal(ready, true, true, false, false) == Refusal.NONE, msg("a READY ship, the helm unlocked: NONE"));
		helper.assertTrue(FlightRules.helmRefusal(null, true, true, false, false) == Refusal.NOT_OWNER, msg("no ship: not the owner"));
		helper.assertTrue(FlightRules.helmRefusal(ready, false, true, false, false) == Refusal.DISABLED_BY_CONFIG, msg("flight switched off"));
		helper.assertTrue(FlightRules.helmRefusal(ready, true, false, false, false) == Refusal.NO_HELM, msg("the helm not unlocked yet"));
		helper.assertTrue(FlightRules.helmRefusal(ready, true, true, true, false) == Refusal.BUSY, msg("something already flies it"));
		helper.assertTrue(FlightRules.helmRefusal(ready.withState(ShipState.FLYING), true, true, false, false) == Refusal.BUSY, msg("FLYING: busy"));
		helper.assertTrue(FlightRules.helmRefusal(ready.withState(ShipState.LAUNCHING), true, true, false, false) == Refusal.BUSY, msg("LAUNCHING: busy"));
		helper.assertTrue(FlightRules.helmRefusal(ready.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, 3))), true, true,
				false, false) == Refusal.BUSY, msg("a record with a flight on it"));
		helper.assertTrue(FlightRules.helmRefusal(hulk, true, true, false, false) == Refusal.NOT_READY, msg("the hulk cannot fly"));
		helper.assertTrue(FlightRules.helmRefusal(ready.withState(ShipState.GROUNDED).withFuel(0), true, true, false, false) == Refusal.NONE,
				msg("GROUNDED flies: sublight flight needs no fuel"));
		helper.assertTrue(FlightRules.helmRefusal(ready.withState(ShipState.DISABLED).withHull(5), true, true, false, false) == Refusal.NONE,
				msg("DISABLED flies: no hull minimum"));
		helper.assertTrue(FlightRules.helmRefusal(ready, true, true, false, true) == Refusal.COMBAT, msg("the combat lock"));
		// The order: owner, config, unlock, busy, state, combat.
		helper.assertTrue(FlightRules.helmRefusal(null, false, false, true, true) == Refusal.NOT_OWNER, msg("the owner comes first"));
		helper.assertTrue(FlightRules.helmRefusal(hulk, false, false, true, true) == Refusal.DISABLED_BY_CONFIG, msg("the config switch before the lock"));
		helper.assertTrue(FlightRules.helmRefusal(hulk, true, false, true, true) == Refusal.NO_HELM, msg("the lock before busy"));
		helper.assertTrue(FlightRules.helmRefusal(hulk, true, true, true, true) == Refusal.BUSY, msg("busy before the state"));
		helper.assertTrue(FlightRules.helmRefusal(hulk, true, true, false, true) == Refusal.NOT_READY, msg("the state before combat"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		ServerPlayer crew = scene.player(CREW_AT);
		UUID id = owner.getUUID();
		BlockPos overhead = new BlockPos(START.getX(), 6, START.getZ());
		scene.now(() -> {
			TravelGameTests.openArena(helper);
			ShipBody ship = aboard(scene, owner, 4, crew);
			TravelAttachments.set(owner, TravelAttachments.get(owner).withHelm(false));
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			helper.assertTrue(resting(scene, id), msg("live: the helm locked (NO_HELM): the ship stays at rest"));

			TravelAttachments.set(owner, TravelAttachments.get(owner).withHelm(true));
			press(crew, ShipActionPayload.TAKE_HELM, 0);
			helper.assertTrue(resting(scene, id), msg("live: a crew member cannot take the owner's helm"));

			owner.stopRiding();
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			helper.assertTrue(resting(scene, id), msg("live: the owner off the ship takes no helm"));

			helper.assertTrue(owner.startRiding(ship, true, false), msg("the owner boards again"));
			helper.setBlock(overhead, Blocks.STONE);
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			helper.assertTrue(resting(scene, id), msg("live: a block over the hull: no room to rise (NO_ROOM)"));

			helper.setBlock(overhead, Blocks.AIR);
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			ShipRecord taking = scene.record(id);
			helper.assertTrue(taking.state() == ShipState.LAUNCHING && Ships.busy(taking), msg("live: with room above, the Spool starts"));
		});
	}

	/** The Helm key: the Spool at once (a second press is BUSY), then the rise and the hand-over: FLYING, piloted, the last landing spot kept. */
	@GameTest(structure = ARENA, maxTicks = 120, skyAccess = true)
	public void takeHelmSetsFlyingAndPiloted(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		FlightBalance live = FlightBalance.get();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			ShipRecord taking = scene.record(id);
			helper.assertTrue(taking.state() == ShipState.LAUNCHING && Ships.busy(taking) && !body(scene, id).piloted(),
					msg("the Spool: LAUNCHING and busy, not piloted yet"));
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			helper.assertTrue(scene.record(id).state() == ShipState.LAUNCHING, msg("a second press changes nothing (BUSY)"));
		}).await("the hand-over at the helm", HELM_WAIT, () -> atHelm(scene, owner)).then(() -> {
			ShipRecord flying = scene.record(id);
			ShipBody ship = body(scene, id);
			double ground = helper.absolutePos(START).getY();
			helper.assertTrue(flying.state() == ShipState.FLYING && flying.flight().isEmpty() && Ships.busy(flying), msg("FLYING, no autopilot run, busy"));
			helper.assertTrue(ship.getY() >= ground + live.helmRiseBlocks - 0.01, msg("risen " + live.helmRiseBlocks + " blocks, at " + ship.getY()));
			helper.assertTrue(ship.phase() == ShipRules.PHASE_HELM && !ship.gearDown() && ship.locksRiders(), msg("the helm's phase, struts up, riders locked in"));
			helper.assertTrue(flying.pos().equals(scene.at(START)), msg("the record keeps the last landing spot"));
			helper.assertTrue(owner.getVehicle() == ship, msg("the pilot is aboard"));
		}).run();
	}

	/** Only the owner aboard steers, and only while the piloted flag is set: never on the ground, in the Spool, landing or settled, never a crew member. */
	@GameTest(structure = ARENA, maxTicks = 200, skyAccess = true)
	public void controllingPassengerOnlyWhilePiloted(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		ServerPlayer crew = scene.player(CREW_AT);
		UUID id = owner.getUUID();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			ShipBody ship = aboard(scene, owner, 4, crew);
			helper.assertTrue(ship.getControllingPassenger() == null, msg("nobody steers a ship on the ground"));
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			helper.assertTrue(ship.getControllingPassenger() == null, msg("nobody steers during the Spool"));
		}).await("the hand-over at the helm", HELM_WAIT, () -> atHelm(scene, owner)).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(ship.getControllingPassenger() == owner && crew.getVehicle() == ship, msg("the owner steers; the crew rides along"));
			press(crew, ShipActionPayload.LAND, 0);
			helper.assertTrue(atHelm(scene, owner), msg("a crew member cannot land the owner's ship"));
			press(owner, ShipActionPayload.LAND, 0);
			helper.assertTrue(!ship.piloted() && ship.getControllingPassenger() == null, msg("landing: the server flies it, nobody steers"));
		}).await("the landing and the Settle", LAND_WAIT, () -> TravelGameTests.landed(scene, id, List.of(owner, crew))).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(!ship.piloted() && ship.getControllingPassenger() == null && ship.getPassengers().isEmpty(), msg("landed: nobody aboard, nobody steers"));
		}).run();
	}

	/** The server never moves a piloted ship: it stays exactly where its pilot left it. */
	@GameTest(structure = ARENA, maxTicks = 160, skyAccess = true)
	public void noServerMoveOfPilotedShip(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		Vec3[] at = new Vec3[1];
		float[] yaw = new float[1];
		int[] ticks = new int[1];

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			ShipBody ship = body(scene, id);
			at[0] = ship.position();
			yaw[0] = ship.getYRot();
		}).await("forty ticks of a piloted ship left alone", 45, () -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(ship.position().distanceTo(at[0]) < 1.0e-6 && ship.getYRot() == yaw[0],
					msg("the server moved the piloted ship after " + ticks[0] + " ticks, to " + ship.position()));
			helper.assertTrue(atHelm(scene, owner), msg("still at the helm"));
			return ++ticks[0] >= 40;
		}).run();
	}

	// ---------------------------------------------------------------- the flight model (4.3, 4.4)

	/** No profile flies past the hard cap; live, a move longer than the cap is pulled back and one within it stands. */
	@GameTest(structure = ARENA, maxTicks = 140, skyAccess = true)
	public void speedNeverAboveCap(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		int[] classes = {0, ShipRecord.UPGRADE_THRUSTERS, ShipRecord.CLASS_INTERCEPTOR, ShipRecord.CLASS_INTERCEPTOR | ShipRecord.UPGRADE_THRUSTERS};

		for (int upgrades : classes) {
			for (boolean orbit : new boolean[] {false, true}) {
				FlightModel.Profile p = FlightRules.profile(b, upgrades, orbit);
				double fastest = flatOut(p);
				helper.assertTrue(p.cap() == b.maxSpeedCap, msg("every profile carries the hard cap"));
				helper.assertTrue(fastest <= p.cap() + 1.0e-9, msg("upgrades " + upgrades + (orbit ? " in orbit" : " in a sky") + " reached " + fastest));
			}
		}

		FlightModel.Profile wild = new FlightModel.Profile(5f, 1f, 0.5f, 3f, 20f, 1f, 2f, 1f, 60f, 3f, b.maxSpeedCap);
		double wildest = flatOut(wild);
		helper.assertTrue(Math.abs(wildest - b.maxSpeedCap) < 1.0e-6, msg("a profile far faster than the cap stops at it, got " + wildest));
		helper.assertTrue(FlightRules.profile(b, ShipRecord.UPGRADE_THRUSTERS, false).top() > FlightRules.profile(b, 0, false).top(),
				msg("thrusters raise the top speed"));
		FlightBalance fallback = new FlightBalance();
		fallback.serverSteering = true;
		helper.assertTrue(FlightRules.profile(fallback, 0, false).top() < FlightRules.profile(b, 0, false).top(), msg("fallback B scales the speeds down"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		float cap = FlightBalance.get().maxSpeedCap;
		Vec3[] before = new Vec3[1];

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).idle(2).then(() -> {
			ShipBody ship = body(scene, id);
			before[0] = ship.position();
			// One move of three times the cap, as a hacked or badly lagging client might send.
			ship.move(MoverType.PLAYER, new Vec3(0, cap * 3.0, 0));
			helper.assertTrue(ship.getY() > before[0].y + cap, msg("precondition: the move was replayed"));
		}).idle(2).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(ship.position().distanceTo(before[0]) < 1.0e-6, msg("the server pulled the ship back, it is at " + ship.position()));
			helper.assertTrue(atHelm(scene, owner), msg("and the pilot keeps the helm"));
			ship.move(MoverType.PLAYER, new Vec3(0, cap * 0.75, 0));
		}).idle(2).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(Math.abs(ship.getY() - (before[0].y + cap * 0.75)) < 1.0e-6, msg("a move under the cap stands, at " + ship.getY()));
		}).run();
	}

	/** The hull a crash costs, from the speed it took off; live, flying into a wall costs it once. */
	@GameTest(structure = ARENA, maxTicks = 140, skyAccess = true)
	public void impactHullFromSpeedLost(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		helper.assertTrue(near(FlightRules.speedLost(new Vec3(1.2, 0, -0.5), true, false, true), 1.3), msg("both blocked axes count"));
		helper.assertTrue(near(FlightRules.speedLost(new Vec3(1.2, 0, -0.5), true, false, false), 1.2), msg("only the blocked axis counts"));
		helper.assertTrue(FlightRules.speedLost(new Vec3(1.2, 0, -0.5), false, false, false) == 0, msg("nothing blocked, nothing lost"));
		helper.assertTrue(FlightRules.speedLost(new Vec3(0, 0.3, 0), false, true, false) == 0, msg("a bump while climbing is no crash"));
		helper.assertTrue(near(FlightRules.speedLost(new Vec3(0, -0.8, 0), false, true, false), 0.8), msg("coming down onto something is"));
		helper.assertTrue(FlightRules.speedLost(new Vec3(0.04, 0, 0), true, false, false) == 0, msg("a crawl against a wall is nothing"));
		helper.assertTrue(FlightRules.impactDamage(0.6, b) == 0, msg("at the floor: no damage"));
		helper.assertTrue(FlightRules.impactDamage(0.61, b) == 2, msg("just over the floor: one step"));
		helper.assertTrue(FlightRules.impactDamage(0.85, b) == 2, msg("a whole step over: still one"));
		helper.assertTrue(FlightRules.impactDamage(0.86, b) == 4, msg("into the second step"));
		helper.assertTrue(FlightRules.impactDamage(1.6, b) == 8, msg("1.6 lost: four steps"));
		helper.assertTrue(FlightRules.impactDamage(2.0, b) == 12, msg("the cap's speed: six steps"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		int expected = 100 - FlightRules.impactDamage(PUSH, FlightBalance.get());
		Runnable push = () -> body(scene, id).move(MoverType.PLAYER, new Vec3(PUSH, 0, 0));

		scene.then(() -> {
			TravelGameTests.openArena(helper);

			// A wall across the ship's way east, as high as the arena.
			for (int y = 1; y < 7; y++) {
				for (int z = 13; z < 22; z++) {
					helper.setBlock(new BlockPos(23, y, z), Blocks.STONE);
				}
			}

			aboard(scene, owner, 4);
		});
		// Past the hand-over's grace, four moves east: the second runs into the wall.
		toHelm(scene, owner).idle(12).then(push).then(push).then(push).then(push).idle(2).then(() -> {
			ShipRecord record = scene.record(id);
			helper.assertTrue(record.hull() == expected, msg("one crash at " + PUSH + " blocks a tick costs " + (100 - expected) + " hull, got " + record.hull()));
			helper.assertTrue(body(scene, id).hull() == expected, msg("the body shows it"));
			helper.assertTrue(atHelm(scene, owner), msg("a dented ship flies on"));
		}).run();
	}

	/** The boost meter as the server keeps it: a press starts it, it drains over its ticks, refills at rest and never starts empty. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void boostMeterServerSide(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		int ticks = FlightRules.boostTicks(b, 0);
		helper.assertTrue(ticks == b.boostTicks, msg("a full meter lasts boostTicks"));
		helper.assertTrue(FlightRules.boostTicks(b, ShipRecord.UPGRADE_THRUSTERS) == Math.round(b.boostTicks * (1f + b.thrustersBoostPct / 100f)),
				msg("thrusters make it last longer"));

		FlightRules.Boost boost = FlightRules.Boost.FULL.press(true);
		helper.assertTrue(boost.running() && boost.held() && boost.meter() == 1f, msg("Sprint on a full meter starts the boost"));
		helper.assertTrue(!boost.tick(ticks, b.boostRechargeTicks).press(false).running(), msg("letting go stops it"));
		int drained = 0;

		while (boost.running() && drained < 1000) {
			boost = boost.tick(ticks, b.boostRechargeTicks);
			drained++;
		}

		helper.assertTrue(drained >= ticks && drained <= ticks + 1 && boost.meter() == 0f, msg("held, it empties in " + ticks + " ticks, took " + drained));
		helper.assertTrue(!boost.press(true).running(), msg("an empty meter does not start"));

		int refilled = 0;

		while (boost.meter() < 1f && refilled < 1000) {
			boost = boost.tick(ticks, b.boostRechargeTicks);
			helper.assertTrue(!boost.running(), msg("Sprint still held does not restart it while it refills"));
			refilled++;
		}

		helper.assertTrue(refilled >= b.boostRechargeTicks && refilled <= b.boostRechargeTicks + 1, msg("it refills in boostRechargeTicks, took " + refilled));
		helper.assertTrue(boost.press(false).press(true).running(), msg("full again, a new press boosts"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- landing (4.5, 4.6)

	/** The landing check: height, speed, liquid, level, room and another's site, in that order; the footprints fit inside the hulls. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void landingChecksFootprintLevelSpeedHeight(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		Ground flat = new Ground(9, 9, false, 64, 64);
		double low = 64 + b.landHeight;

		helper.assertTrue(FlightRules.landing(0.1, low, flat, true, false, false, b) == Landing.OK, msg("flat ground within landHeight, slow: OK"));
		helper.assertTrue(FlightRules.landing(0.1, low + 0.01, flat, true, false, false, b) == Landing.TOO_HIGH, msg("just above landHeight: too high"));
		helper.assertTrue(FlightRules.landing(0.1, 80, new Ground(9, 0, false, 80, 80), true, false, false, b) == Landing.TOO_HIGH,
				msg("no ground within reach: too high"));
		helper.assertTrue(FlightRules.landing(b.landMaxSpeed, low, flat, true, false, false, b) == Landing.OK, msg("at landMaxSpeed: OK"));
		helper.assertTrue(FlightRules.landing(b.landMaxSpeed + 0.01, low, flat, true, false, false, b) == Landing.TOO_FAST, msg("faster: too fast"));
		helper.assertTrue(FlightRules.landing(0.1, 66, new Ground(9, 4, true, 63, 63), true, false, false, b) == Landing.NO_GROUND,
				msg("water under part of the footprint: no ground"));
		helper.assertTrue(FlightRules.landing(0.1, 66, new Ground(9, 0, true, 66, 66), true, false, false, b) == Landing.NO_GROUND,
				msg("all water: no ground"));
		helper.assertTrue(FlightRules.landing(0.1, 66, new Ground(9, 8, false, 64, 64), true, false, false, b) == Landing.NOT_LEVEL,
				msg("a column with nothing under it: not level"));
		helper.assertTrue(FlightRules.landing(0.1, 66, new Ground(9, 9, false, 63, 64.5), true, false, false, b) == Landing.NOT_LEVEL,
				msg("a step of more than landLevelTolerance: not level"));
		helper.assertTrue(FlightRules.landing(0.1, 66, new Ground(9, 9, false, 63, 64), true, false, false, b) == Landing.OK,
				msg("a step of exactly the tolerance: OK"));
		helper.assertTrue(FlightRules.landing(0.1, low, flat, false, false, false, b) == Landing.NO_ROOM, msg("something in the way: no room"));
		helper.assertTrue(FlightRules.landing(0.1, low, flat, true, true, false, b) == Landing.SITE, msg("another player's site"));
		// The order the HUD names a reason in.
		helper.assertTrue(FlightRules.landing(1, low + 1, new Ground(9, 4, true, 64, 64), false, true, false, b) == Landing.TOO_HIGH,
				msg("too high before too fast"));
		helper.assertTrue(FlightRules.landing(1, low, new Ground(9, 4, true, 64, 64), false, true, false, b) == Landing.TOO_FAST, msg("too fast before no ground"));
		helper.assertTrue(FlightRules.landing(0.1, low, new Ground(9, 4, true, 60, 64), false, true, false, b) == Landing.NO_GROUND,
				msg("no ground before not level"));
		helper.assertTrue(FlightRules.landing(0.1, low, new Ground(9, 8, false, 60, 64), false, true, false, b) == Landing.NOT_LEVEL,
				msg("not level before no room"));
		helper.assertTrue(FlightRules.landing(0.1, low, flat, false, true, false, b) == Landing.NO_ROOM, msg("no room before the site"));

		for (Landing landing : Landing.values()) {
			helper.assertTrue(landing.allowed() == (landing == Landing.OK || landing == Landing.HUB), msg("only OK and HUB land: " + landing));
			helper.assertTrue(Landing.of(landing.ordinal()) == landing, msg("the ordinal round-trips: " + landing));
		}

		helper.assertTrue(Landing.of(-1) == Landing.TOO_HIGH && Landing.of(Landing.values().length) == Landing.TOO_HIGH, msg("an unknown ordinal reads as too high"));
		helper.assertTrue(ShipBody.footprintHalf(false) * 2 < ShipEntities.COURIER_SHUTTLE.getWidth()
				&& ShipBody.footprintHeight(false) < ShipEntities.COURIER_SHUTTLE.getHeight(), msg("the courier's footprint fits inside its hull"));
		helper.assertTrue(ShipBody.footprintHalf(true) * 2 < ShipEntities.INTERCEPTOR.getWidth()
				&& ShipBody.footprintHeight(true) < ShipEntities.INTERCEPTOR.getHeight(), msg("the interceptor's footprint fits inside its hull"));

		ShipBalance sb = new ShipBalance();
		GlobalPos spot = GlobalPos.of(Level.OVERWORLD, new BlockPos(120, 70, -40));
		ShipRecord flown = TravelGameTests.detached(ShipState.FLYING, 100, 3);
		ShipRecord landed = FlightRules.landedAt(flown, spot, 135f, sb);
		helper.assertTrue(landed.pos().equals(spot) && landed.yaw() == 135f && landed.flight().isEmpty() && landed.state() == ShipState.READY,
				msg("landed by hand: the spot and heading kept, READY"));
		helper.assertTrue(FlightRules.landedAt(flown.withFuel(0), spot, 0f, sb).state() == ShipState.GROUNDED, msg("an empty tank lands GROUNDED"));
		helper.assertTrue(FlightRules.landedAt(flown.withHull(10), spot, 0f, sb).state() == ShipState.DISABLED, msg("a low hull lands DISABLED"));
		helper.succeed();
	}

	/** Inside a hub's footprint the landing goes to a bay, whatever lies right below; the first free bay is taken. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void landingInHubGlidesToBay(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		Ground flat = new Ground(9, 9, false, 64, 64);
		double low = 66;

		helper.assertTrue(FlightRules.landing(0.1, low, new Ground(9, 0, true, 66, 66), true, false, true, b) == Landing.HUB, msg("over the hub's water: HUB"));
		helper.assertTrue(FlightRules.landing(0.1, low, new Ground(9, 6, false, 60, 64), true, false, true, b) == Landing.HUB, msg("over uneven ground: HUB"));
		helper.assertTrue(FlightRules.landing(0.1, low, flat, false, true, true, b) == Landing.HUB, msg("no room right below: HUB"));
		helper.assertTrue(FlightRules.landing(0.1, 64 + b.landHeight + 1, flat, true, false, true, b) == Landing.TOO_HIGH, msg("too high still comes first"));
		helper.assertTrue(FlightRules.landing(1, low, flat, true, false, true, b) == Landing.TOO_FAST, msg("too fast still comes first"));
		helper.assertTrue(Landing.HUB.allowed(), msg("HUB lands"));

		double radius = 5;
		List<BlockPos> bays = List.of(new BlockPos(0, 64, 0), new BlockPos(20, 64, 0), new BlockPos(40, 64, 0));
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(), radius) == 0, msg("an empty hub: the first bay"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(new BlockPos(1, 64, 1)), radius) == 1, msg("the first bay taken: the second"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(new BlockPos(1, 64, 0), new BlockPos(40, 64, 3)), radius) == 1, msg("the first free one in order"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, bays, radius) == -1, msg("every bay taken: none (the overflow rings)"));
		helper.succeed();
	}

	/** Landed by hand on a field, the ship stays there: the record, the last pad, a save and load, and a new body placed from it. */
	@GameTest(structure = ARENA, maxTicks = 240, skyAccess = true)
	public void fieldLandingKeepsPositionAcrossRestart(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		UUID[] first = new UUID[1];

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			// A two-block pillar under the middle of the footprint, then over the field three blocks up.
			helper.setBlock(FIELD, Blocks.STONE);
			helper.setBlock(FIELD.above(), Blocks.STONE);
			flyTo(body(scene, id), Vec3.atBottomCenterOf(helper.absolutePos(FIELD)).add(0, 3, 0));
		}).then(() -> {
			press(owner, ShipActionPayload.LAND, 0);
			helper.assertTrue(atHelm(scene, owner) && body(scene, id).phase() == ShipRules.PHASE_HELM, msg("the pillar: not level, the landing is refused"));
			helper.setBlock(FIELD, Blocks.AIR);
			helper.setBlock(FIELD.above(), Blocks.AIR);
			press(owner, ShipActionPayload.LAND, 0);
			ShipBody ship = body(scene, id);
			helper.assertTrue(!ship.piloted() && ship.phase() == ShipRules.PHASE_DESCENT, msg("level ground: the ship settles straight down"));
		}).await("the field landing", LAND_WAIT, () -> TravelGameTests.landed(scene, id, List.of(owner))).then(() -> {
			GlobalPos spot = scene.at(FIELD);
			ShipRecord landed = scene.record(id);
			ShipBody ship = body(scene, id);
			helper.assertTrue(landed.pos().equals(spot) && landed.state() == ShipState.READY && landed.flight().isEmpty(),
					msg("the record stands on the field, READY: " + landed));
			helper.assertTrue(TravelAttachments.get(owner).lastPad().equals(Optional.of(spot)), msg("the field is the owner's last landing"));
			helper.assertTrue(ship.position().distanceTo(Vec3.atBottomCenterOf(spot.pos())) < 0.01 && ship.gearDown(), msg("the body stands on it, struts down"));

			ShipRecord saved = roundTrip(helper, landed);
			helper.assertTrue(saved.pos().equals(spot) && saved.state() == ShipState.READY && saved.flight().isEmpty(), msg("saved and loaded, it is still there"));
			helper.assertTrue(ShipRules.recovery(saved) == Recovery.NONE && ShipRules.recovered(saved, ShipBalance.get()).pos().equals(spot),
					msg("a restart has nothing to recover"));
			first[0] = ship.getUUID();
			helper.assertTrue(Ships.restore(scene.server, id, false), msg("a new body is placed from the record, as after a restart"));
		}).await("the new body", 40, () -> {
			ShipBody ship = Ships.ship(scene.server, id);
			return ship != null && !ship.getUUID().equals(first[0]);
		}).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(FIELD))) < 0.5, msg("the new body stands on the field"));
		}).run();
	}

	// ---------------------------------------------------------------- leaving and restarts (4.10)

	/** A crew member leaving the server at the helm is put down beside the last landing spot; the pilot flies on. */
	@GameTest(structure = ARENA, maxTicks = 140, skyAccess = true)
	public void riderLeavingIsPutDownAtLastLanding(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		ServerPlayer crew = scene.player(CREW_AT);
		UUID id = owner.getUUID();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4, crew);
		});
		toHelm(scene, owner).then(() -> {
			M3TestPlayers.remove(crew);
			helper.assertTrue(!crew.isPassenger() && body(scene, id).getPassengers().size() == 1, msg("the crew member is put off"));
			helper.assertTrue(beside(helper, crew, START), msg("and set down beside the last landing spot, at " + crew.position()));
			helper.assertTrue(atHelm(scene, owner), msg("the pilot keeps the helm"));
		}).idle(5).then(() -> {
			helper.assertTrue(atHelm(scene, owner) && scene.record(id).flight().isEmpty(), msg("the ship flies on: no rescue for a crew member leaving"));
		}).run();
	}

	/** The pilot leaving the server: set down beside the last landing spot, and the rescue flies the ship back there. */
	@GameTest(structure = ARENA, maxTicks = 340, skyAccess = true)
	public void pilotLeavingReturnsShip(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			M3TestPlayers.remove(owner);
			helper.assertTrue(!owner.isPassenger() && beside(helper, owner, START), msg("the pilot is put down beside the last landing spot"));
		}).await("the rescue starts", 10, () -> scene.record(id).flight().isPresent()).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(!ship.piloted() && ship.getControllingPassenger() == null, msg("the server flies it now"));
		}).await("the ship is home", RESCUE_WAIT, () -> TravelGameTests.landed(scene, id, List.of())).then(() -> {
			ShipRecord home = scene.record(id);
			ShipBody ship = body(scene, id);
			helper.assertTrue(home.pos().equals(scene.at(START)) && home.state() == ShipState.READY, msg("READY on its last landing spot: " + home));
			helper.assertTrue(home.hull() == 100 && home.fuel() == 4, msg("the rescue costs nothing"));
			helper.assertTrue(ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(START))) < 0.5 && ship.getPassengers().isEmpty(),
					msg("the body stands there, empty"));
		}).run();
	}

	/** A ship saved in flight is settled at its last landing spot: as a rule, and live with nobody at the helm. */
	@GameTest(structure = ARENA, maxTicks = 240, skyAccess = true)
	public void restartWhileFlyingReturnsToLastLanding(GameTestHelper helper) {
		ShipBalance sb = new ShipBalance();
		ShipRecord flying = TravelGameTests.detached(ShipState.FLYING, 90, 3);
		ShipRecord saved = roundTrip(helper, flying);
		helper.assertTrue(saved.state() == ShipState.FLYING && saved.flight().isEmpty() && saved.pos().equals(flying.pos()), msg("FLYING saves and loads"));
		helper.assertTrue(ShipRules.recovery(saved) == Recovery.NONE, msg("no autopilot run to recover"));
		ShipRecord settled = ShipRules.recovered(saved, sb);
		helper.assertTrue(settled.state() == ShipState.READY && settled.pos().equals(flying.pos()) && settled.hull() == 90 && settled.fuel() == 3,
				msg("settled where it last landed, nothing taken"));
		// A run from the air keeps the last landing spot in the record until its jump.
		ShipRecord jumping = flying.withState(ShipState.LAUNCHING)
				.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, ShipRules.PHASE_JUMP)));
		helper.assertTrue(ShipRules.recovery(jumping) == Recovery.ORIGIN && ShipRules.recovered(jumping, sb).pos().equals(flying.pos()),
				msg("caught before its jump: back at the last landing spot"));
		helper.assertTrue(ShipRules.recovery(jumping.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH,
				ShipRules.PHASE_HYPERSPACE)))) == Recovery.DESTINATION, msg("after it: at the destination"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			ShipBody ship = scene.give(owner, START, 0f, 100, 4);
			// As loaded after a restart: FLYING with nobody at the helm, the body up in the air.
			Ships.update(scene.server, id, r -> r.withState(ShipState.FLYING));
			ship.snapTo(ship.getX(), ship.getY() + 6, ship.getZ());
		}).await("the ship is settled", 210, () -> scene.record(id).state() == ShipState.READY).then(() -> {
			ShipRecord record = scene.record(id);
			ShipBody ship = body(scene, id);
			helper.assertTrue(record.pos().equals(scene.at(START)) && record.flight().isEmpty() && record.fuel() == 4, msg("READY at its last landing spot"));
			helper.assertTrue(ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(START))) < 0.5, msg("its body stands there"));
		}).run();
	}

	// ---------------------------------------------------------------- sky and orbit (4.7)

	/** Up above the ceiling in a sky, down below the line in an orbit, neither within the cooldown; arrivals land inside the band. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void transitionCooldownBothWays(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		long rested = b.transitionCooldown;

		helper.assertTrue(FlightRules.transition(true, false, b.orbitCeiling + 1, rested, b) == Transition.UP, msg("above the ceiling in a sky: up"));
		helper.assertTrue(FlightRules.transition(true, false, b.orbitCeiling, rested, b) == Transition.NONE, msg("at the ceiling: not yet"));
		helper.assertTrue(FlightRules.transition(false, true, b.atmosphereLine - 1, rested, b) == Transition.DOWN, msg("below the line in an orbit: down"));
		helper.assertTrue(FlightRules.transition(false, true, b.atmosphereLine, rested, b) == Transition.NONE, msg("at the line: not yet"));
		helper.assertTrue(FlightRules.transition(true, false, b.orbitCeiling + 50, rested - 1, b) == Transition.NONE, msg("up within the cooldown: no"));
		helper.assertTrue(FlightRules.transition(false, true, b.atmosphereLine - 50, rested - 1, b) == Transition.NONE, msg("down within the cooldown: no"));
		helper.assertTrue(FlightRules.transition(false, false, b.orbitCeiling + 50, rested, b) == Transition.NONE
				&& FlightRules.transition(false, false, -10, rested, b) == Transition.NONE, msg("neither a sky nor an orbit: never"));
		helper.assertTrue(FlightRules.cooldownLeft(0, b) == b.transitionCooldown && FlightRules.cooldownLeft(rested - 1, b) == 1
				&& FlightRules.cooldownLeft(rested, b) == 0 && FlightRules.cooldownLeft(Integer.MAX_VALUE, b) == 0, msg("the HUD's cooldown counts down to 0"));
		helper.assertTrue(b.orbitArrivalY > b.atmosphereLine && b.jumpArrivalY > b.atmosphereLine && b.skyArrivalY < b.orbitCeiling,
				msg("every arrival lies inside the band"));
		helper.assertTrue(FlightRules.transition(false, true, b.orbitArrivalY, rested, b) == Transition.NONE
				&& FlightRules.transition(true, false, b.skyArrivalY, rested, b) == Transition.NONE, msg("so no arrival bounces straight back"));
		helper.succeed();
	}

	/** A sky maps to its orbit at one to eight, and back. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void orbitMappingTimesEight(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		helper.assertTrue(b.orbitScale == 8, msg("the default scale is 8"));
		Vec3 up = FlightRules.toOrbit(800, -1600, b);
		helper.assertTrue(near(up.x, 100) && near(up.y, b.orbitArrivalY) && near(up.z, -200), msg("(800, -1600) climbs to (100, -200) in orbit, got " + up));
		Vec3 down = FlightRules.toSky(100, -200, b);
		helper.assertTrue(near(down.x, 800) && near(down.y, b.skyArrivalY) && near(down.z, -1600), msg("and dives back to (800, -1600), got " + down));
		Vec3 far = FlightRules.toOrbit(-4000, 7200, b);
		Vec3 back = FlightRules.toSky(far.x, far.z, b);
		helper.assertTrue(near(back.x, -4000) && near(back.z, 7200), msg("a round trip inside the edge comes back where it began"));
		FlightBalance flat = new FlightBalance();
		flat.orbitScale = 0;
		helper.assertTrue(near(FlightRules.toOrbit(800, 0, flat).x, 800), msg("a scale below 1 counts as 1"));
		helper.succeed();
	}

	/** The orbit's edge: how far inside it a ship is, the warning band, the turn back 16 blocks inside and the heading home. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void orbitEdgeTurnsBack(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		helper.assertTrue(near(FlightRules.edgeDistance(0, 0, b), b.orbitRadius) && near(FlightRules.edgeDistance(b.orbitRadius, 0, b), 0)
				&& near(FlightRules.edgeDistance(0, b.orbitRadius + 52, b), -52), msg("the distance to the edge"));
		helper.assertTrue(!FlightRules.nearEdge(0, b.orbitRadius - b.edgeWarning - 1, b) && FlightRules.nearEdge(0, b.orbitRadius - b.edgeWarning + 1, b),
				msg("the warning band is edgeWarning deep"));
		helper.assertTrue(FlightRules.nearEdge(1500, 1500, b), msg("beyond the edge warns too"));

		Vec3 east = FlightRules.turnBack(b.orbitRadius + 52, 120, 0, b);
		helper.assertTrue(near(east.x, b.orbitRadius - FlightRules.EDGE_PUSH_BACK) && near(east.y, 120) && near(east.z, 0),
				msg("turned back 16 blocks inside, at the same height, got " + east));
		Vec3 diagonal = FlightRules.turnBack(1500, 90, 1500, b);
		helper.assertTrue(near(Math.sqrt(diagonal.x * diagonal.x + diagonal.z * diagonal.z), b.orbitRadius - FlightRules.EDGE_PUSH_BACK)
				&& near(diagonal.x, diagonal.z) && near(diagonal.y, 90), msg("along its own radius, got " + diagonal));
		Vec3 centre = FlightRules.turnBack(0, 50, 0, b);
		helper.assertTrue(near(centre.x, 0) && near(centre.y, 50) && near(centre.z, 0), msg("the centre itself stays put"));

		double[][] points = {{2032, 0}, {0, -2032}, {1436, 1436}, {-1000, 300}};

		for (double[] p : points) {
			Vec3 heading = FlightModel.heading(new FlightModel.Pose(FlightRules.yawToCentre(p[0], p[1]), 0f));
			Vec3 home = new Vec3(-p[0], 0, -p[1]).normalize();
			helper.assertTrue(heading.dot(home) > 0.9999, msg("turned to face the centre from (" + p[0] + ", " + p[1] + ")"));
		}

		helper.succeed();
	}

	/** The autopilot from the air and jumps from orbit: every refusal and its order, the blockade, and the cell and wear a jump takes. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void jumpFromOrbitCostsFuelAndRefuses(GameTestHelper helper) {
		ShipBalance sb = new ShipBalance();
		ShipRecord flying = TravelGameTests.detached(ShipState.FLYING, 100, 4);
		ShipRecord worn = flying.withHull(sb.jumpMinHull - 1);
		ShipRecord empty = flying.withFuel(sb.cellsPerJump - 1);

		helper.assertTrue(FlightRules.airRefusal(flying, true, true, true, false, true, false, false, sb) == Refusal.NONE, msg("a jump at the helm: NONE"));
		helper.assertTrue(FlightRules.airRefusal(null, true, true, true, false, true, false, false, sb) == Refusal.NOT_OWNER, msg("no ship"));
		helper.assertTrue(FlightRules.airRefusal(flying, false, true, true, false, true, false, false, sb) == Refusal.DISABLED_BY_CONFIG, msg("travel off"));
		helper.assertTrue(FlightRules.airRefusal(flying, true, false, true, false, true, false, false, sb) == Refusal.NOT_READY, msg("not at the helm"));
		helper.assertTrue(FlightRules.airRefusal(flying.withState(ShipState.READY), true, true, true, false, true, false, false, sb) == Refusal.NOT_READY,
				msg("not in the air"));
		helper.assertTrue(FlightRules.airRefusal(flying, true, true, false, false, true, false, false, sb) == Refusal.LOCKED, msg("a locked destination"));
		helper.assertTrue(FlightRules.airRefusal(flying, true, true, true, true, true, false, false, sb) == Refusal.BLOCKADED, msg("the blockade"));
		helper.assertTrue(FlightRules.airRefusal(worn, true, true, true, false, true, false, false, sb) == Refusal.HULL, msg("a jump needs the hull"));
		helper.assertTrue(FlightRules.airRefusal(worn, true, true, true, false, false, false, false, sb) == Refusal.NONE, msg("a hop does not"));
		helper.assertTrue(FlightRules.airRefusal(empty, true, true, true, false, true, false, false, sb) == Refusal.NO_FUEL, msg("a jump needs a cell"));
		helper.assertTrue(FlightRules.airRefusal(empty, true, true, true, false, false, false, false, sb) == Refusal.NONE, msg("a hop does not"));
		helper.assertTrue(FlightRules.airRefusal(flying, true, true, true, false, true, true, false, sb) == Refusal.UNDER_FIRE, msg("under fire"));
		helper.assertTrue(FlightRules.airRefusal(flying, true, true, true, false, true, false, true, sb) == Refusal.COMBAT, msg("the combat lock"));
		// The order: config, helm, lock, blockade, hull, fuel, fire, combat.
		helper.assertTrue(FlightRules.airRefusal(worn.withFuel(0), true, true, false, true, true, true, true, sb) == Refusal.LOCKED, msg("the lock before the blockade"));
		helper.assertTrue(FlightRules.airRefusal(worn.withFuel(0), true, true, true, true, true, true, true, sb) == Refusal.BLOCKADED, msg("the blockade before the hull"));
		helper.assertTrue(FlightRules.airRefusal(worn.withFuel(0), true, true, true, false, true, true, true, sb) == Refusal.HULL, msg("the hull before fuel"));
		helper.assertTrue(FlightRules.airRefusal(empty, true, true, true, false, true, true, true, sb) == Refusal.NO_FUEL, msg("fuel before fire"));
		helper.assertTrue(FlightRules.airRefusal(flying, true, true, true, false, true, true, true, sb) == Refusal.UNDER_FIRE, msg("fire before combat"));

		for (ChapterFiveStage stage : ChapterFiveStage.values()) {
			boolean before = stage.ordinal() < ChapterFiveStage.BLOCKADE.ordinal();
			helper.assertTrue(FlightRules.blockaded(Destination.HOLDFAST, stage) == before, msg("the holdfast is closed before BLOCKADE only: " + stage));
			helper.assertTrue(!FlightRules.blockaded(Destination.SALTGATE, stage), msg("Saltgate is never blockaded: " + stage));
			helper.assertTrue(FlightRules.blockadeArrival(Destination.HOLDFAST, stage) == (stage == ChapterFiveStage.BLOCKADE),
					msg("a run to the holdfast ends in orbit during BLOCKADE only: " + stage));
		}

		ShipRecord jumped = FlightRules.afterAirJump(flying, false, sb);
		helper.assertTrue(jumped.fuel() == 4 - sb.cellsPerJump && jumped.hull() == 100 - sb.wearPerJump, msg("a jump takes a cell and the wear"));
		helper.assertTrue(FlightRules.afterAirJump(flying, true, sb).equals(flying), msg("a hop takes nothing"));
		helper.assertTrue(FlightRules.afterAirJump(flying.withUpgrades(ShipRecord.UPGRADE_PLATING), false, sb).hull() == 100 - sb.wearReinforced,
				msg("plating wears less"));
		helper.succeed();
	}

	/** Where a jump into an orbit arrives: over the pad divided by 8, pulled inside the edge's warning band. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void orbitArrivalClampedInsideTheEdge(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		double radius = FlightRules.arrivalRadius(b);
		helper.assertTrue(near(radius, b.orbitRadius - b.edgeWarning) && near(radius, 1920), msg("arrivals stay 1,920 blocks from the centre"));

		Vec3 arrival = FlightRules.orbitArrival(new Vec3(800, 64, -400), b);
		helper.assertTrue(near(arrival.x, 100) && near(arrival.y, b.jumpArrivalY) && near(arrival.z, -50), msg("over the pad / 8 at jumpArrivalY, got " + arrival));
		Vec3 east = FlightRules.orbitArrival(new Vec3(40000, 70, 0), b);
		helper.assertTrue(near(east.x, radius) && near(east.z, 0) && !FlightRules.nearEdge(east.x, east.z, b), msg("a far pad: at the band, not in it, got " + east));
		Vec3 diagonal = FlightRules.orbitArrival(new Vec3(30000, 70, -30000), b);
		helper.assertTrue(near(Math.sqrt(diagonal.x * diagonal.x + diagonal.z * diagonal.z), radius) && near(diagonal.x, -diagonal.z),
				msg("pulled in along its own radius, got " + diagonal));
		Vec3 none = FlightRules.orbitArrival(null, b);
		helper.assertTrue(near(none.x, 0) && near(none.y, b.jumpArrivalY) && near(none.z, 0), msg("no pad yet: over the centre"));
		Vec3 climb = FlightRules.toOrbit(40000, 0, b);
		helper.assertTrue(near(climb.x, radius) && near(climb.y, b.orbitArrivalY), msg("a climb from far out arrives inside the band too"));
		helper.assertTrue(FlightRules.clampInside(3, 7, 4, 10).equals(new Vec3(3, 7, 4)), msg("inside: unchanged"));
		Vec3 pulled = FlightRules.clampInside(30, 7, 40, 10);
		helper.assertTrue(near(pulled.x, 6) && near(pulled.y, 7) && near(pulled.z, 8), msg("outside: pulled in to the radius"));
		helper.succeed();
	}

	/**
	 * An arrival's clear place (4.7): the pure search (straight up first, then sideways, never above
	 * a sky's line nor past the edge's band), then live in the arena with an interceptor's
	 * 5 × 2.5 × 5 hull box: already clear, a block in the hull (it rises over it), a pillar through
	 * its column (it moves aside), and the same pillar with the search too short to get past it
	 * (nothing fits, so the caller keeps the computed place).
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void arrivalPicksAClearPlace(GameTestHelper helper) {
		Vec3 at = new Vec3(100, 100, 0);
		double open = Double.POSITIVE_INFINITY;
		helper.assertTrue(FlightRules.clearArrival(at, p -> true, 16, 16, open, 0).orElseThrow() == at, msg("clear: the computed place"));
		Optional<Vec3> up = FlightRules.clearArrival(at, p -> p.y >= 105, 16, 16, open, 0);
		helper.assertTrue(up.isPresent() && up.get().equals(new Vec3(100, 105, 0)), msg("blocked: the lowest clear place straight up, got " + up));
		helper.assertTrue(FlightRules.clearArrival(at, p -> p.y >= 105, 16, 16, 103, 0).isEmpty(), msg("never above the ceiling (a sky's line)"));
		Optional<Vec3> east = FlightRules.clearArrival(at, p -> p.x >= 104, 16, 16, open, 0);
		helper.assertTrue(east.isPresent() && east.get().equals(new Vec3(104, 100, 0)), msg("the column blocked: the nearest ring, east first, got " + east));
		helper.assertTrue(FlightRules.clearArrival(at, p -> p.x >= 104, 16, 16, open, 103).isEmpty(), msg("never past the edge's band"));
		helper.assertTrue(FlightRules.clearArrival(at, p -> p.x >= 120, 16, 16, open, 0).isEmpty(), msg("nothing within reach: empty"));

		TravelGameTests.openArena(helper);
		ServerLevel level = helper.getLevel();
		Vec3 point = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 12))).add(0, 0.5, 0);
		AABB hull = new AABB(-2.5, 0, -2.5, 2.5, 2.5, 2.5);
		Predicate<Vec3> fits = p -> Ships.hullFits(level, hull.move(p), null);
		helper.assertTrue(FlightRules.clearArrival(point, fits, FlightRules.CLEAR_UP, FlightRules.CLEAR_SIDE, open, 0).orElseThrow() == point,
				msg("already clear: the computed place"));

		helper.setBlock(new BlockPos(12, 2, 12), Blocks.STONE);
		Optional<Vec3> over = FlightRules.clearArrival(point, fits, FlightRules.CLEAR_UP, FlightRules.CLEAR_SIDE, open, 0);
		helper.assertTrue(over.isPresent() && over.get().equals(point.add(0, 2, 0)), msg("a block in the hull: two blocks up clears it, got " + over));

		for (int y = 1; y < 7; y++) {
			helper.setBlock(new BlockPos(12, y, 12), Blocks.STONE);
		}

		Optional<Vec3> aside = FlightRules.clearArrival(point, fits, 2, 4, open, 0);
		helper.assertTrue(aside.isPresent() && aside.get().equals(point.add(4, 0, 0)),
				msg("a pillar through the column: the second ring, four blocks east, got " + aside));
		helper.assertTrue(FlightRules.clearArrival(point, fits, 2, 2, open, 0).isEmpty(), msg("blocked all round within reach: nothing fits"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- across worlds (4.7, 4.10)

	/** The return to the last landing spot in another world: the body and both riders go there together, and land. */
	@GameTest(structure = ARENA, maxTicks = 400, skyAccess = true)
	public void rescueKeepsRidersAcrossLevels(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		ServerPlayer crew = scene.player(CREW_AT);
		UUID id = owner.getUUID();
		GlobalPos[] spot = new GlobalPos[1];

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			spot[0] = netherRoof(scene);
			aboard(scene, owner, 4, crew);
		});
		toHelm(scene, owner).await("the Nether's roof is entity-ticking", 100,
				() -> TravelTickets.ready(scene.server.getLevel(Level.NETHER), spot[0].pos(), 1)).then(() -> {
			// Detached data: the record's last landing spot is on the Nether's roof.
			Ships.update(scene.server, id, r -> r.withPos(spot[0], 90f));
			ShipMenu.open(owner, scene.record(id));
			press(owner, ShipActionPayload.RETURN_TO_LANDING, 0);
			helper.assertTrue(scene.record(id).flight().isPresent(), msg("the free daily recall sends the ship to its last landing spot"));
		}).await("the body reaches the Nether", 100, () -> {
			ShipBody ship = Ships.ship(scene.server, id);
			return ship != null && ship.level().dimension().equals(Level.NETHER);
		}).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(owner.getVehicle() == ship && crew.getVehicle() == ship, msg("both riders aboard the body in the Nether"));
			helper.assertTrue(owner.level() == ship.level() && crew.level() == ship.level(), msg("in the same world as the body"));
		}).await("the landing on the roof", 200, () -> TravelGameTests.landed(scene, id, List.of(owner, crew))).then(() -> {
			ShipRecord home = scene.record(id);
			ShipBody ship = body(scene, id);
			Vec3 roof = Vec3.atBottomCenterOf(spot[0].pos());
			helper.assertTrue(home.pos().equals(spot[0]) && home.state() == ShipState.READY, msg("READY on the roof: " + home));
			helper.assertTrue(ship.level().dimension().equals(Level.NETHER) && ship.position().distanceTo(roof) < 0.5, msg("the body stands there"));

			for (ServerPlayer rider : List.of(owner, crew)) {
				helper.assertTrue(rider.level() == ship.level() && rider.position().subtract(roof).horizontalDistance() <= 10,
						msg("a rider steps off on the roof, at " + rider.position()));
			}

			helper.assertTrue(TravelAttachments.get(owner).recallDay() == Clocks.day(scene.server), msg("today's free recall is used"));
			TravelTickets.release(scene.server, spot[0], ShipBalance.travel().chunkTicketRadius, 0);
		}).run();
	}

	/** At the helm with the last landing spot in another world (as after a jump into an orbit): the same body flies on with its riders. */
	@GameTest(structure = ARENA, maxTicks = 160, skyAccess = true)
	public void jumpFromOrbitKeepsBodyAndRiders(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		ServerPlayer crew = scene.player(CREW_AT);
		UUID id = owner.getUUID();
		UUID[] body = new UUID[1];
		int[] ticks = new int[1];
		GlobalPos away = GlobalPos.of(Level.NETHER, new BlockPos(0, ROOF_Y, 0));

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4, crew);
		});
		toHelm(scene, owner).then(() -> {
			body[0] = body(scene, id).getUUID();
			Ships.update(scene.server, id, r -> r.withPos(away, 0f));
		}).await("forty ticks with the last landing spot in another world", 45, () -> {
			ShipBody ship = Ships.ship(scene.server, id);
			ShipRecord record = scene.record(id);
			helper.assertTrue(ship != null && ship.getUUID().equals(body[0]) && ship.level() == scene.level, msg("the same body flies on in its own world"));
			helper.assertTrue(ship.piloted() && owner.getVehicle() == ship && crew.getVehicle() == ship, msg("piloted, both riders aboard"));
			helper.assertTrue(record.state() == ShipState.FLYING && Ships.busy(record) && !record.pos().dimension().equals(ship.level().dimension()),
					msg("the record keeps its spot in the other world"));
			return ++ticks[0] >= 40;
		}).run();
	}

	// ---------------------------------------------------------------- the shipwright (4.8, 4.9, 11.2)

	/** The refit's refusals and their order; live: riders aboard, away from the yard, a hold of ten, then nine stacks fit. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void refitNeedsSaltgateLandedEmptyAndHoldOfNine(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		ShipRecord ready = TravelGameTests.detached(ShipState.READY, 100, 4);
		ShipRecord full = ready.withHold(stacks(b.interceptorHold + 1));
		int credits = b.refitCredits;
		int alloy = b.refitAlloy;

		helper.assertTrue(FlightRules.refitRefusal(ready, true, true, false, credits, alloy, b) == Refit.OK, msg("landed at the yard, empty, paid: OK"));
		helper.assertTrue(FlightRules.refitRefusal(null, true, true, false, credits, alloy, b) == Refit.NO_SHIP, msg("no ship"));
		helper.assertTrue(FlightRules.refitRefusal(ready.withUpgrades(ShipRecord.CLASS_INTERCEPTOR), true, true, false, credits, alloy, b) == Refit.ALREADY,
				msg("an interceptor already"));
		helper.assertTrue(FlightRules.refitRefusal(ready, false, true, false, credits, alloy, b) == Refit.ALREADY, msg("a courier already"));
		helper.assertTrue(FlightRules.refitRefusal(ready, true, false, false, credits, alloy, b) == Refit.NOT_HERE, msg("not at the yard"));
		helper.assertTrue(FlightRules.refitRefusal(ready.withState(ShipState.FLYING), true, true, false, credits, alloy, b) == Refit.NOT_HERE, msg("in the air"));
		helper.assertTrue(FlightRules.refitRefusal(ready, true, true, true, credits, alloy, b) == Refit.RIDERS, msg("someone aboard"));
		helper.assertTrue(FlightRules.refitRefusal(full, true, true, false, credits, alloy, b) == Refit.HOLD, msg("ten stacks in the hold"));
		helper.assertTrue(FlightRules.refitRefusal(ready.withHold(stacks(b.interceptorHold)), true, true, false, credits, alloy, b) == Refit.OK,
				msg("nine stacks, gaps between them, fit"));
		helper.assertTrue(FlightRules.refitRefusal(ready, true, true, false, credits - 1, alloy, b) == Refit.CREDITS, msg("the credits"));
		helper.assertTrue(FlightRules.refitRefusal(ready, true, true, false, credits, alloy - 1, b) == Refit.ALLOY, msg("the alloy"));
		helper.assertTrue(FlightRules.refitRefusal(ready.withUpgrades(ShipRecord.CLASS_INTERCEPTOR), false, true, false, b.refitBackCredits, 0, b) == Refit.OK,
				msg("back to the courier: credits only"));
		// The order: class, yard, riders, hold, credits, alloy.
		helper.assertTrue(FlightRules.refitRefusal(full, true, false, true, 0, 0, b) == Refit.NOT_HERE, msg("the yard first"));
		helper.assertTrue(FlightRules.refitRefusal(full, true, true, true, 0, 0, b) == Refit.RIDERS, msg("riders before the hold"));
		helper.assertTrue(FlightRules.refitRefusal(full, true, true, false, 0, 0, b) == Refit.HOLD, msg("the hold before the credits"));
		helper.assertTrue(FlightRules.refitRefusal(ready, true, true, false, 0, 0, b) == Refit.CREDITS, msg("the credits before the alloy"));
		helper.assertTrue(FlightRules.holdStacks(full) == b.interceptorHold + 1 && full.hold().size() == 2 * (b.interceptorHold + 1) - 1,
				msg("empty slots are not stacks"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(OWNER_AT);
		UUID id = owner.getUUID();
		FlightBalance live = FlightBalance.get();

		scene.now(() -> {
			TravelGameTests.openArena(helper);
			ShipBody ship = scene.give(owner, START, 0f, 100, 4);
			Credits.give(owner, live.refitCredits);
			owner.getInventory().add(new ItemStack(M4Items.ALLOY_PLATE, live.refitAlloy));

			helper.assertTrue(owner.startRiding(ship, true, false), msg("the owner boards"));
			shipwright(owner, "ship:refit", "interceptor", null);
			helper.assertTrue(!Ships.interceptor(scene.record(id)) && Credits.count(owner, false) == live.refitCredits, msg("live: someone aboard: refused, nothing paid"));
			owner.stopRiding();

			Entity far = EntityTypes.PIG.create(scene.level, EntitySpawnReason.EVENT);
			helper.assertTrue(far != null, msg("a stand-in for a shipwright far away"));
			far.setPos(Vec3.atBottomCenterOf(helper.absolutePos(START)).add(100, 0, 0));
			shipwright(owner, "ship:refit", "interceptor", far);
			helper.assertTrue(!Ships.interceptor(scene.record(id)) && Credits.count(owner, false) == live.refitCredits, msg("live: not at his yard: refused"));

			Ships.update(scene.server, id, r -> r.withHold(stacks(live.interceptorHold + 1)));
			shipwright(owner, "ship:refit", "interceptor", null);
			helper.assertTrue(!Ships.interceptor(scene.record(id)) && Credits.count(owner, false) == live.refitCredits, msg("live: too many stacks: refused"));

			Ships.update(scene.server, id, r -> r.withHold(stacks(live.interceptorHold)));
			shipwright(owner, "ship:refit", "interceptor", null);
			ShipRecord refitted = scene.record(id);
			helper.assertTrue(Ships.interceptor(refitted) && FlightRules.holdStacks(refitted) == live.interceptorHold
					&& refitted.hold().size() == live.interceptorHold, msg("live: nine stacks fit; refitted, the hold packed"));
			helper.assertTrue(Credits.count(owner, false) == 0 && TravelGameTests.count(owner, M4Items.ALLOY_PLATE) == 0, msg("live: the credits and alloy paid"));
		});
	}

	/** The refit keeps the hull code, fuel, upgrades and paint; to the interceptor the hull is cut to 80, the crew to one and the hold packed. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void refitKeepsCodeFuelUpgradesAndOneCrew(GameTestHelper helper) {
		FlightBalance fb = new FlightBalance();
		ShipBalance sb = new ShipBalance();
		UUID first = UUID.randomUUID();
		List<UUID> crew = List.of(first, UUID.randomUUID(), UUID.randomUUID());
		int upgrades = ShipRecord.UPGRADE_SEAT | ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD;
		ShipRecord courier = ShipRules.withPaint(TravelGameTests.detached(ShipState.READY, 95, 3).withUpgrades(upgrades), ShipBody.PAINT_VASCH)
				.withCrew(crew).withHold(stacks(5));

		ShipRecord interceptor = FlightRules.refitted(courier, true, fb, sb);
		helper.assertTrue(interceptor.has(ShipRecord.CLASS_INTERCEPTOR) && (interceptor.upgrades() & ~ShipRecord.CLASS_INTERCEPTOR) == courier.upgrades(),
				msg("the class set, every upgrade and the paint kept"));
		helper.assertTrue(ShipRules.paint(interceptor.upgrades()) == ShipBody.PAINT_VASCH, msg("the paint is still Vasch's"));
		helper.assertTrue(interceptor.hullCode().equals(courier.hullCode()) && interceptor.fuel() == 3, msg("the hull code and the fuel kept"));
		helper.assertTrue(interceptor.crew().equals(List.of(first)), msg("one crew member: the first"));
		helper.assertTrue(interceptor.hull() == fb.interceptorHull, msg("the hull cut to the interceptor's " + fb.interceptorHull));
		helper.assertTrue(interceptor.hold().size() == 5 && FlightRules.holdStacks(interceptor) == 5, msg("the hold packed into its first slots"));
		helper.assertTrue(interceptor.state() == ShipState.READY, msg("READY"));

		ShipRecord back = FlightRules.refitted(interceptor, false, fb, sb);
		helper.assertTrue(!back.has(ShipRecord.CLASS_INTERCEPTOR) && back.upgrades() == courier.upgrades(), msg("back to the courier: the same upgrades"));
		helper.assertTrue(back.hull() == fb.interceptorHull && back.crew().equals(List.of(first)), msg("the hull and crew as they were"));
		helper.assertTrue(ShipRules.seats(back, sb) == sb.seatsUpgraded, msg("the passenger seat counts again"));
		helper.assertTrue(FlightRules.refitted(courier.withHull(30), true, fb, sb).hull() == 30, msg("a hull under 80 is kept"));
		helper.assertTrue(FlightRules.refitted(courier.withHull(15), true, fb, sb).state() == ShipState.DISABLED, msg("a low hull stays DISABLED"));
		helper.succeed();
	}

	/** Each upgrade at its price, once; the capacitor needs the shield, the legendary parts their item; live, through the shipwright. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void upgradesFitOnceAtTheirPrice(GameTestHelper helper) {
		FlightBalance b = new FlightBalance();
		ShipRecord ready = TravelGameTests.detached(ShipState.READY, 100, 4);
		int cannons = ShipRecord.UPGRADE_CANNONS;
		int capacitor = ShipRecord.PART_SPINDLE_CAPACITOR;
		int drive = ShipRecord.PART_HYMN_DRIVE;

		helper.assertTrue(FlightRules.fitPrice(cannons, b) == 800 && FlightRules.fitPrice(ShipRecord.UPGRADE_SHIELD, b) == 1000
				&& FlightRules.fitPrice(ShipRecord.UPGRADE_THRUSTERS, b) == 900 && FlightRules.fitPrice(ShipRecord.UPGRADE_MISSILES, b) == 1200,
				msg("the prices"));
		helper.assertTrue(FlightRules.fitPrice(capacitor, b) == b.legendaryFitCredits && FlightRules.fitPrice(drive, b) == b.legendaryFitCredits,
				msg("the legendary parts' fitting fee"));
		helper.assertTrue(FlightRules.fitRefusal(ready, cannons, true, false, 800, b) == Fit.OK, msg("cannons for 800"));
		helper.assertTrue(FlightRules.fitRefusal(ready, cannons, true, false, 799, b) == Fit.CREDITS, msg("799 is not enough"));
		helper.assertTrue(FlightRules.fitRefusal(null, cannons, true, false, 800, b) == Fit.NO_SHIP, msg("no ship"));
		helper.assertTrue(FlightRules.fitRefusal(ready, 0, true, false, 9999, b) == Fit.UNKNOWN
				&& FlightRules.fitRefusal(ready, ShipRecord.UPGRADE_SEAT, true, false, 9999, b) == Fit.UNKNOWN, msg("not his to fit"));
		helper.assertTrue(FlightRules.fitRefusal(ready.withUpgrades(cannons), cannons, true, false, 800, b) == Fit.ALREADY, msg("fitted once only"));
		helper.assertTrue(FlightRules.fitRefusal(ready, cannons, false, false, 800, b) == Fit.NOT_HERE, msg("not at the yard"));
		helper.assertTrue(FlightRules.fitRefusal(ready.withState(ShipState.FLYING), cannons, true, false, 800, b) == Fit.NOT_HERE, msg("in the air"));
		helper.assertTrue(FlightRules.fitRefusal(ready.withState(ShipState.HULK), cannons, true, false, 800, b) == Fit.NOT_HERE, msg("still the hulk"));
		helper.assertTrue(FlightRules.fitRefusal(ready, capacitor, true, true, 9999, b) == Fit.NEEDS_SHIELD, msg("the capacitor feeds a shield"));
		helper.assertTrue(FlightRules.fitRefusal(ready.withUpgrades(ShipRecord.UPGRADE_SHIELD), capacitor, true, false, 9999, b) == Fit.NO_PART,
				msg("the capacitor comes from the pack"));
		helper.assertTrue(FlightRules.fitRefusal(ready.withUpgrades(ShipRecord.UPGRADE_SHIELD), capacitor, true, true, 9999, b) == Fit.OK, msg("with both"));
		helper.assertTrue(FlightRules.fitRefusal(ready, drive, true, false, 9999, b) == Fit.NO_PART, msg("the drive comes from the pack"));
		// The order: known, fitted, yard, shield, part, credits.
		helper.assertTrue(FlightRules.fitRefusal(ready.withUpgrades(cannons), cannons, false, false, 0, b) == Fit.ALREADY, msg("fitted before the yard"));
		helper.assertTrue(FlightRules.fitRefusal(ready, capacitor, false, false, 0, b) == Fit.NOT_HERE, msg("the yard before the shield"));
		helper.assertTrue(FlightRules.fitRefusal(ready, capacitor, true, false, 0, b) == Fit.NEEDS_SHIELD, msg("the shield before the part"));
		helper.assertTrue(FlightRules.fitRefusal(ready, drive, true, false, 0, b) == Fit.NO_PART, msg("the part before the credits"));

		helper.assertTrue(FlightRules.upgradeOf("cannons") == cannons && FlightRules.upgradeOf(" Shield ") == ShipRecord.UPGRADE_SHIELD
				&& FlightRules.upgradeOf("THRUSTERS") == ShipRecord.UPGRADE_THRUSTERS && FlightRules.upgradeOf("missiles") == ShipRecord.UPGRADE_MISSILES,
				msg("the dialogue's ids, any case"));
		helper.assertTrue(FlightRules.upgradeOf("capacitor") == capacitor && FlightRules.upgradeOf("spindle_capacitor") == capacitor
				&& FlightRules.upgradeOf("hymn_drive") == drive && FlightRules.upgradeOf("hymn_tuned_drive") == drive && FlightRules.upgradeOf("drive") == drive,
				msg("the parts' aliases"));
		helper.assertTrue(FlightRules.upgradeOf("seat") == 0 && FlightRules.upgradeOf("") == 0, msg("anything else is unknown"));

		for (int bit : FlightRules.M5_UPGRADES) {
			helper.assertTrue(FlightRules.upgradeOf(FlightRules.upgradeId(bit)) == bit, msg("the id round-trips: " + FlightRules.upgradeId(bit)));
			helper.assertTrue(FlightRules.isPart(bit) == (bit == capacitor || bit == drive), msg("parts are the legendary ones only"));
		}

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(OWNER_AT);
		UUID id = owner.getUUID();
		FlightBalance live = FlightBalance.get();

		scene.now(() -> {
			helper.assertTrue(live.cannonsPrice < live.shieldPrice && live.legendaryFitCredits <= live.cannonsPrice, msg("precondition: the default prices"));
			TravelGameTests.openArena(helper);
			scene.give(owner, START, 0f, 100, 4);
			Credits.give(owner, live.cannonsPrice);
			shipwright(owner, "ship:fit", "cannons", null);
			helper.assertTrue(scene.record(id).has(cannons) && Credits.count(owner, false) == 0, msg("live: cannons fitted for " + live.cannonsPrice));

			Credits.give(owner, live.cannonsPrice);
			shipwright(owner, "ship:fit", "cannons", null);
			helper.assertTrue(Credits.count(owner, false) == live.cannonsPrice, msg("live: fitted once: the second order is refused, nothing paid"));
			shipwright(owner, "ship:fit", "shield", null);
			helper.assertTrue(!scene.record(id).has(ShipRecord.UPGRADE_SHIELD) && Credits.count(owner, false) == live.cannonsPrice,
					msg("live: too few credits for the shield"));
			shipwright(owner, "ship:fit", "hymn_drive", null);
			helper.assertTrue(!scene.record(id).has(drive) && Credits.count(owner, false) == live.cannonsPrice, msg("live: no drive in the pack"));

			owner.getInventory().add(new ItemStack(LegendaryItems.HYMN_TUNED_DRIVE));
			shipwright(owner, "ship:fit", "hymn_drive", null);
			helper.assertTrue(scene.record(id).has(drive) && Credits.count(owner, false) == live.cannonsPrice - live.legendaryFitCredits
					&& TravelGameTests.count(owner, LegendaryItems.HYMN_TUNED_DRIVE) == 0, msg("live: the drive fitted from the pack for the fee"));
		});
	}

	/** The shipwright takes an upgrade out for nothing and gives nothing back; the capacitor goes with the shield. */
	@GameTest(structure = ARENA, maxTicks = 40, skyAccess = true)
	public void shipwrightRemovesUpgradeWithoutRefund(GameTestHelper helper) {
		ShipBalance sb = new ShipBalance();
		int shield = ShipRecord.UPGRADE_SHIELD;
		int capacitor = ShipRecord.PART_SPINDLE_CAPACITOR;
		int cannons = ShipRecord.UPGRADE_CANNONS;
		ShipRecord armed = TravelGameTests.detached(ShipState.READY, 100, 4).withUpgrades(shield | capacitor | cannons);

		helper.assertTrue(FlightRules.canStrip(armed, shield, true), msg("a fitted upgrade at the yard comes out"));
		helper.assertTrue(!FlightRules.canStrip(null, shield, true) && !FlightRules.canStrip(armed, ShipRecord.UPGRADE_MISSILES, true),
				msg("nothing to take out"));
		helper.assertTrue(!FlightRules.canStrip(armed, shield, false), msg("not away from the yard"));
		helper.assertTrue(!FlightRules.canStrip(armed.withState(ShipState.FLYING), shield, true) && !FlightRules.canStrip(armed.withState(ShipState.HULK), shield, true),
				msg("not in the air, not on the hulk"));
		helper.assertTrue(!FlightRules.canStrip(armed.withUpgrades(armed.upgrades() | ShipRecord.CLASS_INTERCEPTOR), ShipRecord.CLASS_INTERCEPTOR, true)
				&& !FlightRules.canStrip(armed, 0, true), msg("the class is a refit, not an upgrade"));
		ShipRecord noShield = FlightRules.stripped(armed, shield, sb);
		helper.assertTrue(!noShield.has(shield) && !noShield.has(capacitor) && noShield.has(cannons), msg("the capacitor goes with the shield"));
		ShipRecord noCannons = FlightRules.stripped(armed, cannons, sb);
		helper.assertTrue(!noCannons.has(cannons) && noCannons.has(shield) && noCannons.has(capacitor), msg("the cannons go alone"));
		helper.assertTrue(FlightRules.stripped(armed, capacitor, sb).has(shield), msg("the capacitor goes alone"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(OWNER_AT);
		UUID id = owner.getUUID();

		scene.now(() -> {
			TravelGameTests.openArena(helper);
			scene.give(owner, START, 0f, 100, 4);
			Ships.update(scene.server, id, r -> r.withUpgrades(r.upgrades() | shield | capacitor));
			shipwright(owner, "ship:remove", "shield", null);
			ShipRecord stripped = scene.record(id);
			helper.assertTrue(!stripped.has(shield) && !stripped.has(capacitor), msg("live: the shield and its capacitor out"));
			helper.assertTrue(Credits.count(owner, false) == 0 && TravelGameTests.count(owner, LegendaryItems.SPINDLE_CAPACITOR) == 0,
					msg("live: no refund, no capacitor back"));
			shipwright(owner, "ship:remove", "shield", null);
			shipwright(owner, "ship:remove", "seat", null);
			helper.assertTrue(scene.record(id).upgrades() == stripped.upgrades(), msg("live: nothing more to take out: refused"));
		});
	}

	/** The interceptor: one gunner's seat, its own hold and 80 hull; live, the refit swaps the body and back, and a third rider finds no seat. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true)
	public void interceptorSeatsHoldAndHull(GameTestHelper helper) {
		ShipBalance sb = new ShipBalance();
		FlightBalance fb = new FlightBalance();
		ShipRecord courier = TravelGameTests.detached(ShipState.READY, 100, 4);
		ShipRecord interceptor = courier.withUpgrades(ShipRecord.CLASS_INTERCEPTOR);

		helper.assertTrue(ShipRules.seats(interceptor, sb) == 1 && ShipRules.seats(interceptor.withUpgrades(ShipRecord.CLASS_INTERCEPTOR | ShipRecord.UPGRADE_SEAT), sb) == 1,
				msg("one gunner's seat, with or without the passenger seat"));
		helper.assertTrue(ShipRules.crewLimit(interceptor, sb) == 1, msg("crew of one"));
		helper.assertTrue(ShipRules.holdSize(interceptor, sb) == Math.clamp(FlightBalance.get().interceptorHold, 0, ShipRecord.MAX_HOLD), msg("its own hold"));
		helper.assertTrue(ShipRules.maxHull(interceptor, fb) == fb.interceptorHull && ShipRules.maxHull(courier, fb) == 100, msg("80 hull against 100"));
		helper.assertTrue(ShipRules.tankSize(interceptor, sb) == ShipRules.tankSize(courier, sb), msg("the same tank"));
		helper.assertTrue(ShipRules.seats(courier, sb) == sb.seats && ShipRules.holdSize(courier, sb) == sb.hold, msg("the courier's numbers"));
		helper.assertTrue(ShipRules.addCrew(interceptor.withCrew(List.of(UUID.randomUUID())), UUID.randomUUID(), sb) == null, msg("no second crew member"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(OWNER_AT);
		ServerPlayer crew = scene.player(CREW_AT);
		ServerPlayer third = scene.player(THIRD_AT);
		UUID id = owner.getUUID();
		FlightBalance live = FlightBalance.get();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			scene.give(owner, START, 0f, 100, 4);
			Ships.update(scene.server, id, r -> r.withCrew(List.of(crew.getUUID())));
			Credits.give(owner, live.refitCredits);
			owner.getInventory().add(new ItemStack(M4Items.ALLOY_PLATE, live.refitAlloy));
			shipwright(owner, "ship:refit", "interceptor", null);
			ShipRecord refitted = scene.record(id);
			helper.assertTrue(Ships.interceptor(refitted) && refitted.hull() == ShipRules.maxHull(refitted), msg("refitted, the hull cut to the class's most"));
		}).await("the interceptor's body", 30, () -> Ships.ship(scene.server, id) instanceof Interceptor).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(ship.getBbWidth() == 5f && ship.getBbHeight() == 2.5f && ship.interceptor(), msg("a 5 × 2.5 interceptor"));
			helper.assertTrue(ship.maxHull() == live.interceptorHull && ship.hull() == live.interceptorHull, msg("it shows 80 of 80 hull"));
			helper.assertTrue(ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(START))) < 0.5, msg("on the same spot"));
			helper.assertTrue(owner.startRiding(ship, false, false) && crew.startRiding(ship, false, false), msg("the pilot and the gunner board"));
			helper.assertTrue(!third.startRiding(ship, false, false) && ship.getPassengers().size() == 2, msg("a third finds no seat"));
			owner.stopRiding();
			crew.stopRiding();
			Credits.give(owner, live.refitBackCredits);
			shipwright(owner, "ship:refit", "courier", null);
			helper.assertTrue(!Ships.interceptor(scene.record(id)) && scene.record(id).hull() == live.interceptorHull, msg("back to the courier, its hull kept"));
		}).await("the courier's body", 30, () -> Ships.ship(scene.server, id) instanceof CourierShuttle).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(!ship.interceptor() && ship.maxHull() == 100 && ship.hull() == live.interceptorHull, msg("a courier again, 80 of 100 hull"));
		}).run();
	}

	// ---------------------------------------------------------------- the disabled ship and the drive (5.4, 11.2)

	/** Disabled at the helm: the controls lock, it tumbles, and the free rescue takes it home with the rescue hull. */
	@GameTest(structure = ARENA, maxTicks = 380, skyAccess = true)
	public void disableTumblesThenFreeRescueAt20Hull(GameTestHelper helper) {
		FlightBalance fb = new FlightBalance();
		ShipBalance sb = new ShipBalance();
		ShipRecord wreck = TravelGameTests.detached(ShipState.FLYING, 0, 2);
		ShipRecord patched = FlightRules.rescued(wreck, fb, sb);
		helper.assertTrue(patched.hull() == fb.rescueHull && patched.state() == ShipState.READY && patched.fuel() == 2, msg("rescued at 20 hull, READY"));
		helper.assertTrue(FlightRules.rescued(wreck.withFuel(0), fb, sb).state() == ShipState.GROUNDED, msg("an empty tank: GROUNDED"));
		helper.assertTrue(FlightRules.rescued(wreck.withHull(55), fb, sb).hull() == 55, msg("a better hull is kept"));
		FlightBalance frail = new FlightBalance();
		frail.interceptorHull = 10;
		ShipRecord small = FlightRules.rescued(wreck.withUpgrades(ShipRecord.CLASS_INTERCEPTOR), frail, sb);
		helper.assertTrue(small.hull() == 10 && small.state() == ShipState.DISABLED, msg("never more than the class holds"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		FlightBalance live = FlightBalance.get();
		int rescueHull = Math.max(0, Math.min(live.rescueHull, 100));

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			Ships.update(scene.server, id, r -> r.withHull(0));
			Ships.disable(owner);
			ShipBody ship = body(scene, id);
			helper.assertTrue(!ship.piloted() && ship.getControllingPassenger() == null && owner.getVehicle() == ship, msg("the controls lock; the pilot stays aboard"));
			press(owner, ShipActionPayload.LAND, 0);
			helper.assertTrue(ship.phase() == ShipRules.PHASE_HELM && scene.record(id).flight().isEmpty(), msg("no landing while it tumbles"));
		}).await("the rescue after the tumble", live.tumbleTicks + 20, () -> scene.record(id).flight().isPresent()).then(() -> {
			helper.assertTrue(scene.record(id).hull() == rescueHull, msg("the rescue patches the hull to " + rescueHull + " as it starts"));
		}).await("the rescue lands", RESCUE_WAIT, () -> TravelGameTests.landed(scene, id, List.of(owner))).then(() -> {
			ShipRecord home = scene.record(id);
			helper.assertTrue(home.pos().equals(scene.at(START)) && home.hull() == rescueHull && home.state() == ShipRules.settleState(home, ShipBalance.get()),
					msg("home with the rescue hull: " + home));
			helper.assertTrue(beside(helper, owner, START), msg("the pilot steps off at home"));
		}).run();
	}

	/** The Hymn-Tuned Drive: no wear on a jump, Hyperspace halved, fitted for the legendary fee. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void hymnDriveNoWearHalfHyperspace(GameTestHelper helper) {
		FlightBalance fb = new FlightBalance();
		ShipBalance sb = new ShipBalance();
		ShipRecord plain = TravelGameTests.detached(ShipState.READY, 100, 4);
		ShipRecord tuned = plain.withUpgrades(ShipRecord.PART_HYMN_DRIVE);

		helper.assertTrue(ShipRules.wear(tuned, sb) == 0 && ShipRules.wear(tuned.withUpgrades(tuned.upgrades() | ShipRecord.UPGRADE_PLATING), sb) == 0,
				msg("no wear with the drive, plating or not"));
		helper.assertTrue(ShipRules.wear(plain, sb) == sb.wearPerJump && ShipRules.wear(plain.withUpgrades(ShipRecord.UPGRADE_PLATING), sb) == sb.wearReinforced,
				msg("the usual wear without it"));
		helper.assertTrue(FlightRules.hyperspaceTicks(tuned, 80, fb) == 40 && FlightRules.hyperspaceTicks(plain, 80, fb) == 80, msg("Hyperspace halved"));
		FlightBalance none = new FlightBalance();
		none.hymnDriveHyperspaceMult = 0f;
		helper.assertTrue(FlightRules.hyperspaceTicks(tuned, 80, none) == 1, msg("never shorter than a tick"));
		ShipRecord jumped = ShipRules.afterJump(tuned, sb);
		helper.assertTrue(jumped.hull() == 100 && jumped.fuel() == 4 - sb.cellsPerJump, msg("a jump takes the cell, not the hull"));
		helper.assertTrue(!ShipRules.jumpLeavesHullLow(tuned.withHull(sb.jumpMinHull), sb) && ShipRules.jumpLeavesHullLow(plain.withHull(sb.jumpMinHull), sb),
				msg("no low-hull warning with the drive"));
		helper.assertTrue(FlightRules.fitPrice(ShipRecord.PART_HYMN_DRIVE, fb) == fb.legendaryFitCredits, msg("the fitting fee"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Nav and Status tabs in flight (4.5, 4.10)

	/** The Nav's Land at: the autopilot from the air, a free hop on the planet below; the Helm key in its Descent takes the helm back. */
	@GameTest(structure = ARENA, maxTicks = 260, skyAccess = true)
	public void handToAutopilotAndTakeHelmInDescent(GameTestHelper helper) {
		ShipRecord landing = TravelGameTests.detached(ShipState.LANDING, 100, 3);
		helper.assertTrue(FlightRules.takeOverRefusal(landing, true, true, true) == Refusal.NONE, msg("in the Descent: NONE"));
		helper.assertTrue(FlightRules.takeOverRefusal(null, true, true, true) == Refusal.NOT_OWNER, msg("no ship"));
		helper.assertTrue(FlightRules.takeOverRefusal(landing, false, true, true) == Refusal.DISABLED_BY_CONFIG, msg("flight off"));
		helper.assertTrue(FlightRules.takeOverRefusal(landing, true, false, true) == Refusal.NO_HELM, msg("the helm locked"));
		helper.assertTrue(FlightRules.takeOverRefusal(landing, true, true, false) == Refusal.BUSY, msg("any other phase: busy"));
		helper.assertTrue(FlightRules.takeOverRefusal(landing, false, false, false) == Refusal.DISABLED_BY_CONFIG, msg("the config switch first"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			TravelGameTests.homeBeacon(scene, owner);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			ShipMenu.open(owner, scene.record(id));
			press(owner, ShipActionPayload.HAND_TO_AUTOPILOT, Destination.HOME.ordinal());
			ShipRecord record = scene.record(id);
			helper.assertTrue(record.state() == ShipState.LAUNCHING
					&& record.flight().map(f -> f.to() == Destination.HOME && f.phase() == ShipRules.PHASE_JUMP).orElse(false),
					msg("the autopilot takes the ship from its Jump: " + record));
			helper.assertTrue(!body(scene, id).piloted() && !(owner.containerMenu instanceof ShipMenu), msg("the helm released, the screen closed"));
		}).await("the autopilot's Descent", 140, () -> scene.record(id).flight().map(f -> f.phase() == ShipRules.PHASE_DESCENT).orElse(false)).then(() -> {
			ShipRecord descending = scene.record(id);
			helper.assertTrue(descending.pos().equals(scene.at(PAD)) && descending.fuel() == 4 && descending.hull() == 100,
					msg("a hop on the planet below costs nothing; the record names the pad: " + descending));
			press(owner, ShipActionPayload.TAKE_HELM, 0);
			ShipRecord taken = scene.record(id);
			helper.assertTrue(atHelm(scene, owner) && taken.flight().isEmpty() && taken.pos().equals(scene.at(PAD)),
					msg("the helm taken back; the pad stays the last landing spot"));
			helper.assertTrue(body(scene, id).getY() > helper.absolutePos(PAD).getY() + 2, msg("still in the air"));
		}).idle(5).then(() -> {
			helper.assertTrue(atHelm(scene, owner) && scene.record(id).flight().isEmpty(), msg("the landing stays abandoned"));
		}).run();
	}

	/** The return to the last landing: the free daily recall first, then a cell from the tank, then one carried; refused with none. */
	@GameTest(structure = ARENA, maxTicks = 320, skyAccess = true)
	public void returnToLastLandingCostsACellOrDailyRecall(GameTestHelper helper) {
		ShipBalance.Travel t = new ShipBalance.Travel();
		helper.assertTrue(FlightRules.returnCost(10, -1, 0, false, t) == ReturnCost.FREE && FlightRules.returnCost(10, 9, 0, false, t) == ReturnCost.FREE,
				msg("not used today: free"));
		helper.assertTrue(FlightRules.returnCost(10, 10, 2, true, t) == ReturnCost.TANK, msg("used today: a cell from the tank first"));
		helper.assertTrue(FlightRules.returnCost(10, 10, 0, true, t) == ReturnCost.CARRIED, msg("then a carried one"));
		helper.assertTrue(FlightRules.returnCost(10, 10, 0, false, t) == ReturnCost.NONE, msg("then nothing"));
		ShipBalance.Travel noFree = new ShipBalance.Travel();
		noFree.freeRecallPerDay = 0;
		helper.assertTrue(FlightRules.returnCost(10, -1, 2, false, noFree) == ReturnCost.TANK, msg("no free recall configured: the tank"));

		ShipRecord flying = TravelGameTests.detached(ShipState.FLYING, 100, 2);
		helper.assertTrue(FlightRules.returnRefusal(flying, true, ReturnCost.FREE, false, false) == Refusal.NONE, msg("at the helm: NONE"));
		helper.assertTrue(FlightRules.returnRefusal(null, true, ReturnCost.FREE, false, false) == Refusal.NOT_OWNER, msg("no ship"));
		helper.assertTrue(FlightRules.returnRefusal(flying, false, ReturnCost.FREE, false, false) == Refusal.NOT_READY, msg("not at the helm"));
		helper.assertTrue(FlightRules.returnRefusal(flying.withState(ShipState.READY), true, ReturnCost.FREE, false, false) == Refusal.NOT_READY,
				msg("not in the air"));
		helper.assertTrue(FlightRules.returnRefusal(flying, true, ReturnCost.NONE, false, false) == Refusal.NO_FUEL, msg("nothing to pay with"));
		helper.assertTrue(FlightRules.returnRefusal(flying, true, ReturnCost.TANK, true, false) == Refusal.UNDER_FIRE, msg("under fire"));
		helper.assertTrue(FlightRules.returnRefusal(flying, true, ReturnCost.CARRIED, false, true) == Refusal.COMBAT, msg("the combat lock"));
		helper.assertTrue(FlightRules.returnRefusal(flying, true, ReturnCost.NONE, true, true) == Refusal.NO_FUEL, msg("payment before fire"));
		helper.assertTrue(FlightRules.returnRefusal(flying.withHull(3), true, ReturnCost.FREE, false, false) == Refusal.NONE, msg("a low hull does not stop it"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		long[] today = new long[1];

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			today[0] = Clocks.day(scene.server);
			TravelAttachments.set(owner, TravelAttachments.get(owner).withRecallDay(today[0]));
			Ships.update(scene.server, id, r -> r.withFuel(0));
			ShipMenu.open(owner, scene.record(id));
			helper.assertTrue(owner.containerMenu instanceof ShipMenu, msg("precondition: the ship screen is open"));
			press(owner, ShipActionPayload.RETURN_TO_LANDING, 0);
			helper.assertTrue(atHelm(scene, owner) && owner.containerMenu instanceof ShipMenu, msg("today's recall used, no cell: refused, the screen stays"));

			Ships.update(scene.server, id, r -> r.withFuel(2));
			press(owner, ShipActionPayload.RETURN_TO_LANDING, 0);
			ShipRecord paid = scene.record(id);
			helper.assertTrue(paid.flight().isPresent() && paid.fuel() == 1, msg("a cell from the tank pays for it: " + paid));
			helper.assertTrue(!(owner.containerMenu instanceof ShipMenu), msg("the screen closes as the ship heads home"));
		}).await("the ship lands at its last landing spot", RESCUE_WAIT, () -> TravelGameTests.landed(scene, id, List.of(owner))).then(() -> {
			ShipRecord home = scene.record(id);
			helper.assertTrue(home.pos().equals(scene.at(START)) && home.state() == ShipState.READY && home.fuel() == 1 && home.hull() == 100,
					msg("READY at its last landing spot, one cell left: " + home));
			helper.assertTrue(TravelAttachments.get(owner).recallDay() == today[0] && beside(helper, owner, START), msg("the pilot steps off there"));
		}).run();
	}

	/** {@code allow-flight=false}: neither of vanilla's floating kicks can fire for a pilot at the helm (the client-side check is 19.2). */
	@GameTest(structure = ARENA, maxTicks = 220, skyAccess = true)
	public void allowFlightFalseNeverKicksPilot(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(OWNER_AT);
		UUID id = owner.getUUID();
		double[] height = new double[1];

		scene.then(() -> {
			TravelGameTests.openArena(helper);
			aboard(scene, owner, 4);
		});
		toHelm(scene, owner).then(() -> {
			ShipBody ship = body(scene, id);
			height[0] = ship.getY();
			// The vehicle kick needs a vehicle that is neither a flying vehicle nor gravity-free; the player kick, a player riding nothing.
			helper.assertTrue(ship.isFlyingVehicle() && ship.isNoGravity() && ship.getGravity() < 1.0e-5, msg("a gravity-free flying vehicle"));
			helper.assertTrue(owner.isPassenger() && ship.getControllingPassenger() == owner, msg("its pilot rides it and controls it"));
		}).idle(100).then(() -> {
			ShipBody ship = body(scene, id);
			helper.assertTrue(!owner.hasDisconnected() && scene.server.getPlayerList().getPlayer(id) == owner, msg("the pilot is still connected"));
			helper.assertTrue(owner.getVehicle() == ship && atHelm(scene, owner) && ship.getY() == height[0], msg("still at the helm, the ship hovering in place"));
		}).run();
	}

	// ---------------------------------------------------------------- helpers

	/** The owner's ship at {@link #START} with full hull, {@code fuel} cells and the helm unlocked; the owner and {@code crew} aboard. */
	private static ShipBody aboard(Scene scene, ServerPlayer owner, int fuel, ServerPlayer... crew) {
		TravelAttachments.set(owner, TravelAttachments.get(owner).withHelm(true));
		ShipBody ship = scene.give(owner, START, 0f, 100, fuel);
		List<UUID> ids = new ArrayList<>();

		for (ServerPlayer member : crew) {
			ids.add(member.getUUID());
		}

		Ships.update(scene.server, owner.getUUID(), r -> r.withCrew(ids));
		scene.helper.assertTrue(owner.startRiding(ship, true, false), msg("the owner boards"));

		for (ServerPlayer member : crew) {
			scene.helper.assertTrue(member.startRiding(ship, true, false), msg("a crew member boards"));
		}

		return ship;
	}

	/** Sends {@code button} as the client's {@code ship_action} would: the Helm key's, or a ship screen button's. */
	private static void press(ServerPlayer player, int button, int arg) {
		ShipActions.handle(player, new ShipActionPayload(button, arg, ""));
	}

	/** Presses the Helm key and waits for the hand-over. */
	private static Scene toHelm(Scene scene, ServerPlayer owner) {
		return scene.then(() -> press(owner, ShipActionPayload.TAKE_HELM, 0)).await("the hand-over at the helm", HELM_WAIT, () -> atHelm(scene, owner));
	}

	/** Whether the owner flies their ship by hand: FLYING with no autopilot run, the piloted flag set and the owner its controlling passenger. */
	private static boolean atHelm(Scene scene, ServerPlayer owner) {
		ShipRecord record = ShipRegistry.of(scene.server, owner.getUUID()).orElse(null);
		ShipBody ship = Ships.ship(scene.server, owner.getUUID());
		return record != null && ship != null && record.state() == ShipState.FLYING && record.flight().isEmpty() && ship.piloted()
				&& ship.getControllingPassenger() == owner;
	}

	/** Whether the owner's ship still stands at rest: READY and not busy (the helm was refused). */
	private static boolean resting(Scene scene, UUID owner) {
		ShipRecord record = scene.record(owner);
		return record.state() == ShipState.READY && !Ships.busy(record);
	}

	/** The owner's loaded body; the test fails without one. */
	private static ShipBody body(Scene scene, UUID owner) {
		ShipBody ship = Ships.ship(scene.server, owner);
		scene.helper.assertTrue(ship != null, msg("the ship's body is loaded"));
		return ship;
	}

	/** Whether {@code player} stands on the ground beside the ship's spot at {@code rel}: within 10 blocks across, at its height. */
	private static boolean beside(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		return player.position().subtract(spot).horizontalDistance() <= 10 && Math.abs(player.getY() - spot.y) <= 1.5;
	}

	/**
	 * Flies the piloted ship to {@code target} as its pilot's client would: moves of a block or so,
	 * well under the cap, then a still one, so the server measures it at rest.
	 */
	private static void flyTo(ShipBody ship, Vec3 target) {
		Vec3 delta = target.subtract(ship.position());
		int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(delta.x), Math.max(Math.abs(delta.y), Math.abs(delta.z)))));
		Vec3 step = delta.scale(1.0 / steps);

		for (int i = 0; i < steps; i++) {
			ship.move(MoverType.PLAYER, step);
		}

		ship.move(MoverType.PLAYER, target.subtract(ship.position()));
		ship.move(MoverType.PLAYER, Vec3.ZERO);
	}

	/** Runs one of the shipwright's dialogue actions; a null {@code speaker} stands for him right beside the player. */
	private static void shipwright(ServerPlayer player, String action, String arg, Entity speaker) {
		DialogueActions.ArgAction run = DialogueActions.getArg(action);

		if (run == null) {
			throw new IllegalStateException("No dialogue argument action " + action);
		}

		run.run(player, speaker, arg);
	}

	/**
	 * A landing spot on the Nether's bedrock roof (the game-test server's other world) above this
	 * test's arena, so tests running alongside never share it. It is held under the flights' ticket,
	 * and the chunks two around it are generated now, so a ship sent there finds it ready: a flight
	 * jumps there once the spot is entity-ticking ({@link TravelTickets#ready}), which a chunk is only
	 * with the chunks two around it loaded, and the game-test server runs its ticks back to back, so
	 * a cold Nether generating them in the background takes more ticks than the test waits.
	 */
	private static GlobalPos netherRoof(Scene scene) {
		ServerLevel nether = scene.server.getLevel(Level.NETHER);
		scene.helper.assertTrue(nether != null, msg("precondition: the Nether exists"));
		BlockPos arena = scene.helper.absolutePos(START);
		BlockPos spot = new BlockPos(arena.getX(), ROOF_Y, arena.getZ());
		GlobalPos pos = GlobalPos.of(Level.NETHER, spot);
		TravelTickets.hold(scene.server, pos, ShipBalance.travel().chunkTicketRadius);

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				nether.getChunk((spot.getX() >> 4) + dx, (spot.getZ() >> 4) + dz);
			}
		}

		scene.helper.assertTrue(nether.getBlockState(spot.below()).is(Blocks.BEDROCK) && nether.getBlockState(spot).isAir(),
				msg("precondition: the Nether's roof is bedrock under " + spot.toShortString()));
		return pos;
	}

	/** The record saved and loaded with the registry's codec, as a restart does. */
	private static ShipRecord roundTrip(GameTestHelper helper, ShipRecord record) {
		var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonElement json = ShipRecord.CODEC.encodeStart(ops, record).getOrThrow();
		return ShipRecord.CODEC.parse(ops, json).getOrThrow();
	}

	/**
	 * The fastest a profile ever flies over 400 ticks of every key held at once, boosting, with the
	 * look sweeping round and up and down, climbing and diving by turns.
	 */
	private static double flatOut(FlightModel.Profile profile) {
		FlightModel.Motion motion = FlightModel.Motion.STILL;
		FlightModel.Pose pose = new FlightModel.Pose(0f, 0f);
		double fastest = 0;

		for (int t = 0; t < 400; t++) {
			boolean up = t / 50 % 2 == 0;
			FlightModel.Controls controls = new FlightModel.Controls(true, false, false, true, up, !up, t * 7f, t % 80 - 40f, true);
			pose = FlightModel.turn(pose, controls, profile);
			motion = FlightModel.accelerate(motion, controls, profile);
			fastest = Math.max(fastest, FlightModel.velocity(motion, pose, profile.cap()).length());
		}

		return fastest;
	}

	/** {@code n} stacks of dirt with an empty slot between each two, as a hold left untidy. */
	private static List<ItemStack> stacks(int n) {
		List<ItemStack> hold = new ArrayList<>();

		for (int i = 0; i < n; i++) {
			if (i > 0) {
				hold.add(ItemStack.EMPTY);
			}

			hold.add(new ItemStack(Items.DIRT, 16));
		}

		return hold;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-6;
	}
}

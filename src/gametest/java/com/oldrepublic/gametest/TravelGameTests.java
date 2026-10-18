package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.CourierShuttle;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.LandingBeaconBlock;
import com.oldrepublic.travel.NavData;
import com.oldrepublic.travel.ShipActions;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipEntities;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipRules.Recall;
import com.oldrepublic.travel.ShipRules.Recovery;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelBlocks;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.TravelService.Refusal;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 ship and travel tests (spec MILESTONE_4.md 17.1, W3). The rules are checked as pure
 * functions on detached records with the default balance ({@code new ShipBalance()}); bodies,
 * flights, the beacon, recalls and tows run for real in the lead's {@code arena_24x7x24} fixture,
 * laid with grass where a pad needs natural ground, with listed mock players (Fabric's
 * {@code FakePlayer} refuses to ride) and the live balance.
 *
 * <p>A flight test sets a landing beacon at {@link #BEACON}, whose pad is {@link #PAD}, puts the
 * ship at {@link #START} and flies home with the command's instant sequence (every phase one tick);
 * recalls and tows fly their normal length. Each test removes its own players, bodies and ship
 * records when it ends, pass or fail, and only those, so tests running alongside keep theirs.
 */
public class TravelGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** The beacon of the flight tests and the pad it marks (one area's reach north of it), and where the ship starts. */
	private static final BlockPos BEACON = new BlockPos(6, 1, 12);
	private static final BlockPos PAD = new BlockPos(6, 1, 6);
	private static final BlockPos START = new BlockPos(17, 1, 17);
	/** Ticks a recall or tow may take: the jump, hyperspace, descent and settle, and a margin. */
	private static final int RECALL_TICKS = 280;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- launching (4.6)

	/** Every launch refusal and its order, as a pure rule; then the live check for the owner, the lock and the combat lock. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void launchRefusalsMatrix(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipRecord ready = detached(ShipState.READY, 100, 4);

		helper.assertTrue(ShipRules.launchRefusal(ready, true, false, true, false, b) == Refusal.NONE, msg("a READY, fuelled, sound ship launches"));
		helper.assertTrue(ShipRules.launchRefusal(null, true, false, true, false, b) == Refusal.NOT_OWNER, msg("no ship: not the owner"));
		helper.assertTrue(ShipRules.launchRefusal(ready, false, false, true, false, b) == Refusal.DISABLED_BY_CONFIG, msg("the config switch"));
		helper.assertTrue(ShipRules.launchRefusal(ready, true, true, true, false, b) == Refusal.BUSY, msg("a flight already running"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withState(ShipState.LAUNCHING), true, false, true, false, b) == Refusal.BUSY, msg("a ship in the air"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, 3))),
				true, false, true, false, b) == Refusal.BUSY, msg("a record with a flight on it"));
		helper.assertTrue(ShipRules.launchRefusal(ready, true, false, false, false, b) == Refusal.LOCKED, msg("a locked destination"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withState(ShipState.HULK).withHull(0), true, false, true, false, b) == Refusal.NOT_READY,
				msg("the hulk is not ready (its hull does not count)"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withState(ShipState.GROUNDED).withFuel(0), true, false, true, false, b) == Refusal.NO_FUEL,
				msg("GROUNDED: no fuel"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withFuel(b.cellsPerJump - 1), true, false, true, false, b) == Refusal.NO_FUEL,
				msg("less than a jump's cells"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withState(ShipState.DISABLED).withHull(10), true, false, true, false, b) == Refusal.HULL,
				msg("DISABLED: the hull"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withHull(b.jumpMinHull - 1), true, false, true, false, b) == Refusal.HULL, msg("hull below the jump minimum"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withHull(b.jumpMinHull), true, false, true, false, b) == Refusal.NONE, msg("hull at the minimum flies"));
		helper.assertTrue(ShipRules.launchRefusal(ready, true, false, true, true, b) == Refusal.COMBAT, msg("the combat lock"));
		// The order: config, busy, lock, hull, fuel, state, combat.
		helper.assertTrue(ShipRules.launchRefusal(ready, false, true, false, true, b) == Refusal.DISABLED_BY_CONFIG, msg("the config switch comes first"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withHull(5), true, true, false, true, b) == Refusal.BUSY, msg("busy before the lock and the hull"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withHull(5), true, false, false, true, b) == Refusal.LOCKED, msg("the lock before the hull"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withHull(5).withFuel(0), true, false, true, true, b) == Refusal.HULL, msg("the hull before fuel and combat"));
		helper.assertTrue(ShipRules.launchRefusal(ready.withFuel(0), true, false, true, true, b) == Refusal.NO_FUEL, msg("fuel before combat"));

		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerPlayer owner = scene.player(new BlockPos(12, 1, 4));
			helper.assertTrue(TravelService.canLaunch(owner, Destination.HOME) == Refusal.NOT_OWNER, msg("live: a player without a ship"));

			if (!ShipBalance.travel().enabled) {
				return;
			}

			scene.owns(owner.getUUID());
			ShipRegistry.put(scene.server, detached(ShipState.READY, 100, 4, owner.getUUID(), scene.at(new BlockPos(12, 1, 6))));
			helper.assertTrue(TravelService.canLaunch(owner, Destination.GREENWATCH) == Refusal.LOCKED, msg("live: a new player has only home unlocked"));
			helper.assertTrue(TravelService.canLaunch(owner, Destination.HOME) == Refusal.NOT_READY, msg("live: a ship whose body is not loaded is not ready"));

			if (ShipBalance.travel().combatLock) {
				Mob zombie = scene.track(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(12, 1, 9)));
				DamageSource bite = scene.level.damageSources().mobAttack(zombie);
				ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(owner, bite, 2f, 2f, false);
				helper.assertTrue(TravelService.canLaunch(owner, Destination.HOME) == Refusal.COMBAT, msg("live: a hostile near the owner hit them lately"));
				zombie.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(23, 1, 23))));
				helper.assertTrue(TravelService.canLaunch(owner, Destination.HOME) == Refusal.NOT_READY, msg("live: the attacker is out of range"));
			}
		});
	}

	/** A jump burns one cell and wears 5 hull (3 with reinforced plating); a real instant flight home does the same. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true)
	public void jumpTakesOneCellAndWear(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipRecord ready = detached(ShipState.READY, 100, 4);
		ShipRecord after = ShipRules.afterJump(ready, b);
		helper.assertTrue(after.fuel() == 4 - b.cellsPerJump && b.cellsPerJump == 1, msg("one cell per jump, got fuel " + after.fuel()));
		helper.assertTrue(after.hull() == 100 - b.wearPerJump && b.wearPerJump == 5, msg("5 hull per jump, got " + after.hull()));
		ShipRecord plated = ShipRules.afterJump(ready.withUpgrades(ShipRecord.UPGRADE_PLATING), b);
		helper.assertTrue(plated.hull() == 100 - b.wearReinforced && b.wearReinforced == 3, msg("reinforced plating: 3 hull, got " + plated.hull()));
		helper.assertTrue(ShipRules.afterJump(ready.withFuel(0), b).fuel() == 0, msg("the tank never goes below empty"));
		helper.assertTrue(after.state() == ShipState.READY && after.upgrades() == ready.upgrades(), msg("the jump changes only fuel and hull"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(new BlockPos(12, 1, 20));
		ShipBalance live = ShipBalance.get();
		scene.then(() -> launchHome(scene, owner, 100, 4))
				.await("the flight is over", 80, () -> landed(scene, owner.getUUID(), List.of(owner)))
				.then(() -> {
					ShipRecord landed = scene.record(owner.getUUID());
					helper.assertTrue(landed.fuel() == 4 - live.cellsPerJump, msg("live: one cell burnt, fuel " + landed.fuel()));
					helper.assertTrue(landed.hull() == 100 - ShipRules.wear(landed, live), msg("live: the jump's wear, hull " + landed.hull()));
					helper.assertTrue(landed.state() == ShipState.READY, msg("live: READY at the pad, got " + landed.state()));
					helper.assertTrue(landed.pos().equals(scene.at(PAD)), msg("live: the record stands on the beacon's pad, got " + landed.pos()));
					CourierShuttle ship = Ships.shuttle(scene.server, owner.getUUID());
					helper.assertTrue(ship != null && ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(PAD))) < 0.5,
							msg("live: the body stands on the pad"));
					helper.assertTrue(ship.getUUID().equals(landed.body()), msg("live: the same body flew (the jump keeps its UUID)"));
				})
				.run();
	}

	/** Below the jump minimum a ship is DISABLED: no launch, the failing wear on the body, a tow not a recall; a plate back over brings it back. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void disabledBelowJumpMinHull(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipBalance.Travel t = new ShipBalance.Travel();
		ShipRecord worn = detached(ShipState.READY, b.jumpMinHull + 2, 3);
		helper.assertTrue(ShipRules.jumpLeavesHullLow(worn, b), msg("the nav console warns: this jump leaves the hull below " + b.jumpMinHull));
		helper.assertTrue(!ShipRules.jumpLeavesHullLow(worn.withHull(b.jumpMinHull + b.wearPerJump), b), msg("no warning when it stays at the minimum"));
		ShipRecord jumped = ShipRules.afterJump(worn, b);
		ShipRecord settled = jumped.withState(ShipRules.settleState(jumped, b));
		helper.assertTrue(settled.state() == ShipState.DISABLED, msg("hull " + jumped.hull() + " settles DISABLED"));
		helper.assertTrue(ShipRules.launchRefusal(settled, true, false, true, false, b) == Refusal.HULL, msg("a DISABLED ship cannot launch"));
		helper.assertTrue(ShipRules.fuel(settled, 1, b).state() == ShipState.DISABLED, msg("fuel does not lift DISABLED"));
		helper.assertTrue(ShipRules.withUpgrade(settled, ShipRecord.UPGRADE_SEAT, true, b).state() == ShipState.DISABLED, msg("an upgrade does not lift it"));
		helper.assertTrue(ShipRules.recall(settled, true, false, false, true, false, 5, -1, -1, t) == Recall.TOW, msg("a DISABLED ship is towed, not recalled"));
		ShipRecord plated = ShipRules.plate(settled, false, b);
		helper.assertTrue(plated.hull() >= b.jumpMinHull && plated.state() == ShipState.READY, msg("a plate back over the minimum: READY again"));
		helper.assertTrue(ShipRules.wearLevel(settled, b) == ShipRules.WEAR_FAILING && ShipRules.wearLevel(worn.withHull(b.damageFxHull - 1), b) == ShipRules.WEAR_WORN
				&& ShipRules.wearLevel(worn.withHull(b.damageFxHull), b) == ShipRules.WEAR_NONE
				&& ShipRules.wearLevel(settled.withState(ShipState.HULK), b) == ShipRules.WEAR_NONE, msg("the wear levels the body shows"));

		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerPlayer owner = scene.player(new BlockPos(12, 1, 4));
			CourierShuttle ship = scene.give(owner, new BlockPos(12, 1, 14), 0f, 100, 4);
			ShipBalance live = ShipBalance.get();
			Ships.update(scene.server, owner.getUUID(), r -> rest(r.withHull(live.jumpMinHull - 5), live));
			helper.assertTrue(scene.record(owner.getUUID()).state() == ShipState.DISABLED, msg("live: the record is DISABLED"));
			helper.assertTrue(ship.state() == ShipState.DISABLED && ship.wearLevel() == ShipRules.WEAR_FAILING,
					msg("live: the body shows DISABLED and the failing wear at once"));
			helper.assertTrue(owner.startRiding(ship, true, false), msg("live: the owner boards"));
			helper.assertTrue(TravelService.canLaunch(owner, Destination.HOME) == Refusal.HULL, msg("live: the launch is refused for the hull"));
		});
	}

	// ---------------------------------------------------------------- repairs (4.2–4.4)

	/** A plate adds 5 hull (8 for a skilled smith) up to 100, only on a repaired ship on the ground; welding has a cooldown. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void plateRepairAndSkilledRepair(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipRecord worn = detached(ShipState.READY, 50, 2);
		helper.assertTrue(ShipRules.plate(worn, false, b).hull() == 55 && b.plateRepair == 5, msg("a plate: +5"));
		helper.assertTrue(ShipRules.plate(worn, true, b).hull() == 58 && b.plateRepairSkilled == 8, msg("Armourcraft or Armstech level 2: +8"));
		helper.assertTrue(ShipRules.plate(worn.withHull(98), true, b).hull() == 100, msg("never over 100"));
		helper.assertTrue(ShipRules.canPlate(worn) && ShipRules.canPlate(worn.withState(ShipState.DISABLED).withHull(10)), msg("READY and DISABLED ships take plates"));
		helper.assertTrue(!ShipRules.canPlate(worn.withHull(100)) && !ShipRules.canPlate(worn.withState(ShipState.HULK))
				&& !ShipRules.canPlate(worn.withState(ShipState.IN_TRANSIT)), msg("not at full hull, on the hulk (its sockets) or in flight"));
		ShipRecord disabled = detached(ShipState.DISABLED, b.jumpMinHull - 3, 2);
		helper.assertTrue(ShipRules.plate(disabled, false, b).state() == ShipState.READY, msg("a plate over the minimum lifts DISABLED"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(new BlockPos(12, 1, 6));
		ShipBalance live = ShipBalance.get();
		CourierShuttle[] ship = new CourierShuttle[1];
		scene.then(() -> {
			ship[0] = scene.give(owner, new BlockPos(12, 1, 14), 0f, 50, 2);
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(M3Items.DURASTEEL_PLATE, 3));
			ship[0].interact(owner, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(scene.record(owner.getUUID()).hull() == 50 + live.plateRepair, msg("live: the weld adds a plate's hull"));
			helper.assertTrue(owner.getMainHandItem().getCount() == 2, msg("live: one plate used"));
			ship[0].interact(owner, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(scene.record(owner.getUUID()).hull() == 50 + live.plateRepair && owner.getMainHandItem().getCount() == 2,
					msg("live: a second weld inside the cooldown does nothing"));
		}).idle(live.plateRepairCooldown + 1).then(() -> {
			ship[0].interact(owner, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(scene.record(owner.getUUID()).hull() == 50 + 2 * live.plateRepair && owner.getMainHandItem().getCount() == 1,
					msg("live: after the cooldown the next plate goes on"));
		}).run();
	}

	/** Each socket takes only its own part, from the inventory: plates up to the count, a loaded nav computer, nothing else. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void repairSocketsAcceptOnlyTheirPart(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipRecord hulk = detached(ShipState.HULK, 0, 0);
		helper.assertTrue(ShipRules.partsTaken(hulk, ShipRules.SOCKET_HULL, 20, b) == b.hullPlates, msg("the plating takes " + b.hullPlates + " plates"));
		helper.assertTrue(ShipRules.partsTaken(hulk, ShipRules.SOCKET_CORE_INDEX, 3, b) == 1, msg("a core socket takes one"));
		helper.assertTrue(ShipRules.partsTaken(hulk, ShipRules.SOCKET_CORE_INDEX, 0, b) == 0, msg("nothing offered, nothing taken"));
		helper.assertTrue(ShipRules.partsTaken(hulk.withState(ShipState.READY), ShipRules.SOCKET_CORE_INDEX, 1, b) == 0, msg("a repaired ship has no sockets"));

		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerPlayer owner = scene.survivor(new BlockPos(12, 1, 6));
			UUID id = owner.getUUID();
			scene.owns(id);
			ShipRegistry.put(scene.server, ShipRecord.hulk(id, UUID.randomUUID(), scene.at(new BlockPos(12, 1, 14)), 0f, "TS-101"));
			Inventory inventory = owner.getInventory();

			inventory.add(new ItemStack(M4Items.POWER_CORE));
			helper.assertTrue(!ShipActions.fitSocket(owner, ShipRules.SOCKET_MOTIVATOR_INDEX) && !ShipActions.fitSocket(owner, ShipRules.SOCKET_NAV_INDEX)
					&& !ShipActions.fitSocket(owner, ShipRules.SOCKET_HULL), msg("a power core fits no other socket"));
			helper.assertTrue(count(owner, M4Items.POWER_CORE) == 1, msg("and nothing was taken"));
			helper.assertTrue(ShipActions.fitSocket(owner, ShipRules.SOCKET_CORE_INDEX), msg("the core socket takes it"));
			helper.assertTrue(count(owner, M4Items.POWER_CORE) == 0 && ShipRules.socketDone(scene.record(id), ShipRules.SOCKET_CORE_INDEX, b),
					msg("the core is used and its socket done"));
			helper.assertTrue(!ShipActions.fitSocket(owner, ShipRules.SOCKET_CORE_INDEX), msg("a done socket takes no more"));

			inventory.add(new ItemStack(M4Items.NAV_COMPUTER));
			helper.assertTrue(!ShipActions.fitSocket(owner, ShipRules.SOCKET_NAV_INDEX) && count(owner, M4Items.NAV_COMPUTER) == 1,
					msg("a blank nav computer is refused and kept"));
			ItemStack nav = findStack(owner, M4Items.NAV_COMPUTER);
			nav.set(NavData.NAV_DATA, List.of(Planets.Planet.SELVAREN.id()));
			helper.assertTrue(NavData.isLoaded(nav) && ShipActions.fitSocket(owner, ShipRules.SOCKET_NAV_INDEX), msg("a loaded nav computer fits"));
			helper.assertTrue(count(owner, M4Items.NAV_COMPUTER) == 0, msg("and is used"));

			inventory.add(new ItemStack(M3Items.DURASTEEL_PLATE, 5));
			helper.assertTrue(ShipActions.fitSocket(owner, ShipRules.SOCKET_HULL) && ShipRules.plates(scene.record(id)) == 5, msg("five plates go on"));
			inventory.add(new ItemStack(M3Items.DURASTEEL_PLATE, 20));
			ShipBalance live = ShipBalance.get();
			helper.assertTrue(ShipActions.fitSocket(owner, ShipRules.SOCKET_HULL) && count(owner, M3Items.DURASTEEL_PLATE) == 25 - ShipRules.platesNeeded(live),
					msg("the plating takes only what it still needs"));
			helper.assertTrue(scene.record(id).state() == ShipState.HULK && scene.record(id).hull() == 0, msg("three sockets: still the hulk"));

			inventory.add(new ItemStack(M4Items.HYPERDRIVE_MOTIVATOR));
			helper.assertTrue(ShipActions.fitSocket(owner, ShipRules.SOCKET_MOTIVATOR_INDEX), msg("the motivator fits its socket"));
			ShipRecord repaired = scene.record(id);
			helper.assertTrue(repaired.state() == ShipState.GROUNDED && repaired.hull() == 100, msg("the fourth part stands the ship up"));
			inventory.add(new ItemStack(M4Items.POWER_CORE));
			helper.assertTrue(!ShipActions.fitSocket(owner, ShipRules.SOCKET_CORE_INDEX) && count(owner, M4Items.POWER_CORE) == 1,
					msg("a repaired ship takes no parts"));
		});
	}

	/** The four sockets in any order stand the hulk up at hull 100: GROUNDED with an empty tank, READY with fuel. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void allFourPartsMakesGrounded(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipRecord hulk = ShipRules.withPaint(detached(ShipState.HULK, 0, 0), CourierShuttle.PAINT_VASCH);
		int[] order = {ShipRules.SOCKET_NAV_INDEX, ShipRules.SOCKET_CORE_INDEX, ShipRules.SOCKET_HULL, ShipRules.SOCKET_MOTIVATOR_INDEX};
		ShipRecord r = ShipRules.fitSocket(hulk, ShipRules.SOCKET_HULL, b.hullPlates - 1, b);
		helper.assertTrue(!ShipRules.socketDone(r, ShipRules.SOCKET_HULL, b) && r.state() == ShipState.HULK, msg("one plate short: the plating is not done"));

		for (int i = 0; i < order.length; i++) {
			int socket = order[i];
			r = ShipRules.fitSocket(r, socket, ShipRules.partsTaken(r, socket, 99, b), b);
			boolean last = i == order.length - 1;
			helper.assertTrue(ShipRules.socketDone(r, socket, b), msg("socket " + socket + " done"));
			helper.assertTrue(last || r.state() == ShipState.HULK && r.hull() == 0, msg("after " + (i + 1) + " sockets it is still the hulk"));
		}

		helper.assertTrue(r.state() == ShipState.GROUNDED && r.hull() == 100, msg("all four: GROUNDED at hull 100, got " + r.state() + " " + r.hull()));
		helper.assertTrue(ShipRules.paint(r.upgrades()) == CourierShuttle.PAINT_VASCH && ShipRules.upgradesOnly(r.upgrades()) == 0,
				msg("the paint stays and sockets are not upgrades"));

		ShipRecord fuelled = ShipRules.fuel(hulk, 2, b);
		helper.assertTrue(fuelled.state() == ShipState.HULK && fuelled.fuel() == 2, msg("cells in the hulk's tank do not stand it up"));
		ShipRecord three = fuelled;

		for (int socket : new int[] {ShipRules.SOCKET_HULL, ShipRules.SOCKET_CORE_INDEX, ShipRules.SOCKET_MOTIVATOR_INDEX}) {
			three = ShipRules.fitSocket(three, socket, ShipRules.partsTaken(three, socket, 99, b), b);
		}

		helper.assertTrue(ShipRules.restingState(three, b) == ShipState.HULK, msg("three sockets and fuel: still the hulk"));
		ShipRecord standing = ShipRules.fitSocket(three, ShipRules.SOCKET_NAV_INDEX, 1, b);
		helper.assertTrue(standing.state() == ShipState.READY && standing.hull() == 100 && standing.fuel() == 2, msg("all four with fuel in the tank: READY"));
		helper.assertTrue(ShipRules.allSockets(ShipRules.withAllSockets(hulk, b), b), msg("the command path does all four"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- bodies and the registry (4.9)

	/** A body whose UUID is not its record's current body discards itself on its next check; the current one stays and shows the record. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void bodyWithStaleUuidDiscardsItself(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		UUID owner = UUID.randomUUID();
		scene.owns(owner);
		CourierShuttle[] bodies = new CourierShuttle[3];
		scene.then(() -> {
			bodies[0] = scene.track(helper.spawn(ShipEntities.COURIER_SHUTTLE, new BlockPos(6, 1, 6)));
			bodies[1] = scene.track(helper.spawn(ShipEntities.COURIER_SHUTTLE, new BlockPos(17, 1, 17)));
			ShipRegistry.put(scene.server, detached(ShipState.READY, 90, 3, owner, scene.at(new BlockPos(6, 1, 6))).withBody(bodies[0].getUUID()));
		}).idle(4).then(() -> {
			helper.assertTrue(!bodies[0].isRemoved(), msg("the current body stays"));
			helper.assertTrue(bodies[1].isRemoved(), msg("a body no record names discards itself"));
			helper.assertTrue(bodies[0].state() == ShipState.READY && bodies[0].hull() == 90 && owner.equals(bodies[0].owner())
					&& "TS-100".equals(bodies[0].hullCode()), msg("the body shows its record (state, hull, owner, hull code)"));
			// A new body replaces it (a restore, a jump): the older copy is now stale.
			bodies[2] = scene.track(helper.spawn(ShipEntities.COURIER_SHUTTLE, new BlockPos(17, 1, 6)));
			ShipRegistry.put(scene.server, scene.record(owner).withBody(bodies[2].getUUID()).withPos(scene.at(new BlockPos(17, 1, 6)), 0f));
		}).idle(12).then(() -> {
			helper.assertTrue(bodies[0].isRemoved(), msg("the superseded body discards itself"));
			helper.assertTrue(!bodies[2].isRemoved() && Ships.shuttle(scene.server, owner) == bodies[2], msg("the new body is the ship"));
		}).run();
	}

	/** A body put back from the registry has a new UUID and the hold as it was, slots and all; a present body is left alone. */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void restoreFromRegistryKeepsHold(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		UUID owner = UUID.randomUUID();
		UUID firstBody = UUID.randomUUID();
		List<ItemStack> hold = List.of(new ItemStack(Items.IRON_INGOT, 5), ItemStack.EMPTY, new ItemStack(M4Items.HYPERFUEL_CELL, 2));
		UUID[] bodies = new UUID[2];
		scene.owns(owner);
		scene.then(() -> {
			helper.assertTrue(!Ships.restore(scene.server, UUID.randomUUID(), false), msg("no ship, nothing to restore"));
			ShipRegistry.put(scene.server, detached(ShipState.READY, 80, 2, owner, scene.at(new BlockPos(12, 1, 12))).withBody(firstBody).withHold(hold));
			helper.assertTrue(Ships.restore(scene.server, owner, false), msg("the restore is queued"));
		}).await("the body is placed", 40, () -> Ships.shuttle(scene.server, owner) != null).then(() -> {
			CourierShuttle ship = Ships.shuttle(scene.server, owner);
			ShipRecord record = scene.record(owner);
			bodies[0] = ship.getUUID();
			helper.assertTrue(!bodies[0].equals(firstBody) && bodies[0].equals(record.body()), msg("a new body UUID, written to the record"));
			helper.assertTrue(ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 12)))) < 0.5, msg("on the record's pad"));
			assertHold(helper, record, hold);
			// As after /kill: the body is gone, the record is not.
			ship.discard();
			helper.assertTrue(Ships.restore(scene.server, owner, true), msg("a missing body is restored"));
		}).await("the body is placed again", 40, () -> Ships.shuttle(scene.server, owner) != null).then(() -> {
			bodies[1] = Ships.shuttle(scene.server, owner).getUUID();
			helper.assertTrue(!bodies[1].equals(bodies[0]), msg("again a new body"));
			assertHold(helper, scene.record(owner), hold);
			helper.assertTrue(Ships.restore(scene.server, owner, true), msg("a restore with the body present succeeds"));
		}).idle(3).then(() -> {
			helper.assertTrue(Ships.shuttle(scene.server, owner).getUUID().equals(bodies[1]), msg("and leaves the present body alone"));
		}).run();
	}

	// ---------------------------------------------------------------- pads (4.5, 4.8)

	/** The first free bay in order; a ship within 5 blocks holds one; every bay held sends the ship to the rings; claims lapse after a day. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void bayAssignmentThenRing(GameTestHelper helper) {
		ShipBalance.Travel t = new ShipBalance.Travel();
		List<BlockPos> bays = List.of(new BlockPos(0, 64, 0), new BlockPos(12, 64, 0), new BlockPos(0, 64, 12), new BlockPos(12, 64, 12));
		double radius = 5;
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(), radius) == 0, msg("nothing held: the first bay"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(new BlockPos(2, 64, 3)), radius) == 1, msg("a ship 3.6 blocks off holds bay 0"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(new BlockPos(6, 64, 0)), radius) == 0, msg("6 blocks off holds nothing"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, List.of(new BlockPos(0, 64, 0), new BlockPos(12, 64, 0), new BlockPos(12, 64, 12)), radius) == 2,
				msg("the free bay among held ones"));
		helper.assertTrue(ShipRules.firstFreeBay(bays, bays, radius) == -1, msg("every bay held: the overflow rings"));
		helper.assertTrue(ShipRules.occupied(bays.get(0), List.of(new BlockPos(3, 90, 4)), radius), msg("height does not matter, 5 blocks across does"));
		helper.assertTrue(t.padBays == 4 && t.overflowRings.length > 0 && t.overflowRings[0] >= 40, msg("four bays, rings from 40 blocks out"));
		helper.assertTrue(!ShipRules.bayLapsed(1000, 1000 + t.bayLapseTicks - 1, t) && ShipRules.bayLapsed(1000, 1000 + t.bayLapseTicks, t),
				msg("an unattended claim lapses after " + t.bayLapseTicks + " ticks"));
		helper.succeed();
	}

	/**
	 * The beacon's area rules (natural ground, slope, nothing built, open sky) each with its own
	 * reason; a set beacon marks the pad one reach away and lays the tamped pad; one beacon per player
	 * (the old one comes back as an item); others' beacons are theirs, and only the owner can break one.
	 */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void beaconOnePerPlayerAndAreaCheck(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerLevel level = scene.level;
			ShipBalance.Travel t = ShipBalance.travel();
			helper.assertTrue(t.beaconArea == 11, msg("precondition: the default 11-block area (the fixture's layout depends on it)"));
			ServerPlayer a = scene.player(new BlockPos(20, 1, 21));
			ServerPlayer b = scene.player(new BlockPos(21, 1, 21));
			BlockPos pad = helper.absolutePos(PAD);
			int beaconY = helper.absolutePos(BEACON).getY();

			// The fixture's polished floor and props are not natural ground: refused, nothing set.
			helper.assertTrue("ground".equals(LandingBeaconBlock.areaRefusal(level, pad.getX(), pad.getZ(), beaconY, t)), msg("the polished floor is not ground"));
			setBeacon(helper, a, BEACON);
			helper.assertTrue(level.getBlockState(helper.absolutePos(BEACON)).isAir() && TravelAttachments.get(a).homePad().isEmpty(),
					msg("a refused beacon comes off and sets nothing"));

			openArena(helper);
			helper.assertTrue(LandingBeaconBlock.areaRefusal(level, pad.getX(), pad.getZ(), beaconY, t) == null, msg("open grass passes"));
			expectArea(helper, t, List.of(new BlockPos(8, 1, 8), new BlockPos(8, 2, 8), new BlockPos(8, 3, 8)), Blocks.GRASS_BLOCK.defaultBlockState(), "slope");
			helper.setBlock(new BlockPos(0, 4, 6), Blocks.STONE);
			expectArea(helper, t, List.of(new BlockPos(1, 4, 6)), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST), "built");
			helper.setBlock(new BlockPos(0, 4, 6), Blocks.AIR);
			List<BlockPos> canopy = new ArrayList<>();

			for (int x = 2; x <= 5; x++) {
				for (int z = 2; z <= 5; z++) {
					canopy.add(new BlockPos(x, 5, z));
				}
			}

			expectArea(helper, t, canopy, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), "sky");

			// A sets a beacon: the pad is one reach north, and the tamped pad is laid.
			setBeacon(helper, a, BEACON);
			BlockState placed = level.getBlockState(helper.absolutePos(BEACON));
			helper.assertTrue(placed.is(TravelBlocks.LANDING_BEACON) && placed.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH,
					msg("the beacon stays, facing its pad"));
			helper.assertTrue(TravelAttachments.get(a).homePad().equals(Optional.of(scene.at(PAD))), msg("A's home pad is the area's centre"));
			BlockState ground = level.getBlockState(pad.below());
			helper.assertTrue(ground.is(Blocks.STONE) || ground.is(Blocks.ANDESITE) || ground.is(Blocks.GRAVEL), msg("the tamped pad is laid, got " + ground));

			// A second beacon moves the home pad; the first comes back as an item.
			BlockPos second = new BlockPos(17, 1, 12);
			setBeacon(helper, a, second);
			helper.assertTrue(TravelAttachments.get(a).homePad().equals(Optional.of(scene.at(new BlockPos(17, 1, 6)))), msg("the home pad moved"));
			helper.assertTrue(level.getBlockState(helper.absolutePos(BEACON)).isAir(), msg("the old beacon is gone"));
			helper.assertItemEntityPresent(TravelBlocks.LANDING_BEACON.asItem(), BEACON, 2);

			// B's beacon is B's: A's home and beacon are untouched.
			setBeacon(helper, b, BEACON);
			helper.assertTrue(TravelAttachments.get(b).homePad().equals(Optional.of(scene.at(PAD))), msg("B has a home pad of their own"));
			helper.assertTrue(TravelAttachments.get(a).homePad().equals(Optional.of(scene.at(new BlockPos(17, 1, 6))))
					&& level.getBlockState(helper.absolutePos(second)).is(TravelBlocks.LANDING_BEACON), msg("A's home and beacon are untouched"));
			BlockPos aBeacon = helper.absolutePos(second);
			helper.assertTrue(level.getBlockState(aBeacon).getDestroyProgress(b, level, aBeacon) == 0f
					&& level.getBlockState(aBeacon).getDestroyProgress(a, level, aBeacon) > 0f, msg("only the owner makes progress breaking it"));
		});
	}

	/** A recall costs a hyperfuel cell from the inventory (none from the tank) and the normal wear; the beacon used with a cell flies the ship home. */
	@GameTest(structure = ARENA, maxTicks = RECALL_TICKS + 20, skyAccess = true)
	public void recallCostsCell(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipBalance.Travel t = new ShipBalance.Travel();
		ShipRecord ready = detached(ShipState.READY, 100, 2);
		helper.assertTrue(ShipRules.recall(ready, true, false, false, false, false, 3, -1, -1, t) == Recall.NO_CELL, msg("no cell, no recall"));
		helper.assertTrue(ShipRules.recall(ready, true, false, false, true, false, 3, -1, -1, t) == Recall.RECALL, msg("with a cell it flies"));
		helper.assertTrue(ShipRules.recall(null, true, false, false, true, false, 3, -1, -1, t) == Recall.NO_SHIP
				&& ShipRules.recall(ready.withState(ShipState.HULK), true, false, false, true, false, 3, -1, -1, t) == Recall.HULK
				&& ShipRules.recall(ready.withState(ShipState.LANDING), true, false, false, true, false, 3, -1, -1, t) == Recall.FLYING
				&& ShipRules.recall(ready, false, false, false, true, false, 3, -1, -1, t) == Recall.DISABLED_BY_CONFIG
				&& ShipRules.recall(ready, true, true, false, true, false, 3, -1, -1, t) == Recall.ABOARD
				&& ShipRules.recall(ready, true, false, true, true, false, 3, -1, -1, t) == Recall.ALREADY_HERE, msg("the recall's refusals"));
		ShipRecord delivered = ShipRules.afterRecall(ready, false, b);
		helper.assertTrue(delivered.hull() == 100 - b.wearPerJump && delivered.fuel() == 2 && delivered.state() == ShipState.READY,
				msg("a recall wears the hull like a jump and burns nothing from the tank"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(new BlockPos(12, 1, 21));
		ShipBalance live = ShipBalance.get();
		scene.then(() -> {
			openArena(helper);
			homeBeacon(scene, owner);
			scene.owns(owner.getUUID());
			// The body is not loaded: it vanishes where it is and appears over the pad.
			ShipRegistry.put(scene.server, detached(ShipState.READY, 100, 2, owner.getUUID(), scene.at(START)));
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(M4Items.HYPERFUEL_CELL, 3));
			useBeacon(helper, owner, BEACON);
			helper.assertTrue(count(owner, M4Items.HYPERFUEL_CELL) == 2, msg("live: one cell paid"));
			helper.assertTrue(scene.record(owner.getUUID()).state() == ShipState.LAUNCHING, msg("live: the recall started"));
		}).await("the recalled ship lands", RECALL_TICKS, () -> landed(scene, owner.getUUID(), List.of())).then(() -> {
			ShipRecord landed = scene.record(owner.getUUID());
			helper.assertTrue(landed.pos().equals(scene.at(PAD)) && landed.state() == ShipState.READY, msg("live: READY on the home pad, got " + landed));
			helper.assertTrue(landed.hull() == 100 - ShipRules.wear(landed, live) && landed.fuel() == 2, msg("live: the wear, and the tank untouched"));
			CourierShuttle ship = Ships.shuttle(scene.server, owner.getUUID());
			helper.assertTrue(ship != null && ship.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(PAD))) < 0.5, msg("live: the body is on the pad"));
		}).run();
	}

	/** The hub host's free recall: once a day, no cell; a DISABLED ship uses the tow allowance instead. */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void freeRecallOncePerDay(GameTestHelper helper) {
		ShipBalance.Travel t = new ShipBalance.Travel();
		ShipRecord ready = detached(ShipState.READY, 100, 2);
		helper.assertTrue(ShipRules.recall(ready, true, false, false, false, true, 7, 6, -1, t) == Recall.RECALL, msg("free, no cell needed, first today"));
		helper.assertTrue(ShipRules.recall(ready, true, false, false, true, true, 7, 7, -1, t) == Recall.FREE_USED, msg("once a day (even with a cell)"));
		helper.assertTrue(ShipRules.recall(ready, true, false, false, false, true, 8, 7, -1, t) == Recall.RECALL, msg("again the next day"));
		ShipBalance.Travel none = new ShipBalance.Travel();
		none.freeRecallPerDay = 0;
		helper.assertTrue(ShipRules.recall(ready, true, false, false, false, true, 8, 7, -1, none) == Recall.FREE_USED, msg("none when configured off"));
		helper.assertTrue(ShipRules.recall(ready.withState(ShipState.DISABLED).withHull(5), true, false, false, false, true, 7, -1, -1, t) == Recall.TOW,
				msg("a DISABLED ship is towed on the host's favour"));

		Scene scene = new Scene(helper);
		scene.now(() -> {
			DialogueActions.Action free = DialogueActions.get("ship:recall_free");
			helper.assertTrue(free != null, msg("the ship:recall_free action is registered"));
			openArena(helper);
			ServerPlayer owner = scene.player(new BlockPos(12, 1, 21));
			homeBeacon(scene, owner);
			scene.give(owner, START, 0f, 100, 2);
			long today = Clocks.day(scene.server);

			free.run(owner, null);
			helper.assertTrue(scene.record(owner.getUUID()).state() == ShipState.LAUNCHING, msg("live: the free recall flies"));
			helper.assertTrue(TravelAttachments.get(owner).recallDay() == today, msg("live: today's favour is used"));

			// Put the ship back on the ground at the start (the command path ends the flight), and ask again.
			scene.give(owner, START, 0f, 100, 2);
			free.run(owner, null);
			ShipRecord again = scene.record(owner.getUUID());
			helper.assertTrue(again.state() == ShipState.READY && again.flight().isEmpty(), msg("live: a second free recall the same day is refused"));

			TravelAttachments.set(owner, TravelAttachments.get(owner).withRecallDay(today - 1));
			free.run(owner, null);
			helper.assertTrue(scene.record(owner.getUUID()).state() == ShipState.LAUNCHING, msg("live: the next day it flies again"));
		});
	}

	/** A DISABLED ship is towed free, once a day, and arrives with the jump minimum hull; a cell in hand is not taken. */
	@GameTest(structure = ARENA, maxTicks = RECALL_TICKS + 20, skyAccess = true)
	public void towBringsDisabledShipAt20OncePerDay(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		ShipBalance.Travel t = new ShipBalance.Travel();
		ShipRecord disabled = detached(ShipState.DISABLED, 8, 1);
		helper.assertTrue(ShipRules.recall(disabled, true, false, false, false, false, 4, -1, 3, t) == Recall.TOW, msg("towed, no cell needed"));
		helper.assertTrue(ShipRules.recall(disabled, true, false, false, true, false, 4, -1, 4, t) == Recall.TOW_USED, msg("once a day"));
		ShipBalance.Travel none = new ShipBalance.Travel();
		none.freeTowPerDay = 0;
		helper.assertTrue(ShipRules.recall(disabled, true, false, false, true, false, 4, -1, 3, none) == Recall.TOW_USED, msg("none when configured off"));
		ShipRecord towed = ShipRules.afterRecall(disabled, true, b);
		helper.assertTrue(towed.hull() == b.jumpMinHull && b.jumpMinHull == 20 && towed.state() == ShipState.READY && towed.fuel() == 1,
				msg("towed in at 20 hull, no wear, READY with its fuel"));
		helper.assertTrue(ShipRules.afterRecall(disabled.withFuel(0), true, b).state() == ShipState.GROUNDED, msg("GROUNDED with an empty tank"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(new BlockPos(12, 1, 21));
		ShipBalance live = ShipBalance.get();
		DialogueActions.Action tow = DialogueActions.get("ship:tow");
		long[] today = new long[1];
		scene.then(() -> {
			helper.assertTrue(tow != null, msg("the ship:tow action is registered"));
			openArena(helper);
			homeBeacon(scene, owner);
			scene.owns(owner.getUUID());
			ShipRegistry.put(scene.server, detached(ShipState.DISABLED, 8, 1, owner.getUUID(), scene.at(START)));
			owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(M4Items.HYPERFUEL_CELL, 1));
			today[0] = Clocks.day(scene.server);
			tow.run(owner, null);
			helper.assertTrue(scene.record(owner.getUUID()).state() == ShipState.LAUNCHING, msg("live: the tow started"));
			helper.assertTrue(TravelAttachments.get(owner).towDay() == today[0] && count(owner, M4Items.HYPERFUEL_CELL) == 1,
					msg("live: today's tow is used, the cell kept"));
		}).await("the towed ship lands", RECALL_TICKS, () -> landed(scene, owner.getUUID(), List.of())).then(() -> {
			ShipRecord landed = scene.record(owner.getUUID());
			helper.assertTrue(landed.hull() == Math.max(8, live.jumpMinHull) && landed.state() == ShipState.READY && landed.fuel() == 1,
					msg("live: towed in at the jump minimum, READY, its fuel kept: " + landed));
			helper.assertTrue(landed.pos().equals(scene.at(PAD)), msg("live: on the home pad"));
			// Disabled again the same day: no second tow.
			ShipRegistry.put(scene.server, landed.withHull(8).withState(ShipState.DISABLED).withPos(scene.at(START), 0f));
			tow.run(owner, null);
			ShipRecord refused = scene.record(owner.getUUID());
			helper.assertTrue(refused.state() == ShipState.DISABLED && refused.flight().isEmpty(), msg("live: one tow a day"));
		}).run();
	}

	// ---------------------------------------------------------------- the sequence (4.6)

	/** At Settle: DISABLED below the jump minimum (it wins), GROUNDED with too little fuel, else READY; a real flight lands DISABLED. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true)
	public void settleSetsDisabledBelow20AndGroundedWhenEmpty(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		helper.assertTrue(ShipRules.settleState(detached(ShipState.IN_TRANSIT, 19, 3), b) == ShipState.DISABLED, msg("hull 19: DISABLED"));
		helper.assertTrue(ShipRules.settleState(detached(ShipState.IN_TRANSIT, 20, 0), b) == ShipState.GROUNDED, msg("empty tank: GROUNDED"));
		helper.assertTrue(ShipRules.settleState(detached(ShipState.IN_TRANSIT, 19, 0), b) == ShipState.DISABLED, msg("both: DISABLED wins"));
		helper.assertTrue(ShipRules.settleState(detached(ShipState.IN_TRANSIT, 20, 1), b) == ShipState.READY, msg("hull 20 and a cell: READY"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(new BlockPos(12, 1, 20));
		ShipBalance live = ShipBalance.get();
		int hull = live.jumpMinHull + live.wearPerJump - 1;
		scene.then(() -> launchHome(scene, owner, hull, live.cellsPerJump))
				.await("the flight is over", 80, () -> landed(scene, owner.getUUID(), List.of(owner)))
				.then(() -> {
					ShipRecord landed = scene.record(owner.getUUID());
					helper.assertTrue(landed.hull() == live.jumpMinHull - 1 && landed.fuel() == 0, msg("live: hull and tank after the jump: " + landed));
					helper.assertTrue(landed.state() == ShipState.DISABLED, msg("live: the ship settles DISABLED"));
					CourierShuttle ship = Ships.shuttle(scene.server, owner.getUUID());
					helper.assertTrue(ship != null && ship.state() == ShipState.DISABLED, msg("live: the body shows it"));
				})
				.run();
	}

	/** Riders stay aboard through the sequence and are put down on the ground beside the ship when the Settle ends. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true)
	public void ridersDismountAtSettle(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(new BlockPos(12, 1, 20));
		ServerPlayer crew = scene.player(new BlockPos(13, 1, 20));
		CourierShuttle[] ship = new CourierShuttle[1];
		scene.then(() -> {
			ship[0] = launchHome(scene, owner, 100, 4, crew);
			helper.assertTrue(ship[0].locksRiders() && owner.getVehicle() == ship[0] && crew.getVehicle() == ship[0], msg("both aboard and locked in"));
		}).await("the flight is over", 80, () -> landed(scene, owner.getUUID(), List.of(owner, crew))).then(() -> {
			CourierShuttle landed = Ships.shuttle(scene.server, owner.getUUID());
			helper.assertTrue(landed != null && !landed.locksRiders() && landed.getPassengers().isEmpty(), msg("the ship is empty and unlocked"));
			double padY = helper.absolutePos(PAD).getY();

			for (ServerPlayer rider : List.of(owner, crew)) {
				Vec3 at = rider.position();
				helper.assertTrue(!rider.isPassenger(), msg("the rider is off"));
				helper.assertTrue(at.subtract(landed.position()).horizontalDistance() < 10, msg("beside the ship, got " + at));
				helper.assertTrue(at.y >= padY - 0.01 && at.y <= padY + 1.5, msg("on the ground, not in the air: y " + at.y));
				helper.assertTrue(!landed.getBoundingBox().contains(at), msg("outside the hull"));
				helper.assertTrue(!level(scene).getBlockState(BlockPos.containing(at).below()).is(TravelBlocks.LANDING_BEACON),
						msg("not on top of the beacon behind the ramp"));
			}
		}).run();
	}

	/**
	 * A restart (4.9) settles a flight that is not running: before the jump back at its origin with
	 * nothing taken, after it at the destination with nothing taken twice. Checked on detached data
	 * that went through the codec, then on the live registry, where the sweep places a body.
	 */
	@GameTest(structure = ARENA, maxTicks = 260)
	public void restartInTransitLandsAtDestinationLaunchingReturnsToOrigin(GameTestHelper helper) {
		ShipBalance b = new ShipBalance();
		GlobalPos origin = GlobalPos.of(Level.OVERWORLD, new BlockPos(100, 64, 100));
		GlobalPos destination = GlobalPos.of(Planets.SELVAREN, new BlockPos(0, 80, 0));
		ShipRecord launching = detached(ShipState.LAUNCHING, 90, 3, UUID.randomUUID(), origin)
				.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, ShipRules.PHASE_CLIMB)));
		ShipRecord inTransit = detached(ShipState.IN_TRANSIT, 85, 2, UUID.randomUUID(), destination)
				.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, ShipRules.PHASE_HYPERSPACE)));
		ShipRecord landing = detached(ShipState.LANDING, 85, 0, UUID.randomUUID(), destination)
				.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, ShipRules.PHASE_DESCENT)));
		ShipRegistry.Data data = new ShipRegistry.Data();
		List.of(launching, inTransit, landing).forEach(data::put);
		var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonElement saved = ShipRegistry.Data.CODEC.encodeStart(ops, data).getOrThrow();
		ShipRegistry.Data loaded = ShipRegistry.Data.CODEC.parse(ops, saved).getOrThrow();

		ShipRecord a = loaded.get(launching.owner()).orElseThrow();
		ShipRecord aSettled = ShipRules.recovered(a, b);
		helper.assertTrue(ShipRules.recovery(a) == Recovery.ORIGIN, msg("caught before the jump: back to the origin"));
		helper.assertTrue(aSettled.pos().equals(origin) && aSettled.fuel() == 3 && aSettled.hull() == 90 && aSettled.state() == ShipState.READY
				&& aSettled.flight().isEmpty(), msg("at the origin, nothing taken, READY: " + aSettled));
		ShipRecord c = loaded.get(inTransit.owner()).orElseThrow();
		ShipRecord cSettled = ShipRules.recovered(c, b);
		helper.assertTrue(ShipRules.recovery(c) == Recovery.DESTINATION, msg("caught after the jump: at the destination"));
		helper.assertTrue(cSettled.pos().equals(destination) && cSettled.fuel() == 2 && cSettled.hull() == 85 && cSettled.state() == ShipState.READY,
				msg("at the destination pad, the jump paid once: " + cSettled));
		ShipRecord d = loaded.get(landing.owner()).orElseThrow();
		helper.assertTrue(ShipRules.recovery(d) == Recovery.DESTINATION && ShipRules.recovered(d, b).state() == ShipState.GROUNDED,
				msg("caught descending: at the destination, GROUNDED with an empty tank"));
		helper.assertTrue(ShipRules.recovery(detached(ShipState.READY, 100, 4)) == Recovery.NONE, msg("a ship on the ground needs nothing"));

		// Live: a record left mid-flight with no flight running, as after a restart; the sweep settles it.
		Scene scene = new Scene(helper);
		UUID owner = UUID.randomUUID();
		scene.owns(owner);
		GlobalPos pad = scene.at(new BlockPos(12, 1, 12));
		scene.then(() -> ShipRegistry.put(scene.server, detached(ShipState.IN_TRANSIT, 85, 2, owner, pad)
				.withFlight(Optional.of(new ShipRecord.Flight(Destination.GREENWATCH, Destination.HOME, ShipRules.PHASE_HYPERSPACE)))))
				.await("the sweep settles the record and places a body", 230, () -> scene.record(owner).flight().isEmpty() && Ships.shuttle(scene.server, owner) != null)
				.then(() -> {
					ShipRecord settled = scene.record(owner);
					helper.assertTrue(settled.state() == ShipState.READY && settled.pos().equals(pad) && settled.fuel() == 2 && settled.hull() == 85,
							msg("live: READY at the destination pad, nothing taken again: " + settled));
					CourierShuttle ship = Ships.shuttle(scene.server, owner);
					helper.assertTrue(ship.getUUID().equals(settled.body()) && ship.position().distanceTo(Vec3.atBottomCenterOf(pad.pos())) < 0.5,
							msg("live: a body stands on the pad"));
				})
				.run();
	}

	/** A player who logs out aboard is put off first, so the ship is not saved with them; mid-flight they are set down at the origin's pad. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true)
	public void disconnectAboardDismountsBeforeSave(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer parked = scene.player(new BlockPos(20, 1, 6));
		ServerPlayer owner = scene.player(new BlockPos(12, 1, 20));
		ServerPlayer crew = scene.player(new BlockPos(13, 1, 20));
		CourierShuttle[] ships = new CourierShuttle[2];
		scene.then(() -> {
			// On the ground: vanilla would remove a vehicle whose only player leaves (and save it with them).
			ships[0] = scene.give(parked, new BlockPos(17, 1, 6), 0f, 100, 4);
			helper.assertTrue(parked.startRiding(ships[0], true, false), msg("the player boards the parked ship"));
			M3TestPlayers.remove(parked);
			helper.assertTrue(!parked.isPassenger() && !ships[0].isRemoved() && ships[0].getPassengers().isEmpty(),
					msg("logged out off the ship; the ship stays in the world"));
			helper.assertTrue(Ships.shuttle(scene.server, parked.getUUID()) == ships[0], msg("and is still the registry's body"));

			// In flight, before the jump: the leaving rider is set down at the origin.
			ships[1] = launchHome(scene, owner, 100, 4, crew);
			M3TestPlayers.remove(crew);
			helper.assertTrue(!crew.isPassenger() && !ships[1].getPassengers().contains(crew), msg("the leaving rider is off the moving ship"));
			Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(START));
			helper.assertTrue(crew.position().subtract(start).horizontalDistance() < 10, msg("set down beside the origin's pad, got " + crew.position()));
		}).await("the flight is over", 80, () -> landed(scene, owner.getUUID(), List.of(owner))).then(() -> {
			helper.assertTrue(scene.record(owner.getUUID()).pos().equals(scene.at(PAD)), msg("the flight went on without them"));
		}).run();
	}

	/** Riders take no damage from the Spool to the end of the Settle (except /kill); afterwards they do again. */
	@GameTest(structure = ARENA, maxTicks = 100, skyAccess = true)
	public void ridersInvulnerableDuringSequence(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.survivor(new BlockPos(12, 1, 20));
		ServerPlayer crew = scene.survivor(new BlockPos(13, 1, 20));
		scene.then(() -> {
			CourierShuttle ship = launchHome(scene, owner, 100, 4, crew);
			DamageSource generic = scene.level.damageSources().generic();
			DamageSource kill = scene.level.damageSources().genericKill();
			helper.assertTrue(ship.locksRiders(), msg("the sequence has started"));

			for (ServerPlayer rider : List.of(owner, crew)) {
				float health = rider.getHealth();
				helper.assertTrue(!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(rider, generic, 4f), msg("no damage while locked in"));
				rider.hurtServer(scene.level, generic, 4f);
				helper.assertTrue(rider.getHealth() == health, msg("a hit lands nothing"));
				helper.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(rider, kill, 4f), msg("/kill still works"));
			}
		}).await("the flight is over", 80, () -> landed(scene, owner.getUUID(), List.of(owner, crew))).then(() -> {
			DamageSource generic = scene.level.damageSources().generic();

			for (ServerPlayer rider : List.of(owner, crew)) {
				helper.assertTrue(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(rider, generic, 1f), msg("off the ship, damage applies again"));
			}
		}).run();
	}

	// ---------------------------------------------------------------- crew (4.9)

	/** Owner and crew may board, a stranger may not (and is put off if forced aboard); only the owner may launch. */
	@GameTest(structure = ARENA, maxTicks = 60, skyAccess = true)
	public void crewOnlyMayBoardOwnerOnlyLaunches(GameTestHelper helper) {
		ShipRecord r = detached(ShipState.READY, 100, 4);
		UUID crewId = UUID.randomUUID();
		ShipRecord crewed = r.withCrew(ShipRules.addCrew(r, crewId, new ShipBalance()));
		helper.assertTrue(ShipRules.mayBoard(crewed, r.owner()) && ShipRules.mayBoard(crewed, crewId) && !ShipRules.mayBoard(crewed, UUID.randomUUID()),
				msg("owner and crew, nobody else"));
		helper.assertTrue(ShipRules.addCrew(crewed, r.owner(), new ShipBalance()) == null && ShipRules.addCrew(crewed, crewId, new ShipBalance()) == null,
				msg("the owner and a member cannot be added again"));

		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(new BlockPos(12, 1, 21));
		ServerPlayer crew = scene.player(new BlockPos(13, 1, 21));
		ServerPlayer stranger = scene.player(new BlockPos(14, 1, 21));
		CourierShuttle[] ship = new CourierShuttle[1];
		scene.then(() -> {
			openArena(helper);
			homeBeacon(scene, owner);
			ship[0] = scene.give(owner, START, 0f, 100, 4);
			helper.assertTrue(ShipActions.addCrew(owner, crew.getUUID()), msg("the owner names a crew member"));
			helper.assertTrue(TravelAttachments.get(crew).crewOf().equals(Optional.of(owner.getUUID())), msg("who knows whose crew they are"));

			ship[0].interact(stranger, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(!stranger.isPassenger(), msg("a stranger cannot board"));
			ship[0].interact(crew, InteractionHand.MAIN_HAND, ship[0].position());
			ship[0].interact(owner, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(crew.getVehicle() == ship[0] && owner.getVehicle() == ship[0], msg("crew and owner board by using the ship"));

			helper.assertTrue(TravelService.canLaunch(crew, Destination.HOME) == Refusal.NOT_OWNER
					&& TravelService.launchByCommand(crew, Destination.HOME, true) == Refusal.NOT_OWNER, msg("crew cannot launch"));
			helper.assertTrue(TravelService.canLaunch(owner, Destination.HOME) == Refusal.NONE, msg("the owner can"));
			helper.assertTrue(scene.record(owner.getUUID()).flight().isEmpty(), msg("nothing was launched"));

			helper.assertTrue(stranger.startRiding(ship[0], true, false), msg("a stranger forced aboard"));
		}).idle(12).then(() -> {
			helper.assertTrue(!stranger.isPassenger(), msg("is put off by the ship's own check"));
			helper.assertTrue(crew.getVehicle() == ship[0] && owner.getVehicle() == ship[0], msg("crew and owner stay"));
		}).run();
	}

	/** A crew member taken off the list is put off at once; one dropped from the record any other way is put off by the ship's check. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void crewRemovalDismounts(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerPlayer owner = scene.player(new BlockPos(12, 1, 4));
		ServerPlayer crew = scene.player(new BlockPos(13, 1, 4));
		CourierShuttle[] ship = new CourierShuttle[1];
		scene.then(() -> {
			ship[0] = scene.give(owner, new BlockPos(12, 1, 14), 0f, 100, 4);
			helper.assertTrue(!ShipActions.removeCrew(owner, crew.getUUID()), msg("not on the crew: nothing to remove"));
			helper.assertTrue(ShipActions.addCrew(owner, crew.getUUID()), msg("added"));
			ship[0].interact(crew, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(crew.getVehicle() == ship[0], msg("the crew member boards"));
			helper.assertTrue(ShipActions.removeCrew(owner, crew.getUUID()), msg("removed"));
			helper.assertTrue(!crew.isPassenger(), msg("and put off at once"));
			helper.assertTrue(scene.record(owner.getUUID()).crew().isEmpty() && TravelAttachments.get(crew).crewOf().isEmpty(), msg("off the list both ways"));

			helper.assertTrue(ShipActions.addCrew(owner, crew.getUUID()), msg("added again"));
			ship[0].interact(crew, InteractionHand.MAIN_HAND, ship[0].position());
			helper.assertTrue(crew.getVehicle() == ship[0], msg("aboard again"));
			Ships.update(scene.server, owner.getUUID(), r -> r.withCrew(List.of()));
		}).idle(12).then(() -> {
			helper.assertTrue(!crew.isPassenger(), msg("dropped from the record: the ship's check puts them off"));
		}).run();
	}

	/** Nobody steers the ship: no controlling passenger with the owner or crew aboard; it takes no damage and cannot be pushed. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void noControllingPassenger(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.now(() -> {
			ServerPlayer owner = scene.player(new BlockPos(12, 1, 4));
			ServerPlayer crew = scene.player(new BlockPos(13, 1, 4));
			CourierShuttle ship = scene.give(owner, new BlockPos(12, 1, 14), 0f, 100, 4);
			Ships.update(scene.server, owner.getUUID(), r -> r.withCrew(List.of(crew.getUUID())));
			helper.assertTrue(owner.startRiding(ship, true, false) && crew.startRiding(ship, true, false), msg("owner and crew aboard"));
			helper.assertTrue(ship.getFirstPassenger() == owner && ship.getControllingPassenger() == null, msg("the owner sits first but does not steer"));
			helper.assertTrue(!ship.isPushable() && ship.canBeCollidedWith(null), msg("not pushable; solid on the ground"));
			helper.assertTrue(!ship.hurtServer(scene.level, scene.level.damageSources().generic(), 100f) && !ship.isRemoved(), msg("no damage at all"));
		});
	}

	// ---------------------------------------------------------------- helpers

	/** A ship record for a new owner at the world origin, hull code {@code TS-100}. */
	private static ShipRecord detached(ShipState state, int hull, int fuel) {
		return detached(state, hull, fuel, UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));
	}

	private static ShipRecord detached(ShipState state, int hull, int fuel, UUID owner, GlobalPos pos) {
		return ShipRecord.hulk(owner, UUID.randomUUID(), pos, 0f, "TS-100").withState(state).withHull(hull).withFuel(fuel);
	}

	/** The record with its state set as for a ship at rest (a hulk stays one). */
	private static ShipRecord rest(ShipRecord r, ShipBalance b) {
		return r.withState(ShipRules.restingState(r, b));
	}

	private static ServerLevel level(Scene scene) {
		return scene.level;
	}

	/** Lays grass over the whole arena floor and clears everything above it. */
	private static void openArena(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);

				for (int y = 1; y < 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/** Places a landing beacon at {@code rel} as {@code owner} placing it by hand while facing south (its pad is tried north first). */
	private static void setBeacon(GameTestHelper helper, ServerPlayer owner, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(rel);
		BlockState state = TravelBlocks.LANDING_BEACON.defaultBlockState();
		level.setBlock(pos, state, Block.UPDATE_ALL);
		owner.setYRot(0f);
		TravelBlocks.LANDING_BEACON.setPlacedBy(level, pos, state, owner, new ItemStack(TravelBlocks.LANDING_BEACON));
	}

	/** The owner's beacon at {@link #BEACON}, so home is {@link #PAD} (the arena must be open). */
	private static void homeBeacon(Scene scene, ServerPlayer owner) {
		setBeacon(scene.helper, owner, BEACON);
		scene.helper.assertTrue(TravelAttachments.get(owner).homePad().equals(Optional.of(scene.at(PAD))), msg("precondition: the beacon marks the home pad"));
	}

	/**
	 * The flight tests' start: the arena opened, the owner's beacon set, the ship at {@link #START}
	 * with {@code hull} and {@code fuel}, the owner and {@code crew} aboard, and the instant flight
	 * home started.
	 */
	private static CourierShuttle launchHome(Scene scene, ServerPlayer owner, int hull, int fuel, ServerPlayer... crew) {
		GameTestHelper helper = scene.helper;
		openArena(helper);
		homeBeacon(scene, owner);
		CourierShuttle ship = scene.give(owner, START, 0f, hull, fuel);
		List<UUID> ids = new ArrayList<>();

		for (ServerPlayer member : crew) {
			ids.add(member.getUUID());
		}

		Ships.update(scene.server, owner.getUUID(), r -> r.withCrew(ids));
		helper.assertTrue(owner.startRiding(ship, true, false), msg("the owner boards"));

		for (ServerPlayer member : crew) {
			helper.assertTrue(member.startRiding(ship, true, false), msg("a crew member boards"));
		}

		Refusal refusal = TravelService.launchByCommand(owner, Destination.HOME, true);
		helper.assertTrue(refusal == Refusal.NONE, msg("the flight home starts, got " + refusal));
		return ship;
	}

	/** Whether the owner's flight is over: the record settled on the ground and every listed rider off. */
	private static boolean landed(Scene scene, UUID owner, List<ServerPlayer> riders) {
		ShipRecord record = ShipRegistry.of(scene.server, owner).orElse(null);

		if (record == null || record.flight().isPresent() || record.state().flying()) {
			return false;
		}

		for (ServerPlayer rider : riders) {
			if (rider.isPassenger()) {
				return false;
			}
		}

		return true;
	}

	/** Uses the block at {@code rel} with the main-hand item, as a right click on its top would. */
	private static InteractionResult useBeacon(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		BlockPos pos = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return helper.getLevel().getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
	}

	/** Checks the area at {@link #PAD} with {@code block} placed at each of {@code cells}: refused for {@code reason}; the cells are cleared after. */
	private static void expectArea(GameTestHelper helper, ShipBalance.Travel t, List<BlockPos> cells, BlockState block, String reason) {
		BlockPos pad = helper.absolutePos(PAD);

		for (BlockPos cell : cells) {
			helper.setBlock(cell, block);
		}

		String got = LandingBeaconBlock.areaRefusal(helper.getLevel(), pad.getX(), pad.getZ(), helper.absolutePos(BEACON).getY(), t);

		for (BlockPos cell : cells) {
			helper.setBlock(cell, Blocks.AIR);
		}

		helper.assertTrue(reason.equals(got), msg("expected the area refused for " + reason + ", got " + got));
	}

	private static void assertHold(GameTestHelper helper, ShipRecord record, List<ItemStack> expected) {
		helper.assertTrue(record.hold().size() == expected.size(), msg("the hold keeps its " + expected.size() + " slots"));

		for (int i = 0; i < expected.size(); i++) {
			helper.assertTrue(ItemStack.matches(record.hold().get(i), expected.get(i)), msg("hold slot " + i + " is as it was"));
		}
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

	private static ItemStack findStack(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				return inventory.getItem(i);
			}
		}

		return ItemStack.EMPTY;
	}

	/**
	 * One test's world: the players, bodies and ship records it made, removed together when it ends,
	 * pass or fail (records only of its own owners, so tests running alongside keep theirs). A test
	 * with ticks to wait runs as steps, polled once per tick by a single {@link GameTestHelper#onEachTick}
	 * registration, so nothing is scheduled from inside a scheduled task.
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

		/** A listed player standing at {@code rel}. */
		ServerPlayer player(BlockPos rel) {
			ServerPlayer player = M3TestPlayers.listed(helper, rel);
			players.add(player);
			return player;
		}

		/** A listed player in survival (a creative one takes no damage and uses up no items). */
		ServerPlayer survivor(BlockPos rel) {
			ServerPlayer player = player(rel);
			player.setGameMode(GameType.SURVIVAL);
			return player;
		}

		<E extends Entity> E track(E entity) {
			entities.add(entity);
			return entity;
		}

		void owns(UUID owner) {
			if (!owners.contains(owner)) {
				owners.add(owner);
			}
		}

		GlobalPos at(BlockPos rel) {
			return GlobalPos.of(level.dimension(), helper.absolutePos(rel));
		}

		ShipRecord record(UUID owner) {
			return ShipRegistry.of(server, owner).orElseThrow(() -> helper.assertionException(msg("the ship record of " + owner + " is gone")));
		}

		/** The command path's repaired ship at {@code rel} for {@code owner}, then set to {@code hull} and {@code fuel} at rest. */
		CourierShuttle give(ServerPlayer owner, BlockPos rel, float yaw, int hull, int fuel) {
			owns(owner.getUUID());
			Ships.giveAt(owner, helper.absolutePos(rel), yaw);
			ShipBalance b = ShipBalance.get();
			Ships.update(server, owner.getUUID(), r -> rest(r.withHull(hull).withFuel(fuel), b));
			CourierShuttle ship = Ships.shuttle(server, owner.getUUID());
			helper.assertTrue(ship != null, msg("the ship body is placed"));
			return ship;
		}

		Scene then(Runnable action) {
			steps.add(t -> {
				action.run();
				return true;
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
			} catch (RuntimeException e) {
				finished = true;
				clean();
				throw e;
			}
		}

		/** Players first (they leave the ships they ride), then the bodies, other entities and this test's records. */
		private void clean() {
			players.forEach(M3TestPlayers::remove);

			for (UUID owner : owners) {
				CourierShuttle ship = Ships.shuttle(server, owner);

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

package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.network.ShipActionPayload;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What players do to a ship (spec MILESTONE_4.md 4.1–4.4, 4.7, 4.9, 4.10; MILESTONE_5.md 4.2–4.10,
 * 13.2): using the body (board, weld a plate, fuel a cell, open the screen), every button of the
 * ship screen, the Helm key and, at the helm, the inventory key. All server side; the client only
 * sends intents ({@code ship_action}), and only the owner may change anything. The socket and crew
 * buttons and {@link #handle} are public for the game tests, which press them without a screen.
 */
public final class ShipActions {
	/** Each repair socket's part. */
	private static final Item[] SOCKET_PARTS = {M3Items.DURASTEEL_PLATE, M4Items.POWER_CORE, M4Items.HYPERDRIVE_MOTIVATOR, M4Items.NAV_COMPUTER};
	/** Each upgrade bit's item ({@link ShipRules#UPGRADES} order). */
	private static final Item[] UPGRADE_ITEMS = {M4Items.PASSENGER_SEAT, M4Items.CARGO_BAY_EXTENSION, M4Items.REINFORCED_PLATING, M4Items.FUEL_TANK_EXTENSION};

	/** Game time of each player's last plate weld (the 10-tick cooldown). */
	private static final Map<UUID, Long> LAST_PLATE = new HashMap<>();
	/** Names of players seen this session, for the Crew tab's offline members. */
	private static final Map<UUID, String> NAMES = new HashMap<>();

	// ---------------------------------------------------------------- using the body

	/** A player used the ship body. */
	static InteractionResult use(ServerPlayer player, ShipBody ship, InteractionHand hand) {
		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.byBody(server, ship.getUUID()).orElse(null);

		if (record == null) {
			return InteractionResult.PASS;
		}

		remember(player);
		boolean owner = record.owner().equals(player.getUUID());

		if (!ShipRules.mayBoard(record, player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("ship.oldrepublic.not_yours", nameOf(server, record.owner())));
			return InteractionResult.SUCCESS;
		}

		if (Ships.busy(record) && player.getVehicle() != ship) {
			player.sendOverlayMessage(Component.translatable("ship.oldrepublic.flying"));
			return InteractionResult.SUCCESS;
		}

		ItemStack held = player.getItemInHand(hand);

		if (owner && held.is(M3Items.DURASTEEL_PLATE) && ShipRules.canPlate(record)) {
			weld(player, ship, held);
			return InteractionResult.SUCCESS;
		}

		if (owner && held.is(M4Items.HYPERFUEL_CELL) && record.state() != ShipState.HULK) {
			fuel(player, 1);
			return InteractionResult.SUCCESS;
		}

		if (player.getVehicle() == ship || player.isSecondaryUseActive() || !ShipRules.boardable(record) || Ships.busy(record)) {
			ShipMenu.open(player, record);
			return InteractionResult.SUCCESS;
		}

		if (!player.startRiding(ship)) {
			player.sendOverlayMessage(Component.translatable("ship.oldrepublic.no_seat"));
		}

		return InteractionResult.SUCCESS;
	}

	/** One plate on a repaired ship: +5 hull (+8 for an Armourcraft or Armstech player at level 2), every 10 ticks (4.4). */
	private static void weld(ServerPlayer player, ShipBody ship, ItemStack plate) {
		MinecraftServer server = player.level().getServer();
		ShipBalance b = ShipBalance.get();
		long now = Clocks.time(server);
		Long last = LAST_PLATE.get(player.getUUID());

		if (last != null && now - last < b.plateRepairCooldown) {
			return;
		}

		LAST_PLATE.put(player.getUUID(), now);
		boolean skilled = Crafting.level(player, Discipline.ARMOURCRAFT) >= 2 || Crafting.level(player, Discipline.ARMSTECH) >= 2;
		Optional<ShipRecord> updated = Ships.update(server, player.getUUID(), r -> ShipRules.canPlate(r) ? ShipRules.plate(r, skilled, b) : r);

		if (!player.hasInfiniteMaterials()) {
			plate.shrink(1);
		}

		TravelEffects.repair(player.level(), weldPoint(player, ship), ship, TravelEffects.REPAIR_WELD);
		updated.ifPresent(r -> player.sendOverlayMessage(Component.translatable("ship.oldrepublic.hull", r.hull())));
	}

	/** Where the sparks fly: the point on the hull the player looks at, near them. */
	private static Vec3 weldPoint(ServerPlayer player, ShipBody ship) {
		Vec3 eye = player.getEyePosition();
		Vec3 toShip = ship.getBoundingBox().getCenter().subtract(eye);
		double reach = Math.min(toShip.length(), 3.0);
		return eye.add(player.getLookAngle().scale(reach));
	}

	// ---------------------------------------------------------------- the screen's buttons

	/**
	 * A {@code ship_action} from the ship screen. The Helm key's {@code TAKE_HELM} and {@code LAND} and
	 * the inventory key's {@code OPEN_SCREEN} come without a screen (MILESTONE_5.md 18.2); each checks
	 * that the sender is the owner riding their own body. Public for the game tests, which send the
	 * screen's and the Helm key's intents as the payload would.
	 */
	public static void handle(ServerPlayer player, ShipActionPayload action) {
		switch (action.button()) {
			case ShipActionPayload.TAKE_HELM -> {
				takeHelm(player);
				return;
			}
			case ShipActionPayload.LAND -> {
				Helm.land(player);
				return;
			}
			case ShipActionPayload.OPEN_SCREEN -> {
				// The inventory key at the helm (4.3): the ship keeps flying with no input while the screen is open.
				Ships.ridden(player).filter(r -> r.owner().equals(player.getUUID())).ifPresent(r -> ShipMenu.open(player, r));
				return;
			}
			default -> {
			}
		}

		if (!(player.containerMenu instanceof ShipMenu menu) || !menu.stillValid(player)) {
			return;
		}

		UUID owner = menu.owner();
		boolean isOwner = owner.equals(player.getUUID());

		if (isOwner) {
			switch (action.button()) {
				case ShipActionPayload.LAUNCH -> launch(player, action.arg());
				case ShipActionPayload.FIT_SOCKET -> fitSocket(player, action.arg());
				case ShipActionPayload.FUEL -> fuel(player, action.arg() <= 0 ? Integer.MAX_VALUE : action.arg());
				case ShipActionPayload.FIT_UPGRADE -> fitUpgrade(player, action.arg());
				case ShipActionPayload.REMOVE_UPGRADE -> removeUpgrade(player, action.arg());
				case ShipActionPayload.ADD_CREW -> addCrew(player, parse(action.target()));
				case ShipActionPayload.REMOVE_CREW -> removeCrew(player, parse(action.target()));
				case ShipActionPayload.RETURN_TO_LANDING -> closeOrTell(player, Helm.returnToLanding(player));
				case ShipActionPayload.HAND_TO_AUTOPILOT -> {
					Destination to = destination(action.arg());

					if (to != null) {
						closeOrTell(player, Helm.handToAutopilot(player, to));
					}
				}
				case ShipActionPayload.JUMP_ORBIT -> {
					Destination to = destination(action.arg());

					if (to != null) {
						closeOrTell(player, Helm.jumpToOrbit(player, to));
					}
				}
				default -> {
				}
			}
		}

		menu.sendStatus(player, true);
	}

	/**
	 * Take the helm (MILESTONE_5.md 4.2): {@link Helm#take} checks that the sender is the owner in
	 * their own ship. A refusal shows above the hotbar; on success an open ship screen closes.
	 */
	private static void takeHelm(ServerPlayer player) {
		TravelService.Refusal refusal = Helm.take(player);

		if (refusal != TravelService.Refusal.NONE) {
			player.sendOverlayMessage(Component.translatable(refusal.langKey()));
		} else if (player.containerMenu instanceof ShipMenu) {
			player.closeContainer();
		}
	}

	private static void launch(ServerPlayer owner, int destination) {
		Destination to = destination(destination);

		if (to == null) {
			return;
		}

		if (!Ships.isAboard(owner)) {
			owner.sendSystemMessage(Component.translatable("ship.oldrepublic.board_first"));
			return;
		}

		closeOrTell(owner, TravelService.launch(owner, to));
	}

	/**
	 * After a button that sends the ship off (a launch; in the air the Nav's landings and jumps and
	 * the return to the last landing, MILESTONE_5.md 4.5, 4.7, 4.10): the screen closes when the
	 * server took the ship; a refusal says why.
	 */
	private static void closeOrTell(ServerPlayer owner, TravelService.Refusal refusal) {
		if (refusal == TravelService.Refusal.NONE) {
			owner.closeContainer();
		} else {
			owner.sendSystemMessage(Component.translatable(refusal.langKey()));
		}
	}

	/** The destination with this ordinal, or null. */
	private static @Nullable Destination destination(int ordinal) {
		Destination[] all = Destination.values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
	}

	/** One click on a repair socket: its part from the inventory (plates: as many as it still needs). */
	public static boolean fitSocket(ServerPlayer owner, int socket) {
		MinecraftServer server = owner.level().getServer();
		ShipBalance b = ShipBalance.get();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (record == null || socket < 0 || socket >= ShipRules.SOCKETS || Ships.busy(record)) {
			return false;
		}

		Predicate<ItemStack> part = socketPart(socket);
		int available = owner.hasInfiniteMaterials() ? Integer.MAX_VALUE : count(owner, part);
		int taken = ShipRules.partsTaken(record, socket, available, b);

		if (taken <= 0) {
			if (!ShipRules.socketDone(record, socket, b) && record.state() == ShipState.HULK) {
				boolean blankNav = socket == ShipRules.SOCKET_NAV_INDEX && count(owner, s -> s.is(M4Items.NAV_COMPUTER)) > 0;
				owner.sendSystemMessage(Component.translatable(blankNav ? "ship.oldrepublic.socket.nav_blank" : "ship.oldrepublic.socket.missing." + socket));
			}

			return false;
		}

		if (!owner.hasInfiniteMaterials()) {
			take(owner, part, taken);
		}

		ShipRecord fitted = Ships.update(server, owner.getUUID(), r -> ShipRules.fitSocket(r, socket, taken, b)).orElse(record);
		ShipBody ship = Ships.ship(server, owner.getUUID());
		Vec3 at = ship != null ? ship.position().add(0, 1.5, 0) : owner.position();
		TravelEffects.repair(owner.level(), at, ship, socket == ShipRules.SOCKET_HULL ? TravelEffects.REPAIR_WELD : socket);

		if (ShipRules.socketDone(fitted, socket, b)) {
			QuestHooks.onShipPartFitted(owner, socket);
		}

		if (fitted.state() != ShipState.HULK && record.state() == ShipState.HULK) {
			TravelEffects.repair(owner.level(), at, ship, TravelEffects.REPAIR_STAND_UP);
			TravelNetwork.notice(owner, ShipStatusPayload.NOTICE_REPAIRED, 0);
		}

		return true;
	}

	/** The part a socket takes: a loaded nav computer for the nav socket. */
	private static Predicate<ItemStack> socketPart(int socket) {
		Item item = SOCKET_PARTS[socket];
		return socket == ShipRules.SOCKET_NAV_INDEX ? NavData::isLoaded : stack -> stack.is(item);
	}

	/** Fuel from the inventory, at most {@code wanted} cells (4.3). */
	static int fuel(ServerPlayer owner, int wanted) {
		MinecraftServer server = owner.level().getServer();
		ShipBalance b = ShipBalance.get();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (record == null || Ships.busy(record)) {
			return 0;
		}

		Predicate<ItemStack> cell = stack -> stack.is(M4Items.HYPERFUEL_CELL);
		int available = owner.hasInfiniteMaterials() ? wanted : Math.min(wanted, count(owner, cell));
		int taken = ShipRules.fuelTaken(record, available, b);

		if (taken <= 0) {
			owner.sendOverlayMessage(Component.translatable(record.fuel() >= ShipRules.tankSize(record, b) ? "ship.oldrepublic.tank_full" : "ship.oldrepublic.no_cells"));
			return 0;
		}

		if (!owner.hasInfiniteMaterials()) {
			take(owner, cell, taken);
		}

		ShipRecord fuelled = Ships.update(server, owner.getUUID(), r -> ShipRules.fuel(r, taken, b)).orElse(record);
		ShipBody ship = Ships.ship(server, owner.getUUID());
		TravelEffects.repair(owner.level(), ship != null ? ship.position().add(0, 1.2, 0) : owner.position(), ship, TravelEffects.REPAIR_FUEL);
		owner.sendOverlayMessage(Component.translatable("ship.oldrepublic.fuel", fuelled.fuel(), ShipRules.tankSize(fuelled, b)));
		return taken;
	}

	/** Fits the upgrade item for {@code bit} from the inventory (4.7); each fits once. */
	static boolean fitUpgrade(ServerPlayer owner, int bit) {
		int index = upgradeIndex(bit);
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (index < 0 || record == null || Ships.busy(record) || !ShipRules.canFitUpgrade(record, bit)) {
			return false;
		}

		Item item = UPGRADE_ITEMS[index];

		if (!owner.hasInfiniteMaterials()) {
			if (count(owner, s -> s.is(item)) <= 0) {
				owner.sendSystemMessage(Component.translatable("ship.oldrepublic.upgrade.missing", new ItemStack(item).getHoverName()));
				return false;
			}

			take(owner, s -> s.is(item), 1);
		}

		Ships.update(server, owner.getUUID(), r -> ShipRules.withUpgrade(r, bit, true, ShipBalance.get()));
		upgradeEffect(owner);
		return true;
	}

	/** Takes an upgrade out and gives its item back (4.7), when the ship still works without it. */
	static boolean removeUpgrade(ServerPlayer owner, int bit) {
		int index = upgradeIndex(bit);
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBalance b = ShipBalance.get();

		if (index < 0 || record == null || Ships.busy(record) || !ShipRules.canRemoveUpgrade(record, bit, b)) {
			if (record != null && record.has(bit)) {
				owner.sendSystemMessage(Component.translatable("ship.oldrepublic.upgrade.cannot_remove." + index));
			}

			return false;
		}

		Ships.update(server, owner.getUUID(), r -> ShipRules.withUpgrade(r, bit, false, b));
		ItemStack back = new ItemStack(UPGRADE_ITEMS[index]);

		if (!owner.getInventory().add(back)) {
			owner.drop(back, false);
		}

		upgradeEffect(owner);
		return true;
	}

	private static void upgradeEffect(ServerPlayer owner) {
		ShipBody ship = Ships.ship(owner.level().getServer(), owner.getUUID());
		TravelEffects.repair(owner.level(), ship != null ? ship.position().add(0, 1.5, 0) : owner.position(), ship, TravelEffects.REPAIR_UPGRADE);
	}

	private static int upgradeIndex(int bit) {
		for (int i = 0; i < ShipRules.UPGRADES.length; i++) {
			if (ShipRules.UPGRADES[i] == bit) {
				return i;
			}
		}

		return -1;
	}

	// ---------------------------------------------------------------- crew (4.9)

	/** Adds a player within {@code ship.crewAddRange} blocks to the crew; they leave any other crew. */
	public static boolean addCrew(ServerPlayer owner, @Nullable UUID id) {
		MinecraftServer server = owner.level().getServer();
		ServerPlayer member = id == null ? null : server.getPlayerList().getPlayer(id);
		ShipBalance b = ShipBalance.get();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (member == null || record == null || member.level() != owner.level() || member.distanceToSqr(owner) > (double) b.crewAddRange * b.crewAddRange) {
			return false;
		}

		List<UUID> crew = ShipRules.addCrew(record, member.getUUID(), b);

		if (crew == null) {
			owner.sendSystemMessage(Component.translatable("ship.oldrepublic.crew.full"));
			return false;
		}

		TravelAttachments.get(member).crewOf().filter(other -> !other.equals(owner.getUUID()))
				.ifPresent(other -> Ships.update(server, other, r -> r.withCrew(ShipRules.removeCrew(r, member.getUUID()))));
		Ships.update(server, owner.getUUID(), r -> r.withCrew(crew));
		TravelAttachments.set(member, TravelAttachments.get(member).withCrewOf(Optional.of(owner.getUUID())));
		remember(member);
		member.sendSystemMessage(Component.translatable("ship.oldrepublic.crew.joined", owner.getDisplayName()));
		return true;
	}

	/** Takes a player off the crew; if they are aboard they are put off at once. Not while the ship flies. */
	public static boolean removeCrew(ServerPlayer owner, @Nullable UUID id) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (id == null || record == null || !record.crew().contains(id)) {
			return false;
		}

		if (Ships.busy(record)) {
			owner.sendSystemMessage(Component.translatable("ship.oldrepublic.flying"));
			return false;
		}

		Ships.update(server, owner.getUUID(), r -> r.withCrew(ShipRules.removeCrew(r, id)));
		ServerPlayer member = server.getPlayerList().getPlayer(id);

		if (member != null) {
			if (member.getVehicle() instanceof ShipBody ship && ship.getUUID().equals(record.body())) {
				member.stopRiding();
			}

			TravelData data = TravelAttachments.get(member);

			if (data.crewOf().filter(owner.getUUID()::equals).isPresent()) {
				TravelAttachments.set(member, data.withCrewOf(Optional.empty()));
			}

			member.sendSystemMessage(Component.translatable("ship.oldrepublic.crew.removed", owner.getDisplayName()));
		}

		return true;
	}

	// ---------------------------------------------------------------- the screen's view

	/** The {@code ship_status} for {@code viewer} looking at {@code owner}'s ship. */
	static @Nullable ShipStatusPayload status(ServerPlayer viewer, UUID owner, int notice, int noticeArg) {
		MinecraftServer server = viewer.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);

		if (record == null) {
			return null;
		}

		ShipBalance b = ShipBalance.get();
		boolean isOwner = owner.equals(viewer.getUUID());
		ShipBody ship = Ships.ship(server, owner);
		int sockets = 0;

		for (int socket = 0; socket < ShipRules.SOCKETS; socket++) {
			if (ShipRules.socketDone(record, socket, b)) {
				sockets |= 1 << socket;
			}
		}

		ShipStatusPayload.Ship view = new ShipStatusPayload.Ship(record.state().ordinal(), record.hull(), ShipRules.maxHull(record), record.fuel(),
				ShipRules.tankSize(record, b), ShipRules.upgradesOnly(record.upgrades()), ShipRules.holdSize(record, b), ShipRules.seats(record, b),
				ShipRules.crewLimit(record, b), sockets, ShipRules.plates(record), ShipRules.platesNeeded(b), ShipRules.paint(record.upgrades()),
				record.hullCode());
		// In the air the Nav tab hands the ship to the autopilot ("Land at", MILESTONE_5.md 4.5) and, in an orbit, jumps (4.7).
		boolean flying = isOwner && Helm.atHelm(owner);
		Planets.Planet orbit = ship != null && flying ? Planets.orbitOf(ship.level().dimension()) : null;
		List<ShipStatusPayload.Row> rows = new ArrayList<>();
		List<ShipStatusPayload.Row> jumps = new ArrayList<>();

		for (Destination d : Destination.values()) {
			// A Milestone 5 destination is listed once its coordinates are known, so an M4 save's Nav tab is unchanged (MILESTONE_5.md 3.1).
			if (d.hiddenWhileLocked() && !(isOwner && TravelService.isUnlocked(viewer, d))) {
				continue;
			}

			TravelService.Refusal refusal;

			if (!isOwner) {
				refusal = TravelService.Refusal.NOT_OWNER;
			} else if (flying) {
				refusal = Helm.airRefusal(viewer, d, false);
			} else {
				refusal = TravelService.canLaunch(viewer, d);
			}

			rows.add(new ShipStatusPayload.Row(d.ordinal(), refusal.ordinal()));

			if (orbit != null) {
				jumps.add(new ShipStatusPayload.Row(d.ordinal(), Helm.airRefusal(viewer, d, true).ordinal()));
			}
		}

		ShipStatusPayload.Air air = isOwner ? new ShipStatusPayload.Air(Helm.helmRefusal(viewer).ordinal(), flying, Helm.returnRefusal(viewer).ordinal(),
				Helm.returnCost(viewer, record).ordinal(), orbit == null ? -1 : orbit.ordinal(), jumps) : ShipStatusPayload.Air.GROUND;

		List<ShipStatusPayload.Member> crew = new ArrayList<>();

		for (UUID id : record.crew()) {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			crew.add(new ShipStatusPayload.Member(id, name(server, id), ship != null && online != null && online.getVehicle() == ship, online != null));
		}

		List<ShipStatusPayload.Member> nearby = new ArrayList<>();

		if (isOwner) {
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (other != viewer && other.level() == viewer.level() && !record.crew().contains(other.getUUID())
						&& other.distanceToSqr(viewer) <= (double) b.crewAddRange * b.crewAddRange) {
					nearby.add(new ShipStatusPayload.Member(other.getUUID(), other.getPlainTextName(), false, true));
				}
			}
		}

		return new ShipStatusPayload(view, isOwner, ship != null && viewer.getVehicle() == ship, ShipRules.jumpLeavesHullLow(record, b),
				rows, crew, nearby, notice, noticeArg, air);
	}

	// ---------------------------------------------------------------- inventory and names

	/** How many of the matching items the player carries (main inventory and hotbar). */
	static int count(ServerPlayer player, Predicate<ItemStack> matches) {
		int total = 0;

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (matches.test(stack)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	/** Takes {@code amount} of the matching items from the player's inventory (as many as there are). */
	static void take(ServerPlayer player, Predicate<ItemStack> matches, int amount) {
		int left = amount;

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (left <= 0) {
				break;
			}

			if (matches.test(stack)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}

		player.getInventory().setChanged();
	}

	private static @Nullable UUID parse(String id) {
		try {
			return UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Remembers a player's name for the Crew tab. */
	static void remember(ServerPlayer player) {
		NAMES.put(player.getUUID(), player.getPlainTextName());
	}

	/** A player's name: online, or seen this session; empty when unknown. */
	static String name(MinecraftServer server, UUID id) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		return online != null ? online.getPlainTextName() : NAMES.getOrDefault(id, "");
	}

	/** {@link #name} for a message, with a placeholder when unknown. */
	static Component nameOf(MinecraftServer server, UUID id) {
		String name = name(server, id);
		return name.isEmpty() ? Component.translatable("screen.oldrepublic.ship.crew.unknown") : Component.literal(name);
	}

	static void forget(UUID player) {
		LAST_PLATE.remove(player);
	}

	static void clear() {
		LAST_PLATE.clear();
		NAMES.clear();
	}

	private ShipActions() {
	}
}

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
 * What players do to a ship (spec MILESTONE_4.md 4.1–4.4, 4.7, 4.9, 4.10): using the body (board,
 * weld a plate, fuel a cell, open the screen) and every button of the ship screen. All server side;
 * the client only sends intents ({@code ship_action}), and only the owner may change anything. The
 * socket and crew buttons are public for the game tests, which press them without a screen.
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
	static InteractionResult use(ServerPlayer player, CourierShuttle ship, InteractionHand hand) {
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

		if (Flights.isFlying(record.owner()) && player.getVehicle() != ship) {
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

		if (player.getVehicle() == ship || player.isSecondaryUseActive() || !ShipRules.boardable(record) || Flights.isFlying(record.owner())) {
			ShipMenu.open(player, record);
			return InteractionResult.SUCCESS;
		}

		if (!player.startRiding(ship)) {
			player.sendOverlayMessage(Component.translatable("ship.oldrepublic.no_seat"));
		}

		return InteractionResult.SUCCESS;
	}

	/** One plate on a repaired ship: +5 hull (+8 for an Armourcraft or Armstech player at level 2), every 10 ticks (4.4). */
	private static void weld(ServerPlayer player, CourierShuttle ship, ItemStack plate) {
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
	private static Vec3 weldPoint(ServerPlayer player, CourierShuttle ship) {
		Vec3 eye = player.getEyePosition();
		Vec3 toShip = ship.getBoundingBox().getCenter().subtract(eye);
		double reach = Math.min(toShip.length(), 3.0);
		return eye.add(player.getLookAngle().scale(reach));
	}

	// ---------------------------------------------------------------- the screen's buttons

	/** A {@code ship_action} from the ship screen. */
	static void handle(ServerPlayer player, ShipActionPayload action) {
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
				default -> {
				}
			}
		}

		menu.sendStatus(player, true);
	}

	private static void launch(ServerPlayer owner, int destination) {
		Destination[] all = Destination.values();

		if (destination < 0 || destination >= all.length) {
			return;
		}

		if (!Ships.isAboard(owner)) {
			owner.sendSystemMessage(Component.translatable("ship.oldrepublic.board_first"));
			return;
		}

		TravelService.Refusal refusal = TravelService.launch(owner, all[destination]);

		if (refusal == TravelService.Refusal.NONE) {
			owner.closeContainer();
		} else {
			owner.sendSystemMessage(Component.translatable(refusal.langKey()));
		}
	}

	/** One click on a repair socket: its part from the inventory (plates: as many as it still needs). */
	public static boolean fitSocket(ServerPlayer owner, int socket) {
		MinecraftServer server = owner.level().getServer();
		ShipBalance b = ShipBalance.get();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (record == null || socket < 0 || socket >= ShipRules.SOCKETS || Flights.isFlying(record.owner())) {
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
		CourierShuttle ship = Ships.shuttle(server, owner.getUUID());
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

		if (record == null || Flights.isFlying(record.owner())) {
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
		CourierShuttle ship = Ships.shuttle(server, owner.getUUID());
		TravelEffects.repair(owner.level(), ship != null ? ship.position().add(0, 1.2, 0) : owner.position(), ship, TravelEffects.REPAIR_FUEL);
		owner.sendOverlayMessage(Component.translatable("ship.oldrepublic.fuel", fuelled.fuel(), ShipRules.tankSize(fuelled, b)));
		return taken;
	}

	/** Fits the upgrade item for {@code bit} from the inventory (4.7); each fits once. */
	static boolean fitUpgrade(ServerPlayer owner, int bit) {
		int index = upgradeIndex(bit);
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (index < 0 || record == null || Flights.isFlying(record.owner()) || !ShipRules.canFitUpgrade(record, bit)) {
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

		if (index < 0 || record == null || Flights.isFlying(record.owner()) || !ShipRules.canRemoveUpgrade(record, bit, b)) {
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
		CourierShuttle ship = Ships.shuttle(owner.level().getServer(), owner.getUUID());
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

		if (Flights.isFlying(record.owner())) {
			owner.sendSystemMessage(Component.translatable("ship.oldrepublic.flying"));
			return false;
		}

		Ships.update(server, owner.getUUID(), r -> r.withCrew(ShipRules.removeCrew(r, id)));
		ServerPlayer member = server.getPlayerList().getPlayer(id);

		if (member != null) {
			if (member.getVehicle() instanceof CourierShuttle ship && ship.getUUID().equals(record.body())) {
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
		CourierShuttle ship = Ships.shuttle(server, owner);
		int sockets = 0;

		for (int socket = 0; socket < ShipRules.SOCKETS; socket++) {
			if (ShipRules.socketDone(record, socket, b)) {
				sockets |= 1 << socket;
			}
		}

		ShipStatusPayload.Ship view = new ShipStatusPayload.Ship(record.state().ordinal(), record.hull(), record.fuel(), ShipRules.tankSize(record, b),
				ShipRules.upgradesOnly(record.upgrades()), ShipRules.holdSize(record, b), ShipRules.seats(record, b), ShipRules.crewLimit(record, b),
				sockets, ShipRules.plates(record), ShipRules.platesNeeded(b), ShipRules.paint(record.upgrades()), record.hullCode());
		List<ShipStatusPayload.Row> rows = new ArrayList<>();

		for (Destination d : Destination.values()) {
			TravelService.Refusal refusal = isOwner ? TravelService.canLaunch(viewer, d) : TravelService.Refusal.NOT_OWNER;
			rows.add(new ShipStatusPayload.Row(d.ordinal(), refusal.ordinal()));
		}

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
				rows, crew, nearby, notice, noticeArg);
	}

	// ---------------------------------------------------------------- inventory and names

	private static int count(ServerPlayer player, Predicate<ItemStack> matches) {
		int total = 0;

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (matches.test(stack)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static void take(ServerPlayer player, Predicate<ItemStack> matches, int amount) {
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

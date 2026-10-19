package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.ShipRules.Recall;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Launches, recalls and destinations (spec MILESTONE_4.md 4.5–4.8). The checks live in
 * {@link ShipRules} as pure functions; the sequence itself runs in {@link Flights}.
 * {@link #moveAcross} is the lead's and is the only code that moves anything between levels.
 */
public final class TravelService {
	/** Why a launch was refused. Stored by ordinal and append only. */
	public enum Refusal {
		NONE,
		NOT_OWNER,
		NOT_READY,
		NO_FUEL,
		HULL,
		LOCKED,
		COMBAT,
		DISABLED_BY_CONFIG,
		BUSY,
		/** The ship already stands at that destination. */
		HERE,
		/** The destination does not exist now: its world is missing, or the camp is not there. */
		UNAVAILABLE,
		/** Milestone 5: the helm is not unlocked yet ({@code TravelData.helm}, MILESTONE_5.md 4.2). */
		NO_HELM,
		/** Milestone 5: Tyrvann before the owner's {@code BLOCKADE} stage (MILESTONE_5.md 4.7, 6.5). */
		BLOCKADED,
		/** Milestone 5: no clear, level ground for the ship here (MILESTONE_5.md 4.6). */
		NO_ROOM,
		/** Milestone 5: the ship was hit in the last 200 ticks ({@code ShipCombat.underFire}, MILESTONE_5.md 4.7). */
		UNDER_FIRE;

		/** Lang key of the reason shown on the Nav tab and in chat. */
		public String langKey() {
			return "ship.oldrepublic.refusal." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** How a recall is paid (4.8). */
	public enum Payment {
		/** A hyperfuel cell from the owner's inventory (the beacon, a pad terminal). */
		CELL,
		/** The hub host's favour: no cell, once per in-game day. */
		FREE_DAILY,
		/** Nothing and no daily allowance (an operator's command). */
		NONE
	}

	/** A ship this close to a destination's pad (horizontally) is already there; a hub counts as one place. */
	private static final double HERE_RADIUS = 8;
	private static final double HUB_RADIUS = 64;

	/** The checks of 4.6, without starting anything. */
	public static Refusal canLaunch(ServerPlayer owner, Destination to) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		CourierShuttle ship = record == null ? null : Ships.shuttle(server, record.owner());
		boolean busy = record != null && Flights.isFlying(record.owner());
		boolean inCombat = record != null && CombatLock.inCombat(server, record, ship);
		Refusal refusal = ShipRules.launchRefusal(record, ShipBalance.travel().enabled, busy, isUnlocked(owner, to), inCombat, ShipBalance.get());

		if (refusal != Refusal.NONE || record == null) {
			return refusal;
		}

		if (ship == null) {
			return Refusal.NOT_READY;
		}

		return destinationRefusal(owner, record, to);
	}

	/** Starts the flight sequence (4.6) when {@link #canLaunch} allows it; returns why not otherwise. */
	public static Refusal launch(ServerPlayer owner, Destination to) {
		Refusal refusal = canLaunch(owner, to);
		return refusal == Refusal.NONE ? start(owner, to, false) : refusal;
	}

	/**
	 * The operator's flight ({@code /oldrepublic ship travel}, 4.11): the owner must be aboard; the
	 * config switch, the lock, the combat lock, fuel and hull are not checked. {@code instant} skips
	 * the climb and descent (every phase one tick).
	 */
	public static Refusal launchByCommand(ServerPlayer owner, Destination to, boolean instant) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);

		if (record == null) {
			return Refusal.NOT_OWNER;
		}

		CourierShuttle ship = Ships.shuttle(server, record.owner());

		if (Flights.isFlying(record.owner())) {
			return Refusal.BUSY;
		}

		if (ship == null || owner.getVehicle() != ship || record.state() == ShipState.HULK) {
			return Refusal.NOT_READY;
		}

		Refusal refusal = destinationRefusal(owner, record, to);
		return refusal == Refusal.NONE ? start(owner, to, instant) : refusal;
	}

	private static Refusal start(ServerPlayer owner, Destination to, boolean instant) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElseThrow();
		CourierShuttle ship = Ships.shuttle(server, record.owner());
		ResourceKey<Level> level = levelOf(owner, to);

		if (ship == null || level == null) {
			return Refusal.UNAVAILABLE;
		}

		Flights.launch(server, record, ship, to, Pads.of(owner, to), level, instant);
		return Refusal.NONE;
	}

	/** Why the destination itself is refused: missing, or where the ship already stands. */
	static Refusal destinationRefusal(ServerPlayer owner, ShipRecord record, Destination to) {
		MinecraftServer server = owner.level().getServer();
		ResourceKey<Level> level = levelOf(owner, to);

		if (level == null || server.getLevel(level) == null) {
			return Refusal.UNAVAILABLE;
		}

		Pads.Pad pad = Pads.of(owner, to);

		if (to == Destination.HUNTING_CAMP && pad == null) {
			return Refusal.UNAVAILABLE;
		}

		return isHere(server, record, to, pad) ? Refusal.HERE : Refusal.NONE;
	}

	/** Whether the ship already stands at {@code to}: within a few blocks of its pad, or anywhere at a hub. */
	static boolean isHere(MinecraftServer server, ShipRecord record, Destination to, Pads.@Nullable Pad pad) {
		Planets.Planet planet = to.planet();

		if (planet != null && planet != Planets.Planet.HOME && to != Destination.HUNTING_CAMP) {
			BlockPos hub = PlanetHubs.hub(server, planet);
			return hub != null && record.pos().dimension().equals(planet.level()) && horizontal(record.pos().pos(), hub) <= HUB_RADIUS;
		}

		return pad != null && record.pos().dimension().equals(pad.pos().dimension()) && horizontal(record.pos().pos(), pad.pos().pos()) <= HERE_RADIUS;
	}

	/** The world of {@code to} for this owner: the camp's world for the hunting camp. */
	static @Nullable ResourceKey<Level> levelOf(ServerPlayer owner, Destination to) {
		if (to == Destination.HUNTING_CAMP) {
			GlobalPos camp = PlanetSites.find(owner, PlanetSites.Site.HUNTING_CAMP);
			return camp == null ? null : camp.dimension();
		}

		Planets.Planet planet = to.planet();
		return planet == null ? null : planet.level();
	}

	// ---------------------------------------------------------------- recall (4.8)

	/** Flies the owner's ship to {@code pad} on its own (4.8), with no cost and no daily allowance (the operator's recall). */
	public static void recall(ServerPlayer owner, GlobalPos pad) {
		recall(owner, Pads.at(owner, pad), Payment.NONE);
	}

	/**
	 * The recall rule of 4.8 to {@code pad}: refused while anyone is aboard, while it flies or when
	 * it is already there; a DISABLED ship is towed (free, once a day unless {@code NONE}); any
	 * other is recalled for a cell, or free once a day by the hub host.
	 *
	 * @return what happened: {@link Recall#RECALL} or {@link Recall#TOW}, or why not
	 */
	static Recall recall(ServerPlayer owner, Pads.Pad pad, Payment payment) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		TravelData data = TravelAttachments.get(owner);
		ShipBalance.Travel t = ShipBalance.travel();
		long today = Clocks.day(server);
		CourierShuttle ship = record == null ? null : Ships.shuttle(server, record.owner());
		boolean aboard = ship != null && !ship.getPassengers().isEmpty();
		boolean atPad = record != null && atPad(server, record, pad);
		boolean none = payment == Payment.NONE;

		if (record != null && Flights.isFlying(record.owner())) {
			return Recall.FLYING;
		}

		Recall result = ShipRules.recall(record, t.enabled || none, aboard, atPad,
				none || findCell(owner) != null, payment == Payment.FREE_DAILY, today, data.recallDay(), none ? Long.MIN_VALUE : data.towDay(), t);

		if (!result.allowed() || record == null) {
			return result;
		}

		if (result == Recall.TOW && !none) {
			TravelAttachments.set(owner, data.withTowDay(today));
		} else if (result == Recall.RECALL && payment == Payment.FREE_DAILY) {
			TravelAttachments.set(owner, data.withRecallDay(today));
		} else if (result == Recall.RECALL && payment == Payment.CELL) {
			takeCell(owner);
		}

		Flights.recall(server, record, pad, result == Recall.TOW);
		return result;
	}

	/** Whether the ship stands at the recall's pad already (a hub counts as one place). */
	private static boolean atPad(MinecraftServer server, ShipRecord record, Pads.Pad pad) {
		if (pad.hub() != null) {
			BlockPos hub = PlanetHubs.hub(server, pad.hub());
			return hub != null && record.pos().dimension().equals(pad.pos().dimension()) && horizontal(record.pos().pos(), hub) <= HUB_RADIUS;
		}

		return record.pos().dimension().equals(pad.pos().dimension()) && horizontal(record.pos().pos(), pad.pos().pos()) <= HERE_RADIUS;
	}

	/** A hyperfuel cell the owner carries, the held one first; null when there is none. */
	private static @Nullable ItemStack findCell(ServerPlayer owner) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = owner.getItemInHand(hand);

			if (held.is(M4Items.HYPERFUEL_CELL)) {
				return held;
			}
		}

		for (ItemStack stack : owner.getInventory().getNonEquipmentItems()) {
			if (stack.is(M4Items.HYPERFUEL_CELL)) {
				return stack;
			}
		}

		return null;
	}

	private static void takeCell(ServerPlayer owner) {
		ItemStack cell = findCell(owner);

		if (cell != null) {
			cell.shrink(1);
			owner.getInventory().setChanged();
		}
	}

	// ---------------------------------------------------------------- destinations

	/** Whether {@code p} may fly to {@code d}; {@link Destination#HOME} always. */
	public static boolean isUnlocked(ServerPlayer p, Destination d) {
		return TravelAttachments.get(p).isUnlocked(d);
	}

	/** Unlocks {@code d} for {@code p} (the story calls this, 8.2 and 8.6), with a toast the first time. */
	public static void unlock(ServerPlayer p, Destination d) {
		TravelData data = TravelAttachments.get(p);

		if (!data.isUnlocked(d)) {
			TravelAttachments.set(p, data.withUnlocked(d, true));
			TravelNetwork.notice(p, ShipStatusPayload.NOTICE_UNLOCKED, d.ordinal());
		}
	}

	/** Where {@code p}'s ship lands for {@code d}, or null when there is no pad yet. */
	public static @Nullable GlobalPos pad(ServerPlayer p, Destination d) {
		Pads.Pad pad = Pads.of(p, d);
		return pad == null ? null : Pads.surfaceOf(p.level().getServer(), pad).pos();
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Moves {@code root} and everything riding it to {@code pos} in {@code to}, facing {@code yaw}.
	 * Vanilla's cross-level teleport ejects the passengers, moves each one, re-creates the vehicle in
	 * the new level (same UUID, a new object) and remounts them; this then remounts any recorded
	 * rider that is not riding the new body (found by UUID, so only in a tracked chunk: hold a
	 * ticket on the destination first). Within one level the entity moves in place.
	 *
	 * <p>Callers must not keep {@code root} afterwards: use the returned entity.
	 *
	 * @return the moved entity: a new object after a change of level, or {@code root} when the
	 *         teleport did not happen
	 */
	public static Entity moveAcross(Entity root, ServerLevel to, Vec3 pos, float yaw) {
		List<UUID> riders = new ArrayList<>();

		for (Entity passenger : root.getPassengers()) {
			riders.add(passenger.getUUID());
		}

		Entity moved = root.teleport(new TeleportTransition(to, pos, Vec3.ZERO, yaw, root.getXRot(), TeleportTransition.DO_NOTHING));

		if (moved == null) {
			return root;
		}

		for (UUID id : riders) {
			if (isAboard(moved, id)) {
				continue;
			}

			// Only entities in tracked chunks are found by UUID: callers hold a ticket on the destination.
			Entity rider = to.getEntity(id);

			if (rider != null && rider != moved && !rider.isRemoved()) {
				rider.stopRiding();
				rider.startRiding(moved, true, false);
			}
		}

		return moved;
	}

	private static boolean isAboard(Entity vehicle, UUID id) {
		for (Entity passenger : vehicle.getPassengers()) {
			if (passenger.getUUID().equals(id)) {
				return true;
			}
		}

		return false;
	}

	/** Registers the travel contracts' hooks (the "not while flying" lock). Called from {@code M4Contracts}. */
	public static void init() {
		TravelLocks.init();
	}

	private TravelService() {
	}
}

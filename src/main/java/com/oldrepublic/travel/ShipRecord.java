package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/**
 * Everything about one player's ship (spec MILESTONE_4.md 4.9). The truth lives here, in
 * {@link ShipRegistry}; the entity is a body that reads and writes it. Immutable: change it with the
 * {@code with*} methods and {@link ShipRegistry#put}.
 *
 * @param owner the player who owns the ship
 * @param body the UUID of the current body; a body with any other UUID discards itself on load
 * @param pos where the body stands (its pad)
 * @param hull hull integrity, 0 to 100
 * @param fuel hyperfuel cells in the tank
 * @param upgrades fitted upgrades, a bit per {@link #UPGRADE_SEAT} and the others
 * @param hold the hold's stacks (at most {@link #MAX_HOLD}); empty stacks keep their slot
 * @param crew players allowed to board (at most {@link #MAX_CREW})
 * @param hullCode the short code painted on the nose
 * @param flight the flight in progress, if any (for restart recovery, 4.9)
 */
public record ShipRecord(UUID owner, UUID body, GlobalPos pos, float yaw, ShipState state, int hull, int fuel, int upgrades,
		List<ItemStack> hold, List<UUID> crew, String hullCode, Optional<Flight> flight) {
	public static final int MAX_HOLD = 36;
	public static final int MAX_CREW = 3;
	public static final int UPGRADE_SEAT = 1;
	public static final int UPGRADE_CARGO = 1 << 1;
	public static final int UPGRADE_PLATING = 1 << 2;
	public static final int UPGRADE_TANK = 1 << 3;
	/*
	 * Milestone 5 (spec MILESTONE_5.md 4.8, 4.9, 11.2) uses free bits only: 0-3 are the upgrades
	 * above, 8-10 the repair sockets, 12-15 the plate count and 16-17 the paint
	 * (ShipRules.SOCKET_*, PLATES_SHIFT, PAINT_SHIFT); 11 stays unused.
	 */
	/** Laser cannons (4.8): the ship can fire; what the story's {@code ARMED} stage waits for ({@code Ships.hasWeapons}, 6.5). */
	public static final int UPGRADE_CANNONS = 1 << 4;
	/** Shield generator (4.8). */
	public static final int UPGRADE_SHIELD = 1 << 5;
	/** Thrusters (4.8). */
	public static final int UPGRADE_THRUSTERS = 1 << 6;
	/** Missile rack (4.8). */
	public static final int UPGRADE_MISSILES = 1 << 7;
	/** The hull class: set for the interceptor, clear for the courier (4.9). */
	public static final int CLASS_INTERCEPTOR = 1 << 18;
	/** Legendary ship part: the Spindle Capacitor (11.2). */
	public static final int PART_SPINDLE_CAPACITOR = 1 << 19;
	/** Legendary ship part: the Hymn-Tuned Drive (11.2). */
	public static final int PART_HYMN_DRIVE = 1 << 20;

	/**
	 * A flight in progress.
	 *
	 * @param phase the 4.6 phase index: 0 spool, 1 climb, 2 jump, 3 hyperspace, 4 descent, 5 settle
	 */
	public record Flight(Destination from, Destination to, int phase) {
		private static final Codec<Destination> DESTINATION_CODEC = Codec.STRING.comapFlatMap(id -> {
			Destination d = Destination.byId(id);
			return d != null ? DataResult.success(d) : DataResult.error(() -> "Unknown destination " + id);
		}, Destination::id);

		public static final Codec<Flight> CODEC = RecordCodecBuilder.create(i -> i.group(
				DESTINATION_CODEC.fieldOf("from").forGetter(Flight::from),
				DESTINATION_CODEC.fieldOf("to").forGetter(Flight::to),
				Codec.INT.optionalFieldOf("phase", 0).forGetter(Flight::phase)
		).apply(i, Flight::new));
	}

	private static final Codec<ShipState> STATE_CODEC = Codec.INT.xmap(i -> ShipState.values()[Math.floorMod(i, ShipState.values().length)], ShipState::ordinal);

	public static final Codec<ShipRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(ShipRecord::owner),
			UUIDUtil.CODEC.fieldOf("body").forGetter(ShipRecord::body),
			GlobalPos.CODEC.fieldOf("pos").forGetter(ShipRecord::pos),
			Codec.FLOAT.optionalFieldOf("yaw", 0f).forGetter(ShipRecord::yaw),
			STATE_CODEC.optionalFieldOf("state", ShipState.HULK).forGetter(ShipRecord::state),
			Codec.INT.optionalFieldOf("hull", 0).forGetter(ShipRecord::hull),
			Codec.INT.optionalFieldOf("fuel", 0).forGetter(ShipRecord::fuel),
			Codec.INT.optionalFieldOf("upgrades", 0).forGetter(ShipRecord::upgrades),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("hold", List.of()).forGetter(ShipRecord::hold),
			UUIDUtil.CODEC.listOf().optionalFieldOf("crew", List.of()).forGetter(ShipRecord::crew),
			Codec.STRING.optionalFieldOf("hull_code", "").forGetter(ShipRecord::hullCode),
			Flight.CODEC.optionalFieldOf("flight").forGetter(ShipRecord::flight)
	).apply(i, ShipRecord::new));

	public ShipRecord {
		hull = Math.clamp(hull, 0, 100);
		fuel = Math.max(0, fuel);
		List<ItemStack> copied = new ArrayList<>(Math.min(hold.size(), MAX_HOLD));

		for (int s = 0; s < hold.size() && s < MAX_HOLD; s++) {
			copied.add(hold.get(s).copy());
		}

		hold = List.copyOf(copied);
		crew = List.copyOf(crew.size() > MAX_CREW ? crew.subList(0, MAX_CREW) : crew);
	}

	/** A new hulk for {@code owner} at {@code pos}: hull 0, no fuel, empty hold. */
	public static ShipRecord hulk(UUID owner, UUID body, GlobalPos pos, float yaw, String hullCode) {
		return new ShipRecord(owner, body, pos, yaw, ShipState.HULK, 0, 0, 0, List.of(), List.of(), hullCode, Optional.empty());
	}

	public boolean has(int upgrade) {
		return (upgrades & upgrade) != 0;
	}

	public ShipRecord withBody(UUID newBody) {
		return new ShipRecord(owner, newBody, pos, yaw, state, hull, fuel, upgrades, hold, crew, hullCode, flight);
	}

	public ShipRecord withPos(GlobalPos newPos, float newYaw) {
		return new ShipRecord(owner, body, newPos, newYaw, state, hull, fuel, upgrades, hold, crew, hullCode, flight);
	}

	public ShipRecord withState(ShipState newState) {
		return new ShipRecord(owner, body, pos, yaw, newState, hull, fuel, upgrades, hold, crew, hullCode, flight);
	}

	public ShipRecord withHull(int newHull) {
		return new ShipRecord(owner, body, pos, yaw, state, newHull, fuel, upgrades, hold, crew, hullCode, flight);
	}

	public ShipRecord withFuel(int newFuel) {
		return new ShipRecord(owner, body, pos, yaw, state, hull, newFuel, upgrades, hold, crew, hullCode, flight);
	}

	public ShipRecord withUpgrades(int newUpgrades) {
		return new ShipRecord(owner, body, pos, yaw, state, hull, fuel, newUpgrades, hold, crew, hullCode, flight);
	}

	public ShipRecord withHold(List<ItemStack> newHold) {
		return new ShipRecord(owner, body, pos, yaw, state, hull, fuel, upgrades, newHold, crew, hullCode, flight);
	}

	public ShipRecord withCrew(List<UUID> newCrew) {
		return new ShipRecord(owner, body, pos, yaw, state, hull, fuel, upgrades, hold, newCrew, hullCode, flight);
	}

	public ShipRecord withFlight(Optional<Flight> newFlight) {
		return new ShipRecord(owner, body, pos, yaw, state, hull, fuel, upgrades, hold, crew, hullCode, newFlight);
	}
}

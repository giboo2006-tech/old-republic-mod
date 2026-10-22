package com.oldrepublic.travel;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code ship} (spec MILESTONE_4.md 14.3): fuel, hull wear and repair, the hulk,
 * seats, hold and the camera, with the flight and pad keys in the nested {@link Travel} section
 * {@code travel}. Server side; rules take instances so tests can pass the defaults
 * ({@code new ShipBalance()}, {@code new ShipBalance.Travel()}) without touching the config file.
 */
public final class ShipBalance {
	public static ShipBalance get() {
		return BalanceConfig.section("ship", ShipBalance.class, ShipBalance::new);
	}

	/** The {@code travel} section. */
	public static Travel travel() {
		return BalanceConfig.section("travel", Travel.class, Travel::new);
	}

	/** Hyperfuel cells the tank holds, without and with the fuel tank extension, and the cells a jump burns. */
	public int tankCells = 4;
	public int tankCellsUpgraded = 6;
	public int cellsPerJump = 1;

	/** Hull a jump wears off (reinforced plating: {@link #wearReinforced}); below {@link #jumpMinHull} the ship is DISABLED. */
	public int wearPerJump = 5;
	public int wearReinforced = 3;
	public int jumpMinHull = 20;
	/** Below this hull the nacelle sparks and the smoke trail show (4.1). */
	public int damageFxHull = 40;
	/** The story's sabotage sets the hull to this, once (8.5). */
	public int sabotageHull = 10;

	/** Durasteel plates the hulk's plating socket takes (4.2). */
	public int hullPlates = 12;
	/** Hull one plate adds when used on the ship; Armourcraft or Armstech level 2 adds the skilled amount. */
	public int plateRepair = 5;
	public int plateRepairSkilled = 8;
	/** Ticks between two plate repairs by the same player. */
	public int plateRepairCooldown = 10;
	/** Credits per hull point a spacer charges to repair to full. */
	public int spacerRepairPerPoint = 6;

	/** Cells left in the hold when the hulk is placed; the cradle's far band from the giver [min, max] (MILESTONE_6.md 3.2). */
	public int starterCells = 2;
	public int[] cradleRange = {250, 450};
	/** The owner adds crew from players within this many blocks. */
	public int crewAddRange = 16;

	/** Passenger seats besides the pilot, without and with the passenger seat; hold slots without and with the cargo bay. */
	public int seats = 2;
	public int seatsUpgraded = 3;
	public int hold = 27;
	public int holdUpgraded = 36;

	/** Blocks added to a rider's third-person camera distance while aboard (4.6). */
	public float cameraDistance = 8f;

	/** The {@code travel} section: the switch, the combat lock, the flight's phases, pads, the beacon and the daily favours. */
	public static final class Travel {
		/** False: ships do not jump (repairs and the hulk still work); operators use {@code /oldrepublic ship travel}. */
		public boolean enabled = true;
		/** Refuse a launch while a hostile within {@link #combatLockRange} hurt a crew member in the last {@link #combatLockTicks}. */
		public boolean combatLock = true;
		public int combatLockRange = 16;
		public int combatLockTicks = 100;

		/** The phases of 4.6, in ticks; {@link #climbHeight} in blocks; {@link #hyperspaceMaxWait} extra ticks for the destination to load. */
		public int spoolTicks = 40;
		public int climbTicks = 60;
		public int climbHeight = 40;
		public int jumpTicks = 10;
		public int hyperspaceTicks = 80;
		public int hyperspaceMaxWait = 200;
		public int descentTicks = 80;
		public int settleTicks = 20;

		/** Bays of a hub pad; a bay claim lapses after this many ticks unattended; the overflow rings (blocks from the hub centre). */
		public int padBays = 4;
		public int bayLapseTicks = 24000;
		public int[] overflowRings = {64, 72, 80};
		/** The hunting camp's pad lies this far outside the camp. */
		public int campPadOffset = 48;
		/** Chunk radius of the destination ticket, and how long it stays after Settle. */
		public int chunkTicketRadius = 3;
		public int ticketLingerTicks = 200;

		/** The landing beacon's clear square (side) and its allowed slope. */
		public int beaconArea = 11;
		public int beaconMaxSlope = 2;

		/** Free recalls by the hub host and free tows per in-game day. */
		public int freeRecallPerDay = 1;
		public int freeTowPerDay = 1;
	}
}

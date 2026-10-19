package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.travel.TravelService.Refusal;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The ship's rules as pure functions (spec MILESTONE_4.md 4.2–4.9): capacities, launch checks, the
 * jump's cost, the state at Settle, the four repair sockets, plates, fuel, upgrades, crew, bays,
 * recalls and restart recovery. Nothing here touches a level or the registry, so the game tests
 * check every rule on detached records in a few ticks.
 *
 * <p>The hulk's socket progress and the paint scheme have no fields of their own in
 * {@link ShipRecord}: they live in the upgrade mask above the four upgrade bits
 * ({@link #SOCKET_CORE}, {@link #SOCKET_MOTIVATOR}, {@link #SOCKET_NAV}, four bits of fitted plates
 * and two bits of paint), which no upgrade ever uses.
 */
public final class ShipRules {
	/** Repair sockets, as passed to {@code QuestHooks.onShipPartFitted}. */
	public static final int SOCKET_HULL = 0;
	public static final int SOCKET_CORE_INDEX = 1;
	public static final int SOCKET_MOTIVATOR_INDEX = 2;
	public static final int SOCKET_NAV_INDEX = 3;
	public static final int SOCKETS = 4;

	/** Socket bits in {@link ShipRecord#upgrades()}. */
	public static final int SOCKET_CORE = 1 << 8;
	public static final int SOCKET_MOTIVATOR = 1 << 9;
	public static final int SOCKET_NAV = 1 << 10;
	private static final int PLATES_SHIFT = 12;
	private static final int PLATES_MASK = 0xF << PLATES_SHIFT;
	/** The most plates the mask can count. */
	public static final int MAX_PLATES = 15;
	/** The paint scheme (4.1), two bits above the plates: fixed when the hulk is placed. */
	private static final int PAINT_SHIFT = 16;
	private static final int PAINT_MASK = 0x3 << PAINT_SHIFT;
	/** Bits that are not upgrades: socket progress and paint. */
	private static final int NOT_UPGRADES = SOCKET_CORE | SOCKET_MOTIVATOR | SOCKET_NAV | PLATES_MASK | PAINT_MASK;

	/** Every upgrade bit, in Status tab order. */
	public static final int[] UPGRADES = {ShipRecord.UPGRADE_SEAT, ShipRecord.UPGRADE_CARGO, ShipRecord.UPGRADE_PLATING, ShipRecord.UPGRADE_TANK};

	/** The phases of 4.6, as stored in {@link ShipRecord.Flight#phase()}. */
	public static final int PHASE_SPOOL = 0;
	public static final int PHASE_CLIMB = 1;
	public static final int PHASE_JUMP = 2;
	public static final int PHASE_HYPERSPACE = 3;
	public static final int PHASE_DESCENT = 4;
	public static final int PHASE_SETTLE = 5;
	/** Synced animation value of a ship on the ground (not a 4.6 phase). */
	public static final int PHASE_GROUND = -1;
	/**
	 * Synced animation value of a ship its owner flies by hand (MILESTONE_5.md 4.2): never stored in
	 * a {@link ShipRecord.Flight}; the helm's Spool and rise show {@link #PHASE_SPOOL} and {@link #PHASE_CLIMB}.
	 */
	public static final int PHASE_HELM = 6;

	/** How worn a ship looks and sounds (4.1, synced on the body): sound; below {@code ship.damageFxHull}; below the jump minimum. */
	public static final int WEAR_NONE = 0;
	public static final int WEAR_WORN = 1;
	public static final int WEAR_FAILING = 2;

	/** Struts deploy when the descending ship is this many blocks above its pad (4.6). */
	public static final double STRUT_HEIGHT = 6;

	/** Why a recall (4.8) is refused, or that it goes ahead (as a recall or a tow). */
	public enum Recall {
		RECALL,
		TOW,
		NO_SHIP,
		HULK,
		FLYING,
		ABOARD,
		ALREADY_HERE,
		NO_CELL,
		TOW_USED,
		FREE_USED,
		DISABLED_BY_CONFIG;

		public boolean allowed() {
			return this == RECALL || this == TOW;
		}

		/** Lang key of the refusal (or the confirmation) shown to the owner. */
		public String langKey() {
			return "ship.oldrepublic.recall." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Where a ship caught mid-flight by a restart is placed (4.9): back at its origin or at its destination. */
	public enum Recovery {
		NONE,
		ORIGIN,
		DESTINATION
	}

	// ---------------------------------------------------------------- capacities

	public static int tankSize(ShipRecord r, ShipBalance b) {
		return r.has(ShipRecord.UPGRADE_TANK) ? b.tankCellsUpgraded : b.tankCells;
	}

	/** Passenger seats besides the pilot. */
	public static int seats(ShipRecord r, ShipBalance b) {
		return r.has(ShipRecord.UPGRADE_SEAT) ? b.seatsUpgraded : b.seats;
	}

	/** Hold slots in use (27, or 36 with the cargo bay extension). */
	public static int holdSize(ShipRecord r, ShipBalance b) {
		return Math.min(ShipRecord.MAX_HOLD, r.has(ShipRecord.UPGRADE_CARGO) ? b.holdUpgraded : b.hold);
	}

	/** Hull a jump wears off. */
	public static int wear(ShipRecord r, ShipBalance b) {
		return r.has(ShipRecord.UPGRADE_PLATING) ? b.wearReinforced : b.wearPerJump;
	}

	/** The most crew the owner may name (2, or 3 with the passenger seat). */
	public static int crewLimit(ShipRecord r, ShipBalance b) {
		return Math.min(ShipRecord.MAX_CREW, seats(r, b));
	}

	// ---------------------------------------------------------------- launch and jump

	/**
	 * The launch checks of 4.6, in order: an owner with a ship, the config switch, no flight already
	 * running, an unlocked destination, a READY ship (a DISABLED one says why: its hull), fuel, hull,
	 * then the combat lock.
	 *
	 * @param record the owner's ship, or null when they have none
	 */
	public static Refusal launchRefusal(@Nullable ShipRecord record, boolean enabled, boolean busy, boolean unlocked, boolean inCombat, ShipBalance b) {
		if (record == null) {
			return Refusal.NOT_OWNER;
		}

		if (!enabled) {
			return Refusal.DISABLED_BY_CONFIG;
		}

		if (busy || record.state().flying() || record.flight().isPresent()) {
			return Refusal.BUSY;
		}

		if (!unlocked) {
			return Refusal.LOCKED;
		}

		if (record.state() == ShipState.DISABLED || record.hull() < b.jumpMinHull && record.state() != ShipState.HULK) {
			return Refusal.HULL;
		}

		if (record.state() == ShipState.GROUNDED || record.state() == ShipState.READY && record.fuel() < b.cellsPerJump) {
			return Refusal.NO_FUEL;
		}

		if (record.state() != ShipState.READY) {
			return Refusal.NOT_READY;
		}

		return inCombat ? Refusal.COMBAT : Refusal.NONE;
	}

	/** The wear a body shows: none on a hulk (it has its tarp), else by the hull against the damage and jump thresholds. */
	public static int wearLevel(ShipRecord r, ShipBalance b) {
		if (r.state() == ShipState.HULK) {
			return WEAR_NONE;
		}

		return r.hull() < b.jumpMinHull ? WEAR_FAILING : r.hull() < b.damageFxHull ? WEAR_WORN : WEAR_NONE;
	}

	/** Whether the jump will leave the hull below the jump minimum (the nav console's warning, 4.6). */
	public static boolean jumpLeavesHullLow(ShipRecord r, ShipBalance b) {
		return r.hull() - wear(r, b) < b.jumpMinHull;
	}

	/** The record after the jump took its fuel and wear (4.4, 4.6). */
	public static ShipRecord afterJump(ShipRecord r, ShipBalance b) {
		return r.withFuel(Math.max(0, r.fuel() - b.cellsPerJump)).withHull(r.hull() - wear(r, b));
	}

	/**
	 * The state at Settle (4.6), and of any repaired ship at rest: {@code DISABLED} below the jump
	 * minimum (it wins), {@code GROUNDED} with too little fuel for a jump, else {@code READY}.
	 */
	public static ShipState settleState(ShipRecord r, ShipBalance b) {
		if (r.hull() < b.jumpMinHull) {
			return ShipState.DISABLED;
		}

		return r.fuel() < b.cellsPerJump ? ShipState.GROUNDED : ShipState.READY;
	}

	/** The state a resting ship should be in after a change of fuel or hull: the hulk stays a hulk until all four sockets are done. */
	public static ShipState restingState(ShipRecord r, ShipBalance b) {
		if (r.state() == ShipState.HULK && !allSockets(r, b)) {
			return ShipState.HULK;
		}

		return settleState(r, b);
	}

	// ---------------------------------------------------------------- hulk sockets (4.2)

	public static int plates(ShipRecord r) {
		return (r.upgrades() & PLATES_MASK) >>> PLATES_SHIFT;
	}

	/** The plates the plating socket needs, as configured (at most {@link #MAX_PLATES}). */
	public static int platesNeeded(ShipBalance b) {
		return Mth.clamp(b.hullPlates, 1, MAX_PLATES);
	}

	public static boolean socketDone(ShipRecord r, int socket, ShipBalance b) {
		return switch (socket) {
			case SOCKET_HULL -> plates(r) >= platesNeeded(b);
			case SOCKET_CORE_INDEX -> (r.upgrades() & SOCKET_CORE) != 0;
			case SOCKET_MOTIVATOR_INDEX -> (r.upgrades() & SOCKET_MOTIVATOR) != 0;
			case SOCKET_NAV_INDEX -> (r.upgrades() & SOCKET_NAV) != 0;
			default -> false;
		};
	}

	public static boolean allSockets(ShipRecord r, ShipBalance b) {
		for (int socket = 0; socket < SOCKETS; socket++) {
			if (!socketDone(r, socket, b)) {
				return false;
			}
		}

		return true;
	}

	/** How many parts fitting {@code socket} takes from {@code available} matching items (0 when it is done or nothing is offered). */
	public static int partsTaken(ShipRecord r, int socket, int available, ShipBalance b) {
		if (r.state() != ShipState.HULK || available <= 0 || socketDone(r, socket, b) || socket < 0 || socket >= SOCKETS) {
			return 0;
		}

		return socket == SOCKET_HULL ? Math.min(available, platesNeeded(b) - plates(r)) : 1;
	}

	/**
	 * The record after {@code taken} parts went into {@code socket}. When this completes the fourth
	 * socket, the hulk stands up: hull 100 and the resting state ({@code GROUNDED}, or
	 * {@code READY} with fuel in the tank).
	 */
	public static ShipRecord fitSocket(ShipRecord r, int socket, int taken, ShipBalance b) {
		if (taken <= 0) {
			return r;
		}

		int upgrades = r.upgrades();

		switch (socket) {
			case SOCKET_HULL -> {
				int plates = Math.min(platesNeeded(b), plates(r) + taken);
				upgrades = (upgrades & ~PLATES_MASK) | (plates << PLATES_SHIFT);
			}
			case SOCKET_CORE_INDEX -> upgrades |= SOCKET_CORE;
			case SOCKET_MOTIVATOR_INDEX -> upgrades |= SOCKET_MOTIVATOR;
			case SOCKET_NAV_INDEX -> upgrades |= SOCKET_NAV;
			default -> {
				return r;
			}
		}

		ShipRecord fitted = r.withUpgrades(upgrades);

		if (!allSockets(fitted, b)) {
			return fitted;
		}

		ShipRecord repaired = fitted.withHull(100);
		return repaired.withState(settleState(repaired, b));
	}

	/** A record with every socket done (the command and test path), keeping its upgrades. */
	public static ShipRecord withAllSockets(ShipRecord r, ShipBalance b) {
		int upgrades = (r.upgrades() & ~PLATES_MASK) | SOCKET_CORE | SOCKET_MOTIVATOR | SOCKET_NAV | (platesNeeded(b) << PLATES_SHIFT);
		return r.withUpgrades(upgrades);
	}

	// ---------------------------------------------------------------- paint (4.1)

	/** The paint scheme in an upgrade mask ({@code ShipBody.PAINT_*}). */
	public static int paint(int upgrades) {
		return (upgrades & PAINT_MASK) >>> PAINT_SHIFT;
	}

	public static ShipRecord withPaint(ShipRecord r, int paint) {
		return r.withUpgrades((r.upgrades() & ~PAINT_MASK) | (Math.clamp(paint, ShipBody.PAINT_WATCH, ShipBody.PAINT_VASCH) << PAINT_SHIFT));
	}

	/** Only the upgrade bits of a mask, without sockets and paint (the Status tab, the commands). */
	public static int upgradesOnly(int upgrades) {
		return upgrades & ~NOT_UPGRADES;
	}

	// ---------------------------------------------------------------- plates, fuel and upgrades (4.3, 4.4, 4.7)

	/** Hull one plate adds: more for an Armourcraft or Armstech player at discipline level 2. */
	public static int plateRepair(boolean skilled, ShipBalance b) {
		return skilled ? b.plateRepairSkilled : b.plateRepair;
	}

	/** Whether a plate may be welded on now: a repaired ship on the ground below full hull. */
	public static boolean canPlate(ShipRecord r) {
		return r.state() != ShipState.HULK && !r.state().flying() && r.hull() < 100;
	}

	/** The record after one plate (hull up, and out of {@code DISABLED} once it is back over the minimum). */
	public static ShipRecord plate(ShipRecord r, boolean skilled, ShipBalance b) {
		ShipRecord repaired = r.withHull(r.hull() + plateRepair(skilled, b));
		return repaired.withState(restingState(repaired, b));
	}

	/** Cells the tank takes from {@code offered}: none while flying. */
	public static int fuelTaken(ShipRecord r, int offered, ShipBalance b) {
		if (r.state().flying() || offered <= 0) {
			return 0;
		}

		return Math.max(0, Math.min(offered, tankSize(r, b) - r.fuel()));
	}

	/** The record after {@code cells} went into the tank. */
	public static ShipRecord fuel(ShipRecord r, int cells, ShipBalance b) {
		ShipRecord fuelled = r.withFuel(Math.min(tankSize(r, b), r.fuel() + Math.max(0, cells)));
		return fuelled.withState(restingState(fuelled, b));
	}

	/** Whether {@code upgrade} can be fitted now: each fits once, never mid-flight and never on the hulk. */
	public static boolean canFitUpgrade(ShipRecord r, int upgrade) {
		return !r.has(upgrade) && r.state() != ShipState.HULK && !r.state().flying();
	}

	/**
	 * Whether {@code upgrade} can be taken out now. The cargo bay comes out only with its extra hold
	 * slots empty; the tank extension only when the fuel fits the smaller tank; the seat only when
	 * the crew fits without it.
	 */
	public static boolean canRemoveUpgrade(ShipRecord r, int upgrade, ShipBalance b) {
		if (!r.has(upgrade) || r.state().flying()) {
			return false;
		}

		ShipRecord without = r.withUpgrades(r.upgrades() & ~upgrade);

		if (upgrade == ShipRecord.UPGRADE_CARGO) {
			for (int slot = holdSize(without, b); slot < r.hold().size(); slot++) {
				if (!r.hold().get(slot).isEmpty()) {
					return false;
				}
			}
		}

		if (upgrade == ShipRecord.UPGRADE_TANK && r.fuel() > tankSize(without, b)) {
			return false;
		}

		return upgrade != ShipRecord.UPGRADE_SEAT || r.crew().size() <= crewLimit(without, b);
	}

	/** The record after an upgrade went in ({@code on}) or came out; the state is recomputed (the wear rule may differ). */
	public static ShipRecord withUpgrade(ShipRecord r, int upgrade, boolean on, ShipBalance b) {
		ShipRecord changed = r.withUpgrades(on ? r.upgrades() | upgrade : r.upgrades() & ~upgrade);
		return changed.state() == ShipState.HULK || changed.state().flying() ? changed : changed.withState(restingState(changed, b));
	}

	// ---------------------------------------------------------------- crew and seats (4.1, 4.9)

	/** Owner and crew may board. */
	public static boolean mayBoard(ShipRecord r, UUID player) {
		return r.owner().equals(player) || r.crew().contains(player);
	}

	/** Whether boarding is open: a repaired ship on the ground. */
	public static boolean boardable(ShipRecord r) {
		return r.state() == ShipState.READY || r.state() == ShipState.GROUNDED || r.state() == ShipState.DISABLED;
	}

	/** The crew list with {@code player} added, or null when it cannot be (the owner, already listed, or no room). */
	public static @Nullable List<UUID> addCrew(ShipRecord r, UUID player, ShipBalance b) {
		if (r.owner().equals(player) || r.crew().contains(player) || r.crew().size() >= crewLimit(r, b)) {
			return null;
		}

		List<UUID> crew = new ArrayList<>(r.crew());
		crew.add(player);
		return crew;
	}

	public static List<UUID> removeCrew(ShipRecord r, UUID player) {
		List<UUID> crew = new ArrayList<>(r.crew());
		crew.remove(player);
		return crew;
	}

	// ---------------------------------------------------------------- pads (4.5)

	/**
	 * The first bay, in order, with no other ship within {@code radius} blocks of it horizontally;
	 * -1 when every bay is taken.
	 */
	public static int firstFreeBay(List<BlockPos> bays, Collection<BlockPos> taken, double radius) {
		for (int i = 0; i < bays.size(); i++) {
			if (!occupied(bays.get(i), taken, radius)) {
				return i;
			}
		}

		return -1;
	}

	/** Whether any of {@code taken} stands within {@code radius} blocks of {@code bay}, horizontally. */
	public static boolean occupied(BlockPos bay, Collection<BlockPos> taken, double radius) {
		for (BlockPos other : taken) {
			double dx = other.getX() - bay.getX();
			double dz = other.getZ() - bay.getZ();

			if (dx * dx + dz * dz <= radius * radius) {
				return true;
			}
		}

		return false;
	}

	/** Whether a bay claim made at {@code since} has lapsed at {@code now} (4.5: one in-game day unattended). */
	public static boolean bayLapsed(long since, long now, ShipBalance.Travel t) {
		return now - since >= t.bayLapseTicks;
	}

	// ---------------------------------------------------------------- recall and tow (4.8)

	/**
	 * The recall rule. A ship flies itself to the pad when it is not flying, nobody is aboard, it is
	 * not already there, and the owner pays a cell ({@code hasCell}) unless the recall is free. A
	 * {@code DISABLED} ship is towed instead: free, once per day ({@code towDay}).
	 *
	 * @param free      the hub host's daily favour: no cell
	 * @param recallDay the day of the last free recall (only read when {@code free})
	 */
	public static Recall recall(@Nullable ShipRecord r, boolean enabled, boolean aboard, boolean atPad, boolean hasCell, boolean free,
			long today, long recallDay, long towDay, ShipBalance.Travel t) {
		if (r == null) {
			return Recall.NO_SHIP;
		}

		if (r.state() == ShipState.HULK) {
			return Recall.HULK;
		}

		if (r.state().flying() || r.flight().isPresent()) {
			return Recall.FLYING;
		}

		if (!enabled) {
			return Recall.DISABLED_BY_CONFIG;
		}

		if (aboard) {
			return Recall.ABOARD;
		}

		if (atPad) {
			return Recall.ALREADY_HERE;
		}

		if (r.state() == ShipState.DISABLED) {
			return t.freeTowPerDay > 0 && towDay != today ? Recall.TOW : Recall.TOW_USED;
		}

		if (free) {
			return t.freeRecallPerDay > 0 && recallDay != today ? Recall.RECALL : Recall.FREE_USED;
		}

		return hasCell ? Recall.RECALL : Recall.NO_CELL;
	}

	/** The record a recall delivers: a tow arrives at the tow hull with no jump cost; a paid recall pays the normal wear. */
	public static ShipRecord afterRecall(ShipRecord r, boolean tow, ShipBalance b) {
		if (tow) {
			ShipRecord towed = r.withHull(Math.max(r.hull(), b.jumpMinHull));
			return towed.withState(settleState(towed, b));
		}

		ShipRecord flown = r.withHull(r.hull() - wear(r, b));
		return flown.withState(settleState(flown, b));
	}

	// ---------------------------------------------------------------- restart recovery (4.9)

	/** Where a ship found mid-flight at load goes: after the jump (phase 3 on) to its destination, before it back to its origin. */
	public static Recovery recovery(ShipRecord r) {
		if (r.flight().isEmpty()) {
			return Recovery.NONE;
		}

		return r.flight().get().phase() >= PHASE_HYPERSPACE ? Recovery.DESTINATION : Recovery.ORIGIN;
	}

	/** The record once recovered: no flight, and the state as at Settle (nothing more is taken; the jump already paid). */
	public static ShipRecord recovered(ShipRecord r, ShipBalance b) {
		ShipRecord landed = r.withFlight(Optional.empty());
		return landed.withState(settleState(landed, b));
	}

	// ---------------------------------------------------------------- flight path (4.6)

	/** Height above the pad during the climb, {@code t} from 0 to 1: an ease-in. */
	public static double climbHeight(double t, double height) {
		double u = Mth.clamp(t, 0, 1);
		return height * u * u;
	}

	/** Height above the pad during the descent, {@code t} from 0 to 1: an ease-out from {@code height} to 0. */
	public static double descentHeight(double t, double height) {
		double u = 1 - Mth.clamp(t, 0, 1);
		return height * u * u;
	}

	// ---------------------------------------------------------------- hull code (4.1)

	/**
	 * The hull code painted on the nose: the owner's initials (the first two letters of the name,
	 * upper case) and a number from 100 to 999 seeded by the owner's UUID.
	 */
	public static String hullCode(String name, UUID owner) {
		StringBuilder initials = new StringBuilder();

		for (int i = 0; i < name.length() && initials.length() < 2; i++) {
			char c = Character.toUpperCase(name.charAt(i));

			if (c >= 'A' && c <= 'Z') {
				initials.append(c);
			}
		}

		while (initials.length() < 2) {
			initials.append('X');
		}

		RandomSource random = RandomSource.create(owner.getMostSignificantBits() ^ owner.getLeastSignificantBits());
		return initials + "-" + (100 + random.nextInt(900));
	}

	/** Packs a hull code into an int for synced entity data (two letters, five bits each, and a number up to 1023). */
	public static int packHullCode(String code) {
		int a = code.length() > 0 ? letter(code.charAt(0)) : 0;
		int b = code.length() > 1 ? letter(code.charAt(1)) : 0;
		int number = 0;

		for (int i = 2; i < code.length(); i++) {
			char c = code.charAt(i);

			if (c >= '0' && c <= '9') {
				number = Math.min(1023, number * 10 + (c - '0'));
			}
		}

		return a | b << 5 | number << 10;
	}

	/** The hull code of {@link #packHullCode}, or an empty string for 0. */
	public static String unpackHullCode(int packed) {
		if (packed == 0) {
			return "";
		}

		char a = (char) ('A' + Math.max(0, (packed & 31) - 1));
		char b = (char) ('A' + Math.max(0, (packed >> 5 & 31) - 1));
		return "" + a + b + "-" + (packed >> 10 & 1023);
	}

	private static int letter(char c) {
		char upper = Character.toUpperCase(c);
		return upper >= 'A' && upper <= 'Z' ? upper - 'A' + 1 : 0;
	}

	private ShipRules() {
	}
}

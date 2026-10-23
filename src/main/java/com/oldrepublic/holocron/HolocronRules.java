package com.oldrepublic.holocron;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Path;

/**
 * The pure rules of the holocron feature (spec 4.3 to 4.7): who may choose what, which holocron a
 * pedestal gives next, restores, the progression mask and the carry count. No world access, so the
 * game tests can check every case directly.
 */
public final class HolocronRules {
	/** The default skill point spacing, for callers without a balance section. */
	private static final int DEFAULT_SP_EVERY = new HolocronBalance().spEvery;

	/**
	 * The options a reader on {@code path} gets for a world holocron of {@code kind} (4.5): a Jedi
	 * reading a Sith holocron may Seal or Listen, a Sith reading a Jedi one may Break or Listen, an
	 * Independent (or a player with no path) only Listens to Jedi and Sith holocrons; everything else
	 * is the reader's own side and is simply remembered.
	 */
	public static EnumSet<HolocronChoice> allowed(Path path, HolocronKind kind) {
		if (path == Path.JEDI && kind == HolocronKind.SITH) {
			return EnumSet.of(HolocronChoice.SEAL, HolocronChoice.LISTEN);
		}

		if (path == Path.SITH && kind == HolocronKind.JEDI) {
			return EnumSet.of(HolocronChoice.BREAK, HolocronChoice.LISTEN);
		}

		if ((path == Path.INDEPENDENT || path == Path.NONE) && kind != HolocronKind.WAYFARER) {
			return EnumSet.of(HolocronChoice.LISTEN);
		}

		return EnumSet.of(HolocronChoice.OWN);
	}

	/** The choice the operator grant uses: OWN when allowed, otherwise LISTEN. */
	public static HolocronChoice defaultChoice(Path path, HolocronKind kind) {
		return allowed(path, kind).contains(HolocronChoice.OWN) ? HolocronChoice.OWN : HolocronChoice.LISTEN;
	}

	/** The next world holocron of {@code kind} in 4.2 order that {@code d} has not attuned, or null when all four are. */
	public static @Nullable String nextWorldId(HolocronData d, HolocronKind kind) {
		for (HolocronCatalog.Entry entry : HolocronCatalog.world(kind)) {
			if (!d.isAttuned(entry.id())) {
				return entry.id();
			}
		}

		return null;
	}

	/** Whether all four world holocrons of {@code kind} are attuned (the echo rule). */
	public static boolean complete(HolocronData d, HolocronKind kind) {
		return nextWorldId(d, kind) == null;
	}

	/** World holocrons attuned, of any kind (Archive ids never count). */
	public static int worldCount(HolocronData d) {
		return (int) d.attuned.stream().filter(HolocronCatalog::isWorld).count();
	}

	/** World holocrons of {@code kind} attuned. */
	public static int worldCount(HolocronData d, HolocronKind kind) {
		return (int) HolocronCatalog.world(kind).stream().filter(e -> d.isAttuned(e.id())).count();
	}

	/** Whether a restore is still free on day index {@code day} (one per day, 4.3). */
	public static boolean mayRestore(HolocronData d, long day) {
		return d.restoreDay != day;
	}

	/**
	 * The holocron a sneak-use at a pedestal of {@code kind} re-issues, or null (4.3): the first id in
	 * 4.2 order, Archive ids last, that is attuned, not returned, not broken, kept if it is an Archive
	 * id, and not already held by the player as a current copy ({@code heldCurrent}), so a player who
	 * still carries one holocron can get back the one they lost.
	 */
	public static @Nullable String restoreCandidate(HolocronData d, HolocronKind kind, Set<String> heldCurrent) {
		for (HolocronCatalog.Entry entry : HolocronCatalog.world(kind)) {
			if (restorable(d, entry.id()) && !heldCurrent.contains(entry.id())) {
				return entry.id();
			}
		}

		if (d.archiveChoice() == ArchiveChoice.KEEP) {
			for (HolocronCatalog.Entry entry : HolocronCatalog.archive()) {
				if (entry.kind() == kind && restorable(d, entry.id()) && !heldCurrent.contains(entry.id())) {
					return entry.id();
				}
			}
		}

		return null;
	}

	/** Attuned, never returned and not broken. */
	public static boolean restorable(HolocronData d, String id) {
		return d.isAttuned(id) && !d.returned.contains(id) && d.choice(id) != HolocronChoice.BREAK;
	}

	/** The mask bits due at {@code attunedWorld} world holocrons, with the default skill point spacing. */
	public static int milestoneBits(int attunedWorld) {
		return milestoneBits(attunedWorld, DEFAULT_SP_EVERY);
	}

	/**
	 * The mask bits due at {@code attunedWorld} world holocrons (4.7): {@link HolocronData#BIT_3} to
	 * {@link HolocronData#BIT_12} at 3, 6, 9 and 12, and one drip bit for every {@code spEvery}
	 * holocrons, up to {@link HolocronData#DRIP_BITS}. Bits already in the mask are granted already.
	 */
	public static int milestoneBits(int attunedWorld, int spEvery) {
		int bits = 0;

		if (attunedWorld >= 3) {
			bits |= HolocronData.BIT_3;
		}

		if (attunedWorld >= 6) {
			bits |= HolocronData.BIT_6;
		}

		if (attunedWorld >= 9) {
			bits |= HolocronData.BIT_9;
		}

		if (attunedWorld >= 12) {
			bits |= HolocronData.BIT_12;
		}

		int every = Math.max(1, spEvery);

		for (int i = 1; i <= HolocronData.DRIP_BITS && i * every <= attunedWorld; i++) {
			bits |= 1 << (HolocronData.DRIP_SHIFT + i - 1);
		}

		return bits;
	}

	/** The skill points the drip bits in {@code freshBits} are worth (one each). */
	public static int dripPoints(int freshBits) {
		return Integer.bitCount((freshBits >>> HolocronData.DRIP_SHIFT) & ((1 << HolocronData.DRIP_BITS) - 1));
	}

	/** Whether a carried holocron of {@code kind} counts toward the carry bonus on {@code path} (4.6). */
	public static boolean carryCounts(Path path, HolocronKind kind) {
		return switch (path) {
			case JEDI -> kind != HolocronKind.SITH;
			case SITH -> kind != HolocronKind.JEDI;
			default -> true;
		};
	}

	/**
	 * Distinct holocrons in {@code carried} that count toward the carry bonus (4.6): owned by
	 * {@code owner}, carrying the current serial, of a kind {@code path} draws on; at most {@code max}.
	 */
	public static int carryCount(List<HolocronStamp> carried, UUID owner, Map<String, Integer> serials, Path path, int max) {
		Set<String> counted = new HashSet<>();

		for (HolocronStamp stamp : carried) {
			HolocronKind kind = HolocronCatalog.kindOf(stamp.id());
			Integer current = serials.get(stamp.id());

			if (kind != null && stamp.owner().equals(owner) && current != null && current > 0 && stamp.serial() == current
					&& carryCounts(path, kind)) {
				counted.add(stamp.id());
			}
		}

		return Math.min(Math.max(0, max), counted.size());
	}

	private HolocronRules() {
	}
}

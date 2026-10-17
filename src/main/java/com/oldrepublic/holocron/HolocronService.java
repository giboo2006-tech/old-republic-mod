package com.oldrepublic.holocron;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The holocron feature's entry points for other features (spec section 4): pedestals, attunement
 * state, returning holocrons and the Archive. Signatures are frozen (spec section 15.1); the
 * additions below them are frozen for Force Sense and the story (W3 plan, 4.2).
 */
public final class HolocronService {
	/**
	 * The player used a holocron pedestal of {@code kind} at {@code pos}; sneaking restores a lost
	 * holocron instead (4.3). A {@link HolocronEvents#BEFORE_PEDESTAL} listener may take the use over.
	 */
	public static void usePedestal(ServerPlayer p, BlockPos pos, HolocronKind kind) {
		ServerLevel level = p.level();
		PedestalSite site = PedestalSite.resolve(level, pos);

		if (HolocronEvents.BEFORE_PEDESTAL.invoker().intercept(p, site.pos(), kind, site.site())) {
			return;
		}

		long day = level.getGameTime() / Math.max(1, HolocronBalance.get().restoreCooldownTicks);
		HolocronPedestals.use(p, site, kind, p.isShiftKeyDown(), false, day, level.getRandom());
	}

	/** Whether the player has attuned this holocron. Works on both sides (the data is synced to its owner). */
	public static boolean hasAttuned(Player p, String holocronId) {
		HolocronData data = view(p);
		return data != null && data.isAttuned(holocronId);
	}

	/**
	 * World holocrons the player has attuned, of {@code kind} or of any kind when null; Archive
	 * holocrons never count.
	 */
	public static int attunedCount(Player p, @Nullable HolocronKind kind) {
		HolocronData data = view(p);

		if (data == null) {
			return 0;
		}

		return kind == null ? HolocronRules.worldCount(data) : HolocronRules.worldCount(data, kind);
	}

	/**
	 * True for a site whose pedestal already gave the player a holocron: the exclude filter for
	 * {@code StructureLocator.find}.
	 */
	public static Predicate<SitePiece> usedSites(Player p) {
		HolocronData data = snapshot(p);
		return piece -> data.usedSite(piece.siteKey(), null);
	}

	/**
	 * Returns a carried holocron to {@code receiver} ({@code jedi}, {@code sith}, {@code republic} or
	 * {@code imperial}); true when one was returned.
	 */
	public static boolean returnTo(ServerPlayer p, String receiver) {
		return HolocronReturns.returnTo(p, receiver);
	}

	/** Opens the Archive hoard choice at the plinth (4.8). */
	public static void openArchive(ServerPlayer p, BlockPos plinth) {
		HolocronArchive.open(p, plinth);
	}

	/** Applies the player's Archive choice (4.8). */
	public static void resolveArchive(ServerPlayer p, ArchiveChoice choice) {
		HolocronArchive.resolve(p, choice);
	}

	/** Marks the Archive choice as owed after the boss is resolved (4.8). */
	public static void markArchivePending(ServerPlayer p) {
		HolocronArchive.markPending(p);
	}

	// ---------------------------------------------------------------- additions (W3 plan 4.2)

	/**
	 * Site kinds that may still hold a world holocron for p: own path's kind first, then Wayfarer,
	 * then the opposed kind; each SiteKind once; ECHO_SHRINE last. Empty when all 12 world holocrons
	 * are attuned. Courier's Fall is searched only by an Independent who has not attuned the Compass.
	 */
	public static List<SiteKind> senseSearchKinds(Player p) {
		HolocronData data = snapshot(p);
		Path path = Identity.path(p);
		Set<SiteKind> kinds = new LinkedHashSet<>();

		for (HolocronKind kind : kindOrder(path)) {
			if (HolocronRules.complete(data, kind)) {
				continue;
			}

			switch (kind) {
				case JEDI -> kinds.add(SiteKind.JEDI_RUIN);
				case SITH -> kinds.add(SiteKind.SITH_TOMB);
				case WAYFARER -> {
					if (path == Path.INDEPENDENT && !data.isAttuned(HolocronCatalog.WAYFARERS_COMPASS)) {
						kinds.add(SiteKind.COURIERS_FALL);
					}

					kinds.add(SiteKind.WRECK_FIELD);
					kinds.add(SiteKind.JEDI_RUIN);
					kinds.add(SiteKind.SITH_TOMB);
				}
			}
		}

		if (!kinds.isEmpty()) {
			kinds.add(SiteKind.ECHO_SHRINE);
		}

		return List.copyOf(kinds);
	}

	/**
	 * True for a site none of whose pedestals can still give p a world holocron (every pedestal kind
	 * the site holds is complete for p, or its siteKey is already in attuned_at for that kind).
	 * JEDI_RUIN holds JEDI+WAYFARER, SITH_TOMB holds SITH+WAYFARER, WRECK_FIELD and COURIERS_FALL hold
	 * WAYFARER, ECHO_SHRINE holds an unknown kind (exhausted once its siteKey is used at all). Any
	 * other site kind holds no pedestal and is always exhausted.
	 */
	public static Predicate<SitePiece> exhaustedSites(Player p) {
		HolocronData data = snapshot(p);
		return piece -> exhausted(data, piece);
	}

	/**
	 * The kind whose holocron p would get at this site (the Sense pulse tint): the first not-complete
	 * pedestal kind of the site in senseSearchKinds order; WAYFARER for unknown.
	 */
	public static HolocronKind senseKind(Player p, SitePiece site) {
		HolocronData data = snapshot(p);
		Set<HolocronKind> held = siteKinds(site.kind());

		for (HolocronKind kind : kindOrder(Identity.path(p))) {
			if (held.contains(kind) && !HolocronRules.complete(data, kind) && !data.usedSite(site.siteKey(), kind)) {
				return kind;
			}
		}

		return HolocronKind.WAYFARER;
	}

	/** Same as usedSites(p), restricted to pedestals of kind. */
	public static Predicate<SitePiece> usedSites(Player p, HolocronKind kind) {
		HolocronData data = snapshot(p);
		return piece -> data.usedSite(piece.siteKey(), kind);
	}

	/** The holocron id returnTo(p, receiver) would take now, or null (W2 names it in the confirm line). */
	public static @Nullable String returnCandidate(Player p, String receiver) {
		return HolocronReturns.candidate(p, receiver);
	}

	/**
	 * The player's data as a mutable copy (server). Change it, then {@link #commit} it; read it again
	 * after a commit instead of keeping the copy.
	 */
	public static HolocronData data(ServerPlayer p) {
		return p.getAttachedOrCreate(HolocronAttachments.HOLOCRONS).copy();
	}

	/** Seals a copy of {@code d}, attaches it and syncs it to the owner. */
	public static void commit(ServerPlayer p, HolocronData d) {
		p.setAttached(HolocronAttachments.HOLOCRONS, d.copy().seal());
	}

	/** The synced read-only view (client or server), or null. Never mutate. */
	public static @Nullable HolocronData view(Player p) {
		return p.getAttached(HolocronAttachments.HOLOCRONS);
	}

	// ---------------------------------------------------------------- helpers

	/** A read-only snapshot for predicates that outlive this call (the locator runs them later). */
	private static HolocronData snapshot(Player p) {
		HolocronData data = view(p);
		return data == null ? new HolocronData() : data.copy();
	}

	/** Holocron kinds in the order a player on {@code path} looks for them: own, Wayfarer, opposed. */
	static List<HolocronKind> kindOrder(Path path) {
		return switch (path) {
			case JEDI -> List.of(HolocronKind.JEDI, HolocronKind.WAYFARER, HolocronKind.SITH);
			case SITH -> List.of(HolocronKind.SITH, HolocronKind.WAYFARER, HolocronKind.JEDI);
			default -> List.of(HolocronKind.WAYFARER, HolocronKind.JEDI, HolocronKind.SITH);
		};
	}

	/** The pedestal kinds a site kind holds; empty for an Echo Shrine (unknown) or a site without pedestals. */
	static Set<HolocronKind> siteKinds(SiteKind kind) {
		return switch (kind) {
			case JEDI_RUIN -> EnumSet.of(HolocronKind.JEDI, HolocronKind.WAYFARER);
			case SITH_TOMB -> EnumSet.of(HolocronKind.SITH, HolocronKind.WAYFARER);
			case WRECK_FIELD, COURIERS_FALL -> EnumSet.of(HolocronKind.WAYFARER);
			default -> EnumSet.noneOf(HolocronKind.class);
		};
	}

	private static boolean exhausted(HolocronData data, SitePiece piece) {
		return switch (piece.kind()) {
			case ECHO_SHRINE -> data.usedSite(piece.siteKey(), null) || HolocronRules.worldCount(data) >= HolocronCatalog.WORLD_COUNT;
			case COURIERS_FALL -> data.isAttuned(HolocronCatalog.WAYFARERS_COMPASS) || data.usedSite(piece.siteKey(), null);
			default -> siteKinds(piece.kind()).stream()
					.allMatch(kind -> HolocronRules.complete(data, kind) || data.usedSite(piece.siteKey(), kind));
		};
	}

	private HolocronService() {
	}
}

package com.oldrepublic.holocron;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The holocron feature's entry points for other features (spec section 4): pedestals, attunement
 * state, returning holocrons and the Archive. Signatures are frozen (spec section 15.1).
 */
public final class HolocronService {
	/**
	 * The player used a holocron pedestal of {@code kind} at {@code pos}; sneaking restores a lost
	 * holocron instead (4.3). Phase 0 stub: W3 fills this in.
	 */
	public static void usePedestal(ServerPlayer p, BlockPos pos, HolocronKind kind) {
	}

	/** Whether the player has attuned this holocron. Phase 0 stub: W3 fills this in. */
	public static boolean hasAttuned(Player p, String holocronId) {
		return false;
	}

	/**
	 * World holocrons the player has attuned, of {@code kind} or of any kind when null; Archive
	 * holocrons never count. Phase 0 stub: W3 fills this in.
	 */
	public static int attunedCount(Player p, @Nullable HolocronKind kind) {
		return 0;
	}

	/**
	 * True for a site whose pedestal already gave the player a holocron: the exclude filter for
	 * {@code StructureLocator.find}. Phase 0 stub: W3 fills this in.
	 */
	public static Predicate<SitePiece> usedSites(Player p) {
		return piece -> false;
	}

	/**
	 * Returns a carried holocron to {@code receiver} ({@code jedi}, {@code sith}, {@code republic} or
	 * {@code imperial}); true when one was returned. Phase 0 stub: W3 fills this in.
	 */
	public static boolean returnTo(ServerPlayer p, String receiver) {
		return false;
	}

	/** Opens the Archive hoard choice at the plinth (4.8). Phase 0 stub: W3 fills this in. */
	public static void openArchive(ServerPlayer p, BlockPos plinth) {
	}

	/** Applies the player's Archive choice (4.8). Phase 0 stub: W3 fills this in. */
	public static void resolveArchive(ServerPlayer p, ArchiveChoice choice) {
	}

	/** Marks the Archive choice as owed after the boss is resolved (4.8). Phase 0 stub: W3 fills this in. */
	public static void markArchivePending(ServerPlayer p) {
	}

	private HolocronService() {
	}
}

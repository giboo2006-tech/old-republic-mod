package com.oldrepublic.world.puzzle;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Puzzle moments other features hear about (spec 3.3, 13.3). Server side. */
public final class PuzzleEvents {
	/** A veiled group opened. */
	public static final String VEIL = "veil";
	/** An urn keeper was solved. */
	public static final String URNS = "urns";
	/** A puzzle seal opened. */
	public static final String SEAL = "seal";
	/** Collapsed rubble cleared. */
	public static final String RUBBLE = "rubble";
	/** A holocron pedestal became ready. */
	public static final String PEDESTAL = "pedestal";

	/**
	 * A puzzle at {@code pos} was solved: every player near (within {@code sites.puzzleResetRange},
	 * not spectating) hears the resolving chord, and, when the block lies in a site piece, the quest
	 * hook hears it for each of them. Every visitor solves the puzzle for themselves, so the hook
	 * runs once per player present.
	 */
	public static void solved(ServerLevel level, BlockPos pos, String puzzleId) {
		SitePiece piece = StructureLocator.pieceAt(level, pos);
		Vec3 centre = Vec3.atCenterOf(pos);

		for (ServerPlayer player : PuzzleWorld.playersNear(level, pos, SiteBalance.get().puzzleResetRange)) {
			EffectEvents.toPlayer(player, PuzzleEffects.PUZZLE_SOLVED, centre, null, -1, 1f, 0);

			if (piece != null) {
				QuestHooks.onPuzzleSolved(player, piece, puzzleId);
			}
		}
	}

	private PuzzleEvents() {
	}
}

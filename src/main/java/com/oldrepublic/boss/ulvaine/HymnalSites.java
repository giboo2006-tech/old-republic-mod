package com.oldrepublic.boss.ulvaine;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Finds the Hymnal's halls piece around a position (spec MILESTONE_5.md 3.3.3: W3's {@code site/hymnal},
 * sunk under its arch, one per server) and reads its anchors. The halls piece is the Hymnal piece that
 * has a {@code HEART} anchor; the arch piece has none.
 */
public final class HymnalSites {
	/** The Hymnal halls piece containing {@code pos} in {@code level}, or null (no Hymnal here, or only its arch). */
	public static @Nullable SitePiece hallsAt(ServerLevel level, BlockPos pos) {
		SitePiece piece = StructureLocator.runtimePieceAt(level, pos, SiteKind.HYMNAL);
		return piece != null && SiteAnchors.find(piece, SiteAnchors.Anchor.HEART) != null ? piece : null;
	}

	/** The first position of {@code anchor} in {@code piece}, or {@code fallback}. */
	static BlockPos anchorOr(@Nullable SitePiece piece, SiteAnchors.Anchor anchor, BlockPos fallback) {
		BlockPos at = piece == null ? null : SiteAnchors.find(piece, anchor);
		return at != null ? at : fallback;
	}

	/** The first position of {@code anchor} in {@code piece}, or null. */
	static @Nullable BlockPos anchor(@Nullable SitePiece piece, SiteAnchors.Anchor anchor) {
		return piece == null ? null : SiteAnchors.find(piece, anchor);
	}

	private HymnalSites() {
	}
}

package com.oldrepublic.boss;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Placing the Reliquary, the Collector's sunken hall (spec sections 3.6 and 8.2). Signatures are
 * frozen (spec section 15.1).
 */
public final class ReliquarySites {
	/**
	 * Places the owner's Reliquary on the balance rings ahead of {@code origin} along {@code forward},
	 * adds a {@code RELIQUARY} site record with the owner and returns the heart position, or null
	 * when no spot was found (the caller retries later). Phase 0 stub: W5 fills this in.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, Vec3 origin, Vec3 forward) {
		return null;
	}

	/**
	 * The pure placement check of 3.6 for a candidate hall and shell: null when accepted, otherwise
	 * the reason it was refused. Phase 0 stub: W5 fills this in.
	 */
	public static @Nullable String evaluate(ServerLevel l, BoundingBox hallAndShell, Predicate<BlockPos> isWorldgenPiece) {
		return null;
	}

	private ReliquarySites() {
	}
}

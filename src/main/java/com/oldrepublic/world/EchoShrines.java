package com.oldrepublic.world;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.quest.story.MeetingSites;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Echo Shrines (spec 3.4 and 6.3): a small half-buried shrine of one holocron kind, placed for its
 * owner when no worldgen site of that kind is near. Signatures are frozen (spec 15.1).
 */
public final class EchoShrines {
	/**
	 * Places an Echo Shrine of {@code kind} for {@code owner} on the balance rings and returns its
	 * pedestal position, or null when no spot was found (the caller retries later).
	 * Phase 0 stub: W1 fills this in.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind) {
		return null;
	}

	/**
	 * {@link #place(ServerPlayer, HolocronKind)} with injected rings, fallback rings and rules (the
	 * test seam). Phase 0 stub: W1 fills this in.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind, int[] rings, int[] fallback, MeetingSites.Rules rules) {
		return null;
	}

	private EchoShrines() {
	}
}

package com.oldrepublic.identity;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The player's identity: alignment, faction reputation, path, rank, mentor trust and recorded
 * choices. Readers work on both sides (the attachment is synced to its owner); writers are server
 * only and validate, cap, sync and give feedback in one place.
 *
 * <p>Phase 0 stub: nothing is stored yet and every reader returns a neutral default. The identity
 * feature fills in the bodies; the signatures are a frozen contract.
 */
public final class Identity {
	/** -100 (dark) to +100 (light). */
	public static int alignment(Player p) {
		return 0;
	}

	public static AlignmentBand band(Player p) {
		return AlignmentBand.of(alignment(p));
	}

	/** Applies a shift. Returns false if it was ignored (a repeated major source or a cap). */
	public static boolean shift(ServerPlayer p, AlignmentShift shift) {
		return false;
	}

	/** Reports a contextual act; the identity feature decides the amount and applies the caps. */
	public static void contextual(ServerPlayer p, ContextAct act, @Nullable Entity target) {
	}

	/** -1000 to +1000. */
	public static int reputation(Player p, Faction f) {
		return 0;
	}

	/**
	 * Changes reputation with a faction.
	 *
	 * @param oneShot when true, the change applies only once per {@code sourceId}
	 */
	public static void changeReputation(ServerPlayer p, Faction f, int amount, String sourceId, boolean oneShot) {
	}

	public static Path path(Player p) {
		return Path.NONE;
	}

	/**
	 * Sets the path. Refuses changes the rules do not allow (JEDI to SITH and back) unless
	 * {@code force} is set (operator command). Returns whether the path changed.
	 */
	public static boolean setPath(ServerPlayer p, Path path, boolean force) {
		return false;
	}

	/** 0 = none, 1 = Initiate / Acolyte, 2 = Padawan / Apprentice. */
	public static int rank(Player p) {
		return 0;
	}

	public static void promote(ServerPlayer p, int rank) {
	}

	/** Trust with a mentor, 0 to 100. {@code mentorId} is {@code jedi} or {@code sith}. */
	public static int trust(Player p, String mentorId) {
		return 0;
	}

	public static void addTrust(ServerPlayer p, String mentorId, int amount, String reasonKey) {
	}

	public static boolean hasChoice(Player p, String choiceId) {
		return false;
	}

	public static void recordChoice(ServerPlayer p, String choiceId) {
	}

	private Identity() {
	}
}

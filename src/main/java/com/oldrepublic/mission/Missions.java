package com.oldrepublic.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The mission feature's entry points for other features (spec section 5): the bounty board, the
 * active mission and the own-side contract count toward rank 3. Signatures are frozen (spec
 * section 15.1).
 */
public final class Missions {
	/**
	 * Opens the bounty board of the terminal at {@code terminal}, issued by {@code issuer}
	 * ({@link Faction#REPUBLIC} or {@link Faction#SITH_EMPIRE}). Phase 0 stub: W2 fills this in.
	 */
	public static void openBoard(ServerPlayer p, BlockPos terminal, Faction issuer) {
	}

	/** The id of the player's active mission, or null for none. Phase 0 stub: W2 fills this in. */
	public static @Nullable String activeId(Player p) {
		return null;
	}

	/** Contracts completed for the player's own side, toward rank 3 (5.8). Phase 0 stub: W2 fills this in. */
	public static int ownSideContracts(Player p) {
		return 0;
	}

	private Missions() {
	}
}

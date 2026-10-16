package com.oldrepublic.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read and grant access to the skill trees for other features. Readers work on both sides (the
 * skills attachment is synced to its owner).
 *
 * <p>Phase 0 stub: no nodes are owned, no form is active and three slots are open.
 */
public final class Skills {
	private static final int BASE_SLOTS = 3;

	/** Owned rank of a node, 0 if not owned. */
	public static int rank(Player p, String nodeId) {
		return 0;
	}

	/** Owned and not dormant. */
	public static boolean active(Player p, String nodeId) {
		return false;
	}

	/** Node id of the active saber form, or {@code ""} for Unformed. */
	public static String activeForm(Player p) {
		return "";
	}

	/** Sets the active form (the saber feature validates timing first). Returns whether it changed. */
	public static boolean setActiveForm(ServerPlayer p, String formNodeId) {
		return false;
	}

	/** Grants a node at no cost (lessons, trials). Never refunds a node already bought. */
	public static void grant(ServerPlayer p, String nodeId) {
	}

	/** Number of open ability slots (3, or 4 after the first rank). */
	public static int slots(Player p) {
		return BASE_SLOTS;
	}

	private Skills() {
	}
}

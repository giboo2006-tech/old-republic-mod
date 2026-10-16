package com.oldrepublic.saber.duel;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Duels with no death risk (lessons, trials, daily sessions). Sessions live in memory only.
 *
 * <p>Phase 0 stub: no spar can start.
 */
public final class Spar {
	/**
	 * Starts a spar between the player and {@code partner}. {@code onEnd} runs exactly once with the
	 * result.
	 *
	 * @return false if the spar could not start (either side already sparring, partner invalid)
	 */
	public static boolean start(ServerPlayer p, Mob partner, SparRules rules, Consumer<SparResult> onEnd) {
		return false;
	}

	public static boolean inSpar(Player p) {
		return false;
	}

	private Spar() {
	}
}

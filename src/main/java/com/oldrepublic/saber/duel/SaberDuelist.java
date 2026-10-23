package com.oldrepublic.saber.duel;

import com.oldrepublic.saber.SaberToggle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * An NPC that fights with a lightsaber: it guards, parries and attacks in strings, and the player's
 * saber combat treats its blade like a player's. Its fight is run by a {@link DuelistCombatGoal},
 * which also registers its guard with {@link NpcGuard}.
 */
public interface SaberDuelist {
	GuardState guardState();

	/** The lightsaber it holds (built with {@code SaberAssembly}). */
	ItemStack duelSaber();

	/**
	 * Stores the guard state. Implementations keep it in synced entity data so clients can pose the
	 * blade; the default ignores it (the NPC then always shows {@link #guardState()}).
	 */
	default void setGuardState(GuardState state) {
	}

	/**
	 * A spar with this duelist as the partner ended. On {@link SparResult#WON} (the player won) the
	 * duelist should kneel in the yield pose. Server side.
	 */
	default void onSparEnd(ServerPlayer player, SparResult result) {
	}

	/**
	 * How far off this duelist's facing a shooter may stand for it to deflect the shooter's bolts
	 * back (spec 10.7, "faces the shooter within 70°"): degrees either side of its facing, read like
	 * {@link #deflectSpreadDegrees}. Only used when its profile {@code deflects()}.
	 */
	default float deflectConeDegrees() {
		return SaberToggle.balance().npcDeflectConeDegrees;
	}

	/** How far a deflected bolt may stray from the shooter's chest, in degrees either side (spec 10.7). */
	default float deflectSpreadDegrees() {
		return SaberToggle.balance().npcDeflectSpreadDegrees;
	}
}

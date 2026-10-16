package com.oldrepublic.saber;

import net.minecraft.server.level.ServerPlayer;

/**
 * A creature without a saber guard of its own that reacts when a player's saber parries its melee
 * strike (for example the Poacher Captain, staggered for longer after a parried overhead). The
 * usual parry knock-back and slow from {@link SaberGuard} are applied before this is called.
 */
public interface ParryReactive {
	void onParried(ServerPlayer defender, SaberCombat.Strike strike);
}

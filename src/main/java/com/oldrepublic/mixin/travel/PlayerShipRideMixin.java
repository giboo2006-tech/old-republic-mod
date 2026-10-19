package com.oldrepublic.mixin.travel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.oldrepublic.travel.ShipBody;

import net.minecraft.world.entity.player.Player;

/**
 * Keeps riders in their seats from the Spool to the end of the Settle (spec MILESTONE_4.md 4.6) and
 * at the helm (MILESTONE_5.md 4.1, where Sneak means "down"): sneaking does not dismount a player
 * whose ship {@link ShipBody#locksRiders() locks its riders}. Runs on both sides (the synced phase
 * and state), so the client does not predict a dismount the server refuses. On the ground sneaking
 * leaves the ship as usual.
 */
@Mixin(Player.class)
abstract class PlayerShipRideMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void oldrepublic$stayAboardInFlight(CallbackInfoReturnable<Boolean> info) {
		if (((Player) (Object) this).getVehicle() instanceof ShipBody ship && ship.locksRiders()) {
			info.setReturnValue(false);
		}
	}
}

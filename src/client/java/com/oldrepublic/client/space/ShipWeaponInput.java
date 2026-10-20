package com.oldrepublic.client.space;

import com.oldrepublic.client.travel.Piloting;
import com.oldrepublic.space.network.ShipFirePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The ship's triggers (spec MILESTONE_5.md 5.3, 13.1): while the local player flies a ship by hand,
 * the attack button fires the cannons and the use button locks and fires a missile; in the gunner
 * seat the attack button fires the turret. Vanilla's clicks are cancelled there (W1's pilot mixin,
 * this feature's gunner mixin), so the keys are read here every client tick and only their changes
 * are sent ({@code ship_fire}, pressed or released). Opening a screen, leaving the role or the level
 * releases a held trigger. The server decides everything else.
 */
final class ShipWeaponInput {
	private static boolean primary;
	private static boolean missile;

	static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		boolean pilot = Piloting.isPiloting();
		boolean free = player != null && mc.gui.screen() == null && !player.isSpectator() && (pilot || SpaceCrew.isGunner());
		boolean wantPrimary = free && mc.options.keyAttack.isDown();
		boolean wantMissile = free && pilot && mc.options.keyUse.isDown();

		if (wantPrimary != primary) {
			primary = wantPrimary;
			send(ShipFirePayload.PRIMARY, primary);
		}

		if (wantMissile != missile) {
			missile = wantMissile;
			send(ShipFirePayload.MISSILE, missile);
		}
	}

	private static void send(int weapon, boolean pressed) {
		if (ClientPlayNetworking.canSend(ShipFirePayload.TYPE)) {
			ClientPlayNetworking.send(new ShipFirePayload(weapon, pressed));
		}
	}

	/** Disconnect: nothing held (the server forgets the player's triggers with them). */
	static void clear() {
		primary = false;
		missile = false;
	}

	private ShipWeaponInput() {
	}
}

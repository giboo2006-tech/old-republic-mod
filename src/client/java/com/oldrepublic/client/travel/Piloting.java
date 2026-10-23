package com.oldrepublic.client.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.travel.ShipBody;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The local player at a ship's controls (spec MILESTONE_5.md 4.2, 4.3, 5.2): whether they fly it by
 * hand, whether they man its gunner seat, and the ship. Owned by W1 flight; other client code (W2's
 * combat HUD, the camera, the key handlers) reads it.
 */
public final class Piloting {
	/**
	 * Whether the local player flies a ship by hand: they ride it, its piloted flag is set and they
	 * are its controlling passenger (its owner), so their client simulates it (4.1).
	 */
	public static boolean isPiloting() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getVehicle() instanceof ShipBody ship && ship.piloted() && ship.getControllingPassenger() == player;
	}

	/**
	 * Whether the local player sits in a ship's gunner seat (5.2): on a flying ship with cannons,
	 * they are its first rider who is not the owner ({@link ShipBody#gunner}).
	 */
	public static boolean isGunner() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getVehicle() instanceof ShipBody ship && ship.gunner() == player;
	}

	/** The ship the local player pilots or mans, else null. */
	public static @Nullable ShipBody ship() {
		LocalPlayer player = Minecraft.getInstance().player;
		return (isPiloting() || isGunner()) && player != null && player.getVehicle() instanceof ShipBody ship ? ship : null;
	}

	private Piloting() {
	}
}

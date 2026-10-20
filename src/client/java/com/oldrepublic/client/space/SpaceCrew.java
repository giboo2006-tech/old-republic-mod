package com.oldrepublic.client.space;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.travel.Piloting;
import com.oldrepublic.space.ShipWeapons;
import com.oldrepublic.space.network.ShipCombatStatusPayload;
import com.oldrepublic.travel.ShipBody;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The local player's place in a ship's crew for the combat HUD and the weapons (spec MILESTONE_5.md
 * 5.2, 5.10): the pilot through W1's {@link Piloting}, the gunner by the server's own rule
 * ({@link ShipWeapons#gunned}, W1's seat rule: the first rider who is not the owner, on a flying ship
 * with cannons).
 * The server decides who may fire; this only says whose HUD and clicks are the ship's.
 */
public final class SpaceCrew {
	/** The ship whose gunner seat the local player holds, or null. */
	public static @Nullable ShipBody gunned() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null || Piloting.isPiloting() ? null : ShipWeapons.gunned(player);
	}

	/** Whether the local player mans a ship's gunner seat (their clicks fire the turret, never vanilla's attack or use). */
	public static boolean isGunner() {
		return gunned() != null;
	}

	/**
	 * The combat HUD's numbers as the local player's client holds them now (the server's last
	 * {@code ship_combat_status}, {@link ShipCombatStatusPayload#OFF} once stale or without a seat):
	 * for the client game tests.
	 */
	public static ShipCombatStatusPayload status() {
		return SpaceClientState.status();
	}

	/** The ship the local player flies by hand or guns, or null. */
	static @Nullable ShipBody ship() {
		ShipBody piloted = Piloting.ship();
		return piloted != null ? piloted : gunned();
	}

	private SpaceCrew() {
	}
}

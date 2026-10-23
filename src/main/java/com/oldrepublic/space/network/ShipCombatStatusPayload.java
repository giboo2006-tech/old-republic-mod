package com.oldrepublic.space.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The combat HUD's numbers for a ship's pilot or gunner (spec MILESTONE_5.md 5.10, 18.2), sent
 * every other tick while they hold the role, and once with {@link #NONE} when it ends.
 *
 * @param role         {@link #NONE}, {@link #PILOT} or {@link #GUNNER}
 * @param shield       the ship's shield points now, and its most
 * @param cannonHeat   the cannons' heat (0 to 100) and whether an overheat locks them
 * @param turretHeat   the turret's heat and lock (the gunner's)
 * @param missiles     concussion missiles in the hold, or -1 without the {@code MISSILES} upgrade
 * @param lockTarget   the network id of the entity the lock is on, or -1
 * @param lockProgress the lock's progress, 0 to 1 (1: locked)
 * @param incoming     a missile is locking on or flying at the ship
 * @param scan         the scan meter, 0 to 1, or -1 when no patrol scans the ship
 * @param boltSpeed    the bolts' speed (blocks per tick, {@code space.boltSpeed}), for the HUD's lead pip
 */
public record ShipCombatStatusPayload(int role, float shield, float shieldMax, float cannonHeat, boolean cannonLocked, float turretHeat,
		boolean turretLocked, int missiles, int lockTarget, float lockProgress, boolean incoming, float scan, float boltSpeed)
		implements CustomPacketPayload {
	public static final int NONE = 0;
	public static final int PILOT = 1;
	public static final int GUNNER = 2;
	/** What the HUD shows when nobody holds a role. */
	public static final ShipCombatStatusPayload OFF = new ShipCombatStatusPayload(NONE, 0f, 0f, 0f, false, 0f, false, -1, -1, 0f, false, -1f, 4f);

	public static final Type<ShipCombatStatusPayload> TYPE = new Type<>(OldRepublicMod.id("ship_combat_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShipCombatStatusPayload> CODEC = StreamCodec.of(ShipCombatStatusPayload::write,
			ShipCombatStatusPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, ShipCombatStatusPayload p) {
		buf.writeVarInt(p.role);
		buf.writeFloat(p.shield);
		buf.writeFloat(p.shieldMax);
		buf.writeFloat(p.cannonHeat);
		buf.writeBoolean(p.cannonLocked);
		buf.writeFloat(p.turretHeat);
		buf.writeBoolean(p.turretLocked);
		buf.writeVarInt(p.missiles + 1);
		buf.writeVarInt(p.lockTarget + 1);
		buf.writeFloat(p.lockProgress);
		buf.writeBoolean(p.incoming);
		buf.writeFloat(p.scan);
		buf.writeFloat(p.boltSpeed);
	}

	private static ShipCombatStatusPayload read(RegistryFriendlyByteBuf buf) {
		return new ShipCombatStatusPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(),
				buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readFloat(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

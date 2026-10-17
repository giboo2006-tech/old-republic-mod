package com.oldrepublic.mission.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent from the board screen ({@code oldrepublic:mission_action}, spec 4.7): accept offer
 * {@code offer} of the board at {@code terminal}, or abandon the active mission there. The server
 * checks everything again (reach, the day's offers, the soft rules).
 *
 * @param action {@link #ACCEPT} or {@link #ABANDON}
 */
public record MissionActionPayload(BlockPos terminal, int action, int offer) implements CustomPacketPayload {
	public static final int ACCEPT = 0;
	public static final int ABANDON = 1;

	public static final Type<MissionActionPayload> TYPE = new Type<>(OldRepublicMod.id("mission_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MissionActionPayload> CODEC = StreamCodec.of(MissionActionPayload::write, MissionActionPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, MissionActionPayload p) {
		buf.writeLong(p.terminal.asLong());
		buf.writeVarInt(p.action);
		buf.writeVarInt(p.offer);
	}

	private static MissionActionPayload read(RegistryFriendlyByteBuf buf) {
		return new MissionActionPayload(BlockPos.of(buf.readLong()), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

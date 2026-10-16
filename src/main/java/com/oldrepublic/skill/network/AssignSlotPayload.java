package com.oldrepublic.skill.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to put an owned power into a slot, or clear it with {@code ""} ({@code assign_slot}). */
public record AssignSlotPayload(int slot, String abilityId) implements CustomPacketPayload {
	public static final Type<AssignSlotPayload> TYPE = new Type<>(OldRepublicMod.id("assign_slot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AssignSlotPayload> CODEC = StreamCodec.of(AssignSlotPayload::write, AssignSlotPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, AssignSlotPayload p) {
		buf.writeVarInt(p.slot);
		buf.writeUtf(p.abilityId);
	}

	private static AssignSlotPayload read(RegistryFriendlyByteBuf buf) {
		return new AssignSlotPayload(buf.readVarInt(), buf.readUtf());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

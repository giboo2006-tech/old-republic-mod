package com.oldrepublic.force.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client request to choose (or re-choose) a Force Push branch. Validated on the server. */
public record ChooseBranchPayload(String branchId) implements CustomPacketPayload {
	public static final Type<ChooseBranchPayload> TYPE = new Type<>(OldRepublicMod.id("choose_branch"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChooseBranchPayload> CODEC = ByteBufCodecs.stringUtf8(32)
			.map(ChooseBranchPayload::new, ChooseBranchPayload::branchId)
			.cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

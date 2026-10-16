package com.oldrepublic.skill.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to owner: open the Force screen, optionally with the respec confirmation showing
 * ({@code open_skills}; sent by the mentor's "Unburden" dialogue option).
 */
public record OpenSkillsPayload(boolean respec) implements CustomPacketPayload {
	public static final Type<OpenSkillsPayload> TYPE = new Type<>(OldRepublicMod.id("open_skills"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenSkillsPayload> CODEC = ByteBufCodecs.BOOL
			.map(OpenSkillsPayload::new, OpenSkillsPayload::respec)
			.cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.oldrepublic.saber.forge;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A Forge intent ({@code forge_action}, C2S, MILESTONE_7.md 5.2): the action, a design's item id
 * ({@code emitter_vanguard}) and a material ordinal ({@code HiltMaterial}; unused by Study). The server
 * checks that the sender's open menu is a workbench and validates everything else; see
 * {@link ForgeService}.
 */
public record ForgeActionPayload(int action, String design, int material) implements CustomPacketPayload {
	/** Learn the design of the part in its socket. */
	public static final int STUDY = 0;
	/** Make a new part of a known design in a material. */
	public static final int FORGE = 1;
	/** Remake the part in its socket in another material. */
	public static final int REFORGE = 2;

	public static final Type<ForgeActionPayload> TYPE = new Type<>(OldRepublicMod.id("forge_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForgeActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ForgeActionPayload::action,
			ByteBufCodecs.stringUtf8(64), ForgeActionPayload::design,
			ByteBufCodecs.VAR_INT, ForgeActionPayload::material,
			ForgeActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

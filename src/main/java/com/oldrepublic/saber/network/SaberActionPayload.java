package com.oldrepublic.saber.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent for a saber technique ({@code saber_action}): a heavy attack (Sneak + Attack), a
 * lunge at an entity (Sprint + Attack with a target out of reach) or a form cycle (the Saber Form
 * key). The server validates everything.
 *
 * @param targetId the lunge target's entity id; ignored for the other actions
 */
public record SaberActionPayload(Action action, int targetId) implements CustomPacketPayload {
	public enum Action {
		HEAVY,
		LUNGE,
		FORM_CYCLE
	}

	public static final Type<SaberActionPayload> TYPE = new Type<>(OldRepublicMod.id("saber_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SaberActionPayload> CODEC = StreamCodec.of(SaberActionPayload::write, SaberActionPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SaberActionPayload payload) {
		buf.writeVarInt(payload.action.ordinal());
		buf.writeVarInt(payload.targetId);
	}

	private static SaberActionPayload read(RegistryFriendlyByteBuf buf) {
		Action[] actions = Action.values();
		int ordinal = buf.readVarInt();
		Action action = ordinal >= 0 && ordinal < actions.length ? actions[ordinal] : Action.HEAVY;
		return new SaberActionPayload(action, buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

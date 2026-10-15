package com.oldrepublic.force.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent for the {@code Use Force Power} key. The server measures the hold time itself; the
 * client never sends a charge value.
 */
public record PushInputPayload(Action action) implements CustomPacketPayload {
	public enum Action {
		/** Key pressed: start charging. */
		START,
		/** Key released: fire if the hold was long enough. */
		RELEASE,
		/** Charge abandoned (screen opened, item used...): no cost. */
		CANCEL;

		static Action byOrdinal(int ordinal) {
			Action[] values = values();
			return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CANCEL;
		}
	}

	public static final Type<PushInputPayload> TYPE = new Type<>(OldRepublicMod.id("push_input"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PushInputPayload> CODEC = ByteBufCodecs.VAR_INT
			.map(i -> new PushInputPayload(Action.byOrdinal(i)), p -> p.action.ordinal())
			.cast();

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

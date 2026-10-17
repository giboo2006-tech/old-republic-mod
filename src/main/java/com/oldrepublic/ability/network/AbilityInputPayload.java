package com.oldrepublic.ability.network;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client intent for an ability slot key ({@code ability_input}): slot 0 to 3, or
 * {@link #SENSE_SLOT} for the Force Sense key, and what the key did. The server looks up the slot's
 * ability itself and measures hold times itself.
 */
public record AbilityInputPayload(int slot, AbilityAction action) implements CustomPacketPayload {
	/** The Force Sense key's own slot; it never takes an ability slot (spec 10.1). */
	public static final int SENSE_SLOT = 8;
	public static final Type<AbilityInputPayload> TYPE = new Type<>(OldRepublicMod.id("ability_input"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityInputPayload> CODEC = StreamCodec.of(AbilityInputPayload::write, AbilityInputPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, AbilityInputPayload p) {
		buf.writeVarInt(p.slot);
		buf.writeVarInt(p.action.ordinal());
	}

	private static AbilityInputPayload read(RegistryFriendlyByteBuf buf) {
		int slot = buf.readVarInt();
		int ordinal = buf.readVarInt();
		AbilityAction[] actions = AbilityAction.values();
		return new AbilityInputPayload(slot, ordinal >= 0 && ordinal < actions.length ? actions[ordinal] : AbilityAction.CANCEL);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

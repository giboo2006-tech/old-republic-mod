package com.oldrepublic.vendor.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A vendor screen intent ({@code oldrepublic:vendor_buy}, C2S, spec MILESTONE_4.md 16.2): buy
 * {@code count} of line {@code index}, sell {@code count} of sell line {@code index}, or change
 * chits and bundles. The server checks everything again: reach, the gate, the day's limit, the
 * price, the player's credits and inventory space.
 *
 * @param vendor the vendor entity's network id from {@link VendorStockPayload}
 * @param action {@link #BUY}, {@link #SELL}, {@link #TO_BUNDLE} or {@link #TO_CHITS}
 */
public record VendorBuyPayload(int vendor, int action, int index, int count) implements CustomPacketPayload {
	public static final int BUY = 0;
	public static final int SELL = 1;
	/** 100 chits into a bundle. */
	public static final int TO_BUNDLE = 2;
	/** A bundle into 100 chits. */
	public static final int TO_CHITS = 3;

	public static final Type<VendorBuyPayload> TYPE = new Type<>(OldRepublicMod.id("vendor_buy"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VendorBuyPayload> CODEC = StreamCodec.of(VendorBuyPayload::write, VendorBuyPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, VendorBuyPayload p) {
		buf.writeVarInt(p.vendor);
		buf.writeVarInt(p.action);
		buf.writeVarInt(p.index);
		buf.writeVarInt(p.count);
	}

	private static VendorBuyPayload read(RegistryFriendlyByteBuf buf) {
		return new VendorBuyPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

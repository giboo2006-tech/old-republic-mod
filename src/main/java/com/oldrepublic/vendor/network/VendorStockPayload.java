package com.oldrepublic.vendor.network;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Opens or refreshes the vendor screen ({@code oldrepublic:vendor_stock}, S2C, spec MILESTONE_4.md
 * 7.8, 16.2): the vendor's lines with their price for this player, why a line is locked, the daily
 * purchases left, what the vendor would buy from the player's inventory, and the player's credits.
 * Sent when the vendor opens and after every purchase, sale or exchange. The client only displays
 * it; {@code vendor_buy} is checked again in full on the server.
 *
 * @param vendor   the vendor entity's network id, quoted back in {@link VendorBuyPayload}
 * @param stock    the stock id (the screen's title key is {@code vendor.oldrepublic.stock.<stock>})
 * @param faction  the vendor's faction ordinal, or -1 (the emblem and accent)
 * @param lines    the stock lines (at most {@link #MAX_LINES})
 * @param sells    what the vendor would buy now (at most {@link #MAX_SELLS})
 * @param credits  the player's credits (inventory and a near ship's hold)
 * @param chits    loose chits in the inventory (the exchange button)
 * @param bundles  bundles in the inventory
 * @param honoured whether the Honoured discount applies to the prices shown
 */
public record VendorStockPayload(int vendor, String stock, int faction, List<Line> lines, List<Sell> sells, int credits, int chits, int bundles,
		boolean honoured) implements CustomPacketPayload {
	public static final Type<VendorStockPayload> TYPE = new Type<>(OldRepublicMod.id("vendor_stock"));
	public static final StreamCodec<RegistryFriendlyByteBuf, VendorStockPayload> CODEC = StreamCodec.of(VendorStockPayload::write, VendorStockPayload::read);
	public static final int MAX_LINES = 64;
	public static final int MAX_SELLS = 36;

	/**
	 * One line of stock.
	 *
	 * @param index     the line's index in the stock, quoted back to buy it
	 * @param item      what one purchase gives
	 * @param price     what one purchase costs this player now
	 * @param lock      why it cannot be bought ({@code key|arg|@nested}), or empty
	 * @param dailyLeft purchases left today, or -1 without a limit
	 */
	public record Line(int index, ItemStack item, int price, String lock, int dailyLeft) {
	}

	/**
	 * One item the vendor would buy.
	 *
	 * @param index the sell line's index, quoted back to sell it
	 * @param item  the item, with the count the player holds
	 * @param each  credits paid per item
	 */
	public record Sell(int index, ItemStack item, int each) {
	}

	private static void write(RegistryFriendlyByteBuf buf, VendorStockPayload p) {
		buf.writeVarInt(p.vendor);
		buf.writeUtf(p.stock);
		buf.writeVarInt(p.faction);
		int lines = Math.min(MAX_LINES, p.lines.size());
		buf.writeVarInt(lines);

		for (int i = 0; i < lines; i++) {
			Line line = p.lines.get(i);
			buf.writeVarInt(line.index());
			// API-CHECK: ItemStack.OPTIONAL_STREAM_CODEC (StreamCodec<RegistryFriendlyByteBuf, ItemStack>), present since 1.20.5.
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, line.item());
			buf.writeVarInt(line.price());
			buf.writeUtf(line.lock());
			buf.writeVarInt(line.dailyLeft());
		}

		int sells = Math.min(MAX_SELLS, p.sells.size());
		buf.writeVarInt(sells);

		for (int i = 0; i < sells; i++) {
			Sell sell = p.sells.get(i);
			buf.writeVarInt(sell.index());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, sell.item());
			buf.writeVarInt(sell.each());
		}

		buf.writeVarInt(p.credits);
		buf.writeVarInt(p.chits);
		buf.writeVarInt(p.bundles);
		buf.writeBoolean(p.honoured);
	}

	private static VendorStockPayload read(RegistryFriendlyByteBuf buf) {
		int vendor = buf.readVarInt();
		String stock = buf.readUtf();
		int faction = buf.readVarInt();
		int lineCount = Math.min(MAX_LINES, buf.readVarInt());
		List<Line> lines = new ArrayList<>(lineCount);

		for (int i = 0; i < lineCount; i++) {
			lines.add(new Line(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
		}

		int sellCount = Math.min(MAX_SELLS, buf.readVarInt());
		List<Sell> sells = new ArrayList<>(sellCount);

		for (int i = 0; i < sellCount; i++) {
			sells.add(new Sell(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));
		}

		return new VendorStockPayload(vendor, stock, faction, List.copyOf(lines), List.copyOf(sells), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

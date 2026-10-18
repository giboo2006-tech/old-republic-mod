package com.oldrepublic.crafting.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * An Engineering Bench intent ({@code bench_action}, C2S, spec MILESTONE_4.md 16.2): the tab, a
 * schematic or discipline id, and a slot or action number. The server checks that the sender's
 * open menu is a bench and validates everything else; see {@code BenchService}.
 *
 * <ul>
 * <li>Craft tab: {@code id} = schematic; {@code slot} = {@link #SELECT}, {@link #CRAFT},
 * {@link #CANCEL} or {@link #BREAK_BUNDLE} (id unused).</li>
 * <li>Modify tab: {@code slot} = {@link #FIT}, or a {@code ModSlot} ordinal to remove that mod.</li>
 * <li>Salvage tab: {@code slot} = {@link #SALVAGE}.</li>
 * <li>Disciplines tab: {@code id} = discipline; {@code slot} = {@link #CHOOSE}.</li>
 * </ul>
 */
public record BenchActionPayload(int tab, String id, int slot) implements CustomPacketPayload {
	public static final int SELECT = 0;
	public static final int CRAFT = 1;
	public static final int CANCEL = 2;
	public static final int BREAK_BUNDLE = 3;
	public static final int FIT = -1;
	public static final int SALVAGE = 0;
	public static final int CHOOSE = 0;

	public static final Type<BenchActionPayload> TYPE = new Type<>(OldRepublicMod.id("bench_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BenchActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BenchActionPayload::tab,
			ByteBufCodecs.stringUtf8(128), BenchActionPayload::id,
			ByteBufCodecs.VAR_INT, BenchActionPayload::slot,
			BenchActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

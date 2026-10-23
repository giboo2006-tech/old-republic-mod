package com.oldrepublic.mission.network;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Opens (or refreshes) the bounty board screen ({@code oldrepublic:mission_board}, spec 4.7, 5.7).
 *
 * @param terminal    the terminal the board belongs to, quoted back in {@link MissionActionPayload}
 * @param issuer      the terminal's army, a {@code Faction} ordinal (the emblem of the board)
 * @param offers      today's offers (at most {@link #MAX_OFFERS})
 * @param fullPayUsed contracts completed today
 * @param fullPayMax  completions per day that pay in full
 * @param activeId    the active mission's id, or empty
 * @param notice      a lang key shown under the offers (why some are missing), or empty
 */
public record MissionBoardPayload(BlockPos terminal, int issuer, List<OfferView> offers, int fullPayUsed, int fullPayMax, String activeId,
		String notice) implements CustomPacketPayload {
	public static final Type<MissionBoardPayload> TYPE = new Type<>(OldRepublicMod.id("mission_board"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MissionBoardPayload> CODEC = StreamCodec.of(MissionBoardPayload::write, MissionBoardPayload::read);
	/** Offer cards a board shows. */
	public static final int MAX_OFFERS = 4;

	/**
	 * One offer card.
	 *
	 * @param index         the offer's index in the day's order, quoted back to accept it
	 * @param missionId     the contract type ({@code clear_camp}, ...)
	 * @param issuer        who issues it, a {@code Faction} ordinal (Frontier for {@code clear_camp})
	 * @param briefing      which of the briefing variants to show
	 * @param objectiveKey  lang key of the objective line
	 * @param xp            XP it would pay now (after the soft cap)
	 * @param rep           issuer standing it would pay now (0 past the cap)
	 * @param commendations commendations it would pay now
	 * @param loot          whether it pays the Frontier loot instead of commendations
	 * @param used          accepted, completed or abandoned already today
	 * @param credits       credits it would pay now (Milestone 4, 7.7: 0 past the full-pay count)
	 */
	public record OfferView(int index, String missionId, int issuer, int briefing, String objectiveKey, int xp, int rep, int commendations,
			boolean loot, boolean used, int credits) {
	}

	private static void write(RegistryFriendlyByteBuf buf, MissionBoardPayload p) {
		buf.writeLong(p.terminal.asLong());
		buf.writeVarInt(p.issuer);
		int count = Math.min(MAX_OFFERS, p.offers.size());
		buf.writeVarInt(count);

		for (int i = 0; i < count; i++) {
			OfferView o = p.offers.get(i);
			buf.writeVarInt(o.index());
			buf.writeUtf(o.missionId());
			buf.writeVarInt(o.issuer());
			buf.writeVarInt(o.briefing());
			buf.writeUtf(o.objectiveKey());
			buf.writeVarInt(o.xp());
			buf.writeVarInt(o.rep());
			buf.writeVarInt(o.commendations());
			buf.writeBoolean(o.loot());
			buf.writeBoolean(o.used());
			buf.writeVarInt(o.credits());
		}

		buf.writeVarInt(p.fullPayUsed);
		buf.writeVarInt(p.fullPayMax);
		buf.writeUtf(p.activeId);
		buf.writeUtf(p.notice);
	}

	private static MissionBoardPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos terminal = BlockPos.of(buf.readLong());
		int issuer = buf.readVarInt();
		int count = Math.min(MAX_OFFERS, buf.readVarInt());
		List<OfferView> offers = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			offers.add(new OfferView(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
		}

		return new MissionBoardPayload(terminal, issuer, List.copyOf(offers), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

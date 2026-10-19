package com.oldrepublic.quest.turning;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the rank 4 checklist (spec MILESTONE_5.md 8.5, 18), shown by W4's Standing panel from Chapter Five's
 * AFTERMATH. Sent to its player when it changes; the client keeps the last one ({@code RankFourClient}) until it
 * disconnects.
 *
 * @param visible whether the checklist shows (a Jedi, Sith or Independent from Chapter Five's AFTERMATH)
 * @param kind what rank 4 is for the player's path: {@link #MASTER}, {@link #DARTH} or {@link #WAYFARER}
 * @param done whether the player holds rank 4 on this path (the checklist then shows as complete)
 * @param entries the requirements in the order the dialogue names a failure (lang
 *     {@code turning.oldrepublic.rank4.req.<id>}, arguments: what is needed, what the player has)
 */
public record RankFourChecklistPayload(boolean visible, int kind, boolean done, List<Entry> entries) implements CustomPacketPayload {
	public static final Type<RankFourChecklistPayload> TYPE = new Type<>(OldRepublicMod.id("rank_four_checklist"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RankFourChecklistPayload> CODEC = StreamCodec.of(RankFourChecklistPayload::write,
			RankFourChecklistPayload::read);

	public static final int MASTER = 0;
	public static final int DARTH = 1;
	public static final int WAYFARER = 2;
	/** The checklist's longest list (8 for a Jedi or a Sith). */
	public static final int MAX_ENTRIES = 12;
	/** Nothing to show. */
	public static final RankFourChecklistPayload HIDDEN = new RankFourChecklistPayload(false, MASTER, false, List.of());

	/**
	 * One requirement.
	 *
	 * @param id the requirement's id ({@code story}, {@code level}, {@code rank}, {@code title}, {@code trust},
	 *     {@code reputation}, {@code factions}, {@code alignment}, {@code mastery}, {@code deeds})
	 * @param met whether it holds
	 * @param have the player's value
	 * @param need the value asked for
	 */
	public record Entry(String id, boolean met, int have, int need) {
		/** The lang key of the line: {@code turning.oldrepublic.rank4.req.<id>} with the need and what the player has. */
		public String key() {
			return "turning.oldrepublic.rank4.req." + id;
		}
	}

	public RankFourChecklistPayload {
		entries = List.copyOf(entries);
	}

	/** Whether every requirement holds. */
	public boolean allMet() {
		for (Entry entry : entries) {
			if (!entry.met()) {
				return false;
			}
		}

		return true;
	}

	private static void write(RegistryFriendlyByteBuf buf, RankFourChecklistPayload p) {
		buf.writeBoolean(p.visible);
		buf.writeVarInt(p.kind);
		buf.writeBoolean(p.done);
		int count = Math.min(MAX_ENTRIES, p.entries.size());
		buf.writeVarInt(count);

		for (int i = 0; i < count; i++) {
			Entry e = p.entries.get(i);
			buf.writeUtf(e.id());
			buf.writeBoolean(e.met());
			buf.writeInt(e.have());
			buf.writeInt(e.need());
		}
	}

	private static RankFourChecklistPayload read(RegistryFriendlyByteBuf buf) {
		boolean visible = buf.readBoolean();
		int kind = buf.readVarInt();
		boolean done = buf.readBoolean();
		int count = Math.min(MAX_ENTRIES, buf.readVarInt());
		List<Entry> entries = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			entries.add(new Entry(buf.readUtf(), buf.readBoolean(), buf.readInt(), buf.readInt()));
		}

		return new RankFourChecklistPayload(visible, kind, done, entries);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

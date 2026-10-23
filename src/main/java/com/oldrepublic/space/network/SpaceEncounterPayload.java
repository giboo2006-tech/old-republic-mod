package com.oldrepublic.space.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * The player's situation in space for the combat HUD (spec MILESTONE_5.md 5.6, 5.9, 18.2): which
 * encounter or objective they are in, its objective point (a run's buoy, a group to clear, shown
 * from anywhere in the orbit), the lang keys of its comm lines (each {@code space_comm} event names
 * one by its index) and an ace's name key. Sent when it changes; {@link #NONE} clears it.
 *
 * @param kind  {@link #NONE}, {@link #PATROL}, {@link #BLOCKADE}, {@link #PASS}, {@link #RUN} or {@link #CLEAR}
 * @param point the objective point, if the kind has one
 * @param comm  the comm lines' lang keys
 * @param ace   an ace's name key, or empty
 */
public record SpaceEncounterPayload(int kind, Optional<Vec3> point, List<String> comm, String ace) implements CustomPacketPayload {
	public static final int NONE = 0;
	public static final int PATROL = 1;
	public static final int BLOCKADE = 2;
	public static final int PASS = 3;
	public static final int RUN = 4;
	public static final int CLEAR = 5;
	/** Comm lines carried at most (and each key's length). */
	private static final int MAX_LINES = 32;
	private static final int MAX_KEY = 128;

	public static final SpaceEncounterPayload CLEARED = new SpaceEncounterPayload(NONE, Optional.empty(), List.of(), "");

	public static final Type<SpaceEncounterPayload> TYPE = new Type<>(OldRepublicMod.id("space_encounter"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpaceEncounterPayload> CODEC = StreamCodec.of(SpaceEncounterPayload::write,
			SpaceEncounterPayload::read);

	public SpaceEncounterPayload {
		comm = List.copyOf(comm.size() > MAX_LINES ? comm.subList(0, MAX_LINES) : comm);
	}

	private static void write(RegistryFriendlyByteBuf buf, SpaceEncounterPayload p) {
		buf.writeVarInt(p.kind);
		buf.writeBoolean(p.point.isPresent());

		if (p.point.isPresent()) {
			Vec3 point = p.point.get();
			buf.writeDouble(point.x);
			buf.writeDouble(point.y);
			buf.writeDouble(point.z);
		}

		buf.writeVarInt(p.comm.size());

		for (String key : p.comm) {
			buf.writeUtf(key, MAX_KEY);
		}

		buf.writeUtf(p.ace, MAX_KEY);
	}

	private static SpaceEncounterPayload read(RegistryFriendlyByteBuf buf) {
		int kind = buf.readVarInt();
		Optional<Vec3> point = buf.readBoolean() ? Optional.of(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())) : Optional.empty();
		int lines = Math.min(MAX_LINES, buf.readVarInt());
		List<String> comm = new ArrayList<>(lines);

		for (int i = 0; i < lines; i++) {
			comm.add(buf.readUtf(MAX_KEY));
		}

		return new SpaceEncounterPayload(kind, point, comm, buf.readUtf(MAX_KEY));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

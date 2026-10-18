package com.oldrepublic.travel.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The ship screen's view of a ship ({@code ship_status}, S2C, spec MILESTONE_4.md 4.10, 16.2): its
 * state and numbers, each destination with the reason it is refused, the crew and the players who
 * may be added. Sent when the screen opens, after every action and whenever it changes; also sent
 * with a {@link #notice} alone for the "ship repaired" and "destination unlocked" toasts.
 *
 * @param viewerIsOwner the viewer owns the ship (crew see a read-only Status tab)
 * @param aboard        the viewer rides the ship
 * @param hullWarning   the next jump leaves the hull below the jump minimum (4.6)
 * @param notice        {@link #NOTICE_NONE}, {@link #NOTICE_REPAIRED} or {@link #NOTICE_UNLOCKED}
 * @param noticeArg     the destination ordinal for {@link #NOTICE_UNLOCKED}
 */
public record ShipStatusPayload(Ship ship, boolean viewerIsOwner, boolean aboard, boolean hullWarning, List<Row> destinations,
		List<Member> crew, List<Member> nearby, int notice, int noticeArg) implements CustomPacketPayload {
	public static final int NOTICE_NONE = 0;
	public static final int NOTICE_REPAIRED = 1;
	public static final int NOTICE_UNLOCKED = 2;

	/**
	 * The ship's numbers.
	 *
	 * @param state     the {@code ShipState} ordinal
	 * @param upgrades  the fitted upgrade bits only
	 * @param sockets   a bit per finished repair socket (0 hull to 3 nav)
	 * @param paint     the paint scheme ({@code CourierShuttle.PAINT_*})
	 * @param crewLimit the most crew the owner may name
	 */
	public record Ship(int state, int hull, int fuel, int tank, int upgrades, int holdSize, int seats, int crewLimit, int sockets, int plates,
			int platesNeeded, int paint, String hullCode) {
		/** The empty view, for a screen that has not heard from the server yet. */
		public static final Ship EMPTY = new Ship(0, 0, 0, 4, 0, 27, 2, 2, 0, 0, 12, 0, "");
	}

	/**
	 * A destination row of the Nav tab.
	 *
	 * @param destination the {@code Destination} ordinal
	 * @param refusal     the {@code TravelService.Refusal} ordinal ({@code NONE} when it can be launched to)
	 */
	public record Row(int destination, int refusal) {
	}

	/** A crew member (or a player who may be added): name, and whether they are aboard and online. */
	public record Member(UUID id, String name, boolean aboard, boolean online) {
	}

	public static final Type<ShipStatusPayload> TYPE = new Type<>(OldRepublicMod.id("ship_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShipStatusPayload> CODEC = StreamCodec.of(ShipStatusPayload::write, ShipStatusPayload::read);

	/** At most this many rows and members are sent (the screen shows fewer). */
	private static final int MAX_LIST = 16;

	private static void write(RegistryFriendlyByteBuf buf, ShipStatusPayload p) {
		Ship s = p.ship;
		buf.writeVarInt(s.state());
		buf.writeVarInt(s.hull());
		buf.writeVarInt(s.fuel());
		buf.writeVarInt(s.tank());
		buf.writeVarInt(s.upgrades());
		buf.writeVarInt(s.holdSize());
		buf.writeVarInt(s.seats());
		buf.writeVarInt(s.crewLimit());
		buf.writeVarInt(s.sockets());
		buf.writeVarInt(s.plates());
		buf.writeVarInt(s.platesNeeded());
		buf.writeVarInt(s.paint());
		buf.writeUtf(s.hullCode(), 16);
		buf.writeBoolean(p.viewerIsOwner);
		buf.writeBoolean(p.aboard);
		buf.writeBoolean(p.hullWarning);
		int rows = Math.min(MAX_LIST, p.destinations.size());
		buf.writeVarInt(rows);

		for (int i = 0; i < rows; i++) {
			buf.writeVarInt(p.destinations.get(i).destination());
			buf.writeVarInt(p.destinations.get(i).refusal());
		}

		writeMembers(buf, p.crew);
		writeMembers(buf, p.nearby);
		buf.writeVarInt(p.notice);
		buf.writeVarInt(p.noticeArg);
	}

	private static ShipStatusPayload read(RegistryFriendlyByteBuf buf) {
		Ship ship = new Ship(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(16));
		boolean owner = buf.readBoolean();
		boolean aboard = buf.readBoolean();
		boolean warning = buf.readBoolean();
		int rows = Math.min(MAX_LIST, buf.readVarInt());
		List<Row> destinations = new ArrayList<>(rows);

		for (int i = 0; i < rows; i++) {
			destinations.add(new Row(buf.readVarInt(), buf.readVarInt()));
		}

		List<Member> crew = readMembers(buf);
		List<Member> nearby = readMembers(buf);
		return new ShipStatusPayload(ship, owner, aboard, warning, destinations, crew, nearby, buf.readVarInt(), buf.readVarInt());
	}

	private static void writeMembers(RegistryFriendlyByteBuf buf, List<Member> members) {
		int count = Math.min(MAX_LIST, members.size());
		buf.writeVarInt(count);

		for (int i = 0; i < count; i++) {
			Member m = members.get(i);
			UUIDUtil.STREAM_CODEC.encode(buf, m.id());
			buf.writeUtf(m.name(), 32);
			buf.writeBoolean(m.aboard());
			buf.writeBoolean(m.online());
		}
	}

	private static List<Member> readMembers(RegistryFriendlyByteBuf buf) {
		int count = Math.min(MAX_LIST, buf.readVarInt());
		List<Member> members = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			members.add(new Member(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(32), buf.readBoolean(), buf.readBoolean()));
		}

		return members;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

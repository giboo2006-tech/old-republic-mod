package com.oldrepublic.travel.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.travel.TravelService;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The ship screen's view of a ship ({@code ship_status}, S2C, spec MILESTONE_4.md 4.10, 16.2;
 * MILESTONE_5.md 13.2): its state and numbers, each destination with the reason it is refused, the
 * crew and the players who may be added, and the helm: whether it may be taken, and in the air the
 * return to the last landing and the jumps from an orbit. Sent when the screen opens, after every
 * action and whenever it changes; also sent with a {@link #notice} alone for the "ship repaired" and
 * "destination unlocked" toasts.
 *
 * @param viewerIsOwner the viewer owns the ship (crew see a read-only Status tab)
 * @param aboard        the viewer rides the ship
 * @param hullWarning   the next jump leaves the hull below the jump minimum (4.6)
 * @param destinations  each destination with why a launch to it is refused; in the air, why the
 *                      autopilot may not take the ship there ("Land at", MILESTONE_5.md 4.5)
 * @param notice        {@link #NOTICE_NONE}, {@link #NOTICE_REPAIRED} or {@link #NOTICE_UNLOCKED}
 * @param noticeArg     the destination ordinal for {@link #NOTICE_UNLOCKED}
 */
public record ShipStatusPayload(Ship ship, boolean viewerIsOwner, boolean aboard, boolean hullWarning, List<Row> destinations,
		List<Member> crew, List<Member> nearby, int notice, int noticeArg, Air air) implements CustomPacketPayload {
	public static final int NOTICE_NONE = 0;
	public static final int NOTICE_REPAIRED = 1;
	public static final int NOTICE_UNLOCKED = 2;

	/**
	 * The ship's numbers.
	 *
	 * @param state     the {@code ShipState} ordinal
	 * @param maxHull   the most hull the ship's class holds (MILESTONE_5.md 4.9)
	 * @param upgrades  the fitted upgrade bits only
	 * @param sockets   a bit per finished repair socket (0 hull to 3 nav)
	 * @param paint     the paint scheme ({@code ShipBody.PAINT_*})
	 * @param crewLimit the most crew the owner may name
	 */
	public record Ship(int state, int hull, int maxHull, int fuel, int tank, int upgrades, int holdSize, int seats, int crewLimit, int sockets,
			int plates, int platesNeeded, int paint, String hullCode) {
		/** The empty view, for a screen that has not heard from the server yet. */
		public static final Ship EMPTY = new Ship(0, 0, 100, 0, 4, 0, 27, 2, 2, 0, 0, 12, 0, "");
	}

	/**
	 * The helm (MILESTONE_5.md 4.2, 4.5, 4.7, 4.10, 13.2).
	 *
	 * @param helm          why the helm may not be taken now ({@code TravelService.Refusal} ordinal; {@code NONE}: it may)
	 * @param flying        the ship is in the air at its owner's helm: the Nav tab lands or jumps, the Status tab returns
	 * @param returnRefusal why the return to the last landing is refused ({@code NONE}: it can go)
	 * @param returnCost    how the return is paid ({@code FlightRules.ReturnCost} ordinal)
	 * @param orbit         the planet whose orbit the ship flies in ({@code Planets.Planet} ordinal), or −1
	 * @param jumps         in an orbit, each destination with why a jump to its orbit is refused; else empty
	 */
	public record Air(int helm, boolean flying, int returnRefusal, int returnCost, int orbit, List<Row> jumps) {
		/** A ship on the ground, or a view for someone who cannot take its helm. */
		public static final Air GROUND = new Air(TravelService.Refusal.NOT_OWNER.ordinal(), false, TravelService.Refusal.NOT_READY.ordinal(), 0, -1,
				List.of());
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
		buf.writeVarInt(s.maxHull());
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
		writeRows(buf, p.destinations);
		writeMembers(buf, p.crew);
		writeMembers(buf, p.nearby);
		buf.writeVarInt(p.notice);
		buf.writeVarInt(p.noticeArg);
		Air air = p.air;
		buf.writeVarInt(air.helm());
		buf.writeBoolean(air.flying());
		buf.writeVarInt(air.returnRefusal());
		buf.writeVarInt(air.returnCost());
		buf.writeVarInt(air.orbit());
		writeRows(buf, air.jumps());
	}

	private static ShipStatusPayload read(RegistryFriendlyByteBuf buf) {
		Ship ship = new Ship(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readUtf(16));
		boolean owner = buf.readBoolean();
		boolean aboard = buf.readBoolean();
		boolean warning = buf.readBoolean();
		List<Row> destinations = readRows(buf);
		List<Member> crew = readMembers(buf);
		List<Member> nearby = readMembers(buf);
		int notice = buf.readVarInt();
		int noticeArg = buf.readVarInt();
		Air air = new Air(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readRows(buf));
		return new ShipStatusPayload(ship, owner, aboard, warning, destinations, crew, nearby, notice, noticeArg, air);
	}

	private static void writeRows(RegistryFriendlyByteBuf buf, List<Row> rows) {
		int count = Math.min(MAX_LIST, rows.size());
		buf.writeVarInt(count);

		for (int i = 0; i < count; i++) {
			buf.writeVarInt(rows.get(i).destination());
			buf.writeVarInt(rows.get(i).refusal());
		}
	}

	private static List<Row> readRows(RegistryFriendlyByteBuf buf) {
		int count = Math.min(MAX_LIST, buf.readVarInt());
		List<Row> rows = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			rows.add(new Row(buf.readVarInt(), buf.readVarInt()));
		}

		return rows;
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

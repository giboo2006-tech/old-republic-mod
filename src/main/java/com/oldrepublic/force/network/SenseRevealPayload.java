package com.oldrepublic.force.network;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.force.sense.SenseTargets;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * What one Force Sense cast revealed, sent to the caster only ({@code sense_reveal}, spec 10.1 and
 * 16): at most {@link #MAX_POINTS} points and how long the caster sees them. Positions only; the
 * client builds every mote itself. Wire format: varint count, then per point the kind ordinal
 * (varint), x, y, z (floats), the entity id (int, -1 for blocks) and the attitude ordinal (varint),
 * then the duration (varint).
 *
 * @param points        nearest first
 * @param durationTicks how long the clusters stay
 */
public record SenseRevealPayload(List<SenseTargets.Point> points, int durationTicks) implements CustomPacketPayload {
	/** The most points one reveal carries (spec 10.1). */
	public static final int MAX_POINTS = 32;
	public static final Type<SenseRevealPayload> TYPE = new Type<>(OldRepublicMod.id("sense_reveal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SenseRevealPayload> CODEC = StreamCodec.of(SenseRevealPayload::write, SenseRevealPayload::read);

	public SenseRevealPayload {
		points = List.copyOf(points.size() > MAX_POINTS ? points.subList(0, MAX_POINTS) : points);
	}

	private static void write(RegistryFriendlyByteBuf buf, SenseRevealPayload p) {
		buf.writeVarInt(p.points.size());

		for (SenseTargets.Point point : p.points) {
			buf.writeVarInt(point.kind().ordinal());
			buf.writeFloat((float) point.pos().x);
			buf.writeFloat((float) point.pos().y);
			buf.writeFloat((float) point.pos().z);
			buf.writeInt(point.entityId());
			buf.writeVarInt(point.attitude().ordinal());
		}

		buf.writeVarInt(p.durationTicks);
	}

	private static SenseRevealPayload read(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();

		if (count < 0 || count > MAX_POINTS) {
			throw new IllegalArgumentException("sense_reveal carries " + count + " points, at most " + MAX_POINTS + " allowed");
		}

		SenseTargets.Kind[] kinds = SenseTargets.Kind.values();
		Attitude[] attitudes = Attitude.values();
		List<SenseTargets.Point> points = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int kind = buf.readVarInt();
			Vec3 pos = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
			int entityId = buf.readInt();
			int attitude = buf.readVarInt();
			// Unknown ordinals (a newer server) degrade to a neutral block point.
			points.add(new SenseTargets.Point(kind >= 0 && kind < kinds.length ? kinds[kind] : SenseTargets.Kind.BLOCK, pos, entityId,
					attitude >= 0 && attitude < attitudes.length ? attitudes[attitude] : Attitude.NEUTRAL));
		}

		return new SenseRevealPayload(points, buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

package com.oldrepublic.mission.network;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.mission.MissionRules;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The tracker's direction and distance band for the active mission ({@code oldrepublic:tracker_hint},
 * spec 4.7, 5.7), sent every {@code missions.trackerHintTicks}. Never coordinates.
 *
 * @param yaw      the yaw (degrees) from the player toward the target
 * @param band     a {@link MissionRules.Band} ordinal; {@code NONE} when there is no target here
 * @param progress the objective's counter
 * @param target   the counter's goal; 0 or 1 hides the counter
 */
public record TrackerHintPayload(float yaw, int band, int progress, int target) implements CustomPacketPayload {
	public static final Type<TrackerHintPayload> TYPE = new Type<>(OldRepublicMod.id("tracker_hint"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TrackerHintPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, TrackerHintPayload::yaw,
			ByteBufCodecs.VAR_INT, TrackerHintPayload::band,
			ByteBufCodecs.VAR_INT, TrackerHintPayload::progress,
			ByteBufCodecs.VAR_INT, TrackerHintPayload::target,
			TrackerHintPayload::new);

	/** The band as the enum (an unknown ordinal reads as {@code NONE}). */
	public MissionRules.Band bandValue() {
		MissionRules.Band[] bands = MissionRules.Band.values();
		return band >= 0 && band < bands.length ? bands[band] : MissionRules.Band.NONE;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

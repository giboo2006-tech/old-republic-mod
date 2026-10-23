package com.oldrepublic.planet;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: Ghessar's weather (spec MILESTONE_5.md 3.5, 18.2), the {@link PlanetWeather.State}
 * as its kind's ordinal and the game tick it lasts until. Sent to a player who joins in Ghessar or
 * arrives there, and to everyone in Ghessar when it changes.
 */
public record PlanetWeatherPayload(int kind, long untilTick) implements CustomPacketPayload {
	public static final Type<PlanetWeatherPayload> TYPE = new Type<>(OldRepublicMod.id("planet_weather"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PlanetWeatherPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PlanetWeatherPayload::kind,
			ByteBufCodecs.VAR_LONG, PlanetWeatherPayload::untilTick,
			PlanetWeatherPayload::new);

	public PlanetWeatherPayload(PlanetWeather.State state) {
		this(state.kind().ordinal(), state.untilTick());
	}

	/** The state this payload carries. */
	public PlanetWeather.State state() {
		return new PlanetWeather.State(PlanetWeather.Kind.byOrdinal(kind), untilTick);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}

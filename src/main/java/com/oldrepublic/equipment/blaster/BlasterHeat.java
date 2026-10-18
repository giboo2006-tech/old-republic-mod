package com.oldrepublic.equipment.blaster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A blaster's heat (spec MILESTONE_4.md 5.1), the item component {@code oldrepublic:blaster_heat}.
 *
 * @param heat 0 to 100
 * @param lastShot game time of the last shot
 * @param lockedUntil game time an overheat lock ends (0 when not locked)
 * @param freeShots shots left that build no heat (after a perfect vent)
 */
public record BlasterHeat(float heat, long lastShot, long lockedUntil, int freeShots) {
	public static final BlasterHeat COLD = new BlasterHeat(0f, 0L, 0L, 0);

	public static final Codec<BlasterHeat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("heat", 0f).forGetter(BlasterHeat::heat),
			Codec.LONG.optionalFieldOf("last_shot", 0L).forGetter(BlasterHeat::lastShot),
			Codec.LONG.optionalFieldOf("locked_until", 0L).forGetter(BlasterHeat::lockedUntil),
			Codec.INT.optionalFieldOf("free_shots", 0).forGetter(BlasterHeat::freeShots)
	).apply(i, BlasterHeat::new));

	public static final StreamCodec<ByteBuf, BlasterHeat> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, BlasterHeat::heat,
			ByteBufCodecs.VAR_LONG, BlasterHeat::lastShot,
			ByteBufCodecs.VAR_LONG, BlasterHeat::lockedUntil,
			ByteBufCodecs.VAR_INT, BlasterHeat::freeShots,
			BlasterHeat::new);

	public BlasterHeat {
		heat = Math.max(0f, Math.min(100f, heat));
		freeShots = Math.max(0, freeShots);
	}
}

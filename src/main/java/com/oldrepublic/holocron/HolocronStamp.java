package com.oldrepublic.holocron;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The {@code oldrepublic:holocron} item component (spec section 4.1): which holocron this is, whose
 * it is, and the owner's issue number for that id. A copy whose serial is older than the owner's
 * current one is stale.
 */
public record HolocronStamp(String id, UUID owner, int serial) {
	public static final Codec<HolocronStamp> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(HolocronStamp::id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(HolocronStamp::owner),
			Codec.INT.fieldOf("serial").forGetter(HolocronStamp::serial)
	).apply(i, HolocronStamp::new));
	public static final StreamCodec<ByteBuf, HolocronStamp> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, HolocronStamp::id,
			UUIDUtil.STREAM_CODEC, HolocronStamp::owner,
			ByteBufCodecs.VAR_INT, HolocronStamp::serial,
			HolocronStamp::new);
}

package com.oldrepublic.saber;

import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The cosmetic blade palette offered at the workbench. Colour never affects stats. */
public enum BladeColor {
	BLUE(0x2F6BFF),
	GREEN(0x36E05A),
	YELLOW(0xFFD43B),
	VIOLET(0xA24DFF),
	CYAN(0x3BE3E8),
	ORANGE(0xFF8C2E),
	RED(0xFF2E2E),
	SILVER(0xE6EDF5);

	public static final Codec<BladeColor> CODEC = Codec.STRING.comapFlatMap(BladeColor::parse, BladeColor::id);
	public static final StreamCodec<ByteBuf, BladeColor> STREAM_CODEC = ByteBufCodecs.idMapper(BladeColor::byIndex, BladeColor::ordinal);

	private final int rgb;

	BladeColor(int rgb) {
		this.rgb = rgb;
	}

	/** Opaque RGB, without alpha. */
	public int rgb() {
		return rgb;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable("color.oldrepublic.blade." + id());
	}

	public static BladeColor byIndex(int index) {
		BladeColor[] values = values();
		return values[Math.floorMod(index, values.length)];
	}

	/** The palette entry closest to {@code rgb} (used to preselect a crystal's natural hue). */
	public static BladeColor nearest(int rgb) {
		BladeColor best = BLUE;
		long bestDistance = Long.MAX_VALUE;

		for (BladeColor color : values()) {
			long dr = ((rgb >> 16) & 0xFF) - ((color.rgb >> 16) & 0xFF);
			long dg = ((rgb >> 8) & 0xFF) - ((color.rgb >> 8) & 0xFF);
			long db = (rgb & 0xFF) - (color.rgb & 0xFF);
			long distance = dr * dr * 3 + dg * dg * 4 + db * db * 2;

			if (distance < bestDistance) {
				bestDistance = distance;
				best = color;
			}
		}

		return best;
	}

	private static DataResult<BladeColor> parse(String id) {
		for (BladeColor color : values()) {
			if (color.id().equals(id)) {
				return DataResult.success(color);
			}
		}

		return DataResult.error(() -> "Unknown blade colour " + id);
	}
}

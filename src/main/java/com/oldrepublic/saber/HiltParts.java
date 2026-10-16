package com.oldrepublic.saber;

import java.util.Locale;
import java.util.function.IntFunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The three hilt component slots and their variants. Item ids are {@code <slot>_<variant>}. Saved
 * by id and synced by ordinal: append new variants only.
 */
public final class HiltParts {
	/** Common view of a part variant. */
	public interface Part {
		/** Variant id, e.g. {@code guardian}. */
		String id();

		/** Item id path, e.g. {@code emitter_guardian}. */
		String itemId();
	}

	public enum Emitter implements Part {
		GUARDIAN,
		DUELIST,
		/** The Collector's rematch reward (spec 8.9). */
		RELIQUARY;

		public static final Codec<Emitter> CODEC = codec(values());
		public static final StreamCodec<ByteBuf, Emitter> STREAM_CODEC = streamCodec(values());

		@Override
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Override
		public String itemId() {
			return "emitter_" + id();
		}
	}

	public enum Grip implements Part {
		WRAPPED,
		RIDGED;

		public static final Codec<Grip> CODEC = codec(values());
		public static final StreamCodec<ByteBuf, Grip> STREAM_CODEC = streamCodec(values());

		@Override
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Override
		public String itemId() {
			return "grip_" + id();
		}
	}

	public enum Pommel implements Part {
		WEIGHTED,
		BALANCED,
		/** The Collector's first-clear reward (spec 8.8). */
		ARCHIVIST;

		public static final Codec<Pommel> CODEC = codec(values());
		public static final StreamCodec<ByteBuf, Pommel> STREAM_CODEC = streamCodec(values());

		@Override
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		@Override
		public String itemId() {
			return "pommel_" + id();
		}
	}

	/** Parses a variant id (case-insensitive) or returns null. */
	public static <E extends Enum<E> & Part> E byId(E[] values, String id) {
		for (E value : values) {
			if (value.id().equalsIgnoreCase(id)) {
				return value;
			}
		}

		return null;
	}

	private static <E extends Enum<E> & Part> Codec<E> codec(E[] values) {
		return Codec.STRING.comapFlatMap(id -> {
			E value = byId(values, id);
			return value != null ? DataResult.success(value) : DataResult.error(() -> "Unknown hilt part " + id);
		}, Part::id);
	}

	private static <E extends Enum<E>> StreamCodec<ByteBuf, E> streamCodec(E[] values) {
		IntFunction<E> byIndex = i -> values[Math.floorMod(i, values.length)];
		return ByteBufCodecs.idMapper(byIndex, Enum::ordinal);
	}

	private HiltParts() {
	}
}

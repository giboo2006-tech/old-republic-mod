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
 * by id and synced by ordinal: append new variants only. Milestone 7 appends the craftable designs
 * and the boss sets' parts (MILESTONE_7.md 6.3, 7.1).
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
		RELIQUARY,
		/** The Cantor's Emitter, a legendary part (MILESTONE_5.md 11.2); W7 registers its item. */
		CANTOR,
		/** Milestone 7 (MILESTONE_7.md 6.3): a vented heavy shroud, craftable. */
		VANGUARD,
		/** A crossguard: two quillon ports with short side blades (8.1), craftable. */
		WARDEN,
		/** Kaast's trophy, a Watch knight's emitter (the Watchman set, 7.1). */
		WATCHMAN,
		/** Darth Ulvaine's curved emitter (the Penitent set, 7.1, 8.2). */
		PENITENT,
		/** The Long Hymn choir's emitter (the Choir set, 7.1). */
		CHOIR;

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
		RIDGED,
		/** The saberstaff's long grip (spec MILESTONE_4.md 5.6); only the staff takes it. */
		STAFF,
		/** The Sunforged Grip, a legendary part (MILESTONE_5.md 11.2); W7 registers its item. */
		SUNFORGED,
		/** Milestone 7 (MILESTONE_7.md 6.3): metal and rubber rings, craftable. */
		BANDED,
		/** A slim knurled tube, craftable. */
		KNURLED,
		/** Deep vertical flutes, craftable. */
		FLUTED,
		/** The Collector's archival grip (the Collector set, 7.1). */
		RELIQUARY,
		/** Kaast's trophy (the Watchman set). */
		WATCHMAN,
		/** Darth Ulvaine's grip (the Penitent set). */
		PENITENT,
		/** The Long Hymn choir's grip (the Choir set). */
		CHOIR;

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
		ARCHIVIST,
		/** The Hask Pommel, a legendary part (MILESTONE_5.md 11.2); W7 registers its item. */
		HASK,
		/** Milestone 7 (MILESTONE_7.md 6.3): a cap with a D-ring, craftable. */
		RINGED,
		/** A domed cap with a crystal window, craftable. */
		DOMED,
		/** Kaast's trophy (the Watchman set). */
		WATCHMAN,
		/** Darth Ulvaine's pommel (the Penitent set). */
		PENITENT,
		/** The Long Hymn choir's pommel (the Choir set). */
		CHOIR;

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

	/** The three slots a part fits (MILESTONE_7.md 12.1). */
	public enum Slot {
		EMITTER,
		GRIP,
		POMMEL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The slot {@code part} fits. */
	public static Slot slot(Part part) {
		if (part instanceof Emitter) {
			return Slot.EMITTER;
		}

		return part instanceof Grip ? Slot.GRIP : Slot.POMMEL;
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

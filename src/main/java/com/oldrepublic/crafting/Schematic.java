package com.oldrepublic.crafting;

import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One schematic (spec MILESTONE_4.md 7.3): {@code data/oldrepublic/schematic/<id>.json}. The id
 * comes from the file name, so the file codec ({@link #fileCodec}) leaves it out.
 *
 * @param discipline the discipline that makes it; empty for a common schematic
 * @param level the discipline level needed (1 to 5; common schematics use 1)
 * @param quality whether crafting rolls Standard, Fine or Masterwork
 * @param xp discipline XP per craft
 */
public record Schematic(Identifier id, Optional<Discipline> discipline, int level, List<Input> inputs, ItemStack output, boolean quality,
		int craftTicks, int xp, Learned learned) {
	/** One input: an item or an item tag, and how many. */
	public record Input(Either<Item, TagKey<Item>> what, int count) {
		private static final Codec<Item> ITEM_CODEC = Identifier.CODEC.comapFlatMap(
				id -> BuiltInRegistries.ITEM.getOptional(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown item " + id)),
				BuiltInRegistries.ITEM::getKey);
		private static final Codec<TagKey<Item>> TAG_CODEC = Identifier.CODEC.xmap(id -> TagKey.create(Registries.ITEM, id), TagKey::location);

		private record Raw(Optional<Item> item, Optional<TagKey<Item>> tag, int count) {
		}

		public static final Codec<Input> CODEC = RecordCodecBuilder.<Raw>create(i -> i.group(
				ITEM_CODEC.optionalFieldOf("item").forGetter(Raw::item),
				TAG_CODEC.optionalFieldOf("tag").forGetter(Raw::tag),
				Codec.INT.optionalFieldOf("count", 1).forGetter(Raw::count)
		).apply(i, Raw::new)).comapFlatMap(Input::fromRaw, in -> new Raw(in.what().left(), in.what().right(), in.count()));

		public Input {
			count = Math.max(1, count);
		}

		/** Whether {@code stack} counts towards this input (its count is not checked). */
		public boolean matches(ItemStack stack) {
			return what.map(stack::is, stack::is);
		}

		private static DataResult<Input> fromRaw(Raw raw) {
			if (raw.item().isPresent() == raw.tag().isPresent()) {
				return DataResult.error(() -> "A schematic input needs exactly one of item or tag");
			}

			Either<Item, TagKey<Item>> what = raw.item().isPresent() ? Either.left(raw.item().get()) : Either.right(raw.tag().orElseThrow());
			return DataResult.success(new Input(what, raw.count()));
		}
	}

	/** How a player learns it. Stored by id; append only. */
	public enum Learned {
		/** Known on reaching the level. */
		AUTO,
		/** Taught by a {@code schematic} item. */
		ITEM,
		/** Sold by a trainer. */
		TRAINER;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static final Codec<Learned> CODEC = Codec.STRING.comapFlatMap(s -> {
			for (Learned l : values()) {
				if (l.id().equals(s)) {
					return DataResult.success(l);
				}
			}

			return DataResult.error(() -> "Unknown learned " + s);
		}, Learned::id);
	}

	private static final Codec<Optional<Discipline>> DISCIPLINE_CODEC = Codec.STRING.comapFlatMap(s -> {
		if ("common".equals(s)) {
			return DataResult.success(Optional.<Discipline>empty());
		}

		Discipline d = Discipline.byId(s);
		return d != null ? DataResult.success(Optional.of(d)) : DataResult.error(() -> "Unknown discipline " + s);
	}, d -> d.map(Discipline::id).orElse("common"));

	private static final Codec<ItemStack> OUTPUT_CODEC = RecordCodecBuilder.create(i -> i.group(
			Input.ITEM_CODEC.fieldOf("item").forGetter(ItemStack::getItem),
			Codec.INT.optionalFieldOf("count", 1).forGetter(ItemStack::getCount)
	).apply(i, (item, count) -> new ItemStack(item, count)));

	public Schematic {
		level = Math.max(1, Math.min(5, level));
		inputs = List.copyOf(inputs);
		output = output.copy();
		craftTicks = Math.max(1, craftTicks);
		xp = Math.max(0, xp);
	}

	/** The output, copied (the record's stack is never handed out). */
	public ItemStack result() {
		return output.copy();
	}

	/** The codec of a schematic file, whose id is its file name. */
	public static Codec<Schematic> fileCodec(Identifier id) {
		return RecordCodecBuilder.create(i -> i.group(
				DISCIPLINE_CODEC.optionalFieldOf("discipline", Optional.empty()).forGetter(Schematic::discipline),
				Codec.INT.optionalFieldOf("level", 1).forGetter(Schematic::level),
				Input.CODEC.listOf().fieldOf("inputs").forGetter(Schematic::inputs),
				OUTPUT_CODEC.fieldOf("output").forGetter(Schematic::output),
				Codec.BOOL.optionalFieldOf("quality", false).forGetter(Schematic::quality),
				Codec.INT.optionalFieldOf("craft_ticks", 40).forGetter(Schematic::craftTicks),
				Codec.INT.optionalFieldOf("xp", 0).forGetter(Schematic::xp),
				Learned.CODEC.optionalFieldOf("learned", Learned.AUTO).forGetter(Schematic::learned)
		).apply(i, (discipline, level, inputs, output, quality, ticks, xp, learned)
				-> new Schematic(id, discipline, level, inputs, output, quality, ticks, xp, learned)));
	}
}

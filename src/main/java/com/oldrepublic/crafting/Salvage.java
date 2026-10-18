package com.oldrepublic.crafting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Salvage (spec MILESTONE_4.md 7.5): a crafted or bought weapon, armour piece or mod gives back half
 * of its schematic's inputs (rounded down, at least one of the first, main input), its fitted mods
 * unchanged, and a {@code masterwork_fragment} when it is Masterwork. Only items made by a
 * discipline schematic can be salvaged, so story items, M3 robes and plain materials never can;
 * named drops are refused by their {@code named} component. Common code: the screen previews the
 * same list the server gives.
 */
public final class Salvage {
	/** The discipline schematic that makes {@code stack}'s item, or null. Masterwork-only recipes are skipped. */
	public static @Nullable Schematic schematicFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}

		for (Schematic s : Schematics.all()) {
			if (s.discipline().isPresent() && s.output().getCount() == 1 && s.output().is(stack.getItem()) && !Crafting.isMasterworkOnly(s)) {
				return s;
			}
		}

		return null;
	}

	/** Whether {@code stack} can be salvaged. */
	public static boolean canSalvage(ItemStack stack) {
		return !stack.isEmpty() && !stack.has(M4Components.NAMED) && schematicFor(stack) != null;
	}

	/** What salvaging {@code stack} gives, with {@code ratio} of each input; empty when it cannot be salvaged. */
	public static List<ItemStack> returns(ItemStack stack, float ratio) {
		List<ItemStack> out = new ArrayList<>();
		Schematic s = canSalvage(stack) ? schematicFor(stack) : null;

		if (s == null) {
			return out;
		}

		for (int i = 0; i < s.inputs().size(); i++) {
			Schematic.Input input = s.inputs().get(i);
			int count = CraftingRules.salvageCount(input.count(), i == 0, ratio);
			Item item = input.what().map(it -> it, tag -> {
				Iterator<Holder<Item>> members = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator();
				return members.hasNext() ? members.next().value() : null;
			});

			if (count > 0 && item != null) {
				addStacked(out, new ItemStack(item, count));
			}
		}

		for (ItemStack mod : Mods.fitted(stack).mods().values()) {
			out.add(mod.copy());
		}

		if (Crafting.quality(stack) == Quality.MASTERWORK) {
			out.add(new ItemStack(M4Items.MASTERWORK_FRAGMENT));
		}

		return out;
	}

	/** Discipline XP for salvaging {@code stack}: a share of its schematic's craft XP. */
	public static int xp(ItemStack stack, float share) {
		Schematic s = canSalvage(stack) ? schematicFor(stack) : null;
		return s == null ? 0 : Math.round(s.xp() * share);
	}

	/** Adds {@code stack} split into stacks of at most the item's maximum size. */
	private static void addStacked(List<ItemStack> out, ItemStack stack) {
		int max = Math.max(1, stack.getMaxStackSize());

		while (stack.getCount() > max) {
			out.add(stack.split(max));
		}

		out.add(stack);
	}

	private Salvage() {
	}
}

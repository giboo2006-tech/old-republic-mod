package com.oldrepublic.crafting;

import com.oldrepublic.registry.M4Components;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Disciplines, known schematics and quality for other features (spec MILESTONE_4.md 7, 15.1).
 * Signatures are frozen. Phase 0 writes the reads and the quality component; W5 writes the
 * learning rules and {@link #roll}.
 */
public final class Crafting {
	public static boolean has(ServerPlayer p, Discipline d) {
		return CraftingAttachments.get(p).has(d);
	}

	/** The player's level in {@code d} (1 to 5), or 0 without it. */
	public static int level(ServerPlayer p, Discipline d) {
		return CraftingAttachments.get(p).progress(d).map(CraftingData.Progress::level).orElse(0);
	}

	public static boolean knows(ServerPlayer p, Identifier schematic) {
		return CraftingAttachments.get(p).known().contains(schematic);
	}

	/** Teaches {@code schematic} (story rewards, vendors and schematic items call this). */
	public static void teach(ServerPlayer p, Identifier schematic) {
		CraftingData data = CraftingAttachments.get(p);

		if (!data.known().contains(schematic)) {
			CraftingAttachments.set(p, data.withKnown(schematic));
		}
	}

	/** Rolls the quality of one craft of {@code s} for {@code p}, pity included (7.4). */
	public static Quality roll(ServerPlayer p, Schematic s) {
		return roll(p, s, p.getRandom());
	}

	/** {@link #roll(ServerPlayer, Schematic)} with a given random source (tests seed it). */
	public static Quality roll(ServerPlayer p, Schematic s, RandomSource random) {
		return Quality.STANDARD;
	}

	/** The stack's quality; Standard when it has none. */
	public static Quality quality(ItemStack stack) {
		Integer q = stack.get(M4Components.QUALITY);
		return q == null ? Quality.STANDARD : Quality.of(q);
	}

	/** {@code stack} with quality {@code q} (the same stack, changed and returned). */
	public static ItemStack withQuality(ItemStack stack, Quality q) {
		stack.set(M4Components.QUALITY, q.ordinal());
		return stack;
	}

	private Crafting() {
	}
}

package com.oldrepublic.combat;

import java.util.function.Predicate;

import com.oldrepublic.equipment.ArmourSets;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Resolves the {@link Allegiance} of any entity. Pure reads, safe on both sides. */
public final class Allegiances {
	/** Nothing is confused until the powers feature installs its test. */
	private static Predicate<Entity> confused = e -> false;

	/** The entity's side: its own for {@link Aligned} entities, the worn uniform's for players, else {@link Allegiance#NONE}. */
	public static Allegiance of(Entity e) {
		if (e instanceof Aligned aligned) {
			return aligned.allegiance();
		}

		if (e instanceof Player player) {
			return ArmourSets.disguise(player);
		}

		return Allegiance.NONE;
	}

	/**
	 * Whether {@code a} and {@code b} fight for the same side; {@link Allegiance#NONE} is never friendly, and neither is an
	 * entity under Mind Trick's Confusion, nor anything it meets (MILESTONE_5.md 9.8).
	 */
	public static boolean friendly(Entity a, Entity b) {
		Allegiance side = of(a);
		return side != Allegiance.NONE && side == of(b) && !confused.test(a) && !confused.test(b);
	}

	/**
	 * Installs the test for "this entity is confused" (W6, Mind Trick's Confusion, MILESTONE_5.md 9.8). While it holds for
	 * either entity, {@link #friendly} is false, so a confused mob turns on its own side and its victims answer. The test runs
	 * wherever {@link #friendly} does, so it must be a cheap read that is safe on both sides.
	 */
	public static void setConfusedTest(Predicate<Entity> test) {
		confused = test;
	}

	private Allegiances() {
	}
}

package com.oldrepublic.combat;

import com.oldrepublic.equipment.ArmourSets;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Resolves the {@link Allegiance} of any entity. Pure reads, safe on both sides. */
public final class Allegiances {
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

	/** Whether {@code a} and {@code b} fight for the same side; {@link Allegiance#NONE} is never friendly. */
	public static boolean friendly(Entity a, Entity b) {
		Allegiance side = of(a);
		return side != Allegiance.NONE && side == of(b);
	}

	private Allegiances() {
	}
}

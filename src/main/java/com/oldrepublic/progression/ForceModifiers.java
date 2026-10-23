package com.oldrepublic.progression;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Combines every registered {@link ForceModifier}. Cost and regen multiply and are clamped to the
 * {@code progression} balance ranges ({@code modifierCostMin}/{@code Max}, 0.70 to 1.50, and
 * {@code modifierRegenMin}/{@code Max}, 0.50 to 1.50); the max energy bonuses add up. With nothing
 * registered every answer is neutral. Register during init. Server side.
 */
public final class ForceModifiers {
	private static final List<ForceModifier> MODIFIERS = new ArrayList<>();

	/** Adds a modifier source. */
	public static void register(ForceModifier m) {
		MODIFIERS.add(m);
	}

	/** Product of every cost multiplier for {@code abilityId}, clamped to 0.70 to 1.50 (balance). */
	public static float cost(Player p, String abilityId) {
		if (MODIFIERS.isEmpty()) {
			return 1f;
		}

		float mult = 1f;

		for (ForceModifier m : MODIFIERS) {
			mult *= m.costMultiplier(p, abilityId);
		}

		ProgressionBalance b = Progression.balance();
		return Mth.clamp(mult, b.modifierCostMin, b.modifierCostMax);
	}

	/** Product of every regen multiplier, clamped to 0.50 to 1.50 (balance). */
	public static float regen(Player p) {
		if (MODIFIERS.isEmpty()) {
			return 1f;
		}

		float mult = 1f;

		for (ForceModifier m : MODIFIERS) {
			mult *= m.regenMultiplier(p);
		}

		ProgressionBalance b = Progression.balance();
		return Mth.clamp(mult, b.modifierRegenMin, b.modifierRegenMax);
	}

	/** Sum of every max energy bonus (FE). */
	public static int maxEnergyBonus(Player p) {
		int sum = 0;

		for (ForceModifier m : MODIFIERS) {
			sum += m.maxEnergyBonus(p);
		}

		return sum;
	}

	private ForceModifiers() {
	}
}

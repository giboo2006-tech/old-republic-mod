package com.oldrepublic.progression;

import net.minecraft.world.entity.player.Player;

/**
 * One source of Force Energy modifiers outside the skill counters: worn armour traits (W4), the
 * carried-holocron regen bonus (W3). Register through {@link ForceModifiers#register}; every method
 * has a neutral default. Called on every energy commit and regen tick, so keep it cheap and never
 * change {@link ForceData} from here. Server side.
 */
public interface ForceModifier {
	/** Multiplier on the Force Energy cost of {@code abilityId}. */
	default float costMultiplier(Player p, String abilityId) {
		return 1f;
	}

	/** Multiplier on energy regeneration per second. */
	default float regenMultiplier(Player p) {
		return 1f;
	}

	/** Flat bonus to max energy (FE), added before the skills' percent bonus. */
	default int maxEnergyBonus(Player p) {
		return 0;
	}
}

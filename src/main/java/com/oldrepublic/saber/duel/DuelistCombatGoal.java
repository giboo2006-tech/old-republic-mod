package com.oldrepublic.saber.duel;

import java.util.function.Supplier;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Saber duel behaviour for NPCs: approach, circle, guard, attack strings, the profile's Force move,
 * and backing off when the guard is broken.
 *
 * <p>Phase 0 stub: never starts. The saber feature implements it.
 */
public class DuelistCombatGoal extends Goal {
	private final Mob mob;
	private final SaberDuelist duelist;
	private final Supplier<DuelistProfile> profile;

	public <T extends Mob & SaberDuelist> DuelistCombatGoal(T mob, Supplier<DuelistProfile> profile) {
		this.mob = mob;
		this.duelist = mob;
		this.profile = profile;
	}

	@Override
	public boolean canUse() {
		return false;
	}

	protected Mob mob() {
		return mob;
	}

	protected SaberDuelist duelist() {
		return duelist;
	}

	protected DuelistProfile profile() {
		return profile.get();
	}
}

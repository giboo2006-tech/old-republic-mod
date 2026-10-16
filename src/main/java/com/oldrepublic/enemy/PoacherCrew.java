package com.oldrepublic.enemy;

import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;

/**
 * A member of a poacher crew (poachers and their Captain). Crews are {@link Allegiance#CREW}, so
 * their blaster bolts never hit each other and troopers fight them on sight.
 */
public interface PoacherCrew extends Aligned {
	@Override
	default Allegiance allegiance() {
		return Allegiance.CREW;
	}
}

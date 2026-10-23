package com.oldrepublic.enemy.cartel;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.identity.Faction;

/**
 * A Milestone 5 foe whose attitude follows a faction's standing (spec MILESTONE_5.md 7, 12.1): the
 * Vessba kajidic's cartel enforcers and Clan Hask's warriors. {@link StandingRule} answers
 * {@code Attitudes} for every such foe, and {@link StandingTargets} picks its fights.
 */
public interface FactionFoe {
	/** The faction whose standing sets the attitude. */
	Faction faction();

	/**
	 * The attitude toward the owner and the owner's party of a foe spawned with an owner
	 * ({@code StoryTags} owner tag), whatever the standing: a cartel group spawned for a mission
	 * stands against its owner ({@link Attitude#HOSTILE}); a Hask group spawned for the clan's way
	 * fights beside its owner ({@link Attitude#ALLIED}, 6.3).
	 */
	Attitude ownedAttitude();

	/** The foe's per-player fight bookkeeping. */
	StandingState standing();

	/** Whether it is a hub guard (Saltgate's walls, the holdfast's posts): it never starts a fight near a pad bay or the medbay. */
	boolean isHubGuard();

	/** Players this close to the owner of an owned foe count as the owner's party (the faction's balance). */
	double partyRange();
}

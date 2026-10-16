package com.oldrepublic.skill;

import java.util.Set;

import com.oldrepublic.identity.Path;

/**
 * The facts about a player that the skill rules depend on, gathered in one value so the rules can
 * be pure functions (the same on server, client and in tests).
 *
 * @param awakened     the Force has awakened in the player
 * @param level        attunement level
 * @param skillPoints  unspent skill points
 * @param alignment    -100 to +100
 * @param path         the path chosen at the Crossing
 * @param visibleTrees ids of the tree tabs the player can see
 * @param busy         an ability is charging or channelling
 * @param inCombat     damage was dealt or taken within the combat lock
 */
public record SkillContext(boolean awakened, int level, int skillPoints, int alignment, Path path, Set<String> visibleTrees, boolean busy,
		boolean inCombat) {
}

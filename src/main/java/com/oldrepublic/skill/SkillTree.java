package com.oldrepublic.skill;

import java.util.List;

/**
 * One tab of the Force screen: a set of nodes and the rule for when the tab appears.
 *
 * @param id         tree id (file name), for example {@code universal}
 * @param order      tab order, lowest first; also the {@code node_learned} effect variant
 * @param visibility when the tab appears
 * @param tint       ARGB side tint for the tab (0 = none)
 * @param nodes      the nodes, in file order
 */
public record SkillTree(String id, int order, TreeVisibility visibility, int tint, List<SkillNode> nodes) {
	/** Lang key of the tab name. */
	public String nameKey() {
		return "skill.oldrepublic.tree." + id;
	}
}

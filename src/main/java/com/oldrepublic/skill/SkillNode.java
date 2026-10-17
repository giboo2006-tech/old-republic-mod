package com.oldrepublic.skill;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Path;

import net.minecraft.resources.Identifier;

/**
 * One node of a skill tree, as loaded from {@code data/oldrepublic/skill_tree/<tree>.json}. Immutable.
 *
 * @param id          globally unique node id (for ability nodes also the ability id)
 * @param tree        id of the tree it belongs to
 * @param group       exclusive upgrade group, or null
 * @param ability     the ability this node unlocks ({@link NodeKind#ABILITY}) or upgrades, or null
 * @param cost        skill points per rank (empty for granted-only nodes)
 * @param minLevel    minimum attunement level per rank
 * @param requires    prerequisites, all of which must be owned (at their rank) and not dormant
 * @param path        the path that may buy it and keeps it awake, or null for any
 * @param alignmentMin minimum alignment to buy it and keep it awake, or null
 * @param alignmentMax maximum alignment, or null
 * @param grantedOnly cannot be bought; quests and lessons grant it
 * @param taughtBy    paths whose mentor lessons grant this node (the tooltip warns before buying)
 * @param grantedHint lang key that says where a granted-only node comes from, or null
 * @param x           column in the tree view (may be fractional, for centring)
 * @param y           row in the tree view
 * @param icon        GUI sprite of the node's icon
 * @param info        value keys the tooltip shows, looked up in {@code infoSource}'s info map
 * @param infoSource  where the info values come from: an ability id or a {@link NodeInfo} source
 */
public record SkillNode(String id, String tree, NodeKind kind, @Nullable String group, @Nullable String ability, List<Integer> cost,
		List<Integer> minLevel, List<Requirement> requires, @Nullable Path path, @Nullable Integer alignmentMin, @Nullable Integer alignmentMax,
		boolean grantedOnly, List<Path> taughtBy, @Nullable String grantedHint, float x, float y, Identifier icon, List<String> info,
		String infoSource) {
	/** A prerequisite: node {@code node} owned at {@code rank} or higher. */
	public record Requirement(String node, int rank) {
	}

	/** Number of ranks (at least 1). */
	public int maxRank() {
		return Math.max(1, cost.size());
	}

	/** Skill points for rank {@code rank} (1-based); 0 for granted-only nodes. */
	public int costOf(int rank) {
		return cost.isEmpty() ? 0 : cost.get(Math.min(cost.size(), Math.max(1, rank)) - 1);
	}

	/** Minimum level for rank {@code rank} (1-based); 0 when none is set. */
	public int levelFor(int rank) {
		return minLevel.isEmpty() ? 0 : minLevel.get(Math.min(minLevel.size(), Math.max(1, rank)) - 1);
	}

	public boolean isAbility() {
		return kind == NodeKind.ABILITY;
	}

	/** True when the alignment requirement holds for {@code alignment}. */
	public boolean alignmentMet(int alignment) {
		return (alignmentMin == null || alignment >= alignmentMin) && (alignmentMax == null || alignment <= alignmentMax);
	}

	/** Lang key of the node's name. */
	public String nameKey() {
		return "skill.oldrepublic.node." + id + ".name";
	}

	/** Lang key of the node's description. */
	public String descKey() {
		return "skill.oldrepublic.node." + id + ".desc";
	}
}

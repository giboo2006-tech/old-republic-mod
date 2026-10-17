package com.oldrepublic.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Every loaded skill tree, with lookups by node id, exclusive group and ability id. Immutable; the
 * server builds one per data load and clients get the same definitions through the
 * {@code skill_trees} payload.
 */
public final class SkillTreeSet {
	public static final SkillTreeSet EMPTY = new SkillTreeSet(List.of());

	private final List<SkillTree> trees;
	private final Map<String, SkillTree> byId = new LinkedHashMap<>();
	private final Map<String, SkillNode> nodes = new LinkedHashMap<>();
	private final Map<String, List<SkillNode>> groups = new HashMap<>();
	private final Map<String, SkillNode> abilityNodes = new HashMap<>();
	private final List<String> errors = new ArrayList<>();

	public SkillTreeSet(Collection<SkillTree> trees) {
		List<SkillTree> sorted = new ArrayList<>(trees);
		sorted.sort(Comparator.comparingInt(SkillTree::order).thenComparing(SkillTree::id));
		this.trees = List.copyOf(sorted);

		for (SkillTree tree : this.trees) {
			byId.put(tree.id(), tree);

			for (SkillNode node : tree.nodes()) {
				if (nodes.putIfAbsent(node.id(), node) != null) {
					errors.add("Duplicate node id '" + node.id() + "' in tree " + tree.id());
					continue;
				}

				if (node.group() != null) {
					groups.computeIfAbsent(node.group(), g -> new ArrayList<>()).add(node);
				}

				if (node.isAbility()) {
					abilityNodes.put(node.ability() != null ? node.ability() : node.id(), node);
				}
			}
		}

		validate();
	}

	/** The trees in tab order. */
	public List<SkillTree> trees() {
		return trees;
	}

	public @Nullable SkillTree tree(String id) {
		return byId.get(id);
	}

	public @Nullable SkillNode node(String id) {
		return nodes.get(id);
	}

	public Collection<SkillNode> nodes() {
		return nodes.values();
	}

	/** Members of an exclusive group (empty for an unknown group). */
	public List<SkillNode> group(String group) {
		return groups.getOrDefault(group, List.of());
	}

	/** The node that unlocks ability {@code abilityId}, or null. */
	public @Nullable SkillNode abilityNode(String abilityId) {
		return abilityNodes.get(abilityId);
	}

	/** Problems found while building the set (unknown references, cycles, duplicate ids). */
	public List<String> errors() {
		return List.copyOf(errors);
	}

	/**
	 * The ability whose info map feeds a node's numbers: the node's own ability, or the first
	 * required ability node (for branches), or null.
	 */
	public @Nullable String infoAbility(SkillNode node) {
		if (node.ability() != null) {
			return node.ability();
		}

		for (SkillNode.Requirement req : node.requires()) {
			SkillNode parent = nodes.get(req.node());

			if (parent != null && parent.isAbility()) {
				return parent.ability() != null ? parent.ability() : parent.id();
			}
		}

		return null;
	}

	private void validate() {
		for (SkillNode node : nodes.values()) {
			for (SkillNode.Requirement req : node.requires()) {
				SkillNode target = nodes.get(req.node());

				if (target == null) {
					errors.add("Node '" + node.id() + "' requires unknown node '" + req.node() + "'");
				} else if (req.rank() > target.maxRank()) {
					errors.add("Node '" + node.id() + "' requires rank " + req.rank() + " of '" + req.node() + "', which has " + target.maxRank());
				}
			}

			if (node.cost().isEmpty() && !node.grantedOnly()) {
				errors.add("Node '" + node.id() + "' has no cost and is not granted_only");
			}

			if (!node.minLevel().isEmpty() && node.minLevel().size() != node.cost().size()) {
				errors.add("Node '" + node.id() + "' has " + node.cost().size() + " costs but " + node.minLevel().size() + " level requirements");
			}
		}

		for (Map.Entry<String, List<SkillNode>> group : groups.entrySet()) {
			if (group.getValue().size() < 2) {
				errors.add("Group '" + group.getKey() + "' has fewer than two nodes");
			}
		}

		Set<String> done = new HashSet<>();

		for (SkillNode node : nodes.values()) {
			if (hasCycle(node.id(), new HashSet<>(), done)) {
				errors.add("Requirement cycle through node '" + node.id() + "'");
			}
		}
	}

	private boolean hasCycle(String id, Set<String> path, Set<String> done) {
		if (done.contains(id)) {
			return false;
		}

		if (!path.add(id)) {
			return true;
		}

		SkillNode node = nodes.get(id);

		if (node != null) {
			for (SkillNode.Requirement req : node.requires()) {
				if (hasCycle(req.node(), path, done)) {
					return true;
				}
			}
		}

		path.remove(id);
		done.add(id);
		return false;
	}
}

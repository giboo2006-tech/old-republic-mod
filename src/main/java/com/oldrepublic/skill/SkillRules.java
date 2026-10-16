package com.oldrepublic.skill;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The skill-tree rules as pure functions over {@link SkillTreeSet}, {@link SkillData} and
 * {@link SkillContext}: what can be bought and why not, dormancy, stat modifiers, respec maths and
 * tab visibility. The server applies them in {@link SkillService}; the Force screen calls the same
 * functions to show states and reasons, so both always agree.
 */
public final class SkillRules {
	/** Outcome of a purchase check, in the order the checks run. */
	public enum LearnResult {
		OK,
		UNKNOWN,
		NOT_AWAKENED,
		TAB_HIDDEN,
		GRANTED_ONLY,
		LEVEL_TOO_LOW,
		MISSING_REQUIREMENT,
		WRONG_PATH,
		ALIGNMENT,
		MAXED,
		NOT_ENOUGH_POINTS,
		SWITCH_BUSY,
		SWITCH_COMBAT;

		/** Lang key of the explanation (arguments: see {@link LearnCheck#messageArgs}). */
		public String messageKey() {
			return "message.oldrepublic.skill.learn." + name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * A purchase check.
	 *
	 * @param result   the first failing rule, or OK
	 * @param node     the node, or null when unknown
	 * @param nextRank the rank a purchase would reach (1-based)
	 * @param cost     skill points it would cost, including a switch penalty
	 * @param replaces the owned sibling a switch would remove, or null
	 * @param missing  the first unmet prerequisite, or null
	 */
	public record LearnCheck(LearnResult result, @Nullable SkillNode node, int nextRank, int cost, @Nullable SkillNode replaces,
			SkillNode.@Nullable Requirement missing) {
		public boolean ok() {
			return result == LearnResult.OK;
		}

		public boolean isSwitch() {
			return replaces != null;
		}

		/** Arguments for {@link LearnResult#messageKey()}: level, cost, missing node id, path id. */
		public Object[] messageArgs() {
			return new Object[] {node == null ? 0 : node.levelFor(nextRank), cost, missing == null ? "" : missing.node(),
					node == null || node.path() == null ? "" : node.path().id()};
		}
	}

	/** Runs the purchase checks for one more rank of {@code nodeId}. */
	public static LearnCheck check(SkillTreeSet trees, SkillData data, SkillContext ctx, String nodeId, int switchPenalty) {
		SkillNode node = trees.node(nodeId);

		if (node == null) {
			return new LearnCheck(LearnResult.UNKNOWN, null, 1, 0, null, null);
		}

		int owned = data.rank(nodeId);
		int next = owned + 1;
		boolean maxed = next > node.maxRank();
		SkillNode replaces = sibling(trees, data, node);
		int cost = maxed ? 0 : node.costOf(next) + (replaces != null ? switchPenalty : 0);
		LearnResult result = LearnResult.OK;
		SkillNode.Requirement missing = null;

		if (!ctx.awakened()) {
			result = LearnResult.NOT_AWAKENED;
		} else if (!ctx.visibleTrees().contains(node.tree())) {
			result = LearnResult.TAB_HIDDEN;
		} else if (node.grantedOnly()) {
			result = LearnResult.GRANTED_ONLY;
		} else if (!maxed && ctx.level() < node.levelFor(next)) {
			result = LearnResult.LEVEL_TOO_LOW;
		} else if ((missing = missingRequirement(trees, data, ctx, node)) != null) {
			result = LearnResult.MISSING_REQUIREMENT;
		} else if (node.path() != null && node.path() != ctx.path()) {
			result = LearnResult.WRONG_PATH;
		} else if (!node.alignmentMet(ctx.alignment())) {
			result = LearnResult.ALIGNMENT;
		} else if (maxed) {
			result = LearnResult.MAXED;
		} else if (ctx.skillPoints() < cost) {
			result = LearnResult.NOT_ENOUGH_POINTS;
		} else if (replaces != null && ctx.busy()) {
			result = LearnResult.SWITCH_BUSY;
		} else if (replaces != null && ctx.inCombat()) {
			result = LearnResult.SWITCH_COMBAT;
		}

		return new LearnCheck(result, node, Math.min(next, node.maxRank()), cost, replaces, missing);
	}

	/** The owned node in the same exclusive group, or null. */
	public static @Nullable SkillNode sibling(SkillTreeSet trees, SkillData data, SkillNode node) {
		if (node.group() == null) {
			return null;
		}

		for (SkillNode other : trees.group(node.group())) {
			if (!other.id().equals(node.id()) && data.owns(other.id())) {
				return other;
			}
		}

		return null;
	}

	/** The first prerequisite that is not owned at its rank or is dormant, or null. */
	public static SkillNode.@Nullable Requirement missingRequirement(SkillTreeSet trees, SkillData data, SkillContext ctx, SkillNode node) {
		for (SkillNode.Requirement req : node.requires()) {
			SkillNode target = trees.node(req.node());

			if (data.rank(req.node()) < req.rank() || target == null || dormant(target, ctx)) {
				return req;
			}
		}

		return null;
	}

	/**
	 * True when an owned node has no effect because its path or alignment requirement is no longer
	 * met. Ability nodes are never dormant (their powers follow the opposed / refused rule instead).
	 */
	public static boolean dormant(SkillNode node, SkillContext ctx) {
		if (node.isAbility()) {
			return false;
		}

		return (node.path() != null && node.path() != ctx.path()) || !node.alignmentMet(ctx.alignment());
	}

	/** Owned and not dormant. Unknown nodes count as owned only by rank. */
	public static boolean active(SkillTreeSet trees, SkillData data, SkillContext ctx, String nodeId) {
		if (!data.owns(nodeId)) {
			return false;
		}

		SkillNode node = trees.node(nodeId);
		return node == null || !dormant(node, ctx);
	}

	/** Effective rank: the owned rank, or 0 while dormant. */
	public static int activeRank(SkillTreeSet trees, SkillData data, SkillContext ctx, String nodeId) {
		return active(trees, data, ctx, nodeId) ? data.rank(nodeId) : 0;
	}

	/** Tab visibility from the player's progress. */
	public static Set<String> visibleTrees(SkillTreeSet trees, boolean pushKnown, boolean saberBuilt, boolean crossed) {
		Set<String> visible = new HashSet<>();

		for (SkillTree tree : trees.trees()) {
			boolean shown = switch (tree.visibility()) {
				case PUSH -> pushKnown;
				case SABER -> saberBuilt;
				case CROSSING -> crossed;
			};

			if (shown) {
				visible.add(tree.id());
			}
		}

		return visible;
	}

	/**
	 * Force Energy modifiers from the energy passives, as {@link com.oldrepublic.progression.Progression}
	 * reads them from {@code ForceData.counters}.
	 */
	public record StatMods(int maxEnergy, int maxEnergyPct, int regenPct, int regenDelay) {
		public static final StatMods NONE = new StatMods(0, 0, 0, 0);
	}

	public static StatMods statMods(SkillTreeSet trees, SkillData data, SkillContext ctx, SkillBalance b) {
		int reserves = activeRank(trees, data, ctx, SkillIds.DEEP_RESERVES);
		int flow = activeRank(trees, data, ctx, SkillIds.QUICKENED_FLOW);
		boolean calm = active(trees, data, ctx, SkillIds.CALM_FOCUS);
		return new StatMods(
				reserves * b.deepReservesPerRank,
				calm ? b.calmFocusMaxEnergyPct : 0,
				flow * b.quickenedRegenPctPerRank,
				flow * b.quickenedDelayPerRank + (calm ? b.calmFocusDelay : 0));
	}

	/** Points a full respec keeps back: 10% of the spent points, rounded up, at least 1 (0 if nothing was spent). */
	public static int respecLoss(int spent, float lossFraction) {
		if (spent <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.ceil(spent * lossFraction - 1.0e-6));
	}

	/** Points a full respec gives back. */
	public static int respecRefund(int spent, float lossFraction) {
		return Math.max(0, spent - respecLoss(spent, lossFraction));
	}

	/**
	 * Why a full respec is not possible right now (a lang key), or null when it is.
	 *
	 * @param nearAnchor a mentor (or, for Independents, a meditation stone) is close enough
	 */
	public static @Nullable String respecBlock(SkillData data, long gameTime, boolean inCombat, boolean nearAnchor, SkillBalance b) {
		if (data.spentTotal() <= 0) {
			return "message.oldrepublic.skill.respec.nothing";
		}

		if (!nearAnchor) {
			return "message.oldrepublic.skill.respec.no_anchor";
		}

		if (inCombat) {
			return "message.oldrepublic.skill.respec.combat";
		}

		if (respecReadyAt(data, b) > gameTime) {
			return "message.oldrepublic.skill.respec.cooldown";
		}

		return null;
	}

	/** Game time from which the next respec is allowed. */
	public static long respecReadyAt(SkillData data, SkillBalance b) {
		return data.lastRespec == SkillData.NEVER ? Long.MIN_VALUE : data.lastRespec + b.respecCooldownTicks;
	}

	/** Open ability slots: the base count, plus one after the first rank (or "No Master"). */
	public static int openSlots(boolean firstRank, SkillBalance b) {
		return Math.max(0, Math.min(SkillData.SLOT_COUNT, firstRank ? b.maxSlots : b.baseSlots));
	}

	/** The first open slot that is empty, or -1. */
	public static int firstFreeSlot(SkillData data) {
		for (int i = 0; i < Math.min(data.openSlots, SkillData.SLOT_COUNT); i++) {
			if (data.slot(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	/** {@code push_repulse_wave} to the Milestone 1 branch id {@code repulse_wave}, or {@code ""}. */
	public static String pushBranchMirror(SkillTreeSet trees, SkillData data) {
		for (SkillNode node : trees.group(SkillIds.PUSH_GROUP)) {
			if (data.owns(node.id()) && node.id().startsWith(SkillIds.PUSH_PREFIX)) {
				return node.id().substring(SkillIds.PUSH_PREFIX.length());
			}
		}

		return "";
	}

	private SkillRules() {
	}
}

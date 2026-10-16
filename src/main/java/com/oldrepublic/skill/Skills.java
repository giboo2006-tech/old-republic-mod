package com.oldrepublic.skill;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityRuntime;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read and grant access to the skill trees for other features. Readers work on both sides (the
 * skills attachment is synced to its owner, and the client holds the trees the server sent).
 */
public final class Skills {
	/** Owned rank of a node, 0 if not owned. */
	public static int rank(Player p, String nodeId) {
		SkillData data = data(p);
		return data == null ? 0 : data.rank(nodeId);
	}

	/** Owned and not dormant. */
	public static boolean active(Player p, String nodeId) {
		SkillData data = data(p);
		return data != null && SkillRules.active(SkillTrees.of(p), data, context(p), nodeId);
	}

	/** Owned rank while the node is active, otherwise 0. */
	public static int activeRank(Player p, String nodeId) {
		return active(p, nodeId) ? rank(p, nodeId) : 0;
	}

	/** Owned but without effect, because its path or alignment requirement is no longer met. */
	public static boolean dormant(Player p, String nodeId) {
		SkillNode node = SkillTrees.of(p).node(nodeId);
		return node != null && rank(p, nodeId) > 0 && SkillRules.dormant(node, context(p));
	}

	/** Node id of the active saber form, or {@code ""} for Unformed. */
	public static String activeForm(Player p) {
		SkillData data = data(p);
		return data == null ? "" : data.activeForm;
	}

	/** Sets the active form (the saber feature validates timing first). Returns whether it changed. */
	public static boolean setActiveForm(ServerPlayer p, String formNodeId) {
		return SkillService.setActiveForm(p, formNodeId);
	}

	/** Grants a node at no cost (lessons, trials). Never refunds a node already bought. */
	public static void grant(ServerPlayer p, String nodeId) {
		SkillService.grant(p, nodeId);
	}

	/** Number of open ability slots (3, or 4 after the first rank). */
	public static int slots(Player p) {
		SkillData data = data(p);
		return data == null ? 3 : Math.min(SkillData.SLOT_COUNT, data.openSlots);
	}

	/** Ability id in slot {@code index} (0-based), or {@code ""}. */
	public static String slot(Player p, int index) {
		SkillData data = data(p);
		return data == null || index >= slots(p) ? "" : data.slot(index);
	}

	/** True when the player dealt or took damage within the skills combat lock (server). */
	public static boolean inCombat(ServerPlayer p) {
		return CombatTracker.inCombat(p);
	}

	/** The synced skills data, or null on a client that has not received it yet. */
	public static @Nullable SkillData data(Player p) {
		return p instanceof ServerPlayer server ? SkillService.data(server) : p.getAttached(SkillAttachments.SKILLS);
	}

	/**
	 * The facts the rules need about {@code p}. On the client, {@code busy} and {@code inCombat}
	 * are false (the server checks them).
	 */
	public static SkillContext context(Player p) {
		ForceData force = p instanceof ServerPlayer server ? Progression.get(server) : p.getAttached(ForceAttachments.FORCE);
		SkillData skills = data(p);
		boolean awakened = force != null && force.isAwakened();
		boolean pushKnown = (force != null && force.hasAbility(SkillIds.FORCE_PUSH)) || (skills != null && skills.owns(SkillIds.FORCE_PUSH));
		boolean saberBuilt = force != null && force.questStage >= QuestStage.COMPLETE.ordinal();
		boolean crossed = Identity.path(p) != Path.NONE;
		SkillTreeSet trees = SkillTrees.of(p);

		if (!saberBuilt && skills != null) {
			SkillTree saber = trees.tree(SkillIds.TREE_SABER);

			if (saber != null) {
				saberBuilt = saber.nodes().stream().anyMatch(n -> skills.owns(n.id()));
			}
		}

		boolean server = p instanceof ServerPlayer;
		return new SkillContext(awakened, force == null ? 1 : force.level, force == null ? 0 : force.skillPoints, Identity.alignment(p), Identity.path(p),
				SkillRules.visibleTrees(trees, pushKnown, saberBuilt, crossed),
				server && AbilityRuntime.isBusy((ServerPlayer) p),
				server && CombatTracker.inCombat((ServerPlayer) p));
	}

	private Skills() {
	}
}

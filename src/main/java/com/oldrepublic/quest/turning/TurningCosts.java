package com.oldrepublic.quest.turning;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTreeSet;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What turning costs (spec MILESTONE_5.md 8.4), applied at the rite in the order the spec gives: the path (the story's
 * first, so {@code StoryService.onPathChanged} has nothing to restart), the rank cap, the paid ceremonies, the powers, the
 * old tree's bought nodes with half their points back, the slots, the two orders' standing and the two mentors' trust.
 */
final class TurningCosts {
	/** What the rite took and gave, for the chat summary and the game tests. */
	record Result(Path from, Path to, int rankBefore, int rankAfter, int refunded, List<String> revoked) {
	}

	/** Applies every cost of turning {@code kind} to the player. */
	static Result apply(ServerPlayer p, TurningKind kind) {
		TurningBalance b = TurningService.balance();
		Path from = TurningRules.from(kind);
		Path to = TurningRules.to(kind);
		Faction oldFaction = TurningRules.factionOf(from);
		Faction newFaction = TurningRules.factionOf(to);
		MentorRole oldMentor = MentorRole.of(from);
		MentorRole newMentor = MentorRole.of(to);

		// Both orders' standing is read before setPath, which makes the join changes of the new path once.
		int oldBefore = oldFaction == null ? 0 : Identity.reputation(p, oldFaction);
		int newBefore = newFaction == null ? 0 : Identity.reputation(p, newFaction);
		int rankBefore = Identity.rank(p);

		// The path: the story's first, then the identity's (forced: Jedi to Sith and back are otherwise refused).
		StoryData data = Story.get(p);
		data.path = to.id();
		Story.commit(p, data);
		Identity.setPath(p, to, true);

		// The rank: min(rank, 2) on the new path (promote never lowers a rank).
		int rankAfter = TurningRules.rankAfter(Math.max(1, Identity.rank(p)), b);
		Identity.setRank(p, rankAfter);

		// The ceremonies already paid: held again on the new path without their skill points and XP.
		data = Story.get(p);

		if (data.flags.remove(ChapterThreeRules.RANK3_DONE)) {
			data.flags.add(RankThree.RANK3_PAID);
		}

		Story.commit(p, data);
		IdentityData identity = Identity.get(p);

		if (identity.choices.remove(TurningRules.RANK4_DONE)) {
			identity.choices.add(TurningRules.RANK4_PAID);
		}

		identity.choices.removeIf(choice -> choice.startsWith(TurningRules.DARTH_PREFIX));
		Identity.commit(p, identity);

		// The old path's signature powers and rank grants go; then every node of its tree the player bought, half refunded.
		List<String> revoked = new ArrayList<>();

		for (String node : TurningRules.signatures(from)) {
			if (SkillService.revoke(p, node)) {
				revoked.add(node);
			}
		}

		for (String node : TurningRules.rankGrants(from)) {
			if (SkillService.revoke(p, node)) {
				revoked.add(node);
			}
		}

		int refunded = refundOldTree(p, from, b, revoked);
		restoreAbilities(p);

		// The new path's signature powers (granted, never refunded); the slots follow the rank (7 and 8 close).
		for (String node : TurningRules.signatures(to)) {
			Skills.grant(p, node);
		}

		SkillService.refresh(p);

		// The orders: the old one Hostile at best, the new one at least 150; each one change, so the toast shows the net.
		String source = kind == TurningKind.RETURN ? TurningRules.RETURN_SOURCE : TurningRules.FALL_SOURCE;

		if (oldFaction != null) {
			int end = TurningRules.oldFactionEnd(oldBefore, b);
			Identity.changeReputation(p, oldFaction, end - Identity.reputation(p, oldFaction), source, false);
		}

		if (newFaction != null) {
			int end = TurningRules.newFactionEnd(newBefore, b);
			Identity.changeReputation(p, newFaction, end - Identity.reputation(p, newFaction), source, false);
		}

		// The mentors: the new one at newTrust, the old one at 0 (there is no setter: one addTrust of the difference each).
		Identity.addTrust(p, newMentor.id(), b.newTrust - Identity.trust(p, newMentor.id()), source);
		Identity.addTrust(p, oldMentor.id(), -Identity.trust(p, oldMentor.id()), source);

		if (refunded > 0) {
			p.sendSystemMessage(Component.translatable("turning.oldrepublic.rite.refund", refunded));
		}

		return new Result(from, to, rankBefore, rankAfter, refunded, revoked);
	}

	/** Revokes every node of the old path's tree the player bought and refunds half of all the points spent on them. */
	private static int refundOldTree(ServerPlayer p, Path from, TurningBalance b, List<String> revoked) {
		String tree = TurningRules.treeOf(from);

		if (tree == null) {
			return 0;
		}

		SkillTreeSet trees = SkillTrees.server();
		SkillData skills = SkillService.data(p);
		List<String> bought = new ArrayList<>();
		int spent = 0;

		for (Map.Entry<String, Integer> entry : skills.spent.entrySet()) {
			SkillNode node = trees.node(entry.getKey());

			if (node != null && tree.equals(node.tree()) && skills.owns(node.id())) {
				bought.add(node.id());
				spent += Math.max(0, entry.getValue());
			}
		}

		for (String node : bought) {
			if (SkillService.revoke(p, node)) {
				revoked.add(node);
			}
		}

		int refund = TurningRules.refund(spent, b);

		if (refund > 0) {
			Progression.grantSkillPoints(p, refund);
		}

		return refund;
	}

	/**
	 * A revoked node takes its ability with it even when another owned node teaches the same one (Lift is learnt in the
	 * Jedi, the Sith and the exploration trees): every ability of a node still owned is known again.
	 */
	private static void restoreAbilities(ServerPlayer p) {
		SkillTreeSet trees = SkillTrees.server();
		SkillData skills = SkillService.data(p);
		ForceData force = Progression.get(p);
		boolean changed = false;

		for (String id : skills.nodes.keySet()) {
			SkillNode node = trees.node(id);

			if (node != null && node.isAbility()) {
				changed |= force.abilities.add(node.ability() != null ? node.ability() : node.id());
			}
		}

		if (changed) {
			Progression.commit(p, force);
		}
	}

	private TurningCosts() {
	}
}

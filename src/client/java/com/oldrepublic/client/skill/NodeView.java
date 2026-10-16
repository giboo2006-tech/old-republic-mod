package com.oldrepublic.client.skill;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.ability.ClientAbilityInfo;
import com.oldrepublic.client.ui.NodeSocket;
import com.oldrepublic.identity.Path;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillContext;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillTreeSet;

import net.minecraft.network.chat.Component;

/**
 * How one node looks to the local player right now: its socket state, owned rank, the purchase
 * check the server would run (minus the combat and busy checks, which only the server knows) and
 * a short reason for a locked or dormant node. Computed from the synced skills data and trees.
 *
 * @param check      the purchase check for the next rank
 * @param reason     why the node is locked or dormant (short), or null
 * @param pathLabel  "Jedi path" style label for a path node the player is not on, or null
 */
record NodeView(SkillNode node, NodeSocket.State state, int rank, boolean granted, SkillRules.LearnCheck check, @Nullable Component reason,
		@Nullable Component pathLabel) {
	/** Switch penalty from the server's numbers (the {@code skills} info source). */
	static int switchPenalty() {
		Float value = ClientAbilityInfo.value("skills", "switch_penalty");
		return value == null ? new SkillBalance().switchPenalty : Math.round(value);
	}

	static NodeView of(SkillTreeSet trees, SkillData data, SkillContext ctx, SkillNode node) {
		int rank = data.rank(node.id());
		SkillRules.LearnCheck check = SkillRules.check(trees, data, ctx, node.id(), switchPenalty());
		NodeSocket.State state;
		Component reason = null;

		if (rank > 0 && SkillRules.dormant(node, ctx)) {
			state = NodeSocket.State.DORMANT;
			reason = dormantReason(node, ctx);
		} else if (rank > 0) {
			state = NodeSocket.State.OWNED;
		} else if (check.ok()) {
			state = NodeSocket.State.AVAILABLE;
		} else {
			state = NodeSocket.State.LOCKED;
			reason = lockReason(node, check);
		}

		Component pathLabel = node.path() != null && node.path() != ctx.path() ? pathName(node.path()) : null;
		return new NodeView(node, state, rank, data.isGranted(node.id()), check, reason, pathLabel);
	}

	/** The first failed purchase rule, short enough to sit under a node name. */
	static @Nullable Component lockReason(SkillNode node, SkillRules.LearnCheck check) {
		return switch (check.result()) {
			case OK, UNKNOWN, TAB_HIDDEN -> null;
			case NOT_AWAKENED -> Component.translatable("skill.oldrepublic.req.awakened");
			case GRANTED_ONLY -> node.grantedHint() != null ? Component.translatable(node.grantedHint())
					: Component.translatable("skill.oldrepublic.req.granted");
			case LEVEL_TOO_LOW -> Component.translatable("skill.oldrepublic.req.level", node.levelFor(check.nextRank()));
			case MISSING_REQUIREMENT -> check.missing() == null ? null : requirement(check.missing());
			case WRONG_PATH -> node.path() == null ? null : Component.translatable("skill.oldrepublic.req.path", pathName(node.path()));
			case ALIGNMENT -> alignment(node);
			case MAXED -> Component.translatable("skill.oldrepublic.state.maxed");
			case NOT_ENOUGH_POINTS -> Component.translatable("skill.oldrepublic.req.points", check.cost());
			case SWITCH_BUSY -> Component.translatable("skill.oldrepublic.req.not_busy");
			case SWITCH_COMBAT -> Component.translatable("skill.oldrepublic.req.out_of_combat");
		};
	}

	/** Why an owned node has no effect. */
	static Component dormantReason(SkillNode node, SkillContext ctx) {
		if (node.path() != null && node.path() != ctx.path()) {
			return Component.translatable("skill.oldrepublic.req.path", pathName(node.path()));
		}

		Component alignment = alignment(node);
		return alignment != null ? alignment : Component.translatable("skill.oldrepublic.state.dormant");
	}

	/** "Force Pull" or "Steady Guard rank 2". */
	static Component requirement(SkillNode.Requirement req) {
		Component name = Component.translatable("skill.oldrepublic.node." + req.node() + ".name");
		return req.rank() > 1 ? Component.translatable("skill.oldrepublic.req.node_rank", name, req.rank()) : name;
	}

	/** "Alignment +15 or higher" / "Alignment -40 or lower", or null for a node without one. */
	static @Nullable Component alignment(SkillNode node) {
		if (node.alignmentMin() != null) {
			return Component.translatable("skill.oldrepublic.req.alignment_min", signed(node.alignmentMin()));
		}

		if (node.alignmentMax() != null) {
			return Component.translatable("skill.oldrepublic.req.alignment_max", signed(node.alignmentMax()));
		}

		return null;
	}

	static Component pathName(Path path) {
		return Component.translatable("skill.oldrepublic.path." + path.id());
	}

	static String signed(int value) {
		return value > 0 ? "+" + value : Integer.toString(value);
	}
}

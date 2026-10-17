package com.oldrepublic.ability;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.AlignmentBand;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Cost and cooldown modifiers every ability applies: the {@code unbound} passive (universal powers
 * cheaper and faster for Independents), the opposed-alignment rule for side powers (Jedi powers
 * opposed while leaning dark and refused when dark; Sith powers the mirror image) and the other
 * features' {@link ForceModifiers} (armour, carried holocrons). The ability runtime checks
 * {@link #refused} before a START reaches an ability.
 */
public final class AbilityCosts {
	/** How a side power answers the player's alignment. */
	public enum Opposition {
		NORMAL,
		/** Cost ×1.5 and no mastery gain. */
		OPPOSED,
		/** Cannot be used at all. */
		REFUSED
	}

	/** Multiplier on the Force Energy cost: skills, alignment and {@link ForceModifiers#cost}. */
	public static float costMultiplier(ServerPlayer p, String abilityId) {
		return costMultiplier(Skills.active(p, SkillIds.UNBOUND), treeOf(p, abilityId), Identity.alignment(p), SkillService.balance())
				* ForceModifiers.cost(p, abilityId);
	}

	/** Multiplier on the cooldown. */
	public static float cooldownMultiplier(ServerPlayer p, String abilityId) {
		return cooldownMultiplier(Skills.active(p, SkillIds.UNBOUND), treeOf(p, abilityId), SkillService.balance());
	}

	/** True if the player's alignment refuses this power; sends the feedback itself. */
	public static boolean refused(ServerPlayer p, String abilityId) {
		String tree = treeOf(p, abilityId);

		if (opposition(tree, Identity.alignment(p)) != Opposition.REFUSED) {
			return false;
		}

		p.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.refused." + tree));
		return true;
	}

	/** False while the power is opposed by the player's alignment (no mastery gain). */
	public static boolean masteryAllowed(ServerPlayer p, String abilityId) {
		return opposition(treeOf(p, abilityId), Identity.alignment(p)) == Opposition.NORMAL;
	}

	// ---------------------------------------------------------------- pure rules (also used by the HUD)

	/** The opposition of a power from tree {@code treeId} at {@code alignment}; only the side trees oppose. */
	public static Opposition opposition(@Nullable String treeId, int alignment) {
		AlignmentBand band = AlignmentBand.of(alignment);

		if (SkillIds.TREE_JEDI.equals(treeId)) {
			return band == AlignmentBand.DARK ? Opposition.REFUSED : band == AlignmentBand.LEANING_DARK ? Opposition.OPPOSED : Opposition.NORMAL;
		}

		if (SkillIds.TREE_SITH.equals(treeId)) {
			return band == AlignmentBand.LIGHT ? Opposition.REFUSED : band == AlignmentBand.LEANING_LIGHT ? Opposition.OPPOSED : Opposition.NORMAL;
		}

		return Opposition.NORMAL;
	}

	public static float costMultiplier(boolean unbound, @Nullable String treeId, int alignment, SkillBalance b) {
		float mult = unbound && SkillIds.TREE_UNIVERSAL.equals(treeId) ? b.unboundCostMult : 1f;
		return opposition(treeId, alignment) == Opposition.OPPOSED ? mult * b.opposedCostMult : mult;
	}

	public static float cooldownMultiplier(boolean unbound, @Nullable String treeId, SkillBalance b) {
		return unbound && SkillIds.TREE_UNIVERSAL.equals(treeId) ? b.unboundCooldownMult : 1f;
	}

	/** Id of the tree whose node unlocks {@code abilityId}, or null. */
	public static @Nullable String treeOf(Player p, String abilityId) {
		SkillNode node = SkillTrees.of(p).abilityNode(abilityId);
		return node == null ? null : node.tree();
	}

	private AbilityCosts() {
	}
}

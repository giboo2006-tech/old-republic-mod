package com.oldrepublic.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityCosts;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rules every Force power shares: who knows a power, the start checks, cost and cooldown
 * multipliers, mastery, and who a power may affect (the PvP rule).
 */
public final class ForcePowers {
	/**
	 * Whether {@code player} has learned power {@code id}: an owned skill node, or the ability set
	 * of {@link ForceData} (Chapter One's Force Push and the {@code /oldrepublic force grant}
	 * command). Works on both sides; on the client pass the synced attachment.
	 */
	public static boolean knows(Player player, @Nullable ForceData data, String id) {
		return Skills.rank(player, id) > 0 || data != null && data.hasAbility(id);
	}

	/**
	 * Whether an upgrade node is active for {@code player}: an active (not dormant) skill node, or
	 * an id added to the ability set by the test command.
	 */
	public static boolean hasNode(Player player, @Nullable ForceData data, String nodeId) {
		return Skills.active(player, nodeId) || data != null && data.hasAbility(nodeId);
	}

	/**
	 * The shared start checks: alive, awakened, learned, no other power charging or channelling,
	 * off cooldown, not refused by alignment. Sends the reason to the player and returns false when
	 * the power may not start. Energy is checked by each power, since the cost differs.
	 */
	public static boolean mayStart(ServerPlayer player, String id) {
		String reason = refusal(player, id);

		if (reason != null) {
			player.sendOverlayMessage(Component.translatable(reason));
			return false;
		}

		return !AbilityCosts.refused(player, id);
	}

	/** Translation key of why {@code player} may not start {@code id} now, or null. */
	static @Nullable String refusal(ServerPlayer player, String id) {
		if (!player.isAlive() || player.isSpectator()) {
			return "message.oldrepublic.force.cannot_now";
		}

		ForceData data = Progression.get(player);

		if (!data.isAwakened()) {
			return "message.oldrepublic.not_awakened";
		}

		if (!knows(player, data, id)) {
			return "message.oldrepublic.force.not_learned";
		}

		if (otherActive(player, id)) {
			return "message.oldrepublic.force.busy";
		}

		if (Progression.isOnCooldown(player, id)) {
			return "message.oldrepublic.force.cooldown";
		}

		return null;
	}

	/** Whether another registered power is charging or channelling for {@code player}. */
	static boolean otherActive(ServerPlayer player, String id) {
		for (ForceAbility ability : Abilities.all()) {
			if (!ability.id().equals(id) && ability.isActive(player)) {
				return true;
			}
		}

		return false;
	}

	/** Cost multiplier from skills and alignment ({@link AbilityCosts}) and from other features (forms). */
	public static float costMultiplier(ServerPlayer player, String id) {
		return AbilityCosts.costMultiplier(player, id) * ForceReactions.costMultiplier(player, id);
	}

	/** {@code baseTicks} after the skill and alignment cooldown multiplier, at least 1. */
	public static int cooldown(ServerPlayer player, String id, int baseTicks) {
		return Math.max(1, Math.round(baseTicks * AbilityCosts.cooldownMultiplier(player, id)));
	}

	/** Mastery rank 0 to 3 of a power. */
	public static int masteryRank(ServerPlayer player, String id) {
		return Progression.masteryRank(Progression.get(player).mastery(id));
	}

	/** Adds mastery unless the power is opposed by the player's alignment. */
	public static void addMastery(ServerPlayer player, String id, int amount) {
		if (amount > 0 && AbilityCosts.masteryAllowed(player, id)) {
			Progression.addMastery(player, id, amount);
		}
	}

	/** Whether the player has enough energy for {@code cost}; plays the fail feedback when not. */
	public static boolean canAfford(ServerPlayer player, float cost) {
		if (player.isCreative() || Progression.get(player).energy + 1.0e-3f >= cost) {
			return true;
		}

		// Fails and plays the shared "not enough energy" feedback.
		Progression.trySpendEnergy(player, cost);
		return false;
	}

	/**
	 * Whether a power cast by {@code caster} may affect {@code target}: a living, visible creature
	 * that is not the caster. Other players only when the power's {@code pvpEffects} flag is on, the
	 * server allows PvP, they are not in creative and not on the caster's team.
	 */
	public static boolean canAffect(ServerPlayer caster, Entity target, boolean pvpEffects) {
		if (target == caster || !(target instanceof LivingEntity) || !target.isAlive() || target.isSpectator()) {
			return false;
		}

		if (target instanceof Player player) {
			return pvpEffects && caster.level().isPvpAllowed() && !player.isCreative() && !caster.isAlliedTo(player);
		}

		return true;
	}

	public static boolean isHostile(Entity entity) {
		return entity instanceof Enemy;
	}

	/** Whether any hostile creature is within {@code radius} blocks of {@code player}. */
	public static boolean hostileNear(ServerPlayer player, double radius) {
		AABB box = player.getBoundingBox().inflate(radius);
		return !player.level().getEntities(player, box, e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) <= radius * radius).isEmpty();
	}

	public static boolean isResistant(LivingEntity entity) {
		return entity.is(ForceTags.PUSH_RESISTANT); // API-CHECK: Entity#is(TagKey) via TypedInstance (as in PushEffects)
	}

	/** 1 minus {@code weight} times the target's knockback resistance (0 to 1). */
	public static float knockbackFactor(LivingEntity entity, float weight) {
		double resistance = Mth.clamp(entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		return (float) (1.0 - weight * resistance);
	}

	/** Centre of a creature's torso. */
	public static Vec3 chest(Entity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	private ForcePowers() {
	}
}

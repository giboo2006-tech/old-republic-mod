package com.oldrepublic.skill;

import java.util.Map;
import java.util.WeakHashMap;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Exploration passives that are plain vanilla attribute modifiers on the player (spec 10.3):
 * {@code trailwise} and {@code pathfinders_poise} lower fall damage (together never more than
 * {@code fallReductionCap}), {@code hardy} raises max health, and {@code pathfinders_poise} adds
 * movement speed while the player is out of combat. Re-applied every 20 ticks with the other derived
 * state, after any node change and when the player enters combat. Fall and speed modifiers are
 * transient; the health modifier is saved with the player so their extra health survives a relog
 * (the attributes load before the health), and the refresh corrects it if the node went away.
 * Server side.
 */
public final class SkillAttributes {
	public static final Identifier FALL_MODIFIER = OldRepublicMod.id("skill_fall");
	public static final Identifier HEALTH_MODIFIER = OldRepublicMod.id("skill_health");
	public static final Identifier SPEED_MODIFIER = OldRepublicMod.id("skill_speed");

	/** What one player currently carries, so an unchanged value never re-sends its attribute. */
	private record Applied(double fall, double health, double speed) {
	}

	/** Keyed by the player object: a respawned player is a new object and gets everything again. */
	private static final Map<ServerPlayer, Applied> APPLIED = new WeakHashMap<>();

	/** The values {@code player}'s active nodes ask for now. */
	public record Values(double fallReduction, double health, double speed) {
	}

	/** Pure: the attribute values for these active ranks (0 = not active) and combat state. */
	public static Values values(int trailwiseRank, int hardyRank, boolean pathfinder, boolean inCombat, SkillBalance b) {
		double fall = rankValue(b.trailwiseFall, trailwiseRank) + (pathfinder ? b.pathfinderFall : 0f);
		double health = hardyRank <= 0 || b.hardyHealth.length == 0 ? 0 : b.hardyHealth[Math.min(hardyRank, b.hardyHealth.length) - 1];
		return new Values(Math.min(Math.max(0, fall), Math.max(0f, b.fallReductionCap)), health, pathfinder && !inCombat ? b.pathfinderSpeed : 0);
	}

	/** The value of rank {@code rank} (1-based) in a per-rank table, 0 when not owned. */
	static float rankValue(float[] table, int rank) {
		return rank <= 0 || table.length == 0 ? 0f : table[Math.min(rank, table.length) - 1];
	}

	/** Applies what the player's active nodes ask for now, changing only the attributes that differ. */
	public static void refresh(ServerPlayer player) {
		if (!player.isAlive()) {
			return;
		}

		Values want = values(Skills.activeRank(player, SkillIds.TRAILWISE), Skills.activeRank(player, SkillIds.HARDY),
				Skills.active(player, SkillIds.PATHFINDERS_POISE), CombatTracker.inCombat(player), SkillService.balance());
		Applied had = APPLIED.get(player);

		if (had == null || had.fall() != want.fallReduction()) {
			set(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MODIFIER, -want.fallReduction(), false);
		}

		if (had == null || had.health() != want.health()) {
			set(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER, want.health(), true);

			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}

		if (had == null || had.speed() != want.speed()) {
			set(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, want.speed(), false);
		}

		APPLIED.put(player, new Applied(want.fallReduction(), want.health(), want.speed()));
	}

	/** Removes the modifier, then adds it again when {@code amount} is not 0. */
	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, boolean saved) {
		AttributeInstance instance = player.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		instance.removeModifier(id);

		if (amount == 0) {
			return;
		}

		AttributeModifier.Operation operation = attribute == Attributes.MAX_HEALTH ? AttributeModifier.Operation.ADD_VALUE
				: AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
		AttributeModifier modifier = new AttributeModifier(id, amount, operation);

		if (saved) {
			// Saved with the entity (vanilla Mob#finalizeSpawn calls addPermanentModifier(AttributeModifier), api/enemy.txt).
			instance.addPermanentModifier(modifier);
		} else {
			instance.addOrUpdateTransientModifier(modifier);
		}
	}

	/** Forgets a player (disconnect); their transient modifiers go with the entity. */
	static void forget(ServerPlayer player) {
		APPLIED.remove(player);
	}

	static void clear() {
		APPLIED.clear();
	}

	private SkillAttributes() {
	}
}

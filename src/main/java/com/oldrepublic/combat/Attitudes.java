package com.oldrepublic.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player hostility (spec 5.2). Owners register {@link Rule}s (the trooper rule is W4's);
 * anything no rule has an opinion about keeps the M1 rule: {@link Attitude#HOSTILE} for vanilla
 * {@link Enemy} mobs, {@link Attitude#NEUTRAL} otherwise. Kill XP, mastery, quest credit, area
 * powers, aim assist and contextual alignment read hostility from here, never from
 * {@code instanceof Enemy}. Rules are read on the server; the snapshots are server only.
 */
public final class Attitudes {
	/** One owner's view of how its entities regard players. */
	public interface Rule {
		/**
		 * The standing attitude of {@code self} toward {@code p} (the reputation tier), or null when this
		 * rule has no opinion about {@code self}. Provocation and engagement are reported by the two
		 * methods below, not folded into this answer.
		 */
		@Nullable Attitude toward(LivingEntity self, Player p);

		/** {@code self} fired first at {@code p}, or carries an incident that includes {@code p}: counts as hostile. */
		default boolean engaged(LivingEntity self, Player p) {
			return false;
		}

		/** {@code p} struck {@code self} first: {@code self} fights {@code p}, but the fight still counts as not hostile. */
		default boolean provokedBy(LivingEntity self, Player p) {
			return false;
		}
	}

	private static final List<Rule> RULES = new CopyOnWriteArrayList<>();
	/** Victim, then attacker: {@link #countsAsHostile} when that attacker's damage last landed. Entries go with the victim. */
	private static final Map<LivingEntity, Map<UUID, Boolean>> SNAPSHOTS = new WeakHashMap<>();

	/** Adds a rule. Rules are asked in registration order; the first opinion wins. Call during mod init. */
	public static void register(Rule rule) {
		RULES.add(rule);
	}

	/** The first non-null rule answer; otherwise {@link Attitude#HOSTILE} for an {@link Enemy}, {@link Attitude#NEUTRAL} for anything else. */
	public static Attitude toward(LivingEntity self, Player p) {
		Attitude ruled = ruled(self, p);

		if (ruled != null) {
			return ruled;
		}

		return self instanceof Enemy ? Attitude.HOSTILE : Attitude.NEUTRAL;
	}

	/**
	 * Whether a fight between {@code p} and {@code e} counts as hostile: {@code e} is
	 * {@link Attitude#HOSTILE} toward {@code p}, or engaged with {@code p}. Never true for provocation
	 * alone. Gates kill XP, mastery and quest credit.
	 */
	public static boolean countsAsHostile(Player p, Entity e) {
		if (!(e instanceof LivingEntity self) || self == p) {
			return false;
		}

		if (toward(self, p) == Attitude.HOSTILE) {
			return true;
		}

		for (Rule rule : RULES) {
			if (rule.engaged(self, p)) {
				return true;
			}
		}

		return false;
	}

	/** Whether {@code p} struck {@code e} first and {@code e} is still fighting back. */
	public static boolean provokedBy(Entity e, Player p) {
		if (!(e instanceof LivingEntity self) || self == p) {
			return false;
		}

		for (Rule rule : RULES) {
			if (rule.provokedBy(self, p)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Whether area powers (a Push cone, a Lightning chain, a Jump crush) and aim assist must skip
	 * {@code e} for {@code p}: a bystander some rule has an opinion about (a trooper) that neither
	 * counts as hostile nor was provoked by {@code p}. Entities no rule covers (vanilla mobs,
	 * animals, players) are never shielded, so they keep their M2 behaviour.
	 */
	public static boolean shieldedFromArea(Player p, Entity e) {
		return e instanceof LivingEntity self && self != p && ruled(self, p) != null
				&& !countsAsHostile(p, e) && !provokedBy(e, p);
	}

	/**
	 * Records {@link #countsAsHostile} for {@code attacker} and {@code victim} as it stands now.
	 * Called by the lead's damage hook whenever a player's damage lands, including the killing blow.
	 */
	public static void snapshotOnDamage(ServerPlayer attacker, LivingEntity victim) {
		SNAPSHOTS.computeIfAbsent(victim, v -> new HashMap<>(2)).put(attacker.getUUID(), countsAsHostile(attacker, victim));
	}

	/**
	 * {@link #countsAsHostile} as it stood when {@code killer}'s damage last landed on {@code victim}
	 * (spec 5.2), so the answer reflects the fight, not the corpse. Without a snapshot it falls back
	 * to the live answer.
	 */
	public static boolean hostileSnapshot(LivingEntity victim, ServerPlayer killer) {
		Map<UUID, Boolean> byAttacker = SNAPSHOTS.get(victim);
		Boolean snapshot = byAttacker == null ? null : byAttacker.get(killer.getUUID());
		return snapshot != null ? snapshot : countsAsHostile(killer, victim);
	}

	private static @Nullable Attitude ruled(LivingEntity self, Player p) {
		for (Rule rule : RULES) {
			Attitude attitude = rule.toward(self, p);

			if (attitude != null) {
				return attitude;
			}
		}

		return null;
	}

	private Attitudes() {
	}
}

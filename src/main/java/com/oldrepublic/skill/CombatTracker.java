package com.oldrepublic.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Remembers when each player last dealt or took damage, for the combat lock on group switches, slot
 * changes, respecs and (for the saber feature) form changes. In memory only.
 */
public final class CombatTracker {
	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

	/** Marks the player as fighting now. */
	public static void mark(ServerPlayer player) {
		LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
	}

	/** True when the player dealt or took damage within {@code ticks}. */
	public static boolean inCombat(ServerPlayer player, int ticks) {
		Long last = LAST_COMBAT.get(player.getUUID());
		return last != null && player.level().getGameTime() - last < ticks;
	}

	/** True within the configured {@code combatLockTicks}. */
	public static boolean inCombat(ServerPlayer player) {
		return inCombat(player, SkillService.balance().combatLockTicks);
	}

	static void onDamage(LivingEntity entity, DamageSource source, float taken) {
		if (taken <= 0f) {
			return;
		}

		if (entity instanceof ServerPlayer victim) {
			mark(victim);
		}

		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
			mark(attacker);
		}
	}

	static void forget(ServerPlayer player) {
		LAST_COMBAT.remove(player.getUUID());
	}

	static void clear() {
		LAST_COMBAT.clear();
	}

	private CombatTracker() {
	}
}

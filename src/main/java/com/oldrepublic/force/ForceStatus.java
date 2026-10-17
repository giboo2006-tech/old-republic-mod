package com.oldrepublic.force;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;

/**
 * Short-lived conditions that Force powers put on creatures and that other features read: a
 * ranged weapon pulled out of line (Disarming Pull) and a saber guard knocked open. Kept in memory
 * on the server only, keyed by entity UUID and the level's game time, so they expire on their own.
 *
 * <p>The enemy and saber features query these from their AI (a disarmed poacher does not fire and
 * shows its rifle lowered; a suppressed duelist does not guard).
 */
public final class ForceStatus {
	private static final Map<UUID, Long> DISARMED = new HashMap<>();
	private static final Map<UUID, Long> GUARD_SUPPRESSED = new HashMap<>();

	/** True while {@code entity}'s ranged weapon is pulled out of line and it must not fire. */
	public static boolean isDisarmed(LivingEntity entity) {
		return active(DISARMED, entity);
	}

	/** True while {@code entity} may not raise a saber guard. */
	public static boolean isGuardSuppressed(LivingEntity entity) {
		return active(GUARD_SUPPRESSED, entity);
	}

	public static void disarm(LivingEntity entity, int ticks) {
		DISARMED.merge(entity.getUUID(), entity.level().getGameTime() + ticks, Math::max);
	}

	public static void suppressGuard(LivingEntity entity, int ticks) {
		GUARD_SUPPRESSED.merge(entity.getUUID(), entity.level().getGameTime() + ticks, Math::max);
	}

	private static boolean active(Map<UUID, Long> map, LivingEntity entity) {
		Long until = map.get(entity.getUUID());
		return until != null && entity.level().getGameTime() < until;
	}

	/** Forgets expired entries; {@code now} is any level's game time (entries are short). */
	static void prune(long now) {
		DISARMED.values().removeIf(until -> until < now - 1200);
		GUARD_SUPPRESSED.values().removeIf(until -> until < now - 1200);
	}

	static void clear() {
		DISARMED.clear();
		GUARD_SUPPRESSED.clear();
	}

	private ForceStatus() {
	}
}

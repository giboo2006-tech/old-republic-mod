package com.oldrepublic.enemy.hymn;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Target picking for the Milestone 5 foes that are hostile to every player (the Hymnsworn, the sand
 * scour): keep a good target, drop a stale one, and pick the nearest player in sight. Players are
 * skipped by game mode (creative, spectator), never by {@code isCreative()}: a game test's mock player
 * answers that true. Read on the server every {@link #INTERVAL} ticks.
 */
public final class FoeTargets {
	/** Ticks between target checks. */
	public static final int INTERVAL = 10;
	/** A target is dropped beyond this many times the follow range. */
	private static final double DROP_FACTOR = 1.5;

	/** Keeps or finds {@code mob}'s target within {@code range}; true when it has one afterwards. */
	public static boolean keepOrFindPlayer(Mob mob, ServerLevel level, double range) {
		LivingEntity current = mob.getTarget();

		if (current != null) {
			boolean stale = !current.isAlive() || current.level() != level || ignored(current)
					|| current.distanceToSqr(mob) > range * range * DROP_FACTOR * DROP_FACTOR;

			if (!stale) {
				return true;
			}

			mob.setTarget(null);
		}

		Player best = nearestPlayer(mob, level, range, true);

		if (best != null) {
			mob.setTarget(best);
			return true;
		}

		return false;
	}

	/** The nearest player within {@code range} that is not ignored (and in sight when {@code sight}), or null. */
	public static @Nullable Player nearestPlayer(Mob mob, ServerLevel level, double range, boolean sight) {
		Player best = null;
		double bestDistance = range * range;

		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || ignored(player)) {
				continue;
			}

			double d = player.distanceToSqr(mob);

			if (d <= bestDistance && (!sight || mob.getSensing().hasLineOfSight(player))) {
				best = player;
				bestDistance = d;
			}
		}

		return best;
	}

	/** Creative and spectator players, by game mode. */
	public static boolean ignored(LivingEntity entity) {
		return StormCall.ignored(entity);
	}

	private FoeTargets() {
	}
}

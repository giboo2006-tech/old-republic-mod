package com.oldrepublic.force.heal;

import java.util.List;

import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Force Heal raises downed troopers (spec 5.4 handoff): on every heal tick of a channel, each
 * downed trooper within {@code missions.healTendRange} blocks of the caster is stood up at once through
 * {@code Troopers.tend(caster, trooper, true)}. Reaching the troopers goes through {@link Downed}
 * so the game test can stand in for them. Server side.
 */
public final class HealRevive {
	/**
	 * Reach in blocks: the default of {@code missions.healTendRange} (spec 14). Force Heal reads the
	 * key; this stays the test's default.
	 */
	public static final double RANGE = 4.0;

	/** How the heal finds and tends downed creatures. */
	public interface Downed {
		/** W4's troopers. */
		Downed TROOPERS = new Downed() {
			@Override
			public boolean isDowned(Entity e) {
				return Troopers.isDowned(e);
			}

			@Override
			public boolean tend(ServerPlayer p, Entity e, boolean instant) {
				return Troopers.tend(p, e, instant);
			}
		};

		/** Whether {@code e} lies downed, waiting to be tended. */
		boolean isDowned(Entity e);

		/** One tend of {@code e} by {@code p}; {@code instant} stands it up at once. True once it stands. */
		boolean tend(ServerPlayer p, Entity e, boolean instant);
	}

	/**
	 * Revives every downed creature within {@code range} of {@code caster}. Each one that stands up
	 * gets the Shared Light stream from the caster for the rest of the channel.
	 *
	 * @return how many stood up
	 */
	public static int revive(ServerPlayer caster, double range, Downed access) {
		ServerLevel level = caster.level();
		double rangeSqr = range * range;
		List<Entity> downed = level.getEntities(caster, caster.getBoundingBox().inflate(range),
				e -> e.isAlive() && e.distanceToSqr(caster) <= rangeSqr && access.isDowned(e));
		int stood = 0;

		for (Entity entity : downed) {
			if (access.tend(caster, entity, true)) {
				stood++;
				EffectEvents.around(level, ForceEffects.HEAL_ALLY, entity.position(), null, entity.getId(), 1f,
						ForceEffects.packCaster(caster.getId(), ForceEffects.CHANNEL_START));
			}
		}

		return stood;
	}

	private HealRevive() {
	}
}

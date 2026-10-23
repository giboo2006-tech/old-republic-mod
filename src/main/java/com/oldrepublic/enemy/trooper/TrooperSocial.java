package com.oldrepublic.enemy.trooper;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.saber.SaberComponents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The squad's dealings with players and rival patrols outside a fight (spec 5.2, 7.5), run on the
 * squad leader every few ticks:
 * <ul>
 * <li>Wary: the leader warns a player within {@code warnRange} (again at most every
 * {@code warnRepeatTicks}); after the warning, {@code waryTicks} within {@code waryRange}, or a lit
 * saber that close, and the squad opens fire: it is then engaged with that player.</li>
 * <li>Neutral: "Move along." to a player with a lit saber within {@code moveAlongRange}.</li>
 * <li>Allied: the nearest trooper salutes a player within {@code saluteRange}.</li>
 * <li>The cold-peace standoff with an untagged rival patrol within {@code standoffRange}.</li>
 * </ul>
 */
final class TrooperSocial {
	/** How often {@link #tick} runs (the trooper calls it every this many ticks). */
	static final int INTERVAL = 5;

	static void tick(ServerLevel level, TrooperEntity leader, long now) {
		TrooperBalance b = TrooperBalance.get();
		Squads.State state = Squads.state(leader);
		List<TrooperEntity> fighters = Squads.fighters(leader);

		if (fighters.isEmpty()) {
			return;
		}

		boolean inCombat = false;

		for (TrooperEntity member : fighters) {
			inCombat |= member.getTarget() != null;
		}

		if (Squads.encounterTick(state, inCombat, now, b) && Reinforcements.dismiss(leader)) {
			// The flare arrivals walk off; the next check runs with the smaller squad.
			return;
		}

		standoff(level, leader, state, fighters, inCombat, now, b);

		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}

			UUID id = player.getUUID();
			TrooperEntity nearest = nearest(fighters, player.position());
			double distance = nearest.distanceTo(player);

			if (distance > b.warnRange) {
				state.waryTicks.remove(id);
				continue;
			}

			if (fighting(fighters, id, now)) {
				continue;
			}

			switch (Attitudes.toward(leader, player)) {
				case WARY -> {
					// Creative players (invulnerable abilities) are never warned or fired on.
					if (player.canBeSeenAsEnemy()) {
						wary(leader, state, player, distance, now, b);
					}
				}
				case NEUTRAL -> {
					if (distance <= b.moveAlongRange && saberLit(player) && due(state.greeted.get(id), now, b) && Barks.moveAlong(nearest)) {
						state.greeted.put(id, now);
					}
				}
				case ALLIED -> {
					if (!inCombat && distance <= b.saluteRange && due(state.greeted.get(id), now, b)) {
						nearest.salute(player);
						state.greeted.put(id, now);
					}
				}
				case HOSTILE -> {
				}
			}
		}
	}

	/** The Wary warning and the countdown after it. */
	private static void wary(TrooperEntity leader, Squads.State state, Player player, double distance, long now, TrooperBalance b) {
		UUID id = player.getUUID();
		Long warned = state.warned.get(id);

		if (warned == null || now - warned >= b.warnRepeatTicks) {
			if (Barks.warn(leader)) {
				state.warned.put(id, now);
			}

			return;
		}

		if (distance > b.waryRange) {
			state.waryTicks.remove(id);
			return;
		}

		int ticks = state.waryTicks.merge(id, INTERVAL, Integer::sum);

		if (saberLit(player) || ticks >= b.waryTicks) {
			state.waryTicks.remove(id);
			TrooperEvents.engageSquad(leader, player, now);
			Barks.say(leader, BarkCategory.SPOT);
		}
	}

	/** Halts the squad facing an untagged rival patrol, barks, and walks on after {@code standoffTicks}. */
	private static void standoff(ServerLevel level, TrooperEntity leader, Squads.State state, List<TrooperEntity> fighters,
			boolean inCombat, long now, TrooperBalance b) {
		boolean eligible = b.coldPeace && !inCombat && leader.incidentMode() == IncidentMode.NONE;
		TrooperEntity rival = eligible ? rival(level, fighters, b) : null;
		ColdPeace.Phase before = state.standoff.phase();
		state.standoff = ColdPeace.standoffStep(state.standoff, rival != null, now, b);
		state.rival = rival != null ? rival.position() : null;

		if (before != ColdPeace.Phase.HALTED && state.standoff.phase() == ColdPeace.Phase.HALTED) {
			Barks.standoff(leader);
		}
	}

	/** The nearest standing, untagged trooper of the other army within {@code standoffRange} of the squad. */
	private static @Nullable TrooperEntity rival(ServerLevel level, List<TrooperEntity> fighters, TrooperBalance b) {
		TrooperEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (TrooperEntity member : fighters) {
			for (TrooperEntity other : level.getEntitiesOfClass(TrooperEntity.class, member.getBoundingBox().inflate(b.standoffRange),
					t -> t.canFight() && !Allegiances.friendly(t, member) && t.incidentMode() == IncidentMode.NONE)) {
				double distance = other.distanceToSqr(member);

				if (distance <= (double) b.standoffRange * b.standoffRange && distance < bestDistance) {
					best = other;
					bestDistance = distance;
				}
			}
		}

		return best;
	}

	/** Whether the squad already fights this player (engaged, provoked, or an incident that includes them). */
	private static boolean fighting(List<TrooperEntity> fighters, UUID player, long now) {
		for (TrooperEntity member : fighters) {
			if (member.isEngagedWith(player, now) || member.isProvokedBy(player, now) || member.incidentIncludes(player, now)) {
				return true;
			}
		}

		return false;
	}

	private static TrooperEntity nearest(List<TrooperEntity> fighters, Vec3 to) {
		TrooperEntity best = fighters.get(0);

		for (TrooperEntity member : fighters) {
			if (member.distanceToSqr(to) < best.distanceToSqr(to)) {
				best = member;
			}
		}

		return best;
	}

	private static boolean due(@Nullable Long last, long now, TrooperBalance b) {
		return last == null || now - last >= b.warnRepeatTicks;
	}

	/** A lit lightsaber in either hand. */
	static boolean saberLit(Player player) {
		return SaberComponents.isIgnited(player.getMainHandItem()) || SaberComponents.isIgnited(player.getOffhandItem());
	}

	private TrooperSocial() {
	}
}

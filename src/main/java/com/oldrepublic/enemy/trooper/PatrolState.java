package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The natural patrols that exist right now, by squad (spec 7.6). {@link PatrolSpawner} keeps the
 * live one for the server, cleared on stop; game tests make their own detached instance, so they
 * never touch the live caps. Patrols are never saved: their members are not either.
 *
 * <p>Members that were removed (killed, discarded, unloaded with their chunk) drop out on the next
 * read, and a squad with no members left is forgotten.
 */
public final class PatrolState {
	/** One patrol: its squad id and the members still in the world. */
	public static final class Patrol {
		private final UUID squad;
		private final List<LivingEntity> members;

		Patrol(UUID squad, List<LivingEntity> members) {
			this.squad = squad;
			this.members = new ArrayList<>(members);
		}

		public UUID squad() {
			return squad;
		}

		public List<LivingEntity> members() {
			return Collections.unmodifiableList(members);
		}

		/** The level the patrol walks in, or null once it has no members. */
		public @Nullable Level level() {
			return members.isEmpty() ? null : members.get(0).level();
		}

		public List<Vec3> positions() {
			List<Vec3> positions = new ArrayList<>(members.size());

			for (LivingEntity member : members) {
				positions.add(member.position());
			}

			return positions;
		}

		/** Whether any member is within {@code range} of {@code pos} in {@code level}. */
		boolean near(Level in, Vec3 pos, double range) {
			double rangeSqr = range * range;

			for (LivingEntity member : members) {
				if (member.level() == in && member.position().distanceToSqr(pos) <= rangeSqr) {
					return true;
				}
			}

			return false;
		}

		/** Drops removed members; true when none are left. */
		boolean prune() {
			members.removeIf(LivingEntity::isRemoved);
			return members.isEmpty();
		}

		void discard() {
			for (LivingEntity member : members) {
				member.discard();
			}

			members.clear();
		}
	}

	private final Map<UUID, Patrol> patrols = new LinkedHashMap<>();

	/** Records a freshly spawned patrol; a squad id already recorded is replaced. */
	public void add(UUID squad, List<LivingEntity> members) {
		patrols.put(squad, new Patrol(squad, members));
	}

	/**
	 * Adds {@code member} to the patrol of {@code squad} (a reinforcement called by its flare), so it
	 * counts, despawns and clears with the squad. False when no such patrol is recorded.
	 */
	public boolean join(UUID squad, LivingEntity member) {
		Patrol patrol = patrols.get(squad);

		if (patrol == null || patrol.members.contains(member)) {
			return false;
		}

		patrol.members.add(member);
		return true;
	}

	public boolean contains(UUID squad) {
		prune();
		return patrols.containsKey(squad);
	}

	/** How many patrols exist (the global cap counts these). */
	public int size() {
		prune();
		return patrols.size();
	}

	public Collection<Patrol> patrols() {
		prune();
		return Collections.unmodifiableCollection(patrols.values());
	}

	/** How many patrols have a member within {@code range} of {@code pos} in {@code level} (the per-player cap counts these). */
	public int near(Level level, Vec3 pos, double range) {
		prune();
		int count = 0;

		for (Patrol patrol : patrols.values()) {
			if (patrol.near(level, pos, range)) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Discards, as whole squads, the patrols whose members are all more than {@code distance} from
	 * every player of their level ({@code players} gives the player positions of a level). Returns
	 * how many squads went.
	 */
	public int despawnFar(Function<Level, List<Vec3>> players, double distance) {
		prune();
		int removed = 0;

		for (Iterator<Patrol> it = patrols.values().iterator(); it.hasNext();) {
			Patrol patrol = it.next();

			if (PatrolRules.shouldDespawn(patrol.positions(), players.apply(patrol.level()), distance)) {
				patrol.discard();
				it.remove();
				removed++;
			}
		}

		return removed;
	}

	/** Discards every patrol (server stop, {@code /oldrepublic patrol clear}); returns how many there were. */
	public int clear() {
		prune();
		int count = patrols.size();

		for (Patrol patrol : patrols.values()) {
			patrol.discard();
		}

		patrols.clear();
		return count;
	}

	private void prune() {
		patrols.values().removeIf(Patrol::prune);
	}
}

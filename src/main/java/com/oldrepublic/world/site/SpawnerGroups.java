package com.oldrepublic.world.site;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.Poachers;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The garrison groups a {@code site_spawner} can hold (spec 3.2, 3.4). Every group spawns through
 * the owning feature's API ({@code Troopers}, {@code Poachers}, {@code Sentinels}), so each spawner
 * makes its own fresh squad: one {@code spawnSquad} or {@code spawnCrew} call per spawner.
 */
public final class SpawnerGroups {
	/** An Imperial dig team: 2 riflemen and a grenadier (Sith Tomb dig camp). */
	public static final String DIG_TEAM = "dig_team";
	/** A Republic salvage crew: 2 riflemen and a field medic (Wreck Field salvage camp). */
	public static final String SALVAGE_CREW = "salvage_crew";
	/** Vasch's paid diggers: 2 or 3 crystal poachers, seeded by the spawner's position (ruin courtyard, processional). */
	public static final String DIGGERS = "diggers";
	/** Scavenging crystal poachers: {@code count} of them, 1 by default (wreck hull, Wayfarer shrine). */
	public static final String SCAVENGERS = "scavengers";
	/** One Watcher sentinel (Watchers' Rest, Jedi shrine). */
	public static final String WATCHER = "watcher";
	/** One Tomb sentinel (Sith Tomb, Sith shrine). */
	public static final String TOMB = "tomb";

	/** Every group id, in the order of the spec's list. */
	public static final List<String> ALL = List.of(DIG_TEAM, SALVAGE_CREW, DIGGERS, SCAVENGERS, WATCHER, TOMB);

	private static final List<TrooperRole> DIG_TEAM_ROLES = List.of(TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.GRENADIER);
	private static final List<TrooperRole> SALVAGE_CREW_ROLES = List.of(TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.MEDIC);

	/** Whether {@code group} is a known group id. */
	public static boolean known(String group) {
		return ALL.contains(group);
	}

	/**
	 * How many members {@code group} spawns from a spawner at {@code pos} whose data says {@code count}
	 * (0 = the group's default): diggers 2 or 3 seeded by position, scavengers 1, the fixed groups their size.
	 */
	public static int size(String group, int count, BlockPos pos) {
		return switch (group) {
			case DIG_TEAM -> DIG_TEAM_ROLES.size();
			case SALVAGE_CREW -> SALVAGE_CREW_ROLES.size();
			case DIGGERS -> count > 0 ? count : 2 + RandomSource.create(pos.asLong()).nextInt(2);
			case SCAVENGERS -> count > 0 ? count : 1;
			case WATCHER, TOMB -> 1;
			default -> 0;
		};
	}

	/**
	 * Spawns {@code group} around {@code at} and returns the members (empty for an unknown group, or
	 * while an owning feature's API is still a stub that spawns nothing).
	 *
	 * @param bound sentinels only: marks a sentinel re-bound to guard (worldgen writes false)
	 * @param owner the owning player of a per-player site, or null for a shared one
	 */
	public static List<LivingEntity> spawn(ServerLevel level, BlockPos at, String group, int count, boolean bound, @Nullable UUID owner) {
		int size = size(group, count, at);

		return switch (group) {
			case DIG_TEAM -> Troopers.spawnSquad(level, at, Faction.SITH_EMPIRE, DIG_TEAM_ROLES, owner, IncidentMode.NONE);
			case SALVAGE_CREW -> Troopers.spawnSquad(level, at, Faction.REPUBLIC, SALVAGE_CREW_ROLES, owner, IncidentMode.NONE);
			case DIGGERS, SCAVENGERS -> Poachers.spawnCrew(level, at, size, owner);
			case WATCHER -> List.of(Sentinels.spawn(level, at, SentinelVariant.WATCHER, bound, owner));
			case TOMB -> List.of(Sentinels.spawn(level, at, SentinelVariant.TOMB, bound, owner));
			default -> List.of();
		};
	}

	private SpawnerGroups() {
	}
}

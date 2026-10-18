package com.oldrepublic.world.site;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.Poachers;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
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

	/** Mk IV security droids of an Imperial dig (greater tombs; Milestone 4): {@code count}, 1 by default. */
	public static final String SECURITY_DROIDS = "security_droids";
	/** Hollowhorns: the bulls at the Singing Hollow's mouth, the twisted beasts of a corrupted waystone. {@code count}, 2 by default. */
	public static final String HOLLOWHORNS = "hollowhorns";
	/** An ash hound pack (the duskglass rift): {@code count}, 3 by default. */
	public static final String ASH_HOUNDS = "ash_hounds";

	/** Every group id, in the order of the spec's list, then the Milestone 4 groups. */
	public static final List<String> ALL = List.of(DIG_TEAM, SALVAGE_CREW, DIGGERS, SCAVENGERS, WATCHER, TOMB, SECURITY_DROIDS, HOLLOWHORNS, ASH_HOUNDS);
	/** Entity tag of the hollowhorns a corrupted waystone wakes (W6 shows them dark-veined). */
	public static final String CORRUPTED_TAG = "oldrepublic.corrupted";

	private static final Identifier HOLLOWHORN = OldRepublicMod.id("hollowhorn");
	private static final Identifier ASH_HOUND = OldRepublicMod.id("ash_hound");

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
			case SECURITY_DROIDS -> count > 0 ? count : 1;
			case HOLLOWHORNS -> count > 0 ? count : 2;
			case ASH_HOUNDS -> count > 0 ? count : 3;
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
			case SECURITY_DROIDS -> droids(level, at, size, owner);
			case HOLLOWHORNS -> beasts(level, at, HOLLOWHORN, size, bound);
			case ASH_HOUNDS -> beasts(level, at, ASH_HOUND, size, false);
			default -> List.of();
		};
	}

	/** Security droids through W6's {@code Droids} (empty while it is a stub). */
	private static List<LivingEntity> droids(ServerLevel level, BlockPos at, int size, @Nullable UUID owner) {
		List<LivingEntity> out = new ArrayList<>();

		for (int i = 0; i < size; i++) {
			LivingEntity droid = Droids.spawnSecurity(level, at.offset(i % 2, 0, i / 2), owner, true);

			if (droid != null) {
				out.add(droid);
			}
		}

		return out;
	}

	/**
	 * Planet beasts by entity id (W6's types, absent until W6 is merged: then nothing spawns). A
	 * spawner whose data says {@code bound} marks them corrupted ({@link #CORRUPTED_TAG}).
	 */
	private static List<LivingEntity> beasts(ServerLevel level, BlockPos at, Identifier id, int size, boolean corrupted) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);

		if (type == null) {
			return List.of();
		}

		List<LivingEntity> out = new ArrayList<>();

		for (int i = 0; i < size; i++) {
			if (type.spawn(level, at.offset(i % 2, 0, i / 2), EntitySpawnReason.STRUCTURE) instanceof LivingEntity beast) {
				if (corrupted) {
					beast.addTag(CORRUPTED_TAG);
				}

				out.add(beast);
			}
		}

		return out;
	}

	private SpawnerGroups() {
	}
}

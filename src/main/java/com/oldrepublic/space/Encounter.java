package com.oldrepublic.space;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;
import com.oldrepublic.space.network.SpaceEncounterPayload;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One encounter in an orbit (spec MILESTONE_5.md 5.6, 5.9): its owner, its ships, the players who
 * joined it, and its comm lines. In memory only: a restart, the owner leaving the orbit, logging out
 * or being disabled ends it, and its ships vanish ({@link SpaceEncounters}).
 */
final class Encounter {
	/** What the encounter is; the HUD's kind follows from it. */
	enum Type {
		PATROL(SpaceEncounterPayload.PATROL),
		BLOCKADE(SpaceEncounterPayload.BLOCKADE),
		PASS(SpaceEncounterPayload.PASS),
		/** A {@code space_run}'s scanning patrol (5.9). */
		RUN_PATROL(SpaceEncounterPayload.RUN),
		/** A {@code space_clear}'s marked group (5.8, 5.9). */
		CLEAR(SpaceEncounterPayload.CLEAR);

		final int kind;

		Type(int kind) {
			this.kind = kind;
		}
	}

	final Type type;
	final UUID owner;
	final ResourceKey<Level> dimension;
	final Planets.Planet system;
	final long started;
	/** The mission whose objective this is, for {@code RUN_PATROL} and {@code CLEAR}. */
	final @Nullable String missionId;
	/** The objective point shown on the HUD, if any. */
	final @Nullable Vec3 point;
	/** The comm lines' lang keys, all known at the start (a {@code space_comm} event names one by index). */
	final List<String> comm;
	/** The owner and every co-op pilot who joined (5.6). */
	final Set<UUID> participants = new HashSet<>();
	/** Its live ships: fighters, and the gunship and its generator. */
	final Set<UUID> members = new LinkedHashSet<>();
	/**
	 * Its ships' entities, by id: the level finds a ship by its id only while its chunk is loaded, so one
	 * out of sight (placed before its chunk loaded, or left far behind) is found here, still the
	 * encounter's, and goes when the encounter ends.
	 */
	final Map<UUID, Entity> bodies = new HashMap<>();
	/** The network ids of every ship it ever had, destroyed or not: what they still have in the air goes out with the fight. */
	final Set<Integer> shooters = new HashSet<>();
	/** The ships it set out with or launched, and how many of them were destroyed. */
	int spawned;
	int destroyed;
	/** Kills by shooter, for the patrol's bonus (5.6). */
	final Map<UUID, Integer> kills = new HashMap<>();
	/** The ace's name key ({@code bounty.oldrepublic.ace.*}), or empty. */
	String ace = "";

	/** The blockade: its gunship, the current wave's fighters and which wave it is. */
	@Nullable UUID gunship;
	final Set<UUID> wave = new HashSet<>();
	int waveIndex;
	/** The blockade broken, the pass done or a run's scan complete at this game time; the encounter then winds down. */
	long finishedAt = Long.MIN_VALUE;
	/** The Peaceful pass: when the interceptors turn away, and when the way opens. */
	long turnAwayAt = Long.MIN_VALUE;
	long passAt = Long.MIN_VALUE;
	/** A neutral group turned hostile (a scanning patrol fired on, 5.9). */
	boolean provoked;
	/** Clan Hask's fighters fly on the owner's side (the blockade, 6.5). */
	boolean clan;
	/**
	 * Game tests: the difficulty it plays by (true: Peaceful) in place of the world's, which other
	 * tests share, and a blockade's fixed middle and escort, kept for a regroup; null: the world's.
	 */
	@Nullable Boolean peaceful;
	@Nullable Vec3 fixedCentre;
	@Nullable Boolean clanOverride;

	Encounter(Type type, UUID owner, ResourceKey<Level> dimension, Planets.Planet system, long started, @Nullable String missionId,
			@Nullable Vec3 point, List<String> comm) {
		this.type = type;
		this.owner = owner;
		this.dimension = dimension;
		this.system = system;
		this.started = started;
		this.missionId = missionId;
		this.point = point;
		this.comm = new ArrayList<>(comm);
		this.participants.add(owner);
	}

	/** Whether the blockade is broken or the pass is done. */
	boolean finished() {
		return finishedAt != Long.MIN_VALUE;
	}

	/** The index of {@code key} among the comm lines (-1 when it has none). */
	int line(String key) {
		return comm.indexOf(key);
	}

	/**
	 * What the HUD shows for it. Once it is finished (a broken blockade, a pass done, a patrol that
	 * turns away) it shows nothing but still carries its comm lines, so its last words are heard.
	 */
	SpaceEncounterPayload payload() {
		if (finished()) {
			return new SpaceEncounterPayload(SpaceEncounterPayload.NONE, Optional.empty(), comm, ace);
		}

		return new SpaceEncounterPayload(type.kind, Optional.ofNullable(point), comm, ace);
	}
}

package com.oldrepublic.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Planets;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A player's saved space state (spec MILESTONE_5.md 5.7, 5.9, 14.1): the space objectives they are
 * flying, so a run or a clear survives a relog and a restart, and the day they last opened the
 * Silent Spindle's vault. Encounters themselves are never saved. Immutable; persistent, kept on
 * death, server side only.
 *
 * @param runs     the space objectives in progress
 * @param vaultDay the in-game day of the last vault opening, or -1
 */
public record SpaceData(List<Run> runs, long vaultDay) {
	/**
	 * One space objective (5.9).
	 *
	 * @param missionId the mission it belongs to (W4's id)
	 * @param clear     {@code space_clear} (else {@code space_run})
	 * @param system    the system whose orbit it is flown in
	 * @param ace       a bounty's ace and wingmen (5.8)
	 * @param point     the buoy or the group's point, once chosen
	 * @param seed      the draw for the point, the group and the ace's name
	 */
	public record Run(String missionId, boolean clear, Planets.Planet system, boolean ace, Optional<Vec3> point, long seed) {
		public static final Codec<Run> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("mission").forGetter(Run::missionId),
				Codec.BOOL.optionalFieldOf("clear", false).forGetter(Run::clear),
				Codec.STRING.optionalFieldOf("system", "home").forGetter(r -> r.system().id()),
				Codec.BOOL.optionalFieldOf("ace", false).forGetter(Run::ace),
				Vec3.CODEC.optionalFieldOf("point").forGetter(Run::point),
				Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Run::seed)
		).apply(i, (mission, clear, system, ace, point, seed) ->
				new Run(mission, clear, Objects.requireNonNullElse(Planets.Planet.byId(system), Planets.Planet.HOME), ace, point, seed)));

		Run withPoint(Vec3 newPoint) {
			return new Run(missionId, clear, system, ace, Optional.of(newPoint), seed);
		}
	}

	public static final SpaceData EMPTY = new SpaceData(List.of(), -1L);

	public static final Codec<SpaceData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Run.CODEC.listOf().optionalFieldOf("runs", List.of()).forGetter(SpaceData::runs),
			Codec.LONG.optionalFieldOf("vault_day", -1L).forGetter(SpaceData::vaultDay)
	).apply(i, SpaceData::new));

	public static final AttachmentType<SpaceData> SPACE = AttachmentRegistry.create(
			OldRepublicMod.id("space"),
			builder -> builder
					.initializer(() -> EMPTY)
					.persistent(CODEC)
					.copyOnDeath()
	);

	public SpaceData {
		runs = List.copyOf(runs);
	}

	public static SpaceData get(Player p) {
		return p.getAttachedOrCreate(SPACE);
	}

	public static void set(Player p, SpaceData data) {
		p.setAttached(SPACE, data);
	}

	/** The run of {@code missionId}, or empty. */
	public Optional<Run> run(String missionId) {
		return runs.stream().filter(r -> r.missionId().equals(missionId)).findFirst();
	}

	/** With {@code run} in place of the run of the same mission (or added). */
	SpaceData with(Run run) {
		List<Run> next = new ArrayList<>(runs);
		next.removeIf(r -> r.missionId().equals(run.missionId()));
		next.add(run);
		return new SpaceData(next, vaultDay);
	}

	/** Without the run of {@code missionId}. */
	SpaceData without(String missionId) {
		List<Run> next = new ArrayList<>(runs);
		next.removeIf(r -> r.missionId().equals(missionId));
		return new SpaceData(next, vaultDay);
	}

	SpaceData withVaultDay(long day) {
		return new SpaceData(runs, day);
	}

	/** Loads the class, registering the attachment. */
	static void init() {
	}
}

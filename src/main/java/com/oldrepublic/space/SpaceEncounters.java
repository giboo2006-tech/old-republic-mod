package com.oldrepublic.space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.space.network.SpaceEncounterPayload;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Encounters in orbit (spec MILESTONE_5.md 5.6): the random patrols and Chapter Five's blockade
 * over Tyrvann, and the groups of the space objectives ({@link SpaceMissions}). Owned by W2 space.
 *
 * <p>Encounters are in-memory sessions, one per owner at a time (the M3 and M4 boss-session rule:
 * nothing saved). A restart, the owner leaving the orbit, logging out or being disabled ends one,
 * and its ships vanish with it. Other players' armed ships within {@code space.coopRange} join it:
 * its fighters attack them too, their kills count for it and pay them, and only the owner's story
 * moves.
 *
 * <p><b>Patrols.</b> While an owner pilots an armed ship in an orbit, every
 * {@code space.patrolCheckTicks} of such flight a patrol may find them ({@code space.patrolChance},
 * never within {@code space.patrolGapTicks} of their last encounter, never on Peaceful). It arrives
 * {@code space.patrolDistance} blocks away, hails and attacks; it ends when every fighter is
 * destroyed (a bonus for the owner and every pilot who scored), when the ship is
 * {@code space.patrolLeaveRange} from all of them, or when it leaves.
 *
 * <p><b>The blockade.</b> The gunship and its generator {@code space.blockadeDistance} ahead, two
 * waves of Hymn interceptors and the gunship's own launches; Clan Hask's escort fights for the
 * player when the clan stands with them. Destroying the gunship breaks it
 * ({@code QuestHooks.onBlockadeBroken}); a disabled ship resets it. On Peaceful (or with flight
 * switched off) it is a scripted pass: the interceptors hail, turn away, and the way opens after
 * {@code space.peacefulPassTicks}.
 */
public final class SpaceEncounters {
	/** How an encounter ended. */
	public enum End {
		/** Every ship destroyed. */
		CLEARED,
		/** The ship got away, or the group was lost with its chunks. */
		OUTRUN,
		/** The owner left the orbit (a jump, a dive, a rescue). */
		LEFT,
		LOGOUT,
		DISABLED,
		/** The blockade's gunship destroyed (after its wind-down). */
		BROKEN,
		/** The Peaceful pass done. */
		PASSED,
		/** Its mission's objective completed, failed or cancelled. */
		MISSION,
		/** Another encounter of the owner's took its place, or the blockade regrouped. */
		REPLACED,
		/** The world turned Peaceful. */
		PEACEFUL,
		/** A command or a test. */
		RESET
	}

	private static final int CHECK_INTERVAL = 10;
	private static final int COOP_INTERVAL = 20;
	private static final int HUD_INTERVAL = 20;
	/** A broken blockade's pilots flee for this long before the encounter closes. */
	private static final int WIND_DOWN_TICKS = 160;
	/** The pass: the interceptors turn away this long after hailing; the encounter closes this long after the way opens. */
	private static final int PASS_TURN_TICKS = 60;
	private static final int PASS_LINGER_TICKS = 600;
	/** Fleeing fighters head for a point this far out. */
	private static final double DEPART_DISTANCE = 400;
	/** A blockade whose gunship was lost with its chunk regroups ahead of the owner, at most this often. */
	private static final int REGROUP_GAP = 200;
	/** Fighters in a group stand this far apart. */
	private static final double GROUP_SPACING = 7;

	private static final List<Encounter> ENCOUNTERS = new ArrayList<>();
	private static final Map<UUID, Encounter> BY_MEMBER = new HashMap<>();
	private static final Map<UUID, Long> LAST_END = new HashMap<>();
	private static final Map<UUID, End> LAST_REASON = new HashMap<>();
	private static final Map<UUID, Long> ARMED_TICKS = new HashMap<>();
	private static final Map<UUID, SpaceEncounterPayload> SHOWN = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	// ---------------------------------------------------------------- the blockade (frozen API, 17.1)

	/** Starts {@code owner}'s blockade battle in Tyrvann's orbit; W4 calls it from {@code onOrbitEntered} during {@code BLOCKADE} (6.5). */
	public static void startBlockade(ServerPlayer owner) {
		startBlockade(owner, null, null, null);
	}

	/**
	 * Game tests: {@code owner}'s blockade with its gunship's middle at {@code centre}, as the Peaceful
	 * pass or the battle, with Clan Hask's escort or without, whatever the world's difficulty and the
	 * owner's story say (the test server has no Tyrvann, and other tests share its difficulty).
	 */
	public static void startBlockadeAt(ServerPlayer owner, Vec3 centre, boolean peaceful, boolean clan) {
		startBlockade(owner, centre, peaceful, clan);
	}

	/** The blockade; each override, when not null, takes the place of the rule it names (tests, and a regroup of a test's blockade). */
	private static void startBlockade(ServerPlayer owner, @Nullable Vec3 at, @Nullable Boolean peaceful, @Nullable Boolean clanStands) {
		if (blockadeRunning(owner)) {
			return;
		}

		MinecraftServer server = owner.level().getServer();
		ShipBody ship = Ships.ship(server, owner.getUUID());
		Entity anchor = ship != null && ship.level() == owner.level() ? ship : owner;

		if (!(anchor.level() instanceof ServerLevel level)) {
			return;
		}

		Encounter current = ofOwner(owner.getUUID());

		if (current != null) {
			end(server, current, End.REPLACED);
		}

		SpaceBalance b = SpaceBalance.get();
		long now = level.getGameTime();
		boolean pass = peaceful != null ? peaceful : level.getDifficulty() == Difficulty.PEACEFUL || !FlightBalance.get().enabled;
		boolean clan = !pass && (clanStands != null ? clanStands : ChapterFive.clanStands(owner));
		Planets.Planet system = Objects.requireNonNullElse(Planets.orbitOf(level.dimension()), Planets.Planet.TYRVANN);
		Vec3 forward = ShipBody.local(0, 0, 1, anchor.getYRot());
		Vec3 right = ShipBody.local(1, 0, 0, anchor.getYRot());
		Vec3 centre = at != null ? at : anchor.getBoundingBox().getCenter().add(forward.scale(b.blockadeDistance));

		if (at == null && Planets.isOrbit(level)) {
			centre = SpaceRules.clampToOrbit(centre);
		}

		List<String> comm = pass ? List.of(SpaceComms.PASS_HAIL, SpaceComms.PASS_THROUGH)
				: List.of(SpaceComms.BLOCKADE_HAIL, SpaceComms.BLOCKADE_VARRA, SpaceComms.BLOCKADE_WAVE, SpaceComms.BLOCKADE_GENERATOR,
						SpaceComms.BLOCKADE_BROKEN, SpaceComms.BLOCKADE_VARRA_BROKEN);
		Encounter e = begin(pass ? Encounter.Type.PASS : Encounter.Type.BLOCKADE, owner, level, system, null, null, comm);
		e.clan = clan;
		e.peaceful = peaceful;
		e.fixedCentre = at;
		e.clanOverride = clanStands;
		BlockadeGunship gunship = SpaceEntities.BLOCKADE_GUNSHIP.create(level, EntitySpawnReason.EVENT);

		if (gunship == null) {
			end(server, e, End.RESET);
			return;
		}

		// Broadside across the ship's path, circling a point beyond it.
		gunship.snapTo(centre.x, centre.y - BlockadeGunship.HEIGHT / 2, centre.z, anchor.getYRot() + 90f, 0f);
		gunship.setup(b);
		gunship.setPassive(pass);
		gunship.setPost(gunship.position().add(forward.scale(b.gunship.patrolRadius)));
		level.addFreshEntity(gunship);
		join(e, gunship);
		e.gunship = gunship.getUUID();

		int first = b.blockadeWaves.length > 0 ? b.blockadeWaves[0] : 3;
		Vec3 launch = centre.add(0, BlockadeGunship.HEIGHT + 6, 0).subtract(forward.scale(12));
		SpaceRules.Side side = pass ? SpaceRules.Side.NEUTRAL : SpaceRules.Side.HOSTILE;

		for (Starfighter fighter : group(e, level, SpaceRules.Kind.HYMN_INTERCEPTOR, launch, first, side, anchor, b)) {
			e.wave.add(fighter.getUUID());
		}

		if (pass) {
			e.turnAwayAt = now + PASS_TURN_TICKS;
			e.passAt = now + Math.max(PASS_TURN_TICKS, b.peacefulPassTicks);
			say(server, e, SpaceComms.PASS_HAIL, SpaceEffects.SPEAKER_HYMN_PILOT);
			return;
		}

		say(server, e, SpaceComms.BLOCKADE_HAIL, SpaceEffects.SPEAKER_HYMN_PILOT);

		if (clan) {
			// The clan's escort breaks formation at the gunship's flank and turns on the interceptors.
			group(e, level, SpaceRules.Kind.HASK_FIGHTER, centre.add(right.scale(30)).add(0, 4, 0), b.clanFighters, SpaceRules.Side.ALLIED, null, b);
			say(server, e, SpaceComms.BLOCKADE_VARRA, SpaceEffects.SPEAKER_VARRA);
		}
	}

	/** Whether {@code owner}'s blockade battle (or the Peaceful pass) is running and not yet broken. */
	public static boolean blockadeRunning(ServerPlayer owner) {
		Encounter e = ofOwner(owner.getUUID());
		return e != null && (e.type == Encounter.Type.BLOCKADE || e.type == Encounter.Type.PASS) && !e.finished();
	}

	/**
	 * The playthrough shortcut ({@code /oldrepublic space blockade win}): a running blockade is broken
	 * at once; with none running, the story is told it was. Returns whether one was running.
	 */
	static boolean winBlockade(ServerPlayer owner) {
		Encounter e = ofOwner(owner.getUUID());

		if (e != null && (e.type == Encounter.Type.BLOCKADE || e.type == Encounter.Type.PASS) && !e.finished()) {
			finish(owner.level().getServer(), e, owner.level().getGameTime());
			end(owner.level().getServer(), e, End.BROKEN);
			return true;
		}

		QuestHooks.onBlockadeBroken(owner);
		return false;
	}

	// ---------------------------------------------------------------- members

	/** The owner and co-op pilots of the encounter {@code member} flies in, or null when it has none (a spawn egg's fighter). */
	public static @Nullable Set<UUID> participants(Entity member) {
		Encounter e = BY_MEMBER.get(member.getUUID());
		return e == null ? null : Collections.unmodifiableSet(e.participants);
	}

	/** The owner of the encounter {@code member} flies in, or null. */
	public static @Nullable UUID ownerOf(Entity member) {
		Encounter e = BY_MEMBER.get(member.getUUID());
		return e == null ? null : e.owner;
	}

	static @Nullable Encounter encounterOf(Entity member) {
		return BY_MEMBER.get(member.getUUID());
	}

	/** {@code owner}'s encounter, or null. */
	static @Nullable Encounter ofOwner(UUID owner) {
		for (Encounter e : ENCOUNTERS) {
			if (e.owner.equals(owner)) {
				return e;
			}
		}

		return null;
	}

	/** A ship {@code parent} puts out (a fighter from the gunship's bay, its shield generator) joins its encounter. */
	static void adopt(Entity parent, Entity child) {
		Encounter e = BY_MEMBER.get(parent.getUUID());

		if (e != null) {
			join(e, child);
		}
	}

	private static void join(Encounter e, Entity member) {
		if (e.members.add(member.getUUID())) {
			e.spawned++;
			BY_MEMBER.put(member.getUUID(), e);
		}
	}

	/**
	 * A neutral fighter was fired on by a player: its whole group turns hostile (a scanning patrol
	 * stops scanning and fights, 5.9). The Peaceful pass's interceptors never fight back.
	 */
	static void onProvoked(Starfighter fighter) {
		Encounter e = BY_MEMBER.get(fighter.getUUID());

		if (e == null || !(fighter.level() instanceof ServerLevel level)) {
			return;
		}

		if (e.type == Encounter.Type.PASS) {
			fighter.setSide(SpaceRules.Side.NEUTRAL);
			return;
		}

		if (e.provoked) {
			return;
		}

		e.provoked = true;

		for (UUID id : e.members) {
			if (level.getEntity(id) instanceof Starfighter member && member.side() == SpaceRules.Side.NEUTRAL) {
				member.setSide(SpaceRules.Side.HOSTILE);
			}
		}

		if (e.type == Encounter.Type.RUN_PATROL) {
			say(level.getServer(), e, SpaceComms.SCAN_PROVOKED, SpaceComms.speaker(fighter.kind()));
			SpaceMissions.onProvoked(level.getServer(), e);
		}
	}

	/**
	 * A ship of an encounter was destroyed (from {@link SpaceRewards}): the kill is counted, the
	 * blockade moves on (the next wave, the generator's fall, the gunship's end), a patrol whose last
	 * fighter fell ends, and a group to clear tells its mission.
	 */
	static void onDestroyed(ServerLevel level, LivingEntity target, SpaceRules.Kind kind, @Nullable ServerPlayer shooter) {
		Encounter e = BY_MEMBER.remove(target.getUUID());

		if (e == null || !e.members.remove(target.getUUID())) {
			return;
		}

		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		e.destroyed++;

		if (shooter != null) {
			e.kills.merge(shooter.getUUID(), 1, Integer::sum);
		}

		switch (e.type) {
			case BLOCKADE -> blockadeLoss(server, level, e, target, kind, now);
			case PATROL, RUN_PATROL -> {
				if (e.members.isEmpty()) {
					end(server, e, End.CLEARED);
				}
			}
			case CLEAR -> {
				if (target instanceof Starfighter fighter && fighter.ace() && !e.members.isEmpty()) {
					say(server, e, SpaceComms.ACE_DOWN, SpaceComms.speaker(kind));
				}

				if (e.members.isEmpty()) {
					SpaceMissions.onGroupCleared(server, e);
				}
			}
			default -> {
			}
		}
	}

	private static void blockadeLoss(MinecraftServer server, ServerLevel level, Encounter e, LivingEntity target, SpaceRules.Kind kind, long now) {
		if (e.finished()) {
			return;
		}

		if (kind == SpaceRules.Kind.GENERATOR) {
			say(server, e, SpaceComms.BLOCKADE_GENERATOR, SpaceEffects.SPEAKER_HYMN_PILOT);
			return;
		}

		if (kind == SpaceRules.Kind.GUNSHIP) {
			finish(server, e, now);
			return;
		}

		SpaceBalance b = SpaceBalance.get();

		if (e.wave.remove(target.getUUID()) && e.wave.isEmpty() && e.waveIndex + 1 < b.blockadeWaves.length) {
			e.waveIndex++;
			BlockadeGunship gunship = e.gunship != null && level.getEntity(e.gunship) instanceof BlockadeGunship g ? g : null;
			Vec3 from = gunship != null ? gunship.toWorld(0, BlockadeGunship.HEIGHT + 6, 0) : target.position();
			Entity ship = Ships.ship(server, e.owner);

			for (Starfighter fighter : group(e, level, SpaceRules.Kind.HYMN_INTERCEPTOR, from, b.blockadeWaves[e.waveIndex], SpaceRules.Side.HOSTILE, ship, b)) {
				e.wave.add(fighter.getUUID());
			}

			say(server, e, SpaceComms.BLOCKADE_WAVE, SpaceEffects.SPEAKER_HYMN_PILOT);
		}
	}

	/** The blockade is broken (or the pass done): the story is told once, the survivors flee, and the encounter winds down. */
	private static void finish(MinecraftServer server, Encounter e, long now) {
		if (e.finished()) {
			return;
		}

		e.finishedAt = now;
		ServerPlayer owner = server.getPlayerList().getPlayer(e.owner);
		ServerLevel level = server.getLevel(e.dimension);

		if (e.type == Encounter.Type.PASS) {
			say(server, e, SpaceComms.PASS_THROUGH, SpaceEffects.SPEAKER_HYMN_PILOT);
		} else {
			say(server, e, SpaceComms.BLOCKADE_BROKEN, SpaceEffects.SPEAKER_HYMN_PILOT);

			if (e.clan) {
				say(server, e, SpaceComms.BLOCKADE_VARRA_BROKEN, SpaceEffects.SPEAKER_VARRA);
			}

			if (level != null) {
				departAll(level, e);
				fizzle(server, level, e);
			}
		}

		if (owner != null) {
			QuestHooks.onBlockadeBroken(owner);
		}
	}

	/**
	 * The missiles {@code e}'s ships still have in the air go out with the fight (a broken blockade, an
	 * encounter that ends): one fired a moment before must not disable the winner's ship afterwards.
	 * Each pilot's ship is searched as far as a missile flies in its life.
	 */
	private static void fizzle(MinecraftServer server, ServerLevel level, Encounter e) {
		Set<Integer> shooters = new HashSet<>();

		for (UUID id : e.members) {
			Entity member = level.getEntity(id);

			if (member != null) {
				shooters.add(member.getId());
			}
		}

		if (shooters.isEmpty()) {
			return;
		}

		SpaceBalance b = SpaceBalance.get();
		double reach = b.missileSpeed * Math.max(0, b.missileLife);

		for (UUID pilot : e.participants) {
			ShipBody ship = Ships.ship(server, pilot);

			if (ship == null || ship.level() != level) {
				continue;
			}

			for (ShipMissile missile : level.getEntitiesOfClass(ShipMissile.class, ship.getBoundingBox().inflate(reach), m -> shooters.contains(m.shipId()))) {
				missile.fizzle(level);
			}
		}
	}

	/** A run's patrol completed its scan (5.9): it turns away with what it found, and the encounter closes after its wind-down. */
	static void windDown(MinecraftServer server, Encounter e, long now) {
		if (e.finished()) {
			return;
		}

		e.finishedAt = now;
		ServerLevel level = server.getLevel(e.dimension);

		if (level != null) {
			departAll(level, e);
		}

		for (UUID id : e.participants) {
			refresh(server, id);
		}
	}

	/** Every fighter of {@code e} breaks off and flies away along its heading, climbing. */
	private static void departAll(ServerLevel level, Encounter e) {
		for (UUID id : e.members) {
			if (level.getEntity(id) instanceof Starfighter fighter) {
				Vec3 away = new Vec3(fighter.heading().x, 0, fighter.heading().z);
				away = away.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : away.normalize();
				fighter.depart(fighter.position().add(away.scale(DEPART_DISTANCE)).add(0, 30, 0));
			}
		}
	}

	/** {@code owner}'s ship was disabled (5.4): their encounter ends, and a blockade resets (counted as a failure, 6.5). */
	public static void onDisabled(ServerPlayer owner) {
		MinecraftServer server = owner.level().getServer();
		Encounter e = ofOwner(owner.getUUID());

		if (e != null) {
			boolean failedBlockade = e.type == Encounter.Type.BLOCKADE && !e.finished();
			end(server, e, End.DISABLED);

			if (failedBlockade) {
				ChapterFiveService.onBlockadeFailed(owner);
			}
		}

		SpaceMissions.onDisabled(owner);
	}

	/** {@code p} left the server: their encounter ends, and they leave the others. */
	static void onLeave(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		Encounter e = ofOwner(p.getUUID());

		if (e != null) {
			end(server, e, End.LOGOUT);
		}

		for (Encounter other : ENCOUNTERS) {
			other.participants.remove(p.getUUID());
		}

		SHOWN.remove(p.getUUID());
		ARMED_TICKS.remove(p.getUUID());
	}

	// ---------------------------------------------------------------- building encounters

	/** A new encounter of {@code owner}'s in {@code level}. */
	static Encounter begin(Encounter.Type type, ServerPlayer owner, ServerLevel level, Planets.Planet system, @Nullable String missionId,
			@Nullable Vec3 point, List<String> comm) {
		Encounter e = new Encounter(type, owner.getUUID(), level.dimension(), system, level.getGameTime(), missionId, point, comm);
		ENCOUNTERS.add(e);
		refresh(level.getServer(), owner.getUUID());
		return e;
	}

	/**
	 * A group of {@code size} fighters of {@code kind} at {@code at}, in two files behind a leader,
	 * facing {@code target} and engaging it (when not null); Hask wingmen hold formation on the leader.
	 */
	static List<Starfighter> group(Encounter e, ServerLevel level, SpaceRules.Kind kind, Vec3 at, int size, SpaceRules.Side side, @Nullable Entity target,
			SpaceBalance b) {
		Vec3 toward = target == null ? new Vec3(0, 0, 1) : target.getBoundingBox().getCenter().subtract(at);
		toward = toward.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : toward.normalize();
		Vec3 right = toward.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
		List<Starfighter> out = new ArrayList<>();
		Starfighter leader = null;

		for (int i = 0; i < size; i++) {
			int rank = (i + 1) / 2;
			double sideSign = i % 2 == 0 ? 1 : -1;
			Vec3 pos = at.add(right.scale(rank * GROUP_SPACING * sideSign)).subtract(toward.scale(rank * GROUP_SPACING * 0.7)).add(0, (i % 3) * 1.5, 0);
			Starfighter fighter = spawn(e, level, kind, pos, side, false, target, b);

			if (fighter == null) {
				continue;
			}

			if (leader == null) {
				leader = fighter;
			} else if (out.size() <= 2) {
				fighter.follow(leader.getUUID(), out.size());
			}

			out.add(fighter);
		}

		return out;
	}

	/** One fighter of {@code e}: set up, pointed at {@code target} (engaging it, when not null) and added to the level. */
	static @Nullable Starfighter spawn(Encounter e, ServerLevel level, SpaceRules.Kind kind, Vec3 at, SpaceRules.Side side, boolean ace,
			@Nullable Entity target, SpaceBalance b) {
		EntityType<? extends Starfighter> type = switch (kind) {
			case RAIDER -> SpaceEntities.KAJIDIC_RAIDER;
			case HASK_FIGHTER -> SpaceEntities.HASK_FIGHTER;
			default -> SpaceEntities.HYMN_INTERCEPTOR;
		};
		Starfighter fighter = type.create(level, EntitySpawnReason.EVENT);

		if (fighter == null) {
			return null;
		}

		Vec3 toward = target == null ? new Vec3(0, 0, 1) : target.getBoundingBox().getCenter().subtract(at);
		toward = toward.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : toward.normalize();
		fighter.snapTo(at.x, at.y, at.z, SpaceRules.yawOf(toward), SpaceRules.pitchOf(toward));
		fighter.setup(side, ace, b);
		fighter.launchAlong(toward, 0.8);
		fighter.engage(target);

		if (!level.addFreshEntity(fighter)) {
			return null;
		}

		join(e, fighter);
		return fighter;
	}

	/**
	 * Where a group arrives about {@code distance} blocks from {@code from}: open space in an
	 * entity-ticking chunk, inside the orbit's band (in an orbit); a nearer point when no far one
	 * will do, and null when none will.
	 */
	static @Nullable Vec3 arrival(ServerLevel level, Vec3 from, double distance) {
		boolean orbit = Planets.isOrbit(level);

		for (int attempt = 0; attempt < 16; attempt++) {
			double d = distance * (attempt < 8 ? 1.0 : attempt < 12 ? 0.7 : 0.45);
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 p = from.add(Math.cos(angle) * d, (RANDOM.nextDouble() - 0.5) * 30, Math.sin(angle) * d);
			p = orbit ? SpaceRules.clampToOrbit(p) : p;
			BlockPos pos = BlockPos.containing(p);

			if (level.isPositionEntityTicking(pos) && level.getBlockState(pos).isAir()) {
				return p;
			}
		}

		return null;
	}

	/** Ends {@code e}: its ships vanish, a cleared patrol pays its bonus, its mission hears of it, and the HUDs are told. */
	static void end(MinecraftServer server, Encounter e, End reason) {
		if (!ENCOUNTERS.remove(e)) {
			return;
		}

		ServerLevel level = server.getLevel(e.dimension);

		if (level != null) {
			fizzle(server, level, e);
		}

		for (UUID id : e.members) {
			BY_MEMBER.remove(id);
			Entity member = level == null ? null : level.getEntity(id);

			if (member != null && !member.isRemoved()) {
				member.discard();
			}
		}

		e.members.clear();
		LAST_END.put(e.owner, server.overworld().getGameTime());
		LAST_REASON.put(e.owner, reason);

		if (reason == End.CLEARED && e.type == Encounter.Type.PATROL) {
			int bonus = SpaceBalance.get().patrolBonusXp;

			for (UUID id : e.participants) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);

				if (p != null && (id.equals(e.owner) || e.kills.containsKey(id))) {
					SpaceRewards.bonusXp(p, bonus);
				}
			}
		}

		if (e.missionId != null) {
			SpaceMissions.onEncounterEnded(server, e, reason);
		}

		for (UUID id : e.participants) {
			refresh(server, id);
		}
	}

	// ---------------------------------------------------------------- comms and the HUD

	/** {@code key} spoken by {@code speaker} to everyone in {@code e} whose HUD shows it (a subtitle and the radio crackle). */
	static void say(MinecraftServer server, Encounter e, String key, int speaker) {
		int index = e.line(key);

		if (index < 0) {
			return;
		}

		SpaceEncounterPayload view = e.payload();

		for (UUID id : e.participants) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);

			if (p == null) {
				continue;
			}

			refresh(server, id);

			if (view.equals(SHOWN.get(id))) {
				EffectEvents.toPlayer(p, SpaceEffects.SPACE_COMM, p.position(), null, -1, index, speaker);
			}
		}
	}

	/** Sends {@code playerId} their situation when it changed. */
	static void refresh(MinecraftServer server, UUID playerId) {
		ServerPlayer p = server.getPlayerList().getPlayer(playerId);

		if (p == null) {
			SHOWN.remove(playerId);
			return;
		}

		SpaceEncounterPayload view = view(p);

		if (!view.equals(SHOWN.get(playerId))) {
			SHOWN.put(playerId, view);
			SpaceNetwork.send(p, view);
		}
	}

	/** What {@code p}'s HUD shows: their own encounter, else their objective in this orbit, else an encounter they joined. */
	private static SpaceEncounterPayload view(ServerPlayer p) {
		Encounter own = ofOwner(p.getUUID());

		if (own != null && own.dimension == p.level().dimension()) {
			return own.payload();
		}

		SpaceEncounterPayload mission = SpaceMissions.view(p);

		if (mission != null) {
			return mission;
		}

		for (Encounter e : ENCOUNTERS) {
			if (e.participants.contains(p.getUUID()) && e.dimension == p.level().dimension()) {
				return e.payload();
			}
		}

		return SpaceEncounterPayload.CLEARED;
	}

	// ---------------------------------------------------------------- ticking

	/** One server tick: patrols finding armed ships, then every encounter's checks, co-op and the HUDs. */
	static void tick(MinecraftServer server) {
		SpaceBalance b = SpaceBalance.get();
		long now = server.overworld().getGameTime();
		findPatrols(server, b, now);

		if (now % CHECK_INTERVAL == 0) {
			for (Encounter e : List.copyOf(ENCOUNTERS)) {
				check(server, e, b, now);
			}
		}

		if (now % COOP_INTERVAL == 0) {
			coop(server, b);
		}

		if (now % HUD_INTERVAL == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				refresh(server, p.getUUID());
			}
		}
	}

	private static void findPatrols(MinecraftServer server, SpaceBalance b, long now) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ShipBody ship = ShipWeapons.piloted(p);

			if (ship == null || !(ship.level() instanceof ServerLevel level) || !Planets.isOrbit(level) || ship.state() != ShipState.FLYING
					|| (ship.upgrades() & ShipRecord.UPGRADE_CANNONS) == 0) {
				continue;
			}

			long ticks = ARMED_TICKS.merge(p.getUUID(), 1L, Long::sum);

			if (ticks % Math.max(1, b.patrolCheckTicks) != 0 || ofOwner(p.getUUID()) != null || level.getDifficulty() == Difficulty.PEACEFUL
					|| SpaceMissions.busy(p)) {
				continue;
			}

			long lastEnd = LAST_END.getOrDefault(p.getUUID(), Long.MIN_VALUE / 4);

			if (SpaceRules.patrolFinds(true, ticks, now, lastEnd, RANDOM.nextFloat(), b)) {
				startPatrol(p, ship, level, b);
			}
		}
	}

	/** A patrol finds {@code owner} (5.6): who flies it by the orbit and the owner's standing, arriving at a distance, hailing. */
	static @Nullable Encounter startPatrol(ServerPlayer owner, ShipBody ship, ServerLevel level, SpaceBalance b) {
		Planets.Planet system = Objects.requireNonNullElse(Planets.orbitOf(level.dimension()), Planets.Planet.HOME);
		int size = SpaceRules.patrolSize(system, Identity.tier(owner, Faction.VESSBA_KAJIDIC) == ReputationTier.HOSTILE, b, RANDOM);
		Vec3 at = arrival(level, ship.getBoundingBox().getCenter(), b.patrolDistance);
		return at == null ? null : patrol(owner, ship, level, at, size, b);
	}

	/**
	 * Game tests: a patrol finds {@code owner} now, {@code size} fighters arriving at {@code at}, of the
	 * kind the orbit and the owner's standing call for (the test server has no orbit to fly armed in);
	 * it plays by the rules of a world that is not Peaceful. False when the owner's ship is not in their
	 * level or they already have an encounter.
	 */
	public static boolean startPatrolAt(ServerPlayer owner, Vec3 at, int size) {
		MinecraftServer server = owner.level().getServer();
		ShipBody ship = Ships.ship(server, owner.getUUID());

		if (ship == null || !(ship.level() instanceof ServerLevel level) || ship.level() != owner.level() || ofOwner(owner.getUUID()) != null) {
			return false;
		}

		Encounter e = patrol(owner, ship, level, at, Math.max(1, size), SpaceBalance.get());
		e.peaceful = false;
		return true;
	}

	/** The patrol itself: its fighters at {@code at}, engaging the ship, and its hail. */
	private static Encounter patrol(ServerPlayer owner, ShipBody ship, ServerLevel level, Vec3 at, int size, SpaceBalance b) {
		Planets.Planet system = Objects.requireNonNullElse(Planets.orbitOf(level.dimension()), Planets.Planet.HOME);
		boolean blockadeBroken = ChapterFive.atLeast(owner, ChapterFiveStage.TYRVANN);
		boolean clanHostile = Identity.tier(owner, Faction.CLAN_HASK) == ReputationTier.HOSTILE;
		SpaceRules.Kind kind = SpaceRules.patrolKind(system, blockadeBroken, clanHostile);

		if (kind == SpaceRules.Kind.HYMN_INTERCEPTOR) {
			// Ulvaine's pilots fly in pairs.
			size = Math.max(2, size - size % 2);
		}

		String hail = SpaceComms.hail(kind, RANDOM);
		Encounter e = begin(Encounter.Type.PATROL, owner, level, system, null, null, List.of(hail));
		group(e, level, kind, at, size, SpaceRules.Side.HOSTILE, ship, b);
		say(level.getServer(), e, hail, SpaceComms.speaker(kind));
		return e;
	}

	/**
	 * Why an encounter ends at its periodic check before its own rules (5.6): its owner logged out,
	 * left the orbit (their ship is gone from it: a jump, a dive, a rescue), or the world turned
	 * Peaceful (the Peaceful pass goes on); null while none of these holds.
	 */
	public static @Nullable End leaving(boolean ownerOnline, boolean shipHere, boolean peaceful, boolean pass) {
		if (!ownerOnline) {
			return End.LOGOUT;
		}

		if (!shipHere) {
			return End.LEFT;
		}

		return peaceful && !pass ? End.PEACEFUL : null;
	}

	/** An encounter's periodic checks: its owner still here, its ships still near, the blockade's and the pass's timers. */
	private static void check(MinecraftServer server, Encounter e, SpaceBalance b, long now) {
		ServerLevel level = server.getLevel(e.dimension);
		ServerPlayer owner = level == null ? null : server.getPlayerList().getPlayer(e.owner);
		ShipBody ship = Ships.ship(server, e.owner);
		boolean peaceful = level != null && (e.peaceful != null ? e.peaceful : level.getDifficulty() == Difficulty.PEACEFUL);
		End leaving = leaving(owner != null, level != null && ship != null && ship.level() == level, peaceful, e.type == Encounter.Type.PASS);

		if (leaving != null || level == null || owner == null || ship == null) {
			end(server, e, leaving != null ? leaving : End.LOGOUT);
			return;
		}

		e.participants.removeIf(id -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			return !id.equals(e.owner) && (p == null || p.level() != level);
		});
		boolean lost = e.members.removeIf(id -> {
			Entity member = level.getEntity(id);

			if (member == null || member.isRemoved()) {
				BY_MEMBER.remove(id);
				return true;
			}

			return false;
		});

		switch (e.type) {
			case PATROL, RUN_PATROL -> {
				if (e.finished()) {
					if (e.members.isEmpty() || now - e.finishedAt > WIND_DOWN_TICKS) {
						end(server, e, End.MISSION);
					}
				} else if (e.members.isEmpty() || farFromAll(level, ship, e, b.patrolLeaveRange)) {
					end(server, e, End.OUTRUN);
				}
			}
			case CLEAR -> {
				if (e.members.isEmpty() && lost) {
					// Lost with its chunks: the mission places the group again when the ship comes back.
					end(server, e, End.OUTRUN);
				}
			}
			case BLOCKADE -> {
				if (e.finished()) {
					if (now - e.finishedAt > WIND_DOWN_TICKS) {
						end(server, e, End.BROKEN);
					}
				} else if (e.gunship != null && level.getEntity(e.gunship) == null && now - e.started > REGROUP_GAP) {
					// The gunship went with its chunk: the blockade regroups ahead of the ship.
					end(server, e, End.REPLACED);
					startBlockade(owner, e.fixedCentre, e.peaceful, e.clanOverride);
				}
			}
			case PASS -> pass(server, level, e, ship, b, now);
			default -> {
			}
		}
	}

	/** The Peaceful pass: the interceptors turn away, the way opens, and the encounter closes once the ship is past or after a while. */
	private static void pass(MinecraftServer server, ServerLevel level, Encounter e, ShipBody ship, SpaceBalance b, long now) {
		if (e.turnAwayAt != Long.MIN_VALUE && now >= e.turnAwayAt) {
			e.turnAwayAt = Long.MIN_VALUE;
			departAll(level, e);
		}

		if (!e.finished() && now >= e.passAt) {
			finish(server, e, now);
		}

		Entity gunship = e.gunship == null ? null : level.getEntity(e.gunship);
		double leave = b.patrolLeaveRange;
		boolean past = gunship == null || gunship.distanceToSqr(ship) > leave * leave;

		if (e.finished() && (past || now - e.finishedAt > PASS_LINGER_TICKS)) {
			end(server, e, End.PASSED);
		}
	}

	private static boolean farFromAll(ServerLevel level, ShipBody ship, Encounter e, double range) {
		for (UUID id : e.members) {
			Entity member = level.getEntity(id);

			if (member != null && member.distanceToSqr(ship) <= range * range) {
				return false;
			}
		}

		return true;
	}

	/** Other players' armed ships near an owner join the owner's encounter, and leave it when they fly well away (5.6). */
	private static void coop(MinecraftServer server, SpaceBalance b) {
		for (Encounter e : ENCOUNTERS) {
			ServerLevel level = server.getLevel(e.dimension);
			ShipBody owned = level == null || e.type == Encounter.Type.PASS ? null : Ships.ship(server, e.owner);

			if (owned == null || owned.level() != level) {
				continue;
			}

			for (ServerPlayer p : level.players()) {
				if (p.getUUID().equals(e.owner)) {
					continue;
				}

				ShipBody ship = ShipWeapons.piloted(p);
				double distance = ship == null ? Double.MAX_VALUE : ship.distanceToSqr(owned);

				if (ship != null && SpaceRules.coopJoins((ship.upgrades() & ShipRecord.UPGRADE_CANNONS) != 0, distance, b)) {
					if (e.participants.add(p.getUUID())) {
						refresh(server, p.getUUID());
					}
				} else if (SpaceRules.coopLeaves(distance, b) && e.participants.remove(p.getUUID())) {
					refresh(server, p.getUUID());
				}
			}
		}
	}

	// ---------------------------------------------------------------- tests and commands

	/**
	 * A copy of an encounter's state (game tests and the status command): its type's name
	 * ({@code PATROL}, {@code BLOCKADE}, {@code PASS}, {@code RUN_PATROL}, {@code CLEAR}), its live
	 * ships, the blockade's gunship and current wave, who flies in it, the kills by shooter, and
	 * whether it is finished (a broken blockade, a pass done, a scan complete) and winding down.
	 */
	public record Snapshot(String type, UUID owner, Set<UUID> members, Set<UUID> wave, int waveIndex, @Nullable UUID gunship, Set<UUID> participants,
			Map<UUID, Integer> kills, int spawned, int destroyed, boolean finished, boolean clan, boolean provoked, @Nullable String missionId,
			String ace) {
	}

	/** {@code owner}'s encounter as it stands, or null when they have none. */
	public static @Nullable Snapshot snapshot(UUID owner) {
		Encounter e = ofOwner(owner);
		return e == null ? null
				: new Snapshot(e.type.name(), e.owner, Set.copyOf(e.members), Set.copyOf(e.wave), e.waveIndex, e.gunship, Set.copyOf(e.participants),
						Map.copyOf(e.kills), e.spawned, e.destroyed, e.finished(), e.clan, e.provoked, e.missionId, e.ace);
	}

	/** How {@code owner}'s last encounter ended, or null when none has since the server started. */
	public static @Nullable End lastEnd(UUID owner) {
		return LAST_REASON.get(owner);
	}

	/** The overworld game time at which {@code owner}'s last encounter ended (the patrol gap counts from it), or null. */
	public static @Nullable Long lastEndTime(UUID owner) {
		return LAST_END.get(owner);
	}

	/**
	 * Game tests: {@code pilot} joins {@code owner}'s encounter as a co-op pilot, as an armed ship that
	 * {@link SpaceRules#coopJoins} admits does (the test server has no helm to fly it by). The next
	 * co-op pass lets a pilot who is not flying go again. False when the owner has no encounter, or it
	 * is the Peaceful pass.
	 */
	public static boolean joinCoop(ServerPlayer owner, ServerPlayer pilot) {
		Encounter e = ofOwner(owner.getUUID());

		if (e == null || e.type == Encounter.Type.PASS) {
			return false;
		}

		if (e.participants.add(pilot.getUUID())) {
			refresh(owner.level().getServer(), pilot.getUUID());
		}

		return true;
	}

	/** Every encounter (tests). */
	static List<Encounter> all() {
		return List.copyOf(ENCOUNTERS);
	}

	/** Ends every encounter and forgets everything (tests and the reset command). */
	static void reset(MinecraftServer server) {
		for (Encounter e : List.copyOf(ENCOUNTERS)) {
			end(server, e, End.RESET);
		}

		clear();
	}

	/** Forgets everything without touching any entity (the server stopped; the ships were never saved). */
	static void clear() {
		ENCOUNTERS.clear();
		BY_MEMBER.clear();
		LAST_END.clear();
		LAST_REASON.clear();
		ARMED_TICKS.clear();
		SHOWN.clear();
	}

	private SpaceEncounters() {
	}
}

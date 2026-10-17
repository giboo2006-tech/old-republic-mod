package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Imperial sergeant's flare (spec 7.3): {@code reinforceDelayTicks} after it goes up,
 * {@code reinforceCount} Imperial riflemen join the squad, arriving {@code reinforceDistance}
 * blocks from the sergeant on the side away from the threat, out of every player's sight, and run
 * in. Every member's squad size grows by the arrivals (morale counts them). Arrivals stand near the
 * sergeant's height, so a fight under a roof or underground is joined there; only a sergeant under
 * open sky has them come over the surface. Arrivals of a patrol join it; the others are never saved
 * and walk off when the squad's encounter ends ({@link #dismiss}). Pending arrivals are kept in
 * memory on the server and dropped when it stops.
 */
public final class Reinforcements {
	/** Placement attempts per rifleman before settling for a spot a player might see. */
	private static final int ATTEMPTS = 16;
	/** How far above and below the sergeant's feet an arrival looks for standing room. */
	private static final int VERTICAL_SEARCH = 8;
	/** Arrivals come within this many degrees either side of the line away from the threat. */
	private static final double SPREAD_DEGREES = 60;
	private static final double RUN_SPEED = 1.2;

	/** {@code squadSize}: the sergeant's squad size when the flare went up, before the arrivals. */
	private record Pending(ResourceKey<Level> dimension, UUID squad, int squadSize, Vec3 caller, Vec3 threat,
			@Nullable UUID threatId, long due, int count, @Nullable UUID owner, IncidentMode incident, long incidentUntil,
			boolean patrol) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	/** Schedules the reinforcements called by {@code sergeant}'s flare against {@code threat}. */
	public static void schedule(TrooperEntity sergeant, @Nullable LivingEntity threat, long due) {
		TrooperBalance b = TrooperBalance.get();
		UUID squad = Squads.key(sergeant);
		Vec3 threatPos = threat != null ? threat.position() : sergeant.position().add(sergeant.getLookAngle().scale(8));
		PENDING.add(new Pending(sergeant.level().dimension(), squad, sergeant.squadSize(), sergeant.position(),
				threatPos, threat != null ? threat.getUUID() : null, due, b.reinforceCount, sergeant.owner(), sergeant.incidentMode(),
				sergeant.incidentUntil(), sergeant.isPatrol()));
	}

	/** Riflemen still on their way to {@code squad}. */
	public static int pending(UUID squad) {
		int count = 0;

		for (Pending pending : PENDING) {
			if (pending.squad().equals(squad)) {
				count += pending.count();
			}
		}

		return count;
	}

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		List<Pending> due = new ArrayList<>();

		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending pending = it.next();
			ServerLevel level = server.getLevel(pending.dimension());

			if (level == null) {
				it.remove();
			} else if (level.getGameTime() >= pending.due()) {
				it.remove();
				due.add(pending);
			}
		}

		for (Pending pending : due) {
			ServerLevel level = server.getLevel(pending.dimension());

			if (level != null) {
				arrive(level, pending);
			}
		}
	}

	private static void arrive(ServerLevel level, Pending pending) {
		TrooperBalance b = TrooperBalance.get();
		Vec3 away = pending.caller().subtract(pending.threat()).multiply(1, 0, 1);

		if (away.lengthSqr() < 1.0e-4) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2;
			away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		}

		away = away.normalize();
		LivingEntity threat = pending.threatId() != null && level.getEntity(pending.threatId()) instanceof LivingEntity living ? living : null;
		List<TrooperEntity> squad = new ArrayList<>(Squads.members(level, pending.squad(), pending.caller(), Squads.SQUAD_RADIUS));
		int size = pending.squadSize();

		for (TrooperEntity member : squad) {
			size = Math.max(size, member.squadSize());
		}

		int arrived = 0;

		for (int i = 0; i < pending.count(); i++) {
			Vec3 spot = findSpot(level, pending.caller(), away, b);

			if (spot == null) {
				continue;
			}

			TrooperEntity rifleman = Troopers.create(level, Faction.SITH_EMPIRE, TrooperRole.RIFLEMAN, spot, EntitySpawnReason.REINFORCEMENT);

			if (rifleman == null) {
				continue;
			}

			rifleman.setSquad(pending.squad(), size + pending.count());
			rifleman.setOwner(pending.owner());
			rifleman.setIncident(pending.incident(), pending.incidentUntil());

			if (pending.patrol()) {
				rifleman.setPatrol(true);
			} else {
				rifleman.setReinforcement(true);
			}

			if (!level.addFreshEntity(rifleman)) {
				continue;
			}

			arrived++;
			squad.add(rifleman);

			if (pending.patrol()) {
				PatrolSpawner.join(pending.squad(), rifleman);
			}

			if (threat != null && rifleman.wants(threat)) {
				rifleman.setTarget(threat);
			} else {
				rifleman.getNavigation().moveTo(pending.caller().x, pending.caller().y, pending.caller().z, RUN_SPEED);
			}
		}

		// Every member, old and new, counts the same squad for morale.
		for (TrooperEntity member : squad) {
			member.setSquad(pending.squad(), size + arrived);
		}
	}

	/**
	 * The encounter of {@code leader}'s squad is over: its flare arrivals that are not on patrol leave
	 * the squad and walk off (removed out of sight, never saved), and the rest of the squad's size
	 * shrinks by as many. Returns whether anyone left.
	 */
	static boolean dismiss(TrooperEntity leader) {
		List<TrooperEntity> squad = Squads.squadOf(leader);
		List<TrooperEntity> going = new ArrayList<>();

		for (TrooperEntity member : squad) {
			if (member.isReinforcement() && !member.isLeaving()) {
				going.add(member);
			}
		}

		if (going.isEmpty()) {
			return false;
		}

		Player nearest = leader.level().getNearestPlayer(leader, Squads.SQUAD_RADIUS);
		Vec3 awayFrom = nearest != null ? nearest.position() : leader.position();

		for (TrooperEntity member : squad) {
			if (going.contains(member)) {
				member.setSquad(null, 1);
				member.leave(null, awayFrom);
			} else {
				member.setSquad(member.squad(), member.squadSize() - going.size());
			}
		}

		return true;
	}

	/**
	 * A loaded spot with standing room {@code reinforceDistance} from {@code caller}, within
	 * {@value #SPREAD_DEGREES} degrees of {@code away}, that no player can see; failing that, the
	 * first such spot tried; null when none has room.
	 */
	private static @Nullable Vec3 findSpot(ServerLevel level, Vec3 caller, Vec3 away, TrooperBalance b) {
		RandomSource random = level.getRandom();
		int min = b.reinforceDistance[0];
		int max = b.reinforceDistance[b.reinforceDistance.length - 1];
		boolean openSky = caller.y + 0.5 >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(caller.x), Mth.floor(caller.z));
		Vec3 fallback = null;

		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double turn = Math.toRadians((random.nextDouble() * 2 - 1) * SPREAD_DEGREES);
			double cos = Math.cos(turn);
			double sin = Math.sin(turn);
			Vec3 dir = new Vec3(away.x * cos - away.z * sin, 0, away.x * sin + away.z * cos);
			double distance = min + random.nextDouble() * Math.max(0, max - min);
			int x = Mth.floor(caller.x + dir.x * distance);
			int z = Mth.floor(caller.z + dir.z * distance);

			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}

			Vec3 spot = standingSpot(level, x, z, caller.y, openSky);

			if (spot == null) {
				continue;
			}

			if (TrooperSight.unseen(level, spot.add(0, 1.6, 0)) && TrooperSight.unseen(level, spot.add(0, 0.2, 0))) {
				return spot;
			}

			if (fallback == null) {
				fallback = spot;
			}
		}

		return fallback;
	}

	/**
	 * Where a trooper can stand in the column at {@code x, z}: the free spot nearest {@code nearY}
	 * within {@value #VERTICAL_SEARCH} blocks above or below, or, under open sky, the top of the
	 * column (the surface, never the roof of a cave or a building below it). Null when neither has room.
	 */
	private static @Nullable Vec3 standingSpot(ServerLevel level, int x, int z, double nearY, boolean openSky) {
		if (openSky) {
			BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			return standable(level, top) ? Vec3.atBottomCenterOf(top) : null;
		}

		int feet = Mth.floor(nearY + 0.5);

		for (int step = 0; step <= VERTICAL_SEARCH * 2; step++) {
			// 0, +1, -1, +2, -2, ...
			int dy = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
			BlockPos pos = new BlockPos(x, feet + dy, z);

			if (standable(level, pos)) {
				return Vec3.atBottomCenterOf(pos);
			}
		}

		return null;
	}

	/** Solid ground under {@code feet} and room for a trooper's body: no solid block or liquid at the feet or head. */
	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos ground = feet.below();
		return level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP) && roomy(level, feet) && roomy(level, feet.above());
	}

	private static boolean roomy(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).blocksMotion() && level.getFluidState(pos).isEmpty();
	}

	/** Calls off the reinforcements still on their way to {@code squad} (game tests clean up with it). */
	public static void cancel(UUID squad) {
		PENDING.removeIf(pending -> pending.squad().equals(squad));
	}

	/** Forgets every pending arrival (server stopped). */
	static void clear() {
		PENDING.clear();
	}

	private Reinforcements() {
	}
}

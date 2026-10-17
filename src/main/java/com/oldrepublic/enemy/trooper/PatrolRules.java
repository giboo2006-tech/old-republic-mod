package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The pure rules of the natural patrol spawner (spec 7.6): when a player's roll is due, the caps,
 * which army comes, where the squad appears, the route it walks, the clearance around mission sites
 * and the Reliquary, and when a squad despawns. Nothing here touches a level, so game tests check
 * every rule with plain numbers.
 */
public final class PatrolRules {
	/**
	 * A spawn point counts as out of the player's view when it is more than this many degrees from
	 * where they look (horizontally): wider than half of any normal horizontal field of view.
	 */
	public static final double VIEW_HALF_ANGLE = 70;
	/** Route points are checked against the site clearance this often along the route, in blocks. */
	public static final double ROUTE_SAMPLE_STEP = 8;

	/**
	 * Whether a player's roll comes up on {@code tick}: once every {@code interval} ticks, offset by
	 * {@code salt} (from the player's UUID) so the rolls of different players are spread out.
	 */
	public static boolean rollDue(long tick, int salt, int interval) {
		return interval > 0 && Math.floorMod(tick + salt, (long) interval) == 0;
	}

	/**
	 * Whether another patrol may spawn for a player with {@code nearPlayer} patrols within
	 * {@code perPlayerRange} of them while {@code live} patrols exist on the server.
	 */
	public static boolean capsAllow(int nearPlayer, int live, PatrolBalance b) {
		return nearPlayer < b.perPlayerCap && live < b.globalCap;
	}

	/**
	 * The army of a patrol: the one whose outpost terminal is nearer, given the squared distances
	 * to each army's nearest terminal in range ({@code -1} when it has none); a random army when
	 * neither has one.
	 */
	public static Faction army(double republicSqr, double imperialSqr, RandomSource random) {
		if (republicSqr < 0 && imperialSqr < 0) {
			return random.nextBoolean() ? Faction.REPUBLIC : Faction.SITH_EMPIRE;
		}

		if (imperialSqr < 0) {
			return Faction.REPUBLIC;
		}

		if (republicSqr < 0) {
			return Faction.SITH_EMPIRE;
		}

		return republicSqr <= imperialSqr ? Faction.REPUBLIC : Faction.SITH_EMPIRE;
	}

	/** A horizontal offset from the player at a random bearing, {@code minDistance} to {@code maxDistance} long. */
	public static Vec3 spawnOffset(RandomSource random, PatrolBalance b) {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = b.minDistance + random.nextDouble() * Math.max(0, b.maxDistance - b.minDistance);
		return new Vec3(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
	}

	/**
	 * Whether {@code point} is outside the view of someone at {@code eye} looking along {@code look}:
	 * more than {@link #VIEW_HALF_ANGLE} away horizontally. A point straight above or below counts as
	 * outside.
	 */
	public static boolean outsideView(Vec3 eye, Vec3 look, Vec3 point) {
		Vec3 to = new Vec3(point.x - eye.x, 0, point.z - eye.z);
		Vec3 facing = new Vec3(look.x, 0, look.z);

		if (to.lengthSqr() < 1.0e-6 || facing.lengthSqr() < 1.0e-6) {
			return true;
		}

		return to.normalize().dot(facing.normalize()) < Math.cos(Math.toRadians(VIEW_HALF_ANGLE));
	}

	/**
	 * The route of a patrol starting at {@code start}: first a point {@code passDistance} from the
	 * player, on their left or right as seen from the start, then a point {@code routeLength}
	 * further along the same heading. Both keep the start's height; the spawner puts them on the
	 * ground.
	 */
	public static List<Vec3> route(Vec3 start, Vec3 player, boolean left, PatrolBalance b) {
		Vec3 toPlayer = new Vec3(player.x - start.x, 0, player.z - start.z);
		Vec3 along = toPlayer.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : toPlayer.normalize();
		Vec3 side = left ? new Vec3(-along.z, 0, along.x) : new Vec3(along.z, 0, -along.x);
		Vec3 pass = new Vec3(player.x + side.x * b.passDistance, start.y, player.z + side.z * b.passDistance);
		Vec3 heading = new Vec3(pass.x - start.x, 0, pass.z - start.z).normalize();
		Vec3 end = pass.add(heading.scale(b.routeLength));
		return List.of(pass, end);
	}

	/** Points every {@code step} blocks along the line from {@code start} through every route point, both ends included. */
	public static List<Vec3> samples(Vec3 start, List<Vec3> route, double step) {
		List<Vec3> points = new ArrayList<>();
		points.add(start);
		Vec3 from = start;

		for (Vec3 to : route) {
			double length = from.distanceTo(to);
			int steps = Math.max(1, (int) Math.ceil(length / step));

			for (int i = 1; i <= steps; i++) {
				points.add(from.lerp(to, (double) i / steps));
			}

			from = to;
		}

		return points;
	}

	/** Whether every point is at least {@code clearance} blocks (horizontally) from every site. */
	public static boolean clearOfSites(List<Vec3> points, List<BlockPos> sites, int clearance) {
		double clearanceSqr = (double) clearance * clearance;

		for (Vec3 point : points) {
			for (BlockPos site : sites) {
				double dx = point.x - (site.getX() + 0.5);
				double dz = point.z - (site.getZ() + 0.5);

				if (dx * dx + dz * dz < clearanceSqr) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Whether a patrol despawns: every member is more than {@code distance} from every player. A
	 * squad with no members left, or with no players in its level, despawns.
	 */
	public static boolean shouldDespawn(List<Vec3> members, List<Vec3> players, double distance) {
		double distanceSqr = distance * distance;

		for (Vec3 member : members) {
			for (Vec3 player : players) {
				if (member.distanceToSqr(player) <= distanceSqr) {
					return false;
				}
			}
		}

		return true;
	}

	private PatrolRules() {
	}
}

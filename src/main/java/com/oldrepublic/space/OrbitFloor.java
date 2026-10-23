package com.oldrepublic.space;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.TravelTickets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The orbit floor (spec MILESTONE_5.md 5.1): a player who ends up below {@code space.orbitCatchY}
 * in an orbit (a fall from the Silent Spindle, a command) is caught. They are put back aboard their
 * ship (their own, else the one whose crew they are) when it is in that orbit with a free seat;
 * otherwise they are set down beside it at its last landing spot, the cross-level move of 4.10 under
 * a chunk ticket. While that move waits for its chunk they drift with slow falling, so the void
 * never reaches them. A player with no ship at all goes to the home world's spawn.
 */
public final class OrbitFloor {
	/** Players in orbits are checked this often. */
	private static final int CHECK_INTERVAL = 5;
	/** A move waits at most this long for its spot's chunk, then goes anyway (vanilla loads the arrival). */
	private static final int WAIT_TICKS = 100;
	private static final int TICKET_RADIUS = 1;
	private static final int TICKET_LINGER = 100;
	private static final int SLOW_FALL_TICKS = 200;
	/** Where to stand beside a ship at its spot, ship-local (right, forward): behind the ramp, the flanks, the nose, then the spot itself. */
	private static final double[][] BESIDE = {{0, -6.5}, {-1.6, -6.5}, {1.6, -6.5}, {-5.5, -2}, {5.5, -2}, {-5.5, 2}, {5.5, 2}, {0, 7.5}, {0, 0}};

	/** A caught player on the way to {@code spot}. */
	private record Pending(GlobalPos spot, float yaw, long since) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	static void tick(MinecraftServer server) {
		long now = Clocks.time(server);

		if (!PENDING.isEmpty()) {
			moves(server, now);
		}

		if (now % CHECK_INTERVAL != 0) {
			return;
		}

		int floor = SpaceBalance.get().orbitCatchY;

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (SpaceRules.caughtByFloor(Planets.isOrbit(p.level()), p.getY(), floor, p.isAlive(), p.isSpectator(), p.getAbilities().flying,
					p.getVehicle() != null) && !PENDING.containsKey(p.getUUID())) {
				catchPlayer(server, p, now);
			}
		}
	}

	/**
	 * Catches {@code p}: aboard their ship when it is here with a seat, else on the way to its last
	 * landing spot. The floor's check calls it; so do the game tests (the test server has no orbit).
	 */
	public static void catchPlayer(MinecraftServer server, ServerPlayer p, long now) {
		ShipRecord record = shipOf(server, p);
		ShipBody ship = record == null ? null : Ships.ship(server, record.owner());

		if (record != null && ship != null && ship.level() == p.level() && board(p, ship, record)) {
			OldRepublicMod.LOGGER.info("[space] {} fell below the orbit floor and was put back aboard", p.getPlainTextName());
			return;
		}

		GlobalPos spot = record != null ? record.pos() : GlobalPos.of(Level.OVERWORLD, server.overworld().getRespawnData().pos());
		float yaw = record != null ? record.yaw() : p.getYRot();
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, true));
		p.resetFallDistance();
		TravelTickets.hold(server, spot, TICKET_RADIUS);
		PENDING.put(p.getUUID(), new Pending(spot, yaw, now));
		OldRepublicMod.LOGGER.info("[space] {} fell below the orbit floor; moving them to {}", p.getPlainTextName(), spot);
	}

	/** The ship {@code p} belongs to: their own, else the one whose crew they are; null when neither exists. */
	private static @Nullable ShipRecord shipOf(MinecraftServer server, ServerPlayer p) {
		ShipRecord own = ShipRegistry.of(server, p.getUUID()).orElse(null);

		if (own != null) {
			return own;
		}

		UUID captain = TravelAttachments.get(p).crewOf().orElse(null);
		return captain == null ? null : ShipRegistry.of(server, captain).orElse(null);
	}

	/** Puts {@code p} aboard {@code ship} when they may board it and a seat is free. */
	private static boolean board(ServerPlayer p, ShipBody ship, ShipRecord record) {
		if (!ShipRules.mayBoard(record, p.getUUID()) || ship.getPassengers().size() >= 1 + ShipRules.seats(record, ShipBalance.get())) {
			return false;
		}

		Vec3 at = ship.getBoundingBox().getCenter();
		p.teleportTo(at.x, at.y, at.z);
		p.resetFallDistance();
		return p.startRiding(ship);
	}

	/** The waiting moves: each goes once its spot's chunk is ready (or after {@link #WAIT_TICKS}). */
	private static void moves(MinecraftServer server, long now) {
		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
			ServerLevel level = server.getLevel(pending.spot().dimension());

			if (p == null || level == null || !p.isAlive()) {
				TravelTickets.release(server, pending.spot(), TICKET_RADIUS, 0);
				it.remove();
				continue;
			}

			if (!TravelTickets.ready(level, pending.spot().pos(), TICKET_RADIUS) && now - pending.since() < WAIT_TICKS) {
				continue;
			}

			it.remove();
			Vec3 stand = standBeside(level, pending.spot().pos(), pending.yaw(), p);
			TravelService.moveAcross(p, level, stand, pending.yaw());
			p.resetFallDistance();
			TravelTickets.release(server, pending.spot(), TICKET_RADIUS, TICKET_LINGER);
		}
	}

	/** Where {@code p} stands beside a ship at {@code spot} facing {@code yaw}: the first spot with ground and room, else the column's top. */
	static Vec3 standBeside(ServerLevel level, BlockPos spot, float yaw, ServerPlayer p) {
		Vec3 base = Vec3.atBottomCenterOf(spot);

		for (double[] offset : BESIDE) {
			Vec3 found = standNear(level, base.add(ShipBody.local(offset[0], 0, offset[1], yaw)), p);

			if (found != null) {
				return found;
			}
		}

		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
		return new Vec3(base.x, Math.max(top, spot.getY()), base.z);
	}

	/** Ground within a few blocks of {@code at} with room for {@code p} above it, or null. */
	private static @Nullable Vec3 standNear(ServerLevel level, Vec3 at, ServerPlayer p) {
		BlockPos column = BlockPos.containing(at);

		if (!level.isLoaded(column)) {
			return null;
		}

		double half = p.getBbWidth() / 2;

		for (int dy = 2; dy >= -4; dy--) {
			BlockPos below = column.above(dy - 1);
			BlockState ground = level.getBlockState(below);
			VoxelShape shape = ground.getCollisionShape(level, below);

			if (shape.isEmpty() || !level.getFluidState(below.above()).isEmpty()) {
				continue;
			}

			Vec3 stand = new Vec3(at.x, below.getY() + shape.max(Direction.Axis.Y), at.z);
			AABB box = new AABB(stand.x - half, stand.y, stand.z - half, stand.x + half, stand.y + p.getBbHeight(), stand.z + half);
			return level.noCollision(p, box) ? stand : null;
		}

		return null;
	}

	/** {@code player} left: a waiting move is dropped (they log in where they were saved). */
	static void forget(MinecraftServer server, UUID player) {
		Pending pending = PENDING.remove(player);

		if (pending != null) {
			TravelTickets.release(server, pending.spot(), TICKET_RADIUS, 0);
		}
	}

	/** Drops every waiting move and lets its ticket go (the reset command and tests). */
	static void reset(MinecraftServer server) {
		for (Pending pending : PENDING.values()) {
			TravelTickets.release(server, pending.spot(), TICKET_RADIUS, 0);
		}

		PENDING.clear();
	}

	/** Forgets every waiting move (the server stopped; its tickets went with it). */
	static void clear() {
		PENDING.clear();
	}

	private OrbitFloor() {
	}
}

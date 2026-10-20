package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Placing, finding and restoring a player's ship body (spec MILESTONE_4.md 4.1, 4.2, 4.9). The
 * record in {@link ShipRegistry} is the truth and a body only shows it.
 *
 * <p>Every new body gets a new UUID, committed to the record before the body is added, so an older
 * copy (in a chunk saved earlier, or anywhere else) discards itself on its first tick: a ship and
 * its hold never exist twice. A body is never placed into a chunk that is not loaded: a restore
 * waits, under a chunk ticket, for its pad's chunk.
 */
public final class Ships {
	/** The hulk's cradle template (4.2), recorded as {@code SHIP_CRADLE}. */
	public static final Identifier CRADLE = OldRepublicMod.id("ship/cradle");
	/** Rings searched when the cradle range finds nothing. */
	private static final int[] CRADLE_FALLBACK = {72, 80, 96};
	private static final int CRADLE_RING_STEP = 8;
	/** The cradle's spot: a 17-block square of natural ground, a 25-block square with nothing built. */
	private static final MeetingSites.Rules CRADLE_RULES = new MeetingSites.Rules(17, 25, 3, pos -> false, List.of(), 0);
	/** The cradle's floor layer replaces the ground. */
	private static final int CRADLE_BURIED = 1;
	/** How far ahead of the owner {@link #give} parks the ship, and the command's cradle rings. */
	private static final double GIVE_DISTANCE = 8;
	private static final int[] COMMAND_RINGS = {16, 24, 32};
	private static final int[] COMMAND_FALLBACK = {40, 48};
	private static final int COMMAND_AHEAD = 20;
	/** A grounded body this far from its record's pad is moved back to it. */
	private static final double DRIFT = 1.5;
	/** Ticks a queued body waits for its pad's chunks before the request is dropped. */
	private static final int PENDING_TIMEOUT = 1200;
	private static final int PENDING_TICKET_RADIUS = 1;
	/** Riders put off a ship in the air get this much slow falling (ticks). */
	private static final int SAFE_FALL_TICKS = 200;
	/**
	 * The tag of a rider saved away from their ship's last landing spot (MILESTONE_5.md 4.10): put off
	 * in another level, or over a spot that was not loaded; their next join moves them beside it.
	 */
	static final String STRANDED_TAG = "oldrepublic.stranded";

	/** A body waiting for its pad's chunk: placed when the chunk is ready, or checked first when {@code onlyIfMissing}. */
	private record Pending(GlobalPos pad, boolean onlyIfMissing, long since) {
	}

	/** A stranded rider waiting for the chunk beside their ship's last landing spot (MILESTONE_5.md 4.10). */
	private record Stranded(GlobalPos pad, float yaw, boolean interceptor, long since) {
	}

	private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();
	private static final Map<UUID, Stranded> STRANDED = new LinkedHashMap<>();

	// ---------------------------------------------------------------- frozen API

	/**
	 * Places the Chapter Four hulk and its ship cradle near {@code near} (4.2) and records it in
	 * {@link ShipRegistry}. An owner who already has a ship gets their existing record.
	 *
	 * @return the record, or null when no spot was found yet (the caller retries later)
	 */
	public static @Nullable ShipRecord placeHulk(ServerPlayer owner, BlockPos near) {
		MinecraftServer server = owner.level().getServer();
		Optional<ShipRecord> existing = ShipRegistry.of(server, owner.getUUID());

		if (existing.isPresent()) {
			return existing.get();
		}

		ServerLevel level = owner.level();
		Vec3 origin = Vec3.atBottomCenterOf(near);
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, CRADLE, origin, owner.position().subtract(origin),
				cradleRings(ShipBalance.get()), CRADLE_FALLBACK, CRADLE_RULES, CRADLE_BURIED, owner.getUUID());
		return placement == null ? null : createHulk(owner, level, placement, starterHold());
	}

	/**
	 * The command and test path (4.11): a repaired ship with a full tank beside the owner. Replaces
	 * any existing body; the hold is kept.
	 */
	public static ShipRecord give(ServerPlayer owner) {
		Vec3 ahead = flatLook(owner);
		Vec3 spot = owner.position().add(ahead.scale(GIVE_DISTANCE));
		BlockPos column = BlockPos.containing(spot);
		BlockPos pad = owner.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		return giveAt(owner, pad, owner.getYRot());
	}

	/** Whether {@code p} is riding a ship body (either side). */
	public static boolean isAboard(Player p) {
		return p.getVehicle() instanceof ShipBody;
	}

	/** The owner's ship body if it is loaded, else null. */
	public static @Nullable Entity body(MinecraftServer server, UUID owner) {
		return ship(server, owner);
	}

	// ---------------------------------------------------------------- finding

	/**
	 * The owner's current body if it is loaded and a courier (the Milestone 4 finder, kept for the
	 * Milestone 4 tests); {@link #ship} finds a body of any class.
	 */
	public static @Nullable CourierShuttle shuttle(MinecraftServer server, UUID owner) {
		return ship(server, owner) instanceof CourierShuttle courier ? courier : null;
	}

	/**
	 * The owner's current body of any class if it is loaded (in the record's level first, then any
	 * other), MILESTONE_5.md 4.9.
	 */
	public static @Nullable ShipBody ship(MinecraftServer server, UUID owner) {
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);

		if (record == null) {
			return null;
		}

		ServerLevel home = server.getLevel(record.pos().dimension());
		ShipBody found = home == null ? null : bodyIn(home, record.body());

		if (found != null) {
			return found;
		}

		for (ServerLevel level : server.getAllLevels()) {
			if (level != home && (found = bodyIn(level, record.body())) != null) {
				return found;
			}
		}

		return null;
	}

	private static @Nullable ShipBody bodyIn(ServerLevel level, UUID body) {
		return level.getEntity(body) instanceof ShipBody ship && !ship.isRemoved() ? ship : null;
	}

	/** The record of the ship {@code p} rides, if any. */
	public static Optional<ShipRecord> ridden(ServerPlayer p) {
		return p.getVehicle() instanceof ShipBody ship ? ShipRegistry.byBody(p.level().getServer(), ship.getUUID()) : Optional.empty();
	}

	/**
	 * Whether the ship is busy flying (MILESTONE_5.md 4.2): an autopilot run, the owner taking or
	 * holding the helm, or the state {@code FLYING}. The Milestone 4 checks that refuse an action in
	 * flight ask this.
	 */
	public static boolean busy(ShipRecord r) {
		return Flights.isFlying(r.owner()) || Helm.holds(r.owner()) || r.state() == ShipState.FLYING;
	}

	// ---------------------------------------------------------------- Milestone 5 (4.8, 4.9, 5.4)

	/** Whether the ship has laser cannons ({@link ShipRecord#UPGRADE_CANNONS}, MILESTONE_5.md 4.8, 6.5). */
	public static boolean hasWeapons(ShipRecord r) {
		return r.has(ShipRecord.UPGRADE_CANNONS);
	}

	/** Whether the ship's class is the interceptor ({@link ShipRecord#CLASS_INTERCEPTOR}, MILESTONE_5.md 4.9). */
	public static boolean interceptor(ShipRecord r) {
		return r.has(ShipRecord.CLASS_INTERCEPTOR);
	}

	/**
	 * The owner's ship is disabled in flight (MILESTONE_5.md 5.4): the controls lock and it tumbles
	 * for {@code flight.tumbleTicks}, then it makes the free rescue run to its last landing spot with
	 * everyone aboard and arrives with {@code flight.rescueHull}. Nothing happens unless the owner is
	 * at the helm (flying by hand, or held by the server). W2's enemy fire calls it at 0 hull, and a
	 * crash does (4.4).
	 */
	public static void disable(ServerPlayer owner) {
		Helm.disable(owner.level().getServer(), owner.getUUID());
	}

	// ---------------------------------------------------------------- changing records

	/**
	 * Re-reads the owner's record, applies {@code change} and stores the result; tells the story when
	 * the state changed (the owner online). Returns the stored record, or empty when they have none.
	 */
	public static Optional<ShipRecord> update(MinecraftServer server, UUID owner, UnaryOperator<ShipRecord> change) {
		ShipRecord before = ShipRegistry.of(server, owner).orElse(null);

		if (before == null) {
			return Optional.empty();
		}

		ShipRecord changed = change.apply(before);
		// The class's most hull (MILESTONE_5.md 4.9: 80 on an interceptor); the record itself clamps to 0-100.
		int maxHull = ShipRules.maxHull(changed);
		ShipRecord after = changed.hull() > maxHull ? changed.withHull(maxHull) : changed;
		ShipRegistry.put(server, after);
		ShipBody ship = ship(server, owner);

		if (ship != null) {
			// The body shows the new hull at once, so the per-tick check in tickBody never sees this drop.
			if (after.hull() < before.hull() && after.state() != ShipState.HULK) {
				TravelEffects.damage(ship, after.hull());
			}

			show(ship, after);
		}

		if (before.state() != after.state()) {
			ServerPlayer online = server.getPlayerList().getPlayer(owner);

			if (online != null) {
				QuestHooks.onShipStateChanged(online, before.state(), after.state());
			}
		}

		return Optional.of(after);
	}

	// ---------------------------------------------------------------- placing

	/** The hulk's record at the cradle's {@code HEART}, its body spawned now. */
	private static ShipRecord createHulk(ServerPlayer owner, ServerLevel level, RuntimeSites.Placement placement, List<ItemStack> hold) {
		MinecraftServer server = level.getServer();
		BlockPos heart = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.HEART);
		BlockPos stand = heart != null ? heart : placement.origin();
		ShipRecord record = ShipRecord.hulk(owner.getUUID(), UUID.randomUUID(), GlobalPos.of(level.dimension(), stand), yawOf(placement.rotation()),
				ShipRules.hullCode(owner.getPlainTextName(), owner.getUUID())).withHold(hold);
		record = ShipRules.withPaint(record, paintFor(owner));
		ShipRegistry.put(server, record);
		spawn(level, record, Vec3.atBottomCenterOf(stand));
		return record;
	}

	/**
	 * The hulk and cradle placed now near the owner (the {@code hulk} command), replacing any ship
	 * they have and keeping its hold. Null when not even the straight-ahead spot could be placed.
	 */
	public static @Nullable ShipRecord placeHulkNow(ServerPlayer owner) {
		ServerLevel level = owner.level();
		MinecraftServer server = level.getServer();
		Vec3 ahead = flatLook(owner);
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, CRADLE, owner.position(), ahead, COMMAND_RINGS, COMMAND_FALLBACK,
				CRADLE_RULES, CRADLE_BURIED, owner.getUUID());

		if (placement == null) {
			BlockPos column = BlockPos.containing(owner.position().add(ahead.scale(COMMAND_AHEAD)));
			BlockPos stand = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);

			try {
				placement = RuntimeSites.placeAt(level, CRADLE, stand, RuntimeSites.facing(owner.getDirection().getOpposite()), CRADLE_BURIED, owner.getUUID());
			} catch (IllegalArgumentException | IllegalStateException e) {
				OldRepublicMod.LOGGER.warn("Could not place the ship cradle for {}: {}", owner.getPlainTextName(), e.getMessage());
				return null;
			}
		}

		Optional<ShipRecord> old = ShipRegistry.of(server, owner.getUUID());
		old.ifPresent(r -> retire(server, r));
		return createHulk(owner, level, placement, old.map(ShipRecord::hold).orElseGet(Ships::starterHold));
	}

	/**
	 * A repaired ship with a full tank standing at {@code pad} (the {@code give} command and the
	 * tests). Keeps the hold, crew, upgrades and hull code of an existing ship; its old body and any
	 * flight are gone.
	 */
	public static ShipRecord giveAt(ServerPlayer owner, BlockPos pad, float yaw) {
		ServerLevel level = owner.level();
		MinecraftServer server = level.getServer();
		ShipBalance b = ShipBalance.get();
		Optional<ShipRecord> old = ShipRegistry.of(server, owner.getUUID());
		old.ifPresent(r -> retire(server, r));
		ShipRecord base = old.orElseGet(() -> ShipRules.withPaint(ShipRecord.hulk(owner.getUUID(), UUID.randomUUID(), GlobalPos.of(level.dimension(), pad), yaw,
				ShipRules.hullCode(owner.getPlainTextName(), owner.getUUID())), paintFor(owner)));
		ShipRecord repaired = ShipRules.withAllSockets(base, b).withHull(ShipRules.maxHull(base)).withFlight(Optional.empty())
				.withBody(UUID.randomUUID()).withPos(GlobalPos.of(level.dimension(), pad), yaw);
		ShipRecord full = repaired.withFuel(ShipRules.tankSize(repaired, b)).withState(ShipState.READY);
		ShipState from = old.map(ShipRecord::state).orElse(ShipState.HULK);
		ShipRegistry.put(server, full);
		spawn(level, full, Vec3.atBottomCenterOf(pad));

		if (from != full.state()) {
			QuestHooks.onShipStateChanged(owner, from, full.state());
		}

		return full;
	}

	/** Ends a ship's flight or helm and removes its loaded body (its riders put off first), before a replacement. */
	private static void retire(MinecraftServer server, ShipRecord record) {
		Flights.cancel(server, record.owner());
		Helm.cancel(server, record.owner());
		PENDING.remove(record.owner());
		ShipBody ship = ship(server, record.owner());

		if (ship != null) {
			discard(ship);
		}
	}

	/** Spawns a body for {@code record} at {@code at}, of the record's class (MILESTONE_5.md 4.9); its UUID is the record's {@code body}. */
	static @Nullable ShipBody spawn(ServerLevel level, ShipRecord record, Vec3 at) {
		ShipBody ship = interceptor(record) ? ShipEntities.INTERCEPTOR.create(level, EntitySpawnReason.EVENT)
				: ShipEntities.COURIER_SHUTTLE.create(level, EntitySpawnReason.EVENT);

		if (ship == null) {
			return null;
		}

		ship.setUUID(record.body());
		ship.snapTo(at.x, at.y, at.z, record.yaw(), 0f);
		show(ship, record);

		if (!level.addFreshEntity(ship)) {
			OldRepublicMod.LOGGER.warn("Could not add the ship body of {} at {}", record.owner(), at);
			return null;
		}

		return ship;
	}

	/** Copies the record into the body's synced data, with the helm's or the flight's phase and whether the owner flies it by hand. */
	static void show(ShipBody ship, ShipRecord record) {
		UUID owner = record.owner();
		ship.show(record, Helm.holds(owner) ? Helm.phaseOf(owner) : Flights.phaseOf(owner), Helm.isPiloted(owner));
	}

	/** Puts every rider off and removes the body; riders in the air get slow falling. */
	static void discard(ShipBody ship) {
		boolean airborne = ship.state().flying() || ship.phase() != ShipRules.PHASE_GROUND;

		for (Entity rider : List.copyOf(ship.getPassengers())) {
			rider.stopRiding();

			if (airborne && rider instanceof ServerPlayer player) {
				safeFall(player);
			}
		}

		ship.discard();
	}

	/** Slow falling for a rider put off in the air. */
	static void safeFall(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SAFE_FALL_TICKS, 0, false, false, true));
		player.resetFallDistance();
	}

	// ---------------------------------------------------------------- restoring (4.9)

	/**
	 * Places a new body from the record at its last pad, with its hold intact. With
	 * {@code onlyIfMissing}, nothing happens when the current body turns out to be there once the
	 * pad's chunk is loaded. Waits under a chunk ticket when the pad is not loaded.
	 *
	 * @return false when there is no ship or it is flying
	 */
	public static boolean restore(MinecraftServer server, UUID owner, boolean onlyIfMissing) {
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);

		if (record == null || busy(record)) {
			return false;
		}

		if (onlyIfMissing && ship(server, owner) != null) {
			return true;
		}

		GlobalPos pad = restorePad(server, record);
		PENDING.put(owner, new Pending(pad, onlyIfMissing, Clocks.time(server)));
		TravelTickets.hold(server, pad, PENDING_TICKET_RADIUS);
		tickPending(server);
		return true;
	}

	/** The record's pad, or home when its world is gone (12.2: a planet removed by a data pack). */
	private static GlobalPos restorePad(MinecraftServer server, ShipRecord record) {
		if (server.getLevel(record.pos().dimension()) != null) {
			return record.pos();
		}

		return Pads.homeFallback(server, record.owner());
	}

	/** Places the bodies whose pads became ready, and moves stranded riders; drops requests that waited too long. */
	static void tickPending(MinecraftServer server) {
		tickStranded(server);

		if (PENDING.isEmpty()) {
			return;
		}

		long now = Clocks.time(server);

		for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();
			ServerLevel level = server.getLevel(pending.pad().dimension());

			if (level == null || now - pending.since() > PENDING_TIMEOUT) {
				OldRepublicMod.LOGGER.warn("Gave up placing the ship body of {} at {}", entry.getKey(), pending.pad());
				TravelTickets.release(server, pending.pad(), PENDING_TICKET_RADIUS, 0);
				it.remove();
				continue;
			}

			if (!TravelTickets.ready(level, pending.pad().pos(), PENDING_TICKET_RADIUS)) {
				continue;
			}

			it.remove();
			TravelTickets.release(server, pending.pad(), PENDING_TICKET_RADIUS, 0);
			place(server, entry.getKey(), pending);
		}
	}

	private static void place(MinecraftServer server, UUID owner, Pending pending) {
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);

		if (record == null || busy(record)) {
			return;
		}

		ShipBody current = ship(server, owner);

		if (pending.onlyIfMissing() && current != null) {
			return;
		}

		if (current != null) {
			discard(current);
		}

		ServerLevel level = server.getLevel(pending.pad().dimension());
		ShipRecord placed = record.withBody(UUID.randomUUID()).withPos(pending.pad(), record.yaw());
		ShipRegistry.put(server, placed);

		if (level != null) {
			spawn(level, placed, Vec3.atBottomCenterOf(pending.pad().pos()));
		}
	}

	// ---------------------------------------------------------------- the body's own tick

	/**
	 * A body's check, every 10 ticks and on its first ticks: a body that is not its owner's current
	 * one discards itself; a grounded body is kept on its record's pad (never one at the helm, which
	 * the server does not move, MILESTONE_5.md 4.1); the synced view is refreshed; anyone aboard who
	 * is not crew is put off; the damage effect fires when the hull dropped (wear, or the story's
	 * sabotage).
	 */
	static void tickBody(ServerLevel level, ShipBody ship) {
		MinecraftServer server = level.getServer();
		ShipRecord record = ShipRegistry.byBody(server, ship.getUUID()).orElse(null);

		if (record == null) {
			discard(ship);
			return;
		}

		if (Helm.holds(record.owner())) {
			if (!Helm.isBody(record.owner(), ship)) {
				discard(ship);
				return;
			}
		} else if (Flights.isFlying(record.owner())) {
			if (!Flights.isBody(record.owner(), ship)) {
				discard(ship);
				return;
			}
		} else if (ship.interceptor() != interceptor(record)) {
			// The class changed while this body was away (a refit, MILESTONE_5.md 4.9): one of the right class replaces it.
			discard(ship);
			restore(server, record.owner(), false);
			return;
		} else if (!keepOnPad(level, ship, record)) {
			return;
		}

		// A hull drop written to the registry without update (another feature's direct put).
		if (record.hull() < ship.hull() && ship.state() != ShipState.HULK && ship.tickCount > 3) {
			TravelEffects.damage(ship, record.hull());
		}

		show(ship, record);

		if (!ship.locksRiders()) {
			for (Entity rider : List.copyOf(ship.getPassengers())) {
				if (!ShipRules.mayBoard(record, rider.getUUID()) || !ShipRules.boardable(record)) {
					rider.stopRiding();
				}
			}
		}

		if (ship.tickCount % 40 == 1) {
			Pads.attend(server, record, ship);
		}
	}

	/** Keeps a grounded body on its pad: moved back when it drifted, replaced when it is in the wrong world. False when it was removed. */
	private static boolean keepOnPad(ServerLevel level, ShipBody ship, ShipRecord record) {
		Vec3 pad = Vec3.atBottomCenterOf(record.pos().pos());

		if (!record.pos().dimension().equals(level.dimension())) {
			discard(ship);
			restore(level.getServer(), record.owner(), false);
			return false;
		}

		if (ship.position().distanceToSqr(pad) > DRIFT * DRIFT || Math.abs(Mth.wrapDegrees(ship.getYRot() - record.yaw())) > 1f) {
			if (level.isLoaded(record.pos().pos())) {
				ship.flyTo(pad, record.yaw(), 0f);
			} else {
				discard(ship);
				restore(level.getServer(), record.owner(), false);
				return false;
			}
		}

		ship.setGear(true);
		return true;
	}

	// ---------------------------------------------------------------- seats and dismounting

	/** Whether one more player fits: the pilot plus the passenger seats. */
	static boolean hasSeatFor(ServerLevel level, ShipBody ship) {
		ShipRecord record = ShipRegistry.byBody(level.getServer(), ship.getUUID()).orElse(null);
		return record != null && ship.getPassengers().size() < 1 + ShipRules.seats(record, ShipBalance.get());
	}

	/** Where a rider steps off: beside the ramp if there is room, else another side, else on the hull's back. */
	static Vec3 besideRamp(ServerLevel level, ShipBody ship, Entity passenger) {
		return besidePad(level, ship.position(), ship.getYRot(), ship.interceptor(), passenger);
	}

	/**
	 * Whether a ship's hull box fits in {@code level} (MILESTONE_5.md 4.7, an arrival's clear place,
	 * {@link FlightRules#clearArrival}): the chunks under its corners are loaded, and it meets no
	 * block and nothing that blocks a ship but {@code self}. Public for the flight tests.
	 */
	public static boolean hullFits(ServerLevel level, AABB box, @Nullable Entity self) {
		BlockPos low = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos high = BlockPos.containing(box.maxX, box.maxY, box.maxZ);

		if (!level.isLoaded(low) || !level.isLoaded(high) || !level.isLoaded(new BlockPos(low.getX(), low.getY(), high.getZ()))
				|| !level.isLoaded(new BlockPos(high.getX(), low.getY(), low.getZ()))) {
			return false;
		}

		return self == null ? level.noCollision(box) : level.noCollision(self, box);
	}

	/**
	 * {@link #besideRamp} for a ship of a class standing (or about to stand) at {@code base} facing
	 * {@code yaw}: where a rider steps off. Public for the space feature's landing floors.
	 */
	public static Vec3 besidePad(ServerLevel level, Vec3 base, float yaw, boolean interceptor, Entity passenger) {
		for (double[] spot : ShipBody.dismountSpots(interceptor)) {
			Vec3 found = standNear(level, base.add(ShipBody.local(spot[0], 0, spot[1], yaw)), passenger);

			if (found != null) {
				return found;
			}
		}

		return base.add(0, ShipBody.footprintHeight(interceptor) + 0.15, 0);
	}

	/**
	 * A spot within a few blocks up or down of {@code at} on the first ground found from above, with
	 * room for {@code who}; null when that ground is taken (by a block beside it, water, or a landing
	 * beacon or pad terminal, which stands right behind the ramp of a ship on its pad). Nothing below
	 * that ground is tried, so a rider is never put into a cave or a gap under the surface.
	 */
	private static @Nullable Vec3 standNear(ServerLevel level, Vec3 at, Entity who) {
		BlockPos column = BlockPos.containing(at);

		if (!level.isLoaded(column)) {
			return null;
		}

		double half = who.getBbWidth() / 2;

		for (int dy = 2; dy >= -4; dy--) {
			BlockPos feet = column.above(dy);
			BlockPos below = feet.below();
			BlockState ground = level.getBlockState(below);
			VoxelShape shape = ground.getCollisionShape(level, below);

			if (shape.isEmpty()) {
				continue;
			}

			if (ground.is(TravelBlocks.LANDING_BEACON) || ground.is(TravelBlocks.PAD_TERMINAL) || !level.getFluidState(feet).isEmpty()) {
				return null;
			}

			Vec3 stand = new Vec3(at.x, below.getY() + shape.max(Direction.Axis.Y), at.z);
			AABB box = new AABB(stand.x - half, stand.y, stand.z - half, stand.x + half, stand.y + who.getBbHeight(), stand.z + half);
			return level.noCollision(who, box) ? stand : null;
		}

		return null;
	}

	/**
	 * Puts a player down beside the record's pad (a rider logging in, or logging out away from a
	 * moving ship). A player who is {@code leaving} is only moved, since their client is gone; one
	 * whose pad is in another world or not loaded gets slow falling instead, and a leaving one is
	 * marked stranded, so their next join brings them beside it (MILESTONE_5.md 4.10).
	 */
	static void placeBesidePad(ServerPlayer player, ShipRecord record, boolean leaving) {
		ServerLevel level = player.level();

		if (!record.pos().dimension().equals(level.dimension()) || !level.isLoaded(record.pos().pos())) {
			safeFall(player);

			if (leaving) {
				player.addTag(STRANDED_TAG);
			}

			return;
		}

		Vec3 spot = besidePad(level, Vec3.atBottomCenterOf(record.pos().pos()), record.yaw(), interceptor(record), player);

		if (leaving) {
			player.snapTo(spot.x, spot.y, spot.z);
		} else {
			player.teleportTo(spot.x, spot.y, spot.z);
		}

		player.resetFallDistance();
	}

	// ---------------------------------------------------------------- stranded riders and the void (MILESTONE_5.md 4.10)

	/**
	 * A player joining who was saved away from their ship (4.10): queued to move beside the last
	 * landing spot of their own ship, else of the ship whose crew they are, once its chunk is loaded
	 * under a ticket. Without either ship the mark is just dropped.
	 */
	static void joined(ServerPlayer player) {
		if (!player.entityTags().contains(STRANDED_TAG)) {
			return;
		}

		MinecraftServer server = player.level().getServer();
		ShipRecord record = ShipRegistry.of(server, player.getUUID())
				.or(() -> TravelAttachments.get(player).crewOf().flatMap(owner -> ShipRegistry.of(server, owner)))
				.orElse(null);

		if (record == null || server.getLevel(record.pos().dimension()) == null) {
			player.removeTag(STRANDED_TAG);
			return;
		}

		STRANDED.put(player.getUUID(), new Stranded(record.pos(), record.yaw(), interceptor(record), Clocks.time(server)));
		TravelTickets.hold(server, record.pos(), PENDING_TICKET_RADIUS);
	}

	/** Moves each stranded rider whose spot is ready beside it, across levels if need be, with their fall forgotten. */
	private static void tickStranded(MinecraftServer server) {
		if (STRANDED.isEmpty()) {
			return;
		}

		long now = Clocks.time(server);

		for (Iterator<Map.Entry<UUID, Stranded>> it = STRANDED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Stranded> entry = it.next();
			Stranded stranded = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			ServerLevel level = server.getLevel(stranded.pad().dimension());
			boolean ready = level != null && TravelTickets.ready(level, stranded.pad().pos(), PENDING_TICKET_RADIUS);

			if (player == null || level == null || ready || now - stranded.since() > PENDING_TIMEOUT) {
				it.remove();
				TravelTickets.release(server, stranded.pad(), PENDING_TICKET_RADIUS, 0);

				if (player != null && level != null) {
					Vec3 spot = ready ? besidePad(level, Vec3.atBottomCenterOf(stranded.pad().pos()), stranded.yaw(), stranded.interceptor(), player)
							: Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, stranded.pad().pos()));
					player.stopRiding();
					TravelService.moveAcross(player, level, spot, player.getYRot()).resetFallDistance();
					player.removeTag(STRANDED_TAG);
				}
			}
		}
	}

	/**
	 * A body fell out of the world (4.10): the helm sends it home with its riders; an autopilot run
	 * carries it on; any other is replaced at its last landing spot. False for a body with no record
	 * (vanilla removes it).
	 */
	static boolean belowWorld(ServerLevel level, ShipBody ship) {
		MinecraftServer server = level.getServer();
		ShipRecord record = ShipRegistry.byBody(server, ship.getUUID()).orElse(null);

		if (record == null) {
			return false;
		}

		if (Helm.holds(record.owner())) {
			Helm.belowWorld(server, record.owner());
		} else if (!Flights.isFlying(record.owner())) {
			discard(ship);
			restore(server, record.owner(), false);
		}

		return true;
	}

	// ---------------------------------------------------------------- paint and helpers

	/** The paint for a new ship (4.1): Watch green for a Jedi, Imperial grey for a Sith, Vasch's rust red for an Independent. */
	static int paintFor(ServerPlayer owner) {
		Path path = Identity.path(owner);
		return switch (path) {
			case SITH -> ShipBody.PAINT_IMPERIAL;
			case INDEPENDENT -> ShipBody.PAINT_VASCH;
			default -> ShipBody.PAINT_WATCH;
		};
	}

	/** The cells the mentor (or Vasch's wreck) leaves in the hold (4.3). */
	private static List<ItemStack> starterHold() {
		int cells = ShipBalance.get().starterCells;
		List<ItemStack> hold = new ArrayList<>();

		if (cells > 0) {
			hold.add(new ItemStack(M4Items.HYPERFUEL_CELL, cells));
		}

		return hold;
	}

	/** The cradle's rings, every 8 blocks across {@code ship.cradleRange}. */
	private static int[] cradleRings(ShipBalance b) {
		int min = b.cradleRange.length > 0 ? b.cradleRange[0] : 32;
		int max = b.cradleRange.length > 1 ? Math.max(min, b.cradleRange[1]) : min;
		List<Integer> rings = new ArrayList<>();

		for (int r = min; r <= max; r += CRADLE_RING_STEP) {
			rings.add(r);
		}

		return rings.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * The ship's yaw on a template turned by {@code rotation}: the nose points along the template's
	 * local +z, so the ramp faces its entrance (local north).
	 */
	static float yawOf(Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> 90f;
			case CLOCKWISE_180 -> 180f;
			case COUNTERCLOCKWISE_90 -> 270f;
			default -> 0f;
		};
	}

	/** The yaw that points the nose along {@code dir} (horizontal). */
	static float yawAlong(double dx, double dz) {
		return dx * dx + dz * dz < 1.0e-6 ? 0f : (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle().horizontal();
		return look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
	}

	static void clear() {
		PENDING.clear();
		STRANDED.clear();
	}

	private Ships() {
	}
}

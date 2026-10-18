package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Launches, recalls and destinations (spec MILESTONE_4.md 4.5–4.8). Phase 0 freezes the
 * signatures; W3 writes the bodies. {@link #moveAcross} is the lead's and is the only code that
 * moves anything between levels.
 */
public final class TravelService {
	/** Why a launch was refused. Stored by ordinal and append only. */
	public enum Refusal {
		NONE,
		NOT_OWNER,
		NOT_READY,
		NO_FUEL,
		HULL,
		LOCKED,
		COMBAT,
		DISABLED_BY_CONFIG,
		BUSY
	}

	/** The checks of 4.6, without starting anything. */
	public static Refusal canLaunch(ServerPlayer owner, Destination to) {
		if (!isUnlocked(owner, to)) {
			return Refusal.LOCKED;
		}

		return ShipRegistry.of(owner.level().getServer(), owner.getUUID()).isEmpty() ? Refusal.NOT_OWNER : Refusal.NOT_READY;
	}

	/** Starts the flight sequence (4.6) when {@link #canLaunch} allows it; returns why not otherwise. */
	public static Refusal launch(ServerPlayer owner, Destination to) {
		return canLaunch(owner, to);
	}

	/** Flies the owner's ship to {@code pad} on its own (4.8). */
	public static void recall(ServerPlayer owner, GlobalPos pad) {
	}

	/** Whether {@code p} may fly to {@code d}; {@link Destination#HOME} always. */
	public static boolean isUnlocked(ServerPlayer p, Destination d) {
		return TravelAttachments.get(p).isUnlocked(d);
	}

	/** Unlocks {@code d} for {@code p} (the story calls this, 8.2 and 8.6). */
	public static void unlock(ServerPlayer p, Destination d) {
		TravelData data = TravelAttachments.get(p);

		if (!data.isUnlocked(d)) {
			TravelAttachments.set(p, data.withUnlocked(d, true));
		}
	}

	/** Where {@code p}'s ship lands for {@code d}, or null when there is no pad yet. */
	public static @Nullable GlobalPos pad(ServerPlayer p, Destination d) {
		return null;
	}

	/**
	 * Moves {@code root} and everything riding it to {@code pos} in {@code to}, facing {@code yaw}.
	 * Vanilla's cross-level teleport ejects the passengers, moves each one, re-creates the vehicle in
	 * the new level (same UUID, a new object) and remounts them; this then remounts any recorded
	 * rider that is not riding the new body (found by UUID, so only in a tracked chunk: hold a
	 * ticket on the destination first). Within one level the entity moves in place.
	 *
	 * <p>Callers must not keep {@code root} afterwards: use the returned entity.
	 *
	 * @return the moved entity: a new object after a change of level, or {@code root} when the
	 *         teleport did not happen
	 */
	public static Entity moveAcross(Entity root, ServerLevel to, Vec3 pos, float yaw) {
		List<UUID> riders = new ArrayList<>();

		for (Entity passenger : root.getPassengers()) {
			riders.add(passenger.getUUID());
		}

		Entity moved = root.teleport(new TeleportTransition(to, pos, Vec3.ZERO, yaw, root.getXRot(), TeleportTransition.DO_NOTHING));

		if (moved == null) {
			return root;
		}

		for (UUID id : riders) {
			if (isAboard(moved, id)) {
				continue;
			}

			// Only entities in tracked chunks are found by UUID: callers hold a ticket on the destination.
			Entity rider = to.getEntity(id);

			if (rider != null && rider != moved && !rider.isRemoved()) {
				rider.stopRiding();
				rider.startRiding(moved, true, false);
			}
		}

		return moved;
	}

	private static boolean isAboard(Entity vehicle, UUID id) {
		for (Entity passenger : vehicle.getPassengers()) {
			if (passenger.getUUID().equals(id)) {
				return true;
			}
		}

		return false;
	}

	/** Registers the travel contracts' hooks (the "not while flying" lock). Called from {@code M4Contracts}. */
	public static void init() {
		TravelLocks.init();
	}

	private TravelService() {
	}
}

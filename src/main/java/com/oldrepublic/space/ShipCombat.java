package com.oldrepublic.space;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Damage to a player's ship in space and the shield in front of it (spec MILESTONE_5.md 5.4). Owned
 * by W2 space; the flight code (W1) and the stories ask it whether a ship is under fire.
 *
 * <p>Only a ship at the helm ({@code FLYING}) takes hits. The shield (with the {@code SHIELD}
 * upgrade: 40 points, 60 on an interceptor, +20 with the Spindle Capacitor) takes each hit first and
 * recharges {@code space.shieldRecharge} points a second (×1.5 with the capacitor) after
 * {@code space.shieldDelay} ticks without one; what it does not absorb comes off the hull through
 * {@code Ships.update}. At 0 hull the ship is disabled ({@code Ships.disable}, W1) and its
 * encounters end. Shield points live in memory: a ship starts every flight with a full shield, and a
 * ship nobody has hit has no entry here.
 */
public final class ShipCombat {
	/** One ship's shield and recent hits, by owner. */
	private static final class State {
		float shield;
		long lastHit;
		/** Damage below one hull point, carried to the next hit (the hull is whole points). */
		float hullDebt;
		boolean recharging;
		boolean disabled;

		State(float shield, long lastHit) {
			this.shield = shield;
			this.lastHit = lastHit;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	/**
	 * Enemy fire hits {@code owner}'s ship: the shield first, then the hull through
	 * {@code Ships.update}; at 0 hull, {@code Ships.disable} (5.4).
	 */
	public static void hitShip(ServerPlayer owner, float damage, @Nullable Entity source) {
		MinecraftServer server = owner.level().getServer();
		ShipRecord record = ShipRegistry.of(server, owner.getUUID()).orElse(null);
		ShipBody ship = Ships.ship(server, owner.getUUID());

		if (damage <= 0f || record == null || ship == null || !(ship.level() instanceof ServerLevel level) || record.state() != ShipState.FLYING
				|| record.hull() <= 0) {
			return;
		}

		SpaceBalance b = SpaceBalance.get();
		long now = level.getGameTime();
		float max = maxShield(record, b);
		State state = STATES.computeIfAbsent(owner.getUUID(), id -> new State(max, Long.MIN_VALUE / 4));
		state.lastHit = now;
		state.recharging = false;

		boolean hadShield = state.shield > 0f;
		float absorbed = SpaceRules.absorbed(Math.min(state.shield, max), damage);
		state.shield = Math.max(0f, Math.min(state.shield, max) - absorbed);
		float rest = damage - absorbed;

		Vec3 centre = ship.getBoundingBox().getCenter();
		Vec3 at = source != null ? source.position() : centre;
		Vec3 out = at.subtract(centre);
		EffectEvents.around(level, SpaceEffects.SHIP_HIT, at, out.lengthSqr() > 1.0e-6 ? out.normalize() : null,
				ship.getId(), damage, rest > 0f ? SpaceEffects.HIT_HULL : SpaceEffects.HIT_SHIELD);

		if (hadShield && state.shield <= 0f) {
			EffectEvents.toTracking(ship, SpaceEffects.SHIP_SHIELD, centre, null, ship.getId(), 1f, SpaceEffects.SHIELD_DOWN);
		}

		if (rest <= 0f) {
			return;
		}

		state.hullDebt += rest;
		int whole = (int) Math.floor(state.hullDebt);

		if (whole <= 0) {
			return;
		}

		state.hullDebt -= whole;
		ShipRecord after = Ships.update(server, owner.getUUID(), r -> r.withHull(r.hull() - whole)).orElse(record);

		if (after.hull() <= 0 && !state.disabled) {
			state.disabled = true;
			Ships.disable(owner);
			SpaceEncounters.onDisabled(owner);
		}
	}

	/** Whether {@code owner}'s ship was hit in the last {@code space.underFireTicks} (the {@code UNDER_FIRE} refusal, 4.7). */
	public static boolean underFire(ServerPlayer owner) {
		State state = STATES.get(owner.getUUID());
		return state != null && owner.level().getGameTime() - state.lastHit < SpaceBalance.get().underFireTicks;
	}

	/**
	 * Whether {@code owner}'s ship was disabled by hits (its hull reached 0 here, so {@code Ships.disable}
	 * was called) and has not been repaired since (game tests and the status command).
	 */
	public static boolean disabled(ServerPlayer owner) {
		State state = STATES.get(owner.getUUID());
		return state != null && state.disabled;
	}

	/** The shield points {@code owner}'s ship has now (0 without a {@code SHIELD} upgrade, 5.4). */
	public static int shield(ServerPlayer owner) {
		return Math.round(shieldPoints(owner));
	}

	/** The shield points, unrounded (the combat HUD's bar). */
	public static float shieldPoints(ServerPlayer owner) {
		ShipRecord record = ShipRegistry.of(owner.level().getServer(), owner.getUUID()).orElse(null);

		if (record == null) {
			return 0f;
		}

		float max = maxShield(record, SpaceBalance.get());
		State state = STATES.get(owner.getUUID());
		return state == null ? max : Math.min(state.shield, max);
	}

	/** The most shield points the ship of {@code record} holds. */
	public static float maxShield(ShipRecord record, SpaceBalance b) {
		return SpaceRules.maxShield(record.has(ShipRecord.UPGRADE_SHIELD), Ships.interceptor(record), record.has(ShipRecord.PART_SPINDLE_CAPACITOR), b);
	}

	/**
	 * One server tick: shields recharge after the delay ({@code ship_shield} at the start of a
	 * recharge and when full), and a ship that is full, unhit for a while and whole again is
	 * forgotten.
	 */
	static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}

		SpaceBalance b = SpaceBalance.get();
		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, State> entry = it.next();
			State state = entry.getValue();
			ShipRecord record = ShipRegistry.of(server, entry.getKey()).orElse(null);

			if (record == null) {
				it.remove();
				continue;
			}

			if (state.disabled && record.hull() > 0) {
				// Rescued and repaired: a new flight, a fresh shield.
				it.remove();
				continue;
			}

			float max = maxShield(record, b);
			boolean capacitor = record.has(ShipRecord.PART_SPINDLE_CAPACITOR);
			long since = now - state.lastHit;
			float before = Math.min(state.shield, max);
			state.shield = SpaceRules.recharge(before, max, since, b.shieldDelay, SpaceRules.rechargePerSecond(capacitor, b));
			ShipBody ship = state.shield != before ? Ships.ship(server, entry.getKey()) : null;

			if (ship != null && !state.recharging) {
				state.recharging = true;
				EffectEvents.toTracking(ship, SpaceEffects.SHIP_SHIELD, ship.getBoundingBox().getCenter(), null, ship.getId(), 1f, SpaceEffects.SHIELD_RECHARGING);
			}

			if (ship != null && state.shield >= max) {
				state.recharging = false;
				EffectEvents.toTracking(ship, SpaceEffects.SHIP_SHIELD, ship.getBoundingBox().getCenter(), null, ship.getId(), 1f, SpaceEffects.SHIELD_FULL);
			}

			if (state.shield >= max && since > b.underFireTicks && !state.disabled) {
				it.remove();
			}
		}
	}

	/** The owner left: their ship's next flight starts with a full shield. */
	static void forget(UUID owner) {
		STATES.remove(owner);
	}

	/** Tests: everything forgotten. */
	static void reset() {
		STATES.clear();
	}

	private ShipCombat() {
	}
}

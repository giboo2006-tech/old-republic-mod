package com.oldrepublic.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ship's effect events (spec MILESTONE_4.md 13.3). Every look and sound is played by the client
 * handlers in {@code client.travel.TravelFx} and {@code HyperspaceOverlay}; the server only says
 * what happened.
 */
public final class TravelEffects {
	/** toTracking, entity: variant {@link #ENGINE_IDLE} to {@link #ENGINE_SETTLE}. */
	public static final Identifier SHIP_ENGINE = OldRepublicMod.id("ship_engine");
	public static final int ENGINE_IDLE = 0;
	public static final int ENGINE_SPOOL = 1;
	public static final int ENGINE_CLIMB = 2;
	public static final int ENGINE_DESCENT = 3;
	public static final int ENGINE_SETTLE = 4;

	/** toTracking, entity: 0 jump out, 1 arrive. */
	public static final Identifier SHIP_JUMP = OldRepublicMod.id("ship_jump");
	public static final int JUMP_OUT = 0;
	public static final int JUMP_IN = 1;

	/** toPlayer: the overlay; intensity = progress (0 starts it, 1 ends it). */
	public static final Identifier HYPERSPACE = OldRepublicMod.id("hyperspace");

	/**
	 * around: variant = socket (0 hull, 1 core, 2 motivator, 3 nav), 4 plate weld, 5 stand up; beyond the
	 * spec's 0–5, 6 a cell going into the tank and 7 an upgrade fitted or taken out.
	 */
	public static final Identifier SHIP_REPAIR = OldRepublicMod.id("ship_repair");
	public static final int REPAIR_WELD = 4;
	public static final int REPAIR_STAND_UP = 5;
	public static final int REPAIR_FUEL = 6;
	public static final int REPAIR_UPGRADE = 7;

	/** toTracking, entity: intensity = 1 − hull / 100 (a fresh hit of wear or sabotage). */
	public static final Identifier SHIP_DAMAGE = OldRepublicMod.id("ship_damage");

	/** around: 0 set, 1 refuse, 2 recall. */
	public static final Identifier LANDING_BEACON = OldRepublicMod.id("landing_beacon");
	public static final int BEACON_SET = 0;
	public static final int BEACON_REFUSE = 1;
	public static final int BEACON_RECALL = 2;

	static void engine(ShipBody ship, int variant) {
		EffectEvents.toTracking(ship, SHIP_ENGINE, ship.position(), null, ship.getId(), 1f, variant);
	}

	static void jump(ShipBody ship, int variant) {
		EffectEvents.toTracking(ship, SHIP_JUMP, ship.position(), ship.getLookAngle(), ship.getId(), 1f, variant);
	}

	static void hyperspace(ServerPlayer rider, float progress) {
		EffectEvents.toPlayer(rider, HYPERSPACE, rider.position(), null, -1, progress, 0);
	}

	static void repair(ServerLevel level, Vec3 at, @Nullable Entity ship, int variant) {
		EffectEvents.around(level, SHIP_REPAIR, at, null, ship == null ? -1 : ship.getId(), 1f, variant);
	}

	static void damage(ShipBody ship, int hull) {
		EffectEvents.toTracking(ship, SHIP_DAMAGE, ship.position(), null, ship.getId(), 1f - hull / 100f, 0);
	}

	static void beacon(ServerLevel level, BlockPos pos, int variant) {
		EffectEvents.around(level, LANDING_BEACON, Vec3.atCenterOf(pos), null, -1, 1f, variant);
	}

	private TravelEffects() {
	}
}

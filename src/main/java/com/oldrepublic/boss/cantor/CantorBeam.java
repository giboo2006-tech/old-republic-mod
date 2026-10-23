package com.oldrepublic.boss.cantor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The verse beam of the First Cantor's first phase (spec MILESTONE_5.md 12.3): every
 * {@code beamInterval} a rotating beam of pale light sweeps {@code beamArcDegrees} of the hall over
 * {@code beamSweepTicks}, {@code beamLength} long and {@code beamWidth} wide, at a body's height over
 * the floor (it cannot be jumped). For {@code beamTell} ticks before it moves, dust lifts along its
 * coming path ({@code BEAM_TELL}). Each player it crosses takes {@code beamDamage} once a sweep. The
 * sweep is aimed so its middle passes a participant.
 */
public final class CantorBeam {
	/** The beam's band over the floor (blocks): a body's height, so a jump does not clear it. */
	private static final double BAND_LOW = 0.3;
	private static final double BAND_HIGH = 1.9;
	/** A player's half-width, added to the beam's. */
	private static final double BODY = 0.3;

	private final CantorSession session;
	private final Set<UUID> struck = new HashSet<>();
	private int timer;
	private int tell = -1;
	private int sweep = -1;
	private int sweepTicks;
	private @Nullable Vec3 origin;
	/** The start direction (unit, flat) and the signed sweep in radians. */
	private Vec3 start = new Vec3(1, 0, 0);
	private double arc;

	CantorBeam(CantorSession session) {
		this.session = session;
	}

	/** Its tell or its sweep is running. */
	public boolean isActive() {
		return tell >= 0 || sweep >= 0;
	}

	/** Whether the tell runs (the dust lifts). */
	public boolean isTelling() {
		return tell >= 0;
	}

	/** Whether it sweeps now. */
	public boolean isSweeping() {
		return sweep >= 0;
	}

	/** The players struck by the current (or last) sweep. */
	public Set<UUID> struck() {
		return Set.copyOf(struck);
	}

	/** The first phase begins: the first beam comes after half an interval. */
	void start(RaidBalance b) {
		stop();
		timer = Math.max(1, b.beamInterval / 2);
	}

	void stop() {
		tell = -1;
		sweep = -1;
		origin = null;
	}

	/** One tick; {@code held} (Stasis) starts no new beam, but a running one plays out. */
	void tick(FirstCantor c, RaidBalance b, boolean held) {
		if (tell >= 0) {
			if (--tell < 0) {
				beginSweep(c, b);
			}

			return;
		}

		if (sweep >= 0) {
			advance(c, b);
			return;
		}

		if (held || --timer > 0) {
			return;
		}

		timer = Math.max(1, b.beamInterval);
		aim(c, b);
	}

	/** An operator's beam now (tests): its tell begins. Returns false while one runs. */
	public boolean forceBeam(FirstCantor c) {
		if (isActive()) {
			return false;
		}

		aim(c, RaidBalance.get());
		return true;
	}

	/** Picks the sweep: its middle passes a participant (or a random heading), turning either way. */
	private void aim(FirstCantor c, RaidBalance b) {
		Vec3 from = new Vec3(c.getX(), session.floorY() + (BAND_LOW + BAND_HIGH) / 2, c.getZ());
		List<ServerPlayer> targets = session.participantsInside();
		Vec3 toward;

		if (!targets.isEmpty()) {
			ServerPlayer target = targets.get(session.random().nextInt(targets.size()));
			toward = target.position().subtract(from).multiply(1, 0, 1);
		} else {
			double angle = session.random().nextDouble() * Math.PI * 2;
			toward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		}

		if (toward.lengthSqr() < 1.0e-4) {
			toward = new Vec3(0, 0, 1);
		}

		double sign = session.random().nextBoolean() ? 1.0 : -1.0;
		arc = sign * Math.toRadians(Math.max(1f, b.beamArcDegrees));
		start = turn(toward.normalize(), -arc / 2);
		origin = from;
		tell = Math.max(1, b.beamTell);
		struck.clear();
		c.setAction(FirstCantor.Action.BEAM);
		session.event(from, start.scale(b.beamLength).add(0, tell, 0), c.getId(), (float) Math.toDegrees(arc), CantorEffects.BEAM_TELL);
		session.play(from, ModSounds.BOSS_CANTOR_BEAM_TELL, 1.6f, 1f);
	}

	private void beginSweep(FirstCantor c, RaidBalance b) {
		Vec3 from = origin;

		if (from == null) {
			stop();
			return;
		}

		sweepTicks = Math.max(1, b.beamSweepTicks);
		sweep = 0;
		session.event(from, start.scale(b.beamLength).add(0, sweepTicks, 0), c.getId(), (float) Math.toDegrees(arc), CantorEffects.BEAM);
		advance(c, b);
	}

	/** The beam turns one step; everyone between the last step and this one (with the beam's width) is struck once. */
	private void advance(FirstCantor c, RaidBalance b) {
		Vec3 from = origin;

		if (from == null) {
			stop();
			return;
		}

		double before = arc * Math.max(0, sweep - 1) / sweepTicks;
		double now = arc * Math.min(sweep, sweepTicks) / sweepTicks;

		for (ServerPlayer player : session.everyoneInside()) {
			if (!struck.contains(player.getUUID()) && crosses(player, from, before, now, b)) {
				struck.add(player.getUUID());
				player.hurtServer(session.level(), session.hymnDamage(c), b.beamDamage);
			}
		}

		if (++sweep > sweepTicks) {
			sweep = -1;
			origin = null;
			session.restPose(c);
		}
	}

	/** Whether {@code player} lies in the band swept from angle {@code before} to {@code now} (radians from the start, signed like the arc). */
	private boolean crosses(ServerPlayer player, Vec3 from, double before, double now, RaidBalance b) {
		double low = session.floorY() + BAND_LOW;
		double high = session.floorY() + BAND_HIGH;

		if (player.getBoundingBox().maxY < low || player.getBoundingBox().minY > high) {
			return false;
		}

		Vec3 flat = player.position().subtract(from).multiply(1, 0, 1);
		double distance = flat.length();

		if (distance > b.beamLength + BODY) {
			return false;
		}

		if (distance < 1.0e-3) {
			return true;
		}

		// The player's angle from the start direction, signed so the sweep runs from 0 toward arc.
		double angle = Math.atan2(start.x * flat.z - start.z * flat.x, start.x * flat.x + start.z * flat.z);
		double margin = Math.atan((b.beamWidth / 2 + BODY) / distance);
		double lo = Math.min(before, now) - margin;
		double hi = Math.max(before, now) + margin;
		return angle >= lo && angle <= hi;
	}

	/**
	 * {@code dir} turned by {@code radians} about the vertical, positive from +X toward +Z (the
	 * {@code BEAM_TELL} convention): (x, z) goes to (x cos a - z sin a, x sin a + z cos a).
	 */
	static Vec3 turn(Vec3 dir, double radians) {
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
	}

	/** The beam's direction (unit, flat) {@code ticks} into a sweep: for the tests and the client's mirror of it. */
	public Vec3 directionAt(int ticks) {
		return turn(start, arc * Mth.clamp(ticks, 0, Math.max(1, sweepTicks)) / Math.max(1, sweepTicks));
	}
}

package com.oldrepublic.travel;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The flight model of free flight (spec MILESTONE_5.md 4.3, 4.4), as pure functions: the same code
 * runs on the pilot's client, which simulates the ship ({@link ShipBody}), and in the server tests.
 *
 * <p>The ship's speed lives in three channels: along its heading (yaw and pitch: thrust, brake and
 * the slow reverse), along its right-hand side (strafe, a share of thrust and top speed) and along
 * world up (the vertical thrusters, climb and descent). A channel with no input decays (×0.97 per
 * tick in a sky), so the ship coasts to a hover; the vertical thrusters stop the ship's climb or
 * descent at the brake rate instead, so a released Jump or Sneak holds the height. The ship turns
 * toward where the pilot looks at its turn rate, and pitches toward the look within the pitch limit.
 * No velocity ever exceeds the profile's hard cap.
 */
public final class FlightModel {
	/** A channel slower than this stops (blocks per tick). */
	private static final double STOP = 0.003;

	/**
	 * The numbers one ship flies by (4.4), worked out by the server for its class, upgrades and
	 * place (a sky or an orbit) and sent to the pilot ({@link FlightRules#profile}).
	 *
	 * @param top        top speed along the heading, blocks per tick
	 * @param thrust     acceleration along the heading, blocks per tick per tick
	 * @param brake      deceleration when braking, and the vertical thrusters' acceleration
	 * @param climb      top climb and descent speed
	 * @param turn       degrees of yaw and pitch per tick
	 * @param decay      the share of speed kept each tick with no input
	 * @param reverse    top reverse speed
	 * @param strafe     strafe as a share of thrust and top speed (0 to 1)
	 * @param pitchLimit the nose's pitch limit, degrees either way
	 * @param boost      the boost's multiplier of top speed and thrust
	 * @param cap        the hard cap on the ship's speed
	 */
	public record Profile(float top, float thrust, float brake, float climb, float turn, float decay, float reverse, float strafe, float pitchLimit,
			float boost, float cap) {
	}

	/**
	 * The pilot's controls for one tick (the pilot's {@code Input} and look, 4.3).
	 *
	 * @param yaw      where the pilot looks, degrees
	 * @param pitch    where the nose should point, degrees (already inverted when the pilot asked for it)
	 * @param boosting the server granted the boost
	 */
	public record Controls(boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, float yaw, float pitch,
			boolean boosting) {
		/** No input, looking along {@code yaw} and {@code pitch}. */
		public static Controls idle(float yaw, float pitch) {
			return new Controls(false, false, false, false, false, false, yaw, pitch, false);
		}
	}

	/**
	 * The ship's speed in its three channels, blocks per tick.
	 *
	 * @param forward along the heading (negative: reversing)
	 * @param strafe  along the right-hand side (negative: to the left)
	 * @param lift    along world up (negative: descending)
	 */
	public record Motion(double forward, double strafe, double lift) {
		public static final Motion STILL = new Motion(0, 0, 0);
	}

	/** The ship's yaw and pitch, degrees. */
	public record Pose(float yaw, float pitch) {
	}

	/** One tick of turning: yaw toward the look, pitch toward it within the limit, each at most the turn rate. */
	public static Pose turn(Pose ship, Controls c, Profile p) {
		float yaw = ship.yaw() + Mth.clamp(Mth.wrapDegrees(c.yaw() - ship.yaw()), -p.turn(), p.turn());
		float target = Mth.clamp(c.pitch(), -p.pitchLimit(), p.pitchLimit());
		float pitch = ship.pitch() + Mth.clamp(target - ship.pitch(), -p.turn(), p.turn());
		return new Pose(yaw, pitch);
	}

	/** One tick of the controls on the three channels. */
	public static Motion accelerate(Motion m, Controls c, Profile p) {
		double boost = c.boosting() ? p.boost() : 1.0;
		double top = p.top() * boost;
		double thrust = p.thrust() * boost;
		double forward;

		if (c.forward() && !c.backward()) {
			forward = m.forward() < 0 ? Math.min(0, m.forward() + p.brake())
					: m.forward() > top ? Math.max(top, m.forward() * p.decay()) : Math.min(top, m.forward() + thrust);
		} else if (c.backward() && !c.forward()) {
			forward = m.forward() > 0 ? Math.max(0, m.forward() - p.brake()) : Math.max(-p.reverse(), m.forward() - thrust);
		} else {
			forward = settle(m.forward() * p.decay());
		}

		double strafe = channel(m.strafe(), c.right(), c.left(), top * p.strafe(), thrust * p.strafe(), p.brake(), p.decay());
		double lift;

		if (c.up() && !c.down()) {
			lift = m.lift() < 0 ? Math.min(0, m.lift() + p.brake()) : Math.min(p.climb(), m.lift() + p.brake());
		} else if (c.down() && !c.up()) {
			lift = m.lift() > 0 ? Math.max(0, m.lift() - p.brake()) : Math.max(-p.climb(), m.lift() - p.brake());
		} else {
			lift = Math.abs(m.lift()) <= p.brake() ? 0 : m.lift() - Math.copySign(p.brake(), m.lift());
		}

		return new Motion(forward, strafe, lift);
	}

	/** A strafe channel: toward {@code top} one way or the other, braking first when it moves the other way. */
	private static double channel(double v, boolean plus, boolean minus, double top, double acceleration, double brake, double decay) {
		if (plus && !minus) {
			return v < 0 ? Math.min(0, v + brake) : v > top ? Math.max(top, v * decay) : Math.min(top, v + acceleration);
		}

		if (minus && !plus) {
			return v > 0 ? Math.max(0, v - brake) : v < -top ? Math.min(-top, v * decay) : Math.max(-top, v - acceleration);
		}

		return settle(v * decay);
	}

	private static double settle(double v) {
		return Math.abs(v) < STOP ? 0 : v;
	}

	/** The world velocity of {@code m} for a ship at {@code pose}, no faster than {@code cap}. */
	public static Vec3 velocity(Motion m, Pose pose, double cap) {
		Vec3 v = heading(pose).scale(m.forward()).add(right(pose.yaw()).scale(m.strafe())).add(0, m.lift(), 0);
		double length = v.length();
		return length > cap ? v.scale(cap / length) : v;
	}

	/**
	 * The channels that would move a ship at {@code pose} by {@code moved} in one tick: after a
	 * collision the ship keeps only the speed it actually made.
	 */
	public static Motion fromDisplacement(Vec3 moved, Pose pose) {
		Vec3 right = right(pose.yaw());
		double strafe = moved.dot(right);
		Vec3 rest = moved.subtract(right.scale(strafe));
		Vec3 heading = heading(pose);
		double flat = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
		double forward = flat < 1.0e-4 ? 0 : (rest.x * heading.x + rest.z * heading.z) / (flat * flat);
		return new Motion(settle(forward), settle(strafe), settle(rest.y - heading.y * forward));
	}

	/** The unit vector along the nose (Minecraft's view vector for this yaw and pitch). */
	public static Vec3 heading(Pose pose) {
		double yaw = Math.toRadians(pose.yaw());
		double pitch = Math.toRadians(pose.pitch());
		double flat = Math.cos(pitch);
		return new Vec3(-Math.sin(yaw) * flat, -Math.sin(pitch), Math.cos(yaw) * flat);
	}

	/** The unit vector to the ship's right for {@code yaw} (horizontal). */
	public static Vec3 right(float yaw) {
		double rad = Math.toRadians(yaw);
		return new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
	}

	private FlightModel() {
	}
}

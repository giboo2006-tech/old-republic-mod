package com.oldrepublic.enemy.creature;

import net.minecraft.world.phys.Vec3;

/**
 * The beasts' pure rules (spec MILESTONE_4.md 10.3, 10.4, 3.4), kept apart from the entities so the
 * tests can check them without a world.
 */
public final class CreatureRules {
	/** Ash hound pack roles: one holds in front, the rest circle to the flanks. */
	public enum Role {
		FRONT,
		FLANK_LEFT,
		FLANK_RIGHT
	}

	/** Degrees off the front line a flanker circles to. */
	public static final double FLANK_DEGREES = 110;
	/** Blocks a hound's leap carries per unit of starting speed (mob air drag over about 12 ticks of flight). */
	private static final double LEAP_CARRY = 7.4;

	/** A herd's first member is its bull: always for a natural herd, sometimes for one placed by a site or a spawner. */
	public static boolean isBull(int index, boolean natural, float roll) {
		return index == 0 && (natural || roll < 0.3f);
	}

	/** Members after the first two may be calves; corrupted hollowhorns never are. */
	public static boolean isCalf(int index, float roll, float chance, boolean corrupted) {
		return !corrupted && index >= 2 && roll < chance;
	}

	/** Whether the hollowhorn should start its charge tell now. */
	public static boolean chargeDue(CreatureBalance b, double distance, int cooldown, boolean canSee, boolean calf) {
		return !calf && cooldown <= 0 && canSee && distance >= b.chargeMinRange && distance <= b.chargeRange;
	}

	/** The damage a hollowhorn takes: {@code stunDamageMult} times while stunned against a wall. */
	public static float hollowhornDamage(CreatureBalance b, float amount, boolean stunned) {
		return stunned ? amount * b.stunDamageMult : amount;
	}

	/** The pack role of the hound at {@code index} among the hounds hunting one player (ordered by entity id). */
	public static Role role(int index) {
		if (index == 0) {
			return Role.FRONT;
		}

		return index % 2 == 1 ? Role.FLANK_LEFT : Role.FLANK_RIGHT;
	}

	/**
	 * Where a hound of {@code role} stands around {@code target}: the front hound on the line from the
	 * target to the pack ({@code frontDir}, horizontal), {@code hold} blocks out; flankers
	 * {@link #FLANK_DEGREES} to either side of it at {@code radius}.
	 */
	public static Vec3 spot(Vec3 target, Vec3 frontDir, Role role, double hold, double radius) {
		Vec3 flat = new Vec3(frontDir.x, 0, frontDir.z);
		Vec3 dir = flat.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : flat.normalize();

		if (role == Role.FRONT) {
			return target.add(dir.scale(hold));
		}

		double angle = Math.toRadians(role == Role.FLANK_LEFT ? FLANK_DEGREES : -FLANK_DEGREES);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		Vec3 turned = new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
		return target.add(turned.scale(radius));
	}

	/** The horizontal starting speed of a leap that lands {@code distance} blocks away, capped at {@code maxRange}. */
	public static double leapSpeed(double distance, double maxRange) {
		return Math.min(distance, maxRange) / LEAP_CARRY;
	}

	/**
	 * The ash hound's natural spawn rule (10.4, 3.4): at dusk and night or in the ashfall, and never
	 * within {@code exclusion} blocks of the Proving ({@code hubDistanceSqr} negative when the hub is
	 * not placed).
	 */
	public static boolean houndMaySpawn(boolean dark, boolean ashfall, double hubDistanceSqr, int exclusion) {
		if (hubDistanceSqr >= 0 && hubDistanceSqr < (double) exclusion * exclusion) {
			return false;
		}

		return dark || ashfall;
	}

	private CreatureRules() {
	}
}

package com.oldrepublic.enemy.trooper.goal;

import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** Path legs for troopers walking somewhere farther than the pathfinder reaches in one go. */
final class TrooperPaths {
	/** A destination closer than this is walked to directly. */
	private static final double DIRECT = 16;
	/** Horizontal and vertical reach of one leg's random land position. */
	private static final int LEG_RANGE = 16;
	private static final int LEG_HEIGHT = 7;

	/**
	 * The next point to walk to on the way to {@code destination}: the destination itself when it is
	 * near, else a reachable spot about {@value #LEG_RANGE} blocks toward it (straight toward it when
	 * no such spot is found).
	 */
	static Vec3 legToward(TrooperEntity trooper, Vec3 destination) {
		Vec3 here = trooper.position();
		Vec3 toward = destination.subtract(here);

		if (toward.lengthSqr() <= DIRECT * DIRECT) {
			return destination;
		}

		Vec3 leg = DefaultRandomPos.getPosTowards(trooper, LEG_RANGE, LEG_HEIGHT, destination, Math.PI / 2);
		return leg != null ? leg : here.add(toward.normalize().scale(DIRECT));
	}

	private TrooperPaths() {
	}
}

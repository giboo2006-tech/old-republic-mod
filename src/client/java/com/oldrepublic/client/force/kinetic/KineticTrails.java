package com.oldrepublic.client.force.kinetic;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The wake of a creature thrown by the Force (pushed, pulled, hurled): for a short while it is followed, and where it
 * scrapes along the ground it ploughs up dust and grit; in the air dust it was carrying streams off its feet; and streaks of
 * air stream off it along its flight. Each wake has a particle budget and a lifetime, and ends when the creature stops, dies
 * or leaves.
 */
public final class KineticTrails {
	/** Follows {@code target} for at most {@code ticks}, spending at most {@code budget} particles. */
	public static void wake(Entity target, int ticks, int budget) {
		int id = target.getId();
		Vec3[] last = {target.position()};
		int[] left = {budget};
		int[] age = {0};

		KineticRender.run(level -> {
			Entity entity = level.getEntity(id);

			if (entity == null || !entity.isAlive() || ++age[0] > ticks || left[0] <= 0) {
				return false;
			}

			Vec3 now = entity.position();
			Vec3 moved = now.subtract(last[0]);
			last[0] = now;

			if (moved.length() < 0.06) {
				// Not yet moving (the motion arrives a tick late) or come to rest.
				return age[0] < 4;
			}

			left[0] -= plough(level, now, moved);
			Vec3 middle = now.add(0, entity.getBbHeight() * 0.55, 0);

			for (int i = 0; i < 2; i++) {
				Vec3 at = middle.add(KineticKit.spread(0.4), KineticKit.spread(entity.getBbHeight() * 0.45), KineticKit.spread(0.4)).subtract(moved.scale(0.5));
				AirStreakParticle streak = KineticKit.streak(at, moved.scale(0.7), 0.28f, 0.55f, 5);

				if (streak != null) {
					streak.drag(0.75f);
				}
			}

			left[0] -= 2;
			return true;
		});
	}

	/**
	 * Dust in the ground's colour (and now and then grit) ploughed up where a creature moving by {@code moved} a tick
	 * scrapes the ground; how many particles it spent.
	 */
	private static int plough(ClientLevel level, Vec3 now, Vec3 moved) {
		BlockPos ground = KineticKit.ground(level, now.add(0, 0.3, 0), 3);

		if (ground == null) {
			return 0;
		}

		if (now.y - (ground.getY() + 1) > 0.5) {
			// In the air: a little of the dust it was carrying streams off its feet and hangs behind it.
			float[] haze = KineticKit.airDust(KineticKit.dustColor(level, ground));
			DustCloudParticle trail = KineticKit.dust(now.add(KineticKit.spread(0.25), 0.1, KineticKit.spread(0.25)), moved.scale(0.2), haze, 0.22f, 2.4f, 0.5f,
					18 + KineticKit.RANDOM.nextInt(8));

			if (trail != null) {
				trail.hold(0.2f).drag(0.9f);
			}

			return 1;
		}

		float[] colour = KineticKit.dustColor(level, ground);
		Vec3 at = new Vec3(now.x + KineticKit.spread(0.3), ground.getY() + 1.1, now.z + KineticKit.spread(0.3));
		DustCloudParticle cloud = KineticKit.dust(at, moved.scale(0.25).add(0, 0.04, 0), KineticKit.airDust(colour), 0.28f, 2.6f, 0.52f,
				30 + KineticKit.RANDOM.nextInt(14));

		if (cloud != null) {
			cloud.lift(-0.002f);
		}

		if (KineticKit.RANDOM.nextInt(3) == 0) {
			KineticKit.pebble(at, moved.scale(0.4).add(KineticKit.jitter(0.05)).add(0, 0.15, 0), KineticKit.gritColor(colour), 0.05f, 26);
			return 2;
		}

		return 1;
	}

	private KineticTrails() {
	}
}

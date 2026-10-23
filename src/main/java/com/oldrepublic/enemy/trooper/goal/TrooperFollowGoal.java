package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Squad members follow their leader out of combat (spec 7.5): each takes its own place in a loose
 * fan behind the leader, {@code squadSpacing} (3 to 5) blocks from it and from each other, so the
 * squad walks as a group and never bunches up. The leader itself walks its route or its post.
 */
public final class TrooperFollowGoal extends Goal {
	private static final int LEADER_REFRESH = 20;
	private static final int REPATH_TICKS = 10;
	/** A member closer than this to its place stops. */
	private static final double IN_PLACE = 1.5;
	/** A member farther than this from its place starts walking. */
	private static final double OUT_OF_PLACE = 2.5;
	/** Angle between neighbouring places in the fan behind the leader, in radians. */
	private static final double FAN_STEP = Math.toRadians(50);
	private static final double SPEED = 0.9;
	private static final double CATCH_UP_SPEED = 1.15;
	/** Beyond this distance from the leader a member hurries. */
	private static final double FAR = 10;

	private final TrooperEntity trooper;
	private @Nullable TrooperEntity leader;
	private int slot;
	private int leaderAge = LEADER_REFRESH;
	private int repath;

	public TrooperFollowGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!trooper.canFight() || trooper.getTarget() != null || trooper.squad() == null) {
			return false;
		}

		refreshLeader();
		Vec3 place = place();
		return place != null && trooper.position().distanceToSqr(place) > OUT_OF_PLACE * OUT_OF_PLACE;
	}

	@Override
	public boolean canContinueToUse() {
		if (!trooper.canFight() || trooper.getTarget() != null) {
			return false;
		}

		Vec3 place = place();
		return place != null && trooper.position().distanceToSqr(place) > IN_PLACE * IN_PLACE;
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public void stop() {
		trooper.getNavigation().stop();
	}

	@Override
	public void tick() {
		refreshLeader();
		Vec3 place = place();

		if (place == null || --repath > 0) {
			return;
		}

		repath = REPATH_TICKS;
		boolean far = leader != null && trooper.distanceToSqr(leader) > FAR * FAR;
		trooper.getNavigation().moveTo(place.x, place.y, place.z, far ? CATCH_UP_SPEED : SPEED);
	}

	private void refreshLeader() {
		if (++leaderAge < LEADER_REFRESH && leader != null && leader.isAlive()) {
			return;
		}

		leaderAge = 0;
		TrooperEntity found = Squads.leader(trooper);
		leader = found == trooper ? null : found;

		if (leader != null) {
			// Each member's place in the fan: its index among the squad's other members.
			List<TrooperEntity> members = Squads.squadOf(trooper);
			int index = 0;

			for (TrooperEntity member : members) {
				if (member != leader && member.getUUID().compareTo(trooper.getUUID()) < 0) {
					index++;
				}
			}

			slot = index;
		}
	}

	/** This member's place behind the leader, or null when it follows nobody. */
	private @Nullable Vec3 place() {
		TrooperEntity lead = leader;

		if (lead == null || !lead.isAlive() || lead.isRemoved()) {
			return null;
		}

		TrooperBalance b = TrooperBalance.get();
		int min = b.squadSpacing[0];
		int max = b.squadSpacing[b.squadSpacing.length - 1];
		Vec3 heading = lead.getDeltaMovement().multiply(1, 0, 1);

		if (heading.lengthSqr() < 1.0e-4) {
			heading = lead.getLookAngle().multiply(1, 0, 1);
		}

		if (heading.lengthSqr() < 1.0e-4) {
			heading = new Vec3(0, 0, 1);
		}

		// Straight behind first, then alternating left and right, each step a little farther out:
		// slot 0 at min, slots 1-2 at min + 1, slots 3-4 at min + 2, never beyond max.
		int step = (slot + 1) / 2;
		double angle = Math.PI + (slot % 2 == 1 ? 1 : -1) * step * FAN_STEP;
		double radius = min + Math.min(step, Math.max(0, max - min));
		Vec3 ahead = heading.normalize();
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		Vec3 offset = new Vec3(ahead.x * cos - ahead.z * sin, 0, ahead.x * sin + ahead.z * cos).scale(radius);
		return lead.position().add(offset);
	}
}

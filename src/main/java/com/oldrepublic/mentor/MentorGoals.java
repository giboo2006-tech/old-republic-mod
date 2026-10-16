package com.oldrepublic.mentor;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.StoryMentors;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** The mentor's own behaviours: walking away from the Crossing, facing the owner, meditating. */
final class MentorGoals {
	/** Walks away from where the departure began until the entity removes itself. */
	static final class Depart extends Goal {
		private final MentorEntity mentor;

		Depart(MentorEntity mentor) {
			this.mentor = mentor;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return mentor.isDeparting() && mentor.departFrom() != null;
		}

		@Override
		public void tick() {
			Vec3 from = mentor.departFrom();

			if (from == null || mentor.tickCount % 20 != 0 && !mentor.getNavigation().isDone()) {
				return;
			}

			Vec3 away = mentor.position().subtract(from).horizontal();
			Vec3 dir = away.lengthSqr() < 1.0e-4 ? mentor.getLookAngle().horizontal() : away;

			if (dir.lengthSqr() < 1.0e-4) {
				dir = new Vec3(1, 0, 0);
			}

			Vec3 target = mentor.position().add(dir.normalize().scale(10));
			mentor.getNavigation().moveTo(target.x, target.y, target.z, 0.8);
		}
	}

	/** Looks at the owner while they are close (at the Crossing: from a little further). */
	static final class FaceOwner extends Goal {
		private final MentorEntity mentor;
		private @Nullable ServerPlayer owner;

		FaceOwner(MentorEntity mentor) {
			this.mentor = mentor;
			setFlags(EnumSet.of(Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (mentor.isDeparting() || mentor.isSparring()) {
				return false;
			}

			ServerPlayer player = mentor.ownerPlayer();
			double range = StoryMentors.faceDistance(mentor);

			if (player == null || player.level() != mentor.level() || player.distanceToSqr(mentor) > range * range) {
				return false;
			}

			owner = player;
			return true;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			mentor.setMeditating(false);
			mentor.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (owner != null) {
				mentor.getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
			}
		}
	}

	/** Kneels still at the camp for a while when nobody needs the mentor. */
	static final class Meditate extends Goal {
		private static final int MIN_TICKS = 300;
		private final MentorEntity mentor;
		private int remaining;

		Meditate(MentorEntity mentor) {
			this.mentor = mentor;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return !mentor.isDeparting() && !mentor.isSparring() && mentor.anchor() != null && mentor.onGround()
					&& mentor.getRandom().nextInt(reducedTickDelay(400)) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return remaining > 0 && !mentor.isSparring() && !mentor.isDeparting();
		}

		@Override
		public void start() {
			remaining = MIN_TICKS + mentor.getRandom().nextInt(300);
			mentor.getNavigation().stop();
			mentor.setMeditating(true);
		}

		@Override
		public void tick() {
			remaining--;
		}

		@Override
		public void stop() {
			mentor.setMeditating(false);
		}
	}

	private MentorGoals() {
	}
}

package com.oldrepublic.enemy;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * When a ranged fighter is badly hurt and was just hit, it runs to a nearby spot the target cannot
 * see (a block between the target's eyes and its chest), waits there briefly, then lets its combat
 * goal take over again, which peeks out to regain a firing line. If no such spot is reachable the
 * goal simply does not start.
 *
 * <p>Written for the Crystal Poacher ({@link PoacherCombatGoal}) and shared with the riflemen of
 * the trooper squads (spec 7.1), which pass their own {@link Settings}, a condition and what to do
 * when they break off (drop the current tell).
 */
public final class PoacherCoverGoal extends Goal {
	/**
	 * The numbers of one user, read when they are needed (never at mod init).
	 *
	 * @param healthFraction below this fraction of max health a recently hit fighter looks for cover
	 * @param speed navigation speed while running for cover
	 * @param holdMinTicks shortest wait in cover
	 * @param holdMaxTicks longest wait in cover
	 * @param cooldownTicks ticks after leaving cover before it looks again
	 */
	public record Settings(double healthFraction, double speed, int holdMinTicks, int holdMaxTicks, int cooldownTicks) {
	}

	private static final int RECENT_HIT_TICKS = 40;
	private static final int SAMPLES = 12;
	/** Gives up running for cover after this long. */
	private static final int MAX_TICKS = 120;

	private final PathfinderMob mob;
	private final Supplier<Settings> settings;
	private final BooleanSupplier allowed;
	private final Runnable onStart;
	private @Nullable Path path;
	private int nextAllowedTick;
	private int holdTicks;
	private int runningTicks;

	/** The Crystal Poacher's cover, with the {@code enemy} balance values. */
	PoacherCoverGoal(CrystalPoacher mob) {
		this(mob, PoacherCoverGoal::poacherSettings, () -> true, () -> mob.setCharging(false));
	}

	/**
	 * @param settings read each time a number is needed, so balance changes apply at once
	 * @param allowed the goal only starts while this holds (for example: only riflemen take cover)
	 * @param onStart run when it breaks off to run for cover (drop the current tell)
	 */
	public PoacherCoverGoal(PathfinderMob mob, Supplier<Settings> settings, BooleanSupplier allowed, Runnable onStart) {
		this.mob = mob;
		this.settings = settings;
		this.allowed = allowed;
		this.onStart = onStart;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	private static Settings poacherSettings() {
		EnemyBalance b = EnemyBalance.get();
		return new Settings(b.coverHealthFraction, b.coverSpeed, b.coverHoldMinTicks, b.coverHoldMaxTicks, b.coverCooldownTicks);
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();

		if (target == null || !target.isAlive() || mob.tickCount < nextAllowedTick || !allowed.getAsBoolean()) {
			return false;
		}

		Settings s = settings.get();

		if (mob.getHealth() >= mob.getMaxHealth() * s.healthFraction() || mob.tickCount - mob.getLastHurtByMobTimestamp() > RECENT_HIT_TICKS) {
			return false;
		}

		path = findCover(target);

		if (path == null) {
			// Nothing feasible nearby; try again a little later rather than every tick.
			nextAllowedTick = mob.tickCount + 20;
		}

		return path != null;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && runningTicks < MAX_TICKS && holdTicks != 0 && allowed.getAsBoolean();
	}

	@Override
	public void start() {
		onStart.run();
		mob.setAggressive(true);
		mob.getNavigation().moveTo(path, settings.get().speed());
		holdTicks = -1;
		runningTicks = 0;
	}

	@Override
	public void stop() {
		nextAllowedTick = mob.tickCount + settings.get().cooldownTicks();
		mob.getNavigation().stop();
		path = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();

		if (target != null) {
			mob.getLookControl().setLookAt(target, 30f, 30f);
		}

		if (holdTicks < 0) {
			runningTicks++;

			if (mob.getNavigation().isDone()) {
				Settings s = settings.get();
				holdTicks = s.holdMinTicks() + mob.getRandom().nextInt(Math.max(1, s.holdMaxTicks() - s.holdMinTicks() + 1));
			}
		} else if (holdTicks > 0) {
			holdTicks--;
		}
	}

	/** The nearest reachable spot within about 8 blocks that the target cannot see. */
	private @Nullable Path findCover(LivingEntity target) {
		Vec3 targetEye = target.getEyePosition();
		Path best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int i = 0; i < SAMPLES; i++) {
			Vec3 candidate = (i & 1) == 0
					? DefaultRandomPos.getPosAway(mob, 8, 4, target.position())
					: LandRandomPos.getPos(mob, 8, 4);

			if (candidate == null) {
				continue;
			}

			Vec3 chest = candidate.add(0, 1.4, 0);
			HitResult hit = mob.level().clip(new ClipContext(targetEye, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));

			if (hit.getType() != HitResult.Type.BLOCK) {
				continue;
			}

			double distance = mob.distanceToSqr(candidate);

			if (distance >= bestDistance) {
				continue;
			}

			Path candidatePath = mob.getNavigation().createPath(BlockPos.containing(candidate), 0);

			if (candidatePath != null && candidatePath.canReach()) {
				best = candidatePath;
				bestDistance = distance;
			}
		}

		return best;
	}
}

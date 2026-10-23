package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperSight;
import com.oldrepublic.mission.MissionFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An escort's walk (spec 7.1, 5.4 I2 hand-over, 8.7 custody): two troopers without combat goals
 * flank the prisoner, one on each side, and walk it to the exit. The escort on the +1 side leads:
 * it walks a mob prisoner with that prisoner's own navigation (a bound trooper then neither stands
 * still nor walks off alone). Once the three are out of every player's sight, after a short first
 * stretch or at the exit, the lead escort removes all three (never a player prisoner); after
 * {@code escortDespawnTicks} they go regardless.
 */
public final class TrooperEscortGoal extends Goal {
	/** Escorts walk this far to the side of the prisoner. */
	private static final double SIDE_OFFSET = 1.5;
	/** Ticks of walking before the party may vanish on the way. */
	private static final int MIN_WALK_TICKS = 40;
	/** The party is at the exit within this many blocks. */
	private static final double EXIT_REACH = 3;
	private static final int REPATH_TICKS = 10;
	private static final double PRISONER_SPEED = 0.7;
	private static final double ESCORT_SPEED = 0.85;
	/** How far around the prisoner the partner escort is looked for. */
	private static final double PARTNER_RANGE = 8;

	private final TrooperEntity trooper;
	private int repath;

	public TrooperEscortGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return trooper.isEscort() && trooper.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (!(trooper.level() instanceof ServerLevel level)) {
			return;
		}

		long now = level.getGameTime();
		BlockPos exitPos = trooper.escortExit();
		Vec3 exit = exitPos != null ? Vec3.atBottomCenterOf(exitPos) : trooper.position();
		LivingEntity prisoner = trooper.escortPrisonerOf();
		boolean prisonerGone = prisoner == null || !prisoner.isAlive() || prisoner.isRemoved();
		boolean lead = trooper.escortSide() > 0;
		long walked = now - trooper.escortSince();

		if (walked >= MissionFeature.balance().escortDespawnTicks) {
			if (lead || prisonerGone) {
				removeParty(level, prisonerGone ? null : prisoner);
			}

			return;
		}

		Vec3 centre = prisonerGone ? trooper.position() : prisoner.position();
		boolean atExit = centre.distanceToSqr(exit) <= EXIT_REACH * EXIT_REACH;

		if ((lead || prisonerGone) && (walked >= MIN_WALK_TICKS || atExit) && partyUnseen(level, prisonerGone ? null : prisoner)) {
			removeParty(level, prisonerGone ? null : prisoner);
			return;
		}

		boolean repathNow = --repath <= 0;

		if (repathNow) {
			repath = REPATH_TICKS;
		}

		if (lead && !prisonerGone && prisoner instanceof Mob mob) {
			if (prisoner instanceof TrooperEntity bound) {
				bound.markEscorted(now);
			}

			if (!atExit && repathNow) {
				mob.getNavigation().moveTo(exit.x, exit.y, exit.z, PRISONER_SPEED);
			}
		}

		if (repathNow) {
			Vec3 spot = prisonerGone ? exit : besidePrisoner(prisoner, exit);
			trooper.getNavigation().moveTo(spot.x, spot.y, spot.z, ESCORT_SPEED);
		}

		trooper.getLookControl().setLookAt(prisonerGone ? exit.add(0, 1.6, 0) : prisoner.getEyePosition());
	}

	/** This escort's place: beside the prisoner on its own side of the line to the exit. */
	private Vec3 besidePrisoner(LivingEntity prisoner, Vec3 exit) {
		Vec3 ahead = exit.subtract(prisoner.position()).multiply(1, 0, 1);

		if (ahead.lengthSqr() < 1.0e-4) {
			ahead = prisoner.getLookAngle().multiply(1, 0, 1);
		}

		if (ahead.lengthSqr() < 1.0e-4) {
			return prisoner.position();
		}

		Vec3 side = new Vec3(-ahead.z, 0, ahead.x).normalize().scale(SIDE_OFFSET * trooper.escortSide());
		return prisoner.position().add(side);
	}

	/** Whether no player sees either escort or the prisoner. */
	private boolean partyUnseen(ServerLevel level, @Nullable LivingEntity prisoner) {
		if (!TrooperSight.unseen(level, trooper) || prisoner != null && !TrooperSight.unseen(level, prisoner)) {
			return false;
		}

		TrooperEntity partner = partner(level, prisoner);
		return partner == null || TrooperSight.unseen(level, partner);
	}

	/** The other escort of the same prisoner, if it is near. */
	private @Nullable TrooperEntity partner(ServerLevel level, @Nullable LivingEntity prisoner) {
		Vec3 around = prisoner != null ? prisoner.position() : trooper.position();

		List<TrooperEntity> partners = level.getEntitiesOfClass(TrooperEntity.class, new AABB(around, around).inflate(PARTNER_RANGE),
				t -> t != trooper && t.isEscort() && t.escortPrisonerOf() == trooper.escortPrisonerOf()
						&& Objects.equals(t.escortExit(), trooper.escortExit()));
		return partners.isEmpty() ? null : partners.get(0);
	}

	/** Removes both escorts and the prisoner (unless it is a player). */
	private void removeParty(ServerLevel level, @Nullable LivingEntity prisoner) {
		TrooperEntity partner = partner(level, prisoner);

		if (partner != null) {
			partner.discard();
		}

		if (prisoner != null && !(prisoner instanceof Player)) {
			prisoner.discard();
		}

		trooper.discard();
	}
}

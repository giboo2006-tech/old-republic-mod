package com.oldrepublic.boss.ulvaine;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.enemy.hymn.StormCall;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.duel.DuelistCombatGoal;
import com.oldrepublic.saber.duel.DuelistProfiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Darth Ulvaine's duel (spec MILESTONE_5.md 12.2): the M2 duellist with profile {@code ulvaine}
 * ({@code ulvaine_hymn}, 20% slower, in the third phase), and her three Force moves, each taken
 * when she is neutral:
 * <ul>
 * <li><b>The Hymn step</b>, every {@code stepInterval} ticks: a violet shimmer for {@code stepTell}
 * ticks (the tell), then she reappears {@code stepDistance} blocks behind her target, inside the
 * Heart and on firm floor (nearer, or to one side, when that spot is blocked).</li>
 * <li><b>Force Lightning</b>: a {@code lightningChannel}-tick channel at her target within
 * {@code lightningRange}, {@code lightningDamage} every {@code lightningInterval} ticks. A Push or a
 * stagger (the duellist's interrupt) or losing sight of the target ends it.</li>
 * <li><b>Force Push</b>: the palm drawn back for {@code pushTellTicks} (the acolyte's shove tell, by
 * id), then a cone that throws everything within {@code pushRange} back for {@code pushDamage}.</li>
 * </ul>
 * She fights only her session's participants, never while a phase changes or while she kneels.
 */
public final class UlvaineCombatGoal extends DuelistCombatGoal {
	private enum Move {
		NONE,
		STEP,
		LIGHTNING,
		PUSH
	}

	/** The Push's cone, degrees either side of her facing, and its lift. */
	private static final double PUSH_HALF_ANGLE = 35.0;
	private static final double PUSH_LIFT = 0.4;
	/** The first Lightning and Push of a fight wait this long. */
	private static final int OPENING_TICKS = 80;

	private final DarthUlvaine ulvaine;
	private Move move = Move.NONE;
	private int timer;
	private int pulse;
	private boolean armed;
	private long stepReadyAt;
	private long lightningReadyAt;
	private long pushReadyAt;

	UlvaineCombatGoal(DarthUlvaine ulvaine) {
		super(ulvaine, () -> DuelistProfiles.get(ulvaine.phase() >= UlvaineRules.PHASE_HYMN ? DuelistProfiles.ULVAINE_HYMN : DuelistProfiles.ULVAINE));
		this.ulvaine = ulvaine;
	}

	@Override
	protected boolean mayFight(LivingEntity target) {
		UlvaineSession session = ulvaine.session();
		return session != null && session.isFighting() && !session.isTransitioning() && !session.isForming() && !ulvaine.isKneeling()
				&& target instanceof ServerPlayer player && session.isParticipant(player.getUUID());
	}

	@Override
	public void start() {
		super.start();

		if (!armed) {
			// The fight's first moves come after an opening exchange of blades.
			long now = ulvaine.level().getGameTime();
			UlvaineBalance b = UlvaineBalance.get();
			armed = true;
			stepReadyAt = now + b.stepInterval;
			lightningReadyAt = now + OPENING_TICKS;
			pushReadyAt = now + OPENING_TICKS / 2;
		}
	}

	/** The Force move she is making, for the tests: none, or a step, lightning or Push in progress. */
	public String currentMove() {
		return move.name().toLowerCase(Locale.ROOT);
	}

	/** Makes the next Hymn step due now (the tests and {@code /oldrepublic ulvaine}). */
	public void readyStep() {
		stepReadyAt = 0;
		armed = true;
	}

	// ---------------------------------------------------------------- choosing a move

	@Override
	protected boolean tryForceMove(LivingEntity target, double distance) {
		if (!(ulvaine.level() instanceof ServerLevel level)) {
			return false;
		}

		UlvaineBalance b = UlvaineBalance.get();
		long now = level.getGameTime();
		boolean sees = ulvaine.hasLineOfSight(target);

		if (now >= stepReadyAt && distance > 2.5) {
			startStep(level, b, now);
			return true;
		}

		if (now >= lightningReadyAt && sees && distance >= 3.0 && distance <= b.lightningRange && ulvaine.getRandom().nextInt(12) == 0) {
			startLightning(b, now);
			return true;
		}

		if (now >= pushReadyAt && sees && distance >= 2.0 && distance <= b.pushRange - 0.5 && ulvaine.getRandom().nextInt(15) == 0) {
			startPush(b, now);
			return true;
		}

		return false;
	}

	@Override
	protected boolean tickForceMove(LivingEntity target, double distance) {
		if (!(ulvaine.level() instanceof ServerLevel level)) {
			return false;
		}

		UlvaineBalance b = UlvaineBalance.get();
		ulvaine.getNavigation().stop();
		ulvaine.getMoveControl().strafe(0f, 0f);
		ulvaine.getLookControl().setLookAt(target, 30f, 30f);

		boolean going = switch (move) {
			case STEP -> tickStep(level, target, b);
			case LIGHTNING -> tickLightning(level, target, distance, b);
			case PUSH -> tickPush(level, b);
			default -> false;
		};

		if (!going) {
			finish();
		}

		return going;
	}

	@Override
	protected void cancelForceMove() {
		finish();
	}

	private void finish() {
		move = Move.NONE;
		timer = 0;
		DarthUlvaine.Action action = ulvaine.action();

		// Only her own moves' poses: a phase change's raised arms belong to the session.
		if (action == DarthUlvaine.Action.STEP || action == DarthUlvaine.Action.LIGHTNING || action == DarthUlvaine.Action.PUSH) {
			ulvaine.setAction(DarthUlvaine.Action.NONE);
		}
	}

	// ---------------------------------------------------------------- the Hymn step

	private void startStep(ServerLevel level, UlvaineBalance b, long now) {
		move = Move.STEP;
		timer = Math.max(1, b.stepTell);
		stepReadyAt = now + b.stepInterval;
		ulvaine.setAction(DarthUlvaine.Action.STEP);
		EffectEvents.around(level, UlvaineEffects.EVENT, ulvaine.position(), null, ulvaine.getId(), b.stepTell, UlvaineEffects.STEP_TELL);
		level.playSound(null, ulvaine.getX(), ulvaine.getY(), ulvaine.getZ(), ModSounds.BOSS_ULVAINE_STEP_TELL, ulvaine.getSoundSource(), 1.2f, 1f);
	}

	private boolean tickStep(ServerLevel level, LivingEntity target, UlvaineBalance b) {
		if (--timer > 0) {
			return true;
		}

		Vec3 from = ulvaine.position();
		Vec3 to = stepSpot(level, target, b);

		if (to == null) {
			// Nowhere to land behind the target: the shimmer fades and she comes again sooner.
			stepReadyAt = level.getGameTime() + b.stepInterval / 3;
			return false;
		}

		ulvaine.teleportTo(to.x, to.y, to.z);
		Vec3 facing = target.position().subtract(to);
		float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90f;
		ulvaine.setYRot(yaw);
		ulvaine.yBodyRot = yaw;
		ulvaine.yHeadRot = yaw;
		Vec3 travel = to.subtract(from);
		EffectEvents.around(level, UlvaineEffects.EVENT, to, travel.lengthSqr() < 1.0e-4 ? null : travel.normalize(), ulvaine.getId(), 1f,
				UlvaineEffects.STEP);
		level.playSound(null, to.x, to.y, to.z, ModSounds.BOSS_ULVAINE_STEP, ulvaine.getSoundSource(), 1.3f, 1f);
		return false;
	}

	/**
	 * Where the step lands: {@code stepDistance} behind the target's back (by its facing), else
	 * nearer or swung to one side; always inside the Heart, on firm floor and with room to stand.
	 */
	private @Nullable Vec3 stepSpot(ServerLevel level, LivingEntity target, UlvaineBalance b) {
		UlvaineSession session = ulvaine.session();
		Vec3 look = target.getLookAngle().multiply(1, 0, 1);

		if (look.lengthSqr() < 1.0e-4) {
			look = target.position().subtract(ulvaine.position()).multiply(1, 0, 1);
		}

		if (look.lengthSqr() < 1.0e-4 || session == null) {
			return null;
		}

		Vec3 back = look.normalize().reverse();
		double[] distances = {b.stepDistance, b.stepDistance * 0.7, 3.0};
		double[] angles = {0, 35, -35, 70, -70};

		for (double distance : distances) {
			for (double angle : angles) {
				Vec3 dir = back.yRot((float) Math.toRadians(angle));
				Vec3 spot = target.position().add(dir.scale(distance));

				for (int dy : new int[] {0, 1, -1}) {
					BlockPos feet = BlockPos.containing(spot.x, target.getY() + dy, spot.z);
					Vec3 at = new Vec3(spot.x, feet.getY(), spot.z);

					if (session.layout().inside(at) && fits(level, at)) {
						return at;
					}
				}
			}
		}

		return null;
	}

	private boolean fits(ServerLevel level, Vec3 at) {
		BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
		AABB box = ulvaine.getBoundingBox().move(at.subtract(ulvaine.position()));
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.noCollision(ulvaine, box);
	}

	// ---------------------------------------------------------------- Force Lightning

	private void startLightning(UlvaineBalance b, long now) {
		move = Move.LIGHTNING;
		timer = Math.max(1, b.lightningChannel);
		pulse = 0;
		lightningReadyAt = now + b.lightningCooldown;
		ulvaine.setAction(DarthUlvaine.Action.LIGHTNING);
	}

	private boolean tickLightning(ServerLevel level, LivingEntity target, double distance, UlvaineBalance b) {
		if (!target.isAlive() || distance > b.lightningRange + 2 || !ulvaine.hasLineOfSight(target)) {
			return false;
		}

		if (pulse++ % Math.max(1, b.lightningInterval) == 0) {
			Vec3 hand = ulvaine.handPoint();
			Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
			Vec3 dir = chest.subtract(hand);
			EffectEvents.around(level, UlvaineEffects.EVENT, chest, dir.lengthSqr() < 1.0e-4 ? null : dir.normalize(), ulvaine.getId(), b.lightningChannel,
					UlvaineEffects.LIGHTNING);
			target.hurtServer(level, level.damageSources().source(ForceDamage.LIGHTNING, ulvaine, ulvaine), b.lightningDamage);
		}

		return --timer > 0;
	}

	// ---------------------------------------------------------------- Force Push

	private void startPush(UlvaineBalance b, long now) {
		move = Move.PUSH;
		timer = Math.max(1, b.pushTellTicks);
		pushReadyAt = now + b.pushCooldown;
		ulvaine.setAction(DarthUlvaine.Action.PUSH);
		EffectEvents.toTracking(ulvaine, EnemyEffects.ACOLYTE_SHOVE, ulvaine.handPoint(), ulvaine.getLookAngle(), ulvaine.getId(), b.pushTellTicks,
				EnemyEffects.SHOVE_WINDUP);
	}

	private boolean tickPush(ServerLevel level, UlvaineBalance b) {
		if (--timer > 0) {
			return true;
		}

		Vec3 origin = ulvaine.getEyePosition();
		Vec3 look = ulvaine.getLookAngle();
		double cos = Math.cos(Math.toRadians(PUSH_HALF_ANGLE));
		AABB box = ulvaine.getBoundingBox().inflate(b.pushRange);

		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != ulvaine && e.isAlive() && !StormCall.ignored(e)
				&& !Allegiances.friendly(ulvaine, e))) {
			Vec3 to = hit.getBoundingBox().getCenter().subtract(origin);
			double distance = to.length();

			if (distance > b.pushRange || distance < 1.0e-3 || to.normalize().dot(look) < cos || !ulvaine.hasLineOfSight(hit)) {
				continue;
			}

			double resist = Mth.clamp(hit.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
			Vec3 push = to.horizontal().normalize().scale(b.pushStrength * (1.0 - 0.6 * resist));
			hit.setDeltaMovement(hit.getDeltaMovement().scale(0.2).add(push.x, PUSH_LIFT, push.z));
			hit.hurtMarked = true;
			hit.hurtServer(level, ulvaine.damageSources().indirectMagic(ulvaine, ulvaine), b.pushDamage);
		}

		EffectEvents.toTracking(ulvaine, EnemyEffects.ACOLYTE_SHOVE, ulvaine.handPoint(), look, ulvaine.getId(), 1f, EnemyEffects.SHOVE_RELEASE);
		return false;
	}
}

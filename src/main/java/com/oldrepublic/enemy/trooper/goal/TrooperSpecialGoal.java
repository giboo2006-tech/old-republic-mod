package com.oldrepublic.enemy.trooper.goal;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.trooper.BarkCategory;
import com.oldrepublic.enemy.trooper.Barks;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.enemy.trooper.Reinforcements;
import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperEffects;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The role specials (spec 7.2, 7.3), each behind a readable tell that the counterplay can break:
 * <ul>
 * <li><b>Frag grenade</b> (grenadier): every {@code grenadeInterval} at a target
 * {@code grenadeRange} away; {@code grenadeTellTicks} with the arm drawn back
 * ({@code grenade_tell}), then {@link FragGrenade#throwAt}.</li>
 * <li><b>Reinforcements</b> (Imperial sergeant): once per encounter after a squad member fell;
 * {@code reinforceTellTicks} with the flare pistol raised, then the flare ({@code flare_call}) and
 * {@code reinforceCount} riflemen {@code reinforceDelayTicks} later ({@link Reinforcements}).</li>
 * <li><b>Rally</b> (Republic sergeant): once per {@code rallyCooldown} while at least
 * {@code rallyMinAlive} of the squad stand; {@code rallyTellTicks} with a raised fist
 * ({@code trooper_rally}), then Resistance I and a {@code rallyFireRate} shorter burst gap for
 * {@code rallyTicks} to the squad within {@code rallyRadius}.</li>
 * <li><b>Kolto</b> (medic): an ally at or below {@code medicThreshold} within {@code medicRange};
 * the medic walks over, kneels for {@code medicChannelTicks} ({@code kolto_heal}) and heals it by
 * {@code medicHeal}; cooldown {@code medicCooldown}.</li>
 * </ul>
 * A stagger, Push, Pull, Disarming Pull or shock during a tell cancels it, and so does a hit of
 * {@code interruptDamage} or more during the medic's channel; a Disarming Pull also cancels the
 * grenade and the flare. The kill cancels anything. A cancelled special may start again after a
 * short pause. {@link #request} runs a special now (the debug command), skipping cooldowns and
 * combat conditions but never the tell.
 */
public final class TrooperSpecialGoal extends Goal {
	/** The specials. */
	public enum Kind {
		GRENADE,
		FLARE,
		RALLY,
		HEAL
	}

	/** The medic channels from this close. */
	private static final double HEAL_REACH = 2.5;
	/** The channel breaks when the patient is farther than this. */
	private static final double HEAL_BREAK = 4.5;
	/** The medic gives up walking to the patient after this long. */
	private static final int APPROACH_TIMEOUT = 100;
	private static final int REPATH_TICKS = 10;
	/** Ticks before a cancelled special may start again. */
	private static final int RETRY_AFTER_CANCEL = 100;
	/** Ticks before the medic tries again after a broken channel. */
	private static final int HEAL_RETRY = 40;
	/** The flare leaves the pistol this high above the sergeant's eyes. */
	private static final double FLARE_RISE = 0.6;

	private final TrooperEntity trooper;
	private @Nullable Kind requested;
	private @Nullable Vec3 requestedAt;
	private @Nullable Kind running;
	private @Nullable Kind ready;
	/** The special about to start was requested (no conditions), not chosen by the AI. */
	private boolean fromRequest;
	/** Tell or channel ticks left; only counts while {@link #telling}. */
	private int timer;
	/** The tell or channel is under way (false while the medic still walks to the patient). */
	private boolean telling;
	private int approachTicks;
	private int repath;
	private @Nullable LivingEntity patient;
	private @Nullable Vec3 grenadeAt;
	private long nextGrenade;
	private long nextRally;
	private long nextHeal;
	private long nextFlare;

	public TrooperSpecialGoal(TrooperEntity trooper) {
		this.trooper = trooper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Runs {@code kind} as soon as the goal can start (the debug command and tests): no cooldown or
	 * combat condition, but the full tell. {@code at} is the grenade's target point.
	 */
	public void request(Kind kind, @Nullable Vec3 at) {
		requested = kind;
		requestedAt = at;
	}

	/** The special under way, or null. */
	public @Nullable Kind running() {
		return running;
	}

	@Override
	public boolean canUse() {
		if (!trooper.canFight() || trooper.isRetreating()) {
			return false;
		}

		if (requested != null) {
			ready = requested;
			fromRequest = true;
			return true;
		}

		fromRequest = false;

		long now = trooper.level().getGameTime();
		ready = switch (trooper.role()) {
			case GRENADIER -> grenadeReady(now) ? Kind.GRENADE : null;
			case SERGEANT -> trooper.isRepublic() ? (rallyReady(now) ? Kind.RALLY : null) : (flareReady(now) ? Kind.FLARE : null);
			case MEDIC -> healReady(now) ? Kind.HEAL : null;
			default -> null;
		};
		return ready != null;
	}

	@Override
	public boolean canContinueToUse() {
		return running != null && trooper.canFight();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		Kind kind = ready;
		Vec3 at = fromRequest ? requestedAt : null;
		boolean forced = fromRequest;
		fromRequest = false;
		requested = null;
		requestedAt = null;
		ready = null;
		running = kind;

		if (kind == null || !(trooper.level() instanceof ServerLevel)) {
			running = null;
			return;
		}

		TrooperBalance b = TrooperBalance.get();
		trooper.getNavigation().stop();

		switch (kind) {
			case GRENADE -> {
				LivingEntity target = trooper.getTarget();
				grenadeAt = at != null ? at : target != null ? target.position() : ahead(8);
				Vec3 toward = grenadeAt.subtract(trooper.position());
				startTell(TrooperAction.GRENADE_TELL, b.grenadeTellTicks);
				EffectEvents.toTracking(trooper, TrooperEffects.GRENADE_TELL, trooper.muzzlePosition(),
						toward.lengthSqr() > 1.0e-4 ? toward.normalize() : trooper.getLookAngle(), trooper.getId(), b.grenadeTellTicks, 0);
				Barks.say(trooper, BarkCategory.GRENADE);
			}
			case FLARE -> {
				// The pose (the flare pistol raised) is the tell; the flare itself is sent when it fires.
				startTell(TrooperAction.FLARE_TELL, b.reinforceTellTicks);
				Barks.say(trooper, BarkCategory.REINFORCE);
			}
			case RALLY -> {
				startTell(TrooperAction.RALLY_TELL, b.rallyTellTicks);
				EffectEvents.toTracking(trooper, TrooperEffects.TROOPER_RALLY, trooper.position(), null, trooper.getId(), b.rallyTellTicks, 0);
				Barks.say(trooper, BarkCategory.RALLY);
			}
			case HEAL -> {
				patient = forced || patient == null ? mostHurtAlly(b, forced) : patient;

				if (patient == null) {
					running = null;
					return;
				}

				telling = false;
				approachTicks = 0;
				repath = 0;
			}
		}
	}

	@Override
	public void stop() {
		if (running != null) {
			// Pre-empted (cover, yield, death): the special is lost, as if cancelled.
			cancel(trooper.level().getGameTime());
		}

		patient = null;
		grenadeAt = null;
	}

	@Override
	public void tick() {
		Kind kind = running;

		if (kind == null || !(trooper.level() instanceof ServerLevel level)) {
			return;
		}

		long now = level.getGameTime();
		TrooperBalance b = TrooperBalance.get();
		boolean interrupted = trooper.consumeInterrupt();

		if (kind == Kind.HEAL && !telling) {
			approach(now, b);
			return;
		}

		trooper.getNavigation().stop();
		look(kind);

		if (interrupted || broken(kind)) {
			cancel(now);
		} else if (--timer <= 0) {
			complete(level, kind, now, b);
		}
	}

	// ---------------------------------------------------------------- readiness

	private boolean grenadeReady(long now) {
		LivingEntity target = trooper.getTarget();

		if (now < nextGrenade || target == null || ForceStatus.isDisarmed(trooper) || trooper.isHoldingFire()) {
			return false;
		}

		TrooperBalance b = TrooperBalance.get();
		double distance = trooper.distanceTo(target);
		return distance >= b.grenadeRange[0] && distance <= b.grenadeRange[b.grenadeRange.length - 1]
				&& trooper.getSensing().hasLineOfSight(target);
	}

	private boolean rallyReady(long now) {
		return now >= nextRally && trooper.getTarget() != null && Squads.fighters(trooper).size() >= TrooperBalance.get().rallyMinAlive;
	}

	private boolean flareReady(long now) {
		return now >= nextFlare && trooper.getTarget() != null && !ForceStatus.isDisarmed(trooper) && Squads.flareReady(trooper);
	}

	private boolean healReady(long now) {
		if (now < nextHeal) {
			return false;
		}

		patient = mostHurtAlly(TrooperBalance.get(), false);
		return patient != null;
	}

	/**
	 * The squad member within {@code medicRange} with the lowest health fraction at or below
	 * {@code medicThreshold}; with {@code anyHurt} (a requested heal), any hurt ally will do, and the
	 * medic itself when nobody else is hurt.
	 */
	private @Nullable LivingEntity mostHurtAlly(TrooperBalance b, boolean anyHurt) {
		LivingEntity best = null;
		float bestFraction = anyHurt ? 1f : b.medicThreshold + 1.0e-4f;
		double range = (double) b.medicRange * b.medicRange;

		for (TrooperEntity member : Squads.fighters(trooper)) {
			if (member == trooper || member.distanceToSqr(trooper) > range) {
				continue;
			}

			float fraction = member.getHealth() / member.getMaxHealth();

			if (fraction < bestFraction) {
				best = member;
				bestFraction = fraction;
			}
		}

		if (best == null && anyHurt && trooper.getHealth() < trooper.getMaxHealth()) {
			return trooper;
		}

		return best;
	}

	// ---------------------------------------------------------------- tells

	private void startTell(TrooperAction action, int ticks) {
		telling = true;
		timer = Math.max(1, ticks);
		trooper.setAction(action);
	}

	/** The medic walks to the patient, then kneels and starts the channel. */
	private void approach(long now, TrooperBalance b) {
		LivingEntity target = patient;

		if (target == null || !target.isAlive() || target.isRemoved() || ++approachTicks > APPROACH_TIMEOUT) {
			cancel(now);
			return;
		}

		trooper.getLookControl().setLookAt(target, 30f, 30f);

		if (trooper.distanceTo(target) > HEAL_REACH) {
			if (--repath <= 0) {
				repath = REPATH_TICKS;
				trooper.getNavigation().moveTo(target, 1.1);
			}

			return;
		}

		trooper.getNavigation().stop();
		startTell(TrooperAction.HEALING, b.medicChannelTicks);
		EffectEvents.toTracking(target, TrooperEffects.KOLTO_HEAL, target.position().add(0, target.getBbHeight() * 0.6, 0), null, target.getId(),
				b.medicChannelTicks, 0);
	}

	private void look(Kind kind) {
		LivingEntity target = trooper.getTarget();

		if (kind == Kind.HEAL && patient != null) {
			trooper.getLookControl().setLookAt(patient, 30f, 30f);
		} else if (kind == Kind.GRENADE && grenadeAt != null) {
			trooper.getLookControl().setLookAt(grenadeAt);
		} else if (kind == Kind.FLARE) {
			trooper.getLookControl().setLookAt(trooper.getEyePosition().add(trooper.getLookAngle().multiply(1, 0, 1)).add(0, 3, 0));
		} else if (target != null) {
			trooper.getLookControl().setLookAt(target, 30f, 30f);
		}
	}

	/** Whether the special can no longer go on (other than an interrupt). */
	private boolean broken(Kind kind) {
		return switch (kind) {
			case GRENADE, FLARE -> ForceStatus.isDisarmed(trooper);
			case RALLY -> false;
			case HEAL -> patient == null || !patient.isAlive() || patient.isRemoved() || trooper.distanceTo(patient) > HEAL_BREAK;
		};
	}

	private void complete(ServerLevel level, Kind kind, long now, TrooperBalance b) {
		switch (kind) {
			case GRENADE -> {
				LivingEntity target = trooper.getTarget();
				Vec3 aim = target != null && target.isAlive() && grenadeAt != null && target.position().distanceToSqr(grenadeAt) < 64
						? target.position() : grenadeAt;
				FragGrenade.throwAt(level, trooper, aim != null ? aim : ahead(8));
				nextGrenade = now + b.grenadeInterval;
			}
			case FLARE -> {
				EffectEvents.around(level, TrooperEffects.FLARE_CALL, trooper.getEyePosition().add(0, FLARE_RISE, 0), new Vec3(0, 1, 0), trooper.getId(), 20, 0);
				Squads.markFlareUsed(trooper);
				Reinforcements.schedule(trooper, trooper.getTarget(), now + b.reinforceDelayTicks);
				nextFlare = now + b.reinforceDelayTicks;
			}
			case RALLY -> {
				double range = (double) b.rallyRadius * b.rallyRadius;

				for (TrooperEntity member : Squads.fighters(trooper)) {
					if (member.distanceToSqr(trooper) <= range) {
						member.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, b.rallyTicks, 0));
						member.setRallied(now + b.rallyTicks);
					}
				}

				nextRally = now + b.rallyCooldown;
			}
			case HEAL -> {
				if (patient != null) {
					patient.heal(b.medicHeal);
				}

				nextHeal = now + b.medicCooldown;
			}
		}

		finish();
	}

	/** The special was broken off: the tell ends without effect, and it may come again a little later. */
	private void cancel(long now) {
		Kind kind = running;

		if (kind != null) {
			switch (kind) {
				case GRENADE -> nextGrenade = Math.max(nextGrenade, now + RETRY_AFTER_CANCEL);
				case FLARE -> nextFlare = Math.max(nextFlare, now + RETRY_AFTER_CANCEL);
				case RALLY -> nextRally = Math.max(nextRally, now + RETRY_AFTER_CANCEL);
				case HEAL -> nextHeal = Math.max(nextHeal, now + HEAL_RETRY);
			}
		}

		finish();
	}

	private void finish() {
		TrooperAction action = trooper.action();

		if (action == TrooperAction.GRENADE_TELL || action == TrooperAction.FLARE_TELL || action == TrooperAction.RALLY_TELL
				|| action == TrooperAction.HEALING) {
			trooper.setAction(TrooperAction.IDLE);
		}

		running = null;
		telling = false;
		patient = null;
		grenadeAt = null;
	}

	private Vec3 ahead(double distance) {
		Vec3 look = trooper.getLookAngle().multiply(1, 0, 1);
		return trooper.position().add(look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, distance) : look.normalize().scale(distance));
	}
}

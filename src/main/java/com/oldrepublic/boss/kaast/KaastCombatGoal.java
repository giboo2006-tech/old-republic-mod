package com.oldrepublic.boss.kaast;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.mandalorian.HunterBalance;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.enemy.mandalorian.HunterEffects;
import com.oldrepublic.enemy.mandalorian.HunterNet;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Torvin Kaast's fight (spec MILESTONE_4.md 10.5), every attack after its tell:
 * <ul>
 * <li>Phase 1, the Hunt: he holds the four nests, hopping between them (a {@code hopTellTicks} tell)
 * every {@code hopInterval}; rifle bursts of {@code burst} from the nest; a wrist rocket every
 * {@code rocketInterval} (the red dot); the flamer for anyone who climbs up to him. Knocked off a
 * nest he falls, is staggered, then hops back.</li>
 * <li>Phase 2, the Trap: down into the pit; he walks to each mine spot and kneels
 * {@code mineKneelTicks} to set a mine; a net every {@code netInterval} ({@code netTellTicks} tell);
 * the flamer up close; rifle bursts between.</li>
 * <li>Phase 3, the Last Stand: the vibro-axe. Three-blow combos ({@code comboDamage}, the third after
 * a {@code comboWindup} wind-up), a shoulder charge ({@code chargeTell}), one jet slam
 * ({@code slamTellTicks}: he rises and comes down where the player stood), the rifle at range.</li>
 * </ul>
 * A stagger (one of his listed interrupts) drops whatever he was doing.
 */
final class KaastCombatGoal extends Goal {
	private enum Action {
		NONE,
		HOP_TELL,
		HOP,
		AIM,
		BURST,
		GADGET_TELL,
		WALK_TO_MINE,
		KNEEL,
		COMBO,
		CHARGE_TELL,
		CHARGE,
		SLAM_RISE,
		SLAM_FALL
	}

	private static final int AIM_TICKS = 10;
	private static final int BURST_GAP_TICKS = 5;
	private static final float BOLT_SPEED = 2.2f;
	private static final int BOLT_LIFETIME = 50;
	private static final float BOLT_INACCURACY = 1.5f;
	private static final double COMBO_REACH = 3.2;
	private static final double MINE_REACH = 1.6;
	private static final int MINE_WALK_TICKS = 120;
	private static final int MAX_CHARGE_TICKS = 20;
	private static final int MAX_FALL_TICKS = 20;
	private static final double FALL_SPEED = 1.1;
	/** In the pit he stays within this of its centre. */
	private static final double PIT_RADIUS = 7;
	private static final double NET_RANGE = 16;

	private final TorvinKaast mob;
	private Action action = Action.NONE;
	private int timer;
	private int gadget = MandalorianHunter.GADGET_NONE;
	private int shotsLeft;
	private int sinceHop;
	private int sinceRocket;
	private int sinceNet;
	private int sinceBurst;
	private int sinceFlame;
	private int comboBlow;
	private int comboCooldown;
	private int chargeCooldown;
	private int repath;
	private int targetNest = -1;
	private int mineIndex;
	private int phaseSeen = 1;
	private boolean slamDone;
	private @Nullable Vec3 slamAt;
	private @Nullable Vec3 slamFrom;
	private @Nullable Vec3 chargeDir;
	private @Nullable Vec3 chargeFrom;

	KaastCombatGoal(TorvinKaast mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return mob.session() != null && !mob.isKnelt() && target != null && target.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse() || action == Action.HOP || action == Action.SLAM_FALL;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		mob.setAggressive(true);
		KaastBalance b = KaastBalance.get();
		sinceRocket = b.rocketInterval / 2;
		sinceNet = b.netInterval / 2;
		sinceBurst = b.burstCooldown;
		sinceFlame = HunterBalance.get().flamerCooldownTicks;
	}

	@Override
	public void stop() {
		mob.setAggressive(false);
		cancel();
		mob.getNavigation().stop();
	}

	/** Drops the action in progress and its tell. */
	private void cancel() {
		if (action == Action.SLAM_RISE || action == Action.SLAM_FALL) {
			mob.setNoGravity(false);
		}

		action = Action.NONE;
		gadget = MandalorianHunter.GADGET_NONE;
		mob.aim(false);
		mob.showGadget(MandalorianHunter.GADGET_NONE);
		mob.setKneeling(false);
		mob.setWindingUp(false);
		mob.setStriking(-1);
	}

	@Override
	public void tick() {
		KaastSession session = mob.session();
		LivingEntity target = mob.getTarget();

		if (session == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}

		KaastBalance b = KaastBalance.get();
		sinceHop++;
		sinceRocket++;
		sinceNet++;
		sinceBurst++;
		sinceFlame++;
		comboCooldown--;
		chargeCooldown--;

		if (mob.phase() != phaseSeen) {
			phaseSeen = mob.phase();
			cancel();
			onPhase(level, session);
		}

		if (mob.isStaggered()) {
			if (action != Action.NONE) {
				cancel();
			}

			mob.getNavigation().stop();
			return;
		}

		if (action == Action.HOP) {
			tickHop();
			return;
		}

		if (action == Action.SLAM_FALL) {
			// Once he comes down he lands, target or not.
			tickSlamFall(level, b);
			return;
		}

		if (session.isTransitioning() || target == null) {
			mob.getNavigation().stop();
			return;
		}

		mob.getLookControl().setLookAt(target, 30f, 30f);

		if (mob.isFlaming()) {
			mob.getNavigation().stop();
			return;
		}

		switch (action) {
			case NONE -> choose(level, session, target, b);
			case HOP_TELL -> tickHopTell(level, session, b);
			case AIM -> {
				if (--timer <= 0) {
					action = Action.BURST;
					shotsLeft = Math.max(1, b.burst);
					timer = 0;
				}
			}
			case BURST -> tickBurst(level, session, target, b);
			case GADGET_TELL -> tickGadgetTell(level, target, b);
			case WALK_TO_MINE -> tickWalkToMine(session);
			case KNEEL -> tickKneel(level, session, b);
			case COMBO -> tickCombo(level, target, b);
			case CHARGE_TELL -> tickChargeTell(target, b);
			case CHARGE -> tickCharge(level, b);
			case SLAM_RISE -> tickSlamRise(b);
			default -> action = Action.NONE;
		}
	}

	/** A new phase: phase 2 drops him into the pit at once (the transition covers the hop). */
	private void onPhase(ServerLevel level, KaastSession session) {
		if (mob.phase() == 2) {
			BlockPos heart = session.layout().heart();
			mob.startHop(level, Vec3.atBottomCenterOf(heart), KaastBalance.get().hopFlightTicks + 6, 4.0);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.BOSS_KAAST_HOP, mob.getSoundSource(), 1.3f, 1f);
			targetNest = -1;
			action = Action.HOP;
		}
	}

	// ---------------------------------------------------------------- choosing

	private void choose(ServerLevel level, KaastSession session, LivingEntity target, KaastBalance b) {
		double distance = mob.distanceTo(target);
		boolean sees = mob.getSensing().hasLineOfSight(target);
		HunterBalance h = HunterBalance.get();

		switch (mob.phase()) {
			case 1 -> {
				if (mob.nest() < 0 || sinceHop >= b.hopInterval) {
					startHopTell(level, session, b);
				} else if (distance < h.flamerRange && sinceFlame >= h.flamerCooldownTicks) {
					startGadget(target, MandalorianHunter.GADGET_FLAMER, h.flamerTellTicks, HunterEffects.TELL_FLAMER);
				} else if (sees && sinceRocket >= b.rocketInterval && distance >= h.rocketMinRange && distance <= h.rocketMaxRange + 4) {
					startGadget(target, MandalorianHunter.GADGET_ROCKET, b.rocketTellTicks, HunterEffects.TELL_ROCKET);
				} else if (sees && sinceBurst >= b.burstCooldown) {
					startAim();
				} else {
					mob.getNavigation().stop();
				}
			}
			case 2 -> {
				if (session.minesSet() < session.minesWanted() && !session.layout().mines().isEmpty()) {
					action = Action.WALK_TO_MINE;
					timer = MINE_WALK_TICKS;
					repath = 0;
				} else if (distance < h.flamerRange && sinceFlame >= h.flamerCooldownTicks) {
					startGadget(target, MandalorianHunter.GADGET_FLAMER, h.flamerTellTicks, HunterEffects.TELL_FLAMER);
				} else if (sees && sinceNet >= b.netInterval && distance <= NET_RANGE && target instanceof Player) {
					startGadget(target, MandalorianHunter.GADGET_NET, b.netTellTicks, HunterEffects.TELL_NET);
				} else if (sees && sinceBurst >= b.burstCooldown) {
					startAim();
				} else {
					holdPit(session, target);
				}
			}
			default -> {
				float fraction = mob.getHealth() / Math.max(1f, mob.getMaxHealth());

				if (!slamDone && fraction <= b.slamAt) {
					startSlam(level, target, b);
				} else if (distance <= COMBO_REACH && comboCooldown <= 0) {
					action = Action.COMBO;
					comboBlow = 0;
					timer = 4;
				} else if (sees && chargeCooldown <= 0 && distance >= 5 && distance <= b.chargeRange) {
					startChargeTell(target, b);
				} else if (sees && distance > 12 && sinceBurst >= b.burstCooldown) {
					startAim();
				} else if (--repath <= 0) {
					repath = 8;
					mob.getNavigation().moveTo(target, 1.3);
				}
			}
		}
	}

	// ---------------------------------------------------------------- phase 1: nests

	/** The nest to hop to: the farthest from the target that is not the current one. */
	private int nextNest(KaastSession session) {
		List<BlockPos> nests = session.layout().nests();
		LivingEntity target = mob.getTarget();
		Vec3 from = target != null ? target.position() : mob.position();
		int best = -1;
		double bestDistance = -1;

		for (int i = 0; i < nests.size(); i++) {
			if (i == mob.nest()) {
				continue;
			}

			double distance = Vec3.atBottomCenterOf(nests.get(i)).distanceToSqr(from);

			if (distance > bestDistance) {
				best = i;
				bestDistance = distance;
			}
		}

		return best;
	}

	private void startHopTell(ServerLevel level, KaastSession session, KaastBalance b) {
		targetNest = nextNest(session);

		if (targetNest < 0) {
			sinceHop = 0;
			return;
		}

		action = Action.HOP_TELL;
		timer = Math.max(1, b.hopTellTicks);
		mob.getNavigation().stop();
		mob.showGadget(MandalorianHunter.GADGET_JET);
		EffectEvents.toTracking(mob, HunterEffects.TELL, mob.position().add(0, 1.2, 0), null, mob.getId(), timer, HunterEffects.TELL_JET);
	}

	private void tickHopTell(ServerLevel level, KaastSession session, KaastBalance b) {
		mob.getNavigation().stop();

		if (--timer > 0) {
			return;
		}

		mob.showGadget(MandalorianHunter.GADGET_NONE);
		BlockPos nest = session.layout().nests().get(targetNest);
		mob.setNest(-1);
		mob.startHop(level, Vec3.atBottomCenterOf(nest), b.hopFlightTicks, 3.0);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.BOSS_KAAST_HOP, mob.getSoundSource(), 1.3f, 1f);
		action = Action.HOP;
		sinceHop = 0;
	}

	/** The hop runs on the hunter's own motion; when it lands he is on the nest it aimed for (or in the pit). */
	private void tickHop() {
		if (mob.isHopping()) {
			return;
		}

		KaastSession session = mob.session();

		if (targetNest >= 0 && session != null && targetNest < session.layout().nests().size()
				&& Vec3.atBottomCenterOf(session.layout().nests().get(targetNest)).distanceToSqr(mob.position()) <= 4.0) {
			mob.setNest(targetNest);
		}

		action = Action.NONE;
	}

	// ---------------------------------------------------------------- rifle and gadgets

	private void startAim() {
		action = Action.AIM;
		timer = AIM_TICKS;
		mob.aim(true);
		mob.getNavigation().stop();
	}

	private void tickBurst(ServerLevel level, KaastSession session, LivingEntity target, KaastBalance b) {
		if (--timer > 0) {
			return;
		}

		mob.aim(false);
		mob.fireBolt(level, target, KaastRules.rifleDamage(session.marked(target), b), BOLT_SPEED, BOLT_LIFETIME, BOLT_INACCURACY, 0.75f, 1.0f,
				ModSounds.BOSS_KAAST_RIFLE);

		if (--shotsLeft > 0) {
			timer = BURST_GAP_TICKS;
		} else {
			action = Action.NONE;
			sinceBurst = 0;
		}
	}

	/** A gadget's tell: the pose and the {@code hunter_tell} event (the rocket's red dot sits on the target). */
	private void startGadget(LivingEntity target, int which, int ticks, int variant) {
		action = Action.GADGET_TELL;
		gadget = which;
		timer = Math.max(1, ticks);
		mob.showGadget(which);
		mob.getNavigation().stop();
		int entity = variant == HunterEffects.TELL_ROCKET ? target.getId() : mob.getId();
		EffectEvents.toTracking(mob, HunterEffects.TELL, mob.wristPosition(), null, entity, timer, variant);
	}

	private void tickGadgetTell(ServerLevel level, LivingEntity target, KaastBalance b) {
		mob.getNavigation().stop();

		if (--timer > 0) {
			return;
		}

		int which = gadget;
		gadget = MandalorianHunter.GADGET_NONE;
		mob.showGadget(MandalorianHunter.GADGET_NONE);
		action = Action.NONE;
		HunterBalance h = HunterBalance.get();

		switch (which) {
			case MandalorianHunter.GADGET_FLAMER -> {
				sinceFlame = 0;
				mob.startFlame(level, h.flamerTicks, h.flamerDamage, h.flamerDamageInterval, h.flamerConeLength, h.flamerConeDegrees, h.flamerFireSeconds);
			}
			case MandalorianHunter.GADGET_ROCKET -> {
				sinceRocket = 0;
				mob.fireRocket(level, target, h.rocketSpeed, b.rocketDamage, h.rocketRadius, h.rocketLifetimeTicks);
			}
			case MandalorianHunter.GADGET_NET -> {
				sinceNet = 0;
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.BOSS_KAAST_NET_THROW, mob.getSoundSource(), 1.2f, 1f);
				HunterNet.throwAt(level, mob, target, b.netRootTicks, b.netSaberHits);
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- phase 2: the pit and the mines

	private void holdPit(KaastSession session, LivingEntity target) {
		Vec3 heart = Vec3.atBottomCenterOf(session.layout().heart());

		if (--repath > 0) {
			return;
		}

		repath = 15;

		if (mob.position().distanceToSqr(heart) > PIT_RADIUS * PIT_RADIUS) {
			mob.getNavigation().moveTo(heart.x, heart.y, heart.z, 1.0);
			return;
		}

		Vec3 away = mob.position().subtract(target.position()).multiply(1, 0, 1);
		Vec3 side = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-away.z, 0, away.x).normalize().scale(4);
		Vec3 spot = mob.position().add(mob.getRandom().nextBoolean() ? side : side.reverse());

		if (spot.distanceToSqr(heart) <= PIT_RADIUS * PIT_RADIUS) {
			mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
		}
	}

	private BlockPos mineSpot(KaastSession session) {
		List<BlockPos> spots = session.layout().mines();
		return spots.get(mineIndex % spots.size());
	}

	private void tickWalkToMine(KaastSession session) {
		BlockPos spot = mineSpot(session);
		Vec3 at = Vec3.atBottomCenterOf(spot);

		if (mob.position().distanceToSqr(at) <= MINE_REACH * MINE_REACH || --timer <= 0) {
			action = Action.KNEEL;
			timer = Math.max(1, KaastBalance.get().mineKneelTicks);
			mob.getNavigation().stop();
			mob.setKneeling(true);
			return;
		}

		if (--repath <= 0) {
			repath = 10;
			mob.getNavigation().moveTo(at.x, at.y, at.z, 1.2);
		}
	}

	/** The kneel: a heavy blow meanwhile staggers him and the mine is not set (he comes back for it). */
	private void tickKneel(ServerLevel level, KaastSession session, KaastBalance b) {
		mob.getNavigation().stop();

		if (--timer > 0) {
			return;
		}

		mob.setKneeling(false);
		action = Action.NONE;
		CampMine mine = CampMine.place(level, mob, mob.blockPosition(), b);

		if (mine != null) {
			session.mineSet(mine);
		}

		mineIndex++;
	}

	// ---------------------------------------------------------------- phase 3: the axe

	private void tickCombo(ServerLevel level, LivingEntity target, KaastBalance b) {
		if (mob.distanceTo(target) > COMBO_REACH && --repath <= 0) {
			repath = 5;
			mob.getNavigation().moveTo(target, 1.2);
		}

		if (--timer > 0) {
			return;
		}

		strike(level, target, comboBlow, b);

		if (mob.isStaggered()) {
			// The parry broke the combo (TorvinKaast#onParried).
			return;
		}

		comboBlow++;

		if (comboBlow == 2) {
			// The third blow's wind-up: the tell.
			timer = Math.max(1, b.comboWindup);
			mob.setWindingUp(true);
			EffectEvents.toTracking(mob, HunterEffects.TELL, mob.getEyePosition(), mob.getLookAngle(), mob.getId(), timer, HunterEffects.TELL_AXE);
		} else if (comboBlow > 2) {
			mob.setWindingUp(false);
			action = Action.NONE;
			comboCooldown = b.comboCooldown;
		} else {
			timer = Math.max(1, b.comboGapTicks);
		}
	}

	/** One axe blow, through the saber pipeline so a guarding player can parry it. */
	private void strike(ServerLevel level, LivingEntity target, int blow, KaastBalance b) {
		mob.setWindingUp(false);
		mob.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.BOSS_KAAST_COMBO, mob.getSoundSource(), 1f, 0.9f + blow * 0.1f);
		Vec3 to = target.position().subtract(mob.position()).multiply(1, 0, 1);
		boolean inFront = to.lengthSqr() < 1.0e-4 || to.normalize().dot(mob.getLookAngle().multiply(1, 0, 1).normalize()) > 0.3;

		if (mob.distanceTo(target) > COMBO_REACH || !inFront) {
			return;
		}

		mob.setStriking(blow);
		SaberCombat.strike(target, level, mob.damageSources().mobAttack(mob), b.comboDamage(blow), blow >= 2 ? SaberCombat.Strike.npcHeavy(1.5f)
				: SaberCombat.Strike.NPC_LIGHT);
		mob.setStriking(-1);
	}

	private void startChargeTell(LivingEntity target, KaastBalance b) {
		action = Action.CHARGE_TELL;
		timer = Math.max(1, b.chargeTell);
		mob.getNavigation().stop();
		Vec3 dir = target.position().subtract(mob.position()).multiply(1, 0, 1);
		EffectEvents.toTracking(mob, HunterEffects.TELL, mob.position().add(0, 1.0, 0), dir.lengthSqr() < 1.0e-4 ? null : dir.normalize(), mob.getId(), timer,
				HunterEffects.TELL_CHARGE);
	}

	private void tickChargeTell(LivingEntity target, KaastBalance b) {
		mob.getNavigation().stop();
		Vec3 dir = target.position().subtract(mob.position()).multiply(1, 0, 1);
		face(dir);

		if (--timer > 0) {
			return;
		}

		if (dir.lengthSqr() < 1.0e-4) {
			action = Action.NONE;
			return;
		}

		action = Action.CHARGE;
		timer = 0;
		chargeDir = dir.normalize();
		chargeFrom = mob.position();
		mob.playSound(ModSounds.BOSS_KAAST_CHARGE, 1.3f, 1f);
	}

	private void tickCharge(ServerLevel level, KaastBalance b) {
		if (chargeDir == null || chargeFrom == null) {
			endCharge(b);
			return;
		}

		timer++;
		face(chargeDir);
		Vec3 move = chargeDir.scale(b.chargeSpeed);
		mob.setDeltaMovement(move.x, mob.getDeltaMovement().y, move.z);

		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.3), e -> e instanceof Player p && p.isAlive()
				&& !p.isSpectator() && p.gameMode() != GameType.CREATIVE)) {
			hit.hurtServer(level, mob.damageSources().mobAttack(mob), b.chargeDamage);
			hit.push(chargeDir.x * 1.2, 0.35, chargeDir.z * 1.2);
			hit.hurtMarked = true;
			endCharge(b);
			return;
		}

		if (timer > 1 && mob.horizontalCollision || mob.position().distanceTo(chargeFrom) >= b.chargeRange || timer > MAX_CHARGE_TICKS) {
			endCharge(b);
		}
	}

	private void endCharge(KaastBalance b) {
		action = Action.NONE;
		chargeDir = null;
		chargeFrom = null;
		chargeCooldown = b.chargeCooldown;
	}

	/** The jet slam's tell: he rises high over where the player stands now, and comes down there. */
	private void startSlam(ServerLevel level, LivingEntity target, KaastBalance b) {
		action = Action.SLAM_RISE;
		timer = Math.max(1, b.slamTellTicks);
		slamDone = true;
		slamAt = target.position();
		slamFrom = mob.position();
		mob.getNavigation().stop();
		mob.setNoGravity(true);
		EffectEvents.around(level, KaastEffects.EVENT, slamAt, null, mob.getId(), timer, KaastEffects.SLAM_TELL);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModSounds.BOSS_KAAST_SLAM_TELL, mob.getSoundSource(), 1.5f, 1f);
	}

	private void tickSlamRise(KaastBalance b) {
		if (slamAt == null || slamFrom == null) {
			endSlam();
			return;
		}

		int total = Math.max(1, b.slamTellTicks);
		double p = 1.0 - Math.max(0, timer - 1) / (double) total;
		Vec3 high = slamAt.add(0, b.slamHeight, 0);
		Vec3 next = slamFrom.lerp(high, Mth.clamp(p, 0.0, 1.0));
		mob.setDeltaMovement(next.subtract(mob.position()));
		mob.hurtMarked = true;
		mob.resetFallDistance();

		if (--timer <= 0) {
			action = Action.SLAM_FALL;
			timer = 0;
		}
	}

	private void tickSlamFall(ServerLevel level, KaastBalance b) {
		if (slamAt == null) {
			endSlam();
			return;
		}

		timer++;
		Vec3 toSpot = slamAt.subtract(mob.position());
		mob.setDeltaMovement(toSpot.x * 0.3, -FALL_SPEED, toSpot.z * 0.3);
		mob.hurtMarked = true;
		mob.resetFallDistance();

		if (!mob.onGround() && timer <= MAX_FALL_TICKS) {
			return;
		}

		Vec3 at = mob.position();
		DamageSource source = level.damageSources().source(HunterDamage.JET_SLAM, mob, mob);

		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(b.slamRadius), e -> e != mob && e.isAlive()
				&& !(e instanceof MandalorianHunter) && !(e instanceof Rattle) && !(e instanceof Player p && (p.gameMode() == GameType.CREATIVE || p.isSpectator())))) {
			if (hit.position().distanceTo(at) > b.slamRadius + hit.getBbWidth() * 0.5) {
				continue;
			}

			hit.hurtServer(level, source, b.slamDamage);
			Vec3 away = hit.position().subtract(at).multiply(1, 0, 1);
			Vec3 push = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize().scale(0.8);
			hit.push(push.x, 0.45, push.z);
			hit.hurtMarked = true;
		}

		EffectEvents.around(level, KaastEffects.EVENT, at, null, mob.getId(), b.slamRadius, KaastEffects.SLAM);
		level.playSound(null, at.x, at.y, at.z, ModSounds.BOSS_KAAST_SLAM, mob.getSoundSource(), 1.5f, 1f);
		endSlam();
	}

	private void endSlam() {
		action = Action.NONE;
		slamAt = null;
		slamFrom = null;
		mob.setNoGravity(false);
	}

	private void face(Vec3 dir) {
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}

		float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
		mob.setYRot(yaw);
		mob.yBodyRot = yaw;
		mob.yHeadRot = yaw;
	}
}

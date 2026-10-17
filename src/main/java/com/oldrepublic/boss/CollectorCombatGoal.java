package com.oldrepublic.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Holds;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelistCombatGoal;
import com.oldrepublic.saber.duel.DuelistProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Orren Vasch's fight (spec 8.3): the saber duel of {@link DuelistCombatGoal} with his phase's
 * profile, plus his Force moves.
 *
 * <ul>
 * <li>Phase 1: <b>Archive Hurl</b> every {@code hurlInterval} (a chunk from the nearest debris pile,
 * 24-tick tell, thrown at the Hurl target) and the <b>Repelling Palm</b> on a participant who stays
 * within {@code palmRange} for {@code palmDwellTicks} (16-tick tell; a Push in the tell clashes).</li>
 * <li>Phase 2: he keeps the phase 1 sword-work but never strays more than {@code leashRange} from the
 * dais: a target beyond it is answered from the dais with the <b>Resonant Lash</b> every
 * {@code lashInterval} (20-tick tell, then a 1-block ridge travelling {@code lashSpeed} along the
 * floor for up to {@code lashRange}; a jump clears it).</li>
 * <li>Phase 3: the <b>Lunge</b> (14-tick tell, up to {@code lungeRange}), the <b>Holocron Surge</b>
 * every {@code surgeInterval} (30-tick gather that nothing interrupts, cover by server rays, the
 * Push clash in its last {@code surgeClashTicks}; then he is Spent) and the <b>Archive Grip</b> every
 * {@code gripInterval}, never within {@code gripSurgeGap} of a Surge.</li>
 * </ul>
 *
 * Moves start while the duel goal is neutral ({@link #tryForceMove}) and are advanced every fight tick
 * by the session through {@link #serverTick}, so a running Surge or Grip goes on even if the duel
 * itself pauses. Interrupts arrive from {@link CollectorReactions} ({@link #onInterrupt}), Push clashes
 * from {@link #onPushHit}, cast Pushes from the session's watch ({@link #onPushCast}) and hits from
 * {@link #onHitTaken}.
 */
public class CollectorCombatGoal extends DuelistCombatGoal {
	/** His Force moves. */
	public enum Move {
		NONE,
		HURL,
		PALM,
		LASH,
		LUNGE,
		SURGE,
		GRIP
	}

	/** One travelling Lash ridge: its path follows the floor, precomputed at release. */
	private static final class Ridge {
		final List<Vec3> path;
		final Vec3 dir;
		final Set<UUID> hit = new HashSet<>();
		int index;

		Ridge(List<Vec3> path, Vec3 dir) {
			this.path = path;
			this.dir = dir;
		}
	}

	/** Ticks the Lunge dash lasts. */
	private static final int LUNGE_DASH_TICKS = 5;
	/** How close to the dais centre he stands when he holds it. */
	private static final double DAIS_STAND = 1.5;
	/** The Surge clash: the caster must face him within this angle (either side). */
	private static final double CLASH_FACING_COS = Math.cos(Math.toRadians(60));
	/** Half width of the Lash ridge and how far above the ridge a grounded player still counts as on it. */
	private static final double RIDGE_HALF_WIDTH = 0.9;
	private static final double RIDGE_HEIGHT = 0.7;
	/** Lash path sample step: one tick of ridge travel at most this long is checked in pieces. */
	private static final double PATH_STEP = 0.4;
	/** The highest drop the ridge follows down a step, and the farthest a debris pile may be. */
	private static final int RIDGE_MAX_DROP = 4;
	private static final double DEBRIS_REACH = 16;
	/** The Palm hits in front of him within this arc (degrees, total). */
	private static final float PALM_ARC = 110f;

	private final OrrenVasch vasch;
	private Move move = Move.NONE;
	private int moveTicks;
	private @Nullable LivingEntity moveTarget;
	private @Nullable ArchiveDebris debris;
	private long nextHurlAt;
	private long nextLashAt;
	private long nextSurgeAt;
	private long nextGripAt;
	private long nextLungeAt;
	private long lastSurgeAt = Long.MIN_VALUE / 2;
	private final Map<UUID, Integer> palmDwell = new HashMap<>();
	private final Set<UUID> surgeClashers = new HashSet<>();
	private boolean gripHolding;
	private float gripTaken;
	private Vec3 lungeDir = Vec3.ZERO;
	private boolean lungeStruck;
	private Vec3 lashDir = Vec3.ZERO;
	private final List<Ridge> ridges = new ArrayList<>();
	private boolean holdingDais;

	public CollectorCombatGoal(OrrenVasch vasch) {
		super(vasch, () -> profileFor(vasch));
		this.vasch = vasch;
	}

	/** His profile now: the phase's, scaled by the session's outgoing multiplier. */
	static DuelistProfile profileFor(OrrenVasch vasch) {
		ArenaSession session = vasch.session();
		return CollectorProfiles.forPhase(vasch.phase(), session == null ? 1f : session.outgoing(), CollectorBalance.get());
	}

	public Move move() {
		return move;
	}

	/** Ticks since the current move started. */
	public int moveTicks() {
		return moveTicks;
	}

	/** Whether he is channelling the Grip on a held participant (damage taken x{@code gripTakenMultiplier}). */
	public boolean isChannellingGrip() {
		return move == Move.GRIP && gripHolding;
	}

	/** Whether he is in a tell that can still be interrupted (the Surge gather never can). */
	public boolean inTell(Move which) {
		CollectorBalance b = CollectorBalance.get();
		return move == which && switch (which) {
			case HURL -> moveTicks < b.hurlTellTicks;
			case PALM -> moveTicks < b.palmTellTicks;
			case LUNGE -> moveTicks < b.lungeTellTicks;
			case GRIP -> !gripHolding;
			case LASH -> moveTicks < b.lashTellTicks;
			default -> false;
		};
	}

	/** Restarts the move timers for {@code phase} (the session calls it as each phase begins). */
	void onPhaseStart(int phase, long now) {
		CollectorBalance b = CollectorBalance.get();
		palmDwell.clear();

		switch (phase) {
			case CollectorRules.PHASE_ONE -> nextHurlAt = now + b.hurlInterval / 2;
			case CollectorRules.PHASE_TWO -> nextLashAt = now + b.lashInterval / 2;
			default -> {
				nextSurgeAt = now + b.surgeInterval / 2;
				nextGripAt = now + b.gripInterval / 2;
				nextLungeAt = now + b.lungeCooldown;
			}
		}
	}

	// ---------------------------------------------------------------- goal

	@Override
	public boolean canUse() {
		ArenaSession session = vasch.session();
		return session != null && session.isFighting() && !session.isTransitioning() && !vasch.isKneeling() && super.canUse();
	}

	@Override
	public void tick() {
		ArenaSession session = vasch.session();
		LivingEntity target = vasch.getTarget();

		if (session == null || target == null) {
			return;
		}

		if (move != Move.NONE) {
			// The move owns the turn: no swings, he faces what he is doing.
			LivingEntity facing = moveTarget != null ? moveTarget : target;
			vasch.getLookControl().setLookAt(facing, 30f, 30f);

			if (move != Move.LUNGE) {
				vasch.getNavigation().stop();
			}

			return;
		}

		if (session.phase() == CollectorRules.PHASE_TWO && mustHoldDais(session, target)) {
			if (!holdingDais) {
				holdingDais = true;
				super.stop();
			}

			holdDais(session, target);
			return;
		}

		if (holdingDais) {
			holdingDais = false;
			super.start();
		}

		super.tick();
	}

	@Override
	public void stop() {
		super.stop();
		holdingDais = false;
	}

	@Override
	protected boolean tryForceMove(LivingEntity target, double distance) {
		ArenaSession session = vasch.session();

		if (session == null || move != Move.NONE || session.isTransitioning() || !(vasch.level() instanceof ServerLevel level)) {
			return false;
		}

		long now = level.getGameTime();
		CollectorBalance b = CollectorBalance.get();

		switch (session.phase()) {
			case CollectorRules.PHASE_ONE -> {
				ServerPlayer crowding = crowdingPlayer(session, b);

				if (crowding != null) {
					startPalm(level, crowding);
				} else if (now >= nextHurlAt) {
					LivingEntity hurlTarget = session.specialTarget(now);
					startHurl(level, hurlTarget != null ? hurlTarget : target, now, b);
				}
			}
			case CollectorRules.PHASE_TWO -> {
				if (now >= nextLashAt) {
					startLash(level, target, now, b);
				}
			}
			default -> {
				if (now >= nextSurgeAt) {
					startSurge(level, now, b);
				} else if (now >= nextGripAt && now - lastSurgeAt >= b.gripSurgeGap && nextSurgeAt - now >= b.gripSurgeGap) {
					startGrip(level, session, now, b);
				} else if (now >= nextLungeAt) {
					LivingEntity lungeTarget = session.specialTarget(now);
					LivingEntity aim = lungeTarget != null ? lungeTarget : target;
					double range = vasch.distanceTo(aim);

					if (range > reach(aim) + 1.0 && range <= b.lungeRange) {
						startLunge(level, aim, now, b);
					}
				}
			}
		}

		return move != Move.NONE;
	}

	@Override
	protected boolean tickForceMove(LivingEntity target, double distance) {
		return move != Move.NONE;
	}

	/** The duel goal let go of its turn: tells aimed at a lost target end; the Surge, Lash and a running hold go on. */
	@Override
	protected void cancelForceMove() {
		if (move == Move.HURL || move == Move.PALM || move == Move.LUNGE || (move == Move.GRIP && !gripHolding)) {
			abortMove(false);
		}
	}

	/**
	 * Starts {@code which} now, ignoring its timer and the phase (commands, the client screenshot steps
	 * and game tests); the current move ends first, a Surge gather included. The Palm and the Grip need
	 * a player target. Returns whether the move started.
	 */
	public boolean forceMove(Move which, LivingEntity target) {
		ArenaSession session = vasch.session();

		if (session == null || !session.isFighting() || session.isTransitioning() || !(vasch.level() instanceof ServerLevel level)) {
			return false;
		}

		abortMove(true);
		long now = level.getGameTime();
		CollectorBalance b = CollectorBalance.get();

		switch (which) {
			case HURL -> startHurl(level, target, now, b);
			case PALM -> {
				if (target instanceof ServerPlayer player) {
					startPalm(level, player);
				}
			}
			case LASH -> startLash(level, target, now, b);
			case LUNGE -> startLunge(level, target, now, b);
			case SURGE -> startSurge(level, now, b);
			case GRIP -> {
				if (target instanceof ServerPlayer player) {
					startGripOn(player, now, b);
				}
			}
			default -> {
			}
		}

		return move == which;
	}

	// ---------------------------------------------------------------- per tick (from the session)

	/** Advances the moves and the travelling ridges; called by the session every fight tick. */
	void serverTick(ServerLevel level, long now) {
		tickRidges(level);
		ArenaSession session = vasch.session();

		if (session != null && session.phase() == CollectorRules.PHASE_ONE && move == Move.NONE) {
			updatePalmDwell(session);
		}

		if (move == Move.NONE) {
			return;
		}

		moveTicks++;
		CollectorBalance b = CollectorBalance.get();

		switch (move) {
			case HURL -> tickHurl(level, b);
			case PALM -> tickPalm(level, b);
			case LASH -> tickLash(level, b);
			case LUNGE -> tickLunge(level, b);
			case SURGE -> tickSurge(level, now, b);
			case GRIP -> tickGrip(level, b);
			default -> endMove();
		}
	}

	/**
	 * Ends the current move at once. The Surge gather is never interrupted unless {@code force}
	 * (a transition, the yield, a reset). A held Hurl chunk drops; a Grip lets go.
	 */
	public void abortMove(boolean force) {
		if (move == Move.NONE || (move == Move.SURGE && !force)) {
			return;
		}

		if (move == Move.HURL && debris != null && !debris.isRemoved()) {
			debris.drop();
		}

		if (move == Move.GRIP && gripHolding && moveTarget instanceof ServerPlayer held) {
			Holds.release(held, force ? "reset" : "break");
		}

		endMove();
	}

	/** Drops every ridge in flight (a reset). */
	void clearRidges() {
		ridges.clear();
	}

	private void endMove() {
		if (vasch.action() != OrrenVasch.Action.SPENT) {
			vasch.setAction(OrrenVasch.Action.NONE);
		}

		move = Move.NONE;
		moveTicks = 0;
		moveTarget = null;
		debris = null;
		gripHolding = false;
		gripTaken = 0f;
		lungeStruck = false;
	}

	private void begin(Move next, @Nullable LivingEntity target, OrrenVasch.Action action) {
		move = next;
		moveTicks = 0;
		moveTarget = target;
		vasch.setAction(action);
		vasch.getNavigation().stop();
	}

	// ---------------------------------------------------------------- interrupts and reactions

	/**
	 * A wind-up interrupt (8.4): the Hurl tell yields to {@code force_pull} / {@code pull_disarm} (the
	 * chunk is ripped away), the Lunge tell to {@code pull_disarm} / {@code force_push}, the Grip tell to
	 * {@code force_push} / {@code force_pull} / {@code pull_disarm}. Every other cause, the Surge gather
	 * and the transitions ignore it.
	 */
	void onInterrupt(@Nullable ServerPlayer caster, String cause) {
		if (!(vasch.level() instanceof ServerLevel level)) {
			return;
		}

		boolean pull = ForceReactions.CAUSE_FORCE_PULL.equals(cause);
		boolean disarm = ForceReactions.CAUSE_PULL_DISARM.equals(cause);
		boolean push = ForceReactions.CAUSE_FORCE_PUSH.equals(cause);

		if (inTell(Move.HURL) && (pull || disarm)) {
			if (debris != null && !debris.isRemoved()) {
				debris.rip(level, caster);
			}
		} else if (inTell(Move.LUNGE) && (disarm || push)) {
			abortMove(false);

			if (push) {
				vasch.counterStagger(CollectorBalance.get().lungeInterruptStaggerTicks);
			}
		} else if (inTell(Move.GRIP) && (push || pull || disarm)) {
			abortMove(false);
		}
	}

	/** A Push reached him: during the Palm tell it is the push clash (M2 acolyte rule). Returns the knockback multiplier. */
	float onPushHit(ServerPlayer caster) {
		if (!inTell(Move.PALM) || !(vasch.level() instanceof ServerLevel level)) {
			return 1f;
		}

		abortMove(false);
		vasch.counterStagger(CollectorBalance.get().palmClashStaggerTicks);
		// Both are pushed back: the caster rocks back from the clash too.
		Vec3 away = caster.position().subtract(vasch.position()).multiply(1, 0, 1);
		Vec3 knock = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize().scale(0.8);
		caster.setDeltaMovement(caster.getDeltaMovement().add(knock.x, 0.25, knock.z));
		caster.hurtMarked = true;
		EffectEvents.toTracking(vasch, BossEffects.PALM, palmPoint(), vasch.getLookAngle(), vasch.getId(), 1f, BossEffects.PALM_CLASH);
		return 1f;
	}

	/**
	 * A participant cast Push (any branch) this tick: the held player's Push breaks the Grip; a Push
	 * cast facing him in the last {@code surgeClashTicks} of the Surge gather is a clash.
	 */
	void onPushCast(ServerPlayer player) {
		CollectorBalance b = CollectorBalance.get();

		if (move == Move.GRIP && gripHolding && moveTarget == player) {
			breakGrip(b);
			return;
		}

		if (move == Move.SURGE && moveTicks >= b.surgeTellTicks - b.surgeClashTicks && facesHim(player)) {
			surgeClashers.add(player.getUUID());
		}
	}

	/** He took {@code dealt} damage from {@code attacker}: a big hit breaks the Grip tell, allies' damage breaks the hold. */
	void onHitTaken(@Nullable ServerPlayer attacker, float dealt) {
		CollectorBalance b = CollectorBalance.get();

		if (inTell(Move.GRIP) && dealt >= b.interruptHitDamage) {
			abortMove(false);
			return;
		}

		if (move == Move.GRIP && gripHolding && attacker != null) {
			gripTaken += dealt;

			if (gripTaken >= b.gripBreakDamage) {
				breakGrip(b);
			}
		}
	}

	private boolean facesHim(ServerPlayer player) {
		Vec3 to = vasch.getBoundingBox().getCenter().subtract(player.getEyePosition());
		return to.lengthSqr() > 1.0e-4 && player.getLookAngle().dot(to.normalize()) >= CLASH_FACING_COS;
	}

	// ---------------------------------------------------------------- phase 1: Archive Hurl

	private void startHurl(ServerLevel level, LivingEntity target, long now, CollectorBalance b) {
		nextHurlAt = now + b.hurlInterval;
		ArenaSession session = vasch.session();
		BlockPos pile = session == null ? null : nearestPile(session.layout());
		Vec3 from;
		ItemStack chunk;

		if (pile != null) {
			from = Vec3.atCenterOf(pile.above());
			Item item = level.getBlockState(pile).getBlock().asItem();
			chunk = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
		} else {
			// No pile (a test fixture): the floor beside him gives up the chunk.
			Vec3 side = vasch.offHandPoint().subtract(vasch.position()).multiply(1, 0, 1);
			from = vasch.position().add(side.scale(2.0)).add(0, 0.3, 0);
			chunk = ItemStack.EMPTY;
		}

		ArenaSession current = vasch.session();
		ArchiveDebris created = ArchiveDebris.create(level, vasch, from, b.hurlDamage * (current == null ? 1f : current.outgoing()));

		if (!chunk.isEmpty()) {
			created.setItem(chunk);
		}

		created.setHoldPoint(holdPoint());
		level.addFreshEntity(created);
		debris = created;
		begin(Move.HURL, target, OrrenVasch.Action.HURL);
		EffectEvents.toTracking(vasch, BossEffects.HURL, from, null, vasch.getId(), b.hurlTellTicks, BossEffects.HURL_LIFT);
	}

	private @Nullable BlockPos nearestPile(ArenaLayout layout) {
		BlockPos best = null;
		double bestSqr = DEBRIS_REACH * DEBRIS_REACH;

		for (BlockPos pile : layout.debris()) {
			double sqr = pile.distToCenterSqr(vasch.position());

			if (sqr < bestSqr) {
				bestSqr = sqr;
				best = pile;
			}
		}

		return best;
	}

	private Vec3 holdPoint() {
		return vasch.offHandPoint().add(0, 0.6, 0);
	}

	private void tickHurl(ServerLevel level, CollectorBalance b) {
		ArchiveDebris chunk = debris;

		if (chunk == null || chunk.isRemoved() || chunk.state() != ArchiveDebris.State.HELD) {
			// Ripped (he is already staggered), caught or gone: the throw is lost.
			endMove();
			return;
		}

		chunk.setHoldPoint(holdPoint());

		if (moveTicks < b.hurlTellTicks) {
			return;
		}

		LivingEntity target = moveTarget;

		if (target == null || !target.isAlive()) {
			chunk.drop();
			endMove();
			return;
		}

		Vec3 aim = target.getBoundingBox().getCenter().subtract(chunk.position());
		Vec3 velocity = aim.lengthSqr() < 1.0e-4 ? vasch.getLookAngle().scale(b.hurlSpeed) : aim.normalize().scale(b.hurlSpeed);
		chunk.launch(velocity.add(0, 0.04, 0));
		EffectEvents.toTracking(vasch, BossEffects.HURL, chunk.position(), velocity, vasch.getId(), 1f, BossEffects.HURL_THROW);
		endMove();
	}

	// ---------------------------------------------------------------- phase 1: Repelling Palm

	private void updatePalmDwell(ArenaSession session) {
		CollectorBalance b = CollectorBalance.get();
		double rangeSqr = b.palmRange * b.palmRange;

		for (ServerPlayer player : session.participantsInHall()) {
			UUID id = player.getUUID();

			if (player.isAlive() && vasch.distanceToSqr(player) <= rangeSqr) {
				palmDwell.merge(id, 1, Integer::sum);
			} else {
				palmDwell.remove(id);
			}
		}
	}

	/** A participant who has stayed within {@code palmRange} for {@code palmDwellTicks}, or null. */
	private @Nullable ServerPlayer crowdingPlayer(ArenaSession session, CollectorBalance b) {
		for (ServerPlayer player : session.participantsInHall()) {
			Integer dwell = palmDwell.get(player.getUUID());

			if (dwell != null && dwell >= b.palmDwellTicks) {
				return player;
			}
		}

		return null;
	}

	private void startPalm(ServerLevel level, ServerPlayer target) {
		palmDwell.clear();
		begin(Move.PALM, target, OrrenVasch.Action.PALM);
		CollectorBalance b = CollectorBalance.get();
		EffectEvents.toTracking(vasch, BossEffects.PALM, palmPoint(), vasch.getLookAngle(), vasch.getId(), b.palmTellTicks, BossEffects.PALM_TELL);
	}

	private Vec3 palmPoint() {
		return vasch.position().add(vasch.getLookAngle().multiply(1, 0, 1).scale(0.6)).add(0, vasch.getBbHeight() * 0.65, 0);
	}

	private void tickPalm(ServerLevel level, CollectorBalance b) {
		if (moveTicks < b.palmTellTicks) {
			return;
		}

		ArenaSession session = vasch.session();
		float damage = b.palmDamage * (session == null ? 1f : session.outgoing());
		Vec3 origin = vasch.position();
		AABB box = vasch.getBoundingBox().inflate(b.palmRange + 0.5);

		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
			Vec3 to = player.position().subtract(origin).multiply(1, 0, 1);

			if (to.length() > b.palmRange + 0.5 || !SaberGuard.inFront(vasch, player.position(), PALM_ARC)) {
				continue;
			}

			player.hurtServer(level, BossDamageTypes.source(level, BossDamageTypes.REPELLING_PALM, vasch, vasch), damage);
			knock(player, to, b.palmKnockback, 0.35);
		}

		EffectEvents.toTracking(vasch, BossEffects.PALM, palmPoint(), vasch.getLookAngle(), vasch.getId(), 1f, BossEffects.PALM_RELEASE);
		endMove();
	}

	private static void knock(LivingEntity target, Vec3 away, double strength, double lift) {
		double resist = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		double s = strength * (1.0 - resist);

		if (s <= 0.0) {
			return;
		}

		Vec3 dir = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
		target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(dir.x * s, lift * (1.0 - resist), dir.z * s));
		target.hurtMarked = true;
	}

	// ---------------------------------------------------------------- phase 2: the dais and the Resonant Lash

	/** Whether he must answer from the dais: he strayed past {@code leashRange}, or the target is beyond his reach from it. */
	private boolean mustHoldDais(ArenaSession session, LivingEntity target) {
		ArenaLayout layout = session.layout();
		double leash = CollectorBalance.get().leashRange;
		double reachFromRing = leash + reach(target);
		return layout.daisDistanceSqr(vasch.position()) > leash * leash || layout.daisDistanceSqr(target.position()) > reachFromRing * reachFromRing;
	}

	private void holdDais(ArenaSession session, LivingEntity target) {
		Vec3 dais = session.layout().daisFeet();

		if (vasch.position().distanceToSqr(dais) > DAIS_STAND * DAIS_STAND) {
			if (vasch.getNavigation().isDone()) {
				vasch.getNavigation().moveTo(dais.x, dais.y, dais.z, 1.1);
			}
		} else {
			vasch.getNavigation().stop();
		}

		vasch.getLookControl().setLookAt(target, 30f, 30f);

		if (vasch.level() instanceof ServerLevel level && level.getGameTime() >= nextLashAt && move == Move.NONE) {
			startLash(level, target, level.getGameTime(), CollectorBalance.get());
		}
	}

	private void startLash(ServerLevel level, LivingEntity target, long now, CollectorBalance b) {
		nextLashAt = now + b.lashInterval;
		Vec3 to = target.position().subtract(vasch.position()).multiply(1, 0, 1);
		lashDir = to.lengthSqr() < 1.0e-4 ? vasch.getLookAngle().multiply(1, 0, 1).normalize() : to.normalize();
		begin(Move.LASH, target, OrrenVasch.Action.LASH);
		EffectEvents.toTracking(vasch, BossEffects.LASH, vasch.position().add(lashDir.scale(0.8)), lashDir.scale(b.lashSpeed), vasch.getId(),
				b.lashTellTicks, BossEffects.LASH_TELL);
	}

	private void tickLash(ServerLevel level, CollectorBalance b) {
		if (moveTicks < b.lashTellTicks) {
			return;
		}

		Vec3 start = vasch.position().add(lashDir.scale(0.8));
		List<Vec3> path = ridgePath(level, start, lashDir, b.lashRange, b.lashSpeed);
		double length = path.isEmpty() ? 0 : (path.size() - 1) * (double) b.lashSpeed;
		ridges.add(new Ridge(path, lashDir));
		EffectEvents.toTracking(vasch, BossEffects.LASH, start, lashDir.scale(b.lashSpeed), vasch.getId(), (float) length, BossEffects.LASH_WAVE);
		endMove();
	}

	/**
	 * The Lash ridge's path, one point per tick of travel: it follows the floor (up one free step,
	 * down at most {@link #RIDGE_MAX_DROP}) and stops at a wall or a drop. The client draws the same path.
	 */
	public static List<Vec3> ridgePath(ServerLevel level, Vec3 start, Vec3 dir, float range, float speed) {
		List<Vec3> path = new ArrayList<>();
		double y = Math.floor(start.y + 0.01);
		int steps = Mth.ceil(range / Math.max(0.05f, speed));
		path.add(new Vec3(start.x, y, start.z));

		for (int i = 1; i <= steps; i++) {
			Vec3 point = start.add(dir.scale(Math.min(range, i * speed)));
			BlockPos cell = BlockPos.containing(point.x, y + 0.01, point.z);

			if (solid(level, cell)) {
				if (solid(level, cell.above()) || solid(level, cell.above(2))) {
					break;
				}

				y += 1;
			} else {
				int drop = 0;

				while (drop < RIDGE_MAX_DROP && !solid(level, cell.below(drop + 1))) {
					drop++;
				}

				if (drop >= RIDGE_MAX_DROP) {
					break;
				}

				y -= drop;
			}

			path.add(new Vec3(point.x, y, point.z));
		}

		return path;
	}

	private static boolean solid(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private void tickRidges(ServerLevel level) {
		if (ridges.isEmpty()) {
			return;
		}

		CollectorBalance b = CollectorBalance.get();
		ArenaSession session = vasch.session();
		float damage = b.lashDamage * (session == null ? 1f : session.outgoing());

		for (Iterator<Ridge> it = ridges.iterator(); it.hasNext();) {
			Ridge ridge = it.next();

			if (++ridge.index >= ridge.path.size()) {
				it.remove();
				continue;
			}

			Vec3 from = ridge.path.get(ridge.index - 1);
			Vec3 to = ridge.path.get(ridge.index);
			AABB box = new AABB(from, to).inflate(RIDGE_HALF_WIDTH + 0.5, 1.5, RIDGE_HALF_WIDTH + 0.5);

			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isSpectator())) {
				if (ridge.hit.contains(player.getUUID()) || !player.onGround() || !onRidge(player.position(), from, to)) {
					continue;
				}

				ridge.hit.add(player.getUUID());
				player.hurtServer(level, BossDamageTypes.source(level, BossDamageTypes.RESONANT_LASH, vasch, vasch), damage);
				knock(player, ridge.dir, b.lashKnockback, 0.25);
			}
		}
	}

	/** Whether feet at {@code feet} stand on the ridge segment {@code from}-{@code to} (grounded checks are the caller's). */
	static boolean onRidge(Vec3 feet, Vec3 from, Vec3 to) {
		Vec3 seg = to.subtract(from).multiply(1, 0, 1);
		Vec3 rel = feet.subtract(from).multiply(1, 0, 1);
		double lengthSqr = seg.lengthSqr();
		double t = lengthSqr < 1.0e-6 ? 0 : Mth.clamp(rel.dot(seg) / lengthSqr, 0.0, 1.0);
		Vec3 closest = from.add(seg.scale(t));
		double lateral = feet.multiply(1, 0, 1).distanceTo(closest.multiply(1, 0, 1));
		double groundY = Mth.lerp(t, from.y, to.y);
		return lateral <= RIDGE_HALF_WIDTH && feet.y >= groundY - 0.2 && feet.y <= groundY + RIDGE_HEIGHT;
	}

	// ---------------------------------------------------------------- phase 3: Lunge

	private void startLunge(ServerLevel level, LivingEntity target, long now, CollectorBalance b) {
		nextLungeAt = now + b.lungeCooldown;
		begin(Move.LUNGE, target, OrrenVasch.Action.LUNGE);
		Vec3 to = target.position().subtract(vasch.position()).multiply(1, 0, 1);
		lungeDir = to.lengthSqr() < 1.0e-4 ? vasch.getLookAngle().multiply(1, 0, 1).normalize() : to.normalize();
		EffectEvents.toTracking(vasch, BossEffects.LUNGE, vasch.position(), lungeDir, vasch.getId(), b.lungeTellTicks, BossEffects.LUNGE_TELL);
	}

	private void tickLunge(ServerLevel level, CollectorBalance b) {
		LivingEntity target = moveTarget;

		if (target == null || !target.isAlive()) {
			endMove();
			return;
		}

		if (moveTicks < b.lungeTellTicks) {
			Vec3 to = target.position().subtract(vasch.position()).multiply(1, 0, 1);

			if (to.lengthSqr() > 1.0e-4) {
				lungeDir = to.normalize();
			}

			return;
		}

		int dashTick = moveTicks - b.lungeTellTicks;

		if (dashTick == 0) {
			EffectEvents.toTracking(vasch, BossEffects.LUNGE, vasch.position(), lungeDir, vasch.getId(), 1f, BossEffects.LUNGE_STRIKE);
		}

		if (dashTick < LUNGE_DASH_TICKS) {
			double distance = Math.min(b.lungeRange, Math.max(0.0, vasch.distanceTo(target) - reach(target) * 0.5));
			double speed = Math.min(b.lungeRange / (double) LUNGE_DASH_TICKS, distance / Math.max(1, LUNGE_DASH_TICKS - dashTick));
			vasch.setDeltaMovement(lungeDir.x * speed, vasch.getDeltaMovement().y, lungeDir.z * speed);
			vasch.hurtMarked = true;
		}

		if (!lungeStruck && vasch.distanceTo(target) <= reach(target) + 0.3) {
			lungeStruck = true;
			ArenaSession session = vasch.session();
			float damage = b.lungeDamage * (session == null ? 1f : session.outgoing());
			SaberCombat.strike(target, level, vasch.damageSources().mobAttack(vasch), damage,
					SaberCombat.Strike.npcHeavy(SaberToggle.balance().heavyGuardDrainMult));
		}

		if (dashTick >= LUNGE_DASH_TICKS || lungeStruck) {
			endMove();
		}
	}

	// ---------------------------------------------------------------- phase 3: Holocron Surge

	private void startSurge(ServerLevel level, long now, CollectorBalance b) {
		nextSurgeAt = now + b.surgeInterval;
		surgeClashers.clear();
		begin(Move.SURGE, null, OrrenVasch.Action.SURGE);
		EffectEvents.toTracking(vasch, BossEffects.SURGE, vasch.chestPoint(), null, vasch.getId(), b.surgeTellTicks, BossEffects.SURGE_GATHER);
	}

	private void tickSurge(ServerLevel level, long now, CollectorBalance b) {
		if (moveTicks < b.surgeTellTicks) {
			return;
		}

		releaseSurge(level, b);
		lastSurgeAt = now;
		// Keep the Grip clear of the Surge on both sides.
		nextGripAt = Math.max(nextGripAt, now + b.gripSurgeGap);
		endMove();
		vasch.setSpent(b.spentTicks);
		EffectEvents.toTracking(vasch, BossEffects.SPENT, vasch.chestPoint(), null, vasch.getId(), b.spentTicks, 0);
	}

	/** The release (8.3): server rays to each player's eye and chest; clashers and those sheltered behind them take nothing. */
	private void releaseSurge(ServerLevel level, CollectorBalance b) {
		ArenaSession session = vasch.session();
		float outgoing = session == null ? 1f : session.outgoing();
		Vec3 chest = vasch.chestPoint();
		List<ServerPlayer> clashers = new ArrayList<>();

		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, vasch.getBoundingBox().inflate(b.surgeRadius + 4), p -> p.isAlive() && !p.isSpectator())) {
			if (surgeClashers.contains(player.getUUID())) {
				clashers.add(player);
			}
		}

		EffectEvents.toTracking(vasch, BossEffects.SURGE, chest, null, vasch.getId(), b.surgeRadius, BossEffects.SURGE_RELEASE);

		for (ServerPlayer clasher : clashers) {
			EffectEvents.toTracking(vasch, BossEffects.SURGE, clasher.getBoundingBox().getCenter(), clasher.position().subtract(vasch.position()),
					clasher.getId(), b.surgeClashShelter, BossEffects.SURGE_CLASH);
		}

		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, vasch.getBoundingBox().inflate(b.surgeRadius), p -> p.isAlive() && !p.isSpectator())) {
			if (sheltered(player, clashers, b)) {
				continue;
			}

			float mult = surgeExposure(player);

			if (mult <= 0f) {
				continue;
			}

			player.hurtServer(level, BossDamageTypes.source(level, BossDamageTypes.COLLECTOR_SURGE, vasch, vasch), b.surgeDamage * outgoing * mult);
			knock(player, player.position().subtract(vasch.position()), b.surgeKnockback * mult, 0.4 * mult);
		}
	}

	/**
	 * The share of the Surge {@code player} would take if it were released now (8.3): 0 beyond
	 * {@code surgeRadius}; else the server rays from his chest to the player's eye and chest decide
	 * the cover ({@link CollectorRules#surgeMultiplier}), and being airborne halves it. A clasher's
	 * shelter is the release's own rule. The release uses it; public for the game tests.
	 */
	public float surgeExposure(ServerPlayer player) {
		CollectorBalance b = CollectorBalance.get();

		if (!(vasch.level() instanceof ServerLevel level) || player.level() != level || player.distanceTo(vasch) > b.surgeRadius) {
			return 0f;
		}

		Vec3 chest = vasch.chestPoint();
		boolean eyeBlocked = blocked(level, chest, player.getEyePosition());
		boolean chestBlocked = blocked(level, chest, player.getBoundingBox().getCenter());
		return CollectorRules.surgeMultiplier(eyeBlocked, chestBlocked, !player.onGround(), b);
	}

	/** A clasher, or anyone within {@code surgeClashShelter} behind one (on the far side from him). */
	private boolean sheltered(ServerPlayer player, List<ServerPlayer> clashers, CollectorBalance b) {
		for (ServerPlayer clasher : clashers) {
			if (clasher == player) {
				return true;
			}

			Vec3 out = clasher.position().subtract(vasch.position()).multiply(1, 0, 1);
			Vec3 behind = player.position().subtract(clasher.position()).multiply(1, 0, 1);

			if (out.lengthSqr() > 1.0e-4 && behind.length() <= b.surgeClashShelter && behind.dot(out) >= 0.0) {
				return true;
			}
		}

		return false;
	}

	/** Whether a block collider stands between {@code from} and {@code to} (the Surge cover rays). */
	boolean blocked(ServerLevel level, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, vasch));
		return hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(from) < from.distanceToSqr(to) - 1.0e-3;
	}

	// ---------------------------------------------------------------- phase 3: Archive Grip

	private void startGrip(ServerLevel level, ArenaSession session, long now, CollectorBalance b) {
		nextGripAt = now + b.gripInterval;
		ServerPlayer farthest = null;
		double farthestSqr = -1;

		for (ServerPlayer player : session.participantsInHall()) {
			double sqr = player.distanceToSqr(vasch);

			if (player.isAlive() && !Holds.isHeld(player) && sqr > farthestSqr) {
				farthestSqr = sqr;
				farthest = player;
			}
		}

		if (farthest != null) {
			startGripOn(farthest, now, b);
		}
	}

	private void startGripOn(ServerPlayer farthest, long now, CollectorBalance b) {
		begin(Move.GRIP, farthest, OrrenVasch.Action.GRIP);
		// The next Surge waits until the whole Grip and the gap after it are over.
		nextSurgeAt = Math.max(nextSurgeAt, now + b.gripTellTicks + b.gripHoldTicks + b.gripSurgeGap);
		gripHolding = false;
		gripTaken = 0f;
		EffectEvents.toTracking(vasch, BossEffects.GRIP, farthest.position(), null, farthest.getId(), b.gripTellTicks, BossEffects.GRIP_TELL);
	}

	private void tickGrip(ServerLevel level, CollectorBalance b) {
		if (!(moveTarget instanceof ServerPlayer held) || !held.isAlive() || held.level() != level) {
			abortMove(false);
			return;
		}

		if (!gripHolding) {
			if (moveTicks < b.gripTellTicks) {
				return;
			}

			gripHolding = true;
			Holds.hold(held, b.gripHoldTicks, "collector_grip", held.position().add(0, 1.5, 0), true);
			EffectEvents.toTracking(vasch, BossEffects.GRIP, held.position(), null, held.getId(), 1f, BossEffects.GRIP_HOLD);
			return;
		}

		int holdTicks = moveTicks - b.gripTellTicks;

		if (!Holds.isHeld(held) || holdTicks >= b.gripHoldTicks) {
			Holds.release(held, "release");
			endMove();
			return;
		}

		if (holdTicks > 0 && holdTicks % 20 == 0) {
			ArenaSession session = vasch.session();
			held.hurtServer(level, BossDamageTypes.source(level, BossDamageTypes.ARCHIVE_GRIP, vasch, vasch), b.gripDamage * (session == null ? 1f : session.outgoing()));
			EffectEvents.toTracking(vasch, BossEffects.GRIP, held.position(), null, held.getId(), 0f, BossEffects.GRIP_HOLD);
		}
	}

	private void breakGrip(CollectorBalance b) {
		if (moveTarget instanceof ServerPlayer held) {
			Holds.release(held, "break");
			EffectEvents.toTracking(vasch, BossEffects.GRIP, held.position(), null, held.getId(), 1f, BossEffects.GRIP_BREAK);
		}

		endMove();
		vasch.counterStagger(b.gripBreakStaggerTicks);
	}
}

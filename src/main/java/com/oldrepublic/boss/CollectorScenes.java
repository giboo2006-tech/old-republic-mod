package com.oldrepublic.boss;

import java.util.List;

import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.BossOutcome;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The ending scenes of 8.7, played by the session after the owner's choice ({@code collector_resolve},
 * the variant is the scene):
 *
 * <ol start="0">
 * <li>Custody: two Republic troopers come down the descent ({@code Troopers.spawnEscort}), binders lock
 * and they walk him up and out (Spare on the Jedi path).</li>
 * <li>Spare on the Sith path: he rises, sets his blade down on the dais, and walks out alone.</li>
 * <li>Execute: a single stroke; he falls forward, his blade rolls away and gutters out, dust settles.</li>
 * <li>Bind: two Imperial troopers lock his wrists and march him out to the Empire.</li>
 * <li>Release: he bows his head once, takes up his satchel and walks out alone.</li>
 * <li>Bring him in: two troopers of the chosen army escort him out, as in 0 or 3.</li>
 * </ol>
 *
 * When the escort cannot be spawned he walks out alone, bound. A scene ends when he is gone (the
 * escort removes the party out of sight) or, for walk-outs and the fallen body, once nobody is left in
 * the Reliquary to see it; a long safety limit ends any scene.
 */
final class CollectorScenes {
	/** A running scene: {@link #tick} returns true when it is over (the session then ends). */
	interface Scene {
		boolean tick(long now);
	}

	/** Ticks a walk-out repaths. */
	private static final int REPATH_TICKS = 20;
	/** He stands up and turns before walking (custody: the binders lock first). */
	private static final int RISE_TICKS = 30;
	/** The stroke lands this many ticks after the scene starts; he lies still after it. */
	private static final int STROKE_TICKS = 12;
	/** A scene that is seen by nobody ends after at least this long. */
	private static final int MIN_SCENE_TICKS = 200;
	/** No scene lasts longer than this. */
	private static final int MAX_SCENE_TICKS = 2400;
	private static final double WALK_SPEED = 0.7;
	private static final double EXIT_REACH = 2.0;

	/** The scene of {@code outcome} for an owner on {@code path}. */
	static Scene start(ArenaSession session, OrrenVasch vasch, BossOutcome outcome, Path path) {
		int variant = variant(outcome, path);
		long start = session.level().getGameTime();
		BlockPos exit = exitOf(session.layout());
		Vec3 toExit = Vec3.atBottomCenterOf(exit).subtract(vasch.position());
		vasch.setYielded(false);
		vasch.setTarget(null);
		EffectEvents.toTracking(vasch, BossEffects.RESOLVE, vasch.position(), toExit, vasch.getId(), 1f, variant);

		return switch (variant) {
			case BossEffects.RESOLVE_EXECUTE -> execution(session, vasch, start);
			case BossEffects.RESOLVE_CUSTODY, BossEffects.RESOLVE_BOUND, BossEffects.RESOLVE_BROUGHT_IN ->
					escort(session, vasch, army(outcome), exit, start);
			default -> {
				if (variant == BossEffects.RESOLVE_WALK_OUT_SITH) {
					// He sets his blade down on the dais before he goes.
					vasch.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}

				yield walkOut(session, vasch, exit, start, false);
			}
		};
	}

	/** The scene variant (8.7): Spare is custody for Jedi (and any non-Sith path), the walk-out for Sith. */
	static int variant(BossOutcome outcome, Path path) {
		return switch (outcome) {
			case SPARED -> path == Path.SITH ? BossEffects.RESOLVE_WALK_OUT_SITH : BossEffects.RESOLVE_CUSTODY;
			case EXECUTED -> BossEffects.RESOLVE_EXECUTE;
			case BOUND -> BossEffects.RESOLVE_BOUND;
			case RELEASED -> BossEffects.RESOLVE_RELEASE;
			case BROUGHT_TO_REPUBLIC, BROUGHT_TO_EMPIRE -> BossEffects.RESOLVE_BROUGHT_IN;
			case ECHO -> BossEffects.RESOLVE_RELEASE;
		};
	}

	/** Whose troopers come for him. */
	static Faction army(BossOutcome outcome) {
		return outcome == BossOutcome.BOUND || outcome == BossOutcome.BROUGHT_TO_EMPIRE ? Faction.SITH_EMPIRE : Faction.REPUBLIC;
	}

	/** The echo dissolving: it fades over the effect's ticks, then it is gone. */
	static Scene dissolve(OrrenVasch vasch, long until) {
		return now -> {
			if (now >= until) {
				vasch.discard();
				return true;
			}

			return false;
		};
	}

	private static BlockPos exitOf(ArenaLayout layout) {
		return layout.descentTop() != null ? layout.descentTop() : layout.antechamberFloor();
	}

	/** Troopers lead him out; if none can be spawned he walks out alone, bound. */
	private static Scene escort(ArenaSession session, OrrenVasch vasch, Faction army, BlockPos exit, long start) {
		ServerLevel level = session.level();
		vasch.setAction(OrrenVasch.Action.CUFFED);
		List<LivingEntity> escorts = Troopers.spawnEscort(level, exit, army, vasch, exit);

		if (escorts.isEmpty()) {
			return walkOut(session, vasch, exit, start, true);
		}

		return now -> vasch.isRemoved() || now - start >= MAX_SCENE_TICKS && finish(vasch, escorts);
	}

	/** He rises and walks to the exit alone; gone once he is there (or nobody is watching any more). */
	private static Scene walkOut(ArenaSession session, OrrenVasch vasch, BlockPos exit, long start, boolean bound) {
		Vec3 target = Vec3.atBottomCenterOf(exit);
		vasch.setAction(bound ? OrrenVasch.Action.CUFFED : OrrenVasch.Action.NONE);

		return now -> {
			if (vasch.isRemoved()) {
				return true;
			}

			long age = now - start;

			if (age < RISE_TICKS) {
				vasch.getNavigation().stop();
				return false;
			}

			boolean arrived = vasch.position().distanceToSqr(target) <= EXIT_REACH * EXIT_REACH;

			if (arrived || age >= MAX_SCENE_TICKS || age >= MIN_SCENE_TICKS && nobodyInside(session)) {
				vasch.discard();
				return true;
			}

			if (age % REPATH_TICKS == 0) {
				vasch.getNavigation().moveTo(target.x, target.y, target.z, WALK_SPEED);
			}

			return false;
		};
	}

	/** One stroke; he falls forward and lies still while anyone is in the Reliquary to see it. */
	private static Scene execution(ArenaSession session, OrrenVasch vasch, long start) {
		vasch.setAction(OrrenVasch.Action.NONE);

		return now -> {
			if (vasch.isRemoved()) {
				return true;
			}

			long age = now - start;

			if (age >= STROKE_TICKS && !vasch.isFallen()) {
				vasch.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				vasch.setFallen(true);
			}

			if (age >= MAX_SCENE_TICKS || age >= MIN_SCENE_TICKS && nobodyInside(session)) {
				vasch.discard();
				return true;
			}

			return false;
		};
	}

	private static boolean finish(OrrenVasch vasch, List<LivingEntity> escorts) {
		for (LivingEntity escort : escorts) {
			if (!escort.isRemoved()) {
				escort.discard();
			}
		}

		vasch.discard();
		return true;
	}

	private static boolean nobodyInside(ArenaSession session) {
		for (ServerPlayer player : session.level().players()) {
			if (session.layout().isInReliquary(player.position())) {
				return false;
			}
		}

		return true;
	}

	private CollectorScenes() {
	}
}

package com.oldrepublic.force;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * How other features react to Force powers. The force feature asks here instead of knowing about
 * saber guards, NPC wind-ups or captives; features register a {@link Handler} during init (the
 * saber and enemy features for guards, wind-ups, the push clash and form costs; the identity
 * feature for captives). Every method has a neutral default, and the answers of all handlers are
 * combined. Server side only.
 */
public final class ForceReactions {
	/** Interrupt causes (frozen, spec 15.1): what interrupted a wind-up. */
	public static final String CAUSE_FORCE_PUSH = "force_push";
	public static final String CAUSE_FORCE_PULL = "force_pull";
	public static final String CAUSE_PULL_DISARM = "pull_disarm";
	public static final String CAUSE_SABER_THROW = "saber_throw";
	public static final String CAUSE_HEAVY_FEAR = "heavy_fear";
	public static final String CAUSE_STAGGER = "stagger";
	public static final String CAUSE_SHOCK = "shock";
	/** The cause of the one-argument {@link #interruptWindup(LivingEntity)}. */
	public static final String CAUSE_UNKNOWN = "unknown";

	/** One feature's reactions. Override only what the feature knows about. */
	public interface Handler {
		/** Whether {@code target} is guarding with a lit saber against something at {@code from}. */
		default boolean isGuarding(LivingEntity target, Vec3 from) {
			return false;
		}

		/** Drains {@code amount} saber stamina from a guarding target. Returns true if its guard broke. */
		default boolean drainGuard(LivingEntity target, float amount) {
			return false;
		}

		/** Gives back saber stamina (Serene Ward). */
		default void restoreGuard(LivingEntity target, float amount) {
		}

		/**
		 * A Force Push is about to move {@code target} (push clash on a shove wind-up, guard drain).
		 * Returns a multiplier for the knockback.
		 */
		default float onPushHit(ServerPlayer caster, LivingEntity target) {
			return 1f;
		}

		/** A stagger, shock or disarm should interrupt any attack wind-up of this (non-boss) NPC. */
		default void interruptWindup(LivingEntity target) {
		}

		/**
		 * The same with who and what interrupted ({@code CAUSE_*}). The default ignores both and
		 * calls the one-argument form, so handlers written before the causes keep working.
		 */
		default void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
			interruptWindup(target);
		}

		/**
		 * How high Telekinetic Lift may raise {@code target} when {@code requested} blocks are asked;
		 * the answers of all handlers are combined by minimum, and 0 refuses the lift.
		 */
		default float liftHeight(LivingEntity target, float requested) {
			return requested;
		}

		/**
		 * How many ticks Force Choke may hold {@code target} when {@code requested} are asked (Milestone 4,
		 * 9.3); the answers of all handlers are combined by minimum, and 0 refuses the hold. Bosses
		 * decide here (Kaast allows one short hold in his last phase).
		 */
		default int chokeTicks(LivingEntity target, int requested) {
			return requested;
		}

		/** Multiplier for damage from a player's area effects (shockwave, chain arcs, slam). */
		default float areaDamageMultiplier(LivingEntity target) {
			return 1f;
		}

		/** Extra multiplier on an ability's Force Energy cost (for example Ataru on Force Jump). */
		default float costMultiplier(ServerPlayer player, String abilityId) {
			return 1f;
		}
	}

	private static final List<Handler> HANDLERS = new ArrayList<>();

	public static void register(Handler handler) {
		HANDLERS.add(handler);
	}

	public static boolean isGuarding(LivingEntity target, Vec3 from) {
		for (Handler handler : HANDLERS) {
			if (handler.isGuarding(target, from)) {
				return true;
			}
		}

		return false;
	}

	public static boolean drainGuard(LivingEntity target, float amount) {
		boolean broke = false;

		for (Handler handler : HANDLERS) {
			broke |= handler.drainGuard(target, amount);
		}

		return broke;
	}

	public static void restoreGuard(LivingEntity target, float amount) {
		for (Handler handler : HANDLERS) {
			handler.restoreGuard(target, amount);
		}
	}

	public static float onPushHit(ServerPlayer caster, LivingEntity target) {
		float mult = 1f;

		for (Handler handler : HANDLERS) {
			mult *= handler.onPushHit(caster, target);
		}

		return mult;
	}

	/** Interrupts {@code target}'s wind-up for an unknown cause and caster. */
	public static void interruptWindup(LivingEntity target) {
		interruptWindup(target, null, CAUSE_UNKNOWN);
	}

	/** Interrupts {@code target}'s wind-up; {@code cause} is one of the {@code CAUSE_*} constants. */
	public static void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		for (Handler handler : HANDLERS) {
			handler.interruptWindup(target, caster, cause);
		}
	}

	/** The lift height every handler allows for {@code target} (their minimum, never below 0); 0 refuses. */
	public static float liftHeight(LivingEntity target, float requested) {
		float height = requested;

		for (Handler handler : HANDLERS) {
			height = Math.min(height, handler.liftHeight(target, requested));
		}

		return Math.max(0f, height);
	}

	/** The choke hold every handler allows for {@code target} (their minimum, never below 0); 0 refuses. */
	public static int chokeTicks(LivingEntity target, int requested) {
		int ticks = requested;

		for (Handler handler : HANDLERS) {
			ticks = Math.min(ticks, handler.chokeTicks(target, requested));
		}

		return Math.max(0, ticks);
	}

	public static float areaDamageMultiplier(LivingEntity target) {
		float mult = 1f;

		for (Handler handler : HANDLERS) {
			mult *= handler.areaDamageMultiplier(target);
		}

		return mult;
	}

	public static float costMultiplier(ServerPlayer player, String abilityId) {
		float mult = 1f;

		for (Handler handler : HANDLERS) {
			mult *= handler.costMultiplier(player, abilityId);
		}

		return mult;
	}

	private ForceReactions() {
	}
}

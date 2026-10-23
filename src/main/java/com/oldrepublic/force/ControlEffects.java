package com.oldrepublic.force;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Control effects of every power (stagger, slow, disarm, shock, hold, and from Milestone 5 fear,
 * stasis and pacify) and the common rule for push-resistant targets: within a window
 * ({@code force_push.staggerDrWindowTicks}, 200 ticks) the second control effect lasts half as long
 * and the third and later do nothing. Normal targets always get the full duration. Every duration
 * then goes through the registered {@link DurationModifier}s for its kind (Iron Discipline).
 */
public final class ControlEffects {
	/** Control kinds (frozen, spec 15.1). */
	public static final String STAGGER = "stagger";
	public static final String SLOW = "slow";
	public static final String DISARM = "disarm";
	public static final String SHOCK = "shock";
	public static final String HOLD = "hold";
	/**
	 * Milestone 5 control kinds (frozen, MILESTONE_5.md 9.2): a kind is only a string, so
	 * {@link #diminished(LivingEntity, int, String)} takes them as it is. The powers feature (W6) owns
	 * what they do: the status effects, the flee goal, the stasis and pacify cancels, and the
	 * {@link DurationModifier}s for bosses.
	 */
	public static final String FEAR = "fear";
	public static final String STASIS = "stasis";
	public static final String PACIFY = "pacify";

	/** Scales control durations on a target, for example an armour set bonus. */
	@FunctionalInterface
	public interface DurationModifier {
		/** Multiplier on a control of {@code kind} applied to {@code target}; 1 changes nothing. */
		float multiplier(LivingEntity target, String kind);
	}

	/** Control history of one push-resistant target. */
	private record Record(long windowStart, int count) {
	}

	private static final Map<UUID, Record> HISTORY = new HashMap<>();
	private static final List<DurationModifier> MODIFIERS = new ArrayList<>();

	/** Adds a duration modifier. Register during init. */
	public static void registerDurationModifier(DurationModifier m) {
		MODIFIERS.add(m);
	}

	/** {@link #diminished(LivingEntity, int, String)} for a stagger. */
	public static int diminished(LivingEntity target, int baseTicks) {
		return diminished(target, baseTicks, STAGGER);
	}

	/**
	 * Duration of a control effect of {@code kind} on {@code target} after diminishing returns and the
	 * duration modifiers. Records the application for push-resistant targets, so call it once per
	 * effect actually applied.
	 */
	public static int diminished(LivingEntity target, int baseTicks, String kind) {
		if (!ForcePowers.isResistant(target)) {
			return duration(target, baseTicks, kind);
		}

		long now = target.level().getGameTime();
		Record record = HISTORY.get(target.getUUID());

		if (record == null || now - record.windowStart() > ForcePush.balance().staggerDrWindowTicks) {
			record = new Record(now, 0);
		}

		int count = record.count();
		HISTORY.put(target.getUUID(), new Record(record.windowStart(), count + 1));
		return count >= 2 ? 0 : duration(target, baseTicks >> count, kind);
	}

	/**
	 * {@code ticks} of a control of {@code kind} on {@code target} after the duration modifiers only
	 * (no diminishing returns, nothing recorded): the full length of a first application.
	 */
	public static int duration(LivingEntity target, int ticks, String kind) {
		if (ticks <= 0 || MODIFIERS.isEmpty()) {
			return ticks;
		}

		float mult = 1f;

		for (DurationModifier m : MODIFIERS) {
			mult *= m.multiplier(target, kind);
		}

		return Math.max(0, Math.round(ticks * mult));
	}

	/** {@link #stagger(Entity, LivingEntity, int, int, String)} of kind {@link #STAGGER}. */
	public static int stagger(@Nullable Entity source, LivingEntity target, int baseTicks, int amplifier) {
		return stagger(source, target, baseTicks, amplifier, STAGGER);
	}

	/**
	 * Staggers {@code target}: slowness at {@code amplifier} for the diminished duration of
	 * {@code kind}, and any attack wind-up is interrupted (cause {@code stagger}). Returns the
	 * duration applied (0 if diminished away).
	 */
	public static int stagger(@Nullable Entity source, LivingEntity target, int baseTicks, int amplifier, String kind) {
		int ticks = diminished(target, baseTicks, kind);

		if (ticks > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier, false, false, true), source);
			ForceReactions.interruptWindup(target, source instanceof ServerPlayer player ? player : null, ForceReactions.CAUSE_STAGGER);
		}

		return ticks;
	}

	/** Drops histories whose window has passed. */
	static void prune(long now) {
		int window = ForcePush.balance().staggerDrWindowTicks;
		HISTORY.values().removeIf(r -> now - r.windowStart() > window);
	}

	static void clear() {
		HISTORY.clear();
	}

	private ControlEffects() {
	}
}

package com.oldrepublic.force;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Control effects of every power (stagger, disarm, shock) and the common rule for push-resistant
 * targets: within a window ({@code force_push.staggerDrWindowTicks}, 200 ticks) the second control
 * effect lasts half as long and the third and later do nothing. Normal targets always get the full
 * duration.
 */
public final class ControlEffects {
	/** Control history of one push-resistant target. */
	private record Record(long windowStart, int count) {
	}

	private static final Map<UUID, Record> HISTORY = new HashMap<>();

	/**
	 * Duration of a control effect on {@code target} after diminishing returns. Records the
	 * application for push-resistant targets, so call it once per effect actually applied.
	 */
	public static int diminished(LivingEntity target, int baseTicks) {
		if (!ForcePowers.isResistant(target)) {
			return baseTicks;
		}

		long now = target.level().getGameTime();
		Record record = HISTORY.get(target.getUUID());

		if (record == null || now - record.windowStart() > ForcePush.balance().staggerDrWindowTicks) {
			record = new Record(now, 0);
		}

		int count = record.count();
		HISTORY.put(target.getUUID(), new Record(record.windowStart(), count + 1));
		return count >= 2 ? 0 : baseTicks >> count;
	}

	/**
	 * Staggers {@code target}: slowness at {@code amplifier} for the diminished duration, and any
	 * attack wind-up is interrupted. Returns the duration applied (0 if diminished away).
	 */
	public static int stagger(@Nullable Entity source, LivingEntity target, int baseTicks, int amplifier) {
		int ticks = diminished(target, baseTicks);

		if (ticks > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amplifier), source);
			ForceReactions.interruptWindup(target);
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

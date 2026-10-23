package com.oldrepublic.saber.duel;

import java.util.function.Supplier;

import com.oldrepublic.enemy.AcolyteBalance;

/**
 * Server-side guard bookkeeping of one saber NPC: stamina, the broken and staggered windows, how
 * often it was hit lately (it guards more after taking hits) and a pending wind-up interrupt.
 * Created by {@link NpcGuard#register}; regeneration is applied lazily whenever it is read.
 */
public final class DuelState {
	/** Hits older than this no longer raise the guard chance. */
	private static final int RECENT_HIT_TICKS = 80;

	private final Supplier<DuelistProfile> profile;
	private float stamina = Float.NaN;
	private long lastDrainTick = Long.MIN_VALUE / 2;
	private long lastUpdateTick = Long.MIN_VALUE;
	private long brokenUntil;
	private long staggeredUntil;
	private long lastHurtTick = Long.MIN_VALUE / 2;
	private int recentHits;
	private boolean interrupt;

	DuelState(Supplier<DuelistProfile> profile) {
		this.profile = profile;
	}

	public DuelistProfile profile() {
		return profile.get();
	}

	public float maxStamina() {
		return profile().guardStamina();
	}

	/** Stamina at {@code now}, after regeneration. */
	public float stamina(long now) {
		update(now);
		return stamina;
	}

	public boolean isBroken(long now) {
		return now < brokenUntil;
	}

	public boolean isStaggered(long now) {
		return now < staggeredUntil;
	}

	/** Hits taken within the last few seconds. */
	public int recentHits(long now) {
		return now - lastHurtTick > RECENT_HIT_TICKS ? 0 : recentHits;
	}

	/** True once after something asked to interrupt the current wind-up (a stagger, shock or Push). */
	public boolean consumeInterrupt() {
		boolean value = interrupt;
		interrupt = false;
		return value;
	}

	void requestInterrupt() {
		interrupt = true;
	}

	void stagger(long until) {
		staggeredUntil = Math.max(staggeredUntil, until);
		interrupt = true;
	}

	void onHurt(long now) {
		recentHits = recentHits(now) + 1;
		lastHurtTick = now;
	}

	/** Spends stamina; returns true if this emptied the pool. Infinite pools never empty. */
	boolean drain(float amount, long now) {
		update(now);

		if (!Float.isFinite(stamina)) {
			return false;
		}

		stamina = Math.max(0f, stamina - amount);
		lastDrainTick = now;
		return stamina <= 0f;
	}

	void restore(float amount, long now) {
		update(now);
		stamina = Math.min(maxStamina(), stamina + amount);
	}

	void breakGuard(long now, int ticks) {
		stamina = 0f;
		lastDrainTick = now;
		brokenUntil = now + ticks;
		interrupt = true;
	}

	/** Refills the pool (commands and tests). */
	public void refill() {
		stamina = maxStamina();
		brokenUntil = 0;
		staggeredUntil = 0;
	}

	private void update(long now) {
		float max = maxStamina();

		if (Float.isNaN(stamina) || !Float.isFinite(max)) {
			stamina = max;
			lastUpdateTick = now;
			return;
		}

		AcolyteBalance b = AcolyteBalance.get();
		long from = Math.max(lastUpdateTick, Math.max(lastDrainTick + b.guardRegenDelayTicks, brokenUntil));
		lastUpdateTick = now;

		if (now > from && stamina < max) {
			stamina = Math.min(max, stamina + b.guardRegen * (now - from) / 20f);
		}

		stamina = Math.min(stamina, max);
	}
}

package com.oldrepublic.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An entity that can yield instead of dying. The identity feature clamps the damage that would take
 * it below {@link #yieldHealthFraction()}, puts it into the yielded state and runs the Spare /
 * Execute decision; the entity itself handles its pose and goals.
 */
public interface Yieldable {
	/** False while yielding is not possible (for example a hostile acolyte outside an encounter). */
	boolean canYield();

	/** Fraction of max health at which it yields. */
	float yieldHealthFraction();

	/** The player who decides its fate, or null when any player may. */
	@Nullable UUID yieldDecider();

	boolean isYielded();

	/** Enters or leaves the yielded state; the entity handles its pose and goals. */
	void setYielded(boolean yielded);

	/** Called once with the decision. */
	void onYieldResolved(YieldOutcome outcome);
}

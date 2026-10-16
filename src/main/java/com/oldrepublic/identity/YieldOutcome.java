package com.oldrepublic.identity;

/** How the deciding player resolved an enemy that yielded. */
public enum YieldOutcome {
	SPARED,
	EXECUTED,
	FLED,
	/** Questioned instead of judged (the {@code patrol_intercept} yield). */
	INTERROGATED,
	/** Handed over to an army (the I2 yield). */
	HANDED_OVER
}

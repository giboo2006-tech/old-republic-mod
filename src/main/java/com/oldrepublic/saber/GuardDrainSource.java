package com.oldrepublic.saber;

/**
 * A projectile that says how much saber guard stamina blocking it costs (spec 10.7). The guard uses
 * it for heavy and non-deflectable projectiles; without it those cost
 * {@code saber.heavyBoltGuardDrain} and {@code saber.nonDeflectableGuardDrain}.
 */
public interface GuardDrainSource {
	/** Guard stamina a block of this projectile drains, before the form's drain multiplier. */
	float guardDrain();
}

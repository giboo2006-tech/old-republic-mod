package com.oldrepublic.force.barrier;

/**
 * An attacker whose melee blows a Force Barrier does not soften (spec MILESTONE_4.md 10.5: Torvin
 * Kaast's vibro-axe of Mandalorian iron). Bolts and blasts are still handled as usual.
 */
public interface BarrierPiercing {
	/** Whether this attacker's melee passes the barrier now. */
	boolean piercesBarrier();
}

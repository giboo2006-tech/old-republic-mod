package com.oldrepublic.client.travel;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state of a courier shuttle: its pose, look and the animation clocks of its moving parts. */
public class CourierShuttleRenderState extends EntityRenderState {
	public float yRot;
	public float xRot;
	/** {@code ShipState} ordinal, hull integrity, the synced 4.6 phase (or ground) and the upgrade mask. */
	public int state;
	public int hull;
	public int phase;
	public int upgrades;
	public int paint;
	public String hullCode = "";
	/** 0 retracted to 1 deployed (struts), closed to lowered (ramp). */
	public float struts = 1f;
	public float ramp = 1f;
	/** 0 none to 1 fully pulled back (the tarp and jacks of a hulk standing up); 1 when the ship is no hulk. */
	public float standUp;
	/** 0 to 1: how hard the engines run (the nozzle glow). */
	public float engine;
	/** 0 to 1: the running lights (the blink, or steady with flashing off); 0 for a hulk. */
	public float lights;
	/** Below the jump minimum: one running light is dead. */
	public boolean lightDead;
	/** Hidden while it is in hyperspace (the body waits over the pad). */
	public boolean hidden;
}

package com.oldrepublic.client.travel;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Render state of a ship: the courier shuttle's, and the interceptor's (MILESTONE_5.md 4.9): its
 * pose, look, the animation clocks of its moving parts, and where its class hangs its weapons.
 */
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
	/** 0 retracted to 1 deployed (struts), closed to lowered (ramp; the interceptor's canopy: shut to open). */
	public float struts = 1f;
	public float ramp = 1f;
	/** 0 none to 1 fully pulled back (the tarp and jacks of a hulk standing up); 1 when the ship is no hulk. */
	public float standUp;
	/** 0 to 1: how hard the engines run (the nozzle glow). */
	public float engine;
	/** 0 to 1: the running lights (the blink, a disabled ship's flicker, or steady with flashing off); 0 for a hulk. */
	public float lights;
	/** Below the jump minimum: one running light is dead. */
	public boolean lightDead;
	/** Hidden while it is in hyperspace (the body waits over the pad). */
	public boolean hidden;
	/** The class's cannon muzzles, turret pivot and missile rack, in ship space (blocks): the model hangs those parts there. */
	public double[][] cannonsAt = new double[2][3];
	public double[] turretAt = new double[3];
	public double[] rackAt = new double[3];
	/** The gunner turret's aim against the hull, in radians: round from the nose, and up from level. */
	public float turretYaw;
	public float turretPitch;
}

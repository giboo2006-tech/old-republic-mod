package com.oldrepublic.client.enemy;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state of a frag grenade. */
public class FragGrenadeRenderState extends EntityRenderState {
	public float yRot;
	/** Resting on the ground: it lies on its side instead of tumbling. */
	public boolean landed;
	/** Whether the fuse is burning, ticks since it was lit (with partial tick) and its length. */
	public boolean fuseStarted;
	public float fuseTicks;
	public int fuseLength = 40;
	/** The player's flashing-effects setting: the fuse light is steady when it is off. */
	public boolean flashing = true;
}

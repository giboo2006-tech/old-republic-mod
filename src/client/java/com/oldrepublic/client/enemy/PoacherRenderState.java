package com.oldrepublic.client.enemy;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of a Crystal Poacher. */
public class PoacherRenderState extends HumanoidRenderState {
	/** The attack tell is showing. */
	public boolean charging;
	/** Ticks (with partial tick) the current tell has been showing. */
	public float chargeTicks;
}

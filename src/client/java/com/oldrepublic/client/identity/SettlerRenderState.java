package com.oldrepublic.client.identity;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of a Frontier Settler. */
public class SettlerRenderState extends HumanoidRenderState {
	/** Skin variant, 0 to {@code FrontierSettler.VARIANTS - 1}. */
	public int variant;
	/** Bound at a post: kneeling, hands behind the back, rope on the skin. */
	public boolean captive;
}

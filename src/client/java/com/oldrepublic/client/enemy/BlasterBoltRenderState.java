package com.oldrepublic.client.enemy;

import com.oldrepublic.enemy.BlasterBolt;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state of a blaster bolt. */
public class BlasterBoltRenderState extends EntityRenderState {
	public float yRot;
	public float xRot;
	public boolean deflected;
	/** Who fired it: the bolt's colour. */
	public BlasterBolt.Style style = BlasterBolt.Style.POACHER;
	/** A heavy bolt (the sentinel lance) is drawn thicker and longer. */
	public boolean heavy;
}

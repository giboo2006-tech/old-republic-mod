package com.oldrepublic.client.space;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state of the blockade gunship (spec MILESTONE_5.md 5.5, 15.3): its heading, bay, generator and break-up. */
public class GunshipRenderState extends EntityRenderState {
	/** Heading (degrees); the gunship never pitches. */
	public float yRot;
	/** 0 closed to 1 open: the bay doors. */
	public float bay;
	/** The shield generator stands on its spine (synced). */
	public boolean shielded;
	/** 0 whole to 1 fully apart: the two halves drifting away from the break after the end blast. */
	public float breakUp;
	/** 0 to 1: the engines' glow and the generator core's pulse (steady with flashing effects off). */
	public float engine;
	public float core;
}

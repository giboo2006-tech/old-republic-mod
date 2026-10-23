package com.oldrepublic.client.space;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render state of a starfighter (spec MILESTONE_5.md 5.5): its pose, its bank and the engines' glow. */
public class StarfighterRenderState extends EntityRenderState {
	/** Heading and pitch (degrees, the entity's own convention: positive pitch is nose down). */
	public float yRot;
	public float xRot;
	/** Bank (degrees, positive to the right), from how fast it turns. */
	public float bank;
	/** Half its height: the model turns about the middle of its box. */
	public float halfHeight;
	/** 0 to 1: the exhaust's glow (a flicker, steady with flashing effects off). */
	public float engine;
	/** Destroyed: nothing is drawn (the explosion is the {@code ship_destroyed} event's). */
	public boolean hidden;
}

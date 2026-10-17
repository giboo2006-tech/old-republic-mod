package com.oldrepublic.client.boss;

import com.oldrepublic.boss.OrrenVasch;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of Orren Vasch: what the model poses and the renderer tints. */
public class OrrenVaschRenderState extends HumanoidRenderState {
	/** The fight phase, 1 to 3 (phase 3: the Unkept skin, hood up, the holocron in the off-hand). */
	public int phase = 1;
	/** What his body is doing (synced). */
	public OrrenVasch.Action action = OrrenVasch.Action.NONE;
	/** Ticks since {@link #action} last changed, with the partial tick. */
	public float actionAge;
	/** 1 while kneeling (yielded, or Peaceful), else 0. */
	public float kneel;
	/** The rematch's echo: the desaturated skins, drawn translucent. */
	public boolean echo;
	/** The echo's presence, 0 to 1: it gathers out of smoke and drains back into the floor. */
	public float presence = 1f;
	/** How far he has fallen forward after the execution's stroke, 0 to 1. */
	public float fall;
	/** The stolen holocron shows in his off-hand (phase 3). */
	public boolean holocron;
	/** Flashing effects on: the cloak's fraying may flutter; off, every motion stays steady. */
	public boolean flashing = true;
}

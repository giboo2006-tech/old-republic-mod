package com.oldrepublic.client.enemy;

import com.oldrepublic.enemy.SithAcolyte;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of a Sith Acolyte. */
public class AcolyteRenderState extends HumanoidRenderState {
	public SithAcolyte.Skin skin = SithAcolyte.Skin.ACOLYTE;
	/** 1 while kneeling (yielded, or lost a spar), else 0. */
	public float kneel;
	/** The Force Shove wind-up: the free hand thrust forward. */
	public boolean shoving;
}

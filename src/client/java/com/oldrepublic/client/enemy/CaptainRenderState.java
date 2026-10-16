package com.oldrepublic.client.enemy;

import com.oldrepublic.enemy.PoacherCaptain;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of the Poacher Captain. */
public class CaptainRenderState extends HumanoidRenderState {
	public PoacherCaptain.Action action = PoacherCaptain.Action.IDLE;
	/** Ticks (with partial tick) the current action has been showing. */
	public float actionTicks;
	/** 1 while yielded (kneeling), else 0. */
	public float kneel;
}

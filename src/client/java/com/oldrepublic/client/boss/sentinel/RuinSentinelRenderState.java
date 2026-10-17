package com.oldrepublic.client.boss.sentinel;

import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.construct.SentinelVariant;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of a Ruin Sentinel, all from its synced data. */
public class RuinSentinelRenderState extends HumanoidRenderState {
	public SentinelVariant variant = SentinelVariant.WATCHER;
	/** Standing as a statue (dormant). */
	public boolean dormant = true;
	/** 0 to 1: how far a dormant Watcher has lowered its head for a passing player of its side. */
	public float bow;
	public RuinSentinel.Action action = RuinSentinel.Action.IDLE;
	/** Ticks (with the partial tick) since the current action began. */
	public float actionAge;
	/** Flashing effects on: flickers and pulses; off: steady glows. */
	public boolean flashing = true;
}

package com.oldrepublic.client.enemy;

import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperRole;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of a Republic or Imperial trooper, filled from synced data only. */
public class TrooperRenderState extends HumanoidRenderState {
	/** Republic (true) or Imperial (false). */
	public boolean republic = true;
	/** The role after the renderer's skin fallback (a Republic grenadier or an Imperial medic draws as a rifleman). */
	public TrooperRole role = TrooperRole.RIFLEMAN;
	public TrooperAction action = TrooperAction.IDLE;
	/** Ticks (with partial tick) since the action last changed. */
	public float actionTicks;
	public BlasterBolt.Style boltStyle = BlasterBolt.Style.REPUBLIC;
	/** Lying on its side, waiting to be tended. */
	public boolean downed;
	/** The weapon is knocked away (Disarming Pull): hands empty. */
	public boolean disarmed;
	/** 1 while yielded or kneeling (heal, tend), blended for the pose; 0 standing. */
	public float kneel;
	/** The main hand holds the long rifle (riflemen and sergeants), else the pistol or nothing. */
	public boolean rifle;
	/** The main hand holds a weapon that is drawn (not hidden). */
	public boolean armed;
	/** Fighting: the weapon is up even between tells. */
	public boolean aggressive;
	/** The player's flashing-effects setting, so tells glow steadily when it is off. */
	public boolean flashing = true;
	/** Age in ticks with partial tick, for blinking. */
	public float age;
}

package com.oldrepublic.client.enemy.mandalorian;

import com.oldrepublic.enemy.mandalorian.MandalorianHunter;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Render state of a Mandalorian hunter or of Torvin Kaast, from synced data only. The Kaast fields
 * stay at their defaults for a hunter.
 */
public class HunterRenderState extends HumanoidRenderState {
	/** The painted plate variant (0 to 3). */
	public int paint;
	/** The rifle's aim tell (shouldered, sighting along the look). */
	public boolean aiming;
	/** The gadget whose tell shows ({@code MandalorianHunter.GADGET_*}). */
	public int gadget = MandalorianHunter.GADGET_NONE;
	/** Ticks the current tell has shown, with the partial tick (the pose eases in). */
	public float tellTicks;
	public boolean flaming;
	public boolean jetting;
	/** Knelt in a yield. */
	public boolean yielded;

	/** Kaast: the vibro-axe is out (phase 3); the rifle is slung. */
	public boolean axe;
	/** Kaast: the axe drawn back for the combo's third blow. */
	public boolean windup;
	/** Kaast: kneeling to set a mine. */
	public boolean kneeling;
	/** Kaast: knelt in his yield with the helmet off, held at his side. */
	public boolean unmasked;
	public boolean staggered;
	/** Kaast: the cracked helmet of phase 3. */
	public boolean cracked;
	/** Kaast: the daily hunter's echo (drawn pale and see-through). */
	public boolean echo;
}

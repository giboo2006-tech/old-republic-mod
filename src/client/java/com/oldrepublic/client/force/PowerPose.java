package com.oldrepublic.client.force;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Arm pose of a Force power (other than Push, see {@link PushPose}) carried on a humanoid render
 * state and applied by the humanoid model after its vanilla animation.
 *
 * @param kind     which power pose
 * @param amount   0 to 1, how far into the pose the arms are (eases in and out)
 * @param phase    kind-specific second value: Pull 0 while reaching, 1 when yanked back to the chest;
 *                 Lift the pose age in ticks, which drives the tremble
 * @param leftArm  the free arm is the left one
 * @param bothArms both arms take the pose (Lightning without a saber; Heal, Jump and glide always)
 */
public record PowerPose(PowerAnimations.Kind kind, float amount, float phase, boolean leftArm, boolean bothArms) {
	public static final RenderStateDataKey<PowerPose> KEY = RenderStateDataKey.create(() -> "oldrepublic:power_pose");
	/**
	 * How far a creature held by Telekinetic Lift flails (0 to 1), on any living render state; null
	 * when it is not lifted. The humanoid model swings its arms and legs for balance by it.
	 */
	public static final RenderStateDataKey<Float> FLAIL = RenderStateDataKey.create(() -> "oldrepublic:lift_flail");
}

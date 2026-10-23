package com.oldrepublic.client.saber;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Saber arm pose carried on a living entity's render state and applied by the humanoid model after
 * its vanilla animation (and after the Force poses).
 *
 * @param kind     which pose
 * @param amount   0 to 1, how far into the pose the arm is (eases in and out)
 * @param progress 0 to 1 through the pose's motion (the finisher's chop, the heavy sweep...)
 * @param leftArm  the saber is in the left arm
 */
public record SaberPose(SaberAnimations.Kind kind, float amount, float progress, boolean leftArm) {
	public static final RenderStateDataKey<SaberPose> KEY = RenderStateDataKey.create(() -> "oldrepublic:saber_pose");
}

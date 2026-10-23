package com.oldrepublic.client.equipment;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * A blaster arm pose carried on a player's render state and applied by the humanoid model after its
 * vanilla animation ({@link BlasterPoses}).
 *
 * @param pistol  a one-handed pistol aim; otherwise a long gun, which the vanilla two-handed hold
 *                already shoulders and this only kicks
 * @param amount  0 to 1, how far the pistol arm is raised
 * @param kick    0 to 1, the recoil of the last shot (arms jerk up a little); a scatter blast's up to 1.5
 * @param leftArm the gun is in the left arm
 */
public record BlasterPose(boolean pistol, float amount, float kick, boolean leftArm) {
	public static final RenderStateDataKey<BlasterPose> KEY = RenderStateDataKey.create(() -> "oldrepublic:blaster_pose");
}

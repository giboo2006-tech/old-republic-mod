package com.oldrepublic.client.force;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/**
 * Push arm pose carried on a humanoid render state. Any renderer may set {@link #KEY} (the player
 * renderer does it through {@code AvatarRendererMixin}; the Shadow Echo can reuse it) and the
 * humanoid model applies it after its vanilla animation.
 *
 * @param draw     0 to 1, how far the arm is drawn back while charging
 * @param thrust   0 to 1, how far the open palm is thrust forward after release
 * @param leftArm  true to animate the left arm, false for the right
 */
public record PushPose(float draw, float thrust, boolean leftArm) {
	public static final RenderStateDataKey<PushPose> KEY = RenderStateDataKey.create(() -> "oldrepublic:push_pose");
}

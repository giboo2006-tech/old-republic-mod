package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The attack tell on the rifle: a red-orange glow at the muzzle that grows over the 0.6 s charge
 * and pulses just before the shot. Never reduced by quality presets (tells stay readable).
 */
final class MuzzleGlowLayer extends RenderLayer<PoacherRenderState, PoacherModel> {
	/** Muzzle tip relative to the hand, in the held item's frame (see models/item/blaster_rifle_in_hand.json). */
	private static final float MUZZLE_UP = 0.07f;
	private static final float MUZZLE_ALONG = -0.72f;
	private static final float FULL_CHARGE_TICKS = 12f;

	MuzzleGlowLayer(RenderLayerParent<PoacherRenderState, PoacherModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, PoacherRenderState state, float yRot, float xRot) {
		if (!state.charging || state.isInvisible) {
			return;
		}

		HumanoidArm arm = state.mainArm;
		float progress = Mth.clamp(state.chargeTicks / FULL_CHARGE_TICKS, 0f, 1f);
		float pulse = progress > 0.66f ? 0.85f + 0.15f * Mth.sin(state.chargeTicks * 2.2f) : 1f;
		float core = (0.03f + 0.05f * progress) * pulse;
		float halo = (0.07f + 0.13f * progress) * pulse;
		int coreAlpha = (int) (150 + 105 * progress);
		int haloAlpha = (int) (50 + 70 * progress);

		poseStack.pushPose();
		getParentModel().translateToHand(state, arm, poseStack);
		// Same frame the held item is drawn in (vanilla ItemInHandLayer), then out to the muzzle.
		// API-CHECK: ItemInHandLayer#submitArmWithItem transform (rotX -90, rotY 180, translate) as in 1.21.
		poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
		poseStack.mulPose(Axis.YP.rotationDegrees(180f));
		poseStack.translate((arm == HumanoidArm.LEFT ? -1f : 1f) / 16f, 0.125f, -0.625f);
		poseStack.translate(0f, MUZZLE_UP, MUZZLE_ALONG);

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.flare(pose, buffer, halo, (haloAlpha << 24) | 0xFF3A1C);
			GlowGeometry.flare(pose, buffer, core, (coreAlpha << 24) | 0xFFC8A0);
		});
		poseStack.popPose();
	}
}

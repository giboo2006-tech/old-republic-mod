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
 * The Poacher Captain's pistol tell: a red-orange glow at the muzzle of the off-hand pistol that
 * grows over the tell. Never reduced by quality presets (tells stay readable).
 */
final class PistolGlowLayer extends RenderLayer<CaptainRenderState, CaptainModel> {
	/** Muzzle tip relative to the hand, in the held item's frame (see models/item/blaster_pistol.json). */
	private static final float MUZZLE_UP = 0.12f;
	private static final float MUZZLE_ALONG = -0.32f;
	private static final float FULL_TICKS = 12f;

	PistolGlowLayer(RenderLayerParent<CaptainRenderState, CaptainModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CaptainRenderState state, float yRot, float xRot) {
		if (!CaptainModel.aimingPistol(state) || state.isInvisible) {
			return;
		}

		HumanoidArm arm = state.mainArm.getOpposite();
		float progress = Mth.clamp(state.actionTicks / FULL_TICKS, 0f, 1f);
		float core = 0.04f + 0.05f * progress;
		float halo = 0.10f + 0.16f * progress;
		int coreAlpha = (int) (170 + 85 * progress);
		int haloAlpha = (int) (80 + 110 * progress);

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

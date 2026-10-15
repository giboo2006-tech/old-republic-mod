package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.enemy.BlasterBolt;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;

/**
 * Draws a blaster bolt as an elongated, full-bright core inside two layers of additive red glow;
 * the outer layers reach further back than forward, which reads as a short fading trail. Bolt
 * geometry runs along +X and is rotated like vanilla arrows.
 */
public class BlasterBoltRenderer extends EntityRenderer<BlasterBolt, BlasterBoltRenderState> {
	private static final int CORE = 0xFFFFE4DC;
	private static final int CORE_DEFLECTED = 0xFFFFF4EE;
	private static final int GLOW = 0xA0FF2A18;
	private static final int HAZE = 0x40FF1A0C;

	public BlasterBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public BlasterBoltRenderState createRenderState() {
		return new BlasterBoltRenderState();
	}

	@Override
	public void extractRenderState(BlasterBolt bolt, BlasterBoltRenderState state, float partialTick) {
		super.extractRenderState(bolt, state, partialTick);
		state.yRot = bolt.getYRot(partialTick);
		state.xRot = bolt.getXRot(partialTick);
		state.deflected = bolt.isDeflected();
	}

	@Override
	protected int getBlockLightLevel(BlasterBolt bolt, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(BlasterBoltRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.125f, 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot));

		int core = state.deflected ? CORE_DEFLECTED : CORE;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.box(pose, buffer, -1.6f, -0.22f, -0.22f, 0.6f, 0.22f, 0.22f, HAZE);
			GlowGeometry.box(pose, buffer, -1.15f, -0.11f, -0.11f, 0.55f, 0.11f, 0.11f, GLOW);
			GlowGeometry.box(pose, buffer, -0.7f, -0.045f, -0.045f, 0.45f, 0.045f, 0.045f, core);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

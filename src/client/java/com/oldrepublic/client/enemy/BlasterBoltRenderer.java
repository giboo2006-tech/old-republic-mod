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
 * Draws a blaster bolt as an elongated, full-bright core inside two layers of additive glow in the
 * colour of whoever fired it (spec 7.1): orange "scavenged cells" for poachers, blue-white for the
 * Republic, crimson for the Empire, amber-white for the sentinel lance. The outer layers reach
 * further back than forward, which reads as a short fading trail. A heavy bolt is thicker and
 * longer; a deflected bolt burns a little hotter. Bolt geometry runs along +X and is rotated like
 * vanilla arrows.
 */
public class BlasterBoltRenderer extends EntityRenderer<BlasterBolt, BlasterBoltRenderState> {
	/** Core, deflected core, glow and haze colours (ARGB) of one bolt style. */
	private record Palette(int core, int coreDeflected, int glow, int haze) {
	}

	private static final Palette POACHER = new Palette(0xFFFFEAD2, 0xFFFFF6EA, 0xA0FF7A1A, 0x40FF5410);
	private static final Palette REPUBLIC = new Palette(0xFFEAF4FF, 0xFFF6FAFF, 0xA03C8CFF, 0x401E5AFF);
	private static final Palette IMPERIAL = new Palette(0xFFFFE0E0, 0xFFFFF0F0, 0xA0E0142E, 0x40B00A22);
	private static final Palette SENTINEL = new Palette(0xFFFFF8E6, 0xFFFFFCF4, 0xB0FFB43A, 0x50FF9412);
	/** A heavy bolt's thickness and length, relative to a normal one. */
	private static final float HEAVY_WIDTH = 1.6f;
	private static final float HEAVY_LENGTH = 1.3f;

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
		state.style = bolt.style();
		state.heavy = bolt.heavy();
	}

	private static Palette palette(BlasterBolt.Style style) {
		return switch (style) {
			case POACHER -> POACHER;
			case REPUBLIC -> REPUBLIC;
			case IMPERIAL -> IMPERIAL;
			case SENTINEL -> SENTINEL;
		};
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

		Palette palette = palette(state.style);
		int core = state.deflected ? palette.coreDeflected() : palette.core();
		float w = state.heavy ? HEAVY_WIDTH : 1f;
		float l = state.heavy ? HEAVY_LENGTH : 1f;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.box(pose, buffer, -1.6f * l, -0.22f * w, -0.22f * w, 0.6f * l, 0.22f * w, 0.22f * w, palette.haze());
			GlowGeometry.box(pose, buffer, -1.15f * l, -0.11f * w, -0.11f * w, 0.55f * l, 0.11f * w, 0.11f * w, palette.glow());
			GlowGeometry.box(pose, buffer, -0.7f * l, -0.045f * w, -0.045f * w, 0.45f * l, 0.045f * w, 0.045f * w, core);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

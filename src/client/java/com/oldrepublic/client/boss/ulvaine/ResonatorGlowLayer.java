package com.oldrepublic.client.boss.ulvaine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A resonator's inner light (spec MILESTONE_5.md 15.3: "the spires' inner light (steady)"): the
 * model drawn again, full-bright and additive, with a texture holding only the crystal's veins of
 * light ({@code textures/entity/ulvaine/hymn_resonator_glow.png}). It never flickers; it goes out
 * as the spire breaks.
 */
final class ResonatorGlowLayer extends RenderLayer<ResonatorRenderer.State, ResonatorModel> {
	private static final Identifier GLOW = OldRepublicMod.id("textures/entity/ulvaine/hymn_resonator_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	ResonatorGlowLayer(RenderLayerParent<ResonatorRenderer.State, ResonatorModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, ResonatorRenderer.State state, float yRot, float xRot) {
		if (state.isInvisible || state.light <= 0.01f) {
			return;
		}

		int grey = (int) (Mth.clamp(state.light, 0f, 1f) * 255f);
		int tint = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
		// (model, state, pose, render type, light, overlay, tint, sprite, outline colour, crumbling)
		collector.submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint, null,
				state.outlineColor, null);
	}
}

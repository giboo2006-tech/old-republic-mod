package com.oldrepublic.client.enemy.stormback;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A charged stormback's crackling plates: the model drawn again, full-bright and additive, with a
 * texture that holds only the veins of light along its plates
 * ({@code textures/entity/stormback/stormback_charged.png}). The light flickers with flashing effects
 * on and holds steady with them off (spec MILESTONE_5.md 15).
 */
final class StormbackChargeLayer extends RenderLayer<StormbackRenderer.State, StormbackModel> {
	private static final Identifier VEINS = OldRepublicMod.id("textures/entity/stormback/stormback_charged.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	StormbackChargeLayer(RenderLayerParent<StormbackRenderer.State, StormbackModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, StormbackRenderer.State state, float yRot, float xRot) {
		if (!state.charged || state.isInvisible) {
			return;
		}

		float level;

		if (Vfx.flashing()) {
			// Irregular crackle: two beating waves and a quick stutter, never a regular blink.
			float t = state.ageInTicks + state.seed * 7.3f;
			level = 0.55f + 0.25f * Mth.sin(t * 0.9f) * Mth.sin(t * 0.37f) + (Mth.sin(t * 3.1f) > 0.7f ? 0.2f : 0f);
		} else {
			level = 0.7f;
		}

		int grey = (int) (Mth.clamp(level, 0f, 1f) * 255f);
		int tint = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
		// (model, state, pose, render type, light, overlay, tint, sprite, outline colour, crumbling)
		collector.submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(VEINS), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint, null,
				state.outlineColor, null);
	}
}

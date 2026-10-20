package com.oldrepublic.client.boss.cantor;

import java.util.function.ToDoubleFunction;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The light in a spectral figure's robes (the First Cantor's and his choir's): the model drawn again,
 * full-bright and additive, with a texture that holds only the glowing hems, runes and mask
 * ({@code <name>_glow.png}). Its strength comes from the render state (0 to 1): steady, never a
 * blink, so it needs no flashing-effects fallback.
 */
final class SpectralGlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
	private static final int FULL_BRIGHT = 0xF000F0;

	private final Identifier texture;
	private final ToDoubleFunction<S> strength;

	SpectralGlowLayer(RenderLayerParent<S, M> parent, Identifier texture, ToDoubleFunction<S> strength) {
		super(parent);
		this.texture = texture;
		this.strength = strength;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		float level = (float) Mth.clamp(strength.applyAsDouble(state), 0.0, 1.0);

		if (state.isInvisible || level <= 0.01f) {
			return;
		}

		int grey = (int) (level * 255f);
		int tint = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
		// (model, state, pose, render type, light, overlay, tint, sprite, outline colour, crumbling)
		collector.submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(texture), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint, null,
				state.outlineColor, null);
	}
}

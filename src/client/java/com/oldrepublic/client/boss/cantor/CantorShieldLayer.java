package com.oldrepublic.client.boss.cantor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.client.enemy.hymn.FoeGlow;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * The choir's shield on the First Cantor (spec MILESTONE_5.md 12.3: while any echo sings, he is
 * shielded): his figure drawn again a little larger, faint pale gold and full-bright, so the players
 * see that their blows will not land. It shimmers slowly with flashing effects on and holds steady
 * with them off.
 */
final class CantorShieldLayer extends RenderLayer<CantorRenderer.State, CantorModel> {
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int SHIELD_RGB = 0xFFE3A6;
	/** Where his middle is in the model's space (y down, blocks): the shell grows about it. */
	private static final float MIDDLE = 0.75f;
	private static final float GROW = 1.08f;

	CantorShieldLayer(RenderLayerParent<CantorRenderer.State, CantorModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CantorRenderer.State state, float yRot, float xRot) {
		if (!state.shielded || state.isInvisible) {
			return;
		}

		float alpha = Vfx.flashing() ? 0.15f + 0.05f * Mth.sin(state.ageInTicks * 0.2f) : 0.16f;
		poseStack.pushPose();
		poseStack.translate(0f, MIDDLE, 0f);
		poseStack.scale(GROW, GROW, GROW);
		poseStack.translate(0f, -MIDDLE, 0f);
		// (model, state, pose, render type, light, overlay, tint, sprite, outline colour, crumbling)
		collector.submitModel(getParentModel(), state, poseStack, RenderTypes.entityTranslucent(CantorRenderer.TEXTURE), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, FoeGlow.argb(alpha, SHIELD_RGB), null, 0, null);
		poseStack.popPose();
	}
}

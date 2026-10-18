package com.oldrepublic.client.enemy.droid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.droid.SecurityDroid;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the Mk IV security droid (spec MILESTONE_4.md 10.2): the {@link DroidModel} at 2.3 blocks,
 * the crew's rust-and-olive paint or the Imperial guard's grey ({@code textures/entity/droid/}), and
 * its sensor eye drawn full bright ({@code security_droid_eye.png}): steady while it patrols,
 * brighter through the spin-up tell, dark once dead. With flashing effects off it never pulses.
 */
public class DroidRenderer extends HumanoidMobRenderer<SecurityDroid, DroidRenderer.State, DroidModel> {
	private static final Identifier CREW = OldRepublicMod.id("textures/entity/droid/security_droid.png");
	private static final Identifier IMPERIAL = OldRepublicMod.id("textures/entity/droid/security_droid_imperial.png");
	private static final Identifier EYE = OldRepublicMod.id("textures/entity/droid/security_droid_eye.png");
	/** The humanoid mesh is 2 blocks tall; the droid is 2.3. */
	private static final float SCALE = 2.3f / 2f;

	/** Render state of a security droid, from its synced data. */
	public static class State extends HumanoidRenderState {
		public boolean imperial;
		public boolean spinning;
		public boolean firing;
		public boolean shielded;
		public boolean aggressive;
		public boolean flashing = true;
	}

	public DroidRenderer(EntityRendererProvider.Context context) {
		super(context, new DroidModel(context.bakeLayer(DroidModel.LAYER)), 0.5f);
		addLayer(new EyeLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SecurityDroid droid, State state, float partialTick) {
		super.extractRenderState(droid, state, partialTick);
		state.imperial = droid.isImperialGuard();
		state.spinning = droid.isSpinning();
		state.firing = droid.isFiring();
		state.shielded = droid.isShielded();
		state.aggressive = droid.isAggressive();
		state.flashing = Vfx.flashing();
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.imperial ? IMPERIAL : CREW;
	}

	/** The eye's brightness, 0 to 1. */
	static float eyeBrightness(State state) {
		if (state.deathTime > 0f) {
			return 1f - Mth.clamp(state.deathTime / 10f, 0f, 1f);
		}

		if (state.spinning) {
			return state.flashing ? 0.85f + 0.15f * Mth.sin(state.ageInTicks * 1.3f) : 1f;
		}

		return state.aggressive || state.firing ? 0.85f : 0.6f;
	}

	/** The sensor eye, drawn again with the eye texture, additive and full bright. */
	private static final class EyeLayer extends RenderLayer<State, DroidModel> {
		/** Packed block and sky light 15. */
		private static final int FULL_BRIGHT = 0xF000F0;

		EyeLayer(RenderLayerParent<State, DroidModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			float brightness = eyeBrightness(state);

			if (state.isInvisible || brightness <= 0.01f) {
				return;
			}

			int v = Mth.clamp(Math.round(brightness * 255f), 0, 255);
			int tint = 0xFF000000 | (v << 16) | (v << 8) | v;
			collector.submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(EYE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint, null,
					state.outlineColor, null);
		}
	}
}

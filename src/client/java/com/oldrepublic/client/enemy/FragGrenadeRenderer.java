package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.trooper.FragGrenade;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the frag grenade: the model tumbles end over end in flight and lies on its side once it
 * has landed. The fuse light on its cap is the fuse tell (spec 13.3): a slow red blink that
 * quickens to a flicker as the burst nears, with a soft halo so it can be found in grass; a dim
 * steady light before landing; steady and brightening with flashing effects off.
 */
public class FragGrenadeRenderer extends EntityRenderer<FragGrenade, FragGrenadeRenderState> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/frag_grenade.png");
	/** Degrees per tick of the in-flight tumble. */
	private static final float TUMBLE = 28f;

	private final FragGrenadeModel model;

	public FragGrenadeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new FragGrenadeModel(context.bakeLayer(FragGrenadeModel.LAYER));
		this.shadowRadius = 0.12f;
	}

	@Override
	public FragGrenadeRenderState createRenderState() {
		return new FragGrenadeRenderState();
	}

	@Override
	public void extractRenderState(FragGrenade grenade, FragGrenadeRenderState state, float partialTick) {
		super.extractRenderState(grenade, state, partialTick);
		state.yRot = grenade.getYRot(partialTick);
		state.landed = grenade.landed();
		state.fuseStarted = grenade.fuseStarted();
		state.fuseTicks = state.fuseStarted ? grenade.fuseTicks() + partialTick : 0f;
		state.fuseLength = Math.max(1, grenade.fuseLength());
		state.flashing = Vfx.flashing();
	}

	/**
	 * Fuse light brightness 0..1 at {@code ticks} into a fuse of {@code length}: on/off with a period
	 * that shrinks from 12 ticks to 2; steady and rising with flashing off. Package visible for the
	 * fuse handler's smoke, which follows the same rhythm.
	 */
	static float fuseLight(float ticks, int length, boolean flashing) {
		float progress = Mth.clamp(ticks / length, 0f, 1f);

		if (!flashing) {
			return 0.55f + 0.45f * progress;
		}

		float period = Mth.lerp(progress * progress, 12f, 2f);
		return (ticks % period) < period * 0.45f ? 1f : 0.12f;
	}

	@Override
	public void submit(FragGrenadeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.125f, 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));

		if (state.landed) {
			// On its side, the cap toward its direction of travel.
			poseStack.translate(0f, -0.03f, 0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(90f));
		} else {
			poseStack.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * TUMBLE));
		}

		poseStack.pushPose();
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();

		float light = state.fuseStarted ? fuseLight(state.fuseTicks, state.fuseLength, state.flashing) : 0.25f;
		float progress = state.fuseStarted ? Mth.clamp(state.fuseTicks / state.fuseLength, 0f, 1f) : 0f;
		float halo = (0.10f + 0.10f * progress) * (0.4f + 0.6f * light);
		int haloAlpha = (int) (40 + 150 * light);
		int coreAlpha = (int) (90 + 165 * light);
		poseStack.translate(0f, FragGrenadeModel.CAP_TOP, 0f);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.flare(pose, buffer, halo, (haloAlpha << 24) | 0xFF2414);
			GlowGeometry.flare(pose, buffer, 0.035f, (coreAlpha << 24) | 0xFFB8A0);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

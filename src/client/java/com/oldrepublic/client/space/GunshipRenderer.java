package com.oldrepublic.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.space.BlockadeGunship;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Renders the blockade gunship (spec MILESTONE_5.md 5.5, 15.3): the hull turned to its heading,
 * the bay doors parting before a launch, the shield generator on its spine while it stands with
 * its core pulsing (steady with flashing effects off), the engines and running lights glowing; in
 * its death the engines sputter out, and after the end blast the hull breaks in two and drifts
 * apart until it is gone.
 */
public class GunshipRenderer extends EntityRenderer<BlockadeGunship, GunshipRenderState> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/space/blockade_gunship.png");
	private static final Identifier GLOW = OldRepublicMod.id("textures/entity/space/blockade_gunship_glow.png");
	private static final Identifier CORE = OldRepublicMod.id("textures/entity/space/blockade_gunship_core.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Model units are a quarter of a block. */
	private static final float SCALE = 4f;

	private final GunshipModel model;

	public GunshipRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new GunshipModel(context.bakeLayer(GunshipModel.LAYER));
		this.shadowRadius = 0f;
	}

	@Override
	public GunshipRenderState createRenderState() {
		return new GunshipRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(BlockadeGunship gunship) {
		// The turned hull's corners, the generator on the spine and the halves drifting apart.
		return gunship.getBoundingBox().inflate(2.5, 3.5, 2.5);
	}

	@Override
	public void extractRenderState(BlockadeGunship gunship, GunshipRenderState state, float partialTick) {
		super.extractRenderState(gunship, state, partialTick);
		state.yRot = gunship.getYRot(partialTick);
		state.bay = gunship.bay(partialTick);
		boolean dying = gunship.isDeadOrDying();
		float death = dying ? gunship.deathTime + partialTick : 0f;
		state.shielded = gunship.shielded() && !dying;
		state.breakUp = Mth.clamp((death - BlockadeGunship.END_TICK) / (BlockadeGunship.GONE_TICK - BlockadeGunship.END_TICK), 0f, 1f);
		boolean flashing = Vfx.flashing();
		float t = state.ageInTicks;

		if (!dying) {
			state.engine = flashing ? 0.86f + 0.06f * Mth.sin(t * 0.21f) : 0.9f;
		} else {
			// The engines sputter and die over the blasts.
			float left = 1f - Mth.clamp(death / BlockadeGunship.END_TICK, 0f, 1f);
			state.engine = left * (flashing ? 0.55f + 0.45f * Math.abs(Mth.sin(t * 1.3f) * Mth.sin(t * 0.37f)) : 0.7f);
		}

		state.core = flashing ? 0.6f + 0.4f * (0.5f + 0.5f * Mth.sin(t * 0.25f)) : 0.85f;
	}

	@Override
	public void submit(GunshipRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
		poseStack.scale(-SCALE, -SCALE, SCALE);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);

		// OrderedSubmitNodeCollector#submitModel(model, state, pose, type, light, overlay, tint colour, sprite, outline colour, crumbling).
		if (state.engine > 0.01f) {
			collector.submitModel(model, state, poseStack, RenderTypes.eyes(GLOW), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					StarfighterRenderer.grey(state.engine), null, state.outlineColor, null);
		}

		if (state.shielded) {
			collector.submitModel(model, state, poseStack, RenderTypes.eyes(CORE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					StarfighterRenderer.grey(state.core), null, state.outlineColor, null);
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

package com.oldrepublic.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.space.Starfighter;

import net.minecraft.client.model.geom.ModelLayerLocation;
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
 * Renders a starfighter (spec MILESTONE_5.md 5.5): its model turned to its heading and pitch and
 * banked into its turns, with the exhausts glowing on top (a flicker, steady with flashing effects
 * off). A bounty's ace shows its name within 96 blocks. A destroyed fighter is not drawn: the
 * {@code ship_destroyed} event's burst takes its place at once.
 */
public class StarfighterRenderer extends EntityRenderer<Starfighter, StarfighterRenderState> {
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Model units are an eighth of a block. */
	private static final float SCALE = 2f;
	/** An ace's name shows within this many blocks. */
	private static final double NAME_RANGE = 96;

	private final StarfighterModel model;
	private final Identifier texture;
	private final Identifier glow;

	private StarfighterRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String name) {
		super(context);
		this.model = new StarfighterModel(context.bakeLayer(layer));
		this.texture = OldRepublicMod.id("textures/entity/space/" + name + ".png");
		this.glow = OldRepublicMod.id("textures/entity/space/" + name + "_glow.png");
		this.shadowRadius = 0f;
	}

	public static StarfighterRenderer hymn(EntityRendererProvider.Context context) {
		return new StarfighterRenderer(context, StarfighterModel.HYMN, "hymn_interceptor");
	}

	public static StarfighterRenderer raider(EntityRendererProvider.Context context) {
		return new StarfighterRenderer(context, StarfighterModel.RAIDER, "kajidic_raider");
	}

	public static StarfighterRenderer hask(EntityRendererProvider.Context context) {
		return new StarfighterRenderer(context, StarfighterModel.HASK, "hask_fighter");
	}

	@Override
	public StarfighterRenderState createRenderState() {
		return new StarfighterRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Starfighter fighter) {
		// The wings and the nose reach past the box as the fighter turns, pitches and banks.
		return fighter.getBoundingBox().inflate(1.0);
	}

	/** Only a bounty's ace wears a name, and only within {@link #NAME_RANGE}. */
	@Override
	protected boolean shouldShowName(Starfighter fighter, double distanceToCameraSq) {
		return fighter.ace() && fighter.hasCustomName() && distanceToCameraSq < NAME_RANGE * NAME_RANGE;
	}

	@Override
	public void extractRenderState(Starfighter fighter, StarfighterRenderState state, float partialTick) {
		super.extractRenderState(fighter, state, partialTick);
		state.yRot = fighter.getYRot(partialTick);
		state.xRot = fighter.getXRot(partialTick);
		state.bank = fighter.bank(partialTick);
		state.halfHeight = fighter.getBbHeight() / 2f;
		state.hidden = fighter.isDeadOrDying();
		state.engine = engine(state.ageInTicks + fighter.getId() * 7.3f);
	}

	/** The exhaust's glow: a fast uneven flicker around 0.85, steady with flashing effects off. */
	static float engine(float t) {
		if (!Vfx.flashing()) {
			return 0.85f;
		}

		return 0.82f + 0.12f * Mth.sin(t * 1.9f) * Mth.sin(t * 0.73f) + 0.06f * Mth.sin(t * 5.1f);
	}

	@Override
	public void submit(StarfighterRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.hidden) {
			return;
		}

		poseStack.pushPose();
		poseStack.translate(0f, state.halfHeight, 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));
		// Positive bank drops the right wing (the ship's right is −x before the model's flip).
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.bank));
		poseStack.scale(-SCALE, -SCALE, SCALE);
		collector.submitModel(model, state, poseStack, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		// OrderedSubmitNodeCollector#submitModel(model, state, pose, type, light, overlay, tint colour, sprite, outline colour, crumbling).
		collector.submitModel(model, state, poseStack, RenderTypes.eyes(glow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, grey(state.engine), null,
				state.outlineColor, null);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** An opaque grey of {@code brightness}: the eyes type adds the texture, so a darker tint dims the glow. */
	static int grey(float brightness) {
		int v = Mth.clamp(Math.round(brightness * 255f), 0, 255);
		return 0xFF000000 | (v << 16) | (v << 8) | v;
	}
}

package com.oldrepublic.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.enemy.droid.GlowShapes;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.space.NavBuoy;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders a nav buoy (spec MILESTONE_5.md 5.9, 15.3): a squat base with a mast, two sensor vanes, a
 * collar and a lamp on top, turning slowly. The lamp blinks amber with each ping (the
 * {@code nav_buoy} event's sound; on its own clock when no ping reaches this client), is a steady
 * amber with flashing effects off, and turns a steady green once the run reached it.
 *
 * <p>Model units are an eighth of a block (scaled by 2), the base at y 0; UV layout (64 × 32),
 * mirrored in {@code tools/assets/gen_textures_space.py}: base 0,0; mast 32,0; lamp 40,0; vanes
 * 0,12; collar 0,20. The {@code _lamp} texture lights the lamp alone (white, tinted here).
 */
public class NavBuoyRenderer extends EntityRenderer<NavBuoy, NavBuoyRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("nav_buoy"), "main");
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/space/nav_buoy.png");
	private static final Identifier LAMP = OldRepublicMod.id("textures/entity/space/nav_buoy_lamp.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final float SCALE = 2f;
	private static final int AMBER = 0xFFB040;
	private static final int GREEN = 0x9CFFB0;
	/** The lamp's centre above the base (blocks). */
	private static final float LAMP_Y = 2.25f;
	/** A ping lights the lamp this long; the lamp follows its own clock when no ping came for {@link #PING_STALE} ticks. */
	private static final int BLINK_TICKS = 6;
	private static final int PING_STALE = 80;
	private static final int BLINK_INTERVAL = 40;

	/** Render state of a nav buoy. */
	public static class State extends EntityRenderState {
		/** 0 to 1: the lamp's brightness. */
		public float lamp;
		public boolean reached;
	}

	/** The buoy's model. */
	public static final class BuoyModel extends EntityModel<State> {
		public BuoyModel(ModelPart root) {
			super(root);
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -4f, -4f, 8f, 4f, 8f), PartPose.ZERO);
			root.addOrReplaceChild("mast", CubeListBuilder.create().texOffs(32, 0).addBox(-1f, -16f, -1f, 2f, 12f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(40, 0).addBox(-2f, -20f, -2f, 4f, 4f, 4f), PartPose.ZERO);
			root.addOrReplaceChild("vane_right", CubeListBuilder.create().texOffs(0, 12).addBox(1f, -12f, -0.5f, 6f, 4f, 1f), PartPose.ZERO);
			root.addOrReplaceChild("vane_left", CubeListBuilder.create().mirror().texOffs(0, 12).addBox(-7f, -12f, -0.5f, 6f, 4f, 1f),
					PartPose.ZERO);
			root.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 20).addBox(-3f, -8f, -3f, 6f, 1f, 6f), PartPose.ZERO);
			return LayerDefinition.create(mesh, 64, 32);
		}
	}

	private final BuoyModel model;

	public NavBuoyRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new BuoyModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(NavBuoy buoy, State state, float partialTick) {
		super.extractRenderState(buoy, state, partialTick);
		state.reached = buoy.reached();
		state.lamp = lamp(buoy, state.reached, state.ageInTicks);
	}

	/** The lamp: steady green once reached; steady amber with flashing off; else a flash with each ping over a dim glow. */
	private static float lamp(NavBuoy buoy, boolean reached, float age) {
		if (reached) {
			return 1f;
		}

		if (!Vfx.flashing()) {
			return 0.8f;
		}

		long last = SpaceFx.lastBlink(buoy.getId());
		long since = last == Long.MIN_VALUE ? Long.MAX_VALUE : SpaceClientState.ticks() - last;

		if (since < PING_STALE) {
			return since < BLINK_TICKS ? 1f : 0.25f;
		}

		return (age + buoy.getId()) % BLINK_INTERVAL < BLINK_TICKS ? 1f : 0.25f;
	}

	@Override
	protected int getBlockLightLevel(NavBuoy buoy, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		int colour = state.reached ? GREEN : AMBER;
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * 1.5f));
		poseStack.pushPose();
		poseStack.scale(-SCALE, -SCALE, SCALE);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		// OrderedSubmitNodeCollector#submitModel(model, state, pose, type, light, overlay, tint colour, sprite, outline colour, crumbling).
		collector.submitModel(model, state, poseStack, RenderTypes.eyes(LAMP), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFF000000 | scale(colour, state.lamp),
				null, state.outlineColor, null);
		poseStack.popPose();

		// A small soft light around the lamp (a marker light, nothing on any ground).
		if (state.lamp > 0.3f) {
			int alpha = Math.round(0x70 * state.lamp);
			poseStack.translate(0f, LAMP_Y, 0f);
			collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
				GlowShapes.flare(pose, buffer, 0.55f, (alpha / 2 << 24) | colour);
				GlowShapes.flare(pose, buffer, 0.3f, (alpha << 24) | colour);
			});
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** {@code rgb} with each channel scaled by {@code k} (0 to 1). */
	private static int scale(int rgb, float k) {
		float f = Mth.clamp(k, 0f, 1f);
		int r = Math.round(((rgb >> 16) & 0xFF) * f);
		int g = Math.round(((rgb >> 8) & 0xFF) * f);
		int b = Math.round((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}
}

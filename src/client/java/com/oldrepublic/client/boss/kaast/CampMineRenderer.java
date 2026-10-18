package com.oldrepublic.client.boss.kaast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.kaast.CampMine;
import com.oldrepublic.client.enemy.droid.GlowShapes;
import com.oldrepublic.client.fx.Vfx;

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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders a camp mine (spec MILESTONE_4.md 10.5, phase 2): a squat scrap-metal disc with three
 * prongs, and its arming light, the tell: an amber blink that quickens over the arming ticks, then,
 * once live, a dim light that pulses slowly. With flashing effects off the arming light is steady
 * and brightens instead, and a live one glows steadily. Amber, so the rocket's red dot stays the
 * only red light on the field.
 *
 * <p>UV layout (32x16), mirrored in {@code tools/assets/gen_textures_foes.py}: disc 0,0; prong 24,0.
 */
public class CampMineRenderer extends EntityRenderer<CampMine, CampMineRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("camp_mine"), "main");
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/kaast/camp_mine.png");
	private static final int LIGHT = 0xFFFFB040;
	/** The arming time the blink is paced on (the server's default); a longer one blinks fast at the end. */
	private static final float ARM_TICKS = 40f;

	/** Render state of a mine. */
	public static class State extends EntityRenderState {
		public boolean live;
		public boolean flashing = true;
	}

	/** The mine: a disc with a raised hub and three short prongs. Model space y down, centred on the base. */
	public static final class MineModel extends EntityModel<State> {
		public MineModel(ModelPart root) {
			super(root);
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("disc", CubeListBuilder.create().texOffs(0, 0)
					.addBox(-3f, -1.5f, -3f, 6f, 1.5f, 6f)
					.addBox(-1.5f, -2.5f, -1.5f, 3f, 1f, 3f), PartPose.ZERO);

			for (int i = 0; i < 3; i++) {
				root.addOrReplaceChild("prong_" + i, CubeListBuilder.create().texOffs(24, 0)
						.addBox(-0.5f, -3.5f, 2f, 1f, 2f, 1f), PartPose.rotation(0f, i * Mth.TWO_PI / 3f, 0f));
			}

			return LayerDefinition.create(mesh, 32, 16);
		}
	}

	private final MineModel model;

	public CampMineRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new MineModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0.2f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CampMine mine, State state, float partialTick) {
		super.extractRenderState(mine, state, partialTick);
		state.live = mine.isLive();
		state.flashing = Vfx.flashing();
	}

	/** The light's brightness, 0 to 1. */
	static float light(State state) {
		if (state.live) {
			return state.flashing ? 0.35f + 0.2f * Mth.sin(state.ageInTicks * 0.15f) : 0.45f;
		}

		float progress = Mth.clamp(state.ageInTicks / ARM_TICKS, 0f, 1f);

		if (!state.flashing) {
			return 0.5f + 0.5f * progress;
		}

		float period = Mth.lerp(progress, 10f, 3f);
		return state.ageInTicks % period < period * 0.5f ? 1f : 0.15f;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();

		float light = light(state);
		int core = GlowShapes.fade(LIGHT, light);
		poseStack.pushPose();
		poseStack.translate(0f, 2.7f / 16f, 0f);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> GlowShapes.flare(pose, buffer, 0.03f + 0.03f * light, core));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

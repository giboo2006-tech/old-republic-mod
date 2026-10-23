package com.oldrepublic.client.enemy.mandalorian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.enemy.droid.GlowShapes;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.mandalorian.HunterRocket;

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
 * Renders the hunter's wrist rocket (spec MILESTONE_4.md 10.1): a stubby rocket with four fins,
 * nose first along its flight, and the motor's flame at the tail (a small steady light; the smoke
 * trail is {@link HunterFx}'s). A rocket sent back by a Push flies the same way.
 *
 * <p>UV layout (32x16), mirrored in {@code tools/assets/gen_textures_foes.py}: body 0,0; fins 20,0.
 */
public class HunterRocketRenderer extends EntityRenderer<HunterRocket, HunterRocketRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("hunter_rocket"), "main");
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/mandalorian/hunter_rocket.png");
	private static final int FLAME_CORE = 0xE0FFE0A0;
	private static final int FLAME_OUTER = 0x90FF8A30;

	/** Render state of a rocket. */
	public static class State extends EntityRenderState {
		public float yRot;
		public float xRot;
		public boolean flashing = true;
	}

	/** The rocket's model: body along z (nose at positive z), four fins at the tail. */
	public static final class RocketModel extends EntityModel<State> {
		public RocketModel(ModelPart root) {
			super(root);
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
					.addBox(-1f, -1f, -4f, 2f, 2f, 8f), PartPose.ZERO);
			root.addOrReplaceChild("fins_vertical", CubeListBuilder.create().texOffs(20, 0)
					.addBox(-0.5f, -2.5f, -4f, 1f, 5f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("fins_horizontal", CubeListBuilder.create().texOffs(20, 0)
					.addBox(-0.5f, -2.5f, -4f, 1f, 5f, 2f), PartPose.rotation(0f, 0f, Mth.HALF_PI));
			return LayerDefinition.create(mesh, 32, 16);
		}
	}

	private final RocketModel model;

	public HunterRocketRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new RocketModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0.1f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HunterRocket rocket, State state, float partialTick) {
		super.extractRenderState(rocket, state, partialTick);
		state.yRot = rocket.getYRot(partialTick);
		state.xRot = rocket.getXRot(partialTick);
		state.flashing = Vfx.flashing();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.15f, 0f);
		// Projectile#updateRotation: yRot = atan2(dx, dz), xRot = atan2(dy, horizontal), in degrees, lerped 20% a tick.
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
		poseStack.mulPose(Axis.XP.rotationDegrees(-state.xRot));
		poseStack.pushPose();
		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();

		// The motor's flame just behind the fins.
		float flicker = state.flashing ? 0.8f + 0.2f * Mth.sin(state.ageInTicks * 2.1f) : 1f;
		poseStack.translate(0f, 0f, -0.3f);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowShapes.flare(pose, buffer, 0.11f * flicker, FLAME_OUTER);
			GlowShapes.flare(pose, buffer, 0.05f, FLAME_CORE);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

package com.oldrepublic.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.enemy.droid.GlowShapes;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.space.ShipMissile;

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
import net.minecraft.world.phys.Vec3;

/**
 * Renders a concussion missile (spec MILESTONE_5.md 5.3, 15.1): a dark finned body, nose first along
 * its synced velocity, rolling slowly as it homes, with the motor's hot blue-white flame at its tail
 * (a flicker, steady with flashing effects off). Its smoke trail is {@link SpaceFx}'s.
 *
 * <p>Model units are an eighth of a block (scaled by 2); UV layout (32 × 32), mirrored in
 * {@code tools/assets/gen_textures_space.py}: body 0,0; nose 20,0; fins 0,10 and 12,10.
 */
public class ShipMissileRenderer extends EntityRenderer<ShipMissile, ShipMissileRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("ship_missile"), "main");
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/space/ship_missile.png");
	private static final float SCALE = 2f;
	private static final int FLAME_CORE = 0xF0F4F8FF;
	private static final int FLAME_OUTER = 0xA06AA8FF;
	private static final int FLAME_HAZE = 0x403C64E0;

	/** Render state of a missile. */
	public static class State extends EntityRenderState {
		/** Facing along the flight (the projectile convention: yaw from +Z toward +X, pitch positive up), degrees. */
		public float yaw;
		public float pitch;
		public boolean flashing = true;
	}

	/** The missile's model: body along z (nose at positive z), a cross of fins at the tail. */
	public static final class MissileModel extends EntityModel<State> {
		public MissileModel(ModelPart root) {
			super(root);
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1f, -1f, -4f, 2f, 2f, 8f), PartPose.ZERO);
			root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(20, 0).addBox(-0.5f, -0.5f, 4f, 1f, 1f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("fins_horizontal", CubeListBuilder.create().texOffs(0, 10).addBox(-2f, -0.5f, -4f, 4f, 1f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("fins_vertical", CubeListBuilder.create().texOffs(12, 10).addBox(-0.5f, -2f, -4f, 1f, 4f, 2f), PartPose.ZERO);
			return LayerDefinition.create(mesh, 32, 32);
		}
	}

	private final MissileModel model;

	public ShipMissileRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new MissileModel(context.bakeLayer(LAYER));
		this.shadowRadius = 0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ShipMissile missile, State state, float partialTick) {
		super.extractRenderState(missile, state, partialTick);
		Vec3 v = missile.velocity();

		if (v.lengthSqr() > 1.0e-8) {
			state.yaw = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			state.pitch = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		}

		state.flashing = Vfx.flashing();
	}

	@Override
	protected int getBlockLightLevel(ShipMissile missile, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.25f, 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * 9f));
		poseStack.pushPose();
		poseStack.scale(-SCALE, -SCALE, SCALE);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();

		// The motor's flame just behind the fins (the body's tail is half a block back).
		float flicker = state.flashing ? 0.8f + 0.2f * Mth.sin(state.ageInTicks * 2.3f) * Mth.sin(state.ageInTicks * 0.9f) : 1f;
		poseStack.translate(0f, 0f, -0.58f);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowShapes.flare(pose, buffer, 0.32f * flicker, FLAME_HAZE);
			GlowShapes.flare(pose, buffer, 0.17f * flicker, FLAME_OUTER);
			GlowShapes.flare(pose, buffer, 0.07f, FLAME_CORE);
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

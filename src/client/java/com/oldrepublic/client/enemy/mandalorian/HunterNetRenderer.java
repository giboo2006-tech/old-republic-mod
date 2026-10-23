package com.oldrepublic.client.enemy.mandalorian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.mandalorian.HunterNet;

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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Renders the hunter's net (spec MILESTONE_4.md 10.1, 10.5): in flight a weighted bundle of cords
 * tumbling; on a caught player a cage of cord mesh wrapped around them from the boots to the
 * shoulders, following them. The cords are a cut-out texture
 * ({@code textures/entity/mandalorian/hunter_net.png}).
 *
 * <p>UV layout (64x64), mirrored in {@code tools/assets/gen_textures_foes.py}: front and back panel
 * 0,0; side panels 32,0; top 0,40.
 */
public class HunterNetRenderer extends EntityRenderer<HunterNet, HunterNetRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("hunter_net"), "main");
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/mandalorian/hunter_net.png");
	/** Size of the folded bundle in flight, against the full cage. */
	private static final float BUNDLE_SCALE = 0.3f;
	/** Degrees per tick of the bundle's tumble. */
	private static final float TUMBLE = 24f;

	/** Render state of a net. */
	public static class State extends EntityRenderState {
		public boolean caught;
		/** The cage's width and height, in blocks (the caught entity's). */
		public float width = 0.6f;
		public float height = 1.8f;
	}

	/** The cage: four mesh sides and a top, 1 block across and 1.5 high before scaling. */
	public static final class NetModel extends EntityModel<State> {
		public NetModel(ModelPart root) {
			super(root);
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0)
					.addBox(-8f, -24f, -8f, 16f, 24f, 0f), PartPose.ZERO);
			root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 0)
					.addBox(-8f, -24f, 8f, 16f, 24f, 0f), PartPose.ZERO);
			root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(32, 0)
					.addBox(-8f, -24f, -8f, 0f, 24f, 16f), PartPose.ZERO);
			root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(32, 0)
					.addBox(8f, -24f, -8f, 0f, 24f, 16f), PartPose.ZERO);
			root.addOrReplaceChild("top", CubeListBuilder.create().texOffs(0, 40)
					.addBox(-8f, -24f, -8f, 16f, 0f, 16f), PartPose.ZERO);
			return LayerDefinition.create(mesh, 64, 64);
		}
	}

	private final NetModel model;

	public HunterNetRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new NetModel(context.bakeLayer(LAYER));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HunterNet net, State state, float partialTick) {
		super.extractRenderState(net, state, partialTick);
		Entity caught = net.hasCaught() ? net.level().getEntity(net.caughtId()) : null;
		state.caught = caught != null;

		if (caught != null) {
			state.width = caught.getBbWidth() + 0.3f;
			state.height = caught.getBbHeight() * 0.8f;
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();

		if (state.caught) {
			// From the boots up to the shoulders; the model is 1 wide and 1.5 high.
			poseStack.scale(state.width, state.height / 1.5f, state.width);
		} else {
			poseStack.translate(0f, 0.2f, 0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * TUMBLE));
			poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * TUMBLE * 0.6f));
			poseStack.scale(BUNDLE_SCALE, BUNDLE_SCALE, BUNDLE_SCALE);
			poseStack.translate(0f, -0.75f, 0f);
		}

		poseStack.scale(-1f, -1f, 1f);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

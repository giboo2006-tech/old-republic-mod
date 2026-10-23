package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.PoacherCaptain;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders the Poacher Captain: coat and hat over the humanoid model, the vibroblade in the main
 * hand and the blaster pistol in the off hand (vanilla item-in-hand layer), the action tells of
 * {@link CaptainModel}, the pistol muzzle glow and the kneel when she yields.
 */
public class PoacherCaptainRenderer extends HumanoidMobRenderer<PoacherCaptain, CaptainRenderState, CaptainModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/poacher_captain.png");

	public PoacherCaptainRenderer(EntityRendererProvider.Context context) {
		super(context, new CaptainModel(context.bakeLayer(CaptainModel.LAYER)), 0.5f);
		addLayer(new PistolGlowLayer(this));
	}

	@Override
	public CaptainRenderState createRenderState() {
		return new CaptainRenderState();
	}

	@Override
	public void extractRenderState(PoacherCaptain captain, CaptainRenderState state, float partialTick) {
		super.extractRenderState(captain, state, partialTick);
		state.action = captain.action();
		state.actionTicks = state.action == PoacherCaptain.Action.IDLE ? 0f : captain.clientActionTicks() + partialTick;
		state.kneel = captain.isYielded() ? 1f : 0f;
	}

	@Override
	protected void scale(CaptainRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		// API-CHECK: LivingEntityRenderer#scale runs after the model flip (positive y is down), as in 1.21.
		KneelPose.lower(poseStack, state.kneel);
	}

	@Override
	public Identifier getTextureLocation(CaptainRenderState state) {
		return TEXTURE;
	}
}

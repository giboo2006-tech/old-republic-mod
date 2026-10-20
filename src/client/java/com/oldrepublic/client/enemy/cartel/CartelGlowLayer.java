package com.oldrepublic.client.enemy.cartel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.client.enemy.hymn.FoeGlow;
import com.oldrepublic.enemy.cartel.CartelEnforcer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The cartel enforcer's shot tell: a red-orange glow at the muzzle of the gunman's rifle or the
 * captain's pistol that grows over the charge and pulses just before the shot (the M1 muzzle glow,
 * at each weapon's muzzle). Never reduced by quality presets: tells stay readable.
 */
final class CartelGlowLayer extends RenderLayer<CartelRenderer.State, CartelModel> {
	/** The rifle's muzzle tip relative to the hand, in the held item's frame (models/item/blaster_rifle_in_hand.json). */
	private static final float RIFLE_UP = 0.07f;
	private static final float RIFLE_ALONG = -0.72f;
	/** The pistol's (models/item/blaster_pistol.json). */
	private static final float PISTOL_UP = 0.12f;
	private static final float PISTOL_ALONG = -0.32f;
	private static final float FULL_CHARGE_TICKS = 12f;

	CartelGlowLayer(RenderLayerParent<CartelRenderer.State, CartelModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CartelRenderer.State state, float yRot, float xRot) {
		if (!state.charging || state.isInvisible || state.variant == CartelEnforcer.Variant.BRUISER) {
			return;
		}

		boolean rifle = state.variant == CartelEnforcer.Variant.GUNMAN;
		HumanoidArm arm = state.mainArm;
		float progress = Mth.clamp(state.tellTicks / FULL_CHARGE_TICKS, 0f, 1f);
		float pulse = progress > 0.66f ? 0.85f + 0.15f * Mth.sin(state.tellTicks * 2.2f) : 1f;
		float size = rifle ? 1f : 0.75f;
		float core = (0.05f + 0.07f * progress) * pulse * size;
		float halo = (0.12f + 0.22f * progress) * pulse * size;
		float bloom = (0.25f + 0.40f * progress) * pulse * size;
		int coreAlpha = (int) (170 + 85 * progress);
		int haloAlpha = (int) (90 + 110 * progress);
		int bloomAlpha = (int) (25 + 45 * progress);

		poseStack.pushPose();
		getParentModel().translateToHand(state, arm, poseStack);
		// The frame the held item is drawn in (the M1 MuzzleGlowLayer's transform), then out to the muzzle.
		// API-CHECK: ItemInHandLayer#submitArmWithItem transform (rotX -90, rotY 180, translate), as the M1 MuzzleGlowLayer.
		poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
		poseStack.mulPose(Axis.YP.rotationDegrees(180f));
		poseStack.translate((arm == HumanoidArm.LEFT ? -1f : 1f) / 16f, 0.125f, -0.625f);
		poseStack.translate(0f, rifle ? RIFLE_UP : PISTOL_UP, rifle ? RIFLE_ALONG : PISTOL_ALONG);

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			FoeGlow.flare(pose, buffer, bloom, (bloomAlpha << 24) | 0xFF2010);
			FoeGlow.flare(pose, buffer, halo, (haloAlpha << 24) | 0xFF3A1C);
			FoeGlow.flare(pose, buffer, core, (coreAlpha << 24) | 0xFFC8A0);
		});
		poseStack.popPose();
	}
}

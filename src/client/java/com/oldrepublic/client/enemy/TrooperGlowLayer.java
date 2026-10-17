package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.enemy.BlasterBolt;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A trooper's tell glows (spec 7.3, 13.3), never reduced by quality presets:
 * <ul>
 * <li>CHARGING: a muzzle glow in the bolt colour (blue-white Republic, crimson Imperial) that grows
 * over the charge and pulses just before the shot;</li>
 * <li>GRENADE_TELL: the grenade's red arming light in the drawn-back hand, blinking faster as the
 * throw nears;</li>
 * <li>FLARE_TELL: the flare pistol's ember in the raised hand.</li>
 * </ul>
 * With flashing effects off every blink and pulse is a steady glow.
 */
final class TrooperGlowLayer extends RenderLayer<TrooperRenderState, TrooperModel> {
	/** Rifle muzzle relative to the hand, in the held item's frame (see models/item/blaster_rifle_in_hand.json). */
	private static final float RIFLE_UP = 0.07f;
	private static final float RIFLE_ALONG = -0.72f;
	/** Pistol muzzle (see models/item/blaster_pistol.json). */
	private static final float PISTOL_UP = 0.12f;
	private static final float PISTOL_ALONG = -0.32f;
	/** From the arm's pivot to the fist, in blocks. */
	private static final float FIST = 0.62f;
	private static final float FULL_CHARGE_TICKS = 12f;
	private static final float GRENADE_TELL_TICKS = 20f;

	TrooperGlowLayer(RenderLayerParent<TrooperRenderState, TrooperModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, TrooperRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.downed) {
			return;
		}

		switch (state.action) {
			case CHARGING -> {
				if (state.armed) {
					muzzle(poseStack, collector, state);
				}
			}
			case GRENADE_TELL -> grenadeLight(poseStack, collector, state);
			case FLARE_TELL -> flareEmber(poseStack, collector, state);
			default -> {
			}
		}
	}

	private void muzzle(PoseStack poseStack, SubmitNodeCollector collector, TrooperRenderState state) {
		HumanoidArm arm = state.mainArm;
		float progress = Mth.clamp(state.actionTicks / FULL_CHARGE_TICKS, 0f, 1f);
		float pulse = state.flashing && progress > 0.66f ? 0.85f + 0.15f * Mth.sin(state.actionTicks * 2.2f) : 1f;
		float core = (0.05f + 0.07f * progress) * pulse;
		float halo = (0.12f + 0.22f * progress) * pulse;
		float bloom = (0.25f + 0.40f * progress) * pulse;
		int coreAlpha = (int) (170 + 85 * progress);
		int haloAlpha = (int) (90 + 110 * progress);
		int bloomAlpha = (int) (25 + 45 * progress);
		boolean republic = state.boltStyle == BlasterBolt.Style.REPUBLIC;
		int bloomRgb = republic ? 0x2A5CFF : 0xD0102A;
		int haloRgb = republic ? 0x5AA8FF : 0xFF2A40;
		int coreRgb = republic ? 0xE6F2FF : 0xFFD6D6;

		poseStack.pushPose();
		getParentModel().translateToHand(state, arm, poseStack);
		// Same frame the held item is drawn in (vanilla ItemInHandLayer), then out to the muzzle.
		// API-CHECK: ItemInHandLayer#submitArmWithItem transform (rotX -90, rotY 180, translate) as in 1.21.
		poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
		poseStack.mulPose(Axis.YP.rotationDegrees(180f));
		poseStack.translate((arm == HumanoidArm.LEFT ? -1f : 1f) / 16f, 0.125f, -0.625f);
		poseStack.translate(0f, state.rifle ? RIFLE_UP : PISTOL_UP, state.rifle ? RIFLE_ALONG : PISTOL_ALONG);

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.flare(pose, buffer, bloom, (bloomAlpha << 24) | bloomRgb);
			GlowGeometry.flare(pose, buffer, halo, (haloAlpha << 24) | haloRgb);
			GlowGeometry.flare(pose, buffer, core, (coreAlpha << 24) | coreRgb);
		});
		poseStack.popPose();
	}

	/** The arming light: blinks at 2.5 Hz, then 5 Hz in the last third of the tell; steady without flashing. */
	private void grenadeLight(PoseStack poseStack, SubmitNodeCollector collector, TrooperRenderState state) {
		float progress = Mth.clamp(state.actionTicks / GRENADE_TELL_TICKS, 0f, 1f);
		boolean lit = true;

		if (state.flashing) {
			float period = progress > 0.66f ? 4f : 8f;
			lit = (state.age % period) < period * 0.5f;
		}

		float halo = lit ? 0.16f + 0.06f * progress : 0.06f;
		int haloAlpha = lit ? 200 : 70;

		atFist(poseStack, state.mainArm.getOpposite(), state);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.flare(pose, buffer, halo, (haloAlpha << 24) | 0xFF2414);
			GlowGeometry.flare(pose, buffer, 0.04f, 0xF0FFB09A);
			// The grenade itself: a dark core so the light reads as sitting on something.
			GlowGeometry.box(pose, buffer, -0.05f, -0.05f, -0.05f, 0.05f, 0.05f, 0.05f, 0x60401010);
		});
		poseStack.popPose();
	}

	/** The flare pistol's ember, flickering (steady without flashing). */
	private void flareEmber(PoseStack poseStack, SubmitNodeCollector collector, TrooperRenderState state) {
		float flicker = state.flashing ? 0.85f + 0.15f * Mth.sin(state.age * 1.7f) : 1f;
		float halo = 0.2f * flicker;

		atFist(poseStack, state.mainArm.getOpposite(), state);
		poseStack.translate(0f, 0.12f, 0f);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.flare(pose, buffer, halo, 0xB4FF5A1E);
			GlowGeometry.flare(pose, buffer, 0.06f, 0xF0FFE0B0);
		});
		poseStack.popPose();
	}

	/** Pushes the pose and moves to the fist of {@code arm}; the caller pops it. */
	private void atFist(PoseStack poseStack, HumanoidArm arm, TrooperRenderState state) {
		poseStack.pushPose();
		getParentModel().translateToHand(state, arm, poseStack);
		poseStack.translate((arm == HumanoidArm.LEFT ? 1f : -1f) / 16f, FIST, 0f);
	}
}

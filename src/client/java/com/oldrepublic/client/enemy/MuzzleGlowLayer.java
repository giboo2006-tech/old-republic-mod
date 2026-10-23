package com.oldrepublic.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.client.equipment.BlasterM7Client;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The attack tell on the rifle: a red-orange glow at the muzzle that grows over the 0.6 s charge
 * and pulses just before the shot. Never reduced by quality presets (tells stay readable). The glow
 * sits at the muzzle of whichever blaster the poacher holds (its in-hand model's, from
 * {@link BlasterM7Client#heldMuzzle}); the rifle's when it holds anything else.
 */
final class MuzzleGlowLayer extends RenderLayer<PoacherRenderState, PoacherModel> {
	private static final float FULL_CHARGE_TICKS = 12f;

	MuzzleGlowLayer(RenderLayerParent<PoacherRenderState, PoacherModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, PoacherRenderState state, float yRot, float xRot) {
		if (!state.charging || state.isInvisible) {
			return;
		}

		HumanoidArm arm = state.mainArm;
		// API-CHECK: that vanilla's armed-entity extraction fills left/rightHandItemStack for the poacher (the fields exist:
		// TrooperRenderer and BlasterPoses write them). If it stays EMPTY, heldMuzzle gives the rifle's point, as before.
		ItemStack held = arm == HumanoidArm.LEFT ? state.leftHandItemStack : state.rightHandItemStack;
		// Muzzle tip relative to the hand, in the held item's frame (see models/item/blaster_rifle_in_hand.json).
		Vec3 muzzle = BlasterM7Client.heldMuzzle(held);
		float progress = Mth.clamp(state.chargeTicks / FULL_CHARGE_TICKS, 0f, 1f);
		float pulse = progress > 0.66f ? 0.85f + 0.15f * Mth.sin(state.chargeTicks * 2.2f) : 1f;
		// Sized to read at 10-15 blocks: a wide faint bloom, a saturated red halo and a hot core.
		float core = (0.05f + 0.07f * progress) * pulse;
		float halo = (0.12f + 0.22f * progress) * pulse;
		float bloom = (0.25f + 0.40f * progress) * pulse;
		int coreAlpha = (int) (170 + 85 * progress);
		int haloAlpha = (int) (90 + 110 * progress);
		int bloomAlpha = (int) (25 + 45 * progress);

		poseStack.pushPose();
		getParentModel().translateToHand(state, arm, poseStack);
		// Same frame the held item is drawn in (vanilla ItemInHandLayer), then out to the muzzle.
		// Probed (tools/api-probe/m5_w8.txt): ItemInHandLayer#submitArmWithItem still draws an adult's held item in this 1.21 frame,
		// plus a step for a spear stab or an item in use, which this foe never has.
		poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
		poseStack.mulPose(Axis.YP.rotationDegrees(180f));
		poseStack.translate((arm == HumanoidArm.LEFT ? -1f : 1f) / 16f, 0.125f, -0.625f);
		poseStack.translate(0f, (float) muzzle.y, (float) muzzle.z);

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowGeometry.flare(pose, buffer, bloom, (bloomAlpha << 24) | 0xFF2010);
			GlowGeometry.flare(pose, buffer, halo, (haloAlpha << 24) | 0xFF3A1C);
			GlowGeometry.flare(pose, buffer, core, (coreAlpha << 24) | 0xFFC8A0);
		});
		poseStack.popPose();
	}
}

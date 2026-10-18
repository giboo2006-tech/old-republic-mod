package com.oldrepublic.client.equipment;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.equipment.blaster.BlasterItem;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * The blaster arm poses (spec MILESTONE_4.md 5.8), for every player in view:
 * <ul>
 * <li>pistol: the gun arm raised along the look while the trigger is held and for 20 ticks after a
 * shot, easing down after; relaxed otherwise;</li>
 * <li>rifle and repeater: both arms, stock to the shoulder (the vanilla two-handed hold) while the
 * trigger is held, for 20 ticks after a shot, and while sneaking to steady the aim; the off-hand item
 * is hidden while the gun is shouldered; relaxed otherwise;</li>
 * <li>a small upward kick of the arms on each shot, and in first person a short recoil of the view
 * model.</li>
 * </ul>
 * Shots are known from the {@code player_blaster_fire} events ({@link GearEffectsClient#sinceFire}),
 * so other players' guns pose too; nothing is sent for poses.
 */
public final class BlasterPoses {
	/** Ticks the arm stays up after a shot. */
	private static final int AIM_HOLD = 20;
	/** Ticks the pistol arm takes to come down. */
	private static final int LOWER = 6;
	/** Ticks a shot's kick lasts. */
	private static final float KICK_TICKS = 3f;

	/**
	 * Copies {@code entity}'s blaster pose into {@code state}: the pose itself, and for a shouldered
	 * long gun the two-handed hold with the off-hand item hidden. Clears the pose when there is none.
	 */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTick) {
		state.setData(BlasterPose.KEY, pose(entity, state, partialTick));
	}

	private static @Nullable BlasterPose pose(Avatar entity, AvatarRenderState state, float partialTick) {
		ItemStack main = entity.getMainHandItem();

		if (!(main.getItem() instanceof BlasterItem blaster) || entity.isSpectator()) {
			return null;
		}

		boolean left = entity.getMainArm() == HumanoidArm.LEFT;
		float since = GearEffectsClient.sinceFire(entity.getId()) + partialTick;
		float kick = Math.max(0f, 1f - since / KICK_TICKS);
		boolean trigger = entity.isUsingItem();

		if (blaster.kind() == BlasterItem.Kind.PISTOL) {
			float amount = trigger || since <= AIM_HOLD ? 1f : Math.max(0f, 1f - (since - AIM_HOLD) / LOWER);
			return amount <= 0f ? null : new BlasterPose(true, amount, kick, left);
		}

		if (!trigger && since > AIM_HOLD && !entity.isShiftKeyDown()) {
			return null;
		}

		// Shouldered: the vanilla two-handed hold poses both arms along the look.
		if (left) {
			state.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
			state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			state.rightHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
		} else {
			state.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
			state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			state.leftHandItemState.clear();
			state.leftHandItemStack = ItemStack.EMPTY;
		}

		return new BlasterPose(false, 1f, kick, left);
	}

	/** Applies {@code pose} to {@code model} after its vanilla animation (the humanoid model mixin). */
	public static void apply(HumanoidModel<?> model, BlasterPose pose) {
		ModelPart gunArm = pose.leftArm() ? model.leftArm : model.rightArm;
		ModelPart offArm = pose.leftArm() ? model.rightArm : model.leftArm;
		float kick = pose.kick() * 0.22f;

		if (pose.pistol()) {
			float a = pose.amount();
			gunArm.xRot = Mth.lerp(a, gunArm.xRot, model.head.xRot - Mth.HALF_PI) - kick * a;
			gunArm.yRot = Mth.lerp(a, gunArm.yRot, model.head.yRot);
			gunArm.zRot = Mth.lerp(a, gunArm.zRot, 0f);
			return;
		}

		gunArm.xRot -= kick;
		offArm.xRot -= kick * 0.8f;
	}

	/** First person: the view model jerks back and up on a shot, then settles (no screen flash). */
	public static void applyFirstPerson(PoseStack poseStack, LocalPlayer player, float partialTick) {
		if (!(player.getMainHandItem().getItem() instanceof BlasterItem)) {
			return;
		}

		float recoil = GearEffectsClient.recoil(partialTick);

		if (recoil <= 0f) {
			return;
		}

		poseStack.translate(0f, 0.012f * recoil, 0.07f * recoil);
		poseStack.mulPose(Axis.XP.rotationDegrees(3.5f * recoil));
	}

	private BlasterPoses() {
	}
}

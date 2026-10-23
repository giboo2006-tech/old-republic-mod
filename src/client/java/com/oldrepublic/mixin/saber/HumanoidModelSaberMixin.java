package com.oldrepublic.mixin.saber;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.saber.SaberPose;
import com.oldrepublic.client.saber.SaberStance;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;

/**
 * Applies a {@link SaberPose} to the saber arm after the vanilla humanoid animation. The held item
 * is rendered from the arm, so the blade follows. Rotations are blended with the vanilla pose by
 * the pose's amount, so poses ease in and out. First the idle {@link SaberStance} (Milestone 5: the
 * forms' stances and a dual-wielder's off-arm guard), which a technique pose then overrides and which
 * yields to the Force poses ({@link SaberStance#weight}).
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelSaberMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void oldrepublic$applySaberPose(HumanoidRenderState state, CallbackInfo ci) {
		SaberPose pose = state.getData(SaberPose.KEY);
		SaberStance stance = state.getData(SaberStance.KEY);
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

		if (stance != null) {
			// The stance yields to the Force poses (W6's HumanoidModelMixin, at the same point).
			SaberStance.apply(model, stance, pose == null || pose.amount() <= 0f, SaberStance.weight(state));
		}

		if (pose == null || pose.amount() <= 0f) {
			return;
		}

		ModelPart arm = pose.leftArm() ? model.leftArm : model.rightArm;
		// Outward is positive for the right arm and negative for the left.
		float outward = pose.leftArm() ? -1f : 1f;
		float a = pose.amount();
		float p = pose.progress();

		switch (pose.kind()) {
			case FINISHER -> {
				// Up overhead for the first third, then the chop down.
				float x = p < 0.35f ? Mth.lerp(p / 0.35f, -1.2f, -3.0f) : Mth.lerp((p - 0.35f) / 0.65f, -3.0f, -0.35f);
				set(arm, a, x, 0f, outward * 0.1f);
			}
			case HEAVY_WINDUP -> {
				set(arm, a, -2.5f, outward * 0.6f, outward * 0.5f);
				model.body.yRot = model.body.yRot + outward * 0.35f * a;
			}
			case HEAVY_STRIKE -> {
				set(arm, a, -1.45f, Mth.lerp(p, outward * 0.9f, -outward * 0.9f), 0f);
				model.body.yRot = model.body.yRot + Mth.lerp(p, outward * 0.35f, -outward * 0.3f) * a;
			}
			case LUNGE -> {
				set(arm, a, model.head.xRot - Mth.HALF_PI, model.head.yRot, 0f);
				model.body.xRot = model.body.xRot + 0.25f * a;
			}
			case SALUTE -> {
				// Blade up before the face for the middle of the change.
				float hold = Mth.sin(p * Mth.PI);
				set(arm, a * Math.min(1f, hold * 1.6f), -1.75f, -outward * 0.45f, 0f);
			}
			case THROW -> {
				float x = Mth.lerp(Math.min(1f, p * 2f), -2.6f, model.head.xRot - Mth.HALF_PI);
				set(arm, a, x, model.head.yRot, outward * 0.1f);
			}
			case CATCH -> set(arm, a, Mth.lerp(p, -1.9f, -0.9f), outward * 0.2f, 0f);
			case NPC_WINDUP -> set(arm, a * Math.min(1f, p * 3f), -2.3f, outward * 0.45f, outward * 0.35f);
			case GUARD -> set(arm, a, -1.1f, -outward * 0.6f, 0f);
			case BROKEN -> {
				set(arm, a, 0.25f, 0f, outward * 0.2f);
				model.head.xRot = model.head.xRot + 0.35f * a;
			}
		}
	}

	private static void set(ModelPart arm, float a, float x, float y, float z) {
		arm.xRot = Mth.lerp(a, arm.xRot, x);
		arm.yRot = Mth.lerp(a, arm.yRot, y);
		arm.zRot = Mth.lerp(a, arm.zRot, z);
	}
}

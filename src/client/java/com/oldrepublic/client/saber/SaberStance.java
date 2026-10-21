package com.oldrepublic.client.saber;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.FearFx;
import com.oldrepublic.client.force.MindTrickFx;
import com.oldrepublic.client.force.PowerPose;
import com.oldrepublic.client.force.PushPose;
import com.oldrepublic.client.force.RageFx;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberDual;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberStances;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * The idle saber stance carried on a living entity's render state (spec MILESTONE_5.md 10.1, 10.2,
 * 19.2), applied by the humanoid model after its vanilla animation and before any technique pose
 * ({@link SaberPose}), third person only:
 * <ul>
 * <li>Makashi: the blade held forward and up in one hand, the free hand tucked behind the back;</li>
 * <li>Djem So: a high guard, the blade drawn up over the shoulder, the free hand joining it;</li>
 * <li>Jar'Kai: the main blade high, the off blade low and out (only with two lit blades);</li>
 * <li>any other form (and saber NPCs, which have none): the ready stance, the blade held up and forward across the body,
 * the way a duelist carries a lit blade, instead of the vanilla arm hanging with the hilt jutting out;</li>
 * <li>any dual-wielder: the off arm in a low guard while it is not swinging.</li>
 * </ul>
 * An arm that is guarding (using its item) keeps the vanilla pose. A swing starts from the stance and
 * eases into the vanilla swing, and eases back into the stance as it ends ({@link #swingShare}), so
 * nothing snaps. Nothing is posed while riding, gliding, swimming or asleep, and an armour stand keeps
 * its own arm pose (only a second lit blade gets the off-arm guard). The stance yields to the Force
 * poses ({@link #weight}). The form comes from {@link SaberStances}, synced to everyone; nothing else
 * is sent.
 *
 * @param form      the stance's form
 * @param mainLeft  the main hand is the left arm
 * @param mainFree  the main arm is not guarding
 * @param offGuard  the off hand holds a lit single saber and is not guarding
 * @param offFree   the off hand is empty and idle (a form may use it)
 * @param mainSwing how far through a swing the main arm is, 0 to 1 (0: not swinging)
 * @param offSwing  how far through a swing the off arm is, 0 to 1
 * @param ready     a form without a stance of its own holds the blade ready (false for armour stands)
 */
public record SaberStance(SaberForm form, boolean mainLeft, boolean mainFree, boolean offGuard, boolean offFree, float mainSwing, float offSwing,
		boolean ready) {
	public static final RenderStateDataKey<SaberStance> KEY = RenderStateDataKey.create(() -> "oldrepublic:saber_stance");

	/** The stance {@code entity} shows now, or null for none. */
	public static @Nullable SaberStance of(LivingEntity entity, float partialTick) {
		if (entity.isSpectator() || entity.isPassenger() || entity.isFallFlying() || entity.isVisuallySwimming() || entity.isSleeping()) {
			return null;
		}

		ItemStack main = entity.getMainHandItem();
		ItemStack off = entity.getOffhandItem();

		if (!SaberDual.isSingleSaber(main) || !SaberComponents.isUsable(main)) {
			return null;
		}

		boolean offLit = SaberDual.isSingleSaber(off) && SaberComponents.isUsable(off);
		SaberForm form = SaberStances.stance(entity);
		boolean ready = !(entity instanceof ArmorStand);

		if (!hasOwnStance(form) && !offLit && !ready) {
			return null;
		}

		InteractionHand swingHand = entity.swingingArm == null ? InteractionHand.MAIN_HAND : entity.swingingArm;
		float swing = entity.getAttackAnim(partialTick);
		boolean using = entity.isUsingItem();
		InteractionHand useHand = entity.getUsedItemHand();
		boolean mainGuarding = using && useHand == InteractionHand.MAIN_HAND;
		boolean offGuarding = using && useHand == InteractionHand.OFF_HAND;
		float mainSwing = swingHand == InteractionHand.MAIN_HAND ? swing : 0f;
		float offSwing = swingHand == InteractionHand.OFF_HAND ? swing : 0f;
		return new SaberStance(form, entity.getMainArm() == HumanoidArm.LEFT, !mainGuarding, offLit && !offGuarding,
				off.isEmpty() && !offGuarding && offSwing <= 0f, mainSwing, offSwing, ready);
	}

	/** Makashi, Djem So and Jar'Kai have stances of their own; every other form holds the blade ready. */
	private static boolean hasOwnStance(SaberForm form) {
		return form == SaberForm.MAKASHI || form == SaberForm.DJEM_SO || form == SaberForm.JAR_KAI;
	}

	/**
	 * How much of an arm's pose is the vanilla swing rather than the stance, {@code swing} of the way through it: none at
	 * its start and end, all of it from a fifth of the way to four fifths, so the arm leaves the stance and comes back to it
	 * smoothly.
	 */
	private static float swingShare(float swing) {
		return swing <= 0f ? 0f : Math.min(1f, Mth.sin(swing * Mth.PI) * 1.7f);
	}

	/**
	 * How much of the stance shows over the Force poses on {@code state}, which W6's humanoid mixin
	 * applies at the same point of the animation: none while a power or Push pose, a Lift flail, a Mind
	 * Trick calm or a Fear shiver shows, and less as Force Rage's hunch comes in (none at its full
	 * amount), so an idle stance never hides them, whichever mixin runs first.
	 */
	public static float weight(HumanoidRenderState state) {
		if (state.getData(PowerPose.KEY) != null || state.getData(PushPose.KEY) != null || state.getData(PowerPose.FLAIL) != null
				|| state.getData(MindTrickFx.CALM) != null || state.getData(FearFx.SHIVER) != null) {
			return 0f;
		}

		RageFx.Posture rage = state.getData(RageFx.POSTURE);
		return rage == null ? 1f : 1f - Mth.clamp(rage.amount(), 0f, 1f);
	}

	/**
	 * Poses {@code model}'s arms for {@code stance}, blended over the pose they have by {@code weight}
	 * ({@link #weight}). {@code idle} is false while a technique pose holds the saber arm (the technique
	 * wins; the off arm keeps its guard).
	 */
	public static void apply(HumanoidModel<?> model, SaberStance stance, boolean idle, float weight) {
		if (weight <= 0f) {
			return;
		}

		ModelPart mainArm = stance.mainLeft ? model.leftArm : model.rightArm;
		ModelPart offArm = stance.mainLeft ? model.rightArm : model.leftArm;
		// Outward is positive zRot for the right arm and negative for the left; inward yRot is the opposite sign.
		float mainOut = stance.mainLeft ? -1f : 1f;
		float offOut = -mainOut;
		float main = weight * (1f - swingShare(stance.mainSwing));
		float offBlade = weight * (1f - swingShare(stance.offSwing));

		if (stance.offGuard) {
			if (stance.form == SaberForm.JAR_KAI) {
				// Jar'Kai: the off blade low and out to the side.
				set(offArm, offBlade, -0.35f, -offOut * 0.15f, offOut * 0.22f);
			} else {
				// A low guard: the off blade angled low across the front of the body.
				set(offArm, offBlade, 0.25f, -offOut * 0.3f, offOut * 0.1f);
			}
		}

		if (!idle || !stance.mainFree) {
			return;
		}

		switch (stance.form) {
			case MAKASHI -> {
				set(mainArm, main, -0.3f, -mainOut * 0.12f, mainOut * 0.05f);

				if (stance.offFree) {
					set(offArm, main, 0.6f, 0f, -offOut * 0.25f);
				}
			}
			case DJEM_SO -> {
				set(mainArm, main, -1.6f, -mainOut * 0.35f, 0f);

				if (stance.offFree) {
					set(offArm, main, -1.45f, -offOut * 0.75f, 0f);
				}
			}
			case JAR_KAI -> set(mainArm, main, -2.2f, -mainOut * 0.1f, mainOut * 0.25f);
			default -> {
				if (stance.ready) {
					// Ready: the arm forward and in, the blade up and across the front of the body.
					set(mainArm, main, -0.5f, -mainOut * 0.28f, mainOut * 0.05f);
				}
			}
		}
	}

	private static void set(ModelPart arm, float weight, float x, float y, float z) {
		arm.xRot = Mth.lerp(weight, arm.xRot, x);
		arm.yRot = Mth.lerp(weight, arm.yRot, y);
		arm.zRot = Mth.lerp(weight, arm.zRot, z);
	}
}

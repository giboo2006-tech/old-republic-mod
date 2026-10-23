package com.oldrepublic.client.enemy.cartel;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.cartel.CartelEnforcer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Renders a cartel enforcer (spec MILESTONE_5.md 12.1): the {@link CartelModel} in its kit's paint
 * ({@code textures/entity/cartel/cartel_<gunman|bruiser|captain>.png}), the weapon in hand (the
 * vanilla item-in-hand layer), the rifle shouldered while fighting, and the muzzle glow while a
 * shot charges ({@link CartelGlowLayer}, a tell). The bruiser is built a little heavier.
 */
public class CartelRenderer extends HumanoidMobRenderer<CartelEnforcer, CartelRenderer.State, CartelModel> {
	private static final Map<CartelEnforcer.Variant, Identifier> TEXTURES = new EnumMap<>(CartelEnforcer.Variant.class);
	private static final float BRUISER_SCALE = 1.06f;

	static {
		for (CartelEnforcer.Variant variant : CartelEnforcer.Variant.values()) {
			TEXTURES.put(variant, OldRepublicMod.id("textures/entity/cartel/cartel_" + variant.id() + ".png"));
		}
	}

	/** Render state of a cartel enforcer, from its synced data. */
	public static class State extends HumanoidRenderState {
		public CartelEnforcer.Variant variant = CartelEnforcer.Variant.GUNMAN;
		/** The shot's muzzle glow (a tell). */
		public boolean charging;
		/** {@code CartelEnforcer.ACTION_*}. */
		public int action = CartelEnforcer.ACTION_NONE;
		/** Ticks the tell has shown, with the partial tick. */
		public float tellTicks;
	}

	public CartelRenderer(EntityRendererProvider.Context context) {
		super(context, new CartelModel(context.bakeLayer(CartelModel.LAYER)), 0.5f);
		addLayer(new CartelGlowLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CartelEnforcer enforcer, State state, float partialTick) {
		super.extractRenderState(enforcer, state, partialTick);
		state.variant = enforcer.variant();
		state.charging = enforcer.isCharging();
		state.action = enforcer.action();
		state.tellTicks = state.charging || state.action != CartelEnforcer.ACTION_NONE ? enforcer.clientTellTicks() + partialTick : 0f;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);

		if (state.variant == CartelEnforcer.Variant.BRUISER) {
			poseStack.scale(BRUISER_SCALE, BRUISER_SCALE, BRUISER_SCALE);
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURES.get(state.variant);
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(CartelEnforcer enforcer, HumanoidArm arm) {
		if (arm == enforcer.getMainArm() && enforcer.getItemHeldByArm(arm).is(EnemyItems.BLASTER_RIFLE)) {
			// Two-handed shouldered aim while fighting (the M1 poacher's), carried low otherwise.
			return enforcer.isAggressive() ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
		}

		return super.getArmPose(enforcer, arm);
	}
}

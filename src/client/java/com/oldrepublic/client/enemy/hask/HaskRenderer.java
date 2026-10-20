package com.oldrepublic.client.enemy.hask;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.enemy.mandalorian.HunterModel;
import com.oldrepublic.client.enemy.mandalorian.HunterRenderState;
import com.oldrepublic.enemy.hask.HaskWarrior;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders a Clan Hask warrior (spec MILESTONE_5.md 12.1): the {@link HaskModel} in its kit's clan-iron
 * paint ({@code textures/entity/hask/hask_<rifle|blade|champion>.png}), posed from the hunter's synced
 * state (aim, gadget, flame, jets) and the warrior's own action. The champion stands a little taller.
 * The rifle's, the rocket's and the jets' looks are the M4 hunter's ({@code HunterFx}, by event id).
 */
public class HaskRenderer extends HumanoidMobRenderer<HaskWarrior, HunterRenderState, HunterModel> {
	private static final Map<HaskWarrior.Variant, Identifier> TEXTURES = new EnumMap<>(HaskWarrior.Variant.class);
	private static final float CHAMPION_SCALE = 1.06f;

	static {
		for (HaskWarrior.Variant variant : HaskWarrior.Variant.values()) {
			TEXTURES.put(variant, OldRepublicMod.id("textures/entity/hask/hask_" + variant.id() + ".png"));
		}
	}

	/** The hunter's render state plus the warrior's kit and action. */
	public static class HaskRenderState extends HunterRenderState {
		public HaskWarrior.Variant variant = HaskWarrior.Variant.RIFLE;
		/** {@code HaskWarrior.ACTION_*}. */
		public int action = HaskWarrior.ACTION_NONE;
		/** Ticks the action has shown, with the partial tick. */
		public float actionTicks;
	}

	public HaskRenderer(EntityRendererProvider.Context context) {
		super(context, new HaskModel(context.bakeLayer(HaskModel.LAYER)), 0.5f);
	}

	@Override
	public HunterRenderState createRenderState() {
		return new HaskRenderState();
	}

	@Override
	public void extractRenderState(HaskWarrior warrior, HunterRenderState state, float partialTick) {
		super.extractRenderState(warrior, state, partialTick);
		// The hunter's synced state, as the M4 HunterRenderer reads it.
		state.paint = 0;
		state.aiming = warrior.isCharging();
		state.gadget = warrior.gadget();
		state.tellTicks = warrior.clientTellTicks() + partialTick;
		state.flaming = warrior.isFlaming();
		state.jetting = warrior.isJetting();
		state.yielded = false;

		if (state instanceof HaskRenderState hask) {
			hask.variant = warrior.variant();
			hask.action = warrior.action();
			hask.actionTicks = hask.action == HaskWarrior.ACTION_NONE ? 0f : warrior.clientActionTicks() + partialTick;
		}
	}

	@Override
	protected void scale(HunterRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);

		if (state instanceof HaskRenderState hask && hask.variant == HaskWarrior.Variant.CHAMPION) {
			poseStack.scale(CHAMPION_SCALE, CHAMPION_SCALE, CHAMPION_SCALE);
		}
	}

	@Override
	public Identifier getTextureLocation(HunterRenderState state) {
		return TEXTURES.get(state instanceof HaskRenderState hask ? hask.variant : HaskWarrior.Variant.RIFLE);
	}
}

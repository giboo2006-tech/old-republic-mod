package com.oldrepublic.client.enemy.mandalorian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders a Mandalorian hunter (spec MILESTONE_4.md 10.1): the {@link HunterModel} in one of four
 * worn paints ({@code textures/entity/mandalorian/hunter_<paint>.png}, seeded at spawn), posed from
 * the synced tell, flame, jet and yield state. Torvin Kaast's renderer extends this one.
 */
public class HunterRenderer extends HumanoidMobRenderer<MandalorianHunter, HunterRenderState, HunterModel> {
	private static final Identifier[] PAINTS = new Identifier[MandalorianHunter.PAINTS];

	static {
		for (int i = 0; i < PAINTS.length; i++) {
			PAINTS[i] = OldRepublicMod.id("textures/entity/mandalorian/hunter_" + i + ".png");
		}
	}

	public HunterRenderer(EntityRendererProvider.Context context) {
		super(context, new HunterModel(context.bakeLayer(HunterModel.LAYER)), 0.5f);
	}

	@Override
	public HunterRenderState createRenderState() {
		return new HunterRenderState();
	}

	@Override
	public void extractRenderState(MandalorianHunter hunter, HunterRenderState state, float partialTick) {
		super.extractRenderState(hunter, state, partialTick);
		state.paint = hunter.paint();
		state.aiming = hunter.isCharging();
		state.gadget = hunter.gadget();
		state.tellTicks = hunter.clientTellTicks() + partialTick;
		state.flaming = hunter.isFlaming();
		state.jetting = hunter.isJetting();
		state.yielded = hunter.isYielded();
	}

	@Override
	protected void scale(HunterRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);

		if (state.yielded || state.kneeling || state.unmasked) {
			// API-CHECK: LivingEntityRenderer#scale runs after the model flip (positive y is down), as TrooperRenderer.
			poseStack.translate(0f, HunterModel.KNEEL_DROP, 0f);
		}
	}

	@Override
	public Identifier getTextureLocation(HunterRenderState state) {
		return PAINTS[Math.floorMod(state.paint, PAINTS.length)];
	}
}

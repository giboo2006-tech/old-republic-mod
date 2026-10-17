package com.oldrepublic.client.enemy;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.SithAcolyte;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders the Sith Acolyte (and its Brenn and deserter skins): humanoid model in robes, the red
 * saber in hand through the vanilla item-in-hand layer (the blade, hum, trail and technique poses
 * come from the saber feature), the Force Shove arm and the kneel.
 */
public class SithAcolyteRenderer extends HumanoidMobRenderer<SithAcolyte, AcolyteRenderState, AcolyteModel> {
	private static final Map<SithAcolyte.Skin, Identifier> TEXTURES = new EnumMap<>(SithAcolyte.Skin.class);

	static {
		for (SithAcolyte.Skin skin : SithAcolyte.Skin.values()) {
			TEXTURES.put(skin, OldRepublicMod.id("textures/entity/sith_acolyte_" + skin.id() + ".png"));
		}
	}

	public SithAcolyteRenderer(EntityRendererProvider.Context context) {
		super(context, new AcolyteModel(context.bakeLayer(AcolyteModel.LAYER)), 0.5f);
	}

	@Override
	public AcolyteRenderState createRenderState() {
		return new AcolyteRenderState();
	}

	@Override
	public void extractRenderState(SithAcolyte acolyte, AcolyteRenderState state, float partialTick) {
		super.extractRenderState(acolyte, state, partialTick);
		state.skin = acolyte.skin();
		state.shoving = acolyte.isShoving();
		state.kneel = acolyte.isKneeling() ? 1f : 0f;
	}

	@Override
	protected void scale(AcolyteRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		// API-CHECK: LivingEntityRenderer#scale runs after the model flip (positive y is down), as in 1.21.
		KneelPose.lower(poseStack, state.kneel);
	}

	@Override
	public Identifier getTextureLocation(AcolyteRenderState state) {
		return TEXTURES.get(state.skin);
	}
}

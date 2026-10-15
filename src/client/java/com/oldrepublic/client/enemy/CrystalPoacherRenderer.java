package com.oldrepublic.client.enemy;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.CrystalPoacher;
import com.oldrepublic.enemy.EnemyItems;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Renders the Crystal Poacher: humanoid model, rifle in hand (vanilla item-in-hand layer), the
 * rifle raised to the shoulder while it is fighting, and the muzzle glow during each attack tell.
 */
public class CrystalPoacherRenderer extends HumanoidMobRenderer<CrystalPoacher, PoacherRenderState, PoacherModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/crystal_poacher.png");

	public CrystalPoacherRenderer(EntityRendererProvider.Context context) {
		super(context, new PoacherModel(context.bakeLayer(PoacherModel.LAYER)), 0.5f);
		addLayer(new MuzzleGlowLayer(this));
	}

	@Override
	public PoacherRenderState createRenderState() {
		return new PoacherRenderState();
	}

	@Override
	public void extractRenderState(CrystalPoacher poacher, PoacherRenderState state, float partialTick) {
		super.extractRenderState(poacher, state, partialTick);
		state.charging = poacher.isCharging();
		state.chargeTicks = state.charging ? poacher.clientChargeTicks() + partialTick : 0f;
	}

	@Override
	public Identifier getTextureLocation(PoacherRenderState state) {
		return TEXTURE;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(CrystalPoacher poacher, HumanoidArm arm) {
		if (arm == poacher.getMainArm() && poacher.getItemHeldByArm(arm).is(EnemyItems.BLASTER_RIFLE)) {
			// Two-handed shouldered aim while fighting; carried low otherwise.
			return poacher.isAggressive() ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
		}

		return super.getArmPose(poacher, arm);
	}
}

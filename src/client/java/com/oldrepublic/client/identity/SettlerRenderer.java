package com.oldrepublic.client.identity;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.FrontierSettler;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders the Frontier Settler: one of three work-tunic skins, and while captive the kneeling pose
 * with the rope-bound skin of the same variant.
 */
public class SettlerRenderer extends HumanoidMobRenderer<FrontierSettler, SettlerRenderState, SettlerModel> {
	private static final Identifier[] FREE = textures("");
	private static final Identifier[] BOUND = textures("_bound");

	public SettlerRenderer(EntityRendererProvider.Context context) {
		super(context, new SettlerModel(context.bakeLayer(SettlerModel.LAYER)), 0.5f);
	}

	private static Identifier[] textures(String suffix) {
		Identifier[] ids = new Identifier[FrontierSettler.VARIANTS];

		for (int i = 0; i < ids.length; i++) {
			ids[i] = OldRepublicMod.id("textures/entity/frontier_settler_" + i + suffix + ".png");
		}

		return ids;
	}

	@Override
	public SettlerRenderState createRenderState() {
		return new SettlerRenderState();
	}

	@Override
	public void extractRenderState(FrontierSettler settler, SettlerRenderState state, float partialTick) {
		super.extractRenderState(settler, state, partialTick);
		state.variant = Math.floorMod(settler.variant(), FrontierSettler.VARIANTS);
		state.captive = settler.state() == FrontierSettler.State.CAPTIVE;
	}

	@Override
	public Identifier getTextureLocation(SettlerRenderState state) {
		return (state.captive ? BOUND : FREE)[state.variant];
	}
}

package com.oldrepublic.client.boss.kaast;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.kaast.TorvinKaast;
import com.oldrepublic.client.enemy.mandalorian.HunterRenderState;
import com.oldrepublic.client.enemy.mandalorian.HunterRenderer;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Renders Torvin Kaast (spec MILESTONE_4.md 10.5): the hunter's model in his own paint
 * ({@code textures/entity/kaast/torvin_kaast.png}), the cracked helmet from phase 3
 * ({@code torvin_kaast_cracked.png}), the vibro-axe, the mine kneel, the wind-up and the stagger,
 * and in his yield the helmet off in his hand. The daily hunter's echo is drawn pale and
 * see-through.
 */
public class KaastRenderer extends HunterRenderer {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/kaast/torvin_kaast.png");
	private static final Identifier CRACKED = OldRepublicMod.id("textures/entity/kaast/torvin_kaast_cracked.png");
	/** The echo's tint: pale blue-grey, a little more than half opaque. */
	private static final int ECHO_TINT = 0xA0C4D6E8;

	public KaastRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void extractRenderState(MandalorianHunter hunter, HunterRenderState state, float partialTick) {
		super.extractRenderState(hunter, state, partialTick);

		if (hunter instanceof TorvinKaast kaast) {
			state.axe = kaast.isAxeDrawn();
			state.windup = kaast.isWindingUp();
			state.kneeling = kaast.isKneeling();
			state.unmasked = kaast.isKnelt();
			state.staggered = kaast.isStaggered();
			state.cracked = kaast.phase() >= 3;
			state.echo = kaast.isEcho();
			state.yielded = false;
		}
	}

	@Override
	protected int getModelTint(HunterRenderState state) {
		return state.echo ? ECHO_TINT : super.getModelTint(state);
	}

	@Override
	protected @Nullable RenderType getRenderType(HunterRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (state.echo && bodyVisible) {
			return RenderTypes.entityTranslucent(getTextureLocation(state));
		}

		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	public Identifier getTextureLocation(HunterRenderState state) {
		return state.cracked ? CRACKED : TEXTURE;
	}
}

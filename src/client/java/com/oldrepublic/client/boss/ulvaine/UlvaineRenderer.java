package com.oldrepublic.client.boss.ulvaine;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ulvaine.DarthUlvaine;
import com.oldrepublic.client.enemy.hymn.FoeClock;
import com.oldrepublic.client.enemy.hymn.FoeGlow;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders Darth Ulvaine (spec MILESTONE_5.md 12.2): the {@link UlvaineModel} in black and violet
 * ({@code textures/entity/ulvaine/darth_ulvaine.png}), posed from her synced action, and lowered
 * into the kneel of her yield. Her echo is the same figure drawn translucent and pale violet (15.1:
 * the model drawn translucent, no particles), gathering out of the air while it forms. Her saber is
 * the item in her hand: the saber feature draws the blade and its guard poses.
 */
public class UlvaineRenderer extends HumanoidMobRenderer<DarthUlvaine, UlvaineRenderer.State, UlvaineModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/ulvaine/darth_ulvaine.png");
	/** The echo's colour: a pale violet-grey. */
	private static final int ECHO_RGB = 0xC8B8E8;
	/** The echo's opacity once formed. */
	private static final float ECHO_ALPHA = 0.62f;
	/** Ticks the echo takes to gather (the session's forming time). */
	private static final float FORM_TICKS = 40f;
	/** Ticks she takes to sink into the kneel. */
	private static final float KNEEL_TICKS = 10f;

	/** Render state of Darth Ulvaine, from her synced data. */
	public static class State extends HumanoidRenderState {
		public DarthUlvaine.Action action = DarthUlvaine.Action.NONE;
		/** Ticks the action has shown, with the partial tick (the pose eases in). */
		public float actionTicks;
		public boolean kneel;
		/** 0 to 1: how far she has sunk into the kneel. */
		public float kneelAmount;
		public boolean echo;
		/** The echo's opacity, 0 to 1 (it rises while the echo forms). */
		public float alpha = 1f;
		/** The fight phase, 1 to 3. */
		public int phase = 1;
	}

	private final FoeClock kneelClock = new FoeClock();
	private final FoeClock formClock = new FoeClock();

	public UlvaineRenderer(EntityRendererProvider.Context context) {
		super(context, new UlvaineModel(context.bakeLayer(UlvaineModel.LAYER)), 0.5f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DarthUlvaine ulvaine, State state, float partialTick) {
		super.extractRenderState(ulvaine, state, partialTick);
		float age = ulvaine.tickCount + partialTick;
		state.action = ulvaine.action();
		state.actionTicks = state.action == DarthUlvaine.Action.NONE ? 0f : ulvaine.actionAge() + partialTick;
		state.kneel = ulvaine.isKneeling();
		float knelt = kneelClock.ticksIn(ulvaine.getId(), state.kneel ? 1 : 0, age);
		state.kneelAmount = state.kneel ? Mth.clamp(knelt / KNEEL_TICKS, 0f, 1f) : 0f;
		state.echo = ulvaine.isEcho();
		state.phase = ulvaine.phase();

		if (state.echo) {
			float formed = formClock.ticksIn(ulvaine.getId(), ulvaine.isForming() ? 1 : 0, age);
			float presence = ulvaine.isForming() ? Mth.clamp(formed / FORM_TICKS, 0.05f, 1f) : 1f;
			state.alpha = ECHO_ALPHA * presence;
		} else {
			state.alpha = 1f;
		}
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);

		if (state.kneelAmount > 0f) {
			// The model space is flipped (y down): a positive y lowers her.
			poseStack.translate(0f, UlvaineModel.KNEEL_DROP * state.kneelAmount, 0f);
		}
	}

	@Override
	protected int getModelTint(State state) {
		return state.echo ? FoeGlow.argb(state.alpha, ECHO_RGB) : super.getModelTint(state);
	}

	@Override
	protected @Nullable RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (state.echo && bodyVisible) {
			return RenderTypes.entityTranslucent(getTextureLocation(state));
		}

		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}

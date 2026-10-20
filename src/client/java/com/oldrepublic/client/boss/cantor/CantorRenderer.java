package com.oldrepublic.client.boss.cantor;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.cantor.FirstCantor;
import com.oldrepublic.client.enemy.hymn.FoeGlow;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the First Cantor (spec MILESTONE_5.md 12.3): the {@link CantorModel} at 1.3 times a man's
 * size, always a little see-through ({@code textures/entity/cantor/first_cantor.png}, translucent)
 * with the light of his robes drawn over it ({@code first_cantor_glow.png}, full-bright). He bobs
 * gently where he floats and casts no shadow. While the choir shields him, a faint pale-gold shell
 * stands around him ({@link CantorShieldLayer}); while a silenced point's window is open, his light
 * falters to half (he can be hurt). He gathers out of the floor as he rises and comes apart as he
 * unravels (the wisps are {@code CantorFx}'s).
 */
public class CantorRenderer extends MobRenderer<FirstCantor, CantorRenderer.State, CantorModel> {
	static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/cantor/first_cantor.png");
	private static final Identifier GLOW = OldRepublicMod.id("textures/entity/cantor/first_cantor_glow.png");
	/** 2.6 blocks: the humanoid model is two. */
	private static final float SCALE = 1.3f;
	/** His robes' colour over the texture: a cold pale white. */
	private static final int TINT_RGB = 0xE4ECFF;
	private static final float ALPHA = 0.82f;
	/** The rise and the unravelling (raid.riseTicks and raid.unravelTicks defaults; the session drives the real length). */
	private static final float RISE_TICKS = 40f;
	private static final float UNRAVEL_TICKS = 60f;

	/** Render state of the First Cantor, from his synced data. */
	public static class State extends HumanoidRenderState {
		public FirstCantor.Action action = FirstCantor.Action.NONE;
		/** Ticks the action has shown, with the partial tick. */
		public float actionTicks;
		public int phase;
		public boolean shielded;
		public boolean window;
		/** The body's opacity, 0 to 1. */
		public float alpha = ALPHA;
		/** The robes' light, 0 to 1. */
		public float glow = 1f;
		/** How far he floats up and down now (blocks). */
		public float bob;
	}

	public CantorRenderer(EntityRendererProvider.Context context) {
		super(context, new CantorModel(context.bakeLayer(CantorModel.LAYER)), 0f);
		addLayer(new SpectralGlowLayer<>(this, GLOW, state -> state.glow));
		addLayer(new CantorShieldLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FirstCantor cantor, State state, float partialTick) {
		super.extractRenderState(cantor, state, partialTick);
		state.action = cantor.action();
		state.actionTicks = cantor.actionAge() + partialTick;
		state.phase = cantor.phase();
		state.shielded = cantor.isShielded();
		state.window = cantor.isWindowOpen();
		state.bob = Mth.sin(state.ageInTicks * 0.06f) * 0.06f;

		float presence = 1f;

		if (state.action == FirstCantor.Action.RISE) {
			presence = 0.25f + 0.75f * Mth.clamp(state.actionTicks / RISE_TICKS, 0f, 1f);
		} else if (state.action == FirstCantor.Action.UNRAVEL) {
			presence = 1f - Mth.clamp(state.actionTicks / UNRAVEL_TICKS, 0f, 1f);
		}

		state.alpha = ALPHA * presence * (state.window ? 0.85f : 1f);
		state.glow = presence * (state.window ? 0.45f : 1f);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);
		// The model space is flipped (y down): a negative y lifts him.
		poseStack.translate(0f, -state.bob, 0f);
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
		// He never falls over: he unravels (and the session removes him).
		return 0f;
	}

	@Override
	protected int getModelTint(State state) {
		return FoeGlow.argb(state.alpha, TINT_RGB);
	}

	@Override
	protected @Nullable RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (bodyVisible) {
			return RenderTypes.entityTranslucent(getTextureLocation(state));
		}

		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}

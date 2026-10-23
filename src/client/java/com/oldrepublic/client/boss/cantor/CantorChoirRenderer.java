package com.oldrepublic.client.boss.cantor;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.cantor.CantorChoir;
import com.oldrepublic.client.enemy.hymn.FoeClock;
import com.oldrepublic.client.enemy.hymn.FoeGlow;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders a choir echo (spec MILESTONE_5.md 12.3): the {@link CantorChoirModel} (0.6 by 1.9 blocks),
 * pale and see-through ({@code textures/entity/cantor/cantor_choir.png}, translucent) with the light
 * of its robes over it ({@code cantor_choir_glow.png}); it dims while silenced. It casts no shadow
 * and does not topple when it falls: it fades away (its last wisps are {@code CantorFx}'s).
 */
public class CantorChoirRenderer extends MobRenderer<CantorChoir, CantorChoirRenderer.State, CantorChoirModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/cantor/cantor_choir.png");
	private static final Identifier GLOW = OldRepublicMod.id("textures/entity/cantor/cantor_choir_glow.png");
	/** 1.9 blocks: the humanoid model is two. */
	private static final float SCALE = 0.95f;
	private static final int TINT_RGB = 0xD2E2F4;
	private static final float ALPHA = 0.7f;
	/** The vanilla death lasts 20 ticks: the echo fades out over it. */
	private static final float DEATH_TICKS = 20f;

	/** Render state of a choir echo. */
	public static class State extends HumanoidRenderState {
		public boolean silent;
		/** Ticks since it last fell silent or took up the song again, with the partial tick. */
		public float silentTicks;
		/** Spreads the choir's sway so the four do not move as one. */
		public int seed;
		/** The body's opacity, 0 to 1. */
		public float alpha = ALPHA;
		/** The robes' light, 0 to 1. */
		public float glow = 1f;
	}

	private final FoeClock clock = new FoeClock();

	public CantorChoirRenderer(EntityRendererProvider.Context context) {
		super(context, new CantorChoirModel(context.bakeLayer(CantorChoirModel.LAYER)), 0f);
		addLayer(new SpectralGlowLayer<>(this, GLOW, state -> state.glow));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CantorChoir echo, State state, float partialTick) {
		super.extractRenderState(echo, state, partialTick);
		state.silent = echo.isSilent();
		state.silentTicks = clock.ticksIn(echo.getId(), state.silent ? 1 : 0, echo.tickCount + partialTick);
		state.seed = echo.getId() % 7;
		float fading = state.deathTime > 0f ? 1f - Mth.clamp(state.deathTime / DEATH_TICKS, 0f, 1f) : 1f;
		state.alpha = ALPHA * fading * (state.silent ? 0.6f : 1f);
		state.glow = fading * (state.silent ? 0.3f : 1f);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected float getFlipDegrees() {
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

package com.oldrepublic.client.boss.ulvaine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ulvaine.HymnResonator;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders a Hymn resonator (spec MILESTONE_5.md 12.2): the {@link ResonatorModel} in dark stone and
 * violet crystal ({@code textures/entity/ulvaine/hymn_resonator.png}) with a steady inner light
 * ({@link ResonatorGlowLayer}). A broken spire does not topple like a creature: it sinks and
 * shrinks into its plinth while its light goes out (the shards are {@code UlvaineFx}'s).
 */
public class ResonatorRenderer extends MobRenderer<HymnResonator, ResonatorRenderer.State, ResonatorModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/ulvaine/hymn_resonator.png");
	/** The vanilla death lasts 20 ticks. */
	private static final float DEATH_TICKS = 20f;

	/** Render state of a resonator. */
	public static class State extends LivingEntityRenderState {
		/** 1 while it stands, falling to 0 as it breaks. */
		public float light = 1f;
	}

	public ResonatorRenderer(EntityRendererProvider.Context context) {
		super(context, new ResonatorModel(context.bakeLayer(ResonatorModel.LAYER)), 0.45f);
		addLayer(new ResonatorGlowLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HymnResonator resonator, State state, float partialTick) {
		super.extractRenderState(resonator, state, partialTick);
		state.light = state.deathTime > 0f ? Mth.clamp(1f - state.deathTime / (DEATH_TICKS * 0.5f), 0f, 1f) : 1f;
	}

	@Override
	protected float getFlipDegrees() {
		// A spire does not fall over: scale() sinks it instead.
		return 0f;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);

		if (state.deathTime > 0f) {
			float broken = Mth.clamp(state.deathTime / DEATH_TICKS, 0f, 1f);
			// The model space is flipped (y down): scaling about the feet keeps the plinth on the floor.
			poseStack.translate(0f, 1.501f, 0f);
			poseStack.scale(1f - broken * 0.25f, 1f - broken * 0.7f, 1f - broken * 0.25f);
			poseStack.translate(0f, -1.501f, 0f);
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}

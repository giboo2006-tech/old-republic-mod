package com.oldrepublic.client.enemy.scour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.scour.SandScour;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the sand scour (spec MILESTONE_5.md 12.1): the {@link ScourModel} in dune-sand plates
 * ({@code textures/entity/scour/sand_scour.png}), the matriarch darker and banded
 * ({@code sand_scour_matriarch.png}; her size is the server's scale attribute). Burrowed, it is drawn
 * as nothing at all, without a shadow: only the ripple and the hiss give it away (and Force Sense).
 */
public class ScourRenderer extends MobRenderer<SandScour, ScourRenderer.State, ScourModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/scour/sand_scour.png");
	private static final Identifier MATRIARCH = OldRepublicMod.id("textures/entity/scour/sand_scour_matriarch.png");

	/** Render state of a sand scour, from its synced data. */
	public static class State extends LivingEntityRenderState {
		public boolean burrowed;
		public boolean matriarch;
		/** {@code SandScour.ACTION_*}. */
		public int action = SandScour.ACTION_NONE;
		/** Ticks the action has shown, with the partial tick. */
		public float actionTicks;
		/** The bite's swing, 0 to 1. */
		public float bite;
	}

	public ScourRenderer(EntityRendererProvider.Context context) {
		super(context, new ScourModel(context.bakeLayer(ScourModel.LAYER)), 0.7f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SandScour scour, State state, float partialTick) {
		super.extractRenderState(scour, state, partialTick);
		state.burrowed = scour.isBurrowed();
		state.matriarch = scour.isMatriarch();
		state.action = scour.action();
		state.actionTicks = state.action == SandScour.ACTION_NONE ? 0f : scour.clientActionTicks() + partialTick;
		state.bite = scour.getAttackAnim(partialTick);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.burrowed) {
			// Hidden in the sand: nothing to draw.
			return;
		}

		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected float getShadowRadius(State state) {
		return state.burrowed ? 0f : super.getShadowRadius(state);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.matriarch ? MATRIARCH : TEXTURE;
	}
}

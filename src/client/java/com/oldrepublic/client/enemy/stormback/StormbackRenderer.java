package com.oldrepublic.client.enemy.stormback;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.enemy.hymn.FoeClock;
import com.oldrepublic.enemy.stormback.Stormback;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the stormback (spec MILESTONE_5.md 12.1): the {@link StormbackModel} in its slate-grey hide
 * and basalt plates ({@code textures/entity/stormback/stormback.png}); charged by lightning, the
 * plates crackle with light ({@link StormbackChargeLayer}).
 */
public class StormbackRenderer extends MobRenderer<Stormback, StormbackRenderer.State, StormbackModel> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/stormback/stormback.png");

	/** Render state of a stormback, from its synced data. */
	public static class State extends LivingEntityRenderState {
		/** {@code Stormback.STATE_*}. */
		public int state = Stormback.STATE_IDLE;
		/** Ticks in the current state, with the partial tick. */
		public float stateTicks;
		public boolean charged;
		/** Spreads the herd's grazing so they do not dip their heads together. */
		public float grazeOffset;
		/** The entity's id, so each one's crackle flickers on its own. */
		public int seed;
	}

	private final FoeClock clock = new FoeClock();

	public StormbackRenderer(EntityRendererProvider.Context context) {
		super(context, new StormbackModel(context.bakeLayer(StormbackModel.LAYER)), 1.0f);
		addLayer(new StormbackChargeLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Stormback beast, State state, float partialTick) {
		super.extractRenderState(beast, state, partialTick);
		state.state = beast.state();
		state.stateTicks = clock.ticksIn(beast.getId(), state.state, beast.tickCount + partialTick);
		state.charged = beast.isCharged();
		state.grazeOffset = (beast.getId() % 17) * 0.37f;
		state.seed = beast.getId();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}
}

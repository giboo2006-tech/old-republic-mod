package com.oldrepublic.client.enemy.creature;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.creature.AshHound;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the ash hound (spec MILESTONE_4.md 10.4): the {@link AshHoundModel} in its grey,
 * ash-dusted hide with pale eyes ({@code textures/entity/creature/ash_hound.png}), posed from the
 * synced pack state.
 */
public class AshHoundRenderer extends MobRenderer<AshHound, AshHoundRenderer.State, AshHoundModel> {
	private static final Identifier HIDE = OldRepublicMod.id("textures/entity/creature/ash_hound.png");

	/** Render state of an ash hound, from its synced data. */
	public static class State extends LivingEntityRenderState {
		public int state = AshHound.STATE_IDLE;
		/** Ticks in the current state, with the partial tick. */
		public float stateTicks;
	}

	private final StateClock clock = new StateClock();

	public AshHoundRenderer(EntityRendererProvider.Context context) {
		super(context, new AshHoundModel(context.bakeLayer(AshHoundModel.LAYER)), 0.45f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AshHound hound, State state, float partialTick) {
		super.extractRenderState(hound, state, partialTick);
		state.state = hound.state();
		state.stateTicks = clock.ticksIn(hound.getId(), state.state, hound.tickCount + partialTick);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return HIDE;
	}
}

package com.oldrepublic.client.enemy.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.creature.Hollowhorn;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the hollowhorn (spec MILESTONE_4.md 10.3): the {@link HollowhornModel} in its shaggy
 * brown coat ({@code textures/entity/creature/hollowhorn.png}), a bull a little larger with heavier
 * horns ({@code hollowhorn_bull.png}), and a corrupted one dark-veined ({@code hollowhorn_corrupted.png}).
 * Calves come out small through the server's scale attribute.
 */
public class HollowhornRenderer extends MobRenderer<Hollowhorn, HollowhornRenderer.State, HollowhornModel> {
	private static final Identifier COAT = OldRepublicMod.id("textures/entity/creature/hollowhorn.png");
	private static final Identifier BULL = OldRepublicMod.id("textures/entity/creature/hollowhorn_bull.png");
	private static final Identifier CORRUPTED = OldRepublicMod.id("textures/entity/creature/hollowhorn_corrupted.png");
	/** A bull stands this much larger. */
	private static final float BULL_SCALE = 1.1f;

	/** Render state of a hollowhorn, from its synced data. */
	public static class State extends LivingEntityRenderState {
		public int state = Hollowhorn.STATE_IDLE;
		/** Ticks in the current state, with the partial tick. */
		public float stateTicks;
		public boolean calf;
		public boolean bull;
		public boolean corrupted;
		/** Spreads the herd's grazing so they do not dip their heads together. */
		public float grazeOffset;
	}

	private final StateClock clock = new StateClock();

	public HollowhornRenderer(EntityRendererProvider.Context context) {
		super(context, new HollowhornModel(context.bakeLayer(HollowhornModel.LAYER)), 0.9f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Hollowhorn beast, State state, float partialTick) {
		super.extractRenderState(beast, state, partialTick);
		state.state = beast.state();
		state.stateTicks = clock.ticksIn(beast.getId(), state.state, beast.tickCount + partialTick);
		state.calf = beast.isCalf();
		state.bull = beast.isBull();
		state.corrupted = beast.isCorrupted();
		state.grazeOffset = (beast.getId() % 17) * 0.37f;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		super.scale(state, poseStack);

		if (state.bull && !state.calf) {
			poseStack.scale(BULL_SCALE, BULL_SCALE, BULL_SCALE);
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.corrupted ? CORRUPTED : state.bull ? BULL : COAT;
	}
}

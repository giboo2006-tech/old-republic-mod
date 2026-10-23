package com.oldrepublic.client.enemy.hymn;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.hymn.Hymnsworn;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Renders a Hymnsworn (spec MILESTONE_5.md 12.1): the {@link HymnswornModel} in its kind's robes
 * ({@code textures/entity/hymn/hymnsworn_<chanter|blade|caller>.png}), posed from the synced action.
 * A shade (the Long Hymn's, 12.3) is drawn pale and see-through, like the M4 echo.
 */
public class HymnswornRenderer extends HumanoidMobRenderer<Hymnsworn, HymnswornRenderer.State, HymnswornModel> {
	private static final Map<Hymnsworn.Variant, Identifier> TEXTURES = new EnumMap<>(Hymnsworn.Variant.class);
	/** A shade's tint: pale blue-grey, a little more than half opaque. */
	private static final int SHADE_TINT = 0x9CC4D2EC;

	static {
		for (Hymnsworn.Variant variant : Hymnsworn.Variant.values()) {
			TEXTURES.put(variant, OldRepublicMod.id("textures/entity/hymn/hymnsworn_" + variant.id() + ".png"));
		}
	}

	/** Render state of a Hymnsworn, from its synced data. */
	public static class State extends HumanoidRenderState {
		public Hymnsworn.Variant variant = Hymnsworn.Variant.CHANTER;
		/** {@code Hymnsworn.ACTION_*}. */
		public int action = Hymnsworn.ACTION_NONE;
		/** Ticks the action has shown, with the partial tick (the pose eases in). */
		public float actionTicks;
		public boolean shade;
	}

	public HymnswornRenderer(EntityRendererProvider.Context context) {
		super(context, new HymnswornModel(context.bakeLayer(HymnswornModel.LAYER)), 0.5f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Hymnsworn hymnsworn, State state, float partialTick) {
		super.extractRenderState(hymnsworn, state, partialTick);
		state.variant = hymnsworn.variant();
		state.action = hymnsworn.action();
		state.actionTicks = state.action == Hymnsworn.ACTION_NONE ? 0f : hymnsworn.clientActionTicks() + partialTick;
		state.shade = hymnsworn.isShade();
	}

	@Override
	protected int getModelTint(State state) {
		return state.shade ? SHADE_TINT : super.getModelTint(state);
	}

	@Override
	protected @Nullable RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (state.shade && bodyVisible) {
			return RenderTypes.entityTranslucent(getTextureLocation(state));
		}

		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURES.get(state.variant);
	}
}

package com.oldrepublic.client.boss.sentinel;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.construct.SentinelVariant;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renders the Ruin Sentinel (spec 7.7): the {@link RuinSentinelModel} scaled to 2.3 blocks, the
 * variant's skin ({@code textures/entity/ruin_sentinel/<variant>.png}) and its lumen layer
 * ({@code <variant>_lumen.png}, drawn full bright and additive, so the chest core and face slit glow
 * in the dark: pale blue for a Watcher, red for a Tomb sentinel). The lumen is dark while dormant,
 * ignites over the wake, brightens through the lance tell, burns at full while the core is exposed
 * and gutters out on death. With flashing effects off every flicker and pulse is a steady glow.
 *
 * <p>On death the sentinel does not topple: it slumps and sinks where it stands while
 * {@code SentinelFx} breaks it into stone and bronze debris.
 */
public class RuinSentinelRenderer extends HumanoidMobRenderer<RuinSentinel, RuinSentinelRenderState, RuinSentinelModel> {
	/** The humanoid mesh is 2 blocks tall; the sentinel is 2.3. */
	private static final float SCALE = 2.3f / 2f;
	/** How far the body sinks into its own rubble by the end of the death animation, in blocks. */
	private static final float DEATH_SINK = 0.35f;
	private static final Map<SentinelVariant, Identifier> SKINS = new EnumMap<>(SentinelVariant.class);
	private static final Map<SentinelVariant, Identifier> LUMENS = new EnumMap<>(SentinelVariant.class);

	static {
		for (SentinelVariant variant : SentinelVariant.values()) {
			String name = variant.name().toLowerCase(Locale.ROOT);
			SKINS.put(variant, OldRepublicMod.id("textures/entity/ruin_sentinel/" + name + ".png"));
			LUMENS.put(variant, OldRepublicMod.id("textures/entity/ruin_sentinel/" + name + "_lumen.png"));
		}
	}

	public RuinSentinelRenderer(EntityRendererProvider.Context context) {
		super(context, new RuinSentinelModel(context.bakeLayer(RuinSentinelModel.LAYER)), 0.75f);
		addLayer(new LumenLayer(this));
	}

	@Override
	public RuinSentinelRenderState createRenderState() {
		return new RuinSentinelRenderState();
	}

	@Override
	public void extractRenderState(RuinSentinel sentinel, RuinSentinelRenderState state, float partialTick) {
		super.extractRenderState(sentinel, state, partialTick);
		state.variant = sentinel.variant();
		state.dormant = sentinel.isDormant();
		state.action = sentinel.action();
		state.actionAge = sentinel.actionAge() + partialTick;
		state.flashing = Vfx.flashing();
		float bowEase = Mth.clamp((sentinel.bowAge() + partialTick) / RuinSentinelModel.BOW_TICKS, 0f, 1f);
		state.bow = sentinel.isBowing() ? bowEase : 1f - bowEase;
	}

	@Override
	protected void scale(RuinSentinelRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		poseStack.scale(SCALE, SCALE, SCALE);

		if (state.deathTime > 0f) {
			// API-CHECK: LivingEntityRenderer#scale runs after the model flip (positive y is down), as in 1.21.
			poseStack.translate(0f, DEATH_SINK * Mth.clamp(state.deathTime / 20f, 0f, 1f), 0f);
		}
	}

	/** A construct crumbles where it stands; it never topples over. */
	@Override
	protected float getFlipDegrees() {
		return 0f;
	}

	@Override
	public Identifier getTextureLocation(RuinSentinelRenderState state) {
		return SKINS.get(state.variant);
	}

	/**
	 * Brightness of the lumen, 0 to 1, from the synced state: dark while dormant; ignites over the
	 * wake (flickering with flashing effects on); a steady, slowly breathing glow while awake;
	 * brighter in the slam tell; climbing through the lance tell; full while the core is exposed;
	 * unsteady while staggered; guttering out over the death.
	 */
	static float lumenBrightness(RuinSentinelRenderState state) {
		float age = state.actionAge;

		if (state.deathTime > 0f) {
			float left = 1f - Mth.clamp(state.deathTime / 12f, 0f, 1f);
			return state.flashing ? left * (0.55f + 0.45f * Math.abs(Mth.sin(state.deathTime * 2.3f))) : left;
		}

		if (state.dormant) {
			return 0f;
		}

		return switch (state.action) {
			case WAKING -> {
				float p = Mth.clamp(age / 20f, 0f, 1f);
				yield state.flashing && p < 0.7f ? p * (0.5f + 0.5f * Math.abs(Mth.sin(age * 1.7f))) : p * 0.7f;
			}
			case SLAM_TELL -> 0.8f;
			case LANCE_TELL -> {
				float p = Mth.clamp(age / 24f, 0f, 1f);
				float pulse = state.flashing && p > 0.6f ? 0.9f + 0.1f * Mth.sin(age * 2.4f) : 1f;
				yield (0.65f + 0.35f * p) * pulse;
			}
			case EXPOSED -> 1f;
			case STAGGERED -> state.flashing ? 0.3f + 0.15f * Math.abs(Mth.sin(age * 3.1f)) : 0.4f;
			default -> state.flashing ? 0.65f + 0.05f * Mth.sin(state.ageInTicks * 0.15f) : 0.65f;
		};
	}

	/** The glowing lumen, face slit and seams: the model drawn again with the lumen texture, additive and full bright. */
	private static final class LumenLayer extends RenderLayer<RuinSentinelRenderState, RuinSentinelModel> {
		/** Packed block and sky light 15. */
		private static final int FULL_BRIGHT = 0xF000F0;

		LumenLayer(RenderLayerParent<RuinSentinelRenderState, RuinSentinelModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, RuinSentinelRenderState state, float yRot, float xRot) {
			float brightness = lumenBrightness(state);

			if (state.isInvisible || brightness <= 0.01f) {
				return;
			}

			// The eyes type adds the texture to the frame, so scaling the colour dims the glow.
			int v = Mth.clamp(Math.round(brightness * 255f), 0, 255);
			int tint = 0xFF000000 | (v << 16) | (v << 8) | v;
			// OrderedSubmitNodeCollector#submitModel(model, state, pose, type, light, overlay, tint colour, sprite, outline colour, crumbling).
			collector.submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(LUMENS.get(state.variant)), FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor, null);
		}
	}
}

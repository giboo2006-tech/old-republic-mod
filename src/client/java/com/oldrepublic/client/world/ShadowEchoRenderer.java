package com.oldrepublic.client.world;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.PushPose;
import com.oldrepublic.world.trial.ShadowEchoEntity;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.core.BlockPos;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Shadow Echo as a shadow of its owner: the player model (so it mirrors the player's
 * shape, poses and push) with the original violet shadow skin, which has a rim light baked in,
 * and the translucent entity render type. The owner's own skin is not used: a multiply tint
 * cannot desaturate it, so in CI it read as an ordinary player. It is drawn full-bright so it
 * reads against the unlit chamber; at rest a light tint dims it a little and during a lunge or
 * push tell the tint lifts to full brightness. Eye glints and wisps come from
 * {@code WorldAmbience}. The push windup and release reuse the force feature's {@link PushPose},
 * so the echo visibly mirrors the player's own push; the lunge windup crouches.
 *
 * <p>The render state is deliberately a plain {@link HumanoidRenderState} subclass, not
 * {@code AvatarRenderState}: the entity render dispatcher looks up the renderer for an
 * {@code AvatarRenderState} from its skin model (the vanilla player renderers), not from the
 * entity type, so with an avatar state the echo was drawn by the vanilla player renderer with the
 * default skin and none of the overrides here (texture, tint, render type) ran.
 */
// API-CHECK: EntityRenderDispatcher#getRenderer(EntityRenderState) routes AvatarRenderState by state.skin.model() to the player renderers; any other state goes to the renderer of state.entityType.
final class ShadowEchoRenderer extends HumanoidMobRenderer<ShadowEchoEntity, ShadowEchoRenderer.EchoRenderState, HumanoidModel<ShadowEchoRenderer.EchoRenderState>> {
	/** Our own humanoid state, so the dispatcher sends it back to this renderer. */
	static final class EchoRenderState extends HumanoidRenderState {
	}

	/** Dim violet figure with a baked rim light, so the echo reads as a shadow even without tinting. */
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/shadow_echo.png");
	private static final int ALPHA = 0xD0;
	/**
	 * Resting tint, multiplied into the model colour by {@code LivingEntityRenderer#submit}. It only
	 * dims the baked texture slightly, so the tell can brighten it to full (multiply cannot exceed
	 * the texture). The texture alone already reads as the violet shadow.
	 */
	private static final int REST_RGB = 0xB8ACD4;
	/** Tint at the peak of a lunge or push tell: the texture at full brightness. */
	private static final int TELL_RGB = 0xFFFFFF;
	/** Block light the echo is drawn with at rest and during tells (full-bright). */
	private static final int REST_LIGHT = 15;
	private static final int TELL_LIGHT = 15;
	/** 0 to 1, how strongly the current tell shows (set per render state). */
	private static final RenderStateDataKey<Float> TELL = RenderStateDataKey.create(() -> "oldrepublic:echo_tell");
	private static final PushPose NO_PUSH = new PushPose(0f, 0f, false);

	ShadowEchoRenderer(EntityRendererProvider.Context context) {
		// The player layer's overlay parts (hat, jacket, sleeves, trousers) are children of the
		// humanoid parts and render with them; their texture areas are transparent in our skin.
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public EchoRenderState createRenderState() {
		return new EchoRenderState();
	}

	@Override
	public void extractRenderState(ShadowEchoEntity echo, EchoRenderState state, float partialTick) {
		super.extractRenderState(echo, state, partialTick);

		byte action = echo.action();
		float ticks = echo.clientActionTicks() + partialTick;
		state.isCrouching = action == ShadowEchoEntity.ACTION_LUNGE_WINDUP;

		float tell = switch (action) {
			case ShadowEchoEntity.ACTION_LUNGE_WINDUP, ShadowEchoEntity.ACTION_PUSH_WINDUP -> Mth.clamp(ticks / 8f, 0f, 1f) * (0.75f + 0.25f * Mth.sin(ticks * 0.9f));
			case ShadowEchoEntity.ACTION_LUNGING, ShadowEchoEntity.ACTION_PUSH_RELEASE -> 0.6f;
			default -> 0f;
		};
		state.setData(TELL, tell);

		PushPose pose = NO_PUSH;

		if (action == ShadowEchoEntity.ACTION_PUSH_WINDUP) {
			pose = new PushPose(Mth.clamp(ticks / 10f, 0f, 1f), 0f, false);
		} else if (action == ShadowEchoEntity.ACTION_PUSH_RELEASE) {
			pose = new PushPose(0f, Mth.clamp(1f - ticks / 8f, 0f, 1f) * 0.6f + 0.4f, false);
		}

		state.setData(PushPose.KEY, pose);
	}

	@Override
	public Identifier getTextureLocation(EchoRenderState state) {
		return TEXTURE;
	}

	@Override
	protected int getModelTint(EchoRenderState state) {
		Float tell = state.getData(TELL);
		float t = tell == null ? 0f : tell;
		int r = Math.round(Mth.lerp(t, (REST_RGB >> 16) & 0xFF, (TELL_RGB >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (REST_RGB >> 8) & 0xFF, (TELL_RGB >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, REST_RGB & 0xFF, TELL_RGB & 0xFF));
		return (ALPHA << 24) | (r << 16) | (g << 8) | b;
	}

	@Override
	protected int getBlockLightLevel(ShadowEchoEntity echo, BlockPos pos) {
		byte action = echo.action();
		boolean telling = action != ShadowEchoEntity.ACTION_IDLE;
		return Math.max(super.getBlockLightLevel(echo, pos), telling ? TELL_LIGHT : REST_LIGHT);
	}

	@Override
	protected @Nullable RenderType getRenderType(EchoRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (!bodyVisible && !translucent) {
			return super.getRenderType(state, bodyVisible, translucent, glowing);
		}

		return RenderTypes.entityTranslucent(getTextureLocation(state));
	}
}

package com.oldrepublic.client.world;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.PushPose;
import com.oldrepublic.world.trial.ShadowEchoEntity;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Shadow Echo as its owner, shadowed and slightly see-through: the player model with
 * the owner's skin (or an original fallback texture when the owner is not loaded on this client),
 * a dim violet tint and the translucent entity render type. It carries its own faint light
 * (block light 11) so it reads against the unlit chamber, and during a lunge or push tell it
 * flares violet-white at full light; eye glints and wisps come from {@code WorldAmbience}. The push windup and release
 * reuse the force feature's {@link PushPose} so the echo visibly mirrors the player's own push;
 * the lunge windup crouches.
 *
 * <p>Uses the wide player model; slim skins show a one-pixel seam on the arms, hidden by the tint.
 */
final class ShadowEchoRenderer extends HumanoidMobRenderer<ShadowEchoEntity, AvatarRenderState, PlayerModel> {
	private static final Identifier FALLBACK = OldRepublicMod.id("textures/entity/shadow_echo.png");
	private static final int ALPHA = 0xE0;
	/** Resting tint: a dim violet cast, lit full-bright so the figure reads against dark stone. */
	private static final int REST_RGB = 0x8C7AB4;
	/** Tint at the peak of a lunge or push tell: a clear violet-white flare. */
	private static final int TELL_RGB = 0xE6D2FF;
	/** Block light the echo is drawn with at rest (it carries its own faint light) and during tells. */
	private static final int REST_LIGHT = 11;
	private static final int TELL_LIGHT = 15;
	/** 0 to 1, how strongly the current tell shows (set per render state). */
	private static final RenderStateDataKey<Float> TELL = RenderStateDataKey.create(() -> "oldrepublic:echo_tell");
	private static final PushPose NO_PUSH = new PushPose(0f, 0f, false);
	private static final UUID NO_OWNER = new UUID(0L, 0L);
	/** The echo's owner, carried on the render state for {@link #getTextureLocation}. */
	private static final RenderStateDataKey<UUID> OWNER = RenderStateDataKey.create(() -> "oldrepublic:echo_owner");

	ShadowEchoRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(ShadowEchoEntity echo, AvatarRenderState state, float partialTick) {
		super.extractRenderState(echo, state, partialTick);
		state.showHat = true;
		state.showJacket = true;
		state.showLeftPants = true;
		state.showRightPants = true;
		state.showLeftSleeve = true;
		state.showRightSleeve = true;
		state.showCape = false;
		state.id = echo.getId();

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
		UUID owner = echo.ownerId();
		state.setData(OWNER, owner == null ? NO_OWNER : owner);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		UUID owner = state.getData(OWNER);
		AbstractClientPlayer player = owner == null || NO_OWNER.equals(owner) ? null : findPlayer(owner);

		if (player != null) {
			return player.getSkin().body().texturePath();
		}

		return FALLBACK;
	}

	@Override
	protected int getModelTint(AvatarRenderState state) {
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
	protected @Nullable RenderType getRenderType(AvatarRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (!bodyVisible && !translucent) {
			return super.getRenderType(state, bodyVisible, translucent, glowing);
		}

		return RenderTypes.entityTranslucent(getTextureLocation(state));
	}

	private static @Nullable AbstractClientPlayer findPlayer(UUID id) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null) {
			return null;
		}

		for (AbstractClientPlayer player : client.level.players()) {
			if (id.equals(player.getUUID())) {
				return player;
			}
		}

		return null;
	}
}

package com.oldrepublic.client.world;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.PushPose;
import com.oldrepublic.world.trial.ShadowEchoEntity;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
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
 * Draws the Shadow Echo as its owner, darkened and slightly see-through: the player model with
 * the owner's skin (or an original fallback texture when the owner is not loaded on this client),
 * a deep violet-black tint and the translucent entity render type. The push windup and release
 * reuse the force feature's {@link PushPose} so the echo visibly mirrors the player's own push;
 * the lunge windup crouches.
 *
 * <p>Uses the wide player model; slim skins show a one-pixel seam on the arms, hidden by the tint.
 */
final class ShadowEchoRenderer extends HumanoidMobRenderer<ShadowEchoEntity, AvatarRenderState, PlayerModel> {
	private static final Identifier FALLBACK = OldRepublicMod.id("textures/entity/shadow_echo.png");
	private static final int ALPHA = 0xD8;
	private static final int TINT = (ALPHA << 24) | (0x2A << 16) | (0x22 << 8) | 0x3A;
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
		return TINT;
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

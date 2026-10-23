package com.oldrepublic.client.boss;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.OrrenVasch;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Renders Orren Vasch (spec 8.1, W5 plan 4.10): {@link OrrenVaschModel} with the {@code base} skin in
 * phases 1 and 2 and the {@code unkept} skin in phase 3 (hood up, frayed cloak, the holocron's crimson
 * seams), his amber saber through the vanilla item-in-hand layer (the saber feature draws the blade,
 * hum and trail). In phase 3 the holocron lights him a little from below (a raised block light; the
 * crimson motes are {@code BossFx}'s).
 *
 * <p>The rematch's echo uses the desaturated {@code echo} and {@code echo_unkept} skins, drawn with the
 * translucent entity type and a cool grey tint whose alpha is its presence: it gathers out of smoke over
 * the 60-tick form and drains away as it dissolves; while it is mostly smoke its blade is not drawn.
 *
 * <p>Kneeling (the yield, Peaceful) lowers the body; Spent sags it a little; after the execution's
 * stroke he falls forward and lies face down.
 */
public class OrrenVaschRenderer extends HumanoidMobRenderer<OrrenVasch, OrrenVaschRenderState, OrrenVaschModel> {
	/** How far the body is lowered, in blocks, when fully knelt. */
	static final float KNEEL_DROP = 0.55f;
	/** How far the body sags while Spent. */
	private static final float SPENT_DROP = 0.08f;
	/** Lift while lying face down, so the chest rests on the floor instead of in it. */
	private static final float FALL_LIFT = 0.16f;
	/** The echo's tint at full presence: cool grey, a little translucent. */
	private static final int ECHO_RGB = 0xD4D8DE;
	private static final int ECHO_ALPHA = 0xB8;
	/** Below this presence the echo is still mostly smoke: no blade or other layers yet. */
	private static final float LAYERS_PRESENCE = 0.6f;
	/** The holocron's glow from below in phase 3, as a minimum block light. */
	private static final int HOLOCRON_LIGHT = 6;
	private static final Identifier BASE = OldRepublicMod.id("textures/entity/orren_vasch/base.png");
	private static final Identifier UNKEPT = OldRepublicMod.id("textures/entity/orren_vasch/unkept.png");
	private static final Identifier ECHO = OldRepublicMod.id("textures/entity/orren_vasch/echo.png");
	private static final Identifier ECHO_UNKEPT = OldRepublicMod.id("textures/entity/orren_vasch/echo_unkept.png");

	public OrrenVaschRenderer(EntityRendererProvider.Context context) {
		super(context, new OrrenVaschModel(context.bakeLayer(OrrenVaschModel.LAYER)), 0.5f);
	}

	@Override
	public OrrenVaschRenderState createRenderState() {
		return new OrrenVaschRenderState();
	}

	@Override
	public void extractRenderState(OrrenVasch vasch, OrrenVaschRenderState state, float partialTick) {
		super.extractRenderState(vasch, state, partialTick);
		state.phase = vasch.phase();
		state.action = vasch.action();
		state.actionAge = vasch.actionAge() + partialTick;
		state.kneel = vasch.isKneeling() ? 1f : 0f;
		state.echo = vasch.isEcho();
		state.presence = state.echo ? BossFx.presence(vasch, partialTick) : 1f;
		state.fall = vasch.isFallen() ? BossFx.fall(vasch, partialTick) : 0f;
		state.holocron = state.phase >= 3 && state.fall <= 0f;
		state.flashing = Vfx.flashing();

		if (state.action == OrrenVasch.Action.LUNGE && state.kneel <= 0f) {
			state.isCrouching = true;
		}
	}

	@Override
	protected void setupRotations(OrrenVaschRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);

		if (state.fall > 0f) {
			// Face down along his facing (the model's front is -z here), resting on his chest.
			poseStack.translate(0f, FALL_LIFT * state.fall, 0f);
			poseStack.mulPose(Axis.XP.rotationDegrees(-90f * state.fall));
		}
	}

	@Override
	protected void scale(OrrenVaschRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		// API-CHECK: LivingEntityRenderer#scale runs after the model flip (positive y is down), as in 1.21 (same as SithAcolyteRenderer).
		float drop = KNEEL_DROP * state.kneel;

		if (state.action == OrrenVasch.Action.SPENT && state.kneel <= 0f) {
			drop += SPENT_DROP;
		}

		if (drop > 0f && state.fall <= 0f) {
			poseStack.translate(0f, drop, 0f);
		}
	}

	@Override
	public Identifier getTextureLocation(OrrenVaschRenderState state) {
		boolean unkept = state.phase >= 3;

		if (state.echo) {
			return unkept ? ECHO_UNKEPT : ECHO;
		}

		return unkept ? UNKEPT : BASE;
	}

	@Override
	protected int getModelTint(OrrenVaschRenderState state) {
		if (!state.echo) {
			return super.getModelTint(state);
		}

		int alpha = Math.round(ECHO_ALPHA * Math.max(0f, Math.min(1f, state.presence)));
		return (alpha << 24) | ECHO_RGB;
	}

	@Override
	protected @Nullable RenderType getRenderType(OrrenVaschRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (!state.echo || !bodyVisible && !translucent) {
			return super.getRenderType(state, bodyVisible, translucent, glowing);
		}

		return RenderTypes.entityTranslucent(getTextureLocation(state));
	}

	@Override
	protected boolean shouldRenderLayers(OrrenVaschRenderState state) {
		return super.shouldRenderLayers(state) && (!state.echo || state.presence >= LAYERS_PRESENCE);
	}

	@Override
	protected int getBlockLightLevel(OrrenVasch vasch, BlockPos pos) {
		int light = super.getBlockLightLevel(vasch, pos);
		return vasch.phase() >= 3 && !vasch.isFallen() ? Math.max(light, HOLOCRON_LIGHT) : light;
	}
}

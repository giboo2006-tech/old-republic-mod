package com.oldrepublic.client.travel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.travel.CourierShuttle;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Renders the courier shuttle (spec MILESTONE_4.md 4.1, 4.6): the paint of its owner's path, the
 * hull code on both sides of the nose, scorch marks as the hull wears, running lights (they blink
 * unless flashing effects are off; one is dead below the jump minimum), the nozzle glow as the
 * engines run, the struts and ramp moving with the flight, and the hulk's tarp pulled back when it
 * stands up. Nothing is drawn while the ship is in hyperspace.
 */
public class CourierShuttleRenderer extends EntityRenderer<CourierShuttle, CourierShuttleRenderState> {
	private static final Identifier[] PAINTS = {
			OldRepublicMod.id("textures/entity/ship/courier_shuttle_watch.png"),
			OldRepublicMod.id("textures/entity/ship/courier_shuttle_imperial.png"),
			OldRepublicMod.id("textures/entity/ship/courier_shuttle_vasch.png")
	};
	private static final Identifier LIGHTS = OldRepublicMod.id("textures/entity/ship/courier_shuttle_lights.png");
	private static final Identifier LIGHTS_DAMAGED = OldRepublicMod.id("textures/entity/ship/courier_shuttle_lights_damaged.png");
	private static final Identifier ENGINES = OldRepublicMod.id("textures/entity/ship/courier_shuttle_engines.png");
	private static final Identifier SCORCH = OldRepublicMod.id("textures/entity/ship/courier_shuttle_scorch.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** Model units are an eighth of a block. */
	private static final float SCALE = 2f;
	/** Ticks the struts take to fold or deploy, the ramp to move, the tarp to come off. */
	private static final float STRUT_TICKS = 20f;
	private static final float RAMP_TICKS = 16f;
	private static final float STAND_TICKS = 40f;
	/** Scorch marks start to show below this hull and are full at 0. */
	private static final float SCORCH_FROM = 70f;
	/** The hull code: where it sits on the nose (blocks, ship space) and how large. */
	private static final float CODE_SIDE = 1.39f;
	private static final float CODE_UP = 1.8f;
	private static final float CODE_FORWARD = 4.25f;
	private static final float CODE_SCALE = 0.035f;
	private static final int CODE_COLOR = 0xFF2B2F33;

	private final CourierShuttleModel model;
	private final Font font;

	public CourierShuttleRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new CourierShuttleModel(context.bakeLayer(CourierShuttleModel.LAYER));
		this.font = context.getFont();
		this.shadowRadius = 3.5f;
	}

	@Override
	public CourierShuttleRenderState createRenderState() {
		return new CourierShuttleRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(CourierShuttle ship) {
		// The model overhangs the 7 × 7 box by about a block and a half at the wings, nose and tail.
		return ship.getBoundingBox().inflate(2.5, 0.5, 2.5);
	}

	@Override
	public void extractRenderState(CourierShuttle ship, CourierShuttleRenderState state, float partialTick) {
		super.extractRenderState(ship, state, partialTick);
		state.yRot = ship.getYRot(partialTick);
		state.xRot = ship.getXRot(partialTick);
		ShipState shipState = ship.state();
		state.state = shipState.ordinal();
		state.hull = ship.hull();
		state.phase = ship.phase();
		state.upgrades = ship.upgrades();
		state.paint = Mth.clamp(ship.paint(), 0, PAINTS.length - 1);
		state.hullCode = ship.hullCode();
		state.hidden = state.phase == ShipRules.PHASE_HYPERSPACE;

		float gear = Mth.clamp((ship.gearAge() + partialTick) / STRUT_TICKS, 0f, 1f);
		state.struts = ship.gearDown() ? gear : 1f - gear;
		float phaseT = ship.phaseAge() + partialTick;
		state.ramp = ramp(state.phase, phaseT);

		if (shipState == ShipState.HULK) {
			state.standUp = 0f;
		} else if (ship.previousState() == ShipState.HULK.ordinal()) {
			state.standUp = Mth.clamp((ship.stateAge() + partialTick) / STAND_TICKS, 0f, 1f);
		} else {
			state.standUp = 1f;
		}

		state.engine = engine(shipState, state.phase, phaseT, state.ageInTicks);
		state.lights = lights(shipState, state.phase, state.ageInTicks);
		state.lightDead = shipState == ShipState.DISABLED;
	}

	/** The ramp: lowered on the ground, closing at the spool, closed in flight, lowering at the settle. */
	static float ramp(int phase, float phaseTicks) {
		return switch (phase) {
			case ShipRules.PHASE_GROUND -> 1f;
			case ShipRules.PHASE_SPOOL -> 1f - Mth.clamp(phaseTicks / RAMP_TICKS, 0f, 1f);
			case ShipRules.PHASE_SETTLE -> Mth.clamp(phaseTicks / RAMP_TICKS, 0f, 1f);
			default -> 0f;
		};
	}

	/** Nozzle glow: a faint idle when fuelled, rising at the spool, full in the climb, strong at the helm, easing off at the settle; it stutters below 20 hull. */
	static float engine(ShipState state, int phase, float phaseTicks, float age) {
		float glow = switch (phase) {
			case ShipRules.PHASE_GROUND -> state == ShipState.READY ? 0.12f : 0f;
			case ShipRules.PHASE_SPOOL -> 0.2f + 0.6f * Mth.clamp(phaseTicks / 40f, 0f, 1f);
			case ShipRules.PHASE_CLIMB, ShipRules.PHASE_JUMP -> 1f;
			case ShipRules.PHASE_DESCENT -> 0.75f;
			case ShipRules.PHASE_HELM -> 0.85f;
			case ShipRules.PHASE_SETTLE -> 0.6f * (1f - Mth.clamp(phaseTicks / 20f, 0f, 1f));
			default -> 0f;
		};

		if (glow > 0f && state == ShipState.DISABLED) {
			glow *= 0.7f + 0.3f * Mth.sin(age * 1.7f) * Mth.sin(age * 0.43f);
		}

		return glow;
	}

	/** Running lights: off on a hulk; steady in flight; a slow blink on the ground (steady with flashing off). */
	static float lights(ShipState state, int phase, float age) {
		if (state == ShipState.HULK) {
			return 0f;
		}

		if (phase != ShipRules.PHASE_GROUND) {
			return 1f;
		}

		if (!Vfx.flashing()) {
			return 0.8f;
		}

		return age % 30f < 6f ? 1f : 0.5f;
	}

	@Override
	public void submit(CourierShuttleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.hidden) {
			return;
		}

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot));

		poseStack.pushPose();
		poseStack.scale(-SCALE, -SCALE, SCALE);
		collector.submitModel(model, state, poseStack, PAINTS[state.paint], state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		float scorch = Mth.clamp((SCORCH_FROM - state.hull) / SCORCH_FROM, 0f, 1f);

		// OrderedSubmitNodeCollector#submitModel(model, state, pose, type, light, overlay, tint colour, sprite, outline colour, crumbling).
		if (scorch > 0.02f) {
			int alpha = Math.round(scorch * 255f);
			collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(SCORCH), state.lightCoords, OverlayTexture.NO_OVERLAY,
					(alpha << 24) | 0xFFFFFF, null, state.outlineColor, null);
		}

		if (state.lights > 0.01f) {
			collector.submitModel(model, state, poseStack, RenderTypes.eyes(state.lightDead ? LIGHTS_DAMAGED : LIGHTS), FULL_BRIGHT,
					OverlayTexture.NO_OVERLAY, grey(state.lights), null, state.outlineColor, null);
		}

		if (state.engine > 0.01f) {
			collector.submitModel(model, state, poseStack, RenderTypes.eyes(ENGINES), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, grey(state.engine), null,
					state.outlineColor, null);
		}

		poseStack.popPose();

		if (state.standUp >= 1f && !state.hullCode.isEmpty()) {
			submitHullCode(state, poseStack, collector, 1);
			submitHullCode(state, poseStack, collector, -1);
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** The hull code on one side of the nose ({@code side} 1 the right, −1 the left), reading toward the nose on the right and the tail on the left. */
	private void submitHullCode(CourierShuttleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int side) {
		FormattedCharSequence text = Component.literal(state.hullCode).getVisualOrderText();
		float width = font.width(text);
		poseStack.pushPose();
		// Ship space: the right-hand side is −x before the model's flip.
		poseStack.translate(-side * CODE_SIDE, CODE_UP, CODE_FORWARD);
		poseStack.mulPose(Axis.YP.rotationDegrees(-90f * side));
		poseStack.scale(CODE_SCALE, -CODE_SCALE, CODE_SCALE);
		// submitText(pose, x, y, text, dropShadow, displayMode, light, colour, background colour, outline colour): the order of
		// TextFeatureRenderer$Submit's components (m4_w3 probe).
		collector.submitText(poseStack, -width / 2f, -4.5f, text, false, Font.DisplayMode.NORMAL, state.lightCoords, CODE_COLOR, 0, 0);
		poseStack.popPose();
	}

	/** An opaque grey of {@code brightness}: the eyes type adds the texture, so a darker tint dims the glow. */
	private static int grey(float brightness) {
		int v = Mth.clamp(Math.round(brightness * 255f), 0, 255);
		return 0xFF000000 | (v << 16) | (v << 8) | v;
	}
}

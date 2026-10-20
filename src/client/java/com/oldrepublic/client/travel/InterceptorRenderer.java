package com.oldrepublic.client.travel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.travel.Interceptor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Renders the interceptor (spec MILESTONE_5.md 4.9) as the courier is drawn
 * ({@link CourierShuttleRenderer#submitShip}): the owner's path paint, the hull code on both sides
 * of the hull behind the nose, scorch as the hull wears, running lights, the nozzle glow, the struts
 * and the canopy moving with the flight, and the fitted weapons with the turret on the gunner's aim.
 */
public class InterceptorRenderer extends EntityRenderer<Interceptor, CourierShuttleRenderState> {
	private static final CourierShuttleRenderer.Skin SKIN = new CourierShuttleRenderer.Skin(new Identifier[] {
			OldRepublicMod.id("textures/entity/ship/interceptor_watch.png"),
			OldRepublicMod.id("textures/entity/ship/interceptor_imperial.png"),
			OldRepublicMod.id("textures/entity/ship/interceptor_vasch.png")
	}, OldRepublicMod.id("textures/entity/ship/interceptor_lights.png"), OldRepublicMod.id("textures/entity/ship/interceptor_lights_damaged.png"),
			OldRepublicMod.id("textures/entity/ship/interceptor_engines.png"), OldRepublicMod.id("textures/entity/ship/interceptor_scorch.png"));
	/** The hull code on the hull's sides, ahead of the wings. */
	private static final CourierShuttleRenderer.Code CODE = new CourierShuttleRenderer.Code(0.885f, 1.2f, 1.0f, 0.025f);

	private final InterceptorModel model;
	private final Font font;

	public InterceptorRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new InterceptorModel(context.bakeLayer(InterceptorModel.LAYER));
		this.font = context.getFont();
		this.shadowRadius = 2.5f;
	}

	@Override
	public CourierShuttleRenderState createRenderState() {
		return new CourierShuttleRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(Interceptor ship) {
		// The nose, the wing tips and the fin overhang the 5 × 5 box by up to a block.
		return ship.getBoundingBox().inflate(1.5, 0.5, 1.5);
	}

	@Override
	public void extractRenderState(Interceptor ship, CourierShuttleRenderState state, float partialTick) {
		super.extractRenderState(ship, state, partialTick);
		CourierShuttleRenderer.extractShip(ship, state, partialTick, SKIN.paints().length);
	}

	@Override
	public void submit(CourierShuttleRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		CourierShuttleRenderer.submitShip(model, SKIN, CODE, font, state, poseStack, collector);
		super.submit(state, poseStack, collector, camera);
	}
}

package com.oldrepublic.client.enemy.droid;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the seeker droid (spec MILESTONE_4.md 10.7): the {@link SeekerModel} in its dull grey
 * ({@code textures/entity/droid/seeker_droid.png}). Rattle uses the same model in Kaast's paint
 * through a subclass that also draws its scan line.
 */
public class SeekerRenderer extends MobRenderer<Mob, SeekerRenderer.State, SeekerModel> {
	private static final Identifier SEEKER = OldRepublicMod.id("textures/entity/droid/seeker_droid.png");

	/** Render state of a seeker or of Rattle. */
	public static class State extends LivingEntityRenderState {
		/** Rattle: the scan line from the lens to the scanned entity, relative to the droid; null when not scanning. */
		public @Nullable Vec3 scanTo;
		public Vec3 scanFrom = Vec3.ZERO;
		public boolean flashing = true;
	}

	private final Identifier texture;

	protected SeekerRenderer(EntityRendererProvider.Context context, Identifier texture) {
		super(context, new SeekerModel(context.bakeLayer(SeekerModel.LAYER)), 0.25f);
		this.texture = texture;
	}

	/** The story's seeker droid. */
	public static SeekerRenderer seeker(EntityRendererProvider.Context context) {
		return new SeekerRenderer(context, SEEKER);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	/** A destroyed probe sinks where it hovered; it never topples over. */
	@Override
	protected float getFlipDegrees() {
		return 0f;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return texture;
	}
}

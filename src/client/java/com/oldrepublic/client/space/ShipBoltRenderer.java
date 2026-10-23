package com.oldrepublic.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.client.enemy.droid.GlowShapes;
import com.oldrepublic.space.ShipBolt;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a ship's laser bolt (spec MILESTONE_5.md 5.3, 15.1): a long streak of light, a white-hot
 * core inside two layers of additive glow in the colour of whoever fired it (red for a Sith ship,
 * green-white for a Jedi's, yellow for an Independent's, violet for the Hymn and the gunship, orange
 * for the kajidic, amber for the clan). The streak is about a tick of flight long, never longer than
 * the way the bolt has flown (so it never pokes back into the gun), and faces along the synced
 * velocity. No particles trail it. Bolt geometry runs along +X, rotated like vanilla arrows.
 */
public class ShipBoltRenderer extends EntityRenderer<ShipBolt, ShipBoltRenderer.State> {
	/** The streak's length per block per tick of speed, and its bounds (blocks). */
	private static final float LENGTH_PER_SPEED = 0.9f;
	private static final float MIN_LENGTH = 1.5f;
	private static final float MAX_LENGTH = 4.0f;
	/** How far the head leads the bolt's position (blocks). */
	private static final float HEAD = 0.4f;

	/** Core, glow and haze colours (ARGB) and the thickness of one style. */
	private record Palette(int core, int glow, int haze, float width) {
	}

	private static final Palette SITH = new Palette(0xFFFFE6E6, 0xB0FF2436, 0x48C0101E, 1f);
	private static final Palette JEDI = new Palette(0xFFF0FFF2, 0xB040F070, 0x4820C050, 1f);
	private static final Palette INDEPENDENT = new Palette(0xFFFFFCE6, 0xB0FFD830, 0x48E0B010, 1f);
	private static final Palette HYMN = new Palette(0xFFF6EEFF, 0xB0B464FF, 0x487A30E0, 1f);
	private static final Palette RAIDER = new Palette(0xFFFFEEDC, 0xB0FF7A1A, 0x48FF5410, 1f);
	private static final Palette HASK = new Palette(0xFFFFF6DC, 0xB8FFB43A, 0x50FF9412, 1f);
	private static final Palette GUNSHIP = new Palette(0xFFFAF2FF, 0xC09A50FF, 0x586A28E0, 1.7f);

	/** Render state of a ship bolt. */
	public static class State extends EntityRenderState {
		/** Facing along the flight (the projectile convention: yaw from +Z toward +X, pitch positive up), degrees. */
		public float yaw;
		public float pitch;
		/** The streak's length (blocks). */
		public float length = MIN_LENGTH;
		public ShipBolt.Style style = ShipBolt.Style.INDEPENDENT;
	}

	public ShipBoltRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ShipBolt bolt, State state, float partialTick) {
		super.extractRenderState(bolt, state, partialTick);
		// The client flies the bolt at its synced velocity (ShipBolt#tick), so its motion is the true heading.
		Vec3 v = bolt.getDeltaMovement();
		double speed = v.length();

		if (speed > 1.0e-4) {
			state.yaw = (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG);
			state.pitch = (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG);
		}

		float full = Mth.clamp((float) speed * LENGTH_PER_SPEED, MIN_LENGTH, MAX_LENGTH);
		state.length = Math.max(0.3f, Math.min(full, state.ageInTicks * (float) speed));
		state.style = bolt.style();
	}

	private static Palette palette(ShipBolt.Style style) {
		// The style is synced by ordinal (append only): a later one falls back to the plain yellow.
		return switch (style) {
			case SITH -> SITH;
			case JEDI -> JEDI;
			case HYMN -> HYMN;
			case RAIDER -> RAIDER;
			case HASK -> HASK;
			case GUNSHIP -> GUNSHIP;
			default -> INDEPENDENT;
		};
	}

	@Override
	protected int getBlockLightLevel(ShipBolt bolt, BlockPos pos) {
		return 15;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0f, 0.15f, 0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw - 90f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(state.pitch));

		Palette p = palette(state.style);
		float w = p.width();
		float l = state.length;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			GlowShapes.box(pose, buffer, -l * 1.05f, -0.3f * w, -0.3f * w, HEAD + 0.12f, 0.3f * w, 0.3f * w, p.haze());
			GlowShapes.box(pose, buffer, -l * 0.9f, -0.14f * w, -0.14f * w, HEAD + 0.06f, 0.14f * w, 0.14f * w, p.glow());
			GlowShapes.box(pose, buffer, -l * 0.75f, -0.055f * w, -0.055f * w, HEAD, 0.055f * w, 0.055f * w, p.core());
		});
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}

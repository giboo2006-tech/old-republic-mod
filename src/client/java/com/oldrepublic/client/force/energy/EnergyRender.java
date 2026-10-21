package com.oldrepublic.client.force.energy;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRenderTypes;
import com.oldrepublic.client.fx.GlowRibbon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Light drawn in code for the energy and mind powers (lightning strokes, sparks, flares, glowing motes and their trails), as
 * camera-facing soft beams ({@link GlowRibbon}). Each frame every live shape is laid out once, then drawn twice: its light
 * added to the frame ({@link GlowRenderTypes#glow()}), which blooms at night, and its hot core alpha blended
 * ({@code RenderTypes.debugQuads()}), which still reads against a bright sky. Positions are taken relative to the camera in
 * double precision before they become floats, so nothing shimmers far from the world's origin. No shader and no
 * post-processing: the glow is geometry and blending.
 *
 * <p>Shapes age on the client tick (not while the game is paused) and end by themselves; an effect can take its own away at
 * once by owner (a lightning channel's stop), and a level change or a disconnect clears everything.
 */
public final class EnergyRender {
	/** Something the energy effects draw. */
	public interface Shape {
		/** Advances one client tick; false once it is over (it is then dropped). */
		boolean tick(ClientLevel level);

		/** Lays out this frame (where its anchors are now, how bright it is); called once a frame, before drawing. */
		default void prepare(Frame frame) {
		}

		/** Its light, added to the frame. */
		void glow(Frame frame, PoseStack.Pose pose, VertexConsumer buffer);

		/** Its alpha-blended part: hot cores and edges that read by day. */
		default void solid(Frame frame, PoseStack.Pose pose, VertexConsumer buffer) {
		}

		/** The effect it belongs to ({@link #owner(int, int)}), or 0. */
		default long owner() {
			return 0L;
		}
	}

	/** One frame: the camera, the partial tick and the clock. */
	public static final class Frame {
		/** Closer to the eye than this, beams thin out, so light at a first-person hand never fills the view. */
		private static final float NEAR = 2.2f;
		private static final float NEAREST_SCALE = 0.22f;
		final Vec3 cam;
		final float partial;
		/** Client ticks with the partial tick, for flicker. */
		final float time;
		/** The camera in the pose's space (the origin unless the pose moves it). */
		Vector3f eye = new Vector3f();

		Frame(Vec3 cam, float partial, float time) {
			this.cam = cam;
			this.partial = partial;
			this.time = time;
		}

		/** {@code p} relative to the camera. */
		public Vector3f rel(Vec3 p) {
			return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
		}

		/** The point relative to the camera. */
		public Vector3f rel(double x, double y, double z) {
			return new Vector3f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z));
		}

		/** How much of its width a beam keeps at {@code p} (relative to the camera): less close to the eye. */
		public float near(Vector3fc p) {
			return Mth.clamp(p.length() / NEAR, NEAREST_SCALE, 1f);
		}

		public float partial() {
			return partial;
		}

		public float time() {
			return time;
		}
	}

	/** At most this many shapes at once; past it the oldest go first. */
	private static final int MAX_SHAPES = 1400;
	private static final List<Shape> SHAPES = new ArrayList<>();
	/** Shapes added while the others tick (a spark that bursts), joined after. */
	private static final List<Shape> PENDING = new ArrayList<>();
	private static boolean ticking;
	private static boolean initialised;
	private static long ticks;
	private static @Nullable ClientLevel lastLevel;

	/** Hooks the level render, the client tick and the disconnect. Called once from {@code EnergyVfx}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(EnergyRender::render);
		ClientTickEvents.END_CLIENT_TICK.register(EnergyRender::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Adds a shape; it lives until its own {@link Shape#tick} ends it, its owner is {@link #kill}ed or the level changes. */
	public static void add(@Nullable Shape shape) {
		if (shape == null) {
			return;
		}

		if (ticking) {
			PENDING.add(shape);
			return;
		}

		if (SHAPES.size() >= MAX_SHAPES) {
			SHAPES.remove(0);
		}

		SHAPES.add(shape);
	}

	/** Takes away at once every shape of {@code owner}. */
	public static void kill(long owner) {
		SHAPES.removeIf(shape -> shape.owner() == owner);
	}

	/** Shapes of {@code owner} showing now (the client tests read it). */
	public static int count(long owner) {
		int n = 0;

		for (Shape shape : SHAPES) {
			if (shape.owner() == owner) {
				n++;
			}
		}

		return n;
	}

	/** An owner key: which effect ({@code kind}, a small constant of the effect's own) for which creature. */
	public static long owner(int kind, int entityId) {
		return (long) kind << 32 | entityId & 0xFFFFFFFFL;
	}

	/** The energy effects' clock: client ticks while the game is not paused. */
	public static long ticks() {
		return ticks;
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			clear();
		}

		ClientLevel level = mc.level;

		if (level == null || mc.isPaused()) {
			return;
		}

		ticks++;
		ticking = true;

		try {
			SHAPES.removeIf(shape -> !shape.tick(level));
		} finally {
			ticking = false;
		}

		for (Shape shape : PENDING) {
			add(shape);
		}

		PENDING.clear();
	}

	private static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();

		if (SHAPES.isEmpty() || mc.level == null) {
			return;
		}

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Frame frame = new Frame(context.levelState().cameraRenderState.pos, partial, ticks + partial);
		Shape[] shapes = SHAPES.toArray(new Shape[0]);

		for (Shape shape : shapes) {
			shape.prepare(frame);
		}

		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (pose, buffer) -> {
			frame.eye = GlowRibbon.eye(pose.pose());

			for (Shape shape : shapes) {
				shape.glow(frame, pose, buffer);
			}
		});
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			frame.eye = GlowRibbon.eye(pose.pose());

			for (Shape shape : shapes) {
				shape.solid(frame, pose, buffer);
			}
		});
	}

	/** Forgets every shape (level change, disconnect). */
	public static void clear() {
		SHAPES.clear();
		PENDING.clear();
	}

	private EnergyRender() {
	}
}

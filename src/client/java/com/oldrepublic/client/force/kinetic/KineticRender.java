package com.oldrepublic.client.force.kinetic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.GlowRibbon;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the kinetic powers' drawn shapes (a push's pressure front, the bent air at a charging palm) and their short timed
 * work (dust kicked up as a front passes, a trail behind a thrown creature). Shapes are plain alpha-blended
 * position-and-colour quads ({@code RenderTypes.debugQuads()}) drawn after the translucent world: no glow, no
 * post-processing. Everything here has a lifetime, is capped in number, and is dropped on disconnect and on a change of
 * level.
 */
public final class KineticRender {
	/** Something drawn in the level every frame until it ends. */
	public interface Shape {
		/** One client tick; false once it has ended. */
		boolean tick();

		/**
		 * Draws it into alpha-blended position-and-colour quads. Positions are relative to {@code cam} (the camera's position in
		 * the level); {@code eye} is the camera in the pose's own space.
		 */
		void render(PoseStack.Pose pose, VertexConsumer buffer, Vec3 cam, Vector3fc eye, float partialTick);
	}

	/** Work done once a client tick (not while paused) until it says it is finished. */
	@FunctionalInterface
	public interface Task {
		/** One tick of work; false once it is finished. */
		boolean tick(ClientLevel level);
	}

	/** Shapes alive at once; the oldest goes when a new one would pass this. */
	private static final int MAX_SHAPES = 32;
	/** Tasks alive at once; the oldest goes when a new one would pass this. */
	private static final int MAX_TASKS = 96;
	private static final List<Shape> SHAPES = new ArrayList<>();
	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> ADDED = new ArrayList<>();
	private static final List<Runnable> ON_CLEAR = new ArrayList<>();
	private static ClientLevel lastLevel;

	public static void init() {
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(KineticRender::render);
		ClientTickEvents.END_CLIENT_TICK.register(KineticRender::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(KineticRender::clear));
	}

	/** Starts drawing {@code shape}. */
	public static void add(Shape shape) {
		if (SHAPES.size() >= MAX_SHAPES) {
			SHAPES.remove(0);
		}

		SHAPES.add(shape);
	}

	/** Runs {@code task} every tick from the next one until it says it is finished. */
	public static void run(Task task) {
		if (TASKS.size() + ADDED.size() >= MAX_TASKS) {
			if (!TASKS.isEmpty()) {
				TASKS.remove(0);
			} else {
				ADDED.remove(0);
			}
		}

		ADDED.add(task);
	}

	/** Runs {@code action} once, {@code ticks} ticks from now (1 = the next tick). */
	public static void later(int ticks, Consumer<ClientLevel> action) {
		int[] left = {Math.max(1, ticks)};
		run(level -> {
			if (--left[0] > 0) {
				return true;
			}

			action.accept(level);
			return false;
		});
	}

	/** Runs {@code reset} whenever every shape and task is dropped (disconnect, a new level), to forget what refers to them. */
	public static void onClear(Runnable reset) {
		ON_CLEAR.add(reset);
	}

	/** Drops every shape and task (disconnect, a new level). */
	public static void clear() {
		SHAPES.clear();
		TASKS.clear();
		ADDED.clear();

		for (Runnable reset : ON_CLEAR) {
			reset.run();
		}
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

		SHAPES.removeIf(shape -> !shape.tick());
		TASKS.addAll(ADDED);
		ADDED.clear();

		for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
			if (!it.next().tick(level)) {
				it.remove();
			}
		}
	}

	private static void render(LevelRenderContext context) {
		if (SHAPES.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.levelState().cameraRenderState.pos;
		List<Shape> shapes = List.copyOf(SHAPES);
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			Vector3f eye = GlowRibbon.eye(pose.pose());

			for (Shape shape : shapes) {
				shape.render(pose, buffer, cam, eye, partial);
			}
		});
	}

	private KineticRender() {
	}
}

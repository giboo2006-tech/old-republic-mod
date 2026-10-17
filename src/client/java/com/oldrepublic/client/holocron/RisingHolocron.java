package com.oldrepublic.client.holocron;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.holocron.HolocronItems;
import com.oldrepublic.holocron.HolocronKind;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

/**
 * The holocron itself during the attunement moment (spec 4.4), drawn on every nearby client from
 * the {@code holocron_attune} and {@code holocron_choice} events: it rises 0.5 blocks out of the
 * pedestal's cradle, hovers and turns slowly while the gatekeeper speaks, then either sinks back
 * into the cradle (nothing attuned) or leaves it: drifting into the player's hands (remember,
 * listen), going dark first (seal), or splitting apart (break, drawn by {@link HolocronFx}).
 *
 * <p>The item model is the real holocron item (body plus its emissive seam layer); a sealed one
 * switches to the item's dark variant. Every display ends by itself after {@link #MAX_TICKS}, so
 * a lost event can never leave a holocron hanging in the air.
 */
final class RisingHolocron {
	/** Custom model data string of the dark (sealed or unfound) holocron model. */
	static final String DARK = "dark";
	/** How far it rises out of the cradle, in blocks. */
	private static final double RISE = 0.5;
	/** The model's centre above the pedestal's cradle surface (15/16 of the block) when at rest. */
	private static final double REST_ABOVE_SURFACE = 0.2;
	private static final double CRADLE_SURFACE = 15.0 / 16.0;
	/** Below the surface the solid pedestal hides it: where a sinking holocron goes. */
	private static final double SUNK = -0.45;
	private static final int SINK_TICKS = 24;
	private static final int LEAVE_TICKS = 16;
	private static final int SEAL_DIM_TICKS = 12;
	/** Watchdog: no moment lasts longer than this (the hum's limit too; the server ends a moment at the same age). */
	static final int MAX_TICKS = 2400;

	private enum Phase {
		RISE,
		HOVER,
		SINK,
		/** Going dark (seal), then leaving. */
		SEAL,
		/** Drifting into the player. */
		LEAVE
	}

	private static final class Display {
		final BlockPos pedestal;
		final HolocronKind kind;
		final int riseTicks;
		final long start;
		Phase phase = Phase.RISE;
		long phaseStart;
		/** The height offset (above rest) when the current phase began. */
		double phaseFromLift;
		int playerId = -1;
		boolean dark;

		Display(BlockPos pedestal, HolocronKind kind, int riseTicks, long now) {
			this.pedestal = pedestal;
			this.kind = kind;
			this.riseTicks = Math.max(1, riseTicks);
			this.start = now;
			this.phaseStart = now;
		}

		Vec3 rest() {
			return new Vec3(pedestal.getX() + 0.5, pedestal.getY() + CRADLE_SURFACE + REST_ABOVE_SURFACE, pedestal.getZ() + 0.5);
		}

		/** Height above rest at {@code time} (ticks, with partial). */
		double lift(double time) {
			double t = time - phaseStart;
			return switch (phase) {
				case RISE -> RISE * easeOut(Mth.clamp(t / riseTicks, 0, 1));
				case HOVER, SEAL, LEAVE -> RISE + 0.03 * Math.sin((time - start) * 0.08);
				case SINK -> Mth.lerp(easeIn(Mth.clamp(t / SINK_TICKS, 0, 1)), phaseFromLift, SUNK);
			};
		}

		boolean finished(long now) {
			long t = now - phaseStart;
			return switch (phase) {
				case SINK -> t > SINK_TICKS;
				case LEAVE -> t > LEAVE_TICKS;
				case SEAL -> t > SEAL_DIM_TICKS + LEAVE_TICKS;
				default -> now - start > MAX_TICKS;
			};
		}

		void enter(Phase next, long now, double time) {
			phaseFromLift = lift(time);
			phase = next;
			phaseStart = now;
		}
	}

	private static final List<Display> DISPLAYS = new ArrayList<>();

	/** The holocron of {@code kind} starts rising at the pedestal whose top centre is {@code top}. */
	static void rise(Vec3 top, HolocronKind kind, int riseTicks) {
		BlockPos pedestal = pedestalAt(top);
		DISPLAYS.removeIf(d -> d.pedestal.equals(pedestal));
		DISPLAYS.add(new Display(pedestal, kind, riseTicks, HolocronClient.ticks()));
	}

	/** Nothing was attuned: the holocron sinks back into its cradle. */
	static void sink(Vec3 top) {
		Display display = near(top);

		if (display != null && display.phase != Phase.SINK) {
			display.enter(Phase.SINK, HolocronClient.ticks(), HolocronClient.time());
		}
	}

	/** The holocron was taken: it drifts into the player (entity id), going dark first when sealed. */
	static void taken(Vec3 top, int playerId, boolean sealed) {
		Display display = near(top);

		if (display != null) {
			display.playerId = playerId;
			display.enter(sealed ? Phase.SEAL : Phase.LEAVE, HolocronClient.ticks(), HolocronClient.time());
		}
	}

	/** The holocron split apart (break): it is gone at once; {@link HolocronFx} throws the pieces. */
	static void shattered(Vec3 top) {
		Display display = near(top);

		if (display != null) {
			DISPLAYS.remove(display);
		}
	}

	/** The centre of the risen holocron at a pedestal top, where seam light and the figure's beam start. */
	static Vec3 heldCentre(Vec3 top) {
		BlockPos pedestal = pedestalAt(top);
		return new Vec3(pedestal.getX() + 0.5, pedestal.getY() + CRADLE_SURFACE + REST_ABOVE_SURFACE + RISE, pedestal.getZ() + 0.5);
	}

	/** Visits every hovering holocron's current centre and kind (for seam light while it hovers). */
	static void forEachHovering(BiConsumer<Vec3, HolocronKind> visitor) {
		double time = HolocronClient.time();

		for (Display display : DISPLAYS) {
			if (display.phase == Phase.HOVER) {
				visitor.accept(display.rest().add(0, display.lift(time), 0), display.kind);
			}
		}
	}

	static void tick(ClientLevel level) {
		long now = HolocronClient.ticks();
		Iterator<Display> it = DISPLAYS.iterator();

		while (it.hasNext()) {
			Display display = it.next();

			if (display.phase == Phase.RISE && now - display.phaseStart >= display.riseTicks) {
				display.enter(Phase.HOVER, now, now);
			}

			if (display.phase == Phase.SEAL && !display.dark && now - display.phaseStart >= SEAL_DIM_TICKS / 2) {
				display.dark = true;
			}

			if (display.finished(now)) {
				if (display.phase == Phase.HOVER || display.phase == Phase.RISE) {
					// The watchdog: sink instead of vanishing.
					display.enter(Phase.SINK, now, now);
				} else {
					it.remove();
				}
			}
		}
	}

	static void clear() {
		DISPLAYS.clear();
	}

	static void render(LevelRenderContext context) {
		if (DISPLAYS.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;

		if (level == null) {
			return;
		}

		double time = HolocronClient.time();
		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();

		for (Display display : DISPLAYS) {
			Vec3 centre = display.rest().add(0, display.lift(time), 0);

			if (!Vfx.inRange(centre)) {
				continue;
			}

			float scale = 1f;
			long now = HolocronClient.ticks();

			if (display.phase == Phase.LEAVE || (display.phase == Phase.SEAL && now - display.phaseStart > SEAL_DIM_TICKS)) {
				long leaveStart = display.phase == Phase.SEAL ? display.phaseStart + SEAL_DIM_TICKS : display.phaseStart;
				float t = (float) Mth.clamp((time - leaveStart) / LEAVE_TICKS, 0, 1);
				Entity player = display.playerId >= 0 ? level.getEntity(display.playerId) : null;

				if (player != null) {
					Vec3 hands = player.position().add(0, player.getBbHeight() * 0.55, 0);
					centre = centre.lerp(hands, easeIn(t));
				}

				scale = 1f - easeIn(t) * 0.85f;
			}

			ItemStack stack = new ItemStack(HolocronItems.of(display.kind));

			if (display.dark) {
				stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(DARK), List.of()));
			}

			// A fresh state per submit: the collector draws later and keeps references to the layers.
			ItemStackRenderState state = new ItemStackRenderState();
			// API-CHECK: ItemModelResolver#updateForTopItem accepts a null ItemOwner (block-entity renderers pass none).
			mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.FIXED, level, null, display.pedestal.hashCode());
			int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(centre));
			float spin = (float) ((time - display.start) * 1.5) % 360f;

			poseStack.pushPose();
			poseStack.translate(centre.x - cam.x, centre.y - cam.y, centre.z - cam.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(spin));
			poseStack.scale(scale, scale, scale);
			state.submit(poseStack, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}

	private static BlockPos pedestalAt(Vec3 top) {
		// The server sends the pedestal's top centre (y + 1); the block is the one below it.
		return BlockPos.containing(top.x, top.y - 0.5, top.z);
	}

	private static @Nullable Display near(Vec3 top) {
		BlockPos pedestal = pedestalAt(top);

		for (Display display : DISPLAYS) {
			if (display.pedestal.equals(pedestal)) {
				return display;
			}
		}

		return null;
	}

	private static double easeOut(double t) {
		double u = 1 - t;
		return 1 - u * u * u;
	}

	private static float easeIn(float t) {
		return t * t;
	}

	private static double easeIn(double t) {
		return t * t;
	}

	private RisingHolocron() {
	}
}

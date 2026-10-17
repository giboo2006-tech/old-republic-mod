package com.oldrepublic.client.holocron;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.holocron.HolocronKind;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The projected gatekeeper (spec 4.4, 13.3): a narrow spill of light from the risen holocron's
 * opened seams widens into a thin, slightly translucent standing figure two blocks beyond the
 * pedestal, facing the player, tinted by kind (warm gold, rust crimson or brass), with faint
 * horizontal banding that is steady when flashing effects are off. Drifting motes appear only at
 * its edges while it assembles and while it folds back into the holocron.
 *
 * <p>Only the attuning player sees it: the {@code holocron_gatekeeper} event is sent to them alone.
 * The figure is the player model with the pale banded projection skin, drawn translucent and
 * full-bright and multiplied by the kind's tint. A watchdog folds it
 * when the player dies, walks more than 16 blocks away, changes level or after
 * {@link RisingHolocron#MAX_TICKS}. W2 may reuse {@link #show} and {@link #fold} for the
 * Pathfinder's witness.
 */
public final class GatekeeperFigure {
	/** The pale banded humanoid skin (64×64, player layout) the kind tint multiplies. */
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/gui/sprites/portrait/gatekeeper_figure.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int FORM_TICKS = 20;
	private static final int FOLD_TICKS = 16;
	private static final int GLIMPSE_TICKS = 50;
	/** Blocks beyond the pedestal, away from the player. */
	private static final double DISTANCE = 2.0;
	private static final float FIGURE_ALPHA = 0.55f;
	private static final float GLIMPSE_ALPHA = 0.28f;
	/** A thin figure: narrower than a player. */
	private static final float WIDTH_SCALE = 0.86f;
	private static final float HEIGHT = 1.8f;
	private static final double MAX_DISTANCE = 16.0;

	private static final class Figure {
		final BlockPos pedestal;
		/** Unit horizontal vector from the pedestal toward the player. */
		final Vec3 toward;
		final int index;
		final HolocronKind kind;
		final long start;
		final boolean glimpse;
		long foldStart = -1;

		Figure(BlockPos pedestal, Vec3 toward, int index, long start, boolean glimpse) {
			this.pedestal = pedestal;
			this.toward = toward;
			this.index = index;
			String id = HolocronCatalog.byGatekeeperIndex(index);
			HolocronKind known = id == null ? null : HolocronCatalog.kindOf(id);
			this.kind = known == null ? HolocronKind.WAYFARER : known;
			this.start = start;
			this.glimpse = glimpse;
		}

		/** Where the figure stands: the pedestal's floor level, two blocks beyond it. */
		Vec3 feet() {
			return new Vec3(pedestal.getX() + 0.5 - toward.x * DISTANCE, pedestal.getY(), pedestal.getZ() + 0.5 - toward.z * DISTANCE);
		}

		/** 0 to 1: how far the figure has assembled (and back down while it folds). */
		float presence(double time) {
			if (glimpse) {
				double t = (time - start) / GLIMPSE_TICKS;
				return (float) Mth.clamp(Math.sin(Math.min(1, t) * Math.PI) * 1.6, 0, 1);
			}

			float formed = (float) Mth.clamp((time - start) / FORM_TICKS, 0, 1);

			if (foldStart >= 0) {
				formed = Math.min(formed, 1f - (float) Mth.clamp((time - foldStart) / FOLD_TICKS, 0, 1));
			}

			return formed;
		}

		boolean assembling(long now) {
			return !glimpse && (now - start < FORM_TICKS || foldStart >= 0);
		}

		boolean done(long now) {
			return glimpse ? now - start > GLIMPSE_TICKS : foldStart >= 0 && now - foldStart > FOLD_TICKS;
		}
	}

	private static @Nullable Figure figure;
	private static @Nullable Figure echo;
	private static @Nullable HumanoidModel<HumanoidRenderState> model;
	private static @Nullable EntityModelSet bakedFrom;
	private static final RandomSource RANDOM = RandomSource.create();

	/**
	 * Projects gatekeeper {@code gatekeeperIndex} from the holocron risen at {@code pedestal}, two
	 * blocks beyond it and facing along {@code towardPlayer} (the unit vector pedestal to player).
	 */
	public static void show(BlockPos pedestal, Vec3 towardPlayer, int gatekeeperIndex) {
		Vec3 flat = new Vec3(towardPlayer.x, 0, towardPlayer.z);
		Vec3 toward = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		figure = new Figure(pedestal.immutable(), toward, gatekeeperIndex, HolocronClient.ticks(), false);
	}

	/** Folds the figure of {@code gatekeeperIndex} back into its holocron (any figure for -1). */
	public static void fold(int gatekeeperIndex) {
		Figure current = figure;

		if (current != null && current.foldStart < 0 && (gatekeeperIndex < 0 || current.index == gatekeeperIndex)) {
			current.foldStart = HolocronClient.ticks();
		}
	}

	/** A faint, brief silhouette over a pedestal (an echo speaking), facing {@code towardPlayer}. */
	static void glimpse(BlockPos pedestal, Vec3 towardPlayer, int gatekeeperIndex) {
		Vec3 flat = new Vec3(towardPlayer.x, 0, towardPlayer.z);
		Vec3 toward = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		echo = new Figure(pedestal.immutable(), toward, gatekeeperIndex, HolocronClient.ticks(), true);
	}

	/** Whether a figure is projected now (the screenshot step and the hum's watchdog read it). */
	public static boolean active() {
		return figure != null;
	}

	static void clear() {
		figure = null;
		echo = null;
	}

	static void tick(ClientLevel level, LocalPlayer player) {
		long now = HolocronClient.ticks();
		Figure current = figure;

		if (current != null) {
			Vec3 feet = current.feet();
			boolean away = player.isDeadOrDying() || player.position().distanceToSqr(feet) > MAX_DISTANCE * MAX_DISTANCE
					|| now - current.start > RisingHolocron.MAX_TICKS;

			if (away && current.foldStart < 0) {
				current.foldStart = now;
				HolocronHumSound.fadeOut();
			}

			if (current.done(now)) {
				figure = null;
			} else {
				particles(level, current, now);
			}
		}

		if (echo != null && echo.done(now)) {
			echo = null;
		}
	}

	/** Edge motes while it assembles or folds; slow bands drifting up through it while it stands. */
	private static void particles(ClientLevel level, Figure f, long now) {
		Vec3 feet = f.feet();

		if (!Vfx.inRange(feet)) {
			return;
		}

		int tint = tint(f.kind);
		float presence = f.presence(now);
		Vec3 side = new Vec3(-f.toward.z, 0, f.toward.x);
		float halfWidth = 0.32f * WIDTH_SCALE * Math.max(0.15f, presence);

		if (f.assembling(now)) {
			for (int i = 0; i < Vfx.count(2); i++) {
				double h = RANDOM.nextDouble() * HEIGHT;
				double edge = (RANDOM.nextBoolean() ? 1 : -1) * halfWidth * (h > 1.4 ? 0.7 : 1.0);
				Vec3 at = feet.add(side.scale(edge)).add(0, h, 0);
				Vec3 drift = side.scale(edge * 0.04).add(0, 0.008 + RANDOM.nextDouble() * 0.01, 0);
				HolocronMoteParticle mote = HolocronFx.mote(at, drift);

				if (mote != null) {
					mote.tint(tint).size(0.045f, 0.015f).alpha(0.7f).life(14 + RANDOM.nextInt(8));
				}
			}
		} else if (now % 5 == 0) {
			Vec3 at = feet.add(f.toward.scale(0.02)).add(0, 0.1 + RANDOM.nextDouble() * (HEIGHT - 0.4), 0);
			ProjectionBandParticle band = HolocronFx.band(at, new Vec3(0, 0.012, 0));

			if (band != null) {
				band.tint(tint).width(0.34f * WIDTH_SCALE).alpha(0.3f).life(30);
			}
		}
	}

	static void render(LevelRenderContext context) {
		if (figure == null && echo == null) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		HumanoidModel<HumanoidRenderState> humanoid = model(mc);
		double time = HolocronClient.time();
		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();

		for (Figure f : new Figure[] {figure, echo}) {
			if (f == null || !Vfx.inRange(f.feet())) {
				continue;
			}

			float presence = f.presence(time);

			if (presence <= 0.01f) {
				continue;
			}

			poseStack.pushPose();
			poseStack.translate(-cam.x, -cam.y, -cam.z);

			if (!f.glimpse) {
				beam(context, poseStack, f, presence);
			}

			figureModel(context, poseStack, humanoid, f, presence, time);
			poseStack.popPose();
		}
	}

	/** The spill of light from the holocron's seams to the figure: a thin pyramid that widens as the figure forms. */
	private static void beam(LevelRenderContext context, PoseStack poseStack, Figure f, float presence) {
		Vec3 source = RisingHolocron.heldCentre(new Vec3(f.pedestal.getX() + 0.5, f.pedestal.getY() + 1.0, f.pedestal.getZ() + 0.5));
		Vec3 feet = f.feet();
		Vec3 side = new Vec3(-f.toward.z, 0, f.toward.x).scale(0.36 * WIDTH_SCALE * Math.max(0.08f, presence));
		Vec3 low = feet.add(0, 0.05, 0);
		Vec3 high = feet.add(0, HEIGHT * Math.max(0.3f, presence), 0);
		Vec3[] corners = {low.subtract(side), low.add(side), high.add(side), high.subtract(side)};
		int rgb = tint(f.kind);
		int near = argb(0.22f * presence, rgb);
		int far = argb(0.035f * presence, rgb);

		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (int i = 0; i < 4; i++) {
				Vec3 a = corners[i];
				Vec3 b = corners[(i + 1) % 4];
				// Each face of the light pyramid, in both windings so it shows from either side.
				tri(pose, buffer, source, a, b, near, far);
				tri(pose, buffer, source, b, a, near, far);
			}
		});
	}

	private static void tri(PoseStack.Pose pose, VertexConsumer buffer, Vec3 apex, Vec3 a, Vec3 b, int apexColor, int baseColor) {
		// The filled-box type draws quads: the apex is doubled to make a triangle.
		vertex(pose, buffer, apex, apexColor);
		vertex(pose, buffer, a, baseColor);
		vertex(pose, buffer, b, baseColor);
		vertex(pose, buffer, apex, apexColor);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int color) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
	}

	/** The translucent humanoid, narrowed while it assembles, facing the player. */
	private static void figureModel(LevelRenderContext context, PoseStack poseStack, HumanoidModel<HumanoidRenderState> humanoid, Figure f,
			float presence, double time) {
		HumanoidRenderState state = new HumanoidRenderState();
		state.ageInTicks = (float) (time - f.start);
		humanoid.setupAnim(state);

		float peak = f.glimpse ? GLIMPSE_ALPHA : FIGURE_ALPHA;
		// A slow breath of the projection with flashing on; steady with it off.
		float breath = Vfx.flashing() ? 0.92f + 0.08f * Mth.sin((float) time * 0.12f) : 1f;
		float alpha = peak * presence * breath;
		int color = argb(alpha, tint(f.kind));
		float yaw = (float) Math.toDegrees(Math.atan2(-f.toward.x, f.toward.z));
		Vec3 feet = f.feet();
		float width = WIDTH_SCALE * Math.max(0.06f, presence);

		poseStack.pushPose();
		poseStack.translate(feet.x, feet.y, feet.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));
		// The entity model space: flipped on x and y, origin 1.5 blocks up (LivingEntityRenderer).
		poseStack.scale(-width, -1f, width);
		poseStack.translate(0f, -1.501f, 0f);
		// API-CHECK: the full OrderedSubmitNodeCollector#submitModel takes (model, state, pose, type, light, overlay, tint colour, sprite, outline colour, crumbling).
		context.submitNodeCollector().submitModel(humanoid, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, color, null, 0, null);
		poseStack.popPose();
	}

	/** The player model, baked again whenever the resource reload replaces the model set. */
	private static HumanoidModel<HumanoidRenderState> model(Minecraft mc) {
		EntityModelSet models = mc.getEntityModels();

		if (model == null || bakedFrom != models) {
			model = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER));
			bakedFrom = models;
		}

		return model;
	}

	/** The projection tint of a kind: warm gold, rust crimson or brass (0xRRGGBB). */
	static int tint(HolocronKind kind) {
		return switch (kind) {
			case JEDI -> 0xFFE3A8;
			case SITH -> 0xE8826A;
			case WAYFARER -> 0xE6C88C;
		};
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24 | (rgb & 0xFFFFFF);
	}

	private GatekeeperFigure() {
	}
}

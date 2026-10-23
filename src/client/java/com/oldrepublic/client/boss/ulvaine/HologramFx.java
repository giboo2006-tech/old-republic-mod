package com.oldrepublic.client.boss.ulvaine;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ulvaine.UlvaineEffects;
import com.oldrepublic.client.enemy.hymn.FoeFx;
import com.oldrepublic.client.enemy.hymn.FoeGlow;
import com.oldrepublic.client.enemy.hymn.FoeLoops;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code hologram} effect (spec MILESTONE_5.md 6.4, 15.3): Ulvaine's last message, played on
 * Dask's desk projector. W4's dialogue action sends it (around; pos = the desk, entity = the speaker;
 * variant 0 start with intensity = its ticks, 1 end). Her {@link UlvaineModel} stands small over the
 * desk, between the speaker and the player and facing the player, drawn translucent blue and
 * full-bright with the pale banded hologram skin
 * ({@code textures/entity/ulvaine/darth_ulvaine_hologram.png}); thin scan bands climb slowly
 * through her, and her edges flicker (a faint outer shell whose light stutters), steady with
 * flashing effects off. No particles (15.1).
 *
 * <p>The {@code story.hologram} loop plays while she shows. She folds away when the message ends
 * (its ticks, or the end event), when the player moves 16 blocks away, dies, changes level or
 * disconnects.
 */
public final class HologramFx {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/ulvaine/darth_ulvaine_hologram.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	/** The projection's blue. */
	private static final int HOLO_RGB = 0x7FC4FF;
	/** The scan bands' paler blue. */
	private static final int BAND_RGB = 0xC8ECFF;
	/** Her size over the desk: about a block tall. */
	private static final float SCALE = 0.52f;
	private static final float FIGURE_ALPHA = 0.58f;
	private static final int FORM_TICKS = 12;
	private static final int FOLD_TICKS = 12;
	/** An end event that never comes: she folds after this long anyway. */
	private static final int MAX_TICKS = 1200;
	private static final double MAX_DISTANCE = 16.0;
	/** Blocks from the desk toward the player. */
	private static final double FORWARD = 0.7;
	private static final String LOOP_KEY = "story.hologram";

	private static final class Projection {
		/** Where her feet stand. */
		final Vec3 feet;
		/** Unit horizontal vector from her toward the player. */
		final Vec3 toward;
		final long start;
		final int ticks;
		long foldStart = -1;

		Projection(Vec3 feet, Vec3 toward, long start, int ticks) {
			this.feet = feet;
			this.toward = toward;
			this.start = start;
			this.ticks = ticks;
		}

		float presence(double time) {
			float formed = (float) Mth.clamp((time - start) / FORM_TICKS, 0, 1);

			if (foldStart >= 0) {
				formed = Math.min(formed, 1f - (float) Mth.clamp((time - foldStart) / FOLD_TICKS, 0, 1));
			}

			return formed;
		}

		boolean done(long now) {
			return foldStart >= 0 && now - foldStart > FOLD_TICKS;
		}
	}

	private static @Nullable Projection projection;
	private static @Nullable UlvaineModel model;
	private static @Nullable EntityModelSet bakedFrom;
	private static @Nullable ClientLevel shownIn;
	private static long ticks;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientEffects.register(UlvaineEffects.HOLOGRAM, HologramFx::onEvent);
		ClientTickEvents.END_CLIENT_TICK.register(HologramFx::tick);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(HologramFx::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Whether her figure shows now (the client tests and the loop read it). */
	public static boolean active() {
		Projection p = projection;
		return p != null && p.foldStart < 0;
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity speaker) {
		if (p.variant() == UlvaineEffects.HOLOGRAM_END) {
			fold();
			return;
		}

		LocalPlayer player = mc.player;

		if (p.variant() != UlvaineEffects.HOLOGRAM_START || player == null || player.position().distanceToSqr(p.pos()) > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}

		Vec3 desk = p.pos();
		Vec3 toward;
		Vec3 feet;

		if (speaker != null && speaker != player) {
			// Over the desk, a little in front of the speaker, facing the player.
			toward = FoeFx.flat(player.position().subtract(speaker.position()), new Vec3(0, 0, 1));
			feet = desk.add(toward.scale(FORWARD)).add(0, -0.15, 0);
		} else {
			// The player's own projector (no speaker): in front of them, at the same height.
			Vec3 look = FoeFx.flat(player.getLookAngle(), new Vec3(0, 0, 1));
			feet = player.position().add(look.scale(1.6)).add(0, desk.y - player.getY() - 0.15, 0);
			toward = look.scale(-1);
		}

		int length = p.intensity() > 0 ? Math.min(MAX_TICKS, Math.round(p.intensity())) : MAX_TICKS;
		projection = new Projection(feet, toward, ticks, length);
		shownIn = level;
		FoeLoops.start(LOOP_KEY, ModSounds.STORY_HOLOGRAM, SoundSource.NEUTRAL, null, feet.add(0, 0.5, 0), 0.7f, 1f, MAX_DISTANCE, HologramFx::active);
	}

	/** She folds away; the loop fades with her ({@link #active()} turns false). */
	private static void fold() {
		Projection p = projection;

		if (p != null && p.foldStart < 0) {
			p.foldStart = ticks;
		}
	}

	private static void clear() {
		projection = null;
		shownIn = null;
		FoeLoops.stop(LOOP_KEY);
	}

	private static void tick(Minecraft mc) {
		ticks++;
		Projection p = projection;

		if (p == null) {
			return;
		}

		LocalPlayer player = mc.player;

		if (mc.level == null || player == null || mc.level != shownIn) {
			clear();
			return;
		}

		boolean over = p.ticks > 0 && ticks - p.start > p.ticks;
		boolean away = !player.isAlive() || player.position().distanceToSqr(p.feet) > MAX_DISTANCE * MAX_DISTANCE;

		if ((over || away) && p.foldStart < 0) {
			fold();
		}

		if (p.done(ticks)) {
			projection = null;
		}
	}

	private static void render(LevelRenderContext context) {
		Projection p = projection;

		if (p == null || !Vfx.inRange(p.feet)) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		double time = ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		float presence = p.presence(time);

		if (presence <= 0.01f) {
			return;
		}

		UlvaineModel ulvaine = model(mc);
		UlvaineRenderer.State state = new UlvaineRenderer.State();
		state.ageInTicks = (float) (time - p.start);
		ulvaine.setupAnim(state);

		boolean flicker = Vfx.flashing();
		// A slow breath of the light; the edges stutter (both steady with flashing off).
		float breath = flicker ? 0.9f + 0.1f * Mth.sin((float) time * 0.15f) : 1f;
		float edge = flicker ? (FoeFx.RANDOM.nextFloat() < 0.18f ? 0.05f : 0.14f + FoeFx.RANDOM.nextFloat() * 0.12f) : 0.16f;
		// While she gathers or folds, the figure narrows to a sliver of light.
		float width = SCALE * Math.max(0.08f, presence);
		float yaw = (float) Math.toDegrees(Math.atan2(-p.toward.x, p.toward.z));
		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();

		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		poseStack.pushPose();
		poseStack.translate(p.feet.x, p.feet.y, p.feet.z);
		poseStack.mulPose(Axis.YP.rotationDegrees(180f - yaw));

		// The figure: the model space is flipped on x and y, with its origin 1.5 blocks up (LivingEntityRenderer).
		poseStack.pushPose();
		poseStack.scale(-width, -SCALE, width);
		poseStack.translate(0f, -1.501f, 0f);
		// (model, state, pose, render type, light, overlay, tint, sprite, outline colour, crumbling)
		context.submitNodeCollector().submitModel(ulvaine, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, FoeGlow.argb(FIGURE_ALPHA * presence * breath, HOLO_RGB), null, 0, null);
		poseStack.popPose();

		// The flickering edges: a faint shell a little larger than her, about her middle.
		poseStack.pushPose();
		float grow = 1.07f;
		poseStack.translate(0f, SCALE * 0.95f * (1f - grow), 0f);
		poseStack.scale(-width * grow, -SCALE * grow, width * grow);
		poseStack.translate(0f, -1.501f, 0f);
		context.submitNodeCollector().submitModel(ulvaine, state, poseStack, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, FoeGlow.argb(edge * presence, HOLO_RGB), null, 0, null);
		poseStack.popPose();

		// The scan bands: thin glowing slices climbing slowly through her, and the projector's faint disc.
		float height = 1.95f * SCALE;
		float half = 0.36f * width;
		float depth = 0.2f * width;
		float bandAlpha = 0.32f * presence;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			for (int i = 0; i < 3; i++) {
				float phase = (float) (((time - p.start) / 70.0 + i / 3.0) % 1.0);
				float y = phase * height;
				float thick = 0.012f;
				FoeGlow.box(pose, buffer, -half, y, -depth, half, y + thick, depth, FoeGlow.argb(bandAlpha * (1f - phase * 0.5f), BAND_RGB));
			}

			float disc = 0.3f * width / SCALE;
			FoeGlow.quad(pose, buffer, FoeGlow.argb(0.22f * presence, HOLO_RGB),
					-disc, 0.01f, -disc, disc, 0.01f, -disc, disc, 0.01f, disc, -disc, 0.01f, disc);
		});
		poseStack.popPose();
		poseStack.popPose();
	}

	/** Her model, baked again whenever the resource reload replaces the model set. */
	private static UlvaineModel model(Minecraft mc) {
		EntityModelSet models = mc.getEntityModels();

		if (model == null || bakedFrom != models) {
			model = new UlvaineModel(models.bakeLayer(UlvaineModel.LAYER));
			bakedFrom = models;
		}

		return model;
	}

	private HologramFx() {
	}
}

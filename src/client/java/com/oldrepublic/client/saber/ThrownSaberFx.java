package com.oldrepublic.client.saber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.saber.throwing.ThrownSaber;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The flying saber of Saber Throw. The entity itself has a no-op renderer; its blade is drawn here
 * as glowing quads in the blade colour (white core, coloured edge, a short grey hilt), spinning at
 * {@value #ROTATIONS_PER_SECOND} turns a second in the horizontal plane with a fan of fading ghost
 * blades behind it, so the spin reads as a circular trail. Each flying saber has a looping
 * {@code saber.throw_loop} that follows it, its pitch shifted by the saber's speed toward or away
 * from the listener (a simple Doppler).
 */
final class ThrownSaberFx {
	private static final float ROTATIONS_PER_SECOND = 20f;
	private static final float BLADE_LENGTH = 1.0f;
	private static final float HILT_LENGTH = 0.25f;
	private static final float BLADE_HALF_WIDTH = 0.045f;
	/** Ghost blades behind the current one, each {@value #GHOST_STEP} degrees further back. */
	private static final int GHOSTS = 8;
	private static final float GHOST_STEP = 22f;
	private static final int HILT_COLOR = 0xFF8C8F94;

	private static final Map<Integer, LoopSound> LOOPS = new HashMap<>();

	static void init() {
		EntityRendererRegistry.register(SaberThrow.THROWN_SABER, NoopRenderer::new);
	}

	/** Starts a loop for each new flying saber and drops finished ones. */
	static void tick(Minecraft mc) {
		LOOPS.values().removeIf(LoopSound::isStopped);

		if (mc.level == null) {
			clear();
			return;
		}

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof ThrownSaber saber && !LOOPS.containsKey(saber.getId())) {
				LoopSound loop = new LoopSound(saber);
				LOOPS.put(saber.getId(), loop);
				mc.getSoundManager().play(loop);
			}
		}
	}

	static void clear() {
		LOOPS.values().forEach(LoopSound::end);
		LOOPS.clear();
	}

	/** The blade colour of the saber {@code owner} has in flight, or white. */
	static int colorOf(LivingEntity owner) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level != null) {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof ThrownSaber saber && saber.ownerEntityId() == owner.getId()) {
					return SaberComponents.config(saber.saber()).color().rgb();
				}
			}
		}

		return 0xFFFFFF;
	}

	static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null || LOOPS.isEmpty()) {
			return;
		}

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		List<ThrownSaber> visible = new ArrayList<>();

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof ThrownSaber saber && Vfx.inRange(saber.position())) {
				visible.add(saber);
			}
		}

		if (visible.isEmpty()) {
			return;
		}

		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (ThrownSaber saber : visible) {
				drawSaber(pose, buffer, saber, partial);
			}
		});
		poseStack.popPose();
	}

	private static void drawSaber(PoseStack.Pose pose, VertexConsumer buffer, ThrownSaber saber, float partial) {
		Vec3 centre = new Vec3(Mth.lerp(partial, saber.xo, saber.getX()), Mth.lerp(partial, saber.yo, saber.getY()) + saber.getBbHeight() * 0.5,
				Mth.lerp(partial, saber.zo, saber.getZ()));
		int rgb = SaberComponents.config(saber.saber()).color().rgb();
		// Degrees per tick at the spin rate.
		float angle = (saber.tickCount + partial) * ROTATIONS_PER_SECOND * 360f / 20f;

		for (int i = GHOSTS; i >= 1; i--) {
			float alpha = 0.35f * (1f - (float) i / (GHOSTS + 1));
			blade(pose, buffer, centre, angle - i * GHOST_STEP, argb(alpha, rgb), argb(alpha * 0.6f, rgb), BLADE_HALF_WIDTH * 1.6f);
		}

		blade(pose, buffer, centre, angle, argb(0.95f, rgb), argb(0.95f, 0xFFFFFF), BLADE_HALF_WIDTH);
		hilt(pose, buffer, centre, angle);
	}

	/** One blade from the centre outward at {@code degrees}: a flat quad and a crossed one, so it shows edge-on too. */
	private static void blade(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, float degrees, int edge, int core, float halfWidth) {
		Vec3 dir = direction(degrees);
		Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(halfWidth);
		Vec3 up = new Vec3(0, halfWidth, 0);
		Vec3 base = centre.add(dir.scale(HILT_LENGTH * 0.5));
		Vec3 tip = centre.add(dir.scale(HILT_LENGTH * 0.5 + BLADE_LENGTH));
		quad(pose, buffer, base.subtract(side), base.add(side), tip.add(side), tip.subtract(side), core, edge);
		quad(pose, buffer, base.subtract(up), base.add(up), tip.add(up), tip.subtract(up), core, edge);
	}

	private static void hilt(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, float degrees) {
		Vec3 dir = direction(degrees).scale(HILT_LENGTH * 0.5);
		Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(0.035);
		Vec3 up = new Vec3(0, 0.035, 0);
		Vec3 a = centre.subtract(dir);
		Vec3 b = centre.add(dir);
		quad(pose, buffer, a.subtract(side), a.add(side), b.add(side), b.subtract(side), HILT_COLOR, HILT_COLOR);
		quad(pose, buffer, a.subtract(up), a.add(up), b.add(up), b.subtract(up), HILT_COLOR, HILT_COLOR);
	}

	private static Vec3 direction(float degrees) {
		float r = degrees * Mth.DEG_TO_RAD;
		return new Vec3(Mth.cos(r), 0, Mth.sin(r));
	}

	/** A quad in both windings; {@code base} colour at the first pair, {@code tip} colour at the second. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int base, int tip) {
		vertex(pose, buffer, a, base);
		vertex(pose, buffer, b, base);
		vertex(pose, buffer, c, tip);
		vertex(pose, buffer, d, tip);

		vertex(pose, buffer, d, tip);
		vertex(pose, buffer, c, tip);
		vertex(pose, buffer, b, base);
		vertex(pose, buffer, a, base);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int color) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24 | (rgb & 0xFFFFFF);
	}

	/** The spinning hum of one flying saber, following it with a Doppler pitch. */
	private static final class LoopSound extends AbstractTickableSoundInstance {
		private static final float VOLUME = 0.8f;
		private final ThrownSaber saber;

		LoopSound(ThrownSaber saber) {
			super(ModSounds.SABER_THROW_LOOP, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.saber = saber;
			this.looping = true;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.volume = VOLUME * ClientConfig.get().modSoundVolume;
			follow();
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();

			if (saber.isRemoved() || saber.level() != mc.level) {
				stop();
				return;
			}

			Vec3 velocity = saber.position().subtract(saber.xo, saber.yo, saber.zo);
			Entity listener = mc.getCameraEntity();

			if (listener != null) {
				Vec3 toListener = listener.getEyePosition().subtract(saber.position());
				double approach = toListener.lengthSqr() < 1.0E-4 ? 0 : velocity.dot(toListener.normalize());
				this.pitch = (float) Mth.clamp(1.0 + approach * 0.25, 0.8, 1.25);
			}

			this.volume = VOLUME * ClientConfig.get().modSoundVolume;
			follow();
		}

		void end() {
			stop();
		}

		private void follow() {
			this.x = saber.getX();
			this.y = saber.getY();
			this.z = saber.getZ();
		}
	}

	private ThrownSaberFx() {
	}
}

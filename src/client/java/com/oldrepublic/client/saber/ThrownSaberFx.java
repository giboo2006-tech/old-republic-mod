package com.oldrepublic.client.saber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.GlowRenderTypes;
import com.oldrepublic.client.fx.GlowRibbon;
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
 * The flying saber of Saber Throw. The entity itself has a no-op renderer; it is drawn here like the
 * held blade ({@link SaberBladeRenderer}: a white core with a coloured edge and a soft glow of added
 * light, {@link GlowRibbon}) on a short grey hilt, spinning at {@value #ROTATIONS_PER_SECOND} turns a
 * second in the horizontal plane. Behind the blade its sweep fades round as a disc of light, brightest
 * at the leading edge and toward the tip, so the spin reads as a whirling disc. Each flying saber has a
 * looping {@code saber.throw_loop} that follows it, its pitch shifted by the saber's speed toward or
 * away from the listener (a simple Doppler).
 */
final class ThrownSaberFx {
	private static final float ROTATIONS_PER_SECOND = 20f;
	private static final float BLADE_LENGTH = 1.05f;
	private static final float HILT_LENGTH = 0.3f;
	/** The held blade's widths at its third-person size. */
	private static final float BODY_RADIUS = SaberBladeRenderer.BODY_RADIUS * SaberBladeRenderer.PX * 0.68f;
	private static final float GLOW_RADIUS = SaberBladeRenderer.GLOW_RADIUS * SaberBladeRenderer.PX * 0.68f;
	private static final float HILT_RADIUS = 0.032f;
	/** The sweep behind the blade: how far round it reaches (degrees), in steps of {@value #SWEEP_STEP}, and its strength. */
	private static final float SWEEP = 320f;
	private static final float SWEEP_STEP = 10f;
	private static final float SWEEP_ALPHA = 0.55f;
	private static final GlowRibbon.Profile HILT = new GlowRibbon.Profile(new float[] {0f, 1f}, new int[] {0xFF9A9DA3, 0xFF5E6166});

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
		// Drawn relative to the camera, so the floats stay small far from the world's origin.
		Vec3 cam = context.levelState().cameraRenderState.pos;
		List<Spin> spins = new ArrayList<>();

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof ThrownSaber saber && Vfx.inRange(saber.position())) {
				spins.add(spin(saber, partial, cam));
			}
		}

		if (spins.isEmpty()) {
			return;
		}

		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		Vector3f eye = GlowRibbon.eye(poseStack.last().pose());
		context.submitNodeCollector().submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (pose, buffer) -> {
			for (Spin spin : spins) {
				sweep(pose, buffer, spin);
				GlowRibbon.capsule(pose, buffer, eye, spin.base(), spin.tip(), GLOW_RADIUS, SaberBladeRenderer.glow(spin.rgb()), 1f);
			}
		});
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			for (Spin spin : spins) {
				GlowRibbon.capsule(pose, buffer, eye, spin.base(), spin.tip(), BODY_RADIUS, SaberBladeRenderer.body(spin.rgb()), 1f);
				GlowRibbon.capsule(pose, buffer, eye, spin.pommel(), spin.base(), HILT_RADIUS, HILT, 1f);
			}
		});
		poseStack.popPose();
	}

	/**
	 * One flying saber this frame, relative to the camera: the hilt's centre, the blade's angle in the spin plane (degrees),
	 * its base and tip, the pommel end of the hilt, its length and colour.
	 */
	private record Spin(Vector3f centre, float angle, Vector3f base, Vector3f tip, Vector3f pommel, float length, int rgb) {
	}

	private static Spin spin(ThrownSaber saber, float partial, Vec3 cam) {
		Vector3f centre = new Vector3f((float) (Mth.lerp(partial, saber.xo, saber.getX()) - cam.x),
				(float) (Mth.lerp(partial, saber.yo, saber.getY()) + saber.getBbHeight() * 0.5 - cam.y), (float) (Mth.lerp(partial, saber.zo, saber.getZ()) - cam.z));
		int rgb = SaberComponents.config(saber.saber()).color().rgb();
		// A short or long blade (MILESTONE_5.md 10.3) flies at its own length.
		float length = BLADE_LENGTH * SaberModelProperties.bladeScale(saber.saber());
		// Degrees per tick at the spin rate.
		float angle = (saber.tickCount + partial) * ROTATIONS_PER_SECOND * 360f / 20f;
		Vector3f dir = direction(angle);
		Vector3f base = new Vector3f(dir).mul(HILT_LENGTH * 0.5f).add(centre);
		Vector3f tip = new Vector3f(dir).mul(HILT_LENGTH * 0.5f + length).add(centre);
		Vector3f pommel = new Vector3f(dir).mul(-HILT_LENGTH * 0.5f).add(centre);
		return new Spin(centre, angle, base, tip, pommel, length, rgb);
	}

	/**
	 * The disc of light the spinning blade sweeps: a fan in the spin plane from the blade back round {@value #SWEEP}
	 * degrees, fading from the leading edge and from the tip in toward the hilt.
	 */
	private static void sweep(PoseStack.Pose pose, VertexConsumer buffer, Spin spin) {
		float inner = HILT_LENGTH * 0.5f;
		float outer = inner + spin.length();
		int steps = Math.round(SWEEP / SWEEP_STEP);

		for (int i = 0; i < steps; i++) {
			float a0 = SWEEP_ALPHA * (float) Math.pow(1f - (float) i / steps, 1.5);
			float a1 = SWEEP_ALPHA * (float) Math.pow(1f - (float) (i + 1) / steps, 1.5);
			Vector3f d0 = direction(spin.angle() - i * SWEEP_STEP);
			Vector3f d1 = direction(spin.angle() - (i + 1) * SWEEP_STEP);
			// Faint at the hilt, full at the tip.
			vertex(pose, buffer, spin.centre(), d0, inner, spin.rgb(), a0 * 0.2f);
			vertex(pose, buffer, spin.centre(), d0, outer, spin.rgb(), a0);
			vertex(pose, buffer, spin.centre(), d1, outer, spin.rgb(), a1);
			vertex(pose, buffer, spin.centre(), d1, inner, spin.rgb(), a1 * 0.2f);
		}
	}

	private static Vector3f direction(float degrees) {
		float r = degrees * Mth.DEG_TO_RAD;
		return new Vector3f(Mth.cos(r), 0f, Mth.sin(r));
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f centre, Vector3f dir, float distance, int rgb, float alpha) {
		buffer.addVertex(pose, centre.x + dir.x * distance, centre.y + dir.y * distance, centre.z + dir.z * distance)
				.setColor(GlowRibbon.fade(0xFF000000 | rgb, alpha));
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

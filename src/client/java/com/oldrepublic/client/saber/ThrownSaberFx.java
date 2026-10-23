package com.oldrepublic.client.saber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.GlowRenderTypes;
import com.oldrepublic.client.fx.GlowRibbon;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberStaffs;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.saber.throwing.ThrownSaber;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The flying saber of Saber Throw. The entity itself has a no-op renderer; it is drawn here as the real saber
 * (MILESTONE_7.md 10.5): its item model at the size it is held in third person, the hilt of its own parts and materials
 * with its blade or blades, quillons and curve drawn as in the hand ({@link SaberBladeRenderer}), spinning about the
 * grip's middle at {@value #ROTATIONS_PER_SECOND} turns a second in the horizontal plane. Behind each blade its sweep
 * fades round as a disc of light, brightest at the leading edge and toward the tip, so the spin reads as a whirling disc;
 * the blades report where they are drawn for it and leave no ordinary motion trail ({@link BladeTrails#capture}). Each
 * flying saber has a looping {@code saber.throw_loop} that follows it, its pitch shifted by the saber's speed toward or
 * away from the listener (a simple Doppler).
 */
final class ThrownSaberFx {
	private static final float ROTATIONS_PER_SECOND = 20f;
	/** The saber's size in flight against its item model: as held in third person ({@code lightsaber/base.json}, {@code staff_base.json}). */
	private static final float SCALE = 0.68f;
	private static final float STAFF_SCALE = 0.62f;
	/** The sweep behind the blade: how far round it reaches (degrees), in steps of {@value #SWEEP_STEP}, and its strength. */
	private static final float SWEEP = 320f;
	private static final float SWEEP_STEP = 10f;
	private static final float SWEEP_ALPHA = 0.55f;

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
		ClientLevel level = mc.level;

		if (level == null || LOOPS.isEmpty()) {
			return;
		}

		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// Drawn relative to the camera, so the floats stay small far from the world's origin.
		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		// The blades report where the full pose puts them; the sweeps are drawn in the level's pose.
		Matrix4f toLevel = new Matrix4f(poseStack.last().pose()).invert();
		List<Sweep> sweeps = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof ThrownSaber saber && !saber.saber().isEmpty() && Vfx.inRange(saber.position())) {
				submit(mc, level, saber, partial, cam, poseStack, collector, toLevel, sweeps);
			}
		}

		if (!sweeps.isEmpty()) {
			collector.submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (pose, buffer) -> {
				for (Sweep sweep : sweeps) {
					sweep(pose, buffer, sweep);
				}
			});
		}
	}

	/** One blade's sweep this frame, relative to the camera: the spin's centre, where the blade is drawn, its colour. */
	private record Sweep(Vector3f centre, Vector3f base, Vector3f tip, int rgb) {
	}

	/**
	 * Submits one flying saber's item model: turned so the hilt points along the spin and the model's x-y plane (where a
	 * curved blade leans) lies in the spin plane, the grip's middle on the saber's centre. Each blade it draws adds its
	 * sweep.
	 */
	private static void submit(Minecraft mc, ClientLevel level, ThrownSaber saber, float partial, Vec3 cam, PoseStack poseStack,
			SubmitNodeCollector collector, Matrix4f toLevel, List<Sweep> sweeps) {
		ItemStack stack = saber.saber();
		Vec3 at = new Vec3(Mth.lerp(partial, saber.xo, saber.getX()), Mth.lerp(partial, saber.yo, saber.getY()) + saber.getBbHeight() * 0.5,
				Mth.lerp(partial, saber.zo, saber.getZ()));
		Vector3f centre = new Vector3f((float) (at.x - cam.x), (float) (at.y - cam.y), (float) (at.z - cam.z));
		// Degrees per tick at the spin rate.
		float angle = (saber.tickCount + partial) * ROTATIONS_PER_SECOND * 360f / 20f;
		boolean staff = SaberStaffs.isStaff(stack);
		float scale = staff ? STAFF_SCALE : SCALE;
		float grip = staff ? SaberFirstPerson.STAFF_GRIP_Y : SaberFirstPerson.GRIP_Y;
		int rgb = SaberComponents.config(stack).color().rgb();

		// A fresh state per submit: the collector draws later and keeps references to the layers. The stack is the entity's
		// copy without the thrown component, so the item model draws the saber, not the empty model of a saber in flight.
		ItemStackRenderState state = new ItemStackRenderState();
		// API-CHECK: ItemDisplayContext.NONE selects the held model (not the inventory icon) and applies no display transform
		// (the hilt models have no "none" entry); updateForTopItem accepts a null ItemOwner (as RisingHolocron assumes).
		mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.NONE, level, null, saber.getId());
		int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(at));

		poseStack.pushPose();
		poseStack.translate(centre.x, centre.y, centre.z);
		// The model's +y (pommel to emitter) along direction(angle), its +z down: its x-y plane is the spin plane, its +x
		// (where a curved blade leans) trails the spin, and quillons (along z) stand up and down out of it.
		poseStack.mulPose(new Quaternionf().rotationY(Mth.HALF_PI - angle * Mth.DEG_TO_RAD).rotateX(Mth.HALF_PI));
		poseStack.scale(scale, scale, scale);
		// The grip's middle on the centre (the item renderer then moves the model's (8, 8, 8) to the origin).
		poseStack.translate(0f, (8f - grip) * SaberBladeRenderer.PX, 0f);
		// API-CHECK: ItemStackRenderState#submit calls the special renderer's submit (SaberBladeRenderer) at once, inside
		// capture, as the held saber's trails already assume of the pose they are given.
		BladeTrails.capture((blade, base, tip) -> sweeps.add(new Sweep(centre, toLevel.transformPosition(new Vector3f(base)),
				toLevel.transformPosition(new Vector3f(tip)), rgb)), () -> state.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0));
		poseStack.popPose();
	}

	/**
	 * The disc of light a spinning blade sweeps: a fan in the spin plane from the blade back round {@value #SWEEP} degrees,
	 * fading from the leading edge and from the tip in toward the hilt.
	 */
	private static void sweep(PoseStack.Pose pose, VertexConsumer buffer, Sweep sweep) {
		Vector3f in = new Vector3f(sweep.base()).sub(sweep.centre());
		Vector3f out = new Vector3f(sweep.tip()).sub(sweep.centre());
		float inner = in.length();
		float outer = out.length();
		// Where the tip is round the spin, in direction()'s degrees: the sweep's leading edge.
		float lead = (float) Math.toDegrees(Math.atan2(out.z, out.x));
		int steps = Math.round(SWEEP / SWEEP_STEP);

		for (int i = 0; i < steps; i++) {
			float a0 = SWEEP_ALPHA * (float) Math.pow(1f - (float) i / steps, 1.5);
			float a1 = SWEEP_ALPHA * (float) Math.pow(1f - (float) (i + 1) / steps, 1.5);
			Vector3f d0 = direction(lead - i * SWEEP_STEP);
			Vector3f d1 = direction(lead - (i + 1) * SWEEP_STEP);
			// Faint at the hilt, full at the tip.
			vertex(pose, buffer, sweep.centre(), d0, inner, sweep.rgb(), a0 * 0.2f);
			vertex(pose, buffer, sweep.centre(), d0, outer, sweep.rgb(), a0);
			vertex(pose, buffer, sweep.centre(), d1, outer, sweep.rgb(), a1);
			vertex(pose, buffer, sweep.centre(), d1, inner, sweep.rgb(), a1 * 0.2f);
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

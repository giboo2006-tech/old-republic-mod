package com.oldrepublic.client.saber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.saber.SaberComponents;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Third-person swing ribbon: a fading strip between the last few blade positions of a swing by
 * any living entity with a lit saber, in the blade colour. The blade line is approximated from
 * the body yaw, a shoulder point and the vanilla swing progress, so it follows the arc rather than
 * the exact animated hand (listed in KNOWN_ISSUES). The arc follows the swing's {@link Style}: the
 * light combo alternates left-to-right, right-to-left and a vertical finisher, and a heavy attack
 * draws a wide sweep. A dual-wielder's off-hand swing (MILESTONE_5.md 10.2) is drawn from the off
 * arm's shoulder in the off saber's colour. Never drawn for the first-person camera entity.
 */
final class SwingTrailRenderer {
	/** The shape of a swing's arc. */
	enum Style {
		/** The vanilla diagonal (no combo information). */
		DEFAULT(-70f, 50f, 30f, -30f, 6),
		/** Combo step 1. */
		LEFT_TO_RIGHT(-15f, 10f, -65f, 65f, 6),
		/** Combo step 2. */
		RIGHT_TO_LEFT(-5f, 15f, 65f, -65f, 6),
		/** Combo finisher: overhead and down. */
		VERTICAL(-95f, 60f, 5f, -5f, 7),
		/** Heavy attack: a wide, long-lived sweep. */
		HEAVY(-20f, 15f, 95f, -95f, 9),
		/** A dual-wielder's off-hand strike: a low backhand cut from the off side across. */
		OFFHAND(-25f, 20f, 60f, -50f, 6);

		final float pitchFrom;
		final float pitchTo;
		final float yawFrom;
		final float yawTo;
		final int samples;

		Style(float pitchFrom, float pitchTo, float yawFrom, float yawTo, int samples) {
			this.pitchFrom = pitchFrom;
			this.pitchTo = pitchTo;
			this.yawFrom = yawFrom;
			this.yawTo = yawTo;
			this.samples = samples;
		}

		/** The arc of combo step {@code step} (1 to 3). */
		static Style ofComboStep(int step) {
			return switch (step) {
				case 1 -> LEFT_TO_RIGHT;
				case 2 -> RIGHT_TO_LEFT;
				case 3 -> VERTICAL;
				default -> DEFAULT;
			};
		}
	}

	/** How long a style announced by an effect waits for its swing to start. */
	private static final int PENDING_TICKS = 6;
	/** Ticks a trail lingers after its last sample. */
	private static final int LINGER = 4;
	private static final double BLADE_LENGTH = 1.25;

	private record Sample(Vec3 base, Vec3 tip) {
	}

	private static final class Trail {
		final Deque<Sample> samples = new ArrayDeque<>();
		int color;
		int idleTicks;
		Style style = Style.DEFAULT;
		/** Drawn from the off arm (an off-hand swing). */
		boolean offhand;
		/** The swinging blade's length (MILESTONE_5.md 10.3: a short or long blade draws a shorter or longer ribbon). */
		double length = BLADE_LENGTH;
	}

	private record Pending(Style style, long until) {
	}

	private static final Map<Integer, Trail> TRAILS = new HashMap<>();
	private static final Map<Integer, Pending> PENDING = new HashMap<>();
	private static long ticks;

	/** A lit saber swing started: begin sampling this entity. */
	static void onSwing(LivingEntity entity) {
		boolean offhand = SaberAudio.offhandSwing(entity);
		ItemStack lit = offhand ? entity.getOffhandItem() : SaberAudio.litSaber(entity);

		if (lit == null) {
			return;
		}

		Trail trail = TRAILS.computeIfAbsent(entity.getId(), id -> new Trail());
		trail.samples.clear();
		trail.color = SaberComponents.config(lit).color().rgb();
		trail.idleTicks = 0;
		trail.offhand = offhand;
		trail.length = BLADE_LENGTH * SaberModelProperties.bladeScale(lit);
		Pending pending = PENDING.remove(entity.getId());
		trail.style = pending != null && pending.until() >= ticks ? pending.style() : Style.DEFAULT;
	}

	/**
	 * Sets the arc of {@code entityId}'s current or next swing. The own player's swing starts locally
	 * before the server's combo event arrives, so a running trail is restyled in place.
	 */
	static void style(int entityId, Style style) {
		Trail trail = TRAILS.get(entityId);

		if (trail != null && trail.idleTicks == 0 && trail.samples.size() < 3) {
			trail.style = style;
			trail.samples.clear();
		} else {
			PENDING.put(entityId, new Pending(style, ticks + PENDING_TICKS));
		}
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}

		ticks++;
		PENDING.values().removeIf(p -> p.until() < ticks);

		for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Trail> entry = it.next();
			Entity entity = mc.level.getEntity(entry.getKey());
			Trail trail = entry.getValue();

			if (!(entity instanceof LivingEntity living) || SaberAudio.litSaber(living) == null) {
				it.remove();
				continue;
			}

			if (living.swinging) {
				trail.samples.addLast(sample(living, trail.style, trail.offhand, trail.length));
				trail.idleTicks = 0;

				while (trail.samples.size() > trail.style.samples) {
					trail.samples.removeFirst();
				}
			} else if (++trail.idleTicks > LINGER) {
				it.remove();
			} else if (!trail.samples.isEmpty()) {
				trail.samples.removeFirst();
			}
		}
	}

	static void clear() {
		TRAILS.clear();
		PENDING.clear();
	}

	/** Blade base and tip in world space for the current swing progress along {@code style}'s arc. */
	private static Sample sample(LivingEntity entity, Style style, boolean offhand, double length) {
		float progress = entity.getAttackAnim(1f);
		float bodyYaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		// The swinging arm's side: the main arm's (right unless left-handed), or the other for an off-hand swing.
		float side = (entity.getMainArm() == HumanoidArm.LEFT ? -1f : 1f) * (offhand ? -1f : 1f);
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw)).scale(side);
		Vec3 shoulder = entity.position().add(0, entity.getBbHeight() * 0.75, 0).add(right.scale(0.35));
		float pitch = Mth.lerp(progress, style.pitchFrom, style.pitchTo);
		float yawOffset = Mth.lerp(progress, style.yawFrom, style.yawTo) * side;
		Vec3 dir = Vec3.directionFromRotation(pitch, entity.getYRot() + yawOffset);
		Vec3 base = shoulder.add(dir.scale(0.55));
		return new Sample(base, base.add(dir.scale(length)));
	}

	static void render(LevelRenderContext context) {
		if (TRAILS.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Entity camera = mc.getCameraEntity();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		List<Trail> visible = new ArrayList<>();

		for (Map.Entry<Integer, Trail> entry : TRAILS.entrySet()) {
			Trail trail = entry.getValue();

			if (trail.samples.size() < 2 || (firstPerson && camera != null && camera.getId() == entry.getKey())
					|| !Vfx.inRange(trail.samples.peekLast().base())) {
				continue;
			}

			visible.add(trail);
		}

		if (visible.isEmpty()) {
			return;
		}

		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			for (Trail trail : visible) {
				Sample[] samples = trail.samples.toArray(new Sample[0]);
				float fade = 1f - (float) trail.idleTicks / (LINGER + 1);
				float peak = trail.style == Style.HEAVY || trail.style == Style.VERTICAL ? 0.65f : 0.5f;

				for (int i = 0; i + 1 < samples.length; i++) {
					// Older segments are fainter; the newest is at most half opaque (a finisher or heavy a bit more).
					float a0 = peak * fade * i / (samples.length - 1);
					float a1 = peak * fade * (i + 1) / (samples.length - 1);
					quad(pose, buffer, samples[i], samples[i + 1], argb(a0, trail.color), argb(a1, trail.color));
				}
			}
		});
		poseStack.popPose();
	}

	/** One ribbon segment, emitted in both windings so it shows from either side. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Sample from, Sample to, int c0, int c1) {
		vertex(pose, buffer, from.base(), c0);
		vertex(pose, buffer, from.tip(), c0);
		vertex(pose, buffer, to.tip(), c1);
		vertex(pose, buffer, to.base(), c1);

		vertex(pose, buffer, to.base(), c1);
		vertex(pose, buffer, to.tip(), c1);
		vertex(pose, buffer, from.tip(), c0);
		vertex(pose, buffer, from.base(), c0);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int color) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255f), 0, 255) << 24 | (rgb & 0xFFFFFF);
	}

	private SwingTrailRenderer() {
	}
}

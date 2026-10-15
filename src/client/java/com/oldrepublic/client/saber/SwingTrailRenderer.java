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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Third-person swing ribbon: a fading strip between the last few blade positions of a player's
 * swing, in the blade colour. The blade line is approximated from the body yaw, a right-shoulder
 * point and the vanilla swing progress, so it follows the arc rather than the exact animated hand
 * (listed in KNOWN_ISSUES). Never drawn for the first-person camera entity.
 */
final class SwingTrailRenderer {
	private static final int MAX_SAMPLES = 6;
	/** Ticks a trail lingers after its last sample. */
	private static final int LINGER = 4;
	private static final double BLADE_LENGTH = 1.25;

	private record Sample(Vec3 base, Vec3 tip) {
	}

	private static final class Trail {
		final Deque<Sample> samples = new ArrayDeque<>();
		int color;
		int idleTicks;
	}

	private static final Map<Integer, Trail> TRAILS = new HashMap<>();

	/** A lit saber swing started: begin sampling this player. */
	static void onSwing(Player player) {
		ItemStack lit = SaberAudio.litSaber(player);

		if (lit == null) {
			return;
		}

		Trail trail = TRAILS.computeIfAbsent(player.getId(), id -> new Trail());
		trail.samples.clear();
		trail.color = SaberComponents.config(lit).color().rgb();
		trail.idleTicks = 0;
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			TRAILS.clear();
			return;
		}

		for (Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Trail> entry = it.next();
			Entity entity = mc.level.getEntity(entry.getKey());
			Trail trail = entry.getValue();

			if (!(entity instanceof Player player) || SaberAudio.litSaber(player) == null) {
				it.remove();
				continue;
			}

			if (player.swinging) {
				trail.samples.addLast(sample(player));
				trail.idleTicks = 0;

				while (trail.samples.size() > MAX_SAMPLES) {
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
	}

	/** Blade base and tip in world space for the current swing progress. */
	private static Sample sample(Player player) {
		float progress = player.getAttackAnim(1f);
		float bodyYaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw));
		Vec3 shoulder = player.position().add(0, player.getBbHeight() * 0.75, 0).add(right.scale(0.35));
		float pitch = Mth.lerp(progress, -70f, 50f);
		float yawOffset = Mth.lerp(progress, 30f, -30f);
		Vec3 dir = Vec3.directionFromRotation(pitch, player.getYRot() + yawOffset);
		Vec3 base = shoulder.add(dir.scale(0.55));
		return new Sample(base, base.add(dir.scale(BLADE_LENGTH)));
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

				for (int i = 0; i + 1 < samples.length; i++) {
					// Older segments are fainter; the newest is at most half opaque.
					float a0 = 0.5f * fade * i / (samples.length - 1);
					float a1 = 0.5f * fade * (i + 1) / (samples.length - 1);
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

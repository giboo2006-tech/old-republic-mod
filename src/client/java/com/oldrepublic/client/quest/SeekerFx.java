package com.oldrepublic.client.quest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The seeker droid's scan (spec MILESTONE_4.md 8.2, effect {@code seeker_scan}, sent to the marked
 * player only). {@link QuestEffects#SEEKER_SCANNING}: a narrow red fan of lit motes from the
 * droid's eye that sweeps down and up the player for {@code intensity} ticks, with
 * {@code seeker.scan}. {@link QuestEffects#SEEKER_LEAVING}: two chirps and the whine of the droid
 * climbing away. Nothing is drawn on the ground; the fan follows the droid while it hovers.
 */
public final class SeekerFx {
	/** The fan's lamp red and a hotter core near the eye. */
	private static final int SCAN_RED = 0xD0352B;
	private static final int SCAN_CORE = 0xFF7A5C;
	/** Half the fan's horizontal spread, in radians, and the vertical sweep of the scan line. */
	private static final double FAN_HALF_WIDTH = Math.toRadians(14);
	private static final double SWEEP_HALF_HEIGHT = Math.toRadians(16);
	/** Ticks for one sweep down and back up. */
	private static final int SWEEP_PERIOD = 30;
	/** Ticks between the two chirps. */
	private static final int CHIRP_GAP = 7;
	private static final RandomSource RANDOM = RandomSource.create();

	private static final class Scan {
		final int entityId;
		final Vec3 origin;
		final int start;
		final int duration;

		Scan(int entityId, Vec3 origin, int start, int duration) {
			this.entityId = entityId;
			this.origin = origin;
			this.start = start;
			this.duration = duration;
		}
	}

	private record Pending(int at, Vec3 pos) {
	}

	private static final List<Scan> SCANS = new ArrayList<>();
	private static final List<Pending> CHIRPS = new ArrayList<>();

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = eye(entity, payload.pos());

		switch (payload.variant()) {
			case QuestEffects.SEEKER_SCANNING -> {
				SCANS.removeIf(s -> s.entityId == payload.entityId());
				SCANS.add(new Scan(payload.entityId(), payload.pos(), QuestClient.ticks(), Math.max(1, (int) payload.intensity())));
				QuestSounds.at(level, at, ModSounds.SEEKER_SCAN, 0.9f, 1f);
			}
			case QuestEffects.SEEKER_LEAVING -> {
				SCANS.removeIf(s -> s.entityId == payload.entityId());
				QuestSounds.at(level, at, ModSounds.SEEKER_CHIRP, 0.8f, 1.05f);
				CHIRPS.add(new Pending(QuestClient.ticks() + CHIRP_GAP, at));
				QuestSounds.at(level, at, ModSounds.SEEKER_FLY, 0.7f, 1f);
			}
			default -> {
			}
		}
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null || mc.player == null) {
			reset();
			return;
		}

		int now = QuestClient.ticks();
		Iterator<Pending> chirps = CHIRPS.iterator();

		while (chirps.hasNext()) {
			Pending pending = chirps.next();

			if (now >= pending.at()) {
				QuestSounds.at(level, pending.pos(), ModSounds.SEEKER_CHIRP, 0.8f, 1.25f);
				chirps.remove();
			}
		}

		Iterator<Scan> it = SCANS.iterator();

		while (it.hasNext()) {
			Scan scan = it.next();
			int age = now - scan.start;

			if (age > scan.duration) {
				it.remove();
				continue;
			}

			Entity droid = level.getEntity(scan.entityId);
			// When the droid is missing the server sent the player's own id: the fan plays from the stored hover point.
			Vec3 eye = droid == null || droid == mc.player ? scan.origin : eye(droid, scan.origin);
			Vec3 target = mc.player.position().add(0, mc.player.getBbHeight() * 0.55, 0);

			if (Vfx.inRange(eye)) {
				fan(level, eye, target, age);
			}
		}
	}

	/** One tick of the fan: motes along a few rays spread sideways, the scan line sweeping down and up. */
	private static void fan(ClientLevel level, Vec3 eye, Vec3 target, int age) {
		Vec3 toTarget = target.subtract(eye);
		double length = toTarget.length();

		if (length < 0.5) {
			return;
		}

		Vec3 forward = toTarget.scale(1 / length);
		Vec3 side = forward.cross(new Vec3(0, 1, 0));

		if (side.lengthSqr() < 1.0E-4) {
			side = new Vec3(1, 0, 0);
		}

		side = side.normalize();
		Vec3 up = side.cross(forward).normalize();
		double phase = (age % SWEEP_PERIOD) / (double) SWEEP_PERIOD;
		double sweep = Math.sin(phase * Math.PI * 2) * SWEEP_HALF_HEIGHT;
		int rays = Math.max(2, Vfx.count(7));

		for (int i = 0; i < rays; i++) {
			double across = (rays == 1 ? 0 : (i / (double) (rays - 1)) * 2 - 1) * FAN_HALF_WIDTH;
			Vec3 dir = forward.add(side.scale(Math.tan(across))).add(up.scale(Math.tan(sweep))).normalize();
			double reach = length * (0.35 + RANDOM.nextDouble() * 0.75);
			Vec3 p = eye.add(dir.scale(reach));
			level.addParticle(new DustParticleOptions(SCAN_RED, 0.45f), p.x, p.y, p.z, 0, 0, 0);
		}

		if (age % 3 == 0) {
			Vec3 core = eye.add(forward.scale(0.25));
			level.addParticle(new DustParticleOptions(SCAN_CORE, 0.6f), core.x, core.y, core.z, 0, 0, 0);
		}
	}

	/** The droid's lens: a little in front of its centre; the given point when there is no droid. */
	private static Vec3 eye(@Nullable Entity entity, Vec3 fallback) {
		if (entity == null || entity == Minecraft.getInstance().player) {
			return fallback;
		}

		return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
	}

	static void reset() {
		SCANS.clear();
		CHIRPS.clear();
	}

	private SeekerFx() {
	}
}

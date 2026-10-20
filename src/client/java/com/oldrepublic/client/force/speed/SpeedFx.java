package com.oldrepublic.client.force.speed;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Speed visuals and sounds (spec MILESTONE_4.md 9.1, 13.1, 13.3).
 *
 * <ul>
 * <li><b>Start</b> ({@code force_speed} 0): a puff of displaced air and a little dust at the feet,
 * {@code force.speed.start}; for the caster only, the {@code force.speed.loop} wind at the ears,
 * which stops when the run ends on any path (end event, watchdog, death, removal, level change,
 * disconnect).</li>
 * <li><b>Running</b>: 2 dust motes kicked back from each footfall (the ground's colour) and 6 faint
 * bent-air streaks a second at the ankles and wrists. No glow and no after-images.</li>
 * <li><b>Burst</b> (2): a line of bent-air streaks along the dash and {@code force.speed.burst}.</li>
 * <li><b>End</b> (1): a second puff of air and {@code force.speed.end}.</li>
 * </ul>
 *
 * <p>The client also carries the sprint-jump bonus (+20% horizontal on a sprint jump while the own
 * run is up), because a player's movement is decided by their own client.
 */
public final class SpeedFx {
	/** Ticks past the run length the server sent before a lost end event stops the loop. */
	private static final int WATCHDOG_SLACK = 20;
	/** Horizontal blocks between two footfalls at a sprint. */
	private static final double STRIDE = 1.4;
	/** One streak every this many ticks: 6 a second. */
	private static final int STREAK_INTERVAL = 3;
	private static final float SPRINT_JUMP_BONUS = 0.2f;

	/** One runner: until when (client ticks) and the distance walked since the last footfall. */
	private static final class Run {
		final long until;
		double stride;
		@Nullable Vec3 last;

		Run(long until) {
			this.until = until;
		}
	}

	private static final Map<Integer, Run> RUNS = new HashMap<>();
	private static long ticks;
	private static @Nullable PowerLoop loop;
	private static boolean wasOnGround;

	/** {@code force_speed}: start, end or burst. Entity = the runner. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 pos = payload.pos();

		switch (payload.variant()) {
			case ForceSpeed.VARIANT_START -> {
				RUNS.put(payload.entityId(), new Run(ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK));
				FxKit.play(level, pos, ModSounds.FORCE_SPEED_START, 0.8f, FxKit.pitch(0.05f));
				puff(level, pos, 1f);

				if (entity != null && entity == mc.player) {
					startLoop(entity);
				}
			}
			case ForceSpeed.VARIANT_END -> {
				RUNS.remove(payload.entityId());
				FxKit.play(level, pos, ModSounds.FORCE_SPEED_END, 0.6f, FxKit.pitch(0.05f));
				puff(level, pos, 0.6f);
			}
			case ForceSpeed.VARIANT_BURST -> burst(level, payload);
			default -> {
			}
		}
	}

	/** Whether {@code entityId} is running with Force Speed, as far as this client knows. */
	public static boolean isRunning(int entityId) {
		Run run = RUNS.get(entityId);
		return run != null && ticks <= run.until;
	}

	public static void tick(Minecraft mc, ClientLevel level) {
		ticks++;

		Iterator<Map.Entry<Integer, Run>> it = RUNS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Run> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());

			if (ticks > entry.getValue().until || entity == null || entity.isRemoved() || !entity.isAlive()) {
				it.remove();
				continue;
			}

			if (Vfx.inRange(entity.position())) {
				running(level, entity, entry.getValue());
			}
		}

		sprintJump(mc);
	}

	/** Footfall dust and the ankle and wrist streaks of one runner. */
	private static void running(ClientLevel level, Entity entity, Run run) {
		Vec3 pos = entity.position();
		Vec3 last = run.last == null ? pos : run.last;
		run.last = pos;
		Vec3 moved = new Vec3(pos.x - last.x, 0, pos.z - last.z);
		double step = moved.length();

		if (step > 4) {
			// A teleport, not a stride.
			return;
		}

		Vec3 back = step > 1.0e-3 ? moved.normalize().scale(-1) : FxKit.horizontal(entity.getLookAngle()).scale(-1);

		if (entity.onGround()) {
			run.stride += step;

			if (run.stride >= STRIDE) {
				run.stride = 0;
				BlockPos ground = FxKit.groundBelow(level, pos.subtract(0, 0.2, 0), 1);
				float[] rgb = FxKit.dustColor(level, ground);

				// A short trailing plume: low, backward, settling fast.
				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					Vec3 at = pos.add(FxKit.jitter1(0.2), 0.05, FxKit.jitter1(0.2));
					Vec3 v = back.scale(0.05 + RANDOM.nextDouble() * 0.05).add(FxKit.jitter1(0.015), 0.03 + RANDOM.nextDouble() * 0.03, FxKit.jitter1(0.015));
					FxKit.dust(at, v, rgb, 0.08f, 0.55f, 14 + RANDOM.nextInt(6), 0.03f);
				}
			}
		}

		if (ticks % STREAK_INTERVAL == 0 && step > 0.05) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				// Ankles or wrists, alternating sides at random.
				boolean wrist = RANDOM.nextBoolean();
				double height = wrist ? entity.getBbHeight() * 0.55 : 0.2;
				Vec3 side = new Vec3(-back.z, 0, back.x).scale(RANDOM.nextBoolean() ? 0.3 : -0.3);
				FxKit.streak(pos.add(side).add(0, height, 0), back.scale(0.12), 0.18f, 0.18f, 6);
			}
		}
	}

	/** A puff of displaced air and a little dust around the feet. */
	private static void puff(ClientLevel level, Vec3 pos, float scale) {
		if (!Vfx.inRange(pos)) {
			return;
		}

		float[] rgb = FxKit.dustColor(level, FxKit.groundBelow(level, pos.subtract(0, 0.2, 0), 1));

		for (int i = 0, n = Vfx.count(Math.round(4 * scale)); i < n; i++) {
			Vec3 out = FxKit.randomFlat();
			FxKit.ripple(pos.add(out.scale(0.3)).add(0, 0.4 + RANDOM.nextDouble() * 0.6, 0), out.scale(0.04), 0.2f, 0.7f, 0.12f, 8);
		}

		for (int i = 0, n = Vfx.count(Math.round(5 * scale)); i < n; i++) {
			Vec3 out = FxKit.randomFlat();
			FxKit.dust(pos.add(out.scale(0.2 + RANDOM.nextDouble() * 0.3)).add(0, 0.05, 0), out.scale(0.05 + RANDOM.nextDouble() * 0.06).add(0, 0.02, 0),
					rgb, 0.08f, 0.5f, 14 + RANDOM.nextInt(8), 0.02f);
		}
	}

	/** The Burst dash: streaks along the path and the burst sound. Pos = start, dir = dash, intensity = blocks. */
	private static void burst(ClientLevel level, EffectEventPayload payload) {
		Vec3 pos = payload.pos();
		Vec3 dir = payload.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : payload.dir().normalize();
		FxKit.play(level, pos, ModSounds.FORCE_SPEED_BURST, 0.9f, FxKit.pitch(0.05f));

		if (!Vfx.inRange(pos)) {
			return;
		}

		double distance = Math.max(1, payload.intensity());

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			double along = RANDOM.nextDouble() * distance;
			Vec3 at = pos.add(dir.scale(along)).add(FxKit.jitter1(0.35), 0.2 + RANDOM.nextDouble() * 1.4, FxKit.jitter1(0.35));
			FxKit.streak(at, dir.scale(0.25), 0.3f, 0.2f, 7);
		}

		puff(level, pos, 0.8f);
	}

	private static void startLoop(Entity entity) {
		if (loop != null) {
			loop.end();
		}

		int id = entity.getId();
		loop = PowerLoop.start(ModSounds.FORCE_SPEED_LOOP, entity, 0.4f, 1f, () -> isRunning(id), () -> 0);
	}

	/**
	 * The own run's sprint jumps carry 20% further: on the tick the local player leaves the ground in
	 * a sprint jump, the horizontal speed is raised once.
	 */
	private static void sprintJump(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null) {
			wasOnGround = false;
			return;
		}

		boolean onGround = player.onGround();
		Vec3 v = player.getDeltaMovement();

		if (wasOnGround && !onGround && v.y > 0.3 && player.isSprinting() && isRunning(player.getId())) {
			player.setDeltaMovement(v.x * (1 + SPRINT_JUMP_BONUS), v.y, v.z * (1 + SPRINT_JUMP_BONUS));
		}

		wasOnGround = onGround;
	}

	/** Forgets every run and stops the loop (level change, disconnect). */
	public static void clear() {
		RUNS.clear();

		if (loop != null) {
			loop.end();
			loop = null;
		}

		wasOnGround = false;
	}

	private SpeedFx() {
	}
}

package com.oldrepublic.client.force.speed;

import static com.oldrepublic.client.force.kinetic.KineticKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.barrier.HeldDustParticle;
import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.KineticNoise;
import com.oldrepublic.client.force.kinetic.KineticRender;
import com.oldrepublic.client.force.kinetic.PebbleParticle;
import com.oldrepublic.client.force.kinetic.PressureFront;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.KineticParticles;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Force Speed visuals and sounds (spec MILESTONE_4.md 9.1, 13.1, 13.3). The run is shown by the air and the ground it
 * tears through: no glow and no after-images.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_speed} 0): a burst of displaced air round the body (a broken front of bent air), dust
 * blown out from the feet in uneven puffs, grit kicked up, {@code force.speed.start}; for the caster only, the
 * {@code force.speed.loop} wind at the ears, which stops when the run ends on any path (end event, watchdog, death,
 * removal, level change, disconnect).</li>
 * <li><b>Running</b>: every footfall (one every 1.4 blocks) kicks a plume of dust in the ground's colour up and out to the
 * sides, with grit and, over grass, a torn leaf; low dust is dragged along in the runner's wake and spreads behind it; the
 * air the body shoves aside peels off its front in streaks that fan out behind it like a bow wave, and streaks of air
 * dragged along by the ankles, wrists, shoulders and head keep up with the runner and slip back along the body.</li>
 * <li><b>Burst</b> (2): a front of air shoved ahead of the dash, streaks along its whole line and dust ripped up along the
 * ground where it passed, {@code force.speed.burst}.</li>
 * <li><b>End</b> (1): a smaller puff of air and dust and {@code force.speed.end}.</li>
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
	/** Streaks peel off the body every this many ticks. */
	private static final int STREAK_INTERVAL = 2;
	/** The wake's low dust every this many ticks. */
	private static final int WAKE_INTERVAL = 2;
	private static final float SPRINT_JUMP_BONUS = 0.2f;

	/** One runner: until when (client ticks), the distance walked since the last footfall, and its counts. */
	private static final class Run {
		final long until;
		final int plume;
		final int grit;
		final int streaks;
		final int bow;
		final int wake;
		double stride;
		boolean left;
		@Nullable Vec3 last;

		Run(long until) {
			this.until = until;
			// Counted once for the run: per footfall, per streak interval and per wake interval.
			this.plume = Vfx.count(6);
			this.grit = Vfx.count(3);
			this.streaks = Vfx.count(3);
			this.bow = Vfx.count(4);
			this.wake = Vfx.count(2);
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
				puff(level, pos, entity, 1f);

				if (entity != null && entity == mc.player) {
					startLoop(entity);
				}
			}
			case ForceSpeed.VARIANT_END -> {
				RUNS.remove(payload.entityId());
				FxKit.play(level, pos, ModSounds.FORCE_SPEED_END, 0.6f, FxKit.pitch(0.05f));
				puff(level, pos, entity, 0.55f);
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

	/** The footfall plumes, the wake and the streaks of one runner. */
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

		Vec3 ahead = step > 1.0e-3 ? moved.normalize() : FxKit.horizontal(entity.getLookAngle());
		Vec3 side = new Vec3(-ahead.z, 0, ahead.x);
		// How fast, 0 at a walk to 1 at a Force sprint (about 0.4 blocks a tick).
		float pace = (float) Math.min(1, step / 0.4);

		if (entity.onGround()) {
			run.stride += step;

			if (run.stride >= STRIDE) {
				run.stride = 0;
				run.left = !run.left;
				footfall(level, pos.add(side.scale(run.left ? 0.16 : -0.16)), moved, ahead, run, pace);
			}

			if (ticks % WAKE_INTERVAL == 0 && step > 0.1) {
				wake(level, pos, moved, ahead, side, run);
			}
		}

		if (ticks % STREAK_INTERVAL == 0 && step > 0.08) {
			double height = entity.getBbHeight();

			for (int i = 0; i < run.streaks; i++) {
				// Ankles, wrists, shoulders or the head, either side.
				double at = switch (RANDOM.nextInt(4)) {
					case 0 -> 0.15;
					case 1 -> height * 0.45;
					case 2 -> height * 0.75;
					default -> height * 0.95;
				};
				double way = RANDOM.nextBoolean() ? 1 : -1;
				Vec3 from = pos.add(side.scale(way * (0.3 + RANDOM.nextDouble() * 0.25))).add(0, at, 0).add(ahead.scale(0.25));
				// The air dragged along by the body: it keeps up with the runner at first, then slows and slips back along it.
				Vec3 v = moved.scale(0.75 + RANDOM.nextDouble() * 0.15).add(side.scale(way * 0.02)).add(0, KineticKit.spread(0.008), 0);
				AirStreakParticle streak = KineticKit.streak(from, v, 0.36f + 0.16f * pace, 0.68f, 8);

				if (streak != null) {
					streak.stretch(1.2f).drag(0.94f);
				}
			}

			for (int i = 0; i < run.bow; i++) {
				// The air shoved aside by the body peels off its front and fans out wide behind it, as a bow wave does: from in
				// front or behind, long streaks opening out to both sides of the runner.
				double way = RANDOM.nextBoolean() ? 1 : -1;
				Vec3 from = pos.add(ahead.scale(0.4)).add(side.scale(way * (0.2 + RANDOM.nextDouble() * 0.15))).add(0, 0.15 + RANDOM.nextDouble() * (height - 0.25), 0);
				Vec3 v = moved.scale(0.5).add(side.scale(way * (0.16 + RANDOM.nextDouble() * 0.12))).add(0, KineticKit.spread(0.025), 0);
				AirStreakParticle streak = KineticKit.streak(from, v, 0.42f + 0.18f * pace, 0.75f, 8);

				if (streak != null) {
					streak.stretch(1.1f).drag(0.9f);
				}
			}
		}
	}

	/**
	 * A footfall: a plume of dust kicked up and out to the sides, dragged a little way after the runner before it is left
	 * behind; grit flicked behind; a leaf torn off grass. {@code moved} is the runner's step this tick.
	 */
	private static void footfall(ClientLevel level, Vec3 foot, Vec3 moved, Vec3 ahead, Run run, float pace) {
		BlockPos ground = KineticKit.ground(level, foot.subtract(0, 0.2, 0), 1);

		if (ground == null) {
			return;
		}

		float[] dust = KineticKit.dustColor(level, ground);
		float[] haze = KineticKit.airDust(dust);
		float[] grit = KineticKit.gritColor(dust);
		double top = ground.getY() + 1.02;
		Vec3 back = ahead.scale(-1);
		// Out to the side of the foot that struck, so from in front or behind the plumes burst out either side of the runner.
		Vec3 out = new Vec3(-ahead.z, 0, ahead.x).scale(run.left ? 1 : -1);

		for (int i = 0; i < run.plume; i++) {
			Vec3 at = new Vec3(foot.x + KineticKit.spread(0.15), top + 0.05, foot.z + KineticKit.spread(0.15));
			Vec3 v = moved.scale(0.3).add(back.scale(0.04 * (0.6 + 0.6 * pace))).add(out.scale(0.03 + RANDOM.nextDouble() * 0.07))
					.add(KineticKit.spread(0.05), 0.05 + RANDOM.nextDouble() * 0.07, KineticKit.spread(0.05));
			DustCloudParticle cloud = KineticKit.dust(at, v, haze, 0.3f + RANDOM.nextFloat() * 0.16f, 3f, 0.72f + RANDOM.nextFloat() * 0.13f,
					24 + RANDOM.nextInt(14));

			if (cloud != null) {
				cloud.hold(0.18f).lift(-0.0015f).drag(0.88f);
			}
		}

		for (int i = 0; i < run.grit; i++) {
			Vec3 v = back.scale(0.08 + RANDOM.nextDouble() * 0.1).add(moved.scale(0.3)).add(KineticKit.spread(0.04), 0.12 + RANDOM.nextDouble() * 0.1,
					KineticKit.spread(0.04));
			PebbleParticle pebble = KineticKit.pebble(new Vec3(foot.x, top + 0.05, foot.z), v, grit, 0.045f + RANDOM.nextFloat() * 0.04f, 16 + RANDOM.nextInt(8));

			if (pebble != null) {
				pebble.weight(0.8f);
			}
		}

		if (FxKit.leafy(level, ground) && RANDOM.nextInt(3) == 0) {
			leaf(new Vec3(foot.x, top + 0.1, foot.z), back.scale(0.08).add(0, 0.12 + RANDOM.nextDouble() * 0.06, 0));
		}
	}

	/** Low dust dragged along after the runner and spreading to the sides, as the air it pushed through closes in behind it. */
	private static void wake(ClientLevel level, Vec3 pos, Vec3 moved, Vec3 ahead, Vec3 side, Run run) {
		BlockPos ground = KineticKit.ground(level, pos.subtract(0, 0.2, 0), 1);

		if (ground == null) {
			return;
		}

		float[] haze = KineticKit.airDust(KineticKit.dustColor(level, ground));

		for (int i = 0; i < run.wake; i++) {
			double way = KineticKit.spread(1);
			Vec3 at = pos.add(ahead.scale(-0.3 - RANDOM.nextDouble() * 0.6)).add(side.scale(way * 0.45)).add(0, 0.12, 0);
			// The wake follows the runner a while, spreading out to the sides, then is left behind and settles.
			Vec3 v = moved.scale(0.55).add(side.scale(way * 0.06)).add(0, 0.02 + RANDOM.nextDouble() * 0.02, 0);
			DustCloudParticle cloud = KineticKit.dust(at, v, haze, 0.38f + RANDOM.nextFloat() * 0.17f, 2.8f, 0.5f + RANDOM.nextFloat() * 0.12f,
					30 + RANDOM.nextInt(14));

			if (cloud != null) {
				cloud.hold(0.25f).drag(0.9f);
			}
		}
	}

	/** One torn leaf, fluttering down. */
	private static void leaf(Vec3 at, Vec3 velocity) {
		if (KineticKit.spawn(KineticParticles.KINETIC_LEAF, at, velocity) instanceof HeldDustParticle leaf) {
			leaf.flutter(0.01f);
			leaf.color(0.36f + RANDOM.nextFloat() * 0.2f, 0.46f + RANDOM.nextFloat() * 0.12f, 0.2f).size(0.1f, 0.1f).alpha(0.95f).life(30 + RANDOM.nextInt(12))
					.drag(0.9f).fall(0.05f, true);
		}
	}

	/** A burst of displaced air round the body, dust blown out from the feet in uneven puffs and grit kicked up. */
	private static void puff(ClientLevel level, Vec3 pos, @Nullable Entity entity, float scale) {
		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockPos ground = KineticKit.ground(level, pos.subtract(0, 0.2, 0), 1);
		float[] dust = KineticKit.dustColor(level, ground);
		float[] haze = KineticKit.airDust(dust);
		float[] grit = KineticKit.gritColor(dust);
		double height = entity != null ? entity.getBbHeight() : 1.8;
		float light = KineticKit.light(level, pos.add(0, 1, 0));
		KineticRender.add(new PressureFront(pos.add(0, height * 0.5, 0), new Vec3(0, 1, 0)).band(45f, 135f).sectors(4, 60f).flatten(0.9f)
				.radius(0.4f, 1.4f + 1.4f * scale).life(6).burst(2.4f).strength(0.7f * scale + 0.2f).light(light));
		float seed = RANDOM.nextFloat() * 40f;
		int clouds = Vfx.count(Math.round(12 * scale));
		int pebbles = Vfx.count(Math.round(5 * scale));
		int streaks = Vfx.count(Math.round(8 * scale));

		for (int i = 0; i < clouds; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 1.7f + seed, (float) Math.sin(angle) * 1.7f, seed) < 0.38f) {
				continue;
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = pos.add(out.scale(0.2 + RANDOM.nextDouble() * 0.35)).add(0, 0.08, 0);
			Vec3 v = out.scale((0.08 + RANDOM.nextDouble() * 0.08) * (0.6 + 0.4 * scale)).add(0, 0.02 + RANDOM.nextDouble() * 0.04, 0);
			DustCloudParticle cloud = KineticKit.dust(at, v, haze, 0.22f + RANDOM.nextFloat() * 0.14f, 2.6f, 0.55f + RANDOM.nextFloat() * 0.12f,
					24 + RANDOM.nextInt(14));

			if (cloud != null) {
				cloud.hold(0.2f).drag(0.87f);
			}
		}

		for (int i = 0; i < pebbles; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			KineticKit.pebble(pos.add(out.scale(0.25)).add(0, 0.08, 0), out.scale(0.07 + RANDOM.nextDouble() * 0.07).add(0, 0.14 + RANDOM.nextDouble() * 0.1, 0),
					grit, 0.04f + RANDOM.nextFloat() * 0.04f, 18 + RANDOM.nextInt(8));
		}

		for (int i = 0; i < streaks; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = pos.add(out.scale(0.45)).add(0, 0.2 + RANDOM.nextDouble() * height * 0.8, 0);
			AirStreakParticle streak = KineticKit.streak(at, out.scale(0.22 + RANDOM.nextDouble() * 0.1), 0.24f, 0.5f, 6);

			if (streak != null) {
				streak.stretch(0.6f).drag(0.84f);
			}
		}
	}

	/** The Burst dash: air shoved ahead, streaks along the path, dust ripped up behind. Pos = start, dir = dash, intensity = blocks. */
	private static void burst(ClientLevel level, EffectEventPayload payload) {
		Vec3 pos = payload.pos();
		Vec3 dir = payload.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : payload.dir().normalize();
		FxKit.play(level, pos, ModSounds.FORCE_SPEED_BURST, 0.9f, FxKit.pitch(0.05f));

		if (!Vfx.inRange(pos)) {
			return;
		}

		double distance = Math.max(1, payload.intensity());
		Vec3 flat = FxKit.horizontal(dir);
		float light = KineticKit.light(level, pos.add(0, 1, 0));
		KineticRender.add(new PressureFront(pos.add(0, 0.9, 0), dir).spread(55f).sectors(3, 60f).flatten(0.8f).radius(0.4f, (float) distance + 1.5f).life(6)
				.burst(1.8f).strength(0.85f).light(light));
		int streaks = Vfx.count(14);
		int clouds = Vfx.count(10);

		for (int i = 0; i < streaks; i++) {
			double along = RANDOM.nextDouble() * distance;
			Vec3 at = pos.add(dir.scale(along)).add(KineticKit.spread(0.4), 0.15 + RANDOM.nextDouble() * 1.6, KineticKit.spread(0.4));
			AirStreakParticle streak = KineticKit.streak(at, dir.scale(0.35 + RANDOM.nextDouble() * 0.15), 0.32f, 0.55f, 7);

			if (streak != null) {
				streak.stretch(1.2f).drag(0.82f);
			}
		}

		BlockPos ground = KineticKit.ground(level, pos.subtract(0, 0.2, 0), 1);
		float[] haze = KineticKit.airDust(KineticKit.dustColor(level, ground));
		BlockState state = ground == null ? null : level.getBlockState(ground);
		float seed = RANDOM.nextFloat() * 40f;

		for (int i = 0; i < clouds; i++) {
			double along = RANDOM.nextDouble() * distance;

			if (KineticNoise.at((float) along * 0.9f + seed, seed, 0.5f) < 0.35f) {
				continue;
			}

			Vec3 at = pos.add(flat.scale(along)).add(KineticKit.spread(0.35), 0.1, KineticKit.spread(0.35));
			// Torn up behind the dash and pulled along after it.
			DustCloudParticle cloud = KineticKit.dust(at, flat.scale(0.05 + RANDOM.nextDouble() * 0.06).add(0, 0.03 + RANDOM.nextDouble() * 0.03, 0), haze,
					0.26f + RANDOM.nextFloat() * 0.14f, 2.6f, 0.5f + RANDOM.nextFloat() * 0.15f, 28 + RANDOM.nextInt(12));

			if (cloud != null) {
				cloud.hold(0.2f).drag(0.88f);
			}

			if (state != null && RANDOM.nextInt(3) == 0) {
				KineticKit.blockBit(level, state, at, flat.scale(0.12).add(0, 0.12, 0));
			}
		}

		puff(level, pos, null, 0.7f);
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

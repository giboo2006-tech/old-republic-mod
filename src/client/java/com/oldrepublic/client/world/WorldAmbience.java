package com.oldrepublic.client.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.block.CrystalFormationBlock;
import com.oldrepublic.world.block.ResonantSealBlock;
import com.oldrepublic.world.block.UnstableCrystalClusterBlock;
import com.oldrepublic.world.block.WorldBlockAmbience;
import com.oldrepublic.world.trial.ShadowEchoEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client ambience of crystal caves:
 * <ul>
 * <li>Block display ticks: motes and glints at kyber clusters, sparks at unstable ones, glints on
 * veined stone, shimmer on seals. Scaled by the quality preset and the {@link Vfx} budget.</li>
 * <li>{@code crystal.resonance}: one positional loop at the densest nearby kyber formation
 * (6 or more clusters seen recently within a few blocks of each other).</li>
 * <li>{@code ambient.cave}: a non-positional bed while the server says the player is in the
 * cave's underground pieces, plus random {@code crystal.chime} one-shots at nearby clusters.</li>
 * <li>Trial atmosphere (wisps near the chamber walls, motes suppressed, loop ducked) between the
 * trial's begin and end events, plus wisps around Shadow Echoes and timed tells.</li>
 * </ul>
 * Every loop stops on release, level change and disconnect.
 */
final class WorldAmbience implements WorldBlockAmbience.Listener {
	static final WorldAmbience INSTANCE = new WorldAmbience();

	private static final int MAX_TRACKED_CLUSTERS = 160;
	private static final long CLUSTER_MEMORY_TICKS = 160;
	private static final double FORMATION_RADIUS = 5;
	private static final int FORMATION_MIN_CLUSTERS = 6;
	private static final double FORMATION_MAX_DISTANCE = 14;

	/** Kyber clusters seen by display ticks, with the game time they were last seen. */
	private final Map<BlockPos, Long> clusters = new HashMap<>();
	private final List<ActiveTrial> trials = new ArrayList<>();
	private final List<TimedEffect> timed = new ArrayList<>();
	private @Nullable FadingLoop resonance;
	private @Nullable FadingLoop caveBed;
	private boolean inCave;
	private int chimeCooldown = 160;
	private int ticks;
	private @Nullable Object lastLevel;

	/** A trial running near this client: suppresses motes and adds wisps along the chamber wall. */
	record ActiveTrial(Vec3 stoneTop, double radius, long expires) {
	}

	/** Effect that keeps spawning particles for a while (charge tells, the echo forming). */
	record TimedEffect(Kind kind, Vec3 pos, @Nullable Vec3 dir, int duration, int[] age) {
		enum Kind { UNSTABLE_CHARGE, ECHO_GATHER, CRYSTAL_GLOW }
	}

	// ---- display ticks (WorldBlockAmbience.Listener) ----

	@Override
	public void cluster(BlockState state, Level level, BlockPos pos, RandomSource random, boolean unstable) {
		Vec3 center = Vec3.atCenterOf(pos);

		if (!unstable) {
			remember(level, pos);
		}

		if (!Vfx.inRange(center) || suppressed(center)) {
			return;
		}

		Direction facing = state.getValue(CrystalFormationBlock.FACING);
		Vec3 out = CrystalFormationBlock.facingVector(facing);
		Vec3 tip = center.subtract(out.scale(0.5)).add(out.scale(0.25 + random.nextDouble() * 0.3));
		ClientConfig.Preset preset = ClientConfig.get().preset;
		int motes = switch (preset) {
			case REDUCED -> random.nextInt(2);
			case STANDARD, HIGH -> 1;
			case CINEMATIC -> 2;
		};

		if (unstable) {
			boolean charging = state.getValue(UnstableCrystalClusterBlock.CHARGING);
			int sparks = Vfx.count(charging ? 6 : random.nextInt(3) == 0 ? 1 : 0);

			for (int i = 0; i < sparks; i++) {
				Vec3 v = randomDirection(random).scale(0.08).add(out.scale(0.05));
				level.addParticle(WorldParticles.CRYSTAL_SPARK, tip.x, tip.y, tip.z, v.x, v.y, v.z);
			}

			if (random.nextInt(charging ? 1 : 3) == 0) {
				spawn(level, WorldParticles.CRYSTAL_GLINT, jitter(tip, random, 0.25), Vec3.ZERO);
			}

			return;
		}

		for (int i = 0; i < Vfx.count(motes); i++) {
			Vec3 at = jitter(tip, random, 0.3);
			level.addParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z,
					(random.nextDouble() - 0.5) * 0.004, 0.006 + random.nextDouble() * 0.008, (random.nextDouble() - 0.5) * 0.004);
		}

		int glintOdds = preset == ClientConfig.Preset.CINEMATIC ? 2 : preset == ClientConfig.Preset.REDUCED ? 6 : 3;

		if (random.nextInt(glintOdds) == 0) {
			spawn(level, WorldParticles.CRYSTAL_GLINT, jitter(tip, random, 0.3), Vec3.ZERO);
		}
	}

	@Override
	public void vein(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(5) != 0) {
			return;
		}

		Direction face = Direction.values()[random.nextInt(6)];

		if (!level.getBlockState(pos.relative(face)).isAir()) {
			return;
		}

		Vec3 out = CrystalFormationBlock.facingVector(face);
		Vec3 at = Vec3.atCenterOf(pos).add(out.scale(0.52)).add(jitterOnFace(out, random, 0.4));

		if (Vfx.inRange(at)) {
			spawn(level, WorldParticles.CRYSTAL_GLINT, at, Vec3.ZERO);
		}
	}

	@Override
	public void seal(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Vec3 center = Vec3.atCenterOf(pos);

		if (!Vfx.inRange(center)) {
			return;
		}

		boolean open = state.getValue(ResonantSealBlock.OPEN);

		if (open && random.nextInt(3) != 0) {
			return;
		}

		for (int i = 0; i < Vfx.count(open ? 1 : 2); i++) {
			Vec3 at = jitter(center, random, 0.5);
			level.addParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, open ? -0.004 : 0.004, 0);
		}

		if (!open && random.nextInt(4) == 0) {
			spawn(level, WorldParticles.CRYSTAL_GLINT, jitter(center, random, 0.5), Vec3.ZERO);
		}
	}

	@Override
	public void meditationStone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Vec3 top = Vec3.atBottomCenterOf(pos).add(0, 0.65, 0);

		if (!Vfx.inRange(top) || random.nextInt(2) != 0) {
			return;
		}

		Vec3 at = top.add((random.nextDouble() - 0.5) * 0.7, 0.02, (random.nextDouble() - 0.5) * 0.7);
		level.addParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.01, 0);
	}

	@Override
	public void echoTick(ShadowEchoEntity echo) {
		Level level = echo.level();
		Vec3 pos = echo.position();

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = echo.getRandom();
		AABB box = echo.getBoundingBox();
		byte action = echo.action();
		int base = action == ShadowEchoEntity.ACTION_IDLE ? (echo.tickCount % 2 == 0 ? 1 : 0) : 2;

		for (int i = 0; i < Vfx.count(base); i++) {
			Vec3 at = new Vec3(Mth.lerp(random.nextDouble(), box.minX, box.maxX), Mth.lerp(random.nextDouble(), box.minY, box.maxY),
					Mth.lerp(random.nextDouble(), box.minZ, box.maxZ));
			Vec3 v = action == ShadowEchoEntity.ACTION_LUNGE_WINDUP
					? new Vec3(pos.x - at.x, pos.y - at.y, pos.z - at.z).scale(0.08)
					: new Vec3(0, 0.015 + random.nextDouble() * 0.015, 0);
			level.addParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, v.y, v.z);
		}

		// Two faint eye glints so the face reads in the dark; they flicker faster during a tell.
		boolean telling = action == ShadowEchoEntity.ACTION_LUNGE_WINDUP || action == ShadowEchoEntity.ACTION_PUSH_WINDUP;

		if (echo.tickCount % (telling ? 3 : 10) == 0) {
			Vec3 look = Vec3.directionFromRotation(0, echo.getYHeadRot());
			Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.13);
			Vec3 eyes = echo.getEyePosition().add(look.scale(0.32)).add(0, -0.02, 0);
			spawn(level, WorldParticles.CRYSTAL_GLINT, eyes.add(side), Vec3.ZERO);
			spawn(level, WorldParticles.CRYSTAL_GLINT, eyes.subtract(side), Vec3.ZERO);
		}

		if (action == ShadowEchoEntity.ACTION_PUSH_WINDUP) {
			// Ripples drawn in to the palm.
			Vec3 look = Vec3.directionFromRotation(0, echo.getYHeadRot());
			Vec3 hand = echo.getEyePosition().add(look.scale(0.5)).add(0, -0.35, 0);
			Vec3 from = hand.add(randomDirection(random).scale(1.2));
			Vec3 v = hand.subtract(from).scale(0.12);
			level.addParticle(WorldParticles.SHADOW_WISP, from.x, from.y, from.z, v.x, v.y, v.z);
		}
	}

	// ---- server-driven state ----

	void setInCave(boolean value) {
		inCave = value;

		if (value) {
			if (caveBed == null || caveBed.isStopped() || caveBed.isReleased()) {
				caveBed = FadingLoop.play(ModSounds.AMBIENT_CAVE, null, 0.6f, 40);
			}
		} else if (caveBed != null) {
			caveBed.release();
			caveBed = null;
		}
	}

	void trialBegin(ClientLevel level, Vec3 stoneTop, double radius) {
		trials.removeIf(t -> t.stoneTop().distanceToSqr(stoneTop) < 1);
		trials.add(new ActiveTrial(stoneTop, radius, level.getGameTime() + 7200));
	}

	void trialEnd(Vec3 stoneTop) {
		trials.removeIf(t -> t.stoneTop().distanceToSqr(stoneTop) < 1);
	}

	void addTimed(TimedEffect.Kind kind, Vec3 pos, @Nullable Vec3 dir, int duration) {
		timed.removeIf(t -> t.kind() == kind && t.pos().distanceToSqr(pos) < 0.25);
		timed.add(new TimedEffect(kind, pos, dir, duration, new int[1]));
	}

	void clearTimed(TimedEffect.Kind kind, Vec3 pos) {
		timed.removeIf(t -> t.kind() == kind && t.pos().distanceToSqr(pos) < 0.25);
	}

	// ---- client tick ----

	void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level != lastLevel) {
			reset();
			lastLevel = level;
		}

		if (level == null || client.player == null) {
			return;
		}

		ticks++;
		long now = level.getGameTime();
		trials.removeIf(t -> t.expires() < now);
		tickTimed(level);
		tickTrials(level);
		checkCrystal(level);

		if (ticks % 20 == 0) {
			clusters.values().removeIf(seen -> now - seen > CLUSTER_MEMORY_TICKS || seen > now);
			updateResonance(client);
		}

		if (inCave && --chimeCooldown <= 0) {
			chimeCooldown = 160 + level.getRandom().nextInt(240);
			chime(client, level);
		}
	}

	/** Disconnect or level change: stop every loop at once and forget all state. */
	void reset() {
		if (resonance != null) {
			resonance.kill();
			resonance = null;
		}

		if (caveBed != null) {
			caveBed.kill();
			caveBed = null;
		}

		inCave = false;
		trackedCrystal = -1;
		clusters.clear();
		trials.clear();
		timed.clear();
	}

	private void remember(Level level, BlockPos pos) {
		if (clusters.size() >= MAX_TRACKED_CLUSTERS && !clusters.containsKey(pos)) {
			return;
		}

		clusters.put(pos.immutable(), level.getGameTime());
	}

	private void updateResonance(Minecraft client) {
		Vec3 camera = client.gameRenderer.mainCamera().position();
		Vec3 best = null;
		int bestCount = 0;

		for (BlockPos pos : clusters.keySet()) {
			Vec3 center = Vec3.atCenterOf(pos);

			if (center.distanceToSqr(camera) > FORMATION_MAX_DISTANCE * FORMATION_MAX_DISTANCE) {
				continue;
			}

			int count = 0;
			double sx = 0;
			double sy = 0;
			double sz = 0;

			for (BlockPos other : clusters.keySet()) {
				if (other.distSqr(pos) <= FORMATION_RADIUS * FORMATION_RADIUS) {
					count++;
					sx += other.getX() + 0.5;
					sy += other.getY() + 0.5;
					sz += other.getZ() + 0.5;
				}
			}

			if (count >= FORMATION_MIN_CLUSTERS && count > bestCount) {
				bestCount = count;
				best = new Vec3(sx / count, sy / count, sz / count);
			}
		}

		if (best == null) {
			if (resonance != null) {
				resonance.release();
				resonance = null;
			}

			return;
		}

		if (resonance != null && (resonance.isStopped() || resonance.position().distanceToSqr(best) > 16)) {
			resonance.release();
			resonance = null;
		}

		if (resonance == null) {
			resonance = FadingLoop.play(ModSounds.CRYSTAL_RESONANCE, best, 0.55f, 30);
		}

		resonance.duck(suppressed(best) ? 0.3f : 1f);
	}

	private void chime(Minecraft client, ClientLevel level) {
		Vec3 camera = client.gameRenderer.mainCamera().position();
		List<BlockPos> near = new ArrayList<>();

		for (BlockPos pos : clusters.keySet()) {
			if (Vec3.atCenterOf(pos).distanceToSqr(camera) < 16 * 16) {
				near.add(pos);
			}
		}

		if (near.isEmpty()) {
			return;
		}

		BlockPos pos = near.get(level.getRandom().nextInt(near.size()));
		float volume = 0.45f * ClientConfig.get().modSoundVolume;
		level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.CRYSTAL_CHIME, SoundSource.AMBIENT,
				volume, 0.85f + level.getRandom().nextFloat() * 0.35f, false);
		spawn(level, WorldParticles.CRYSTAL_GLINT, Vec3.atCenterOf(pos), Vec3.ZERO);
	}

	private void tickTrials(ClientLevel level) {
		if (trials.isEmpty() || ticks % 3 != 0) {
			return;
		}

		RandomSource random = level.getRandom();

		for (ActiveTrial trial : trials) {
			if (!Vfx.inRange(trial.stoneTop())) {
				continue;
			}

			for (int i = 0; i < Vfx.count(1); i++) {
				double angle = random.nextDouble() * Mth.TWO_PI;
				double r = trial.radius() - 3 - random.nextDouble() * 1.5;
				Vec3 at = trial.stoneTop().add(Math.cos(angle) * r, -0.8 + random.nextDouble() * 3, Math.sin(angle) * r);
				Vec3 v = new Vec3(-Math.cos(angle) * 0.01, 0.01, -Math.sin(angle) * 0.01);
				level.addParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, v.y, v.z);
			}
		}
	}

	private void tickTimed(ClientLevel level) {
		RandomSource random = level.getRandom();
		Iterator<TimedEffect> it = timed.iterator();

		while (it.hasNext()) {
			TimedEffect effect = it.next();
			int age = ++effect.age()[0];

			if (age > effect.duration()) {
				it.remove();
				continue;
			}

			if (!Vfx.inRange(effect.pos())) {
				continue;
			}

			float t = age / (float) effect.duration();

			switch (effect.kind()) {
				case UNSTABLE_CHARGE -> {
					// Sparks drawn into the crystal, faster and denser as the charge builds.
					for (int i = 0; i < Vfx.count(1 + Math.round(t * 3)); i++) {
						Vec3 from = effect.pos().add(randomDirection(random).scale(1.2 + random.nextDouble()));
						Vec3 v = effect.pos().subtract(from).scale(0.12 + t * 0.1);
						level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_SPARK, from.x, from.y, from.z, v.x, v.y, v.z);
					}

					if (age % Math.max(1, 6 - Math.round(t * 5)) == 0) {
						level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_GLINT, effect.pos().x, effect.pos().y, effect.pos().z, 0, 0, 0);
					}
				}
				case ECHO_GATHER -> {
					// A column of wisps spiralling in where the echo will stand, with pale motes rising
					// through it so the column reads even in an unlit chamber.
					if (age % 2 == 0) {
						Vec3 at = effect.pos().add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.6);
						level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.04 + t * 0.03, 0);
					}

					for (int i = 0; i < Vfx.count(3 + Math.round(t * 3)); i++) {
						double angle = random.nextDouble() * Mth.TWO_PI;
						double r = 1.6 * (1 - t) + 0.3;
						Vec3 at = effect.pos().add(Math.cos(angle) * r, random.nextDouble() * 1.9, Math.sin(angle) * r);
						Vec3 v = new Vec3(-Math.cos(angle) * 0.04 - Math.sin(angle) * 0.03, 0.005, -Math.sin(angle) * 0.04 + Math.cos(angle) * 0.03);
						level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, v.y, v.z);
					}
				}
				case CRYSTAL_GLOW -> {
					if (age % 8 == 0) {
						spawn(level, WorldParticles.CRYSTAL_GLINT, jitter(effect.pos(), random, 0.2), Vec3.ZERO);
					}
				}
			}
		}
	}

	private boolean suppressed(Vec3 pos) {
		for (ActiveTrial trial : trials) {
			if (trial.stoneTop().distanceToSqr(pos) < (trial.radius() + 1) * (trial.radius() + 1)) {
				return true;
			}
		}

		return false;
	}

	/** Keeps the crystal on the stone glinting until it is picked up (the entity disappears). */
	void glowWhilePresent(int entityId, Vec3 pos) {
		timed.removeIf(t -> t.kind() == TimedEffect.Kind.CRYSTAL_GLOW);
		timed.add(new TimedEffect(TimedEffect.Kind.CRYSTAL_GLOW, pos, null, 20 * 60 * 10, new int[1]));
		trackedCrystal = entityId;
		trackedCrystalGrace = 40;
	}

	private int trackedCrystal = -1;
	/** The item entity may arrive a few ticks after the effect event; wait before giving up on it. */
	private int trackedCrystalGrace;

	/** Called each tick from the client tick hook: ends the glow when the crystal entity is gone. */
	void checkCrystal(ClientLevel level) {
		if (trackedCrystal < 0) {
			return;
		}

		if (trackedCrystalGrace > 0) {
			trackedCrystalGrace--;
			return;
		}

		if (ticks % 10 != 0) {
			return;
		}

		Entity entity = level.getEntity(trackedCrystal);

		if (entity == null || entity.isRemoved()) {
			trackedCrystal = -1;
			timed.removeIf(t -> t.kind() == TimedEffect.Kind.CRYSTAL_GLOW);
		}
	}

	static void spawn(Level level, SimpleParticleType type, Vec3 at, Vec3 v) {
		level.addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
	}

	static Vec3 jitter(Vec3 pos, RandomSource random, double spread) {
		return pos.add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread);
	}

	static Vec3 randomDirection(RandomSource random) {
		double y = random.nextDouble() * 2 - 1;
		double angle = random.nextDouble() * Mth.TWO_PI;
		double r = Math.sqrt(1 - y * y);
		return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
	}

	private static Vec3 jitterOnFace(Vec3 normal, RandomSource random, double spread) {
		double a = (random.nextDouble() - 0.5) * 2 * spread;
		double b = (random.nextDouble() - 0.5) * 2 * spread;

		if (Math.abs(normal.x) > 0.5) {
			return new Vec3(0, a, b);
		}

		if (Math.abs(normal.y) > 0.5) {
			return new Vec3(a, 0, b);
		}

		return new Vec3(a, b, 0);
	}

	private WorldAmbience() {
	}
}

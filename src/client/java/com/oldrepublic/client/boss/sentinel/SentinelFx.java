package com.oldrepublic.client.boss.sentinel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.boss.sentinel.LumenSparkProvider.Spark;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.construct.SentinelEffects;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers for the five Ruin Sentinel effect events (spec 13.3), and the short running
 * effects that follow a sentinel through its tells:
 *
 * <ul>
 * <li>{@code sentinel_wake} 0: the lumen ignites in sputtering sparks while stone dust sheds from the
 * shoulders for the whole wake ({@code sentinel.wake}, a stone grind). 1: a Watcher bows, a little
 * dust falls from its head (the grind, soft and high).</li>
 * <li>{@code sentinel_slam} 0 (a tell): dust sheds from the raised arms through the tell
 * ({@code sentinel.slam_tell}). 1: irregular dust puffs and ground debris of varied heights along
 * the 90 degree arc, with gaps, never a ring, and a short pressure push at waist height
 * ({@code sentinel.slam}, a camera kick close by).</li>
 * <li>{@code sentinel_core}: the plates part (the model) and lumen specks drift from the core,
 * steadily, while it is exposed ({@code sentinel.core_exposed}).</li>
 * <li>{@code sentinel_lance} 0 (a tell): motes are drawn into the chest through the tell
 * ({@code sentinel.lance_charge}). 1: an amber-white burst at the muzzle and a wisp of smoke
 * ({@code sentinel.lance_fire}); the bolt itself is the enemy feature's {@code SENTINEL} style.</li>
 * <li>{@code sentinel_death}: the lumen gutters out in falling sparks, the body breaks into stone
 * and bronze debris, and dust keeps falling through the slump ({@code sentinel.death}).</li>
 * </ul>
 *
 * <p>Tell particles (the slam and lance tells) go through {@link Vfx#tellCount} and are never
 * thinned by presets; everything else goes through {@link Vfx#count}. The slam stays within its
 * Standard budget of 35 particles (13.1). With flashing effects off, the lumen never sputters.
 */
final class SentinelFx {
	/** The Watcher's pale blue lumen. */
	static final int WATCHER_LUMEN = 0xA8DCFF;
	/** The Tomb sentinel's red lumen. */
	static final int TOMB_LUMEN = 0xFF4636;
	/** The lance bolt's amber-white. */
	private static final int LANCE_WHITE = 0xFFE2A8;
	private static final RandomSource RANDOM = RandomSource.create();
	/** The skins' stone, for debris and falling dust: weathered pale stone and dark basalt. */
	private static final BlockState WATCHER_STONE = Blocks.CALCITE.defaultBlockState();
	private static final BlockState TOMB_STONE = Blocks.SMOOTH_BASALT.defaultBlockState();
	/** The bronze plates' debris. */
	private static final BlockState BRONZE = Blocks.RAW_COPPER_BLOCK.defaultBlockState();
	/** Ticks a running effect may run before the synced action has caught up with the event. */
	private static final int SYNC_GRACE = 4;
	/** Slam impact: angular slots across the 90 degree arc; a few are left empty so it never reads as a ring. */
	private static final int ARC_SLOTS = 9;
	/** Ticks the death keeps shedding debris and dust while the body slumps. */
	private static final int DEATH_TICKS = 20;

	/** The running effects that follow a sentinel through a tell or the exposure. */
	private enum Kind {
		WAKE(RuinSentinel.Action.WAKING),
		SLAM_TELL(RuinSentinel.Action.SLAM_TELL),
		LANCE_CHARGE(RuinSentinel.Action.LANCE_TELL),
		CORE(RuinSentinel.Action.EXPOSED),
		/** The slump after death: follows the stored position, not the entity (its action is never read). */
		DEATH(RuinSentinel.Action.IDLE);

		final RuinSentinel.Action action;

		Kind(RuinSentinel.Action action) {
			this.action = action;
		}
	}

	private static final class Run {
		final int entityId;
		final Kind kind;
		final long start;
		final int length;
		final Vec3 pos;
		final Vec3 facing;
		final SentinelVariant variant;

		Run(int entityId, Kind kind, long start, int length, Vec3 pos, Vec3 facing, SentinelVariant variant) {
			this.entityId = entityId;
			this.kind = kind;
			this.start = start;
			this.length = length;
			this.pos = pos;
			this.facing = facing;
			this.variant = variant;
		}
	}

	private static final List<Run> RUNS = new ArrayList<>();
	private static long ticks;

	static void init() {
		ClientEffects.register(SentinelEffects.WAKE, SentinelFx::onWake);
		ClientEffects.register(SentinelEffects.SLAM, SentinelFx::onSlam);
		ClientEffects.register(SentinelEffects.CORE, SentinelFx::onCore);
		ClientEffects.register(SentinelEffects.LANCE, SentinelFx::onLance);
		ClientEffects.register(SentinelEffects.DEATH, SentinelFx::onDeath);
	}

	// ---------------------------------------------------------------- handlers

	private static void onWake(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == SentinelEffects.WAKE_BOW) {
			play(level, p.pos(), ModSounds.SENTINEL_WAKE, 0.3f, 1.35f + RANDOM.nextFloat() * 0.1f);

			if (entity instanceof RuinSentinel sentinel && Vfx.inRange(p.pos())) {
				fallingDust(level, p.pos(), 0.35, Vfx.count(3), stone(sentinel.variant()));
			}

			return;
		}

		play(level, p.pos(), ModSounds.SENTINEL_WAKE, 1.1f, 0.9f + RANDOM.nextFloat() * 0.15f);

		if (entity instanceof RuinSentinel sentinel) {
			start(sentinel, Kind.WAKE, Math.max(1, Math.round(p.intensity())), p);

			if (Vfx.inRange(p.pos())) {
				// The first spark of the lumen.
				for (int i = 0; i < Vfx.count(3); i++) {
					spark(p.pos().add(jitter(0.12)), jitter(0.03), lumen(sentinel.variant()), 8, true);
				}
			}
		}
	}

	private static void onSlam(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == SentinelEffects.SLAM_TELL) {
			play(level, p.pos(), ModSounds.SENTINEL_SLAM_TELL, 1.1f, 0.95f + RANDOM.nextFloat() * 0.1f);

			if (entity instanceof RuinSentinel sentinel) {
				start(sentinel, Kind.SLAM_TELL, Math.max(1, Math.round(p.intensity())), p);
			}

			return;
		}

		play(level, p.pos(), ModSounds.SENTINEL_SLAM, 1.3f, 0.9f + RANDOM.nextFloat() * 0.1f);
		float radius = Math.max(1f, p.intensity());
		Vec3 facing = horizontal(p.dir(), entity);
		Vec3 origin = entity != null ? entity.position() : p.pos().subtract(facing.scale(radius * 0.55 + 0.55));

		if (mc.player != null) {
			double distance = mc.player.position().distanceTo(p.pos());

			if (distance < radius + 4.0) {
				CameraShake.add(0.3f * (float) (1.0 - distance / (radius + 4.0)));
			}
		}

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		slamImpact(level, origin, facing, radius);
	}

	private static void onCore(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.SENTINEL_CORE_EXPOSED, 1f, 1f);

		if (entity instanceof RuinSentinel sentinel) {
			start(sentinel, Kind.CORE, Math.max(1, Math.round(p.intensity())), p);

			if (Vfx.inRange(p.pos())) {
				// The plates crack apart: grit from the seams, a breath of the lumen.
				BlockParticleOption grit = new BlockParticleOption(ParticleTypes.BLOCK, stone(sentinel.variant()));
				Vec3 facing = sentinel.facing();

				for (int i = 0; i < Vfx.count(4); i++) {
					Vec3 at = p.pos().add(jitter(0.25));
					level.addParticle(grit, at.x, at.y, at.z, facing.x * 0.08, 0.05, facing.z * 0.08);
				}

				for (int i = 0; i < Vfx.count(3); i++) {
					spark(p.pos().add(jitter(0.1)), facing.scale(0.03).add(jitter(0.015)), lumen(sentinel.variant()), 14, false);
				}
			}
		}
	}

	private static void onLance(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == SentinelEffects.LANCE_CHARGE) {
			play(level, p.pos(), ModSounds.SENTINEL_LANCE_CHARGE, 1.1f, 1f);

			if (entity instanceof RuinSentinel sentinel) {
				start(sentinel, Kind.LANCE_CHARGE, Math.max(1, Math.round(p.intensity())), p);
			}

			return;
		}

		play(level, p.pos(), ModSounds.SENTINEL_LANCE_FIRE, 1.3f, 0.95f + RANDOM.nextFloat() * 0.1f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		Vec3 dir = p.dir().lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
		Spark flash = LumenSparkProvider.spawn(p.pos(), Vec3.ZERO, LANCE_WHITE);

		if (flash != null) {
			// One bright tick, or a soft glow fading over a few ticks with flashing effects off.
			if (Vfx.flashing()) {
				flash.size(0.5f).life(2);
			} else {
				flash.size(0.3f).alpha(0.4f).life(5).steady();
			}
		}

		for (int i = 0; i < Vfx.count(6); i++) {
			Vec3 v = dir.add(jitter(0.35)).normalize().scale(0.12 + RANDOM.nextDouble() * 0.12);
			spark(p.pos(), v, LANCE_WHITE, 6 + RANDOM.nextInt(4), false);
		}

		for (int i = 0; i < Vfx.count(2); i++) {
			Vec3 v = dir.scale(0.02).add(0, 0.015, 0);
			level.addParticle(ParticleTypes.SMOKE, p.pos().x, p.pos().y, p.pos().z, v.x, v.y, v.z);
		}
	}

	private static void onDeath(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.SENTINEL_DEATH, 1.3f, 0.9f + RANDOM.nextFloat() * 0.1f);
		SentinelVariant variant = SentinelVariant.values()[Mth.clamp(p.variant(), 0, SentinelVariant.values().length - 1)];
		Vec3 facing = horizontal(p.dir(), entity);
		Vec3 base = entity != null ? entity.position() : p.pos().subtract(0, 1.4, 0);
		RUNS.removeIf(run -> run.entityId == p.entityId());
		RUNS.add(new Run(p.entityId(), Kind.DEATH, ticks, DEATH_TICKS, base, facing, variant));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		// The lumen gutters out: a last few sparks spill from the chest and fall.
		for (int i = 0; i < Vfx.count(5); i++) {
			Vec3 v = facing.scale(0.04).add(jitter(0.04)).add(0, 0.03, 0);
			Spark spark = spark(p.pos().add(jitter(0.1)), v, lumen(variant), 12 + RANDOM.nextInt(8), true);

			if (spark != null) {
				spark.falling();
			}
		}

		// The plates break: stone and bronze debris thrown out and down from the whole body.
		BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, stone(variant));
		BlockParticleOption bronze = new BlockParticleOption(ParticleTypes.BLOCK, BRONZE);

		for (int i = 0; i < Vfx.count(14); i++) {
			Vec3 at = bodyPoint(base, 0.2, 2.1);
			Vec3 out = at.subtract(base).multiply(1, 0, 1);
			Vec3 v = out.lengthSqr() < 1.0e-4 ? jitter(0.08) : out.normalize().scale(0.05 + RANDOM.nextDouble() * 0.08);
			level.addParticle(i % 3 == 0 ? bronze : stone, at.x, at.y, at.z, v.x, 0.05 + RANDOM.nextDouble() * 0.1, v.z);
		}

		level.addParticle(ParticleTypes.DUST_PLUME, base.x, base.y + 0.1, base.z, 0, 0.02, 0);
	}

	// ---------------------------------------------------------------- running effects

	private static void start(RuinSentinel sentinel, Kind kind, int length, EffectEventPayload p) {
		RUNS.removeIf(run -> run.entityId == sentinel.getId() && run.kind != Kind.DEATH);
		RUNS.add(new Run(sentinel.getId(), kind, ticks, length, p.pos(), sentinel.facing(), sentinel.variant()));
	}

	static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level == null) {
			RUNS.clear();
			return;
		}

		for (Iterator<Run> it = RUNS.iterator(); it.hasNext();) {
			Run run = it.next();
			long age = ticks - run.start;

			if (age >= run.length) {
				it.remove();
				continue;
			}

			if (run.kind == Kind.DEATH) {
				tickDeath(level, run, age);
				continue;
			}

			// A running effect ends with its sentinel, or with its action (a tell cancelled by a stagger or an interrupt).
			if (!(level.getEntity(run.entityId) instanceof RuinSentinel sentinel) || !sentinel.isAlive()
					|| age > SYNC_GRACE && sentinel.action() != run.kind.action) {
				it.remove();
				continue;
			}

			Vec3 lumen = sentinel.lumenPosition();

			if (!Vfx.inRange(lumen)) {
				continue;
			}

			switch (run.kind) {
				case WAKE -> tickWake(level, sentinel, lumen, age, run.length);
				case SLAM_TELL -> tickSlamTell(level, sentinel, age);
				case LANCE_CHARGE -> tickLanceCharge(sentinel, lumen, age, run.length);
				case CORE -> tickCore(sentinel, lumen, age);
				default -> {
				}
			}
		}
	}

	/** Stone dust sheds from the shoulders; the lumen catches in sputters that steady as it wakes. */
	private static void tickWake(ClientLevel level, RuinSentinel sentinel, Vec3 lumen, long age, int length) {
		if (age % 2 != 0) {
			return;
		}

		BlockState stone = stone(sentinel.variant());
		Vec3 right = right(sentinel);
		double top = sentinel.getY() + sentinel.getBbHeight() * 0.82;

		for (int i = 0; i < Vfx.count(2); i++) {
			Vec3 shoulder = new Vec3(sentinel.getX(), top, sentinel.getZ()).add(right.scale(RANDOM.nextBoolean() ? 0.5 : -0.5));
			fallingDust(level, shoulder, 0.25, 1, stone);
		}

		float progress = (float) age / Math.max(1, length);

		if (RANDOM.nextFloat() < 0.3f + 0.6f * progress) {
			spark(lumen.add(jitter(0.12)), jitter(0.02), lumen(sentinel.variant()), 8, true);
		}
	}

	/** Dust sheds from the raised forearms, alternating sides: a tell, never thinned by presets. */
	private static void tickSlamTell(ClientLevel level, RuinSentinel sentinel, long age) {
		if (age % 2 != 0 || age < 4) {
			return;
		}

		Vec3 side = right(sentinel).scale(age % 4 == 0 ? 0.45 : -0.45);
		Vec3 hands = sentinel.position().add(0, sentinel.getBbHeight() * 1.05, 0).add(side);
		BlockState stone = stone(sentinel.variant());

		for (int i = 0; i < Vfx.tellCount(1); i++) {
			fallingDust(level, hands, 0.2, 1, stone);
		}
	}

	/** Motes drawn from around the chest into the lumen, faster as the tell closes: a tell, never thinned. */
	private static void tickLanceCharge(RuinSentinel sentinel, Vec3 lumen, long age, int length) {
		float progress = (float) age / Math.max(1, length);
		int color = lumen(sentinel.variant());

		for (int i = 0; i < Vfx.tellCount(1); i++) {
			Vec3 offset = jitter(1.0).normalize().scale(1.0 + RANDOM.nextDouble() * 0.7);
			Vec3 from = lumen.add(offset.x, offset.y * 0.6, offset.z);
			int life = 8;
			// With the spark's friction it covers 0.7 to 1 times the way in over its 8 ticks, fading as it arrives.
			Vec3 v = lumen.subtract(from).scale((0.12 + 0.06 * progress));
			Spark spark = spark(from, v, mixWhite(color, progress * 0.6f), life, false);

			if (spark != null) {
				spark.size(0.03f + 0.03f * progress);
			}
		}
	}

	/** While the core is exposed: lumen specks drifting from the open chest, steady (no flicker). */
	private static void tickCore(RuinSentinel sentinel, Vec3 lumen, long age) {
		if (age % 4 != 0) {
			return;
		}

		Vec3 facing = sentinel.facing();

		for (int i = 0; i < Vfx.count(1); i++) {
			Vec3 v = facing.scale(0.015).add(jitter(0.01)).add(0, 0.012, 0);
			Spark spark = spark(lumen.add(jitter(0.12)), v, lumen(sentinel.variant()), 16, false);

			if (spark != null) {
				spark.steady();
			}
		}
	}

	/** While the body slumps: more chips and dust, then a last settle. */
	private static void tickDeath(ClientLevel level, Run run, long age) {
		if (age % 4 != 2 || !Vfx.inRange(run.pos)) {
			return;
		}

		BlockState stone = stone(run.variant);
		BlockParticleOption chip = new BlockParticleOption(ParticleTypes.BLOCK, age % 8 == 2 ? BRONZE : stone);

		for (int i = 0; i < Vfx.count(2); i++) {
			Vec3 at = bodyPoint(run.pos, 0.3, 1.8 - age * 0.05);
			level.addParticle(chip, at.x, at.y, at.z, run.facing.x * 0.03, 0.02, run.facing.z * 0.03);
		}

		fallingDust(level, run.pos.add(0, 1.6, 0), 0.5, Vfx.count(2), stone);

		if (age >= run.length - 6) {
			for (int i = 0; i < Vfx.count(3); i++) {
				Vec3 at = run.pos.add(jitter(0.6).multiply(1, 0, 1));
				level.addParticle(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 0, 0.01, 0);
			}
		}
	}

	// ---------------------------------------------------------------- the slam impact

	/**
	 * Dust puffs and ground debris across the 90 degree arc in front of {@code origin}: each puff in
	 * its own angular slot at its own distance and height, two or three slots left empty, and a short
	 * pressure push at waist height. About 20 particles at Standard (with the 10 of the tell, within
	 * the slam's 35).
	 */
	private static void slamImpact(ClientLevel level, Vec3 origin, Vec3 facing, float radius) {
		double baseYaw = Math.atan2(facing.z, facing.x);
		int gaps = 2 + RANDOM.nextInt(2);
		boolean[] empty = new boolean[ARC_SLOTS];

		for (int i = 0; i < gaps; i++) {
			empty[RANDOM.nextInt(ARC_SLOTS)] = true;
		}

		int budget = Vfx.count(16);

		for (int i = 0; i < budget; i++) {
			int slot = i % ARC_SLOTS;

			if (empty[slot]) {
				continue;
			}

			double angle = baseYaw + Math.toRadians(-45.0 + (slot + RANDOM.nextDouble()) * (90.0 / ARC_SLOTS));
			double distance = 0.9 + RANDOM.nextDouble() * (radius - 0.4);
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = origin.add(out.scale(distance));
			BlockState ground = groundAt(level, at);
			double lift = 0.04 + RANDOM.nextDouble() * 0.14;

			if (i % 2 == 0 && !ground.isAir()) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z,
						out.x * 0.12, lift + 0.08, out.z * 0.12);
			} else {
				level.addParticle(RANDOM.nextInt(3) == 0 ? ParticleTypes.DUST_PLUME : ParticleTypes.POOF, at.x, at.y + 0.05 + RANDOM.nextDouble() * 0.3, at.z,
						out.x * 0.06, lift * 0.4, out.z * 0.06);
			}
		}

		// The pressure push: a few quick puffs at waist height thrown outward, not a sprite on the floor.
		for (int i = 0; i < Vfx.count(3); i++) {
			double angle = baseYaw + Math.toRadians(-35.0 + RANDOM.nextDouble() * 70.0);
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = origin.add(out.scale(0.9)).add(0, 0.7 + RANDOM.nextDouble() * 0.5, 0);
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, out.x * 0.25, 0.0, out.z * 0.25);
		}
	}

	/** The block under {@code at} (the top face it stands on), or air. */
	private static BlockState groundAt(ClientLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
		BlockState state = level.getBlockState(pos);
		return state.isAir() ? level.getBlockState(pos.below()) : state;
	}

	// ---------------------------------------------------------------- helpers

	/** The lumen colour of {@code variant}. */
	static int lumen(SentinelVariant variant) {
		return variant == SentinelVariant.TOMB ? TOMB_LUMEN : WATCHER_LUMEN;
	}

	private static BlockState stone(SentinelVariant variant) {
		return variant == SentinelVariant.TOMB ? TOMB_STONE : WATCHER_STONE;
	}

	/**
	 * A lumen spark; {@code sputter} makes it flicker when flashing effects are on (the wake and the
	 * death), else it glows steadily.
	 */
	private static @Nullable Spark spark(Vec3 pos, Vec3 velocity, int color, int life, boolean sputter) {
		Spark spark = LumenSparkProvider.spawn(pos, velocity, color);

		if (spark != null) {
			spark.life(life);

			if (sputter && Vfx.flashing()) {
				spark.flicker();
			}
		}

		return spark;
	}

	/** Fine stone dust drifting down from around {@code pos}. */
	private static void fallingDust(ClientLevel level, Vec3 pos, double spread, int count, BlockState stone) {
		ParticleOptions dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, stone);

		for (int i = 0; i < count; i++) {
			Vec3 at = pos.add(jitter(spread));
			level.addParticle(dust, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	/** A random point in the body's column above {@code base}, between {@code low} and {@code high}. */
	private static Vec3 bodyPoint(Vec3 base, double low, double high) {
		double h = low + RANDOM.nextDouble() * Math.max(0.1, high - low);
		return base.add((RANDOM.nextDouble() - 0.5) * 0.9, h, (RANDOM.nextDouble() - 0.5) * 0.9);
	}

	/** The sentinel's right, from its body yaw. */
	private static Vec3 right(RuinSentinel sentinel) {
		float yaw = sentinel.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/** {@code dir} flattened, or the entity's facing, or north. */
	private static Vec3 horizontal(Vec3 dir, @Nullable Entity entity) {
		Vec3 flat = dir.multiply(1, 0, 1);

		if (flat.lengthSqr() > 1.0e-6) {
			return flat.normalize();
		}

		return entity instanceof RuinSentinel sentinel ? sentinel.facing() : new Vec3(0, 0, -1);
	}

	/** {@code rgb} mixed toward white by {@code amount} (0 to 1). */
	private static int mixWhite(int rgb, float amount) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r += Math.round((255 - r) * amount);
		g += Math.round((255 - g) * amount);
		b += Math.round((255 - b) * amount);
		return (r << 16) | (g << 8) | b;
	}

	private static Vec3 jitter(double scale) {
		return new Vec3((RANDOM.nextDouble() - 0.5) * 2 * scale, (RANDOM.nextDouble() - 0.5) * 2 * scale, (RANDOM.nextDouble() - 0.5) * 2 * scale);
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Forgets every running effect (disconnect, level change). */
	static void clear() {
		RUNS.clear();
	}

	private SentinelFx() {
	}
}

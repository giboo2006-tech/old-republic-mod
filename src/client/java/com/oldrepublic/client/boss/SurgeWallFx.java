package com.oldrepublic.client.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.BossParticles;
import com.oldrepublic.client.boss.BossParticleProviders.BossParticle;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Holocron Surge's release (spec 8.3, 13.3): an irregular wall of dust puffs and tumbling debris
 * rushing out from Orren Vasch to the Surge radius. It is never a ring: the circle is cut into
 * {@value #SLOTS} angular slots, each with its own jittered angle, its own height (knee to over the head)
 * and its own puff sizes, and four to six slots are left empty as gaps. Each slot is clipped by blocks on
 * the client: a ray from his feet at the slot's height finds the first block, the slot's puffs stop
 * there and break against it (the server's cover rays decide the damage; this only has to look right),
 * and the puffs themselves collide with blocks. A {@code boss_surge} clash breaks the wall around the
 * clashing player: the slots behind them stop at the clash and the dust splits to both sides.
 *
 * <p>Budget (13.1, the Surge's gather and release together at most 160 at Standard): the release spends
 * exactly {@value #PUFFS} puffs (the travelling puffs and the breaks against blocks together), 10 debris
 * and a 6-puff kick at his feet through {@link Vfx#count}, 76 in all; the gather's tell ({@code BossFx})
 * spends 75 through {@link Vfx#tellCount}, and a clash 8 more: 159.
 */
final class SurgeWallFx {
	/** Angular slots around him. */
	private static final int SLOTS = 28;
	/** How fast the wall's front rushes out, in blocks per tick (8 blocks in 13 ticks). */
	private static final double SPEED = 0.62;
	/** Puffs of the whole wall at Standard. */
	private static final int PUFFS = 60;
	/** Debris chunks of the whole wall at Standard. */
	private static final int DEBRIS = 10;
	/** The kick of dust at his feet as the Surge leaves him. */
	private static final int KICK = 6;
	/** A clash finds the wall released within this many ticks. */
	private static final int CLASH_WINDOW = 6;
	private static final RandomSource RANDOM = RandomSource.create();

	private static final class Slot {
		final double angle;
		final Vec3 out;
		/** Height above his feet of this slot's puffs. */
		final double height;
		/** How far the slot's puffs reach (the radius, or the first block in the way). */
		final double reach;
		final boolean gap;
		/** Puffs this slot emits over its travel. */
		int emissions;
		/** The slot breaks here (a clash), or never (negative). */
		double brokenAt = -1;
		boolean hitWall;

		Slot(double angle, double height, double reach, boolean gap) {
			this.angle = angle;
			this.out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			this.height = height;
			this.reach = reach;
			this.gap = gap;
		}
	}

	private static final class Wall {
		final Vec3 feet;
		final double radius;
		final long start;
		final Slot[] slots;
		int debrisLeft;
		/** Break puffs left for slots blocked short of the radius (part of the puff budget). */
		int breaksLeft;

		Wall(Vec3 feet, double radius, long start, Slot[] slots, int debrisLeft, int breaksLeft) {
			this.feet = feet;
			this.radius = radius;
			this.start = start;
			this.slots = slots;
			this.debrisLeft = debrisLeft;
			this.breaksLeft = breaksLeft;
		}
	}

	private static final List<Wall> WALLS = new ArrayList<>();
	private static long ticks;

	/** {@code boss_surge} release at {@code chest}, radius {@code radius}; {@code vasch} may be null (not loaded here). */
	static void release(ClientLevel level, Vec3 chest, float radius, @Nullable Entity vasch) {
		Vec3 feet = vasch != null ? vasch.position() : chest.subtract(0, 1.365, 0);
		Entity rayEntity = vasch != null ? vasch : Minecraft.getInstance().player;

		if (rayEntity == null) {
			return;
		}

		double base = RANDOM.nextDouble() * Mth.TWO_PI;
		boolean[] gaps = new boolean[SLOTS];
		int gapCount = 4 + RANDOM.nextInt(3);

		for (int i = 0; i < gapCount; i++) {
			gaps[RANDOM.nextInt(SLOTS)] = true;
		}

		Slot[] slots = new Slot[SLOTS];
		int blocked = 0;
		double weights = 0;

		for (int i = 0; i < SLOTS; i++) {
			double angle = base + (i + RANDOM.nextDouble() * 0.7) * (Mth.TWO_PI / SLOTS);
			double height = 0.15 + RANDOM.nextDouble() * 1.65;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			double reach = reach(level, feet.add(0, Math.max(0.3, height), 0), out, radius, rayEntity);
			slots[i] = new Slot(angle, height, reach, gaps[i]);

			if (!gaps[i]) {
				weights += weight(slots[i], radius);

				if (reach < radius - 0.2) {
					blocked++;
				}
			}
		}

		// The puff budget, spent exactly: a break puff for each slot blocked short of the radius (at most a
		// third of the budget), the rest spread over the live slots by how far each reaches, at most 4 each.
		int puffs = Vfx.count(PUFFS);
		int breaks = Math.min(blocked, puffs / 3);
		int travel = puffs - breaks;
		int given = 0;

		for (Slot slot : slots) {
			if (!slot.gap && weights > 0) {
				slot.emissions = Math.min(4, (int) Math.floor(travel * weight(slot, radius) / weights));
				given += slot.emissions;
			}
		}

		// The remainder of the rounding, one more puff to a slot at a time.
		for (int pass = 0; pass < 4 && given < travel; pass++) {
			for (Slot slot : slots) {
				if (given < travel && !slot.gap && slot.emissions < 4) {
					slot.emissions++;
					given++;
				}
			}
		}

		WALLS.add(new Wall(feet, radius, ticks, slots, Vfx.count(DEBRIS), breaks));

		// The kick of dust at his feet as it leaves him.
		for (int i = 0, n = Vfx.count(KICK); i < n; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			puff(feet.add(out.scale(0.4)).add(0, 0.1, 0), out.scale(0.35).add(0, 0.02, 0), 0.3f);
		}
	}

	/**
	 * {@code boss_surge} clash: the wall breaks around the player at {@code at} (who clashed with Push or a
	 * Deflecting Pulse); the slots behind them stop there and the dust splits around them.
	 */
	static void clash(ClientLevel level, Vec3 at, Vec3 fromVasch, float shelter) {
		Wall wall = null;

		for (Wall candidate : WALLS) {
			if (ticks - candidate.start <= CLASH_WINDOW && candidate.feet.distanceTo(at) <= candidate.radius + 3.0) {
				wall = candidate;
			}
		}

		Vec3 flat = fromVasch.multiply(1, 0, 1);
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();

		if (wall != null) {
			Vec3 rel = at.subtract(wall.feet).multiply(1, 0, 1);
			double distance = rel.length();
			double angle = Math.atan2(rel.z, rel.x);
			// The sheltered cone: about a body and a half wide at the clash, widening a little behind it.
			double halfWidth = Math.atan2(0.9 + 0.15 * shelter, Math.max(0.5, distance));

			for (Slot slot : wall.slots) {
				if (Math.abs(Mth.wrapDegrees((float) Math.toDegrees(slot.angle - angle))) <= Math.toDegrees(halfWidth)) {
					slot.brokenAt = Math.max(0.3, distance - 0.4);
				}
			}
		}

		// The dust splitting around them, to both sides.
		Vec3 side = new Vec3(-dir.z, 0, dir.x);

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 lateral = side.scale(i % 2 == 0 ? 1 : -1);
			Vec3 velocity = lateral.scale(0.18 + RANDOM.nextDouble() * 0.12).add(dir.scale(0.12)).add(0, 0.02 + RANDOM.nextDouble() * 0.03, 0);
			puff(at.add(dir.scale(-0.5)).add(0, -0.4 + RANDOM.nextDouble() * 1.2, 0), velocity, 0.28f);
		}
	}

	static void tick(ClientLevel level) {
		ticks++;

		for (Iterator<Wall> it = WALLS.iterator(); it.hasNext();) {
			Wall wall = it.next();
			long age = ticks - wall.start;
			double front = SPEED * age;
			double previous = SPEED * (age - 1);

			if (previous > wall.radius + 0.5) {
				it.remove();
				continue;
			}

			if (!Vfx.inRange(wall.feet)) {
				continue;
			}

			for (Slot slot : wall.slots) {
				if (!slot.gap) {
					tickSlot(level, wall, slot, previous, front);
				}
			}
		}
	}

	private static void tickSlot(ClientLevel level, Wall wall, Slot slot, double previous, double front) {
		double stop = slot.brokenAt >= 0 ? Math.min(slot.reach, slot.brokenAt) : slot.reach;

		for (int k = 0; k < slot.emissions; k++) {
			// Emission points spread over the slot's travel, a little uneven so no two slots line up.
			double at = stop * (k + 0.35 + 0.3 * ((slot.angle * 7.0 + k) % 1.0)) / slot.emissions;

			if (at <= previous || at > front) {
				continue;
			}

			double height = slot.height * (0.75 + RANDOM.nextDouble() * 0.35);
			Vec3 pos = wall.feet.add(slot.out.scale(at)).add(0, height, 0);
			float size = (float) (0.24 + 0.12 * slot.height + RANDOM.nextDouble() * 0.08);
			puff(pos, slot.out.scale(SPEED * 0.55).add(0, 0.01 + RANDOM.nextDouble() * 0.03, 0), size);

			if (wall.debrisLeft > 0 && RANDOM.nextInt(4) == 0) {
				wall.debrisLeft--;
				debris(level, wall.feet.add(slot.out.scale(at)), slot.out);
			}
		}

		// Blocked short of the radius: the dust breaks against the block.
		if (!slot.hitWall && stop < wall.radius - 0.2 && stop > previous && stop <= front) {
			slot.hitWall = true;

			if (wall.breaksLeft <= 0) {
				return;
			}

			wall.breaksLeft--;
			Vec3 side = new Vec3(-slot.out.z, 0, slot.out.x);
			Vec3 pos = wall.feet.add(slot.out.scale(Math.max(0.2, stop - 0.3))).add(0, slot.height, 0);
			puff(pos, side.scale(RANDOM.nextBoolean() ? 0.12 : -0.12).add(0, 0.05, 0), 0.3f);
		}
	}

	/** A chunk of the floor thrown out and tumbling (the vanilla block particle of the ground). */
	private static void debris(ClientLevel level, Vec3 at, Vec3 out) {
		BlockState ground = groundAt(level, at);

		if (ground.isAir()) {
			return;
		}

		level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.15, at.z,
				out.x * 0.25, 0.12 + RANDOM.nextDouble() * 0.15, out.z * 0.25);
	}

	private static void puff(Vec3 pos, Vec3 velocity, float size) {
		BossParticle puff = BossParticleProviders.spawn(BossParticles.SURGE_DUST, pos, velocity);

		if (puff != null) {
			puff.size(size);
		}
	}

	/** A slot's share of the travelling puffs: nearer slots (blocked early) get fewer. */
	private static double weight(Slot slot, double radius) {
		return Math.max(0.4, slot.reach / radius);
	}

	/** How far a slot reaches before a block: the server's cover rays are authoritative, this only clips the look. */
	private static double reach(ClientLevel level, Vec3 from, Vec3 out, double radius, Entity rayEntity) {
		Vec3 start = from.add(out.scale(0.5));
		Vec3 end = from.add(out.scale(radius));
		BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rayEntity));
		return hit.getType() == HitResult.Type.MISS ? radius : 0.5 + start.distanceTo(hit.getLocation());
	}

	private static BlockState groundAt(ClientLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
		BlockState state = level.getBlockState(pos);
		return state.isAir() ? level.getBlockState(pos.below()) : state;
	}

	/** Forgets every running wall (level change, disconnect). */
	static void clear() {
		WALLS.clear();
	}

	private SurgeWallFx() {
	}
}

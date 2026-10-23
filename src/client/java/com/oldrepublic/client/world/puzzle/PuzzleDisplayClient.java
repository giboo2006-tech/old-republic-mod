package com.oldrepublic.client.world.puzzle;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.puzzle.PuzzleDisplay;
import com.oldrepublic.world.puzzle.PuzzleStates;
import com.oldrepublic.world.puzzle.PuzzleStates.Veil;
import com.oldrepublic.world.puzzle.PuzzleStates.VentPhase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The puzzle kit's display-tick ambience (installed with {@link PuzzleDisplay#set}): faint warm
 * motes over lumen moss, fine dust seeping from a REVEALED veiled stone, an occasional spark from a
 * wreck conduit, slow motes over a READY pedestal, a thin wisp from a resting tomb vent and a warm
 * glint in a solved urn keeper's seams. Each block draws at most a particle or two per display tick,
 * through {@link Vfx#count}, and only within the preset's effect distance.
 */
final class PuzzleDisplayClient implements PuzzleDisplay.Listener {
	static final PuzzleDisplayClient INSTANCE = new PuzzleDisplayClient();

	private PuzzleDisplayClient() {
	}

	@Override
	public void lumenMoss(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(4) != 0) {
			return;
		}

		Vec3 at = new Vec3(pos.getX() + 0.1 + random.nextDouble() * 0.8, pos.getY() + 0.12, pos.getZ() + 0.1 + random.nextDouble() * 0.8);

		if (Vfx.inRange(at)) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				spawn(level, WorldParticles.LIGHT_MOTE, at, new Vec3(0, 0.006 + random.nextDouble() * 0.006, 0));
			}
		}
	}

	@Override
	public void veiledStone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(PuzzleStates.VEIL) != Veil.REVEALED || random.nextInt(2) != 0) {
			return;
		}

		Direction face = Direction.values()[random.nextInt(6)];

		if (face == Direction.DOWN || !level.getBlockState(pos.relative(face)).isAir()) {
			return;
		}

		// On a mortar line: a bed joint (a course) or a staggered head joint.
		double u = random.nextDouble();
		double v = random.nextDouble();

		if (random.nextBoolean()) {
			v = random.nextInt(3) * 0.47 + 0.03;
		} else {
			u = v < 0.5 ? 0.25 : 0.75;
		}

		Vec3 at = PuzzleEffectHandlers.facePoint(pos, face, u, v, 0.03);

		if (Vfx.inRange(at)) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 drift = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()).scale(0.004);
				spawn(level, WorldParticles.VEIL_DUST, at, drift.add(0, -0.004, 0));
			}
		}
	}

	@Override
	public void wreckConduit(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(8) != 0) {
			return;
		}

		Direction face = Direction.values()[random.nextInt(6)];

		if (!level.getBlockState(pos.relative(face)).isAir()) {
			return;
		}

		Vec3 at = PuzzleEffectHandlers.facePoint(pos, face, 0.2 + random.nextDouble() * 0.6, 0.2 + random.nextDouble() * 0.6, 0.03);

		if (!Vfx.inRange(at)) {
			return;
		}

		Vec3 out = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());

		for (int i = 0, n = Vfx.count(1 + random.nextInt(3)); i < n; i++) {
			Vec3 v = out.scale(0.04 + random.nextDouble() * 0.04).add((random.nextDouble() - 0.5) * 0.06, 0.02 + random.nextDouble() * 0.04,
					(random.nextDouble() - 0.5) * 0.06);
			spawn(level, WorldParticles.CONDUIT_SPARK, at, v);
		}
	}

	@Override
	public void pedestal(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(PuzzleStates.READY) || random.nextInt(2) != 0) {
			return;
		}

		Vec3 at = new Vec3(pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 15 / 16.0 + 0.04,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5);

		if (Vfx.inRange(at)) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				spawn(level, WorldParticles.LIGHT_MOTE, at, new Vec3(0, 0.01 + random.nextDouble() * 0.008, 0));
			}
		}
	}

	@Override
	public void tombVent(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(PuzzleStates.PHASE) != VentPhase.IDLE || random.nextInt(5) != 0 || !level.getBlockState(pos.above()).isAir()) {
			return;
		}

		Vec3 at = new Vec3(pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.03, pos.getZ() + 0.3 + random.nextDouble() * 0.4);

		if (!Vfx.inRange(at)) {
			return;
		}

		// Mostly grey tomb dust rising; now and then a hint of the miasma's green.
		ParticleOptions wisp = random.nextInt(12) == 0 ? WorldParticles.MIASMA : WorldParticles.TOMB_DUST;

		for (int i = 0, n = Vfx.count(1); i < n; i++) {
			spawn(level, wisp, at, new Vec3((random.nextDouble() - 0.5) * 0.004, 0.018 + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.004));
		}
	}

	@Override
	public void urnKeeper(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(PuzzleStates.SOLVED) || random.nextInt(3) != 0) {
			return;
		}

		Direction front = state.getValue(PuzzleStates.FACING);

		if (!level.getBlockState(pos.relative(front)).isAir()) {
			return;
		}

		// The carved relief's outline on the front face.
		Vec3 at = PuzzleEffectHandlers.facePoint(pos, front, 0.2 + random.nextDouble() * 0.6, 0.15 + random.nextDouble() * 0.7, 0.03);

		if (Vfx.inRange(at)) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				spawn(level, WorldParticles.LIGHT_MOTE, at, new Vec3(front.getStepX() * 0.004, 0.006, front.getStepZ() * 0.004));
			}
		}
	}

	private static void spawn(Level level, ParticleOptions type, Vec3 at, Vec3 v) {
		level.addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
	}
}

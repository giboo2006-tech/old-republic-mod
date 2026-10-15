package com.oldrepublic.client.quest;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The uncontrolled Force burst at the end of the Awakening, seen by everyone nearby: one soft shell
 * of bent air expanding to the burst radius, leaves and grass shedding pieces, and a soft concussive
 * bloom. No ground marks.
 */
final class AwakeningFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Set<Block> LEAVES = Set.of(Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES,
			Blocks.ACACIA_LEAVES, Blocks.CHERRY_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.PALE_OAK_LEAVES);
	private static final Set<Block> GRASS = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.BUSH, Blocks.SHORT_DRY_GRASS);

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 center = payload.pos();
		QuestSounds.at(level, center, ModSounds.AWAKENING_BURST, 1f, 1f);

		if (entity != null && entity == client.player) {
			CameraShake.add(0.25f);
		}

		if (!Vfx.inRange(center)) {
			return;
		}

		float radius = Math.max(1f, payload.intensity());
		ripple(level, center.add(0, 1.0, 0), radius);
		shedFoliage(level, center, radius);
	}

	/** One expanding shell: ripple particles leave the body in all horizontal directions, slightly up and down. */
	private static void ripple(ClientLevel level, Vec3 origin, float radius) {
		int count = Vfx.count(28);
		// Air ripples live about 10 ticks without drag, so the shell ends near the burst radius.
		double speed = (radius - 0.5) / 10.0;

		for (int i = 0; i < count; i++) {
			double angle = (Math.PI * 2 * i) / count + RANDOM.nextDouble() * 0.1;
			double pitch = (RANDOM.nextDouble() - 0.5) * 0.5;
			Vec3 dir = new Vec3(Math.cos(angle), pitch, Math.sin(angle)).normalize();
			Vec3 start = origin.add(dir.scale(0.5));
			Vec3 vel = dir.scale(speed);
			level.addParticle(ForceParticles.AIR_RIPPLE, start.x, start.y, start.z, vel.x, vel.y, vel.z);
		}
	}

	private static void shedFoliage(ClientLevel level, Vec3 center, float radius) {
		BlockPos origin = BlockPos.containing(center);
		int r = (int) Math.ceil(radius);
		int budget = Vfx.count(36);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = -r; dx <= r && budget > 0; dx++) {
			for (int dz = -r; dz <= r && budget > 0; dz++) {
				for (int dy = -2; dy <= 4 && budget > 0; dy++) {
					if (dx * dx + dz * dz + dy * dy > r * r) {
						continue;
					}

					pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					boolean leaves = LEAVES.contains(state.getBlock());

					if ((!leaves && !GRASS.contains(state.getBlock())) || RANDOM.nextFloat() > 0.35f) {
						continue;
					}

					double x = pos.getX() + RANDOM.nextDouble();
					double y = pos.getY() + (leaves ? 0 : 0.3 + RANDOM.nextDouble() * 0.4);
					double z = pos.getZ() + RANDOM.nextDouble();
					Vec3 away = new Vec3(x - center.x, 0, z - center.z);
					Vec3 push = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize().scale(0.08);
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, push.x, leaves ? -0.02 : 0.12, push.z);
					budget--;
				}
			}
		}
	}

	private AwakeningFx() {
	}
}

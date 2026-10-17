package com.oldrepublic.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.block.ReliquaryHeartBlock;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The paced build of a Reliquary hall (spec 3.6), so nothing starts half-built and no single tick
 * places the whole hall:
 * <ol>
 * <li>the {@value ReliquarySites#SHELL}-block shell around the hall: air, fluid and replaceable cells
 * become rock with {@link Block#UPDATE_CLIENTS} only (no fluid ticks), never a cell inside the
 * surface template or touching a {@code #built_blocks} block;</li>
 * <li>the hall template in {@value #SLABS} Y-slabs, one per tick, bottom first;</li>
 * <li>the heart, set last with its facing turned like the hall; then the hall's {@code WorldSites}
 * record and {@link BossArenas#onReliquaryPlaced}.</li>
 * </ol>
 * A build waits while any chunk of the hall and its shell is unloaded. Builds still running when the
 * server stops are finished on the spot when their chunks are loaded; the others are not saved, but
 * {@link ReliquarySites#resumeUnfinished} queues them again when the server next starts (a surface
 * record with no hall record).
 */
final class ReliquaryBuild {
	/** The hall's 18 layers go in over this many ticks (3.6: 2 to 4). */
	static final int SLABS = 4;
	private static final long TICKS_PER_DAY = 24000L;
	private static final List<Job> JOBS = new ArrayList<>();

	/** One hall being built. {@code step} 0 is the shell, 1 to {@link #SLABS} the slabs, then the heart. */
	private static final class Job {
		final ResourceKey<Level> dimension;
		final UUID owner;
		final SitePiece hall;
		final BoundingBox surface;
		final BlockPos heart;
		int step;

		Job(ResourceKey<Level> dimension, UUID owner, SitePiece hall, BoundingBox surface, BlockPos heart) {
			this.dimension = dimension;
			this.owner = owner;
			this.hall = hall;
			this.surface = surface;
			this.heart = heart;
		}
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ReliquaryBuild::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(ReliquarySites::resumeUnfinished);
		ServerLifecycleEvents.SERVER_STOPPING.register(ReliquaryBuild::finishAll);
	}

	/** Queues the hall for {@code hall} (its piece) under a surface already placed in {@code surface}; the first step runs next tick. */
	static void start(ServerLevel level, UUID owner, SitePiece hall, BoundingBox surface, BlockPos heart) {
		JOBS.add(new Job(level.dimension(), owner, hall, surface, heart.immutable()));
	}

	private static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}

		for (Job job : List.copyOf(JOBS)) {
			ServerLevel level = server.getLevel(job.dimension);

			if (level == null) {
				JOBS.remove(job);
				OldRepublicMod.LOGGER.warn("Dropping the Reliquary hall at {}: its dimension is gone", job.hall.origin());
			} else if (ReliquarySites.loaded(level, job.hall.box().inflatedBy(ReliquarySites.SHELL + 1)) && advance(level, job)) {
				JOBS.remove(job);
			}
		}
	}

	private static void finishAll(MinecraftServer server) {
		for (Job job : List.copyOf(JOBS)) {
			ServerLevel level = server.getLevel(job.dimension);

			if (level != null && ReliquarySites.loaded(level, job.hall.box().inflatedBy(ReliquarySites.SHELL + 1))) {
				boolean done = false;

				while (!done) {
					done = advance(level, job);
				}
			} else {
				OldRepublicMod.LOGGER.warn("The Reliquary hall at {} was not finished before the server stopped; it resumes at the next start", job.hall.origin());
			}
		}

		JOBS.clear();
	}

	/** Runs the job's next step; true when the build is complete. */
	private static boolean advance(ServerLevel level, Job job) {
		if (job.step == 0) {
			shell(level, job.hall.box(), job.surface);
		} else if (job.step <= SLABS) {
			StructureTemplate template = ReliquarySites.template(level, ReliquarySites.HALL);

			if (template == null) {
				return true;
			}

			slab(level, template, job.hall, job.step - 1);
		} else {
			finish(level, job);
			return true;
		}

		job.step++;
		return false;
	}

	/** Fills the open cells of the hall's shell with rock (the surface template and anything beside a built block are left alone). */
	private static void shell(ServerLevel level, BoundingBox hall, BoundingBox surface) {
		BoundingBox shell = hall.inflatedBy(ReliquarySites.SHELL);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = shell.minX(); x <= shell.maxX(); x++) {
			for (int z = shell.minZ(); z <= shell.maxZ(); z++) {
				for (int y = shell.minY(); y <= shell.maxY(); y++) {
					pos.set(x, y, z);

					if (hall.isInside(pos) || surface.isInside(pos)) {
						continue;
					}

					BlockState state = level.getBlockState(pos);

					if (open(state) && !touchesBuilt(level, pos)) {
						level.setBlock(pos, y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** Places Y-slab {@code index} of {@link #SLABS} of the hall template. */
	private static void slab(ServerLevel level, StructureTemplate template, SitePiece hall, int index) {
		BoundingBox box = hall.box();
		int layers = (box.getYSpan() + SLABS - 1) / SLABS;
		int minY = box.minY() + index * layers;
		int maxY = Math.min(box.maxY(), minY + layers - 1);

		if (minY > maxY) {
			return;
		}

		BoundingBox slab = new BoundingBox(box.minX(), minY, box.minZ(), box.maxX(), maxY, box.maxZ());
		template.placeInWorld(level, hall.origin(), hall.origin(),
				ReliquarySites.settings(hall.rotation()).setBoundingBox(slab).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING),
				RandomSource.create(hall.origin().asLong()), Block.UPDATE_CLIENTS);
	}

	/** Sets the heart, records the hall and hands the arena to the fight. */
	private static void finish(ServerLevel level, Job job) {
		Direction facing = job.hall.rotation().rotate(Direction.NORTH);
		level.setBlock(job.heart, BossBlocks.RELIQUARY_HEART.defaultBlockState().setValue(ReliquaryHeartBlock.FACING, facing), Block.UPDATE_ALL);
		long day = level.getServer().overworld().getGameTime() / TICKS_PER_DAY;
		WorldSites.add(level.getServer(), new WorldSites.SiteRecord(job.hall.kind(), level.dimension(), job.hall.origin(), job.hall.rotation(),
				job.hall.template(), job.owner, day));
		BossArenas.onReliquaryPlaced(level, job.heart, job.owner, job.hall);
		OldRepublicMod.LOGGER.info("Reliquary hall finished at {} (heart {})", job.hall.origin(), job.heart);
	}

	private static boolean open(BlockState state) {
		return !state.is(MeetingSites.BUILT_BLOCKS) && (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty());
	}

	private static boolean touchesBuilt(ServerLevel level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).is(MeetingSites.BUILT_BLOCKS)) {
				return true;
			}
		}

		return false;
	}

	private ReliquaryBuild() {
	}
}

package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.mentor.MentorRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Places Chapter Two's small templates ({@code oldrepublic:story/jedi_camp}, {@code sith_camp},
 * {@code raider_camp}) into an existing world, and knows where their people stand.
 *
 * <p>Every template's layer y = 0 is the ground layer: it sits one block below the site's stand
 * height, centred on the site. Before placing, the terrain is adapted: holes under the footprint
 * are filled with dirt and stone down to the ground, and natural blocks (plants, snow, leaves,
 * bumps of natural ground) above it are cleared. Nothing else is removed. The layouts are mirrored
 * in {@code tools/assets/gen_structures_story.py}.
 */
public final class StoryStructures {
	public static final Identifier JEDI_CAMP = OldRepublicMod.id("story/jedi_camp");
	public static final Identifier SITH_CAMP = OldRepublicMod.id("story/sith_camp");
	public static final Identifier RAIDER_CAMP = OldRepublicMod.id("story/raider_camp");

	public static final int CAMP_SIZE = 7;
	public static final int RAIDER_SIZE = 13;

	private static final int SUPPORT_DEPTH = 8;
	private static final int CLEAR_HEIGHT = 7;

	/**
	 * Raider camp, template-local (x, z): where each captive kneels, in front of the binding posts at
	 * (3, 10), (6, 11) and (9, 10), facing the camp's centre.
	 */
	private static final int[][] CAPTIVE_SPOTS = {{3, 9}, {6, 10}, {9, 9}};
	/** Where the crew waits: four poachers, then up to three extras. */
	private static final int[][] CREW_SPOTS = {{2, 3}, {10, 3}, {2, 7}, {11, 6}, {5, 2}, {8, 2}, {11, 9}};
	private static final int[] ACOLYTE_SPOT = {7, 5};
	private static final int[] CAPTAIN_SPOT = {5, 6};

	/** Places the camp of {@code role}'s mentor centred on {@code stand}. Returns whether blocks were placed. */
	public static boolean placeCamp(ServerLevel level, MentorRole role, BlockPos stand, boolean placeBlocks) {
		return place(level, role == MentorRole.SITH ? SITH_CAMP : JEDI_CAMP, stand, placeBlocks);
	}

	/** Places the raider camp centred on {@code stand}. Returns whether blocks were placed. */
	public static boolean placeRaiderCamp(ServerLevel level, BlockPos stand, boolean placeBlocks) {
		return place(level, RAIDER_CAMP, stand, placeBlocks);
	}

	/**
	 * Places a template centred on {@code stand} after adapting the terrain under and above it.
	 * With {@code placeBlocks} false (server switch {@code story.placeStructures}) nothing is changed.
	 */
	public static boolean place(ServerLevel level, Identifier id, BlockPos stand, boolean placeBlocks) {
		if (!placeBlocks) {
			return false;
		}

		Optional<StructureTemplate> template = level.getStructureManager().get(id);

		if (template.isEmpty()) {
			OldRepublicMod.LOGGER.error("Missing story structure template {}", id);
			return false;
		}

		Vec3i size = template.get().getSize();
		BlockPos origin = origin(stand, size.getX(), size.getZ());
		adaptTerrain(level, origin, size.getX(), size.getZ());
		template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(true), level.getRandom(), Block.UPDATE_CLIENTS);
		return true;
	}

	/** The template origin (its local 0, 0, 0) for a template of the given size centred on {@code stand}. */
	public static BlockPos origin(BlockPos stand, int sizeX, int sizeZ) {
		return stand.offset(-sizeX / 2, -1, -sizeZ / 2);
	}

	/**
	 * Fills holes under the footprint down to the ground and clears natural blocks above it, so the
	 * template neither floats nor is buried.
	 */
	public static void adaptTerrain(Level level, BlockPos origin, int sizeX, int sizeZ) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int groundY = origin.getY();

		for (int x = origin.getX(); x < origin.getX() + sizeX; x++) {
			for (int z = origin.getZ(); z < origin.getZ() + sizeZ; z++) {
				for (int y = groundY; y >= groundY - SUPPORT_DEPTH; y--) {
					if (!needsSupport(level.getBlockState(pos.set(x, y, z)))) {
						break;
					}

					BlockState fill = y == groundY ? Blocks.GRASS_BLOCK.defaultBlockState()
							: y >= groundY - 2 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
					level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
				}

				boolean cleared = false;

				for (int y = groundY + 1; y <= groundY + CLEAR_HEIGHT; y++) {
					BlockState state = level.getBlockState(pos.set(x, y, z));

					if (!state.isAir() && isNaturalClutter(state)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						cleared = true;
					}
				}

				// A lowered bump shows its dirt; give it a grass top again.
				if (cleared && level.getBlockState(pos.set(x, groundY, z)).is(Blocks.DIRT)) {
					level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static boolean needsSupport(BlockState state) {
		return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES);
	}

	/** Natural things that may be cleared above a site: plants, snow, leaves and natural ground. */
	private static boolean isNaturalClutter(BlockState state) {
		if (state.is(MeetingSites.BUILT_BLOCKS)) {
			return false;
		}

		return (state.canBeReplaced() && state.getFluidState().isEmpty()) || state.is(BlockTags.LEAVES) || state.is(MeetingSites.STORY_GROUND);
	}

	// --- Where people stand. Positions are feet positions on the ground of each column.

	/** Feet positions in front of the binding posts, one per captive. */
	public static List<BlockPos> captiveSpots(Level level, BlockPos stand) {
		return spots(level, stand, RAIDER_SIZE, CAPTIVE_SPOTS);
	}

	/** Crew positions, in order: four poachers, then extras. */
	public static List<BlockPos> crewSpots(Level level, BlockPos stand) {
		return spots(level, stand, RAIDER_SIZE, CREW_SPOTS);
	}

	public static BlockPos acolyteSpot(Level level, BlockPos stand) {
		return spots(level, stand, RAIDER_SIZE, new int[][] {ACOLYTE_SPOT}).getFirst();
	}

	public static BlockPos captainSpot(Level level, BlockPos stand) {
		return spots(level, stand, RAIDER_SIZE, new int[][] {CAPTAIN_SPOT}).getFirst();
	}

	private static List<BlockPos> spots(Level level, BlockPos stand, int size, int[][] local) {
		BlockPos origin = origin(stand, size, size);
		List<BlockPos> out = new ArrayList<>(local.length);

		for (int[] p : local) {
			int x = origin.getX() + p[0];
			int z = origin.getZ() + p[1];
			int y = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : stand.getY();
			out.add(new BlockPos(x, y, z));
		}

		return out;
	}

	private StoryStructures() {
	}
}

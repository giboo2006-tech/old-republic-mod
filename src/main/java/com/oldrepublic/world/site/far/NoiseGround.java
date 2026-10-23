package com.oldrepublic.world.site.far;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * The ground of a level as its world generator would make it (docs/MILESTONE_6.md 3.1): the noise
 * heights ({@link ChunkGenerator#getBaseHeight}), the noise biome and the structure placements,
 * read without loading or generating a chunk.
 *
 * <p>The structure check looks only at the surface structures of random-spread sets (villages,
 * ruins, the mod's own landmarks), and only at sets one of whose structures may stand in the
 * biome at the point; underground ones (mineshafts, strongholds, trial chambers) never meet a
 * surface site.
 */
final class NoiseGround implements FarChooser.Ground {
	/** A structure set worth checking and its surface structures. */
	private record SurfaceSet(Holder<StructureSet> set, List<Structure> structures) {
	}

	private final ServerLevel level;
	private final ChunkGenerator generator;
	private final RandomState random;
	private final BiomeSource biomes;
	private final ChunkGeneratorStructureState structures;
	private final @Nullable TagKey<Biome> tag;
	private final @Nullable ResourceKey<Biome> key;
	private final int structureRange;
	private final List<SurfaceSet> sets = new ArrayList<>();

	/**
	 * @param tag            a biome tag the target keeps to, or null
	 * @param key            one biome the target keeps to, or null
	 * @param structureRange chunks around a point a structure start must stay out of ({@code far.structureRange})
	 */
	NoiseGround(ServerLevel level, @Nullable TagKey<Biome> tag, @Nullable ResourceKey<Biome> key, int structureRange) {
		this.level = level;
		this.generator = level.getChunkSource().getGenerator();
		this.random = level.getChunkSource().randomState();
		this.biomes = generator.getBiomeSource();
		this.structures = level.getChunkSource().getGeneratorState();
		this.tag = tag;
		this.key = key;
		this.structureRange = structureRange;

		for (Holder<StructureSet> set : structures.possibleStructureSets()) {
			// API-CHECK: StructureSet is a record (structures(), placement()); StructureSelectionEntry is a record with structure().
			if (!(set.value().placement() instanceof RandomSpreadStructurePlacement)) {
				continue;
			}

			List<Structure> surface = new ArrayList<>();

			for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
				Structure structure = entry.structure().value();

				if (structure.step() == GenerationStep.Decoration.SURFACE_STRUCTURES) {
					surface.add(structure);
				}
			}

			if (!surface.isEmpty()) {
				sets.add(new SurfaceSet(set, surface));
			}
		}
	}

	@Override
	public int surface(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
	}

	@Override
	public int floor(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
	}

	@Override
	public boolean biomeAllowed(int x, int y, int z) {
		if (tag == null && key == null) {
			return true;
		}

		Holder<Biome> biome = biome(x, y, z);
		return (tag == null || biome.is(tag)) && (key == null || biome.is(key));
	}

	@Override
	public boolean structureNear(int x, int y, int z) {
		Holder<Biome> biome = biome(x, y, z);

		for (SurfaceSet set : sets) {
			if (allows(set, biome) && structures.hasStructureChunkInRange(set.set(), x >> 4, z >> 4, structureRange)) {
				return true;
			}
		}

		return false;
	}

	/** The noise biome at a block (quart coordinates, the generator's own climate sampler). */
	private Holder<Biome> biome(int x, int y, int z) {
		return biomes.getNoiseBiome(x >> 2, y >> 2, z >> 2, random.sampler());
	}

	private static boolean allows(SurfaceSet set, Holder<Biome> biome) {
		for (Structure structure : set.structures()) {
			if (structure.biomes().contains(biome)) {
				return true;
			}
		}

		return false;
	}
}

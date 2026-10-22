package com.oldrepublic.world.gen;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code oldrepublic:clear_of_origin} (docs/MILESTONE_6.md 6, 7.1): vanilla's
 * random spread, except that no structure starts in a chunk whose centre lies within
 * {@code min_distance} blocks of (0, 0), where every planet's hub stands. The planets' structure
 * sets use it; the JSON is a {@code random_spread} placement plus {@code min_distance} (default 256).
 *
 * <p>It is still a {@link RandomSpreadStructurePlacement}, so the locator's spread maths and
 * vanilla's {@code /locate} treat it as one (a refused cell simply has no start).
 */
public class ClearOfOriginPlacement extends RandomSpreadStructurePlacement {
	/** The default radius kept clear around (0, 0), in blocks. */
	public static final int DEFAULT_MIN_DISTANCE = 256;

	// API-CHECK: StructurePlacement.placementCodec(Instance) returns Products.P5 (locate_offset,
	// frequency_reduction_method, frequency, salt, exclusion_zone), as RandomSpreadStructurePlacement's own
	// codec uses it; P5.and(...) up to P9.
	public static final MapCodec<ClearOfOriginPlacement> CODEC = RecordCodecBuilder.<ClearOfOriginPlacement>mapCodec(i -> placementCodec(i)
			.and(Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing))
			.and(Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation))
			.and(RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType))
			.and(Codec.intRange(0, 1 << 20).optionalFieldOf("min_distance", DEFAULT_MIN_DISTANCE).forGetter(ClearOfOriginPlacement::minDistance))
			.apply(i, ClearOfOriginPlacement::new));
	// API-CHECK: StructurePlacementType is a functional interface with a single codec() method (as
	// StructurePoolElementType for AdaptedPoolElement), registered in BuiltInRegistries.STRUCTURE_PLACEMENT.
	public static final StructurePlacementType<ClearOfOriginPlacement> TYPE = () -> CODEC;

	private final int minDistance;

	public ClearOfOriginPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
			Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, int minDistance) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
		this.minDistance = minDistance;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, OldRepublicMod.id("clear_of_origin"), TYPE);
	}

	public int minDistance() {
		return minDistance;
	}

	/** True when a start in this chunk is far enough from (0, 0): its centre is at least min_distance away. */
	public static boolean clearOfOrigin(int chunkX, int chunkZ, int minDistance) {
		long x = ((long) chunkX << 4) + 8;
		long z = ((long) chunkZ << 4) + 8;
		return x * x + z * z >= (long) minDistance * minDistance;
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
		return clearOfOrigin(chunkX, chunkZ, minDistance) && super.isPlacementChunk(state, chunkX, chunkZ);
	}

	@Override
	public StructurePlacementType<?> type() {
		return TYPE;
	}
}

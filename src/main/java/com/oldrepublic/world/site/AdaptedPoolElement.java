package com.oldrepublic.world.site;

import java.util.Optional;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.CampPoolElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Pool element {@code oldrepublic:adapted_pool_element} (spec 3.2): a single pool element that fits
 * the terrain to its piece after placing it, like the crystal camp's {@link CampPoolElement} but with
 * its numbers in the pool JSON, so the courtyard, the gate, the hull, the outposts and the surface
 * side pieces each get their own. {@code crystal_cave} keeps its own element and saved starts.
 *
 * <ul>
 * <li>{@code support_depth}: foundation columns are filled down to solid ground, at most this deep.</li>
 * <li>{@code clear_from}, {@code clear_inset}: terrain left above template-local height
 * {@code clear_from} (usually the template's height) is removed, inset from the edges.</li>
 * <li>{@code yard_stand}, {@code ramp_rings}: the outer rings of the piece step down toward low
 * ground outside (0 rings: no ramp).</li>
 * </ul>
 */
public class AdaptedPoolElement extends SinglePoolElement {
	public static final MapCodec<AdaptedPoolElement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec(),
			Codec.INT.optionalFieldOf("yard_stand", 1).forGetter(e -> e.adaptation.yardStand()),
			Codec.INT.optionalFieldOf("clear_from", 0).forGetter(e -> e.adaptation.clearFrom()),
			Codec.INT.optionalFieldOf("clear_inset", 0).forGetter(e -> e.adaptation.clearInset()),
			Codec.INT.optionalFieldOf("support_depth", 8).forGetter(e -> e.adaptation.supportDepth()),
			Codec.INT.optionalFieldOf("ramp_rings", 0).forGetter(e -> e.adaptation.rampRings())
	).apply(i, (template, processors, projection, liquid, yardStand, clearFrom, clearInset, supportDepth, rampRings) -> new AdaptedPoolElement(
			template, processors, projection, liquid,
			new CampPoolElement.Adaptation(Math.max(0, supportDepth), Math.max(0, clearInset), clearFrom, CampPoolElement.CLEAR_HEIGHT, yardStand, Math.max(0, rampRings)))));
	// API-CHECK: StructurePoolElementType is a functional interface with a single codec() method (as CampPoolElement.TYPE).
	public static final StructurePoolElementType<AdaptedPoolElement> TYPE = () -> CODEC;

	private final CampPoolElement.Adaptation adaptation;

	protected AdaptedPoolElement(Either<Identifier, StructureTemplate> template, Holder<StructureProcessorList> processors,
			StructureTemplatePool.Projection projection, Optional<LiquidSettings> overrideLiquidSettings, CampPoolElement.Adaptation adaptation) {
		super(template, processors, projection, overrideLiquidSettings);
		this.adaptation = adaptation;
	}

	static void init() {
		Registry.register(BuiltInRegistries.STRUCTURE_POOL_ELEMENT, OldRepublicMod.id("adapted_pool_element"), TYPE);
	}

	@Override
	public boolean place(StructureTemplateManager templates, WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
			BlockPos position, BlockPos reference, Rotation rotation, BoundingBox box, RandomSource random, LiquidSettings liquidSettings,
			boolean keepJigsaws) {
		if (!super.place(templates, level, structures, generator, position, reference, rotation, box, random, liquidSettings, keepJigsaws)) {
			return false;
		}

		// API-CHECK: WorldGenLevel#getHeight(Heightmap.Types, int, int) for a column next to the piece (as CampPoolElement).
		CampPoolElement.adaptTerrain(level, getBoundingBox(templates, position, rotation), box,
				(x, z) -> level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z), adaptation);
		return true;
	}

	@Override
	public StructurePoolElementType<?> getType() {
		return TYPE;
	}
}

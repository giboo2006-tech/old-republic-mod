package com.oldrepublic.planet;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.block.AshrootBlock;
import com.oldrepublic.planet.block.AureliteOreBlock;
import com.oldrepublic.planet.block.BuddingDuskglassBlock;
import com.oldrepublic.planet.block.DuskglassClusterBlock;
import com.oldrepublic.planet.block.HeartstoneBlock;
import com.oldrepublic.planet.block.HeartstoneBlockEntity;
import com.oldrepublic.planet.block.HubSpawnerBlock;
import com.oldrepublic.planet.block.HubSpawnerBlockEntity;
import com.oldrepublic.planet.block.SealedPlatingBlock;
import com.oldrepublic.planet.block.SilverleafBlock;
import com.oldrepublic.planet.block.WaystoneBlock;
import com.oldrepublic.planet.block.WaystoneBlockEntity;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The planet blocks (spec MILESTONE_4.md 3.2 to 3.5, 16.1), their block entities and points of
 * interest, in creative-tab order. The shard items are the lead's ({@code M4Items}).
 */
public final class PlanetBlocks {
	public static final SilverleafBlock SILVERLEAF = ModRegistry.block("silverleaf", SilverleafBlock::new, plant(MapColor.COLOR_LIGHT_GRAY));
	public static final AshrootBlock ASHROOT = ModRegistry.block("ashroot", AshrootBlock::new, plant(MapColor.TERRACOTTA_RED));

	public static final AureliteOreBlock AURELITE_ORE = ModRegistry.block("aurelite_ore", AureliteOreBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.STONE));
	public static final AureliteOreBlock DEEPSLATE_AURELITE_ORE = ModRegistry.block("deepslate_aurelite_ore", AureliteOreBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5f, 3f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));

	public static final DuskglassClusterBlock DUSKGLASS_CLUSTER = ModRegistry.block("duskglass_cluster", DuskglassClusterBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(1.5f)
					.sound(SoundType.AMETHYST_CLUSTER)
					.lightLevel(state -> 1)
					.noOcclusion()
					.pushReaction(PushReaction.DESTROY));
	public static final BuddingDuskglassBlock BUDDING_DUSKGLASS = ModRegistry.block("budding_duskglass", BuddingDuskglassBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLACK)
					.strength(1.5f)
					.sound(SoundType.AMETHYST)
					.randomTicks()
					.requiresCorrectToolForDrops()
					.pushReaction(PushReaction.DESTROY));

	public static final WaystoneBlock WAYSTONE = ModRegistry.block("waystone", p -> new WaystoneBlock(false, p), site(MapColor.STONE, 4));
	public static final WaystoneBlock WAYSTONE_CORRUPTED = ModRegistry.block("waystone_corrupted", p -> new WaystoneBlock(true, p), site(MapColor.DEEPSLATE, 1));
	public static final HeartstoneBlock HEARTSTONE = ModRegistry.block("heartstone", HeartstoneBlock::new, site(MapColor.DEEPSLATE, 2));
	/** Opened only by the fusion cutter or a Force Push ({@link SealedPlatingBlock}). */
	public static final SealedPlatingBlock SEALED_PLATING = ModRegistry.block("sealed_plating", SealedPlatingBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(-1f, 3600000f)
					.sound(SoundType.NETHERITE_BLOCK)
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));
	/** A hub person's spawn point: invisible, no collision, unbreakable, no item. */
	public static final HubSpawnerBlock HUB_SPAWNER = ModRegistry.blockOnly("hub_spawner", HubSpawnerBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.NONE)
					.strength(-1f, 3600000f)
					.sound(SoundType.STONE)
					.noCollision()
					.noOcclusion()
					.noLootTable()
					.noTerrainParticles()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false));

	public static final BlockEntityType<WaystoneBlockEntity> WAYSTONE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("waystone"),
			FabricBlockEntityTypeBuilder.create(WaystoneBlockEntity::new, WAYSTONE, WAYSTONE_CORRUPTED).build());
	public static final BlockEntityType<HeartstoneBlockEntity> HEARTSTONE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("heartstone"),
			FabricBlockEntityTypeBuilder.create(HeartstoneBlockEntity::new, HEARTSTONE).build());
	public static final BlockEntityType<HubSpawnerBlockEntity> HUB_SPAWNER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("hub_spawner"),
			FabricBlockEntityTypeBuilder.create(HubSpawnerBlockEntity::new, HUB_SPAWNER).build());

	/** Waystones of both kinds, for the Sense provider and {@link Waystones#corruptedNear}. */
	public static final ResourceKey<PoiType> WAYSTONE_POI = poi("waystone", WAYSTONE, WAYSTONE_CORRUPTED);
	public static final ResourceKey<PoiType> HEARTSTONE_POI = poi("heartstone", HEARTSTONE);

	private static BlockBehaviour.Properties plant(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.noCollision()
				.instabreak()
				.replaceable()
				.sound(SoundType.GRASS)
				.pushReaction(PushReaction.DESTROY);
	}

	/** Unbreakable in survival, never a spawn surface, never moved by pistons, no drops. */
	private static BlockBehaviour.Properties site(MapColor color, int light) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.strength(-1f, 3600000f)
				.sound(SoundType.STONE)
				.lightLevel(state -> light)
				.noLootTable()
				.pushReaction(PushReaction.BLOCK)
				.isValidSpawn((state, level, pos, type) -> false);
	}

	private static ResourceKey<PoiType> poi(String name, Block... blocks) {
		Identifier id = OldRepublicMod.id(name);
		PoiHelper.register(id, 0, 1, blocks);
		return ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);
	}

	/** Loads the class: registers every block, its item, the block entity types and the POI types. */
	static void init() {
	}

	private PlanetBlocks() {
	}
}

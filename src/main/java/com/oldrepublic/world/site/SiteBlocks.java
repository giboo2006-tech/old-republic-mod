package com.oldrepublic.world.site;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.world.outpost.BountyTerminalBlock;
import com.oldrepublic.world.outpost.BountyTerminalBlockEntity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The three site blocks (W1 plan 4.2) and their block entities: the outpost's {@code bounty_terminal},
 * the owner-only {@code echo_cairn} and the invisible garrison {@code site_spawner}. All three are
 * unbreakable in survival (destroy time -1, explosion resistance 3,600,000) and drop nothing.
 */
public final class SiteBlocks {
	/** The outpost's anchor and mission board; has an item for creative builders. */
	public static final BountyTerminalBlock BOUNTY_TERMINAL = ModRegistry.block("bounty_terminal", BountyTerminalBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(-1f, 3600000f)
					.sound(SoundType.METAL)
					.lightLevel(state -> 5)
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));

	/** An echo cairn or cache; only its owner's Sense reveals it. No item. */
	public static final EchoCairnBlock ECHO_CAIRN = ModRegistry.blockOnly("echo_cairn", EchoCairnBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(-1f, 3600000f)
					.sound(SoundType.STONE)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false));

	/** The spawn point of a site's garrison: invisible, no collision, spawns once. No item. */
	public static final SiteSpawnerBlock SITE_SPAWNER = ModRegistry.blockOnly("site_spawner", SiteSpawnerBlock::new,
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

	public static final BlockEntityType<BountyTerminalBlockEntity> BOUNTY_TERMINAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("bounty_terminal"),
			FabricBlockEntityTypeBuilder.create(BountyTerminalBlockEntity::new, BOUNTY_TERMINAL).build());

	public static final BlockEntityType<EchoCairnBlockEntity> ECHO_CAIRN_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("echo_cairn"),
			FabricBlockEntityTypeBuilder.create(EchoCairnBlockEntity::new, ECHO_CAIRN).build());

	public static final BlockEntityType<SiteSpawnerBlockEntity> SITE_SPAWNER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("site_spawner"),
			FabricBlockEntityTypeBuilder.create(SiteSpawnerBlockEntity::new, SITE_SPAWNER).build());

	/** Loads the class, which registers the blocks and block entity types. */
	public static void init() {
	}

	private SiteBlocks() {
	}
}

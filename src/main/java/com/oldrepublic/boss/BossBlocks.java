package com.oldrepublic.boss;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.block.HoardPlinthBlock;
import com.oldrepublic.boss.block.ReliquaryHeartBlock;
import com.oldrepublic.boss.block.ReliquaryHeartBlockEntity;
import com.oldrepublic.boss.block.ReliquarySealBlock;
import com.oldrepublic.boss.block.ResonancePylonBlock;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Reliquary's four blocks (spec 4.7 of the W5 plan, 16) and the heart's block entity. All four
 * are unbreakable in survival and drop nothing; they are already in {@code #oldrepublic:built_blocks}.
 * Each has a block item (creative and commands); B writes their assets.
 */
public final class BossBlocks {
	/** A resonance pylon: the crystal state is driven by the fight (phase 2). */
	public static final Block RESONANCE_PYLON = ModRegistry.block("resonance_pylon", ResonancePylonBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(-1f, 3600000f)
					.sound(SoundType.AMETHYST)
					.lightLevel(ResonancePylonBlock::lightLevel)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));

	/** The heart in the antechamber: the saved half of the fight. */
	public static final Block RELIQUARY_HEART = ModRegistry.block("reliquary_heart", ReliquaryHeartBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.TERRACOTTA_ORANGE)
					.strength(-1f, 3600000f)
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> 6)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));

	/** The hoard plinth in the vault: opens the Archive choice once the vault is open. */
	public static final Block HOARD_PLINTH = ModRegistry.block("hoard_plinth", HoardPlinthBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.strength(-1f, 3600000f)
					.sound(SoundType.POLISHED_DEEPSLATE)
					.lightLevel(state -> 4)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));

	/** The doorway and vault seals: solid when closed, empty when open. */
	public static final Block RELIQUARY_SEAL = ModRegistry.block("reliquary_seal", ReliquarySealBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_ORANGE)
					.strength(-1f, 3600000f)
					.sound(SoundType.AMETHYST)
					.lightLevel(state -> state.getValue(ReliquarySealBlock.OPEN) ? 0 : 3)
					.noOcclusion()
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false));

	public static final BlockEntityType<ReliquaryHeartBlockEntity> RELIQUARY_HEART_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("reliquary_heart"),
			FabricBlockEntityTypeBuilder.create(ReliquaryHeartBlockEntity::new, RELIQUARY_HEART).build());

	static void init() {
	}

	private BossBlocks() {
	}
}

package com.oldrepublic.travel;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModRegistry;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The ship's blocks (spec MILESTONE_4.md 4.8, 3.5, 16.1): the {@code landing_beacon} (only its owner
 * breaks it; explosions and pistons cannot) and the hubs' {@code pad_terminal} (unbreakable in
 * survival, no drop).
 */
public final class TravelBlocks {
	/** The owner's home pad marker (4.8). */
	public static final LandingBeaconBlock LANDING_BEACON = ModRegistry.block("landing_beacon", LandingBeaconBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(2f, 3600000f)
					.sound(SoundType.METAL)
					.noOcclusion()
					.lightLevel(state -> 7)
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));

	/** A hub pad's recall console (3.5, 4.8); has an item for creative builders. */
	public static final PadTerminalBlock PAD_TERMINAL = ModRegistry.block("pad_terminal", PadTerminalBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.strength(-1f, 3600000f)
					.sound(SoundType.METAL)
					.noOcclusion()
					.lightLevel(state -> 5)
					.noLootTable()
					.pushReaction(PushReaction.BLOCK)
					.isValidSpawn((state, level, pos, type) -> false));

	public static final BlockEntityType<LandingBeaconBlockEntity> LANDING_BEACON_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OldRepublicMod.id("landing_beacon"),
			FabricBlockEntityTypeBuilder.create(LandingBeaconBlockEntity::new, LANDING_BEACON).build());

	/** Loads the class, registering everything above. */
	static void init() {
	}

	private TravelBlocks() {
	}
}

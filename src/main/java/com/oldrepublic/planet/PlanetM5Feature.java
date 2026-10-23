package com.oldrepublic.planet;

import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.LegendaryDrops;
import com.oldrepublic.planet.block.HoldfastGateBlock;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Milestone 5 server and common initialisation for Ghessar and Tyrvann (section 3): their hubs,
 * sites and worldgen, the verse stones, the Hymnal, the detention post, per-dimension weather and
 * sandstorms, ridge lightning and ambience (spec MILESTONE_5.md). Owned by W3 worlds; the lead
 * calls it from {@code OldRepublicMod} after the Phase 0 contracts, so the worker never edits the
 * mod's entry point. Register this feature's own items, blocks, entities, particles, payloads,
 * balance section, events and commands from here.
 */
public final class PlanetM5Feature {
	/** A crashed freighter's hold (3.2); each one a player opens rolls the Sunstone Edge schematic (11.4). */
	public static final ResourceKey<LootTable> FREIGHTER_HOLD = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("chests/crashed_freighter"));
	/** The legendary schematic of the freighter holds (W7's {@code mod_sunstone_edge_legendary}). */
	public static final Identifier SUNSTONE_EDGE_SCHEMATIC = OldRepublicMod.id("mod_sunstone_edge_legendary");
	/** Peat burns 1.5 times coal's time: twelve smelts of the base time (coal is eight). Fixed at registration (16.4). */
	public static final int PEAT_FUEL_SMELTS = 12;

	public static void init() {
		VerseStones.init();
		Hymnal.init();
		PlanetWeather.init();
		FuelValueEvents.BUILD.register((builder, context) -> builder.add(PlanetBlocks.PEAT, context.baseSmeltTime() * PEAT_FUEL_SMELTS));
		LootTableEvents.MODIFY_DROPS.register(PlanetM5Feature::onLootDrops);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PlanetWeather.tick(server);
			RidgeLightning.tick(server);
			Hymnal.tick(server);
			HymnalDoors.tick(server);
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			VerseStones.forget(player.getUUID());
			HoldfastGateBlock.forget(player.getUUID());
			HymnalDoors.forget(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PlanetWeather.clear();
			RidgeLightning.clear();
			VerseStones.clear();
			Hymnal.clear();
			HymnalDoors.clear();
		});
	}

	/**
	 * A freighter hold's loot is rolled once, when a player first opens it (the vanilla chest
	 * unpacking passes the player as {@code this_entity}); that player gets one try at the Sunstone
	 * Edge schematic ({@code planets.freighterEdgeChance} and {@code freighterEdgePity}). The
	 * schematic is taught, not dropped, so the hold's items are left as they are.
	 */
	private static void onLootDrops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (!table.is(FREIGHTER_HOLD)) {
			return;
		}

		// Probed (tools/api-probe/m5_w3.txt): RandomizableContainer#unpackLootTable passes a non-null opening player as THIS_ENTITY.
		if (context.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
			PlanetBalance balance = PlanetBalance.get();
			LegendaryDrops.roll(player, SUNSTONE_EDGE_SCHEMATIC, (float) balance.freighterEdgeChance, balance.freighterEdgePity);
		}
	}

	private PlanetM5Feature() {
	}
}

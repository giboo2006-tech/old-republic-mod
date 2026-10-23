package com.oldrepublic.quest;

import java.util.List;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Handing story and mission rewards to a player: an item goes into the inventory first, and what
 * does not fit is dropped at the player's feet for them alone (the {@code CrystalGrant} pattern:
 * only the owner can pick it up and it never despawns). Loot tables are rolled with an explicit
 * seed, so a test or a daily rule gets the same items every time. Server only.
 */
public final class Rewards {
	/** Ticks before a dropped reward can be picked up, so it is seen landing. */
	private static final int PICKUP_DELAY = 10;

	/**
	 * Gives {@code stack} to the player: as much as fits goes into the inventory, the rest is dropped
	 * at their feet for them only. An empty stack does nothing. The stack passed in is not changed.
	 */
	public static void give(ServerPlayer p, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}

		ItemStack rest = stack.copy();
		// Inventory#add moves what fits and shrinks the stack to the remainder.
		p.getInventory().add(rest);

		if (rest.isEmpty()) {
			return;
		}

		ServerLevel level = p.level();
		ItemEntity drop = new ItemEntity(level, p.getX(), p.getY() + 0.3, p.getZ(), rest, 0, 0.1, 0);
		drop.setTarget(p.getUUID());
		drop.setUnlimitedLifetime();
		drop.setPickUpDelay(PICKUP_DELAY);
		level.addFreshEntity(drop);
	}

	/**
	 * Rolls {@code table} as a chest at the player's position with {@code seed} and returns the
	 * items; an unknown table gives nothing (the loot system logs it).
	 */
	public static List<ItemStack> roll(ServerPlayer p, ResourceKey<LootTable> table, long seed) {
		ServerLevel level = p.level();
		// API-CHECK: MinecraftServer#reloadableRegistries().getLootTable(ResourceKey), LootParams.Builder(level).withParameter(..).create(LootContextParamSets.CHEST) and LootTable#getRandomItems(LootParams, long) (as identity/Settlers.java).
		LootTable loot = level.getServer().reloadableRegistries().getLootTable(table);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, p.position()).create(LootContextParamSets.CHEST);
		List<ItemStack> items = loot.getRandomItems(params, seed);

		if (items.isEmpty()) {
			OldRepublicMod.LOGGER.debug("Loot table {} gave {} nothing (seed {})", table.identifier(), p.getName().getString(), seed);
		}

		return items;
	}

	/** Rolls {@code table} with {@code seed} and gives every item ({@link #give}). */
	public static void giveLoot(ServerPlayer p, ResourceKey<LootTable> table, long seed) {
		for (ItemStack stack : roll(p, table, seed)) {
			give(p, stack);
		}
	}

	/** A seed for a reward roll that stays the same for the same player and the same occasion. */
	public static long seed(ServerPlayer p, String occasion, long salt) {
		long mixed = p.getUUID().getMostSignificantBits() ^ Long.rotateLeft(p.getUUID().getLeastSignificantBits(), 17);
		return mixed ^ (occasion.hashCode() * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
	}

	private Rewards() {
	}
}

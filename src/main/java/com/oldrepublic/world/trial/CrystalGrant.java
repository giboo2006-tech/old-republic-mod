package com.oldrepublic.world.trial;

import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.world.WorldEffects;
import com.oldrepublic.world.block.MeditationStoneBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The attuned kyber crystal of the Trial of Reflection. All state lives in the player's
 * persistent {@link ForceData} so it survives logouts, deaths and restarts, and each step happens
 * exactly once:
 * <ol>
 * <li>{@link #PENDING}: the trial was won but the crystal has not formed yet (position in
 * {@code world.crystal_x/y/z} counters and the {@code world.crystal_dim=} flag).</li>
 * <li>{@link #GRANTED} + {@link #AWAITING_PICKUP}: the owner-only item entity was spawned. It is
 * saved with its chunk like any item, so it can never be duplicated by relogging.</li>
 * <li>Pickup is detected by polling the owner's inventory; then
 * {@link QuestHooks#onCrystalObtained} fires once.</li>
 * </ol>
 */
public final class CrystalGrant {
	public static final String TRIAL_COMPLETE = "world.trial_complete";
	public static final String PENDING = "world.crystal_pending";
	public static final String GRANTED = "world.crystal_granted";
	public static final String AWAITING_PICKUP = "world.crystal_awaiting_pickup";
	static final String POS_X = "world.crystal_x";
	static final String POS_Y = "world.crystal_y";
	static final String POS_Z = "world.crystal_z";
	static final String DIM_PREFIX = "world.crystal_dim=";

	/** Natural hues (the non-red entries of the saber worker's blade palette). */
	private static final int[] NATURAL_COLORS = { 0x2F6BFF, 0x36E05A, 0x3BE3E8, 0xFFD43B, 0xA24DFF, 0xE6EDF5 };

	public static int naturalColor(UUID owner) {
		return NATURAL_COLORS[Math.floorMod(owner.hashCode(), NATURAL_COLORS.length)];
	}

	/**
	 * Records that the player won and the crystal should form at {@code stone}. Returns false if
	 * the player already received their crystal (then nothing is recorded).
	 */
	static boolean markPending(ServerPlayer player, ServerLevel level, BlockPos stone) {
		ForceData data = Progression.get(player);
		data.flags.add(TRIAL_COMPLETE);

		if (data.flags.contains(GRANTED)) {
			Progression.commit(player, data);
			return false;
		}

		data.flags.add(PENDING);
		data.flags.removeIf(flag -> flag.startsWith(DIM_PREFIX));
		data.flags.add(DIM_PREFIX + level.dimension().identifier());
		data.counters.put(POS_X, stone.getX());
		data.counters.put(POS_Y, stone.getY());
		data.counters.put(POS_Z, stone.getZ());
		Progression.commit(player, data);
		return true;
	}

	/** Spawns a pending crystal if the player has one in this level and its chunk is loaded. */
	static void trySpawnPending(ServerPlayer player) {
		ForceData data = Progression.get(player);

		if (!data.flags.contains(PENDING)) {
			return;
		}

		ServerLevel level = player.level();

		if (!data.flags.contains(DIM_PREFIX + level.dimension().identifier())) {
			return;
		}

		BlockPos stone = new BlockPos(data.counter(POS_X), data.counter(POS_Y), data.counter(POS_Z));

		if (level.isLoaded(stone)) {
			spawn(player, level, stone);
		}
	}

	/** Spawns the owner-only crystal on the stone. Clears {@link #PENDING} in the same tick. */
	static boolean spawn(ServerPlayer player, ServerLevel level, BlockPos stone) {
		ForceData data = Progression.get(player);

		if (!data.flags.contains(PENDING)) {
			return false;
		}

		Item item = BuiltInRegistries.ITEM.getValue(OldRepublicMod.id("kyber_crystal"));

		if (item == null || item == Items.AIR) {
			OldRepublicMod.LOGGER.error("oldrepublic:kyber_crystal is not registered; the trial crystal for {} stays pending", player.getPlainTextName());
			return false;
		}

		UUID owner = player.getUUID();
		int color = naturalColor(owner);
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.CRYSTAL_ATTUNEMENT, new CrystalAttunement(owner, player.getPlainTextName(), color));

		Vec3 at = new Vec3(stone.getX() + 0.5, stone.getY() + MeditationStoneBlock.TOP + 0.3, stone.getZ() + 0.5);
		ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack, 0, 0, 0);
		entity.setTarget(owner);
		entity.setUnlimitedLifetime();
		entity.setPickUpDelay(20);
		entity.setNoGravity(true);
		entity.setInvulnerable(true);
		level.addFreshEntity(entity);

		data.flags.remove(PENDING);
		data.flags.add(GRANTED);
		data.flags.add(AWAITING_PICKUP);
		Progression.commit(player, data);

		EffectEvents.around(level, WorldEffects.CRYSTAL_FORM, at, null, entity.getId(), 1f, color);
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.trial.crystal_formed"));
		return true;
	}

	/** Polls the inventory of a player whose crystal waits to be picked up. */
	static void checkPickup(ServerPlayer player) {
		ForceData data = Progression.get(player);

		if (!data.flags.contains(AWAITING_PICKUP) || !holdsOwnCrystal(player)) {
			return;
		}

		data.flags.remove(AWAITING_PICKUP);
		Progression.commit(player, data);
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.trial.crystal_obtained"));
		QuestHooks.onCrystalObtained(player);
	}

	static boolean holdsOwnCrystal(ServerPlayer player) {
		Item item = BuiltInRegistries.ITEM.getValue(OldRepublicMod.id("kyber_crystal"));
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isOwnCrystal(inventory.getItem(slot), item, player)) {
				return true;
			}
		}

		// The stack held on the cursor in an open screen (e.g. moving it straight into the workbench).
		// API-CHECK: AbstractContainerMenu#getCarried (standard, not dumped).
		return isOwnCrystal(player.containerMenu.getCarried(), item, player);
	}

	private static boolean isOwnCrystal(ItemStack stack, Item item, ServerPlayer player) {
		if (stack.isEmpty() || !stack.is(item)) {
			return false;
		}

		CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);
		return attunement != null && attunement.owner().equals(player.getUUID());
	}

	/** Clears every world flag of the player (operator command for testing). */
	public static void reset(ServerPlayer player) {
		ForceData data = Progression.get(player);
		data.flags.removeIf(flag -> flag.startsWith("world."));
		data.counters.keySet().removeIf(key -> key.startsWith("world."));
		Progression.commit(player, data);
	}

	private CrystalGrant() {
	}
}

package com.oldrepublic.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.LightsaberItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player poacher kill tracking and bad-luck protection for the lightsaber emitter. The data
 * pack loot table ({@code oldrepublic:entities/crystal_poacher}) handles credits and the rare grip
 * and pommel drops; the emitter is decided here because it depends on the killer's history.
 */
public final class PoacherLoot {
	/** ForceData counter: poachers this player has killed. */
	public static final String KILLS_COUNTER = "poacher_kills";
	/** ForceData flag: a poacher has dropped an emitter for this player. Chest emitters do not count. */
	public static final String EMITTER_FLAG = "got_emitter";

	private static final Identifier[] EMITTERS = {
			OldRepublicMod.id("emitter_guardian"),
			OldRepublicMod.id("emitter_duelist")
	};

	/**
	 * Called once when a poacher dies on the server.
	 *
	 * @param dropLoot whether loot may drop (game rule and kill conditions); the counter and quest
	 *                 hook run either way
	 */
	static void onKilled(CrystalPoacher poacher, ServerLevel level, DamageSource source, boolean dropLoot) {
		ServerPlayer killer = killer(poacher, source);

		if (killer == null) {
			return;
		}

		ForceData data = Progression.get(killer);
		int kills = data.counter(KILLS_COUNTER) + 1;
		data.counters.put(KILLS_COUNTER, kills);

		if (dropLoot) {
			reconsiderEmitterFlag(killer, data);
		}

		if (dropLoot && !data.hasFlag(EMITTER_FLAG) && rollEmitter(kills, poacher.getRandom())) {
			ItemStack emitter = pickEmitter(poacher.getRandom());

			if (!emitter.isEmpty()) {
				// At the killer's feet and never despawning, so it cannot fall out of reach (camp shaft) or expire.
				ItemEntity drop = new ItemEntity(level, killer.getX(), killer.getY() + 0.25, killer.getZ(), emitter);
				drop.setDefaultPickUpDelay();
				drop.setUnlimitedLifetime();
				level.addFreshEntity(drop);
				data.flags.add(EMITTER_FLAG);
			}
		}

		Progression.commit(killer, data);
		QuestHooks.onPoacherKilled(killer);
	}

	/**
	 * Clears {@link #EMITTER_FLAG} when the player holds neither an emitter nor a lightsaber: the
	 * dropped emitter was lost (never picked up, thrown away), so it may be rolled again and the
	 * kill guarantee applies once more. Does not commit {@code data}.
	 *
	 * @return true when the flag was cleared
	 */
	public static boolean reconsiderEmitterFlag(Player player, ForceData data) {
		if (!data.hasFlag(EMITTER_FLAG) || hasEmitterOrSaber(player)) {
			return false;
		}

		data.flags.remove(EMITTER_FLAG);
		return true;
	}

	private static boolean hasEmitterOrSaber(Player player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (isEmitterOrSaber(player.getInventory().getItem(slot))) {
				return true;
			}
		}

		return isEmitterOrSaber(player.containerMenu.getCarried());
	}

	private static boolean isEmitterOrSaber(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		if (stack.getItem() instanceof LightsaberItem) {
			return true;
		}

		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());

		for (Identifier emitter : EMITTERS) {
			if (emitter.equals(id)) {
				return true;
			}
		}

		return false;
	}

	/** Whether the {@code kills}-th kill drops an emitter (guaranteed from the configured kill on). */
	static boolean rollEmitter(int kills, RandomSource random) {
		EnemyBalance b = EnemyBalance.get();
		return kills >= b.emitterGuaranteeKill || random.nextFloat() < b.emitterChance;
	}

	/** One of the two emitter components, looked up by id (registered by the saber feature). */
	static ItemStack pickEmitter(RandomSource random) {
		int first = random.nextInt(EMITTERS.length);

		for (int i = 0; i < EMITTERS.length; i++) {
			Item item = BuiltInRegistries.ITEM.getOptional(EMITTERS[(first + i) % EMITTERS.length]).orElse(null);

			if (item != null) {
				return new ItemStack(item);
			}
		}

		OldRepublicMod.LOGGER.warn("No emitter items registered; poacher bad-luck protection cannot drop one");
		return ItemStack.EMPTY;
	}

	/** The player credited with the kill: the damage source's owner, else the last player who hurt it. */
	private static @Nullable ServerPlayer killer(CrystalPoacher poacher, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}

		return poacher.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
	}

	private PoacherLoot() {
	}
}

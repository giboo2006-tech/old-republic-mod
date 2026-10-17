package com.oldrepublic.progression;

import java.util.Iterator;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side progression rules: attunement XP and levels, Midi-chlorian Resonance, Force Energy,
 * Ability Mastery, cooldowns and anti-grind. All changes go through here so they are validated,
 * synced and given feedback in one place.
 */
public final class Progression {
	/** Effect event sent to the owning player when they gain a level. */
	public static final net.minecraft.resources.Identifier LEVEL_UP = OldRepublicMod.id("level_up");
	/** Effect event sent to the owning player when Resonance grows. */
	public static final net.minecraft.resources.Identifier RESONANCE_UP = OldRepublicMod.id("resonance_up");
	/** Effect event played at a player who tried to use a power without enough energy. */
	public static final net.minecraft.resources.Identifier ENERGY_FAIL = OldRepublicMod.id("energy_fail");

	private static final int ENERGY_SYNC_INTERVAL = 4;

	/**
	 * {@link ForceData#counters} keys the skills feature writes to modify Force Energy. Each is
	 * optional; an absent key changes nothing. Flat bonus to max energy (FE).
	 */
	public static final String MOD_MAX_ENERGY = "mod_max_energy";
	/** Percent bonus to max energy, applied after every flat bonus (e.g. 10 = +10%). */
	public static final String MOD_MAX_ENERGY_PCT = "mod_max_energy_pct";
	/** Percent bonus to energy regeneration per second (e.g. 20 = +20%). */
	public static final String MOD_REGEN_PCT = "mod_regen_pct";
	/** Ticks taken off the regen delay after spending energy (never below 0). */
	public static final String MOD_REGEN_DELAY = "mod_regen_delay";

	public static ProgressionBalance balance() {
		return BalanceConfig.section("progression", ProgressionBalance.class, ProgressionBalance::new);
	}

	/** The live server-side data. Call {@link #commit} after changing it. */
	public static ForceData get(ServerPlayer player) {
		ForceData data = player.getAttachedOrCreate(ForceAttachments.FORCE);

		if (data.resonance <= 0) {
			ProgressionBalance b = balance();
			// Seeded per player so the starting Resonance is stable for one character.
			long seed = player.getUUID().getLeastSignificantBits() ^ player.getUUID().getMostSignificantBits();
			int span = Math.max(1, b.resonanceStartMax - b.resonanceStartMin);
			data.resonance = b.resonanceStartMin + (int) Math.floorMod(seed, (long) span);
			data.counters.put("resonance_start", data.resonance);
			recalc(player, data);
			data.energy = data.maxEnergy;
		}

		return data;
	}

	/** Recomputes cached values and syncs the data to the owning client. */
	public static void commit(ServerPlayer player, ForceData data) {
		recalc(player, data);
		player.setAttached(ForceAttachments.FORCE, data.copy().seal());
	}

	public static long xpToNext(int level) {
		ProgressionBalance b = balance();

		if (level >= b.maxLevel) {
			return 0;
		}

		return Math.max(1, Math.round(b.curveBase * Math.pow(level, b.curveExponent) + b.curveLinear * level));
	}

	/**
	 * Recomputes max energy at once from the current {@link ForceModifiers} (armour, carried
	 * holocrons) and syncs it. Called by the equipment feature when worn armour changes.
	 */
	public static void refreshModifiers(ServerPlayer player) {
		commit(player, get(player));
	}

	private static void recalc(ServerPlayer player, ForceData data) {
		ProgressionBalance b = balance();
		data.xpToNext = xpToNext(data.level);
		int startResonance = data.counters.getOrDefault("resonance_start", data.resonance);
		float resonanceBonus = Math.max(0, data.resonance - startResonance) / 1000f * b.maxEnergyPerThousandResonance;
		float flat = b.baseMaxEnergy + b.maxEnergyPerLevel * (data.level - 1) + resonanceBonus + data.counter(MOD_MAX_ENERGY)
				+ ForceModifiers.maxEnergyBonus(player);
		data.maxEnergy = Math.max(1f, flat * (1f + data.counter(MOD_MAX_ENERGY_PCT) / 100f));
		data.energy = Mth.clamp(data.energy, 0, data.maxEnergy);
	}

	/**
	 * Grants attunement XP. {@code source} is a short tag used for logging and future tuning
	 * (e.g. {@code kill}, {@code discovery}, {@code trial}).
	 */
	public static void addXp(ServerPlayer player, long amount, String source) {
		if (amount <= 0) {
			return;
		}

		ForceData data = get(player);
		ProgressionBalance b = balance();
		data.xp += amount;
		int gained = 0;

		while (data.level < b.maxLevel && data.xp >= xpToNext(data.level)) {
			data.xp -= xpToNext(data.level);
			data.level++;
			data.skillPoints += b.skillPointsPerLevel;
			gained++;
		}

		if (data.level >= b.maxLevel) {
			data.xp = 0;
		}

		commit(player, data);

		if (gained > 0) {
			player.sendSystemMessage(Component.translatable("message.oldrepublic.level_up", data.level, b.skillPointsPerLevel * gained));
			EffectEvents.toPlayer(player, LEVEL_UP, player.position(), null, player.getId(), data.level, 0);
			QuestHooks.onLevelUp(player, data.level);
		}
	}

	/**
	 * Raises Midi-chlorian Resonance. Only major milestones should call this, each guarded by a
	 * one-shot flag so it can never be farmed.
	 */
	public static void grantResonanceMilestone(ServerPlayer player, String milestoneFlag, int amount) {
		ForceData data = get(player);

		if (!data.flags.add("resonance:" + milestoneFlag)) {
			return;
		}

		data.resonance += amount;
		commit(player, data);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.resonance_up", amount, data.resonance));
		EffectEvents.toPlayer(player, RESONANCE_UP, player.position(), null, player.getId(), amount, 0);
	}

	public static void grantSkillPoints(ServerPlayer player, int amount) {
		ForceData data = get(player);
		data.skillPoints += amount;
		commit(player, data);
	}

	/**
	 * Spends Force Energy. On failure plays the distinct "not enough energy" feedback and returns
	 * false; on success resets the regen delay.
	 */
	public static boolean trySpendEnergy(ServerPlayer player, float amount) {
		ForceData data = get(player);

		if (player.isCreative()) {
			return true;
		}

		if (data.energy + 1.0e-3f < amount) {
			EffectEvents.toTracking(player, ENERGY_FAIL, player.getEyePosition(), player.getLookAngle(), player.getId(), 1f, 0);
			return false;
		}

		data.energy -= amount;
		data.energyIdleTicks = 0;
		commit(player, data);
		return true;
	}

	public static void addMastery(ServerPlayer player, String abilityId, int amount) {
		ForceData data = get(player);
		data.mastery.merge(abilityId, amount, Integer::sum);
		commit(player, data);
	}

	/** Mastery rank 0-3 from mastery points, thresholds 50 / 150 / 400. */
	public static int masteryRank(int points) {
		return points >= 400 ? 3 : points >= 150 ? 2 : points >= 50 ? 1 : 0;
	}

	public static boolean isOnCooldown(ServerPlayer player, String id) {
		return get(player).cooldown(id) > 0;
	}

	public static void setCooldown(ServerPlayer player, String id, int ticks) {
		ForceData data = get(player);
		data.cooldowns.put(id, Math.max(0, ticks));
		commit(player, data);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}

			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		ForceData data = get(player);
		ProgressionBalance b = balance();
		boolean dirty = false;

		if (!data.cooldowns.isEmpty()) {
			Iterator<Map.Entry<String, Integer>> it = data.cooldowns.entrySet().iterator();

			while (it.hasNext()) {
				Map.Entry<String, Integer> entry = it.next();
				int next = entry.getValue() - 1;

				if (next <= 0) {
					it.remove();
					dirty = true;
				} else {
					entry.setValue(next);
				}
			}
		}

		if (!data.killFatigue.isEmpty() && player.tickCount % 20 == 0) {
			float decay = b.fatigueDecayPerMinute / 60f; // runs once per second
			data.killFatigue.replaceAll((k, v) -> v - decay);
			data.killFatigue.values().removeIf(v -> v <= 0);
		}

		if (!data.isAwakened()) {
			data.stirTicks++;

			if (data.stirTicks >= b.awakeningTicks) {
				QuestHooks.onAwakeningDue(player, "time");
			}
		} else if (data.energy < data.maxEnergy) {
			data.energyIdleTicks++;

			if (data.energyIdleTicks >= Math.max(0, b.energyRegenDelayTicks - data.counter(MOD_REGEN_DELAY))) {
				int startResonance = data.counters.getOrDefault("resonance_start", data.resonance);
				float regen = b.energyRegenPerSecond + Math.max(0, data.resonance - startResonance) / 1000f * b.energyRegenPerThousandResonance;
				regen *= Math.max(0f, 1f + data.counter(MOD_REGEN_PCT) / 100f) * ForceModifiers.regen(player);
				data.energy = Math.min(data.maxEnergy, data.energy + regen / 20f);

				if (player.tickCount % ENERGY_SYNC_INTERVAL == 0 || data.energy >= data.maxEnergy) {
					dirty = true;
				}
			}
		}

		if (dirty) {
			commit(player, data);
		}
	}

	/** Kill XP, only for a creature that counted as hostile to the killer when the killer's damage landed (5.2). */
	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || entity == player || !Attitudes.hostileSnapshot(entity, player)) {
			return;
		}

		ForceData data = get(player);

		if (!data.isAwakened()) {
			return;
		}

		ProgressionBalance b = balance();
		String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		float fatigue = data.killFatigue.getOrDefault(typeId, 0f);
		float multiplier = Math.max(b.fatigueFloor, (float) Math.pow(b.fatigueFactor, fatigue));
		float base = Math.min(b.killXpCap, entity.getMaxHealth() * b.killXpPerHealth);
		data.killFatigue.put(typeId, fatigue + 1f);
		addXp(player, Math.max(1, Math.round(base * multiplier)), "kill");
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Progression::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Progression::onDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ForceData data = get(newPlayer);
			data.energy = data.maxEnergy;
			data.cooldowns.clear();
			commit(newPlayer, data);
		});
		ServerPlayerEvents.JOIN.register(player -> commit(player, get(player)));
	}

	private Progression() {
	}
}

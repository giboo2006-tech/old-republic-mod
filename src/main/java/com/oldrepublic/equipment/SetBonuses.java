package com.oldrepublic.equipment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.equipment.blaster.PlayerBolts;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The event-driven robe set bonuses (spec section 9.1). Calm of the Watch restores Force Energy on
 * every successful parry, melee or bolt ({@link CombatEvents#PARRY}); Hunger of the Tomb restores it
 * on a kill within {@code sithSetWindowTicks} of the killer's power hitting that target
 * ({@link CombatEvents#POWER_HIT}, then {@code AFTER_DEATH}). Both pay through the shared ledger
 * {@link EnergyRefunds}, so every refund for one parry or one kill (a skill node's and the set
 * bonus's) together stays within {@code parryRefundCap} or {@code killRefundCap}. A kill by a
 * player's blaster bolt never feeds Hunger (MILESTONE_4.md 5.1). Server side.
 * <p>
 * Milestone 4 (6.2): Warden's Resolve opens its window on every parry, recorded here
 * ({@link #warden}); the other M4 bonuses are read where they apply ({@link EquipmentModifiers},
 * the jet pack) or by the power they change ({@link #forceSpeedDurationMultiplier}).
 */
public final class SetBonuses {
	/** Power hits per target: caster to game time. Pruned as entries age past the window. */
	private static final Map<UUID, Map<UUID, Long>> POWER_HITS = new HashMap<>();
	/** Kills seen before the killing power's hit was reported in the same tick: target to (killer, tick). */
	private static final Map<UUID, Kill> PENDING_KILLS = new HashMap<>();
	/** The game time of each player's last parry, for Warden's Resolve. */
	private static final Map<UUID, Long> LAST_PARRY = new HashMap<>();
	private static long lastPrune;

	private record Kill(UUID killer, long tick) {
	}

	static void init() {
		CombatEvents.PARRY.register((defender, attacker, projectile) -> {
			calm(defender);
			parried(defender);
		});
		CombatEvents.POWER_HIT.register((caster, abilityId, target) -> onPowerHit(caster, target));
		ServerLivingEntityEvents.AFTER_DEATH.register(SetBonuses::onDeath);
		ServerPlayerEvents.LEAVE.register(player -> LAST_PARRY.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POWER_HITS.clear();
			PENDING_KILLS.clear();
			LAST_PARRY.clear();
			lastPrune = 0;
		});
	}

	/**
	 * Calm of the Watch: restores {@code jediSetParryFe} when the defender's Jedi robe set bonus is
	 * active, within what the per-parry cap ({@code parryRefundCap}) leaves for this tick's parry.
	 * Returns the Force Energy restored.
	 */
	public static float calm(ServerPlayer defender) {
		EquipmentBalance b = EquipmentBalance.get();

		if (!SetBonusRules.active(ArmourSet.JEDI_ROBES, ArmourSets.worn(defender), ArmourSets.wearer(defender), b)) {
			return 0f;
		}

		return EnergyRefunds.forParry(defender, defender.level().getGameTime(), b.jediSetParryFe);
	}

	/**
	 * Hunger of the Tomb: restores {@code sithSetKillFe} when the killer's Sith robe set bonus is
	 * active and its power hit the victim within the window, within what the per-kill cap
	 * ({@code killRefundCap}) leaves for this victim. Returns the Force Energy restored.
	 */
	public static float hunger(ServerPlayer killer, LivingEntity victim) {
		EquipmentBalance b = EquipmentBalance.get();
		Map<UUID, Long> hits = POWER_HITS.get(victim.getUUID());
		Long hit = hits == null ? null : hits.get(killer.getUUID());

		if (hit == null || !SetBonusRules.hungerWindow(hit, killer.level().getGameTime(), b)) {
			return 0f;
		}

		POWER_HITS.remove(victim.getUUID());

		if (!SetBonusRules.active(ArmourSet.SITH_ROBES, ArmourSets.worn(killer), ArmourSets.wearer(killer), b)) {
			return 0f;
		}

		return EnergyRefunds.forKill(killer, victim, b.sithSetKillFe);
	}

	/** Warden's Resolve: whether {@code e} wears the active Watch battle set and parried within {@code watchSetTicks}. */
	public static boolean warden(LivingEntity e) {
		Long parry = LAST_PARRY.get(e.getUUID());
		return parry != null && SetBonusRules.warden(ArmourSets.worn(e), ArmourSets.wearer(e), parry, e.level().getGameTime(),
				EquipmentBalance.get(), EquipmentM4Balance.get());
	}

	/** Wrath Unbound: whether {@code e} wears the active Sith war set and is below the health threshold. */
	public static boolean wrath(LivingEntity e) {
		return SetBonusRules.wrath(ArmourSets.worn(e), ArmourSets.wearer(e), e.getHealth(), e.getMaxHealth(), EquipmentBalance.get(), EquipmentM4Balance.get());
	}

	/**
	 * Light on Your Feet: the multiplier the Force Speed power puts on its duration for {@code e}
	 * (1.3 with the full spacer's gear, else 1). Server side; W6's {@code force_speed} reads it.
	 */
	public static float forceSpeedDurationMultiplier(LivingEntity e) {
		return SetBonusRules.speedDuration(ArmourSets.worn(e), EquipmentM4Balance.get());
	}

	/** Records a parry for Warden's Resolve (from {@link CombatEvents#PARRY}; tests call it directly). */
	public static void parried(ServerPlayer defender) {
		LAST_PARRY.put(defender.getUUID(), defender.level().getGameTime());
	}

	private static void onPowerHit(ServerPlayer caster, LivingEntity target) {
		long now = caster.level().getGameTime();
		prune(now);
		POWER_HITS.computeIfAbsent(target.getUUID(), k -> new HashMap<>()).put(caster.getUUID(), now);
		Kill kill = PENDING_KILLS.remove(target.getUUID());

		// The killing blow was this power, reported after the death.
		if (kill != null && kill.killer().equals(caster.getUUID()) && kill.tick() == now) {
			hunger(caster, target);
		}
	}

	private static void onDeath(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || PlayerBolts.isPlayerBlaster(source)) {
			POWER_HITS.remove(victim.getUUID());
			return;
		}

		long now = killer.level().getGameTime();
		prune(now);
		Map<UUID, Long> hits = POWER_HITS.get(victim.getUUID());

		if (hits != null && hits.containsKey(killer.getUUID())) {
			hunger(killer, victim);
		} else {
			PENDING_KILLS.put(victim.getUUID(), new Kill(killer.getUUID(), now));
		}
	}

	/** Drops power hits older than the window and pending kills older than a tick, at most once a second. */
	private static void prune(long now) {
		if (now - lastPrune < 20 && now >= lastPrune) {
			return;
		}

		lastPrune = now;
		long window = EquipmentBalance.get().sithSetWindowTicks;

		for (Iterator<Map<UUID, Long>> targets = POWER_HITS.values().iterator(); targets.hasNext();) {
			Map<UUID, Long> hits = targets.next();
			hits.values().removeIf(tick -> now - tick > window || tick > now);

			if (hits.isEmpty()) {
				targets.remove();
			}
		}

		PENDING_KILLS.values().removeIf(kill -> kill.tick() != now);
	}

	private SetBonuses() {
	}
}

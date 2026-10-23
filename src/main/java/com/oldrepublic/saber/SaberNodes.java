package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The four holocron and rank nodes the saber feature implements (spec 10.4). Each is granted only
 * (W3 adds them to the Jedi and Sith trees) and active per the M2 dormancy rules, which
 * {@link Skills#active} applies. The rules are static and take the "is the node active" test as a
 * predicate, so tests can run them without the tree data.
 * <ul>
 * <li>{@code watchers_insight}: a parry, melee or bolt, restores {@code watchersInsightFe} Force
 * Energy; with every other refund for the same parry, at most {@code progression.parryRefundCap}.</li>
 * <li>{@code archivists_ward}: the parry window is {@code archivistsWardTicks} longer (a flat bonus,
 * before the form multiplier and the cap, see {@link SaberStats#withForm}).</li>
 * <li>{@code knights_vigil}: while the Knight guards with a lit saber, allies within
 * {@code knightsVigilRange} take {@code knightsVigilReduction} less damage.</li>
 * <li>{@code lords_dominion}: after the Lord lands a heavy attack, non-boss enemies within
 * {@code lordsDominionRange} deal {@code lordsDominionReduction} less damage for
 * {@code lordsDominionTicks}.</li>
 * </ul>
 * Server side.
 */
public final class SaberNodes {
	public static final String WATCHERS_INSIGHT = "watchers_insight";
	public static final String ARCHIVISTS_WARD = "archivists_ward";
	public static final String KNIGHTS_VIGIL = "knights_vigil";
	public static final String LORDS_DOMINION = "lords_dominion";

	/** The M2 technique id of a heavy attack ({@code QuestHooks.onSaberTechnique}). */
	private static final String HEAVY = "heavy";

	/** Enemies under a Lord's dominion: entity UUID to the game tick it ends. */
	private static final Map<UUID, Long> DOMINATED = new HashMap<>();
	/** Last tick each player was refunded for a parry: one refund per parry moment. */
	private static final Map<UUID, Long> PARRY_REFUNDED = new HashMap<>();

	static void init() {
		CombatEvents.PARRY.register((defender, attacker, projectile) -> insightRefund(defender, p -> Skills.active(p, WATCHERS_INSIGHT), SaberToggle.balance()));
		CombatEvents.TECHNIQUE.register((p, technique, target, hit) -> {
			if (HEAVY.equals(technique) && hit) {
				applyDominion(p, lord -> Skills.active(lord, LORDS_DOMINION), SaberToggle.balance());
			}
		});
		DamageModifiers.register(SaberNodes::modify);
	}

	// ---------------------------------------------------------------- archivists_ward

	/** Extra parry-window ticks from {@code archivists_ward} (0 when not active). */
	public static float wardTicks(Player player, SaberBalance b) {
		return Skills.active(player, ARCHIVISTS_WARD) ? b.archivistsWardTicks : 0f;
	}

	// ---------------------------------------------------------------- watchers_insight

	/**
	 * {@code watchers_insight} for one parry: restores {@code watchersInsightFe} through the shared
	 * ledger ({@link EnergyRefunds#forParry}), so it and every other refund for the same parry (Calm of
	 * the Watch) together give at most {@code progression.parryRefundCap}. Once per parry moment: a
	 * second parry event in the same tick restores nothing. Returns the energy restored.
	 */
	public static float insightRefund(ServerPlayer player, Predicate<Player> active, SaberBalance b) {
		if (!active.test(player) || !player.isAlive()) {
			return 0f;
		}

		long now = player.level().getGameTime();
		Long last = PARRY_REFUNDED.get(player.getUUID());

		if (last != null && last == now) {
			return 0f;
		}

		PARRY_REFUNDED.put(player.getUUID(), now);
		return EnergyRefunds.forParry(player, now, b.watchersInsightFe);
	}

	// ---------------------------------------------------------------- knights_vigil

	/**
	 * The {@code knights_vigil} multiplier for damage {@code victim} takes: {@code 1 - knightsVigilReduction}
	 * when a player for whom {@code active} holds is {@code guarding} within {@code knightsVigilRange}
	 * of an ally ({@link #isAlly}: never a PvP opponent), else 1. Several Knights do not stack.
	 */
	public static float vigilMultiplier(LivingEntity victim, Iterable<? extends Player> players, Predicate<Player> active,
			Predicate<Player> guarding, SaberBalance b) {
		double range = b.knightsVigilRange;

		for (Player knight : players) {
			if (knight == victim || !knight.isAlive() || knight.level() != victim.level() || knight.distanceToSqr(victim) > range * range) {
				continue;
			}

			if (isAlly(victim, knight) && active.test(knight) && guarding.test(knight)) {
				return 1f - b.knightsVigilReduction;
			}
		}

		return 1f;
	}

	/**
	 * A creature allied to {@code knight}, or another player who is not his foe: one he cannot harm
	 * (PvP off, or a team without friendly fire), a teammate when both are on teams, and otherwise any
	 * player he is not fighting (neither has hurt the other lately). A PvP opponent is never covered.
	 */
	static boolean isAlly(LivingEntity victim, Player knight) {
		if (victim == knight) {
			return false;
		}

		if (!(victim instanceof Player other)) {
			return Attitudes.toward(victim, knight) == Attitude.ALLIED;
		}

		if (!knight.canHarmPlayer(other)) {
			return true;
		}

		if (knight.getTeam() != null && other.getTeam() != null) {
			return knight.isAlliedTo(other);
		}

		return knight.getLastHurtByMob() != other && other.getLastHurtByMob() != knight;
	}

	// ---------------------------------------------------------------- lords_dominion

	/**
	 * A heavy attack landed: when {@code active} holds for {@code lord}, every non-boss enemy within
	 * {@code lordsDominionRange} deals less damage for {@code lordsDominionTicks}. Returns how many
	 * came under the dominion.
	 */
	public static int applyDominion(ServerPlayer lord, Predicate<Player> active, SaberBalance b) {
		if (!active.test(lord)) {
			return 0;
		}

		ServerLevel level = lord.level();
		long now = level.getGameTime();
		long until = now + b.lordsDominionTicks;
		DOMINATED.values().removeIf(end -> end <= now);
		double range = b.lordsDominionRange;
		int count = 0;

		for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, lord.getBoundingBox().inflate(range))) {
			if (enemy == lord || enemy instanceof Player || !enemy.isAlive() || enemy.distanceToSqr(lord) > range * range
					|| enemy.is(ConventionalEntityTypeTags.BOSSES) || !Attitudes.countsAsHostile(lord, enemy)) {
				continue;
			}

			DOMINATED.merge(enemy.getUUID(), until, Math::max);
			count++;
		}

		return count;
	}

	/** The {@code lords_dominion} multiplier for damage dealt by {@code attacker} at {@code now}. */
	public static float dominionMultiplier(@Nullable Entity attacker, long now, SaberBalance b) {
		if (attacker == null) {
			return 1f;
		}

		Long until = DOMINATED.get(attacker.getUUID());

		if (until == null) {
			return 1f;
		}

		if (now >= until) {
			DOMINATED.remove(attacker.getUUID());
			return 1f;
		}

		return 1f - b.lordsDominionReduction;
	}

	/** Whether {@code e} is under a Lord's dominion at {@code now}. */
	public static boolean isDominated(Entity e, long now) {
		Long until = DOMINATED.get(e.getUUID());
		return until != null && now < until;
	}

	/** Lifts the dominion from {@code e} (tests, death). */
	public static void clearDominion(Entity e) {
		DOMINATED.remove(e.getUUID());
	}

	// ---------------------------------------------------------------- the damage modifier

	private static float modify(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return amount;
		}

		SaberBalance b = SaberToggle.balance();
		float mult = dominionMultiplier(source.getEntity(), level.getGameTime(), b);
		mult *= vigilMultiplier(victim, level.players(), p -> Skills.active(p, KNIGHTS_VIGIL), p -> !SaberGuard.guardingSaber(p).isEmpty(), b);
		return amount * mult;
	}

	/** Forgets a player's parry-refund record (disconnect, and tests that refund a parry). */
	public static void forget(Player player) {
		PARRY_REFUNDED.remove(player.getUUID());
	}

	private SaberNodes() {
	}
}

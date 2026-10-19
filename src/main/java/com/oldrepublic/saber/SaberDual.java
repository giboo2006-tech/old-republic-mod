package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Dual-wielding (spec MILESTONE_5.md 10.2): a lightsaber in each hand, two single sabers (never a
 * saberstaff; training sabers count, for spars).
 *
 * <p>The off hand strikes through its own payload ({@code offhand_strike}), because the vanilla
 * attack packet has no hand and the server checks melee reach for the main hand only. The off hand
 * keeps its own recovery clock: the off saber's attack speed, scaled by whatever else changes the
 * player's attack speed (the form, effects), and {@code offhandSpeed} slower unless the form is
 * Jar'Kai. An off-hand strike deals the off saber's damage (its own parts, length and tunings)
 * times {@code offhandDamage}, again unless the form is Jar'Kai, through {@link SaberCombat} as an
 * {@link SaberCombat.Technique#OFFHAND} strike, so the guards, spars and the striking saber's lens
 * all see which hand struck. Finishers, heavies, lunges and Saber Throw stay with the main hand.
 * The tests and the payload share {@link #strike}. Both sides compute {@link #offhandDelay}; the
 * clock and every decision are the server's.
 */
public final class SaberDual {
	/** What an off-hand strike request came to. */
	public enum Result {
		/** The blow landed (or was blocked by the target's guard: the damage call happened). */
		STRUCK,
		/** A swing at nothing: the off hand swung and its recovery started. */
		SWUNG,
		/** Not two lit single sabers in hand, or dead, or a spectator. */
		NOT_DUAL,
		/** A technique, a form change or a guard is running. */
		BUSY,
		/** The off hand is still recovering. */
		NOT_READY,
		/** The target is beyond the off saber's reach. */
		OUT_OF_REACH,
		/** No such entity, not alive, not attackable, the striker itself, or out of sight. */
		BAD_TARGET
	}

	/** Ticks the client's off-hand clock may run ahead of the server's. */
	public static final int READY_SLACK = 1;
	/**
	 * Extra blocks allowed beyond the off saber's reach for movement between the client's pick and
	 * the server's check (vanilla allows the main hand 3).
	 */
	public static final double REACH_TOLERANCE = 1.0;
	/** Game time at which each player's off hand is ready again. */
	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	/** Players whose first off-hand strike has unlocked the codex entry this session. */
	private static final Set<UUID> CODEX_SEEN = new HashSet<>();

	/** Whether {@code stack} is a single-bladed saber: a lightsaber or a training saber, not a saberstaff. */
	public static boolean isSingleSaber(ItemStack stack) {
		return stack.getItem() instanceof LightsaberItem && !(stack.getItem() instanceof SaberStaffItem);
	}

	/** Whether {@code player} holds a single saber in each hand, lit or not. */
	public static boolean holdsTwo(Player player) {
		return isSingleSaber(player.getMainHandItem()) && isSingleSaber(player.getOffhandItem());
	}

	/**
	 * Whether {@code player} dual-wields: two single sabers in hand, both lit and neither thrown. This is
	 * what Jar'Kai, the two-blade guard bonus and the off-hand strike need (10.1, 10.2).
	 */
	public static boolean isDualWielding(Player player) {
		return holdsTwo(player) && SaberComponents.isUsable(player.getMainHandItem()) && SaberComponents.isUsable(player.getOffhandItem());
	}

	/** Whether {@code player}'s form takes the off-hand penalty (every form but Jar'Kai, 10.1). */
	public static boolean penalised(Player player, SaberBalance b) {
		return SaberPassives.form(player).modifiers(b).traits().offhandPenalty();
	}

	/**
	 * The off hand's recovery in ticks: 20 over the off saber's attack speed, where that speed is the
	 * saber's own scaled by the ratio of the player's attack speed to the main saber's (so the form's
	 * and any effect's changes apply to both hands), then {@code offhandSpeed} longer unless Jar'Kai.
	 */
	public static int offhandDelay(Player player, SaberBalance b) {
		double mainNominal = SaberStats.of(player.getMainHandItem(), b).attackSpeed();
		double ratio = mainNominal > 0 ? player.getAttributeValue(Attributes.ATTACK_SPEED) / mainNominal : 1.0;
		return offhandTicks(SaberStats.of(player.getOffhandItem(), b).attackSpeed(), ratio, penalised(player, b), b);
	}

	/**
	 * The rule behind {@link #offhandDelay} (pure, for the tests): 20 ticks over the off saber's
	 * {@code offSpeed} times the player's speed {@code ratio}, {@code offhandSpeed} longer when
	 * {@code penalised}; at least 1 tick.
	 */
	public static int offhandTicks(double offSpeed, double ratio, boolean penalised, SaberBalance b) {
		double speed = Math.max(0.1, offSpeed * Math.max(0.1, ratio));
		double ticks = 20.0 / speed * (penalised ? 1.0 + b.offhandSpeed : 1.0);
		return Math.max(1, (int) Math.round(ticks));
	}

	/**
	 * The off-hand strike's damage before the pipeline: the off saber's ignited damage plus whatever
	 * the player's attack damage carries beyond the main saber's (effects, buffs), times
	 * {@code offhandDamage} unless the form is Jar'Kai.
	 */
	public static float offhandDamage(Player player, SaberBalance b) {
		double main = SaberStats.of(player.getMainHandItem(), b).damage();
		double extra = Math.max(0.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE) - main);
		return offhandDamage(SaberStats.of(player.getOffhandItem(), b).damage(), extra, penalised(player, b), b);
	}

	/**
	 * The rule behind {@link #offhandDamage(Player, SaberBalance)} (pure, for the tests): the off
	 * saber's {@code offDamage} plus the player's {@code extra}, times {@code offhandDamage} when
	 * {@code penalised}.
	 */
	public static float offhandDamage(double offDamage, double extra, boolean penalised, SaberBalance b) {
		return (float) ((offDamage + extra) * (penalised ? b.offhandDamage : 1.0));
	}

	/** Whether {@code player}'s off hand is ready (with the server's slack). */
	public static boolean offhandReady(Player player) {
		return player.level().getGameTime() + READY_SLACK >= READY_AT.getOrDefault(player.getUUID(), Long.MIN_VALUE);
	}

	/**
	 * The {@code offhand_strike} payload: checks the dual wield, that nothing else is running, the off
	 * hand's clock, the target (alive, attackable, in sight) and the off saber's reach
	 * ({@code Player#isWithinAttackRange} with the off saber, which reads its {@code attack_range}),
	 * then deals the blow. A swing at nothing ({@code targetId} -1) still swings and starts the clock.
	 */
	public static Result strike(ServerPlayer player, int targetId) {
		if (player.isSpectator() || !player.isAlive() || !isDualWielding(player)) {
			return Result.NOT_DUAL;
		}

		if (player.isUsingItem() || SaberTechniques.isBusy(player) || SaberForms.isSwitching(player)) {
			return Result.BUSY;
		}

		if (!offhandReady(player)) {
			return Result.NOT_READY;
		}

		SaberBalance b = SaberToggle.balance();
		ServerLevel level = player.level();
		ItemStack off = player.getOffhandItem();

		if (targetId < 0) {
			swing(player, b, false);
			return Result.SWUNG;
		}

		Entity entity = level.getEntity(targetId);

		if (!(entity instanceof LivingEntity target) || target == player || !target.isAlive() || target.isSpectator() || !target.isAttackable()
				|| !player.hasLineOfSight(target)) {
			return Result.BAD_TARGET;
		}

		if (!player.isWithinAttackRange(off, target.getBoundingBox(), REACH_TOLERANCE)) {
			return Result.OUT_OF_REACH;
		}

		swing(player, b, true);
		float damage = offhandDamage(player, b);
		SaberCombat.strike(target, level, player.damageSources().playerAttack(player), damage, SaberCombat.Strike.OFFHAND);
		player.setLastHurtMob(target);
		player.causeFoodExhaustion(0.1f);

		if (CODEX_SEEN.add(player.getUUID())) {
			Journal.unlockCodex(player, JournalEntries.CODEX_DUAL_BLADES);
		}

		return Result.STRUCK;
	}

	/**
	 * Starts the off hand's recovery and swings it: the swing fields are set directly (vanilla's
	 * {@code swing} refuses a new swing in the first half of the last one), and the
	 * {@code offhand_strike} effect shows the swing, its arc and its sound to everyone tracking.
	 */
	private static void swing(ServerPlayer player, SaberBalance b, boolean hit) {
		READY_AT.put(player.getUUID(), player.level().getGameTime() + offhandDelay(player, b));
		player.swingTime = -1;
		player.swinging = true;
		player.swingingArm = InteractionHand.OFF_HAND;
		EffectEvents.toTracking(player, SaberEffects.OFFHAND_STRIKE, player.getEyePosition(), player.getLookAngle(), player.getId(), hit ? 1f : 0f, 0);
	}

	/** Makes the off hand ready now (tests). */
	public static void resetClock(Player player) {
		READY_AT.remove(player.getUUID());
	}

	static void forget(Player player) {
		READY_AT.remove(player.getUUID());
	}

	private SaberDual() {
	}
}

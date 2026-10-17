package com.oldrepublic.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Force Energy given back for kills and parries (spec 9.1). Several sources may pay for the same
 * moment ({@code hungering_dark} and {@code tomb_hunger} for one kill, a set bonus and
 * {@code watchers_insight} for one parry); together they restore at most
 * {@link ProgressionBalance#killRefundCap} per kill and {@link ProgressionBalance#parryRefundCap}
 * per parry. Every refund source goes through here. Server side.
 */
public final class EnergyRefunds {
	/** One refunded moment: a kill (victim UUID) or a parry (game tick) of one player. */
	private record Moment(UUID player, Object key) {
	}

	/** What was already restored for a moment, and when it was first paid. */
	private record Paid(long tick, float amount) {
	}

	/** A moment is forgotten this long after its first refund; every source pays within a tick or two. */
	private static final int FORGET_TICKS = 100;
	private static final Map<Moment, Paid> PAID = new HashMap<>();

	/**
	 * Restores up to {@code amount} Force Energy for {@code p}'s kill of {@code victim}. All refunds
	 * for the same kill together restore at most {@code progression.killRefundCap}. Returns the
	 * energy actually restored.
	 */
	public static float forKill(ServerPlayer p, Entity victim, float amount) {
		return refund(p, victim.getUUID(), amount, Progression.balance().killRefundCap);
	}

	/** The same for one parry ({@code parryTick}: the game tick of the parry), capped at {@code parryRefundCap}. */
	public static float forParry(ServerPlayer p, long parryTick, float amount) {
		return refund(p, parryTick, amount, Progression.balance().parryRefundCap);
	}

	/** Pure: how much of {@code amount} still fits under {@code cap} when {@code already} was given. */
	public static float fit(float already, float amount, float cap) {
		return amount <= 0f ? 0f : Math.max(0f, Math.min(amount, cap - already));
	}

	private static float refund(ServerPlayer p, Object key, float amount, float cap) {
		long now = p.level().getGameTime();
		PAID.values().removeIf(paid -> now - paid.tick() > FORGET_TICKS || paid.tick() > now);
		Moment moment = new Moment(p.getUUID(), key);
		Paid paid = PAID.get(moment);
		float allowed = fit(paid == null ? 0f : paid.amount(), amount, cap);

		if (allowed <= 0f || !p.isAlive()) {
			return 0f;
		}

		ForceData data = Progression.get(p);
		float restored = Math.min(allowed, Math.max(0f, data.maxEnergy - data.energy));

		if (restored <= 0f) {
			return 0f;
		}

		data.energy += restored;
		Progression.commit(p, data);
		PAID.put(moment, new Paid(paid == null ? now : paid.tick(), (paid == null ? 0f : paid.amount()) + restored));
		return restored;
	}

	/** Forgets a player's moments (disconnect). */
	public static void forget(ServerPlayer p) {
		PAID.keySet().removeIf(moment -> moment.player().equals(p.getUUID()));
	}

	/** Forgets everything (server stop). */
	public static void clear() {
		PAID.clear();
	}

	private EnergyRefunds() {
	}
}

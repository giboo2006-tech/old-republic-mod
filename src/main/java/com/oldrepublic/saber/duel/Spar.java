package com.oldrepublic.saber.duel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberToggle;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Duels with no death risk (lessons, trials, daily sessions; spec section 9.6). Sessions live in
 * memory only. The damage rules run inside {@link SaberCombat}'s pipeline: a partner hit that would
 * take the player below {@code sparPlayerLoseFraction} is cancelled and the spar is lost; a player
 * hit that would take the partner below its yield fraction is cancelled and the spar is won (for
 * every goal, so the partner never dies in a spar either); damage from any third entity to either
 * side interrupts it. Leaving the arena forfeits (lost). Death, logout, a dimension change and a
 * server stop end it without a result.
 */
public final class Spar {
	private static final class Session {
		final UUID player;
		final Mob partner;
		final SparRules rules;
		final Consumer<SparResult> onEnd;
		final Vec3 origin;
		final ResourceKey<Level> dimension;
		int progress;

		Session(ServerPlayer player, Mob partner, SparRules rules, Consumer<SparResult> onEnd) {
			this.player = player.getUUID();
			this.partner = partner;
			this.rules = rules;
			this.onEnd = onEnd;
			this.origin = player.position();
			this.dimension = player.level().dimension();
		}
	}

	private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Session> BY_PARTNER = new HashMap<>();

	/**
	 * Starts a spar between the player and {@code partner}. {@code onEnd} runs exactly once with the
	 * result.
	 *
	 * @return false if the spar could not start (either side already sparring, partner invalid)
	 */
	public static boolean start(ServerPlayer p, Mob partner, SparRules rules, Consumer<SparResult> onEnd) {
		if (!p.isAlive() || p.isSpectator() || !partner.isAlive() || partner.level() != p.level()
				|| BY_PLAYER.containsKey(p.getUUID()) || BY_PARTNER.containsKey(partner.getUUID())) {
			return false;
		}

		Session session = new Session(p, partner, rules, onEnd);
		BY_PLAYER.put(session.player, session);
		BY_PARTNER.put(partner.getUUID(), session);
		partner.setTarget(p);
		EffectEvents.toTracking(partner, SaberEffects.SPAR, partner.position(), null, partner.getId(), 1f, SaberEffects.SPAR_BEGIN);
		p.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.spar_begin", partner.getDisplayName()));
		return true;
	}

	public static boolean inSpar(Player p) {
		return BY_PLAYER.containsKey(p.getUUID());
	}

	/** Whether {@code entity} is a spar partner right now (mentors take damage only then). */
	public static boolean isPartner(Entity entity) {
		return BY_PARTNER.containsKey(entity.getUUID());
	}

	/** The player's spar partner, or null. */
	public static @Nullable Mob partner(Player p) {
		Session session = BY_PLAYER.get(p.getUUID());
		return session == null ? null : session.partner;
	}

	/** Goal progress (parries or technique hits so far) of the player's spar, or 0. */
	public static int progress(Player p) {
		Session session = BY_PLAYER.get(p.getUUID());
		return session == null ? 0 : session.progress;
	}

	/**
	 * The spar rules for one hit, from {@link SaberCombat}'s pipeline. Returns the damage that goes
	 * through, or -1 to cancel the hit.
	 */
	public static float onDamage(LivingEntity target, DamageSource source, float amount, SaberCombat.@Nullable Strike strike) {
		Session session = target instanceof Player ? BY_PLAYER.get(target.getUUID()) : BY_PARTNER.get(target.getUUID());

		if (session == null || amount <= 0f) {
			return amount;
		}

		boolean victimIsPlayer = target.getUUID().equals(session.player);
		Entity attacker = source.getEntity();
		boolean fromCounterpart = attacker != null && (victimIsPlayer ? attacker == session.partner : attacker.getUUID().equals(session.player));

		if (attacker != null && attacker != target && !fromCounterpart) {
			end(session, SparResult.INTERRUPTED);
			return amount;
		}

		if (!fromCounterpart) {
			return amount;
		}

		SaberBalance b = SaberToggle.balance();

		if (victimIsPlayer) {
			float scaled = amount * session.rules.partnerDamageScale();

			if (target.getHealth() - scaled < target.getMaxHealth() * b.sparPlayerLoseFraction) {
				end(session, SparResult.LOST);
				return -1f;
			}

			return scaled;
		}

		if (target.getHealth() - amount < target.getMaxHealth() * session.rules.partnerYieldFraction()) {
			end(session, SparResult.WON);
			return -1f;
		}

		boolean technique = strike != null && (strike.technique() == SaberCombat.Technique.HEAVY || strike.technique() == SaberCombat.Technique.LUNGE);

		if (technique && session.rules.goal() == SparRules.Goal.TECHNIQUE_HITS && ++session.progress >= session.rules.count()) {
			end(session, SparResult.WON);
		}

		return amount;
	}

	/** The player parried {@code attacker}: counts toward a {@code PARRIES} goal. */
	public static void onParry(ServerPlayer player, LivingEntity attacker) {
		Session session = BY_PLAYER.get(player.getUUID());

		if (session != null && attacker == session.partner && session.rules.goal() == SparRules.Goal.PARRIES
				&& ++session.progress >= session.rules.count()) {
			end(session, SparResult.WON);
		}
	}

	/** Arena, partner and player checks, once per server tick. */
	public static void tick(MinecraftServer server) {
		if (BY_PLAYER.isEmpty()) {
			return;
		}

		float radius = SaberToggle.balance().sparArenaRadius;

		for (Session session : new ArrayList<>(BY_PLAYER.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(session.player);

			if (player == null || !player.isAlive() || !session.partner.isAlive() || session.partner.isRemoved()
					|| player.level().dimension() != session.dimension || session.partner.level() != player.level()) {
				end(session, SparResult.ENDED);
			} else if (player.position().distanceToSqr(session.origin) > radius * radius) {
				end(session, SparResult.LOST);
			} else if (session.partner.getTarget() != player) {
				session.partner.setTarget(player);
			}
		}
	}

	/** Ends the spar of a player or partner without a result (death, logout, dimension change). */
	public static void endWithoutResult(Entity entity) {
		Session session = entity instanceof Player ? BY_PLAYER.get(entity.getUUID()) : BY_PARTNER.get(entity.getUUID());

		if (session != null) {
			end(session, SparResult.ENDED);
		}
	}

	/** Server stop: every spar ends without a result. */
	public static void endAll() {
		List<Session> sessions = new ArrayList<>(BY_PLAYER.values());

		for (Session session : sessions) {
			end(session, SparResult.ENDED);
		}
	}

	private static void end(Session session, SparResult result) {
		if (BY_PLAYER.remove(session.player) == null) {
			return;
		}

		BY_PARTNER.remove(session.partner.getUUID());
		Mob partner = session.partner;
		partner.setTarget(null);
		partner.getNavigation().stop();
		MinecraftServer server = partner.level().getServer();
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(session.player);
		int variant = switch (result) {
			case WON -> SaberEffects.SPAR_WON;
			case LOST -> SaberEffects.SPAR_LOST;
			case INTERRUPTED, ENDED -> SaberEffects.SPAR_OVER;
		};

		if (partner.isAlive()) {
			EffectEvents.toTracking(partner, SaberEffects.SPAR, partner.position(), null, partner.getId(), 1f, variant);
		}

		if (player != null) {
			if (partner instanceof SaberDuelist duelist) {
				duelist.onSparEnd(player, result);
			}

			if (result != SparResult.ENDED) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.spar_" + result.name().toLowerCase(Locale.ROOT),
						partner.getDisplayName()));
			}
		}

		try {
			session.onEnd.accept(result);
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.error("Spar end callback failed", e);
		}
	}

	private Spar() {
	}
}

package com.oldrepublic.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The yield mechanic for {@link Yieldable} entities (the Poacher Captain, rivals). A player's hit
 * that would take one below its yield fraction is clamped to the threshold and the entity yields:
 * it kneels, lowers its weapon and stops fighting (the entity handles its pose and goals). While
 * yielded it ignores everything except the deciding player's melee: one strike warns "Strike again
 * to execute", a second within the confirm window executes. Using it opens the Spare / Execute
 * dialogue. If the decider walks away or time runs out, it flees.
 */
public final class Yielding {
	/** The Spare / Execute dialogue ({@code data/oldrepublic/dialogue/yield.json}). */
	public static final Identifier DIALOGUE = OldRepublicMod.id("yield");

	private static final Map<UUID, YieldState> TRACKED = new HashMap<>();
	/** Entities being executed right now: their death blow must pass the yielded invulnerability. */
	private static final Set<UUID> EXECUTING = new HashSet<>();
	/** Entities already resolved this session, so a reloaded entity is never decided twice. */
	private static final Set<UUID> RESOLVED = new HashSet<>();

	/** Fabric ALLOW_DAMAGE: false cancels the damage. */
	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof Yieldable yieldable) || EXECUTING.contains(entity.getUUID()) || !(entity.level() instanceof ServerLevel)) {
			return true;
		}

		ServerPlayer attacker = source.getEntity() instanceof ServerPlayer player ? player : null;

		if (yieldable.isYielded()) {
			onAttackWhileYielded(entity, yieldable, source, attacker);
			return false;
		}

		return attacker == null || !yieldable.canYield() || !tryYield(entity, yieldable, attacker, amount);
	}

	/**
	 * If {@code amount} would take {@code entity} below its yield threshold, clamps its health to
	 * exactly the threshold and puts it into the yielded state.
	 *
	 * @param yieldable usually the entity itself; separate so tests can drive any living entity
	 * @return true when the entity yielded (the damage is then consumed)
	 */
	public static boolean tryYield(LivingEntity entity, Yieldable yieldable, ServerPlayer attacker, float amount) {
		float threshold = entity.getMaxHealth() * yieldable.yieldHealthFraction();

		if (entity.getHealth() - amount > threshold || RESOLVED.contains(entity.getUUID())) {
			return false;
		}

		if (entity.getHealth() > threshold) {
			entity.setHealth(threshold);
		}

		yieldable.setYielded(true);
		track(entity);
		EffectEvents.toTracking(entity, IdentityEffects.YIELD, entity.position(), null, entity.getId(), 1f, 0);

		if (mayDecide(yieldable, attacker)) {
			attacker.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.yield_prompt", entity.getDisplayName()));
		}

		return true;
	}

	/**
	 * Puts a yieldable entity into the yielded state at its current health (the debug command).
	 *
	 * @return false when it is not yieldable, already yielded or already resolved
	 */
	public static boolean forceYield(LivingEntity entity) {
		if (!(entity instanceof Yieldable yieldable) || yieldable.isYielded() || RESOLVED.contains(entity.getUUID())) {
			return false;
		}

		yieldable.setYielded(true);
		track(entity);
		EffectEvents.toTracking(entity, IdentityEffects.YIELD, entity.position(), null, entity.getId(), 1f, 0);
		return true;
	}

	/** Whether this player decides the yielded entity's fate. */
	public static boolean mayDecide(Yieldable yieldable, Player player) {
		UUID decider = yieldable.yieldDecider();
		return decider == null || decider.equals(player.getUUID());
	}

	/** Whether the entity is yielded and waiting for a decision on this server. */
	public static boolean isPending(Entity entity) {
		return TRACKED.containsKey(entity.getUUID());
	}

	private static void track(LivingEntity entity) {
		TRACKED.put(entity.getUUID(), new YieldState(entity.getUUID(), entity.level().dimension(), entity.level().getGameTime()));
	}

	private static void onAttackWhileYielded(LivingEntity entity, Yieldable yieldable, DamageSource source, @Nullable ServerPlayer attacker) {
		if (attacker == null) {
			return;
		}

		YieldState state = TRACKED.get(entity.getUUID());
		boolean melee = source.getDirectEntity() == attacker;

		if (state == null || !melee || !mayDecide(yieldable, attacker)) {
			// Hurting someone who has surrendered, other than a deliberate execution.
			Identity.contextual(attacker, ContextAct.CRUELTY, entity);
			return;
		}

		if (state.strike(attacker.getUUID(), entity.level().getGameTime(), Identity.balance().executeConfirmTicks)) {
			resolve(entity, YieldOutcome.EXECUTED, attacker);
		} else {
			attacker.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.strike_again"));
		}
	}

	/**
	 * Resolves a yielded entity: tells the entity (it leaves, flees or is executed), the quest
	 * feature and, for an execution, deals the killing blow credited to the decider. Does nothing
	 * if it is not pending.
	 *
	 * @param decider the player who decided, or null when it fled with nobody around
	 */
	public static void resolve(LivingEntity entity, YieldOutcome outcome, @Nullable ServerPlayer decider) {
		if (TRACKED.remove(entity.getUUID()) == null || !(entity instanceof Yieldable yieldable) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		RESOLVED.add(entity.getUUID());
		yieldable.onYieldResolved(outcome);

		if (decider != null) {
			QuestHooks.onYieldResolved(decider, entity, outcome);
		}

		if (outcome == YieldOutcome.EXECUTED) {
			EXECUTING.add(entity.getUUID());

			try {
				// API-CHECK: DamageSources#generic().
				DamageSource blow = decider != null ? decider.damageSources().playerAttack(decider) : level.damageSources().generic();
				entity.hurtServer(level, blow, entity.getHealth() + entity.getMaxHealth());

				if (entity.isAlive()) {
					entity.kill(level);
				}
			} finally {
				EXECUTING.remove(entity.getUUID());
			}
		}
	}

	/** Fabric UseEntityCallback: using a yielded entity opens the Spare / Execute dialogue. */
	static InteractionResult onUse(Player player, Level level, InteractionHand hand, Entity entity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof Yieldable yieldable)
				|| !yieldable.isYielded() || !isPending(entity)) {
			return InteractionResult.PASS;
		}

		if (hand == InteractionHand.MAIN_HAND) {
			if (mayDecide(yieldable, serverPlayer)) {
				Dialogues.open(serverPlayer, DIALOGUE, entity);
			} else {
				serverPlayer.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.yield_not_yours", entity.getDisplayName()));
			}
		}

		return InteractionResult.SUCCESS;
	}

	/** A yielded entity loaded from disk (or a chunk reload) waits for its decision again. */
	static void onEntityLoad(Entity entity) {
		if (entity instanceof Yieldable yieldable && entity instanceof LivingEntity living && yieldable.isYielded()
				&& !TRACKED.containsKey(entity.getUUID()) && !RESOLVED.contains(entity.getUUID())) {
			track(living);
		}
	}

	/** Lets yielded entities flee when the decider walks away or time runs out. */
	static void tick(MinecraftServer server) {
		IdentityBalance b = Identity.balance();
		long now = server.overworld().getGameTime();
		List<Runnable> fleeing = new ArrayList<>();

		for (Iterator<YieldState> it = TRACKED.values().iterator(); it.hasNext();) {
			YieldState state = it.next();
			ServerLevel level = server.getLevel(state.dimension);
			Entity entity = level == null ? null : level.getEntity(state.entity);

			if (entity == null) {
				// Unloaded or gone; give up once its time would have run out anyway.
				if (now - state.since > b.yieldTimeoutTicks) {
					it.remove();
				}

				continue;
			}

			if (!(entity instanceof LivingEntity living) || !living.isAlive() || !(entity instanceof Yieldable yieldable) || !yieldable.isYielded()) {
				it.remove();
				continue;
			}

			ServerPlayer witness = nearestDecider(level, living, yieldable, b.yieldLeaveDistance);

			if (witness == null || now - state.since >= b.yieldTimeoutTicks) {
				ServerPlayer credited = witness != null ? witness : creditedPlayer(server, yieldable, state);
				fleeing.add(() -> resolve(living, YieldOutcome.FLED, credited));
			}
		}

		// Resolved after the loop: resolve() removes entries from TRACKED.
		fleeing.forEach(Runnable::run);
	}

	/** The deciding player if they are within {@code range}, or for "any player", the nearest one in range. */
	private static @Nullable ServerPlayer nearestDecider(ServerLevel level, LivingEntity entity, Yieldable yieldable, double range) {
		UUID decider = yieldable.yieldDecider();
		ServerPlayer best = null;
		double bestDistance = range * range;

		for (ServerPlayer player : level.players()) {
			if ((decider == null || decider.equals(player.getUUID())) && player.isAlive() && !player.isSpectator()) {
				double distance = player.distanceToSqr(entity);

				if (distance <= bestDistance) {
					best = player;
					bestDistance = distance;
				}
			}
		}

		return best;
	}

	private static @Nullable ServerPlayer creditedPlayer(MinecraftServer server, Yieldable yieldable, YieldState state) {
		UUID id = yieldable.yieldDecider() != null ? yieldable.yieldDecider() : state.striker();
		return id == null ? null : server.getPlayerList().getPlayer(id);
	}

	/** Forgets all in-memory state (server stopped). Yielded entities re-register when loaded. */
	static void clear() {
		TRACKED.clear();
		EXECUTING.clear();
		RESOLVED.clear();
	}

	private Yielding() {
	}
}

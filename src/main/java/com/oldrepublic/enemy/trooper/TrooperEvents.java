package com.oldrepublic.enemy.trooper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.combat.IncidentMode;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Server listeners of the troopers (spec 5.2, 7.1, 7.4, 7.5): a hit or an aimed power on a trooper
 * that did not count as hostile provokes its squad; damage to a player makes the Allied troopers
 * around them defend them (against the other army this starts an ARMY incident on both squads);
 * a bled-out downed trooper and a duplicate at a post are discarded instead of entering the level;
 * the server tick runs the reinforcements and forgets old squad state.
 */
public final class TrooperEvents {
	/** Squad state is pruned this often. */
	private static final int PRUNE_INTERVAL = 1200;

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
				onDamage(entity, source, damageTaken, blocked));
		CombatEvents.POWER_HIT.register((caster, abilityId, target) -> onPowerHit(caster, target));
		ServerEntityEvents.ALLOW_LOAD.register(TrooperEvents::allowLoad);
		ServerTickEvents.END_SERVER_TICK.register(TrooperEvents::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Squads.clear();
			Reinforcements.clear();
		});
	}

	private static void onDamage(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
			return;
		}

		long now = level.getGameTime();

		if (victim instanceof TrooperEntity trooper && taken > 0f) {
			onTrooperHurt(trooper, attacker, now);
		}

		if (victim instanceof Player player && (taken > 0f || blocked)) {
			defendAlly(level, player, attacker, now);
		}
	}

	/** An aimed power counts as a hit (spec 5.2): area powers already skip troopers that were not provoked. */
	private static void onPowerHit(ServerPlayer caster, LivingEntity target) {
		if (target instanceof TrooperEntity trooper && !TrooperAttitudeRule.peaceful(trooper)
				&& !Attitudes.countsAsHostile(caster, trooper) && !Attitudes.provokedBy(trooper, caster)) {
			provokeSquad(trooper, caster, trooper.level().getGameTime());
		}
	}

	private static void onTrooperHurt(TrooperEntity trooper, LivingEntity attacker, long now) {
		if (attacker instanceof Player player) {
			if (TrooperAttitudeRule.peaceful(trooper)) {
				return;
			}

			if (!Attitudes.countsAsHostile(player, trooper) && !trooper.isProvokedBy(player.getUUID(), now)) {
				provokeSquad(trooper, player, now);
			} else if (trooper.canFight()) {
				trooper.setTarget(player);
				Squads.shareTarget(trooper, player);
			}
		} else if (!Allegiances.friendly(trooper, attacker) && trooper.canFight() && trooper.getTarget() == null) {
			trooper.setTarget(attacker);
			Squads.shareTarget(trooper, attacker);
		}

		if (trooper.getRandom().nextInt(3) == 0) {
			Barks.say(trooper, BarkCategory.HURT);
		}
	}

	/**
	 * {@code player} struck the squad first: every member fights them for {@code provokedTicks}. For
	 * kill reputation the fight still counts as not hostile.
	 */
	public static void provokeSquad(TrooperEntity trooper, Player player, long now) {
		for (TrooperEntity member : Squads.squadOf(trooper)) {
			member.provoke(player.getUUID(), now);

			if (member.canFight()) {
				member.setTarget(player);
			}
		}
	}

	/** The squad fires first at {@code player} (a Wary squad after its warning, an ally defence): hostile. */
	public static void engageSquad(TrooperEntity trooper, Player player, long now) {
		for (TrooperEntity member : Squads.squadOf(trooper)) {
			member.engage(player.getUUID(), now);

			if (member.canFight()) {
				member.setTarget(player);
			}
		}
	}

	/**
	 * Allied troopers within {@code allyDefendRange} of a hurt player turn on the attacker (spec 5.2):
	 * another player becomes engaged with the squad; the other army's trooper starts an ARMY incident
	 * on both squads for {@code incidentTicks}; anything else is simply fought. A trooper never turns
	 * on its own army. Called for every hit that lands on (or is blocked by) a player; public so the
	 * game tests can drive it without hurting a mock player.
	 */
	public static void defendAlly(ServerLevel level, Player player, LivingEntity attacker, long now) {
		TrooperBalance b = TrooperBalance.get();
		Set<UUID> handled = new HashSet<>();

		for (TrooperEntity defender : level.getEntitiesOfClass(TrooperEntity.class, player.getBoundingBox().inflate(b.allyDefendRange),
				TrooperEntity::canFight)) {
			if (Allegiances.friendly(defender, attacker) || Attitudes.toward(defender, player) != Attitude.ALLIED
					|| !handled.add(Squads.key(defender))) {
				continue;
			}

			if (attacker instanceof Player other) {
				if (!TrooperAttitudeRule.peaceful(defender) && other.canBeSeenAsEnemy()) {
					engageSquad(defender, other, now);
				}
			} else if (attacker instanceof TrooperEntity rival) {
				startIncident(defender, rival, now, b);
			} else {
				for (TrooperEntity member : Squads.squadOf(defender)) {
					member.defend(attacker);
				}
			}
		}
	}

	/** An ally defence against the other army: both squads carry an ARMY incident for {@code incidentTicks}. */
	static void startIncident(TrooperEntity defender, TrooperEntity rival, long now, TrooperBalance b) {
		long until = TrooperRules.expiry(now, b.incidentTicks);

		for (TrooperEntity member : Squads.squadOf(defender)) {
			member.addIncident(IncidentMode.ARMY, until);
		}

		for (TrooperEntity member : Squads.squadOf(rival)) {
			member.addIncident(IncidentMode.ARMY, until);
		}

		for (TrooperEntity member : Squads.squadOf(defender)) {
			member.defend(rival);
		}
	}

	/**
	 * Keeps a downed trooper whose {@code BleedAt} has passed, and a stale duplicate at a post, out of
	 * the level as it loads (from a chunk or freshly added): it is discarded and never added, so it is
	 * not saved again either. Cancelling here rather than discarding after the load keeps a removed
	 * entity out of the level's tick list.
	 */
	private static boolean allowLoad(Entity entity, ServerLevel level, @Nullable EntitySpawnReason reason, boolean fromDisk) {
		if (!(entity instanceof TrooperEntity trooper)) {
			return true;
		}

		boolean bledOut = trooper.isDowned() && trooper.bleedAt() >= 0 && level.getGameTime() >= trooper.bleedAt();

		if (bledOut || duplicateAtPost(level, trooper)) {
			trooper.discard();
			return false;
		}

		return true;
	}

	/**
	 * Whether {@code trooper} is bound to a loaded {@link TrooperPost} that keeps someone else in its
	 * slot (spec 7.4): a stale duplicate of a respawned officer or garrison member.
	 */
	static boolean duplicateAtPost(ServerLevel level, TrooperEntity trooper) {
		BlockPos terminal = trooper.terminal();

		if (terminal == null || !level.isLoaded(terminal)) {
			return false;
		}

		BlockEntity blockEntity = level.getBlockEntity(terminal);

		if (!(blockEntity instanceof TrooperPost post)) {
			return false;
		}

		UUID listed = post.memberAt(trooper.terminalSlot());
		return listed != null && !listed.equals(trooper.getUUID());
	}

	private static void tick(MinecraftServer server) {
		Reinforcements.tick(server);

		if (server.getTickCount() % PRUNE_INTERVAL == 0) {
			Squads.prune(server.overworld().getGameTime());
		}
	}

	private TrooperEvents() {
	}
}

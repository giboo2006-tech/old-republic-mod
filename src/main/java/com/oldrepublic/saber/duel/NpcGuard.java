package com.oldrepublic.saber.duel;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberToggle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The guard and parry of saber NPCs (spec section 9.5). While {@link GuardState#GUARDING}, a melee
 * hit from within {@code npcGuardArc} of the NPC's facing does {@code npcBlockDamageMult} damage and
 * drains {@code (npcDrainBase + npcDrainPerDamage x damage) x the strike's drain multiplier}. At 0
 * stamina the guard breaks: {@code npcBrokenTicks} with no attacking or guarding and
 * {@code npcBrokenDamageMult} damage taken. A {@code READY} or guarding NPC with at least its profile's
 * {@code parryMinStamina} (else {@code npcParryMinStamina}) of its stamina may parry a player's light attack (profile chance):
 * the player is staggered (a {@link ControlEffects} stagger, so control modifiers apply). Heavy attacks
 * and lunges are never parried. Server side.
 */
public final class NpcGuard {
	private static final Map<Mob, DuelState> STATES = new WeakHashMap<>();

	/** Gives a saber NPC its guard. Called by {@link DuelistCombatGoal}'s constructor. */
	public static <T extends Mob & SaberDuelist> DuelState register(T mob, Supplier<DuelistProfile> profile) {
		return STATES.computeIfAbsent(mob, m -> new DuelState(profile));
	}

	/** The NPC's guard, or null if it is not a registered saber duelist. */
	public static @Nullable DuelState state(Mob mob) {
		return mob instanceof SaberDuelist ? STATES.get(mob) : null;
	}

	/**
	 * Applies the NPC's guard to a hit. Returns the damage that goes through, or -1 when the NPC
	 * parried (the pipeline cancels the hit).
	 */
	public static float onHit(Mob mob, DuelState state, ServerLevel level, DamageSource source, float amount, SaberCombat.@Nullable Strike strike) {
		SaberDuelist duelist = (SaberDuelist) mob;
		SaberBalance b = SaberToggle.balance();
		long now = level.getGameTime();

		if (state.isBroken(now)) {
			state.onHurt(now);
			return amount * b.npcBrokenDamageMult;
		}

		Entity direct = source.getDirectEntity();
		Vec3 from = source.getSourcePosition();
		boolean melee = direct != null && direct == source.getEntity() && direct instanceof LivingEntity;

		if (!melee || from == null || source.is(DamageTypeTags.BYPASSES_SHIELD) || ForceStatus.isGuardSuppressed(mob)
				|| !SaberGuard.inFront(mob, from, b.npcGuardArc)) {
			state.onHurt(now);
			return amount;
		}

		GuardState guard = duelist.guardState();
		DuelistProfile profile = state.profile();
		Vec3 contact = SaberGuard.contactPoint(mob, direct.getBoundingBox().getCenter());
		int colour = colour(duelist);
		boolean canParry = direct instanceof ServerPlayer && strike != null && strike.technique().light() && !strike.unparryable()
				&& (guard == GuardState.READY || guard == GuardState.GUARDING) && profile.parryChance() > 0f
				&& state.stamina(now) >= parryMinStamina(profile, b) * state.maxStamina();

		if (canParry && mob.getRandom().nextFloat() < profile.parryChance()) {
			ServerPlayer player = (ServerPlayer) direct;
			ControlEffects.stagger(mob, player, b.npcParryStaggerTicks, 2, ControlEffects.STAGGER);
			player.resetAttackStrengthTicker();
			EffectEvents.toTracking(mob, SaberEffects.PARRY, contact, mob.getLookAngle(), mob.getId(), 1f, colour);
			return -1f;
		}

		if (guard != GuardState.GUARDING) {
			state.onHurt(now);
			return amount;
		}

		float drain = (b.npcDrainBase + b.npcDrainPerDamage * amount) * (strike == null ? 1f : strike.guardDrainMult());

		if (state.drain(drain, now)) {
			breakGuard(mob, state, now, b);
			state.onHurt(now);
			return amount;
		}

		EffectEvents.toTracking(mob, SaberEffects.CLASH, contact, mob.getLookAngle(), mob.getId(), drain, colour);
		return amount * b.npcBlockDamageMult;
	}

	/** A player's saber parried the NPC's strike: it is staggered, sparks fly at the blades. */
	public static void onParriedByPlayer(Mob mob, Vec3 contact) {
		DuelState state = state(mob);

		if (state == null || !(mob instanceof SaberDuelist duelist)) {
			return;
		}

		SaberBalance b = SaberToggle.balance();
		state.stagger(mob.level().getGameTime() + b.staggerTicks);
		duelist.setGuardState(GuardState.RECOVERING);
		EffectEvents.toTracking(mob, SaberEffects.CLASH, contact, mob.getLookAngle(), mob.getId(), 1f, colour(duelist));
	}

	/** Whether the NPC is guarding and {@code from} is inside its guard arc. */
	public static boolean isGuardingAgainst(Mob mob, Vec3 from) {
		return mob instanceof SaberDuelist duelist && state(mob) != null && duelist.guardState() == GuardState.GUARDING
				&& !ForceStatus.isGuardSuppressed(mob) && SaberGuard.inFront(mob, from, SaberToggle.balance().npcGuardArc);
	}

	/** Drains stamina from outside melee (Push, Lightning, a thrown saber). Returns true if the guard broke. */
	public static boolean drain(Mob mob, float amount) {
		DuelState state = state(mob);

		if (state == null) {
			return false;
		}

		long now = mob.level().getGameTime();

		if (state.isBroken(now) || !state.drain(amount, now)) {
			return false;
		}

		breakGuard(mob, state, now, SaberToggle.balance());
		return true;
	}

	/** Gives back stamina. */
	public static void restore(Mob mob, float amount) {
		DuelState state = state(mob);

		if (state != null) {
			state.restore(amount, mob.level().getGameTime());
		}
	}

	/** Staggers the NPC for {@code ticks}: no attacking or guarding, and any wind-up is cancelled. */
	public static void stagger(Mob mob, int ticks) {
		DuelState state = state(mob);

		if (state != null && mob instanceof SaberDuelist duelist) {
			state.stagger(mob.level().getGameTime() + ticks);
			duelist.setGuardState(GuardState.RECOVERING);
		}
	}

	/** Cancels the NPC's current wind-up (a stagger, shock or disarm). */
	public static void interruptWindup(Mob mob) {
		DuelState state = state(mob);

		if (state != null) {
			state.requestInterrupt();
		}
	}

	/**
	 * Empties the NPC's guard and breaks it for {@code ticks} (sends {@code npc_guard_break}): no
	 * attacking or guarding, and broken-guard damage taken. No-op for a mob that is not a registered
	 * saber duelist.
	 */
	public static void breakGuard(Mob mob, int ticks) {
		DuelState state = state(mob);

		if (state == null) {
			return;
		}

		breakGuard(mob, state, mob.level().getGameTime(), Math.max(0, ticks));
	}

	/**
	 * The stamina fraction a duelist with {@code profile} needs to parry a light attack: the profile's
	 * {@code parryMinStamina} when it is above 0, else {@code saber.npcParryMinStamina}.
	 */
	public static float parryMinStamina(DuelistProfile profile, SaberBalance b) {
		return profile.parryMinStamina() > 0f ? profile.parryMinStamina() : b.npcParryMinStamina;
	}

	private static void breakGuard(Mob mob, DuelState state, long now, SaberBalance b) {
		breakGuard(mob, state, now, b.npcBrokenTicks);
	}

	private static void breakGuard(Mob mob, DuelState state, long now, int ticks) {
		state.breakGuard(now, ticks);
		((SaberDuelist) mob).setGuardState(GuardState.BROKEN);
		Vec3 chest = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
		EffectEvents.toTracking(mob, SaberEffects.NPC_GUARD_BREAK, chest, mob.getLookAngle(), mob.getId(), 1f, colour((SaberDuelist) mob));
	}

	/** Blade colour index of the duelist's saber. */
	static int colour(SaberDuelist duelist) {
		return SaberComponents.config(duelist.duelSaber()).color().ordinal();
	}

	private NpcGuard() {
	}
}

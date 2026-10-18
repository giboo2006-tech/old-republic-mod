package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.skill.SkillPassives;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The saber feature's damage pipeline, run from {@code LivingEntityGuardMixin} before anything else
 * in {@code LivingEntity#hurtServer} uses the amount. In order:
 * <ol>
 * <li>the attacker's saber bonuses (combo finisher, Soresu, Ataru aerial strike, riposte, a Force
 * Pull's next-hit bonus, {@code fury});</li>
 * <li>the defender's guard: a player's saber guard ({@link SaberGuard}) or a saber NPC's guard
 * ({@link NpcGuard}); a parry cancels the hit;</li>
 * <li>{@code guardians_resolve} for players near a guarding ally;</li>
 * <li>spar safety and outcomes ({@link Spar});</li>
 * <li>what being hit does to the defender (the combo string resets, a heavy wind-up of at least
 * {@code heavyInterruptDamage} is cancelled);</li>
 * <li>the other features' {@link DamageModifiers} (armour resistances, auras).</li>
 * </ol>
 * Cancelled hits return 0 and are also refused by the Fabric {@code ALLOW_DAMAGE} event. All server side.
 */
public final class SaberCombat {
	/** What kind of strike a damage call is. */
	public enum Technique {
		/** A player's light saber hit (combo steps 1 and 2, or a hit outside a string). */
		LIGHT,
		/** The third step of a player's combo. */
		FINISHER,
		HEAVY,
		LUNGE,
		THROW,
		/** A saber NPC's or the Captain's normal melee strike. */
		NPC_LIGHT,
		/** The Captain's overhead heavy. */
		NPC_HEAVY;

		/** Whether a player's saber deals it. */
		public boolean byPlayerSaber() {
			return ordinal() <= THROW.ordinal();
		}

		/** Melee techniques get the form's melee damage multiplier and can be riposted. */
		public boolean melee() {
			return this != THROW;
		}

		/** Light attacks are the only ones an NPC can parry. */
		public boolean light() {
			return this == LIGHT || this == FINISHER;
		}
	}

	/**
	 * The context of one strike.
	 *
	 * @param guardDrainMult multiplier on the stamina a guard loses blocking it
	 * @param unparryable    a parry window does not stop it (it is blocked as a normal hit)
	 */
	public record Strike(Technique technique, float guardDrainMult, boolean unparryable) {
		public static final Strike LIGHT = new Strike(Technique.LIGHT, 1f, false);
		public static final Strike NPC_LIGHT = new Strike(Technique.NPC_LIGHT, 1f, false);

		public static Strike finisher(SaberBalance b) {
			return new Strike(Technique.FINISHER, b.finisherGuardDrainMult, false);
		}

		public static Strike heavy(SaberBalance b) {
			return new Strike(Technique.HEAVY, b.heavyGuardDrainMult, true);
		}

		public static Strike lunge() {
			return new Strike(Technique.LUNGE, 1f, true);
		}

		public static Strike thrown() {
			return new Strike(Technique.THROW, 1f, true);
		}

		/** An NPC heavy blow, parryable, draining a guard {@code drainMult} times as much. */
		public static Strike npcHeavy(float drainMult) {
			return new Strike(Technique.NPC_HEAVY, drainMult, false);
		}
	}

	/** A light swing seen by the attack callback, matched to the damage call of the same tick. */
	private record Swing(int targetId, long tick, int step) {
	}

	/** Finisher knock-back applied once the hit has gone through. */
	private record PostHit(Vec3 from, float strength, long tick) {
	}

	/** Damage bonus for the next saber hit. */
	private record Bonus(float mult, long until) {
	}

	private static @Nullable Strike current;
	private static final Map<UUID, Long> CANCEL = new HashMap<>();
	private static final Map<UUID, Swing> PENDING = new HashMap<>();
	private static final Map<UUID, PostHit> POST = new HashMap<>();
	private static final Map<UUID, Long> RIPOSTE = new HashMap<>();
	private static final Map<UUID, Bonus> NEXT_HIT = new HashMap<>();
	private static final Set<UUID> LEAPING = new HashSet<>();
	private static final Set<UUID> AERIAL_USED = new HashSet<>();

	/** Deals {@code amount} to {@code target} as a strike of the given kind. */
	public static boolean strike(LivingEntity target, ServerLevel level, DamageSource source, float amount, Strike strike) {
		Strike previous = current;
		current = strike;

		try {
			return target.hurtServer(level, source, amount);
		} finally {
			current = previous;
		}
	}

	/** Makes {@code ALLOW_DAMAGE} refuse the hit {@code entity} is taking this tick. */
	public static void cancel(LivingEntity entity) {
		CANCEL.put(entity.getUUID(), entity.level().getGameTime());
	}

	/** True once for a hit cancelled this tick. */
	static boolean consumeCancel(LivingEntity entity) {
		Long tick = CANCEL.remove(entity.getUUID());
		return tick != null && tick == entity.level().getGameTime();
	}

	/**
	 * Fabric {@code AttackEntityCallback} (server, at the head of {@code Player#attack}, before the
	 * attack strength resets): counts the combo step of a lit-saber swing, and refuses attacks while
	 * the saber is thrown, a heavy attack or lunge is running or the form is changing.
	 */
	static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		ItemStack weapon = player.getMainHandItem();

		if (weapon.getItem() instanceof LightsaberItem
				&& (SaberComponents.isThrown(weapon) || SaberTechniques.isBusy(serverPlayer) || SaberForms.isSwitching(serverPlayer))) {
			return InteractionResult.FAIL;
		}

		if (!SaberComponents.isUsable(weapon) || !(target instanceof LivingEntity)) {
			return InteractionResult.PASS;
		}

		SaberBalance b = SaberToggle.balance();
		long now = level.getGameTime();
		int window = SaberPassives.form(player).modifiers(b).comboWindow();
		int step = ComboTracker.onSwing(player.getUUID(), player.getAttackStrengthScale(0.5f), now, window, b.comboMinStrength);
		PENDING.put(player.getUUID(), new Swing(target.getId(), now, step));

		if (step > 0) {
			EffectEvents.toTracking(player, SaberEffects.COMBO, player.getEyePosition(), player.getLookAngle(), player.getId(), 1f, step);
		}

		if (step == ComboTracker.FINISHER) {
			QuestHooks.onSaberTechnique(serverPlayer, "finisher", (LivingEntity) target, true);
		}

		return InteractionResult.PASS;
	}

	/** The damage pipeline (see the class comment). Returns the amount that goes on. */
	public static float modifyIncomingDamage(LivingEntity target, ServerLevel level, DamageSource source, float amount) {
		return modifyIncomingDamage(target, level, source, amount, null);
	}

	/**
	 * The damage pipeline for an explicit strike kind (tests), or, with {@code explicit} null, the
	 * strike set by {@link #strike} or a player's own light or finisher hit.
	 */
	public static float modifyIncomingDamage(LivingEntity target, ServerLevel level, DamageSource source, float amount, @Nullable Strike explicit) {
		if (amount <= 0f || target.isDeadOrDying() || target instanceof Yieldable yieldable && yieldable.isYielded()) {
			return amount;
		}

		SaberBalance b = SaberToggle.balance();
		long now = level.getGameTime();
		Strike strike = explicit != null ? explicit : current != null ? current : lightStrike(target, source, now, b);

		if (strike != null && strike.technique().byPlayerSaber() && source.getEntity() instanceof ServerPlayer attacker) {
			amount *= attackerMultiplier(attacker, strike, now, b);
		}

		float result = amount;
		DuelState duel = target instanceof Mob mob ? NpcGuard.state(mob) : null;

		if (target instanceof ServerPlayer player) {
			result = SaberGuard.onMelee(player, level, source, amount, strike);
		} else if (duel != null) {
			result = NpcGuard.onHit((Mob) target, duel, level, source, amount, strike);
		}

		if (result >= 0f && target instanceof ServerPlayer player) {
			result *= guardiansResolve(player, level);
		}

		if (result >= 0f) {
			result = Spar.onDamage(target, source, result, strike);
		}

		if (result < 0f) {
			cancel(target);
			return 0f;
		}

		if (result > 0f && target instanceof ServerPlayer player) {
			ComboTracker.reset(player.getUUID());
			SaberTechniques.onHurt(player, result);
		}

		if (strike != null && strike.technique() == Technique.FINISHER && result > 0f && source.getEntity() != null) {
			POST.put(target.getUUID(), new PostHit(source.getEntity().position(), b.finisherKnockback, now));
		}

		return DamageModifiers.apply(target, source, result);
	}

	/** A player's own melee hit with a lit saber: light, or the finisher when the swing was step 3. */
	private static @Nullable Strike lightStrike(LivingEntity target, DamageSource source, long now, SaberBalance b) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker
				|| !source.is(DamageTypes.PLAYER_ATTACK) || !SaberComponents.isUsable(attacker.getMainHandItem())) {
			return null;
		}

		Swing swing = PENDING.remove(attacker.getUUID());
		boolean finisher = swing != null && swing.targetId == target.getId() && swing.tick == now && swing.step == ComboTracker.FINISHER;
		return finisher ? Strike.finisher(b) : Strike.LIGHT;
	}

	private static float attackerMultiplier(ServerPlayer attacker, Strike strike, long now, SaberBalance b) {
		Technique technique = strike.technique();
		SaberForm form = SaberPassives.form(attacker);
		UUID id = attacker.getUUID();
		float mult = 1f;

		if (technique == Technique.FINISHER) {
			mult *= b.finisherDamageMult;
		}

		if (technique.melee()) {
			if (form == SaberForm.SORESU) {
				mult *= b.soresuDamageMult;
			}

			Long riposte = RIPOSTE.remove(id);

			if (riposte != null && now <= riposte) {
				mult *= b.riposteDamageMult;
			}
		}

		if (technique.light() && isAerial(attacker, form, b)) {
			AERIAL_USED.add(id);
			mult *= b.ataruAerialMult;
		}

		Bonus bonus = NEXT_HIT.remove(id);

		if (bonus != null && now <= bonus.until) {
			mult *= bonus.mult;
		}

		// A honed lens (M4, spec 5.7) sharpens every strike of the saber it is fitted to.
		return mult * SkillPassives.furyMultiplier(attacker) * SaberLens.damageMult(attacker);
	}

	/** Ataru's aerial strike: the first attack while airborne after a Force Jump or a long enough fall. */
	static boolean isAerial(Player player, SaberForm form, SaberBalance b) {
		UUID id = player.getUUID();
		return form.modifiers(b).aerialStrike() && !player.onGround() && !AERIAL_USED.contains(id)
				&& (LEAPING.contains(id) || ForceJump.isLeaping(player) || player.fallDistance >= b.ataruMinFall);
	}

	private static float guardiansResolve(ServerPlayer victim, ServerLevel level) {
		float mult = 1f;
		double radius = SkillPassives.guardiansResolveRadius();

		for (ServerPlayer guardian : level.players()) {
			if (guardian != victim && guardian.distanceToSqr(victim) <= radius * radius && !SaberGuard.guardingSaber(guardian).isEmpty()) {
				mult = Math.min(mult, SkillPassives.guardiansResolveMultiplier(guardian, victim));
			}
		}

		return mult;
	}

	/** {@code ALLOW_DAMAGE}: false for hits the pipeline cancelled. */
	static boolean allowDamage(LivingEntity entity) {
		return !consumeCancel(entity);
	}

	/** {@code AFTER_DAMAGE}: the finisher's knock-back, once the hit went through. */
	static void afterDamage(LivingEntity target, float taken) {
		PostHit post = POST.remove(target.getUUID());

		if (post == null || taken <= 0f || post.tick != target.level().getGameTime()) {
			return;
		}

		Vec3 away = target.position().subtract(post.from).horizontal();
		Vec3 dir = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
		double resist = Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		double strength = post.strength * (1.0 - resist);
		target.setDeltaMovement(target.getDeltaMovement().add(dir.x * strength, 0.1 * (1.0 - resist), dir.z * strength));
		target.hurtMarked = true;
	}

	/** A parry by {@code player}: arms {@code riposte} when the player has it. */
	static void onParry(ServerPlayer player, SaberBalance b) {
		if (SaberPassives.has(player, SaberPassives.RIPOSTE)) {
			RIPOSTE.put(player.getUUID(), player.level().getGameTime() + b.riposteWindowTicks);
		}
	}

	/** The next saber hit within {@code ticks} deals {@code mult} times the damage (Reeling Pull). */
	public static void grantNextHitBonus(ServerPlayer player, float mult, int ticks) {
		NEXT_HIT.put(player.getUUID(), new Bonus(mult, player.level().getGameTime() + ticks));
	}

	/** The player has just leapt with Force Jump: their first airborne attack can be an Ataru aerial strike. */
	public static void markLeap(ServerPlayer player) {
		LEAPING.add(player.getUUID());
		AERIAL_USED.remove(player.getUUID());
	}

	/** Once per server tick per player: landing ends the airborne state. */
	static void tick(ServerPlayer player) {
		if (player.onGround()) {
			LEAPING.remove(player.getUUID());
			AERIAL_USED.remove(player.getUUID());
		}
	}

	static void forget(Player player) {
		UUID id = player.getUUID();
		CANCEL.remove(id);
		PENDING.remove(id);
		POST.remove(id);
		RIPOSTE.remove(id);
		NEXT_HIT.remove(id);
		LEAPING.remove(id);
		AERIAL_USED.remove(id);
		ComboTracker.reset(id);
	}

	private SaberCombat() {
	}
}

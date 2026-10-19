package com.oldrepublic.force.stasis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Holds;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code stasis} control (MILESTONE_5.md 9.2, 9.3): the runtime behind {@link ControlEffects#STASIS}. A creature in
 * stasis has the {@code oldrepublic:stasis} effect, and while it does {@code mixin.force.LivingEntityStasisMixin} makes
 * {@code LivingEntity#isImmobile} true, so {@code aiStep} zeroes its input and skips its AI while gravity and knockback
 * still apply. A creature caught in the air that flies is pinned there instead ({@code setYya(0)}, no gravity) and gets its
 * gravity back when the stasis ends. A player (PvP only) is held through the M3 {@link Holds}, with the effect for its icon.
 *
 * <p>The effect is the state: whatever removes it (expiry, milk, {@code /effect clear}, a boss's rule) ends the stasis on
 * the next tick. Damage breaks it: one hit of {@code breakDamage} or more at once, otherwise {@code breakDelayTicks}
 * after the first hit; a hold may ignore its first hits (Stasis Lock). Server side.
 */
public final class StasisHolds {
	/** {@code Holds} source of a PvP stasis. */
	public static final String HOLD_SOURCE = "force_stasis";

	/** One creature in stasis. */
	private static final class Held {
		final LivingEntity target;
		/** Hits that do not count yet (Stasis Lock). */
		int ignoreHits;
		/** Game time of the first hit that counts, or -1. */
		long firstHit = -1;
		/** A flyer pinned in the air, and whether it floated before. */
		final boolean pinned;
		final boolean wasNoGravity;

		Held(LivingEntity target, int ignoreHits, boolean pinned, boolean wasNoGravity) {
			this.target = target;
			this.ignoreHits = ignoreHits;
			this.pinned = pinned;
			this.wasNoGravity = wasNoGravity;
		}
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	/**
	 * Puts {@code target} in stasis for {@code baseTicks} before the control rules: the length comes from
	 * {@link ControlEffects#diminished(LivingEntity, int, String)} alone (diminishing returns on push-resistant targets, a
	 * boss at 25%, a {@code #oldrepublic:force_resistant} creature at half). A stasis already on the target is replaced.
	 * Returns the ticks applied, 0 when nothing took.
	 *
	 * @param ignoreHits hits that do not count toward breaking it (Stasis Lock)
	 */
	public static int apply(LivingEntity target, @Nullable ServerPlayer caster, int baseTicks, int ignoreHits) {
		if (!target.isAlive() || target.isRemoved() || baseTicks <= 0) {
			return 0;
		}

		int ticks = ControlEffects.diminished(target, baseTicks, ControlEffects.STASIS);

		if (ticks <= 0) {
			return 0;
		}

		Held previous = HELD.remove(target.getUUID());

		if (previous != null && previous.pinned) {
			previous.target.setNoGravity(previous.wasNoGravity);
		}

		// Replace a shorter one: addEffect keeps the longer of two instances, which is what a fresh stasis wants anyway.
		target.removeEffect(ForceMobEffects.STASIS);
		ForceMobEffects.apply(target, ForceMobEffects.STASIS, ticks, caster);
		boolean pinned = false;
		boolean wasNoGravity = target.isNoGravity();

		if (target instanceof ServerPlayer victim) {
			Holds.hold(victim, ticks + 1, HOLD_SOURCE, victim.position(), false);
		} else if (flies(target)) {
			pinned = true;
			target.setNoGravity(true);
			target.setDeltaMovement(Vec3.ZERO);
		}

		HELD.put(target.getUUID(), new Held(target, Math.max(0, ignoreHits), pinned, wasNoGravity));
		ForceReactions.interruptWindup(target, caster, ForceReactions.CAUSE_STAGGER);
		EffectEvents.toTracking(target, ForceStasis.EFFECT, ForcePowers.chest(target), null, target.getId(), ticks / 20f,
				ForceEffects.packCaster(caster == null ? 0 : caster.getId(), ForceStasis.VARIANT_HELD));
		return ticks;
	}

	/** A creature in the air that flies: no gravity of its own, or a flying navigator. */
	static boolean flies(LivingEntity target) {
		// API-CHECK: net.minecraft.world.entity.ai.navigation.FlyingPathNavigation (package as PathNavigation's)
		return !target.onGround() && (target.isNoGravity() || target instanceof Mob mob && mob.getNavigation() instanceof FlyingPathNavigation);
	}

	/** Whether {@code entity} is held in stasis now: the mixin's test, so it must be cheap. Works wherever effects are known. */
	public static boolean isFrozen(LivingEntity entity) {
		return entity.hasEffect(ForceMobEffects.STASIS);
	}

	/** Whether the stasis on {@code entity} is tracked here (server). */
	public static boolean isHeld(Entity entity) {
		return HELD.containsKey(entity.getUUID());
	}

	/** Ends the stasis on {@code target}, if any (a boss's rule, a command). */
	public static void release(LivingEntity target) {
		Held held = HELD.get(target.getUUID());

		if (held != null) {
			end(held, false);
		} else if (target.hasEffect(ForceMobEffects.STASIS)) {
			target.removeEffect(ForceMobEffects.STASIS);
		}
	}

	/** Advances every stasis; called every server tick. */
	public static void tick() {
		if (HELD.isEmpty()) {
			return;
		}

		StasisBalance b = ForceStasis.balance();

		for (Held held : new ArrayList<>(HELD.values())) {
			LivingEntity target = held.target;

			if (!target.isAlive() || target.isRemoved()) {
				HELD.remove(target.getUUID());
				restore(held);
				continue;
			}

			if (!target.hasEffect(ForceMobEffects.STASIS)) {
				end(held, true);
				continue;
			}

			if (held.firstHit >= 0 && target.level().getGameTime() - held.firstHit >= b.breakDelayTicks) {
				end(held, false);
				continue;
			}

			if (target instanceof ServerPlayer victim && !Holds.isHeld(victim)) {
				// Pushed free, or released by another rule.
				end(held, false);
				continue;
			}

			if (held.pinned && target instanceof Mob mob) {
				// isImmobile clears xxa and zza but not yya.
				mob.setYya(0f);
				mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.5, 0.0, 0.5));
			}
		}
	}

	/**
	 * Fabric {@code AFTER_DAMAGE}: a hit on a creature in stasis. One hit of {@code breakDamage} or more ends it at once;
	 * a smaller one starts the {@code breakDelayTicks} count, unless the hold still ignores hits.
	 */
	public static void onDamaged(LivingEntity entity, float damageTaken) {
		if (damageTaken <= 0f || HELD.isEmpty()) {
			return;
		}

		Held held = HELD.get(entity.getUUID());

		if (held == null || held.target != entity) {
			return;
		}

		if (held.ignoreHits > 0) {
			held.ignoreHits--;
			return;
		}

		if (damageTaken >= ForceStasis.balance().breakDamage) {
			end(held, false);
		} else if (held.firstHit < 0) {
			held.firstHit = entity.level().getGameTime();
		}
	}

	/** Pure: whether a hit of {@code damage} breaks a hold at once (no hits left to ignore). */
	public static boolean breaksAtOnce(float damage, StasisBalance b) {
		return damage >= b.breakDamage;
	}

	private static void end(Held held, boolean effectGone) {
		LivingEntity target = held.target;
		HELD.remove(target.getUUID());

		if (!effectGone) {
			target.removeEffect(ForceMobEffects.STASIS);
		}

		restore(held);
		EffectEvents.toTracking(target, ForceStasis.EFFECT, ForcePowers.chest(target), null, target.getId(), 0f, ForceStasis.VARIANT_BREAK);
	}

	/** Gives a pinned flyer its gravity back and lets a held player go. */
	private static void restore(Held held) {
		if (held.pinned) {
			held.target.setNoGravity(held.wasNoGravity);
		}

		if (held.target instanceof ServerPlayer victim && Holds.isHeld(victim)) {
			Holds.release(victim, "break");
		}
	}

	/** Fabric {@code ENTITY_UNLOAD}: a creature leaving the world keeps its effect but loses its pin (gravity is saved). */
	public static void onUnload(Entity entity) {
		Held held = HELD.remove(entity.getUUID());

		if (held != null) {
			restore(held);
		}
	}

	/**
	 * Fabric {@code ENTITY_LOAD}: a creature that comes back with the effect (a reloaded chunk or save) is tracked again, so
	 * a hit still breaks it; it is not pinned again (its gravity was given back when it left).
	 */
	public static void onLoad(Entity entity) {
		if (entity instanceof LivingEntity living && !(entity instanceof ServerPlayer) && living.hasEffect(ForceMobEffects.STASIS)
				&& !HELD.containsKey(entity.getUUID())) {
			HELD.put(entity.getUUID(), new Held(living, 0, false, living.isNoGravity()));
		}
	}

	/** Drops every stasis and gives every pinned flyer its gravity back (server stop). */
	public static void clear() {
		for (Held held : HELD.values()) {
			restore(held);
		}

		HELD.clear();
	}

	private StasisHolds() {
	}
}

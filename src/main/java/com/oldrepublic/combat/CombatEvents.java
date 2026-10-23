package com.oldrepublic.combat;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side combat moments other features listen to without editing the code that causes them.
 * Fired by the lead's call sites ({@code QuestHooks.onSaberTechnique}, {@code onAbilityHit} and
 * {@code onBoltDeflected}).
 */
public final class CombatEvents {
	/** A player parried: once per melee parry, and once per parried bolt ({@code projectile} true). */
	public static final Event<Parry> PARRY = EventFactory.createArrayBacked(Parry.class,
			callbacks -> (defender, attacker, projectile) -> {
				for (Parry callback : callbacks) {
					callback.onParry(defender, attacker, projectile);
				}
			});

	/** A player's power hit a living target. */
	public static final Event<PowerHit> POWER_HIT = EventFactory.createArrayBacked(PowerHit.class,
			callbacks -> (caster, abilityId, target) -> {
				for (PowerHit callback : callbacks) {
					callback.onPowerHit(caster, abilityId, target);
				}
			});

	/** A player used a saber technique, hit or miss. */
	public static final Event<Technique> TECHNIQUE = EventFactory.createArrayBacked(Technique.class,
			callbacks -> (p, technique, target, hit) -> {
				for (Technique callback : callbacks) {
					callback.onTechnique(p, technique, target, hit);
				}
			});

	/** Listener for {@link #PARRY}. */
	@FunctionalInterface
	public interface Parry {
		/**
		 * {@code defender} parried {@code attacker}'s melee strike or bolt.
		 *
		 * @param attacker   the parried attacker, or the bolt's shooter when it is living; null if unknown
		 * @param projectile true for a parried bolt, false for a melee parry
		 */
		void onParry(ServerPlayer defender, @Nullable LivingEntity attacker, boolean projectile);
	}

	/** Listener for {@link #POWER_HIT}. */
	@FunctionalInterface
	public interface PowerHit {
		/** {@code caster}'s power {@code abilityId} hit {@code target}; non-living targets never fire this. */
		void onPowerHit(ServerPlayer caster, String abilityId, LivingEntity target);
	}

	/** Listener for {@link #TECHNIQUE}. */
	@FunctionalInterface
	public interface Technique {
		/**
		 * {@code p} used {@code technique} (the M2 technique id passed to {@code QuestHooks.onSaberTechnique})
		 * at {@code target}, which it {@code hit} or missed.
		 */
		void onTechnique(ServerPlayer p, String technique, @Nullable LivingEntity target, boolean hit);
	}

	private CombatEvents() {
	}
}

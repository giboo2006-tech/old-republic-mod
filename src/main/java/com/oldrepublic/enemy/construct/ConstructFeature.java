package com.oldrepublic.enemy.construct;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceReactions;

/**
 * Server and common initialisation of the Ruin Sentinel (spec 7.7): its entity type and attributes,
 * the spawn egg, the {@code lumen_spark} particle, its resistances, its attitude and what troopers
 * know of it, and the {@code sentinel} command. Called first from {@code BossFeature.init()} (W5 plan 4.1).
 */
public final class ConstructFeature {
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		SentinelEntities.init();
		SentinelParticles.init();

		SentinelReactions reactions = new SentinelReactions();
		ForceReactions.register(reactions);
		DamageModifiers.register(reactions);
		ControlEffects.registerDurationModifier(reactions);

		// A dormant, unbound sentinel is a statue: not hostile to anyone, so a holocron pedestal beside it
		// opens and area powers pass it by. Awake (or bound, 7.7: always hostile) it keeps the Monster rule.
		Attitudes.register((self, player) -> self instanceof RuinSentinel sentinel && sentinel.isDormant() && !sentinel.isBound() ? Attitude.NEUTRAL : null);
		// Troopers fight a bound construct on sight, any other only while it is awake (5.2).
		Troopers.registerBoundCheck(e -> e instanceof RuinSentinel sentinel && sentinel.isBound());
		Troopers.registerDormancyCheck(e -> e instanceof RuinSentinel sentinel && sentinel.isDormant());

		SentinelCommands.init();
	}

	private ConstructFeature() {
	}
}

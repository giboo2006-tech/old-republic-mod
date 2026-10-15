package com.oldrepublic.enemy;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/** Effect event ids sent by the enemy feature (handled in {@code client.enemy.EnemyEffectsClient}). */
public final class EnemyEffects {
	/** A poacher fired. pos = muzzle, dir = shot direction, entityId = shooter. */
	public static final Identifier BLASTER_FIRE = OldRepublicMod.id("blaster_fire");
	/**
	 * A blaster bolt struck something. pos = impact point, dir = surface normal (or the reversed
	 * flight direction for entities), variant = {@link #IMPACT_BLOCK} or {@link #IMPACT_ENTITY}.
	 */
	public static final Identifier BOLT_IMPACT = OldRepublicMod.id("bolt_impact");

	public static final int IMPACT_BLOCK = 0;
	public static final int IMPACT_ENTITY = 1;

	private EnemyEffects() {
	}
}

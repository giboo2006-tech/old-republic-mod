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

	/**
	 * The Sith Acolyte's Force Shove. Entity: the acolyte. Variant {@link #SHOVE_WINDUP} (intensity =
	 * wind-up ticks; never reduced by presets) or {@link #SHOVE_RELEASE} (dir = shove direction).
	 */
	public static final Identifier ACOLYTE_SHOVE = OldRepublicMod.id("acolyte_shove");
	public static final int SHOVE_WINDUP = 0;
	public static final int SHOVE_RELEASE = 1;
	/** A Push caught the acolyte's shove wind-up: cancelled and staggered. Entity: the acolyte. */
	public static final Identifier ACOLYTE_SHOVE_CLASH = OldRepublicMod.id("acolyte_shove_clash");
	/**
	 * The Poacher Captain's overhead heavy. Entity: the Captain. Variant {@link #HEAVY_WINDUP}
	 * (intensity = wind-up ticks) or {@link #HEAVY_STRIKE}.
	 */
	public static final Identifier CAPTAIN_HEAVY = OldRepublicMod.id("captain_heavy");
	public static final int HEAVY_WINDUP = 0;
	public static final int HEAVY_STRIKE = 1;

	public static final int IMPACT_BLOCK = 0;
	public static final int IMPACT_ENTITY = 1;

	private EnemyEffects() {
	}
}

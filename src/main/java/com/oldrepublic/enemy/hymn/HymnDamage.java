package com.oldrepublic.enemy.hymn;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * The Hymn's damage types ({@code data/oldrepublic/damage_type/}, spec MILESTONE_5.md 18.1); the
 * death messages are in {@code lang-parts/enemy.json} (storm_call) and {@code boss.json} (hymn).
 */
public final class HymnDamage {
	/**
	 * A called lightning strike: the storm-callers' call, Ulvaine's rods, the First Cantor's basalt
	 * verse and the stormback's roar. Dealt with no direct entity, so it has no source position and a
	 * saber guard cannot turn it.
	 */
	public static final ResourceKey<DamageType> STORM_CALL = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("storm_call"));
	/**
	 * The Hymn itself: Ulvaine's pulse and Crescendo, the Cantor's beam, rings and mire fog.
	 */
	public static final ResourceKey<DamageType> HYMN = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("hymn"));

	private HymnDamage() {
	}
}

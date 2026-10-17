package com.oldrepublic.world;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types of the world feature ({@code data/oldrepublic/damage_type/}). */
public final class WorldDamage {
	/** Unstable crystal cluster discharge. */
	public static final ResourceKey<DamageType> CRYSTAL_DISCHARGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("crystal_discharge"));
	/** A tomb vent's miasma puff. */
	public static final ResourceKey<DamageType> TOMB_MIASMA = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("tomb_miasma"));
	/** The dust and grit an urn keeper drops on a wrong push. */
	public static final ResourceKey<DamageType> URN_DUST = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("urn_dust"));

	private WorldDamage() {
	}
}

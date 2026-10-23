package com.oldrepublic.planet;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/** Damage types of the planets ({@code data/oldrepublic/damage_type/}). */
public final class PlanetDamage {
	/** A duskglass cluster's edge (spec MILESTONE_4.md 8.4). */
	public static final ResourceKey<DamageType> DUSKGLASS_CUT = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("duskglass_cut"));

	private PlanetDamage() {
	}
}

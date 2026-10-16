package com.oldrepublic.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Damage types of the force feature ({@code data/oldrepublic/damage_type/}). */
public final class ForceDamage {
	/** Force Lightning arcs, and the caster's own burn after a long channel. Armour applies. */
	public static final ResourceKey<DamageType> LIGHTNING = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_lightning"));
	/** The Crushing Descent shockwave of Force Jump. Armour applies. */
	public static final ResourceKey<DamageType> IMPACT = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_impact"));

	/** A source of {@code type} caused by {@code attacker} (null for the lightning burn). */
	public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
		return attacker == null ? level.damageSources().source(type) : level.damageSources().source(type, attacker, attacker);
	}

	private ForceDamage() {
	}
}

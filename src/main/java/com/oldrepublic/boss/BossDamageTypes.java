package com.oldrepublic.boss;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * The Collector's damage types ({@code data/oldrepublic/damage_type/}, spec 16). The lead's tags put
 * {@code archive_debris} in {@code minecraft:is_projectile} and the other four in
 * {@code oldrepublic:force_harm}.
 */
public final class BossDamageTypes {
	/** The Holocron Surge's wall of dust and debris. */
	public static final ResourceKey<DamageType> COLLECTOR_SURGE = key("collector_surge");
	/** The Resonant Lash's travelling ridge. */
	public static final ResourceKey<DamageType> RESONANT_LASH = key("resonant_lash");
	/** A hurled (or returned) Archive debris chunk. */
	public static final ResourceKey<DamageType> ARCHIVE_DEBRIS = key("archive_debris");
	/** The Archive Grip's squeeze. */
	public static final ResourceKey<DamageType> ARCHIVE_GRIP = key("archive_grip");
	/** The Repelling Palm's shove. */
	public static final ResourceKey<DamageType> REPELLING_PALM = key("repelling_palm");

	/**
	 * A source of {@code type}: {@code direct} is what struck (a debris chunk, or the attacker itself)
	 * and {@code attacker} who is credited.
	 */
	public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity attacker) {
		return direct == null && attacker == null ? level.damageSources().source(type) : level.damageSources().source(type, direct, attacker);
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id(name));
	}

	static void init() {
	}

	private BossDamageTypes() {
	}
}

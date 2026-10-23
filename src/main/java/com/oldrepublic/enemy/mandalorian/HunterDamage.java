package com.oldrepublic.enemy.mandalorian;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * Damage types of the Mandalorian hunter and Torvin Kaast ({@code data/oldrepublic/damage_type/},
 * spec MILESTONE_4.md 10.1 and 10.5). The death messages are in {@code lang-parts/enemy.json} and
 * {@code boss.json}.
 */
public final class HunterDamage {
	/** The wrist flamer's cone: Force Barrier halves it, a parry does nothing, the security droid ignores it. */
	public static final ResourceKey<DamageType> WRIST_FLAMER = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("wrist_flamer"));
	/** The wrist rocket's burst (an explosion: Barrier cuts it from outside). */
	public static final ResourceKey<DamageType> ROCKET = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("mandalorian_rocket"));
	/** A live camp mine going off (Kaast phase 2). */
	public static final ResourceKey<DamageType> CAMP_MINE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("camp_mine"));
	/** Kaast's last jet slam (phase 3). */
	public static final ResourceKey<DamageType> JET_SLAM = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("jet_slam"));

	private HunterDamage() {
	}
}

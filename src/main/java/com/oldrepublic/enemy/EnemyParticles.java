package com.oldrepublic.enemy;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Particle types of the enemy feature. Providers live in {@code client.enemy.EnemyParticleProviders}. */
public final class EnemyParticles {
	/** Red-orange flare at a blaster muzzle: the attack tell and the shot flash. */
	public static final SimpleParticleType MUZZLE_GLOW = register("muzzle_glow");
	/** Hot sparks thrown off where a bolt strikes. */
	public static final SimpleParticleType BOLT_IMPACT = register("bolt_impact");
	/** Dark smoke wisp with a faint ember, left on blocks a bolt has scorched. */
	public static final SimpleParticleType BOLT_SCORCH = register("bolt_scorch");
	/** Soft teal-green kolto mist: a medic's heal and a trooper tending the downed. */
	public static final SimpleParticleType KOLTO_MIST = register("kolto_mist");
	/** White steam jetting from a venting Republic rifle; also the bent-air streak of a redirected grenade. */
	public static final SimpleParticleType VENT_STEAM = register("vent_steam");
	/** Grey-brown smoke: the grenade fuse's thin curl and its burst, and the flare's trail. */
	public static final SimpleParticleType GRENADE_SMOKE = register("grenade_smoke");
	/** A bright falling ember: the Imperial flare, grenade burst sparks and the rally motes. */
	public static final SimpleParticleType FLARE_EMBER = register("flare_ember");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, OldRepublicMod.id(name), FabricParticleTypes.simple());
	}

	static void init() {
	}

	private EnemyParticles() {
	}
}

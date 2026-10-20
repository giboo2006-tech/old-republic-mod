package com.oldrepublic.client.planet;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.PlanetEffects;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the Milestone 5 planet effect events (spec MILESTONE_5.md 15.3; ids in
 * {@link PlanetEffects}):
 * <ul>
 * <li>{@code sandstorm} 0 and 1: the storm's cue to {@link Sandstorm} (the wind rises with it or fades;
 * the fog and the sand follow the synced state).</li>
 * <li>{@code verse_stone} 0 to 5: {@link VerseStoneClient}.</li>
 * <li>{@code stormglass} 0: static crawls over the struck basalt (6 {@code lightning_spark}; steady
 * {@code crystal_mote} with flashing effects off), the new cluster pushes up through a little of its
 * own dust, {@code stormglass.form}.</li>
 * <li>{@code site_door} 0 to 3: 16 dust of the door's block over 20 ticks along the door, and
 * {@code holdfast.gate} (the gate's plates slide) or {@code hymnal.door} (the Hymnal's stone grinds,
 * its grit falling); a door settling shut (intensity below 0) sounds a little lower.</li>
 * </ul>
 */
final class PlanetM5EffectHandlers {
	private static final int DOOR_DUST = 16;
	private static final int DOOR_DUST_TICKS = 20;
	private static final int STORMGLASS_SPARKS = 6;
	private static final int STORMGLASS_CRAWL_TICKS = 10;

	static void init() {
		ClientEffects.register(PlanetEffects.SANDSTORM, PlanetM5EffectHandlers::sandstorm);
		ClientEffects.register(PlanetEffects.VERSE_STONE, PlanetM5EffectHandlers::verseStone);
		ClientEffects.register(PlanetEffects.STORMGLASS, PlanetM5EffectHandlers::stormglass);
		ClientEffects.register(PlanetEffects.SITE_DOOR, PlanetM5EffectHandlers::siteDoor);
	}

	private static void sandstorm(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Sandstorm.INSTANCE.cue(client, p.variant() == PlanetEffects.SANDSTORM_START);
	}

	private static void verseStone(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		VerseStoneClient.INSTANCE.effect(client, level, p);
	}

	private static void stormglass(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		sound(level, pos, ModSounds.STORMGLASS_FORM, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();
		// The struck basalt's top face, just under the new cluster.
		double top = Math.floor(pos.y) + 0.02;
		ParticleOptions crawl = Vfx.flashing() ? ForceParticles.LIGHTNING_SPARK : WorldParticles.CRYSTAL_MOTE;

		// The static crawls for half a second rather than cracking once.
		Bursts.add(level, STORMGLASS_SPARKS, STORMGLASS_CRAWL_TICKS, r -> {
			double angle = r.nextDouble() * Mth.TWO_PI;
			double radius = 0.3 + r.nextDouble() * 0.7;
			level.addParticle(crawl, pos.x + Math.cos(angle) * radius, top, pos.z + Math.sin(angle) * radius, -Math.sin(angle) * 0.04, 0.005,
					Math.cos(angle) * 0.04);
		});

		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, PlanetBlocks.STORMGLASS_CLUSTER.defaultBlockState());

		for (int i = 0; i < Vfx.count(4); i++) {
			level.addParticle(dust, pos.x + (random.nextDouble() - 0.5) * 0.6, top + 0.1, pos.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.12, 0);
		}
	}

	private static void siteDoor(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		boolean gate = p.variant() == PlanetEffects.DOOR_GATE_SHUT || p.variant() == PlanetEffects.DOOR_GATE_OPEN;
		boolean settling = p.variant() == PlanetEffects.DOOR_GATE_SHUT || p.intensity() < 0;
		sound(level, pos, gate ? ModSounds.HOLDFAST_GATE : ModSounds.HYMNAL_DOOR, 1f, settling ? 0.88f : 1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		BlockState block = gate ? PlanetBlocks.HOLDFAST_GATE.defaultBlockState() : PlanetBlocks.SEALED_PLATING.defaultBlockState();
		// A gate's plates shed flakes off their faces; the Hymnal's stone sheds falling grit.
		BlockParticleOption dust = new BlockParticleOption(gate ? ParticleTypes.BLOCK : ParticleTypes.FALLING_DUST, block);
		// The door's extent grows with the cells moved: a wide gate, a tall stone door.
		double half = Mth.clamp(Math.sqrt(Math.abs(p.intensity())) * 0.6, 0.8, 2.5);
		Bursts.add(level, DOOR_DUST, DOOR_DUST_TICKS, random -> {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 2 * half, (random.nextDouble() - 0.3) * 2 * half, (random.nextDouble() - 0.5) * 2 * half);
			level.addParticle(dust, at.x, at.y, at.z, 0, gate ? 0.02 : 0, 0);
		});
	}

	private static void sound(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		float scaled = volume * ClientConfig.get().modSoundVolume;

		if (scaled > 0.001f) {
			level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, scaled, pitch, false);
		}
	}

	private PlanetM5EffectHandlers() {
	}
}

package com.oldrepublic.client.planet;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.planet.PlanetAmbience.Timed;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetEffects;
import com.oldrepublic.planet.PlanetParticles;
import com.oldrepublic.planet.block.AureliteOreBlock;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.block.CrystalFormationBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the planet effect events (spec MILESTONE_4.md 13.3; ids in {@link PlanetEffects}).
 * <ul>
 * <li>{@code waystone} 0: the corrupt refresh, handed to {@link PlanetAmbience} (loop and vein
 * motes, kept alive for the intensity in seconds). 1: the cleanse: grit drops from the stone,
 * {@code waystone.cleanse}, the veins drain as dark dust over a second and the stone's own
 * {@code waystone.hum} returns with pale motes; the cleanser's own loop fades.</li>
 * <li>{@code heartstone} 0: a wave of dark mist from uneven vents around the stone's base for two
 * seconds, stronger each wave, {@code heartstone.wave} and a light shake nearby. 1: the slow exhale
 * of pale motes over four seconds and {@code heartstone.cleansed}.</li>
 * <li>{@code crystal_answer} 0: a Hollow chime pitched by its tone (1 to 3) with aurelite glints in
 * time; tone 0 is a low, dull clunk with a little grit. 1: the rift crystal's bleed (begin: the
 * whispers and the darkening for as long as it is held; done: {@code rift.bleed} and a burst of
 * black slivers and red; broken: the whispers stop). 2: the Hollow answers together.</li>
 * </ul>
 * With flashing effects off, glints become steady motes. Every loop and timed effect stops with
 * {@link PlanetAmbience#reset()} on disconnect and level change.
 */
final class PlanetEffectHandlers {
	/** The Hollow chime's pitches by tone: a falling third, the root, a rising third. */
	private static final float[] TONE_PITCH = { 0.5f, 0.794f, 1f, 1.26f };
	private static final int ANSWER_SCAN = 10;
	private static final int ANSWER_MAX_POINTS = 48;
	private static final int BLEED_SPARE_TICKS = 100;
	private static final int BLEED_COLOR = 0x8C1414;
	private static final int VEIN_COLOR = 0x2A1220;

	static void init() {
		ClientEffects.register(PlanetEffects.WAYSTONE, PlanetEffectHandlers::waystone);
		ClientEffects.register(PlanetEffects.HEARTSTONE, PlanetEffectHandlers::heartstone);
		ClientEffects.register(PlanetEffects.CRYSTAL_ANSWER, PlanetEffectHandlers::crystalAnswer);
	}

	private static void waystone(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();

		if (p.variant() == PlanetEffects.WAYSTONE_CORRUPT) {
			PlanetAmbience.INSTANCE.corrupt(level, pos, p.intensity());
			return;
		}

		if (p.variant() != PlanetEffects.WAYSTONE_CLEANSE) {
			return;
		}

		if (client.player != null && p.entityId() == client.player.getId()) {
			PlanetAmbience.INSTANCE.cleansed(pos);
		}

		sound(level, pos, ModSounds.WAYSTONE_CLEANSE, 1f, 1f);
		PlanetAmbience.INSTANCE.addTimed(Timed.Kind.CLEANSE_DRAIN, pos, p.entityId(), 30, 1f, List.of());

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();
		BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.BLACKSTONE.defaultBlockState());

		// The stone shudders: black grit sifts off its faces.
		for (int i = 0; i < Vfx.count(14); i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 1.3, -0.8 + random.nextDouble() * 3, (random.nextDouble() - 0.5) * 1.3);
			level.addAlwaysVisibleParticle(grit, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	private static void heartstone(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		RandomSource random = level.getRandom();

		if (p.variant() == PlanetEffects.HEARTSTONE_WAVE) {
			int wave = Mth.clamp(Math.round(p.intensity()), 1, 3);
			sound(level, pos, ModSounds.HEARTSTONE_WAVE, 0.8f + wave * 0.1f, 1.08f - wave * 0.06f);
			PlanetAmbience.INSTANCE.addTimed(Timed.Kind.HEART_WAVE, pos, -1, 40, wave, List.of());

			if (Vfx.inRange(pos)) {
				// The first welling: a ragged burst at the vents before the mist settles in.
				for (int i = 0; i < Vfx.count(6 + wave * 4); i++) {
					PlanetAmbience.mistAtBase(level, pos.add(0, 1, 0), random, 1.4, 4, 0.03 + wave * 0.01);
				}
			}

			if (client.player != null) {
				double distance = client.player.position().distanceTo(pos);

				if (distance < 12) {
					CameraShake.add((0.06f + wave * 0.04f) * (float) (1 - distance / 12));
				}
			}

			return;
		}

		if (p.variant() != PlanetEffects.HEARTSTONE_COMPLETE) {
			return;
		}

		if (client.player != null && p.entityId() == client.player.getId()) {
			PlanetAmbience.INSTANCE.cleansed(pos);
		}

		PlanetAmbience.INSTANCE.clearTimed(Timed.Kind.HEART_WAVE, pos.add(0, -0.5, 0));
		sound(level, pos, ModSounds.HEARTSTONE_CLEANSED, 1f, 1f);
		PlanetAmbience.INSTANCE.addTimed(Timed.Kind.HEART_EXHALE, pos, p.entityId(), 80, 1f, List.of());
	}

	private static void crystalAnswer(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		switch (p.variant()) {
			case PlanetEffects.CRYSTAL_CHIME -> chime(level, p.pos(), Math.round(p.intensity()));
			case PlanetEffects.CRYSTAL_BLEED -> bleed(level, p.pos(), p.entityId(), p.intensity());
			case PlanetEffects.CRYSTAL_ANSWER_ALL -> answerAll(level, p.pos(), p.entityId());
			default -> {
			}
		}
	}

	/** A Hollow cluster chimes at its tone; tone 0 is a wrong stone's dull clunk. */
	private static void chime(ClientLevel level, Vec3 pos, int tone) {
		RandomSource random = level.getRandom();

		if (tone <= 0) {
			sound(level, pos, ModSounds.HOLLOW_CHIME, 0.7f, TONE_PITCH[0]);

			if (Vfx.inRange(pos)) {
				BlockParticleOption grit = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState());

				for (int i = 0; i < Vfx.count(6); i++) {
					Vec3 at = pos.add((random.nextDouble() - 0.5) * 0.9, 0.4 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.9);
					level.addParticle(grit, at.x, at.y, at.z, 0, 0, 0);
				}
			}

			return;
		}

		int clamped = Math.min(tone, 3);
		sound(level, pos, ModSounds.HOLLOW_CHIME, 1f, TONE_PITCH[clamped]);

		if (!Vfx.inRange(pos)) {
			return;
		}

		int glints = Vfx.flashing() ? 3 + clamped * 2 : 2;

		for (int i = 0; i < glints; i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 1.2);
			level.addAlwaysVisibleParticle(PlanetParticles.AURELITE_GLINT, at.x, at.y, at.z, 0, 0, 0);
		}

		for (int i = 0; i < Vfx.count(4 + clamped * 2); i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8);
			level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.01 + random.nextDouble() * 0.01, 0);
		}
	}

	/** The Hollow's crystals answer together: {@code hollow.answer} and glints running over the clusters around. */
	private static void answerAll(ClientLevel level, Vec3 pos, int owner) {
		sound(level, pos, ModSounds.HOLLOW_ANSWER, 1f, 1f);
		PlanetAmbience.INSTANCE.addTimed(Timed.Kind.ANSWER, pos, owner, 40, 1f, crystalsAround(level, BlockPos.containing(pos)));
	}

	/** The rift crystal's bleed for the holder {@code owner}, by stage. */
	private static void bleed(ClientLevel level, Vec3 pos, int owner, float stage) {
		if (stage == PlanetEffects.BLEED_BEGIN) {
			PlanetAmbience.INSTANCE.whisper(level, pos, 0.5f);
			int hold = Math.max(20, PlanetBalance.get().riftBleedTicks);
			// The hold length is the server's; this only shapes the darkening and lets a lost "done" lapse.
			PlanetAmbience.INSTANCE.addTimed(Timed.Kind.BLEED, pos, owner, hold + BLEED_SPARE_TICKS, hold, List.of());
			return;
		}

		// "Broken" comes from where the holder stood (or logged out), so the bleed is found by its holder.
		PlanetAmbience.INSTANCE.stopWhisper();
		PlanetAmbience.INSTANCE.clearOwned(Timed.Kind.BLEED, owner);

		if (stage != PlanetEffects.BLEED_DONE) {
			return;
		}

		sound(level, pos, ModSounds.RIFT_BLEED, 1f, 1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < Vfx.count(18); i++) {
			Vec3 v = PlanetAmbience.randomDirection(random).scale(0.12 + random.nextDouble() * 0.1);
			level.addAlwaysVisibleParticle(PlanetParticles.DUSKGLASS_SHARD, pos.x, pos.y, pos.z, v.x, Math.abs(v.y) + 0.08, v.z);
		}

		for (int i = 0; i < Vfx.count(14); i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
			level.addAlwaysVisibleParticle(new DustParticleOptions(BLEED_COLOR, 1.2f), at.x, at.y, at.z, 0, -0.03, 0);
		}

		for (int i = 0; i < Vfx.count(8); i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.5) * 1.4);
			level.addAlwaysVisibleParticle(new DustParticleOptions(VEIN_COLOR, 1.4f), at.x, at.y, at.z, 0, 0.01, 0);
		}
	}

	/**
	 * Points to glint at for the answer: the centres of crystal clusters and the open faces of
	 * aurelite within {@link #ANSWER_SCAN} blocks (at most {@link #ANSWER_MAX_POINTS}).
	 */
	private static List<Vec3> crystalsAround(ClientLevel level, BlockPos centre) {
		List<Vec3> found = new ArrayList<>();
		BlockPos from = centre.offset(-ANSWER_SCAN, -ANSWER_SCAN, -ANSWER_SCAN);
		BlockPos to = centre.offset(ANSWER_SCAN, ANSWER_SCAN, ANSWER_SCAN);

		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			BlockState state = level.getBlockState(pos);

			if (state.getBlock() instanceof CrystalFormationBlock) {
				found.add(Vec3.atCenterOf(pos));
			} else if (state.getBlock() instanceof AureliteOreBlock) {
				for (Direction face : Direction.values()) {
					if (level.getBlockState(pos.relative(face)).isAir()) {
						found.add(Vec3.atCenterOf(pos).add(face.getStepX() * 0.55, face.getStepY() * 0.55, face.getStepZ() * 0.55));
						break;
					}
				}
			}

			if (found.size() >= ANSWER_MAX_POINTS) {
				break;
			}
		}

		return found;
	}

	private static void sound(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		float scaled = volume * ClientConfig.get().modSoundVolume;

		if (scaled > 0.001f) {
			level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, scaled, pitch, false);
		}
	}

	private PlanetEffectHandlers() {
	}
}

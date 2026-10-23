package com.oldrepublic.client.world;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.world.WorldAmbience.TimedEffect;
import com.oldrepublic.client.world.puzzle.PuzzleEffectHandlers;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldEffects;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.puzzle.PuzzleEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client side of every world effect event (see {@link WorldEffects}). */
final class WorldEffectHandlers {
	static void init() {
		ClientEffects.register(WorldEffects.CRYSTAL_UNSTABLE_CHARGE, (client, level, p, entity) -> {
			sound(level, p.pos(), ModSounds.CRYSTAL_UNSTABLE_CHARGE, 1f, 1f);
			WorldAmbience.INSTANCE.addTimed(TimedEffect.Kind.UNSTABLE_CHARGE, p.pos(), p.dir(), Math.max(1, Math.round(p.intensity() * 20)));
		});
		ClientEffects.register(WorldEffects.CRYSTAL_DISCHARGE, WorldEffectHandlers::discharge);
		ClientEffects.register(WorldEffects.SEAL_OPEN, WorldEffectHandlers::sealOpen);
		ClientEffects.register(WorldEffects.SEAL_REFUSE, WorldEffectHandlers::sealRefuse);
		ClientEffects.register(WorldEffects.TRIAL_BEGIN, WorldEffectHandlers::trialBegin);
		ClientEffects.register(WorldEffects.TRIAL_ECHO_FORM, WorldEffectHandlers::echoForm);
		ClientEffects.register(WorldEffects.TRIAL_ECHO_LUNGE, WorldEffectHandlers::echoLunge);
		ClientEffects.register(WorldEffects.TRIAL_ECHO_PUSH, WorldEffectHandlers::echoPush);
		ClientEffects.register(WorldEffects.TRIAL_ECHO_DISSOLVE, WorldEffectHandlers::echoDissolve);
		ClientEffects.register(WorldEffects.TRIAL_COMPLETE, WorldEffectHandlers::trialComplete);
		ClientEffects.register(WorldEffects.CRYSTAL_FORM, WorldEffectHandlers::crystalForm);
		ClientEffects.register(WorldEffects.CAVE_AMBIENCE, (client, level, p, entity) -> WorldAmbience.INSTANCE.setInCave(p.variant() == 1));
	}

	private static void discharge(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		WorldAmbience.INSTANCE.clearTimed(TimedEffect.Kind.UNSTABLE_CHARGE, pos);
		sound(level, pos, ModSounds.CRYSTAL_DISCHARGE, 1f, 0.95f + level.getRandom().nextFloat() * 0.1f);
		RandomSource random = level.getRandom();
		double radius = Math.max(1, p.intensity());

		if (Vfx.inRange(pos)) {
			for (int i = 0, n = Vfx.count(32); i < n; i++) {
				Vec3 v = WorldAmbience.randomDirection(random).scale(0.25 + random.nextDouble() * 0.25 * radius / 2.5);
				level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_SPARK, pos.x, pos.y, pos.z, v.x, v.y + 0.1, v.z);
			}

			int glints = Vfx.flashing() ? 6 : 2;

			for (int i = 0; i < glints; i++) {
				Vec3 at = WorldAmbience.jitter(pos, random, Vfx.flashing() ? 1.2 : 0.4);
				level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0, 0);
			}
		}

		if (client.player != null) {
			double distance = client.player.position().distanceTo(pos);

			if (distance < radius + 1.5) {
				CameraShake.add(0.35f * (float) (1 - distance / (radius + 1.5)) + 0.1f);
			}
		}
	}

	private static void sealOpen(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		if (p.variant() == PuzzleEffects.SEAL_PUZZLE) {
			PuzzleEffectHandlers.puzzleSealOpen(client, level, p, entity);
			return;
		}

		Vec3 pos = p.pos();
		sound(level, pos, ModSounds.CRYSTAL_RESONANCE, 1f, 0.6f);
		sound(level, pos, ModSounds.CRYSTAL_CHIME, 0.8f, 0.7f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();

		// Dust shaken loose from the frame, and a sweep of glints across the face.
		for (int i = 0, n = Vfx.count(24); i < n; i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 3, 1.4 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 3);
			level.addParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, -0.02 - random.nextDouble() * 0.02, 0);
		}

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 3, (random.nextDouble() - 0.5) * 3, (random.nextDouble() - 0.5) * 3);
			level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	private static void sealRefuse(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		sound(level, pos, ModSounds.FORCE_FAIL, 0.7f, 0.8f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();
		Vec3 toward = p.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : p.dir().normalize();

		// A few motes rise to meet the hand, then bounce off.
		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 at = pos.add(toward.scale(0.55)).add(WorldAmbience.jitter(Vec3.ZERO, random, 0.8));
			Vec3 v = toward.scale(0.03 + random.nextDouble() * 0.03).add(0, 0.01, 0);
			level.addParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void trialBegin(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 top = p.pos();
		double radius = Math.max(3, p.intensity());
		WorldAmbience.INSTANCE.trialBegin(level, top, radius);
		sound(level, top, ModSounds.TRIAL_BEGIN, 1f, 1f);

		if (!Vfx.inRange(top)) {
			return;
		}

		RandomSource random = level.getRandom();

		// The light gutters: wisps rise from the floor along the chamber wall.
		for (int i = 0, n = Vfx.count(28); i < n; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double r = radius - 3 - random.nextDouble() * 1.5;
			Vec3 at = top.add(Math.cos(angle) * r, -0.9, Math.sin(angle) * r);
			level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, 0, 0.03 + random.nextDouble() * 0.03, 0);
		}
	}

	private static void echoForm(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();

		if (p.variant() == 0) {
			sound(level, pos, ModSounds.TRIAL_ECHO_FORM, SoundSource.HOSTILE, 1f, 1f);
			WorldAmbience.INSTANCE.addTimed(TimedEffect.Kind.ECHO_GATHER, pos, null, Math.max(1, Math.round(p.intensity() * 20)));
			return;
		}

		WorldAmbience.INSTANCE.clearTimed(TimedEffect.Kind.ECHO_GATHER, pos);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(20); i < n; i++) {
			Vec3 v = WorldAmbience.randomDirection(random).scale(0.08);
			Vec3 at = pos.add(0, random.nextDouble() * 1.8, 0);
			level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, Math.abs(v.y) * 0.5, v.z);
		}
	}

	private static void echoLunge(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		RandomSource random = level.getRandom();

		if (p.variant() == 0) {
			sound(level, pos, ModSounds.TRIAL_ECHO_LUNGE, SoundSource.HOSTILE, 1f, 1f);

			if (Vfx.inRange(pos)) {
				// Wisps pool at the feet: the tell.
				for (int i = 0, n = Vfx.count(12); i < n; i++) {
					double angle = random.nextDouble() * Mth.TWO_PI;
					Vec3 at = pos.add(Math.cos(angle) * 1.2, 0.1, Math.sin(angle) * 1.2);
					Vec3 v = pos.subtract(at).scale(0.07);
					level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, 0.01, v.z);
				}
			}

			return;
		}

		if (!Vfx.inRange(pos)) {
			return;
		}

		Vec3 dir = p.dir();

		// A smear of shadow along the dash.
		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			double along = random.nextDouble() * 4;
			Vec3 at = pos.add(dir.scale(along)).add(0, 0.3 + random.nextDouble() * 1.3, 0);
			level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, -dir.x * 0.03, 0.01, -dir.z * 0.03);
		}
	}

	private static void echoPush(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 eye = p.pos();
		Vec3 dir = p.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : p.dir().normalize();
		RandomSource random = level.getRandom();

		if (p.variant() == 0) {
			sound(level, eye, ModSounds.FORCE_PUSH_CHARGE, SoundSource.HOSTILE, 0.9f, 0.7f);
			return;
		}

		sound(level, eye, ModSounds.FORCE_PUSH_RELEASE, SoundSource.HOSTILE, 1f, 0.75f);
		double range = Math.max(2, p.intensity());

		if (Vfx.inRange(eye)) {
			// A dark, widening cone: the mirror of the player's own push.
			for (int i = 0, n = Vfx.count(26); i < n; i++) {
				double along = random.nextDouble();
				double spread = 0.2 + along * range * 0.45;
				Vec3 at = eye.add(dir.scale(0.6 + along * range * 0.3)).add(WorldAmbience.jitter(Vec3.ZERO, random, spread));
				Vec3 v = dir.scale(0.35 + random.nextDouble() * 0.25);
				level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, v.y, v.z);
			}
		}

		if (client.player != null) {
			Vec3 toPlayer = client.player.getEyePosition().subtract(eye);
			double distance = toPlayer.length();

			if (distance < range && distance > 1.0e-3 && toPlayer.normalize().dot(dir) > 0.8) {
				CameraShake.add(0.3f);
			}
		}
	}

	private static void echoDissolve(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		boolean defeated = p.variant() == WorldEffects.DISSOLVE_DEFEATED;
		sound(level, pos, ModSounds.TRIAL_ECHO_FORM, SoundSource.HOSTILE, defeated ? 1f : 0.6f, defeated ? 1.35f : 0.6f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();
		int wisps = Vfx.count(defeated ? 36 : 16);

		for (int i = 0; i < wisps; i++) {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 0.7, random.nextDouble() * 1.9, (random.nextDouble() - 0.5) * 0.7);
			Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.06, defeated ? 0.04 + random.nextDouble() * 0.05 : -0.01, (random.nextDouble() - 0.5) * 0.06);
			level.addAlwaysVisibleParticle(WorldParticles.SHADOW_WISP, at.x, at.y, at.z, v.x, v.y, v.z);
		}

		if (defeated) {
			// The reflection turns to light.
			for (int i = 0, n = Vfx.count(14); i < n; i++) {
				Vec3 at = pos.add((random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 1.9, (random.nextDouble() - 0.5) * 0.6);
				level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.03 + random.nextDouble() * 0.03, 0);
			}
		}
	}

	private static void trialComplete(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 top = p.pos();
		WorldAmbience.INSTANCE.trialEnd(top);
		WorldAmbience.INSTANCE.clearTimed(TimedEffect.Kind.ECHO_GATHER, top);

		if (p.variant() != WorldEffects.COMPLETE_SUCCESS) {
			return;
		}

		sound(level, top, ModSounds.TRIAL_COMPLETE, 1f, 1f);

		if (!Vfx.inRange(top)) {
			return;
		}

		RandomSource random = level.getRandom();

		// The chamber brightens: motes rise everywhere and drift toward the stone.
		for (int i = 0, n = Vfx.count(40); i < n; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double r = 1 + random.nextDouble() * 6;
			Vec3 at = top.add(Math.cos(angle) * r, -0.9 + random.nextDouble() * 2, Math.sin(angle) * r);
			Vec3 v = top.subtract(at).scale(0.012).add(0, 0.02, 0);
			level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void crystalForm(Minecraft client, ClientLevel level, EffectEventPayload p, Entity entity) {
		Vec3 pos = p.pos();
		sound(level, pos, ModSounds.CRYSTAL_FORM, 1f, 1f);
		WorldAmbience.INSTANCE.glowWhilePresent(p.entityId(), pos);

		if (!Vfx.inRange(pos)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(18); i < n; i++) {
			Vec3 from = pos.add(WorldAmbience.randomDirection(random).scale(1.5 + random.nextDouble()));
			Vec3 v = pos.subtract(from).scale(0.09);
			level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, from.x, from.y, from.z, v.x, v.y, v.z);
		}

		int glints = Vfx.flashing() ? 8 : 3;

		for (int i = 0; i < glints; i++) {
			Vec3 at = WorldAmbience.jitter(pos, random, 0.5);
			level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	private static void sound(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		sound(level, pos, sound, SoundSource.BLOCKS, volume, pitch);
	}

	private static void sound(ClientLevel level, Vec3 pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
		float scaled = volume * ClientConfig.get().modSoundVolume;

		if (scaled > 0.001f) {
			level.playLocalSound(pos.x, pos.y, pos.z, sound, source, scaled, pitch, false);
		}
	}

	private WorldEffectHandlers() {
	}
}

package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers for the saber effect events: sparks in the blade colour, the contact flash
 * (one tick, or a soft glow when flashing effects are off), sounds and camera kicks. Clash and
 * deflect sounds are limited to one per entity every {@value #SOUND_GAP} ticks.
 */
final class SaberFx {
	private static final int SOUND_GAP = 3;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<Integer, Long> LAST_SOUND = new HashMap<>();

	static void init() {
		ClientEffects.register(SaberEffects.CLASH, SaberFx::onClash);
		ClientEffects.register(SaberEffects.DEFLECT, SaberFx::onDeflect);
		ClientEffects.register(SaberEffects.PARRY, SaberFx::onParry);
		ClientEffects.register(SaberEffects.GUARD_BREAK, SaberFx::onGuardBreak);
		ClientEffects.register(SaberEffects.HIT, SaberFx::onHit);
		ClientEffects.register(SaberEffects.WORKBENCH_ASSEMBLE, SaberFx::onAssemble);
	}

	private static void onClash(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();
		Vec3 normal = p.dir().scale(-1);

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), normal, 0.35, Vfx.count(14), color);
			flash(p.pos(), color);
		}

		limitedSound(level, p, ModSounds.SABER_CLASH, 1f, 0.9f + RANDOM.nextFloat() * 0.2f);
		shakeIfSelf(mc, entity, 0.15f);
	}

	private static void onDeflect(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir(), 0.25, Vfx.count(12), color);
			flash(p.pos(), color);
		}

		limitedSound(level, p, ModSounds.SABER_DEFLECT, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
	}

	private static void onParry(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir(), 0.6, Vfx.count(20), color);
			flash(p.pos(), color);
		}

		play(level, p.pos(), ModSounds.SABER_PARRY, 1f, 1f);
		shakeIfSelf(mc, entity, 0.1f);
	}

	private static void onGuardBreak(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();
		Vec3 front = p.pos().add(p.dir().scale(0.6));

		if (Vfx.inRange(front)) {
			sparks(front, new Vec3(0, 1, 0), 1.0, Vfx.count(26), color);
			flash(front, color);
		}

		play(level, p.pos(), ModSounds.SABER_GUARD_BREAK, 1.1f, 1f);
		shakeIfSelf(mc, entity, 0.35f);
	}

	private static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir().scale(-1), 0.5, Vfx.count(8), color);
		}

		play(level, p.pos(), ModSounds.SABER_HIT, 0.9f, 0.95f + RANDOM.nextFloat() * 0.1f);
	}

	/** Assembly at the workbench: the assemble sting and a burst of crystal glints above the bench. */
	private static void onAssemble(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.WORKBENCH_ASSEMBLE, 1f, 1f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		ParticleOptions glint = crystalGlint();

		for (int i = 0; i < Vfx.count(16); i++) {
			Vec3 v = randomDirection().scale(0.04 + RANDOM.nextDouble() * 0.05).add(0, 0.03, 0);
			level.addParticle(glint, p.pos().x, p.pos().y, p.pos().z, v.x, v.y, v.z);
		}

		sparks(p.pos(), new Vec3(0, 1, 0), 0.9, Vfx.count(10), BladeColor.byIndex(p.variant()).rgb());
	}

	/** The world feature's {@code crystal_glint}, or the end rod sparkle if it is missing. */
	private static ParticleOptions crystalGlint() {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id("crystal_glint")) instanceof ParticleOptions options
				? options
				: ParticleTypes.END_ROD;
	}

	/** Sparks in a cone of {@code spread} around {@code dir}. */
	private static void sparks(Vec3 pos, Vec3 dir, double spread, int count, int color) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 axis = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : dir.normalize();

		for (int i = 0; i < count; i++) {
			Vec3 v = axis.add(randomDirection().scale(spread)).normalize().scale(0.15 + RANDOM.nextDouble() * 0.2);
			Particle particle = mc.particleEngine.createParticle(SaberParticles.SABER_SPARK, pos.x, pos.y, pos.z, v.x, v.y, v.z);

			if (particle instanceof SaberParticleProviders.SaberParticle spark) {
				spark.tint(color);
			}
		}
	}

	/** One-tick contact flash, or a soft steady glow when flashing effects are disabled. */
	private static void flash(Vec3 pos, int color) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(SaberParticles.CONTACT_FLASH, pos.x, pos.y, pos.z, 0, 0, 0);

		if (particle instanceof SaberParticleProviders.Flash flash) {
			flash.tint(color);

			if (!Vfx.flashing()) {
				flash.soft();
			}
		}
	}

	private static Vec3 randomDirection() {
		return new Vec3(RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1);
	}

	private static void shakeIfSelf(Minecraft mc, @Nullable Entity entity, float intensity) {
		if (entity != null && entity == mc.player) {
			CameraShake.add(intensity);
		}
	}

	private static void limitedSound(ClientLevel level, EffectEventPayload p, SoundEvent sound, float volume, float pitch) {
		long now = level.getGameTime();
		Long last = LAST_SOUND.get(p.entityId());

		if (last != null && now - last < SOUND_GAP && now >= last) {
			return;
		}

		LAST_SOUND.put(p.entityId(), now);
		play(level, p.pos(), sound, volume, pitch);
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Forgets rate-limit state (disconnect). */
	static void clear() {
		LAST_SOUND.clear();
	}

	private SaberFx() {
	}
}

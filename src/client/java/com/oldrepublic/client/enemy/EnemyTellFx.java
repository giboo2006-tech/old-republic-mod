package com.oldrepublic.client.enemy;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.EnemyEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers for the Sith Acolyte's Force Shove and the Poacher Captain's heavy: the wind-up
 * tells (sound, dark ripples at the palm, the glint over the raised vibroblade) are never reduced
 * by the particle presets; the release and clash effects are.
 */
final class EnemyTellFx {
	/** Deep red-black of the shove ripples. */
	private static final DustParticleOptions SHOVE_DUST = new DustParticleOptions(0x3A0A12, 1.2f);
	private static final RandomSource RANDOM = RandomSource.create();

	static void init() {
		ClientEffects.register(EnemyEffects.ACOLYTE_SHOVE, EnemyTellFx::onShove);
		ClientEffects.register(EnemyEffects.ACOLYTE_SHOVE_CLASH, EnemyTellFx::onClash);
		ClientEffects.register(EnemyEffects.CAPTAIN_HEAVY, EnemyTellFx::onCaptainHeavy);
	}

	private static void onShove(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 palm = p.pos();

		if (p.variant() == EnemyEffects.SHOVE_WINDUP) {
			play(level, palm, ModSounds.ACOLYTE_SHOVE_WINDUP, 1f, 1f);

			// The tell: a ring of dark ripples gathering at the palm, and dust kicked up at the feet.
			for (int i = 0; i < 10; i++) {
				double a = i * Mth.TWO_PI / 10;
				Vec3 offset = new Vec3(Math.cos(a) * 0.45, Math.sin(a) * 0.45, 0).yRot((float) -Math.atan2(p.dir().x, p.dir().z));
				Vec3 v = offset.scale(-0.08);
				level.addParticle(SHOVE_DUST, palm.x + offset.x, palm.y + offset.y, palm.z + offset.z, v.x, v.y, v.z);
			}

			if (entity != null) {
				for (int i = 0; i < 6; i++) {
					level.addParticle(ParticleTypes.POOF, entity.getX() + (RANDOM.nextDouble() - 0.5), entity.getY() + 0.1,
							entity.getZ() + (RANDOM.nextDouble() - 0.5), 0, 0.02, 0);
				}
			}

			return;
		}

		play(level, palm, ModSounds.ACOLYTE_SHOVE, 1.1f, 0.95f + RANDOM.nextFloat() * 0.1f);

		if (!Vfx.inRange(palm)) {
			return;
		}

		// The release: a cone of ripples rolling out along the shove.
		Vec3 dir = p.dir().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : p.dir().normalize();

		for (int i = 0, n = Vfx.count(24); i < n; i++) {
			Vec3 spread = new Vec3(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5).scale(0.6);
			Vec3 v = dir.add(spread).normalize().scale(0.35 + RANDOM.nextDouble() * 0.25);
			level.addParticle(SHOVE_DUST, palm.x, palm.y, palm.z, v.x, v.y, v.z);
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			Vec3 v = dir.scale(0.2 + RANDOM.nextDouble() * 0.2);
			level.addParticle(ParticleTypes.CLOUD, palm.x, palm.y, palm.z, v.x, v.y + 0.02, v.z);
		}

		if (entity != null && mc.player != null && mc.player.distanceTo(entity) < 7) {
			CameraShake.add(0.15f);
		}
	}

	/** The push clash: the shove snuffed out in a burst, the acolyte reeling. */
	private static void onClash(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.ACOLYTE_SHOVE, 1f, 0.6f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		for (int i = 0, n = Vfx.count(18); i < n; i++) {
			Vec3 v = new Vec3(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5).scale(0.5);
			level.addParticle(SHOVE_DUST, p.pos().x, p.pos().y, p.pos().z, v.x, v.y, v.z);
		}

		level.addParticle(ParticleTypes.EXPLOSION, p.pos().x, p.pos().y, p.pos().z, 0, 0, 0);
	}

	/** The Captain's heavy: the wind-up growl and a glint over the raised blade, then the strike's kick. */
	private static void onCaptainHeavy(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == EnemyEffects.HEAVY_WINDUP) {
			play(level, p.pos(), ModSounds.CAPTAIN_HEAVY_WINDUP, 1f, 1f);

			if (entity != null) {
				Vec3 tip = entity.position().add(0, entity.getBbHeight() + 0.7, 0);
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 0, 0.02, 0);
				level.addParticle(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0, 0.01, 0);
			}

			return;
		}

		if (entity != null && mc.player != null && mc.player.distanceTo(entity) < 4) {
			CameraShake.add(0.2f);
		}

		if (entity != null && Vfx.inRange(entity.position())) {
			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				level.addParticle(ParticleTypes.POOF, entity.getX() + (RANDOM.nextDouble() - 0.5) * 1.2, entity.getY() + 0.1,
						entity.getZ() + (RANDOM.nextDouble() - 0.5) * 1.2, 0, 0.03, 0);
			}
		}
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private EnemyTellFx() {
	}
}

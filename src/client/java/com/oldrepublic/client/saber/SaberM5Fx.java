package com.oldrepublic.client.saber;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client handlers for the Milestone 5 saber effects (spec MILESTONE_5.md 15.3):
 * <ul>
 * <li>{@code offhand_strike}: the off hand swings on every other client at once (vanilla's
 * {@code swing} would wait for half the main hand's swing; the striker's own client swung already),
 * the trail takes the off-hand arc in the off saber's colour, and {@code saber.offhand_swing}
 * plays;</li>
 * <li>{@code crystal_tune}: a burst of 12 {@code crystal_glint} over the bench and in the tuned
 * crystal's socket of the open Tune tab, and {@code workbench.tune}.</li>
 * </ul>
 */
final class SaberM5Fx {
	/** {@code crystal_tune}'s glint budget (15.1). */
	private static final int TUNE_GLINTS = 12;
	private static final RandomSource RANDOM = RandomSource.create();

	static void init() {
		ClientEffects.register(SaberEffects.OFFHAND_STRIKE, SaberM5Fx::onOffhandStrike);
		ClientEffects.register(SaberEffects.CRYSTAL_TUNE, SaberM5Fx::onCrystalTune);
	}

	private static void onOffhandStrike(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (entity instanceof LivingEntity living && living != mc.player) {
			living.swingTime = -1;
			living.swinging = true;
			living.swingingArm = InteractionHand.OFF_HAND;
		}

		SwingTrailRenderer.style(p.entityId(), SwingTrailRenderer.Style.OFFHAND);
		float pitch = 0.95f + RANDOM.nextFloat() * 0.12f;
		level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, ModSounds.SABER_OFFHAND_SWING, SoundSource.PLAYERS,
				0.8f * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private static void onCrystalTune(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, ModSounds.WORKBENCH_TUNE, SoundSource.BLOCKS,
				0.9f * ClientConfig.get().modSoundVolume, 1f, false);
		LightsaberWorkbenchScreen.onCrystalTune(level.getGameTime(), Math.round(p.intensity()), p.variant());

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		ParticleOptions glint = BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id("crystal_glint")) instanceof ParticleOptions options
				? options
				: ParticleTypes.END_ROD;

		for (int i = 0; i < Vfx.count(TUNE_GLINTS); i++) {
			double vx = (RANDOM.nextDouble() - 0.5) * 0.08;
			double vz = (RANDOM.nextDouble() - 0.5) * 0.08;
			level.addParticle(glint, p.pos().x, p.pos().y, p.pos().z, vx, 0.02 + RANDOM.nextDouble() * 0.05, vz);
		}
	}

	private SaberM5Fx() {
	}
}

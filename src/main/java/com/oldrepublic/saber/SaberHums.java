package com.oldrepublic.saber;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.HiltParts.Emitter;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/**
 * The hum profiles (MILESTONE_5.md 10.5): each emitter has its own ignition, hum and retraction.
 * Guardian is deep and steady; Duelist bright and thin, with a quick ignition; Reliquary old and
 * wavering, with a crackle; Cantor low, with a choir-like overtone. Milestone 7 (MILESTONE_7.md 11)
 * adds five: Vanguard heavier and lower than the Guardian, its shroud's vents breathing; Warden
 * an unstable crackle over the hum, its quillons venting; Watchman a clean, old-fashioned tone;
 * Penitent low and menacing, a slow growl; Choir a harmonic, choral hum. A training saber has no
 * emitter of its own and keeps the M1 sounds, as does anything that is not a saber. The server
 * plays the ignition and the retraction ({@link SaberToggle#playToggleSound}); the client plays the
 * hum, one per lit saber in each hand ({@code client/saber/SaberAudio}).
 */
public final class SaberHums {
	/** The emitter whose profile {@code stack} plays, or null for the M1 sounds. */
	public static @Nullable Emitter profile(ItemStack stack) {
		if (!(stack.getItem() instanceof LightsaberItem) || stack.getItem() instanceof TrainingSaberItem) {
			return null;
		}

		return SaberComponents.config(stack).emitter();
	}

	/** The ignition of {@code stack}'s emitter. */
	public static SoundEvent ignite(ItemStack stack) {
		Emitter emitter = profile(stack);

		if (emitter == null) {
			return ModSounds.SABER_IGNITE;
		}

		return switch (emitter) {
			case GUARDIAN -> ModSounds.SABER_GUARDIAN_IGNITE;
			case DUELIST -> ModSounds.SABER_DUELIST_IGNITE;
			case RELIQUARY -> ModSounds.SABER_RELIQUARY_IGNITE;
			case CANTOR -> ModSounds.SABER_CANTOR_IGNITE;
			case VANGUARD -> ModSounds.SABER_VANGUARD_IGNITE;
			case WARDEN -> ModSounds.SABER_WARDEN_IGNITE;
			case WATCHMAN -> ModSounds.SABER_WATCHMAN_IGNITE;
			case PENITENT -> ModSounds.SABER_PENITENT_IGNITE;
			case CHOIR -> ModSounds.SABER_CHOIR_IGNITE;
			// An emitter appended later keeps the M1 sound until it has a profile of its own.
			default -> ModSounds.SABER_IGNITE;
		};
	}

	/** The looping hum of {@code stack}'s emitter. */
	public static SoundEvent hum(ItemStack stack) {
		Emitter emitter = profile(stack);

		if (emitter == null) {
			return ModSounds.SABER_HUM;
		}

		return switch (emitter) {
			case GUARDIAN -> ModSounds.SABER_GUARDIAN_HUM;
			case DUELIST -> ModSounds.SABER_DUELIST_HUM;
			case RELIQUARY -> ModSounds.SABER_RELIQUARY_HUM;
			case CANTOR -> ModSounds.SABER_CANTOR_HUM;
			case VANGUARD -> ModSounds.SABER_VANGUARD_HUM;
			case WARDEN -> ModSounds.SABER_WARDEN_HUM;
			case WATCHMAN -> ModSounds.SABER_WATCHMAN_HUM;
			case PENITENT -> ModSounds.SABER_PENITENT_HUM;
			case CHOIR -> ModSounds.SABER_CHOIR_HUM;
			// An emitter appended later keeps the M1 sound until it has a profile of its own.
			default -> ModSounds.SABER_HUM;
		};
	}

	/** The retraction of {@code stack}'s emitter. */
	public static SoundEvent retract(ItemStack stack) {
		Emitter emitter = profile(stack);

		if (emitter == null) {
			return ModSounds.SABER_RETRACT;
		}

		return switch (emitter) {
			case GUARDIAN -> ModSounds.SABER_GUARDIAN_RETRACT;
			case DUELIST -> ModSounds.SABER_DUELIST_RETRACT;
			case RELIQUARY -> ModSounds.SABER_RELIQUARY_RETRACT;
			case CANTOR -> ModSounds.SABER_CANTOR_RETRACT;
			case VANGUARD -> ModSounds.SABER_VANGUARD_RETRACT;
			case WARDEN -> ModSounds.SABER_WARDEN_RETRACT;
			case WATCHMAN -> ModSounds.SABER_WATCHMAN_RETRACT;
			case PENITENT -> ModSounds.SABER_PENITENT_RETRACT;
			case CHOIR -> ModSounds.SABER_CHOIR_RETRACT;
			// An emitter appended later keeps the M1 sound until it has a profile of its own.
			default -> ModSounds.SABER_RETRACT;
		};
	}

	private SaberHums() {
	}
}

package com.oldrepublic.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Per-player visual and audio comfort settings, stored in {@code config/oldrepublic/client.json}.
 * Editable in game with {@code /oldrepublicfx} (see {@code ClientCommandsFx}).
 */
public final class ClientConfig {
	public enum Preset {
		REDUCED(0.35f, 24, 300),
		STANDARD(1.0f, 48, 900),
		HIGH(1.5f, 64, 1600),
		CINEMATIC(2.2f, 96, 2600);

		public final float particleMultiplier;
		public final int effectDistance;
		public final int maxParticles;

		Preset(float particleMultiplier, int effectDistance, int maxParticles) {
			this.particleMultiplier = particleMultiplier;
			this.effectDistance = effectDistance;
			this.maxParticles = maxParticles;
		}
	}

	public Preset preset = Preset.STANDARD;
	/** 0 to 1. Scales all camera shake. */
	public float screenShake = 0.6f;
	/** When false, flashes are replaced by steady glows. */
	public boolean flashingEffects = true;
	/** 0 to 1. Extra volume multiplier applied to the mod's own sounds. */
	public float modSoundVolume = 1.0f;
	/** Show the Force HUD (energy, level, resonance) once awakened. */
	public boolean showHud = true;
	/**
	 * Show the small text line for minor (contextual, ±1) alignment shifts. Major shifts always show
	 * their toast.
	 */
	public boolean showMinorAlignmentToasts = true;
	/** Play the boss fight music (it also follows the vanilla music slider). */
	public boolean bossMusic = true;
	/** Draw the robes with their own models (hood, sleeves, skirt panels); off draws the plain armour layers. */
	public boolean robeModels = true;
	/** Show the mission tracker in the top-right corner. */
	public boolean showMissionTracker = true;
	/** Show troopers' bark lines low in the centre, above the hotbar. */
	public boolean showBarks = true;
	/** Switch a first-person ship rider to a third-person camera behind the ship for the climb and descent (spec MILESTONE_4.md 4.6). */
	public boolean shipCamera = true;
	/** Draw the hyperspace tunnel during a jump; off shows a plain fade. */
	public boolean hyperspaceOverlay = true;
	/** Draw the blaster reticle and heat gauge near the crosshair. */
	public boolean blasterReticle = true;
	/** Play the planets' ambient sounds and particles. */
	public boolean planetAmbience = true;
	/**
	 * Draw the orbits' sky: the planet below, extra suns and the nebula; off draws a black sky with stars only (spec
	 * MILESTONE_5.md 5.1, 14.4).
	 */
	public boolean spaceSky = true;
	/** Invert the ship's pitch control while piloting (MILESTONE_5.md 4.3, 14.4). */
	public boolean invertShipPitch = false;
	/**
	 * Let storm lightning flash the screen (Tyrvann, Ulvaine's storm phase, the storm-callers); off, or with
	 * {@link #flashingEffects} off, dims each flash to a steady brightening (MILESTONE_5.md 15, 14.4).
	 */
	public boolean stormFlashes = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(OldRepublicMod.MOD_ID).resolve("client.json");
	private static ClientConfig instance = new ClientConfig();

	public static ClientConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
				ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);

				if (loaded != null) {
					if (loaded.preset == null) {
						loaded.preset = Preset.STANDARD;
					}

					instance = loaded;
				}
			} catch (IOException | RuntimeException e) {
				OldRepublicMod.LOGGER.error("Could not read {}, using defaults", FILE, e);
			}
		}

		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());

			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			OldRepublicMod.LOGGER.error("Could not write {}", FILE, e);
		}
	}
}

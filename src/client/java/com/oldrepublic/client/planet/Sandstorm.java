package com.oldrepublic.client.planet;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.world.FadingLoop;
import com.oldrepublic.planet.PlanetParticles;
import com.oldrepublic.planet.PlanetWeather;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Ghessar's sandstorm on the client (spec MILESTONE_5.md 3.5, 15.1, 15.3), from the
 * {@code planet_weather} payload (the storm's state) and the {@code sandstorm} effect (its cue):
 * <ul>
 * <li><b>Strength</b> eases from 0 to 1 over {@link PlanetWeather#FOG_TICKS} as a storm rises, and
 * back as it passes; a player who arrives while one blows sees it at once. Under a roof the fog
 * thins to {@link #SHELTERED} over two seconds. {@link PlanetFog} pulls the fog in with it.</li>
 * <li><b>Blowing sand</b>: {@code sand_gust} streaks on a strong, slowly turning wind around the
 * camera, {@link #GUSTS} alive at Standard at full strength, only under open sky, through the
 * {@link Vfx} budget.</li>
 * <li><b>Wind</b>: the non-positional {@code sandstorm.wind} loop while a storm blows, rising with
 * the storm and ducked under a roof; released when the storm ends and on death, stopped with the
 * level on leaving Ghessar, killed on disconnect.</li>
 * </ul>
 */
final class Sandstorm {
	static final Sandstorm INSTANCE = new Sandstorm();

	/** Sand streaks alive around the camera at Standard (15.1). */
	private static final int GUSTS = 60;
	/** A streak's mean life in ticks ({@code sand_gust} lives 35 to 64). */
	private static final float GUST_LIFE = 50f;
	private static final int GUST_REACH = 16;
	/** Under a roof the storm's fog thins to this share. */
	private static final float SHELTERED = 0.35f;
	private static final int SHELTER_TICKS = 40;
	private static final float WIND_VOLUME = 0.75f;
	private static final int WIND_FADE_TICKS = 100;
	private static final float WIND_DUCK_SHELTERED = 0.45f;

	private float strength;
	private float oStrength;
	private float shelter = 1f;
	private float oShelter = 1f;
	private float gustCarry;
	private @Nullable FadingLoop wind;
	/** The level the last payload was for; another level forgets that state until its own comes. */
	private @Nullable Object stateLevel;
	private @Nullable Object lastLevel;
	/** The next payload sets the strength at once (the player just arrived) instead of easing it. */
	private boolean snap = true;

	private Sandstorm() {
	}

	/** A {@code planet_weather} payload (client thread). */
	void receive(Minecraft client, PlanetWeather.State state) {
		syncLevel(client);
		PlanetWeather.setClientState(state);
		stateLevel = client.level;

		if (snap && client.level != null) {
			strength = target(client.level);
			oStrength = strength;
			snap = false;
		}
	}

	/** The {@code sandstorm} effect: the wind starts as the storm rises, and fades as it passes. */
	void cue(Minecraft client, boolean rising) {
		syncLevel(client);

		if (rising && client.player != null && client.player.isAlive()) {
			updateWind(true, false);
		} else if (!rising && wind != null) {
			wind.release();
			wind = null;
		}
	}

	/** The fog's share of the storm at {@code partialTick} (0 to 1), thinned under a roof. */
	float fogStrength(float partialTick) {
		return Mth.lerp(partialTick, oStrength, strength) * Mth.lerp(partialTick, oShelter, shelter);
	}

	void tick(Minecraft client) {
		syncLevel(client);
		oStrength = strength;
		oShelter = shelter;
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			return;
		}

		float target = target(level);
		strength = approach(strength, target, 1f / PlanetWeather.FOG_TICKS);
		Vec3 camera = client.gameRenderer.mainCamera().position();
		boolean covered = covered(level, camera);
		shelter = approach(shelter, covered ? SHELTERED : 1f, 1f / SHELTER_TICKS);
		boolean alive = player.isAlive();
		updateWind(target > 0f && alive, covered);

		if (alive && strength > 0.01f) {
			gusts(level, camera, strength);
		}
	}

	/** Disconnect: the wind stops at once and the state is forgotten. */
	void reset() {
		if (wind != null) {
			wind.kill();
		}

		wind = null;
		strength = 0f;
		oStrength = 0f;
		shelter = 1f;
		oShelter = 1f;
		gustCarry = 0f;
		stateLevel = null;
		lastLevel = null;
		snap = true;
		PlanetWeather.setClientState(PlanetWeather.State.NONE);
	}

	/**
	 * A new client level (a dimension change, a respawn, joining): the storm starts from nothing, the
	 * next payload snaps it, and a state sent for another level is dropped. The wind stops itself with
	 * its level.
	 */
	private void syncLevel(Minecraft client) {
		if (client.level == lastLevel) {
			return;
		}

		lastLevel = client.level;
		snap = true;
		strength = 0f;
		oStrength = 0f;
		shelter = 1f;
		oShelter = 1f;
		gustCarry = 0f;

		if (wind != null) {
			wind.kill();
			wind = null;
		}

		if (stateLevel != client.level) {
			PlanetWeather.setClientState(PlanetWeather.State.NONE);
			stateLevel = null;
		}
	}

	private static float target(ClientLevel level) {
		return PlanetWeather.kind(level) == PlanetWeather.Kind.SANDSTORM ? 1f : 0f;
	}

	private void updateWind(boolean wanted, boolean covered) {
		if (wind != null && (wind.isStopped() || !wanted)) {
			wind.release();
			wind = null;
		}

		if (wanted && wind == null) {
			wind = FadingLoop.play(ModSounds.SANDSTORM_WIND, null, WIND_VOLUME, WIND_FADE_TICKS);
		}

		if (wind != null) {
			wind.duck(Math.max(0.2f, strength) * (covered ? WIND_DUCK_SHELTERED : 1f));
		}
	}

	/** Starts this tick's streaks: {@link #GUSTS} alive at full strength at Standard, scaled by the preset and the budget. */
	private void gusts(ClientLevel level, Vec3 camera, float strength) {
		gustCarry += GUSTS / GUST_LIFE * strength;
		int base = (int) gustCarry;

		if (base <= 0) {
			return;
		}

		gustCarry -= base;
		RandomSource random = level.getRandom();
		Vec3 blow = wind(level.getGameTime());
		PlanetAmbience.repeat(random, base, () -> gust(level, camera, blow, random));
	}

	/** One streak upwind of the camera under open sky, so the wind carries it past the player. */
	private static void gust(ClientLevel level, Vec3 camera, Vec3 blow, RandomSource random) {
		Vec3 at = camera.add((random.nextDouble() - 0.5) * 2 * GUST_REACH, -2 + random.nextDouble() * 7, (random.nextDouble() - 0.5) * 2 * GUST_REACH)
				.subtract(blow.scale(10 + random.nextDouble() * 15));

		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z)) > at.y) {
			return;
		}

		double gust = 0.8 + random.nextDouble() * 0.5;
		level.addParticle(PlanetParticles.SAND_GUST, at.x, at.y, at.z, blow.x * gust, (random.nextDouble() - 0.4) * 0.03, blow.z * gust);
	}

	/** The storm's wind at {@code gameTime}: a strong blow whose heading turns over ten minutes, gusting every twenty seconds. */
	static Vec3 wind(long gameTime) {
		double angle = (gameTime % 12000) * (Mth.TWO_PI / 12000.0);
		double speed = 0.32 + 0.08 * Mth.sin((gameTime % 400) * Mth.TWO_PI / 400f);
		return new Vec3(Math.cos(angle) * speed, 0, Math.sin(angle) * speed);
	}

	/** Whether a block stands over the camera's eye: a roof, an overhang, a cave. */
	static boolean covered(ClientLevel level, Vec3 camera) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(camera.x), Mth.floor(camera.z)) > Mth.floor(camera.y) + 1;
	}

	static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}
}

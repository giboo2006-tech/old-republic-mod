package com.oldrepublic.client.planet;

import org.joml.Vector4f;

import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The planets' own fog (spec MILESTONE_5.md 3.2, 3.3, 3.5), applied after vanilla's atmospheric fog
 * by {@code mixin.planet.PlanetFogMixin}, so it never touches water, lava or blindness fog:
 * <ul>
 * <li><b>Sandstorm</b>: the fog closes in to {@code planets.sandstormFogEnd} blocks (24) and the sky
 * and clouds go with it, tinted toward blown ochre, as {@link Sandstorm}'s strength rises over 200
 * ticks; thinner under a roof.</li>
 * <li><b>Mire</b>: in {@code tyrvann_mire} a low murky fog closes to {@link #MIRE_END} blocks over
 * three seconds and lifts as the player leaves it.</li>
 * </ul>
 * The tint is scaled by the fog's own brightness, so dusk and night darken the dust instead of
 * lighting it. Nothing here flashes; every change eases over at least two seconds.
 */
public final class PlanetFog {
	/** The sandstorm's colour by full daylight. */
	private static final float[] SAND = { 0.78f, 0.64f, 0.44f };
	/** The mire's colour by full daylight. */
	private static final float[] MIRE = { 0.3f, 0.34f, 0.29f };
	private static final float MIRE_START = 4f;
	private static final float MIRE_END = 48f;
	private static final int MIRE_TICKS = 60;
	/** The mire tints the fog only this far, so the sky's storm grey still shows through. */
	private static final float MIRE_TINT = 0.5f;

	private static float mire;
	private static float oMire;

	/** Pulls the atmospheric fog in for a sandstorm or the mire (from the fog mixin, render thread). */
	public static void apply(FogData fog, ClientLevel level, float partialTick) {
		float sand = Sandstorm.INSTANCE.fogStrength(partialTick);

		if (sand > 0.001f) {
			pull(fog, 0f, (float) PlanetBalance.get().sandstormFogEnd, sand, SAND, sand);
		}

		float murk = Mth.lerp(partialTick, oMire, mire);

		if (murk > 0.001f) {
			pull(fog, MIRE_START, MIRE_END, murk, MIRE, murk * MIRE_TINT);
		}
	}

	/** One client tick: the mire's fog eases toward whether the camera is in the mire. */
	static void tick(Minecraft client) {
		oMire = mire;
		ClientLevel level = client.level;

		if (level == null) {
			mire = 0f;
			oMire = 0f;
			return;
		}

		Vec3 camera = client.gameRenderer.mainCamera().position();
		boolean inMire = level.dimension().equals(Planets.TYRVANN) && level.getBiome(BlockPos.containing(camera)).is(PlanetWorldgen.TYRVANN_MIRE);
		mire = Sandstorm.approach(mire, inMire ? 1f : 0f, 1f / MIRE_TICKS);
	}

	/** Disconnect: the mire's fog is gone at once. */
	static void reset() {
		mire = 0f;
		oMire = 0f;
	}

	/**
	 * Eases the fog toward starting at {@code start} and ending at {@code end} (never pushing it
	 * further out) by {@code weight}, and its colour toward {@code colour} by {@code tint}.
	 */
	private static void pull(FogData fog, float start, float end, float weight, float[] colour, float tint) {
		fog.environmentalStart = Mth.lerp(weight, fog.environmentalStart, Math.min(fog.environmentalStart, start));
		fog.environmentalEnd = Mth.lerp(weight, fog.environmentalEnd, Math.min(fog.environmentalEnd, end));
		fog.skyEnd = Mth.lerp(weight, fog.skyEnd, Math.min(fog.skyEnd, end));
		fog.cloudEnd = Mth.lerp(weight, fog.cloudEnd, Math.min(fog.cloudEnd, end));

		Vector4f c = fog.color;
		float shade = Mth.clamp(Math.max(c.x, Math.max(c.y, c.z)) * 1.25f, 0.06f, 1f);
		c.set(Mth.lerp(tint, c.x, colour[0] * shade), Mth.lerp(tint, c.y, colour[1] * shade), Mth.lerp(tint, c.z, colour[2] * shade), c.w);
	}

	private PlanetFog() {
	}
}

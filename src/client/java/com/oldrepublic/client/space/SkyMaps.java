package com.oldrepublic.client.space;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

/**
 * The orbit sky's colour maps (spec MILESTONE_5.md 5.1): each planet's surface as an equirectangular
 * image ({@code textures/environment/space/planet_<id>.png}, longitude across, latitude down) and the
 * nebula band ({@code nebula.png}), read once into memory and sampled on the CPU, because the
 * impostors are coloured per vertex with an unfogged colour-only render type. A map that cannot be
 * read is remembered as missing and the caller falls back to plain colour bands. Cleared on
 * disconnect, so a resource pack change shows after the next join.
 */
final class SkyMaps {
	/** One map as ARGB pixels. */
	record SkyMap(int width, int height, int[] argb) {
		/**
		 * The colour at {@code u} (0 to 1, wrapping: longitude) and {@code v} (0 to 1, clamped:
		 * latitude from the north), blended between the four nearest pixels.
		 */
		int sample(double u, double v) {
			double x = (u - Math.floor(u)) * width - 0.5;
			double y = Mth.clamp(v, 0, 1) * height - 0.5;
			int x0 = Mth.floor(x);
			int y0 = Mth.floor(y);
			double fx = x - x0;
			double fy = y - y0;
			int c00 = pixel(x0, y0);
			int c10 = pixel(x0 + 1, y0);
			int c01 = pixel(x0, y0 + 1);
			int c11 = pixel(x0 + 1, y0 + 1);
			int a = blend(c00 >>> 24, c10 >>> 24, c01 >>> 24, c11 >>> 24, fx, fy);
			int r = blend(c00 >> 16 & 0xFF, c10 >> 16 & 0xFF, c01 >> 16 & 0xFF, c11 >> 16 & 0xFF, fx, fy);
			int g = blend(c00 >> 8 & 0xFF, c10 >> 8 & 0xFF, c01 >> 8 & 0xFF, c11 >> 8 & 0xFF, fx, fy);
			int b = blend(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, fx, fy);
			return a << 24 | r << 16 | g << 8 | b;
		}

		private int pixel(int x, int y) {
			int wx = Math.floorMod(x, width);
			int cy = Mth.clamp(y, 0, height - 1);
			return argb[cy * width + wx];
		}

		private static int blend(int c00, int c10, int c01, int c11, double fx, double fy) {
			double top = c00 + (c10 - c00) * fx;
			double bottom = c01 + (c11 - c01) * fx;
			return Mth.clamp((int) Math.round(top + (bottom - top) * fy), 0, 255);
		}
	}

	/** A map that could not be read. */
	private static final SkyMap MISSING = new SkyMap(0, 0, new int[0]);
	private static final Map<String, SkyMap> CACHE = new HashMap<>();

	/** The map {@code name} ({@code planet_home}, {@code nebula}, ...), or null when it is missing. */
	static @Nullable SkyMap get(String name) {
		SkyMap map = CACHE.computeIfAbsent(name, SkyMaps::load);
		return map == MISSING ? null : map;
	}

	static void clear() {
		CACHE.clear();
	}

	private static SkyMap load(String name) {
		Identifier id = OldRepublicMod.id("textures/environment/space/" + name + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);

		if (resource.isEmpty()) {
			OldRepublicMod.LOGGER.warn("Orbit sky map {} is missing; drawing plain bands", id);
			return MISSING;
		}

		try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] argb = new int[width * height];

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					// NativeImage#getPixel returns ARGB in 26.2 (Fabric's CustomSpriteSourcesTest blends it with ARGB.alphaBlend).
					argb[y * width + x] = image.getPixel(x, y);
				}
			}

			return width > 0 && height > 0 ? new SkyMap(width, height, argb) : MISSING;
		} catch (IOException | RuntimeException e) {
			OldRepublicMod.LOGGER.warn("Could not read orbit sky map {}", id, e);
			return MISSING;
		}
	}

	private SkyMaps() {
	}
}

package com.oldrepublic.client.space;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.planet.Planets;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What the pilot sees around an orbit (spec MILESTONE_5.md 5.1): the vanilla black sky and stars come
 * from the {@code oldrepublic:orbit} dimension type; this adds the planet below as a sphere impostor
 * and a faint nebula band, both off with {@code ClientConfig.spaceSky}, and holds the client's rain
 * at 0 in every orbit (3.5: the overworld's rain packets reach every dimension and would dim the sun
 * and the stars).
 *
 * <p>Everything is drawn camera-relative between the render distance and 0.9 of the frame's far
 * plane (the space digest's planet recipe), with the unfogged colour-only {@code debugQuads} type, so
 * the terrain, the ships and the station, all nearer, cover it by depth, and it covers the stars, sun
 * and moon of the earlier sky pass. The planet is a polar grid of directions around the nadir out to
 * its rim: each direction's ray meets the sphere, and the point it meets gives the colour (the
 * planet's map, scrolled by the camera's position), the sunlight and the haze toward the limb. The
 * height above the atmosphere line sets the planet's apparent size: at y 100 its rim sits 15 degrees
 * below the horizon, and at the line itself it fills the view below the horizon. Tyrvann's storm
 * shows as flashes moving under the disc (a slow, dim glow with flashing or storm flashes off).
 */
final class OrbitSky {
	/** The orbit type's sun angle ({@code dimension_type/orbit.json}): degrees from the zenith, increasing toward the west. */
	static final float SUN_ANGLE = 325f;
	private static final double ATMOSPHERE_LINE = 64;
	/** The camera's height over the surface per block above the atmosphere line, in planet radii: the rim 15 degrees below the horizon at y 100. */
	private static final double ALTITUDE_PER_BLOCK = (1 / Math.sin(Math.toRadians(75)) - 1) / 36.0;
	/** How far the map scrolls per block flown (30 degrees across the orbit's 2,048 blocks), and its slow spin per tick. */
	private static final double DEGREES_PER_BLOCK = 30.0 / 2048.0;
	private static final double SPIN_PER_TICK = 0.0015;
	/** The atmosphere's rim beyond the disc, radians. */
	private static final double LIMB_WIDTH = Math.toRadians(3.0);
	/** The nebula band's half-width and its brightest alpha. */
	private static final double NEBULA_HALF_WIDTH = Math.toRadians(13);
	private static final float NEBULA_ALPHA = 0.5f;
	/** The nebula band's plane: its normal, tilted so the band climbs from the north-east horizon over the zenith's south side. */
	private static final Vec3 NEBULA_NORMAL = new Vec3(0.30, 0.64, 0.71).normalize();

	/** One planet's look: its map, the air at its limb, how strong that haze is, two fallback bands and whether it storms. */
	private record Look(String map, int atmosphere, float haze, int bandA, int bandB, double baseLatitude, boolean storm) {
	}

	/** A lightning flash under Tyrvann's clouds, in the camera's frame. */
	private record Flash(Vec3 dir, long start, int length, float strength) {
	}

	private static final List<Flash> FLASHES = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null || !Planets.isOrbit(level)) {
			FLASHES.clear();
			return;
		}

		// 3.5: no rain or thunder in an orbit, whatever the overworld's packets say.
		level.setRainLevel(0f);
		level.setThunderLevel(0f);
		Planets.Planet system = Planets.orbitOf(level.dimension());
		long now = level.getGameTime();
		FLASHES.removeIf(f -> now > f.start() + f.length());

		if (system == Planets.Planet.TYRVANN && ClientConfig.get().spaceSky && RANDOM.nextInt(flashOdds()) == 0) {
			double theta = Math.sqrt(RANDOM.nextDouble()) * 0.5;
			double phi = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 dir = new Vec3(Math.sin(theta) * Math.cos(phi), -Math.cos(theta), Math.sin(theta) * Math.sin(phi));
			FLASHES.add(new Flash(dir, now, 4 + RANDOM.nextInt(6), 0.6f + RANDOM.nextFloat() * 0.4f));
		}
	}

	static void clear() {
		FLASHES.clear();
	}

	/** One flash in this many ticks on average, by preset. */
	private static int flashOdds() {
		return switch (ClientConfig.get().preset) {
			case REDUCED -> 50;
			case HIGH -> 18;
			case CINEMATIC -> 12;
			default -> 25;
		};
	}

	static void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;

		if (level == null || !Planets.isOrbit(level) || !ClientConfig.get().spaceSky) {
			return;
		}

		Planets.Planet system = Planets.orbitOf(level.dimension());

		if (system == null) {
			return;
		}

		Vec3 cam = context.levelState().cameraRenderState.pos;
		float far = context.levelState().cameraRenderState.depthFar;
		double renderDistance = mc.options.getEffectiveRenderDistance() * 16.0;
		// Every body sits beyond the render distance and inside the far plane (the space digest: rd + 32 <= D <= 0.9 far).
		double dNebula = Math.max(renderDistance + 40, far * 0.88);
		double dPlanet = Math.max(renderDistance + 32, far * 0.84);
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		double time = level.getGameTime() + partial;
		Look look = look(system);
		int detail = detail();

		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			nebula(pose, buffer, dNebula, detail);
			planet(pose, buffer, dPlanet, cam, time, look, detail);
		});
	}

	/** Grid detail by preset: 0 Reduced, 1 Standard, 2 High and Cinematic. */
	private static int detail() {
		return switch (ClientConfig.get().preset) {
			case REDUCED -> 0;
			case HIGH, CINEMATIC -> 2;
			default -> 1;
		};
	}

	// ---------------------------------------------------------------- the planet

	private static void planet(PoseStack.Pose pose, VertexConsumer buffer, double distance, Vec3 cam, double time, Look look, int detail) {
		int rings = 16 + detail * 8;
		int segments = 40 + detail * 24;
		double altitude = Math.max(0, (cam.y - ATMOSPHERE_LINE) * ALTITUDE_PER_BLOCK);
		double h = 1 + altitude;
		double rim = Math.asin(1 / h);
		Vec3 sun = celestial(SUN_ANGLE);
		double lat0 = Math.toRadians(look.baseLatitude() - cam.z * DEGREES_PER_BLOCK);
		double lon0 = Math.toRadians(cam.x * DEGREES_PER_BLOCK + time * SPIN_PER_TICK);
		SkyMaps.SkyMap map = SkyMaps.get(look.map());
		long now = (long) time;

		// Rings closer together toward the rim, where the surface is foreshortened.
		double[] theta = new double[rings + 1];

		for (int i = 0; i <= rings; i++) {
			theta[i] = rim * Math.sin(Math.PI / 2 * i / rings);
		}

		int[][] colours = new int[rings + 1][segments];
		Vec3[][] dirs = new Vec3[rings + 1][segments];

		for (int i = 0; i <= rings; i++) {
			for (int j = 0; j < segments; j++) {
				double phi = Math.PI * 2 * j / segments;
				Vec3 dir = nadirDirection(theta[i], phi);
				dirs[i][j] = dir;
				colours[i][j] = surface(dir, theta[i] / rim, h, lat0, lon0, sun, look, map, now);
			}
		}

		for (int i = 0; i < rings; i++) {
			for (int j = 0; j < segments; j++) {
				int k = (j + 1) % segments;
				quad(pose, buffer, distance, dirs[i][j], dirs[i][k], dirs[i + 1][k], dirs[i + 1][j],
						colours[i][j], colours[i][k], colours[i + 1][k], colours[i + 1][j], true);
			}
		}

		// The air at the limb: a thin band past the rim, lit like the rim beneath it, fading outward.
		for (int j = 0; j < segments; j++) {
			int k = (j + 1) % segments;
			double phiJ = Math.PI * 2 * j / segments;
			double phiK = Math.PI * 2 * k / segments;
			int inJ = limb(normalAt(dirs[rings][j], h), sun, look, 0.55f);
			int inK = limb(normalAt(dirs[rings][k], h), sun, look, 0.55f);
			quad(pose, buffer, distance, dirs[rings][j], dirs[rings][k], nadirDirection(rim + LIMB_WIDTH, phiK), nadirDirection(rim + LIMB_WIDTH, phiJ),
					inJ, inK, inK & 0x00FFFFFF, inJ & 0x00FFFFFF, false);
		}
	}

	/** The colour of the surface seen along {@code dir} ({@code edge}: 0 at the nadir, 1 at the rim). */
	private static int surface(Vec3 dir, double edge, double h, double lat0, double lon0, Vec3 sun, Look look, SkyMaps.@Nullable SkyMap map,
			long now) {
		Vec3 n = normalAt(dir, h);
		// The planet's frame: up (toward the viewer) is latitude lat0, north is world north (-Z), east is +X.
		double qx = n.y;
		double qy = -n.z;
		double qz = n.x;
		double cos = Math.cos(lat0);
		double sin = Math.sin(lat0);
		double rx = qx * cos - qy * sin;
		double ry = qx * sin + qy * cos;
		double lat = Math.asin(Mth.clamp(ry, -1, 1));
		double lon = Math.atan2(qz, rx) + lon0;
		int base = map != null ? map.sample(lon / (Math.PI * 2), 0.5 - lat / Math.PI) : bands(look, lat, lon);
		float light = light(n, sun);
		float r = (base >> 16 & 0xFF) * light;
		float g = (base >> 8 & 0xFF) * light;
		float b = (base & 0xFF) * light;

		if (look.storm() && !FLASHES.isEmpty()) {
			float flash = flash(dir, now);
			r += 200 * flash;
			g += 205 * flash;
			b += 230 * flash;
		}

		// Haze thickens toward the limb, where the line of sight crosses the most air.
		float haze = (float) Math.pow(edge, 5) * look.haze();
		r = Mth.lerp(haze, r, (look.atmosphere() >> 16 & 0xFF) * light);
		g = Mth.lerp(haze, g, (look.atmosphere() >> 8 & 0xFF) * light);
		b = Mth.lerp(haze, b, (look.atmosphere() & 0xFF) * light);
		return 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
	}

	/** The limb's colour with {@code alpha}, lit like the surface under it. */
	private static int limb(Vec3 n, Vec3 sun, Look look, float alpha) {
		float light = light(n, sun);
		int a = Mth.clamp(Math.round(alpha * 255 * Math.min(1f, 0.35f + light)), 0, 255);
		return a << 24 | channel((look.atmosphere() >> 16 & 0xFF) * light) << 16 | channel((look.atmosphere() >> 8 & 0xFF) * light) << 8
				| channel((look.atmosphere() & 0xFF) * light);
	}

	/** Sunlight on the surface normal {@code n}, with a dim night side. */
	private static float light(Vec3 n, Vec3 sun) {
		return 0.16f + 0.84f * (float) Math.max(0, n.dot(sun));
	}

	/** How much the storm lights the surface seen along {@code dir} now (0 to 1). */
	private static float flash(Vec3 dir, long now) {
		boolean flicker = Vfx.flashing() && ClientConfig.get().stormFlashes;
		float total = 0;

		for (Flash f : FLASHES) {
			double angle = Math.acos(Mth.clamp(dir.dot(f.dir()), -1, 1));
			float spread = (float) Math.max(0, 1 - angle / 0.09);

			if (spread <= 0) {
				continue;
			}

			long age = now - f.start();
			float envelope;

			if (flicker) {
				// A real flash: bright, then stuttering out.
				envelope = age == 0 ? 1f : age % 2 == 1 ? 0.25f : 0.8f * (1 - (float) age / f.length());
			} else {
				// Flashing or storm flashes off: a slow, dim swell instead.
				envelope = 0.3f * Mth.sin((float) Math.PI * age / f.length());
			}

			total += spread * spread * Math.max(0, envelope) * f.strength();
		}

		return Math.min(1f, total);
	}

	/** The fallback when a planet's map is missing: two colour bands by latitude, broken up by longitude. */
	private static int bands(Look look, double lat, double lon) {
		double t = 0.5 + 0.5 * Math.sin(lat * 7 + Math.sin(lon * 3) * 0.8);
		int a = look.bandA();
		int b = look.bandB();
		return 0xFF000000 | channel(Mth.lerp((float) t, a >> 16 & 0xFF, b >> 16 & 0xFF)) << 16
				| channel(Mth.lerp((float) t, a >> 8 & 0xFF, b >> 8 & 0xFF)) << 8 | channel(Mth.lerp((float) t, a & 0xFF, b & 0xFF));
	}

	/** The unit direction {@code theta} from straight down, at bearing {@code phi}. */
	private static Vec3 nadirDirection(double theta, double phi) {
		double s = Math.sin(theta);
		return new Vec3(s * Math.cos(phi), -Math.cos(theta), s * Math.sin(phi));
	}

	/**
	 * The sphere's unit normal where the ray along {@code dir} meets it: the planet (radius 1) has its
	 * centre {@code h} straight below the eye; a ray that misses (past the rim) takes the rim.
	 */
	private static Vec3 normalAt(Vec3 dir, double h) {
		double cos = -dir.y;
		double disc = Math.max(0, 1 - h * h * (1 - cos * cos));
		double t = h * cos - Math.sqrt(disc);
		Vec3 n = new Vec3(dir.x * t, dir.y * t + h, dir.z * t);
		double length = n.length();
		return length > 1.0E-6 ? n.scale(1 / length) : new Vec3(0, 1, 0);
	}

	/** The direction of a body at {@code angle} on the sky's east-west arc (0 at the zenith, 270 in the east). */
	static Vec3 celestial(float angle) {
		double rad = Math.toRadians(angle);
		return new Vec3(-Math.sin(rad), Math.cos(rad), 0);
	}

	// ---------------------------------------------------------------- the nebula

	private static void nebula(PoseStack.Pose pose, VertexConsumer buffer, double distance, int detail) {
		int segments = 48 + detail * 48;
		int across = 4 + detail * 2;
		Vec3 w = NEBULA_NORMAL;
		Vec3 u = w.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 v = w.cross(u).normalize();
		SkyMaps.SkyMap map = SkyMaps.get("nebula");
		Vec3[][] dirs = new Vec3[across + 1][segments + 1];
		int[][] colours = new int[across + 1][segments + 1];

		for (int a = 0; a <= across; a++) {
			double psi = NEBULA_HALF_WIDTH * (2.0 * a / across - 1);

			for (int s = 0; s <= segments; s++) {
				double phi = Math.PI * 2 * s / segments;
				Vec3 along = u.scale(Math.cos(phi)).add(v.scale(Math.sin(phi)));
				dirs[a][s] = along.scale(Math.cos(psi)).add(w.scale(Math.sin(psi)));
				colours[a][s] = nebulaColour(map, (double) s / segments, (double) a / across, psi);
			}
		}

		for (int a = 0; a < across; a++) {
			for (int s = 0; s < segments; s++) {
				quad(pose, buffer, distance, dirs[a][s], dirs[a][s + 1], dirs[a + 1][s + 1], dirs[a + 1][s],
						colours[a][s], colours[a][s + 1], colours[a + 1][s + 1], colours[a + 1][s], false);
			}
		}
	}

	/** The nebula's colour at band position {@code x} (along, 0 to 1) and {@code y} (across, 0 to 1). */
	private static int nebulaColour(SkyMaps.@Nullable SkyMap map, double x, double y, double psi) {
		int c;

		if (map != null) {
			c = map.sample(x, y);
		} else {
			double core = Math.exp(-Math.pow(psi / NEBULA_HALF_WIDTH * 1.8, 2));
			int a = (int) (255 * core * (0.55 + 0.45 * Math.sin(x * Math.PI * 6)));
			c = Mth.clamp(a, 0, 255) << 24 | 0x6A5A8C;
		}

		float alpha = (c >>> 24) / 255f * NEBULA_ALPHA;
		return withAlpha(c, alpha);
	}

	// ---------------------------------------------------------------- drawing

	/**
	 * One quad on the sky sphere at {@code distance}, wound to face the camera (the type's culling is
	 * not assumed either way); an opaque one is also emitted the other way round, so it shows even if
	 * the winding is read the other way.
	 */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, double distance, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd,
			boolean opaque) {
		Vec3 pa = a.scale(distance);
		Vec3 pb = b.scale(distance);
		Vec3 pc = c.scale(distance);
		Vec3 pd = d.scale(distance);
		// Counter-clockwise as seen from the camera at the origin: the face normal points back at it.
		boolean facing = pb.subtract(pa).cross(pd.subtract(pa)).dot(pa) < 0;

		if (facing || opaque) {
			vertex(pose, buffer, pa, ca);
			vertex(pose, buffer, pb, cb);
			vertex(pose, buffer, pc, cc);
			vertex(pose, buffer, pd, cd);
		}

		if (!facing || opaque) {
			vertex(pose, buffer, pd, cd);
			vertex(pose, buffer, pc, cc);
			vertex(pose, buffer, pb, cb);
			vertex(pose, buffer, pa, ca);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int argb) {
		buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(argb);
	}

	private static int withAlpha(int rgb, float alpha) {
		return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | (rgb & 0xFFFFFF);
	}

	private static int channel(float value) {
		return Mth.clamp(Math.round(value), 0, 255);
	}

	/** Each world's look from orbit (5.1). */
	private static Look look(Planets.Planet system) {
		return switch (system) {
			case SELVAREN -> new Look("planet_selvaren", 0xFFB4E2D2, 0.55f, 0xFF3C7A44, 0xFFB8C4CC, 18, false);
			case VARKHAST -> new Look("planet_varkhast", 0xFFD49A74, 0.45f, 0xFF8C4A2C, 0xFF6A625C, -12, false);
			case GHESSAR -> new Look("planet_ghessar", 0xFFF2D6A2, 0.5f, 0xFFC8964A, 0xFFE8E2D2, 8, false);
			case TYRVANN -> new Look("planet_tyrvann", 0xFF9AB4AA, 0.65f, 0xFF1E4628, 0xFF5A6464, 24, true);
			default -> new Look("planet_home", 0xFF94BCFF, 0.6f, 0xFF22508E, 0xFF4E7A3A, 22, false);
		};
	}

	private OrbitSky() {
	}
}

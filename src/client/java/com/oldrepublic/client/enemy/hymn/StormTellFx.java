package com.oldrepublic.client.enemy.hymn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.oldrepublic.client.fx.Vfx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The storm-call tell (spec MILESTONE_5.md 15.1), shared by the Hymnsworn storm-caller, Ulvaine's
 * rods and the First Cantor's basalt marks: over the tell, 20 {@code lightning_spark} crawl over an
 * uneven patch of ground and 10 {@code gather_dust} lift from it. The patch is a cluster of a few
 * overlapping blobs with gaps between them, never a circle. A tell: the counts go through
 * {@link Vfx#tellCount} and are never thinned by a preset.
 */
public final class StormTellFx {
	private static final int SPARKS = 20;
	private static final int DUST = 10;
	private static final int MAX_PATCHES = 24;

	private static final class Patch {
		final Vec3 centre;
		final int ticks;
		final Vec3[] blobs;
		final double[] radii;
		int age;
		float sparksDue;
		float dustDue;

		Patch(Vec3 centre, double radius, int ticks, RandomSource random) {
			this.centre = centre;
			this.ticks = Math.max(1, ticks);
			int count = 4 + random.nextInt(3);
			this.blobs = new Vec3[count];
			this.radii = new double[count];

			for (int i = 0; i < count; i++) {
				double angle = random.nextDouble() * Mth.TWO_PI;
				double r = radius * (0.25 + random.nextDouble() * 0.5);
				blobs[i] = centre.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
				radii[i] = radius * (0.25 + random.nextDouble() * 0.3);
			}
		}

		/** A random point on one of the blobs (the gaps between them stay bare). */
		Vec3 point(RandomSource random) {
			int i = random.nextInt(blobs.length);
			double angle = random.nextDouble() * Mth.TWO_PI;
			double r = Math.sqrt(random.nextDouble()) * radii[i];
			return blobs[i].add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
		}
	}

	private static final List<Patch> PATCHES = new ArrayList<>();
	private static boolean initialised;

	/** Hooks the client tick and the disconnect. Called once from {@code FoesM5Client}. */
	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientTickEvents.END_CLIENT_TICK.register(StormTellFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PATCHES.clear());
	}

	/** A storm-call tell on the ground at {@code centre}, {@code radius} across, over {@code ticks}. */
	public static void tell(Vec3 centre, double radius, int ticks) {
		if (PATCHES.size() >= MAX_PATCHES) {
			PATCHES.remove(0);
		}

		PATCHES.add(new Patch(centre, Math.max(0.5, radius), ticks, FoeFx.RANDOM));
	}

	/** Ends the tell on the patch at {@code centre} (its strike has come, whatever its length was). */
	public static void end(Vec3 centre) {
		PATCHES.removeIf(patch -> patch.centre.distanceToSqr(centre) < 0.75 * 0.75);
	}

	/** Patches showing now (the client tests read it). */
	public static int active() {
		return PATCHES.size();
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null) {
			PATCHES.clear();
			return;
		}

		if (mc.isPaused()) {
			return;
		}

		for (Iterator<Patch> it = PATCHES.iterator(); it.hasNext();) {
			Patch patch = it.next();

			if (++patch.age > patch.ticks) {
				it.remove();
				continue;
			}

			patch.sparksDue += (float) SPARKS / patch.ticks;
			patch.dustDue += (float) DUST / patch.ticks;
			int sparks = (int) patch.sparksDue;
			int dust = (int) patch.dustDue;
			patch.sparksDue -= sparks;
			patch.dustDue -= dust;

			if (!Vfx.inRange(patch.centre)) {
				continue;
			}

			for (int i = 0; i < Vfx.tellCount(sparks); i++) {
				// Static crawling over the ground: short, low, sideways hops.
				Vec3 at = patch.point(FoeFx.RANDOM);
				FoeFx.add(level, FoeFx.lightningSpark(), at.x, at.y, at.z, FoeFx.jitter(0.06), 0.01 + FoeFx.RANDOM.nextDouble() * 0.02, FoeFx.jitter(0.06));
			}

			for (int i = 0; i < Vfx.tellCount(dust); i++) {
				// Grit lifting off the patch as the charge builds.
				Vec3 at = patch.point(FoeFx.RANDOM);
				FoeFx.add(level, FoeFx.gatherDust(), at.x, at.y, at.z, FoeFx.jitter(0.008), 0.04 + FoeFx.RANDOM.nextDouble() * 0.05, FoeFx.jitter(0.008));
			}
		}
	}

	private StormTellFx() {
	}
}

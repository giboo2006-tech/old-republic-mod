package com.oldrepublic.boss.cantor;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The First Cantor's effect event {@code first_cantor} (spec MILESTONE_5.md 15.3; around; variant = the
 * moment). Variants 1, 3, 6, 9 and 11 are attack tells in {@code DangerTellTable} (never thinned). The
 * server plays the one-shot sounds; the client draws the looks and keeps the loops (the beam, a singing
 * ring, the engine).
 */
public final class CantorEffects {
	/** {@code first_cantor}. */
	public static final Identifier EVENT = OldRepublicMod.id("first_cantor");
	/**
	 * A phase begins (pos = his spot; intensity = the phase, entity = him): 0 = the keystone turns and the
	 * countdown runs (dir.x = its ticks, entity = none), 1 = he rises (dir.x = the rise's ticks), 2 and 3
	 * the later phases (3: the engine wakes), 4 = the Hymn stops (a wipe, a reset: the engine falls quiet).
	 */
	public static final int PHASE = 0;
	/**
	 * The verse beam's tell: dust lifts along its coming path (pos = the beam's origin, at the height it
	 * sweeps; dir.x and dir.z = its starting direction times its reach, dir.y = the tell's ticks;
	 * intensity = the signed sweep in degrees, positive turning from +X toward +Z: a direction (x, z)
	 * turned by a goes to (x cos a - z sin a, x sin a + z cos a)). A tell.
	 */
	public static final int BEAM_TELL = 1;
	/** The beam sweeps (as {@link #BEAM_TELL}, dir.y = the sweep's ticks). */
	public static final int BEAM = 2;
	/** The slam's tell (pos = his feet; intensity = the radius). A tell. */
	public static final int SLAM_TELL = 3;
	/** The slam lands (pos = his feet; intensity = the radius). */
	public static final int SLAM = 4;
	/** One link of chain lightning (pos = the struck chest, dir = from the previous link; intensity = the link's index). */
	public static final int CHAIN = 5;
	/** Stormwood's tell: cracks spread as roots push up (pos = the circle's centre; intensity = the radius). A tell. */
	public static final int ROOTS_TELL = 6;
	/** The roots grip (pos = the centre, intensity = the radius, dir.x = the ticks); intensity 0 at a freed player's feet. */
	public static final int ROOTS = 7;
	/** Mire's black fog spreads (pos = a pool's centre; intensity = its width; dir.x = its ticks). */
	public static final int FOG = 8;
	/** Basalt's mark: static and grit (pos = the mark; intensity = the radius). A tell. */
	public static final int MARK = 9;
	/** Basalt's strike on a mark. */
	public static final int STRIKE = 10;
	/** A floor ring's stones tremble before it sings (pos = the ring's centre; intensity = the radius). A tell. */
	public static final int RING_TELL = 11;
	/** A ring sings (pos = the centre, intensity = the radius, dir.x = the ticks). */
	public static final int RING = 12;
	/** A silencing point (pos = the point; intensity = the channel's progress, 0 when it opens or the channel breaks). */
	public static final int SILENCE_POINT = 13;
	/** A point is silenced: his window opens (pos = the point; dir.x = the window's ticks). */
	public static final int SILENCED = 14;
	/** The choir shields him (pos = an echo's chest; dir = the whole way from it to his chest; entity = him). */
	public static final int SHIELD = 15;
	/** He unravels into song (pos = his centre; dir.x = the unravelling's ticks; entity = him): the engine falls silent. */
	public static final int UNRAVEL = 16;

	private CantorEffects() {
	}
}

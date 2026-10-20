package com.oldrepublic.boss.ulvaine;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Darth Ulvaine's effect events (spec MILESTONE_5.md 15.3): {@code ulvaine} (around; variant = the
 * moment) and {@code hologram} (W4's dialogue action sends it; W8's client draws Ulvaine's figure).
 * Variants 1, 4, 6 and 7 of {@code ulvaine} are attack tells in {@code DangerTellTable} (never thinned).
 */
public final class UlvaineEffects {
	/** {@code ulvaine}. */
	public static final Identifier EVENT = OldRepublicMod.id("ulvaine");
	/** A phase begins (intensity = the phase; 4 = the echo forming). */
	public static final int PHASE = 0;
	/** The Hymn step's tell: a violet shimmer around her (intensity = the tell's ticks). A tell. */
	public static final int STEP_TELL = 1;
	/** She arrives behind her target (pos = where she stands now, dir = from where she left). */
	public static final int STEP = 2;
	/** Force Lightning (pos = the struck chest, dir = from her hand; one per damage pulse). */
	public static final int LIGHTNING = 3;
	/** A rod charges toward a patch (pos = the patch, dir = from the rod; intensity = the tell's ticks). A tell. */
	public static final int ROD_TELL = 4;
	/** The rod's strike on the patch. */
	public static final int ROD_STRIKE = 5;
	/** A Hymn pulse rolls out from her (pos = her feet; intensity = the radius; dir.x = the wave's ticks, dir.y = its height). A tell. */
	public static final int PULSE = 6;
	/** The Crescendo begins (intensity = its ticks). A tell. */
	public static final int CRESCENDO = 7;
	/** A resonator lights (intensity 1) or breaks (intensity 0) (pos = the spire). */
	public static final int RESONATOR = 8;
	/** The Crescendo's blast: the hall shakes. */
	public static final int BLAST = 9;
	/** She kneels. */
	public static final int YIELD = 10;
	/** The echo fades. */
	public static final int ECHO_FADE = 11;

	/** {@code hologram}: W4's dialogue action shows Ulvaine's figure at Dask's desk (around; pos = the desk). */
	public static final Identifier HOLOGRAM = OldRepublicMod.id("hologram");
	/** The hologram starts (intensity = its ticks, 0 = until the end event). */
	public static final int HOLOGRAM_START = 0;
	/** The hologram ends. */
	public static final int HOLOGRAM_END = 1;

	private UlvaineEffects() {
	}
}

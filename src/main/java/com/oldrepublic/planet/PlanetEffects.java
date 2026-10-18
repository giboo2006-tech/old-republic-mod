package com.oldrepublic.planet;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the planets (spec MILESTONE_4.md 13.3). The server sends them through
 * {@link com.oldrepublic.network.EffectEvents}; the client handlers live in
 * {@code client.planet.PlanetEffectHandlers}.
 */
public final class PlanetEffects {
	/**
	 * A waystone. Variant {@link #WAYSTONE_CORRUPT} (toPlayer, every 40 ticks to each player within
	 * 16 blocks it is not cleansed for; intensity = seconds the loop stays alive without another):
	 * dark veins pulse and the corrupt loop plays. Variant {@link #WAYSTONE_CLEANSE} (around): the
	 * veins drain as dust and the stone's own hum returns.
	 */
	public static final Identifier WAYSTONE = OldRepublicMod.id("waystone");
	/**
	 * The Heartstone (around). Variant {@link #HEARTSTONE_WAVE}: corruption wells from the ground as
	 * dark mist at the stone's base (intensity = the wave, 1 to 3). Variant
	 * {@link #HEARTSTONE_COMPLETE}: a slow exhale of pale motes.
	 */
	public static final Identifier HEARTSTONE = OldRepublicMod.id("heartstone");
	/**
	 * A crystal answers (around). Variant {@link #CRYSTAL_CHIME}: a Singing Hollow cluster chimes
	 * (intensity = its tone, 1 to 3; 0 is the dull clunk of a wrong stone). Variant
	 * {@link #CRYSTAL_BLEED}: the rift crystal ({@link #BLEED_BEGIN}: the whispers begin;
	 * {@link #BLEED_DONE}: it has bled red; {@link #BLEED_BROKEN}: the holder stepped away and the
	 * whispers stop).
	 * Variant {@link #CRYSTAL_ANSWER_ALL}: the Hollow's crystals answer together (solved).
	 */
	public static final Identifier CRYSTAL_ANSWER = OldRepublicMod.id("crystal_answer");

	public static final int WAYSTONE_CORRUPT = 0;
	public static final int WAYSTONE_CLEANSE = 1;
	public static final int HEARTSTONE_WAVE = 0;
	public static final int HEARTSTONE_COMPLETE = 1;
	public static final int CRYSTAL_CHIME = 0;
	public static final int CRYSTAL_BLEED = 1;
	public static final int CRYSTAL_ANSWER_ALL = 2;
	/** Intensities of {@link #CRYSTAL_BLEED}. */
	public static final float BLEED_BEGIN = 0f;
	public static final float BLEED_DONE = 1f;
	public static final float BLEED_BROKEN = 2f;

	private PlanetEffects() {
	}
}

package com.oldrepublic.planet;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the planets (spec MILESTONE_4.md 13.3, MILESTONE_5.md 15.3). The server sends them through
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
	/**
	 * Ghessar's sandstorm (MILESTONE_5.md 3.5, 15.3; toPlayer, to every player in Ghessar, and on
	 * joining Ghessar while one blows). Variant {@link #SANDSTORM_START}: the fog closes in over 200
	 * ticks, sand blows around the camera and the wind loop starts (intensity = strength, 0 to 1).
	 * Variant {@link #SANDSTORM_END}: the fog lifts and the wind fades.
	 */
	public static final Identifier SANDSTORM = OldRepublicMod.id("sandstorm");
	/**
	 * A verse stone (3.3.2; around, pos = the stone). Variants: {@link #VERSE_HUM} the slowing hum
	 * (the air bends outward once and motes drift off), {@link #VERSE_CHANNEL_START} a channel starts,
	 * {@link #VERSE_CHANNEL_PROGRESS} its progress (intensity 0 to 1, every 5 ticks: motes stream from
	 * the stone to the channeller's hand; entity = the channeller), {@link #VERSE_CHANNEL_BROKEN} it
	 * breaks, {@link #VERSE_FORCE} the Force answers (the 40-tick cut), {@link #VERSE_SILENCED} the
	 * stone falls silent (its glow drains, motes fall as grit).
	 */
	public static final Identifier VERSE_STONE = OldRepublicMod.id("verse_stone");
	/** Stormglass forms where real lightning struck exposed basalt (3.3; around, pos = the new cluster): static crawls, a cluster pushes up. */
	public static final Identifier STORMGLASS = OldRepublicMod.id("stormglass");
	/**
	 * A site door (around, pos = the door; intensity = cells moved). Variants {@link #DOOR_GATE_SHUT}
	 * and {@link #DOOR_GATE_OPEN}: the holdfast gate's heavy plates slide; {@link #DOOR_HEART} and
	 * {@link #DOOR_UNDERCROFT}: the Hymnal's stone grinds apart with falling grit, as the door opens
	 * (intensity above 0) or settles back shut (below 0, minus the cells moved).
	 */
	public static final Identifier SITE_DOOR = OldRepublicMod.id("site_door");

	public static final int WAYSTONE_CORRUPT = 0;
	public static final int WAYSTONE_CLEANSE = 1;
	public static final int HEARTSTONE_WAVE = 0;
	public static final int HEARTSTONE_COMPLETE = 1;
	public static final int CRYSTAL_CHIME = 0;
	public static final int CRYSTAL_BLEED = 1;
	public static final int CRYSTAL_ANSWER_ALL = 2;
	public static final int SANDSTORM_START = 0;
	public static final int SANDSTORM_END = 1;
	public static final int VERSE_HUM = 0;
	public static final int VERSE_CHANNEL_START = 1;
	public static final int VERSE_CHANNEL_PROGRESS = 2;
	public static final int VERSE_CHANNEL_BROKEN = 3;
	public static final int VERSE_FORCE = 4;
	public static final int VERSE_SILENCED = 5;
	public static final int DOOR_GATE_SHUT = 0;
	public static final int DOOR_GATE_OPEN = 1;
	public static final int DOOR_HEART = 2;
	public static final int DOOR_UNDERCROFT = 3;
	/** Intensities of {@link #CRYSTAL_BLEED}. */
	public static final float BLEED_BEGIN = 0f;
	public static final float BLEED_DONE = 1f;
	public static final float BLEED_BROKEN = 2f;

	private PlanetEffects() {
	}
}

package com.oldrepublic.boss.kaast;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The Kaast fight's effect event (spec MILESTONE_4.md 13.3): {@code kaast}, variant = the moment.
 * {@code DangerTellTable} marks variants 1 (a mine arming) and 3 (the slam's rise) as tells. Kaast's
 * own gadget tells (hop, rocket, net, shoulder charge, the combo's wind-up) use the hunters'
 * {@code hunter_tell}, which counts every variant as a tell.
 */
public final class KaastEffects {
	/** {@code kaast}. */
	public static final Identifier EVENT = OldRepublicMod.id("kaast");

	/** A phase begins: its banner (intensity = the phase), to each participant. */
	public static final int PHASE = 0;
	/** A mine arms: it blinks and beeps (intensity = the arming ticks); entity = the mine. */
	public static final int MINE_ARM = 1;
	/** A mine is live; entity = the mine. */
	public static final int MINE_LIVE = 2;
	/** The jet slam's rise (intensity = the tell's ticks); pos = where it will land. */
	public static final int SLAM_TELL = 3;
	/** The slam lands (intensity = the radius). */
	public static final int SLAM = 4;
	/** He yields: kneels and waits. */
	public static final int YIELD = 5;
	/** The cracked helmet (phase 3), or the helmet taken off (the yield); intensity 1 = off. */
	public static final int HELMET = 6;
	/** Additions to 13.3. A sensing player sees dust settle on a live mine; entity = the mine, to that player only. */
	public static final int MINE_SENSED = 7;
	/** The boss music for one participant: intensity = the phase (1 to 3), 0 = stop. */
	public static final int MUSIC = 8;
	/** The axe blocks a saber: sparks at pos. */
	public static final int CLASH = 9;
	/** The hunter's echo appears (intensity 1) or fades (intensity 0). */
	public static final int ECHO = 10;
	/** He burns off a choke with his flamer (dir = toward the caster). */
	public static final int BURN_OFF = 11;
	/** A mine bursts (intensity = the blast's reach). */
	public static final int MINE_BURST = 12;

	private KaastEffects() {
	}
}

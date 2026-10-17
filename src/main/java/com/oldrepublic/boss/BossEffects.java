package com.oldrepublic.boss;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the Collector fight (spec 13.3, 16) and their variants. One event per gameplay
 * moment; the client's {@code BossFx} draws and sounds each one. The tells (variant 0 of
 * {@code boss_hurl}, {@code boss_palm}, {@code boss_lash}, {@code boss_surge}, {@code boss_grip} and
 * {@code boss_lunge}) are in {@code DangerTellTable}, so their particles go through {@code Vfx.tellCount}.
 */
public final class BossEffects {
	/** Archive Hurl. pos = the chunk, entityId = Vasch (the caught variant: the chunk). */
	public static final Identifier HURL = OldRepublicMod.id("boss_hurl");
	public static final int HURL_LIFT = 0;
	public static final int HURL_THROW = 1;
	public static final int HURL_RIPPED = 2;
	public static final int HURL_RETURNED = 3;
	public static final int HURL_CAUGHT = 4;

	/** Repelling Palm. pos = the palm, dir = his facing, entityId = Vasch. */
	public static final Identifier PALM = OldRepublicMod.id("boss_palm");
	public static final int PALM_TELL = 0;
	public static final int PALM_RELEASE = 1;
	public static final int PALM_CLASH = 2;

	/** Resonant Lash. pos = the start of the line, dir = along the line; the wave's intensity = its length. */
	public static final Identifier LASH = OldRepublicMod.id("boss_lash");
	public static final int LASH_TELL = 0;
	public static final int LASH_WAVE = 1;

	/** Holocron Surge. Gather: intensity = gather ticks; release: intensity = radius; clash: entityId = the clashing player. */
	public static final Identifier SURGE = OldRepublicMod.id("boss_surge");
	public static final int SURGE_GATHER = 0;
	public static final int SURGE_RELEASE = 1;
	public static final int SURGE_CLASH = 2;

	/** Spent after the Surge; intensity = spent ticks. */
	public static final Identifier SPENT = OldRepublicMod.id("boss_spent");

	/** Archive Grip. entityId = the target; hold: intensity 1 on the first pulse, 0 after. */
	public static final Identifier GRIP = OldRepublicMod.id("boss_grip");
	public static final int GRIP_TELL = 0;
	public static final int GRIP_HOLD = 1;
	public static final int GRIP_BREAK = 2;

	/** Lunge. dir = toward the target. */
	public static final Identifier LUNGE = OldRepublicMod.id("boss_lunge");
	public static final int LUNGE_TELL = 0;
	public static final int LUNGE_STRIKE = 1;

	/** Resonance Ward. Variant 0: intensity = pylons singing (0-4); variant 1: Unsung. */
	public static final Identifier WARD = OldRepublicMod.id("boss_ward");
	public static final int WARD_LEVEL = 0;
	public static final int WARD_UNSUNG = 1;

	/**
	 * The fight's phase moments, sent to each participant ({@code toPlayer}). Intensity 1 for a real
	 * moment, 0 for a sync-only resend of the current phase to a joining or returning participant.
	 */
	public static final Identifier PHASE = OldRepublicMod.id("boss_phase");
	public static final int PHASE_START = 0;
	public static final int PHASE_TRANSITION_ONE = 1;
	public static final int PHASE_TRANSITION_TWO = 2;
	public static final int PHASE_YIELD = 3;
	public static final int PHASE_RESET = 4;
	public static final int PHASE_VICTORY = 5;
	public static final int PHASE_MUSIC_STOP = 6;

	/** He kneels: the blade retracts, the crystal light dims, dust settles. */
	public static final Identifier YIELD = OldRepublicMod.id("boss_yield");

	/** The leash. Vanish at the old position, reappear at the dais. */
	public static final Identifier LEASH = OldRepublicMod.id("boss_leash");
	public static final int LEASH_VANISH = 0;
	public static final int LEASH_REAPPEAR = 1;

	/** The rematch echo. Form: intensity = form ticks; dissolve at 0 health. */
	public static final Identifier ECHO = OldRepublicMod.id("boss_echo");
	public static final int ECHO_FORM = 0;
	public static final int ECHO_DISSOLVE = 1;

	/** The ending scenes of 8.7; the variant is the scene. */
	public static final Identifier RESOLVE = OldRepublicMod.id("collector_resolve");
	public static final int RESOLVE_CUSTODY = 0;
	public static final int RESOLVE_WALK_OUT_SITH = 1;
	public static final int RESOLVE_EXECUTE = 2;
	public static final int RESOLVE_BOUND = 3;
	public static final int RESOLVE_RELEASE = 4;
	public static final int RESOLVE_BROUGHT_IN = 5;

	/** A pylon. pos = the crystal, entityId = Vasch or -1; hit: intensity = resonance left. */
	public static final Identifier PYLON_STATE = OldRepublicMod.id("pylon_state");
	public static final int PYLON_SING = 0;
	public static final int PYLON_HIT = 1;
	public static final int PYLON_SILENCED = 2;
	public static final int PYLON_REGROW = 3;
	public static final int PYLON_RESUNG = 4;
	public static final int PYLON_SHATTER = 5;

	/** The vault seal crumbles and the hoard is open. pos = the hoard plinth, dir = from it to the vault seal (zero when none). */
	public static final Identifier HOARD_OPEN = OldRepublicMod.id("hoard_open");

	private BossEffects() {
	}
}

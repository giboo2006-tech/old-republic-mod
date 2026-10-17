package com.oldrepublic.world.puzzle;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.world.WorldEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Effect event ids of the puzzle kit (spec 13.3). One event per gameplay moment; the client builds
 * the dust, light and sound (see {@code client.world.puzzle.PuzzleEffectHandlers}). Unless noted,
 * each is sent with {@link #send} around the block.
 */
public final class PuzzleEffects {
	/** Veiled stone revealed by Sense. Intensity = group size, variant = {@link PuzzleStates.Skin} ordinal. */
	public static final Identifier VEIL_REVEAL = OldRepublicMod.id("veil_reveal");
	/** A veiled group opened. Intensity = group size, variant = {@link PuzzleStates.Skin} ordinal. */
	public static final Identifier VEIL_OPEN = OldRepublicMod.id("veil_open");
	/** A counterweight sank, warns or rose; the variant says which. */
	public static final Identifier COUNTERWEIGHT = OldRepublicMod.id("counterweight");
	/** A step stone extended (0) or retracted (1); one event per stone. */
	public static final Identifier STEP_STONE = OldRepublicMod.id("step_stone");
	/** An urn echoes; variant = the step of the sequence (0–3), which sets the note. */
	public static final Identifier URN_ECHO = OldRepublicMod.id("urn_echo");
	/** The wrong urn was pushed: a dust curtain falls around it. */
	public static final Identifier URN_WRONG = OldRepublicMod.id("urn_wrong");
	/** A pull anchor strains (0) or releases (1). */
	public static final Identifier ANCHOR = OldRepublicMod.id("anchor");
	/** Collapsed rubble shudders as a Lift channel starts (0) or collapses as it clears (1). */
	public static final Identifier RUBBLE_CRUMBLE = OldRepublicMod.id("rubble_crumble");
	/** The M1 seal id, shared: variant {@link #SEAL_PUZZLE} is a puzzle seal, 0 stays the resonant seal. Intensity = group size. */
	public static final Identifier SEAL_OPEN = WorldEffects.SEAL_OPEN;
	/** A puzzle was solved: a soft chord for each player near ({@code EffectEvents.toPlayer}). */
	public static final Identifier PUZZLE_SOLVED = OldRepublicMod.id("puzzle_solved");
	/** A holocron pedestal became ready; variant = {@link PuzzleStates.PedestalKind} ordinal. */
	public static final Identifier PEDESTAL_READY = OldRepublicMod.id("pedestal_ready");
	/** A tomb vent's tell (0, a Danger Sense tell) or puff (1). */
	public static final Identifier TOMB_VENT = OldRepublicMod.id("tomb_vent");

	public static final int COUNTERWEIGHT_SINK = 0;
	public static final int COUNTERWEIGHT_WARN = 1;
	public static final int COUNTERWEIGHT_RISE = 2;

	public static final int STEP_EXTEND = 0;
	public static final int STEP_RETRACT = 1;

	public static final int ANCHOR_STRAIN = 0;
	public static final int ANCHOR_RELEASE = 1;

	public static final int RUBBLE_SHUDDER = 0;
	public static final int RUBBLE_COLLAPSE = 1;

	/** {@link #SEAL_OPEN} variant of a puzzle seal. */
	public static final int SEAL_PUZZLE = 1;

	public static final int VENT_TELL = 0;
	public static final int VENT_PUFF = 1;

	/** Sends {@code effect} around the centre of {@code pos}. */
	public static void send(ServerLevel level, Identifier effect, BlockPos pos, float intensity, int variant) {
		EffectEvents.around(level, effect, Vec3.atCenterOf(pos), null, -1, intensity, variant);
	}

	private PuzzleEffects() {
	}
}

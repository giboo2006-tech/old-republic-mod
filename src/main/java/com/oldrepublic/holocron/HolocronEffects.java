package com.oldrepublic.holocron;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the holocron feature (spec 13.3). One event per gameplay moment; clients build
 * the particles, the projected gatekeeper and the sounds locally.
 */
public final class HolocronEffects {
	/**
	 * Around the pedestal: {@code variant} = {@link HolocronKind} ordinal, {@code intensity} 1 rise or
	 * 0 sink, {@code entityId} = the rise duration in ticks, {@code pos} = the pedestal's top centre.
	 */
	public static final Identifier ATTUNE = OldRepublicMod.id("holocron_attune");
	/**
	 * To the attuning player only: {@code variant} = gatekeeper index, {@code intensity} 1 form or 0
	 * fade, {@code pos} = the pedestal's top centre, {@code dir} = unit horizontal vector from the
	 * pedestal toward the player, {@code entityId} = the player.
	 */
	public static final Identifier GATEKEEPER = OldRepublicMod.id("holocron_gatekeeper");
	/**
	 * Around the pedestal (or the player): {@link #CHOICE_SEAL}, {@link #CHOICE_BREAK},
	 * {@link #CHOICE_LISTEN} or {@link #CHOICE_OWN}; {@code variant} bits 8 and up hold the
	 * {@link HolocronKind} ordinal; {@code entityId} = the player; {@code intensity} 1 when a
	 * holocron rose from a pedestal at {@code pos}, 0 for an operator grant (no pedestal).
	 */
	public static final Identifier CHOICE = OldRepublicMod.id("holocron_choice");
	/** Around the pedestal: {@code variant} = gatekeeper index of the speaker; {@code entityId} = the player. */
	public static final Identifier ECHO = OldRepublicMod.id("holocron_echo");
	/** Around the hoard plinth: {@link #ARCHIVE_RETURN}, {@link #ARCHIVE_KEEP} or {@link #ARCHIVE_UNMAKE}; {@code entityId} = the player. */
	public static final Identifier ARCHIVE = OldRepublicMod.id("archive_choice");

	public static final int CHOICE_SEAL = 0;
	public static final int CHOICE_BREAK = 1;
	public static final int CHOICE_LISTEN = 2;
	/**
	 * "I will remember": the risen holocron settles into the player's hands. Not in the 13.3 table
	 * (OWN has no look of its own there); every confirmed attunement ends with one choice event, so
	 * onlookers see the holocron leave the cradle.
	 */
	public static final int CHOICE_OWN = 3;
	/** Mask of the choice in a {@link #CHOICE} variant. */
	public static final int CHOICE_MASK = 0xFF;
	/** Shift of the holocron kind ordinal in a {@link #CHOICE} variant. */
	public static final int KIND_SHIFT = 8;

	public static final int ARCHIVE_RETURN = 0;
	public static final int ARCHIVE_KEEP = 1;
	public static final int ARCHIVE_UNMAKE = 2;

	private HolocronEffects() {
	}
}

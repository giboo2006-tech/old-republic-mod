package com.oldrepublic.equipment;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids and variants of the Milestone 4 gear (spec MILESTONE_4.md 13.3 and 16.3). Sent
 * through {@code EffectEvents}; the client ({@code client.equipment.GearEffectsClient}) builds the
 * particles and plays the sounds. The variants spec 13.3 lists keep their numbers; the variants
 * after them are W4's own (personal feedback and loop stops), documented in {@code docs/design/gear.md}.
 */
public final class GearEffects {
	/**
	 * A player's blaster fired: {@code toTracking}, pos the muzzle, dir the shot, entity the shooter.
	 * Variants {@link #FIRE_PISTOL}, {@link #FIRE_RIFLE}, {@link #FIRE_REPEATER}, {@link #FIRE_CHARGED};
	 * {@link #CHARGE_START} (toTracking) and the shooter-only {@link #HIT}, {@link #DEFLECTED},
	 * {@link #CHARGE_READY} ({@code toPlayer}).
	 */
	public static final Identifier PLAYER_BLASTER_FIRE = OldRepublicMod.id("player_blaster_fire");
	public static final int FIRE_PISTOL = 0;
	public static final int FIRE_RIFLE = 1;
	public static final int FIRE_REPEATER = 2;
	public static final int FIRE_CHARGED = 3;
	/** Shooter only: a bolt of theirs hit a living target (the reticle's hit marker); pos the target. */
	public static final int HIT = 4;
	/** Shooter only: a bolt of theirs was deflected (the reticle's deflect marker). */
	public static final int DEFLECTED = 5;
	/** Shooter only: the rifle's charged shot is loaded. */
	public static final int CHARGE_READY = 6;
	/** The rifle's charge began (everyone near hears it); intensity 0 when a charge is dropped. */
	public static final int CHARGE_START = 7;

	/** A blaster's heat event: {@code toTracking}, pos the muzzle, entity the holder. */
	public static final Identifier BLASTER_HEAT = OldRepublicMod.id("blaster_heat");
	public static final int HEAT_OVERHEAT = 0;
	public static final int HEAT_VENT = 1;
	public static final int HEAT_PERFECT = 2;

	/** The repeater's barrel: {@code toTracking}, entity the holder. */
	public static final Identifier REPEATER_SPIN = OldRepublicMod.id("repeater_spin");
	public static final int SPIN_UP = 0;
	public static final int SPIN_DOWN = 1;

	/** A vibroweapon moment: {@code around}, pos the impact, entity the wielder. */
	public static final Identifier VIBRO_HIT = OldRepublicMod.id("vibro_hit");
	public static final int VIBRO_FLURRY = 0;
	public static final int VIBRO_CLEAVE = 1;
	/** A raised guard took a melee hit; intensity the fraction of guard stamina left. */
	public static final int VIBRO_GUARD = 2;
	/** A saber parry notched the blade, or a guard broke. */
	public static final int VIBRO_NOTCH = 3;

	/** The electrostaff: {@code toTracking}, entity the wielder. */
	public static final Identifier ELECTROSTAFF = OldRepublicMod.id("electrostaff");
	public static final int STAFF_CHARGE = 0;
	public static final int STAFF_SHOCK = 1;
	public static final int STAFF_BLOCK_SABER = 2;
	/** The charge is loaded: the crackle loop starts and runs until the shock or {@link #STAFF_SPENT}. */
	public static final int STAFF_CHARGED = 3;
	/** The charge ended without a shock (its window ran out, or the staff left the hand): stop the loop. */
	public static final int STAFF_SPENT = 4;
	/** A full swing swept the enemies beside the struck one; pos the struck one. */
	public static final int STAFF_SWEEP = 5;

	/** The jet pack: {@code toTracking}, pos the pack, entity the wearer. */
	public static final Identifier JET_PACK = OldRepublicMod.id("jet_pack");
	public static final int JET_BURST = 0;
	public static final int JET_EMPTY = 1;

	private GearEffects() {
	}
}

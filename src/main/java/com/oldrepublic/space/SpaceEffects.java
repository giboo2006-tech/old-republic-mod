package com.oldrepublic.space;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Space's effect events (spec MILESTONE_5.md 15.3, 18.3) and their variants. None is an enemy attack
 * tell in {@code DangerTellTable}: an incoming missile is announced by the combat HUD's warning.
 */
public final class SpaceEffects {
	/** {@code ship_weapon}: to everyone tracking the firing ship or fighter (entity = it; dir = the shot). */
	public static final Identifier SHIP_WEAPON = OldRepublicMod.id("ship_weapon");
	public static final int WEAPON_CANNONS = 0;
	public static final int WEAPON_TURRET = 1;
	public static final int WEAPON_MISSILE = 2;
	public static final int WEAPON_OVERHEAT = 3;
	public static final int WEAPON_FIGHTER_LASERS = 4;
	public static final int WEAPON_FIGHTER_MISSILE = 5;
	public static final int WEAPON_GUNSHIP_TURRET = 6;

	/** {@code ship_hit}: around the impact (intensity = damage). */
	public static final Identifier SHIP_HIT = OldRepublicMod.id("ship_hit");
	public static final int HIT_SHIELD = 0;
	public static final int HIT_HULL = 1;
	public static final int HIT_BLOCK = 2;

	/** {@code ship_shield}: to everyone tracking the ship (entity = the ship). */
	public static final Identifier SHIP_SHIELD = OldRepublicMod.id("ship_shield");
	public static final int SHIELD_DOWN = 0;
	public static final int SHIELD_RECHARGING = 1;
	public static final int SHIELD_FULL = 2;

	/** {@code missile_lock}: to one player (intensity = the lock's progress, 0 to 1). */
	public static final Identifier MISSILE_LOCK = OldRepublicMod.id("missile_lock");
	public static final int LOCK_LOCKING = 0;
	public static final int LOCK_LOCKED = 1;
	public static final int LOCK_LOST = 2;
	public static final int LOCK_INCOMING = 3;
	public static final int LOCK_INCOMING_END = 4;

	/** {@code ship_destroyed}: around (entity = the destroyed ship target). */
	public static final Identifier SHIP_DESTROYED = OldRepublicMod.id("ship_destroyed");
	public static final int DESTROYED_FIGHTER = 0;
	public static final int DESTROYED_GENERATOR = 1;
	public static final int DESTROYED_GUNSHIP_BLAST = 2;
	public static final int DESTROYED_GUNSHIP_END = 3;

	/** {@code gunship}: to everyone tracking the gunship (entity = it). */
	public static final Identifier GUNSHIP = OldRepublicMod.id("gunship");
	public static final int GUNSHIP_BAY_OPENS = 0;
	public static final int GUNSHIP_LAUNCH = 1;

	/** {@code space_comm}: to one player; variant = the speaker, intensity = the line's index. */
	public static final Identifier SPACE_COMM = OldRepublicMod.id("space_comm");
	public static final int SPEAKER_HYMN_PILOT = 0;
	public static final int SPEAKER_PIRATE = 1;
	public static final int SPEAKER_CLAN_PILOT = 2;
	public static final int SPEAKER_VARRA = 3;
	public static final int SPEAKER_ACE = 4;

	/** {@code space_scan}: to one player (intensity = the meter, 0 to 1). */
	public static final Identifier SPACE_SCAN = OldRepublicMod.id("space_scan");
	public static final int SCAN_SCANNING = 0;
	public static final int SCAN_BROKEN = 1;
	public static final int SCAN_COMPLETE = 2;

	/** {@code nav_buoy}: around (entity = the buoy). */
	public static final Identifier NAV_BUOY = OldRepublicMod.id("nav_buoy");
	public static final int BUOY_BLINK = 0;
	public static final int BUOY_REACHED = 1;

	private SpaceEffects() {
	}
}

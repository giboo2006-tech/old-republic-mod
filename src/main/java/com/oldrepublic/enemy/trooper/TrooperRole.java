package com.oldrepublic.enemy.trooper;

/**
 * A trooper's role in its squad (spec sections 7.2 to 7.4). Saved on each trooper as {@code Role}
 * by ordinal, so only append.
 */
public enum TrooperRole {
	RIFLEMAN,
	SERGEANT,
	MEDIC,
	GRENADIER,
	OFFICER
}

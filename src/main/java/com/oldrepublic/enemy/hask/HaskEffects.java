package com.oldrepublic.enemy.hask;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The Clan Hask warrior's own effect event (spec MILESTONE_5.md 15.3), to everyone tracking the
 * warrior, entity = the warrior. Variants 0 and 3 are attack tells in {@code DangerTellTable} (never
 * thinned). Its rifle, wrist rocket and jet hops send the M4 {@code hunter_tell} and
 * {@code hunter_attack} events by id, so the hunters' client draws them.
 */
public final class HaskEffects {
	/** {@code hask_warrior}. */
	public static final Identifier EVENT = OldRepublicMod.id("hask_warrior");
	/** The jet-slam tell: the pack whines, the warrior crouches (intensity = the tell's ticks). A tell. */
	public static final int SLAM_TELL = 0;
	/** The jet-slam lands: flame and a dust burst (pos = the landing). */
	public static final int SLAM = 1;
	/** A bolt or blow turned by the champion's clan-iron shield (pos = the contact). */
	public static final int SHIELD_BLOCK = 2;
	/** The shield-bash tell: the shield drawn back (intensity = the tell's ticks). A tell. */
	public static final int BASH_TELL = 3;
	/** The shield bash lands (pos = the contact). */
	public static final int BASH = 4;

	private HaskEffects() {
	}
}

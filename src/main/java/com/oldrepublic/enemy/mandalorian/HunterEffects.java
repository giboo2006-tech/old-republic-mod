package com.oldrepublic.enemy.mandalorian;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The hunters' effect events (spec MILESTONE_4.md 13.3). Every variant of {@link #TELL} is an attack
 * tell in {@code DangerTellTable}, so the client never thins it.
 */
public final class HunterEffects {
	/**
	 * {@code hunter_tell}, to everyone tracking the hunter. Intensity is the tell's length in ticks.
	 * Jet, flamer and net: entity = the hunter. Rocket: entity = the target (the red dot sits on it),
	 * position = the hunter's wrist. Kaast's own: the shoulder charge (dir = the charge) and the
	 * wind-up of his axe combo's third blow; entity = Kaast.
	 */
	public static final Identifier TELL = OldRepublicMod.id("hunter_tell");
	public static final int TELL_JET = 0;
	public static final int TELL_FLAMER = 1;
	public static final int TELL_ROCKET = 2;
	public static final int TELL_NET = 3;
	public static final int TELL_CHARGE = 4;
	public static final int TELL_AXE = 5;

	/**
	 * {@code hunter_attack}, around the position. Jet hop: entity = the hunter, dir = the hop,
	 * intensity = the flight ticks. Flame: entity = the hunter, intensity = the flame's ticks (the
	 * loop follows the hunter's synced flaming flag). Rocket burst: intensity = the radius. Net hit:
	 * entity = the caught player.
	 */
	public static final Identifier ATTACK = OldRepublicMod.id("hunter_attack");
	public static final int ATTACK_JET = 0;
	public static final int ATTACK_FLAME = 1;
	public static final int ATTACK_ROCKET = 2;
	public static final int ATTACK_NET = 3;

	private HunterEffects() {
	}
}

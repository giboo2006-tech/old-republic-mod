package com.oldrepublic.enemy.trooper;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the troopers (spec 13.3), handled on the client by
 * {@code client.enemy.TrooperEffectsClient}. Each is sent once per moment; pos, dir, entity,
 * intensity and variant are given per id. Shots use {@code EnemyEffects.BLASTER_FIRE} with the bolt
 * style as variant.
 */
public final class TrooperEffects {
	/** Charge tell starts. toTracking(trooper): muzzle / look / trooper / chargeTicks / bolt style ordinal. */
	public static final Identifier TROOPER_CHARGE = OldRepublicMod.id("trooper_charge");
	/** A Republic rifle starts venting. toTracking: muzzle / none / trooper / ventTicks / 0. */
	public static final Identifier TROOPER_VENT = OldRepublicMod.id("trooper_vent");
	/** A medic's kolto channel starts. toTracking(patient): patient chest / none / patient / channelTicks / 0. */
	public static final Identifier KOLTO_HEAL = OldRepublicMod.id("kolto_heal");
	/**
	 * A downed trooper is tended. toTracking(downed): downed / none / downed / 1 / {@link #TEND_TENDING}
	 * every 20 kneel ticks, {@link #TEND_STANDS} when it gets up.
	 */
	public static final Identifier TROOPER_TEND = OldRepublicMod.id("trooper_tend");
	public static final int TEND_TENDING = 0, TEND_STANDS = 1;
	/** A Republic sergeant's Rally tell starts. toTracking: sergeant / none / sergeant / rallyTellTicks / 0. */
	public static final Identifier TROOPER_RALLY = OldRepublicMod.id("trooper_rally");
	/** The Imperial flare fires at the end of its tell. around: sergeant head / up / sergeant / 20 (climb) / 0. */
	public static final Identifier FLARE_CALL = OldRepublicMod.id("flare_call");
	/** A grenadier's arm tell starts. toTracking: hand / toward target / grenadier / grenadeTellTicks / 0. */
	public static final Identifier GRENADE_TELL = OldRepublicMod.id("grenade_tell");
	/** Sent by the frag grenade when it lands. toTracking(grenade): grenade / none / grenade / fuseLength / 0. */
	public static final Identifier GRENADE_FUSE = OldRepublicMod.id("grenade_fuse");
	/** Sent by the frag grenade when it bursts. around: grenade / none / -1 / radius / 0. */
	public static final Identifier GRENADE_BURST = OldRepublicMod.id("grenade_burst");
	/** Sent by the frag grenade when Push or Pull redirects it. around: grenade / velocity / grenade / 1 / {@link #RETURN_PUSH} or {@link #RETURN_PULL}. */
	public static final Identifier GRENADE_RETURN = OldRepublicMod.id("grenade_return");
	public static final int RETURN_PUSH = 0, RETURN_PULL = 1;
	/** A radio bark. around: speaker eyes / none / speaker / barkRange / {@code BarkCategory#variant(line)}. */
	public static final Identifier TROOPER_BARK = OldRepublicMod.id("trooper_bark");
	/** A salute to an Allied player. toTracking: trooper / look / trooper / 30 / 0. */
	public static final Identifier TROOPER_SALUTE = OldRepublicMod.id("trooper_salute");

	private TrooperEffects() {
	}
}

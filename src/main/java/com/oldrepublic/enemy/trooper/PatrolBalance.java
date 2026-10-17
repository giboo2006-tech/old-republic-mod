package com.oldrepublic.enemy.trooper;

import com.oldrepublic.config.BalanceConfig;

/**
 * Tunable numbers for the natural patrol spawner (spec 7.6), stored in the {@code patrols} section
 * of {@code config/oldrepublic/balance.json}. Read on the server only, never during mod init.
 * Ticks are game ticks, distances are blocks.
 */
public final class PatrolBalance {
	/** When false, no natural patrols spawn (the debug command still works). */
	public boolean enabled = true;
	/** Each eligible player gets one roll this often, with {@link #chance} of a squad. */
	public int intervalTicks = 1200;
	public float chance = 0.15f;
	/** A squad appears this far from the player (horizontally), out of the player's sight. */
	public int minDistance = 48;
	public int maxDistance = 80;
	/** Its route passes this close to the player, then continues {@link #routeLength} beyond. */
	public int passDistance = 16;
	public int routeLength = 96;
	/** At most {@link #perPlayerCap} patrols within {@link #perPlayerRange} of a player, and {@link #globalCap} per server. */
	public int perPlayerRange = 128;
	public int perPlayerCap = 1;
	public int globalCap = 6;
	/** Every {@link #despawnCheckTicks}, a squad whose members are all farther than this from every player is removed. */
	public int despawnDistance = 128;
	public int despawnCheckTicks = 100;
	/** The squad belongs to the army whose outpost terminal is nearer, within this range; else a random army. */
	public int outpostBiasRange = 384;
	/** No patrol spawns or walks within this distance of a mission site or the Reliquary. */
	public int siteClearance = 64;

	public static PatrolBalance get() {
		return BalanceConfig.section("patrols", PatrolBalance.class, PatrolBalance::new);
	}
}

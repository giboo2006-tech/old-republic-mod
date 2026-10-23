package com.oldrepublic.equipment.melee;

/**
 * The electrostaff (spec MILESTONE_4.md 5.3): a two-handed staff with shock emitters at both ends.
 * Reach +1, its own longer swing, a sweep, the guard of the vibroweapons (−60% from the front) that
 * can also stop a saber without losing durability, and a charge (Weapon Action, 40 ticks) whose next
 * hit shocks and arcs to one more enemy. The behaviour lives in {@link MeleeCombat}.
 */
public class ElectrostaffItem extends VibroWeaponItem {
	public ElectrostaffItem(Properties properties) {
		super(Kind.STAFF, properties);
	}
}

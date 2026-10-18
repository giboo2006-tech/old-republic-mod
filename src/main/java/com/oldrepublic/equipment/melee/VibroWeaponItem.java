package com.oldrepublic.equipment.melee;

import net.minecraft.world.item.Item;

/**
 * A vibroweapon (spec MILESTONE_4.md 5.2). Phase 0 registers it in place for the M2 id
 * {@code vibroblade} (the Poacher Captain keeps holding it); W4 writes the behaviour and adds
 * {@code vibrosword}.
 */
public class VibroWeaponItem extends Item {
	public VibroWeaponItem(Item.Properties properties) {
		super(properties);
	}
}

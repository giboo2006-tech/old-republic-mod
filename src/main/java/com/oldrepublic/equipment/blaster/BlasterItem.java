package com.oldrepublic.equipment.blaster;

import net.minecraft.world.item.Item;

/**
 * A player's blaster (spec MILESTONE_4.md 5.1). Phase 0 registers it in place for the M1 ids
 * {@code blaster_pistol} and {@code blaster_rifle} (enemies keep holding them) and for
 * {@code heavy_repeater}; W4 writes the firing, heat and vent behaviour.
 */
public class BlasterItem extends Item {
	/** The three blasters. Stored by ordinal in effect variants; append only. */
	public enum Kind {
		PISTOL,
		RIFLE,
		REPEATER
	}

	private final Kind kind;

	public BlasterItem(Kind kind, Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}
}

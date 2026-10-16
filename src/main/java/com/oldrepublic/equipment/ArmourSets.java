package com.oldrepublic.equipment;

import com.oldrepublic.combat.Allegiance;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The era armour sets for other features (spec section 9): what an entity wears and the pieces the
 * story hands out. Signatures are frozen (spec section 15.1).
 */
public final class ArmourSets {
	/**
	 * How many pieces of {@code set} the entity wears; {@code jedi_knight_robe} and
	 * {@code sith_lord_robe} count as their set's robe. Phase 0 stub: W4 fills this in.
	 */
	public static int count(LivingEntity e, ArmourSet set) {
		return 0;
	}

	/** Whether the entity wears all four pieces of {@code set}. Phase 0 stub: W4 fills this in. */
	public static boolean fullSet(LivingEntity e, ArmourSet set) {
		return false;
	}

	/** A new stack of the set's piece for {@code slot}, or empty for a slot the set has no piece in. Phase 0 stub: W4 fills this in. */
	public static ItemStack piece(ArmourSet set, EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	/**
	 * The allegiance a worn uniform passes for (9.5); {@link Allegiance#NONE} while the disguise is cut.
	 * Phase 0 stub: W4 fills this in.
	 */
	public static Allegiance disguise(LivingEntity e) {
		return Allegiance.NONE;
	}

	private ArmourSets() {
	}
}

package com.oldrepublic.equipment;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.registry.ModComponents;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The era armour sets for other features (spec section 9): what an entity wears and the pieces the
 * story hands out. Signatures are frozen (spec section 15.1). A worn piece belongs to the set named by
 * its {@code oldrepublic:equipment_traits} component, so the rank 3 and rank 4 robes count as their
 * set's robe and the Wayfarer's mantle as the spacer's jacket (MILESTONE_5.md 8.5).
 */
public final class ArmourSets {
	/** The four armour slots, head to feet. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/**
	 * How many pieces of {@code set} the entity wears; {@code jedi_knight_robe} and
	 * {@code sith_lord_robe} count as their set's robe.
	 */
	public static int count(LivingEntity e, ArmourSet set) {
		return worn(e).count(set);
	}

	/** Whether the entity wears all four pieces of {@code set}. */
	public static boolean fullSet(LivingEntity e, ArmourSet set) {
		return count(e, set) >= SetBonusRules.FULL;
	}

	/** A new stack of the set's piece for {@code slot}, or empty for a slot the set has no piece in. */
	public static ItemStack piece(ArmourSet set, EquipmentSlot slot) {
		int index = switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			case FEET -> 3;
			default -> -1;
		};

		if (index < 0) {
			return ItemStack.EMPTY;
		}

		ArmourItem[] pieces = switch (set) {
			case REPUBLIC_TROOPER -> new ArmourItem[] {EquipmentItems.REPUBLIC_TROOPER_HELMET, EquipmentItems.REPUBLIC_TROOPER_CHESTPLATE,
					EquipmentItems.REPUBLIC_TROOPER_LEGGINGS, EquipmentItems.REPUBLIC_TROOPER_BOOTS};
			case SITH_TROOPER -> new ArmourItem[] {EquipmentItems.SITH_TROOPER_HELMET, EquipmentItems.SITH_TROOPER_CHESTPLATE,
					EquipmentItems.SITH_TROOPER_LEGGINGS, EquipmentItems.SITH_TROOPER_BOOTS};
			case JEDI_ROBES -> new ArmourItem[] {EquipmentItems.JEDI_HOOD, EquipmentItems.JEDI_ROBE, EquipmentItems.JEDI_TROUSERS, EquipmentItems.JEDI_BOOTS};
			case SITH_ROBES -> new ArmourItem[] {EquipmentItems.SITH_HOOD, EquipmentItems.SITH_ROBE, EquipmentItems.SITH_TROUSERS, EquipmentItems.SITH_BOOTS};
			case WATCH_BATTLE -> new ArmourItem[] {EquipmentItems.WATCH_BATTLE_HELMET, EquipmentItems.WATCH_BATTLE_CHEST,
					EquipmentItems.WATCH_BATTLE_LEGS, EquipmentItems.WATCH_BATTLE_BOOTS};
			case SITH_WAR -> new ArmourItem[] {EquipmentItems.SITH_WAR_HELMET, EquipmentItems.SITH_WAR_CHEST,
					EquipmentItems.SITH_WAR_LEGS, EquipmentItems.SITH_WAR_BOOTS};
			case MANDALORIAN -> new ArmourItem[] {EquipmentItems.MANDALORIAN_HELMET, EquipmentItems.MANDALORIAN_CHEST,
					EquipmentItems.MANDALORIAN_LEGS, EquipmentItems.MANDALORIAN_BOOTS};
			case SPACER -> new ArmourItem[] {EquipmentItems.SPACER_CAP, EquipmentItems.SPACER_JACKET, EquipmentItems.SPACER_TROUSERS, EquipmentItems.SPACER_BOOTS};
			default -> null;
		};

		if (pieces == null) {
			return ItemStack.EMPTY;
		}

		return new ItemStack(pieces[index]);
	}

	/**
	 * The allegiance a worn uniform passes for (9.5); always {@link Allegiance#NONE}, because the
	 * uniform disguise is cut: {@code Allegiances.of(player)} reads this, and a live disguise would
	 * let every trooper bolt pass through the wearer.
	 */
	public static Allegiance disguise(LivingEntity e) {
		return Allegiance.NONE;
	}

	/**
	 * The rank 3 robe of a robe set (5.8): {@code jedi_knight_robe} for {@link ArmourSet#JEDI_ROBES},
	 * {@code sith_lord_robe} for {@link ArmourSet#SITH_ROBES}, else empty.
	 */
	public static ItemStack rankRobe(ArmourSet set) {
		return switch (set) {
			case JEDI_ROBES -> new ItemStack(EquipmentItems.JEDI_KNIGHT_ROBE);
			case SITH_ROBES -> new ItemStack(EquipmentItems.SITH_LORD_ROBE);
			default -> ItemStack.EMPTY;
		};
	}

	/**
	 * The rank 4 piece of a path (MILESTONE_5.md 8.5), a new stack: {@code jedi_master_robe} for
	 * {@link Path#JEDI}, {@code sith_darth_robe} for {@link Path#SITH}, {@code wayfarer_mantle} for
	 * {@link Path#INDEPENDENT}, else empty. The ceremonies (W5) give it and reissue it.
	 */
	public static ItemStack rankFourRobe(Path path) {
		return switch (path) {
			case JEDI -> new ItemStack(EquipmentItems.JEDI_MASTER_ROBE);
			case SITH -> new ItemStack(EquipmentItems.SITH_DARTH_ROBE);
			case INDEPENDENT -> new ItemStack(EquipmentItems.WAYFARER_MANTLE);
			default -> ItemStack.EMPTY;
		};
	}

	/**
	 * Shoulder to Shoulder: the multiplier on saber guard drain from bolts, 0.75 with the full
	 * Republic trooper set ({@code republicSetBoltDrainPct}), else 1. Server side (live balance).
	 */
	public static float boltGuardDrainMultiplier(LivingEntity e) {
		return SetBonusRules.boltDrainMultiplier(worn(e), EquipmentBalance.get());
	}

	/** How many pieces of every set the entity wears. Works on both sides. */
	public static SetBonusRules.Worn worn(LivingEntity e) {
		SetBonusRules.Worn worn = SetBonusRules.Worn.NONE;

		for (EquipmentSlot slot : SLOTS) {
			EquipmentTraits traits = e.getItemBySlot(slot).get(ModComponents.EQUIPMENT_TRAITS);

			if (traits != null) {
				worn = worn.plus(traits.set());
			}
		}

		return worn;
	}

	/** The Milestone 4 pieces {@code e} wears, each with its quality's armour multiplier (MILESTONE_4.md 6.2, 7.4). */
	public static List<SetBonusRules.Piece> m4Pieces(LivingEntity e) {
		List<SetBonusRules.Piece> pieces = new ArrayList<>(SLOTS.length);

		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = e.getItemBySlot(slot);
			EquipmentTraits traits = stack.get(ModComponents.EQUIPMENT_TRAITS);

			if (traits != null && traits.set().isM4()) {
				pieces.add(new SetBonusRules.Piece(traits.set(), Crafting.quality(stack).armour()));
			}
		}

		return pieces;
	}

	/**
	 * The control duration multiplier of the worn pieces' {@code Extra.controlPct}, quality
	 * strengthening a shortening (1 when no piece has one).
	 */
	public static float extraControl(LivingEntity e) {
		float mult = 1f;

		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = e.getItemBySlot(slot);
			EquipmentTraits traits = stack.get(ModComponents.EQUIPMENT_TRAITS);

			if (traits != null && traits.extra().controlPct() != 0f) {
				float q = traits.set().isM4() ? Crafting.quality(stack).armour() : 1f;
				mult *= SetBonusRules.pieceRatio(traits.extra().controlPct() * 100f, q, true);
			}
		}

		return mult;
	}

	/** The robe gates' view of {@code e}: alignment, band and path for a player, {@link SetBonusRules.Wearer#NOBODY} otherwise. */
	public static SetBonusRules.Wearer wearer(LivingEntity e) {
		if (e instanceof Player p) {
			return new SetBonusRules.Wearer(Identity.alignment(p), Identity.band(p), Identity.path(p));
		}

		return SetBonusRules.Wearer.NOBODY;
	}

	private ArmourSets() {
	}
}

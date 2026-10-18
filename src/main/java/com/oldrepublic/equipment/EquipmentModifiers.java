package com.oldrepublic.equipment;

import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.DamageModifier;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.jet.JetPack;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.progression.ForceModifier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Applies the worn gear through the shared modifier hooks (spec section 9.2 and MILESTONE_4.md 6):
 * Force cost, regeneration and max energy ({@link ForceModifier}), damage taken and dealt
 * ({@link DamageModifier}) and control durations ({@link ControlEffects.DurationModifier}). The M3
 * sets keep their M3 rules ({@link SetBonusRules}, counted per set); the M4 pieces add their traits
 * per piece at their quality, then the M4 set bonuses and the fitted mods multiply on top. The
 * registries' clamps (cost 0.70 to 1.50, regeneration 0.50 to 1.50, damage 0.25 to 2.0) bound the
 * whole product, so mods never stack past them; control is bounded here ({@code controlMin}).
 * Server side; the numbers are the live balance.
 */
public final class EquipmentModifiers implements ForceModifier, DamageModifier, ControlEffects.DurationModifier {
	/** W6's Force Choke damage type, named by id so this class does not load W6's power. */
	private static final ResourceKey<DamageType> CHOKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("force_choke"));

	@Override
	public float costMultiplier(Player p, String abilityId) {
		SetBonusRules.Worn worn = ArmourSets.worn(p);

		if (worn.equals(SetBonusRules.Worn.NONE)) {
			return 1f;
		}

		EquipmentM4Balance m = EquipmentM4Balance.get();
		float mult = SetBonusRules.costMultiplier(worn, ArmourSets.wearer(p), abilityId, EquipmentBalance.get())
				* m4(p, m).cost()
				* ModEffects.wornCost(p, EquipmentM4Balance.mods());
		return mult * SetBonusRules.setCostMultiplier(worn, abilityId, SetBonuses.warden(p), SetBonuses.wrath(p), m);
	}

	@Override
	public float regenMultiplier(Player p) {
		SetBonusRules.Worn worn = ArmourSets.worn(p);

		if (worn.equals(SetBonusRules.Worn.NONE)) {
			return 1f;
		}

		return SetBonusRules.regenMultiplier(worn, EquipmentBalance.get()) * m4(p, EquipmentM4Balance.get()).regen()
				* ModEffects.wornRegen(p, EquipmentM4Balance.mods());
	}

	@Override
	public int maxEnergyBonus(Player p) {
		SetBonusRules.Worn worn = ArmourSets.worn(p);

		if (worn.equals(SetBonusRules.Worn.NONE)) {
			return 0;
		}

		return SetBonusRules.maxEnergyBonus(worn, EquipmentBalance.get()) + m4(p, EquipmentM4Balance.get()).maxEnergy();
	}

	/**
	 * The victim's side (armour traits, mods and set bonuses against blaster, melee and fall
	 * damage; the raised vibroweapon guard) times the attacker's side (blaster mods on the bolt,
	 * the keen edge, Wrath Unbound).
	 */
	@Override
	public float modify(LivingEntity victim, DamageSource source, float amount) {
		return amount * taken(victim, source, amount) * dealt(source);
	}

	/** Iron Discipline, Hunter's Tenacity, stabiliser mods and the pieces' own control traits, together. */
	@Override
	public float multiplier(LivingEntity target, String kind) {
		SetBonusRules.Worn worn = ArmourSets.worn(target);

		if (worn.equals(SetBonusRules.Worn.NONE)) {
			return 1f;
		}

		EquipmentM4Balance m = EquipmentM4Balance.get();
		float product = SetBonusRules.controlMultiplier(worn, EquipmentBalance.get())
				* SetBonusRules.hunterControl(worn, m)
				* ModEffects.wornControl(target, EquipmentM4Balance.mods())
				* ArmourSets.extraControl(target);
		return SetBonusRules.controlTotal(product, m);
	}

	/** The multiplier on damage {@code victim} takes from {@code source}, from what it wears and holds. */
	private static float taken(LivingEntity victim, DamageSource source, float amount) {
		SetBonusRules.Worn worn = ArmourSets.worn(victim);

		if (source.getDirectEntity() instanceof BlasterBolt) {
			if (worn.equals(SetBonusRules.Worn.NONE)) {
				return 1f;
			}

			return SetBonusRules.blasterMultiplier(worn, EquipmentBalance.get()) * m4(victim, EquipmentM4Balance.get()).blasterTaken()
					* ModEffects.wornBlaster(victim, EquipmentM4Balance.mods());
		}

		if (source.is(DamageTypeTags.IS_FALL)) {
			float jet = JetPack.fallMultiplier(victim);

			if (worn.equals(SetBonusRules.Worn.NONE)) {
				return jet;
			}

			EquipmentM4Balance m = EquipmentM4Balance.get();
			return jet * m4(victim, m).fallTaken() * SetBonusRules.spacerFall(worn, m);
		}

		if (source.getDirectEntity() instanceof LivingEntity) {
			float guard = MeleeCombat.guardMultiplier(victim, source, amount);

			if (worn.equals(SetBonusRules.Worn.NONE)) {
				return guard;
			}

			EquipmentM4Balance m = EquipmentM4Balance.get();
			return guard * SetBonusRules.meleeMultiplier(worn, EquipmentBalance.get()) * m4(victim, m).meleeTaken()
					* ModEffects.wornMelee(victim, EquipmentM4Balance.mods())
					* SetBonusRules.wardenMeleeTaken(SetBonuses.warden(victim), m);
		}

		return 1f;
	}

	/** The multiplier the attacker's gear puts on damage it deals through {@code source}. */
	private static float dealt(DamageSource source) {
		if (source.getDirectEntity() instanceof BlasterBolt) {
			return Blasters.modDamage(source);
		}

		if (!(source.getEntity() instanceof LivingEntity attacker)) {
			return 1f;
		}

		if (source.getDirectEntity() == attacker && attacker.getMainHandItem().getItem() instanceof VibroWeaponItem) {
			ModNumbers mods = EquipmentM4Balance.mods();
			return ModEffects.meleeDamage(attacker.getMainHandItem(), mods);
		}

		if ((source.is(ForceDamage.LIGHTNING) || source.is(CHOKE_DAMAGE)) && SetBonuses.wrath(attacker)) {
			return SetBonusRules.wrathDamage(true, EquipmentM4Balance.get());
		}

		return 1f;
	}

	/** The worn M4 pieces' traits of {@code e} together. */
	private static SetBonusRules.GearTotals m4(LivingEntity e, EquipmentM4Balance m) {
		List<SetBonusRules.Piece> pieces = ArmourSets.m4Pieces(e);
		return pieces.isEmpty() ? SetBonusRules.GearTotals.NONE : SetBonusRules.totals(pieces, m);
	}
}

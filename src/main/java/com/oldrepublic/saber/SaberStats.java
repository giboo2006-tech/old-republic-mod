package com.oldrepublic.saber;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The numbers a hilt configuration produces. Pure computation from the parts, the blade length, the
 * crystals' tunings and {@link SaberBalance}; the blade colour never enters here.
 *
 * @param damage      ignited attack damage (including the player's base 1)
 * @param attackSpeed attacks per second
 * @param reachBonus  extra entity interaction range in blocks (negative for a short blade)
 * @param maxStamina  guard stamina
 * @param parryTicks  parry window length in ticks (guard ticks below this value parry)
 */
public record SaberStats(double damage, double attackSpeed, double reachBonus, float maxStamina, float parryTicks) {
	/** Modifier id for the saber's reach (the Guardian emitter, the staff, the blade length). */
	public static final Identifier REACH_MODIFIER_ID = OldRepublicMod.id("saber_reach");
	private static final double PLAYER_BASE_DAMAGE = 1.0;
	private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
	/** A player's base {@code ENTITY_INTERACTION_RANGE}, and what creative mode adds to it. */
	private static final float PLAYER_BASE_REACH = 3.0f;
	private static final float CREATIVE_REACH_BONUS = 2.0f;
	/** A mob's melee reach without an {@code attack_range} component (vanilla's {@code sqrt(2.04) - 0.6}). */
	private static final float MOB_DEFAULT_REACH = (float) (Math.sqrt(2.04) - 0.6);

	/**
	 * The legendary hilt parts' numbers (MILESTONE_5.md 11.2; balance section {@code crafting}). Both
	 * sides compute saber stats, so these start at the built-in defaults and the server hands over the
	 * live ones on start ({@code CraftingBalance.applyQuality}), as {@code Quality.configure} does.
	 *
	 * @param cantorDamagePct   the Cantor's Emitter: damage in percent more
	 * @param haskGuard         the Hask Pommel: guard stamina added
	 * @param haskParryTicks    the Hask Pommel: parry window ticks added (before the form multiplier)
	 * @param sunforgedSpeedPct the Sunforged Grip: attack speed in percent more
	 * @param sunforgedGuard    the Sunforged Grip: guard stamina added
	 */
	public record Legendary(float cantorDamagePct, float haskGuard, float haskParryTicks, float sunforgedSpeedPct, float sunforgedGuard) {
		public static final Legendary DEFAULTS = of(new CraftingBalance());

		public static Legendary of(CraftingBalance b) {
			return new Legendary(b.cantorDamagePct, b.haskGuard, b.haskParryTicks, b.sunforgedSpeedPct, b.sunforgedGuard);
		}
	}

	private static volatile Legendary legendary = Legendary.DEFAULTS;

	/** Replaces the legendary parts' numbers (server start). */
	public static void configureLegendary(Legendary numbers) {
		legendary = numbers;
	}

	/** The legendary parts' numbers in use. */
	public static Legendary legendary() {
		return legendary;
	}

	/** A saber of these parts; a null {@code pommel} (the saberstaff, which has none) adds nothing. */
	public static SaberStats of(Emitter emitter, Grip grip, @Nullable Pommel pommel, SaberBalance b) {
		Legendary l = legendary;
		double damage = b.baseDamage;
		double speed = b.baseAttackSpeed;
		double reach = 0;
		float stamina = b.guardStamina;
		float parry = b.parryWindowTicks;
		double damagePct = 0;
		double speedPct = 0;

		switch (emitter) {
			case GUARDIAN -> {
				reach += b.guardianReach;
				speed += b.guardianSpeed;
			}
			case DUELIST -> {
				speed += b.duelistSpeed;
				damage += b.duelistDamage;
			}
			// The Cantor's Emitter (MILESTONE_5.md 11.2): sharper strikes; its hum is the client's (10.5).
			case CANTOR -> damagePct += l.cantorDamagePct();
			default -> {
			}
		}

		switch (grip) {
			case WRAPPED -> stamina *= b.wrappedStaminaMultiplier;
			case RIDGED -> speed += b.ridgedSpeed;
			case SUNFORGED -> speedPct += l.sunforgedSpeedPct();
			// The staff grip's numbers are the staff's own (staff()).
			default -> {
			}
		}

		switch (pommel) {
			case null -> {
			}
			case WEIGHTED -> {
				damage += b.weightedDamage;
				speed += b.weightedSpeed;
			}
			case BALANCED -> parry *= b.balancedParryMultiplier;
			case ARCHIVIST -> {
				// A flat bonus: added before the form multiplier (withForm), like centered_mind.
				parry += b.archivistParryTicks;
				speed += b.archivistAttackSpeed;
			}
			case HASK -> {
				stamina += l.haskGuard();
				parry += l.haskParryTicks();
			}
			default -> {
			}
		}

		// Flat guard after the grip's multiplier, so the Sunforged Grip's stamina is plain.
		if (grip == Grip.SUNFORGED) {
			stamina += l.sunforgedGuard();
		}

		// Emitter.RELIQUARY changes no number here: its aimed deflections and parry bonus are SaberGuard's.

		return new SaberStats(damage * (1.0 + damagePct / 100.0), Math.max(0.1, speed * (1.0 + speedPct / 100.0)), reach, stamina, parry);
	}

	/** A single saber of this config: its parts, their materials (MILESTONE_7.md 3.2), then its blade length and its crystal's tuning. */
	public static SaberStats of(SaberConfig config, SaberBalance b) {
		return of(config.emitter(), config.grip(), config.pommel(), b).withMaterials(config.materials(), true, b).withLength(config.length(), b).tuned(config, b);
	}

	/**
	 * The stats of {@code stack} itself (spec MILESTONE_4.md 5.5, 5.6): a lightsaber's by its
	 * parts, blade length and tunings; a training saber's with its fixed damage; a saberstaff's by
	 * {@link #staff}, with its second blade lit or not, both crystals' tunings and no blade length
	 * (staffs stay standard, MILESTONE_5.md 10.3). Lens effects are not here: they depend on the
	 * wielder's {@code lens_mastery} and are applied where they act ({@code SaberLens}).
	 */
	public static SaberStats of(ItemStack stack, SaberBalance b) {
		SaberConfig config = SaberComponents.config(stack);

		if (stack.getItem() instanceof TrainingSaberItem) {
			SaberStats s = of(config, b);
			return new SaberStats(b.trainingSaberDamage, s.attackSpeed, s.reachBonus, s.maxStamina, s.parryTicks);
		}

		if (stack.getItem() instanceof SaberStaffItem) {
			return staff(config.emitter(), config.grip(), !SaberComponents.staffMode(stack).single(), b).withMaterials(config.materials(), false, b)
					.tuned(config, b);
		}

		return of(config, b);
	}

	/**
	 * A saberstaff of these parts (5.6): the hilt with no pommel, then with both blades lit damage
	 * {@code staffDamagePct}, attack speed {@code staffSpeedBonus}, reach {@code staffReach} and the
	 * parry window {@code staffParryTicks}. One blade gives a normal saber's damage and parry window
	 * and keeps the staff's speed and reach.
	 */
	public static SaberStats staff(Emitter emitter, Grip grip, boolean bothBlades, SaberBalance b) {
		SaberStats s = of(emitter, grip, null, b);
		double speed = s.attackSpeed + b.staffSpeedBonus;
		double reach = s.reachBonus + b.staffReach;

		if (!bothBlades) {
			return new SaberStats(s.damage, speed, reach, s.maxStamina, s.parryTicks);
		}

		return new SaberStats(s.damage * (1.0 + b.staffDamagePct / 100.0), speed, reach, s.maxStamina, Math.max(0f, s.parryTicks + b.staffParryTicks));
	}

	/**
	 * These stats with the parts made of {@code materials} (MILESTONE_7.md 3.2): the emitter's damage, the
	 * grip's attack speed and guard stamina and the pommel's parry ticks and guard stamina, each by its
	 * material's tier. A saberstaff ({@code pommel} false) has no pommel to count. All steel adds nothing.
	 */
	public SaberStats withMaterials(HiltMaterials materials, boolean pommel, SaberBalance b) {
		// Phase 0 contract (MILESTONE_7.md 12.1): HS implements the tier bonuses.
		return this;
	}

	/**
	 * These stats with blade length {@code length} (MILESTONE_5.md 10.3): a short blade (a shoto)
	 * reaches {@code shortReach} less far and swings {@code shortSpeedPct} faster for
	 * {@code shortDamagePct} damage; a long one the {@code long*} numbers; standard changes nothing.
	 */
	public SaberStats withLength(int length, SaberBalance b) {
		return switch (length) {
			case SaberConfig.LENGTH_SHORT -> new SaberStats(damage * (1.0 + b.shortDamagePct / 100.0), Math.max(0.1, attackSpeed * (1.0 + b.shortSpeedPct / 100.0)),
					reachBonus + b.shortReach, maxStamina, parryTicks);
			case SaberConfig.LENGTH_LONG -> new SaberStats(damage * (1.0 + b.longDamagePct / 100.0), Math.max(0.1, attackSpeed * (1.0 + b.longSpeedPct / 100.0)),
					reachBonus + b.longReach, maxStamina, parryTicks);
			default -> this;
		};
	}

	/**
	 * These stats with {@code config}'s crystal tunings (MILESTONE_5.md 10.4), each crystal counting
	 * (a saberstaff's two): Keen damage, Swift attack speed, Poised parry ticks (flat, before the form
	 * multiplier and under the cap), Warding guard stamina. Attuned changes no stat (a Force modifier).
	 */
	public SaberStats tuned(SaberConfig config, SaberBalance b) {
		return tuned(config.crystal(), config.second().orElse(null), b);
	}

	/** {@link #tuned(SaberConfig, SaberBalance)} for two crystals, either of which may be missing (the bench's preview). */
	public SaberStats tuned(@Nullable CrystalAttunement crystal, @Nullable CrystalAttunement second, SaberBalance b) {
		int keen = CrystalTuning.count(crystal, second, CrystalTuning.KEEN);
		int swift = CrystalTuning.count(crystal, second, CrystalTuning.SWIFT);
		int steady = CrystalTuning.count(crystal, second, CrystalTuning.STEADY);
		int warding = CrystalTuning.count(crystal, second, CrystalTuning.WARDING);

		if (keen + swift + steady + warding == 0) {
			return this;
		}

		return new SaberStats(damage * (1.0 + keen * b.keenPct / 100.0), Math.max(0.1, attackSpeed * (1.0 + swift * b.swiftPct / 100.0)), reachBonus,
				maxStamina + warding * b.wardingGuard, parryTicks + steady * b.steadyTicks);
	}

	/**
	 * These stats in a form, with the {@code steady_guard} passive at {@code steadyGuardRank} and
	 * {@code extraParryTicks} from other passives ({@code centered_mind}, {@code archivists_ward}).
	 * Only stamina and the parry window change here; the form's other numbers are read from
	 * {@link SaberForm.Modifiers}.
	 *
	 * <p>Parry window order (spec 10.4): the hilt's window (base, Balanced pommel, the Archivist
	 * pommel's flat ticks) plus the flat passive bonuses, then the form multiplier (Soresu), then the
	 * form's own flat ticks (Ataru, Makashi, Jar'Kai), then the cap {@code parryWindowCap}. Stamina:
	 * the hilt's, times {@code steady_guard} and the form's multiplier, plus the form's flat bonus
	 * (Jar'Kai).
	 */
	public SaberStats withForm(SaberForm.Modifiers form, int steadyGuardRank, float extraParryTicks, SaberBalance b) {
		float stamina = maxStamina * (1f + b.steadyGuardPerRank * Math.max(0, steadyGuardRank)) * form.maxStaminaMult() + form.traits().staminaBonus();
		float parry = parryWindow(parryTicks, extraParryTicks, form.parryMult(), form.parryTicks(), b.parryWindowCap);
		return new SaberStats(damage, attackSpeed, reachBonus, stamina, parry);
	}

	/**
	 * The parry window: {@code (hilt + flat) x formMult + formTicks}, never below 0 and never above
	 * {@code cap} (a cap of 0 or less means none).
	 */
	public static float parryWindow(float hilt, float flat, float formMult, float formTicks, float cap) {
		float window = Math.max(0f, (hilt + flat) * formMult + formTicks);
		return cap > 0f ? Math.min(window, cap) : window;
	}

	/** The attribute modifiers a saber carries while ignited or retracted (main hand only). */
	public ItemAttributeModifiers modifiers(boolean ignited, SaberBalance b) {
		double attackDamage = ignited ? damage : b.retractedDamage;
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed - PLAYER_BASE_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);

		if (ignited && reachBonus != 0) {
			// Interaction (and any reach check without the component) reads ENTITY_INTERACTION_RANGE; melee reach reads attackRange().
			builder.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_MODIFIER_ID, reachBonus, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}

		return builder.build();
	}

	/**
	 * The saber's own melee reach as an {@code attack_range} component (MILESTONE_5.md 10.3, 22.31), so
	 * each hand reads its own blade's reach: a player's base reach plus the bonus while ignited (the
	 * base alone retracted), the same in creative with creative's extra reach, no hitbox margin, and a
	 * mob factor that keeps a mob's reach at vanilla's default (saber NPCs fight as before).
	 */
	public AttackRange attackRange(boolean ignited) {
		float max = Math.max(0.5f, PLAYER_BASE_REACH + (ignited ? (float) reachBonus : 0f));
		float creative = Math.max(0.5f, max + CREATIVE_REACH_BONUS);
		// API-CHECK: AttackRange#effectiveMaxRange is assumed to return maxReach * mobFactor for a non-player (body not probed).
		return new AttackRange(0f, max, 0f, creative, 0f, Math.min(2f, MOB_DEFAULT_REACH / max));
	}

	/** Writes {@code stack}'s attribute modifiers and reach component for its parts and ignition (every config change). */
	public static void apply(ItemStack stack, boolean ignited, SaberBalance b) {
		SaberStats stats = of(stack, b);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, stats.modifiers(ignited, b));
		stack.set(DataComponents.ATTACK_RANGE, stats.attackRange(ignited));
	}
}

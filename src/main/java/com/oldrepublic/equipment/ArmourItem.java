package com.oldrepublic.equipment;

import java.util.Locale;
import java.util.function.Consumer;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * One piece of an era armour set (spec section 9). Its tooltip lists the exact per-piece traits from
 * its {@code oldrepublic:equipment_traits} component and the set bonus with its numbers. The numbers
 * are the registration defaults, because the client cannot read the server's balance file; the live
 * "Set n/4" line is added on the client by {@code ArmourTooltips}. A Milestone 4 piece's traits are
 * shown at its quality (MILESTONE_4.md 7.4: the helpful direction scales, a drawback does not).
 */
public class ArmourItem extends Item {
	/** Defaults used for the set bonus numbers in the tooltip. */
	private static final EquipmentBalance SHOWN = new EquipmentBalance();
	private static final EquipmentM4Balance SHOWN_M4 = new EquipmentM4Balance();
	private final ArmourSet set;
	private final ArmorType type;
	private final boolean rankRobe;

	public ArmourItem(Properties properties, ArmourSet set, ArmorType type, boolean rankRobe) {
		super(properties);
		this.set = set;
		this.type = type;
		this.rankRobe = rankRobe;
	}

	/** The set this piece belongs to. */
	public ArmourSet set() {
		return set;
	}

	/** Which piece it is. */
	public ArmorType type() {
		return type;
	}

	/** Whether this is a rank 3 robe ({@code jedi_knight_robe}, {@code sith_lord_robe}). */
	public boolean isRankRobe() {
		return rankRobe;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		EquipmentTraits traits = stack.get(ModComponents.EQUIPMENT_TRAITS);

		if (traits == null) {
			return;
		}

		tooltip.accept(Component.translatable("equipment.oldrepublic.set." + traits.set().id()).withStyle(ChatFormatting.GRAY));

		if (rankRobe) {
			tooltip.accept(Component.translatable("equipment.oldrepublic.rank_robe." + traits.set().id()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		}

		// M3 traits are counted per set and never scale; an M4 piece's do (quality 7.4).
		float q = traits.set().isM4() ? Crafting.quality(stack).armour() : 1f;
		EquipmentTraits.Extra extra = traits.extra();
		trait(tooltip, "cost", traits.costPct(), q, true);
		trait(tooltip, "regen", traits.regenPct(), q, false);
		trait(tooltip, "blaster", -traits.blasterResist(), q, true);
		trait(tooltip, "melee", -traits.meleeResist(), q, true);
		trait(tooltip, "speed", extra.speedPct(), 1f, false);
		trait(tooltip, "control", extra.controlPct(), q, true);
		trait(tooltip, "fall", extra.fallPct(), q, true);
		int maxEnergy = traits.maxEnergy() > 0 ? Math.round(traits.maxEnergy() * q) : traits.maxEnergy();

		if (maxEnergy != 0) {
			tooltip.accept(Component.translatable("equipment.oldrepublic.trait.max_energy", signed(maxEnergy))
					.withStyle(maxEnergy > 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
		}

		tooltip.accept(Component.translatable("equipment.oldrepublic.bonus.header",
				Component.translatable("equipment.oldrepublic.bonus." + traits.set().id())).withStyle(ChatFormatting.GOLD));

		for (Component line : bonusLines(traits.set())) {
			tooltip.accept(line.copy().withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** The set bonus lines with their numbers (defaults). */
	private static Component[] bonusLines(ArmourSet set) {
		EquipmentBalance b = SHOWN;
		EquipmentM4Balance m = SHOWN_M4;
		return switch (set) {
			case REPUBLIC_TROOPER -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.republic_trooper.blaster", percent(b.republicSetBlasterPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.republic_trooper.drain", percent(b.republicSetBoltDrainPct / 100f))};
			case SITH_TROOPER -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.sith_trooper.control", percent(b.sithSetControlPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.sith_trooper.knockback", percent(-b.sithSetKnockbackResist))};
			case JEDI_ROBES -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.gate.light", b.setAlignment),
					Component.translatable("equipment.oldrepublic.bonus.jedi_robes.parry", number(b.jediSetParryFe)),
					Component.translatable("equipment.oldrepublic.bonus.jedi_robes.cost", percent(b.jediSetHealCost / 100f))};
			case SITH_ROBES -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.gate.dark", b.setAlignment),
					Component.translatable("equipment.oldrepublic.bonus.sith_robes.kill", number(b.sithSetKillFe), number(b.sithSetWindowTicks / 20f)),
					Component.translatable("equipment.oldrepublic.bonus.sith_robes.cost", percent(b.sithSetLightningCost / 100f))};
			case WATCH_BATTLE -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.gate.light", b.setAlignment),
					Component.translatable("equipment.oldrepublic.bonus.watch_battle.parry", number(m.watchSetTicks / 20f)),
					Component.translatable("equipment.oldrepublic.bonus.watch_battle.melee", percent(m.watchSetParryResistPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.watch_battle.barrier", percent(m.watchSetBarrierCostPct / 100f))};
			case SITH_WAR -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.gate.dark", b.setAlignment),
					Component.translatable("equipment.oldrepublic.bonus.sith_war.health", number(m.sithWarSetHealthFraction * 100f)),
					Component.translatable("equipment.oldrepublic.bonus.sith_war.cost", percent(m.sithWarSetCostPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.sith_war.damage", percent(m.sithWarSetDamagePct / 100f))};
			case MANDALORIAN -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.mandalorian.control", percent(m.mandoSetControlPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.mandalorian.jet", signed(m.mandoSetJetCharges)),
					Component.translatable("equipment.oldrepublic.bonus.mandalorian.speed", percent(m.mandoSetSpeedCostPct / 100f))};
			case SPACER -> new Component[] {
					Component.translatable("equipment.oldrepublic.bonus.spacer.speed", percent(m.spacerSetSpeedDurationPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.spacer.fall", percent(m.spacerSetFallPct / 100f)),
					Component.translatable("equipment.oldrepublic.bonus.spacer.terrain")};
			default -> new Component[0];
		};
	}

	/**
	 * One trait line, skipped when zero: {@code fraction} is the signed change printed ("damage taken"
	 * negative is resistance), scaled by {@code quality} when it helps the wearer; {@code lowerIsBetter}
	 * picks the colour.
	 */
	private static void trait(Consumer<Component> tooltip, String key, float fraction, float quality, boolean lowerIsBetter) {
		if (fraction == 0f) {
			return;
		}

		float shown = SetBonusRules.pieceRatio(fraction * 100f, quality, lowerIsBetter) - 1f;
		boolean good = lowerIsBetter ? shown < 0f : shown > 0f;
		tooltip.accept(Component.translatable("equipment.oldrepublic.trait." + key, percent(shown))
				.withStyle(good ? ChatFormatting.BLUE : ChatFormatting.RED));
	}

	/** A signed percentage of a fraction, without trailing zeros: 0.05 is "+5%", -0.015 is "-1.5%". */
	public static String percent(float fraction) {
		return signed(Math.round(fraction * 1000f) / 10f) + "%";
	}

	/** A signed number without trailing zeros: 5 is "+5", -1.5 is "-1.5". */
	public static String signed(float value) {
		return (value > 0f ? "+" : "") + number(value);
	}

	/** A number without trailing zeros. */
	public static String number(float value) {
		return value == Math.rint(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}
}

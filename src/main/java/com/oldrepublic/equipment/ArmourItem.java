package com.oldrepublic.equipment;

import java.util.Locale;
import java.util.function.Consumer;

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
 * "Set n/4" line is added on the client by {@code ArmourTooltips}.
 */
public class ArmourItem extends Item {
	/** Defaults used for the set bonus numbers in the tooltip. */
	private static final EquipmentBalance SHOWN = new EquipmentBalance();
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

		trait(tooltip, "cost", -traits.costPct(), traits.costPct());
		trait(tooltip, "regen", traits.regenPct(), traits.regenPct());
		trait(tooltip, "blaster", traits.blasterResist(), -traits.blasterResist());
		trait(tooltip, "melee", traits.meleeResist(), -traits.meleeResist());

		if (traits.maxEnergy() != 0) {
			tooltip.accept(Component.translatable("equipment.oldrepublic.trait.max_energy", signed(traits.maxEnergy()))
					.withStyle(traits.maxEnergy() > 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
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
		};
	}

	/**
	 * One trait line, skipped when zero. {@code good} decides the colour (positive is good for the
	 * wearer); {@code shown} is the signed fraction printed.
	 */
	private static void trait(Consumer<Component> tooltip, String key, float good, float shown) {
		if (shown == 0f) {
			return;
		}

		tooltip.accept(Component.translatable("equipment.oldrepublic.trait." + key, percent(shown))
				.withStyle(good > 0f ? ChatFormatting.BLUE : ChatFormatting.RED));
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

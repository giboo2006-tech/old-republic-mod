package com.oldrepublic.client.equipment;

import java.util.List;

import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentBalance;
import com.oldrepublic.equipment.EquipmentTraits;
import com.oldrepublic.equipment.SetBonusRules;
import com.oldrepublic.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The live set line of an era armour tooltip (spec 9.1), added through Fabric's
 * {@code ItemTooltipCallback} because {@code appendHoverText} does not see the viewer: how many
 * pieces of the set the local player wears and whether the bonus is on, for example
 * "Set 4/4 · requires Light (+15)". It goes right under the item name.
 *
 * <p>The client cannot read the server's balance file, so the alignment threshold shown is the
 * default ({@code setAlignment}); the server decides with the live value.
 */
final class ArmourTooltips {
	private static final EquipmentBalance SHOWN = new EquipmentBalance();

	static void append(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		EquipmentTraits traits = stack.get(ModComponents.EQUIPMENT_TRAITS);
		LocalPlayer player = Minecraft.getInstance().player;

		if (traits == null || player == null || lines.isEmpty()) {
			return;
		}

		lines.add(1, line(traits.set(), ArmourSets.worn(player), ArmourSets.wearer(player)));
	}

	/** The set line for {@code set} given what is worn and who wears it. */
	static Component line(ArmourSet set, SetBonusRules.Worn worn, SetBonusRules.Wearer wearer) {
		int count = worn.count(set);

		if (SetBonusRules.active(set, worn, wearer, SHOWN)) {
			return Component.translatable("equipment.oldrepublic.tooltip.active", count).withStyle(ChatFormatting.GREEN);
		}

		if ((set == ArmourSet.JEDI_ROBES || set == ArmourSet.WATCH_BATTLE) && !SetBonusRules.jediGate(wearer, SHOWN)) {
			return Component.translatable("equipment.oldrepublic.tooltip.requires.light", count, SHOWN.setAlignment).withStyle(ChatFormatting.GOLD);
		}

		if ((set == ArmourSet.SITH_ROBES || set == ArmourSet.SITH_WAR) && !SetBonusRules.sithGate(wearer, SHOWN)) {
			return Component.translatable("equipment.oldrepublic.tooltip.requires.dark", count, SHOWN.setAlignment).withStyle(ChatFormatting.GOLD);
		}

		return Component.translatable("equipment.oldrepublic.tooltip.set", count).withStyle(ChatFormatting.GRAY);
	}

	private ArmourTooltips() {
	}
}

package com.oldrepublic.equipment;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Cantor's Mask (MILESTONE_5.md 12.3): the First Cantor's first clear gives it, worn on the head
 * with no armour and no other effect. Its tooltip says where it came from and that it is cosmetic;
 * the robe renderer draws it on the face ({@code client/equipment/RobeArmorRenderer}).
 */
public class CantorsMaskItem extends Item {
	public CantorsMaskItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.oldrepublic.cantors_mask.desc").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		tooltip.accept(Component.translatable("equipment.oldrepublic.cosmetic").withStyle(ChatFormatting.GRAY));
	}
}

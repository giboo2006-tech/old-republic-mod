package com.oldrepublic.vendor;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A Chapter Four story item with one line of lore ({@code item.oldrepublic.<id>.desc}): the armour
 * pattern, the tomb relic and the sealed contract. Holding it does nothing by itself; the story and
 * the trainers look for it.
 */
public class KeepsakeItem extends Item {
	public KeepsakeItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.oldrepublic." + BuiltInRegistries.ITEM.getKey(this).getPath() + ".desc").withStyle(ChatFormatting.GRAY));
	}
}

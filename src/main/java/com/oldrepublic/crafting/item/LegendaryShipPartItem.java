package com.oldrepublic.crafting.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A legendary ship part (spec MILESTONE_5.md 11.2): the Spindle Capacitor or the Hymn-Tuned Drive.
 * The item does nothing in the hand; a shipwright fits it to the player's ship and uses it up (W1's
 * {@code ship:fit}), and the owners of what it changes code its effect (W2 the capacitor's shield,
 * W1 the drive's jumps). Its tooltip says what it does ({@code item.oldrepublic.<id>.desc}) and who
 * fits it.
 */
public class LegendaryShipPartItem extends Item {
	private final String descKey;

	public LegendaryShipPartItem(Properties properties, String id) {
		super(properties);
		this.descKey = "item.oldrepublic." + id + ".desc";
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.oldrepublic.legendary_ship_part.fit").withStyle(ChatFormatting.DARK_GRAY));
	}
}

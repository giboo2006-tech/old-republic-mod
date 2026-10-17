package com.oldrepublic.saber;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An emitter, grip or pommel. Its tooltip names the stat change it brings to a saber; the
 * Collector's two parts (spec 8.8, 8.9) add a line of lore ({@code saber.oldrepublic.part.<item id>.lore}).
 */
public class HiltPartItem extends Item {
	private final HiltParts.Part part;

	public HiltPartItem(Properties properties, HiltParts.Part part) {
		super(properties);
		this.part = part;
	}

	public HiltParts.Part part() {
		return part;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.oldrepublic." + part.itemId() + ".desc").withStyle(ChatFormatting.GRAY));

		if (hasLore(part)) {
			tooltip.accept(Component.translatable("saber.oldrepublic.part." + part.itemId() + ".lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The parts with a lore line: the archivist pommel and the Reliquary emitter. */
	static boolean hasLore(HiltParts.Part part) {
		return part == HiltParts.Pommel.ARCHIVIST || part == HiltParts.Emitter.RELIQUARY;
	}
}

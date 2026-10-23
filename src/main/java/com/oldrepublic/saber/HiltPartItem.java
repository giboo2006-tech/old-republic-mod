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
 * Collector's two parts (spec 8.8, 8.9) and the legendary parts (MILESTONE_5.md 11.2) add a line of
 * lore ({@code saber.oldrepublic.part.<item id>.lore}). Milestone 7 (MILESTONE_7.md 3.4): a part made of
 * a material carries it in its name ("Starfall Vanguard Emitter"; steel keeps the plain name), the
 * material's line (name, tier, what it adds, its trait) follows the stat line, and a boss set's part
 * names its set and both bonuses (7.2).
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

	/** The part's name with its material in front (3.4); a legendary part keeps its gold name after it. */
	@Override
	public Component getName(ItemStack stack) {
		return HiltTraits.partName(SaberComponents.material(stack), super.getName(stack));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.oldrepublic." + part.itemId() + ".desc").withStyle(ChatFormatting.GRAY));
		HiltTraits.materialLines(HiltParts.slot(part), SaberComponents.material(stack), tooltip);
		HiltSetEffects.partLines(part, tooltip);

		if (hasLore(part)) {
			tooltip.accept(Component.translatable("saber.oldrepublic.part." + part.itemId() + ".lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The parts with a lore line: the archivist pommel, the Reliquary emitter and the legendary parts (MILESTONE_5.md 11.2). */
	static boolean hasLore(HiltParts.Part part) {
		return part == HiltParts.Pommel.ARCHIVIST || part == HiltParts.Emitter.RELIQUARY || SaberItems.isLegendary(part);
	}
}

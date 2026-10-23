package com.oldrepublic.saber;

import java.util.Locale;
import java.util.function.Consumer;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.saber.SaberLens.Lens;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A saber lens, {@code lens_honed}, {@code lens_steady} or {@code lens_resonant} (spec
 * MILESTONE_4.md 5.7), or the legendary {@code lens_stormglass} (MILESTONE_5.md 11.2): made by
 * Artifice with a quality, fitted in the Lightsaber Workbench's lens slot. The tooltip shows its effect at its quality, from the default crafting balance (a client
 * reads no balance file).
 */
public class LensItem extends Item {
	private static final CraftingBalance DEFAULTS = new CraftingBalance();

	private final Lens lens;

	public LensItem(Properties properties, Lens lens) {
		super(properties);
		this.lens = lens;
	}

	public Lens lens() {
		return lens;
	}

	/**
	 * The effect line, e.g. "+9% saber damage while ignited", for {@code lens} at {@code quality}; the
	 * stormglass lens names its finisher shock in seconds and its bonus against droids (MILESTONE_5.md 11.2).
	 */
	public static Component effectLine(Lens lens, Quality quality) {
		String shown = number(lens.value(quality, DEFAULTS));

		if (lens == Lens.STORMGLASS) {
			return Component.translatable("item.oldrepublic.lens.stormglass.effect", number(DEFAULTS.stormglassShockTicks / 20f), shown);
		}

		return Component.translatable("item.oldrepublic.lens." + lens.id() + ".effect", shown);
	}

	/** A whole number as it is, anything else with one decimal. */
	private static String number(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}

	/** The line on a saber's tooltip naming its fitted lens and effect. */
	public static Component fittedLine(Lens lens, Quality quality) {
		return Component.translatable("item.oldrepublic.lightsaber.lens", Component.translatable("item.oldrepublic.lens_" + lens.id()), effectLine(lens, quality))
				.withStyle(ChatFormatting.DARK_AQUA);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(effectLine(lens, Crafting.quality(stack)).copy().withStyle(ChatFormatting.DARK_AQUA));
		tooltip.accept(Component.translatable("item.oldrepublic.lens.fit").withStyle(ChatFormatting.GRAY));
	}
}

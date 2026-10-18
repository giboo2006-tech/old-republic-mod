package com.oldrepublic.saber;

import java.util.function.Consumer;

import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The saberstaff, {@code lightsaber_staff} (spec MILESTONE_4.md 5.6): two blades, two crystals
 * ({@code SaberConfig.second}), one emitter fitted at both ends and the staff grip. The config's
 * pommel is a placeholder the staff never renders, never returns and never counts
 * ({@link SaberStats#staff}). Built at the Lightsaber Workbench ({@link SaberAssembly}); its rules
 * in a fight are {@link SaberStaffs}'s and {@link SaberTechniques}'s whirl.
 */
public class SaberStaffItem extends LightsaberItem {
	public SaberStaffItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		SaberConfig config = SaberComponents.config(stack);

		if (!config.ownerName().isEmpty()) {
			tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.owner", config.ownerName()).withStyle(ChatFormatting.AQUA));
		}

		tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.color", config.color().displayName())
				.withStyle(style -> style.withColor(config.color().rgb())));
		tooltip.accept(Component.translatable("item.oldrepublic.lightsaber_staff.parts",
				Component.translatable("item.oldrepublic." + config.emitter().itemId()),
				Component.translatable("item.oldrepublic." + config.grip().itemId())).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable(SaberComponents.staffMode(stack).single()
				? "item.oldrepublic.lightsaber_staff.single" : "item.oldrepublic.lightsaber_staff.both").withStyle(ChatFormatting.GRAY));

		SaberLens.Lens lens = SaberLens.lens(stack);

		if (lens != null) {
			tooltip.accept(LensItem.fittedLine(lens, SaberLens.quality(stack)));
		}

		if (SaberComponents.isSinging(stack)) {
			tooltip.accept(Component.translatable("saber.oldrepublic.lightsaber.singing").withStyle(ChatFormatting.GOLD));
		}

		if (SaberComponents.isThrown(stack)) {
			tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.thrown").withStyle(ChatFormatting.GOLD));
		}

		tooltip.accept(Component.translatable(SaberComponents.state(stack).ignited() ? "item.oldrepublic.lightsaber.ignited" : "item.oldrepublic.lightsaber.retracted")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}

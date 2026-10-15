package com.oldrepublic.saber;

import java.util.function.Consumer;

import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A kyber crystal. Attuned crystals carry {@link ModComponents#CRYSTAL_ATTUNEMENT}; only their
 * owner can build with them at the workbench.
 */
public class KyberCrystalItem extends Item {
	public KyberCrystalItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);

		if (attunement == null) {
			tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.unattuned").withStyle(ChatFormatting.GRAY));
			return;
		}

		tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.owner", attunement.ownerName()).withStyle(ChatFormatting.AQUA));
		tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.hue", BladeColor.nearest(attunement.color()).displayName())
				.withStyle(style -> style.withColor(attunement.color() & 0xFFFFFF)));
		tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.bound").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}

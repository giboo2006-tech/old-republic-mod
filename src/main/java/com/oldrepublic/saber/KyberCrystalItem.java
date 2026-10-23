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
 *
 * <p>The singing crystal (spec 8.8) is a plain item of the lead's registry, so its tooltip comes
 * from the saber client through {@link #crystalLines}, the same lines with the singing ones added:
 * it sings, and, before it attunes to its builder, it answers no one yet
 * ({@link SaberAssembly#isSingingCrystal}).
 */
public class KyberCrystalItem extends Item {
	public KyberCrystalItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		crystalLines(stack, tooltip);
	}

	/** The tooltip lines of a kyber or singing crystal: its owner and hue once attuned, else a hint. */
	public static void crystalLines(ItemStack stack, Consumer<Component> tooltip) {
		CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);
		boolean singing = SaberAssembly.isSingingCrystal(stack);

		if (attunement == null) {
			tooltip.accept(Component.translatable(singing ? "saber.oldrepublic.singing_crystal.unattuned" : "item.oldrepublic.kyber_crystal.unattuned")
					.withStyle(ChatFormatting.GRAY));
			return;
		}

		tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.owner", attunement.ownerName()).withStyle(ChatFormatting.AQUA));
		tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.hue", BladeColor.nearest(attunement.color()).displayName())
				.withStyle(style -> style.withColor(attunement.color() & 0xFFFFFF)));

		if (singing) {
			tooltip.accept(Component.translatable("saber.oldrepublic.singing_crystal.sings").withStyle(ChatFormatting.GOLD));
		}

		tuningLine(attunement, tooltip);
		tooltip.accept(Component.translatable("item.oldrepublic.kyber_crystal.bound").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** The crystal's tuning (MILESTONE_5.md 10.4), if it has one: "Tuning: Keen". */
	public static void tuningLine(CrystalAttunement attunement, Consumer<Component> tooltip) {
		CrystalTuning tuning = CrystalTuning.of(attunement);

		if (tuning != null) {
			tooltip.accept(Component.translatable("saber.oldrepublic.tuning.line", tuning.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}
}

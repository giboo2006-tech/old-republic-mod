package com.oldrepublic.crafting.item;

import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * The Medtech healing items (spec MILESTONE_4.md 7.6): the {@code medpac} (a 16-tick use, then 8
 * health over 40 ticks; a hit of 4 or more while using interrupts it) and the {@code kolto_injector}
 * (a 10-tick use, 6 health at once, poison and wither removed). Both share the healing cooldown.
 */
public class HealItem extends Item {
	private final boolean kolto;

	public HealItem(Properties properties, boolean kolto) {
		super(properties);
		this.kolto = kolto;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		CraftingBalance b = CraftingBalance.get();
		return Math.max(1, kolto ? b.koltoUseTicks : b.medpacUseTicks);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer p)) {
			return stack;
		}

		CraftingBalance b = CraftingBalance.get();

		if (kolto) {
			p.heal(b.koltoHeal);
			p.removeEffect(MobEffects.POISON);
			p.removeEffect(MobEffects.WITHER);
			Consumables.healCooldowns(p, stack, b.koltoCooldown);
		} else {
			Consumables.startHeal(p, b.medpacHeal, b.medpacTicks);
			Consumables.healCooldowns(p, stack, b.healCooldown);
		}

		EffectEvents.toTracking(p, CraftingEffects.CONSUMABLE, p.position().add(0, 1.2, 0), p.getLookAngle(), p.getId(), 1f,
				kolto ? CraftingEffects.KOLTO : CraftingEffects.MEDPAC);
		stack.consume(1, p);
		return stack;
	}
}

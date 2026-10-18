package com.oldrepublic.crafting.item;

import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.crafting.item.Consumables.Stim;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A stim (spec MILESTONE_4.md 7.6): {@code combat_stim} (+8% melee and blaster damage) or
 * {@code focus_stim} (+20% Force regen) for 1,200 ticks, then a 200-tick crash. One at a time:
 * injecting while another runs or crashes is refused.
 */
public class StimItem extends Item {
	private final Stim stim;

	public StimItem(Properties properties, Stim stim) {
		super(properties);
		this.stim = stim;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!(player instanceof ServerPlayer p)) {
			return InteractionResult.SUCCESS;
		}

		if (!Consumables.inject(p, stim)) {
			p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.stim.one_at_a_time").withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.stim." + (stim == Stim.COMBAT ? "combat" : "focus")).withStyle(ChatFormatting.AQUA));
		EffectEvents.toTracking(p, CraftingEffects.CONSUMABLE, p.position().add(0, 1.2, 0), p.getLookAngle(), p.getId(), 1f, CraftingEffects.STIM);
		stack.consume(1, p);
		return InteractionResult.SUCCESS;
	}
}

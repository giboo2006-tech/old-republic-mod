package com.oldrepublic.saber;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.throwing.SaberThrow;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The lightsaber. Sneak + use ignites or retracts it (as does the Toggle Lightsaber key); holding
 * use with an ignited blade guards. Stats come from {@link SaberComponents#SABER_CONFIG} through
 * attribute modifiers rewritten on every toggle.
 */
public class LightsaberItem extends Item {
	private static final int GUARD_DURATION = 72000;

	public LightsaberItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (SaberComponents.isThrown(stack)) {
			// In flight: the hand is empty until it comes back.
			return InteractionResult.FAIL;
		}

		if (player.isShiftKeyDown()) {
			if (player instanceof ServerPlayer serverPlayer) {
				SaberToggle.toggle(serverPlayer, stack);
			}

			// No arm swing: igniting is not an attack (and must not play a swing sound).
			return InteractionResult.CONSUME;
		}

		if (!SaberComponents.isIgnited(stack) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer serverPlayer && (SaberTechniques.isBusy(serverPlayer) || SaberForms.isSwitching(serverPlayer))) {
			return InteractionResult.FAIL;
		}

		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BLOCK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return GUARD_DURATION;
	}

	/**
	 * Retracts a blade that is no longer in a hand (moved to the inventory, a chest...). A thrown
	 * saber is left alone (it comes back lit), and its {@code saber_thrown} component is cleared
	 * once no throw with its id exists any more.
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (SaberComponents.isThrown(stack)) {
			SaberThrow.checkOrphan(stack, level);
			return;
		}

		if (!SaberComponents.isIgnited(stack)) {
			return;
		}

		boolean held = entity instanceof LivingEntity living && (living.getMainHandItem() == stack || living.getOffhandItem() == stack);

		if (!held) {
			SaberToggle.setIgnited(stack, false, level.getGameTime());
			SaberToggle.playToggleSound(level, entity, false);
		}
	}

	/** Ignite/retract only changes components: never replay the re-equip animation for it. */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		SaberConfig config = SaberComponents.config(stack);

		if (!config.ownerName().isEmpty()) {
			tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.owner", config.ownerName()).withStyle(ChatFormatting.AQUA));
		}

		tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.color", config.color().displayName())
				.withStyle(style -> style.withColor(config.color().rgb())));
		tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.parts",
				Component.translatable("item.oldrepublic." + config.emitter().itemId()),
				Component.translatable("item.oldrepublic." + config.grip().itemId()),
				Component.translatable("item.oldrepublic." + config.pommel().itemId())).withStyle(ChatFormatting.GRAY));
		if (SaberComponents.isThrown(stack)) {
			tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.thrown").withStyle(ChatFormatting.GOLD));
		}

		tooltip.accept(Component.translatable(SaberComponents.state(stack).ignited() ? "item.oldrepublic.lightsaber.ignited" : "item.oldrepublic.lightsaber.retracted")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}

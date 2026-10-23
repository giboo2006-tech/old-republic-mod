package com.oldrepublic.crafting.item;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The repair kit (spec MILESTONE_4.md 7.6, a common schematic): restores {@code repairKitPct}% of the
 * item in the other hand, or else of the most worn armour piece. Three uses (its durability).
 */
public class RepairKitItem extends Item {
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public RepairKitItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack kit = player.getItemInHand(hand);
		ItemStack target = target(player, hand);

		if (target == null) {
			if (player instanceof ServerPlayer p) {
				p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.repair_kit.nothing").withStyle(ChatFormatting.GRAY));
			}

			return InteractionResult.FAIL;
		}

		if (player instanceof ServerPlayer p) {
			int restore = Math.max(1, Math.round(target.getMaxDamage() * CraftingBalance.get().repairKitPct / 100f));
			target.setDamageValue(Math.max(0, target.getDamageValue() - restore));
			p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.BENCH_MOD_FIT, SoundSource.PLAYERS, 0.8f, 1.3f);
			kit.hurtAndBreak(1, p, hand);
		}

		return InteractionResult.SUCCESS;
	}

	/** The damaged item in the other hand, else the most worn armour piece, else null. */
	private static @Nullable ItemStack target(Player player, InteractionHand hand) {
		ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);

		if (other.isDamaged() && !(other.getItem() instanceof RepairKitItem)) {
			return other;
		}

		ItemStack worst = null;
		float worstWear = 0f;

		for (EquipmentSlot slot : ARMOUR) {
			ItemStack piece = player.getItemBySlot(slot);

			if (piece.isDamaged()) {
				float wear = piece.getDamageValue() / (float) Math.max(1, piece.getMaxDamage());

				if (wear > worstWear) {
					worst = piece;
					worstWear = wear;
				}
			}
		}

		return worst;
	}
}

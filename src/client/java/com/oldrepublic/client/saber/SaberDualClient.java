package com.oldrepublic.client.saber;

import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberDual;
import com.oldrepublic.saber.network.OffhandStrikePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The client side of dual-wielding (spec MILESTONE_5.md 10.2): when the attack button is pressed with
 * a lit saber in each hand, the main hand still recovering and the off hand ready, the off hand
 * strikes instead. It swings at once (the swing fields are set directly, so it follows the main
 * hand's swing without waiting half a swing), picks its target with the off saber's own reach, and
 * sends {@code offhand_strike}. A swing at a block is left to vanilla (mining); a swing at the air
 * still swings and starts the off hand's clock. The off hand's clock here mirrors the server's
 * ({@link SaberDual#offhandDelay}) with the default balance; the server decides.
 */
final class SaberDualClient {
	private static final SaberBalance DEFAULT_BALANCE = new SaberBalance();
	/** Game time at which the own off hand is ready again. */
	private static long readyAt = Long.MIN_VALUE;

	/** Returns true when the off hand took this attack (the vanilla attack is then swallowed). */
	static boolean tryStrike(Minecraft mc, LocalPlayer player) {
		if (!SaberDual.isDualWielding(player) || player.isUsingItem() || player.getAttackStrengthScale(0.5f) >= 1f) {
			return false;
		}

		long now = player.level().getGameTime();

		if (now < readyAt) {
			return false;
		}

		HitResult crosshair = mc.hitResult;

		if (crosshair != null && crosshair.getType() == HitResult.Type.BLOCK) {
			return false;
		}

		ItemStack off = player.getOffhandItem();
		AttackRange range = off.get(DataComponents.ATTACK_RANGE);
		HitResult hit = range != null ? range.getClosesetHit(player, 1f, EntitySelector.CAN_BE_PICKED) : crosshair;
		int targetId = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && target.isAlive() ? target.getId() : -1;

		player.swingTime = -1;
		player.swinging = true;
		player.swingingArm = InteractionHand.OFF_HAND;
		readyAt = now + SaberDual.offhandDelay(player, DEFAULT_BALANCE);
		ClientPlayNetworking.send(new OffhandStrikePayload(targetId));
		return true;
	}

	static void clear() {
		readyAt = Long.MIN_VALUE;
	}

	private SaberDualClient() {
	}
}

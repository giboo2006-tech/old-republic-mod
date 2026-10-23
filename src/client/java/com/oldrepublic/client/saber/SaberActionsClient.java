package com.oldrepublic.client.saber;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.ability.ClientAbilityHook;
import com.oldrepublic.client.ability.ClientAbilityHooks;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberPassives;
import com.oldrepublic.saber.network.SaberActionPayload;
import com.oldrepublic.saber.throwing.SaberThrow;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The client side of the saber techniques: Sneak + Attack sends a heavy attack, Sprint + Attack at
 * a target {@code lungeMinRange} to the form's lunge range away (within {@code lungeAimAssist}
 * degrees of the crosshair) sends a lunge, and the Saber Form key ({@link ForceKeys#SABER_FORM},
 * unbound by default) cycles the owned forms. While the saber is in flight the attack key does
 * nothing. With a lit saber in each hand, an attack while the main hand recovers goes to the off
 * hand ({@link SaberDualClient}, Milestone 5). The server decides every technique; this only picks
 * the intent.
 */
final class SaberActionsClient {
	/**
	 * Default balance for the client's target pick (the server's tuned numbers are not synced; the
	 * server re-checks the range and line of sight).
	 */
	private static final SaberBalance DEFAULT_BALANCE = new SaberBalance();

	static void init() {
		ClientPreAttackCallback.EVENT.register(SaberActionsClient::onPreAttack);
		ClientAbilityHooks.register(SaberThrow.ID, new ClientAbilityHook() {
			@Override
			public boolean precheck(Minecraft mc) {
				if (mc.player == null || SaberComponents.isUsable(mc.player.getMainHandItem())) {
					return true;
				}

				mc.player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.throw_no_saber"));
				return false;
			}
		});
	}

	/** Polls the Saber Form key. */
	static void tick(Minecraft mc) {
		while (ForceKeys.SABER_FORM.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null) {
				ClientPlayNetworking.send(new SaberActionPayload(SaberActionPayload.Action.FORM_CYCLE, -1));
			}
		}
	}

	/** Returns true to swallow the vanilla attack. */
	private static boolean onPreAttack(Minecraft mc, LocalPlayer player, int clickCount) {
		ItemStack main = player.getMainHandItem();

		if (SaberComponents.isThrown(main)) {
			return true;
		}

		if (clickCount <= 0 || mc.gui.screen() != null || player.isSpectator() || !SaberComponents.isUsable(main)) {
			return false;
		}

		if (player.isShiftKeyDown()) {
			ClientPlayNetworking.send(new SaberActionPayload(SaberActionPayload.Action.HEAVY, -1));
			return true;
		}

		if (player.isSprinting()) {
			SaberForm.Modifiers form = SaberPassives.form(player).modifiers(DEFAULT_BALANCE);

			if (form.lungeAllowed() && SaberPassives.has(player, SaberPassives.LUNGE)) {
				LivingEntity target = lungeTarget(player, form.lungeRange());

				if (target != null) {
					ClientPlayNetworking.send(new SaberActionPayload(SaberActionPayload.Action.LUNGE, target.getId()));
					return true;
				}
			}
		}

		// Two lit blades: the off hand strikes while the main hand recovers (MILESTONE_5.md 10.2).
		return SaberDualClient.tryStrike(mc, player);
	}

	/** The living entity closest to the crosshair within the lunge band and the aim assist cone, or null. */
	private static @Nullable LivingEntity lungeTarget(LocalPlayer player, float maxRange) {
		double minRange = DEFAULT_BALANCE.lungeMinRange;
		double cos = Math.cos(Math.toRadians(DEFAULT_BALANCE.lungeAimAssist));
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = cos;

		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(maxRange),
				e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable())) {
			double distance = player.distanceTo(candidate);

			if (distance < minRange || distance > maxRange) {
				continue;
			}

			Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye).normalize();
			double dot = to.dot(look);

			if (dot >= bestDot && player.hasLineOfSight(candidate)) {
				best = candidate;
				bestDot = dot;
			}
		}

		return best;
	}

	private SaberActionsClient() {
	}
}

package com.oldrepublic.client.force;

import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.network.PushInputPayload;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Turns the {@code Use Force Power} key into START / RELEASE / CANCEL intents and predicts the
 * local charge visuals. A release while a screen is open (vanilla releases every key when a screen
 * opens) is always a cancel, never a cast.
 */
final class PushInput {
	/** Ticks to keep the drawn pose while waiting for the server's answer to a release. */
	private static final int RELEASE_WAIT_TICKS = 10;

	private static boolean wasDown;
	private static boolean charging;
	private static long pressedAt;

	static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null) {
			reset();
			return;
		}

		while (ForceKeys.FORCE_SCREEN.consumeClick()) {
			ForceScreen.open(mc, player);
		}

		boolean screenOpen = mc.gui.screen() != null;
		boolean down = ForceKeys.USE_POWER.isDown() && !screenOpen;

		if (charging && (screenOpen || !player.isAlive() || player.isSpectator() || player.isUsingItem())) {
			cancel(player);
		} else if (down && !wasDown && !charging) {
			press(player);
		} else if (!down && wasDown && charging) {
			release(player);
		}

		wasDown = down;
	}

	private static void press(LocalPlayer player) {
		ForceData data = player.getAttached(ForceAttachments.FORCE);

		if (data == null || !data.isAwakened()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"));
			return;
		}

		if (!data.hasAbility(ForcePush.ABILITY_ID)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.not_learned"));
			return;
		}

		if (data.cooldown(ForcePush.ABILITY_ID) > 0) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.cooldown"));
			return;
		}

		ClientPlayNetworking.send(new PushInputPayload(PushInputPayload.Action.START));
		charging = true;
		pressedAt = PushAnimations.ticks();

		// Without enough energy the server answers with the fail feedback; do not fake a charge.
		if (player.isCreative() || data.energy >= ClientPushInfo.minimumCost(data)) {
			PushAnimations.startCharge(player.getId());
		}
	}

	private static void release(LocalPlayer player) {
		ClientPlayNetworking.send(new PushInputPayload(PushInputPayload.Action.RELEASE));
		charging = false;

		if (PushAnimations.ticks() - pressedAt < ClientPushInfo.minChargeTicks()) {
			PushAnimations.stopCharge(player.getId());
		} else {
			PushAnimations.awaitRelease(player.getId(), RELEASE_WAIT_TICKS);
		}
	}

	private static void cancel(LocalPlayer player) {
		ClientPlayNetworking.send(new PushInputPayload(PushInputPayload.Action.CANCEL));
		charging = false;
		PushAnimations.stopCharge(player.getId());
	}

	/** The server ended our charge (refused, cancelled or timed out): stop expecting a release. */
	static void onServerStop() {
		charging = false;
	}

	static void reset() {
		wasDown = false;
		charging = false;
	}

	private PushInput() {
	}
}

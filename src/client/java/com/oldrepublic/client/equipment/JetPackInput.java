package com.oldrepublic.client.equipment;

import com.oldrepublic.equipment.jet.JetBurstPayload;
import com.oldrepublic.equipment.jet.JetPack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The jet pack's key (spec MILESTONE_4.md 6.4 and 11.1): pressing jump while airborne, with a jet
 * pack worn, asks the server for a burst ({@link JetBurstPayload}). Only the press counts, never a
 * held key, so one press is at most one burst. The client only filters the obvious cases (on the
 * ground, swimming, riding, flying, gliding, a screen open); the server checks everything again,
 * including a Force Jump leap in progress (whose glide keeps the key) and being held.
 */
final class JetPackInput {
	private static boolean wasDown;

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(JetPackInput::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		boolean down = player != null && mc.gui.screen() == null && mc.options.keyJump.isDown();
		boolean pressed = down && !wasDown;
		wasDown = down;

		if (player == null || !pressed || player.getAttached(JetPack.JET) == null || player.onGround() || player.isInWater() || player.isPassenger()
				|| player.getAbilities().flying || player.isFallFlying() || player.isSpectator()) {
			return;
		}

		ClientPlayNetworking.send(JetBurstPayload.INSTANCE);
	}

	private JetPackInput() {
	}
}

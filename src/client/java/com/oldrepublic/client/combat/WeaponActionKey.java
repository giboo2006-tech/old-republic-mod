package com.oldrepublic.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.combat.WeaponActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The Weapon Action key (default N, spec MILESTONE_4.md 11.1). Sends a press when it goes down and
 * a release when it comes up (or a screen opens), with something in the main hand; the server's
 * {@code WeaponActions} decides what the held item does.
 */
final class WeaponActionKey {
	static final KeyMapping KEY = new KeyMapping("key.oldrepublic.weapon_action", InputConstants.Type.KEYSYM, InputConstants.KEY_N, ForceKeys.CATEGORY);

	private static boolean down;

	static void init() {
		KeyMappingHelper.registerKeyMapping(KEY);
		ClientTickEvents.END_CLIENT_TICK.register(WeaponActionKey::tick);
	}

	private static void tick(Minecraft mc) {
		boolean held = KEY.isDown() && mc.player != null && mc.gui.screen() == null && !mc.player.getMainHandItem().isEmpty();

		while (KEY.consumeClick()) {
			// Presses are read from isDown(); the queued clicks are only drained.
		}

		if (held != down) {
			down = held;

			if (mc.player != null && ClientPlayNetworking.canSend(WeaponActionPayload.TYPE)) {
				ClientPlayNetworking.send(new WeaponActionPayload(held));
			}
		}
	}

	private WeaponActionKey() {
	}
}

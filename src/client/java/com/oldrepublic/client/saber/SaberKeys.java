package com.oldrepublic.client.saber;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.network.ToggleSaberPayload;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** The Toggle Lightsaber key (default G), in the mod's shared key category. */
final class SaberKeys {
	static final KeyMapping TOGGLE = new KeyMapping("key.oldrepublic.toggle_saber", InputConstants.Type.KEYSYM, InputConstants.KEY_G, ForceKeys.CATEGORY);

	static void init() {
		KeyMappingHelper.registerKeyMapping(TOGGLE);
	}

	/** Sends one toggle intent per press while a saber is in hand; the server decides. */
	static void tick(Minecraft mc) {
		while (TOGGLE.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null
					&& (mc.player.getMainHandItem().getItem() instanceof LightsaberItem || mc.player.getOffhandItem().getItem() instanceof LightsaberItem)) {
				ClientPlayNetworking.send(ToggleSaberPayload.INSTANCE);
			}
		}
	}

	private SaberKeys() {
	}
}

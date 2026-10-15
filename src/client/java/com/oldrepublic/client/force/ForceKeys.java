package com.oldrepublic.client.force;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Key mappings of the mod. The force feature owns the shared {@link #CATEGORY}; other client
 * features use it for their own keys (their init runs after {@link ForceClient#init()}).
 */
public final class ForceKeys {
	/** The mod's key category, shown as "Echoes of the Old Republic" in the controls screen. */
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(OldRepublicMod.id("force"));

	/** Hold to charge Force Push, release to fire. */
	public static final KeyMapping USE_POWER = new KeyMapping("key.oldrepublic.use_force_power", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
	/** Opens the Force screen. */
	public static final KeyMapping FORCE_SCREEN = new KeyMapping("key.oldrepublic.force_screen", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);

	static void init() {
		KeyMappingHelper.registerKeyMapping(USE_POWER);
		KeyMappingHelper.registerKeyMapping(FORCE_SCREEN);
	}

	private ForceKeys() {
	}
}

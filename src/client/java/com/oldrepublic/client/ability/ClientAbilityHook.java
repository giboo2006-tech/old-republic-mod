package com.oldrepublic.client.ability;

import net.minecraft.client.Minecraft;

/**
 * Client-side companion of an ability: local checks and prediction (animation, charge sound,
 * crosshair brackets) around the slot key. The server stays authoritative; a hook never applies
 * gameplay.
 */
public interface ClientAbilityHook {
	/** Return false to swallow the key press locally (for example no saber in hand). */
	default boolean precheck(Minecraft mc) {
		return true;
	}

	default void onPress(Minecraft mc) {
	}

	default void onRelease(Minecraft mc) {
	}

	default void onCancel(Minecraft mc) {
	}
}

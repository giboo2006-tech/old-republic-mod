package com.oldrepublic.ability;

import java.util.Map;

import net.minecraft.server.level.ServerPlayer;

/**
 * An active power that can sit in an ability slot. Server side; the ability runtime routes slot
 * input to {@link #onInput} and cancels the ability on death, disconnect, dimension change, a screen
 * opening or the slot changing.
 */
public interface ForceAbility {
	/** Ability id, also its skill node id, cooldown key and mastery key (for example {@code force_pull}). */
	String id();

	InputStyle inputStyle();

	void onInput(ServerPlayer p, AbilityAction action);

	/** Stops the ability if it is active. {@code feedback} plays the cancel effect. */
	void cancel(ServerPlayer p, boolean feedback);

	/** True while charging or channelling. */
	boolean isActive(ServerPlayer p);

	/** Live balance values shown in tooltips, keyed by the node's {@code info} keys. */
	Map<String, Float> info();
}

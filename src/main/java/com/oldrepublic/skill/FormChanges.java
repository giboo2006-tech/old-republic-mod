package com.oldrepublic.skill;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;

/**
 * Who performs a saber form change requested from the Force screen. The saber feature installs a
 * handler (it owns the 20-tick change, the salute and the {@code saber_form} effect); until it does,
 * the skills feature applies the change directly after the combat lock check.
 */
public final class FormChanges {
	/** Performs a form change. {@code formNodeId} is an owned form node, or {@code ""} for Unformed. */
	@FunctionalInterface
	public interface Handler {
		/** Returns true if the change was accepted (it may finish later). */
		boolean request(ServerPlayer player, String formNodeId);
	}

	private static @Nullable Handler handler;

	/** Installs the handler (the saber feature, once). */
	public static void setHandler(Handler value) {
		handler = value;
	}

	static @Nullable Handler handler() {
		return handler;
	}

	private FormChanges() {
	}
}

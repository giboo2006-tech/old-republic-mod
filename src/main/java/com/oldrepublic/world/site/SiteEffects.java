package com.oldrepublic.world.site;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * Effect event ids of the site layer (spec 13.3). The server sends them through
 * {@link com.oldrepublic.network.EffectEvents}; the client handlers live in
 * {@code client.world.site.SiteClient}.
 */
public final class SiteEffects {
	/**
	 * A player used a bounty terminal. pos = the terminal's centre, dir = its screen side,
	 * variant = {@link SiteStates.Army} ordinal. The screen brightens (steady) and {@code terminal.use} plays.
	 */
	public static final Identifier TERMINAL_USE = OldRepublicMod.id("terminal_use");

	private SiteEffects() {
	}
}

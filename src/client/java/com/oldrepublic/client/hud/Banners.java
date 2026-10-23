package com.oldrepublic.client.hud;

import net.minecraft.network.chat.Component;

/**
 * The holo banner above the centre of the screen, for big moments: level up, Resonance, rank up,
 * alignment band change. Banners queue, so two rewards in one tick both get their moment. Drawn by
 * {@link FlourishHud}. Client thread only.
 */
public final class Banners {
	/**
	 * Queues a banner.
	 *
	 * @param title      the small upper line (for example "Rank attained")
	 * @param subtitle   the large lower line (for example "Padawan of the Watch")
	 * @param accentArgb colour of the rules and the large line (see {@code UiColors})
	 */
	public static void show(Component title, Component subtitle, int accentArgb) {
		FlourishHud.showBanner(title, subtitle, accentArgb);
	}

	private Banners() {
	}
}

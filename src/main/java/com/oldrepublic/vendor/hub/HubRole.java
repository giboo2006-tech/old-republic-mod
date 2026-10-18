package com.oldrepublic.vendor.hub;

import java.util.Locale;

/**
 * What a hub person does (spec MILESTONE_4.md 3.5): each is one entity type
 * ({@link HubEntities}); the variant picks who exactly (Ansa or Kaddor, which vendor stock, which
 * apprentice's face).
 */
public enum HubRole {
	/** Keeper Ansa Veylin or Overseer Vael Kaddor ({@code hub_host}); also Zarvos's echo in the Sepulchre. */
	HOST,
	/** A Greenwatch apprentice: barks, the training-saber spar, an escort ({@code apprentice}). */
	APPRENTICE,
	/** A Proving acolyte: barks, the drill-yard duel, an escort ({@code acolyte_hub}). */
	ACOLYTE,
	/** An Imperial guard at the Proving: barks only ({@code proving_guard}). */
	GUARD,
	/** A quartermaster, a discipline trainer or the spacer ({@code vendor}, variant = stock id). */
	VENDOR;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}
}

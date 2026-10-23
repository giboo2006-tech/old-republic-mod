package com.oldrepublic.identity;

import net.minecraft.server.level.ServerPlayer;

/**
 * The one-time Republic standing a player starts Chapter Three with (spec section 5.1): the Jedi
 * path is welcome among the Republic's soldiers, the Sith path is not, and Independents start
 * Neutral. The story feature applies it when Chapter Three reaches SUMMONS.
 */
public final class RepublicSeed {
	/** The reputation source; {@link Identity#changeReputation} records it as the choice {@code rep:republic:seed}. */
	public static final String SOURCE = "seed";
	/** The recorded choice that makes the seed one-shot. */
	public static final String CHOICE = "rep:" + Faction.REPUBLIC.id() + ":" + SOURCE;

	/** The seed for a path. */
	public static int amount(Path path, IdentityBalance b) {
		return switch (path) {
			case JEDI -> b.republicSeedJedi;
			case SITH -> b.republicSeedSith;
			case INDEPENDENT -> b.republicSeedIndependent;
			case NONE -> 0;
		};
	}

	/** Applies the seed once. Returns true when it was applied now, false when it already had been. */
	public static boolean apply(ServerPlayer p) {
		if (Identity.hasChoice(p, CHOICE)) {
			return false;
		}

		Identity.changeReputation(p, Faction.REPUBLIC, amount(Identity.path(p), Identity.balance()), SOURCE, true);
		return Identity.hasChoice(p, CHOICE);
	}

	private RepublicSeed() {
	}
}

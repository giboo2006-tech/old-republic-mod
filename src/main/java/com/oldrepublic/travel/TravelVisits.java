package com.oldrepublic.travel;

import com.mojang.serialization.Codec;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Planets;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/**
 * The worlds a player has landed on by ship, a bit per {@link Planets.Planet} ordinal: the
 * {@code first} flag of {@code QuestHooks.onPlanetArrived} (spec MILESTONE_4.md 15.1). Persistent,
 * kept on death, never synced. An {@code Integer} is immutable, so it needs no seal.
 */
public final class TravelVisits {
	public static final AttachmentType<Integer> VISITS = AttachmentRegistry.create(
			OldRepublicMod.id("travel_visits"),
			builder -> builder
					.initializer(() -> 0)
					.persistent(Codec.INT)
					.copyOnDeath()
	);

	/** Records a landing on {@code planet}; true when it is the player's first there. */
	public static boolean visit(Player p, Planets.Planet planet) {
		int mask = p.getAttachedOrCreate(VISITS);
		int bit = 1 << planet.ordinal();

		if ((mask & bit) != 0) {
			return false;
		}

		p.setAttached(VISITS, mask | bit);
		return true;
	}

	static void init() {
	}

	private TravelVisits() {
	}
}

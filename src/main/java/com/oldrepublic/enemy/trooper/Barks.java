package com.oldrepublic.enemy.trooper;

import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerLevel;

/**
 * Server side of the troopers' radio barks (spec 7.1, 11.2): picks the line, applies the rate
 * limits (one per trooper per {@code barkTrooperTicks}, one per squad per {@code barkSquadTicks}) and
 * sends {@code trooper_bark} around the speaker. The client shows the line on the bark HUD within
 * {@code barkRange} and plays the radio click.
 */
public final class Barks {
	/** The Wary warning: WARN line 0 or 1. */
	public static boolean warn(TrooperEntity speaker) {
		return say(speaker, BarkCategory.WARN, speaker.getRandom().nextInt(2));
	}

	/** The Neutral "Move along.": WARN line 2. */
	public static boolean moveAlong(TrooperEntity speaker) {
		return say(speaker, BarkCategory.WARN, 2);
	}

	/** The leader's standoff line: STANDOFF line 0. */
	public static boolean standoff(TrooperEntity leader) {
		return say(leader, BarkCategory.STANDOFF, 0);
	}

	/** A random line of {@code category}. Use {@link #warn}, {@link #moveAlong} and {@link #standoff} for those. */
	public static boolean say(TrooperEntity speaker, BarkCategory category) {
		return say(speaker, category, speaker.getRandom().nextInt(BarkCategory.LINES));
	}

	/**
	 * Sends line {@code line} of {@code category} from {@code speaker} if the rate limits allow it.
	 *
	 * @return true when the bark went out
	 */
	public static boolean say(TrooperEntity speaker, BarkCategory category, int line) {
		if (!(speaker.level() instanceof ServerLevel level) || !speaker.isAlive() || speaker.isDowned()) {
			return false;
		}

		TrooperBalance b = TrooperBalance.get();
		long now = level.getGameTime();
		Squads.State squad = Squads.state(speaker);

		if (!TrooperRules.barkAllowed(now, speaker.lastBark(), squad.lastBark, b)) {
			return false;
		}

		speaker.setLastBark(now);
		squad.lastBark = now;
		EffectEvents.around(level, TrooperEffects.TROOPER_BARK, speaker.getEyePosition(), null, speaker.getId(),
				b.barkRange, category.variant(Math.floorMod(line, BarkCategory.LINES)));
		return true;
	}

	private Barks() {
	}
}

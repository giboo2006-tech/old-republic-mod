package com.oldrepublic.identity;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;

/**
 * The pure rules of identity: alignment caps and clamping, the shift log, reputation tiers and
 * clamping, path changes and trust. They work on an {@link IdentityData} and the balance values
 * only, so the game tests can check them without a player. {@link Identity} wraps them with
 * syncing, events and feedback.
 */
public final class IdentityRules {
	public static final int ALIGNMENT_LIMIT = 100;
	public static final int REPUTATION_LIMIT = 1000;
	public static final int TRUST_MAX = 100;
	public static final int TICKS_PER_DAY = 24000;
	/** Recorded in {@link IdentityData#choices} once an Independent has joined a mentor (allowed once). */
	public static final String LEFT_INDEPENDENT = "path:left_independent";

	/** How a shift is limited. */
	public enum Cap {
		/** Story choices: one time per source id, no daily cap, full -100 to +100 range. */
		MAJOR,
		/** Contextual acts: daily cap per direction, never beyond the minor limit. */
		MINOR,
		/** Killing innocents: its own daily cap, full range. */
		SEVERE
	}

	/** The in-game day index of a game time, as used by the daily caps and the shift log. */
	public static int dayIndex(long gameTime) {
		return (int) Math.min(Integer.MAX_VALUE - 1, Math.max(0L, gameTime / TICKS_PER_DAY));
	}

	/**
	 * Applies an alignment shift to {@code data}.
	 *
	 * @param sourceId for {@link Cap#MAJOR}, recorded so the same source never shifts again
	 *                 (an empty id is never recorded)
	 * @return the change actually applied (possibly 0 when the value was already at the limit), or
	 *         {@code null} when the shift was ignored: a repeated major source, or a capped minor or
	 *         severe shift with nothing left to give
	 */
	public static @Nullable Integer shift(IdentityData data, String sourceId, int amount, Cap cap, String reasonKey, int day, IdentityBalance b) {
		int allowed;

		switch (cap) {
			case MAJOR -> {
				if (!sourceId.isEmpty() && !data.choices.add(sourceId)) {
					return null;
				}

				allowed = amount;
			}
			case MINOR -> {
				rollDay(data, day);
				allowed = minorAllowance(data, amount, b);

				if (allowed == 0) {
					return null;
				}

				if (allowed > 0) {
					data.minorLight += allowed;
				} else {
					data.minorDark -= allowed;
				}
			}
			case SEVERE -> {
				rollDay(data, day);
				int dark = Math.min(Math.max(0, -amount), Math.max(0, b.severeDailyCap - data.severeDark));

				if (dark == 0) {
					return null;
				}

				data.severeDark += dark;
				allowed = -dark;
			}
			default -> throw new IllegalStateException();
		}

		int before = data.alignment;
		data.alignment = Mth.clamp(before + allowed, -ALIGNMENT_LIMIT, ALIGNMENT_LIMIT);
		int applied = data.alignment - before;

		if (applied != 0) {
			log(data, applied, reasonKey, day);
		}

		return applied;
	}

	/** How much of a minor shift fits in today's cap and the minor limit (same sign as amount, or 0). */
	private static int minorAllowance(IdentityData data, int amount, IdentityBalance b) {
		if (amount > 0) {
			int room = Math.min(b.minorDailyCap - data.minorLight, b.minorLimit - data.alignment);
			return Math.max(0, Math.min(amount, room));
		}

		if (amount < 0) {
			int room = Math.min(b.minorDailyCap - data.minorDark, data.alignment + b.minorLimit);
			return -Math.max(0, Math.min(-amount, room));
		}

		return 0;
	}

	private static void rollDay(IdentityData data, int day) {
		if (data.capDay != day) {
			data.capDay = day;
			data.minorLight = 0;
			data.minorDark = 0;
			data.severeDark = 0;
		}
	}

	/**
	 * Adds a log entry, merging it into the last one when it has the same reason, direction and day
	 * (so a run of small acts reads as one line), and keeps the last {@link IdentityData#LOG_SIZE}.
	 */
	public static void log(IdentityData data, int amount, String reasonKey, int day) {
		int last = data.alignmentLog.size() - 1;

		if (last >= 0) {
			IdentityData.LogEntry previous = data.alignmentLog.get(last);

			if (previous.day() == day && previous.reason().equals(reasonKey) && Integer.signum(previous.amount()) == Integer.signum(amount)) {
				data.alignmentLog.set(last, new IdentityData.LogEntry(previous.amount() + amount, reasonKey, day));
				return;
			}
		}

		data.alignmentLog.add(new IdentityData.LogEntry(amount, reasonKey, day));

		while (data.alignmentLog.size() > IdentityData.LOG_SIZE) {
			data.alignmentLog.removeFirst();
		}
	}

	/** The band of an alignment with the configured edges. */
	public static AlignmentBand band(int alignment, IdentityBalance b) {
		if (alignment >= b.lightThreshold) {
			return AlignmentBand.LIGHT;
		}

		if (alignment >= b.leaningThreshold) {
			return AlignmentBand.LEANING_LIGHT;
		}

		if (alignment <= -b.lightThreshold) {
			return AlignmentBand.DARK;
		}

		if (alignment <= -b.leaningThreshold) {
			return AlignmentBand.LEANING_DARK;
		}

		return AlignmentBand.BALANCED;
	}

	/** The tier of a reputation value with the configured edges. */
	public static ReputationTier tier(int rep, IdentityBalance b) {
		if (rep >= b.repHonoured) {
			return ReputationTier.HONOURED;
		}

		if (rep >= b.repTrusted) {
			return ReputationTier.TRUSTED;
		}

		if (rep <= b.repHostile) {
			return ReputationTier.HOSTILE;
		}

		if (rep <= b.repDistrusted) {
			return ReputationTier.DISTRUSTED;
		}

		return ReputationTier.NEUTRAL;
	}

	/**
	 * Changes reputation, clamped to plus or minus {@link #REPUTATION_LIMIT}.
	 *
	 * @param oneShot when true the change applies only once per source id and faction
	 * @return the change applied, or null when a one-shot source was already used
	 */
	public static @Nullable Integer changeReputation(IdentityData data, Faction faction, int amount, String sourceId, boolean oneShot) {
		if (oneShot && !data.choices.add("rep:" + faction.id() + ":" + sourceId)) {
			return null;
		}

		int before = data.reputation(faction);
		int after = Mth.clamp(before + amount, -REPUTATION_LIMIT, REPUTATION_LIMIT);
		data.reputation.put(faction.id(), after);
		return after - before;
	}

	/** Sets reputation directly (operator command), clamped. */
	public static void setReputation(IdentityData data, Faction faction, int value) {
		data.reputation.put(faction.id(), Mth.clamp(value, -REPUTATION_LIMIT, REPUTATION_LIMIT));
	}

	/**
	 * Whether a path change is allowed: from NONE to any path; from INDEPENDENT to JEDI or SITH once;
	 * nothing else (JEDI and SITH are permanent, nobody goes back to NONE) unless forced.
	 */
	public static boolean pathChangeAllowed(IdentityData data, Path to, boolean force) {
		Path from = data.path;

		if (from == to) {
			return false;
		}

		if (force) {
			return true;
		}

		return switch (from) {
			case NONE -> to != Path.NONE;
			case INDEPENDENT -> (to == Path.JEDI || to == Path.SITH) && !data.choices.contains(LEFT_INDEPENDENT);
			case JEDI, SITH -> false;
		};
	}

	/**
	 * Applies a path change already checked with {@link #pathChangeAllowed}: Jedi and Sith start at
	 * rank 1 (Initiate / Acolyte) unless already higher; Independents and NONE have rank 0.
	 */
	public static void applyPath(IdentityData data, Path to) {
		if (data.path == Path.INDEPENDENT && (to == Path.JEDI || to == Path.SITH)) {
			data.choices.add(LEFT_INDEPENDENT);
		}

		data.path = to;
		data.rank = (to == Path.JEDI || to == Path.SITH) ? Math.max(1, data.rank) : 0;
	}

	/** The faction a path belongs to, or null for NONE and INDEPENDENT. */
	public static @Nullable Faction factionOf(Path path) {
		return switch (path) {
			case JEDI -> Faction.JEDI_ORDER;
			case SITH -> Faction.SITH_EMPIRE;
			default -> null;
		};
	}

	/** The mentor (trust key) of a path, or null for NONE and INDEPENDENT. */
	public static @Nullable String mentorOf(Path path) {
		return switch (path) {
			case JEDI -> "jedi";
			case SITH -> "sith";
			default -> null;
		};
	}

	/** Adds trust, clamped to 0 to {@link #TRUST_MAX}. Returns the change applied. */
	public static int addTrust(IdentityData data, String mentorId, int amount) {
		int before = data.trust(mentorId);
		int after = Mth.clamp(before + amount, 0, TRUST_MAX);
		data.trust.put(mentorId, after);
		return after - before;
	}

	private IdentityRules() {
	}
}

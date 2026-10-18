package com.oldrepublic.quest.story;

/** How the owner ended the duel with Torvin Kaast (spec MILESTONE_4.md 8.6). Stored by ordinal, append only. */
public enum KaastOutcome {
	SPARED,
	STRIPPED,
	ENDED;

	/** The dialogue argument for this outcome: {@code spare}, {@code strip} or {@code end}. */
	public String id() {
		return switch (this) {
			case SPARED -> "spare";
			case STRIPPED -> "strip";
			case ENDED -> "end";
		};
	}

	/** The outcome for a dialogue argument, or null. */
	public static @org.jspecify.annotations.Nullable KaastOutcome byId(String id) {
		for (KaastOutcome outcome : values()) {
			if (outcome.id().equals(id)) {
				return outcome;
			}
		}

		return null;
	}
}

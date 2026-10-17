package com.oldrepublic.quest.story;

import com.oldrepublic.identity.CaptiveOutcome;

/** What became of the three captive settlers. Stored by ordinal; only append. */
public enum CaptivesFate {
	NONE,
	FREED,
	EXPLOITED,
	ABANDONED;

	public static CaptivesFate of(CaptiveOutcome outcome) {
		return switch (outcome) {
			case FREED -> FREED;
			case EXPLOITED -> EXPLOITED;
			case ABANDONED -> ABANDONED;
		};
	}

	public static CaptivesFate of(int ordinal) {
		CaptivesFate[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}

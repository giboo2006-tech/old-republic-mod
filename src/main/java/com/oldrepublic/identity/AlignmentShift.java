package com.oldrepublic.identity;

/**
 * One request to move a player's alignment, passed to {@link Identity#shift}.
 *
 * @param sourceId  what caused it, such as {@code reckoning.captain}; a {@link Kind#MAJOR} shift
 *                  applies only once per source id
 * @param amount    positive toward the Light, negative toward the Dark
 * @param kind      major (one time, uncapped) or minor (daily capped, limited to the minor range)
 * @param reasonKey lang key shown in the shift toast and the Standing tab's log
 */
public record AlignmentShift(String sourceId, int amount, Kind kind, String reasonKey) {
	public enum Kind {
		MAJOR,
		MINOR
	}
}

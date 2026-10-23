package com.oldrepublic.boss.raid;

import com.oldrepublic.boss.cantor.LongHymn;

/**
 * Server and common initialisation of the Long Hymn (spec MILESTONE_5.md 12.3, 12.4): the
 * {@code oldrepublic:raid} attachment ({@link RaidData}), then the First Cantor's entities, particle,
 * reactions, runs, hooks and the {@code raid} command ({@link LongHymn}). Called from
 * {@code FoesM5Feature}.
 */
public final class RaidFeature {
	public static void init() {
		RaidAttachments.init();
		LongHymn.init();
	}

	private RaidFeature() {
	}
}

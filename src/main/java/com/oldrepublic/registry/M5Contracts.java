package com.oldrepublic.registry;

/**
 * Milestone 5's Phase 0 contracts (spec MILESTONE_5.md 17.1): the shared items and the new
 * attachments. Workers' features register their own content from their own init. Called once from
 * {@code OldRepublicMod} after the Milestone 4 features, before the Milestone 5 ones and the
 * creative tab.
 */
public final class M5Contracts {
	public static void init() {
		M5Items.init();
	}

	private M5Contracts() {
	}
}

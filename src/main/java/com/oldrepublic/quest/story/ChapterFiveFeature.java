package com.oldrepublic.quest.story;

/**
 * Milestone 5 server and common initialisation for Chapter Five, "The Long Hymn" (section 6), the
 * Vessba kajidic and Clan Hask (section 7): the chapter service, standing, vendors, contracts, the
 * journal and the hub people (spec MILESTONE_5.md). Owned by W4 story and factions; the lead calls
 * it from {@code OldRepublicMod} after the Phase 0 contracts, so the worker never edits the mod's
 * entry point. Register this feature's own items, blocks, entities, particles, payloads, balance
 * section, events and commands from here.
 */
public final class ChapterFiveFeature {
	/**
	 * The chapter's dialogue conditions and actions (6.11) and its {@code /oldrepublic c5} commands.
	 * Its ticks, hooks and leftovers run through {@link StoryService} and {@code QuestHooks}; the hub
	 * people, vendors and boards register with their Milestone 4 features.
	 */
	public static void init() {
		ChapterFiveService.init();
	}

	private ChapterFiveFeature() {
	}
}

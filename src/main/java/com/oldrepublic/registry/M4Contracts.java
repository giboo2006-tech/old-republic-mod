package com.oldrepublic.registry;

import com.oldrepublic.combat.WeaponActions;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelService;

/**
 * Milestone 4's Phase 0 contracts (spec MILESTONE_4.md 15.1): the shared items and components, the
 * new attachments, the Weapon Action payload and the "not while flying" lock. Workers' features
 * register their own content from their own init. Called once from {@code OldRepublicMod}, before
 * the creative tab is built.
 */
public final class M4Contracts {
	public static void init() {
		M4Components.init();
		M4Items.init();
		TravelAttachments.init();
		ShipRegistry.init();
		CraftingAttachments.init();
		WeaponActions.init();
		TravelService.init();
		ChapterFourService.init();
	}

	private M4Contracts() {
	}
}

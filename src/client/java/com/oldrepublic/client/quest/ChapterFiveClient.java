package com.oldrepublic.client.quest;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.quest.QuestEffects;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Milestone 5 client initialisation for Chapter Five (sections 6 and 7): the verse vision
 * ({@link VerseVision}, 6.6, 15.3). The Chapter Five journal page lives in {@link JournalScreen},
 * the Standing panel's new rows and the rank 4 checklist in {@code StandingPanel}, the planet boards'
 * space bounty in {@code BoardScreen}, and Vessba's renderer in {@code VendorClient}, so they need
 * nothing here. Owned by W4 story and factions; the lead calls it from {@code OldRepublicModClient},
 * so the worker never edits the client entry point.
 */
public final class ChapterFiveClient {
	private static ClientLevel lastLevel;

	public static void init() {
		ClientEffects.register(QuestEffects.VERSE_VISION, VerseVision::handle);
		// Drawn last so the vision washes over the whole HUD.
		HudElementRegistry.addLast(OldRepublicMod.id("verse_vision"), VerseVision::extract);
		ClientTickEvents.END_CLIENT_TICK.register(ChapterFiveClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VerseVision.reset());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			VerseVision.reset();
		}

		VerseVision.tick();
	}

	private ChapterFiveClient() {
	}
}

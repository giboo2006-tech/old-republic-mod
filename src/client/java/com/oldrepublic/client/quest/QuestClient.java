package com.oldrepublic.client.quest;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.hud.FlourishHud;
import com.oldrepublic.client.hud.HudClient;
import com.oldrepublic.client.mentor.MentorClient;
import com.oldrepublic.quest.JournalItem;
import com.oldrepublic.quest.LedgerItem;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/** Client initialisation for the quest feature. Called from OldRepublicModClient. */
public final class QuestClient {
	private static final KeyMapping JOURNAL_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.oldrepublic.journal", InputConstants.Type.KEYSYM, InputConstants.KEY_J, ForceKeys.CATEGORY));

	private static int ticks;
	private static ClientLevel lastLevel;

	public static void init() {
		JournalItem.clientOpener = player -> openJournal();
		LedgerItem.clientOpener = player -> openLedger();
		MentorClient.init();
		QuestParticleProviders.init();

		ClientEffects.register(QuestEffects.AWAKENING_DREAM, DreamOverlay::handle);
		ClientEffects.register(QuestEffects.AWAKENING_BURST, AwakeningFx::handle);
		ClientEffects.register(QuestEffects.MEDITATION, MeditationFx::handle);
		ClientEffects.register(QuestEffects.CALLING, CallingFx::handle);
		ClientEffects.register(QuestEffects.CAIRN_TOUCH, StoryM3Fx::cairnTouch);
		ClientEffects.register(QuestEffects.RANK_CEREMONY, StoryM3Fx::rankCeremony);
		ClientEffects.register(QuestEffects.JOURNAL_ENTRY, (client, level, payload, entity) -> {
			boolean codex = payload.variant() == 1;
			JournalScreen.markUnread(codex, (int) payload.intensity());
			FlourishHud.showJournalNotice(codex);
			QuestSounds.ui(ModSounds.JOURNAL_ENTRY, 1f, 0.8f);
		});

		// Drawn last so the dream covers the whole HUD.
		HudElementRegistry.addLast(OldRepublicMod.id("awakening_dream"), DreamOverlay::extract);

		ClientTickEvents.END_CLIENT_TICK.register(QuestClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetAll());
	}

	private static void tick(Minecraft mc) {
		ticks++;

		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			resetEffects();
		}

		while (JOURNAL_KEY.consumeClick()) {
			if (mc.gui.screen() == null && mc.player != null) {
				openJournal();
			}
		}

		DreamOverlay.tick();
		MeditationFx.tick(mc);
		CallingFx.tick(mc);
		StoryM3Fx.tick(mc);
	}

	/** Opens the journal screen (from the key or by using the journal item). */
	public static void openJournal() {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		QuestSounds.ui(ModSounds.JOURNAL_OPEN, 1f, 0.8f);
		mc.gui.setScreen(new JournalScreen());
	}

	/** Opens the Poachers' Ledger in the vanilla book reader (fixed lang pages). */
	public static void openLedger() {
		Minecraft mc = Minecraft.getInstance();
		List<Component> pages = new ArrayList<>();

		for (int i = 1; i <= LedgerItem.PAGES; i++) {
			pages.add(Component.translatable(LedgerItem.PAGE_PREFIX + i));
		}

		// API-CHECK: BookViewScreen.BookAccess is a record taking List<Component> pages (constructor not in the probe).
		mc.gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
	}

	/** Client ticks since start; drives every quest and HUD animation. */
	public static int ticks() {
		return ticks;
	}

	public static KeyMapping journalKey() {
		return JOURNAL_KEY;
	}

	private static void resetEffects() {
		DreamOverlay.reset();
		MeditationFx.reset();
		CallingFx.reset();
		StoryM3Fx.reset();
	}

	private static void resetAll() {
		resetEffects();
		JournalScreen.resetSession();
		HudClient.reset();
	}

	private QuestClient() {
	}
}

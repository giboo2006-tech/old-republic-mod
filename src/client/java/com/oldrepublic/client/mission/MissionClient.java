package com.oldrepublic.client.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.quest.JournalTabs;
import com.oldrepublic.mission.MissionEffects;
import com.oldrepublic.mission.network.MissionBoardPayload;
import com.oldrepublic.mission.network.TrackerHintPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * Client initialisation for the mission feature (spec section 5): the board screen opened by
 * {@code mission_board}, the tracker fed by {@code tracker_hint}, the discovery toast and the
 * mission update chimes, and the Missions tab of the journal. Called from OldRepublicModClient.
 */
public final class MissionClient {
	/** The Missions tab's id ({@code JournalScreen.openOnTab("missions")}). */
	public static final String TAB_ID = "missions";
	/** After the journal's own pages and the Holocrons tab (order 20). */
	private static final int TAB_ORDER = 30;

	private static @Nullable ClientLevel lastLevel;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MissionBoardPayload.TYPE, (payload, context) -> showBoard(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(TrackerHintPayload.TYPE, (payload, context) -> ClientMissionState.hint(payload));

		ClientEffects.register(MissionEffects.SITE_DISCOVERED, MissionFx::siteDiscovered);
		ClientEffects.register(MissionEffects.MISSION_UPDATE, MissionFx::missionUpdate);

		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, OldRepublicMod.id("mission_tracker"), MissionTrackerHud::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("site_discovered"), MissionFx::extractToast);

		JournalTabs.register(new JournalTabs.Tab(TAB_ID, Component.translatable("screen.oldrepublic.missions.tab"), TAB_ORDER, MissionsPanel::new,
				MissionsPanel::visible));

		ClientTickEvents.END_CLIENT_TICK.register(MissionClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	/** Opens the board, or refreshes the open one of the same terminal in place. */
	private static void showBoard(Minecraft mc, MissionBoardPayload payload) {
		if (mc.gui.screen() instanceof BoardScreen screen && screen.isFor(payload)) {
			screen.show(payload);
			return;
		}

		// The terminal's own terminal_use moment (W1) already sounds; the screen opens silently.
		mc.gui.setScreen(new BoardScreen(payload));
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			reset();
		}
	}

	private static void reset() {
		ClientMissionState.reset();
		MissionFx.reset();
		MissionTrackerHud.reset();
	}

	private MissionClient() {
	}
}

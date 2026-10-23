package com.oldrepublic.client.mission;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.mission.MissionEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The mission feature's two personal moments (spec 13.3), both {@code toPlayer}:
 *
 * <ul>
 * <li>{@code site_discovered} (variant = {@code SiteKind} ordinal): a "Discovered" toast slides down
 * at the top centre with the site's name over a rule in the site's tint (warm gold for a Jedi Ruin,
 * deep red for a Sith Tomb, brass for a Wreck Field, the army's colour for an outpost), and
 * {@code site.discovered} plays (three recorded variants, picked by the sound engine). Toasts queue,
 * so two discoveries in one moment each get their turn.</li>
 * <li>{@code mission_update} (0 accept, 1 progress): {@code mission.accept} or {@code mission.update}
 * plays and the tracker flashes ({@link MissionTrackerHud}).</li>
 * </ul>
 *
 * Nothing here is a world effect: no particles, so presets and range do not apply. With flashing
 * effects off the toast's rule glows steadily.
 */
final class MissionFx {
	private static final int TOAST_TICKS = 90;
	private static final int TOAST_IN = 8;
	private static final int TOAST_OUT = 14;
	private static final int TOP = 36;
	private static final int MAX_QUEUED = 4;

	private static final int RUIN = 0xFFF3E3A6;
	private static final int TOMB = 0xFFD0463A;
	private static final int WRECK = 0xFFC9A66B;
	private static final int REPUBLIC = 0xFF8FB8E8;

	private static final Deque<SiteKind> QUEUE = new ArrayDeque<>();
	private static @Nullable SiteKind showing;
	private static int showStart = Integer.MIN_VALUE;

	// ---------------------------------------------------------------- handlers

	static void siteDiscovered(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		SiteKind[] kinds = SiteKind.values();
		int ordinal = payload.variant();

		if (ordinal < 0 || ordinal >= kinds.length) {
			return;
		}

		if (QUEUE.size() < MAX_QUEUED) {
			QUEUE.addLast(kinds[ordinal]);
		}
	}

	static void missionUpdate(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		boolean accept = payload.variant() == MissionEffects.UPDATE_ACCEPT;
		QuestSounds.ui(accept ? ModSounds.MISSION_ACCEPT : ModSounds.MISSION_UPDATE, 1f, accept ? 0.85f : 0.6f);
		ClientMissionState.flash(accept);
	}

	// ---------------------------------------------------------------- the toast

	/** HUD pass: the discovery toast, one at a time. */
	static void extractToast(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return;
		}

		float now = QuestClient.ticks() + deltaTracker.getGameTimeDeltaPartialTick(true);

		if (showing != null && now > showStart + TOAST_TICKS) {
			showing = null;
		}

		if (showing == null && !QUEUE.isEmpty()) {
			showing = QUEUE.removeFirst();
			showStart = QuestClient.ticks();
			// The sound belongs to the toast's moment, so queued discoveries each get theirs.
			QuestSounds.ui(ModSounds.SITE_DISCOVERED, 1f, 0.9f);
		}

		if (showing == null || mc.gui.hud.isHidden()) {
			return;
		}

		draw(graphics, mc.font, showing, now);
	}

	/** The site kind's id as capitalised words, shown when the {@code site.oldrepublic.<id>} key is missing. */
	private static String fallbackName(SiteKind kind) {
		StringBuilder name = new StringBuilder();

		for (String word : kind.id().split("_")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}

		return name.toString();
	}

	private static void draw(GuiGraphicsExtractor g, Font font, SiteKind kind, float now) {
		float alpha = UiAnim.envelope(now, showStart, TOAST_TICKS, TOAST_IN, TOAST_OUT);

		if (alpha <= 0.02f) {
			return;
		}

		float t = now - showStart;
		Component label = Component.translatable("mission.oldrepublic.discovered");
		// W1 owns the site names; until a key exists the id reads as words ("Jedi Ruin").
		Component name = Component.translatableWithFallback("site.oldrepublic." + kind.id(), fallbackName(kind));
		int accent = tint(kind);
		int w = Math.max(font.width(label), font.width(name)) + 28;
		int h = 26;
		int x = (g.guiWidth() - w) / 2;
		int y = TOP - Math.round((1f - UiAnim.easeOutCubic(Mth.clamp(t / TOAST_IN, 0f, 1f))) * 8f);

		Ui.plate(g, x, y, w, h, alpha * 0.95f);
		// A thin rule in the site's tint opens out under the name.
		int half = Math.round((w / 2f - 6) * UiAnim.easeOutCubic(Mth.clamp((t - 2) / 10f, 0f, 1f)));
		Ui.fadeLine(g, x + w / 2, y + h - 4, Math.max(2, half), accent, alpha * 0.85f * UiAnim.flicker(now));
		Ui.text(g, font, label, x + (w - font.width(label)) / 2, y + 3, UiColors.TEXT_MUTED, alpha);
		Ui.text(g, font, name, x + (w - font.width(name)) / 2, y + 13, UiColors.TEXT_WARM, alpha * Mth.clamp((t - 3) / 6f, 0f, 1f));
	}

	/** The rule's colour for a site kind; a kind without its own colour (the Milestone 5 sites, MILESTONE_5.md 3.2 to 3.4) takes the brass. */
	static int tint(SiteKind kind) {
		return switch (kind) {
			case JEDI_RUIN -> RUIN;
			case SITH_TOMB, IMPERIAL_OUTPOST -> TOMB;
			case WRECK_FIELD -> WRECK;
			case REPUBLIC_OUTPOST -> REPUBLIC;
			case CRYSTAL_CAVE, ECHO_SHRINE -> UiColors.HOLO;
			case COURIERS_FALL, RELIQUARY -> UiColors.AMBER;
			case MISSION_SITE, PLANET_HUB, SHIP_CRADLE -> UiColors.BRASS_LIGHT;
			case WAYSTONE, HEARTSTONE, EXPEDITION -> UiColors.HOLO;
			case GREATER_TOMB, SEPULCHRE -> TOMB;
			case WAYFARER_CACHE -> UiColors.AMBER;
			case HUNTING_CAMP -> WRECK;
			default -> UiColors.BRASS_LIGHT;
		};
	}

	static void reset() {
		QUEUE.clear();
		showing = null;
		showStart = Integer.MIN_VALUE;
	}

	private MissionFx() {
	}
}

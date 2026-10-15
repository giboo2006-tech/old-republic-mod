package com.oldrepublic.client.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The Awakening dream: the screen fades to near-black with a slow pulse while three whispered lines
 * surface and fade. Plays the whispers and a drone loop that stops cleanly when the dream ends or is
 * cancelled (the server cancels it if the dreamer takes damage).
 */
public final class DreamOverlay {
	private static final int FADE_IN = 20;
	private static final int FADE_OUT = 20;
	private static final int CANCEL_FADE = 8;
	private static final int LINE_START = 14;
	private static final int LINE_SPACING = 40;
	private static final int LINE_LENGTH = 38;
	private static final int LINE_FADE = 8;
	private static final int LINES = 3;

	private static boolean active;
	private static int startTick;
	private static int endTick;
	private static @Nullable LoopSound drone;
	private static @Nullable SimpleSoundInstance whispers;

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.variant() == QuestEffects.DREAM_CANCEL) {
			cancel();
			return;
		}

		stopSounds(true);
		active = true;
		startTick = QuestClient.ticks();
		endTick = startTick + Math.max(FADE_IN + FADE_OUT, (int) payload.intensity());
		whispers = QuestSounds.ui(ModSounds.AWAKENING_WHISPERS, 1f, 0.9f);
		drone = LoopSound.play(ModSounds.AWAKENING_DRONE, 0.8f, null);
	}

	private static void cancel() {
		if (!active) {
			return;
		}

		int now = QuestClient.ticks();
		endTick = Math.min(endTick, now + CANCEL_FADE);
		stopSounds(false);

		if (whispers != null) {
			Minecraft.getInstance().getSoundManager().stop(whispers);
			whispers = null;
		}
	}

	static void tick() {
		if (!active) {
			return;
		}

		int now = QuestClient.ticks();

		if (now >= endTick - FADE_OUT / 2 && drone != null) {
			drone.release();
			drone = null;
		}

		Minecraft mc = Minecraft.getInstance();

		if (now >= endTick || mc.player == null || !mc.player.isAlive()) {
			reset();
		}
	}

	/** Ends the dream at once and silences it (disconnect, level change, death). */
	static void reset() {
		active = false;
		stopSounds(true);
	}

	private static void stopSounds(boolean hard) {
		if (drone != null) {
			if (hard) {
				drone.kill();
			} else {
				drone.release();
			}

			drone = null;
		}

		if (hard && whispers != null) {
			Minecraft.getInstance().getSoundManager().stop(whispers);
			whispers = null;
		}
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!active) {
			return;
		}

		float t = QuestClient.ticks() - startTick + deltaTracker.getGameTimeDeltaPartialTick(true);
		float fade = Math.min(Mth.clamp(t / FADE_IN, 0f, 1f), Mth.clamp((endTick - startTick - t) / FADE_OUT, 0f, 1f));

		float pulse = Vfx.flashing() ? 0.045f * Mth.sin(t * Mth.PI * 2f / 80f) : 0f;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int dark = (int) (255 * fade * Mth.clamp(0.9f + pulse, 0f, 1f));
		graphics.fill(0, 0, width, height, dark << 24 | 0x02030A);
		// A faint cold haze from below, like light far away under stone.
		int haze = (int) (60 * fade * (1f + pulse * 4f));
		graphics.fillGradient(0, height / 2, width, height, 0x000A1420, Mth.clamp(haze, 0, 255) << 24 | 0x0A1828);

		Minecraft mc = Minecraft.getInstance();

		for (int i = 0; i < LINES; i++) {
			float local = t - (LINE_START + i * LINE_SPACING);

			if (local < 0 || local > LINE_LENGTH) {
				continue;
			}

			float lineAlpha = Math.min(Mth.clamp(local / LINE_FADE, 0f, 1f), Mth.clamp((LINE_LENGTH - local) / LINE_FADE, 0f, 1f)) * fade;
			int alpha = (int) (lineAlpha * 230);

			if (alpha < 8) {
				continue;
			}

			Component line = Component.translatable("dream.oldrepublic.line" + (i + 1)).withStyle(ChatFormatting.ITALIC);
			graphics.centeredText(mc.font, line, width / 2, height / 2 - 4 + (int) ((1f - lineAlpha) * 3f), alpha << 24 | 0xC4CEDD);
		}
	}

	private DreamOverlay() {
	}
}

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
 *
 * <p>Variant 2 is Chapter Two's Two Voices dream: three shorter lines alternate between a warm voice
 * (pale gold) and a cold one (grey-blue) over the {@code story.two_voices} bed, which stops the same
 * way.
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
	/** The Two Voices dream is 6 s: its lines come faster. */
	private static final int VOICES_LINE_SPACING = 32;
	private static final int VOICES_LINE_LENGTH = 30;
	private static final int WARM = 0xE8D2A0;
	private static final int COLD = 0xA8C4E0;

	private static boolean active;
	private static int startTick;
	private static int endTick;
	private static boolean twoVoices;
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
		twoVoices = payload.variant() == QuestEffects.DREAM_TWO_VOICES;

		if (twoVoices) {
			drone = LoopSound.play(ModSounds.STORY_TWO_VOICES, 0.8f, null);
		} else {
			whispers = QuestSounds.ui(ModSounds.AWAKENING_WHISPERS, 1f, 0.9f);
			drone = LoopSound.play(ModSounds.AWAKENING_DRONE, 0.8f, null);
		}
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

		int spacing = twoVoices ? VOICES_LINE_SPACING : LINE_SPACING;
		int length = twoVoices ? VOICES_LINE_LENGTH : LINE_LENGTH;

		for (int i = 0; i < LINES; i++) {
			float local = t - (LINE_START + i * spacing);

			if (local < 0 || local > length) {
				continue;
			}

			float lineAlpha = Math.min(Mth.clamp(local / LINE_FADE, 0f, 1f), Mth.clamp((length - local) / LINE_FADE, 0f, 1f)) * fade;
			int alpha = (int) (lineAlpha * 230);

			if (alpha < 8) {
				continue;
			}

			String key = (twoVoices ? "dream.oldrepublic.voices.line" : "dream.oldrepublic.line") + (i + 1);
			int colour = twoVoices ? (i % 2 == 0 ? WARM : COLD) : 0xC4CEDD;
			Component line = Component.translatable(key).withStyle(ChatFormatting.ITALIC);
			graphics.centeredText(mc.font, line, width / 2, height / 2 - 4 + (int) ((1f - lineAlpha) * 3f), alpha << 24 | colour);
		}
	}

	private DreamOverlay() {
	}
}

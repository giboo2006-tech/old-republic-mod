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
 *
 * <p>Variant 3 is Chapter Three's dream, The Ones That Sing: the two armies' banners snap as black
 * silhouettes against a low dusk glow, and below them a crystal glows inside a split stone, pulsing
 * slowly (steady when flashing effects are off). Three lines in a pale crystal tone surface at the
 * Two Voices pace over the {@code story.ones_that_sing} bed, which stops the same way.
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
	private static final int CRYSTAL_LINE = 0xCDE6DE;
	private static final int DUSK = 0x3A2638;
	private static final int SILHOUETTE = 0x040509;
	private static final int STONE = 0x14161C;
	private static final int CRYSTAL = 0x8FE3D2;

	private static boolean active;
	private static int startTick;
	private static int endTick;
	private static boolean twoVoices;
	private static boolean onesThatSing;
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
		onesThatSing = payload.variant() == QuestEffects.DREAM_ONES_THAT_SING;

		if (onesThatSing) {
			drone = LoopSound.play(ModSounds.STORY_ONES_THAT_SING, 0.8f, null);
		} else if (twoVoices) {
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

		if (onesThatSing) {
			drawOnesThatSing(graphics, t, fade, width, height);
		}

		Minecraft mc = Minecraft.getInstance();

		boolean quick = twoVoices || onesThatSing;
		int spacing = quick ? VOICES_LINE_SPACING : LINE_SPACING;
		int length = quick ? VOICES_LINE_LENGTH : LINE_LENGTH;

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

			String key = (onesThatSing ? "dream.oldrepublic.sing.line" : twoVoices ? "dream.oldrepublic.voices.line" : "dream.oldrepublic.line") + (i + 1);
			int colour = onesThatSing ? CRYSTAL_LINE : twoVoices ? (i % 2 == 0 ? WARM : COLD) : 0xC4CEDD;
			Component line = Component.translatable(key).withStyle(ChatFormatting.ITALIC);
			graphics.centeredText(mc.font, line, width / 2, height / 2 - 4 + (int) ((1f - lineAlpha) * 3f), alpha << 24 | colour);
		}
	}

	/**
	 * The Ones That Sing: a dusk glow along a far horizon, two banners on tall poles snapping in the
	 * wind as silhouettes (one on each side, both flying with the wind), and a split stone low in the
	 * middle with a crystal glowing inside it.
	 */
	private static void drawOnesThatSing(GuiGraphicsExtractor graphics, float t, float fade, int width, int height) {
		int horizon = height * 3 / 5;
		int glow = (int) (150 * fade);
		graphics.fillGradient(0, horizon - height / 5, width, horizon, DUSK, glow << 24 | DUSK);
		graphics.fillGradient(0, horizon, width, horizon + 6, glow << 24 | DUSK, DUSK);
		int ground = (int) (235 * fade);
		graphics.fill(0, horizon + 2, width, height, ground << 24 | SILHOUETTE);

		boolean motion = Vfx.flashing();
		int poleHeight = Math.max(40, height / 3);
		banner(graphics, width * 3 / 10, horizon, poleHeight, 1, t, fade, motion);
		banner(graphics, width * 7 / 10 - poleHeight / 3, horizon, poleHeight - 6, 1, t + 17f, fade, motion);

		// The split stone, its crystal glowing through the crack.
		int cx = width / 2;
		int top = horizon + Math.max(10, height / 12);
		int stoneAlpha = (int) (255 * fade) << 24;
		graphics.fill(cx - 22, top + 8, cx + 22, top + 30, stoneAlpha | STONE);
		graphics.fill(cx - 16, top + 2, cx + 18, top + 8, stoneAlpha | STONE);
		graphics.fill(cx - 9, top - 3, cx + 10, top + 2, stoneAlpha | STONE);

		float beat = motion ? 0.5f + 0.5f * Mth.sin(t * Mth.PI * 2f / 48f) : 0.6f;
		for (int ring = 3; ring >= 1; ring--) {
			int alpha = (int) (fade * (18 + 22 * beat) / ring);
			int r = 4 + ring * 6;
			graphics.fill(cx - r, top + 12 - r / 2, cx + r, top + 20 + r / 2, Mth.clamp(alpha, 0, 255) << 24 | CRYSTAL);
		}

		int core = (int) (fade * (150 + 90 * beat));
		int coreColour = Mth.clamp(core, 0, 255) << 24 | CRYSTAL;
		// A narrow crack down the stone, widest where the crystal sits.
		graphics.fill(cx, top - 2, cx + 1, top + 28, coreColour);
		graphics.fill(cx - 1, top + 8, cx + 2, top + 24, coreColour);
		graphics.fill(cx - 2, top + 12, cx + 3, top + 20, coreColour);
	}

	/** One banner silhouette: a pole rising from {@code ground}, its cloth flying toward {@code side}. */
	private static void banner(GuiGraphicsExtractor graphics, int x, int ground, int poleHeight, int side, float t, float fade, boolean motion) {
		int colour = (int) (255 * fade) << 24 | SILHOUETTE;
		int top = ground - poleHeight;
		graphics.fill(x - 1, top, x + 1, ground + 2, colour);
		graphics.fill(x - 2, top - 2, x + 2, top, colour);

		int cloth = Math.max(26, poleHeight * 2 / 3);
		int depth = Math.max(14, cloth / 2);

		for (int c = 0; c < cloth; c += 2) {
			float along = c / (float) cloth;
			float wave = motion ? Mth.sin(t * 0.55f - c * 0.22f) * along * 4f : along * 1.5f;
			// The fly end frays: the strips shorten and tear toward the tip.
			int tear = along > 0.8f ? (int) ((along - 0.8f) * depth * 2f) : 0;
			int x0 = side > 0 ? x + 1 + c : x - 1 - c - 2;
			int y0 = top + 1 + (int) wave;
			graphics.fill(x0, y0, x0 + 2, y0 + depth - tear, colour);
		}
	}

	private DreamOverlay() {
	}
}

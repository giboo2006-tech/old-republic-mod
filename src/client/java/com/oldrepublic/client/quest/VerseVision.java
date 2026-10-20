package com.oldrepublic.client.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
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
 * The verse vision (spec MILESTONE_5.md 6.6, 15.3, {@code verse_vision}): when one of the player's
 * verse stones falls silent, a six-second wash of pale grey-gold covers the screen, the world's
 * sound dips under the vision's own bed ({@code story.verse_vision}), and three subtitled lines
 * surface and fade over a faint motif drawn low on the screen. The variant is the vision, by the
 * order the stones fall (not their index):
 *
 * <ol start="0">
 * <li>The First Cantor composing the Hymn: a music stave across the middle, its notes written in
 * one by one.</li>
 * <li>The choir of bound singers: a row of kneeling singers, their voices rising off them as
 * motes.</li>
 * <li>The Hymn sung over a city that fell quiet: a skyline of pale roofs whose windows go dark one by
 * one.</li>
 * </ol>
 *
 * <p>With flashing effects off, the wash and the motif hold steady (no shimmer, no rising motes). The
 * vision ends early on death, a level change or a disconnect; {@link #dipping()} tells the world's
 * ambience owners when to duck their beds.
 */
public final class VerseVision {
	private static final String[] VERSES = {"verse_one", "verse_two", "verse_three"};
	private static final int MIN_TICKS = 60;
	private static final int FADE_IN = 18;
	private static final int FADE_OUT = 20;
	private static final int LINE_START = 14;
	private static final int LINE_FADE = 8;
	private static final int LINES = 3;
	/** The pale grey-gold of the wash, its deeper edge, the lines and the motif's ink. */
	private static final int WASH = 0xD9CDA8;
	private static final int EDGE = 0x6E6248;
	private static final int LINE = 0xF6E9C4;
	private static final int INK = 0x2E2618;
	private static final int WINDOW = 0xF2D27A;
	private static final float WASH_ALPHA = 0.46f;

	private static boolean active;
	private static int variant;
	private static int startTick;
	private static int endTick;
	private static @Nullable SimpleSoundInstance bed;

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		stopSound();
		active = true;
		variant = Mth.clamp(payload.variant(), 0, VERSES.length - 1);
		startTick = QuestClient.ticks();
		endTick = startTick + Math.max(MIN_TICKS, (int) payload.intensity());
		bed = QuestSounds.ui(ModSounds.STORY_VERSE_VISION, 1f, 0.9f);
	}

	static void tick() {
		if (!active) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (QuestClient.ticks() >= endTick || mc.player == null || !mc.player.isAlive()) {
			reset();
		}
	}

	/** Ends the vision at once and silences it (disconnect, level change, death). */
	static void reset() {
		active = false;
		stopSound();
	}

	/** Whether a vision is playing, so the world's ambience can dip under it (15.3). */
	public static boolean dipping() {
		return active;
	}

	private static void stopSound() {
		if (bed != null) {
			Minecraft.getInstance().getSoundManager().stop(bed);
			bed = null;
		}
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!active) {
			return;
		}

		int duration = endTick - startTick;
		float t = QuestClient.ticks() - startTick + deltaTracker.getGameTimeDeltaPartialTick(true);
		float fade = Math.min(Mth.clamp(t / FADE_IN, 0f, 1f), Mth.clamp((duration - t) / FADE_OUT, 0f, 1f));

		if (fade <= 0f) {
			return;
		}

		boolean motion = Vfx.flashing();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float shimmer = motion ? 0.04f * Mth.sin(t * Mth.PI * 2f / 50f) : 0f;

		// The wash, deeper toward the top and bottom edges like light through old glass.
		graphics.fill(0, 0, width, height, alpha(WASH_ALPHA + shimmer, fade) | WASH);
		graphics.fillGradient(0, 0, width, height / 4, alpha(0.38f, fade) | EDGE, EDGE);
		graphics.fillGradient(0, height * 3 / 4, width, height, EDGE, alpha(0.42f, fade) | EDGE);

		float progress = Mth.clamp(t / Math.max(1f, duration - FADE_OUT), 0f, 1f);

		switch (variant) {
			case 0 -> drawStave(graphics, width, height, progress, fade);
			case 1 -> drawChoir(graphics, width, height, t, fade, motion);
			default -> drawCity(graphics, width, height, progress, fade);
		}

		drawLines(graphics, width, height, t, duration, fade);
	}

	/** The three subtitled lines, one after another, on a soft dark band so they read on the pale wash. */
	private static void drawLines(GuiGraphicsExtractor graphics, int width, int height, float t, int duration, float fade) {
		Minecraft mc = Minecraft.getInstance();
		int spacing = Math.max(20, (duration - LINE_START - FADE_OUT / 2) / LINES);
		int length = spacing - 2;
		int y = height / 2 - 4;

		for (int i = 0; i < LINES; i++) {
			float local = t - (LINE_START + i * spacing);

			if (local < 0 || local > length) {
				continue;
			}

			float lineAlpha = Math.min(Mth.clamp(local / LINE_FADE, 0f, 1f), Mth.clamp((length - local) / LINE_FADE, 0f, 1f)) * fade;

			if (lineAlpha < 0.04f) {
				continue;
			}

			Component line = Component.translatable("vision.oldrepublic." + VERSES[variant] + "." + (i + 1)).withStyle(ChatFormatting.ITALIC);
			int half = Math.min(width / 2 - 4, mc.font.width(line) / 2 + 24);
			graphics.fillGradient(width / 2 - half, y - 6, width / 2 + half, y + 2, 0, alpha(0.32f, lineAlpha) | INK);
			graphics.fillGradient(width / 2 - half, y + 2, width / 2 + half, y + 14, alpha(0.32f, lineAlpha) | INK, 0);
			graphics.centeredText(mc.font, line, width / 2, y + (int) ((1f - lineAlpha) * 3f), alpha(0.95f, lineAlpha) | LINE);
		}
	}

	/** The First Cantor's stave: five ruled lines across the middle, the notes written in as the vision runs. */
	private static void drawStave(GuiGraphicsExtractor graphics, int width, int height, float progress, float fade) {
		int left = width / 6;
		int right = width - width / 6;
		int top = height * 2 / 3;
		int ink = alpha(0.5f, fade) | INK;

		for (int i = 0; i < 5; i++) {
			graphics.fill(left, top + i * 5, right, top + i * 5 + 1, ink);
		}

		// Notes at seeded heights, written left to right.
		int notes = 14;
		int written = (int) (progress * notes + 0.5f);
		int step = (right - left) / (notes + 1);

		for (int n = 0; n < written; n++) {
			int x = left + step * (n + 1);
			int row = Math.floorMod(n * 7 + 3, 9);
			int y = top + row * 5 / 2 - 1;
			graphics.fill(x - 2, y, x + 2, y + 3, ink);
			graphics.fill(x + 1, y - 9, x + 2, y + 1, ink);
		}
	}

	/** The bound choir: a row of kneeling singers low on the screen, motes rising off them. */
	private static void drawChoir(GuiGraphicsExtractor graphics, int width, int height, float t, float fade, boolean motion) {
		int singers = 9;
		int ground = height * 4 / 5;
		int step = width / (singers + 1);
		int ink = alpha(0.62f, fade) | INK;

		for (int s = 0; s < singers; s++) {
			int x = step * (s + 1);
			// Kneeling: a low back, a bowed head, the mouth turned up to the stone.
			graphics.fill(x - 4, ground - 8, x + 4, ground, ink);
			graphics.fill(x - 3, ground - 13, x + 2, ground - 8, ink);
			graphics.fill(x - 2, ground - 16, x + 2, ground - 13, ink);

			if (motion) {
				for (int m = 0; m < 3; m++) {
					float rise = (t * 0.6f + s * 11f + m * 17f) % 40f;
					int my = ground - 18 - (int) rise;
					int mx = x + (int) (Mth.sin(rise * 0.2f + s) * 3f);
					graphics.fill(mx, my, mx + 1, my + 1, alpha(0.7f * (1f - rise / 40f), fade) | LINE);
				}
			}
		}
	}

	/** The quiet city: a skyline of pale roofs along the bottom, its lit windows going dark as the vision runs. */
	private static void drawCity(GuiGraphicsExtractor graphics, int width, int height, float progress, float fade) {
		int ground = height;
		int ink = alpha(0.6f, fade) | INK;
		int x = 0;
		int index = 0;

		while (x < width) {
			int w = 14 + Math.floorMod(index * 37 + 11, 18);
			int h = 18 + Math.floorMod(index * 53 + 7, 34);
			int top = ground - h;
			graphics.fill(x, top, x + w - 2, ground, ink);
			// The pale roof line.
			graphics.fill(x, top, x + w - 2, top + 1, alpha(0.55f, fade) | WASH);

			for (int wy = top + 4; wy < ground - 4; wy += 6) {
				for (int wx = x + 3; wx < x + w - 5; wx += 5) {
					// Each window has its moment to go dark, by its place in the street.
					float out = ((wx * 31 + wy * 17) % 100) / 100f;

					if (out > progress) {
						graphics.fill(wx, wy, wx + 2, wy + 2, alpha(0.75f, fade) | WINDOW);
					}
				}
			}

			x += w;
			index++;
		}
	}

	/** An ARGB alpha byte for {@code a} scaled by {@code fade}, in the high bits. */
	private static int alpha(float a, float fade) {
		return Mth.clamp((int) (255 * a * fade), 0, 255) << 24;
	}

	private VerseVision() {
	}
}

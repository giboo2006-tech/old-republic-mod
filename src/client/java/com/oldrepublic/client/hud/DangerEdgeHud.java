package com.oldrepublic.client.hud;

import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Danger Sense's "faint dust tick at that edge of the view" (spec 10.1): a small scatter of pale dust
 * specks at the screen edge toward a tell behind the player, drifting outward and fading within
 * about a second. No arrow, ring or flash: it reads as grit at the corner of the eye. The scatter is
 * seeded per prickle, so it holds still while it fades.
 */
public final class DangerEdgeHud {
	private static final int TICKS = 18;
	private static final int SPECKS = 8;
	/** Distance of the scatter's centre from the screen edge. */
	private static final int MARGIN = 16;
	/** Half the scatter's length along the edge. */
	private static final float SPREAD = 11f;
	/** Pale dust. */
	private static final int DUST = 0xDCD2BE;

	private static int start = Integer.MIN_VALUE;
	private static float yaw;
	private static long seed;

	/**
	 * Shows the dust tick toward a tell {@code relativeYaw} degrees from straight ahead (positive to
	 * the right, 180 straight behind, which draws at the bottom edge).
	 */
	public static void prickle(float relativeYaw) {
		start = QuestClient.ticks();
		yaw = relativeYaw;
		seed++;
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (start == Integer.MIN_VALUE || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float now = UiAnim.time(deltaTracker.getGameTimeDeltaPartialTick(true));
		float t = now - start;

		if (t > TICKS) {
			start = Integer.MIN_VALUE;
			return;
		}

		float alpha = UiAnim.envelope(now, start, TICKS, 2, 12);

		if (alpha <= 0.02f) {
			return;
		}

		double rad = Math.toRadians(yaw);
		float dx = (float) Math.sin(rad);
		float dy = (float) -Math.cos(rad);
		float halfW = g.guiWidth() / 2f - MARGIN;
		float halfH = g.guiHeight() / 2f - MARGIN;
		// The point where the direction leaves the margin box: the screen edge on that side.
		float reach = Math.min(halfW / Math.max(1.0e-3f, Math.abs(dx)), halfH / Math.max(1.0e-3f, Math.abs(dy)));
		float cx = g.guiWidth() / 2f + dx * reach;
		float cy = g.guiHeight() / 2f + dy * reach;
		RandomSource random = RandomSource.create(seed * 0x9E3779B97F4A7C15L);

		for (int i = 0; i < SPECKS; i++) {
			float along = (random.nextFloat() * 2f - 1f) * SPREAD;
			float depth = (random.nextFloat() - 0.5f) * 6f;
			float drift = t * (0.15f + random.nextFloat() * 0.25f);
			float x = cx - dy * along + dx * (depth + drift);
			float y = cy + dx * along + dy * (depth + drift);
			int size = random.nextFloat() < 0.3f ? 2 : 1;
			float a = alpha * (0.3f + 0.3f * random.nextFloat());
			int px = Mth.floor(x);
			int py = Mth.floor(y);
			g.fill(px, py, px + size, py + size, Ui.a(DUST, a));
		}
	}

	static void reset() {
		start = Integer.MIN_VALUE;
	}

	private DangerEdgeHud() {
	}
}

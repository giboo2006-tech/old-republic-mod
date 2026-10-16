package com.oldrepublic.client.identity;

import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Standing tab of the Force screen: alignment bar and shift log, faction reputation, path and
 * rank, the mentor card with trust, and the active lesson. The skills feature's Force screen hosts
 * one instance as its last tab and forwards drawing and clicks; the identity feature owns the
 * contents.
 *
 * <p>Phase 0 stub: draws a framed placeholder.
 */
public class StandingPanel {
	/** Draws the panel inside the given box (GUI coordinates). */
	public void render(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, float partialTick) {
		Ui.inset(graphics, x, y, w, h, 1f);
		Ui.header(graphics, font, Component.translatableWithFallback("screen.oldrepublic.standing.title", "Standing"), x + 6, y + 5, w - 12, UiAnim.time(partialTick));
		Ui.text(graphics, font, Component.translatableWithFallback("screen.oldrepublic.standing.placeholder", "Not yet charted."), x + 6, y + 22, UiColors.TEXT_DIM, 1f);
	}

	/** Handles a click forwarded by the host screen. Returns true if the panel used it. */
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return false;
	}
}

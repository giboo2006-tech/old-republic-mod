package com.oldrepublic.client.dialogue;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.PortraitFrame;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.dialogue.network.DialogueChoicePayload;
import com.oldrepublic.dialogue.network.DialogueClosePayload;
import com.oldrepublic.dialogue.network.DialogueNodePayload;
import com.oldrepublic.dialogue.network.DialogueNodePayload.OptionView;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * The dialogue screen: a holo panel across the lower third with the speaker's name (and portrait
 * when the dialogue has one), the spoken line typing out at 60 characters per second, and the
 * numbered replies. Space, Enter or a click shows the whole line at once; number keys 1 to 6 or a
 * click pick a reply. Disabled replies are shown dimmed with the reason under them. The game is
 * not paused. Escape closes the screen and tells the server, which ends the session with no choice
 * made.
 */
public final class DialogueScreen extends Screen {
	private static final float CHARS_PER_TICK = 3f;
	private static final int MAX_W = 420;
	private static final int PORTRAIT = 44;
	private static final int PAD = 8;
	private static final int LINE_H = 10;
	private static final int OPTION_H = 12;
	private static final int REASON_H = 10;
	private static final int MAX_LINE_ROWS = 6;
	private static final int BOTTOM_MARGIN = 10;

	/** One reply as laid out on screen. */
	private record Row(OptionView option, int number, Component text, @Nullable Component reason, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private DialogueNodePayload node;
	private Component name = Component.empty();
	private List<String> lineRows = List.of();
	private int lineChars;
	private float revealStart;
	private boolean revealed;
	/** A reply was sent; input waits for the next node or the end of the session. */
	private boolean waiting;
	private boolean endedByServer;
	private final List<Row> rows = new ArrayList<>();
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int textX;
	private int textW;

	DialogueScreen(DialogueNodePayload node) {
		super(Component.translatable("screen.oldrepublic.dialogue"));
		this.node = node;
		this.revealStart = UiAnim.time(0f);
	}

	int session() {
		return node.session();
	}

	/** Shows the next node of the same session in place. */
	void show(DialogueNodePayload next) {
		boolean sameLine = next.line().equals(node.line()) && next.lineArgs().equals(node.lineArgs());
		node = next;
		waiting = false;

		if (!sameLine) {
			revealed = false;
			revealStart = UiAnim.time(0f);
		}

		layout();
	}

	/** The server ended the session; closing must not send a close back. */
	void endedByServer() {
		endedByServer = true;
	}

	@Override
	protected void init() {
		layout();
	}

	private void layout() {
		name = speakerName();
		boolean portrait = !node.portrait().isEmpty();
		panelW = Math.min(width - 16, MAX_W);
		panelX = (width - panelW) / 2;
		textX = panelX + PAD + (portrait ? PORTRAIT + PAD : 0);
		textW = panelX + panelW - PAD - textX;

		Component line = DialogueClient.text(node.line(), node.lineArgs());
		lineRows = Ui.wrap(font, line.getString(), textW, MAX_LINE_ROWS);
		lineChars = lineRows.stream().mapToInt(String::length).sum();

		int optionsH = 0;

		for (OptionView option : node.options()) {
			optionsH += OPTION_H + (option.enabled() || option.reason().isEmpty() ? 0 : REASON_H);
		}

		int textH = 14 + lineRows.size() * LINE_H + 6 + optionsH;
		panelH = PAD * 2 + Math.max(textH, portrait ? PORTRAIT + 4 : 0);
		panelY = height - panelH - BOTTOM_MARGIN;

		rows.clear();
		int y = panelY + PAD + 14 + lineRows.size() * LINE_H + 6;
		int number = 1;

		for (OptionView option : node.options()) {
			Component reason = option.enabled() || option.reason().isEmpty() ? null : DialogueClient.reason(option.reason());
			int h = OPTION_H + (reason == null ? 0 : REASON_H);
			rows.add(new Row(option, number++, DialogueClient.text(option.text(), option.args()), reason, textX - 2, y, textW + 4, h));
			y += h;
		}
	}

	private Component speakerName() {
		if (!node.nameKey().isEmpty()) {
			return Component.translatable(node.nameKey());
		}

		Entity speaker = minecraft.level == null || node.speakerId() < 0 ? null : minecraft.level.getEntity(node.speakerId());
		return speaker == null ? Component.empty() : speaker.getDisplayName();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	// API-CHECK: Screen#extractBackground(GuiGraphicsExtractor, int, int, float) is the background pass
	// (as overridden by LightsaberWorkbenchScreen); replacing it skips the menu blur so the world stays visible.
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, height * 2 / 3 - 30, width, height, 0x00000000, 0xA0000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float time = UiAnim.time(partialTick);
		float open = UiAnim.easeOutCubic(Math.min(1f, (time - revealStart) / 6f));
		int lift = revealed || waiting ? 0 : Math.round((1f - open) * 6f);

		graphics.pose().pushMatrix();
		graphics.pose().translate(0f, lift);
		Ui.panel(graphics, panelX, panelY, panelW, panelH);
		Ui.scanlines(graphics, panelX + 3, panelY + 3, panelW - 6, panelH - 6, Ui.a(UiColors.HOLO, 0.03f * UiAnim.flicker(time)), time);

		if (!node.portrait().isEmpty()) {
			PortraitFrame.draw(graphics, panelX + PAD, panelY + PAD, PORTRAIT, Identifier.tryParse(node.portrait()), node.accent(), time, 1f);
		}

		drawName(graphics, time);
		boolean typed = drawLine(graphics, time);

		if (typed) {
			drawOptions(graphics, mouseX, mouseY - lift, time);
		}

		graphics.pose().popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawName(GuiGraphicsExtractor graphics, float time) {
		int y = panelY + PAD;
		Ui.text(graphics, font, name, textX, y, UiColors.TEXT_WARM, 1f);
		int accent = node.accent() != 0 ? node.accent() : UiColors.HOLO_DIM;
		int ruleW = Math.min(textW, font.width(name) + 24);
		graphics.fill(textX, y + 10, textX + ruleW, y + 11, Ui.a(accent, 0.9f));
		graphics.fill(textX + ruleW, y + 10, textX + Math.min(textW, ruleW + 30), y + 11, Ui.a(accent, 0.25f));
	}

	/** Types the line out. Returns true once it is fully shown. */
	private boolean drawLine(GuiGraphicsExtractor graphics, float time) {
		int shown = revealed ? lineChars : (int) ((time - revealStart) * CHARS_PER_TICK);

		if (shown >= lineChars) {
			revealed = true;
		}

		int y = panelY + PAD + 14;
		int left = shown;

		for (String row : lineRows) {
			if (left <= 0) {
				break;
			}

			String part = row.length() <= left ? row : row.substring(0, left);
			Ui.text(graphics, font, part, textX, y, UiColors.TEXT, 1f);
			left -= row.length();

			if (!revealed && left <= 0 && UiAnim.pulse(time, 10f) > 0.5f) {
				int cx = textX + font.width(part) + 1;
				graphics.fill(cx, y, cx + 4, y + 8, Ui.a(UiColors.HOLO, 0.7f));
			}

			y += LINE_H;
		}

		return revealed;
	}

	private void drawOptions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float time) {
		float alpha = waiting ? 0.45f : 1f;

		for (Row row : rows) {
			boolean enabled = row.option().enabled();
			boolean hover = enabled && !waiting && row.contains(mouseX, mouseY);

			if (hover) {
				graphics.fill(row.x(), row.y() - 1, row.x() + row.w(), row.y() + OPTION_H - 2, Ui.a(UiColors.HOLO_DEEP, 0.55f));
				graphics.fill(row.x(), row.y() - 1, row.x() + 1, row.y() + OPTION_H - 2, Ui.a(UiColors.HOLO, 0.9f * UiAnim.flicker(time)));
			}

			int numberColor = enabled ? UiColors.BRASS_LIGHT : UiColors.TEXT_DIM;
			int textColor = !enabled ? UiColors.TEXT_DIM : hover ? UiColors.HOLO_BRIGHT : UiColors.TEXT;
			String number = row.number() + ".";
			Ui.text(graphics, font, number, row.x() + 4, row.y() + 1, numberColor, alpha);
			int tx = row.x() + 4 + font.width("0.") + 4;
			Ui.text(graphics, font, Ui.fit(font, row.text().getString(), row.x() + row.w() - tx - 2), tx, row.y() + 1, textColor, alpha);

			if (row.reason() != null) {
				Ui.text(graphics, font, Ui.fit(font, row.reason().getString(), row.x() + row.w() - tx - 2), tx, row.y() + OPTION_H, UiColors.AMBER_DIM, alpha);
			}
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}

		if (!revealed) {
			revealed = true;
			return true;
		}

		for (Row row : rows) {
			if (row.contains(event.x(), event.y())) {
				choose(row);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();

		if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN) {
			revealed = true;
			return true;
		}

		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_6) {
			if (!revealed) {
				revealed = true;
			} else if (key - InputConstants.KEY_1 < rows.size()) {
				choose(rows.get(key - InputConstants.KEY_1));
			}

			return true;
		}

		return super.keyPressed(event);
	}

	private void choose(Row row) {
		if (waiting || !row.option().enabled()) {
			return;
		}

		waiting = true;
		QuestSounds.ui(ModSounds.DIALOGUE_SELECT, 1f, 0.55f);
		ClientPlayNetworking.send(new DialogueChoicePayload(node.session(), row.option().index()));
	}

	// API-CHECK: Screen#removed() runs whenever this screen is replaced or closed.
	@Override
	public void removed() {
		if (!endedByServer && ClientPlayNetworking.canSend(DialogueClosePayload.TYPE)) {
			ClientPlayNetworking.send(new DialogueClosePayload(node.session()));
		}
	}
}

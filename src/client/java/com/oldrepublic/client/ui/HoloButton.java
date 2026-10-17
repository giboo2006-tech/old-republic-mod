package com.oldrepublic.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A bevelled durasteel button with a holo indicator strip: normal, hover (cyan edge and a soft
 * glow that eases in), pressed (sunk for a few ticks after a click) and disabled. It is a real
 * {@link Button}, so keyboard focus, narration, the vanilla click sound and the client game tests'
 * "click the button labelled X" all keep working.
 */
public class HoloButton extends Button {
	private static final int PRESS_TICKS = 4;

	private final boolean primary;
	private float hover;
	private float lastTime = Float.NaN;
	private float pressedAt = Float.NEGATIVE_INFINITY;

	/**
	 * @param primary draws brass end caps marking the screen's main action
	 */
	public HoloButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.primary = primary;
	}

	public HoloButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		this(x, y, width, height, message, onPress, false);
	}

	/** Fluent tooltip setter for one-line construction. */
	public HoloButton withTooltip(Component text) {
		setTooltip(Tooltip.create(text));
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		pressedAt = UiAnim.time(0f);
		super.onPress(input);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float time = UiAnim.time(partialTick);
		float dt = Float.isNaN(lastTime) ? 0f : Math.min(5f, Math.max(0f, time - lastTime));
		lastTime = time;
		boolean hot = active && isHoveredOrFocused();
		hover = UiAnim.approach(hover, hot ? 1f : 0f, 0.45f, dt);
		boolean pressed = active && time - pressedAt < PRESS_TICKS;

		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		Identifier sprite = !active ? Ui.BUTTON_DISABLED : pressed ? Ui.BUTTON_PRESSED : hover > 0.5f ? Ui.BUTTON_HOVER : Ui.BUTTON;
		Ui.sprite(graphics, sprite, x, y, w, h, alpha);

		if (hover > 0.02f) {
			// Soft outer glow: two faint cyan rings that ease in with the hover.
			float glow = hover * alpha;
			graphics.fill(x + 2, y - 1, x + w - 2, y, Ui.a(UiColors.HOLO, 0.35f * glow));
			graphics.fill(x + 2, y + h, x + w - 2, y + h + 1, Ui.a(UiColors.HOLO, 0.35f * glow));
			graphics.fill(x - 1, y + 2, x, y + h - 2, Ui.a(UiColors.HOLO, 0.35f * glow));
			graphics.fill(x + w, y + 2, x + w + 1, y + h - 2, Ui.a(UiColors.HOLO, 0.35f * glow));
			// A slow band of light sweeping across the face.
			int span = w + 16;
			int band = x - 8 + (int) ((time % 40f) / 40f * span);
			int b0 = Math.max(x + 3, band);
			int b1 = Math.min(x + w - 3, band + 6);

			if (b1 > b0) {
				graphics.fill(b0, y + 2, b1, y + h - 3, Ui.a(0xFFFFFFFF, 0.08f * glow));
			}
		}

		if (primary && active) {
			graphics.fill(x + 1, y + 4, x + 2, y + h - 4, Ui.a(UiColors.BRASS_LIGHT, alpha));
			graphics.fill(x + w - 2, y + 4, x + w - 1, y + h - 4, Ui.a(UiColors.BRASS_LIGHT, alpha));
		}

		Font font = Minecraft.getInstance().font;
		int color = !active ? UiColors.TEXT_DIM : hover > 0.5f ? UiColors.HOLO_BRIGHT : UiColors.TEXT;
		int ty = y + (h - 8) / 2 + (pressed ? 1 : 0);
		graphics.centeredText(font, getMessage(), x + w / 2, ty, Ui.a(color, alpha));
	}
}

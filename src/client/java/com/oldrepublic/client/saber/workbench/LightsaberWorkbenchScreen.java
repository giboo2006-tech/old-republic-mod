package com.oldrepublic.client.saber.workbench;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltPartItem;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Workbench screen, a framed durasteel panel. Left: the four part sockets (ghost silhouettes when
 * empty, conduits lighting up toward the saber socket as parts go in). Centre: a holo-projector
 * preview of the assembled hilt, each section labelled, with the blade drawn in the chosen colour
 * once a crystal is in. Right: the server's exact stats as bars, each placed within the range of
 * every possible build and marked against the saber you carry, then the status line. Bottom right:
 * colour swatches and the Construct / Disassemble buttons. Every action is a menu button click
 * validated on the server; slot positions and button ids belong to the menu and are unchanged.
 *
 * <p>Milestone 4 (spec 5.6, 5.7): beside the bus, the second-crystal socket (staff mode, where the
 * pommel socket shows locked and a lit STAFF plate names the mode) and the lens socket (Artifice 2
 * or a lens carried), which glints when a lens seats ({@link #onLensFit}). The projector shows a
 * staff as a two-ended hilt with a blade each way.
 */
public class LightsaberWorkbenchScreen extends AbstractContainerScreen<LightsaberWorkbenchMenu> {
	private static final int WIDTH = 272;
	private static final int HEIGHT = 204;

	private static final int SWATCH_X = 204;
	private static final int SWATCH_Y = 123;
	private static final int SWATCH_SIZE = 10;
	private static final int SWATCH_STEP = 12;
	private static final int CONSOLE_X = 202;
	private static final int CONSOLE_W = 62;

	private static final int PREVIEW_X = 61;
	private static final int PREVIEW_Y = 18;
	private static final int PREVIEW_W = 76;
	private static final int PREVIEW_H = 94;
	/** Hilt column: item icons are drawn at this x, stacked emitter / grip / pommel. */
	private static final int HILT_X = 75;
	private static final int EMITTER_Y = 58;
	private static final int GRIP_Y = 71;
	private static final int POMMEL_Y = 84;
	private static final int BLADE_TOP = 23;

	private static final int STATS_X = 143;
	private static final int STATS_RIGHT = 260;
	private static final int STATS_Y = 21;
	private static final int STAT_LABEL_W = 38;
	private static final int STAT_VALUE_W = 30;

	/** Default balance for the comparison bars (positions only; the numbers shown are the server's). */
	private static final SaberBalance DEFAULT_BALANCE = new SaberBalance();
	private static final float[] STAT_MIN = new float[5];
	private static final float[] STAT_MAX = new float[5];

	static {
		for (int i = 0; i < 5; i++) {
			STAT_MIN[i] = Float.MAX_VALUE;
			STAT_MAX[i] = -Float.MAX_VALUE;
		}

		for (Emitter e : Emitter.values()) {
			for (Grip g : Grip.values()) {
				for (Pommel p : Pommel.values()) {
					widen(values(SaberStats.of(e, g, p, DEFAULT_BALANCE)));
				}
			}

			widen(values(SaberStats.staff(e, Grip.STAFF, true, DEFAULT_BALANCE)));
			widen(values(SaberStats.staff(e, Grip.STAFF, false, DEFAULT_BALANCE)));
		}
	}

	private static void widen(float[] v) {
		for (int i = 0; i < 5; i++) {
			STAT_MIN[i] = Math.min(STAT_MIN[i], v[i]);
			STAT_MAX[i] = Math.max(STAT_MAX[i], v[i]);
		}
	}

	/** Staff layout of the projector (5.6): upper emitter, grip, lower emitter, with a blade each way. */
	private static final int STAFF_EMITTER_Y = 41;
	private static final int STAFF_GRIP_Y = 54;
	private static final int STAFF_LOWER_Y = 67;
	private static final int STAFF_BLADE_BOTTOM = 100;
	/** The lens glint after a lens seats ({@link #onLensFit}), in ticks. */
	private static final int LENS_GLINT_TICKS = 16;
	private static long lensGlintAt = Long.MIN_VALUE;
	private static int lensGlintVariant;

	/** The {@code lens_fit} effect (from the saber effects): the lens socket glints. */
	public static void onLensFit(long gameTime, int variant) {
		lensGlintAt = gameTime;
		lensGlintVariant = variant;
	}

	private static final String[] STAT_KEYS = {"damage", "speed", "reach", "stamina", "parry"};

	private HoloButton construct;
	private HoloButton disassemble;
	private float bladeOut;
	private float secondOut;
	private float lastTime = Float.NaN;

	public LightsaberWorkbenchScreen(LightsaberWorkbenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.titleLabelX = 10;
		this.titleLabelY = 6;
		this.inventoryLabelX = 34;
		this.inventoryLabelY = 107;
	}

	@Override
	protected void init() {
		super.init();
		construct = addRenderableWidget(new HoloButton(leftPos + CONSOLE_X, topPos + 156, CONSOLE_W, 16,
				Component.translatable("container.oldrepublic.lightsaber_workbench.construct"),
				b -> click(LightsaberWorkbenchMenu.BUTTON_CONSTRUCT), true));
		disassemble = addRenderableWidget(new HoloButton(leftPos + CONSOLE_X, topPos + 176, CONSOLE_W, 16,
				Component.translatable("container.oldrepublic.lightsaber_workbench.disassemble"),
				b -> click(LightsaberWorkbenchMenu.BUTTON_DISASSEMBLE)));
		updateButtons();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtons();
	}

	private void updateButtons() {
		Status status = menu.status();
		construct.active = status == Status.READY;
		disassemble.active = status == Status.CAN_DISASSEMBLE;
	}

	private void click(int id) {
		if (minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			BladeColor color = swatchAt(event.x(), event.y());

			if (color != null) {
				AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
				click(color.ordinal());
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	private @Nullable BladeColor swatchAt(double mouseX, double mouseY) {
		for (BladeColor color : BladeColor.values()) {
			int x = leftPos + swatchX(color);
			int y = topPos + swatchY(color);

			if (mouseX >= x && mouseX < x + SWATCH_SIZE && mouseY >= y && mouseY < y + SWATCH_SIZE) {
				return color;
			}
		}

		return null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < SaberAssembly.SIZE) {
			graphics.setTooltipForNextFrame(font, Component.translatable("container.oldrepublic.lightsaber_workbench.socket." + socketName(hoveredSlot.index)), mouseX, mouseY);
		}

		BladeColor swatch = swatchAt(mouseX, mouseY);

		if (swatch != null) {
			graphics.setTooltipForNextFrame(font, swatch.displayName(), mouseX, mouseY);
		}
	}

	// ---------------------------------------------------------------- background

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		float time = UiAnim.time(delta);
		float dt = Float.isNaN(lastTime) ? 0f : Mth.clamp(time - lastTime, 0f, 5f);
		lastTime = time;
		int x0 = leftPos;
		int y0 = topPos;

		Ui.panel(graphics, x0, y0, imageWidth, imageHeight);
		drawSockets(graphics, x0, y0, time);
		drawPreview(graphics, x0, y0, time, dt);
		Ui.inset(graphics, x0 + STATS_X - 4, y0 + STATS_Y - 4, STATS_RIGHT - STATS_X + 8, 93, 1f);

		// Inventory slot recesses (rows at y 118, 136, 154 and the hotbar at 176).
		for (int row = 0; row < 4; row++) {
			int sy = y0 + 117 + row * 18 + (row == 3 ? 4 : 0);

			for (int col = 0; col < 9; col++) {
				Ui.sprite(graphics, Ui.SLOT, x0 + 33 + col * 18, sy, 18, 18, 1f);
			}
		}

		drawSwatches(graphics, x0, y0, mouseX, mouseY, time);
	}

	private void drawSockets(GuiGraphicsExtractor g, int x0, int y0, float time) {
		Identifier[] hints = {Ui.sprite("hint_crystal"), Ui.sprite("hint_emitter"), Ui.sprite("hint_grip"), Ui.sprite("hint_pommel")};
		boolean ready = menu.status() == Status.READY;
		int busX = x0 + 33;

		for (int i = 0; i < 4; i++) {
			int sx = x0 + 10;
			int sy = y0 + 20 + i * 22;
			boolean filled = menu.getSlot(i).hasItem();
			Ui.sprite(g, Ui.SOCKET, sx - 2, sy - 2, 20, 20, 1f);

			if (!filled) {
				Ui.sprite(g, hints[i], sx, sy, 16, 16, Ui.a(UiColors.HOLO_DIM, 0.55f));
			}

			// Conduit stub from the socket to the bus.
			int cy = sy + 8;
			g.fill(sx + 18, cy, busX, cy + 1, filled ? Ui.a(UiColors.HOLO, 0.8f) : UiColors.STEEL);
		}

		// The bus down to the saber socket, with a pulse running along it when the bench is ready.
		int top = y0 + 28;
		int bottom = y0 + 94 + 1;
		g.fill(busX, top, busX + 1, bottom, ready ? Ui.a(UiColors.HOLO, 0.8f) : UiColors.STEEL);
		int saberY = y0 + 53 + 8;
		g.fill(busX, saberY, x0 + 38, saberY + 1, ready ? Ui.a(UiColors.HOLO, 0.8f) : UiColors.STEEL);

		if (ready) {
			int travel = bottom - top;
			int py = top + (int) ((time * 1.5f) % travel);
			g.fill(busX - 1, py, busX + 2, py + 2, Ui.a(UiColors.HOLO_BRIGHT, UiAnim.flicker(time)));
		}

		// Saber socket, framed in brass as the bench's output.
		int sx = x0 + 40;
		int sy = y0 + 53;
		g.fill(sx - 3, sy - 3, sx + 19, sy + 19, Ui.a(UiColors.BRASS, 0.9f));
		Ui.sprite(g, Ui.SOCKET, sx - 2, sy - 2, 20, 20, 1f);

		if (!menu.getSlot(SaberAssembly.SABER).hasItem()) {
			Ui.sprite(g, Ui.sprite("hint_saber"), sx, sy, 16, 16, Ui.a(UiColors.HOLO_DIM, 0.55f));
		}

		drawM4Sockets(g, x0, y0, busX, time);
	}

	/**
	 * The second-crystal and lens sockets right of the bus, and the locked pommel socket in staff
	 * mode. Hidden slots draw nothing (the menu makes them inactive).
	 */
	private void drawM4Sockets(GuiGraphicsExtractor g, int x0, int y0, int busX, float time) {
		boolean staff = menu.isStaffMode();

		if (staff && !menu.getSlot(SaberAssembly.POMMEL).hasItem()) {
			// The pommel socket, shuttered: a staff has none.
			int px = x0 + 10;
			int py = y0 + 20 + 3 * 22;
			g.fill(px - 1, py - 1, px + 17, py + 17, Ui.a(UiColors.STEEL, 0.95f));

			for (int i = 0; i < 4; i++) {
				g.fill(px + 1, py + 2 + i * 4, px + 15, py + 3 + i * 4, Ui.a(UiColors.OUTLINE, 0.9f));
			}
		}

		for (int slot : new int[] {SaberAssembly.SECOND, SaberAssembly.LENS}) {
			if (!menu.benchSlotActive(slot)) {
				continue;
			}

			Slot s = menu.getSlot(slot);
			int sx = x0 + s.x;
			int sy = y0 + s.y;
			boolean filled = s.hasItem();
			Ui.sprite(g, Ui.SOCKET, sx - 2, sy - 2, 20, 20, 1f);

			if (!filled) {
				Identifier hint = slot == SaberAssembly.LENS ? OldRepublicMod.id("bench/hint_lens") : Ui.sprite("hint_crystal");
				Ui.sprite(g, hint, sx, sy, 16, 16, Ui.a(UiColors.HOLO_DIM, 0.55f));
			}

			// A short conduit from the bus.
			int cy = sy + 8;
			g.fill(busX + 1, cy, sx - 2, cy + 1, filled ? Ui.a(UiColors.HOLO, 0.8f) : UiColors.STEEL);

			if (slot == SaberAssembly.LENS && minecraft.level != null) {
				float age = minecraft.level.getGameTime() - lensGlintAt;

				if (age >= 0f && age < LENS_GLINT_TICKS) {
					// The lens seats: a brass ring that opens out and fades.
					float t = age / LENS_GLINT_TICKS;
					int grow = Math.round(t * 4f);
					int glint = lensGlintVariant == 2 ? UiColors.HOLO_BRIGHT : UiColors.BRASS_LIGHT;
					g.outline(sx - 2 - grow, sy - 2 - grow, 20 + grow * 2, 20 + grow * 2, Ui.a(glint, 1f - t));
				}
			}
		}

		if (staff) {
			// The lit STAFF plate over the saber socket.
			String label = Component.translatable("container.oldrepublic.lightsaber_workbench.staff").getString();
			int w = font.width(label) + 6;
			int lx = x0 + 48 - w / 2;
			int ly = y0 + 40;
			g.fill(lx, ly, lx + w, ly + 9, Ui.a(UiColors.BRASS, 0.85f + 0.15f * UiAnim.flicker(time)));
			g.text(font, label, lx + 3, ly + 1, UiColors.OUTLINE, false);
		}
	}

	/** Holo projector: the hilt parts stacked as they will be assembled, the blade once a crystal is in. */
	private void drawPreview(GuiGraphicsExtractor g, int x0, int y0, float time, float dt) {
		int px = x0 + PREVIEW_X;
		int py = y0 + PREVIEW_Y;
		Ui.inset(g, px, py, PREVIEW_W, PREVIEW_H, 1f);
		int cx = x0 + HILT_X + 8;

		// Projector base and its cone of light.
		int baseY = py + PREVIEW_H - 11;
		for (int i = 0; i < 3; i++) {
			int half = 8 + i * 5;
			g.fillGradient(cx - half, py + 30 + i * 8, cx + half, baseY, 0x00000000, Ui.a(UiColors.HOLO, 0.05f));
		}

		Ui.sprite(g, Ui.PROJECTOR, cx - 20, baseY, 40, 8, 1f);
		Ui.scanlines(g, px + 2, py + 2, PREVIEW_W - 4, PREVIEW_H - 4, 0x0CFFFFFF, time);

		ItemStack saber = menu.getSlot(SaberAssembly.SABER).getItem();
		ItemStack emitter;
		ItemStack grip;
		ItemStack pommel;
		boolean crystal;

		if (menu.isStaffMode()) {
			drawStaffPreview(g, x0, y0, cx, saber, time, dt);
			return;
		}

		if (!saber.isEmpty()) {
			SaberConfig config = SaberComponents.config(saber);
			emitter = new ItemStack(SaberItems.partItem(config.emitter()));
			grip = new ItemStack(SaberItems.partItem(config.grip()));
			pommel = new ItemStack(SaberItems.partItem(config.pommel()));
			crystal = true;
		} else {
			emitter = menu.getSlot(SaberAssembly.EMITTER).getItem();
			grip = menu.getSlot(SaberAssembly.GRIP).getItem();
			pommel = menu.getSlot(SaberAssembly.POMMEL).getItem();
			crystal = menu.getSlot(SaberAssembly.CRYSTAL).hasItem();
		}

		// Pommel first so the sections above overlap it, as on the real hilt.
		part(g, pommel, "hint_pommel", x0 + HILT_X, y0 + POMMEL_Y);
		part(g, grip, "hint_grip", x0 + HILT_X, y0 + GRIP_Y);
		part(g, emitter, "hint_emitter", x0 + HILT_X, y0 + EMITTER_Y);

		bladeOut = UiAnim.approach(bladeOut, crystal ? 1f : 0f, 0.3f, dt);
		BladeColor color = BladeColor.byIndex(menu.data(LightsaberWorkbenchMenu.DATA_COLOR));

		if (bladeOut > 0.02f) {
			drawBlade(g, cx, y0 + EMITTER_Y + 2, y0 + BLADE_TOP, color, UiAnim.easeOutCubic(bladeOut), time);
		}
	}

	/** A staff on the projector: one emitter at each end of the grip, a blade from each end. */
	private void drawStaffPreview(GuiGraphicsExtractor g, int x0, int y0, int cx, ItemStack saber, float time, float dt) {
		ItemStack emitter;
		ItemStack grip;
		boolean crystal;
		boolean second;

		if (!saber.isEmpty()) {
			SaberConfig config = SaberComponents.config(saber);
			emitter = new ItemStack(SaberItems.partItem(config.emitter()));
			grip = new ItemStack(SaberItems.partItem(config.grip()));
			crystal = true;
			second = config.second().isPresent();
		} else {
			emitter = menu.getSlot(SaberAssembly.EMITTER).getItem();
			grip = menu.getSlot(SaberAssembly.GRIP).getItem();
			crystal = menu.getSlot(SaberAssembly.CRYSTAL).hasItem();
			second = menu.getSlot(SaberAssembly.SECOND).hasItem();
		}

		part(g, emitter, "hint_emitter", x0 + HILT_X, y0 + STAFF_LOWER_Y);
		part(g, grip, "hint_grip", x0 + HILT_X, y0 + STAFF_GRIP_Y);
		part(g, emitter, "hint_emitter", x0 + HILT_X, y0 + STAFF_EMITTER_Y);

		bladeOut = UiAnim.approach(bladeOut, crystal ? 1f : 0f, 0.3f, dt);
		secondOut = UiAnim.approach(secondOut, second ? 1f : 0f, 0.3f, dt);
		BladeColor color = BladeColor.byIndex(menu.data(LightsaberWorkbenchMenu.DATA_COLOR));

		if (bladeOut > 0.02f) {
			drawBlade(g, cx, y0 + STAFF_EMITTER_Y + 2, y0 + BLADE_TOP, color, UiAnim.easeOutCubic(bladeOut), time);
		}

		if (secondOut > 0.02f) {
			drawBladeDown(g, cx, y0 + STAFF_LOWER_Y + 14, y0 + STAFF_BLADE_BOTTOM, color, UiAnim.easeOutCubic(secondOut), time);
		}
	}

	/** {@link #drawBlade} pointing down: from {@code baseY} toward {@code tipY} below it. */
	private static void drawBladeDown(GuiGraphicsExtractor g, int cx, int baseY, int tipY, BladeColor color, float out, float time) {
		int bottom = baseY + Math.round((tipY - baseY) * out);
		int rgb = 0xFF000000 | color.rgb();
		float hum = UiAnim.flicker(time * 2f + 1.3f);
		g.fill(cx - 3, baseY, cx + 3, bottom - 2, Ui.a(rgb, 0.22f * hum));
		g.fill(cx - 2, baseY, cx + 2, bottom - 1, Ui.a(rgb, 0.55f * hum));
		g.fill(cx - 1, baseY, cx + 1, bottom, ARGB.srgbLerp(0.75f, rgb, 0xFFFFFFFF));
		g.fill(cx - 4, baseY - 1, cx + 4, baseY + 1, Ui.a(rgb, 0.35f * hum));
	}

	private static void part(GuiGraphicsExtractor g, ItemStack stack, String hint, int x, int y) {
		if (!stack.isEmpty()) {
			g.item(stack, x, y);
		} else {
			Ui.sprite(g, Ui.sprite(hint), x, y, 16, 16, Ui.a(UiColors.HOLO, 0.3f));
		}
	}

	/** A blade from {@code baseY} up toward {@code tipY}: soft coloured glow around a white core. */
	private static void drawBlade(GuiGraphicsExtractor g, int cx, int baseY, int tipY, BladeColor color, float out, float time) {
		int top = baseY - Math.round((baseY - tipY) * out);
		int rgb = 0xFF000000 | color.rgb();
		float hum = UiAnim.flicker(time * 2f);
		g.fill(cx - 3, top + 2, cx + 3, baseY, Ui.a(rgb, 0.22f * hum));
		g.fill(cx - 2, top + 1, cx + 2, baseY, Ui.a(rgb, 0.55f * hum));
		g.fill(cx - 1, top, cx + 1, baseY, ARGB.srgbLerp(0.75f, rgb, 0xFFFFFFFF));
		g.fill(cx - 4, baseY - 1, cx + 4, baseY + 1, Ui.a(rgb, 0.35f * hum));
	}

	private void drawSwatches(GuiGraphicsExtractor g, int x0, int y0, int mouseX, int mouseY, float time) {
		int selected = menu.data(LightsaberWorkbenchMenu.DATA_COLOR);
		BladeColor hover = swatchAt(mouseX, mouseY);

		for (BladeColor color : BladeColor.values()) {
			int x = x0 + swatchX(color);
			int y = y0 + swatchY(color);
			boolean isSelected = color.ordinal() == selected;
			int rgb = 0xFF000000 | color.rgb();

			g.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, UiColors.OUTLINE);
			// A small crystal of the colour: lit top, deep bottom.
			g.fillGradient(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, ARGB.srgbLerp(0.35f, rgb, 0xFFFFFFFF), ARGB.srgbLerp(0.45f, rgb, 0xFF000000));
			g.fill(x + 4, y + 2, x + 6, y + SWATCH_SIZE - 2, Ui.a(0xFFFFFFFF, 0.55f));

			if (isSelected) {
				// Selected ring: brass with a soft glow in the blade's colour.
				g.outline(x - 3, y - 3, SWATCH_SIZE + 6, SWATCH_SIZE + 6, Ui.a(rgb, 0.45f * UiAnim.flicker(time)));
				g.outline(x - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, UiColors.BRASS_LIGHT);
			} else if (color == hover) {
				g.outline(x - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, Ui.a(UiColors.HOLO, 0.8f));
			}
		}
	}

	// ---------------------------------------------------------------- labels (relative to the panel)

	// API-CHECK: label coordinates are relative to leftPos/topPos, as renderLabels was.
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Ui.header(graphics, font, title, titleLabelX, titleLabelY, imageWidth - 2 * titleLabelX, UiAnim.time(0f));
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiColors.TEXT_MUTED, true);
		graphics.text(font, Component.translatable("container.oldrepublic.lightsaber_workbench.colour"), SWATCH_X, 112, UiColors.TEXT_MUTED, true);
		drawHiltLabels(graphics);

		int y = STATS_Y;

		if (menu.data(LightsaberWorkbenchMenu.DATA_HAS_PREVIEW) != 0) {
			float[] bench = benchValues();
			float[] carried = carriedValues();
			String[] text = {
					String.format(Locale.ROOT, "%.2f", menu.data(LightsaberWorkbenchMenu.DATA_DAMAGE_X100) / 100f),
					String.format(Locale.ROOT, "%.2f", menu.data(LightsaberWorkbenchMenu.DATA_SPEED_X100) / 100f),
					String.format(Locale.ROOT, "+%.2f", menu.data(LightsaberWorkbenchMenu.DATA_REACH_X100) / 100f),
					String.valueOf(menu.data(LightsaberWorkbenchMenu.DATA_STAMINA)),
					String.format(Locale.ROOT, "%.1f", menu.data(LightsaberWorkbenchMenu.DATA_PARRY_X10) / 10f)};

			for (int i = 0; i < STAT_KEYS.length; i++) {
				statRow(graphics, y, i, text[i], bench == null ? Float.NaN : bench[i], carried == null ? Float.NaN : carried[i]);
				y += 9;
			}

			if (carried != null) {
				graphics.fill(STATS_X, y + 3, STATS_X + 1, y + 7, UiColors.TEXT);
				graphics.text(font, Ui.fit(font, Component.translatable("container.oldrepublic.lightsaber_workbench.compare").getString(), STATS_RIGHT - STATS_X - 5),
						STATS_X + 4, y + 1, UiColors.TEXT_DIM, false);
			}

			y += 11;
		} else {
			for (String line : Ui.wrap(font, Component.translatable("container.oldrepublic.lightsaber_workbench.no_preview").getString(), STATS_RIGHT - STATS_X, 4)) {
				graphics.text(font, line, STATS_X, y, UiColors.TEXT_DIM, false);
				y += 10;
			}

			y += 6;
		}

		Status status = menu.status();
		int statusColor = switch (status) {
			case READY, CAN_DISASSEMBLE -> UiColors.OK;
			case NOT_YOUR_CRYSTAL, NOT_YOUR_SABER, SLOTS_NOT_EMPTY, STAFF_HAS_POMMEL, SECOND_NEEDS_STAFF -> UiColors.AMBER;
			default -> UiColors.TEXT_MUTED;
		};
		int sy = STATS_Y + 60;
		graphics.fill(STATS_X - 4, sy - 3, STATS_RIGHT + 4, sy - 2, Ui.a(UiColors.HOLO_DIM, 0.6f));
		graphics.fill(STATS_X, sy + 3, STATS_X + 3, sy + 6, statusColor);

		for (String line : Ui.wrap(font, Component.translatable(status.translationKey()).getString(), STATS_RIGHT - STATS_X - 6, 3)) {
			graphics.text(font, line, STATS_X + 6, sy, statusColor, false);
			sy += 9;
		}
	}

	/** Leader lines and names beside the hilt preview, so the sockets read as a labelled schematic. */
	private void drawHiltLabels(GuiGraphicsExtractor g) {
		int lx = HILT_X + 17;
		int tx = lx + 3;

		if (menu.isStaffMode()) {
			drawStaffLabels(g, lx, tx);
			return;
		}

		int[] ys = {BLADE_TOP + 10, EMITTER_Y + 4, GRIP_Y + 4, POMMEL_Y + 4};
		String[] names = {"crystal", "emitter", "grip", "pommel"};

		for (int i = 0; i < ys.length; i++) {
			boolean has = i == 0
					? menu.getSlot(SaberAssembly.CRYSTAL).hasItem() || menu.getSlot(SaberAssembly.SABER).hasItem()
					: menu.getSlot(i).hasItem() || menu.getSlot(SaberAssembly.SABER).hasItem();
			int color = has ? UiColors.TEXT_MUTED : UiColors.TEXT_DIM;
			g.fill(lx, ys[i] + 3, tx - 1, ys[i] + 4, Ui.a(has ? UiColors.HOLO : UiColors.HOLO_DIM, 0.7f));
			String name = Component.translatable("container.oldrepublic.lightsaber_workbench.part." + names[i]).getString();
			g.text(font, Ui.fit(font, name, PREVIEW_X + PREVIEW_W - 2 - tx), tx, ys[i], color, false);
		}
	}

	/** The staff's labels: crystal, the emitters, the grip and the second crystal. */
	private void drawStaffLabels(GuiGraphicsExtractor g, int lx, int tx) {
		boolean built = menu.getSlot(SaberAssembly.SABER).hasItem();
		int[] ys = {BLADE_TOP + 4, STAFF_EMITTER_Y + 4, STAFF_GRIP_Y + 4, STAFF_LOWER_Y + 4, STAFF_BLADE_BOTTOM - 10};
		String[] names = {"crystal", "emitter", "grip", "emitter", "second"};
		int[] slots = {SaberAssembly.CRYSTAL, SaberAssembly.EMITTER, SaberAssembly.GRIP, SaberAssembly.EMITTER, SaberAssembly.SECOND};

		for (int i = 0; i < ys.length; i++) {
			boolean has = built || menu.getSlot(slots[i]).hasItem();
			int color = has ? UiColors.TEXT_MUTED : UiColors.TEXT_DIM;
			g.fill(lx, ys[i] + 3, tx - 1, ys[i] + 4, Ui.a(has ? UiColors.HOLO : UiColors.HOLO_DIM, 0.7f));
			String name = Component.translatable("container.oldrepublic.lightsaber_workbench.part." + names[i]).getString();
			g.text(font, Ui.fit(font, name, PREVIEW_X + PREVIEW_W - 2 - tx), tx, ys[i], color, false);
		}
	}

	/**
	 * One stat: label, a bar placing the value within every possible build, a marker for the saber
	 * the player carries, and the server's exact value. The bar is green when this build beats the
	 * carried saber and amber when it is worse.
	 */
	private void statRow(GuiGraphicsExtractor g, int y, int index, String value, float bench, float carried) {
		g.text(font, Component.translatable("container.oldrepublic.lightsaber_workbench.stat." + STAT_KEYS[index]), STATS_X, y, UiColors.TEXT_DIM, false);
		g.text(font, value, STATS_RIGHT - font.width(value), y, UiColors.TEXT, false);

		int bx = STATS_X + STAT_LABEL_W + 2;
		int bw = STATS_RIGHT - STAT_VALUE_W - bx;
		float fill = Float.isNaN(bench) ? 0f : position(index, bench);
		int top = UiColors.HOLO_BRIGHT;
		int bottom = 0xFF4E8FBF;

		if (!Float.isNaN(carried) && !Float.isNaN(bench) && Math.abs(bench - carried) > 1.0E-3f) {
			boolean better = bench > carried;
			top = better ? 0xFFC8F5D2 : UiColors.AMBER_BRIGHT;
			bottom = better ? 0xFF3E8A55 : UiColors.AMBER_DIM;
		}

		Ui.bar(g, bx, y + 1, bw, 6, 0.12f + 0.88f * fill, top, bottom, 4, 1f);

		if (!Float.isNaN(carried)) {
			int mx = bx + 1 + Math.round((bw - 2) * (0.12f + 0.88f * position(index, carried)));
			g.fill(mx, y - 1, mx + 1, y + 8, UiColors.TEXT);
		}
	}

	private static float position(int index, float value) {
		float span = STAT_MAX[index] - STAT_MIN[index];
		return span <= 1.0E-4f ? 1f : Mth.clamp((value - STAT_MIN[index]) / span, 0f, 1f);
	}

	/** The bench build's stats under the default balance (for bar positions), or null. */
	private float @Nullable [] benchValues() {
		ItemStack saber = menu.getSlot(SaberAssembly.SABER).getItem();

		if (!saber.isEmpty()) {
			return values(SaberStats.of(saber, DEFAULT_BALANCE));
		}

		HiltParts.Part e = part(menu.getSlot(SaberAssembly.EMITTER).getItem());
		HiltParts.Part g = part(menu.getSlot(SaberAssembly.GRIP).getItem());
		HiltParts.Part p = part(menu.getSlot(SaberAssembly.POMMEL).getItem());

		if (g == Grip.STAFF) {
			return e instanceof Emitter emitter ? values(SaberStats.staff(emitter, Grip.STAFF, true, DEFAULT_BALANCE)) : null;
		}

		if (e instanceof Emitter emitter && g instanceof Grip grip && p instanceof Pommel pommel) {
			return values(SaberStats.of(emitter, grip, pommel, DEFAULT_BALANCE));
		}

		return null;
	}

	/** Stats of the first lightsaber or saberstaff in the player's inventory, or null if they carry none. */
	private float @Nullable [] carriedValues() {
		if (minecraft.player == null) {
			return null;
		}

		Inventory inventory = minecraft.player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (SaberAssembly.isBenchSaber(stack)) {
				return values(SaberStats.of(stack, DEFAULT_BALANCE));
			}
		}

		return null;
	}

	private static HiltParts.@Nullable Part part(ItemStack stack) {
		return stack.getItem() instanceof HiltPartItem item ? item.part() : null;
	}

	private static float[] values(SaberStats stats) {
		return new float[] {(float) stats.damage(), (float) stats.attackSpeed(), (float) stats.reachBonus(), stats.maxStamina(), stats.parryTicks()};
	}

	private static String socketName(int index) {
		return switch (index) {
			case SaberAssembly.CRYSTAL -> "crystal";
			case SaberAssembly.EMITTER -> "emitter";
			case SaberAssembly.GRIP -> "grip";
			case SaberAssembly.POMMEL -> "pommel";
			case SaberAssembly.SECOND -> "second";
			case SaberAssembly.LENS -> "lens";
			default -> "saber";
		};
	}

	private static int swatchX(BladeColor color) {
		return SWATCH_X + (color.ordinal() % 5) * SWATCH_STEP;
	}

	private static int swatchY(BladeColor color) {
		return SWATCH_Y + (color.ordinal() / 5) * SWATCH_STEP;
	}
}

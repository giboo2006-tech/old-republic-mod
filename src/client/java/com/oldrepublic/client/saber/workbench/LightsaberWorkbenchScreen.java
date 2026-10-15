package com.oldrepublic.client.saber.workbench;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Workbench screen: part slots on the left, the saber slot, the blade colour swatches, Construct
 * and Disassemble, and an exact stat preview computed by the server. Every action is a menu
 * button click validated on the server.
 */
public class LightsaberWorkbenchScreen extends AbstractContainerScreen<LightsaberWorkbenchMenu> {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/gui/container/lightsaber_workbench.png");
	private static final int WIDTH = 230;
	private static final int HEIGHT = 200;
	private static final int SWATCH_X = 66;
	private static final int SWATCH_Y = 20;
	private static final int SWATCH_SIZE = 11;
	private static final int SWATCH_STEP = 13;
	private static final int PANEL_X = 136;
	private static final int PANEL_RIGHT = 220;
	private static final int PANEL_Y = 21;
	private static final int LABEL_COLOR = 0xFF3A3F45;
	private static final int TEXT_COLOR = 0xFFD8E2EC;
	private static final int DIM_COLOR = 0xFF7E8A96;

	private Button construct;
	private Button disassemble;

	public LightsaberWorkbenchScreen(LightsaberWorkbenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelX = 34;
		this.inventoryLabelY = 107;
	}

	@Override
	protected void init() {
		super.init();
		construct = addRenderableWidget(Button.builder(Component.translatable("container.oldrepublic.lightsaber_workbench.construct"),
				b -> click(LightsaberWorkbenchMenu.BUTTON_CONSTRUCT)).bounds(leftPos + 66, topPos + 50, 60, 16).build());
		disassemble = addRenderableWidget(Button.builder(Component.translatable("container.oldrepublic.lightsaber_workbench.disassemble"),
				b -> click(LightsaberWorkbenchMenu.BUTTON_DISASSEMBLE)).bounds(leftPos + 66, topPos + 70, 60, 16).build());
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
			for (BladeColor color : BladeColor.values()) {
				int x = leftPos + swatchX(color);
				int y = topPos + swatchY(color);

				if (event.x() >= x && event.x() < x + SWATCH_SIZE && event.y() >= y && event.y() < y + SWATCH_SIZE) {
					AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
					click(color.ordinal());
					return true;
				}
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	// API-CHECK: Screen#extractBackground still draws the dimmed world behind container screens.
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, WIDTH, HEIGHT);
		int selected = menu.data(LightsaberWorkbenchMenu.DATA_COLOR);

		for (BladeColor color : BladeColor.values()) {
			int x = leftPos + swatchX(color);
			int y = topPos + swatchY(color);
			boolean isSelected = color.ordinal() == selected;
			graphics.fill(x - 1, y - 1, x + SWATCH_SIZE + 1, y + SWATCH_SIZE + 1, isSelected ? 0xFFF2F6FA : 0xFF1A1E22);
			graphics.fill(x, y, x + SWATCH_SIZE, y + SWATCH_SIZE, 0xFF000000 | color.rgb());

			if (isSelected) {
				graphics.outline(x - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, 0xFF2A2F35);
			}
		}
	}

	// API-CHECK: label coordinates are relative to leftPos/topPos, as renderLabels was.
	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);

		int y = PANEL_Y;

		if (menu.data(LightsaberWorkbenchMenu.DATA_HAS_PREVIEW) != 0) {
			y = stat(graphics, y, "damage", String.format("%.2f", menu.data(LightsaberWorkbenchMenu.DATA_DAMAGE_X100) / 100f));
			y = stat(graphics, y, "speed", String.format("%.2f", menu.data(LightsaberWorkbenchMenu.DATA_SPEED_X100) / 100f));
			y = stat(graphics, y, "reach", String.format("+%.2f", menu.data(LightsaberWorkbenchMenu.DATA_REACH_X100) / 100f));
			y = stat(graphics, y, "stamina", String.valueOf(menu.data(LightsaberWorkbenchMenu.DATA_STAMINA)));
			y = stat(graphics, y, "parry", String.format("%.1f", menu.data(LightsaberWorkbenchMenu.DATA_PARRY_X10) / 10f));
		} else {
			graphics.textWithWordWrap(font, Component.translatable("container.oldrepublic.lightsaber_workbench.no_preview"),
					PANEL_X, y, PANEL_RIGHT - PANEL_X, DIM_COLOR, false);
			y += 40;
		}

		Status status = menu.status();
		int statusColor = switch (status) {
			case READY, CAN_DISASSEMBLE -> 0xFF8FE3A0;
			case NOT_YOUR_CRYSTAL, NOT_YOUR_SABER, SLOTS_NOT_EMPTY -> 0xFFF0A070;
			default -> DIM_COLOR;
		};
		graphics.textWithWordWrap(font, Component.translatable(status.translationKey()), PANEL_X, y + 4, PANEL_RIGHT - PANEL_X, statusColor, false);
	}

	private int stat(GuiGraphicsExtractor graphics, int y, String key, String value) {
		graphics.text(font, Component.translatable("container.oldrepublic.lightsaber_workbench.stat." + key), PANEL_X, y, DIM_COLOR, false);
		graphics.text(font, value, PANEL_RIGHT - font.width(value), y, TEXT_COLOR, false);
		return y + 10;
	}

	private static int swatchX(BladeColor color) {
		return SWATCH_X + (color.ordinal() % 4) * SWATCH_STEP;
	}

	private static int swatchY(BladeColor color) {
		return SWATCH_Y + (color.ordinal() / 4) * SWATCH_STEP;
	}
}

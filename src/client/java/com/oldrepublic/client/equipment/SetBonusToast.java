package com.oldrepublic.client.equipment;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentBalance;
import com.oldrepublic.equipment.SetBonusRules;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The "set bonus active" toast (spec 11.2), shown once per equip when the server's
 * {@code armour_set} event says a set was completed. It slides in from the right edge at the top
 * right, where vanilla toasts appear: a framed plate in the set's accent with the chest piece's
 * icon, the bonus name ("Calm of the Watch") and its state, either "Set bonus active" or, for a set
 * with an alignment gate (the robes, the Watch battle armour, the Sith war armour) that is not met,
 * "Set complete · requires Light (+15)". A timer line runs out
 * along its bottom edge. A newer completion replaces the toast showing.
 *
 * <p>Like the tooltip line, the gate shown uses the default {@code setAlignment}; the server
 * decides with the live value.
 */
final class SetBonusToast {
	private static final int TOAST_TICKS = 70;
	private static final int SLIDE_IN = 8;
	private static final int SLIDE_OUT = 10;
	private static final int MARGIN = 4;
	private static final int HEIGHT = 26;
	private static final EquipmentBalance SHOWN = new EquipmentBalance();

	private static @Nullable ArmourSet set;
	private static @Nullable Component title;
	private static @Nullable Component state;
	private static boolean active;
	private static ItemStack icon = ItemStack.EMPTY;
	private static int start = Integer.MIN_VALUE;

	static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("set_bonus_toast"), SetBonusToast::extract);
	}

	/** Shows the toast for {@code completed}, replacing one already showing. */
	static void show(ArmourSet completed) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		SetBonusRules.Wearer wearer = ArmourSets.wearer(player);
		set = completed;
		title = Component.translatable("equipment.oldrepublic.bonus." + completed.id());
		active = true;
		state = Component.translatable("equipment.oldrepublic.toast.active");

		boolean light = completed == ArmourSet.JEDI_ROBES || completed == ArmourSet.WATCH_BATTLE;
		boolean dark = completed == ArmourSet.SITH_ROBES || completed == ArmourSet.SITH_WAR;

		if (light && !SetBonusRules.jediGate(wearer, SHOWN)) {
			active = false;
			state = Component.translatable("equipment.oldrepublic.toast.requires.light", SHOWN.setAlignment);
		} else if (dark && !SetBonusRules.sithGate(wearer, SHOWN)) {
			active = false;
			state = Component.translatable("equipment.oldrepublic.toast.requires.dark", SHOWN.setAlignment);
		}

		icon = ArmourSets.piece(completed, EquipmentSlot.CHEST);
		start = QuestClient.ticks();
	}

	/**
	 * The accent of a set: Republic blue, Imperial red, Jedi cream, Sith crimson-violet; Watch green,
	 * war-forge red, hunter's gold and spacer's tan.
	 */
	static int accent(ArmourSet armourSet) {
		return switch (armourSet) {
			case REPUBLIC_TROOPER -> 0xFF9CCBFF;
			case SITH_TROOPER -> 0xFFF0606A;
			case JEDI_ROBES -> 0xFFEAD9A8;
			case SITH_ROBES -> 0xFFD0507E;
			case WATCH_BATTLE -> 0xFFA8D890;
			case SITH_WAR -> 0xFFE0563C;
			case MANDALORIAN -> 0xFFE8C060;
			case SPACER -> 0xFFC8A070;
			default -> 0xFFE0E0E0;
		};
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			set = null;
			start = Integer.MIN_VALUE;
			return;
		}

		if (set == null || title == null || state == null || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}

		float now = UiAnim.time(deltaTracker.getGameTimeDeltaPartialTick(true));
		float t = now - start;

		if (t > TOAST_TICKS) {
			set = null;
			return;
		}

		float alpha = UiAnim.envelope(now, start, TOAST_TICKS, 3, SLIDE_OUT);

		if (alpha <= 0.02f) {
			return;
		}

		draw(graphics, mc.font, set, title, state, now, t, alpha);
	}

	private static void draw(GuiGraphicsExtractor g, Font font, ArmourSet shown, Component name, Component line, float now, float t, float alpha) {
		int accent = accent(shown);
		int w = Math.max(font.width(name), font.width(line)) + 36;
		// Slides in from the right edge, and back out at the end.
		float in = UiAnim.easeOutCubic(Mth.clamp(t / SLIDE_IN, 0f, 1f));
		float out = UiAnim.easeOutCubic(Mth.clamp((TOAST_TICKS - t) / SLIDE_OUT, 0f, 1f));
		int x = g.guiWidth() - MARGIN - w + Math.round((w + MARGIN + 4) * (1f - Math.min(in, out)));
		int y = MARGIN;

		Ui.plate(g, x, y, w, HEIGHT, alpha);
		// The accent bar on the leading edge, pulsing gently while the bonus is live.
		float glow = active ? 0.75f + 0.25f * UiAnim.pulse(now, 30f) : 0.55f;
		g.fill(x + 2, y + 3, x + 4, y + HEIGHT - 3, Ui.a(accent, alpha * glow));
		Ui.inset(g, x + 6, y + 4, 18, 18, alpha);

		if (alpha > 0.35f && !icon.isEmpty()) {
			g.item(icon, x + 7, y + 5);
		}

		Ui.text(g, font, name, x + 28, y + 4, accent, alpha);
		Ui.text(g, font, line, x + 28, y + 14, active ? UiColors.OK : UiColors.AMBER, alpha);

		// A short sheen sweeps across the plate as it lands.
		if (t < SLIDE_IN + 8) {
			int sx = x + Math.round(w * Mth.clamp((t - SLIDE_IN * 0.5f) / 10f, 0f, 1f));
			g.fill(sx, y + 2, Math.min(x + w - 2, sx + 2), y + HEIGHT - 2, Ui.a(0xFFFFFFFF, alpha * 0.25f));
		}

		// Timer line running out along the bottom edge.
		float left = 1f - Mth.clamp(t / TOAST_TICKS, 0f, 1f);
		int lw = Math.round((w - 10) * left);
		g.fill(x + 5, y + HEIGHT - 2, x + 5 + lw, y + HEIGHT - 1, Ui.a(accent, alpha * 0.6f));
	}

	private SetBonusToast() {
	}
}

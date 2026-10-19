package com.oldrepublic.client.crafting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingItems;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.M5Items;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The crafting toasts (spec 11.2): schematic learned (a legendary one in gold, MILESTONE_5.md 11),
 * discipline chosen or levelled up, and the small "Masterwork" and "Legendary" crafted plates.
 * Learning and levels are read from the synced {@code crafting} attachment, compared each tick with
 * what was last seen (the first sight after joining sets the baseline and shows nothing); the
 * crafted plates come from the {@code bench_craft} event. Toasts slide in at the top right, one at a
 * time, and queue.
 */
final class CraftingToasts {
	private static final int TOAST_TICKS = 70;
	private static final int SLIDE = 8;
	private static final int HEIGHT = 26;
	private static final int MARGIN = 4;
	private static final int MAX_QUEUE = 6;
	/** The Legendary accent: the gold of a Legendary item's name. */
	private static final int LEGENDARY = 0xFFFFAA00;

	private record Toast(ItemStack icon, Component title, Component line, int accent) {
	}

	private static final Deque<Toast> QUEUE = new ArrayDeque<>();
	private static @Nullable Toast showing;
	private static int start;
	private static @Nullable CraftingData seen;

	static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, OldRepublicMod.id("crafting_toasts"), CraftingToasts::extract);
	}

	/** The small Masterwork plate for the local player's craft. */
	static void masterwork() {
		add(new Toast(new ItemStack(M4Items.MASTERWORK_FRAGMENT), Component.translatable("crafting.oldrepublic.toast.masterwork"),
				Component.translatable("crafting.oldrepublic.toast.masterwork.line"), UiColors.BRASS_LIGHT));
	}

	/** The small Legendary plate for the local player's craft (after the first, which has its banner). */
	static void legendary() {
		add(new Toast(new ItemStack(M5Items.HYMNSTONE_SHARD), Component.translatable("crafting.oldrepublic.toast.legendary"),
				Component.translatable("crafting.oldrepublic.toast.legendary.line"), LEGENDARY));
	}

	/** Client tick: compares the synced crafting data with what was last seen. */
	static void tick(Minecraft mc) {
		if (mc.player == null) {
			seen = null;
			return;
		}

		CraftingData now = CraftingAttachments.get(mc.player);

		if (seen == null) {
			seen = now;
			return;
		}

		if (now == seen) {
			return;
		}

		for (Progress progress : now.disciplines()) {
			Progress before = seen.progress(progress.discipline()).orElse(null);
			Discipline d = progress.discipline();

			if (before == null) {
				add(new Toast(icon(d), Component.translatable("crafting.oldrepublic.toast.chosen"), Crafting.name(d), UiColors.HOLO));
			} else if (progress.level() > before.level()) {
				add(new Toast(icon(d), Component.translatable("crafting.oldrepublic.toast.level"),
						Component.translatable("crafting.oldrepublic.toast.level.line", Crafting.name(d), progress.level()), UiColors.OK));
			}
		}

		Set<Identifier> learned = new HashSet<>(now.known());
		learned.removeAll(seen.known());

		for (Identifier id : learned) {
			Schematic s = Schematics.get(id);
			ItemStack out = s == null ? ItemStack.EMPTY : s.result();
			boolean legendary = Crafting.isLegendaryId(id);
			add(new Toast(out, Component.translatable(legendary ? "crafting.oldrepublic.toast.learned_legendary" : "crafting.oldrepublic.toast.learned"),
					out.isEmpty() ? Component.literal(id.getPath()) : out.getHoverName(), legendary ? LEGENDARY : UiColors.AMBER));
		}

		seen = now;
	}

	/** The icon of a discipline: the output of its first schematic, or a schematic item. */
	static ItemStack icon(Discipline d) {
		for (Schematic s : Schematics.all()) {
			if (s.discipline().orElse(null) == d) {
				return s.result();
			}
		}

		return new ItemStack(CraftingItems.SCHEMATIC);
	}

	private static void add(Toast toast) {
		if (QUEUE.size() < MAX_QUEUE) {
			QUEUE.add(toast);
		}
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.level == null) {
			reset();
			return;
		}

		int now = QuestClient.ticks();

		if (showing == null || now - start > TOAST_TICKS) {
			showing = QUEUE.poll();
			start = now;
		}

		if (showing == null || mc.gui.hud.isHidden()) {
			return;
		}

		float time = UiAnim.time(deltaTracker.getGameTimeDeltaPartialTick(true));
		float t = time - start;
		float alpha = UiAnim.envelope(time, start, TOAST_TICKS, 3, SLIDE + 2);

		if (alpha > 0.02f) {
			draw(g, mc.font, showing, t, alpha);
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, Toast toast, float t, float alpha) {
		int w = Math.max(font.width(toast.title()), font.width(toast.line())) + 36;
		float in = UiAnim.easeOutCubic(Mth.clamp(t / SLIDE, 0f, 1f));
		float out = UiAnim.easeOutCubic(Mth.clamp((TOAST_TICKS - t) / SLIDE, 0f, 1f));
		int x = g.guiWidth() - MARGIN - w + Math.round((w + MARGIN + 4) * (1f - Math.min(in, out)));
		// Below the set bonus toast's row, so the two never overlap.
		int y = MARGIN + HEIGHT + 4;

		Ui.plate(g, x, y, w, HEIGHT, alpha);
		g.fill(x + 2, y + 3, x + 4, y + HEIGHT - 3, Ui.a(toast.accent(), alpha));
		Ui.inset(g, x + 6, y + 4, 18, 18, alpha);

		if (alpha > 0.35f && !toast.icon().isEmpty()) {
			g.item(toast.icon(), x + 7, y + 5);
		}

		Ui.text(g, font, toast.title(), x + 28, y + 4, UiColors.TEXT_MUTED, alpha);
		Ui.text(g, font, toast.line(), x + 28, y + 14, toast.accent(), alpha);
		float left = 1f - Mth.clamp(t / TOAST_TICKS, 0f, 1f);
		g.fill(x + 5, y + HEIGHT - 2, x + 5 + Math.round((w - 10) * left), y + HEIGHT - 1, Ui.a(toast.accent(), alpha * 0.6f));
	}

	/** Forgets everything (disconnect). */
	static void reset() {
		QUEUE.clear();
		showing = null;
		seen = null;
	}

	private CraftingToasts() {
	}
}

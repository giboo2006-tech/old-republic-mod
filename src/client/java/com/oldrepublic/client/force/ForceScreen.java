package com.oldrepublic.client.force;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.force.network.ChooseBranchPayload;
import com.oldrepublic.force.network.PushInfoPayload;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The Force screen (default K), a framed durasteel panel. Header: the attunement emblem with its XP
 * ring, the title and the XP bar. Stat strip: Resonance, Force Energy, skill points and Force Push
 * mastery with rank pips. Then the three Push branches as illustrated cards with their exact
 * numbers, chosen / available / locked state and the lock reason, each with its own choose button;
 * the full tooltip sits on a small "?" chip per card (and on the buttons after a short pause).
 * Choosing sends a request; the server validates it and the synced data refreshes the screen.
 * The layout adapts to small windows (the stat strip collapses to one line, card text drops first).
 */
public final class ForceScreen extends Screen {
	private static final PushBranch[] BRANCHES = {PushBranch.REPULSE_WAVE, PushBranch.FOCUSED_BLAST, PushBranch.DEFLECTING_PULSE};
	private static final int MAX_W = 420;
	private static final int MAX_H = 236;
	private static final int GAP = 6;
	private static final int EMBLEM = 28;
	private static final int DONE_W = 72;
	/** Full numbers appear on the "?" chip at once, and on the choose buttons only after a pause. */
	private static final Duration TOOLTIP_DELAY = Duration.ofMillis(500);
	private static final int INFO_SIZE = 9;

	private enum CardState {
		CHOSEN, AVAILABLE, LOCKED
	}

	/** Snapshot of what the widgets were built from, to rebuild them when the synced data changes. */
	private String builtFrom = "";
	private int px;
	private int py;
	private int pw;
	private int ph;
	private boolean compact;
	private int statsY;
	private int sectionY;
	private int cardsY;
	private int cardsH;
	private int cardW;
	private int footerY;

	private ForceScreen() {
		super(Component.translatable("screen.oldrepublic.force.title"));
	}

	/** Opens the screen, or says nothing stirs yet when the player is not awakened. */
	static void open(Minecraft mc, LocalPlayer player) {
		ForceData data = player.getAttached(ForceAttachments.FORCE);

		if (data == null || !data.isAwakened()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"));
			return;
		}

		mc.gui.setScreen(new ForceScreen());
	}

	private @Nullable ForceData data() {
		return minecraft.player == null ? null : minecraft.player.getAttached(ForceAttachments.FORCE);
	}

	@Override
	protected void init() {
		ForceData data = data();
		builtFrom = signature(data);
		layout();

		for (int i = 0; i < BRANCHES.length; i++) {
			PushBranch branch = BRANCHES[i];
			int cx = cardX(i);
			HoloButton button = new HoloButton(cx + 6, cardsY + cardsH - 22, cardW - 12, 16, buttonLabel(branch, data), b -> choose(branch),
					data != null && canChoose(branch, data));
			button.setTooltip(Tooltip.create(tooltip(branch, data)));
			button.setTooltipDelay(TOOLTIP_DELAY);
			button.active = data != null && canChoose(branch, data);
			addRenderableWidget(button);
		}

		addRenderableWidget(new HoloButton(px + pw - 10 - DONE_W, footerY, DONE_W, 18, Component.translatable("gui.done"), b -> onClose()));
	}

	private void layout() {
		pw = Math.min(width - 8, MAX_W);
		ph = Math.min(height - 8, MAX_H);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		compact = ph < 214;
		statsY = py + 40;
		sectionY = statsY + (compact ? 14 : 24) + 4;
		cardsY = sectionY + 14;
		footerY = py + ph - 26;
		cardsH = footerY - 4 - cardsY;
		cardW = (pw - 20 - 2 * GAP) / 3;
	}

	private int cardX(int index) {
		return px + 10 + index * (cardW + GAP);
	}

	@Override
	public void tick() {
		super.tick();

		if (!builtFrom.equals(signature(data()))) {
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		ForceData data = data();
		float time = UiAnim.time(partialTick);

		if (data != null) {
			Ui.panel(graphics, px, py, pw, ph);
			Ui.scanlines(graphics, px + 6, py + 6, pw - 12, ph - 12, 0x06FFFFFF, time);
			drawHeader(graphics, data, time);

			if (compact) {
				drawStatsLine(graphics, data);
			} else {
				drawStatBlocks(graphics, data, time);
			}

			drawSection(graphics, data, time);

			for (int i = 0; i < BRANCHES.length; i++) {
				drawCard(graphics, BRANCHES[i], data, cardX(i), mouseX, mouseY, time);
			}

			int ruleY = footerY;

			for (String line : Ui.wrap(font, branchRule(data).getString(), pw - 30 - DONE_W, 2)) {
				Ui.text(graphics, font, line, px + 10, ruleY, UiColors.TEXT_MUTED, 1f);
				ruleY += 9;
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	// ---------------------------------------------------------------- header and stats

	private void drawHeader(GuiGraphicsExtractor g, ForceData data, float time) {
		int ex = px + 10;
		int ey = py + 8;
		float xp = data.xpToNext <= 0 ? 1f : Mth.clamp((float) data.xp / data.xpToNext, 0f, 1f);
		Ui.sprite(g, Ui.EMBLEM_LARGE, ex, ey, EMBLEM, EMBLEM, 1f);
		Ui.ring(g, ex - 1, ey - 1, EMBLEM + 2, EMBLEM + 2, 10, xp, UiColors.BRASS_LIGHT, 0x70101418, 1f);
		String level = Integer.toString(data.level);
		g.text(font, level, ex + (EMBLEM - font.width(level)) / 2 + 1, ey + 10, UiColors.BRASS_LIGHT, true);

		int tx = ex + EMBLEM + 8;
		int tw = px + pw - 10 - tx;
		Ui.header(g, font, title, tx, py + 9, tw, time);

		int rowY = py + 25;
		Component levelText = Component.translatable("screen.oldrepublic.force.header.level", data.level);
		g.text(font, levelText, tx, rowY, UiColors.BRASS_LIGHT, true);
		String xpText = Component.translatable("screen.oldrepublic.force.xp", data.xp, data.xpToNext).getString();
		int xpW = font.width(xpText);
		g.text(font, xpText, px + pw - 10 - xpW, rowY, UiColors.TEXT_MUTED, true);
		int barX = tx + font.width(levelText) + 6;
		int barW = px + pw - 10 - xpW - 6 - barX;

		if (barW > 20) {
			Ui.bar(g, barX, rowY + 1, barW, 6, xp, 0xFFFFF0C8, UiColors.BRASS, 10, 1f);
		}
	}

	private void drawStatBlocks(GuiGraphicsExtractor g, ForceData data, float time) {
		int bw = (pw - 20 - 3 * 4) / 4;
		int bh = 24;
		int x = px + 10;
		int y = statsY;

		// Resonance, with a drifting line of notch script beside the number.
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.force.stat.resonance"), x + 5, y + 3, UiColors.TEXT_DIM, true);
		String resonance = String.format(Locale.ROOT, "%,d", data.resonance);
		g.text(font, resonance, x + 5, y + 13, UiColors.RESONANCE, true);
		int gx = x + 5 + font.width(resonance) + 5;

		if (x + bw - 5 - gx > 8) {
			Ui.glyphs(g, gx, y + 14, x + bw - 5 - gx, Ui.a(UiColors.AMBER, 0.3f), (int) (time / 8f));
		}

		x += bw + 4;

		// Force Energy
		float energy = data.maxEnergy <= 0 ? 0f : Mth.clamp(data.energy / data.maxEnergy, 0f, 1f);
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.force.stat.energy"), x + 5, y + 3, UiColors.TEXT_DIM, true);
		String energyText = Math.round(data.energy) + "/" + Math.round(data.maxEnergy);
		g.text(font, energyText, x + 5, y + 13, UiColors.HOLO_BRIGHT, true);
		miniBar(g, x + 5 + font.width(energyText) + 5, y + 14, x + bw - 5, energy, ARGB.srgbLerp(energy, 0xFF4E8FBF, 0xFFE4F6FF), UiColors.HOLO_DEEP,
				Math.max(1, Math.round(data.maxEnergy / 10f)));
		x += bw + 4;

		// Skill points
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.force.stat.skill_points"), x + 5, y + 3, UiColors.TEXT_DIM, true);
		g.text(font, Integer.toString(data.skillPoints), x + 5, y + 13, data.skillPoints > 0 ? UiColors.HOLO_BRIGHT : UiColors.TEXT_DIM, true);

		if (data.skillPoints > 0) {
			Ui.pips(g, x + 5 + font.width(Integer.toString(data.skillPoints)) + 5, y + 14, Math.min(data.skillPoints, Math.max(1, (bw - 30) / 6)),
					Math.min(data.skillPoints, Math.max(1, (bw - 30) / 6)), UiColors.HOLO, 1f);
		}

		x += bw + 4;

		// Force Push mastery: rank, pips, and progress toward the next rank.
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.force.stat.mastery"), x + 5, y + 3, UiColors.TEXT_DIM, true);

		if (data.hasAbility(ForcePush.ABILITY_ID)) {
			int points = data.mastery(ForcePush.ABILITY_ID);
			int rank = Progression.masteryRank(points);
			Component rankText = Component.translatable("screen.oldrepublic.force.mastery_rank", rank);
			g.text(font, rankText, x + 5, y + 13, UiColors.TEXT, true);
			int pipX = x + 5 + font.width(rankText) + 4;
			Ui.pips(g, pipX, y + 14, 3, rank, UiColors.BRASS_LIGHT, 1f);
			int prev = rankStart(rank);
			int next = rankStart(rank + 1);
			float progress = next < 0 ? 1f : (float) (points - prev) / Math.max(1, next - prev);
			miniBar(g, pipX + 20, y + 14, x + bw - 5, progress, 0xFFFFF0C8, UiColors.BRASS, 0);
		} else {
			g.text(font, Component.translatable("screen.oldrepublic.force.mastery_unknown"), x + 5, y + 13, UiColors.TEXT_DIM, true);
		}
	}

	/** A 5 px bar from {@code x} to {@code right}, skipped when there is no room for it. */
	private static void miniBar(GuiGraphicsExtractor g, int x, int y, int right, float fill, int top, int bottom, int segments) {
		if (right - x >= 12) {
			Ui.bar(g, x, y, right - x, 5, fill, top, bottom, segments, 1f);
		}
	}

	/** Small windows: the same numbers on one line. */
	private void drawStatsLine(GuiGraphicsExtractor g, ForceData data) {
		Component line = Component.translatable("screen.oldrepublic.force.stats_line",
				String.format(Locale.ROOT, "%,d", data.resonance), Math.round(data.energy), Math.round(data.maxEnergy), data.skillPoints,
				Progression.masteryRank(data.mastery(ForcePush.ABILITY_ID)));
		Ui.inset(g, px + 10, statsY - 1, pw - 20, 13, 1f);
		g.text(font, Ui.fit(font, line.getString(), pw - 28), px + 14, statsY + 2, UiColors.TEXT_MUTED, true);
	}

	/** The first mastery points of {@code rank}, or -1 above the top rank. */
	private static int rankStart(int rank) {
		if (rank <= 0) {
			return 0;
		}

		for (int points = 0; points <= 100_000; points++) {
			if (Progression.masteryRank(points) >= rank) {
				return points;
			}
		}

		return -1;
	}

	private void drawSection(GuiGraphicsExtractor g, ForceData data, float time) {
		PushInfoPayload info = ClientPushInfo.get();
		String cost = info == null ? "" : Component.translatable("screen.oldrepublic.force.energy_cost", f(info.costMin()), f(info.costMax())).getString();
		int costW = cost.isEmpty() ? 0 : font.width(cost) + 8;
		Ui.header(g, font, Component.translatable("screen.oldrepublic.force.section"), px + 10, sectionY, pw - 20 - costW, time);

		if (!cost.isEmpty()) {
			g.text(font, cost, px + pw - 10 - font.width(cost), sectionY, UiColors.TEXT_MUTED, true);
		}
	}

	// ---------------------------------------------------------------- cards

	private void drawCard(GuiGraphicsExtractor g, PushBranch branch, ForceData data, int x, int mouseX, int mouseY, float time) {
		CardState state = state(branch, data);
		int y = cardsY;
		int w = cardW;
		int h = cardsH;
		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		// Full numbers live on the "?" chip, so a resting cursor never buries the neighbouring cards.
		Identifier frame = switch (state) {
			case CHOSEN -> Ui.CARD_SELECTED;
			case LOCKED -> Ui.CARD_LOCKED;
			case AVAILABLE -> Ui.CARD;
		};
		Ui.sprite(g, frame, x, y, w, h, 1f);

		if (state == CardState.CHOSEN) {
			Ui.scanlines(g, x + 4, y + 4, w - 8, h - 8, Ui.a(UiColors.HOLO, 0.05f * UiAnim.flicker(time)), time);
		}

		if (hovered && state != CardState.LOCKED) {
			g.fill(x + 3, y + 2, x + w - 3, y + 3, Ui.a(UiColors.HOLO, 0.7f));
		}

		String name = Component.translatable("force.oldrepublic.branch." + branch.id()).getString();
		// Long names get the small icon so the whole name fits beside it.
		int bigIcon = w >= 120 ? 24 : 16;
		int icon = font.width(name) <= x + w - 8 - INFO_SIZE - (x + 6 + bigIcon + 5) ? bigIcon : 16;
		int tint = state == CardState.LOCKED ? 0xFF6A7078 : 0xFFFFFFFF;
		Ui.sprite(g, Ui.sprite("branch_" + branch.id()), x + 6, y + 6, icon, icon, tint);

		int tx = x + 6 + icon + 4;
		int tw = x + w - 7 - INFO_SIZE - tx;
		int infoX = x + w - 6 - INFO_SIZE;
		int infoY = y + 6;
		boolean overInfo = mouseX >= infoX && mouseX < infoX + INFO_SIZE && mouseY >= infoY && mouseY < infoY + INFO_SIZE;
		drawInfoChip(g, infoX, infoY, overInfo);
		g.text(font, Ui.fit(font, name, tw), tx, y + 7, state == CardState.CHOSEN ? UiColors.BRASS_LIGHT : UiColors.TEXT, true);
		Component chip = switch (state) {
			case CHOSEN -> Component.translatable("screen.oldrepublic.force.state.chosen");
			case AVAILABLE -> Component.translatable("screen.oldrepublic.force.state.available");
			case LOCKED -> lockReason(branch, data);
		};
		int chipColor = switch (state) {
			case CHOSEN -> UiColors.HOLO;
			case AVAILABLE -> UiColors.OK;
			case LOCKED -> UiColors.AMBER;
		};
		int chipX = tx;

		if (state == CardState.LOCKED) {
			drawLock(g, tx, y + 17, chipColor);
			chipX += 8;
		}

		g.text(font, Ui.fit(font, chip.getString(), x + w - 6 - chipX), chipX, y + 17, chipColor, true);

		// Body: the description, then the numbers; when space is short the description is cut first.
		int top = y + 6 + Math.max(icon, 20) + 3;
		int bottom = y + h - 24;
		List<NumberRow> numbers = numbers(branch);
		int lineH = font.lineHeight;
		int numberLines = numbers.isEmpty() ? 1 : Math.min(numbers.size(), Math.max(0, (bottom - top) / lineH));
		int descLines = Math.max(0, (bottom - top - numberLines * lineH - 3) / lineH);
		String desc = Component.translatable("screen.oldrepublic.force.tooltip." + branch.id() + ".short").getString();
		int textColor = state == CardState.LOCKED ? UiColors.TEXT_DIM : UiColors.TEXT_MUTED;
		int ly = top;

		for (String line : Ui.wrap(font, desc, w - 12, descLines)) {
			g.text(font, line, x + 6, ly, textColor, true);
			ly += lineH;
		}

		if (descLines > 0) {
			ly += 3;
		}

		if (numbers.isEmpty() && ly + lineH <= bottom) {
			String none = Component.translatable("screen.oldrepublic.force.card.no_data").getString();
			g.text(font, Ui.fit(font, none, w - 12), x + 6, ly, UiColors.TEXT_DIM, true);
		}

		for (int i = 0; i < numberLines && i < numbers.size(); i++) {
			boolean locked = state == CardState.LOCKED;
			g.fill(x + 6, ly + 3, x + 8, ly + 5, Ui.a(locked ? UiColors.TEXT_DIM : UiColors.HOLO, 0.9f));
			Ui.labelValue(g, font, numbers.get(i).label(), numbers.get(i).value(), x + 11, ly, w - 17,
					UiColors.TEXT_MUTED, locked ? UiColors.TEXT_DIM : UiColors.HOLO_BRIGHT);
			ly += lineH;
		}

		if (overInfo) {
			g.setTooltipForNextFrame(font, Tooltip.splitTooltip(minecraft, tooltip(branch, data)), mouseX, mouseY);
		}
	}

	/** The small "?" chip in a card's corner that shows the branch's full numbers on hover. */
	private void drawInfoChip(GuiGraphicsExtractor g, int x, int y, boolean hover) {
		g.fill(x, y, x + INFO_SIZE, y + INFO_SIZE, UiColors.OUTLINE);
		g.fill(x + 1, y + 1, x + INFO_SIZE - 1, y + INFO_SIZE - 1, hover ? UiColors.HOLO_DIM : UiColors.STEEL_DARK);
		g.text(font, "?", x + (INFO_SIZE - font.width("?")) / 2 + 1, y + 1, hover ? UiColors.HOLO_BRIGHT : UiColors.HOLO, false);
	}

	/** A 6x7 pixel padlock in front of a lock reason. */
	private static void drawLock(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x + 1, y, x + 5, y + 1, color);
		g.fill(x + 1, y, x + 2, y + 3, color);
		g.fill(x + 4, y, x + 5, y + 3, color);
		g.fill(x, y + 3, x + 6, y + 7, color);
		g.fill(x + 2, y + 4, x + 4, y + 6, UiColors.OUTLINE);
	}

	/** One line of a card's numbers: a label and its value, joined by a dotted leader. */
	private record NumberRow(Component label, Component value) {
	}

	/** The card's key numbers (empty until the server has sent them). */
	private static List<NumberRow> numbers(PushBranch branch) {
		PushInfoPayload info = ClientPushInfo.get();

		if (info == null) {
			return List.of();
		}

		NumberRow knockback;
		NumberRow shape;
		NumberRow special;
		float cooldown = info.cooldownSeconds();

		switch (branch) {
			case REPULSE_WAVE -> {
				shape = row("cone", f(info.repulseCone()), f(info.repulseRange()));
				knockback = row("knockback", f(info.knockbackMin() * info.repulseKnockback()), f(info.knockbackMax() * info.repulseKnockback()));
				special = row("stagger", f(info.staggerSeconds()));
			}
			case FOCUSED_BLAST -> {
				shape = row("cone", f(info.focusedCone()), f(info.focusedRange()));
				knockback = row("knockback", f(info.knockbackMin() * info.focusedKnockback()), f(info.knockbackMax() * info.focusedKnockback()));
				special = row("slam", f(info.slamDamageMin()), f(info.slamDamageMax()));
			}
			case DEFLECTING_PULSE -> {
				shape = row("radius", f(info.deflectRadius()));
				knockback = row("knockback", f(info.knockbackMin() * info.deflectKnockback()), f(info.knockbackMax() * info.deflectKnockback()));
				special = row("reflect");
				cooldown = info.deflectCooldownSeconds();
			}
			default -> {
				return List.of();
			}
		}

		return List.of(shape, knockback, special, row("cooldown", f(cooldown)));
	}

	private static NumberRow row(String key, Object... args) {
		String base = "screen.oldrepublic.force.card." + key;
		return new NumberRow(Component.translatable(base), Component.translatable(base + ".value", args));
	}

	private CardState state(PushBranch branch, ForceData data) {
		if (branch.id().equals(data.pushBranch)) {
			return CardState.CHOSEN;
		}

		return canChoose(branch, data) ? CardState.AVAILABLE : CardState.LOCKED;
	}

	private static Component lockReason(PushBranch branch, ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		int unlock = info != null ? info.unlockLevel() : 3;

		if (!data.hasAbility(ForcePush.ABILITY_ID)) {
			return Component.translatable("screen.oldrepublic.force.lock.not_learned");
		}

		if (data.level < unlock) {
			return Component.translatable("screen.oldrepublic.force.lock.level", unlock);
		}

		return Component.translatable("screen.oldrepublic.force.lock.points", branchCost(data));
	}

	private static Component buttonLabel(PushBranch branch, @Nullable ForceData data) {
		if (data != null && branch.id().equals(data.pushBranch)) {
			return Component.translatable("screen.oldrepublic.force.button.chosen");
		}

		int cost = data == null ? 1 : branchCost(data);
		boolean first = data == null || data.pushBranch.isEmpty();
		return Component.translatable(first ? "screen.oldrepublic.force.button.choose" : "screen.oldrepublic.force.button.reshape", cost);
	}

	private static int branchCost(ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		return data.pushBranch.isEmpty() ? (info != null ? info.firstCost() : 1) : (info != null ? info.rechooseCost() : 3);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Component branchRule(ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		int unlock = info != null ? info.unlockLevel() : 3;
		int first = info != null ? info.firstCost() : 1;
		int again = info != null ? info.rechooseCost() : 3;

		if (data.level < unlock) {
			return Component.translatable("screen.oldrepublic.force.branch_locked", unlock);
		}

		return Component.translatable("screen.oldrepublic.force.branch_rule", first, again);
	}

	private static boolean canChoose(PushBranch branch, ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		int unlock = info != null ? info.unlockLevel() : 3;
		int cost = data.pushBranch.isEmpty() ? (info != null ? info.firstCost() : 1) : (info != null ? info.rechooseCost() : 3);
		return data.hasAbility(ForcePush.ABILITY_ID) && data.level >= unlock && !branch.id().equals(data.pushBranch) && data.skillPoints >= cost;
	}

	private static Component tooltip(PushBranch branch, @Nullable ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		String key = "screen.oldrepublic.force.tooltip." + branch.id();

		if (info == null) {
			return Component.translatable(key + ".short").append("\n").append(Component.translatable("screen.oldrepublic.force.tooltip.no_data"));
		}

		Component numbers = switch (branch) {
			case REPULSE_WAVE -> Component.translatable(key, f(info.repulseCone()), f(info.repulseRange()),
					f(info.knockbackMin() * info.repulseKnockback()), f(info.knockbackMax() * info.repulseKnockback()),
					f(info.staggerSeconds()), f(info.cooldownSeconds()), f(info.costMin()), f(info.costMax()));
			case FOCUSED_BLAST -> Component.translatable(key, f(info.focusedCone()), f(info.focusedRange()),
					f(info.knockbackMin() * info.focusedKnockback()), f(info.knockbackMax() * info.focusedKnockback()),
					f(info.slamDamageMin()), f(info.slamDamageMax()), f(info.cooldownSeconds()), f(info.costMin()), f(info.costMax()));
			case DEFLECTING_PULSE -> Component.translatable(key, f(info.deflectRadius()),
					f(info.knockbackMin() * info.deflectKnockback()), f(info.knockbackMax() * info.deflectKnockback()),
					f(info.deflectCooldownSeconds()), f(info.costMin()), f(info.costMax()));
			case NONE -> Component.empty();
		};

		int cost = data == null || data.pushBranch.isEmpty() ? info.firstCost() : info.rechooseCost();
		return Component.translatable(key + ".short").append("\n").append(numbers).append("\n")
				.append(Component.translatable("screen.oldrepublic.force.tooltip.base", f(info.baseCone()), f(info.baseRange())))
				.append("\n").append(Component.translatable("screen.oldrepublic.force.tooltip.cost", cost));
	}

	private static String f(float value) {
		return Math.abs(value - Math.round(value)) < 0.05f ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}

	private static String signature(@Nullable ForceData data) {
		return data == null ? "" : data.level + "|" + data.skillPoints + "|" + data.pushBranch + "|" + data.abilities.size() + "|" + (ClientPushInfo.get() != null);
	}

	private void choose(PushBranch branch) {
		ClientPlayNetworking.send(new ChooseBranchPayload(branch.id()));
	}
}

package com.oldrepublic.client.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.client.ability.ClientAbilityInfo;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.identity.StandingPanel;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.NodeSocket;
import com.oldrepublic.client.ui.TabStrip;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.skill.NodeKind;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillContext;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTree;
import com.oldrepublic.skill.SkillTreeSet;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.skill.network.AssignSlotPayload;
import com.oldrepublic.skill.network.LearnNodePayload;
import com.oldrepublic.skill.network.RespecConfirmPayload;
import com.oldrepublic.skill.network.SetFormPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The Force screen (default K): a framed durasteel panel. Header: the attunement emblem (tinted by
 * alignment after the Crossing) with its XP ring, the title, level and XP bar; a stat strip with
 * Resonance, Force Energy, skill points and path. A tab strip opens each visible skill tree
 * (Universal, Lightsaber, Jedi in pale gold, Sith in deep red) and, after the Crossing, the
 * Standing panel of the identity feature. A tree tab shows the tree on the left and the selected
 * node's {@link NodeDetail} on the right, with its buttons (Learn / Switch, Assign to slot, Set
 * active form). The footer holds the four ability slots with their keys (drag a power onto a slot,
 * or off it to clear it), Respec (enabled only when allowed, the reason otherwise; it asks for
 * confirmation in the panel) and Done.
 *
 * <p>The screen only sends requests; the server validates them and the synced data rebuilds the
 * screen. It adapts to small windows (the stat strip collapses to one line).
 */
public final class ForceScreen extends Screen {
	private static final int MAX_W = 460;
	private static final int MAX_H = 260;
	private static final int EMBLEM = 28;
	private static final int DONE_W = 60;
	private static final int RESPEC_W = 70;
	private static final int SLOT = 20;
	private static final int SLOT_STEP = 38;
	private static final int BUTTON_H = 16;
	private static final String STANDING = "standing";
	private static final int ALIGNMENT_TINT_JEDI = 0xFFE8D7A0;
	private static final int ALIGNMENT_TINT_SITH = 0xFFB8323A;
	private static final int DRAG_DISTANCE = 3;
	private static final int RESPEC_RECHECK_TICKS = 10;

	/** Remembered across openings: the open tab and the selected node per tab. */
	private static String lastTab = "";
	private static final Map<String, String> LAST_SELECTED = new HashMap<>();
	/** The node to flash (just learned) and when. */
	private static @Nullable String flashNode;
	private static int flashAt = Integer.MIN_VALUE;

	private final TabStrip tabs = new TabStrip(List.of());
	private final TreeView treeView = new TreeView();
	private final StandingPanel standing = new StandingPanel();
	private @Nullable NodeDetail detail;
	private @Nullable String selected;
	private boolean confirmRespec;
	/** True when the detail panel has a row of buttons under it. */
	private boolean detailButtons;
	private long lastRespec = Long.MIN_VALUE;
	private @Nullable Component respecBlock;
	private int respecCheckedAt = Integer.MIN_VALUE;
	private String builtFrom = "";
	private @Nullable List<String> lastSlots;
	private @Nullable Map<String, Integer> lastNodes;

	private int px;
	private int py;
	private int pw;
	private int ph;
	private boolean compact;
	private int statsY;
	private int tabsY;
	private int contentY;
	private int contentH;
	private int treeW;
	private int detailX;
	private int detailW;
	private int footerY;
	private double detailScroll;
	private int detailContentH;

	private @Nullable String dragAbility;
	private int dragFromSlot = -1;
	private double pressX;
	private double pressY;
	private boolean dragging;

	private ForceScreen(boolean respec, @Nullable String tab) {
		super(Component.translatable("screen.oldrepublic.skills.title"));
		confirmRespec = respec;

		if (tab != null) {
			lastTab = tab;
		}
	}

	/** Opens the screen, or says nothing stirs yet when the player is not awakened. */
	public static void open(Minecraft mc, boolean respec, @Nullable String tab) {
		LocalPlayer player = mc.player;

		if (player == null) {
			return;
		}

		ForceData data = player.getAttached(ForceAttachments.FORCE);

		if (data == null || !data.isAwakened()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"));
			return;
		}

		mc.gui.setScreen(new ForceScreen(respec, tab));
	}

	/** Opens the screen on {@code tab} with {@code node} selected (client game tests, links from other screens). */
	public static void openOn(Minecraft mc, String tab, @Nullable String node) {
		if (node != null) {
			LAST_SELECTED.put(tab, node);
		}

		open(mc, false, tab);
	}

	/** Marks a node as just learned so it flashes (called when the synced data shows it). */
	static void flash(String nodeId) {
		flashNode = nodeId;
		flashAt = QuestClient.ticks();
	}

	/** Shows {@code tab} the next time the screen opens (or now, if it is open). */
	static void showTab(String tab) {
		lastTab = tab;
	}

	// ---------------------------------------------------------------- data

	private @Nullable ForceData force() {
		return minecraft.player == null ? null : minecraft.player.getAttached(ForceAttachments.FORCE);
	}

	private SkillData skills() {
		SkillData data = minecraft.player == null ? null : Skills.data(minecraft.player);
		return data == null ? new SkillData() : data;
	}

	private SkillTreeSet trees() {
		return SkillTrees.client();
	}

	private @Nullable SkillTree openTree() {
		String id = tabs.selectedId();
		return id == null || STANDING.equals(id) ? null : trees().tree(id);
	}

	private boolean standingOpen() {
		return STANDING.equals(tabs.selectedId());
	}

	// ---------------------------------------------------------------- layout and widgets

	@Override
	protected void init() {
		detail = new NodeDetail(font);
		layout();
		LocalPlayer player = minecraft.player;
		SkillContext ctx = player == null ? null : Skills.context(player);
		tabs.setTabs(tabList(ctx));

		if (!lastTab.isEmpty()) {
			tabs.select(lastTab);
		}

		lastTab = tabs.selectedId() == null ? "" : tabs.selectedId();
		SkillTree tree = openTree();

		if (tree != null) {
			treeView.layout(tree, px + 10, contentY, treeW, contentH);
			String remembered = LAST_SELECTED.get(tree.id());
			selected = remembered != null && tree.nodes().stream().anyMatch(n -> n.id().equals(remembered)) ? remembered
					: tree.nodes().isEmpty() ? null : tree.nodes().getFirst().id();
		} else {
			selected = null;
		}

		addFooterWidgets();
		detailButtons = false;

		if (confirmRespec) {
			addConfirmWidgets();
		} else if (tree != null && selected != null && player != null && ctx != null) {
			addDetailWidgets(player, ctx);
		}

		builtFrom = signature();
	}

	private void layout() {
		pw = Math.min(width - 8, MAX_W);
		ph = Math.min(height - 8, MAX_H);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		compact = ph < 236;
		statsY = py + 40;
		tabsY = statsY + (compact ? 14 : 26) + 2;
		contentY = tabsY + TabStrip.HEIGHT + 4;
		footerY = py + ph - 28;
		contentH = footerY - 6 - contentY;
		int inner = pw - 20;
		treeW = Math.round(inner * 0.6f);
		detailX = px + 10 + treeW + 6;
		detailW = px + pw - 10 - detailX;
	}

	private List<TabStrip.Tab> tabList(@Nullable SkillContext ctx) {
		List<TabStrip.Tab> list = new ArrayList<>();

		for (SkillTree tree : trees().trees()) {
			if (ctx == null || ctx.visibleTrees().contains(tree.id())) {
				list.add(new TabStrip.Tab(tree.id(), Component.translatable(tree.nameKey() + ".tab"), tree.tint()));
			}
		}

		if (ctx != null && ctx.path() != Path.NONE) {
			list.add(new TabStrip.Tab(STANDING, Component.translatable("screen.oldrepublic.skills.tab.standing")));
		}

		return list;
	}

	private void addFooterWidgets() {
		addRenderableWidget(new HoloButton(px + pw - 10 - DONE_W, footerY + 2, DONE_W, 18, Component.translatable("gui.done"), b -> onClose()));
		HoloButton respec = new HoloButton(px + pw - 10 - DONE_W - 6 - RESPEC_W, footerY + 2, RESPEC_W, 18,
				Component.translatable("screen.oldrepublic.skills.respec"), b -> openConfirm());
		updateRespecBlock(true);
		respec.active = respecBlock == null && !confirmRespec;
		respec.setTooltip(Tooltip.create(respecBlock != null ? respecBlock : Component.translatable("screen.oldrepublic.skills.respec.tooltip")));
		addRenderableWidget(respec);
	}

	private void addConfirmWidgets() {
		int cardW = Math.min(240, pw - 40);
		int cx = px + (pw - cardW) / 2;
		int by = confirmCardY() + confirmCardH() - 24;
		int bw = (cardW - 30) / 2;
		HoloButton yes = new HoloButton(cx + 10, by, bw, 18, Component.translatable("screen.oldrepublic.skills.respec.confirm"), b -> {
			ClientPlayNetworking.send(RespecConfirmPayload.INSTANCE);
			confirmRespec = false;
			rebuildWidgets();
		}, true);
		yes.active = respecBlock == null;
		addRenderableWidget(yes);
		addRenderableWidget(new HoloButton(cx + cardW - 10 - bw, by, bw, 18, Component.translatable("gui.cancel"), b -> {
			confirmRespec = false;
			rebuildWidgets();
		}));
	}

	private void addDetailWidgets(LocalPlayer player, SkillContext ctx) {
		SkillNode node = trees().node(selected);

		if (node == null) {
			return;
		}

		SkillData data = skills();
		NodeView view = NodeView.of(trees(), data, ctx, node);
		int by = contentY + contentH - BUTTON_H;
		int rank = data.rank(node.id());

		if (node.isAbility() && rank > 0) {
			String ability = node.ability() != null ? node.ability() : node.id();
			int current = data.slotOf(ability);
			Component label = Component.translatable("screen.oldrepublic.skills.assign");
			int labelW = font.width(label) + 6;
			int bw = (detailW - labelW - 3 * 3) / 4;
			int open = Skills.slots(player);

			for (int i = 0; i < 4; i++) {
				int slot = i;
				boolean here = slot == current;
				HoloButton button = new HoloButton(detailX + labelW + i * (bw + 3), by, bw, BUTTON_H, Component.literal(Integer.toString(i + 1)),
						b -> assign(slot, here ? "" : ability), here);
				button.active = slot < open;
				Component keyName = ForceKeys.SLOTS.get(slot).getTranslatedKeyMessage();
				button.setTooltip(Tooltip.create(slot >= open ? Component.translatable("screen.oldrepublic.skills.slot.locked")
						: here ? Component.translatable("screen.oldrepublic.skills.slot.clear", keyName)
						: Component.translatable("screen.oldrepublic.skills.slot.assign", slot + 1, keyName)));
				addRenderableWidget(button);
			}

			detailButtons = true;
			return;
		}

		if (node.kind() == NodeKind.FORM && rank > 0) {
			boolean activeForm = node.id().equals(data.activeForm);
			HoloButton form = new HoloButton(detailX, by, detailW, BUTTON_H,
					Component.translatable(activeForm ? "screen.oldrepublic.skills.form.unformed" : "screen.oldrepublic.skills.form.set"),
					b -> ClientPlayNetworking.send(new SetFormPayload(activeForm ? "" : node.id())), !activeForm);
			form.active = activeForm || view.state() == NodeSocket.State.OWNED;
			addRenderableWidget(form);
			detailButtons = true;
			return;
		}

		if (!node.grantedOnly() && rank < node.maxRank()) {
			SkillRules.LearnCheck check = view.check();
			String key = check.isSwitch() ? "screen.oldrepublic.skills.switch" : rank > 0 ? "screen.oldrepublic.skills.rank_up" : "screen.oldrepublic.skills.learn";
			HoloButton learn = new HoloButton(detailX, by, detailW, BUTTON_H, Component.translatable(key, check.cost()),
					b -> ClientPlayNetworking.send(new LearnNodePayload(node.id())), check.ok());
			learn.active = check.ok();

			if (!check.ok() && view.reason() != null) {
				learn.setTooltip(Tooltip.create(view.reason()));
			} else if (check.isSwitch() && check.replaces() != null) {
				learn.setTooltip(Tooltip.create(Component.translatable("screen.oldrepublic.skills.detail.replaces",
						Component.translatable(check.replaces().nameKey()))));
			}

			addRenderableWidget(learn);
			detailButtons = true;
		}
	}

	/** What the widgets were built from; a change rebuilds them. */
	private String signature() {
		SkillData data = skills();
		ForceData force = force();
		LocalPlayer player = minecraft.player;
		return tabs.selectedId() + "|" + selected + "|" + confirmRespec + "|" + data.nodes + "|" + data.slots + "|" + data.openSlots + "|" + data.activeForm
				+ "|" + data.lastRespec + "|" + (force == null ? "" : force.level + "/" + force.skillPoints) + "|" + ClientAbilityInfo.received()
				+ "|" + trees().nodes().size() + "|" + (player == null ? "" : Identity.alignment(player) + "/" + Identity.path(player)) + "|" + respecBlockKey();
	}

	private String respecBlockKey() {
		return respecBlock == null ? "" : respecBlock.getString();
	}

	@Override
	public void tick() {
		super.tick();
		SkillData data = skills();
		watchChanges(data);
		updateRespecBlock(false);

		if (!builtFrom.equals(signature())) {
			rebuildWidgets();
		}
	}

	/** Plays the slot sound when a slot changes and flashes nodes that were just learned. */
	private void watchChanges(SkillData data) {
		List<String> slots = List.copyOf(data.slots);

		if (lastSlots != null && !lastSlots.equals(slots)) {
			SkillClient.playSlot();
		}

		lastSlots = slots;
		Map<String, Integer> nodes = new LinkedHashMap<>(data.nodes);

		if (lastNodes != null) {
			for (Map.Entry<String, Integer> e : nodes.entrySet()) {
				if (e.getValue() > lastNodes.getOrDefault(e.getKey(), 0)) {
					flash(e.getKey());
				}
			}

		}

		lastNodes = nodes;

		if (lastRespec != Long.MIN_VALUE && data.lastRespec != lastRespec) {
			// The respec went through: drop the confirmation.
			confirmRespec = false;
		}

		lastRespec = data.lastRespec;
	}

	/** Recomputes (every half second) why a respec is not possible here, or null. */
	private void updateRespecBlock(boolean force) {
		int now = QuestClient.ticks();
		LocalPlayer player = minecraft.player;

		if (player == null || (!force && now - respecCheckedAt < RESPEC_RECHECK_TICKS)) {
			return;
		}

		respecCheckedAt = now;
		SkillBalance b = new SkillBalance();
		Map<String, Float> info = ClientAbilityInfo.values("skills");
		b.respecCooldownTicks = Math.round(info.getOrDefault("respec_cooldown_ticks", (float) b.respecCooldownTicks));
		int radius = Math.round(info.getOrDefault("respec_radius", (float) b.respecAnchorRadius));
		SkillData data = skills();
		long time = player.level().getGameTime();
		String key = SkillRules.respecBlock(data, time, false, SkillService.nearRespecAnchor(player, radius), b);
		respecBlock = key == null ? null : Component.translatable(key, SkillService.durationText(Math.max(0, SkillRules.respecReadyAt(data, b) - time)));
	}

	private void openConfirm() {
		confirmRespec = true;
		rebuildWidgets();
	}

	private void assign(int slot, String ability) {
		ClientPlayNetworking.send(new AssignSlotPayload(slot, ability));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		ForceData force = force();
		LocalPlayer player = minecraft.player;
		float time = UiAnim.time(partialTick);

		if (force != null && player != null) {
			SkillData data = skills();
			SkillContext ctx = Skills.context(player);
			Ui.panel(graphics, px, py, pw, ph);
			Ui.scanlines(graphics, px + 6, py + 6, pw - 12, ph - 12, 0x06FFFFFF, time);
			drawHeader(graphics, force, player, time);

			if (compact) {
				drawStatsLine(graphics, force, player);
			} else {
				drawStatBlocks(graphics, force, player, time);
			}

			tabs.render(graphics, font, px + 10, tabsY, pw - 20, mouseX, mouseY, partialTick);
			drawContent(graphics, player, data, ctx, force, mouseX, mouseY, partialTick, time);
			drawFooter(graphics, player, data, mouseX, mouseY, time);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		if (dragAbility != null && !dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > DRAG_DISTANCE) {
			dragging = true;
		}

		if (dragging && dragAbility != null) {
			// The dragged power follows the cursor.
			Ui.sprite(graphics, abilityIcon(dragAbility), mouseX - 8, mouseY - 8, 16, 16, 0.85f);
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, ForceData data, LocalPlayer player, float time) {
		int ex = px + 10;
		int ey = py + 8;
		float xp = data.xpToNext <= 0 ? 1f : Mth.clamp((float) data.xp / data.xpToNext, 0f, 1f);
		Ui.sprite(g, Ui.EMBLEM_LARGE, ex, ey, EMBLEM, EMBLEM, emblemTint(player));
		Ui.ring(g, ex - 1, ey - 1, EMBLEM + 2, EMBLEM + 2, 10, xp, UiColors.BRASS_LIGHT, 0x70101418, 1f);
		String level = Integer.toString(data.level);
		g.text(font, level, ex + (EMBLEM - font.width(level)) / 2 + 1, ey + 10, UiColors.BRASS_LIGHT, true);

		int tx = ex + EMBLEM + 8;
		int tw = px + pw - 10 - tx;
		Ui.header(g, font, title, tx, py + 9, tw, time);

		int rowY = py + 25;
		Component levelText = Component.translatable("screen.oldrepublic.skills.header.level", data.level);
		g.text(font, levelText, tx, rowY, UiColors.BRASS_LIGHT, true);
		String xpText = Component.translatable("screen.oldrepublic.skills.xp", data.xp, data.xpToNext).getString();
		int xpW = font.width(xpText);
		g.text(font, xpText, px + pw - 10 - xpW, rowY, UiColors.TEXT_MUTED, true);
		int barX = tx + font.width(levelText) + 6;
		int barW = px + pw - 10 - xpW - 6 - barX;

		if (barW > 20) {
			Ui.bar(g, barX, rowY + 1, barW, 6, xp, 0xFFFFF0C8, UiColors.BRASS, 10, 1f);
		}
	}

	/** After the Crossing the emblem takes up to 35% of the side's colour, by how far alignment leans. */
	private static int emblemTint(LocalPlayer player) {
		if (Identity.path(player) == Path.NONE) {
			return 0xFFFFFFFF;
		}

		int alignment = Identity.alignment(player);
		float amount = 0.35f * Mth.clamp(Math.abs(alignment) / 100f, 0f, 1f);
		return ARGB.srgbLerp(amount, 0xFFFFFFFF, alignment >= 0 ? ALIGNMENT_TINT_JEDI : ALIGNMENT_TINT_SITH);
	}

	private void drawStatBlocks(GuiGraphicsExtractor g, ForceData data, LocalPlayer player, float time) {
		int bw = (pw - 20 - 3 * 4) / 4;
		int bh = 24;
		int x = px + 10;
		int y = statsY;

		// Resonance, with a drifting line of notch script beside the number.
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.skills.stat.resonance"), x + 5, y + 3, UiColors.TEXT_DIM, true);
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
		g.text(font, Component.translatable("screen.oldrepublic.skills.stat.energy"), x + 5, y + 3, UiColors.TEXT_DIM, true);
		String energyText = Math.round(data.energy) + "/" + Math.round(data.maxEnergy);
		g.text(font, energyText, x + 5, y + 13, UiColors.HOLO_BRIGHT, true);
		miniBar(g, x + 5 + font.width(energyText) + 5, y + 14, x + bw - 5, energy, ARGB.srgbLerp(energy, 0xFF4E8FBF, 0xFFE4F6FF), UiColors.HOLO_DEEP,
				Math.max(1, Math.round(data.maxEnergy / 10f)));
		x += bw + 4;

		// Skill points, as a number and a row of pips.
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.skills.stat.skill_points"), x + 5, y + 3, UiColors.TEXT_DIM, true);
		String points = Integer.toString(data.skillPoints);
		g.text(font, points, x + 5, y + 13, data.skillPoints > 0 ? UiColors.HOLO_BRIGHT : UiColors.TEXT_DIM, true);

		if (data.skillPoints > 0) {
			int shown = Math.min(data.skillPoints, Math.max(1, (bw - 30) / 6));
			Ui.pips(g, x + 5 + font.width(points) + 5, y + 14, shown, shown, UiColors.HOLO, 1f);
		}

		x += bw + 4;

		// Path and alignment (after the Crossing), in the side's colour.
		Ui.inset(g, x, y, bw, bh, 1f);
		g.text(font, Component.translatable("screen.oldrepublic.skills.stat.path"), x + 5, y + 3, UiColors.TEXT_DIM, true);
		Path path = Identity.path(player);
		int pathColor = path == Path.JEDI ? ALIGNMENT_TINT_JEDI : path == Path.SITH ? 0xFFE06A70 : UiColors.TEXT;
		String pathName = NodeView.pathName(path).getString();
		String align = path == Path.NONE ? "" : NodeView.signed(Identity.alignment(player));
		int alignW = align.isEmpty() ? 0 : font.width(align) + 4;
		g.text(font, Ui.fit(font, pathName, bw - 10 - alignW), x + 5, y + 13, pathColor, true);

		if (!align.isEmpty()) {
			g.text(font, align, x + bw - 5 - font.width(align), y + 13, UiColors.TEXT_MUTED, true);
		}
	}

	/** A 5 px bar from {@code x} to {@code right}, skipped when there is no room for it. */
	private static void miniBar(GuiGraphicsExtractor g, int x, int y, int right, float fill, int top, int bottom, int segments) {
		if (right - x >= 12) {
			Ui.bar(g, x, y, right - x, 5, fill, top, bottom, segments, 1f);
		}
	}

	/** Small windows: the same numbers on one line. */
	private void drawStatsLine(GuiGraphicsExtractor g, ForceData data, LocalPlayer player) {
		Component line = Component.translatable("screen.oldrepublic.skills.stats_line", String.format(Locale.ROOT, "%,d", data.resonance),
				Math.round(data.energy), Math.round(data.maxEnergy), data.skillPoints, NodeView.pathName(Identity.path(player)));
		Ui.inset(g, px + 10, statsY - 1, pw - 20, 13, 1f);
		g.text(font, Ui.fit(font, line.getString(), pw - 28), px + 14, statsY + 2, UiColors.TEXT_MUTED, true);
	}

	private void drawContent(GuiGraphicsExtractor g, LocalPlayer player, SkillData data, SkillContext ctx, ForceData force, int mouseX, int mouseY,
			float partialTick, float time) {
		if (standingOpen()) {
			standing.render(g, font, px + 10, contentY, pw - 20, contentH, partialTick);
			return;
		}

		SkillTree tree = openTree();

		if (tree == null) {
			Ui.inset(g, px + 10, contentY, pw - 20, contentH, 1f);
			String text = Component.translatable(trees().trees().isEmpty() ? "screen.oldrepublic.skills.no_trees" : "screen.oldrepublic.skills.nothing_yet").getString();
			int ly = contentY + contentH / 2 - 8;

			for (String line : Ui.wrap(font, text, pw - 60, 3)) {
				g.text(font, line, px + pw / 2 - font.width(line) / 2, ly, UiColors.TEXT_DIM, true);
				ly += 10;
			}

			return;
		}

		Map<String, NodeView> views = new LinkedHashMap<>();

		for (SkillNode node : tree.nodes()) {
			views.put(node.id(), NodeView.of(trees(), data, ctx, node));
		}

		boolean modal = confirmRespec;
		String hovered = modal ? null : treeView.nodeAt(mouseX, mouseY);
		Ui.inset(g, px + 10, contentY, treeW, contentH, 1f);

		if (tree.tint() != 0) {
			// The side tint is only a thin rule along the top of the tree well.
			g.fill(px + 12, contentY + 1, px + 10 + treeW - 2, contentY + 2, Ui.a(tree.tint(), 0.6f));
		}

		treeView.render(g, font, views, selected, hovered, flashNode, flashAt, time, modal ? 0.35f : 1f);
		Ui.inset(g, detailX, contentY, detailW, contentH, 1f);
		NodeView view = selected == null ? null : views.get(selected);

		if (view != null && detail != null) {
			int bottom = contentY + contentH - (detailButtons ? BUTTON_H + 4 : 0);
			int top = contentY + 5;
			g.enableScissor(detailX + 1, contentY + 1, detailX + detailW - 1, bottom);
			detailContentH = detail.render(g, detailX + 6, top - (int) detailScroll, detailW - 12, view, trees(), data, ctx, force, player);
			g.disableScissor();
			clampScroll(bottom - top);

			if (detailContentH > bottom - top) {
				drawScrollbar(g, detailX + detailW - 4, top, bottom - top - 2);
			}
		}

		if (modal) {
			drawConfirmCard(g, data, time);
			return;
		}

		if (hovered != null && !dragging) {
			NodeView hv = views.get(hovered);

			if (hv != null) {
				Component tip = Component.translatable(hv.node().nameKey());
				Component line = hv.reason() != null ? hv.reason() : Component.translatable("skill.oldrepublic.state." + hv.state().name().toLowerCase(Locale.ROOT));
				g.setTooltipForNextFrame(font, Tooltip.splitTooltip(minecraft, tip.copy().append("\n").append(line)), mouseX, mouseY);
			}
		}
	}

	private void drawScrollbar(GuiGraphicsExtractor g, int x, int top, int height) {
		float visible = (float) height / Math.max(1, detailContentH);
		int barH = Math.max(8, Math.round(height * visible));
		float max = Math.max(1, detailContentH - height);
		int barY = top + Math.round((height - barH) * (float) (detailScroll / max));
		g.fill(x, top, x + 2, top + height, Ui.a(UiColors.STEEL_DARK, 0.8f));
		g.fill(x, barY, x + 2, barY + barH, Ui.a(UiColors.HOLO_DIM, 0.9f));
	}

	private void clampScroll(int visible) {
		detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, detailContentH - visible));
	}

	private int confirmCardH() {
		return Math.min(contentH - 8, 104);
	}

	private int confirmCardY() {
		return contentY + (contentH - confirmCardH()) / 2;
	}

	/** The in-panel respec confirmation: refund, loss, what stays, the cooldown. */
	private void drawConfirmCard(GuiGraphicsExtractor g, SkillData data, float time) {
		int cardW = Math.min(240, pw - 40);
		int x = px + (pw - cardW) / 2;
		int y = confirmCardY();
		int h = confirmCardH();
		g.fill(px + 10, contentY, px + pw - 10, contentY + contentH, 0x80000000);
		Ui.panel(g, x, y, cardW, h);
		Ui.header(g, font, Component.translatable("screen.oldrepublic.skills.respec.title"), x + 10, y + 8, cardW - 20, time);
		Float lossPct = ClientAbilityInfo.value("skills", "respec_loss_pct");
		float loss = (lossPct == null ? new SkillBalance().respecLoss * 100f : lossPct) / 100f;
		int spent = data.spentTotal();
		int refund = SkillRules.respecRefund(spent, loss);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("screen.oldrepublic.skills.respec.refund", refund, spent - refund));
		lines.add(Component.translatable("screen.oldrepublic.skills.respec.keeps"));
		lines.add(respecBlock != null ? respecBlock : Component.translatable("screen.oldrepublic.skills.respec.cooldown_note"));
		int ly = y + 24;

		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? UiColors.TEXT : i == 2 && respecBlock != null ? UiColors.AMBER : UiColors.TEXT_MUTED;

			for (String line : Ui.wrap(font, lines.get(i).getString(), cardW - 20, 2)) {
				g.text(font, line, x + 10, ly, color, true);
				ly += 10;
			}
		}
	}

	private void drawFooter(GuiGraphicsExtractor g, LocalPlayer player, SkillData data, int mouseX, int mouseY, float time) {
		int open = Skills.slots(player);
		int hoveredSlot = slotAt(mouseX, mouseY);

		for (int i = 0; i < 4; i++) {
			int sx = slotX(i);
			int sy = footerY + 1;
			String key = Ui.fit(font, ForceKeys.SLOTS.get(i).getTranslatedKeyMessage().getString(), 14);
			boolean locked = i >= open;
			g.text(font, key, sx - 3 - font.width(key), sy + 6, locked ? UiColors.TEXT_DIM : UiColors.TEXT_MUTED, true);
			Ui.sprite(g, Ui.SLOT, sx, sy, SLOT, SLOT, locked ? 0.45f : 1f);
			String ability = locked ? "" : data.slot(i);

			if (!ability.isEmpty()) {
				Ui.sprite(g, abilityIcon(ability), sx + 2, sy + 2, 16, 16, dragging && i == dragFromSlot ? 0.35f : 1f);
			}

			if (locked) {
				drawLock(g, sx + 7, sy + 6, UiColors.STEEL_LIGHT);
			} else if (dragging && i == hoveredSlot) {
				Ui.ring(g, sx - 1, sy - 1, SLOT + 2, SLOT + 2, 3, 1f, UiColors.HOLO_BRIGHT, 0, 0.6f + 0.4f * UiAnim.pulse(time, 12f));
			} else if (i == hoveredSlot) {
				g.fill(sx + 2, sy + 2, sx + SLOT - 2, sy + SLOT - 2, 0x14FFFFFF);
			}
		}

		int hintX = slotX(4) - 6;
		int hintW = px + pw - 10 - DONE_W - 6 - RESPEC_W - 8 - hintX;

		if (hintW > 40) {
			String hint = Ui.fit(font, Component.translatable("screen.oldrepublic.skills.slot_hint").getString(), hintW);
			g.text(font, hint, hintX, footerY + 7, UiColors.TEXT_DIM, true);
		}

		if (hoveredSlot >= 0 && !dragging && !confirmRespec) {
			g.setTooltipForNextFrame(font, Tooltip.splitTooltip(minecraft, slotTooltip(hoveredSlot, open, data)), mouseX, mouseY);
		}
	}

	private Component slotTooltip(int slot, int open, SkillData data) {
		Component keyName = ForceKeys.SLOTS.get(slot).getTranslatedKeyMessage();

		if (slot >= open) {
			return Component.translatable("screen.oldrepublic.skills.slot.locked");
		}

		String ability = data.slot(slot);

		if (ability.isEmpty()) {
			return Component.translatable("screen.oldrepublic.skills.slot.empty", slot + 1, keyName);
		}

		SkillNode node = trees().abilityNode(ability);
		Component name = node != null ? Component.translatable(node.nameKey()) : Component.literal(ability);
		return Component.translatable("screen.oldrepublic.skills.slot.filled", slot + 1, keyName, name);
	}

	private int slotX(int i) {
		return px + 10 + 16 + i * SLOT_STEP;
	}

	private int slotAt(double mouseX, double mouseY) {
		for (int i = 0; i < 4; i++) {
			int sx = slotX(i);

			if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= footerY + 1 && mouseY < footerY + 1 + SLOT) {
				return i;
			}
		}

		return -1;
	}

	private Identifier abilityIcon(String ability) {
		SkillNode node = trees().abilityNode(ability);
		return node != null ? node.icon() : Ui.sprite("push_icon");
	}

	/** A 6x7 pixel padlock. */
	private static void drawLock(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x + 1, y, x + 5, y + 1, color);
		g.fill(x + 1, y, x + 2, y + 3, color);
		g.fill(x + 4, y, x + 5, y + 3, color);
		g.fill(x, y + 3, x + 6, y + 7, color);
		g.fill(x + 2, y + 4, x + 4, y + 6, UiColors.OUTLINE);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (tabs.mouseClicked(event)) {
			AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
			lastTab = tabs.selectedId() == null ? "" : tabs.selectedId();
			detailScroll = 0;
			rebuildWidgets();
			return true;
		}

		if (confirmRespec || event.button() != 0) {
			return confirmRespec;
		}

		if (standingOpen()) {
			return standing.mouseClicked(event, doubleClick);
		}

		String node = treeView.nodeAt(event.x(), event.y());
		SkillTree tree = openTree();

		if (node != null && tree != null) {
			if (!node.equals(selected)) {
				selected = node;
				LAST_SELECTED.put(tree.id(), node);
				detailScroll = 0;
				rebuildWidgets();
			}

			SkillNode skillNode = trees().node(node);

			if (skillNode != null && skillNode.isAbility() && skills().owns(node)) {
				startDrag(skillNode.ability() != null ? skillNode.ability() : node, -1, event);
			}

			return true;
		}

		int slot = slotAt(event.x(), event.y());

		if (slot >= 0 && minecraft.player != null && slot < Skills.slots(minecraft.player) && !skills().slot(slot).isEmpty()) {
			startDrag(skills().slot(slot), slot, event);
			return true;
		}

		return false;
	}

	private void startDrag(String ability, int fromSlot, MouseButtonEvent event) {
		dragAbility = ability;
		dragFromSlot = fromSlot;
		pressX = event.x();
		pressY = event.y();
		dragging = false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragAbility != null) {
			if (dragging) {
				int target = slotAt(event.x(), event.y());
				int open = minecraft.player == null ? 3 : Skills.slots(minecraft.player);

				if (target >= 0 && target < open && target != dragFromSlot) {
					assign(target, dragAbility);
				} else if (target < 0 && dragFromSlot >= 0) {
					// Dropped off the slot bar: clear the slot it came from.
					assign(dragFromSlot, "");
				}
			}

			dragAbility = null;
			dragFromSlot = -1;
			dragging = false;
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= detailX && mouseX < detailX + detailW && mouseY >= contentY && mouseY < contentY + contentH) {
			detailScroll -= scrollY * font.lineHeight * 2;
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ForceKeys.FORCE_SCREEN.matches(event)) {
			onClose();
			return true;
		}

		int key = event.key();

		if (!confirmRespec && key >= InputConstants.KEY_1 && key <= InputConstants.KEY_4 && selected != null && minecraft.player != null) {
			// Number keys put the selected power into that slot.
			SkillNode node = trees().node(selected);
			int slot = key - InputConstants.KEY_1;

			if (node != null && node.isAbility() && skills().owns(node.id()) && slot < Skills.slots(minecraft.player)) {
				assign(slot, node.ability() != null ? node.ability() : node.id());
				return true;
			}
		}

		return super.keyPressed(event);
	}
}

package com.oldrepublic.client.skill;

import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityCosts;
import com.oldrepublic.client.ability.ClientAbilityInfo;
import com.oldrepublic.client.ui.NodeSocket;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.SkillContext;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillTree;
import com.oldrepublic.skill.SkillTreeSet;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The right-hand detail panel of the Force screen for the selected node: icon, name, kind and tree,
 * rank pips, its state (and the first failed requirement when locked, or why it is dormant), the
 * description, the live numbers from the server's ability info, alignment effects on side powers,
 * mastery, every requirement with a check or a cross, and warnings (a mentor will teach this, a
 * switch replaces a node without refund). Draws into a scrolled, clipped area; the buttons under it
 * belong to the screen.
 */
final class NodeDetail {
	private static final String CHECK = "✔";
	private static final String CROSS = "✘";

	private final Font font;
	private int y;
	private int x;
	private int w;

	NodeDetail(Font font) {
		this.font = font;
	}

	/**
	 * Draws the panel content from {@code top} (already scrolled) and returns its full height.
	 */
	int render(GuiGraphicsExtractor g, int left, int top, int width, NodeView view, SkillTreeSet trees, SkillData data, SkillContext ctx,
			@Nullable ForceData force, LocalPlayer player) {
		x = left;
		y = top;
		w = width;
		SkillNode node = view.node();
		SkillTree tree = trees.tree(node.tree());

		drawTitle(g, view, tree);
		drawState(g, view);
		paragraph(g, Component.translatable(node.descKey()), UiColors.TEXT_MUTED);
		y += 3;
		drawNumbers(g, trees, node, player);
		drawAlignmentEffect(g, node, ctx);

		if (node.isAbility() && view.rank() > 0 && force != null) {
			drawMastery(g, node, force);
		}

		if (!(view.rank() >= node.maxRank() && view.state() != NodeSocket.State.DORMANT)) {
			drawRequirements(g, view, trees, data, ctx);
		}

		drawWarnings(g, view, ctx);
		return y - top;
	}

	// ---------------------------------------------------------------- parts

	private void drawTitle(GuiGraphicsExtractor g, NodeView view, @Nullable SkillTree tree) {
		SkillNode node = view.node();
		NodeSocket.draw(g, x, y, node.icon(), view.state(), view.granted(), false, false, 0f, 1f);
		int tx = x + NodeSocket.SIZE + 5;
		int tw = x + w - tx;
		int nameColor = switch (view.state()) {
			case OWNED -> UiColors.BRASS_LIGHT;
			case DORMANT -> UiColors.AMBER;
			case LOCKED -> UiColors.TEXT_MUTED;
			case AVAILABLE -> UiColors.TEXT;
		};
		g.text(font, Ui.fit(font, Component.translatable(node.nameKey()).getString(), tw), tx, y + 1, nameColor, true);
		Component kind = Component.translatable("skill.oldrepublic.kind." + node.kind().id());
		String sub = tree == null ? kind.getString()
				: Component.translatable("screen.oldrepublic.skills.detail.kind_tree", kind, Component.translatable(tree.nameKey())).getString();
		int subColor = tree != null && tree.tint() != 0 ? Ui.a(tree.tint(), 0.9f) : UiColors.TEXT_DIM;
		g.text(font, Ui.fit(font, sub, tw), tx, y + 11, subColor, true);
		y += NodeSocket.SIZE + 5;

		if (node.maxRank() > 1) {
			Component rank = Component.translatable("screen.oldrepublic.skills.detail.rank", view.rank(), node.maxRank());
			g.text(font, rank, x, y, UiColors.TEXT_MUTED, true);
			Ui.pips(g, x + font.width(rank) + 5, y + 1, node.maxRank(), view.rank(), UiColors.HOLO, 1f);
			y += 11;
		}
	}

	private void drawState(GuiGraphicsExtractor g, NodeView view) {
		Component state;
		int color;

		switch (view.state()) {
			case OWNED -> {
				state = Component.translatable(view.granted() ? "skill.oldrepublic.state.granted" : "skill.oldrepublic.state.owned");
				color = view.granted() ? UiColors.BRASS_LIGHT : UiColors.HOLO;
			}
			case AVAILABLE -> {
				state = Component.translatable(view.check().isSwitch() ? "skill.oldrepublic.state.switch" : "skill.oldrepublic.state.available");
				color = UiColors.OK;
			}
			case DORMANT -> {
				state = Component.translatable("skill.oldrepublic.state.dormant_because", view.reason());
				color = UiColors.AMBER;
			}
			case LOCKED -> {
				Component reason = view.reason() != null ? view.reason() : Component.translatable("skill.oldrepublic.state.locked");
				state = Component.translatable("skill.oldrepublic.state.locked_because", reason);
				color = UiColors.AMBER;
			}
			default -> throw new IllegalStateException();
		}

		g.fill(x, y + 3, x + 3, y + 6, color);
		y = wrapped(g, state.getString(), x + 6, w - 6, color, 3);
		y += 3;
	}

	/** The node's live numbers, from the ability's info map or the node's info source. */
	private void drawNumbers(GuiGraphicsExtractor g, SkillTreeSet trees, SkillNode node, LocalPlayer player) {
		if (node.info().isEmpty()) {
			return;
		}

		String source = !node.infoSource().isEmpty() ? node.infoSource() : trees.infoAbility(node);
		Map<String, Float> values = ClientAbilityInfo.values(source);
		boolean any = false;

		for (String key : node.info()) {
			Float value = values.get(key);

			if (value == null) {
				continue;
			}

			any = true;
			row(g, Component.translatable("skill.oldrepublic.info." + key), Component.translatable(unitKey(key), format(value)), UiColors.HOLO_BRIGHT);
		}

		if (!any) {
			paragraph(g, Component.translatable("screen.oldrepublic.skills.detail.no_data"), UiColors.TEXT_DIM);
		}

		if (node.isAbility()) {
			String ability = node.ability() != null ? node.ability() : node.id();
			float mult = ClientAbilityInfo.costMultiplier(player, ability);

			if (Math.abs(mult - 1f) > 0.001f) {
				row(g, Component.translatable("screen.oldrepublic.skills.detail.cost_mult"), Component.translatable("skill.oldrepublic.unit.multiplier", format(mult)),
						mult < 1f ? UiColors.OK : UiColors.AMBER);
			}
		}

		y += 3;
	}

	/** Side powers: opposed (cost up, no mastery) or refused by the player's alignment. */
	private void drawAlignmentEffect(GuiGraphicsExtractor g, SkillNode node, SkillContext ctx) {
		if (!node.isAbility()) {
			return;
		}

		AbilityCosts.Opposition opposition = AbilityCosts.opposition(node.tree(), ctx.alignment());

		if (opposition == AbilityCosts.Opposition.OPPOSED) {
			Float mult = ClientAbilityInfo.value("skills", "opposed_cost_mult");
			paragraph(g, Component.translatable("screen.oldrepublic.skills.detail.opposed", format(mult == null ? 1.5f : mult)), UiColors.AMBER);
			y += 3;
		} else if (opposition == AbilityCosts.Opposition.REFUSED) {
			paragraph(g, Component.translatable("message.oldrepublic.skill.refused." + node.tree()), UiColors.DANGER);
			y += 3;
		}
	}

	private void drawMastery(GuiGraphicsExtractor g, SkillNode node, ForceData force) {
		String ability = node.ability() != null ? node.ability() : node.id();
		int points = force.mastery(ability);
		int rank = Progression.masteryRank(points);
		Component label = Component.translatable("screen.oldrepublic.skills.detail.mastery");
		g.text(font, label, x, y, UiColors.TEXT_MUTED, true);
		int px = x + font.width(label) + 5;
		Ui.pips(g, px, y + 1, 3, rank, UiColors.BRASS_LIGHT, 1f);
		int next = rankStart(rank + 1);
		Component progress = next < 0 ? Component.translatable("screen.oldrepublic.skills.detail.mastery_max")
				: Component.translatable("screen.oldrepublic.skills.detail.mastery_next", next - points);
		String text = Ui.fit(font, progress.getString(), x + w - (px + 22));
		g.text(font, text, x + w - font.width(text), y, UiColors.TEXT_DIM, true);
		y += 12;
	}

	private void drawRequirements(GuiGraphicsExtractor g, NodeView view, SkillTreeSet trees, SkillData data, SkillContext ctx) {
		SkillNode node = view.node();
		Ui.header(g, font, Component.translatable("screen.oldrepublic.skills.detail.requires"), x, y, w, 0f);
		y += 14;
		int next = Math.min(node.maxRank(), view.rank() + 1);

		if (node.grantedOnly()) {
			Component hint = node.grantedHint() != null ? Component.translatable(node.grantedHint()) : Component.translatable("skill.oldrepublic.req.granted");
			requirement(g, hint, view.rank() > 0);
		} else if (node.levelFor(next) > 0) {
			requirement(g, Component.translatable("skill.oldrepublic.req.level", node.levelFor(next)), ctx.level() >= node.levelFor(next));
		}

		for (SkillNode.Requirement req : node.requires()) {
			SkillNode target = trees.node(req.node());
			boolean met = data.rank(req.node()) >= req.rank() && target != null && !SkillRules.dormant(target, ctx);
			requirement(g, NodeView.requirement(req), met);
		}

		if (node.path() != null) {
			requirement(g, Component.translatable("skill.oldrepublic.req.path", NodeView.pathName(node.path())), node.path() == ctx.path());
		}

		Component alignment = NodeView.alignment(node);

		if (alignment != null) {
			requirement(g, alignment, node.alignmentMet(ctx.alignment()));
		}

		if (!node.grantedOnly() && view.rank() < node.maxRank()) {
			requirement(g, Component.translatable("skill.oldrepublic.req.points", view.check().cost()), ctx.skillPoints() >= view.check().cost());
		}

		y += 3;
	}

	private void drawWarnings(GuiGraphicsExtractor g, NodeView view, SkillContext ctx) {
		SkillNode node = view.node();

		if (view.rank() == 0 && node.taughtBy().contains(ctx.path())) {
			warning(g, Component.translatable("screen.oldrepublic.skills.detail.mentor_teaches"));
		}

		if (view.check().replaces() != null) {
			warning(g, Component.translatable("screen.oldrepublic.skills.detail.replaces", Component.translatable(view.check().replaces().nameKey())));
		}
	}

	// ---------------------------------------------------------------- helpers

	private void row(GuiGraphicsExtractor g, Component label, Component value, int valueColor) {
		g.fill(x, y + 3, x + 2, y + 5, Ui.a(UiColors.HOLO, 0.9f));
		int vw = font.width(value);
		String text = Ui.fit(font, label.getString(), w - 5 - vw - 8);
		Ui.labelValue(g, font, Component.literal(text), value, x + 5, y, w - 5, UiColors.TEXT_MUTED, valueColor);
		y += font.lineHeight + 1;
	}

	private void requirement(GuiGraphicsExtractor g, Component text, boolean met) {
		int color = met ? UiColors.OK : UiColors.DANGER;
		g.text(font, met ? CHECK : CROSS, x, y, color, true);
		y = wrapped(g, text.getString(), x + 10, w - 10, met ? UiColors.TEXT_MUTED : UiColors.TEXT, 2);
	}

	private void warning(GuiGraphicsExtractor g, Component text) {
		g.fill(x, y + 1, x + 2, y + 7, UiColors.AMBER);
		y = wrapped(g, text.getString(), x + 5, w - 5, UiColors.AMBER_BRIGHT, 4);
		y += 2;
	}

	private void paragraph(GuiGraphicsExtractor g, Component text, int color) {
		y = wrapped(g, text.getString(), x, w, color, 12);
	}

	private int wrapped(GuiGraphicsExtractor g, String text, int left, int width, int color, int maxLines) {
		int ly = y;

		for (String line : Ui.wrap(font, text, width, maxLines)) {
			g.text(font, line, left, ly, color, true);
			ly += font.lineHeight + 1;
		}

		return ly;
	}

	/** The lang key that formats an info value, chosen by the key's unit suffix. */
	static String unitKey(String key) {
		String unit;

		if (key.endsWith("_pct_per_s")) {
			unit = "percent_per_second";
		} else if (key.endsWith("_per_s")) {
			unit = key.contains("cost") ? "energy_per_second" : "per_second";
		} else if (key.endsWith("_pct")) {
			unit = "percent";
		} else if (key.endsWith("_mult")) {
			unit = "multiplier";
		} else if (key.endsWith("_s") || key.endsWith("_seconds")) {
			unit = "seconds";
		} else if (key.endsWith("_ticks")) {
			unit = "ticks";
		} else if (key.contains("cone") || key.contains("aim_assist") || key.contains("degrees") || key.contains("arc")) {
			unit = "degrees";
		} else if (key.contains("range") || key.contains("radius") || key.contains("distance") || key.contains("height") || key.contains("fall")) {
			unit = "blocks";
		} else if (key.contains("cost") || key.contains("energy")) {
			unit = "energy";
		} else if (key.contains("stamina")) {
			unit = "stamina";
		} else if (key.contains("damage")) {
			unit = "damage";
		} else {
			unit = "plain";
		}

		return "skill.oldrepublic.unit." + unit;
	}

	/** Whole numbers without decimals, others with one decimal, or two when one would round (0.85). */
	static String format(float value) {
		if (Math.abs(value - Math.round(value)) < 0.005f) {
			return Integer.toString(Math.round(value));
		}

		String one = String.format(Locale.ROOT, "%.1f", value);
		return Math.abs(Float.parseFloat(one) - value) < 0.005f ? one : String.format(Locale.ROOT, "%.2f", value);
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
}

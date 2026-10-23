package com.oldrepublic.client.mission;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.ui.HoloButton;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.mission.MissionRules;
import com.oldrepublic.mission.SpaceContracts;
import com.oldrepublic.mission.network.MissionActionPayload;
import com.oldrepublic.mission.network.MissionBoardPayload;
import com.oldrepublic.mission.network.MissionBoardPayload.OfferView;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The bounty board of an outpost terminal (spec 5.3, 5.5, 5.7), opened by the server's
 * {@code mission_board} payload: the army's emblem and today's full-pay count at the top, the day's
 * two offer cards (title, issuer emblem, briefing, objective, what it pays now, and Accept, or
 * "Taken today" for an offer already used), and, while a mission is in hand, an Abandon button.
 * The game is not paused.
 *
 * <p>Milestone 5 (5.8): a planet board also posts one space bounty a day, apart from the two rolled
 * contracts. It is drawn as a strip under the cards (the objective, what it pays and a Hunt button,
 * with the briefing as the button's tooltip), so the two cards keep their size.
 *
 * <p>The screen only sends intents ({@code mission_action}); the server checks reach, the day's
 * offers and the soft rules, then sends the board again, which refreshes this screen in place.
 */
public final class BoardScreen extends Screen {
	private static final int MAX_W = 340;
	private static final int PAD = 8;
	private static final int HEADER_H = 16;
	private static final int CARD_H = 150;
	private static final int CARD_PAD = 6;
	private static final int LINE_H = 10;
	private static final int BRIEFING_LINES = 5;
	private static final int OBJECTIVE_LINES = 2;
	private static final int BUTTON_H = 16;
	private static final int FOOTER_H = 22;
	private static final int BOUNTY_H = 28;
	private static final int HUNT_W = 56;
	private static final int REPUBLIC = 0xFF8FB8E8;
	private static final int IMPERIAL = 0xFFD0463A;
	private static final int FRONTIER = 0xFFC9A66B;
	private static final int KAJIDIC = 0xFFD8B04C;
	private static final int CLAN = 0xFFA3B4C0;

	/** One laid-out offer card. */
	private record Card(OfferView offer, int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private MissionBoardPayload board;
	private final List<Card> cards = new ArrayList<>();
	/** Today's space bounty of a planet board, drawn as a strip under the cards, or null. */
	private @Nullable Card bounty;
	private final float openedAt;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	BoardScreen(MissionBoardPayload board) {
		super(Component.translatable("screen.oldrepublic.board.title." + MissionRules.issuerKey(army(board.issuer()))));
		this.board = board;
		this.openedAt = UiAnim.time(0f);
	}

	/** The terminal this board belongs to. */
	boolean isFor(MissionBoardPayload next) {
		return next.terminal().equals(board.terminal()) && next.issuer() == board.issuer();
	}

	/** Shows a newer board of the same terminal in place (after an accept or abandon). */
	void show(MissionBoardPayload next) {
		board = next;
		rebuildWidgets();
	}

	@Override
	protected void init() {
		List<OfferView> regular = new ArrayList<>();
		OfferView spaceBounty = null;

		for (OfferView offer : board.offers()) {
			if (isBounty(offer)) {
				spaceBounty = spaceBounty == null ? offer : spaceBounty;
			} else if (regular.size() < 2) {
				regular.add(offer);
			}
		}

		panelW = Math.min(width - 16, MAX_W);
		panelH = PAD + HEADER_H + 4 + CARD_H + 4 + LINE_H + (spaceBounty == null ? 0 : BOUNTY_H + 4) + FOOTER_H + PAD;
		panelX = (width - panelW) / 2;
		panelY = Math.max(4, (height - panelH) / 2);
		cards.clear();
		bounty = null;

		int cardW = (panelW - PAD * 3) / 2;
		int cy = panelY + PAD + HEADER_H + 4;
		boolean busy = !board.activeId().isEmpty();

		for (int i = 0; i < regular.size(); i++) {
			OfferView offer = regular.get(i);
			int cx = panelX + PAD + i * (cardW + PAD);
			cards.add(new Card(offer, cx, cy, cardW, CARD_H));
			Component label = Component.translatable(offer.used() ? "screen.oldrepublic.board.taken" : "screen.oldrepublic.board.accept");
			HoloButton accept = addRenderableWidget(new HoloButton(cx + CARD_PAD, cy + CARD_H - CARD_PAD - BUTTON_H, cardW - CARD_PAD * 2, BUTTON_H, label,
					b -> send(MissionActionPayload.ACCEPT, offer.index()), true));
			accept.active = !offer.used() && !busy;

			if (busy && !offer.used()) {
				accept.withTooltip(Component.translatable("screen.oldrepublic.board.busy"));
			}
		}

		if (spaceBounty != null) {
			OfferView offer = spaceBounty;
			int by = cy + CARD_H + 4 + LINE_H + 2;
			bounty = new Card(offer, panelX + PAD, by, panelW - PAD * 2, BOUNTY_H);
			Component label = Component.translatable(offer.used() ? "screen.oldrepublic.board.taken" : "screen.oldrepublic.board.bounty.hunt");
			HoloButton hunt = addRenderableWidget(new HoloButton(panelX + panelW - PAD - CARD_PAD - HUNT_W, by + (BOUNTY_H - BUTTON_H) / 2, HUNT_W, BUTTON_H,
					label, b -> send(MissionActionPayload.ACCEPT, offer.index()), true));
			hunt.active = !offer.used() && !busy;
			hunt.withTooltip(busy && !offer.used() ? Component.translatable("screen.oldrepublic.board.busy") : briefing(offer));
		}

		int footerY = panelY + panelH - PAD - BUTTON_H;
		int closeW = 60;
		addRenderableWidget(new HoloButton(panelX + panelW - PAD - closeW, footerY, closeW, BUTTON_H, Component.translatable("gui.done"), b -> onClose()));

		if (busy) {
			int abandonW = 80;
			addRenderableWidget(new HoloButton(panelX + panelW - PAD - closeW - 6 - abandonW, footerY, abandonW, BUTTON_H,
					Component.translatable("screen.oldrepublic.board.abandon"), b -> send(MissionActionPayload.ABANDON, 0))
					.withTooltip(Component.translatable("screen.oldrepublic.board.abandon.hint")));
		}
	}

	private void send(int action, int offer) {
		ClientPlayNetworking.send(new MissionActionPayload(board.terminal(), action, offer));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float time = UiAnim.time(partialTick);
		float open = UiAnim.easeOutCubic(Math.min(1f, (time - openedAt) / 6f));
		Faction army = army(board.issuer());
		int accent = tint(army);

		Ui.panel(graphics, panelX, panelY, panelW, panelH);
		Ui.scanlines(graphics, panelX + 3, panelY + 3, panelW - 6, panelH - 6, Ui.a(UiColors.HOLO, 0.03f * UiAnim.flicker(time)), time);
		drawHeader(graphics, army, accent, time);

		if (cards.isEmpty()) {
			Component none = Component.translatable(board.notice().isEmpty() ? "screen.oldrepublic.board.notice.none" : board.notice());
			int ty = panelY + PAD + HEADER_H + 4 + CARD_H / 2 - LINE_H;

			for (String line : Ui.wrap(font, none.getString(), panelW - PAD * 4, 3)) {
				Ui.text(graphics, font, line, panelX + (panelW - font.width(line)) / 2, ty, UiColors.TEXT_MUTED, open);
				ty += LINE_H;
			}
		} else {
			for (Card card : cards) {
				drawCard(graphics, card, mouseX, mouseY, open, time);
			}

			if (!board.notice().isEmpty()) {
				Component notice = Component.translatable(board.notice());
				int ny = panelY + PAD + HEADER_H + 4 + CARD_H + 4;
				Ui.text(graphics, font, Ui.fit(font, notice.getString(), panelW - PAD * 2), panelX + PAD, ny, UiColors.AMBER, open);
			}
		}

		if (bounty != null) {
			drawBounty(graphics, bounty, mouseX, mouseY, open, time);
		}

		drawFooter(graphics, open);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawHeader(GuiGraphicsExtractor g, Faction army, int accent, float time) {
		int x = panelX + PAD;
		int y = panelY + PAD;
		Ui.sprite(g, emblem(army), x, y, 9, 9, 1f);
		Component fullPay = Component.translatable("screen.oldrepublic.board.full_pay", board.fullPayUsed(), board.fullPayMax());
		int payW = font.width(fullPay);
		Ui.header(g, font, title, x + 13, y, panelW - PAD * 2 - 13 - payW - 8, time);
		boolean spent = board.fullPayUsed() >= board.fullPayMax();
		Ui.text(g, font, fullPay, panelX + panelW - PAD - payW, y, spent ? UiColors.AMBER : UiColors.TEXT_MUTED, 1f);
		g.fill(x, y + 12, x + 13, y + 13, Ui.a(accent, 0.9f));
	}

	private void drawCard(GuiGraphicsExtractor g, Card card, int mouseX, int mouseY, float alpha, float time) {
		OfferView offer = card.offer();
		boolean hover = !offer.used() && card.contains(mouseX, mouseY);
		Ui.sprite(g, offer.used() ? Ui.CARD_LOCKED : hover ? Ui.CARD_SELECTED : Ui.CARD, card.x(), card.y(), card.w(), card.h(), alpha);

		Faction issuer = army(offer.issuer());
		int x = card.x() + CARD_PAD;
		int w = card.w() - CARD_PAD * 2;
		int y = card.y() + CARD_PAD;
		float text = offer.used() ? alpha * 0.55f : alpha;

		// Issuer row: emblem and the issuing faction.
		Ui.sprite(g, emblem(issuer), x, y, 9, 9, text);
		Ui.text(g, font, Component.translatable("faction.oldrepublic." + issuer.id()), x + 12, y + 1, tint(issuer), text);
		y += LINE_H + 2;

		Component title = Component.translatable("mission.oldrepublic." + offer.missionId() + ".title");
		Ui.text(g, font, Ui.fit(font, title.getString(), w), x, y, UiColors.TEXT_WARM, text);
		g.fill(x, y + 9, x + Math.min(w, font.width(title)), y + 10, Ui.a(tint(issuer), 0.5f * text * UiAnim.flicker(time)));
		y += LINE_H + 3;

		String briefing = briefing(offer).getString();

		for (String line : Ui.wrap(font, briefing, w, BRIEFING_LINES)) {
			Ui.text(g, font, line, x, y, UiColors.TEXT_MUTED, text);
			y += LINE_H;
		}

		y = card.y() + CARD_PAD + LINE_H * 2 + 5 + LINE_H * BRIEFING_LINES + 2;

		for (String line : Ui.wrap(font, Component.translatable(offer.objectiveKey()).getString(), w, OBJECTIVE_LINES)) {
			Ui.text(g, font, line, x, y, UiColors.HOLO, text);
			y += LINE_H;
		}

		// Two reward lines above the button: XP and standing, then the commendation or the Frontier cache.
		y = card.y() + card.h() - CARD_PAD - BUTTON_H - 3 - LINE_H * 2;
		boolean paid = offer.rep() > 0 || offer.loot() || offer.commendations() > 0;

		for (Component line : rewards(offer, issuer)) {
			Ui.text(g, font, Ui.fit(font, line.getString(), w), x, y, paid ? UiColors.BRASS_LIGHT : UiColors.AMBER_DIM, text);
			y += LINE_H;
		}
	}

	/**
	 * The space bounty's strip (5.8): the issuer's emblem, "Today's bounty" and the objective, then
	 * what it pays (XP, credits and the board faction's standing; a bounty is paid outside the daily
	 * full-pay count), with the Hunt button on the right.
	 */
	private void drawBounty(GuiGraphicsExtractor g, Card card, int mouseX, int mouseY, float alpha, float time) {
		OfferView offer = card.offer();
		boolean hover = !offer.used() && card.contains(mouseX, mouseY);
		Ui.sprite(g, offer.used() ? Ui.CARD_LOCKED : hover ? Ui.CARD_SELECTED : Ui.CARD, card.x(), card.y(), card.w(), card.h(), alpha);

		Faction issuer = army(offer.issuer());
		int x = card.x() + CARD_PAD;
		int w = card.w() - CARD_PAD * 3 - HUNT_W - 12;
		int y = card.y() + 5;
		float text = offer.used() ? alpha * 0.55f : alpha;

		Ui.sprite(g, emblem(issuer), x, y, 9, 9, text);
		Component headline = Component.translatable("screen.oldrepublic.board.bounty", Component.translatable(offer.objectiveKey()));
		Ui.text(g, font, Ui.fit(font, headline.getString(), w), x + 12, y + 1, UiColors.TEXT_WARM, text);
		g.fill(x + 12, y + 10, x + 12 + Math.min(w, font.width(headline)), y + 11, Ui.a(tint(issuer), 0.5f * text * UiAnim.flicker(time)));
		y += LINE_H + 3;

		Component pays = offer.used() ? Component.translatable("screen.oldrepublic.board.bounty.taken")
				: Component.translatable("screen.oldrepublic.board.reward.line",
						Component.translatable("screen.oldrepublic.board.reward.line", Component.translatable("screen.oldrepublic.board.reward.xp", offer.xp()),
								Component.translatable("screen.oldrepublic.board.reward.credits", offer.credits())),
						Component.translatable("screen.oldrepublic.board.reward.rep", offer.rep(), Component.translatable("faction.oldrepublic." + issuer.id())));
		Ui.text(g, font, Ui.fit(font, pays.getString(), w), x + 12, y, offer.used() ? UiColors.AMBER_DIM : UiColors.BRASS_LIGHT, text);
	}

	/** An offer's briefing text, in the issuing faction's voice. */
	private static Component briefing(OfferView offer) {
		return Component.translatable(MissionRules.briefingKey(offer.missionId(), MissionRules.issuerKey(army(offer.issuer())), offer.briefing()));
	}

	/** Whether an offer is a planet board's space bounty ({@code <planet>_bounty}, 5.8), drawn as the strip. */
	private static boolean isBounty(OfferView offer) {
		return offer.missionId().endsWith("_" + SpaceContracts.BOUNTY);
	}

	/**
	 * What the offer pays now, in two lines: XP and the issuer's standing, then the commendation or
	 * the Frontier cache; past the full-pay count, XP only and why.
	 */
	private static List<Component> rewards(OfferView offer, Faction issuer) {
		Component xp = Component.translatable("screen.oldrepublic.board.reward.xp", offer.xp());

		if (offer.rep() <= 0 && offer.commendations() <= 0 && !offer.loot()) {
			return List.of(Component.translatable("screen.oldrepublic.board.reward.xp_only", xp), Component.translatable("screen.oldrepublic.board.reward.over_cap"));
		}

		Component rep = Component.translatable("screen.oldrepublic.board.reward.rep", offer.rep(), Component.translatable("faction.oldrepublic." + issuer.id()));
		Component extra = offer.loot() ? Component.translatable("screen.oldrepublic.board.reward.loot")
				: offer.commendations() > 0 ? Component.translatable("screen.oldrepublic.board.reward.commendations", offer.commendations()) : null;

		// Milestone 4 (7.7): contracts also pay credits at full pay.
		if (offer.credits() > 0) {
			Component credits = Component.translatable("screen.oldrepublic.board.reward.credits", offer.credits());
			extra = extra == null ? credits : Component.translatable("screen.oldrepublic.board.reward.line", extra, credits);
		}

		return extra == null ? List.of(Component.translatable("screen.oldrepublic.board.reward.line", xp, rep))
				: List.of(Component.translatable("screen.oldrepublic.board.reward.line", xp, rep), extra);
	}

	private void drawFooter(GuiGraphicsExtractor g, float alpha) {
		int y = panelY + panelH - PAD - BUTTON_H + 4;

		if (board.activeId().isEmpty()) {
			Ui.text(g, font, Component.translatable("screen.oldrepublic.board.hint"), panelX + PAD, y, UiColors.TEXT_DIM, alpha);
			return;
		}

		Component active = Component.translatable("screen.oldrepublic.board.active",
				Component.translatable("mission.oldrepublic." + board.activeId() + ".title"));
		Ui.text(g, font, Ui.fit(font, active.getString(), panelW - PAD * 2 - 160), panelX + PAD, y, UiColors.TEXT_WARM, alpha);
	}

	// ---------------------------------------------------------------- styles

	private static Faction army(int ordinal) {
		Faction[] factions = Faction.values();
		return ordinal >= 0 && ordinal < factions.length ? factions[ordinal] : Faction.REPUBLIC;
	}

	private static int tint(Faction faction) {
		return switch (faction) {
			case REPUBLIC -> REPUBLIC;
			case SITH_EMPIRE -> IMPERIAL;
			case FRONTIER -> FRONTIER;
			case JEDI_ORDER -> UiColors.RESONANCE;
			case VESSBA_KAJIDIC -> KAJIDIC;
			case CLAN_HASK -> CLAN;
			default -> UiColors.BRASS;
		};
	}

	private static Identifier emblem(Faction faction) {
		return OldRepublicMod.id("identity/faction_" + faction.id());
	}
}

package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.network.MissionBoardPayload;
import com.oldrepublic.mission.network.MissionNetworking;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The bounty board of a terminal (spec 5.3, 5.5, 5.7): finds whether a survey and a secure site
 * exist near the player, rolls the day's offers for that player and terminal, sends the board, and
 * checks every accept and abandon again on the server.
 *
 * <p>Opening a board starts two locator searches (they answer at once when cached). The board is
 * sent when both have answered, or after {@value #SEARCH_TIMEOUT} ticks with what is known; an
 * offer whose site is unknown is left out. The last board sent is kept per player to validate the
 * accept against it.
 */
public final class MissionBoard {
	/** Ticks a board waits for its site searches before it opens with what is known. */
	static final int SEARCH_TIMEOUT = 60;
	private static final String SURVEY = "survey";
	private static final String SECURE = "secure_site";

	/** A board being prepared: its searches are still out. */
	private static final class Pending {
		final BlockPos terminal;
		final Faction army;
		final long created;
		int searches = 2;
		@Nullable SitePiece survey;
		@Nullable SitePiece secure;

		Pending(BlockPos terminal, Faction army, long created) {
			this.terminal = terminal;
			this.army = army;
			this.created = created;
		}
	}

	/** The board last shown to a player, what accept is checked against. */
	private record Shown(BlockPos terminal, Faction army, int day, List<MissionRules.Offer> offers, @Nullable SitePiece survey, @Nullable SitePiece secure) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, Shown> SHOWN = new HashMap<>();

	/** Opens the board of the terminal at {@code terminal} issued by {@code army} (Republic or Sith Empire). */
	public static void open(ServerPlayer p, BlockPos terminal, Faction army) {
		if (army != Faction.REPUBLIC && army != Faction.SITH_EMPIRE) {
			return;
		}

		MissionBalance b = MissionFeature.balance();
		Pending pending = new Pending(terminal.immutable(), army, p.level().getGameTime());
		PENDING.put(p.getUUID(), pending);
		List<BlockPos> surveyed = List.copyOf(MissionService.get(p).surveyed);
		MissionSites.nearest(p, MissionRules.WORLDGEN_KINDS, b.surveyRadius, piece -> surveyed.contains(MissionSites.heart(piece)), piece -> {
			pending.survey = piece;
			answered(p, pending);
		});
		MissionSites.nearest(p, MissionRules.WORLDGEN_KINDS, b.secureRadius, null, piece -> {
			pending.secure = piece;
			answered(p, pending);
		});
	}

	private static void answered(ServerPlayer p, Pending pending) {
		if (--pending.searches <= 0 && PENDING.get(p.getUUID()) == pending) {
			PENDING.remove(p.getUUID());
			send(p, pending.terminal, pending.army, pending.survey, pending.secure);
		}
	}

	/** Opens boards whose searches took too long, with what is known. */
	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();

			if (now - pending.created < SEARCH_TIMEOUT) {
				continue;
			}

			it.remove();
			ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());

			if (p != null) {
				send(p, pending.terminal, pending.army, pending.survey, pending.secure);
			}
		}
	}

	/** Rolls the day's offers and sends the board. */
	private static void send(ServerPlayer p, BlockPos terminal, Faction army, @Nullable SitePiece survey, @Nullable SitePiece secure) {
		MissionBalance b = MissionFeature.balance();
		int day = MissionService.day(p);
		MissionData d = MissionService.get(p);
		int boardDay = d.boardDay;
		int contractDay = d.contractDay;
		MissionRules.rollDay(d, day);

		if (d.boardDay != boardDay || d.contractDay != contractDay) {
			MissionService.commit(p, d);
			d = MissionService.get(p);
		}

		MissionData data = d;
		List<MissionRules.Offer> offers = MissionRules.offers(day, p.getUUID(), terminal, MissionRules.BOARD_TYPES,
				type -> available(p, army, type, survey, secure), index -> MissionRules.offerUsed(data, terminal, index), b.offersPerBoard);
		SHOWN.put(p.getUUID(), new Shown(terminal, army, day, List.copyOf(offers), survey, secure));

		boolean fullPay = MissionRules.fullPay(d, b);
		int xp = MissionRules.paidXp(MissionRules.contractXp(Progression.get(p).level, b), fullPay, b);
		long seed = MissionRules.boardSeed(day, p.getUUID(), terminal);
		List<MissionBoardPayload.OfferView> views = new ArrayList<>(offers.size());

		for (MissionRules.Offer offer : offers) {
			MissionDefinition def = MissionLoader.get(offer.type());
			Faction issuer = issuerOf(offer.type(), army);
			boolean loot = issuer == Faction.FRONTIER;
			views.add(new MissionBoardPayload.OfferView(offer.index(), offer.type(), issuer.ordinal(),
					MissionRules.briefingVariant(seed + offer.index(), def == null ? 1 : def.briefings()),
					MissionRules.objectiveKey(offer.type(), Stage.ACTIVE), xp, fullPay ? b.contractRep : 0,
					fullPay && !loot ? b.contractCommendations : 0, fullPay && loot, MissionRules.offerUsed(d, terminal, offer.index())));
		}

		String notice = gateMet(p, army) ? (offers.isEmpty() ? "screen.oldrepublic.board.notice.none" : "")
				: "screen.oldrepublic.board.notice.standing." + MissionRules.issuerKey(army);
		MissionNetworking.sendBoard(p, new MissionBoardPayload(terminal, army.ordinal(), views, d.contractsToday, b.fullPayPerDay,
				d.active == null ? "" : d.active.id, notice));
	}

	/** Whether a contract type can be offered: its issuer's standing is Neutral or better and, for survey and secure, a site was found. */
	private static boolean available(ServerPlayer p, Faction army, String type, @Nullable SitePiece survey, @Nullable SitePiece secure) {
		MissionDefinition def = MissionLoader.get(type);

		if (def == null || !gateMet(p, issuerOf(type, army))) {
			return false;
		}

		return switch (type) {
			case SURVEY -> survey != null;
			case SECURE -> secure != null;
			default -> true;
		};
	}

	/** Contracts need their issuer at Neutral or better (5.3). */
	static boolean gateMet(ServerPlayer p, Faction issuer) {
		return Identity.tier(p, issuer).ordinal() >= ReputationTier.NEUTRAL.ordinal();
	}

	/** Who issues a board contract: the Frontier for {@code clear_camp} at either terminal, else the terminal's army. */
	static Faction issuerOf(String type, Faction army) {
		return type.equals(MissionRules.FRONTIER_TYPE) ? Faction.FRONTIER : army;
	}

	/**
	 * Accepts an offer of the board last shown: the same terminal and day, within reach of it, no
	 * active mission, an offer of that board not used yet, its issuer still at Neutral and its site
	 * still known.
	 */
	public static void accept(ServerPlayer p, BlockPos terminal, int index) {
		Shown shown = SHOWN.get(p.getUUID());
		MissionBalance b = MissionFeature.balance();
		int day = MissionService.day(p);

		if (shown == null || !shown.terminal().equals(terminal) || shown.day() != day || !inReach(p, terminal)) {
			reopen(p, terminal, shown);
			return;
		}

		MissionRules.Offer offer = shown.offers().stream().filter(o -> o.index() == index).findFirst().orElse(null);
		MissionData d = MissionService.get(p);
		MissionRules.rollDay(d, day);

		if (offer == null || d.active != null || MissionRules.offerUsed(d, terminal, index)
				|| !available(p, shown.army(), offer.type(), shown.survey(), shown.secure())) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.offer_refused"));
			reopen(p, terminal, shown);
			return;
		}

		MissionDefinition def = MissionLoader.get(offer.type());
		SitePiece site = switch (offer.type()) {
			case SURVEY -> shown.survey();
			case SECURE -> shown.secure();
			default -> null;
		};

		if (def == null || !MissionRules.useOffer(d, terminal, index, b.usedOffersMax)) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.offer_refused"));
			return;
		}

		MissionService.commit(p, d);
		Faction issuer = issuerOf(offer.type(), shown.army());

		MissionService.start(p, def, issuer.id(), terminal, null, site);
		reopen(p, terminal, shown);
	}

	/** Abandons the active mission at a board within reach. The offer it came from stays used today. */
	public static void abandon(ServerPlayer p, BlockPos terminal) {
		Shown shown = SHOWN.get(p.getUUID());

		if (inReach(p, terminal)) {
			MissionService.abandon(p);
		}

		reopen(p, terminal, shown);
	}

	private static void reopen(ServerPlayer p, BlockPos terminal, @Nullable Shown shown) {
		if (shown != null && shown.terminal().equals(terminal) && inReach(p, terminal)) {
			send(p, terminal, shown.army(), shown.survey(), shown.secure());
		}
	}

	private static boolean inReach(ServerPlayer p, BlockPos terminal) {
		double reach = MissionFeature.balance().boardReach;
		return p.position().distanceToSqr(Vec3.atCenterOf(terminal)) <= reach * reach;
	}

	/** Forgets a player's boards (leave, respawn). */
	static void forget(UUID player) {
		PENDING.remove(player);
		SHOWN.remove(player);
	}

	/** Forgets all in-memory state (server stopped). */
	static void clear() {
		PENDING.clear();
		SHOWN.clear();
	}

	private MissionBoard() {
	}
}

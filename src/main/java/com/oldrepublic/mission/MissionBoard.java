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
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.vendor.VendorFeature;
import com.oldrepublic.vendor.VendorRules;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
 *
 * <p>Milestone 4 (spec MILESTONE_4.md 8.7): a hub's contract terminal opens a <em>planet board</em>
 * ({@link #openPlanet}): the planet's six contracts ({@link PlanetContracts#types}), issued by the
 * Jedi Order on Selvaren and the Sith Empire on Varkhast, seeded and marked used per dimension, so
 * a terminal at the same position on another world rolls its own offers. Their sites are found
 * when the contract is accepted. Every offer also shows the credits it pays at full pay (7.7).
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

	/** The board last shown to a player, what accept is checked against. {@code planet} is set for a planet board. */
	private record Shown(BlockPos terminal, Faction army, int day, List<MissionRules.Offer> offers, @Nullable SitePiece survey, @Nullable SitePiece secure,
			ResourceKey<Level> dimension, Planets.@Nullable Planet planet) {
		Shown(BlockPos terminal, Faction army, int day, List<MissionRules.Offer> offers, @Nullable SitePiece survey, @Nullable SitePiece secure) {
			this(terminal, army, day, offers, survey, secure, Level.OVERWORLD, null);
		}
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
		int level = Progression.get(p).level;
		int xp = MissionRules.paidXp(MissionRules.contractXp(level, b), fullPay, b);
		int credits = fullPay ? VendorRules.contractCredits(level, VendorFeature.balance()) : 0;
		long seed = MissionRules.boardSeed(day, p.getUUID(), terminal);
		List<MissionBoardPayload.OfferView> views = new ArrayList<>(offers.size());

		for (MissionRules.Offer offer : offers) {
			MissionDefinition def = MissionLoader.get(offer.type());
			Faction issuer = issuerOf(offer.type(), army);
			boolean loot = issuer == Faction.FRONTIER;
			views.add(new MissionBoardPayload.OfferView(offer.index(), offer.type(), issuer.ordinal(),
					MissionRules.briefingVariant(seed + offer.index(), def == null ? 1 : def.briefings()),
					MissionRules.objectiveKey(offer.type(), Stage.ACTIVE), xp, fullPay ? b.contractRep : 0,
					fullPay && !loot ? b.contractCommendations : 0, fullPay && loot, MissionRules.offerUsed(d, terminal, offer.index()), credits));
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

		if (shown == null || !shown.terminal().equals(terminal) || shown.day() != day || !inReach(p, terminal)
				|| !shown.dimension().equals(p.level().dimension())) {
			reopen(p, terminal, shown);
			return;
		}

		if (shown.planet() != null) {
			acceptPlanet(p, shown, index);
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
		if (shown == null || !shown.terminal().equals(terminal) || !inReach(p, terminal) || !shown.dimension().equals(p.level().dimension())) {
			return;
		}

		if (shown.planet() != null) {
			sendPlanet(p, terminal, shown.planet());
		} else {
			send(p, terminal, shown.army(), shown.survey(), shown.secure());
		}
	}

	// ---------------------------------------------------------------- planet boards (Milestone 4, 8.7)

	/**
	 * Opens the planet board of a hub's contract terminal on {@code planet}: any planet whose contracts
	 * have an issuer ({@link PlanetContracts#issuer}; Milestone 5 adds Ghessar and Tyrvann, spec
	 * MILESTONE_5.md 7.2).
	 */
	public static void openPlanet(ServerPlayer p, BlockPos terminal, Planets.Planet planet) {
		if (PlanetContracts.issuer(planet) == null) {
			return;
		}

		PENDING.remove(p.getUUID());
		sendPlanet(p, terminal.immutable(), planet);
	}

	/** Rolls the day's planet offers (seeded per dimension) and sends the board. */
	private static void sendPlanet(ServerPlayer p, BlockPos terminal, Planets.Planet planet) {
		MissionBalance b = MissionFeature.balance();
		Faction issuer = PlanetContracts.issuer(planet);

		if (issuer == null) {
			return;
		}

		ResourceKey<Level> dimension = p.level().dimension();
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
		boolean gate = gateMet(p, issuer);
		List<MissionRules.Offer> offers = MissionRules.offers(day, p.getUUID(), terminal, dimension, PlanetContracts.types(planet),
				type -> gate && MissionLoader.get(type) != null, index -> MissionRules.offerUsed(data, terminal, dimension, index), b.offersPerBoard);
		SHOWN.put(p.getUUID(), new Shown(terminal, issuer, day, List.copyOf(offers), null, null, dimension, planet));

		boolean fullPay = MissionRules.fullPay(d, b);
		int level = Progression.get(p).level;
		int xp = MissionRules.paidXp(MissionRules.contractXp(level, b), fullPay, b);
		int credits = fullPay ? VendorRules.contractCredits(level, VendorFeature.balance()) : 0;
		long seed = MissionRules.boardSeed(day, p.getUUID(), terminal, dimension);
		List<MissionBoardPayload.OfferView> views = new ArrayList<>(offers.size());

		for (MissionRules.Offer offer : offers) {
			MissionDefinition def = MissionLoader.get(offer.type());
			boolean loot = def != null && def.loot() != null;
			views.add(new MissionBoardPayload.OfferView(offer.index(), offer.type(), issuer.ordinal(),
					MissionRules.briefingVariant(seed + offer.index(), def == null ? 1 : def.briefings()),
					MissionRules.objectiveKey(offer.type(), Stage.ACTIVE), xp, fullPay ? b.contractRep : 0, 0, fullPay && loot,
					MissionRules.offerUsed(d, terminal, dimension, offer.index()), credits));
		}

		String notice = gate ? (offers.isEmpty() ? "screen.oldrepublic.board.notice.none" : "")
				: "screen.oldrepublic.board.notice.standing." + MissionRules.issuerKey(issuer);
		MissionNetworking.sendBoard(p, new MissionBoardPayload(terminal, issuer.ordinal(), views, d.contractsToday, b.fullPayPerDay,
				d.active == null ? "" : d.active.id, notice));
	}

	/** Accepts a planet board's offer: the same checks as the outpost board, with the dimension in the used offer. */
	private static void acceptPlanet(ServerPlayer p, Shown shown, int index) {
		MissionBalance b = MissionFeature.balance();
		BlockPos terminal = shown.terminal();
		MissionRules.Offer offer = shown.offers().stream().filter(o -> o.index() == index).findFirst().orElse(null);
		MissionData d = MissionService.get(p);
		MissionRules.rollDay(d, shown.day());
		MissionDefinition def = offer == null ? null : MissionLoader.get(offer.type());

		if (def == null || d.active != null || MissionRules.offerUsed(d, terminal, shown.dimension(), index) || !gateMet(p, shown.army())
				|| !MissionRules.useOffer(d, terminal, shown.dimension(), index, b.usedOffersMax)) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.offer_refused"));
			reopen(p, terminal, shown);
			return;
		}

		MissionService.commit(p, d);
		MissionService.start(p, def, shown.army().id(), terminal, null, null);
		reopen(p, terminal, shown);
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

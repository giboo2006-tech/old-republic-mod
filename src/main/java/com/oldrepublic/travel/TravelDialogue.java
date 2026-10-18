package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.ShipRules.Recall;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The ship's dialogue conditions and actions (spec MILESTONE_4.md 8.10, 4.8, 4.9):
 * <ul>
 * <li>condition {@code ship:state} (a {@code ShipState} id, or {@code none}, {@code any},
 * {@code repaired}; several joined by {@code |} mean any of them; a leading {@code !} negates)</li>
 * <li>condition {@code ship:aboard} (empty or {@code true}: riding a ship; {@code false}: not)</li>
 * <li>action {@code ship:recall_free}: the hub host's free recall, once a day, to this hub's pad (home on the home world)</li>
 * <li>action {@code ship:tow}: the spacer's tow of a {@code DISABLED} ship on this planet, once a day</li>
 * <li>action {@code ship:repair_paid}: the spacer repairs a landed ship on this planet to full hull, for
 * {@code spacerRepairPerPoint} credits a point (4.4)</li>
 * <li>action {@code ship:recall_terminal}: the pad terminal's recall for a cell (a tow when {@code DISABLED})</li>
 * <li>action {@code ship:restore}: a new body at the ship's last pad when its body is missing</li>
 * <li>action {@code nav:load} (optionally with planet ids): loads coordinates into a held nav computer</li>
 * </ul>
 */
final class TravelDialogue {
	/** The pad terminal each player last used, for the recall's effect at the console. */
	private static final Map<UUID, GlobalPos> TERMINALS = new HashMap<>();
	/** A terminal farther than this from the player when the recall is chosen plays nothing. */
	private static final double TERMINAL_REACH = 8;

	static void init() {
		DialogueConditions.register("ship:state", TravelDialogue::stateCondition);
		DialogueConditions.register("ship:aboard", (p, speaker, arg) -> {
			boolean wanted = arg.isBlank() || Boolean.parseBoolean(arg.trim());
			return Ships.isAboard(p) == wanted ? null : wanted ? "ship.oldrepublic.condition.not_aboard" : "ship.oldrepublic.condition.aboard";
		});
		DialogueActions.register("ship:recall_free", (p, speaker) -> recall(p, TravelService.Payment.FREE_DAILY));
		DialogueActions.register("ship:tow", (p, speaker) -> tow(p));
		DialogueActions.register("ship:repair_paid", (p, speaker) -> repairPaid(p));
		DialogueActions.register("ship:recall_terminal", (p, speaker) -> {
			recall(p, TravelService.Payment.CELL);
			terminalEffect(p);
		});
		DialogueActions.register("ship:restore", (p, speaker) -> restore(p));
		DialogueActions.register("nav:load", (p, speaker) -> navLoaded(p, NavData.loadForPath(p)));
		DialogueActions.registerArg("nav:load", (p, speaker, arg) -> navLoaded(p, NavData.load(p, planets(arg))));
	}

	// ---------------------------------------------------------------- conditions

	/** {@code ship:state}: null when the player's ship matches, else why not. */
	static @Nullable String stateCondition(ServerPlayer p, @Nullable Entity speaker, String arg) {
		String text = arg.trim().toLowerCase(Locale.ROOT);
		boolean negate = text.startsWith("!");
		ShipRecord record = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);
		boolean matches = false;

		for (String part : (negate ? text.substring(1) : text).split("\\|")) {
			matches |= stateMatches(record, part.trim());
		}

		if (matches != negate) {
			return null;
		}

		return record == null ? "ship.oldrepublic.condition.no_ship" : "ship.oldrepublic.condition.state";
	}

	/** Whether {@code record} (null: no ship) is in the state named by {@code id}. */
	static boolean stateMatches(@Nullable ShipRecord record, String id) {
		return switch (id) {
			case "none" -> record == null;
			case "any" -> record != null;
			case "repaired" -> record != null && record.state() != ShipState.HULK;
			default -> record != null && record.state().name().toLowerCase(Locale.ROOT).equals(id);
		};
	}

	// ---------------------------------------------------------------- actions

	/** A recall to the hub of the planet the player stands on (home on the home world). */
	private static void recall(ServerPlayer p, TravelService.Payment payment) {
		Pads.Pad pad = Pads.hereHub(p);

		if (pad == null) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.recall.no_pad"));
			return;
		}

		tell(p, TravelService.recall(p, pad, payment));
	}

	/** The spacer's tow: only a {@code DISABLED} ship on this planet. */
	private static void tow(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);

		if (record == null) {
			tell(p, Recall.NO_SHIP);
			return;
		}

		if (record.state() != ShipState.DISABLED) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.recall.not_disabled"));
			return;
		}

		if (!record.pos().dimension().equals(p.level().dimension())) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.recall.tow_elsewhere"));
			return;
		}

		recall(p, TravelService.Payment.FREE_DAILY);
	}

	/** The spacer's repair: a landed ship on this planet, to full hull, paid all or nothing (the hold pays too). */
	private static void repairPaid(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);

		if (record == null) {
			tell(p, Recall.NO_SHIP);
			return;
		}

		if (!ShipRules.canPlate(record)) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.spacer.no_repair"));
			return;
		}

		if (!record.pos().dimension().equals(p.level().dimension())) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.spacer.repair_elsewhere"));
			return;
		}

		ShipBalance b = ShipBalance.get();
		int cost = (100 - record.hull()) * b.spacerRepairPerPoint;

		if (!Credits.take(p, cost, true)) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.spacer.repair_cost", cost));
			return;
		}

		Ships.update(server, p.getUUID(), r -> {
			ShipRecord full = r.withHull(100);
			return full.withState(ShipRules.restingState(full, b));
		});
		CourierShuttle body = Ships.shuttle(server, p.getUUID());

		if (body != null && body.level() instanceof ServerLevel level) {
			TravelEffects.repair(level, body.position().add(0, 1.2, 0), body, TravelEffects.REPAIR_WELD);
		}

		p.sendSystemMessage(Component.translatable("ship.oldrepublic.spacer.repaired", cost));
	}

	private static void tell(ServerPlayer p, Recall result) {
		p.sendSystemMessage(Component.translatable(result.langKey()));
	}

	/** {@code ship:restore}: places a body from the registry when the current one is missing. */
	private static void restore(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();

		if (ShipRegistry.of(server, p.getUUID()).isEmpty()) {
			tell(p, Recall.NO_SHIP);
			return;
		}

		if (Flights.isFlying(p.getUUID())) {
			tell(p, Recall.FLYING);
			return;
		}

		boolean present = Ships.shuttle(server, p.getUUID()) != null;
		Ships.restore(server, p.getUUID(), true);
		p.sendSystemMessage(Component.translatable(present ? "ship.oldrepublic.restore.present" : "ship.oldrepublic.restore.queued"));
	}

	/** Remembers the pad terminal a player opened, for the recall's effect. */
	static void usedTerminal(ServerPlayer p, BlockPos pos) {
		TERMINALS.put(p.getUUID(), GlobalPos.of(p.level().dimension(), pos.immutable()));
	}

	private static void terminalEffect(ServerPlayer p) {
		GlobalPos terminal = TERMINALS.remove(p.getUUID());

		if (terminal != null && terminal.dimension().equals(p.level().dimension())
				&& p.position().distanceToSqr(Vec3.atCenterOf(terminal.pos())) <= TERMINAL_REACH * TERMINAL_REACH) {
			TravelEffects.beacon(p.level(), terminal.pos(), TravelEffects.BEACON_RECALL);
		}
	}

	/** Tells the player whether coordinates went in, with the fitting effect (also for {@link NavData}'s item use). */
	static void navLoaded(ServerPlayer p, boolean loaded) {
		if (loaded) {
			TravelEffects.repair(p.level(), p.position().add(0, 1.2, 0), null, ShipRules.SOCKET_NAV_INDEX);
		}

		p.sendSystemMessage(Component.translatable(loaded ? "ship.oldrepublic.nav.loaded" : "ship.oldrepublic.nav.no_computer"));
	}

	/** Planet ids from an action argument, separated by commas or spaces; unknown ids are skipped. */
	static List<Planets.Planet> planets(String arg) {
		List<Planets.Planet> planets = new ArrayList<>();

		for (String id : arg.split("[,\\s]+")) {
			Planets.Planet planet = Planets.Planet.byId(id.trim());

			if (planet != null && !planets.contains(planet)) {
				planets.add(planet);
			}
		}

		return planets;
	}

	static void forget(UUID player) {
		TERMINALS.remove(player);
	}

	static void clear() {
		TERMINALS.clear();
	}

	private TravelDialogue() {
	}
}

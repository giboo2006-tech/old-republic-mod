package com.oldrepublic.travel;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.LegendaryItems;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.registry.M4Items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * The Saltgate shipwright's services (spec MILESTONE_5.md 4.8, 4.9, 6.11, 7.1, 11.2), as dialogue
 * conditions and actions for W4's {@code vendor_saltgate_shipwright}:
 * <ul>
 * <li>condition {@code ship:weapons} (empty or {@code true}: the player's ship has cannons; {@code false}: it has none)</li>
 * <li>action {@code ship:fit <id>}: fits an M5 upgrade ({@code cannons}, {@code shield}, {@code thrusters},
 * {@code missiles}) for its price, or a legendary ship part ({@code spindle_capacitor}, {@code hymn_drive})
 * from the pack for the fitting fee</li>
 * <li>action {@code ship:remove <id>}: takes an M5 upgrade or part out, for nothing and with no refund</li>
 * <li>action {@code ship:refit interceptor|courier}: the class refit</li>
 * </ul>
 * Every service needs the ship landed at his yard: in his world, within {@link #YARD_RANGE} blocks
 * of him. The rules themselves are {@link FlightRules}'s; this class pays, writes the record and
 * plays the work.
 */
final class Shipwright {
	/** How near the shipwright (blocks) the ship must stand: his hangar and the hub's pad bays. */
	static final double YARD_RANGE = 64;
	/** {@code ship:refit}'s arguments. */
	private static final String INTERCEPTOR = "interceptor";
	private static final String COURIER = "courier";

	static void init() {
		DialogueConditions.register("ship:weapons", (p, speaker, arg) -> {
			boolean wanted = arg.isBlank() || Boolean.parseBoolean(arg.trim());
			boolean armed = ShipRegistry.of(p.level().getServer(), p.getUUID()).map(Ships::hasWeapons).orElse(false);
			return armed == wanted ? null : wanted ? "ship.oldrepublic.condition.no_weapons" : "ship.oldrepublic.condition.weapons";
		});
		DialogueActions.registerArg("ship:fit", Shipwright::fit);
		DialogueActions.registerArg("ship:remove", Shipwright::remove);
		DialogueActions.registerArg("ship:refit", (p, speaker, arg) -> {
			String to = arg.trim().toLowerCase(Locale.ROOT);

			if (to.equals(INTERCEPTOR) || to.equals(COURIER)) {
				refit(p, speaker, to.equals(INTERCEPTOR));
			} else {
				OldRepublicMod.LOGGER.warn("ship:refit takes {} or {}, not '{}'", INTERCEPTOR, COURIER, arg);
			}
		});
	}

	// ---------------------------------------------------------------- upgrades (4.8, 11.2)

	/** {@code ship:fit <id>}: the upgrade for its price, or the legendary part from the pack for the fitting fee. */
	private static void fit(ServerPlayer p, @Nullable Entity speaker, String id) {
		MinecraftServer server = p.level().getServer();
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);
		FlightBalance b = FlightBalance.get();
		int bit = FlightRules.upgradeOf(id);
		Item part = partItem(bit);
		boolean hasPart = part != null && (p.hasInfiniteMaterials() || ShipActions.count(p, stack -> stack.is(part)) > 0);
		int price = FlightRules.fitPrice(bit, b);
		FlightRules.Fit fit = FlightRules.fitRefusal(record, bit, record != null && atYard(record, p, speaker), hasPart, Credits.count(p, true), b);

		if (fit != FlightRules.Fit.OK || record == null) {
			p.sendSystemMessage(Component.translatable(fit.langKey(), price));
			return;
		}

		if (!Credits.take(p, price, true)) {
			p.sendSystemMessage(Component.translatable(FlightRules.Fit.CREDITS.langKey(), price));
			return;
		}

		if (part != null && !p.hasInfiniteMaterials()) {
			ShipActions.take(p, stack -> stack.is(part), 1);
		}

		Ships.update(server, p.getUUID(), r -> ShipRules.withUpgrade(r, bit, true, ShipBalance.get()));
		work(server, p.getUUID(), TravelEffects.REFIT_UPGRADE);
		p.sendSystemMessage(Component.translatable("ship.oldrepublic.fit.done", upgradeName(bit), price));
		OldRepublicMod.LOGGER.info("The shipwright fits {} to the ship of {}", FlightRules.upgradeId(bit), p.getPlainTextName());
	}

	/** {@code ship:remove <id>}: takes an upgrade or part out, for nothing (4.8: no refund); the capacitor goes with the shield. */
	private static void remove(ServerPlayer p, @Nullable Entity speaker, String id) {
		MinecraftServer server = p.level().getServer();
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);
		int bit = FlightRules.upgradeOf(id);

		if (!FlightRules.canStrip(record, bit, record != null && atYard(record, p, speaker))) {
			p.sendSystemMessage(Component.translatable("ship.oldrepublic.strip.refused"));
			return;
		}

		Ships.update(server, p.getUUID(), r -> FlightRules.stripped(r, bit, ShipBalance.get()));
		work(server, p.getUUID(), TravelEffects.REFIT_UPGRADE);
		p.sendSystemMessage(Component.translatable("ship.oldrepublic.strip.done", upgradeName(bit)));
	}

	/** The item a legendary part is fitted from, or null for an upgrade the shipwright supplies himself. */
	private static @Nullable Item partItem(int bit) {
		return switch (bit) {
			case ShipRecord.PART_SPINDLE_CAPACITOR -> LegendaryItems.SPINDLE_CAPACITOR;
			case ShipRecord.PART_HYMN_DRIVE -> LegendaryItems.HYMN_TUNED_DRIVE;
			default -> null;
		};
	}

	private static Component upgradeName(int bit) {
		return Component.translatable("ship.oldrepublic.upgrade." + FlightRules.upgradeId(bit));
	}

	// ---------------------------------------------------------------- the refit (4.9)

	/**
	 * {@code ship:refit interceptor|courier} (4.9): to the interceptor for 2,500 credits and 6 alloy
	 * plates, back to the courier for 500. The record keeps its hull code, fuel, upgrades and (trimmed
	 * to one) crew; the body is replaced by one of the new class at the same spot on its next tick
	 * ({@link Ships#tickBody}).
	 */
	private static void refit(ServerPlayer p, @Nullable Entity speaker, boolean toInterceptor) {
		MinecraftServer server = p.level().getServer();
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);
		ShipBody ship = record == null ? null : Ships.ship(server, p.getUUID());
		FlightBalance b = FlightBalance.get();
		boolean riders = ship != null && !ship.getPassengers().isEmpty();
		int alloy = p.hasInfiniteMaterials() ? Integer.MAX_VALUE : ShipActions.count(p, stack -> stack.is(M4Items.ALLOY_PLATE));
		int price = toInterceptor ? b.refitCredits : b.refitBackCredits;
		FlightRules.Refit refusal = FlightRules.refitRefusal(record, toInterceptor, record != null && atYard(record, p, speaker), riders,
				Credits.count(p, true), alloy, b);

		if (refusal != FlightRules.Refit.OK || record == null) {
			int arg = switch (refusal) {
				case HOLD -> record == null ? 0 : FlightRules.holdStacks(record) - b.interceptorHold;
				case ALLOY -> b.refitAlloy;
				default -> price;
			};
			p.sendSystemMessage(Component.translatable(refusal.langKey(), arg));
			return;
		}

		if (!Credits.take(p, price, true)) {
			p.sendSystemMessage(Component.translatable(FlightRules.Refit.CREDITS.langKey(), price));
			return;
		}

		if (toInterceptor && !p.hasInfiniteMaterials()) {
			ShipActions.take(p, stack -> stack.is(M4Items.ALLOY_PLATE), b.refitAlloy);
		}

		List<UUID> crewBefore = record.crew();
		ShipRecord refitted = Ships.update(server, p.getUUID(), r -> FlightRules.refitted(r, toInterceptor, b, ShipBalance.get())).orElse(record);

		for (UUID id : crewBefore) {
			if (!refitted.crew().contains(id)) {
				leftCrew(server, p, id);
			}
		}

		work(server, p.getUUID(), toInterceptor ? TravelEffects.REFIT_INTERCEPTOR : TravelEffects.REFIT_COURIER);
		p.sendSystemMessage(Component.translatable(toInterceptor ? "ship.oldrepublic.refit.done_interceptor" : "ship.oldrepublic.refit.done_courier"));
		OldRepublicMod.LOGGER.info("The shipwright refits the ship of {} as {}", p.getPlainTextName(), toInterceptor ? "an interceptor" : "a courier");
	}

	/** A crew member the interceptor's single seat leaves ashore: told, and no longer listed as its crew. */
	private static void leftCrew(MinecraftServer server, ServerPlayer owner, UUID id) {
		ServerPlayer member = server.getPlayerList().getPlayer(id);

		if (member == null) {
			return;
		}

		TravelData data = TravelAttachments.get(member);

		if (data.crewOf().filter(owner.getUUID()::equals).isPresent()) {
			TravelAttachments.set(member, data.withCrewOf(Optional.empty()));
		}

		member.sendSystemMessage(Component.translatable("ship.oldrepublic.crew.removed", owner.getDisplayName()));
	}

	// ---------------------------------------------------------------- helpers

	/** Whether the ship stands at this shipwright's yard: landed in his world, within {@link #YARD_RANGE} blocks of him (of the player without him). */
	static boolean atYard(ShipRecord record, ServerPlayer p, @Nullable Entity speaker) {
		Entity at = speaker != null ? speaker : p;
		return record.pos().dimension().equals(at.level().dimension())
				&& Vec3.atBottomCenterOf(record.pos().pos()).distanceToSqr(at.position()) <= YARD_RANGE * YARD_RANGE;
	}

	/** Sparks and the work's sound around the ship ({@code ship_refit}). */
	private static void work(MinecraftServer server, UUID owner, int variant) {
		ShipBody ship = Ships.ship(server, owner);

		if (ship != null) {
			TravelEffects.refit(ship, variant);
		}
	}

	private Shipwright() {
	}
}

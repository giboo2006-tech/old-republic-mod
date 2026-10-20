package com.oldrepublic.travel;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.travel.ShipRules.Recall;
import com.oldrepublic.travel.TravelService.Refusal;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic ship ...} (spec MILESTONE_4.md 4.11). Each takes one player and logs what it
 * changed; the root command already needs permission level 2.
 * <ul>
 * <li>{@code give <player>}: a repaired, fuelled ship beside the player (hold, crew and upgrades kept)</li>
 * <li>{@code hulk <player>}: the Chapter Four hulk and its cradle, placed now</li>
 * <li>{@code repair <player> [amount]}: a hulk gets all four sockets; a repaired ship gains hull (to full by default)</li>
 * <li>{@code fuel <player> [amount]}: cells into the tank (to full by default), free</li>
 * <li>{@code hull <player> <value>}: sets the hull integrity</li>
 * <li>{@code travel <player> <destination> [instant]}: the flight sequence (the player aboard), or the jump alone</li>
 * <li>{@code recall <player>}: the ship flies itself to the hub of the planet the player is on (home on the home world), free</li>
 * <li>{@code restore <player>}: a new body from the registry at the ship's last pad</li>
 * <li>{@code unlock <player> <destination|all>}</li>
 * <li>{@code info <player>}: the record and the travel state</li>
 * <li>{@code helm <player>}: unlocks flying the ship by hand (MILESTONE_5.md 4.2)</li>
 * </ul>
 */
final class ShipCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("ship")
				.then(literal("give").then(player().executes(ShipCommands::give)))
				.then(literal("hulk").then(player().executes(ShipCommands::hulk)))
				.then(literal("repair").then(player().executes(ctx -> repair(ctx, -1))
						.then(argument("amount", IntegerArgumentType.integer(1, 100)).executes(ctx -> repair(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
				.then(literal("fuel").then(player().executes(ctx -> fuel(ctx, -1))
						.then(argument("amount", IntegerArgumentType.integer(1, 64)).executes(ctx -> fuel(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
				.then(literal("hull").then(player().then(argument("value", IntegerArgumentType.integer(0, 100)).executes(ShipCommands::hull))))
				.then(literal("travel").then(player().then(destination(false).executes(ctx -> travel(ctx, false))
						.then(literal("instant").executes(ctx -> travel(ctx, true))))))
				.then(literal("recall").then(player().executes(ShipCommands::recall)))
				.then(literal("restore").then(player().executes(ShipCommands::restore)))
				.then(literal("unlock").then(player().then(destination(true).executes(ShipCommands::unlock))))
				.then(literal("info").then(player().executes(ShipCommands::info)))
				.then(literal("helm").then(player().executes(ShipCommands::helm)))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
		return argument("player", EntityArgument.player());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> destination(boolean withAll) {
		return argument("destination", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
				Stream.concat(Arrays.stream(Destination.values()).map(Destination::id), withAll ? Stream.of("all") : Stream.empty()), builder));
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, "player");
	}

	/** The player's record, or a failure message and empty. */
	private static Optional<ShipRecord> record(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		Optional<ShipRecord> record = ShipRegistry.of(p.level().getServer(), p.getUUID());

		if (record.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + " has no ship; use /oldrepublic ship give or hulk"));
		}

		return record;
	}

	private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		ShipRecord record = Ships.give(p);
		log(ctx, p, "ship given at " + record.pos().pos().toShortString() + ", " + record.state());
		return 1;
	}

	private static int hulk(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		ShipRecord record = Ships.placeHulkNow(p);

		if (record == null) {
			ctx.getSource().sendFailure(Component.literal("No spot for the ship cradle near " + p.getPlainTextName()));
			return 0;
		}

		log(ctx, p, "hulk placed at " + record.pos().pos().toShortString());
		return 1;
	}

	/** A hulk gets every socket (and stands up); a repaired ship gains {@code amount} hull, or full with -1. */
	private static int repair(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		MinecraftServer server = p.level().getServer();
		ShipBalance b = ShipBalance.get();

		if (record(ctx, p).isEmpty()) {
			return 0;
		}

		ShipState before = ShipRegistry.of(server, p.getUUID()).map(ShipRecord::state).orElse(ShipState.HULK);
		ShipRecord after = Ships.update(server, p.getUUID(), r -> {
			if (r.state() == ShipState.HULK) {
				ShipRecord fitted = ShipRules.withAllSockets(r, b).withHull(ShipRules.maxHull(r));
				return fitted.withState(ShipRules.settleState(fitted, b));
			}

			ShipRecord repaired = r.withHull(amount < 0 ? ShipRules.maxHull(r) : r.hull() + amount);
			return r.state().flying() ? repaired : repaired.withState(ShipRules.restingState(repaired, b));
		}).orElseThrow();
		ShipBody ship = Ships.ship(server, p.getUUID());

		if (ship != null) {
			Vec3 at = ship.position().add(0, 1.5, 0);
			TravelEffects.repair((ServerLevel) ship.level(), at, ship, before == ShipState.HULK ? TravelEffects.REPAIR_STAND_UP : TravelEffects.REPAIR_WELD);
		}

		log(ctx, p, "hull " + after.hull() + ", " + after.state());
		return 1;
	}

	/** Cells into the tank, free: {@code amount}, or to full with -1. */
	private static int fuel(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		MinecraftServer server = p.level().getServer();
		ShipBalance b = ShipBalance.get();
		ShipRecord record = record(ctx, p).orElse(null);

		if (record == null) {
			return 0;
		}

		int cells = ShipRules.fuelTaken(record, amount < 0 ? ShipRules.tankSize(record, b) : amount, b);

		if (cells <= 0) {
			ctx.getSource().sendFailure(Component.literal("The tank is full, or the ship is flying"));
			return 0;
		}

		ShipRecord after = Ships.update(server, p.getUUID(), r -> ShipRules.fuel(r, cells, b)).orElseThrow();
		log(ctx, p, "fuel " + after.fuel() + "/" + ShipRules.tankSize(after, b) + ", " + after.state());
		return 1;
	}

	private static int hull(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		MinecraftServer server = p.level().getServer();
		ShipBalance b = ShipBalance.get();
		int value = IntegerArgumentType.getInteger(ctx, "value");

		if (record(ctx, p).isEmpty()) {
			return 0;
		}

		ShipRecord after = Ships.update(server, p.getUUID(), r -> {
			ShipRecord changed = r.withHull(value);
			return r.state().flying() ? changed : changed.withState(ShipRules.restingState(changed, b));
		}).orElseThrow();
		log(ctx, p, "hull " + after.hull() + ", " + after.state());
		return 1;
	}

	private static int travel(CommandContext<CommandSourceStack> ctx, boolean instant) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Destination d = Destination.byId(StringArgumentType.getString(ctx, "destination"));

		if (d == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown destination; one of " + Arrays.stream(Destination.values()).map(Destination::id).toList()));
			return 0;
		}

		Refusal refusal = TravelService.launchByCommand(p, d, instant);

		if (refusal != Refusal.NONE) {
			ctx.getSource().sendFailure(Component.literal("Refused: " + refusal + " (the player must be aboard a repaired ship)"));
			return 0;
		}

		log(ctx, p, (instant ? "jump to " : "flight to ") + d.id());
		return 1;
	}

	private static int recall(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);

		if (record(ctx, p).isEmpty()) {
			return 0;
		}

		Pads.Pad here = Pads.hereHub(p);
		Pads.Pad pad = here != null ? here : Pads.home(p);
		Recall result = TravelService.recall(p, pad, TravelService.Payment.NONE);

		if (!result.allowed()) {
			ctx.getSource().sendFailure(Component.literal("Refused: " + result));
			return 0;
		}

		log(ctx, p, result + " to " + pad.pos().dimension().identifier() + " " + pad.pos().pos().toShortString());
		return 1;
	}

	private static int restore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);

		if (!Ships.restore(p.level().getServer(), p.getUUID(), false)) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + " has no ship, or it is flying"));
			return 0;
		}

		log(ctx, p, "body restored at its last pad (placed when the chunk is ready)");
		return 1;
	}

	private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		String id = StringArgumentType.getString(ctx, "destination");

		if (id.equals("all")) {
			for (Destination d : Destination.values()) {
				TravelService.unlock(p, d);
			}

			log(ctx, p, "every destination unlocked");
			return 1;
		}

		Destination d = Destination.byId(id);

		if (d == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown destination " + id));
			return 0;
		}

		TravelService.unlock(p, d);
		log(ctx, p, d.id() + " unlocked");
		return 1;
	}

	/** Unlocks the helm, as the story does at Chapter Five's call (MILESTONE_5.md 4.2). */
	private static int helm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		TravelAttachments.set(p, TravelAttachments.get(p).withHelm(true));
		log(ctx, p, "helm unlocked");
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		MinecraftServer server = p.level().getServer();
		ShipBalance b = ShipBalance.get();
		TravelData data = TravelAttachments.get(p);
		StringBuilder text = new StringBuilder(p.getPlainTextName()).append(": ");
		ShipRecord r = ShipRegistry.of(server, p.getUUID()).orElse(null);

		if (r == null) {
			text.append("no ship");
		} else {
			ShipBody ship = Ships.ship(server, p.getUUID());
			text.append(r.hullCode()).append(' ').append(r.state())
					.append(", hull ").append(r.hull())
					.append(", fuel ").append(r.fuel()).append('/').append(ShipRules.tankSize(r, b))
					.append(", upgrades ").append(Integer.toBinaryString(ShipRules.upgradesOnly(r.upgrades())))
					.append(", sockets");

			for (int socket = 0; socket < ShipRules.SOCKETS; socket++) {
				text.append(ShipRules.socketDone(r, socket, b) ? " +" : " -");
			}

			text.append(" (plates ").append(ShipRules.plates(r)).append('/').append(ShipRules.platesNeeded(b)).append(')')
					.append(", hold ").append(r.hold().stream().filter(s -> !s.isEmpty()).count()).append('/').append(ShipRules.holdSize(r, b))
					.append(", crew ").append(r.crew().size())
					.append(", pad ").append(r.pos().dimension().identifier()).append(' ').append(r.pos().pos().toShortString())
					.append(", body ").append(ship != null ? "loaded" : "not loaded")
					.append(", flight ").append(r.flight().map(f -> f.from().id() + "->" + f.to().id() + " phase " + f.phase()).orElse("none"));
		}

		text.append("; unlocked ").append(Arrays.stream(Destination.values()).filter(data::isUnlocked).map(Destination::id).toList())
				.append(", beacon ").append(data.homePad().map(g -> g.pos().toShortString()).orElse("none"))
				.append(", last pad ").append(data.lastPad().map(g -> g.pos().toShortString()).orElse("none"))
				.append(", recall day ").append(data.recallDay()).append(", tow day ").append(data.towDay())
				.append(", crew of ").append(data.crewOf().map(id -> ShipActions.name(server, id)).orElse("nobody"))
				.append(", helm ").append(data.helm() ? "unlocked" : "locked");
		ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static void log(CommandContext<CommandSourceStack> ctx, ServerPlayer p, String what) {
		String line = "Ship: " + p.getPlainTextName() + " " + what;
		OldRepublicMod.LOGGER.info("[command] {}", line);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
	}

	private ShipCommands() {
	}
}

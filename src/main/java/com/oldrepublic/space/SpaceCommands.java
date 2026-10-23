package com.oldrepublic.space;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic space ...} operator commands (spec MILESTONE_5.md 5, 19.3), each with a single player argument where
 * it acts on one. Every shortcut is logged ({@code [command]}).
 * <ul>
 * <li>{@code status <player>}: their encounter, shield, objectives and vault day, and the Spindle</li>
 * <li>{@code blockade start|win|reset <player>}: starts their blockade (or the Peaceful pass) where they are; breaks it
 * at once (the playthrough's shortcut; with none running the story is told it was broken); ends it untold</li>
 * <li>{@code patrol <player>}: a patrol finds their armed ship now</li>
 * <li>{@code end <player>}: ends their encounter, its ships vanishing</li>
 * <li>{@code hit <player> <damage>}: enemy fire on their flying ship (shield first, then hull)</li>
 * <li>{@code spindle place}, {@code spindle visit <player>}, {@code spindle restock}: places the Silent Spindle now;
 * moves a player onto its landing deck (placing it first); lets its posts restock again today</li>
 * <li>{@code vault reset <player>}: their vault opens again today</li>
 * <li>{@code reset}: ends every encounter and forgets the shields, the guns and the objectives' moment-to-moment state</li>
 * </ul>
 */
final class SpaceCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(literal("space")
				.then(literal("status").then(player().executes(SpaceCommands::status)))
				.then(literal("blockade")
						.then(literal("start").then(player().executes(SpaceCommands::blockadeStart)))
						.then(literal("win").then(player().executes(SpaceCommands::blockadeWin)))
						.then(literal("reset").then(player().executes(SpaceCommands::blockadeReset))))
				.then(literal("patrol").then(player().executes(SpaceCommands::patrol)))
				.then(literal("end").then(player().executes(SpaceCommands::end)))
				.then(literal("hit").then(player().then(argument("damage", FloatArgumentType.floatArg(0f, 1000f)).executes(SpaceCommands::hit))))
				.then(literal("spindle")
						.then(literal("place").executes(SpaceCommands::spindlePlace))
						.then(literal("visit").then(player().executes(SpaceCommands::spindleVisit)))
						.then(literal("restock").executes(SpaceCommands::spindleRestock)))
				.then(literal("vault").then(literal("reset").then(player().executes(SpaceCommands::vaultReset))))
				.then(literal("reset").executes(SpaceCommands::reset))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
		return argument("player", EntityArgument.player());
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, "player");
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		MinecraftServer server = p.level().getServer();
		StringBuilder text = new StringBuilder("Space: ").append(p.getPlainTextName());
		Encounter e = SpaceEncounters.ofOwner(p.getUUID());

		if (e == null) {
			SpaceEncounters.End last = SpaceEncounters.lastEnd(p.getUUID());
			text.append(", no encounter").append(last == null ? "" : " (the last ended " + last.name().toLowerCase(Locale.ROOT) + ")");
		} else {
			text.append(", encounter ").append(e.type.name().toLowerCase(Locale.ROOT)).append(" in ").append(e.dimension.identifier())
					.append(" (ships ").append(e.members.size()).append(", spawned ").append(e.spawned).append(", destroyed ").append(e.destroyed)
					.append(", wave ").append(e.waveIndex + 1).append(e.finished() ? ", finished" : "").append(e.provoked ? ", provoked" : "")
					.append(e.clan ? ", clan escort" : "").append(", pilots ").append(e.participants.size()).append(')');
		}

		float max = ShipRegistry.of(server, p.getUUID()).map(r -> ShipCombat.maxShield(r, SpaceBalance.get())).orElse(0f);
		text.append("; shield ").append(Math.round(ShipCombat.shieldPoints(p))).append('/').append(Math.round(max))
				.append(ShipCombat.underFire(p) ? ", under fire" : "").append(ShipCombat.disabled(p) ? ", disabled" : "");
		SpaceData data = SpaceData.get(p);

		for (SpaceData.Run run : data.runs()) {
			text.append("; ").append(run.clear() ? "space_clear " : "space_run ").append(run.missionId()).append(" over ").append(run.system().id())
					.append(run.ace() ? " (ace)" : "").append(run.point().map(v -> " at " + BlockPos.containing(v).toShortString()).orElse(""));
		}

		float scan = SpaceMissions.scanMeter(p.getUUID());

		if (scan >= 0f) {
			text.append("; scanned ").append(Math.round(scan * 100)).append('%');
		}

		text.append("; vault day ").append(data.vaultDay()).append(" (today ").append(Clocks.day(server)).append(')');
		WorldSites.SiteRecord spindle = SilentSpindle.record(server);
		text.append("; Spindle ").append(spindle == null ? "not placed" : "at " + spindle.origin().toShortString());
		ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static int blockadeStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		SpaceEncounters.startBlockade(p);

		if (!SpaceEncounters.blockadeRunning(p)) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + "'s blockade could not start here"));
			return 0;
		}

		log(ctx, p, "blockade started");
		return 1;
	}

	private static int blockadeWin(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		boolean running = SpaceEncounters.winBlockade(p);
		log(ctx, p, running ? "blockade broken (shortcut)" : "no blockade running; the story was told it was broken (shortcut)");
		return 1;
	}

	private static int blockadeReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Encounter e = SpaceEncounters.ofOwner(p.getUUID());

		if (e == null || e.type != Encounter.Type.BLOCKADE && e.type != Encounter.Type.PASS) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + " has no blockade running"));
			return 0;
		}

		SpaceEncounters.end(p.level().getServer(), e, SpaceEncounters.End.RESET);
		log(ctx, p, "blockade reset");
		return 1;
	}

	private static int patrol(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		ShipBody ship = ShipWeapons.piloted(p);

		if (ship == null || !(ship.level() instanceof ServerLevel level) || ship.state() != ShipState.FLYING) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + " is not flying a ship"));
			return 0;
		}

		if (SpaceEncounters.ofOwner(p.getUUID()) != null) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + " is in an encounter already"));
			return 0;
		}

		if (SpaceEncounters.startPatrol(p, ship, level, SpaceBalance.get()) == null) {
			ctx.getSource().sendFailure(Component.literal("No room for a patrol near " + p.getPlainTextName()));
			return 0;
		}

		log(ctx, p, "patrol started");
		return 1;
	}

	private static int end(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Encounter e = SpaceEncounters.ofOwner(p.getUUID());

		if (e == null) {
			ctx.getSource().sendFailure(Component.literal(p.getPlainTextName() + " has no encounter"));
			return 0;
		}

		SpaceEncounters.end(p.level().getServer(), e, SpaceEncounters.End.RESET);
		log(ctx, p, "encounter ended");
		return 1;
	}

	private static int hit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		float damage = FloatArgumentType.getFloat(ctx, "damage");
		ShipCombat.hitShip(p, damage, null);
		log(ctx, p, "hit for " + damage + " (shield " + Math.round(ShipCombat.shieldPoints(p)) + ")");
		return 1;
	}

	private static int spindlePlace(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		WorldSites.SiteRecord record = SilentSpindle.record(server);

		if (record != null) {
			ctx.getSource().sendFailure(Component.literal("The Silent Spindle is already at " + record.origin().toShortString()));
			return 0;
		}

		SitePiece piece = placeNow(server);

		if (piece == null) {
			ctx.getSource().sendFailure(Component.literal("The Silent Spindle could not be placed"));
			return 0;
		}

		String line = "Space: the Silent Spindle placed at " + piece.origin().toShortString();
		OldRepublicMod.LOGGER.info("[command] {}", line);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return 1;
	}

	private static int spindleVisit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		MinecraftServer server = p.level().getServer();
		ServerLevel level = Planets.orbitLevel(server, Planets.Planet.HOME);
		WorldSites.SiteRecord record = SilentSpindle.record(server);
		SitePiece piece = record == null ? placeNow(server) : level == null ? null : RuntimeSites.pieceOf(level, record);
		BlockPos deck = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.SPINDLE_DECK);

		if (level == null || deck == null) {
			ctx.getSource().sendFailure(Component.literal("The Silent Spindle has no landing deck to visit"));
			return 0;
		}

		p.stopRiding();
		TravelService.moveAcross(p, level, Vec3.atBottomCenterOf(deck), p.getYRot());
		p.resetFallDistance();
		log(ctx, p, "moved to the Silent Spindle's landing deck at " + deck.toShortString());
		return 1;
	}

	private static int spindleRestock(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().getServer().globalAttachments().removeAttached(SilentSpindle.DROID_DAY);
		String line = "Space: the Silent Spindle's posts restock on the next visit";
		OldRepublicMod.LOGGER.info("[command] {}", line);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return 1;
	}

	private static int vaultReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		SpaceData.set(p, SpaceData.get(p).withVaultDay(-1L));
		log(ctx, p, "vault opens again today");
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		SpaceFeature.reset(server);
		String line = "Space: every encounter ended and the combat state forgotten";
		OldRepublicMod.LOGGER.info("[command] {}", line);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
		return 1;
	}

	/** The Spindle placed now at its configured place, or null. */
	private static @Nullable SitePiece placeNow(MinecraftServer server) {
		ServerLevel level = Planets.orbitLevel(server, Planets.Planet.HOME);
		return level == null ? null : SilentSpindle.place(level, SilentSpindle.centre(SpaceBalance.get()));
	}

	private static void log(CommandContext<CommandSourceStack> ctx, ServerPlayer p, String what) {
		String line = "Space: " + p.getPlainTextName() + " " + what;
		OldRepublicMod.LOGGER.info("[command] {}", line);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
	}

	private SpaceCommands() {
	}
}

package com.oldrepublic.mission;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Locale;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionDefinition.Kind;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic mission ...} (operators, spec 10.8). Every shortcut is logged.
 * <ul>
 * <li>{@code mission start <player> <id>}: starts a mission now, without its gate (a contract's
 * issuer follows the player's path; a duty takes today's first type);</li>
 * <li>{@code mission complete <player> [choice]}: completes the active mission, with a story choice;</li>
 * <li>{@code mission abandon <player>}: abandons it;</li>
 * <li>{@code mission offers <player>}: today's board use, soft cap, duty, own-side counts and the active mission;</li>
 * <li>{@code mission board <player> <republic|imperial>}: a test aid that opens that army's board at
 * the player's feet, as if they used a terminal there.</li>
 * </ul>
 */
final class MissionCommands {
	static void register() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(mission()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> mission() {
		return literal("mission")
				.then(literal("start").then(argument("player", EntityArgument.player())
						.then(argument("id", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(MissionLoader.all().stream().map(MissionDefinition::id), builder))
								.executes(MissionCommands::start))))
				.then(literal("complete").then(argument("player", EntityArgument.player()).executes(ctx -> complete(ctx, null))
						.then(argument("choice", StringArgumentType.word()).executes(ctx -> complete(ctx, StringArgumentType.getString(ctx, "choice"))))))
				.then(literal("abandon").then(argument("player", EntityArgument.player()).executes(MissionCommands::abandon)))
				.then(literal("offers").then(argument("player", EntityArgument.player()).executes(MissionCommands::offers)))
				.then(literal("board").then(argument("player", EntityArgument.player())
						.then(literal("republic").executes(ctx -> board(ctx, Faction.REPUBLIC)))
						.then(literal("imperial").executes(ctx -> board(ctx, Faction.SITH_EMPIRE)))));
	}

	private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id");
		MissionDefinition def = MissionLoader.get(id);

		if (def == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown mission " + id + "; one of "
					+ MissionLoader.all().stream().map(MissionDefinition::id).collect(Collectors.joining(", "))));
			return 0;
		}

		Path path = Identity.path(player);
		String issuer = switch (def.kind()) {
			case STORY -> def.issuer();
			case DUTY -> MissionDefinition.MENTOR;
			case CONTRACT -> MissionDefinition.TERMINAL.equals(def.issuer()) ? (path == Path.SITH ? Faction.SITH_EMPIRE : Faction.REPUBLIC).id() : def.issuer();
		};
		String base = def.kind() == Kind.DUTY
				? MissionRules.firstDutyType(def.base(), MissionRules.dutyOrder(MissionService.day(player), player.getUUID())) : null;

		if (!MissionService.start(player, def, issuer, player.blockPosition(), base, null)) {
			ctx.getSource().sendFailure(Component.literal(player.getName().getString() + " already has a mission, or " + id + " cannot run"));
			return 0;
		}

		OldRepublicMod.LOGGER.info("Mission {} started for {} by command (issuer {})", id, player.getName().getString(), issuer);
		ctx.getSource().sendSuccess(() -> Component.literal("Started " + id + " for " + player.getName().getString()), true);
		return 1;
	}

	private static int complete(CommandContext<CommandSourceStack> ctx, @Nullable String choice) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Active active = MissionService.get(player).active;

		if (active == null) {
			ctx.getSource().sendFailure(Component.literal(player.getName().getString() + " has no active mission"));
			return 0;
		}

		MissionDefinition def = MissionLoader.get(active.id);

		if (choice != null && (def == null || active.kind != Kind.STORY || !def.choices().containsKey(choice))) {
			ctx.getSource().sendFailure(Component.literal("No choice " + choice + " in " + active.id + (def == null ? "" : "; one of " + def.choices().keySet())));
			return 0;
		}

		OldRepublicMod.LOGGER.info("Mission {} of {} completed by command (choice {})", active.id, player.getName().getString(), choice);
		MissionService.complete(player, choice);
		ctx.getSource().sendSuccess(() -> Component.literal("Completed " + active.id + " for " + player.getName().getString()), true);
		return 1;
	}

	private static int abandon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		if (!MissionService.abandon(player)) {
			ctx.getSource().sendFailure(Component.literal(player.getName().getString() + " has no active mission"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Abandoned the mission of " + player.getName().getString()), true);
		return 1;
	}

	private static int offers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		MissionData d = MissionService.get(player);
		int day = MissionService.day(player);
		MissionBalance b = MissionFeature.balance();
		Active active = d.active;
		String used = d.boardDay == day ? d.usedOffers.stream().map(u -> u.terminal().toShortString() + "#" + u.index()).collect(Collectors.joining(", ")) : "";
		String text = String.format(Locale.ROOT,
				"%s, day %d: full pay %d/%d, duty %s, used offers [%s], own side %s (counts %d), story done %s, discovered %s, pull until %d, active %s",
				player.getName().getString(), day, d.contractDay == day ? d.contractsToday : 0, b.fullPayPerDay,
				MissionRules.dutyOpen(d, day, b.dutiesPerDay) ? "open" : "taken", used, d.ownSide, MissionRules.ownSideCount(Identity.path(player), d.ownSide),
				d.storyDone, d.discovered, d.pullUntil,
				active == null ? "none" : active.id + " (" + active.base + ", " + active.stage.name().toLowerCase(Locale.ROOT) + " " + active.progress + "/"
						+ active.target + ", extra " + active.extra + ", site " + (active.site == null ? "-" : active.site.toShortString()) + ")");
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int board(CommandContext<CommandSourceStack> ctx, Faction army) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		OldRepublicMod.LOGGER.info("Opening the {} board for {} by command (test aid)", army.id(), player.getName().getString());
		MissionBoard.open(player, player.blockPosition(), army);
		ctx.getSource().sendSuccess(() -> Component.literal("Opening the " + army.id() + " board for " + player.getName().getString()), false);
		return 1;
	}

	private MissionCommands() {
	}
}

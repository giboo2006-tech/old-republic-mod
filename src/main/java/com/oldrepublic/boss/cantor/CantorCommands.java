package com.oldrepublic.boss.cantor;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidData;
import com.oldrepublic.boss.raid.RaidSession;
import com.oldrepublic.boss.raid.UndercroftLayout;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic raid ...} (spec MILESTONE_5.md 12.3; the root command needs operator level 2):
 * {@code start <player> [countdown]} begins the Long Hymn in the undercroft around the player (or one
 * drawn around them when there is none); {@code phase <player> <2|3>}, {@code verse <player> <verse>},
 * {@code silence <player> <1..3>}, {@code clear <player>} (he unravels, the rewards are paid) and
 * {@code wipe <player>} drive the running Hymn; {@code status <player>} reports it and the player's
 * weekly lockout; {@code unlock <player>} lifts that lockout.
 */
final class CantorCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(raid()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> raid() {
		return literal("raid")
				.then(literal("start")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> start(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), RaidBalance.get().countdownTicks))
								.then(argument("countdown", IntegerArgumentType.integer(0, 1200))
										.executes(ctx -> start(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
												IntegerArgumentType.getInteger(ctx, "countdown"))))))
				.then(literal("phase")
						.then(argument("player", EntityArgument.player())
								.then(argument("phase", IntegerArgumentType.integer(2, 3))
										.executes(ctx -> phase(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "phase"))))))
				.then(literal("verse")
						.then(argument("player", EntityArgument.player())
								.then(argument("verse", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(CantorVerses.Verse.values()).map(v -> v.name().toLowerCase(Locale.ROOT)), builder))
										.executes(ctx -> verse(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "verse"))))))
				.then(literal("silence")
						.then(argument("player", EntityArgument.player())
								.then(argument("point", IntegerArgumentType.integer(1, 3))
										.executes(ctx -> silence(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "point"))))))
				.then(literal("clear")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("wipe")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> wipe(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("status")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("unlock")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> unlock(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
	}

	private static int start(CommandSourceStack source, ServerPlayer player, int countdown) {
		ServerLevel level = player.level();
		UndercroftLayout layout = UndercroftLayout.at(level, player.blockPosition());

		if (layout == null) {
			// No undercroft here: one drawn around a spot a few blocks ahead of the player.
			layout = UndercroftLayout.around(player.blockPosition().relative(player.getDirection(), 6), CantorSession.FIXTURE_HALF);
		}

		if (LongHymn.start(level, layout, player, countdown) == null) {
			source.sendFailure(Component.translatable("boss.oldrepublic.cantor.command.start_failed", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.start", player.getDisplayName()), true);
		return 1;
	}

	private static int phase(CommandSourceStack source, ServerPlayer player, int phase) {
		CantorSession session = LongHymn.sessionOf(player);

		if (session == null || !session.forcePhase(phase)) {
			return noRaid(source, player);
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.phase", player.getDisplayName(), phase), true);
		return 1;
	}

	private static int verse(CommandSourceStack source, ServerPlayer player, String name) {
		CantorSession session = LongHymn.sessionOf(player);
		FirstCantor cantor = session == null ? null : session.cantor();
		CantorVerses.Verse verse = null;

		for (CantorVerses.Verse v : CantorVerses.Verse.values()) {
			if (v.name().equalsIgnoreCase(name)) {
				verse = v;
			}
		}

		if (session == null || cantor == null || verse == null || !session.isFighting()) {
			return noRaid(source, player);
		}

		session.verses().forceVerse(cantor, verse);
		String id = verse.name().toLowerCase(Locale.ROOT);
		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.verse", player.getDisplayName(), id), true);
		return 1;
	}

	private static int silence(CommandSourceStack source, ServerPlayer player, int point) {
		CantorSession session = LongHymn.sessionOf(player);

		if (session == null || !session.engine().forceSilence(point - 1)) {
			return noRaid(source, player);
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.silence", player.getDisplayName(), point), true);
		return 1;
	}

	private static int clear(CommandSourceStack source, ServerPlayer player) {
		CantorSession session = LongHymn.sessionOf(player);

		if (session == null || !session.forceClear()) {
			return noRaid(source, player);
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.clear", player.getDisplayName()), true);
		return 1;
	}

	private static int wipe(CommandSourceStack source, ServerPlayer player) {
		CantorSession session = LongHymn.sessionOf(player);

		if (session == null) {
			return noRaid(source, player);
		}

		session.stop(false);
		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.wipe", player.getDisplayName()), true);
		return 1;
	}

	private static int status(CommandSourceStack source, ServerPlayer player) {
		CantorSession session = LongHymn.sessionOf(player);
		RaidData data = RaidData.of(player);
		boolean locked = RaidSession.clearedThisWeek(player);
		Component lockout = Component.translatable("boss.oldrepublic.cantor.command.lockout", player.getDisplayName(), locked ? 1 : 0, data.clears(),
				data.pity());

		if (session == null) {
			source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.status_none", lockout), false);
			return 1;
		}

		FirstCantor cantor = session.cantor();
		RaidSession raid = session.raid();
		int health = cantor == null ? 0 : Math.round(cantor.getHealth());
		int max = cantor == null ? 0 : Math.round(cantor.getMaxHealth());
		int participants = raid == null ? 0 : raid.count();
		int phase = session.phase();
		int window = session.engine().window();
		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.status", phase, participants, health, max, window, lockout),
				false);
		return 1;
	}

	private static int unlock(CommandSourceStack source, ServerPlayer player) {
		RaidData data = RaidData.of(player);
		RaidData.set(player, new RaidData(-1L, data.pity(), data.clears(), data.echoDay(), data.echoes()));
		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.cantor.command.unlock", player.getDisplayName()), true);
		return 1;
	}

	private static int noRaid(CommandSourceStack source, ServerPlayer player) {
		source.sendFailure(Component.translatable("boss.oldrepublic.cantor.command.no_raid", player.getDisplayName()));
		return 0;
	}

	private CantorCommands() {
	}
}

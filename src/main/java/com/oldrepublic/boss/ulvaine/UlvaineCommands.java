package com.oldrepublic.boss.ulvaine;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic ulvaine start|echo|phase|yield|reset <player>} (spec MILESTONE_5.md 12.2; the root
 * command needs operator level 2): start a player's fight or echo where they stand (in the Hymnal's
 * Heart, or a Heart drawn around them when there is none), jump the running fight to a phase, make her
 * yield, or reset the fight as the 200-tick rule does.
 */
final class UlvaineCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(ulvaine()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ulvaine() {
		return literal("ulvaine")
				.then(literal("start")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> start(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))))
				.then(literal("echo")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> start(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))))
				.then(literal("phase")
						.then(argument("player", EntityArgument.player())
								.then(argument("phase", IntegerArgumentType.integer(2, 3))
										.executes(ctx -> phase(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "phase"))))))
				.then(literal("yield")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> yieldNow(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("reset")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
	}

	private static int start(CommandSourceStack source, ServerPlayer player, boolean echo) {
		ServerLevel level = player.level();
		HeartLayout layout = HeartLayout.at(level, player.blockPosition());

		if (layout == null) {
			layout = HeartLayout.around(player.blockPosition(), UlvaineBalance.get().heartRadius);
		}

		if (UlvaineFight.begin(player.getUUID(), level, layout, echo, false) == null) {
			source.sendFailure(Component.translatable("boss.oldrepublic.ulvaine.command.start_failed", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable(echo ? "boss.oldrepublic.ulvaine.command.echo" : "boss.oldrepublic.ulvaine.command.start",
				player.getDisplayName()), true);
		return 1;
	}

	private static int phase(CommandSourceStack source, ServerPlayer player, int phase) {
		if (!UlvaineFight.phase(player, phase)) {
			source.sendFailure(Component.translatable("boss.oldrepublic.ulvaine.command.no_fight", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.ulvaine.command.phase", player.getDisplayName(), phase), true);
		return 1;
	}

	private static int yieldNow(CommandSourceStack source, ServerPlayer player) {
		if (!UlvaineFight.forceYield(player)) {
			source.sendFailure(Component.translatable("boss.oldrepublic.ulvaine.command.no_fight", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.ulvaine.command.yield", player.getDisplayName()), true);
		return 1;
	}

	private static int reset(CommandSourceStack source, ServerPlayer player) {
		if (!UlvaineFight.reset(player)) {
			source.sendFailure(Component.translatable("boss.oldrepublic.ulvaine.command.no_fight", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.ulvaine.command.reset", player.getDisplayName()), true);
		return 1;
	}

	private UlvaineCommands() {
	}
}

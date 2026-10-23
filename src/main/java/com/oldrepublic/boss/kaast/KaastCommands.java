package com.oldrepublic.boss.kaast;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic kaast camp|phase|reset <player>} (spec MILESTONE_4.md 10.5; the root command
 * needs operator level 2): place a player's camp now (the only way when {@code kaast.placeCamp} is
 * false), jump that player's running fight to a phase, or reset their camp as the 200-tick rule does.
 */
final class KaastCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(kaast()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> kaast() {
		return literal("kaast")
				.then(literal("camp")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> camp(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("phase")
						.then(argument("player", EntityArgument.player())
								.then(argument("phase", IntegerArgumentType.integer(1, 3))
										.executes(ctx -> phase(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "phase"))))))
				.then(literal("reset")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
	}

	private static int camp(CommandSourceStack source, ServerPlayer player) {
		GlobalPos camp = HuntingCamps.place(player, true);

		if (camp == null) {
			source.sendFailure(Component.translatable("boss.oldrepublic.kaast.command.camp_failed", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.kaast.command.camp", player.getDisplayName(), camp.pos().getX(), camp.pos().getY(),
				camp.pos().getZ(), camp.dimension().identifier().toString()), true);
		return 1;
	}

	private static int phase(CommandSourceStack source, ServerPlayer player, int phase) {
		if (!KaastFight.phase(player, phase)) {
			source.sendFailure(Component.translatable("boss.oldrepublic.kaast.command.no_fight", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.kaast.command.phase", player.getDisplayName(), phase), true);
		return 1;
	}

	private static int reset(CommandSourceStack source, ServerPlayer player) {
		if (!KaastFight.reset(player)) {
			source.sendFailure(Component.translatable("boss.oldrepublic.kaast.command.no_fight", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("boss.oldrepublic.kaast.command.reset", player.getDisplayName()), true);
		return 1;
	}

	private KaastCommands() {
	}
}

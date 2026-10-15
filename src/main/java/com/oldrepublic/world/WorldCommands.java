package com.oldrepublic.world;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.world.trial.CrystalGrant;
import com.oldrepublic.world.trial.TrialManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * {@code /oldrepublic locatecave} and {@code /oldrepublic trial start|stop|reset}.
 */
final class WorldCommands {
	private static final int STONE_SEARCH_RADIUS = 16;

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root
				.then(literal("locatecave").executes(WorldCommands::locate))
				.then(literal("trial")
						.then(literal("start").executes(WorldCommands::startTrial))
						.then(literal("stop").executes(WorldCommands::stopTrial))
						.then(literal("reset").executes(WorldCommands::resetTrial))));
	}

	private static int locate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BlockPos cave = CaveLocator.findNearestCaveNow(player);

		if (cave == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.locatecave.none"));
			return 0;
		}

		int distance = Mth.floor(Math.sqrt(cave.distSqr(player.blockPosition())));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.locatecave.found",
				cave.getX(), cave.getY(), cave.getZ(), distance), false);
		return 1;
	}

	private static int startTrial(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		BlockPos stone = TrialManager.findStone(player.level(), player.blockPosition(), STONE_SEARCH_RADIUS);
		BlockPos at = stone != null ? stone : player.blockPosition();

		if (!TrialManager.start(player, at, true)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trial.start_failed"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable(stone != null ? "commands.oldrepublic.trial.started" : "commands.oldrepublic.trial.started_here",
				at.getX(), at.getY(), at.getZ()), false);
		return 1;
	}

	private static int stopTrial(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();

		if (!TrialManager.stop(player)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trial.none"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.trial.stopped"), false);
		return 1;
	}

	private static int resetTrial(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TrialManager.stop(player);
		CrystalGrant.reset(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.trial.reset", player.getPlainTextName()), false);
		return 1;
	}

	private WorldCommands() {
	}
}

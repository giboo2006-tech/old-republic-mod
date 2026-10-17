package com.oldrepublic.enemy.trooper;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The natural patrol debug commands (spec 7.6): {@code /oldrepublic patrol spawn <player>}
 * spawns a natural patrol for the player now, ignoring the roll and the Chapter Three stage but
 * honouring the overworld, Peaceful, the caps and the placement rules; {@code /oldrepublic patrol
 * clear} discards every natural patrol. Both need the {@code /oldrepublic} permission level.
 */
final class PatrolCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("patrol")
				.then(literal("spawn")
						.then(argument("player", EntityArgument.player())
								.executes(PatrolCommands::spawn)))
				.then(literal("clear")
						.executes(PatrolCommands::clear))));
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		PatrolBalance b = PatrolBalance.get();
		PatrolSpawner.Outcome outcome = PatrolSpawner.trySpawn(player, b);

		if (outcome.result() != PatrolSpawner.Result.SPAWNED || outcome.at() == null) {
			ctx.getSource().sendFailure(switch (outcome.result()) {
				case CAPPED -> Component.translatable("commands.oldrepublic.patrol.capped", b.perPlayerRange, player.getDisplayName(), b.globalCap);
				case NO_SITE -> Component.translatable("commands.oldrepublic.patrol.no_site", b.minDistance, b.maxDistance, player.getDisplayName());
				case WRONG_DIMENSION -> Component.translatable("commands.oldrepublic.patrol.dimension");
				case PEACEFUL -> Component.translatable("commands.oldrepublic.patrol.peaceful");
				default -> Component.translatable("commands.oldrepublic.patrol.failed");
			});
			return 0;
		}

		LivingEntity first = outcome.squad().get(0);
		double dx = outcome.at().getX() + 0.5 - player.getX();
		double dz = outcome.at().getZ() + 0.5 - player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.patrol.spawned", outcome.squad().size(),
				first.getType().getDescription(), distance, player.getDisplayName()), true);
		return outcome.squad().size();
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		int removed = PatrolSpawner.clear();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.patrol.cleared", removed), true);
		return removed;
	}

	private PatrolCommands() {
	}
}

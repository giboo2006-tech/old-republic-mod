package com.oldrepublic.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.QuestStage;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic ...} operator commands for testing and server administration. Features add
 * their own subcommands with {@link #addSubcommand}.
 */
public final class OldRepublicCommands {
	private static final List<BiFunction<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>, LiteralArgumentBuilder<CommandSourceStack>>> EXTENSIONS = new ArrayList<>();

	/** Adds subcommands under {@code /oldrepublic}. Call during mod init. */
	public static void addSubcommand(BiFunction<CommandBuildContext, LiteralArgumentBuilder<CommandSourceStack>, LiteralArgumentBuilder<CommandSourceStack>> extension) {
		EXTENSIONS.add(extension);
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = literal("oldrepublic")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(literal("progress")
							.executes(ctx -> showProgress(ctx, ctx.getSource().getPlayerOrException()))
							.then(argument("player", EntityArgument.player())
									.executes(ctx -> showProgress(ctx, EntityArgument.getPlayer(ctx, "player")))))
					.then(literal("awaken")
							.executes(ctx -> {
								QuestHooks.onAwakeningDue(ctx.getSource().getPlayerOrException(), "command");
								return 1;
							}))
					.then(literal("setlevel")
							.then(argument("level", IntegerArgumentType.integer(1, 100))
									.executes(ctx -> setLevel(ctx, IntegerArgumentType.getInteger(ctx, "level")))))
					.then(literal("addxp")
							.then(argument("amount", IntegerArgumentType.integer(1))
									.executes(ctx -> {
										Progression.addXp(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"), "command");
										return 1;
									})))
					.then(literal("grantskillpoints")
							.then(argument("amount", IntegerArgumentType.integer(1, 100))
									.executes(ctx -> {
										Progression.grantSkillPoints(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
										return 1;
									})))
					.then(literal("energy")
							.then(literal("fill").executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayerOrException();
								ForceData data = Progression.get(player);
								data.energy = data.maxEnergy;
								Progression.commit(player, data);
								return 1;
							})))
					.then(literal("quest")
							.then(literal("set")
									.then(argument("stage", IntegerArgumentType.integer(0, QuestStage.values().length - 1))
											.executes(ctx -> setStage(ctx, IntegerArgumentType.getInteger(ctx, "stage")))))
							.then(literal("reset").executes(ctx -> resetAll(ctx))));

			for (var extension : EXTENSIONS) {
				root = extension.apply(buildContext, root);
			}

			dispatcher.register(root);
		});
	}

	private static int showProgress(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		ForceData d = Progression.get(player);
		ctx.getSource().sendSuccess(() -> Component.literal(String.format(
				"%s: level %d (%d/%d xp), resonance %d, skill points %d, energy %.0f/%.0f, quest %s, abilities %s, push branch '%s', mastery %s",
				player.getName().getString(), d.level, d.xp, d.xpToNext, d.resonance, d.skillPoints, d.energy, d.maxEnergy,
				QuestStage.of(d.questStage), d.abilities, d.pushBranch, d.mastery)), false);
		return 1;
	}

	private static int setLevel(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ForceData d = Progression.get(player);
		d.level = level;
		d.xp = 0;
		Progression.commit(player, d);
		return 1;
	}

	private static int setStage(CommandContext<CommandSourceStack> ctx, int stage) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ForceData d = Progression.get(player);
		d.questStage = stage;
		Progression.commit(player, d);
		ctx.getSource().sendSuccess(() -> Component.literal("Quest stage set to " + QuestStage.of(stage)), false);
		return 1;
	}

	private static int resetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ForceData fresh = new ForceData();
		Progression.commit(player, fresh);
		ctx.getSource().sendSuccess(() -> Component.literal("Force progression reset for " + player.getName().getString()), false);
		return 1;
	}

	private OldRepublicCommands() {
	}
}

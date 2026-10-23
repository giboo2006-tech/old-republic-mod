package com.oldrepublic.quest.story;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic c3 ...} (operators, spec 10.8): inspect Chapter Three and move it for testing.
 * Stages only move forward and run their entry logic; every shortcut is logged.
 * <ul>
 * <li>{@code c3 status <player>}: stage, leads, places, boss state, flags;</li>
 * <li>{@code c3 stage <player> <stage>}: moves the chapter forward to the stage;</li>
 * <li>{@code c3 lead add <player> <lead>}: adds a lead ({@code mission:<id>}, {@code holo:<id>}, {@code ledger}, {@code contract});</li>
 * <li>{@code c3 reliquary <player>}: places the player's Reliquary now (also when {@code collector.placeReliquary} is off).</li>
 * </ul>
 */
final class ChapterThreeCommands {
	static void register() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(c3()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> c3() {
		return literal("c3")
				.then(literal("status").then(argument("player", EntityArgument.player()).executes(ChapterThreeCommands::status)))
				.then(literal("stage").then(argument("player", EntityArgument.player())
						.then(argument("stage", StringArgumentType.word()).executes(ChapterThreeCommands::stage))))
				.then(literal("lead").then(literal("add").then(argument("player", EntityArgument.player())
						.then(argument("lead", StringArgumentType.greedyString()).executes(ChapterThreeCommands::addLead)))))
				.then(literal("reliquary").then(argument("player", EntityArgument.player()).executes(ChapterThreeCommands::reliquary)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);
		StoryData.Chapter3 c = d.chapter3;
		String flags = d.flags.stream().filter(f -> f.startsWith("c3.")).sorted().collect(Collectors.joining(", "));
		String text = String.format(Locale.ROOT,
				"%s: chapter three %s (waiting since %d), leads %s, cairns %d touched of %d, fall %s, shrines %s, reliquary %s, "
						+ "boss %d, outcome %d, stance %d, echo day %d (%d clears), flags [%s]",
				player.getName().getString(), c.stage(), c.waitSince(), c.leads(), Integer.bitCount(c.cairnsTouched()), c.cairns().size(),
				pos(c.fall()), c.shrines(), pos(c.reliquary()), c.boss(), c.outcome(), c.stance(), c.echoDay(), c.echoClears(), flags);
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int stage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ChapterThreeStage stage = ChapterThreeRules.parseStage(StringArgumentType.getString(ctx, "stage"));

		if (stage == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown stage; one of "
					+ Arrays.stream(ChapterThreeStage.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))));
			return 0;
		}

		ChapterThreeStage before = Story.get(player).chapter3.stage();

		if (stage.ordinal() <= before.ordinal()) {
			ctx.getSource().sendFailure(Component.literal("Chapter Three only moves forward; " + player.getName().getString() + " is at "
					+ before.name().toLowerCase(Locale.ROOT)));
			return 0;
		}

		ChapterThreeService.enterStage(player, stage);
		log(ctx, "moved " + player.getName().getString() + " from " + before + " to " + Story.get(player).chapter3.stage());
		return 1;
	}

	private static int addLead(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String lead = StringArgumentType.getString(ctx, "lead").trim();

		if (!ChapterThreeService.addLead(player, lead)) {
			ctx.getSource().sendFailure(Component.literal("Lead not added (already there, blank, or 16 leads): " + lead));
			return 0;
		}

		log(ctx, "added the lead " + lead + " for " + player.getName().getString() + " (" + Story.get(player).chapter3.leads().size() + " leads)");
		return 1;
	}

	private static int reliquary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		if (Story.get(player).chapter3.stage().ordinal() < ChapterThreeStage.RELIQUARY.ordinal()) {
			ChapterThreeService.enterStage(player, ChapterThreeStage.RELIQUARY);
			log(ctx, "moved " + player.getName().getString() + " to RELIQUARY for the placement");
		}

		ChapterThreeService.clearRetry(player, "reliquary");

		if (!ChapterThreeService.placeReliquary(player, true)) {
			ctx.getSource().sendFailure(Component.literal("No Reliquary spot near " + player.getName().getString()
					+ " (overworld only); the story retries every " + ChapterThreeService.balance().retryTicks + " ticks"));
			return 0;
		}

		log(ctx, "placed the Reliquary of " + player.getName().getString() + " (heart " + pos(Story.get(player).chapter3.reliquary()) + ")");
		return 1;
	}

	private static String pos(@Nullable BlockPos pos) {
		return pos == null ? "none" : pos.toShortString();
	}

	private static void log(CommandContext<CommandSourceStack> ctx, String what) {
		OldRepublicMod.LOGGER.info("[c3 command] {} {}", ctx.getSource().getTextName(), what);
		ctx.getSource().sendSuccess(() -> Component.literal("Chapter Three: " + what), true);
	}

	private ChapterThreeCommands() {
	}
}

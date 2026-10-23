package com.oldrepublic.quest.story;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.kaast.KaastFight;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.travel.ShipRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic c4 ...} (operators): inspect Chapter Four and move it for testing. Stages
 * only move forward and run their entry logic; every shortcut is logged. Each command takes one
 * player.
 * <ul>
 * <li>{@code c4 status <player>}: stage, arc, hunt, duel, sites and the chapter's flags;</li>
 * <li>{@code c4 stage <player> <stage>}: moves the chapter forward to the stage;</li>
 * <li>{@code c4 seeker <player>}: plays the seeker scene now (from MARKED);</li>
 * <li>{@code c4 clue <player> <1-3>}: records a clue as the story would;</li>
 * <li>{@code c4 sabotage <player>}: blows the landing strut now (clue 1, the ship to hull 10);</li>
 * <li>{@code c4 backpay <player>}: settles the M3 back pay now (once per player);</li>
 * <li>{@code c4 camp <player>}: retries the hunting camp's placement at once;</li>
 * <li>{@code c4 yield <player>}: Kaast yields to the player (DUEL; opens the choice);</li>
 * <li>{@code c4 report <player>}: makes the report (from AFTERMATH).</li>
 * </ul>
 */
final class ChapterFourCommands {
	static void register() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(c4()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> c4() {
		return literal("c4")
				.then(literal("status").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::status)))
				.then(literal("stage").then(argument("player", EntityArgument.player())
						.then(argument("stage", StringArgumentType.word()).executes(ChapterFourCommands::stage))))
				.then(literal("seeker").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::seeker)))
				.then(literal("clue").then(argument("player", EntityArgument.player())
						.then(argument("clue", IntegerArgumentType.integer(1, ChapterFour.MAX_CLUE)).executes(ChapterFourCommands::clue))))
				.then(literal("sabotage").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::sabotage)))
				.then(literal("backpay").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::backPay)))
				.then(literal("camp").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::camp)))
				.then(literal("yield").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::yield)))
				.then(literal("report").then(argument("player", EntityArgument.player()).executes(ChapterFourCommands::report)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);
		StoryData.Chapter4 c = d.chapter4;
		String flags = d.flags.stream().filter(f -> f.startsWith("c4.")).sorted().collect(Collectors.joining(", "));
		String counters = d.counters.entrySet().stream().filter(e -> e.getKey().startsWith("c4.")).sorted(Map.Entry.comparingByKey())
				.map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
		String sites = c.sites().places().entrySet().stream().map(e -> e.getKey() + "@" + pos(e.getValue())).collect(Collectors.joining(", "));
		String text = String.format(Locale.ROOT,
				"%s: chapter four %s (since %d), arc %s steps %d expedition %d, clues %d (mask %d), sabotage %s, ambush day %d (%d), "
						+ "camp %s, outcome %d, boss %d, back pay %s, sites [%s], flags [%s], counters [%s]",
				player.getName().getString(), c.stage(), c.waitSince(), c.arc().planet(), c.arc().missionFlags(), c.arc().expedition(),
				Integer.bitCount(c.hunt().clues()), c.hunt().clues(), c.hunt().sabotageDone(), c.hunt().ambushDay(), c.hunt().ambushes(),
				pos(c.duel().camp()), c.duel().outcome(), c.duel().boss(), c.backPay(), sites, flags, counters);
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int stage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ChapterFourStage stage = ChapterFourRules.parseStage(StringArgumentType.getString(ctx, "stage"));

		if (stage == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown stage; one of "
					+ Arrays.stream(ChapterFourStage.values()).map(ChapterFourRules::stageId).collect(Collectors.joining(", "))));
			return 0;
		}

		ChapterFourStage before = Story.get(player).chapter4.stage();

		if (!ChapterFourService.enterStage(player, stage)) {
			ctx.getSource().sendFailure(Component.literal("Chapter Four only moves forward; " + player.getName().getString() + " is at "
					+ ChapterFourRules.stageId(before)));
			return 0;
		}

		log(ctx, "moved " + player.getName().getString() + " from " + before + " to " + Story.get(player).chapter4.stage());
		return 1;
	}

	private static int seeker(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ChapterFourStage stage = Story.get(player).chapter4.stage();

		if (stage != ChapterFourStage.MARKED && stage != ChapterFourStage.NOT_STARTED) {
			ctx.getSource().sendFailure(Component.literal("The seeker plays only from MARKED; " + player.getName().getString() + " is at "
					+ ChapterFourRules.stageId(stage)));
			return 0;
		}

		ChapterFourService.enterStage(player, ChapterFourStage.MARKED);
		SeekerScene.start(player);
		log(ctx, "started the seeker scene for " + player.getName().getString());
		return 1;
	}

	private static int clue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int clue = IntegerArgumentType.getInteger(ctx, "clue");

		if (ChapterFour.hasClue(player, clue)) {
			ctx.getSource().sendFailure(Component.literal(player.getName().getString() + " already has clue " + clue));
			return 0;
		}

		ChapterFour.addClue(player, clue);
		log(ctx, "gave " + player.getName().getString() + " clue " + clue + " (" + ChapterFour.clues(player) + " held)");
		return 1;
	}

	private static int sabotage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		if (Story.get(player).chapter4.hunt().sabotageDone()) {
			ctx.getSource().sendFailure(Component.literal("The sabotage already happened for " + player.getName().getString()));
			return 0;
		}

		TheHunt.sabotage(player, ShipRegistry.of(player.level().getServer(), player.getUUID()).orElse(null));
		log(ctx, "blew the landing strut of " + player.getName().getString());
		return 1;
	}

	private static int backPay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		if (Story.get(player).chapter4.backPay()) {
			ctx.getSource().sendFailure(Component.literal("The back pay of " + player.getName().getString() + " is settled already"));
			return 0;
		}

		int paid = ChapterFourService.settleBackPay(player);
		log(ctx, "settled the back pay of " + player.getName().getString() + ": " + paid + " credits");
		return 1;
	}

	private static int camp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ChapterFourService.clearRetry(player, "camp");
		GlobalPos camp = KaastDuel.placeCamp(player);

		if (camp == null) {
			ctx.getSource().sendFailure(Component.literal("No camp for " + player.getName().getString() + " yet (W6's placement refused); retrying every "
					+ ChapterFourService.balance().retryTicks + " ticks"));
			return 0;
		}

		log(ctx, "the camp of " + player.getName().getString() + " is at " + pos(camp));
		return 1;
	}

	private static int yield(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ChapterFourStage stage = Story.get(player).chapter4.stage();

		if (stage != ChapterFourStage.CHALLENGED && stage != ChapterFourStage.DUEL) {
			ctx.getSource().sendFailure(Component.literal("Kaast yields only in CHALLENGED or DUEL; " + player.getName().getString() + " is at "
					+ ChapterFourRules.stageId(stage)));
			return 0;
		}

		// A running fight yields as at his threshold (mines, hunters and Rattle gone); the choice opens either way.
		KaastFight.forceYield(player);
		KaastDuel.onYielded(player);
		log(ctx, "Kaast yielded to " + player.getName().getString());
		return 1;
	}

	private static int report(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		if (!KaastDuel.reportReady(Story.get(player))) {
			ctx.getSource().sendFailure(Component.literal("The report is not open for " + player.getName().getString()));
			return 0;
		}

		KaastDuel.report(player);
		log(ctx, "made the report of " + player.getName().getString());
		return 1;
	}

	private static String pos(@org.jspecify.annotations.Nullable GlobalPos pos) {
		return pos == null ? "none" : pos.dimension().identifier() + " " + pos.pos().toShortString();
	}

	private static void log(CommandContext<CommandSourceStack> ctx, String what) {
		OldRepublicMod.LOGGER.info("[c4 command] {} {}", ctx.getSource().getTextName(), what);
		ctx.getSource().sendSuccess(() -> Component.literal("Chapter Four: " + what), true);
	}

	private ChapterFourCommands() {
	}
}

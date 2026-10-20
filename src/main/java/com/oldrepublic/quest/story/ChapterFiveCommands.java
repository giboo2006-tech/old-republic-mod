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
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.mission.MissionService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic c5 ...} (operators): inspect Chapter Five and move it for testing, standing in
 * for the hooks other workers fire until integration (W2's blockade and cargo run, W3's verse stones,
 * W8's yield). Stages only move forward and run their entry logic; every shortcut is logged. Each
 * command takes one player.
 * <ul>
 * <li>{@code c5 status <player>}: stage, ways, Dask, blockade failures, verses, the fight, flags and counters;</li>
 * <li>{@code c5 stage <player> <stage>}: moves the chapter forward to the stage;</li>
 * <li>{@code c5 call <player>}: plays the call now (from WAITING, or a save at Chapter Four COMPLETE);</li>
 * <li>{@code c5 way <player> <kajidic|clan|force>}: completes a Saltgate way as the story would;</li>
 * <li>{@code c5 cargo <player> <success|fail>}: ends the quiet cargo's run as W2 would;</li>
 * <li>{@code c5 dask <player> <release|clan|end>}: chooses Dask's fate (THE_FACTOR; the counting-house counts as cleared);</li>
 * <li>{@code c5 blockade <player> <broken|failed>}: W2's blockade result;</li>
 * <li>{@code c5 verse <player> <1-3>}: W3's verse stone silenced;</li>
 * <li>{@code c5 yield <player>}: Ulvaine yields to the player (THE_HYMNAL; opens the choice);</li>
 * <li>{@code c5 choose <player> <spare|bind|end>}: makes the choice over Ulvaine;</li>
 * <li>{@code c5 report <player>}: makes the report (from AFTERMATH);</li>
 * <li>{@code c5 daily <player>}: forgets today's cipher, shard, helper reward and bounties (test aid).</li>
 * </ul>
 */
final class ChapterFiveCommands {
	private static final String PLAYER = "player";

	static void register() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(c5()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> c5() {
		return literal("c5")
				.then(literal("status").then(argument(PLAYER, EntityArgument.player()).executes(ChapterFiveCommands::status)))
				.then(literal("stage").then(argument(PLAYER, EntityArgument.player())
						.then(argument("stage", StringArgumentType.word()).executes(ChapterFiveCommands::stage))))
				.then(literal("call").then(argument(PLAYER, EntityArgument.player()).executes(ChapterFiveCommands::call)))
				.then(literal("way").then(argument(PLAYER, EntityArgument.player())
						.then(argument("way", StringArgumentType.word()).executes(ChapterFiveCommands::way))))
				.then(literal("cargo").then(argument(PLAYER, EntityArgument.player())
						.then(argument("result", StringArgumentType.word()).executes(ChapterFiveCommands::cargo))))
				.then(literal("dask").then(argument(PLAYER, EntityArgument.player())
						.then(argument("choice", StringArgumentType.word()).executes(ChapterFiveCommands::dask))))
				.then(literal("blockade").then(argument(PLAYER, EntityArgument.player())
						.then(argument("result", StringArgumentType.word()).executes(ChapterFiveCommands::blockade))))
				.then(literal("verse").then(argument(PLAYER, EntityArgument.player())
						.then(argument("stone", IntegerArgumentType.integer(1, 3)).executes(ChapterFiveCommands::verse))))
				.then(literal("yield").then(argument(PLAYER, EntityArgument.player()).executes(ChapterFiveCommands::yield)))
				.then(literal("choose").then(argument(PLAYER, EntityArgument.player())
						.then(argument("choice", StringArgumentType.word()).executes(ChapterFiveCommands::choose))))
				.then(literal("report").then(argument(PLAYER, EntityArgument.player()).executes(ChapterFiveCommands::report)))
				.then(literal("daily").then(argument(PLAYER, EntityArgument.player()).executes(ChapterFiveCommands::daily)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		StoryData d = Story.get(player);
		StoryData.Chapter5 c = d.chapter5;
		String ways = Arrays.stream(ChapterFive.Way.values()).filter(c::hasWay).map(ChapterFive.Way::id).collect(Collectors.joining(", "));
		String flags = d.flags.stream().filter(f -> f.startsWith("c5.")).sorted().collect(Collectors.joining(", "));
		String counters = d.counters.entrySet().stream().filter(e -> e.getKey().startsWith("c5.")).sorted(Map.Entry.comparingByKey())
				.map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
		ChapterFive.Way first = c.chosenWay();
		String text = String.format(Locale.ROOT,
				"%s: chapter five %s (since %d), ways [%s] first %s, dask %s, blockade failures %d, verses %d (mask %d), boss %d, ulvaine %s, "
						+ "clan stands %s, flags [%s], counters [%s]",
				player.getName().getString(), c.stage().id(), c.waitSince(), ways, first == null ? "none" : first.id(),
				ChapterFiveRules.daskId(c.dask()).isEmpty() ? "none" : ChapterFiveRules.daskId(c.dask()), c.blockadeFailures(),
				ChapterFiveRules.versesSilenced(c.verses()), c.verses(), c.boss(),
				c.ulvaineOutcome() == null ? "none" : ChapterFiveRules.outcomeId(c.ulvaineOutcome()), ChapterFive.clanStands(player), flags, counters);
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int stage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		ChapterFiveStage stage = ChapterFiveRules.parseStage(StringArgumentType.getString(ctx, "stage"));

		if (stage == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown stage; one of "
					+ Arrays.stream(ChapterFiveStage.values()).map(ChapterFiveStage::id).collect(Collectors.joining(", "))));
			return 0;
		}

		ChapterFiveStage before = Story.get(player).chapter5.stage();

		if (!ChapterFiveService.enterStage(player, stage)) {
			ctx.getSource().sendFailure(Component.literal("Chapter Five only moves forward; " + player.getName().getString() + " is at " + before.id()));
			return 0;
		}

		log(ctx, "moved " + player.getName().getString() + " from " + before.id() + " to " + Story.get(player).chapter5.stage().id());
		return 1;
	}

	private static int call(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		ChapterFiveService.checkWaiting(player);
		ChapterFiveStage stage = Story.get(player).chapter5.stage();

		if (stage != ChapterFiveStage.WAITING) {
			ctx.getSource().sendFailure(Component.literal("The call plays only from WAITING; " + player.getName().getString() + " is at " + stage.id()));
			return 0;
		}

		TheCall.play(player);
		log(ctx, "played the call for " + player.getName().getString());
		return 1;
	}

	private static int way(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		ChapterFive.Way way = ChapterFive.Way.byId(StringArgumentType.getString(ctx, "way").toLowerCase(Locale.ROOT));
		ChapterFiveStage stage = Story.get(player).chapter5.stage();

		if (way == null || stage.ordinal() < ChapterFiveStage.SALTGATE.ordinal()) {
			ctx.getSource().sendFailure(Component.literal("Give kajidic, clan or force, from SALTGATE on; " + player.getName().getString() + " is at " + stage.id()));
			return 0;
		}

		SaltgateWays.completeWay(player, way);
		log(ctx, "completed the " + way.id() + " way for " + player.getName().getString());
		return 1;
	}

	private static int cargo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		String result = StringArgumentType.getString(ctx, "result");

		if (!ChapterFiveRules.CARGO.equals(MissionService.activeId(player))) {
			ctx.getSource().sendFailure(Component.literal(player.getName().getString() + " is not flying the quiet cargo"));
			return 0;
		}

		SaltgateWays.onCargoDone(player, "success".equalsIgnoreCase(result));
		log(ctx, "ended the quiet cargo of " + player.getName().getString() + ": " + result);
		return 1;
	}

	private static int dask(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		String choice = StringArgumentType.getString(ctx, "choice").toLowerCase(Locale.ROOT);
		StoryData data = Story.get(player);

		if (data.chapter5.stage() != ChapterFiveStage.THE_FACTOR || ChapterFiveRules.daskChoice(choice) == StoryData.Chapter5.DASK_NONE) {
			ctx.getSource().sendFailure(Component.literal("Give release, clan or end, in THE_FACTOR; " + player.getName().getString() + " is at "
					+ data.chapter5.stage().id()));
			return 0;
		}

		if (data.flags.add(ChapterFiveRules.COUNTING_HOUSE) | data.flags.add(ChapterFiveRules.MET_VARRA)) {
			Story.commit(player, data);
		}

		CountingHouse.choose(player, null, choice);
		log(ctx, "chose " + choice + " for Dask; " + player.getName().getString() + " is at " + Story.get(player).chapter5.stage().id());
		return 1;
	}

	private static int blockade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		String result = StringArgumentType.getString(ctx, "result").toLowerCase(Locale.ROOT);
		ChapterFiveStage stage = Story.get(player).chapter5.stage();

		if (stage != ChapterFiveStage.BLOCKADE) {
			ctx.getSource().sendFailure(Component.literal("The blockade runs only in BLOCKADE; " + player.getName().getString() + " is at " + stage.id()));
			return 0;
		}

		if ("broken".equals(result)) {
			ChapterFiveService.onBlockadeBroken(player);
		} else {
			ChapterFiveService.onBlockadeFailed(player);
		}

		log(ctx, "blockade " + result + " for " + player.getName().getString() + " (failures " + Story.get(player).chapter5.blockadeFailures() + ")");
		return 1;
	}

	private static int verse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		int stone = IntegerArgumentType.getInteger(ctx, "stone");

		if (Story.get(player).chapter5.stage().ordinal() < ChapterFiveStage.VERSES.ordinal()) {
			ctx.getSource().sendFailure(Component.literal("The verse stones wake in VERSES"));
			return 0;
		}

		ChapterFiveService.onVerseSilenced(player, stone);
		log(ctx, "silenced verse stone " + stone + " for " + player.getName().getString() + " ("
				+ ChapterFiveRules.versesSilenced(Story.get(player).chapter5.verses()) + "/3)");
		return 1;
	}

	private static int yield(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		ChapterFiveStage stage = Story.get(player).chapter5.stage();

		if (stage != ChapterFiveStage.THE_HYMNAL) {
			ctx.getSource().sendFailure(Component.literal("Ulvaine yields only in THE_HYMNAL; " + player.getName().getString() + " is at " + stage.id()));
			return 0;
		}

		UlvaineChoice.onYielded(player);
		log(ctx, "Ulvaine yielded to " + player.getName().getString());
		return 1;
	}

	private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		String choice = StringArgumentType.getString(ctx, "choice").toLowerCase(Locale.ROOT);

		if (ChapterFiveRules.outcomeOf(choice) == null || !ChapterFiveRules.choiceOpen(Story.get(player))) {
			ctx.getSource().sendFailure(Component.literal("Give spare, bind or end, after Ulvaine yielded (c5 yield)"));
			return 0;
		}

		UlvaineChoice.resolve(player, choice);
		log(ctx, "chose " + choice + " for Ulvaine; " + player.getName().getString() + " is at " + Story.get(player).chapter5.stage().id());
		return 1;
	}

	private static int report(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);

		if (!UlvaineChoice.report(player)) {
			ctx.getSource().sendFailure(Component.literal("The report is not open for " + player.getName().getString()));
			return 0;
		}

		log(ctx, "made the report of " + player.getName().getString());
		return 1;
	}

	private static int daily(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, PLAYER);
		StoryData data = Story.get(player);
		data.counters.keySet().removeIf(k -> k.equals(ChapterFiveRules.CIPHER_DAY) || k.equals(ChapterFiveRules.SHARD_WEEK)
				|| k.equals(ChapterFiveRules.HELPER_DAY) || k.startsWith(ChapterFiveRules.BOUNTY_DAY_PREFIX));
		Story.commit(player, data);
		log(ctx, "forgot today's cipher, shard, helper reward and bounties of " + player.getName().getString());
		return 1;
	}

	private static void log(CommandContext<CommandSourceStack> ctx, String what) {
		OldRepublicMod.LOGGER.info("[c5 command] {} {}", ctx.getSource().getTextName(), what);
		ctx.getSource().sendSuccess(() -> Component.literal("Chapter Five: " + what), true);
	}

	private ChapterFiveCommands() {
	}
}

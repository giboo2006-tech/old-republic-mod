package com.oldrepublic.crafting;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.item.SchematicItem;
import com.oldrepublic.quest.Rewards;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic crafting ...} test commands (each takes one player, and every change is logged):
 * <ul>
 * <li>{@code info <player>}: disciplines, levels, XP, pity and known schematics</li>
 * <li>{@code choose <player> <discipline>}: chooses a discipline with the bench's rules</li>
 * <li>{@code level <player> <discipline> <level>}: sets the level (and the XP to its threshold), adding the discipline if needed</li>
 * <li>{@code pity <player> <discipline> <count>}: sets the Masterwork pity counter</li>
 * <li>{@code unlearn <player> <discipline>}: drops a discipline (no fee)</li>
 * <li>{@code teach <player> <schematic>}: teaches a schematic</li>
 * <li>{@code schematic <player> <schematic>}: gives a schematic item</li>
 * <li>{@code reset <player>}: clears all crafting state</li>
 * </ul>
 */
final class CraftingCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("crafting")
				.then(literal("info").then(player().executes(CraftingCommands::info)))
				.then(literal("choose").then(player().then(discipline().executes(CraftingCommands::choose))))
				.then(literal("level").then(player().then(discipline()
						.then(argument("level", IntegerArgumentType.integer(1, 5)).executes(CraftingCommands::level)))))
				.then(literal("pity").then(player().then(discipline()
						.then(argument("count", IntegerArgumentType.integer(0, 100)).executes(CraftingCommands::pity)))))
				.then(literal("unlearn").then(player().then(discipline().executes(CraftingCommands::unlearn))))
				.then(literal("teach").then(player().then(argument("schematic", IdentifierArgument.id())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Schematics.all().stream().map(s -> s.id().toString()), builder))
						.executes(CraftingCommands::teach))))
				.then(literal("schematic").then(player().then(argument("schematic", IdentifierArgument.id())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Schematics.all().stream().map(s -> s.id().toString()), builder))
						.executes(CraftingCommands::giveSchematic))))
				.then(literal("reset").then(player().executes(CraftingCommands::reset)))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, ?> player() {
		return argument("player", EntityArgument.player());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> discipline() {
		return argument("discipline", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Discipline.values()).map(Discipline::id), builder));
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, "player");
	}

	private static Discipline disciplineArg(CommandContext<CommandSourceStack> ctx) {
		Discipline d = Discipline.byId(StringArgumentType.getString(ctx, "discipline"));

		if (d == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown discipline; one of " + Arrays.stream(Discipline.values()).map(Discipline::id).toList()));
		}

		return d;
	}

	private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		CraftingData data = CraftingAttachments.get(p);
		StringBuilder text = new StringBuilder(p.getPlainTextName()).append(": ");

		if (data.disciplines().isEmpty()) {
			text.append("no discipline");
		}

		for (Progress progress : data.disciplines()) {
			text.append(progress.discipline().id()).append(" L").append(progress.level()).append(" xp ").append(progress.xp())
					.append(" pity ").append(progress.pity()).append("; ");
		}

		text.append(" unlearned ").append(data.unlearned()).append(", known ").append(data.known().size())
				.append(", first crafts ").append(data.firstCrafts().size()).append(", craftable ").append(Crafting.craftable(data).size());
		ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Discipline d = disciplineArg(ctx);

		if (d == null) {
			return 0;
		}

		Crafting.ChoiceResult result = Crafting.choose(p, d);
		log(ctx, p, "choose " + d.id() + ": " + result);
		return result == Crafting.ChoiceResult.CHOSEN ? 1 : 0;
	}

	private static int level(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Discipline d = disciplineArg(ctx);

		if (d == null) {
			return 0;
		}

		int level = IntegerArgumentType.getInteger(ctx, "level");
		CraftingData data = CraftingAttachments.get(p);
		int[] thresholds = CraftingBalance.get().levelXp;
		int xp = level - 1 < thresholds.length ? thresholds[level - 1] : 0;
		int pity = data.progress(d).map(Progress::pity).orElse(0);
		CraftingData next = data.withProgress(new Progress(d, level, xp, pity));

		if (!next.has(d)) {
			ctx.getSource().sendFailure(Component.literal("The player already has two disciplines"));
			return 0;
		}

		CraftingAttachments.set(p, next);
		log(ctx, p, "set " + d.id() + " to level " + level);
		return 1;
	}

	private static int pity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Discipline d = disciplineArg(ctx);

		if (d == null) {
			return 0;
		}

		CraftingData data = CraftingAttachments.get(p);
		Progress progress = data.progress(d).orElse(null);

		if (progress == null) {
			ctx.getSource().sendFailure(Component.literal("The player does not have " + d.id()));
			return 0;
		}

		int count = IntegerArgumentType.getInteger(ctx, "count");
		CraftingAttachments.set(p, data.withProgress(new Progress(d, progress.level(), progress.xp(), count)));
		log(ctx, p, "set " + d.id() + " pity to " + count);
		return 1;
	}

	private static int unlearn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Discipline d = disciplineArg(ctx);

		if (d == null || !Crafting.unlearn(p, d)) {
			return 0;
		}

		log(ctx, p, "unlearned " + d.id());
		return 1;
	}

	private static int teach(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Identifier id = IdentifierArgument.getId(ctx, "schematic");
		Crafting.teach(p, id);
		log(ctx, p, "taught " + id + (Schematics.get(id) == null ? " (not loaded)" : ""));
		return 1;
	}

	private static int giveSchematic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		Identifier id = IdentifierArgument.getId(ctx, "schematic");
		Rewards.give(p, SchematicItem.of(id));
		log(ctx, p, "gave a schematic item for " + id);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = target(ctx);
		CraftingAttachments.set(p, CraftingData.EMPTY);
		log(ctx, p, "reset crafting");
		return 1;
	}

	private static void log(CommandContext<CommandSourceStack> ctx, ServerPlayer p, String what) {
		String line = "Crafting: " + p.getPlainTextName() + " " + what;
		OldRepublicMod.LOGGER.info("[command] {}", line);
		ctx.getSource().sendSuccess(() -> Component.literal(line), true);
	}

	private CraftingCommands() {
	}
}

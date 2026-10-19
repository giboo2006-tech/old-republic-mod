package com.oldrepublic.identity;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.dialogue.DialogueLoader;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic identity ...} and {@code /oldrepublic dialogue ...}: inspect and set
 * alignment, reputation, path, rank, trust and choices; report contextual acts; spawn settlers and
 * captives; force a yield; open, drive and close dialogues. Every command that changes identity
 * is logged.
 */
final class IdentityCommands {
	static void register() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(identity()).then(dialogue()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> identity() {
		return literal("identity")
				.executes(ctx -> show(ctx, ctx.getSource().getPlayerOrException()))
				.then(literal("show").then(argument("player", EntityArgument.player())
						.executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("alignment").then(argument("player", EntityArgument.player())
						.then(literal("set").then(argument("value", IntegerArgumentType.integer(-100, 100))
								.executes(ctx -> setAlignment(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
						.then(literal("shift").then(argument("amount", IntegerArgumentType.integer(-100, 100))
								.executes(ctx -> shift(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false))))
						.then(literal("minor").then(argument("amount", IntegerArgumentType.integer(-100, 100))
								.executes(ctx -> shift(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true))))))
				.then(literal("contextual").then(argument("player", EntityArgument.player())
						.then(argument("act", StringArgumentType.word()).executes(IdentityCommands::contextual))))
				.then(literal("reputation").then(argument("player", EntityArgument.player())
						.then(argument("faction", StringArgumentType.word())
								.then(literal("set").then(argument("value", IntegerArgumentType.integer(-1000, 1000))
										.executes(ctx -> reputation(ctx, true))))
								.then(literal("add").then(argument("value", IntegerArgumentType.integer(-2000, 2000))
										.executes(ctx -> reputation(ctx, false)))))))
				.then(literal("path").then(argument("player", EntityArgument.player())
						.then(argument("path", StringArgumentType.word())
								.executes(ctx -> path(ctx, false))
								.then(literal("force").executes(ctx -> path(ctx, true))))))
				.then(literal("rank").then(argument("player", EntityArgument.player())
						.then(literal("set").then(argument("rank", IntegerArgumentType.integer(0, 4))
								.executes(ctx -> rank(ctx, false))))
						.then(literal("promote").then(argument("rank", IntegerArgumentType.integer(1, 4))
								.executes(ctx -> rank(ctx, true))))))
				.then(literal("trust").then(argument("player", EntityArgument.player())
						.then(argument("mentor", StringArgumentType.word())
								.then(literal("set").then(argument("value", IntegerArgumentType.integer(0, 100))
										.executes(ctx -> trust(ctx, true))))
								.then(literal("add").then(argument("value", IntegerArgumentType.integer(-100, 100))
										.executes(ctx -> trust(ctx, false)))))))
				.then(literal("choice").then(argument("player", EntityArgument.player())
						.then(literal("add").then(argument("id", StringArgumentType.greedyString()).executes(ctx -> choice(ctx, true))))
						.then(literal("remove").then(argument("id", StringArgumentType.greedyString()).executes(ctx -> choice(ctx, false))))))
				.then(literal("introduce").then(argument("player", EntityArgument.player())
						.executes(ctx -> {
							ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
							Identity.recordChoice(player, Identity.INTRODUCED);
							return done(ctx, "Alignment and reputation toasts enabled for " + player.getName().getString());
						})))
				.then(literal("reset").then(argument("player", EntityArgument.player())
						.executes(ctx -> {
							ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
							Identity.commit(player, new IdentityData());
							log(ctx, "reset identity of " + player.getName().getString());
							return done(ctx, "Identity reset for " + player.getName().getString());
						})))
				.then(literal("settler")
						.then(literal("free").executes(IdentityCommands::spawnFree))
						.then(literal("captives").executes(IdentityCommands::spawnCaptives)))
				.then(literal("yield").then(argument("entity", EntityArgument.entity())
						.executes(IdentityCommands::forceYield)));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> dialogue() {
		return literal("dialogue")
				.then(literal("list").executes(ctx -> done(ctx, "Dialogues: " + DialogueLoader.ids().stream().map(Identifier::toString).sorted().collect(Collectors.joining(", ")))))
				.then(literal("open").then(argument("id", IdentifierArgument.id())
						.executes(ctx -> open(ctx, null))
						.then(argument("speaker", EntityArgument.entity())
								.executes(ctx -> open(ctx, EntityArgument.getEntity(ctx, "speaker"))))))
				.then(literal("choose").then(argument("option", IntegerArgumentType.integer(1, 6))
						.executes(IdentityCommands::choose)))
				.then(literal("close").executes(ctx -> {
					Dialogues.close(ctx.getSource().getPlayerOrException());
					return 1;
				}));
	}

	// ---------------------------------------------------------------- identity

	private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		IdentityData d = Identity.get(player);
		IdentityBalance b = Identity.balance();
		List<String> reps = new ArrayList<>();

		for (Faction faction : Faction.values()) {
			// A faction the player has not met stays out of the list unless a command already gave it a value (MILESTONE_5.md 7.3).
			if (IdentityRules.knows(d, faction) || d.reputation(faction) != 0) {
				reps.add(faction.id() + " " + d.reputation(faction) + " (" + IdentityRules.tier(d.reputation(faction), b).name().toLowerCase(Locale.ROOT) + ")");
			}
		}

		String log = d.alignmentLog.stream().map(e -> (e.amount() > 0 ? "+" : "") + e.amount() + " " + e.reason() + " d" + e.day()).collect(Collectors.joining("; "));
		return done(ctx, String.format(Locale.ROOT,
				"%s: alignment %d (%s), today light %d / dark %d / severe %d; reputation %s; path %s, rank %d; trust %s; choices %s; log [%s]",
				player.getName().getString(), d.alignment, IdentityRules.band(d.alignment, b), d.minorLight, d.minorDark, d.severeDark,
				String.join(", ", reps), d.path.id(), d.rank, d.trust, d.choices.stream().sorted().toList(), log));
	}

	private static int setAlignment(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Identity.setAlignment(player, value, "alignment.oldrepublic.reason.command");
		log(ctx, "set alignment of " + player.getName().getString() + " to " + value);
		return done(ctx, "Alignment of " + player.getName().getString() + " is now " + Identity.alignment(player));
	}

	private static int shift(CommandContext<CommandSourceStack> ctx, int amount, boolean minor) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		// An empty source is never recorded, so operator shifts repeat freely.
		String source = minor ? "command.minor" : "";
		AlignmentShift.Kind kind = minor ? AlignmentShift.Kind.MINOR : AlignmentShift.Kind.MAJOR;
		boolean applied = Identity.shift(player, new AlignmentShift(source, amount, kind, "alignment.oldrepublic.reason.command"));
		log(ctx, (minor ? "minor" : "major") + " alignment shift " + amount + " for " + player.getName().getString() + (applied ? "" : " (ignored by a cap)"));
		return done(ctx, (applied ? "Shifted. " : "Ignored (cap). ") + "Alignment of " + player.getName().getString() + " is " + Identity.alignment(player));
	}

	private static int contextual(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String name = StringArgumentType.getString(ctx, "act").toUpperCase(Locale.ROOT);
		ContextAct act;

		try {
			act = ContextAct.valueOf(name);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Unknown act; one of " + List.of(ContextAct.values())));
			return 0;
		}

		int before = Identity.alignment(player);
		Identity.contextual(player, act, null);
		log(ctx, "contextual " + act + " for " + player.getName().getString());
		return done(ctx, act + ": alignment " + before + " -> " + Identity.alignment(player));
	}

	private static int reputation(CommandContext<CommandSourceStack> ctx, boolean set) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Faction faction = Faction.byId(StringArgumentType.getString(ctx, "faction"));
		int value = IntegerArgumentType.getInteger(ctx, "value");

		if (faction == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown faction; one of "
					+ Arrays.stream(Faction.values()).map(Faction::id).collect(Collectors.joining(", "))));
			return 0;
		}

		if (set) {
			Identity.setReputation(player, faction, value);
		} else {
			Identity.changeReputation(player, faction, value, "command", false);
		}

		log(ctx, (set ? "set" : "changed") + " " + faction.id() + " reputation of " + player.getName().getString() + " by/to " + value);
		return done(ctx, faction.id() + " reputation of " + player.getName().getString() + " is " + Identity.reputation(player, faction));
	}

	private static int path(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "path").toLowerCase(Locale.ROOT);
		Path path = Path.byId(id);

		if (!path.id().equals(id)) {
			ctx.getSource().sendFailure(Component.literal("Unknown path; one of none, jedi, sith, independent"));
			return 0;
		}

		boolean changed = Identity.setPath(player, path, force);
		log(ctx, "path of " + player.getName().getString() + " -> " + path.id() + (force ? " (forced)" : "") + (changed ? "" : " refused"));

		if (!changed) {
			ctx.getSource().sendFailure(Component.literal("Path change refused (same path, or not allowed without 'force')"));
			return 0;
		}

		return done(ctx, "Path of " + player.getName().getString() + " is now " + path.id() + ", rank " + Identity.rank(player));
	}

	private static int rank(CommandContext<CommandSourceStack> ctx, boolean promote) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int rank = IntegerArgumentType.getInteger(ctx, "rank");

		if (promote) {
			Identity.promote(player, rank);
		} else {
			Identity.setRank(player, rank);
		}

		log(ctx, (promote ? "promoted " : "set rank of ") + player.getName().getString() + " to " + rank);
		return done(ctx, "Rank of " + player.getName().getString() + " is " + Identity.rank(player));
	}

	private static int trust(CommandContext<CommandSourceStack> ctx, boolean set) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String mentor = StringArgumentType.getString(ctx, "mentor");
		int value = IntegerArgumentType.getInteger(ctx, "value");

		if (!mentor.equals("jedi") && !mentor.equals("sith")) {
			ctx.getSource().sendFailure(Component.literal("Unknown mentor; jedi or sith"));
			return 0;
		}

		Identity.addTrust(player, mentor, set ? value - Identity.trust(player, mentor) : value, "command");
		log(ctx, (set ? "set" : "changed") + " " + mentor + " trust of " + player.getName().getString() + " by/to " + value);
		return done(ctx, mentor + " trust of " + player.getName().getString() + " is " + Identity.trust(player, mentor));
	}

	private static int choice(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id");
		IdentityData data = Identity.get(player);
		boolean changed = add ? data.choices.add(id) : data.choices.remove(id);
		Identity.commit(player, data);
		log(ctx, (add ? "recorded" : "removed") + " choice " + id + " for " + player.getName().getString());
		return done(ctx, (changed ? "" : "Unchanged: ") + "choice " + id + (add ? " recorded" : " removed"));
	}

	private static int spawnFree(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Settlers.spawnFree(player.level(), BlockPos.containing(ahead(player, 3)));
		return done(ctx, "Spawned a free settler");
	}

	private static int spawnCaptives(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Vec3 centre = ahead(player, 4);
		Vec3 side = Vec3.directionFromRotation(0f, player.getYRot() + 90f);
		List<BlockPos> posts = new ArrayList<>();

		for (int i = -1; i <= 1; i++) {
			posts.add(BlockPos.containing(centre.add(side.scale(i * 2.0))));
		}

		String group = "command_" + player.level().getGameTime();
		Settlers.spawnCaptives(player.level(), posts, player.getUUID(), group);
		return done(ctx, "Spawned 3 captives, group " + group);
	}

	private static int forceYield(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Entity entity = EntityArgument.getEntity(ctx, "entity");

		if (!(entity instanceof LivingEntity living) || !(entity instanceof Yieldable)) {
			ctx.getSource().sendFailure(Component.literal("That entity cannot yield"));
			return 0;
		}

		if (!Yielding.forceYield(living)) {
			ctx.getSource().sendFailure(Component.literal("It is already yielded or was decided"));
			return 0;
		}

		return done(ctx, entity.getName().getString() + " yields");
	}

	// ---------------------------------------------------------------- dialogue

	private static int open(CommandContext<CommandSourceStack> ctx, @Nullable Entity speaker) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Identifier id = IdentifierArgument.getId(ctx, "id");

		if (!Dialogues.open(player, id, speaker)) {
			ctx.getSource().sendFailure(Component.literal("Could not open dialogue " + id + " (unknown, or the speaker is too far)"));
			return 0;
		}

		return 1;
	}

	private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		DialogueSession session = Dialogues.session(player);

		if (session == null) {
			ctx.getSource().sendFailure(Component.literal("No dialogue open"));
			return 0;
		}

		Dialogues.ChoiceResult result = Dialogues.choose(player, session.id(), IntegerArgumentType.getInteger(ctx, "option") - 1);
		return done(ctx, "Choice: " + result);
	}

	// ---------------------------------------------------------------- helpers

	private static Vec3 ahead(ServerPlayer player, double distance) {
		return player.position().add(Vec3.directionFromRotation(0f, player.getYRot()).scale(distance));
	}

	private static void log(CommandContext<CommandSourceStack> ctx, String what) {
		// API-CHECK: CommandSourceStack#getTextName().
		OldRepublicMod.LOGGER.info("[identity command by {}] {}", ctx.getSource().getTextName(), what);
	}

	private static int done(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private IdentityCommands() {
	}
}

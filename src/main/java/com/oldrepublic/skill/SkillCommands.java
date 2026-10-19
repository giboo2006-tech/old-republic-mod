package com.oldrepublic.skill;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.ability.AbilityRuntime;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /oldrepublic skills ...} operator commands for testing:
 * <ul>
 * <li>{@code list [<player>]}: owned nodes (dormant marked), slots, form, points, respec timer</li>
 * <li>{@code learn <node>}: buy a node with the normal rules (as the Force screen does)</li>
 * <li>{@code grant <node> [<player>]} / {@code revoke <node> [<player>]}: give or take a node, no cost or refund</li>
 * <li>{@code respec [force]}: full respec (force skips the mentor, combat and cooldown checks)</li>
 * <li>{@code slot <1-4> <ability|none>}: assign a slot</li>
 * <li>{@code form <form|none>}: set the active form directly</li>
 * <li>{@code combat}: mark yourself in combat (tests the combat lock)</li>
 * <li>{@code reset [<player>]}: clear every node and slot (no refund) and run the migration again</li>
 * <li>{@code validate}: list problems in the loaded trees</li>
 * </ul>
 */
final class SkillCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("skills")
				.then(literal("list")
						.executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("learn")
						.then(node("node").executes(ctx -> {
							SkillRules.LearnResult result = SkillService.learn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "node"));
							ctx.getSource().sendSuccess(() -> Component.literal("Learn: " + result), false);
							return result == SkillRules.LearnResult.OK ? 1 : 0;
						})))
				.then(literal("grant")
						.then(node("node")
								.executes(ctx -> grant(ctx, ctx.getSource().getPlayerOrException()))
								.then(argument("player", EntityArgument.player())
										.executes(ctx -> grant(ctx, EntityArgument.getPlayer(ctx, "player"))))))
				.then(literal("revoke")
						.then(node("node")
								.executes(ctx -> revoke(ctx, ctx.getSource().getPlayerOrException()))
								.then(argument("player", EntityArgument.player())
										.executes(ctx -> revoke(ctx, EntityArgument.getPlayer(ctx, "player"))))))
				.then(literal("respec")
						.executes(ctx -> SkillService.respec(ctx.getSource().getPlayerOrException(), false) ? 1 : 0)
						.then(literal("force").executes(ctx -> SkillService.respec(ctx.getSource().getPlayerOrException(), true) ? 1 : 0)))
				.then(literal("slot")
						.then(argument("slot", IntegerArgumentType.integer(1, SkillData.SLOT_COUNT))
								.then(argument("ability", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(abilityIds(), builder))
										.executes(SkillCommands::slot))))
				.then(literal("form")
						.then(argument("form", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(formIds(), builder))
								.executes(ctx -> {
									String form = StringArgumentType.getString(ctx, "form");
									boolean changed = SkillService.setActiveForm(ctx.getSource().getPlayerOrException(), "none".equals(form) ? "" : form);
									ctx.getSource().sendSuccess(() -> Component.literal(changed ? "Form set" : "Form unchanged (not owned, or already active)"), false);
									return changed ? 1 : 0;
								})))
				.then(literal("combat").executes(ctx -> {
					CombatTracker.mark(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(literal("reset")
						.executes(ctx -> reset(ctx, ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("validate").executes(SkillCommands::validate))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> node(String name) {
		return argument(name, StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SkillTrees.server().nodes().stream().map(SkillNode::id).toList(), builder));
	}

	private static List<String> abilityIds() {
		List<String> ids = new ArrayList<>();
		ids.add("none");
		SkillTrees.server().nodes().stream().filter(SkillNode::isAbility).forEach(n -> ids.add(n.ability() != null ? n.ability() : n.id()));
		return ids;
	}

	private static List<String> formIds() {
		List<String> ids = new ArrayList<>();
		ids.add("none");
		SkillTrees.server().nodes().stream().filter(n -> n.kind() == NodeKind.FORM).forEach(n -> ids.add(n.id()));
		return ids;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		SkillData data = SkillService.data(player);
		String nodes = data.nodes.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(e -> e.getKey() + (e.getValue() > 1 ? "x" + e.getValue() : "") + (data.isGranted(e.getKey()) ? "(granted)" : "")
						+ (Skills.dormant(player, e.getKey()) ? "(dormant)" : ""))
				.collect(Collectors.joining(", "));
		long wait = Math.max(0, SkillRules.respecReadyAt(data, SkillService.balance()) - player.level().getGameTime());
		ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s: nodes [%s], slots %s (open %d + %d), form '%s', spent %d, respec %s, migrated %s",
				player.getName().getString(), nodes, data.slots, data.openSlots, data.openSlots2, data.activeForm, data.spentTotal(),
				wait > 0 ? "in " + wait + " ticks" : "ready", data.migrated)), false);
		return 1;
	}

	private static int grant(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		String id = StringArgumentType.getString(ctx, "node");

		if (SkillTrees.server().node(id) == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown node " + id));
			return 0;
		}

		SkillService.grant(player, id);
		ctx.getSource().sendSuccess(() -> Component.literal("Granted " + id + " to " + player.getName().getString()), false);
		return 1;
	}

	private static int revoke(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		String id = StringArgumentType.getString(ctx, "node");
		boolean done = SkillService.revoke(player, id);
		ctx.getSource().sendSuccess(() -> Component.literal(done ? "Revoked " + id : player.getName().getString() + " does not own " + id), false);
		return done ? 1 : 0;
	}

	private static int slot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String ability = StringArgumentType.getString(ctx, "ability");
		boolean done = SkillService.assignSlot(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot") - 1,
				"none".equals(ability) ? "" : ability);
		return done ? 1 : 0;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		AbilityRuntime.cancelAll(player, true);
		SkillService.commit(player, new SkillData());
		SkillService.migrate(player);
		SkillService.refresh(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Skills reset for " + player.getName().getString()), false);
		return 1;
	}

	private static int validate(CommandContext<CommandSourceStack> ctx) {
		SkillTreeSet trees = SkillTrees.server();
		List<String> errors = trees.errors();
		ctx.getSource().sendSuccess(() -> Component.literal(trees.trees().size() + " trees, " + trees.nodes().size() + " nodes, "
				+ (errors.isEmpty() ? "no problems" : errors.size() + " problems: " + String.join("; ", errors))), false);
		return errors.isEmpty() ? 1 : 0;
	}

	private SkillCommands() {
	}
}

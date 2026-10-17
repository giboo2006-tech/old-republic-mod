package com.oldrepublic.holocron;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.registry.M3Items;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /oldrepublic holocron ...} operator commands (spec 10.8), each with a single player argument:
 * <ul>
 * <li>{@code grant <player> <id>}: attunes with every first-time reward and the choice OWN, or
 * LISTEN when OWN is not allowed for the player's path</li>
 * <li>{@code reset <player>}: clears the player's holocron data (nodes are not revoked)</li>
 * <li>{@code shards <player> <n>}: gives n echo shards</li>
 * <li>{@code status <player>}: prints the holocron data</li>
 * </ul>
 */
final class HolocronCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("holocron")
				.then(literal("grant")
						.then(argument("player", EntityArgument.player())
								.then(argument("id", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(JournalEntries.HOLOCRON_IDS, builder))
										.executes(HolocronCommands::grant))))
				.then(literal("reset")
						.then(argument("player", EntityArgument.player())
								.executes(HolocronCommands::reset)))
				.then(literal("shards")
						.then(argument("player", EntityArgument.player())
								.then(argument("n", IntegerArgumentType.integer(1, 64))
										.executes(HolocronCommands::shards))))
				.then(literal("status")
						.then(argument("player", EntityArgument.player())
								.executes(HolocronCommands::status)))));
	}

	private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id");
		HolocronCatalog.Entry entry = HolocronCatalog.find(id);

		if (entry == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown holocron " + id));
			return 0;
		}

		if (HolocronService.hasAttuned(player, id)) {
			ctx.getSource().sendFailure(Component.literal(player.getName().getString() + " has already attuned " + id));
			return 0;
		}

		HolocronAttunement.cancel(player, "command");
		HolocronChoice choice = HolocronRules.defaultChoice(Identity.path(player), entry.kind());
		boolean attuned = HolocronRewards.attune(player, id, choice, null);
		ctx.getSource().sendSuccess(() -> Component.literal("Attuned " + id + " (" + choice + ") for " + player.getName().getString()), true);
		return attuned ? 1 : 0;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		HolocronAttunement.cancel(player, "command");
		HolocronService.commit(player, new HolocronData());
		HolocronCarry.refresh(player);
		Progression.refreshModifiers(player);
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared the holocron data of " + player.getName().getString()), true);
		return 1;
	}

	private static int shards(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int n = IntegerArgumentType.getInteger(ctx, "n");
		HolocronRewards.give(player, new ItemStack(M3Items.HOLOCRON_SHARD, n), null);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + n + " holocron shards to " + player.getName().getString()), true);
		return n;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		HolocronData d = HolocronService.data(player);
		String text = String.format("%s: attuned %s, choices %s, returned %s, serials %s, pedestals %d, echoes %d, milestones 0x%X, archive %s%s, restore day %d, carry %d, pending %s",
				player.getName().getString(), d.attuned, d.choices, d.returned, d.serials, d.attunedAt.size(), d.echoes.size(), d.milestones,
				d.archiveChoice(), d.archivePending ? " (pending)" : "", d.restoreDay, HolocronCarry.count(player), HolocronAttunement.pendingId(player));
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private HolocronCommands() {
	}
}

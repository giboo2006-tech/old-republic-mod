package com.oldrepublic.equipment;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /oldrepublic armour give <player> <set> [rank3]} (spec 10.8): gives a whole era armour set.
 * Each piece goes into its armour slot when that slot is empty, else into the inventory, else it is
 * dropped at the player. {@code rank3} swaps the robe for the set's rank 3 robe.
 */
final class EquipmentCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("armour")
				.then(literal("give")
						.then(argument("player", EntityArgument.player())
								.then(argument("set", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ArmourSet.values()).map(ArmourSet::id).toList(), builder))
										.executes(ctx -> give(ctx, false))
										.then(literal("rank3")
												.executes(ctx -> give(ctx, true))))))));
	}

	private static int give(CommandContext<CommandSourceStack> ctx, boolean rank3) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "set");
		ArmourSet set = ArmourSet.byId(id);

		if (set == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.armour.unknown_set", id));
			return 0;
		}

		if (rank3 && ArmourSets.rankRobe(set).isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.armour.no_rank3", id));
			return 0;
		}

		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			ItemStack piece = rank3 && slot == EquipmentSlot.CHEST ? ArmourSets.rankRobe(set) : ArmourSets.piece(set, slot);
			give(player, slot, piece);
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.armour.given",
				Component.translatable("equipment.oldrepublic.set." + set.id()), player.getDisplayName()), true);
		return 1;
	}

	/** Equips {@code piece} into an empty {@code slot}, else adds it to the inventory, else drops it. */
	private static void give(ServerPlayer player, EquipmentSlot slot, ItemStack piece) {
		if (player.getItemBySlot(slot).isEmpty()) {
			player.setItemSlot(slot, piece);
		} else if (!player.getInventory().add(piece)) {
			player.drop(piece, false);
		}
	}

	private EquipmentCommands() {
	}
}

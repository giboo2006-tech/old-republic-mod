package com.oldrepublic.equipment;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.equipment.blaster.BlasterHeat;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.mod.FittedMods;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The equipment commands (spec 10.8 and MILESTONE_4.md 11 for testing), each taking one player:
 * <ul>
 * <li>{@code /oldrepublic armour give <player> <set> [rank3]}: a whole era armour set, M3 or M4.
 * Each piece goes into its armour slot when that slot is empty, else into the inventory, else it is
 * dropped at the player. {@code rank3} swaps the robe for the set's rank 3 robe;</li>
 * <li>{@code /oldrepublic gear fit <player> <slot> <mod> [quality]}: fits a new mod of that quality
 * into the gear in {@code mainhand}, {@code head}, {@code chest}, {@code legs} or {@code feet},
 * returning the mod it replaces;</li>
 * <li>{@code /oldrepublic gear quality <player> <quality>}: remakes the held weapon or armour piece
 * at that quality (as a craft would), keeping its fitted mods;</li>
 * <li>{@code /oldrepublic gear heat <player> <heat>}: sets the held blaster's heat (0 to 100).</li>
 * </ul>
 */
final class EquipmentCommands {
	private static final String[] GEAR_SLOTS = {"mainhand", "head", "chest", "legs", "feet"};

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("armour")
				.then(literal("give")
						.then(argument("player", EntityArgument.player())
								.then(argument("set", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ArmourSet.values()).map(ArmourSet::id).toList(), builder))
										.executes(ctx -> give(ctx, false))
										.then(literal("rank3")
												.executes(ctx -> give(ctx, true))))))));
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("gear")
				.then(literal("fit")
						.then(argument("player", EntityArgument.player())
								.then(argument("slot", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(GEAR_SLOTS, builder))
										.then(argument("mod", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														M4Items.MODS.stream().map(item -> BuiltInRegistries.ITEM.getKey(item).getPath()).toList(), builder))
												.executes(ctx -> fit(ctx, Quality.STANDARD))
												.then(quality().executes(ctx -> fit(ctx, quality(ctx))))))))
				.then(literal("quality")
						.then(argument("player", EntityArgument.player())
								.then(quality().executes(EquipmentCommands::remake))))
				.then(literal("heat")
						.then(argument("player", EntityArgument.player())
								.then(argument("heat", FloatArgumentType.floatArg(0f, 100f))
										.executes(EquipmentCommands::heat))))));
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
		} else {
			hand(player, piece);
		}
	}

	/** Adds {@code stack} to the inventory, or drops it at the player. */
	private static void hand(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static int fit(CommandContext<CommandSourceStack> ctx, Quality quality) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		EquipmentSlot slot = slot(StringArgumentType.getString(ctx, "slot"));
		String modId = StringArgumentType.getString(ctx, "mod");
		Item modItem = BuiltInRegistries.ITEM.getOptional(OldRepublicMod.id(modId)).orElse(null);
		ModSlot modSlot = modItem == null ? null : Mods.slotOf(modItem);
		ItemStack gear = slot == null ? ItemStack.EMPTY : player.getItemBySlot(slot);

		if (modItem == null || modSlot == null || !Mods.slotsOf(gear).contains(modSlot)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.gear.no_fit", modId));
			return 0;
		}

		// The stack changes in place; the next equipment check sees it against last tick's copy.
		hand(player, Mods.remove(gear, modSlot));
		ItemStack mod = Crafting.withQuality(new ItemStack(modItem), quality);
		Component name = mod.getHoverName();
		Mods.fit(gear, modSlot, mod);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.gear.fitted", name, player.getDisplayName()), true);
		return 1;
	}

	private static int remake(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		Quality quality = quality(ctx);
		ItemStack held = player.getMainHandItem();

		if (held.isEmpty() || Mods.slotsOf(held).isEmpty() || held.has(M4Components.NAMED)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.gear.no_quality"));
			return 0;
		}

		ItemStack remade = Crafting.applyQuality(new ItemStack(held.getItem()), quality);
		FittedMods mods = Mods.fitted(held);

		if (!mods.mods().isEmpty()) {
			remade.set(M4Components.MODS, mods);
		}

		player.setItemSlot(EquipmentSlot.MAINHAND, remade);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.gear.quality",
				remade.getHoverName(), Component.translatable("quality.oldrepublic." + quality.name().toLowerCase(Locale.ROOT))), true);
		return 1;
	}

	private static int heat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		ItemStack held = player.getMainHandItem();

		if (!(held.getItem() instanceof BlasterItem)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.gear.no_blaster"));
			return 0;
		}

		float heat = FloatArgumentType.getFloat(ctx, "heat");
		held.set(M4Components.BLASTER_HEAT, new BlasterHeat(heat, player.level().getGameTime(), 0L, 0));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.gear.heat", ArmourItem.number(heat)), true);
		return 1;
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> quality() {
		return argument("quality", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
						Arrays.stream(Quality.values()).map(q -> q.name().toLowerCase(Locale.ROOT)).toList(), builder));
	}

	private static Quality quality(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "quality");

		for (Quality q : Quality.values()) {
			if (q.name().equalsIgnoreCase(id)) {
				return q;
			}
		}

		return Quality.STANDARD;
	}

	private static @Nullable EquipmentSlot slot(String id) {
		return switch (id) {
			case "mainhand" -> EquipmentSlot.MAINHAND;
			case "head" -> EquipmentSlot.HEAD;
			case "chest" -> EquipmentSlot.CHEST;
			case "legs" -> EquipmentSlot.LEGS;
			case "feet" -> EquipmentSlot.FEET;
			default -> null;
		};
	}

	private EquipmentCommands() {
	}
}

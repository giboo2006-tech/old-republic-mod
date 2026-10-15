package com.oldrepublic.saber;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberGuard.GuardSnapshot;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /oldrepublic saber ...} test commands:
 * <ul>
 * <li>{@code give [<color> <emitter> <grip> <pommel>]}: a saber attuned to you</li>
 * <li>{@code crystal [<color>]}: a kyber crystal attuned to you</li>
 * <li>{@code parts}: one of every hilt part</li>
 * <li>{@code stamina [refill]}: shows (or refills) your guard stamina</li>
 * </ul>
 */
final class SaberCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("saber")
				.then(literal("give")
						.executes(ctx -> give(ctx, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED))
						.then(word("color", BladeColor.values())
								.then(word("emitter", Emitter.values())
										.then(word("grip", Grip.values())
												.then(word("pommel", Pommel.values())
														.executes(SaberCommands::giveConfigured))))))
				.then(literal("crystal")
						.executes(ctx -> crystal(ctx, BladeColor.CYAN))
						.then(word("color", BladeColor.values())
								.executes(ctx -> crystal(ctx, parse(ctx, "color", BladeColor.values())))))
				.then(literal("parts").executes(SaberCommands::parts))
				.then(literal("stamina")
						.executes(ctx -> stamina(ctx, false))
						.then(literal("refill").executes(ctx -> stamina(ctx, true))))));
	}

	private static <E extends Enum<E>> RequiredArgumentBuilder<CommandSourceStack, String> word(String name, E[] values) {
		List<String> ids = Arrays.stream(values).map(SaberCommands::id).toList();
		return argument(name, StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids, builder));
	}

	private static String id(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> E parse(CommandContext<CommandSourceStack> ctx, String name, E[] values) throws CommandSyntaxException {
		String raw = StringArgumentType.getString(ctx, name);

		for (E value : values) {
			if (id(value).equals(raw)) {
				return value;
			}
		}

		throw new SimpleCommandExceptionType(
				Component.translatable("commands.oldrepublic.saber.unknown", name, raw)).create();
	}

	private static int giveConfigured(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return give(ctx, parse(ctx, "color", BladeColor.values()), parse(ctx, "emitter", Emitter.values()),
				parse(ctx, "grip", Grip.values()), parse(ctx, "pommel", Pommel.values()));
	}

	private static int give(CommandContext<CommandSourceStack> ctx, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack saber = SaberItems.buildSaber(attunement(player, color), color, emitter, grip, pommel, SaberToggle.balance());
		player.getInventory().placeItemBackInInventory(saber);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.give", color.displayName()), false);
		return 1;
	}

	private static int crystal(CommandContext<CommandSourceStack> ctx, BladeColor color) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		player.getInventory().placeItemBackInInventory(SaberAssembly.crystalStack(attunement(player, color)));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.crystal", color.displayName()), false);
		return 1;
	}

	private static int parts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();

		for (HiltParts.Part[] group : new HiltParts.Part[][] {Emitter.values(), Grip.values(), Pommel.values()}) {
			for (HiltParts.Part part : group) {
				player.getInventory().placeItemBackInInventory(new ItemStack(SaberItems.partItem(part)));
			}
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.parts"), false);
		return 1;
	}

	private static int stamina(CommandContext<CommandSourceStack> ctx, boolean refill) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();

		if (refill) {
			SaberGuard.refill(player);
		}

		GuardSnapshot snapshot = SaberGuard.snapshot(player);

		if (snapshot == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.stamina.none"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.stamina",
				String.format("%.1f", snapshot.stamina()), String.format("%.0f", snapshot.max()), String.valueOf(snapshot.broken())), false);
		return 1;
	}

	private static CrystalAttunement attunement(ServerPlayer player, BladeColor color) {
		return new CrystalAttunement(player.getUUID(), player.getName().getString(), color.rgb());
	}

	private SaberCommands() {
	}
}

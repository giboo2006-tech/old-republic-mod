package com.oldrepublic.enemy.construct;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /oldrepublic sentinel spawn <watcher|tomb> <pos> [bound]} (spec 10.8): a dormant Ruin
 * Sentinel at {@code pos}. With {@code bound} it is re-bound (always hostile) and owned by the player
 * who ran the command.
 */
final class SentinelCommands {
	private static final List<String> VARIANTS = Arrays.stream(SentinelVariant.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).toList();

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("sentinel")
				.then(literal("spawn")
						.then(argument("variant", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VARIANTS, builder))
								// API-CHECK: BlockPosArgument.blockPos() / getLoadedBlockPos(ctx, name) (vanilla, not in the dumps)
								.then(argument("pos", BlockPosArgument.blockPos())
										.executes(ctx -> spawn(ctx, false))
										.then(literal("bound")
												.executes(ctx -> spawn(ctx, true))))))));
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, boolean bound) throws CommandSyntaxException {
		String raw = StringArgumentType.getString(ctx, "variant");
		SentinelVariant variant = null;

		for (SentinelVariant v : SentinelVariant.values()) {
			if (v.name().equalsIgnoreCase(raw)) {
				variant = v;
			}
		}

		if (variant == null) {
			ctx.getSource().sendFailure(Component.translatable("boss.oldrepublic.sentinel.command.unknown_variant", raw));
			return 0;
		}

		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		ServerPlayer player = ctx.getSource().getPlayer();
		UUID owner = bound && player != null ? player.getUUID() : null;
		LivingEntity sentinel = Sentinels.spawn(level, pos, variant, bound, owner);
		String key = bound ? "boss.oldrepublic.sentinel.command.spawned_bound" : "boss.oldrepublic.sentinel.command.spawned";
		ctx.getSource().sendSuccess(() -> Component.translatable(key, sentinel.getDisplayName(), pos.getX(), pos.getY(), pos.getZ()), true);
		return 1;
	}

	private SentinelCommands() {
	}
}

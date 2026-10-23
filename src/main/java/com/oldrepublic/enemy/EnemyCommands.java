package com.oldrepublic.enemy;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic enemy spawn <acolyte|captain|poacher> [hostile|rival] [acolyte|brenn|deserter]}:
 * spawns an enemy three blocks in front of you. A rival acolyte is owned by you (it fights only in
 * a spar, and yields to you); a captain spawned here yields to you.
 */
final class EnemyCommands {
	private static final List<String> KINDS = List.of("acolyte", "captain", "poacher");

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("enemy")
				.then(literal("spawn")
						.then(argument("kind", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(KINDS, builder))
								.executes(ctx -> spawn(ctx, SithAcolyte.Mode.HOSTILE, SithAcolyte.Skin.ACOLYTE))
								.then(argument("mode", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids(SithAcolyte.Mode.values()), builder))
										.executes(ctx -> spawn(ctx, mode(ctx), SithAcolyte.Skin.ACOLYTE))
										.then(argument("skin", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids(SithAcolyte.Skin.values()), builder))
												.executes(ctx -> spawn(ctx, mode(ctx), skin(ctx)))))))));
	}

	private static List<String> ids(Enum<?>[] values) {
		return Arrays.stream(values).map(v -> v.name().toLowerCase(Locale.ROOT)).toList();
	}

	private static SithAcolyte.Mode mode(CommandContext<CommandSourceStack> ctx) {
		return "rival".equals(StringArgumentType.getString(ctx, "mode")) ? SithAcolyte.Mode.RIVAL : SithAcolyte.Mode.HOSTILE;
	}

	private static SithAcolyte.Skin skin(CommandContext<CommandSourceStack> ctx) {
		String raw = StringArgumentType.getString(ctx, "skin");

		for (SithAcolyte.Skin skin : SithAcolyte.Skin.values()) {
			if (skin.id().equals(raw)) {
				return skin;
			}
		}

		return SithAcolyte.Skin.ACOLYTE;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, SithAcolyte.Mode mode, SithAcolyte.Skin skin) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 at = player.position().add(look.lengthSqr() < 1.0e-4 ? Vec3.ZERO : look.normalize().scale(3));
		BlockPos pos = BlockPos.containing(at);
		String kind = StringArgumentType.getString(ctx, "kind");
		Entity spawned = switch (kind) {
			case "acolyte" -> {
				SithAcolyte acolyte = EnemyEntities.SITH_ACOLYTE.spawn(level, pos, EntitySpawnReason.COMMAND);

				if (acolyte != null) {
					acolyte.setSkin(skin);
					acolyte.setMode(mode);

					if (mode == SithAcolyte.Mode.RIVAL) {
						acolyte.setOwner(player.getUUID());
					}
				}

				yield acolyte;
			}
			case "captain" -> {
				PoacherCaptain captain = EnemyEntities.POACHER_CAPTAIN.spawn(level, pos, EntitySpawnReason.COMMAND);

				if (captain != null) {
					captain.setOwner(player.getUUID());
				}

				yield captain;
			}
			case "poacher" -> EnemyEntities.CRYSTAL_POACHER.spawn(level, pos, EntitySpawnReason.COMMAND);
			default -> null;
		};

		if (spawned == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.enemy.spawn.failed", kind));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.enemy.spawn", spawned.getDisplayName()), true);
		return 1;
	}

	private EnemyCommands() {
	}
}

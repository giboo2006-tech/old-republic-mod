package com.oldrepublic.boss;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.world.site.RuntimeSites;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic boss ...} (spec 10.8), each with a single player argument ({@code @p}):
 *
 * <ul>
 * <li>{@code place <player>}: the player's Reliquary through {@code ReliquarySites.place}; if no site
 * passes, forced 24 blocks ahead with its entrance toward the player ({@code placeAt}).</li>
 * <li>{@code start <player>}: starts the owner's fight now (a resolved heart calls the echo).</li>
 * <li>{@code phase <player> <n>}: jumps the fight to phase 1-3 through its transition, or 4 for the yield.</li>
 * <li>{@code reset <player>}: resets the owner's running fight (8.6).</li>
 * </ul>
 */
final class BossCommands {
	/** How far ahead the forced placement stands. */
	private static final int FORCED_DISTANCE = 24;

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("boss")
				.then(literal("place")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> place(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("start")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> start(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("phase")
						.then(argument("player", EntityArgument.player())
								.then(argument("n", IntegerArgumentType.integer(1, 4))
										.executes(ctx -> phase(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(literal("reset")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))));
	}

	private static int place(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		BlockPos heart = ReliquarySites.place(player, player.position(), player.getLookAngle());

		if (heart != null) {
			ctx.getSource().sendSuccess(() -> Component.translatable("boss.oldrepublic.command.placed", player.getDisplayName(),
					heart.getX(), heart.getY(), heart.getZ()), true);
			return 1;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle().multiply(1, 0, 1);
		Vec3 ahead = look.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : look.normalize();
		int x = Mth.floor(player.getX() + ahead.x * FORCED_DISTANCE);
		int z = Mth.floor(player.getZ() + ahead.z * FORCED_DISTANCE);
		BlockPos stand = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
		Direction toPlayer = horizontal(player.getX() - (x + 0.5), player.getZ() - (z + 0.5));

		try {
			BlockPos forced = ReliquarySites.placeAt(player, stand, RuntimeSites.facing(toPlayer));
			ctx.getSource().sendSuccess(() -> Component.translatable("boss.oldrepublic.command.placed_forced", player.getDisplayName(),
					forced.getX(), forced.getY(), forced.getZ()), true);
			return 1;
		} catch (IllegalStateException e) {
			ctx.getSource().sendFailure(Component.translatable("boss.oldrepublic.command.place_failed", e.getMessage()));
			return 0;
		}
	}

	private static int start(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		if (BossArenas.isResolved(player)) {
			return BossArenas.callEcho(player, false) ? ok(ctx, "boss.oldrepublic.command.echo", player) : 0;
		}

		ArenaSession session = session(ctx, player);

		if (session == null) {
			return 0;
		}

		if (session.stage() != ArenaSession.Stage.WAITING && session.stage() != ArenaSession.Stage.MEETING) {
			ctx.getSource().sendFailure(Component.translatable("boss.oldrepublic.command.already", player.getDisplayName()));
			return 0;
		}

		session.startFight(null);
		return ok(ctx, "boss.oldrepublic.command.started", player);
	}

	private static int phase(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int n) {
		ArenaSession session = session(ctx, player);

		if (session == null) {
			return 0;
		}

		if (!session.forcePhase(n)) {
			ctx.getSource().sendFailure(Component.translatable("boss.oldrepublic.command.phase_refused", player.getDisplayName(), n));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("boss.oldrepublic.command.phase", player.getDisplayName(), n), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		ArenaSession session = BossArenas.sessionOwnedBy(player);

		if (session == null || !(session.isFighting() || session.stage() == ArenaSession.Stage.MEETING || session.stage() == ArenaSession.Stage.FORMING)) {
			ctx.getSource().sendFailure(Component.translatable("boss.oldrepublic.command.no_fight", player.getDisplayName()));
			return 0;
		}

		session.reset();
		return ok(ctx, "boss.oldrepublic.command.reset", player);
	}

	/** The owner's session, created at their loaded heart when needed; a failure message when there is none. */
	private static @Nullable ArenaSession session(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		ArenaSession session = BossArenas.ensureSession(player);

		if (session == null) {
			ctx.getSource().sendFailure(Component.translatable("boss.oldrepublic.command.no_heart", player.getDisplayName()));
		}

		return session;
	}

	private static int ok(CommandContext<CommandSourceStack> ctx, String key, ServerPlayer player) {
		ctx.getSource().sendSuccess(() -> Component.translatable(key, player.getDisplayName()), true);
		return 1;
	}

	/** The cardinal direction of (dx, dz): the larger axis wins. */
	private static Direction horizontal(double dx, double dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}

		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private BossCommands() {
	}
}

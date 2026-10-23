package com.oldrepublic.combat;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.oldrepublic.command.OldRepublicCommands;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server wiring for the combat contracts (spec 15.1): the held attachment and its release paths and
 * ability lock ({@link Holds}), the player-damage hook that takes the {@link Attitudes} snapshot, and
 * the operator command {@code /oldrepublic hold <player> <ticks>}. Called from
 * {@code OldRepublicMod.onInitialize} after {@code Progression.init()}.
 */
public final class CombatContracts {
	/** Longest hold the command accepts (5 minutes). */
	private static final int MAX_COMMAND_TICKS = 6000;
	/** How far the command lifts the player, like the Archive Grip. */
	private static final double COMMAND_LIFT = 1.5;

	/** Registers everything above. Call once during mod init. */
	public static void init() {
		Holds.init();

		// The snapshot is taken when a player's damage lands: AFTER_DAMAGE for hits the victim survives
		// (Fabric skips it for the killing blow) and ALLOW_DEATH, which runs after the killing damage
		// has been applied and before AFTER_DEATH pays kill XP.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (damageTaken > 0f) {
				snapshot(entity, source);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			snapshot(entity, source);
			return true;
		});

		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("hold")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(argument("player", EntityArgument.player())
						.then(argument("ticks", IntegerArgumentType.integer(0, MAX_COMMAND_TICKS))
								.executes(ctx -> hold(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
										IntegerArgumentType.getInteger(ctx, "ticks")))))));
	}

	private static void snapshot(LivingEntity victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
			Attitudes.snapshotOnDamage(attacker, victim);
		}
	}

	/** Holds the player 1.5 blocks up for {@code ticks} with Push allowed; 0 releases them. */
	private static int hold(CommandSourceStack source, ServerPlayer player, int ticks) {
		if (ticks == 0) {
			Holds.release(player, "command");
			source.sendSuccess(() -> Component.translatable("commands.oldrepublic.hold.released", player.getDisplayName()), true);
			return 1;
		}

		Holds.hold(player, ticks, "command", player.position().add(0, COMMAND_LIFT, 0), true);
		source.sendSuccess(() -> Component.translatable("commands.oldrepublic.hold.started", player.getDisplayName(), ticks), true);
		return 1;
	}

	private CombatContracts() {
	}
}

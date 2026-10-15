package com.oldrepublic.force;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.force.network.ChooseBranchPayload;
import com.oldrepublic.force.network.PushInfoPayload;
import com.oldrepublic.force.network.PushInputPayload;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server and common initialisation for the force feature. Called from OldRepublicMod. */
public final class ForceFeature {
	public static void init() {
		ForceParticles.init();

		PayloadTypeRegistry.serverboundPlay().register(PushInputPayload.TYPE, PushInputPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChooseBranchPayload.TYPE, ChooseBranchPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PushInfoPayload.TYPE, PushInfoPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(PushInputPayload.TYPE, (payload, context) -> ForcePush.onInput(context.player(), payload.action()));
		ServerPlayNetworking.registerGlobalReceiver(ChooseBranchPayload.TYPE, (payload, context) -> BranchChoice.apply(context.player(), payload.branchId()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			PushCharges.tick(server);
			PushEffects.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PushCharges.clear();
			PushEffects.clear();
		});
		ServerPlayerEvents.JOIN.register(player -> ServerPlayNetworking.send(player, PushInfoPayload.from(ForcePush.balance())));
		ServerPlayerEvents.LEAVE.register(player -> ForcePush.cancel(player, false));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				ForcePush.cancel(player, true);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> ForcePush.cancel(player, true));

		OldRepublicCommands.addSubcommand((context, root) -> root.then(pushCommand()));
	}

	/** {@code /oldrepublic push grant|branch <id>|cooldown|charge} for testing. */
	private static LiteralArgumentBuilder<CommandSourceStack> pushCommand() {
		LiteralArgumentBuilder<CommandSourceStack> branch = literal("branch");

		for (PushBranch value : PushBranch.values()) {
			String name = value == PushBranch.NONE ? "none" : value.id();
			branch.then(literal(name).executes(ctx -> setBranch(ctx.getSource().getPlayerOrException(), value)));
		}

		return literal("push")
				.then(literal("grant")
						.executes(ctx -> grant(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(branch)
				.then(literal("cooldown").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Progression.setCooldown(player, ForcePush.ABILITY_ID, 0);
					return 1;
				}))
				.then(literal("fire").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					return ForcePush.cast(player, 1f) ? 1 : 0;
				}));
	}

	private static int grant(CommandSourceStack source, ServerPlayer player) {
		boolean learned = ForcePush.grant(player);
		source.sendSuccess(() -> Component.literal(learned ? "Taught Force Push to " + player.getName().getString() : "Already knows Force Push"), false);
		return 1;
	}

	private static int setBranch(ServerPlayer player, PushBranch branch) {
		ForceData data = Progression.get(player);
		data.pushBranch = branch.id();
		Progression.commit(player, data);
		player.sendSystemMessage(Component.literal("Push branch set to '" + branch.id() + "'"));
		return 1;
	}

	private ForceFeature() {
	}
}

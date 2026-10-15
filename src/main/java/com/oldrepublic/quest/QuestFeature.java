package com.oldrepublic.quest;

import static net.minecraft.commands.Commands.literal;

import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.config.BalanceConfig;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Server and common initialisation for the quest feature. Called from OldRepublicMod. */
public final class QuestFeature {
	public static QuestBalance balance() {
		return BalanceConfig.section("quest", QuestBalance.class, QuestBalance::new);
	}

	public static void init() {
		QuestItems.init();
		QuestParticles.init();

		ServerTickEvents.END_SERVER_TICK.register(QuestFeature::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Awakening.clear();
			Meditation.clear();
		});

		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Awakening.onStartSleeping(player);
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Awakening.onStopSleeping(player);
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0) {
				Awakening.onDamaged(player);
				Meditation.interrupt(player);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				Meditation.interrupt(serverPlayer);
			}

			return InteractionResult.PASS;
		});

		ServerPlayerEvents.LEAVE.register(player -> {
			Awakening.forget(player);
			Meditation.forget(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Awakening.forget(oldPlayer);
			Meditation.forget(oldPlayer);
		});

		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("journal").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			boolean given = Journal.giveIfMissing(player);
			ctx.getSource().sendSuccess(() -> Component.translatable(given ? "command.oldrepublic.journal.given" : "command.oldrepublic.journal.owned"), false);
			return given ? 1 : 0;
		})));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Awakening.tick(player);
			Meditation.tick(player);
			Calling.tick(player);
		}
	}

	private QuestFeature() {
	}
}

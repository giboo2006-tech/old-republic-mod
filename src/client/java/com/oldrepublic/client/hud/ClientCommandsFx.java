package com.oldrepublic.client.hud;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.Locale;
import java.util.function.Consumer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.oldrepublic.client.config.ClientConfig;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * {@code /oldrepublicfx}: the player's visual and audio comfort settings, saved to
 * {@code config/oldrepublic/client.json}.
 *
 * <pre>
 * /oldrepublicfx                                   show the current settings
 * /oldrepublicfx preset reduced|standard|high|cinematic
 * /oldrepublicfx shake &lt;0-100&gt;
 * /oldrepublicfx flashing on|off
 * /oldrepublicfx hud on|off
 * /oldrepublicfx volume &lt;0-100&gt;
 * /oldrepublicfx alignment_toasts on|off           minor alignment shift lines
 * /oldrepublicfx boss_music on|off
 * /oldrepublicfx robe_models on|off
 * /oldrepublicfx tracker on|off                    the mission tracker
 * /oldrepublicfx barks on|off                      trooper bark lines
 * </pre>
 */
final class ClientCommandsFx {
	static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> presets = literal("preset");

			for (ClientConfig.Preset preset : ClientConfig.Preset.values()) {
				presets.then(literal(preset.name().toLowerCase(Locale.ROOT)).executes(ctx -> apply(ctx, c -> c.preset = preset)));
			}

			dispatcher.register(literal("oldrepublicfx")
					.executes(ClientCommandsFx::show)
					.then(presets)
					.then(literal("shake").then(argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> apply(ctx, c -> c.screenShake = IntegerArgumentType.getInteger(ctx, "percent") / 100f))))
					.then(literal("flashing")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.flashingEffects = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.flashingEffects = false))))
					.then(literal("hud")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.showHud = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.showHud = false))))
					.then(literal("volume").then(argument("percent", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> apply(ctx, c -> c.modSoundVolume = IntegerArgumentType.getInteger(ctx, "percent") / 100f))))
					.then(literal("alignment_toasts")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.showMinorAlignmentToasts = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.showMinorAlignmentToasts = false))))
					.then(literal("boss_music")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.bossMusic = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.bossMusic = false))))
					.then(literal("robe_models")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.robeModels = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.robeModels = false))))
					.then(literal("tracker")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.showMissionTracker = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.showMissionTracker = false))))
					.then(literal("barks")
							.then(literal("on").executes(ctx -> apply(ctx, c -> c.showBarks = true)))
							.then(literal("off").executes(ctx -> apply(ctx, c -> c.showBarks = false)))));
		});
	}

	private static int apply(CommandContext<FabricClientCommandSource> ctx, Consumer<ClientConfig> change) {
		change.accept(ClientConfig.get());
		ClientConfig.save();
		ctx.getSource().sendFeedback(Component.translatable("command.oldrepublicfx.saved"));
		return show(ctx);
	}

	private static int show(CommandContext<FabricClientCommandSource> ctx) {
		ClientConfig c = ClientConfig.get();
		ctx.getSource().sendFeedback(Component.translatable("command.oldrepublicfx.status",
				Component.translatable("command.oldrepublicfx.preset." + c.preset.name().toLowerCase(Locale.ROOT)),
				Math.round(c.screenShake * 100),
				Component.translatable(c.flashingEffects ? "options.on" : "options.off"),
				Component.translatable(c.showHud ? "options.on" : "options.off"),
				Math.round(c.modSoundVolume * 100)));
		ctx.getSource().sendFeedback(Component.translatable("command.oldrepublicfx.status.alignment_toasts",
				Component.translatable(c.showMinorAlignmentToasts ? "options.on" : "options.off")));
		ctx.getSource().sendFeedback(Component.translatable("command.oldrepublicfx.status.exploration", onOff(c.bossMusic), onOff(c.robeModels),
				onOff(c.showMissionTracker), onOff(c.showBarks)));
		return 1;
	}

	private static Component onOff(boolean value) {
		return Component.translatable(value ? "options.on" : "options.off");
	}

	private ClientCommandsFx() {
	}
}

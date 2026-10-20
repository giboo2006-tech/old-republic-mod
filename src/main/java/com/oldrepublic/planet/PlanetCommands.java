package com.oldrepublic.planet;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.world.site.SitePois;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Planet commands under {@code /oldrepublic planet} (operators, level 2; for tests and servers):
 * <ul>
 * <li>{@code hub <planet>}: places the planet's hub now if it is not placed ({@link PlanetHubs#ensure})
 * and says where it is once placed; a planet whose hub template is missing is refused;</li>
 * <li>{@code site <player> <site>}: {@link PlanetSites#ensure} for the player on the world they stand
 * on (or says where its ring point is when it cannot be placed yet);</li>
 * <li>{@code cleanse <player>}: cleanses the nearest corrupted waystone within 16 blocks for the
 * player, as their Force Sense would;</li>
 * <li>{@code sandstorm start [ticks]} and {@code sandstorm stop}: raises a sandstorm on Ghessar now
 * (for {@code ticks}, or the longest {@code planets.sandstormTicks}), or ends the one blowing
 * ({@link PlanetWeather#force}).</li>
 * </ul>
 */
final class PlanetCommands {
	private static final int CLEANSE_RANGE = 16;
	/** The longest storm the command raises: a day. */
	private static final int MAX_STORM_TICKS = PlanetWeather.DAY_TICKS;

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root
				.then(literal("planet")
						.then(literal("hub")
								.then(argument("planet", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Planets.planets().stream().map(Planets.Planet::id), builder))
										.executes(PlanetCommands::hub)))
						.then(literal("site")
								.then(argument("player", EntityArgument.player())
										.then(argument("site", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														Arrays.stream(PlanetSites.Site.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), builder))
												.executes(PlanetCommands::site))))
						.then(literal("cleanse")
								.then(argument("player", EntityArgument.player())
										.executes(PlanetCommands::cleanse)))
						.then(literal("sandstorm")
								.then(literal("start")
										.executes(ctx -> sandstorm(ctx, longestStorm()))
										.then(argument("ticks", IntegerArgumentType.integer(1, MAX_STORM_TICKS))
												.executes(ctx -> sandstorm(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))
								.then(literal("stop")
										.executes(ctx -> sandstorm(ctx, 0))))));
	}

	private static int hub(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "planet");
		Planets.Planet planet = Planets.Planet.byId(id);
		ServerLevel level = planet == null || planet == Planets.Planet.HOME ? null : Planets.level(source.getServer(), planet);

		// A planet whose hub template is missing has no hub (MILESTONE_5.md 3.1): refused like an unknown one.
		if (level == null || !PlanetHubs.templateReady(level, planet)) {
			source.sendFailure(Component.translatable("commands.oldrepublic.planet.unknown", id));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("commands.oldrepublic.planet.hub.placing", Component.translatable("dimension.oldrepublic." + planet.id())), true);
		PlanetHubs.ensure(source.getServer(), planet).thenAccept(pos -> source.sendSuccess(() -> Component.translatable("commands.oldrepublic.planet.hub.at",
				Component.translatable("dimension.oldrepublic." + planet.id()), pos.getX(), pos.getY(), pos.getZ()), true));
		return 1;
	}

	private static int site(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "site");
		PlanetSites.Site site = null;

		for (PlanetSites.Site candidate : PlanetSites.Site.values()) {
			if (candidate.name().toLowerCase(Locale.ROOT).equals(id)) {
				site = candidate;
			}
		}

		Planets.Planet planet = Planets.Planet.of(player.level().dimension());

		if (site == null || planet == null) {
			source.sendFailure(Component.translatable("commands.oldrepublic.planet.unknown", id));
			return 0;
		}

		PlanetSites.forget(player.getUUID());
		GlobalPos at = PlanetSites.ensure(player, site, planet);
		Component name = Component.translatable("site.oldrepublic.planet." + id);

		if (at != null) {
			BlockPos pos = at.pos();
			source.sendSuccess(() -> Component.translatable("commands.oldrepublic.planet.site.at", name, pos.getX(), pos.getY(), pos.getZ()), true);
			return 1;
		}

		GlobalPos ring = PlanetSites.ringPoint(source.getServer(), player.getUUID(), site, planet);

		if (ring != null) {
			source.sendFailure(Component.translatable("commands.oldrepublic.planet.site.ring", name, ring.pos().getX(), ring.pos().getZ()));
		} else {
			source.sendFailure(Component.translatable("commands.oldrepublic.planet.site.not_yet", name));
		}

		return 0;
	}

	private static int cleanse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		for (BlockPos stone : SitePois.find(player.level(), PlanetBlocks.WAYSTONE_POI, player.blockPosition(), CLEANSE_RANGE)) {
			if (Waystones.cleanse(player, stone)) {
				source.sendSuccess(() -> Component.translatable("commands.oldrepublic.planet.cleansed", stone.getX(), stone.getY(), stone.getZ()), true);
				return 1;
			}
		}

		source.sendFailure(Component.translatable("commands.oldrepublic.planet.no_waystone", CLEANSE_RANGE));
		return 0;
	}

	private static int sandstorm(CommandContext<CommandSourceStack> ctx, int ticks) {
		CommandSourceStack source = ctx.getSource();

		if (!PlanetWeather.force(source.getServer(), ticks)) {
			source.sendFailure(Component.translatable("commands.oldrepublic.planet.unknown", Planets.Planet.GHESSAR.id()));
			return 0;
		}

		source.sendSuccess(() -> ticks > 0 ? Component.translatable("commands.oldrepublic.planet.sandstorm.started", ticks)
				: Component.translatable("commands.oldrepublic.planet.sandstorm.stopped"), true);
		return 1;
	}

	/** The longest storm {@code planets.sandstormTicks} allows. */
	private static int longestStorm() {
		int[] range = PlanetBalance.get().sandstormTicks;
		return range.length == 0 ? 3000 : Math.max(1, range[range.length - 1]);
	}

	private PlanetCommands() {
	}
}

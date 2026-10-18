package com.oldrepublic.world.site;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.world.EchoShrines;
import com.oldrepublic.world.outpost.OutpostPlacement;
import com.oldrepublic.world.outpost.Outposts;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Site commands (spec 10.8, plan 4.15), all under {@code /oldrepublic} (permission level 2):
 * <ul>
 * <li>{@code site place <site> <pos>}: one of the five worldgen structures by short name (on the
 * code path of vanilla {@code place structure}, then registered like worldgen; {@link #placeStructure}),
 * a W1 site template by short name ({@code echo_shrine_jedi},
 * {@code echo_shrine_sith}, {@code echo_shrine_wayfarer}, {@code republic_post},
 * {@code imperial_post}) or any site template by id (for W2's and W5's templates), placed and
 * recorded through {@link RuntimeSites#placeAt}.</li>
 * <li>{@code site locate <player> <kind>}: the nearest site of a kind through the locator queue;
 * the reply comes when the search has run.</li>
 * <li>{@code outpost ensure <player>}: {@link Outposts#ensureNear} with each army's decision.</li>
 * </ul>
 */
final class SiteCommands {
	/** The worldgen structures, by structure id path (Milestone 4 adds the planets' waystone and greater tomb). */
	private static final List<String> STRUCTURES = List.of("jedi_ruin", "sith_tomb", "wreck_field", "republic_outpost", "imperial_outpost",
			"waystone", "greater_tomb");
	/** Short names of W1's runtime site templates. */
	private static final Map<String, Identifier> SHORT_TEMPLATES = shortTemplates();
	/** Search radius of {@code site locate}, in blocks. */
	private static final int LOCATE_RADIUS = 1024;
	/** Layers the Reliquary's surface piece sets into the ground (spec 4.15). */
	private static final int RELIQUARY_SURFACE_BURIED = 9;
	private static final Identifier RELIQUARY_SURFACE = OldRepublicMod.id("boss/reliquary_surface");

	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root
				.then(literal("site")
						.then(literal("place")
								.then(argument("site", IdentifierArgument.id())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(siteSuggestions(), builder))
										// API-CHECK: BlockPosArgument.blockPos() / getLoadedBlockPos(ctx, name) (vanilla, as SentinelCommands)
										.then(argument("pos", BlockPosArgument.blockPos())
												.executes(SiteCommands::place))))
						.then(literal("locate")
								.then(argument("player", EntityArgument.player())
										.then(argument("kind", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(SiteKind.values()).map(SiteKind::id), builder))
												.executes(SiteCommands::locate)))))
				.then(literal("outpost")
						.then(literal("ensure")
								.then(argument("player", EntityArgument.player())
										.executes(SiteCommands::ensureOutposts)))));
	}

	private static int place(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Identifier site = IdentifierArgument.getId(ctx, "site");
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		CommandSourceStack source = ctx.getSource();
		String shortName = shortName(site);

		if (shortName != null && STRUCTURES.contains(shortName)) {
			Identifier structure = OldRepublicMod.id(shortName);
			String failure = placeStructure(source.getLevel(), structure, pos);

			if (failure != null) {
				source.sendFailure(Component.translatable("commands.oldrepublic.site.place_failed", structure.toString(), failure));
				return 0;
			}

			source.sendSuccess(() -> Component.translatable("commands.oldrepublic.site.placed", structure.toString(),
					SectionPos.blockToSectionCoord(pos.getX()) << 4, pos.getY(), SectionPos.blockToSectionCoord(pos.getZ()) << 4), true);
			return 1;
		}

		Identifier template = shortName != null && SHORT_TEMPLATES.containsKey(shortName) ? SHORT_TEMPLATES.get(shortName) : site;

		if (SiteKind.ofTemplate(template) == null) {
			source.sendFailure(Component.translatable("commands.oldrepublic.site.unknown", site.toString()));
			return 0;
		}

		ServerLevel level = source.getLevel();

		try {
			RuntimeSites.Placement placement = RuntimeSites.placeAt(level, template, pos, Rotation.NONE, buried(template), null);
			BlockPos origin = placement.origin();
			source.sendSuccess(() -> Component.translatable("commands.oldrepublic.site.placed", template.toString(), origin.getX(), origin.getY(), origin.getZ()), true);
			return 1;
		} catch (IllegalArgumentException | IllegalStateException e) {
			source.sendFailure(Component.translatable("commands.oldrepublic.site.place_failed", template.toString(), String.valueOf(e.getMessage())));
			return 0;
		}
	}

	/**
	 * Places a worldgen structure on the code path of vanilla {@code /place structure} (one start
	 * generated for the chunk of {@code pos} in any biome, then placed chunk by chunk; a jigsaw start
	 * sits at the chunk's corner on the surface) and registers it as worldgen does: the start in its
	 * chunk and a reference in every chunk its box touches. Vanilla's command leaves both out, and
	 * then {@code getStructureWithPieceAt}, the locator, the site tracker and the Calling would never
	 * see the placed site (spec 10.8, 17.2). Returns null on success, else why it failed.
	 */
	static @Nullable String placeStructure(ServerLevel level, Identifier id, BlockPos pos) {
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(id).orElse(null);

		if (holder == null) {
			return "unknown structure";
		}

		ChunkGenerator generator = level.getChunkSource().getGenerator();
		ChunkPos startChunk = ChunkPos.containing(pos);
		// API-CHECK: Structure#generate (core dump 3825), ServerChunkCache#getGenerator / randomState (m3_boss dump).
		StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), startChunk, 0, level, biome -> true);

		if (!start.isValid()) {
			return "no valid start here";
		}

		BoundingBox box = start.getBoundingBox();
		int minChunkX = SectionPos.blockToSectionCoord(box.minX());
		int maxChunkX = SectionPos.blockToSectionCoord(box.maxX());
		int minChunkZ = SectionPos.blockToSectionCoord(box.minZ());
		int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ());

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					return "the area is not loaded";
				}
			}
		}

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				int x = chunkX << 4;
				int z = chunkZ << 4;
				BoundingBox slice = new BoundingBox(x, level.getMinY(), z, x + 15, level.getMinY() + level.getHeight(), z + 15);
				// API-CHECK: StructureStart#placeInChunk(WorldGenLevel, StructureManager, ChunkGenerator, RandomSource, BoundingBox, ChunkPos) (world dump 7768)
				start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), slice, new ChunkPos(chunkX, chunkZ));
			}
		}

		// API-CHECK: ChunkAccess#setStartForStructure / addReferenceForStructure / markUnsaved (world dump 7911-7919)
		ChunkAccess startAccess = level.getChunk(startChunk.x(), startChunk.z());
		startAccess.setStartForStructure(holder.value(), start);
		startAccess.markUnsaved();

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
				chunk.addReferenceForStructure(holder.value(), startChunk.pack());
				chunk.markUnsaved();
			}
		}

		return null;
	}

	private static int locate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String raw = StringArgumentType.getString(ctx, "kind");
		SiteKind kind = SiteKind.byId(raw);
		CommandSourceStack source = ctx.getSource();

		if (kind == null) {
			source.sendFailure(Component.translatable("commands.oldrepublic.site.unknown_kind", raw));
			return 0;
		}

		Component name = Component.translatable("site.oldrepublic." + kind.id());
		BlockPos from = player.blockPosition();
		source.sendSuccess(() -> Component.translatable("commands.oldrepublic.site.locate.searching", name, player.getDisplayName()), false);
		StructureLocator.find(player, kind, LOCATE_RADIUS, null, piece -> {
			if (piece == null) {
				source.sendFailure(Component.translatable("commands.oldrepublic.site.locate.none", name, LOCATE_RADIUS));
				return;
			}

			BlockPos centre = piece.box().getCenter();
			int distance = Mth.floor(Math.sqrt(LocatorQueue.distSqr(from, piece)));
			source.sendSuccess(() -> Component.translatable("commands.oldrepublic.site.locate.found", name, centre.getX(), centre.getY(), centre.getZ(), distance), false);
		});
		return 1;
	}

	private static int ensureOutposts(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		CommandSourceStack source = ctx.getSource();

		if (player.level().dimension() != Level.OVERWORLD) {
			source.sendFailure(Component.translatable("commands.oldrepublic.outpost.overworld_only", player.getDisplayName()));
			return 0;
		}

		source.sendSuccess(() -> Component.translatable("commands.oldrepublic.outpost.ensuring", player.getDisplayName()), true);
		Outposts.ensureNear(player, (army, decision) -> source.sendSuccess(() -> Component.translatable(
				"commands.oldrepublic.outpost.decision." + decision.name().toLowerCase(Locale.ROOT),
				Component.translatable("faction.oldrepublic." + army.id()), terminalText(player, army)), true));
		return 1;
	}

	/** Where the army's nearest terminal is, as far as the runtime records and the locator cache know. */
	private static Component terminalText(ServerPlayer player, Faction army) {
		BlockPos terminal = Outposts.nearestTerminal(player, army, Integer.MAX_VALUE / 2);
		return terminal == null
				? Component.translatable("commands.oldrepublic.outpost.terminal_unknown")
				: Component.translatable("commands.oldrepublic.outpost.terminal_at", terminal.getX(), terminal.getY(), terminal.getZ());
	}

	/**
	 * The path of an id that names a short form, else null: {@code jedi_ruin} (read by the argument as
	 * {@code minecraft:jedi_ruin}) or {@code oldrepublic:jedi_ruin}.
	 */
	private static @Nullable String shortName(Identifier site) {
		boolean shortNamespace = "minecraft".equals(site.getNamespace()) || OldRepublicMod.MOD_ID.equals(site.getNamespace());
		return shortNamespace && !site.getPath().contains("/") ? site.getPath() : null;
	}

	/** Layers a template sets into the ground when a command places it (4.15). */
	private static int buried(Identifier template) {
		SiteKind kind = SiteKind.ofTemplate(template);
		int planet = PlanetSites.buried(template);

		if (planet >= 0) {
			return planet;
		} else if (kind == SiteKind.ECHO_SHRINE) {
			return EchoShrines.BURIED;
		} else if (kind == SiteKind.REPUBLIC_OUTPOST || kind == SiteKind.IMPERIAL_OUTPOST) {
			return OutpostPlacement.BURIED;
		}

		return RELIQUARY_SURFACE.equals(template) ? RELIQUARY_SURFACE_BURIED : 0;
	}

	private static List<String> siteSuggestions() {
		List<String> suggestions = new ArrayList<>(STRUCTURES);
		suggestions.addAll(SHORT_TEMPLATES.keySet());

		for (Identifier template : SiteAnchors.templates()) {
			if (SiteKind.ofTemplate(template) != null) {
				suggestions.add(template.toString());
			}
		}

		return suggestions;
	}

	private static Map<String, Identifier> shortTemplates() {
		Map<String, Identifier> templates = new LinkedHashMap<>();

		for (HolocronKind kind : HolocronKind.values()) {
			Identifier template = EchoShrines.template(kind);
			templates.put(template.getPath().substring("site/".length()), template);
		}

		for (SiteStates.Army army : SiteStates.Army.values()) {
			Identifier template = OutpostPlacement.template(army);
			templates.put(template.getPath().substring("outpost/".length()), template);
		}

		return Collections.unmodifiableMap(templates);
	}

	private SiteCommands() {
	}
}

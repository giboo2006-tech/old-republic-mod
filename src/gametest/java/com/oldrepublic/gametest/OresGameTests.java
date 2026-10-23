package com.oldrepublic.gametest;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.world.ore.OreFeature;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Milestone 7's ores (spec MILESTONE_7.md 4, 14): starfall iron and clan iron need their pickaxes and drop their raw
 * metal; starfall smelts and blasts into ingots and hymnsteel alloys four materials into two ingots; the deep starfall
 * veins and Tyrvann's clan iron load and reach their biomes; every meteor crater holds 6 to 14 ore blocks in a starfall
 * shell and no chest; and a crater generates and places on the test world's flat ground.
 *
 * <p>Nothing here changes the test's own area. The placement test builds a crater far from every other test, at chunk
 * ({@link #FAR_CHUNK}, {@link #FAR_CHUNK}); the test world is superflat, where a crater's bowl reaches below the world's
 * floor and is cut there, but its rim, ejecta and fragments stand on the ground.
 */
public class OresGameTests {
	private static final double EPS = 1.0e-4;
	/** The meteor craters (data/oldrepublic/worldgen/structure/wilds/starfall_crater.json) and their templates' folder. */
	private static final Identifier CRATER = OldRepublicMod.id("wilds/starfall_crater");
	private static final String TEMPLATE_DIR = "wilds/starfall_crater/";
	/** The crater's starts (tools/assets/structures/or/starfall_crater.py): sizes, and whether its ore is the deepslate kind. */
	private static final List<CraterTemplate> CRATERS = List.of(
			new CraterTemplate("fresh", new Vec3i(57, 22, 39), false),
			new CraterTemplate("old", new Vec3i(55, 20, 41), false),
			new CraterTemplate("deep", new Vec3i(45, 22, 33), true));
	/** Ore blocks a crater's meteorite holds (4.1). */
	private static final int MIN_ORE = 6;
	private static final int MAX_ORE = 14;
	/** Starfall rock blocks a crater shows at least: the meteorite's shell and its thrown fragments. */
	private static final int MIN_SHELL = 40;
	/**
	 * Layers a crater's start is buried (tools/assets/structures/or/worldgen_or.py): the ground's top block at template
	 * y 13, the feet at y 14, so the piece's bottom is 14 below the first free block of the ground.
	 */
	private static final int BURIED = 14;
	/** The chunk the placement test builds its crater in: far from every test's structure. */
	private static final int FAR_CHUNK = 2048;
	/** Smelting and blasting (4.1). */
	private static final int SMELT_TICKS = 200;
	private static final int BLAST_TICKS = 100;
	private static final float SMELT_XP = 0.7f;
	/** Hymnsteel ingots one alloying gives (4.3). */
	private static final int HYMNSTEEL_YIELD = 2;
	private static final TagKey<Block> MINEABLE_WITH_PICKAXE = vanillaBlockTag("mineable/pickaxe");
	private static final TagKey<Block> NEEDS_IRON_TOOL = vanillaBlockTag("needs_iron_tool");
	private static final TagKey<Block> NEEDS_DIAMOND_TOOL = vanillaBlockTag("needs_diamond_tool");

	/** A crater start: its template's name, its size and whether its ore is deepslate starfall ore. */
	private record CraterTemplate(String name, Vec3i size, boolean deepslate) {
		Identifier id() {
			return OldRepublicMod.id(TEMPLATE_DIR + name);
		}
	}

	/** One of the new ore blocks: its strength, the weakest pickaxe that mines it, the tier below (null: any pickaxe) and its plain drop. */
	private record Ore(Block block, float hardness, Item weakest, @Nullable Item tooWeak, Item drop) {
	}

	private static TagKey<Block> vanillaBlockTag(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path));
	}

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// API-CHECK: Items.WOODEN_PICKAXE, STONE_PICKAXE and DIAMOND_PICKAXE (this repository uses IRON_ and NETHERITE_PICKAXE)
	private static List<Ore> ores() {
		return List.of(
				new Ore(M7Items.STARFALL_ORE, 3.5f, Items.IRON_PICKAXE, Items.STONE_PICKAXE, M7Items.RAW_STARFALL),
				new Ore(M7Items.DEEPSLATE_STARFALL_ORE, 5f, Items.IRON_PICKAXE, Items.STONE_PICKAXE, M7Items.RAW_STARFALL),
				new Ore(M7Items.STARFALL_ROCK, 2.5f, Items.WOODEN_PICKAXE, null, M7Items.STARFALL_ROCK.asItem()),
				new Ore(M7Items.CLAN_IRON_ORE, 4f, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, M5Items.CLAN_IRON));
	}

	// ---------------------------------------------------------------- mining

	/**
	 * Each new block has its strength (4.1, 4.2), is a pickaxe block that needs the right tool, and the tool tags put the
	 * starfall ores at iron and clan iron at diamond: the weakest pickaxe that mines it is correct, the tier below is not.
	 */
	@GameTest(maxTicks = 20)
	public void oresNeedTheirPickaxes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos spot = helper.absolutePos(BlockPos.ZERO);

		// The checks read only the blocks' properties, so nothing is placed.
		for (Ore ore : ores()) {
			BlockState state = ore.block().defaultBlockState();
			helper.assertTrue(state.getDestroySpeed(level, spot) == ore.hardness(),
					msg(ore.block() + " has strength " + ore.hardness() + ", got " + state.getDestroySpeed(level, spot)));
			helper.assertTrue(state.is(MINEABLE_WITH_PICKAXE), msg(ore.block() + " is mined with a pickaxe"));
			// API-CHECK: BlockBehaviour.BlockStateBase#requiresCorrectToolForDrops()
			helper.assertTrue(state.requiresCorrectToolForDrops(), msg(ore.block() + " drops nothing to the wrong tool"));
			helper.assertTrue(correctTool(ore.weakest(), state), msg(ore.weakest() + " mines " + ore.block()));

			if (ore.tooWeak() != null) {
				helper.assertFalse(correctTool(ore.tooWeak(), state), msg(ore.tooWeak() + " is too weak for " + ore.block()));
			}
		}

		helper.assertTrue(M7Items.STARFALL_ORE.defaultBlockState().is(NEEDS_IRON_TOOL) && M7Items.DEEPSLATE_STARFALL_ORE.defaultBlockState().is(NEEDS_IRON_TOOL),
				msg("the starfall ores need an iron pickaxe"));
		helper.assertTrue(M7Items.CLAN_IRON_ORE.defaultBlockState().is(NEEDS_DIAMOND_TOOL), msg("clan iron ore needs a diamond pickaxe"));
		helper.assertFalse(M7Items.STARFALL_ROCK.defaultBlockState().is(NEEDS_IRON_TOOL), msg("starfall rock takes any pickaxe"));
		helper.succeed();
	}

	/**
	 * Mined with the weakest right pickaxe, each block drops one of its plain drop: raw starfall from both starfall ores,
	 * clan iron from clan iron ore, the rock itself. Each ore's loot table gives the block to Silk Touch and lets Fortune
	 * multiply the raw drop, as deepslate iron ore's does.
	 */
	@GameTest(maxTicks = 20)
	public void oresDropTheirLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos spot = helper.absolutePos(BlockPos.ZERO);
		List<String> problems = new ArrayList<>();

		for (Ore ore : ores()) {
			// API-CHECK: Block.getDrops(BlockState, ServerLevel, BlockPos, BlockEntity, Entity, ItemStack) (static, nullable block entity and entity)
			List<ItemStack> drops = Block.getDrops(ore.block().defaultBlockState(), level, spot, null, null, new ItemStack(ore.weakest()));

			if (drops.size() != 1 || !drops.getFirst().is(ore.drop()) || drops.getFirst().getCount() != 1) {
				problems.add(ore.block() + " dropped " + drops + ", expected one " + ore.drop());
			}

			if (ore.tooWeak() == null) {
				continue; // the rock: a plain self drop
			}

			Identifier block = BuiltInRegistries.BLOCK.getKey(ore.block());
			JsonObject table = readJson(level, "loot_table/blocks/", block);

			if (table == null) {
				problems.add(block + ": no loot table");
			} else if (!silkTouchThenFortune(table, block, BuiltInRegistries.ITEM.getKey(ore.drop()))) {
				problems.add(block + ": the loot table is not Silk Touch for the block, else the raw drop with Fortune");
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("drop problems: " + problems));
		helper.succeed();
	}

	/** Whether {@code table} is one pool of two alternatives: the block under Silk Touch, else {@code drop} with Fortune's ore bonus. */
	private static boolean silkTouchThenFortune(JsonObject table, Identifier block, Identifier drop) {
		try {
			JsonObject entry = table.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
			JsonArray children = entry.getAsJsonArray("children");

			if (!entry.get("type").getAsString().equals("minecraft:alternatives") || children.size() != 2) {
				return false;
			}

			JsonObject silk = children.get(0).getAsJsonObject();
			JsonObject raw = children.get(1).getAsJsonObject();
			return silk.get("name").getAsString().equals(block.toString()) && silk.toString().contains("minecraft:silk_touch")
					&& raw.get("name").getAsString().equals(drop.toString()) && raw.toString().contains("minecraft:fortune")
					&& raw.toString().contains("minecraft:ore_drops");
		} catch (RuntimeException e) {
			return false;
		}
	}

	// ---------------------------------------------------------------- recipes

	/** Raw starfall and both ores smelt (200 ticks) and blast (100 ticks) into a starfall ingot for 0.7 experience (4.1). */
	@GameTest(maxTicks = 20)
	public void starfallSmeltsAndBlasts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String ingot = BuiltInRegistries.ITEM.getKey(M7Items.STARFALL_INGOT).toString();
		List<String> problems = new ArrayList<>();

		for (Item input : List.of(M7Items.RAW_STARFALL, M7Items.STARFALL_ORE.asItem(), M7Items.DEEPSLATE_STARFALL_ORE.asItem())) {
			Identifier inputId = BuiltInRegistries.ITEM.getKey(input);

			for (String kind : List.of("smelting", "blasting")) {
				Identifier id = OldRepublicMod.id("starfall_ingot_from_" + kind + "_" + inputId.getPath());

				if (level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id)).isEmpty()) {
					problems.add(id + ": not loaded");
					continue;
				}

				JsonObject json = readJson(level, "recipe/", id);

				if (json == null) {
					problems.add(id + ": no recipe file");
					continue;
				}

				int ticks = kind.equals("smelting") ? SMELT_TICKS : BLAST_TICKS;

				if (!json.get("type").getAsString().equals("minecraft:" + kind) || json.get("cookingtime").getAsInt() != ticks
						|| Math.abs(json.get("experience").getAsFloat() - SMELT_XP) > EPS) {
					problems.add(id + ": not " + kind + " for " + ticks + " ticks and " + SMELT_XP + " experience");
				}

				if (!json.get("ingredient").getAsString().equals(inputId.toString()) || !json.getAsJsonObject("result").get("id").getAsString().equals(ingot)) {
					problems.add(id + ": does not turn " + inputId + " into " + ingot);
				}
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("starfall recipe problems: " + problems));
		helper.succeed();
	}

	/**
	 * Hymnsteel (4.3): a hymnstone shard, clan iron, sunstone and a starfall ingot, in any places, make two ingots; with
	 * any one of them swapped for raw starfall nothing is made.
	 */
	@GameTest(maxTicks = 20)
	public void hymnsteelAlloysFourIntoTwo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Identifier id = BuiltInRegistries.ITEM.getKey(M7Items.HYMNSTEEL_INGOT);
		Optional<RecipeHolder<?>> holder = level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id));
		helper.assertTrue(holder.isPresent(), msg(id + " is not loaded"));

		@SuppressWarnings("unchecked")
		Recipe<CraftingInput> recipe = (Recipe<CraftingInput>) holder.get().value();
		List<ItemStack> four = List.of(new ItemStack(M5Items.HYMNSTONE_SHARD), new ItemStack(M5Items.CLAN_IRON), new ItemStack(M5Items.SUNSTONE),
				new ItemStack(M7Items.STARFALL_INGOT));
		helper.assertTrue(recipe.matches(CraftingInput.of(2, 2, four), level), msg("the four materials make hymnsteel"));
		helper.assertTrue(recipe.matches(CraftingInput.of(2, 2, four.reversed()), level), msg("in any places"));

		for (int i = 0; i < four.size(); i++) {
			List<ItemStack> swapped = new ArrayList<>(four);
			swapped.set(i, new ItemStack(M7Items.RAW_STARFALL));
			helper.assertFalse(recipe.matches(CraftingInput.of(2, 2, swapped), level), msg("nothing is made without " + four.get(i)));
		}

		JsonObject json = readJson(level, "recipe/", id);
		helper.assertTrue(json != null && json.getAsJsonObject("result").get("count").getAsInt() == HYMNSTEEL_YIELD
				&& json.getAsJsonObject("result").get("id").getAsString().equals(id.toString()), msg("one alloying gives " + HYMNSTEEL_YIELD + " ingots"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- worldgen

	/**
	 * The deep starfall veins and Tyrvann's clan iron load as configured and placed features; Fabric's biome modification
	 * adds the deep veins to the overworld's biomes (plains) and to no planet's; the clan iron is in Tyrvann's basalt
	 * ridges only.
	 */
	@GameTest(maxTicks = 20)
	public void oreFeaturesReachTheirBiomes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// API-CHECK: Registries.CONFIGURED_FEATURE and PLACED_FEATURE; ConfiguredFeature in net.minecraft.world.level.levelgen.feature
		Registry<ConfiguredFeature<?, ?>> configured = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
		Registry<PlacedFeature> placed = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);

		for (String name : List.of("ore_starfall_deep", "ore_clan_iron")) {
			helper.assertTrue(configured.get(OldRepublicMod.id(name)).isPresent(), msg("configured feature " + name + " is not registered"));
			helper.assertTrue(placed.get(OldRepublicMod.id(name)).isPresent(), msg("placed feature " + name + " is not registered"));
		}

		PlacedFeature deep = placed.get(OreFeature.STARFALL_DEEP.identifier()).orElseThrow().value();
		PlacedFeature clanIron = placed.get(OldRepublicMod.id("ore_clan_iron")).orElseThrow().value();
		Holder.Reference<Biome> plains = biomes.get(Identifier.withDefaultNamespace("plains")).orElseThrow();
		Holder.Reference<Biome> basalt = biomes.get(OldRepublicMod.id("tyrvann_basalt")).orElseThrow();
		Holder.Reference<Biome> stormwood = biomes.get(OldRepublicMod.id("tyrvann_stormwood")).orElseThrow();
		// API-CHECK: Biome#getGenerationSettings() and BiomeGenerationSettings#hasFeature(PlacedFeature) (Fabric's modification refreshes the feature set)
		helper.assertTrue(plains.value().getGenerationSettings().hasFeature(deep), msg("the deep starfall veins are added to the plains"));
		helper.assertFalse(basalt.value().getGenerationSettings().hasFeature(deep), msg("the deep starfall veins stay off the planets"));
		helper.assertTrue(basalt.value().getGenerationSettings().hasFeature(clanIron), msg("clan iron is in Tyrvann's basalt ridges"));
		helper.assertFalse(stormwood.value().getGenerationSettings().hasFeature(clanIron), msg("clan iron is not in the stormwood"));
		helper.assertFalse(plains.value().getGenerationSettings().hasFeature(clanIron), msg("clan iron is not in the overworld"));
		helper.succeed();
	}

	/**
	 * Every crater start loads with its size and holds 6 to 14 ore blocks (the deep crater's of the deepslate kind, the
	 * others' of the stone kind) in a shell of starfall rock, and no chest: the ore is the prize (4.1). Loads the templates;
	 * never places one.
	 */
	@GameTest(maxTicks = 40)
	public void cratersHoldTheirOre(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		for (CraterTemplate crater : CRATERS) {
			StructureTemplate template = level.getStructureManager().get(crater.id()).orElse(null);
			helper.assertTrue(template != null, msg("missing structure template " + crater.id()));
			helper.assertTrue(template.getSize().equals(crater.size()), msg(crater.id() + " has size " + template.getSize() + ", expected " + crater.size()));
			int stone = cells(template, M7Items.STARFALL_ORE);
			int deepslate = cells(template, M7Items.DEEPSLATE_STARFALL_ORE);
			int ore = stone + deepslate;
			helper.assertTrue(ore >= MIN_ORE && ore <= MAX_ORE, msg(crater.id() + " holds " + ore + " ore blocks, not " + MIN_ORE + " to " + MAX_ORE));
			helper.assertTrue(crater.deepslate() ? stone == 0 : deepslate == 0,
					msg(crater.id() + "'s ore is " + (crater.deepslate() ? "deepslate" : "stone") + " starfall ore: " + stone + " and " + deepslate));
			helper.assertTrue(cells(template, M7Items.STARFALL_ROCK) >= MIN_SHELL, msg(crater.id() + " has too little starfall rock"));
			helper.assertTrue(cells(template, Blocks.CHEST) == 0, msg(crater.id() + " holds a chest"));
		}

		helper.succeed();
	}

	private static int cells(StructureTemplate template, Block block) {
		// With BlockPos.ZERO and default settings, filterBlocks gives template-local positions.
		return template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).size();
	}

	/**
	 * A crater generates and places on the flat ground far from every test, as {@code /place structure} does it (one start
	 * in any biome, then placed chunk by chunk): one piece of one of the three starts, its bottom {@link #BURIED} below
	 * the ground's first free block, and starfall rock standing in the world afterwards.
	 */
	@GameTest(maxTicks = 100)
	public void craterPlacesOnFlatGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(CRATER).orElse(null);
		helper.assertTrue(holder != null, msg("structure " + CRATER + " is not registered"));
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		ChunkPos startChunk = new ChunkPos(FAR_CHUNK, FAR_CHUNK);
		// As SiteCommands#placeStructure: Structure#generate, ServerChunkCache#getGenerator / randomState.
		StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), startChunk, 0, level, biome -> true);
		helper.assertTrue(start.isValid(), msg("no crater start on the flat ground"));
		List<StructurePiece> pieces = start.getPieces();
		helper.assertTrue(pieces.size() == 1, msg("a crater is one piece, got " + pieces.size()));
		Identifier template = pieces.getFirst() instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
				? single.getTemplateLocation() : null;
		helper.assertTrue(template != null && CRATERS.stream().anyMatch(c -> c.id().equals(template)), msg("the piece is a crater start, got " + template));

		// Its chunks and the columns round it (the ramp reads the ground just outside the piece) generate first.
		BoundingBox box = start.getBoundingBox();
		int minChunkX = SectionPos.blockToSectionCoord(box.minX() - 1);
		int maxChunkX = SectionPos.blockToSectionCoord(box.maxX() + 1);
		int minChunkZ = SectionPos.blockToSectionCoord(box.minZ() - 1);
		int maxChunkZ = SectionPos.blockToSectionCoord(box.maxZ() + 1);

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				level.getChunk(chunkX, chunkZ);
			}
		}

		// The ground beside the piece, which the crater never touches (so a rerun reads the same).
		int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, box.minX() - 1, box.minZ() - 1);
		helper.assertTrue(box.minY() == ground - BURIED, msg("the crater's bottom is at " + box.minY() + ", " + BURIED + " below the ground's " + ground));

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				int x = chunkX << 4;
				int z = chunkZ << 4;
				BoundingBox slice = new BoundingBox(x, level.getMinY(), z, x + 15, level.getMinY() + level.getHeight(), z + 15);
				// As SiteCommands#placeStructure: StructureStart#placeInChunk(WorldGenLevel, StructureManager, ChunkGenerator, RandomSource, BoundingBox, ChunkPos).
				start.placeInChunk(level, level.structureManager(), generator, level.getRandom(), slice, new ChunkPos(chunkX, chunkZ));
			}
		}

		int rock = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = Math.max(box.minY(), level.getMinY()); y <= box.maxY(); y++) {
					if (level.getBlockState(pos.set(x, y, z)).is(M7Items.STARFALL_ROCK)) {
						rock++;
					}
				}
			}
		}

		helper.assertTrue(rock > 0, msg("no starfall rock stands in the placed " + template + " (box " + box + ")"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	/** The data file {@code <folder><path>.json} of {@code id}'s namespace, or null. */
	private static @Nullable JsonObject readJson(ServerLevel level, String folder, Identifier id) {
		Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), folder + id.getPath() + ".json");
		Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);

		if (resource.isEmpty()) {
			return null;
		}

		try (Reader reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	/** Whether {@code tool} is the right tool to get {@code state}'s drops. */
	private static boolean correctTool(Item tool, BlockState state) {
		// API-CHECK: Item#isCorrectToolForDrops(ItemStack, BlockState) is public (crafting/item/FusionCutterItem overrides it)
		return tool.isCorrectToolForDrops(new ItemStack(tool), state);
	}
}

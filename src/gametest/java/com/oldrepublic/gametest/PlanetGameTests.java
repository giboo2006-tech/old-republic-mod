package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.planet.Heartstones;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSiteData;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.TerrainPalette;
import com.oldrepublic.planet.Waystones;
import com.oldrepublic.planet.block.SealedPlatingBlock;
import com.oldrepublic.planet.block.WaystoneBlock;
import com.oldrepublic.planet.block.WaystoneBlockEntity;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.world.site.SiteAnchors;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 planet tests (spec MILESTONE_4.md 17.1, {@code PlanetGameTests}, W1): the planet
 * biomes and their tags and palette rows, the hub's once-per-server placement and its fallback
 * stilts, the planet sites' retry rule, waystone cleansing per player and the seeded corruption,
 * sealed plating, the vanilla portal rule and the M4 templates' sidecars.
 *
 * <p>The planets do not exist on the game-test server, so every test uses detached data, pure rules
 * or a fixture in the overworld (the portal rule is checked in the End). Each test declares
 * {@code maxTicks} (at most 400); randomness is seeded; listed players are removed and global state
 * (the retry times) is cleaned up in {@code finally}; no test writes {@code WorldSites} or
 * {@code PlanetSiteData}, and no test places an M4 template.
 */
public class PlanetGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";

	/** The M4 templates W1 owns, with their sizes and the anchors each must name. */
	private record M4Template(String path, Vec3i size, List<String> anchors) {
	}

	private static final List<String> HUB_ANCHORS = List.of("HEART", "PAD_BAY_1", "PAD_BAY_2", "PAD_BAY_3", "PAD_BAY_4", "MEDBAY", "HOST",
			"VENDOR_1", "VENDOR_2", "VENDOR_3", "VENDOR_4", "RESIDENT_1", "RESIDENT_2", "RESIDENT_3", "PAD_TERMINAL", "CONTRACT_TERMINAL");

	private static final List<M4Template> TEMPLATES = List.of(
			new M4Template("planet/waystone", new Vec3i(11, 9, 11), List.of("STONE", "CACHE", "HEART")),
			new M4Template("planet/waystone_corrupted", new Vec3i(11, 9, 11), List.of("STONE", "CACHE", "HEART", "GUARD")),
			new M4Template("planet/heartstone", new Vec3i(19, 12, 19), List.of("HEART", "STONE")),
			new M4Template("planet/wayfarer_cache", new Vec3i(13, 9, 13), List.of("HEART", "CACHE", "PLATFORM_1", "PLATFORM_2", "PLATFORM_3")),
			new M4Template("greater_tomb/gate", new Vec3i(21, 14, 21), List.of("STAIR_MOUTH", "SQUAD", "GUARD", "CHEST_1", "SURFACE_GUARD")),
			new M4Template("greater_tomb/descent", new Vec3i(11, 18, 27), List.of("SENTINEL_1")),
			new M4Template("greater_tomb/urn_vault", new Vec3i(21, 12, 21),
					List.of("URN_KEEPER", "URN_1", "URN_2", "URN_3", "URN_4", "SENTINEL_1", "SENTINEL_2", "CHEST_1", "DOOR_SEAL")),
			new M4Template("greater_tomb/sealed_crypt", new Vec3i(21, 12, 21), List.of("HEART", "RELIC", "SENTINEL_1", "SENTINEL_2", "CHEST_1")),
			new M4Template("hub/greenwatch", new Vec3i(63, 24, 63), HUB_ANCHORS),
			new M4Template("hub/proving", new Vec3i(63, 28, 63), concat(HUB_ANCHORS, List.of("RESIDENT_4", "RESIDENT_5", "RESIDENT_6", "RESIDENT_7", "STAIR_DOWN"))),
			new M4Template("hub/sepulchre", new Vec3i(33, 16, 41), List.of("HEART", "ROOM_1", "ROOM_2", "ROOM_3", "ECHO")),
			new M4Template("expedition/singing_hollow_surface", new Vec3i(13, 10, 13), List.of("HEART", "GUARD")),
			new M4Template("expedition/singing_hollow_grotto", new Vec3i(31, 16, 31), List.of("HEART", "CHIME_1", "CHIME_2", "CHIME_3", "CHIME_4",
					"CHIME_5", "CHIME_6", "WEIGHT_1", "WEIGHT_2", "WEIGHT_3", "WEIGHT_4", "WEIGHT_5", "WEIGHT_6", "GATE", "RELIC")),
			new M4Template("expedition/duskglass_rift_surface", new Vec3i(15, 8, 15), List.of("HEART")),
			new M4Template("expedition/duskglass_rift_rift", new Vec3i(23, 28, 31), List.of("HEART", "RELIC", "SQUAD_1", "SQUAD_2")),
			new M4Template("boss/hunting_camp", new Vec3i(41, 14, 41), List.of("HEART", "NEST_1", "NEST_2", "NEST_3", "NEST_4", "GATE", "MINE_1",
					"MINE_2", "MINE_3", "MINE_4", "MINE_5", "MINE_6")),
			new M4Template("mission/ambush_rocks", new Vec3i(13, 6, 13), List.of("HEART")));

	// ---------------------------------------------------------------- biomes

	/**
	 * Each planet biome (M4's six and M5's six) is registered, in {@code oldrepublic:is_planet} and its
	 * own planet's tag only, and in no vanilla {@code has_structure/*} tag nor
	 * {@code #minecraft:is_overworld}, so no vanilla or M3 structure grows on a planet (3.1;
	 * MILESTONE_5.md 3.2, 3.3).
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void biomesInNoVanillaStructureTag(GameTestHelper helper) {
		Registry<Biome> biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);

		for (Planets.Planet planet : Planets.planets()) {
			for (ResourceKey<Biome> key : PlanetWorldgen.biomes(planet)) {
				Holder.Reference<Biome> biome = biomes.get(key.identifier()).orElse(null);
				helper.assertTrue(biome != null, msg("biome " + key.identifier() + " is not registered"));
				Set<Identifier> tags = biome.tags().map(TagKey::location).collect(Collectors.toSet());
				helper.assertTrue(biome.is(PlanetWorldgen.IS_PLANET), msg(key.identifier() + " must be in #oldrepublic:is_planet"));

				for (Planets.Planet other : Planets.planets()) {
					TagKey<Biome> tag = PlanetWorldgen.biomeTag(other);

					if (tag != null) {
						helper.assertTrue(biome.is(tag) == (other == planet),
								msg(key.identifier() + (other == planet ? " must be in its planet's tag" : " must not be in #" + tag.location())));
					}
				}

				for (Identifier tag : tags) {
					boolean vanilla = tag.getNamespace().equals("minecraft");
					helper.assertFalse(vanilla && (tag.getPath().startsWith("has_structure/") || tag.getPath().equals("is_overworld")),
							msg(key.identifier() + " is in the vanilla tag #" + tag));
				}
			}
		}

		helper.succeed();
	}

	/**
	 * Every planet biome registers its own terrain palette row (3.6) with no air layer. Selvaren's rows are grass, dirt
	 * and stone by design (docs/design/planets.md); the other planets' are never the overworld's. Twelve biomes since
	 * Milestone 5 (Ghessar's and Tyrvann's three each).
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void planetBiomesHavePaletteRows(GameTestHelper helper) {
		for (ResourceKey<Biome> key : PlanetWorldgen.BIOMES) {
			TerrainPalette.Layers row = TerrainPalette.row(key);
			helper.assertTrue(row != null, msg(key.identifier() + " has no terrain palette row"));
			helper.assertFalse(!key.identifier().getPath().startsWith("selvaren_") && row.equals(TerrainPalette.Layers.DEFAULT),
					msg(key.identifier() + " uses the overworld palette"));
			helper.assertTrue(!row.top().isAir() && !row.fill().isAir() && !row.base().isAir(), msg(key.identifier() + " has an air layer"));
		}

		helper.assertTrue(PlanetWorldgen.BIOMES.size() == 12, msg("twelve planet biomes, got " + PlanetWorldgen.BIOMES.size()));
		helper.succeed();
	}

	// ---------------------------------------------------------------- hubs

	/**
	 * {@link PlanetHubs#ensureIn} places a planet's hub once, whoever asks and however often (detached
	 * data, a counting fake placement), each planet its own; the result survives the codec.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void hubEnsurePlacesOncePerServer(GameTestHelper helper) {
		PlanetSiteData.Data data = new PlanetSiteData.Data();
		int[] placements = new int[1];
		PlanetSiteData.Hub fake = new PlanetSiteData.Hub(new BlockPos(3, 70, -4), List.of(new BlockPos(10, 70, 0), new BlockPos(-10, 70, 0)),
				new BlockPos(0, 70, 8), false);

		PlanetSiteData.Hub first = PlanetHubs.ensureIn(data, Planets.Planet.SELVAREN, () -> {
			placements[0]++;
			return fake;
		});
		PlanetSiteData.Hub second = PlanetHubs.ensureIn(data, Planets.Planet.SELVAREN, () -> {
			placements[0]++;
			return new PlanetSiteData.Hub(BlockPos.ZERO, List.of(), BlockPos.ZERO, true);
		});
		helper.assertTrue(placements[0] == 1, msg("the second ensure must not place again, placements " + placements[0]));
		helper.assertTrue(first.equals(fake) && second.equals(fake), msg("both ensures return the one hub"));

		PlanetHubs.ensureIn(data, Planets.Planet.VARKHAST, () -> {
			placements[0]++;
			return new PlanetSiteData.Hub(new BlockPos(1, 64, 1), List.of(), new BlockPos(1, 64, 2), true);
		});
		helper.assertTrue(placements[0] == 2, msg("Varkhast places its own hub, placements " + placements[0]));
		helper.assertTrue(data.hub(Planets.Planet.HOME) == null, msg("the home world has no hub"));

		Tag nbt = PlanetSiteData.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		PlanetSiteData.Data loaded = PlanetSiteData.Data.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
		helper.assertTrue(fake.equals(loaded.hub(Planets.Planet.SELVAREN)), msg("the Selvaren hub survives the codec"));
		helper.assertTrue(loaded.hub(Planets.Planet.VARKHAST) != null && loaded.hub(Planets.Planet.VARKHAST).raised(),
				msg("the Varkhast hub survives the codec, raised"));
		helper.succeed();
	}

	/**
	 * The fallback when no ring fits (3.5): the hub stands on a raised platform with stilts to the
	 * ground. Built here under a small box (the stilt rule {@link PlanetHubs#stilts} the fallback
	 * uses): columns every 4 blocks and at the far edges down to the floor, a ladder up the north face
	 * of the middle north stilt, and nothing above the floor between the stilts.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void hubFallbackPlatformWhenNoRingFits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos min = helper.absolutePos(new BlockPos(4, 5, 4));
		BlockPos max = helper.absolutePos(new BlockPos(12, 5, 10));
		BoundingBox box = BoundingBox.fromCorners(min, max);
		BlockState pillar = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		PlanetHubs.stilts(level, box, pillar, 4);
		int[] xs = {box.minX(), box.minX() + 4, box.maxX()};
		int[] zs = {box.minZ(), box.minZ() + 4, box.maxZ()};

		for (int x : xs) {
			for (int z : zs) {
				for (int y = box.minY() - 1; y > helper.absolutePos(BlockPos.ZERO).getY(); y--) {
					BlockPos at = new BlockPos(x, y, z);
					helper.assertTrue(level.getBlockState(at).is(Blocks.MOSSY_STONE_BRICKS), msg("a stilt is missing at " + at.toShortString()));
				}
			}
		}

		BlockPos between = new BlockPos(box.minX() + 2, box.minY() - 2, box.minZ() + 2);
		helper.assertTrue(level.getBlockState(between).isAir(), msg("no stilt between the stilts at " + between.toShortString()));
		BlockPos ladder = new BlockPos(box.minX() + 4, box.minY() - 1, box.minZ() - 1);
		BlockState ladderState = level.getBlockState(ladder);
		helper.assertTrue(ladderState.is(Blocks.LADDER) && ladderState.getValue(LadderBlock.FACING) == Direction.NORTH,
				msg("a ladder faces north on the middle north stilt at " + ladder.toShortString() + ", found " + ladderState));
		helper.assertTrue(level.getBlockState(ladder.atY(helper.absolutePos(BlockPos.ZERO).getY() + 1)).is(Blocks.LADDER),
				msg("the ladder reaches the floor"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- planet sites

	/**
	 * A planet site that cannot be placed yet is retried, at most once per
	 * {@code planets.siteRetryTicks} per owner and site ({@link PlanetSites#due}); {@link PlanetSites#ensure}
	 * returns null without placing anything while the planet (here: absent) or its chunks are not there.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void planetSiteEnsureRetriesUntilChunksExist(GameTestHelper helper) {
		int retry = Math.max(1, PlanetBalance.get().siteRetryTicks);
		UUID owner = new UUID(0x504c414e4554L, 0x5245545259L);
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			helper.assertTrue(PlanetSites.due(owner, PlanetSites.Site.SINGING_HOLLOW, 1000), msg("the first try is due"));
			helper.assertFalse(PlanetSites.due(owner, PlanetSites.Site.SINGING_HOLLOW, 1001), msg("a try the next tick is not due"));
			helper.assertFalse(PlanetSites.due(owner, PlanetSites.Site.SINGING_HOLLOW, 1000 + retry - 1), msg("not due before the retry interval"));
			helper.assertTrue(PlanetSites.due(owner, PlanetSites.Site.DUSKGLASS_RIFT, 1001), msg("another site keeps its own retry time"));
			helper.assertTrue(PlanetSites.due(owner, PlanetSites.Site.SINGING_HOLLOW, 1000 + retry), msg("due again after the retry interval"));

			GlobalPos placed = PlanetSites.ensure(player, PlanetSites.Site.SINGING_HOLLOW, Planets.Planet.SELVAREN);
			helper.assertTrue(placed == null, msg("nothing can be placed without the planet's level and chunks, got " + placed));
			helper.assertTrue(PlanetSites.find(player, PlanetSites.Site.SINGING_HOLLOW) == null, msg("and nothing is recorded"));
		} finally {
			PlanetSites.forget(owner);
			PlanetSites.forget(player.getUUID());
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- waystones

	/**
	 * Cleansing a corrupted waystone is per player (8.4): the first cleanse for a player succeeds,
	 * a second for the same player does not, another player still has it corrupted until they cleanse
	 * it too; the stone stays corrupted in the world. A clean waystone cannot be cleansed.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void waystoneCleansePerPlayer(GameTestHelper helper) {
		BlockPos corruptedRel = new BlockPos(6, 1, 6);
		BlockPos cleanRel = new BlockPos(14, 1, 6);
		helper.setBlock(corruptedRel, PlanetBlocks.WAYSTONE_CORRUPTED.defaultBlockState().setValue(WaystoneBlock.FACING, Direction.NORTH));
		helper.setBlock(cleanRel, PlanetBlocks.WAYSTONE.defaultBlockState().setValue(WaystoneBlock.FACING, Direction.NORTH));
		BlockPos corrupted = helper.absolutePos(corruptedRel);
		BlockPos clean = helper.absolutePos(cleanRel);
		ServerPlayer first = M3TestPlayers.listed(helper, new BlockPos(6, 1, 4));
		ServerPlayer second = M3TestPlayers.listed(helper, new BlockPos(8, 1, 4));

		try {
			WaystoneBlockEntity stone = stone(helper, corrupted);
			helper.assertTrue(stone.corrupted(), msg("the corrupted waystone's block entity reports corrupted"));
			helper.assertTrue(Waystones.cleanse(first, corrupted), msg("the first player's cleanse succeeds"));
			helper.assertFalse(Waystones.cleanse(first, corrupted), msg("the same player cannot cleanse it twice"));
			helper.assertTrue(stone.cleansedFor(first.getUUID()) && !stone.cleansedFor(second.getUUID()),
					msg("cleansed for the first player only"));
			helper.assertTrue(helper.getBlockState(corruptedRel).is(PlanetBlocks.WAYSTONE_CORRUPTED), msg("the stone stays corrupted in the world"));
			helper.assertTrue(Waystones.cleanse(second, corrupted), msg("the second player can cleanse it for themself"));
			helper.assertTrue(stone.cleansedFor(second.getUUID()), msg("now cleansed for the second player too"));
			helper.assertFalse(Waystones.cleanse(first, clean), msg("a clean waystone cannot be cleansed"));
			helper.assertFalse(Heartstones.complete(first, clean), msg("a waystone is not the Heartstone"));
		} finally {
			M3TestPlayers.remove(first);
			M3TestPlayers.remove(second);
		}

		helper.succeed();
	}

	/**
	 * Whether a waystone is corrupted depends on its position only (seeded): the same answer every
	 * time, never at chance 0, always at chance 1, and about a quarter at the default 0.25.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void corruptedChanceSeededByPosition(GameTestHelper helper) {
		RandomSource random = RandomSource.create(905117L);
		int samples = 4000;
		int corrupted = 0;

		for (int i = 0; i < samples; i++) {
			BlockPos pos = new BlockPos(random.nextInt(60000) - 30000, 40 + random.nextInt(120), random.nextInt(60000) - 30000);
			boolean once = Waystones.corrupted(pos, 0.25);
			helper.assertTrue(once == Waystones.corrupted(pos.immutable(), 0.25), msg("the answer at " + pos.toShortString() + " changed"));
			helper.assertFalse(Waystones.corrupted(pos, 0), msg("chance 0 never corrupts"));
			helper.assertTrue(Waystones.corrupted(pos, 1), msg("chance 1 always corrupts"));

			if (once) {
				corrupted++;
			}
		}

		double share = corrupted / (double) samples;
		helper.assertTrue(share > 0.21 && share < 0.29, msg("about a quarter corrupted at 0.25, got " + share));
		helper.succeed();
	}

	// ---------------------------------------------------------------- sealed plating

	/**
	 * Sealed plating cannot be mined or blown open; a Pull does nothing; a Push opens the whole
	 * connected sheet and only it; the fusion cutter's {@link SealedPlatingBlock#cut} opens a sheet
	 * and returns 0 off plating.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void sealedPlatingOnlyByCutterOrPush(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> sheet = new ArrayList<>();

		for (int x = 6; x < 8; x++) {
			for (int y = 1; y < 3; y++) {
				BlockPos pos = new BlockPos(x, y, 8);
				helper.setBlock(pos, PlanetBlocks.SEALED_PLATING);
				sheet.add(pos);
			}
		}

		BlockPos lone = new BlockPos(14, 1, 8);
		helper.setBlock(lone, PlanetBlocks.SEALED_PLATING);
		BlockState plating = helper.getBlockState(lone);
		helper.assertTrue(plating.getDestroySpeed(level, helper.absolutePos(lone)) < 0, msg("sealed plating is unbreakable"));
		helper.assertTrue(plating.getBlock().getExplosionResistance() >= 1200f, msg("sealed plating resists explosions"));
		helper.assertTrue(plating.is(ForceBlockInteractions.REACTIVE), msg("sealed plating is #oldrepublic:force_reactive"));

		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(6, 1, 5));

		try {
			BlockPos first = helper.absolutePos(sheet.getFirst());
			helper.assertFalse(act(caster, first, ForceAct.PULL), msg("a Pull does not open plating"));
			helper.assertTrue(sheet.stream().allMatch(p -> helper.getBlockState(p).is(PlanetBlocks.SEALED_PLATING)), msg("the sheet is still closed"));
			helper.assertTrue(SealedPlatingBlock.sheet(level, first).size() == 4, msg("the sheet is the four connected cells"));
			helper.assertTrue(act(caster, first, ForceAct.PUSH), msg("a Push opens the plating"));
			helper.assertTrue(sheet.stream().allMatch(p -> helper.getBlockState(p).isAir()), msg("the whole sheet opened"));
			helper.assertTrue(helper.getBlockState(lone).is(PlanetBlocks.SEALED_PLATING), msg("a separate sheet stays closed"));
			helper.assertTrue(SealedPlatingBlock.cut(level, helper.absolutePos(lone)) == 1, msg("the cutter opens the lone plate"));
			helper.assertTrue(helper.getBlockState(lone).isAir(), msg("the lone plate is open"));
			helper.assertTrue(SealedPlatingBlock.cut(level, helper.absolutePos(lone)) == 0, msg("cutting air opens nothing"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- portals

	/**
	 * Nether portals light only in the overworld and the Nether (vanilla
	 * {@code BaseFireBlock#inPortalDimension}, 3.1): the same obsidian frame and fire make a portal
	 * in the overworld fixture and none in the End, which stands in for the planets (they do not exist
	 * on the game-test server and are refused by the same key check). Both builds are removed.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void vanillaRefusesPortalsOnPlanets(GameTestHelper helper) {
		ServerLevel overworld = helper.getLevel();
		ServerLevel end = overworld.getServer().getLevel(Level.END);
		helper.assertTrue(end != null, msg("the game-test server has the End"));
		BlockPos here = helper.absolutePos(new BlockPos(10, 1, 12));
		BlockPos there = new BlockPos(1000, 200, 1000);
		end.getChunk(there);
		List<BlockPos> builtHere = new ArrayList<>();
		List<BlockPos> builtThere = new ArrayList<>();

		try {
			helper.assertTrue(portalLights(overworld, here, builtHere), msg("the frame must light in the overworld (test setup)"));
			helper.assertFalse(portalLights(end, there, builtThere), msg("a portal must not light outside the overworld and the Nether"));
		} finally {
			builtHere.forEach(pos -> overworld.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
			builtThere.forEach(pos -> end.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
		}

		helper.succeed();
	}

	/**
	 * Builds a 4 by 5 obsidian frame along x with its bottom-left corner at {@code corner}, lights
	 * fire in it and says whether a portal formed; records every cell it touched in {@code built}.
	 */
	private static boolean portalLights(ServerLevel level, BlockPos corner, List<BlockPos> built) {
		for (int dx = 0; dx < 4; dx++) {
			for (int dy = 0; dy < 5; dy++) {
				BlockPos pos = corner.offset(dx, dy, 0);
				boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
				level.setBlock(pos, frame ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				built.add(pos);
			}
		}

		BlockPos fire = corner.offset(1, 1, 0);
		level.setBlock(fire, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
		return level.getBlockState(fire).is(Blocks.NETHER_PORTAL) || level.getBlockState(corner.offset(2, 2, 0)).is(Blocks.NETHER_PORTAL);
	}

	// ---------------------------------------------------------------- templates

	/**
	 * Every M4 template of W1 loads with its size, has a sidecar naming each of its anchors with
	 * every cell inside the size, and the waystones' and the Heartstone's {@code STONE} cells hold the
	 * right stone. Loads the templates; never places one.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void everyM4TemplateHasSidecarAnchorsInsideSize(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, Block> stones = Map.of("planet/waystone", PlanetBlocks.WAYSTONE, "planet/waystone_corrupted", PlanetBlocks.WAYSTONE_CORRUPTED,
				"planet/heartstone", PlanetBlocks.HEARTSTONE);

		for (M4Template expected : TEMPLATES) {
			Identifier id = OldRepublicMod.id(expected.path());
			StructureTemplate template = level.getStructureManager().get(id).orElse(null);
			helper.assertTrue(template != null, msg("missing structure template " + id));
			Vec3i size = template.getSize();
			helper.assertTrue(size.equals(expected.size()), msg(id + " has size " + size + ", expected " + expected.size()));
			helper.assertTrue(SiteAnchors.templates().contains(id), msg(id + " has no sidecar"));

			for (String name : expected.anchors()) {
				SiteAnchors.Anchor anchor = SiteAnchors.Anchor.byName(name);
				helper.assertTrue(anchor != null, msg("unknown anchor " + name));
				helper.assertFalse(SiteAnchors.of(id, anchor).isEmpty(), msg(id + " does not name " + name));
			}

			for (SiteAnchors.Anchor anchor : SiteAnchors.Anchor.values()) {
				for (BlockPos cell : SiteAnchors.of(id, anchor)) {
					helper.assertTrue(cell.getX() >= 0 && cell.getY() >= 0 && cell.getZ() >= 0 && cell.getX() < size.getX() && cell.getY() < size.getY()
							&& cell.getZ() < size.getZ(), msg(id + " " + anchor + " " + cell.toShortString() + " lies outside " + size));
				}
			}

			Block stone = stones.get(expected.path());

			if (stone != null) {
				BlockPos cell = SiteAnchors.of(id, SiteAnchors.Anchor.STONE).getFirst();
				// With BlockPos.ZERO and default settings, filterBlocks gives template-local positions.
				boolean found = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), stone).stream().anyMatch(info -> info.pos().equals(cell));
				helper.assertTrue(found, msg(id + " STONE " + cell.toShortString() + " must hold " + stone));
			}
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	private static WaystoneBlockEntity stone(GameTestHelper helper, BlockPos absolute) {
		if (helper.getLevel().getBlockEntity(absolute) instanceof WaystoneBlockEntity stone) {
			return stone;
		}

		throw helper.assertionException(msg("no waystone block entity at " + absolute.toShortString()));
	}

	/** A Force act of {@code act} by {@code caster} on the block at the absolute {@code pos}, as a power dispatches it. */
	private static boolean act(ServerPlayer caster, BlockPos pos, ForceAct act) {
		ServerLevel level = caster.level();
		String ability = act == ForceAct.PULL ? ForcePull.ID : ForcePush.ABILITY_ID;
		return ForceBlockInteractions.dispatch(new ForceBlockContext(caster, level, pos, level.getBlockState(pos), act, new Vec3(0, 0, 1), 1f, ability));
	}

	private static <T> List<T> concat(List<T> a, List<T> b) {
		List<T> out = new ArrayList<>(a);
		out.addAll(b);
		return List.copyOf(out);
	}

	private static Component msg(String text) {
		return Component.literal(text);
	}
}

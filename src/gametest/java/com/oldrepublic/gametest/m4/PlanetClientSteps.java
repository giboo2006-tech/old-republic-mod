package com.oldrepublic.gametest.m4;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSiteSearch;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.Waystones;
import com.oldrepublic.planet.block.WaystoneBlock;
import com.oldrepublic.planet.block.WaystoneBlockEntity;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 client test steps for planets, sites and hubs (W1). {@code M4ClientTest} runs them in
 * the shared seeded world (spec MILESTONE_4.md 17.2, with the {@code PlanetsClientTest} assertions).
 * Screenshots are {@code oldrepublic_m4_<nn>_<what>} with {@code nn} from 10 to 19.
 *
 * <p><b>Getting there.</b> Both hubs are placed first ({@link PlanetHubs#ensure}, the same call a
 * landing makes) and the player crosses to each planet's first pad bay with
 * {@link TravelService#moveAcross}, the only cross-level move; every other move is a same-level
 * {@code execute in <planet> run tp}. Steps run in creative mode, flying, first person.
 *
 * <p><b>Assertions</b>, on the server:
 * <ul>
 * <li>each of the six biomes is found within {@link #BIOME_SEARCH} blocks of its planet's hub
 * ({@code findClosestBiome3d}; deterministic for the seed; the camera column is checked to be in
 * the biome, else a warning is logged);</li>
 * <li>a waystone (Selvaren) and a greater tomb (Varkhast) placed with {@code /oldrepublic site place}
 * (the worldgen code path) have exactly their template pieces, every sidecar anchor cell lies in
 * its piece's box, holds no stray terrain, and {@code STONE} holds a waystone and
 * {@code DOOR_SEAL} sealed plating;</li>
 * <li>the worldgen waystone decides its corruption by position ({@link Waystones#corruptedAt}) on
 * its first tick;</li>
 * <li>each hub is recorded as a {@code planet_hub} piece; each expedition is placed by
 * {@link PlanetSites#ensure} near its ring point within {@link #SITE_TIMEOUT} ticks, with its
 * underground piece; when it is not, the failure carries {@link PlanetSiteSearch#explain} (every
 * ring candidate and the check that refused it, and the clearing search's refusals).</li>
 * </ul>
 *
 * <p><b>Screenshots:</b> 10 {@code <biome>_noon} and 11 {@code <biome>_dusk} for the six biomes
 * (looking west, so the dusk shot faces the sunset; Selvaren's dusk is among them); 12
 * {@code varkhast_ashfall_storm} (the ashfall during the planet's storm, the rain flag); 13
 * {@code waystone_clean}; 14 {@code waystone_corrupted}; 15 {@code greater_tomb_gate}; 16
 * {@code greenwatch_from_pad}; 17 {@code proving_from_pad}; 18 {@code singing_hollow} (the grotto,
 * with night vision); 19 {@code duskglass_rift} (from a ledge down to the crystal, with night vision).
 *
 * <p>Side effects, on the planets only: both hubs, the two expeditions (owned by the test player)
 * and the placed waystones and tomb stay in the world. The player's level, position, game mode
 * (survival, as the driver sets it), night vision, time (noon), weather (clear), the planets'
 * force-loaded chunks and the camera are put back; the driver's resistance and saturation are kept.
 */
public final class PlanetClientSteps {
	/** Ticks a planet's hub gets to generate its area and place itself. */
	private static final int HUB_TIMEOUT = 3600;
	/** Ticks a force-loaded area gets to generate and load. */
	private static final int LOAD_TIMEOUT = 2400;
	/** Ticks the client gets to receive a far area after a teleport. */
	private static final int CLIENT_CHUNK_TIMEOUT = 600;
	/**
	 * Ticks an expedition gets to be placed with the player at its ring point (a try every {@code planets.siteRetryTicks}): far
	 * placement finds and loads its ground first (docs/MILESTONE_6.md 3.1; {@code sites.far.loadTimeoutTicks} 2400).
	 */
	private static final int SITE_TIMEOUT = 3600;
	/** Ticks a worldgen waystone gets to decide its corruption (its block entity runs every 20 ticks). */
	private static final int DECIDE_TIMEOUT = 200;
	/** Ticks for the view to settle (chunk meshes, particles) after a move within an area. */
	private static final int SETTLE = 20;
	/** Ticks for the view to settle after a teleport to another area. */
	private static final int ARRIVE = 60;
	/** Extra ticks a landscape gets to draw its far chunks before its sky shots. */
	private static final int VISTA = 60;
	/** Ticks the sky and the light get after a time change. */
	private static final int SKY = 20;
	/** Ticks the storm gets to set in (the rain level climbs by 0.01 a tick) and the flakes to thicken. */
	private static final int STORM = 120;
	/** Ticks for a corrupted stone's loop refresh (every 40) and its vein pulse. */
	private static final int PULSE = 60;
	/** The dusk time of day: the sun low in the west. */
	private static final int DUSK = 12600;

	/** How far from the hub a biome is looked for, and the search's steps. */
	private static final int BIOME_SEARCH = 2400;
	private static final int BIOME_STEP = 32;
	private static final int BIOME_STEP_Y = 64;
	/** How far past the nearest biome point (away from the hub) the camera tries to stand. */
	private static final int BIOME_INSET = 32;
	/** Landscape camera: height over the ground (leaves count), reach and rise of the look. */
	private static final int VISTA_RISE = 6;
	private static final int VISTA_REACH = 48;
	private static final int VISTA_TILT = 6;
	/** Pad camera: height over the pad bay. */
	private static final int PAD_RISE = 5;

	/** The nearest distance of the placed waystones and tomb from the hub, in blocks. */
	private static final int RING = 128;
	private static final int TOMB_RING = 192;
	/** Spots tried per bearing for dry ground; each further one is 13 degrees on and 48 blocks out. */
	private static final int CANDIDATES = 6;
	/** Sample columns of a spot (the spot and 12 blocks each way) that must end on dry ground. */
	private static final int[][] SAMPLES = {{0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}};
	/** Columns tried, one block east each, for a clean waystone stand. */
	private static final int CLEAN_TRIES = 64;
	/** Half-size of the force-loaded square around a template stand and around an expedition's ring point. */
	private static final int TEMPLATE_LOAD = 16;
	private static final int EXPEDITION_LOAD = 112;

	/**
	 * One worldgen structure to place with {@code /oldrepublic site place}: the command argument,
	 * its kind, its start template, the template pieces every placement has, and how far its pieces
	 * reach from the start chunk's corner.
	 */
	private record Plan(String command, SiteKind kind, Identifier start, Map<Identifier, Integer> pieces, int reach) {
	}

	/** A placed structure: its plan and every piece found there, start first. */
	private record Placed(Plan plan, List<SitePiece> pieces) {
		SitePiece piece(Identifier template) {
			for (SitePiece piece : pieces) {
				if (piece.template().equals(template)) {
					return piece;
				}
			}

			throw new AssertionError(plan.command() + ": no " + template + " piece");
		}
	}

	private static final Plan WAYSTONE = new Plan("waystone", SiteKind.WAYSTONE, Waystones.CLEAN_TEMPLATE, Map.of(Waystones.CLEAN_TEMPLATE, 1), 24);
	private static final Identifier TOMB_GATE = OldRepublicMod.id("greater_tomb/gate");
	private static final Plan GREATER_TOMB = new Plan("greater_tomb", SiteKind.GREATER_TOMB, TOMB_GATE, Map.of(
			TOMB_GATE, 1,
			OldRepublicMod.id("greater_tomb/descent"), 1,
			OldRepublicMod.id("greater_tomb/urn_vault"), 1,
			OldRepublicMod.id("greater_tomb/sealed_crypt"), 1), 96);

	public static void run(ClientGameTestContext context, TestServerContext server) {
		double[] origin = new double[5];
		AtomicReference<ResourceKey<Level>> home = new AtomicReference<>(Level.OVERWORLD);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			origin[0] = p.getX();
			origin[1] = p.getY();
			origin[2] = p.getZ();
			origin[3] = p.getYRot();
			origin[4] = p.getXRot();
			home.set(p.level().dimension());
		});
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		try {
			server.runCommand("gamemode creative @p");
			// Both hubs generate while the Selvaren steps run.
			server.runOnServer(s -> {
				PlanetHubs.ensure(s, Planets.Planet.SELVAREN);
				PlanetHubs.ensure(s, Planets.Planet.VARKHAST);
			});
			selvaren(context, server);
			varkhast(context, server);
		} finally {
			// Put back what can be put back: level and position (the one cross-level move), game mode, night vision, time, weather, force-loading, camera.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel level = s.getLevel(home.get());
				Vec3 back = new Vec3(origin[0], origin[1], origin[2]);

				if (level != null && !p.level().dimension().equals(home.get())) {
					TravelService.moveAcross(p, level, back, (float) origin[3]);
				}
			});
			server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @p %.3f %.3f %.3f %.1f %.1f", home.get().identifier(), origin[0], origin[1], origin[2],
					origin[3], origin[4]));
			server.runCommand("gamemode survival @p");
			server.runCommand("effect clear @p minecraft:night_vision");
			server.runCommand("time set noon");
			server.runCommand("weather clear");

			for (Planets.Planet planet : List.of(Planets.Planet.SELVAREN, Planets.Planet.VARKHAST)) {
				in(server, planet, "forceload remove all");
			}

			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(ARRIVE);
		}
	}

	// ---------------------------------------------------------------- the planets

	/** Selvaren: the Greenwatch from its pad, the three biomes, the waystones and the Singing Hollow. */
	private static void selvaren(ClientGameTestContext context, TestServerContext server) {
		Planets.Planet planet = Planets.Planet.SELVAREN;
		SitePiece hub = arriveAtHub(context, server, planet);
		fromPad(context, server, planet, hub, "oldrepublic_m4_16_greenwatch_from_pad");
		BlockPos centre = hubCentre(server, planet);

		for (ResourceKey<Biome> biome : List.of(PlanetWorldgen.SELVAREN_HIGHWOOD, PlanetWorldgen.SELVAREN_MEADOW, PlanetWorldgen.SELVAREN_TARN)) {
			biome(context, server, planet, biome, centre);
		}

		waystones(context, server, centre);
		expedition(context, server, planet, PlanetSites.Site.SINGING_HOLLOW, PlanetSites.HOLLOW_GROTTO.template(), new BlockPos(9, 6, 6),
				new BlockPos(17, 3, 19), "oldrepublic_m4_18_singing_hollow");
	}

	/** Varkhast: the Proving from its pad, the three biomes (and the ashfall storm), a greater tomb and the duskglass rift. */
	private static void varkhast(ClientGameTestContext context, TestServerContext server) {
		Planets.Planet planet = Planets.Planet.VARKHAST;
		SitePiece hub = arriveAtHub(context, server, planet);
		fromPad(context, server, planet, hub, "oldrepublic_m4_17_proving_from_pad");
		BlockPos centre = hubCentre(server, planet);
		biome(context, server, planet, PlanetWorldgen.VARKHAST_REDROCK, centre);
		biome(context, server, planet, PlanetWorldgen.VARKHAST_ASHFALL, centre);
		// 12: the same view while the planet's storm (the rain flag, which never wets Varkhast) thickens the ashfall.
		server.runCommand("weather rain");
		context.waitTicks(STORM);
		context.takeScreenshot("oldrepublic_m4_12_varkhast_ashfall_storm");
		server.runCommand("weather clear");
		biome(context, server, planet, PlanetWorldgen.VARKHAST_TOMB_VALLEY, centre);

		Placed tomb = placeStructure(server, planet, GREATER_TOMB, 0, centre, TOMB_RING);
		SitePiece gate = tomb.piece(TOMB_GATE);
		arrive(context, server, planet, feet(gate, 10, 7, -4), centre(gate, 10, 3, 10), true);
		context.takeScreenshot("oldrepublic_m4_15_greater_tomb_gate");

		expedition(context, server, planet, PlanetSites.Site.DUSKGLASS_RIFT, PlanetSites.RIFT.template(), new BlockPos(6, 13, 12), null,
				"oldrepublic_m4_19_duskglass_rift");
	}

	// ---------------------------------------------------------------- hubs

	/**
	 * Waits for the planet's hub, crosses to its first pad bay with {@link TravelService#moveAcross}
	 * and returns the hub's piece (it must be recorded as a {@code planet_hub} piece).
	 */
	private static SitePiece arriveAtHub(ClientGameTestContext context, TestServerContext server, Planets.Planet planet) {
		server.waitFor(s -> PlanetHubs.hub(s, planet) != null, HUB_TIMEOUT);
		Vec3 pad = server.computeOnServer(s -> {
			List<BlockPos> bays = PlanetHubs.padBays(s, planet);
			BlockPos at = bays.isEmpty() ? PlanetHubs.hub(s, planet) : bays.getFirst();
			Vec3 stand = Vec3.atBottomCenterOf(at);
			ServerPlayer p = player(s);
			TravelService.moveAcross(p, level(s, planet), stand, p.getYRot());
			return stand;
		});
		waitForClient(context, planet, pad);
		SitePiece hub = server.computeOnServer(s -> {
			ServerLevel level = level(s, planet);
			BlockPos centre = PlanetHubs.hub(s, planet);

			for (SitePiece piece : StructureLocator.piecesNear(level, centre, 8)) {
				if (piece.kind() == SiteKind.PLANET_HUB && piece.template().equals(PlanetHubs.template(planet))) {
					return piece;
				}
			}

			return null;
		});

		if (hub == null) {
			throw new AssertionError(planet.id() + ": the hub is placed but no " + PlanetHubs.template(planet) + " piece is recorded at it");
		}

		OldRepublicMod.LOGGER.info("Planet client step: {} hub at {} ({} {})", planet.id(), hub.origin().toShortString(), hub.template(), hub.rotation());
		return hub;
	}

	/** 16 and 17: the hub from above its first pad bay, looking across the town to its host. */
	private static void fromPad(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, SitePiece hub, String screenshot) {
		BlockPos bay = anchor(hub, SiteAnchors.Anchor.PAD_BAY_1);
		BlockPos host = anchor(hub, SiteAnchors.Anchor.HOST);
		look(context, server, planet, Vec3.atBottomCenterOf(bay).add(0, PAD_RISE, 0), Vec3.atCenterOf(host).add(0, 3, 0));
		context.takeScreenshot(screenshot);
	}

	private static BlockPos hubCentre(TestServerContext server, Planets.Planet planet) {
		return server.computeOnServer(s -> PlanetHubs.hub(s, planet));
	}

	// ---------------------------------------------------------------- biomes

	/**
	 * 10 and 11: the nearest {@code biome} to the hub (which must exist within
	 * {@link #BIOME_SEARCH}), from {@link #VISTA_RISE} blocks over its ground looking west, at noon
	 * and at dusk. Leaves the time at noon.
	 */
	private static void biome(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, ResourceKey<Biome> biome, BlockPos hub) {
		Vec3 stand = server.computeOnServer(s -> biomeStand(level(s, planet), biome, hub));

		if (stand == null) {
			throw new AssertionError(planet.id() + ": no " + biome.identifier() + " within " + BIOME_SEARCH + " blocks of the hub at " + hub.toShortString());
		}

		String name = biome.identifier().getPath();
		server.runCommand("time set noon");
		arrive(context, server, planet, stand, stand.add(-VISTA_REACH, VISTA_TILT, 0), false);
		context.waitTicks(VISTA);
		context.takeScreenshot("oldrepublic_m4_10_" + name + "_noon");
		server.runCommand("time set " + DUSK);
		context.waitTicks(SKY);
		context.takeScreenshot("oldrepublic_m4_11_" + name + "_dusk");
		server.runCommand("time set noon");
		context.waitTicks(SKY);
	}

	/**
	 * Where the camera stands for {@code biome}: over the nearest point of it to {@code hub}, moved
	 * up to {@link #BIOME_INSET} blocks further from the hub while the column's surface is still in
	 * the biome; null when the biome is not found. Loads (and if needed generates) the columns tried.
	 */
	private static @Nullable Vec3 biomeStand(ServerLevel level, ResourceKey<Biome> biome, BlockPos hub) {
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(biome), hub, BIOME_SEARCH, BIOME_STEP, BIOME_STEP_Y);

		if (found == null) {
			return null;
		}

		BlockPos at = found.getFirst();
		Vec3 away = new Vec3(at.getX() - hub.getX(), 0, at.getZ() - hub.getZ());
		away = away.lengthSqr() < 1 ? Vec3.ZERO : away.normalize();

		for (int inset = BIOME_INSET; inset >= 0; inset -= BIOME_INSET / 2) {
			int x = Mth.floor(at.getX() + away.x * inset);
			int z = Mth.floor(at.getZ() + away.z * inset);
			int ground = surface(level, x, z);

			if (level.getBiome(new BlockPos(x, ground, z)).is(biome)) {
				OldRepublicMod.LOGGER.info("Planet client step: {} at {} {} {}", biome.identifier(), x, ground, z);
				return clear(level, new Vec3(x + 0.5, ground, z + 0.5)).add(0, VISTA_RISE, 0);
			}
		}

		OldRepublicMod.LOGGER.warn("Planet client step: {} found at {} but its surface there is another biome; the shot stands there anyway", biome.identifier(),
				at.toShortString());
		return clear(level, new Vec3(at.getX() + 0.5, surface(level, at.getX(), at.getZ()), at.getZ() + 0.5)).add(0, VISTA_RISE, 0);
	}

	// ---------------------------------------------------------------- waystones

	/**
	 * The worldgen waystone (placed and checked, then its corruption decided by position), and 13
	 * and 14: a clean stone and a corrupted stone, each from 6 blocks in front of it. The clean shot
	 * uses the clean template on a stand whose stone is clean by position; the corrupted shot the
	 * corrupted template.
	 */
	private static void waystones(ClientGameTestContext context, TestServerContext server, BlockPos hub) {
		Planets.Planet planet = Planets.Planet.SELVAREN;
		Placed worldgen = placeStructure(server, planet, WAYSTONE, 0, hub, RING);
		BlockPos stone = anchor(worldgen.piece(Waystones.CLEAN_TEMPLATE), SiteAnchors.Anchor.STONE);
		server.waitFor(s -> level(s, planet).getBlockEntity(stone) instanceof WaystoneBlockEntity entity && !entity.pending(), DECIDE_TIMEOUT);
		String decided = server.computeOnServer(s -> {
			BlockState state = level(s, planet).getBlockState(stone);
			boolean corrupted = state.getBlock() instanceof WaystoneBlock block && block.corrupted();
			return corrupted == Waystones.corruptedAt(stone) ? null : "the worldgen waystone at " + stone.toShortString() + " is " + state
					+ ", but its position says corrupted=" + Waystones.corruptedAt(stone);
		});

		if (decided != null) {
			throw new AssertionError(decided);
		}

		BlockPos cleanStand = server.computeOnServer(s -> cleanStand(level(s, planet), spot(level(s, planet), 1, hub, RING)));
		SitePiece clean = placeTemplate(server, planet, Waystones.CLEAN_TEMPLATE, cleanStand);
		BlockPos cleanStone = anchor(clean, SiteAnchors.Anchor.STONE);
		arrive(context, server, planet, feet(clean, 10, 2, 3), Vec3.atCenterOf(cleanStone).add(0, 0.5, 0), true);
		boolean stillClean = server.computeOnServer(s -> level(s, planet).getBlockState(cleanStone).is(PlanetBlocks.WAYSTONE));

		if (!stillClean) {
			throw new AssertionError("the clean waystone at " + cleanStone.toShortString() + " turned corrupted although its position says clean");
		}

		context.takeScreenshot("oldrepublic_m4_13_waystone_clean");

		BlockPos corruptedStand = server.computeOnServer(s -> spot(level(s, planet), 2, hub, RING));
		SitePiece corrupted = placeTemplate(server, planet, Waystones.CORRUPTED_TEMPLATE, corruptedStand);
		BlockPos corruptedStone = anchor(corrupted, SiteAnchors.Anchor.STONE);
		arrive(context, server, planet, feet(corrupted, 10, 2, 3), Vec3.atCenterOf(corruptedStone).add(0, 0.5, 0), true);
		// The corrupt loop's refresh reaches the player and the veins pulse.
		context.waitTicks(PULSE);
		context.takeScreenshot("oldrepublic_m4_14_waystone_corrupted");
	}

	/**
	 * A stand near {@code spot} (moving east one block at a time) where the clean waystone template,
	 * placed as the command places it, puts its stone on a position that stays clean.
	 */
	private static BlockPos cleanStand(ServerLevel level, BlockPos spot) {
		StructureTemplate template = level.getStructureManager().get(Waystones.CLEAN_TEMPLATE).orElse(null);
		List<BlockPos> stones = SiteAnchors.of(Waystones.CLEAN_TEMPLATE, SiteAnchors.Anchor.STONE);

		if (template == null || stones.isEmpty()) {
			throw new AssertionError("the clean waystone template or its STONE anchor is missing");
		}

		for (int i = 0; i < CLEAN_TRIES; i++) {
			int x = spot.getX() + i;
			BlockPos stand = new BlockPos(x, surface(level, x, spot.getZ()), spot.getZ());
			BlockPos stone = RuntimeSites.templateOrigin(template, stand, Rotation.NONE, Waystones.BURIED).offset(stones.getFirst());

			if (!Waystones.corruptedAt(stone)) {
				return stand;
			}
		}

		throw new AssertionError("no clean waystone stand within " + CLEAN_TRIES + " blocks east of " + spot.toShortString());
	}

	/**
	 * Places a site template with {@code /oldrepublic site place <template> <stand>} in the planet
	 * (force-loading its area first) and returns the runtime piece it recorded.
	 */
	private static SitePiece placeTemplate(TestServerContext server, Planets.Planet planet, Identifier template, BlockPos stand) {
		forceload(server, planet, stand.getX(), stand.getZ(), TEMPLATE_LOAD);
		server.waitFor(s -> loaded(level(s, planet), stand.getX(), stand.getZ(), TEMPLATE_LOAD), LOAD_TIMEOUT);
		in(server, planet, String.format(Locale.ROOT, "oldrepublic site place %s %d %d %d", template, stand.getX(), stand.getY(), stand.getZ()));
		SitePiece piece = server.computeOnServer(s -> {
			for (SitePiece candidate : StructureLocator.piecesNear(level(s, planet), stand, 8)) {
				if (candidate.runtime() && candidate.template().equals(template)) {
					return candidate;
				}
			}

			return null;
		});

		if (piece == null) {
			throw new AssertionError(template + ": nothing was placed at " + stand.toShortString() + " (see the command's reply in the log)");
		}

		OldRepublicMod.LOGGER.info("Planet client step: placed {} at {}", template, piece.origin().toShortString());
		return piece;
	}

	// ---------------------------------------------------------------- worldgen structures

	/**
	 * Places one worldgen structure on its slot's spot with {@code /oldrepublic site place} and
	 * checks it: force-loads the area, runs the command in the planet, finds the pieces and checks
	 * their templates and anchor cells. Fails the test when anything is off.
	 */
	private static Placed placeStructure(TestServerContext server, Planets.Planet planet, Plan plan, int slot, BlockPos hub, int ring) {
		BlockPos spot = server.computeOnServer(s -> spot(level(s, planet), slot, hub, ring));
		// The pieces reach out from the start chunk's corner.
		int cx = SectionPos.blockToSectionCoord(spot.getX()) << 4;
		int cz = SectionPos.blockToSectionCoord(spot.getZ()) << 4;
		forceload(server, planet, cx, cz, plan.reach());
		server.waitFor(s -> loaded(level(s, planet), cx, cz, plan.reach()), LOAD_TIMEOUT);
		in(server, planet, String.format(Locale.ROOT, "oldrepublic site place %s %d %d %d", plan.command(), spot.getX(), spot.getY(), spot.getZ()));

		BlockPos near = new BlockPos(cx, spot.getY(), cz);
		List<SitePiece> pieces = server.computeOnServer(s -> pieces(level(s, planet), plan, near));

		if (pieces == null) {
			throw new AssertionError(plan.command() + ": nothing was placed at " + spot.toShortString() + " (see the command's reply in the log)");
		}

		Placed placed = new Placed(plan, pieces);
		String failure = server.computeOnServer(s -> check(level(s, planet), placed));

		if (failure != null) {
			throw new AssertionError(plan.command() + " at " + spot.toShortString() + ": " + failure);
		}

		OldRepublicMod.LOGGER.info("Planet client step: placed {} at {} in {} ({} pieces, start {} {})", plan.command(), spot.toShortString(), planet.id(),
				pieces.size(), pieces.getFirst().origin().toShortString(), pieces.getFirst().rotation());
		return placed;
	}

	/**
	 * The spot of slot {@code slot} on a ring {@code ring} blocks or more around the hub (bearing
	 * {@code 120 * slot} degrees): the first of {@link #CANDIDATES} points whose {@link #SAMPLES}
	 * columns all end on dry ground, else the driest, at its surface height.
	 */
	private static BlockPos spot(ServerLevel level, int slot, BlockPos hub, int ring) {
		BlockPos best = null;
		int bestDry = -1;

		for (int k = 0; k < CANDIDATES && bestDry < SAMPLES.length; k++) {
			double bearing = Math.toRadians(slot * 120.0 + k * 13.0);
			int radius = ring + k * 48;
			int x = hub.getX() + Mth.floor(Math.cos(bearing) * radius);
			int z = hub.getZ() + Mth.floor(Math.sin(bearing) * radius);
			int dry = 0;

			for (int[] sample : SAMPLES) {
				if (dry(level, x + sample[0], z + sample[1])) {
					dry++;
				}
			}

			if (dry > bestDry) {
				bestDry = dry;
				best = new BlockPos(x, surface(level, x, z), z);
			}
		}

		return best;
	}

	/** Every piece of the site whose start template has a piece within 8 blocks of {@code near}, start first; null when there is none. */
	private static @Nullable List<SitePiece> pieces(ServerLevel level, Plan plan, BlockPos near) {
		for (SitePiece piece : StructureLocator.piecesNear(level, near, 8)) {
			if (piece.kind() == plan.kind() && piece.template().equals(plan.start())) {
				return List.copyOf(StructureLocator.sitePieces(piece));
			}
		}

		return null;
	}

	/**
	 * Why the placed structure is wrong, or null: the template pieces must be exactly the plan's;
	 * every anchor cell must lie in its piece's box and hold no stray terrain; {@code STONE} must hold
	 * a waystone and {@code DOOR_SEAL} sealed plating.
	 */
	private static @Nullable String check(ServerLevel level, Placed placed) {
		Map<Identifier, Integer> counts = new HashMap<>();

		for (SitePiece piece : placed.pieces()) {
			counts.merge(piece.template(), 1, Integer::sum);
		}

		if (!counts.equals(placed.plan().pieces())) {
			return "pieces " + counts + ", expected " + placed.plan().pieces();
		}

		for (SitePiece piece : placed.pieces()) {
			for (SiteAnchors.Anchor anchor : SiteAnchors.Anchor.values()) {
				for (BlockPos cell : SiteAnchors.all(piece, anchor)) {
					BlockState state = level.getBlockState(cell);
					String where = piece.template() + " " + anchor + " at " + cell.toShortString();

					if (!piece.box().isInside(cell)) {
						return where + " lies outside its piece " + piece.box();
					} else if (anchor == SiteAnchors.Anchor.STONE && !(state.getBlock() instanceof WaystoneBlock)) {
						return where + " holds " + state + ", not a waystone";
					} else if (anchor == SiteAnchors.Anchor.DOOR_SEAL && !state.is(PlanetBlocks.SEALED_PLATING)) {
						return where + " holds " + state + ", not sealed plating";
					} else if (strayTerrain(state)) {
						return where + " holds stray terrain " + state;
					}
				}
			}
		}

		return null;
	}

	/** Natural ground or rock of either planet, foliage or a fluid: nothing an anchor cell should hold. */
	private static boolean strayTerrain(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA)
				|| state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.SMOOTH_BASALT) || !state.getFluidState().isEmpty();
	}

	// ---------------------------------------------------------------- expeditions

	/**
	 * 18 and 19: stands over the expedition's ring point (force-loaded), waits for
	 * {@link PlanetSites#ensure} to place it, finds its underground piece and takes the shot from
	 * {@code standLocal} in it towards {@code targetLocal} (or its {@code RELIC}), with night vision.
	 */
	private static void expedition(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, PlanetSites.Site site, Identifier deep,
			BlockPos standLocal, @Nullable BlockPos targetLocal, String screenshot) {
		UUID owner = server.computeOnServer(s -> player(s).getUUID());
		GlobalPos ring = server.computeOnServer(s -> PlanetSites.ringPoint(s, owner, site, planet));

		if (ring == null) {
			throw new AssertionError(site + ": no ring point (is the hub placed?)");
		}

		BlockPos point = ring.pos();
		forceload(server, planet, point.getX(), point.getZ(), EXPEDITION_LOAD);
		server.waitFor(s -> loaded(level(s, planet), point.getX(), point.getZ(), EXPEDITION_LOAD), LOAD_TIMEOUT);
		Vec3 over = server.computeOnServer(s -> clear(level(s, planet), Vec3.atBottomCenterOf(point)).add(0, 4, 0));
		arrive(context, server, planet, over, over.add(8, -2, 0), false);

		GlobalPos[] placed = new GlobalPos[1];

		try {
			server.waitFor(s -> (placed[0] = PlanetSites.ensure(player(s), site, planet)) != null, SITE_TIMEOUT);
		} catch (AssertionError timedOut) {
			String why = server.computeOnServer(s -> PlanetSiteSearch.explain(player(s), site, planet));
			OldRepublicMod.LOGGER.error("Planet client step: {} was not placed near {} within {} ticks:\n{}", site, point.toShortString(), SITE_TIMEOUT, why);
			throw new AssertionError(site + " was not placed near " + point.toShortString() + " within " + SITE_TIMEOUT + " ticks:\n" + why, timedOut);
		}

		SitePiece piece = server.computeOnServer(s -> {
			for (SitePiece candidate : StructureLocator.piecesNear(level(s, planet), placed[0].pos(), 48)) {
				if (candidate.kind() == SiteKind.EXPEDITION && candidate.template().equals(deep)) {
					return candidate;
				}
			}

			return null;
		});

		if (piece == null) {
			throw new AssertionError(site + " was placed at " + placed[0].pos().toShortString() + " without its " + deep + " piece");
		}

		OldRepublicMod.LOGGER.info("Planet client step: {} at {}, {} at {}", site, placed[0].pos().toShortString(), deep, piece.origin().toShortString());
		BlockPos target = targetLocal != null ? SiteAnchors.toWorld(piece, targetLocal) : anchor(piece, SiteAnchors.Anchor.RELIC);
		// The grotto and the rift are lit by their crystals only: night vision so the review can see them.
		server.runCommand("effect give @p minecraft:night_vision infinite 0 true");

		try {
			arrive(context, server, planet, feet(piece, standLocal.getX(), standLocal.getY(), standLocal.getZ()), Vec3.atCenterOf(target), false);
			context.takeScreenshot(screenshot);
		} finally {
			server.runCommand("effect clear @p minecraft:night_vision");
		}
	}

	// ---------------------------------------------------------------- camera

	/** Teleports within the planet to another area and waits until the client has it and has drawn it. */
	private static void arrive(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, Vec3 feet, Vec3 target, boolean aboveGround) {
		Vec3 stand = teleport(server, planet, feet, target, aboveGround);
		waitForClient(context, planet, stand);
	}

	/** Moves within an area and lets the view settle. */
	private static void look(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, Vec3 feet, Vec3 target) {
		teleport(server, planet, feet, target, false);
		context.waitTicks(SETTLE);
	}

	/** Waits until the client is in the planet with the chunk of {@code at}, then lets the view settle. */
	private static void waitForClient(ClientGameTestContext context, Planets.Planet planet, Vec3 at) {
		int chunkX = SectionPos.blockToSectionCoord(Mth.floor(at.x));
		int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(at.z));
		context.waitFor(client -> client.level != null && client.level.dimension().equals(planet.level()) && client.level.hasChunk(chunkX, chunkZ),
				CLIENT_CHUNK_TIMEOUT);
		context.waitTicks(ARRIVE);
	}

	/**
	 * Puts the player's feet at {@code feet} in the planet the player is in (raised to just above
	 * the ground of that column when {@code aboveGround}), looking at {@code target}, flying so they
	 * hold still. A same-level teleport only. Returns the stand.
	 */
	private static Vec3 teleport(TestServerContext server, Planets.Planet planet, Vec3 feet, Vec3 target, boolean aboveGround) {
		Vec3 stand = aboveGround ? server.computeOnServer(s -> clear(level(s, planet), feet)) : feet;
		in(server, planet, String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f facing %.3f %.3f %.3f", stand.x, stand.y, stand.z, target.x, target.y, target.z));
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
		return stand;
	}

	/** {@code feet}, raised so that it is at least one block above everything solid or leafy in its column. */
	private static Vec3 clear(ServerLevel level, Vec3 feet) {
		int x = Mth.floor(feet.x);
		int z = Mth.floor(feet.z);
		level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
		return feet.y >= ground ? feet : new Vec3(feet.x, ground, feet.z);
	}

	/** The bottom centre of a template-local cell of {@code piece}, in world space: where feet go. */
	private static Vec3 feet(SitePiece piece, int x, int y, int z) {
		return Vec3.atBottomCenterOf(SiteAnchors.toWorld(piece, new BlockPos(x, y, z)));
	}

	/** The centre of a template-local cell of {@code piece}, in world space: what the camera looks at. */
	private static Vec3 centre(SitePiece piece, int x, int y, int z) {
		return Vec3.atCenterOf(SiteAnchors.toWorld(piece, new BlockPos(x, y, z)));
	}

	/** The first cell of {@code anchor} in {@code piece}, in world space; fails the test when the piece has none. */
	private static BlockPos anchor(SitePiece piece, SiteAnchors.Anchor anchor) {
		BlockPos cell = SiteAnchors.find(piece, anchor);

		if (cell == null) {
			throw new AssertionError(piece.template() + " has no " + anchor + " anchor");
		}

		return cell;
	}

	// ---------------------------------------------------------------- server helpers

	/** Runs {@code command} in the planet's level (a same-level command: {@code tp}, {@code forceload}, {@code site place}). */
	private static void in(TestServerContext server, Planets.Planet planet, String command) {
		server.runCommand("execute in " + planet.level().identifier() + " run " + command);
	}

	/** Force-loads the square {@code reach} blocks around x, z in the planet. */
	private static void forceload(TestServerContext server, Planets.Planet planet, int x, int z, int reach) {
		in(server, planet, String.format(Locale.ROOT, "forceload add %d %d %d %d", x - reach, z - reach, x + reach, z + reach));
	}

	/** True when the column at x, z ends on something that is not a fluid (no tarn or sea). */
	private static boolean dry(ServerLevel level, int x, int z) {
		int y = surface(level, x, z) - 1;
		return level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty();
	}

	/** The first free height of the column at x, z, ignoring leaves; loads the chunk. */
	private static int surface(ServerLevel level, int x, int z) {
		level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** True when every chunk of the square {@code reach} blocks around x, z is loaded. */
	private static boolean loaded(ServerLevel level, int x, int z, int reach) {
		for (int chunkX = SectionPos.blockToSectionCoord(x - reach); chunkX <= SectionPos.blockToSectionCoord(x + reach); chunkX++) {
			for (int chunkZ = SectionPos.blockToSectionCoord(z - reach); chunkZ <= SectionPos.blockToSectionCoord(z + reach); chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					return false;
				}
			}
		}

		return true;
	}

	/** The planet's level; fails the test when the dimension did not load. */
	private static ServerLevel level(MinecraftServer server, Planets.Planet planet) {
		ServerLevel level = Planets.level(server, planet);

		if (level == null) {
			throw new AssertionError("the " + planet.id() + " dimension is not loaded");
		}

		return level;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private PlanetClientSteps() {
	}
}

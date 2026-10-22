package com.oldrepublic.gametest.m5;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSiteSearch;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.PlanetWeather;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.VerseStones;
import com.oldrepublic.planet.block.VerseStoneBlock;
import com.oldrepublic.planet.block.VerseStoneBlockEntity;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.travel.TravelService;
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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for Ghessar and Tyrvann (W3). {@code M5ClientTest} runs them first,
 * in the shared seeded world (spec MILESTONE_5.md 19.2, with the {@code PlanetsM5ClientTest}
 * assertions). Screenshots are {@code oldrepublic_m5_<nnn>_<what>} with {@code nnn} from 125 to 139.
 *
 * <p><b>Getting there</b>, as in the M4 steps: both hubs are placed first ({@link PlanetHubs#ensure},
 * the same call a landing makes) and the player crosses to each hub's first pad bay with
 * {@link TravelService#moveAcross}, the only cross-level move; every other move is a same-level
 * {@code execute in <planet> run tp}. Steps run in creative mode, flying, first person.
 *
 * <p><b>Assertions</b>, on the server unless said otherwise:
 * <ul>
 * <li>each hub is recorded as a {@code planet_hub} piece of its template, every sidecar anchor cell
 * of it lies in its box and the holdfast's {@code HOLDFAST_GATE} cells hold the gate;</li>
 * <li>each of the six biomes is found within {@link #BIOME_SEARCH} blocks of its planet's hub
 * ({@code findClosestBiome3d}: the seed survey logged no M5 positions, as in M4);</li>
 * <li>on the client, a sandstorm raised with {@code /oldrepublic planet sandstorm start} reaches the
 * player ({@link PlanetWeather#kind} on the client's copy) and {@code sandstorm stop} ends it;</li>
 * <li>on the client, the rain check of 3.5: while the overworld rains ({@code /weather rain}) and
 * {@link #RAIN_CHECK} ticks after {@code /weather clear}, the rain level of the player on Ghessar is
 * 0; on Tyrvann it is 1 (its storm never ends);</li>
 * <li>a verse stone site placed with {@code /oldrepublic site place oldrepublic:site/verse_stone} is
 * a {@code verse_stone} piece whose anchor cells lie in its box, with an awake verse stone at
 * {@code STONE}; silenced ({@link VerseStones#silence}), the stone stays silent;</li>
 * <li>the Hymnal, placed by {@link PlanetSites#ensure} with the player over its ring point (its own
 * path: the command places one template, and the Hymnal is the arch with the halls sunk under it),
 * is two {@code hymnal} pieces, the arch and the halls, whose anchor cells lie in their boxes, with
 * sealed plating at {@code HEART_DOOR} and {@code UNDERCROFT_DOOR}.</li>
 * </ul>
 *
 * <p><b>Screenshots:</b> 125 {@code ghessar_dunes_noon}, 126 {@code ghessar_saltflat_noon} and 127
 * {@code ghessar_canyons_noon} (looking west); 128 {@code ghessar_sandstorm} (the dunes view once the
 * storm's fog has closed in); 129 {@code saltgate_from_pad}; 130 {@code tyrvann_stormwood_noon}, 131
 * {@code tyrvann_mire_noon} and 132 {@code tyrvann_basalt_noon}, under Tyrvann's storm; 133
 * {@code tyrvann_ridge_strike} (a bolt on the basalt ridge in that view); 134
 * {@code holdfast_from_pad}; 135 {@code verse_stone_awake} and 136 {@code verse_stone_silenced}
 * (from 6 blocks in front of the stone); 137 {@code hymnal_arch}; 138 {@code hymnal_heart} (from its
 * threshold, with night vision). The twin sun's shot, 139, is cut with the twin sun (MILESTONE_5.md
 * 1, cut item 1).
 *
 * <p>Side effects, on the planets only: both hubs, the verse stone site (its stone silenced) and the
 * Hymnal stay in the world, and Ghessar's weather is left in a fresh clear spell (the steps end with
 * {@code sandstorm stop}, so no seeded storm blows over the later steps). The player's level,
 * position, game mode (survival, as the driver sets it), night vision, time (noon), weather (clear),
 * the planets' force-loaded chunks and the camera are put back.
 */
public final class PlanetClientSteps {
	/** Ticks a planet's hub gets to generate its area and place itself. */
	private static final int HUB_TIMEOUT = 3600;
	/** Ticks a force-loaded area gets to generate and load. */
	private static final int LOAD_TIMEOUT = 2400;
	/** Ticks the client gets to receive a far area after a teleport. */
	private static final int CLIENT_CHUNK_TIMEOUT = 600;
	/**
	 * Ticks the Hymnal gets to be placed with the player over its ring point (a try every {@code planets.siteRetryTicks}): far
	 * placement finds and loads its ground first (docs/MILESTONE_6.md 3.1; {@code sites.far.loadTimeoutTicks} 2400).
	 */
	private static final int SITE_TIMEOUT = 3600;
	/** Ticks for the view to settle (chunk meshes, particles) after a move within an area. */
	private static final int SETTLE = 20;
	/** Ticks for the view to settle after a teleport to another area. */
	private static final int ARRIVE = 60;
	/** Extra ticks a landscape gets to draw its far chunks before its shot. */
	private static final int VISTA = 60;
	/** Ticks the client gets to hear of a weather change: the {@code planet_weather} payload, or two resends of the held levels. */
	private static final int WEATHER = 2 * PlanetWeather.RESEND_TICKS;
	/** The rain check's window (3.5): this long after the overworld's rain stops, the client on Ghessar is dry again. */
	private static final int RAIN_CHECK = 20;
	/** Ticks the overworld rains before the first rain check: its rain level is well up by then. */
	private static final int RAIN_SPELL = 60;
	/** Ticks from a summoned bolt to its shot: the client has it, and it is still flashing. */
	private static final int STRIKE = 2;
	/** Ticks for an awake stone's motes to gather, and after a silencing for its motes and dust to fall. */
	private static final int HUM = 40;
	private static final int SILENCE = 10;

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
	/** How far in front of the basalt camera the ridge bolt falls. */
	private static final int STRIKE_REACH = 24;
	/** Pad camera: height over the pad bay. */
	private static final int PAD_RISE = 5;

	/** The nearest distance of the placed verse stone site from the holdfast, in blocks. */
	private static final int RING = 144;
	/** Spots tried for dry ground; each further one is 13 degrees on and 48 blocks out. */
	private static final int CANDIDATES = 6;
	/** Sample columns of a spot (the spot and 12 blocks each way) that must end on dry ground. */
	private static final int[][] SAMPLES = {{0, 0}, {12, 0}, {-12, 0}, {0, 12}, {0, -12}};
	/** Half-size of the force-loaded square around a template stand and around the Hymnal's ring point. */
	private static final int TEMPLATE_LOAD = 16;
	private static final int SITE_LOAD = 112;
	/** How far from the Hymnal's position its pieces are looked for: the halls lie under the arch, sunk up to 64 blocks further. */
	private static final int HYMNAL_REACH = 160;

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
			// Both hubs generate while the Ghessar steps run.
			server.runOnServer(s -> {
				PlanetHubs.ensure(s, Planets.Planet.GHESSAR);
				PlanetHubs.ensure(s, Planets.Planet.TYRVANN);
			});
			ghessar(context, server);
			tyrvann(context, server);
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
			server.runCommand("execute in minecraft:overworld run weather clear");
			server.runCommand("oldrepublic planet sandstorm stop");

			for (Planets.Planet planet : List.of(Planets.Planet.GHESSAR, Planets.Planet.TYRVANN)) {
				in(server, planet, "forceload remove all");
			}

			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(ARRIVE);
		}
	}

	// ---------------------------------------------------------------- the planets

	/** Ghessar: Saltgate from its pad, the three biomes, the sandstorm and the rain check. */
	private static void ghessar(ClientGameTestContext context, TestServerContext server) {
		Planets.Planet planet = Planets.Planet.GHESSAR;
		SitePiece hub = arriveAtHub(context, server, planet);
		fromPad(context, server, planet, hub, "oldrepublic_m5_129_saltgate_from_pad");
		BlockPos centre = hubCentre(server, planet);
		biome(context, server, planet, PlanetWorldgen.GHESSAR_SALTFLAT, centre, "oldrepublic_m5_126_ghessar_saltflat_noon");
		biome(context, server, planet, PlanetWorldgen.GHESSAR_CANYONS, centre, "oldrepublic_m5_127_ghessar_canyons_noon");
		// The dunes last: the sandstorm's shot is the same view.
		biome(context, server, planet, PlanetWorldgen.GHESSAR_DUNES, centre, "oldrepublic_m5_125_ghessar_dunes_noon");
		sandstorm(context, server);
		rainCheck(context, server);
	}

	/** Tyrvann: the holdfast from its pad, the storm, the three biomes and a ridge strike, a verse stone and the Hymnal. */
	private static void tyrvann(ClientGameTestContext context, TestServerContext server) {
		Planets.Planet planet = Planets.Planet.TYRVANN;
		SitePiece hub = arriveAtHub(context, server, planet);
		// Tyrvann's storm never ends (3.5).
		waitForRain(context, Planets.TYRVANN, 1f, "on arrival in Tyrvann");
		fromPad(context, server, planet, hub, "oldrepublic_m5_134_holdfast_from_pad");
		BlockPos centre = hubCentre(server, planet);
		biome(context, server, planet, PlanetWorldgen.TYRVANN_STORMWOOD, centre, "oldrepublic_m5_130_tyrvann_stormwood_noon");
		biome(context, server, planet, PlanetWorldgen.TYRVANN_MIRE, centre, "oldrepublic_m5_131_tyrvann_mire_noon");
		Vec3 ridge = biome(context, server, planet, PlanetWorldgen.TYRVANN_BASALT, centre, "oldrepublic_m5_132_tyrvann_basalt_noon");
		ridgeStrike(context, server, ridge);
		verseStone(context, server, centre);
		hymnal(context, server);
	}

	// ---------------------------------------------------------------- hubs

	/**
	 * Waits for the planet's hub, crosses to its first pad bay with {@link TravelService#moveAcross}
	 * and returns the hub's piece, checked ({@link #check}).
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

		check(server, planet, hub, SiteKind.PLANET_HUB);
		OldRepublicMod.LOGGER.info("Planet client step: {} hub at {} ({} {})", planet.id(), hub.origin().toShortString(), hub.template(), hub.rotation());
		return hub;
	}

	/** 129 and 134: the hub from above its first pad bay, looking across the town to Varra's stand. */
	private static void fromPad(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, SitePiece hub, String screenshot) {
		BlockPos bay = anchor(hub, SiteAnchors.Anchor.PAD_BAY_1);
		BlockPos town = anchor(hub, SiteAnchors.Anchor.NPC_VARRA);
		look(context, server, planet, Vec3.atBottomCenterOf(bay).add(0, PAD_RISE, 0), Vec3.atCenterOf(town).add(0, 3, 0));
		context.takeScreenshot(screenshot);
	}

	private static BlockPos hubCentre(TestServerContext server, Planets.Planet planet) {
		return server.computeOnServer(s -> PlanetHubs.hub(s, planet));
	}

	// ---------------------------------------------------------------- biomes and weather

	/**
	 * 125 to 127 and 130 to 132: the nearest {@code biome} to the hub (which must exist within
	 * {@link #BIOME_SEARCH}), at noon from {@link #VISTA_RISE} blocks over its ground, looking west.
	 * Returns the stand.
	 */
	private static Vec3 biome(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, ResourceKey<Biome> biome, BlockPos hub,
			String screenshot) {
		Vec3 stand = server.computeOnServer(s -> biomeStand(level(s, planet), biome, hub));

		if (stand == null) {
			throw new AssertionError(planet.id() + ": no " + biome.identifier() + " within " + BIOME_SEARCH + " blocks of the hub at " + hub.toShortString());
		}

		server.runCommand("time set noon");
		arrive(context, server, planet, stand, stand.add(-VISTA_REACH, VISTA_TILT, 0), false);
		context.waitTicks(VISTA);
		context.takeScreenshot(screenshot);
		return stand;
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

	/**
	 * 128: the dunes view while a sandstorm blows. {@code /oldrepublic planet sandstorm start} must
	 * reach the client ({@link PlanetWeather#kind} on its copy) and the shot waits for the fog to close
	 * in ({@link PlanetWeather#FOG_TICKS}); {@code sandstorm stop} must end it on the client too.
	 */
	private static void sandstorm(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic planet sandstorm start");
		waitForWeather(context, PlanetWeather.Kind.SANDSTORM);
		context.waitTicks(PlanetWeather.FOG_TICKS + SETTLE);
		context.takeScreenshot("oldrepublic_m5_128_ghessar_sandstorm");
		server.runCommand("oldrepublic planet sandstorm stop");
		waitForWeather(context, PlanetWeather.Kind.CLEAR);
	}

	/**
	 * The rain check of 3.5: the overworld's rain never reaches the player on Ghessar. Its rain level
	 * is 0 while the overworld rains, and {@link #RAIN_CHECK} ticks after {@code /weather clear} (the
	 * stop packet reaches every level and sets the client's level to 1 for its fade; Ghessar's held 0
	 * must follow it).
	 */
	private static void rainCheck(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute in minecraft:overworld run weather rain");
		context.waitTicks(RAIN_SPELL);
		assertRain(context, Planets.GHESSAR, 0f, "while the overworld rains");
		server.runCommand("execute in minecraft:overworld run weather clear");
		context.waitTicks(RAIN_CHECK);
		assertRain(context, Planets.GHESSAR, 0f, RAIN_CHECK + " ticks after /weather clear in the overworld");
	}

	/** Waits until the client in Ghessar sees {@code kind}; fails the test after {@link #WEATHER} ticks. */
	private static void waitForWeather(ClientGameTestContext context, PlanetWeather.Kind kind) {
		try {
			context.waitFor(client -> client.level != null && client.level.dimension().equals(Planets.GHESSAR) && PlanetWeather.kind(client.level) == kind, WEATHER);
		} catch (AssertionError timedOut) {
			throw new AssertionError("the client in Ghessar does not see " + kind + " within " + WEATHER + " ticks", timedOut);
		}
	}

	/** Waits until the client's rain level in {@code dimension} is {@code wanted}; fails the test after {@link #WEATHER} ticks. */
	private static void waitForRain(ClientGameTestContext context, ResourceKey<Level> dimension, float wanted, String when) {
		try {
			context.waitFor(client -> client.level != null && client.level.dimension().equals(dimension) && Math.abs(client.level.getRainLevel(1f) - wanted) < 0.01f,
					WEATHER);
		} catch (AssertionError timedOut) {
			throw new AssertionError(rainFailure(context, dimension, wanted, when + ", within " + WEATHER + " ticks"), timedOut);
		}
	}

	/** Fails the test unless the client is in {@code dimension} with a rain level of {@code wanted} now. */
	private static void assertRain(ClientGameTestContext context, ResourceKey<Level> dimension, float wanted, String when) {
		boolean ok = context.computeOnClient(client -> client.level != null && client.level.dimension().equals(dimension)
				&& Math.abs(client.level.getRainLevel(1f) - wanted) < 0.01f);

		if (!ok) {
			throw new AssertionError(rainFailure(context, dimension, wanted, when));
		}
	}

	private static String rainFailure(ClientGameTestContext context, ResourceKey<Level> dimension, float wanted, String when) {
		String seen = context.computeOnClient(client -> client.level == null ? "no level"
				: String.format(Locale.ROOT, "%s at rain level %.2f", client.level.dimension().identifier(), client.level.getRainLevel(1f)));
		return "the client should be in " + dimension.identifier() + " at rain level " + wanted + " " + when + ", but is in " + seen;
	}

	/**
	 * 133: a bolt on the basalt ridge {@link #STRIKE_REACH} blocks into the basalt view, under the
	 * storm, shot while it flashes. It is summoned: the ridge lightning's own strikes fall where they
	 * will.
	 */
	private static void ridgeStrike(ClientGameTestContext context, TestServerContext server, Vec3 stand) {
		Planets.Planet planet = Planets.Planet.TYRVANN;
		BlockPos at = server.computeOnServer(s -> {
			int x = Mth.floor(stand.x) - STRIKE_REACH;
			int z = Mth.floor(stand.z);
			return new BlockPos(x, surface(level(s, planet), x, z), z);
		});
		in(server, planet, String.format(Locale.ROOT, "summon minecraft:lightning_bolt %d %d %d", at.getX(), at.getY(), at.getZ()));
		context.waitTicks(STRIKE);
		context.takeScreenshot("oldrepublic_m5_133_tyrvann_ridge_strike");
	}

	// ---------------------------------------------------------------- the verse stone and the Hymnal

	/**
	 * 135 and 136: a verse stone site placed with {@code /oldrepublic site place} on dry ground
	 * {@link #RING} blocks or more from the holdfast and checked; shot awake from 6 blocks in front of
	 * the stone, then silenced on the server ({@link VerseStones#silence}) and shot as its motes fall.
	 * The command's stone has no owner, so no guards rise.
	 */
	private static void verseStone(ClientGameTestContext context, TestServerContext server, BlockPos hub) {
		Planets.Planet planet = Planets.Planet.TYRVANN;
		BlockPos stand = server.computeOnServer(s -> spot(level(s, planet), 0, hub, RING));
		SitePiece piece = placeTemplate(server, planet, PlanetSites.VERSE_STONE.template(), stand);
		check(server, planet, piece, SiteKind.VERSE_STONE);
		BlockPos stone = anchor(piece, SiteAnchors.Anchor.STONE);
		boolean awake = server.computeOnServer(s -> {
			BlockState state = level(s, planet).getBlockState(stone);
			return state.getBlock() instanceof VerseStoneBlock && state.getValue(VerseStoneBlock.AWAKE);
		});

		if (!awake) {
			throw new AssertionError("the placed verse stone at " + stone.toShortString() + " is not awake");
		}

		arrive(context, server, planet, feet(piece, 11, 2, 5), Vec3.atCenterOf(stone), true);
		context.waitTicks(HUM);
		context.takeScreenshot("oldrepublic_m5_135_verse_stone_awake");

		String failure = server.computeOnServer(s -> {
			ServerLevel level = level(s, planet);

			if (!(level.getBlockEntity(stone) instanceof VerseStoneBlockEntity entity)) {
				return "no verse stone block entity at " + stone.toShortString();
			}

			VerseStones.silence(level, stone, entity, null);
			BlockState after = level.getBlockState(stone);
			boolean silent = !entity.awake() && after.getBlock() instanceof VerseStoneBlock && !after.getValue(VerseStoneBlock.AWAKE);
			return silent ? null : "the verse stone at " + stone.toShortString() + " is still awake after its silencing: " + after;
		});

		if (failure != null) {
			throw new AssertionError(failure);
		}

		context.waitTicks(SILENCE);
		context.takeScreenshot("oldrepublic_m5_136_verse_stone_silenced");
	}

	/**
	 * 137 and 138: the Hymnal, placed by {@link PlanetSites#ensure} with the player over its ring point
	 * and checked (the arch and the halls, both {@code hymnal} pieces); the arch from 20 blocks before
	 * it and the Heart from its threshold, with night vision.
	 */
	private static void hymnal(ClientGameTestContext context, TestServerContext server) {
		Planets.Planet planet = Planets.Planet.TYRVANN;
		GlobalPos placed = placeSite(context, server, planet, PlanetSites.Site.HYMNAL);
		List<SitePiece> pieces = server.computeOnServer(s -> StructureLocator.piecesNear(level(s, planet), placed.pos(), HYMNAL_REACH).stream()
				.filter(piece -> piece.kind() == SiteKind.HYMNAL)
				.toList());
		SitePiece arch = piece(pieces, PlanetSites.HYMNAL_ARCH.template());
		SitePiece halls = piece(pieces, PlanetSites.HYMNAL_HALLS.template());
		check(server, planet, arch, SiteKind.HYMNAL);
		check(server, planet, halls, SiteKind.HYMNAL);
		OldRepublicMod.LOGGER.info("Planet client step: the Hymnal's arch at {} ({}), its halls at {}", arch.origin().toShortString(), arch.rotation(),
				halls.origin().toShortString());

		arrive(context, server, planet, feet(arch, 7, 20, -20), centre(arch, 7, 23, 7), true);
		context.takeScreenshot("oldrepublic_m5_137_hymnal_arch");
		// The Heart lies deep under the ridge: night vision so the review can see it.
		server.runCommand("effect give @p minecraft:night_vision infinite 0 true");

		try {
			arrive(context, server, planet, feet(halls, 16, 10, 47), centre(halls, 18, 11, 64), false);
			context.takeScreenshot("oldrepublic_m5_138_hymnal_heart");
		} finally {
			server.runCommand("effect clear @p minecraft:night_vision");
		}
	}

	/**
	 * Stands over a shared site's ring point (force-loaded) and waits for {@link PlanetSites#ensure}
	 * to place it; when it is not placed in time, the failure carries {@link PlanetSiteSearch#explain}.
	 */
	private static GlobalPos placeSite(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, PlanetSites.Site site) {
		UUID owner = server.computeOnServer(s -> player(s).getUUID());
		GlobalPos ring = server.computeOnServer(s -> PlanetSites.ringPoint(s, owner, site, planet));

		if (ring == null) {
			throw new AssertionError(site + ": no ring point (is the hub placed?)");
		}

		BlockPos point = ring.pos();
		forceload(server, planet, point.getX(), point.getZ(), SITE_LOAD);
		server.waitFor(s -> loaded(level(s, planet), point.getX(), point.getZ(), SITE_LOAD), LOAD_TIMEOUT);
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

		OldRepublicMod.LOGGER.info("Planet client step: {} at {}", site, placed[0].pos().toShortString());
		return placed[0];
	}

	/** The piece of {@code template} among {@code pieces}; fails the test when there is none. */
	private static SitePiece piece(List<SitePiece> pieces, Identifier template) {
		for (SitePiece piece : pieces) {
			if (piece.template().equals(template)) {
				return piece;
			}
		}

		throw new AssertionError("the Hymnal has no " + template + " piece; found " + pieces.stream().map(SitePiece::template).toList());
	}

	// ---------------------------------------------------------------- placing and checking

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

	/**
	 * Fails the test unless {@code piece} is recorded as {@code kind} and every sidecar anchor cell of
	 * it lies in its box, with a verse stone at {@code STONE}, the gate at {@code HOLDFAST_GATE} and
	 * sealed plating at {@code HEART_DOOR} and {@code UNDERCROFT_DOOR}.
	 */
	private static void check(TestServerContext server, Planets.Planet planet, SitePiece piece, SiteKind kind) {
		String failure = server.computeOnServer(s -> problem(level(s, planet), piece, kind));

		if (failure != null) {
			throw new AssertionError(piece.template() + " at " + piece.origin().toShortString() + ": " + failure);
		}
	}

	/** What is wrong with {@code piece} ({@link #check}), or null. */
	private static @Nullable String problem(ServerLevel level, SitePiece piece, SiteKind kind) {
		if (piece.kind() != kind) {
			return "recorded as " + piece.kind() + ", not " + kind;
		}

		for (SiteAnchors.Anchor anchor : SiteAnchors.Anchor.values()) {
			Block wanted = switch (anchor) {
				case STONE -> PlanetBlocks.VERSE_STONE;
				case HOLDFAST_GATE -> PlanetBlocks.HOLDFAST_GATE;
				case HEART_DOOR, UNDERCROFT_DOOR -> PlanetBlocks.SEALED_PLATING;
				default -> null;
			};

			for (BlockPos cell : SiteAnchors.all(piece, anchor)) {
				BlockState state = level.getBlockState(cell);

				if (!piece.box().isInside(cell)) {
					return anchor + " at " + cell.toShortString() + " lies outside the piece " + piece.box();
				} else if (wanted != null && !state.is(wanted)) {
					return anchor + " at " + cell.toShortString() + " holds " + state + ", not " + wanted;
				}
			}
		}

		return null;
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

	/** Runs {@code command} in the planet's level (a same-level command: {@code tp}, {@code forceload}, {@code site place}, {@code summon}). */
	private static void in(TestServerContext server, Planets.Planet planet, String command) {
		server.runCommand("execute in " + planet.level().identifier() + " run " + command);
	}

	/** Force-loads the square {@code reach} blocks around x, z in the planet. */
	private static void forceload(TestServerContext server, Planets.Planet planet, int x, int z, int reach) {
		in(server, planet, String.format(Locale.ROOT, "forceload add %d %d %d %d", x - reach, z - reach, x + reach, z + reach));
	}

	/** True when the column at x, z ends on something that is not a fluid (no mire pool). */
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

package com.oldrepublic.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.MissionLoader;
import com.oldrepublic.mission.MissionRules;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.far.FarChooser;
import com.oldrepublic.world.site.far.FarSiteData;
import com.oldrepublic.world.site.far.FarSites;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Far placement (docs/MILESTONE_6.md 3.1 to 3.5): the chooser on synthetic ground (flat beats rough,
 * high beats low for {@code high} and the reverse for {@code low}, wet ground refused, the distance
 * band kept, the preferred bearing first, the same key always the same target, kept points,
 * structures and biomes respected), the "about 550 blocks to the north-east" wording and the
 * tracker's rough distance, the saved requests' round trip and cap, site meta's defaults and file
 * format, and a mission's choice of template variant.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks}, uses the default empty structure and
 * changes nothing in the world. The chooser reads a synthetic {@link FarChooser.Ground}, never the
 * noise generator; the saved requests are detached {@link FarSiteData.Data} values; the server is
 * only read (an unknown key's status, the loaded site meta, mission definitions and templates).
 * Every seed is fixed.
 */
public class FarSitesGameTests {
	/** The band's centre: away from the origin, so signs and rounding show. */
	private static final BlockPos FROM = new BlockPos(1000, 64, -2000);
	private static final long WORLD_SEED = 0x0123456789ABCDEFL;
	/** The {@code far.anglesPerRing} and {@code far.ringsPerBand} defaults, fixed here so retuning them does not move these tests. */
	private static final int ANGLES = 24;
	private static final int RINGS = 4;
	private static final int FOOTPRINT = 16;
	private static final int HALF = FOOTPRINT / 2;
	/** A mission's band ({@code missions.siteRange}). */
	private static final int MIN = 300;
	private static final int MAX = 600;
	/** Water over a wet column. */
	private static final int WATER = 5;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- synthetic ground

	/** A test on a column of synthetic ground. */
	@FunctionalInterface
	private interface Columns {
		boolean test(int x, int z);
	}

	/**
	 * Synthetic ground: {@code floor} everywhere, {@value #WATER} blocks of water over the columns
	 * {@link #wet} names, every biome allowed and no structure nearby unless set otherwise.
	 */
	private static final class Field implements FarChooser.Ground {
		private final IntBinaryOperator floor;
		private Columns wet = (x, z) -> false;
		private Columns biome = (x, z) -> true;
		private Columns structure = (x, z) -> false;

		Field(IntBinaryOperator floor) {
			this.floor = floor;
		}

		Field wet(Columns columns) {
			wet = columns;
			return this;
		}

		Field biome(Columns columns) {
			biome = columns;
			return this;
		}

		Field structure(Columns columns) {
			structure = columns;
			return this;
		}

		@Override
		public int surface(int x, int z) {
			return wet.test(x, z) ? floor(x, z) + WATER : floor(x, z);
		}

		@Override
		public int floor(int x, int z) {
			return floor.applyAsInt(x, z);
		}

		@Override
		public boolean biomeAllowed(int x, int y, int z) {
			return biome.test(x, z);
		}

		@Override
		public boolean structureNear(int x, int y, int z) {
			return structure.test(x, z);
		}
	}

	private static Field flat() {
		return new Field((x, z) -> 64);
	}

	/** Square mesas 36 blocks high and 64 across, every 128 blocks; the plain between them at 64. */
	private static Field mesas() {
		return new Field((x, z) -> Math.floorMod(x, 128) < 64 && Math.floorMod(z, 128) < 64 ? 100 : 64);
	}

	private static FarChooser.Spec spec(String key, FarSites.Terrain terrain) {
		return spec(key, terrain, null, 360f, List.of());
	}

	private static FarChooser.Spec spec(String key, FarSites.Terrain terrain, @Nullable Vec3 bearing, float spread, List<FarChooser.Keep> keep) {
		return new FarChooser.Spec(FROM, MIN, MAX, FOOTPRINT, terrain, bearing, spread, keep, FarChooser.seed(key, WORLD_SEED), ANGLES, RINGS);
	}

	/** Whether every column the chooser samples under a footprint centred on {@code target} (centre, corners, edge middles) passes {@code test}. */
	private static boolean footprint(BlockPos target, Columns test) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!test.test(target.getX() + dx * HALF, target.getZ() + dz * HALF)) {
					return false;
				}
			}
		}

		return true;
	}

	private static double horizontal(BlockPos from, int x, int z) {
		double dx = x - from.getX();
		double dz = z - from.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static double horizontal(BlockPos from, BlockPos to) {
		return horizontal(from, to.getX(), to.getZ());
	}

	/** Degrees between {@code bearing} and the way from {@link #FROM} to (x, z), in the chooser's convention (x by cosine, z by sine). */
	private static double offBearing(int x, int z, Vec3 bearing) {
		double way = Math.atan2(z - FROM.getZ(), x - FROM.getX());
		return Math.toDegrees(Math.abs(Math.IEEEremainder(way - Math.atan2(bearing.z, bearing.x), Math.PI * 2)));
	}

	// ---------------------------------------------------------------- the chooser

	@GameTest(maxTicks = 20)
	public void chooserPrefersFlatGround(GameTestHelper helper) {
		// Level ground east of FROM; west of it every footprint varies by 2 to 22 blocks.
		Field halfRough = new Field((x, z) -> x >= FROM.getX() ? 64 : 65 + Math.floorMod(x, 23));

		for (FarSites.Terrain terrain : List.of(FarSites.Terrain.FLAT, FarSites.Terrain.ANY)) {
			FarChooser.Choice choice = FarChooser.choose(spec("test/flat", terrain), halfRough);
			helper.assertTrue(choice != null && footprint(choice.target(), (x, z) -> x >= FROM.getX()) && choice.target().getY() == 64 && choice.score() == 0.0,
					msg(terrain + ": the target's whole footprint is on the level ground; got " + choice));
		}

		FarChooser.Choice rough = FarChooser.choose(spec("test/flat", FarSites.Terrain.FLAT), new Field((x, z) -> 65 + Math.floorMod(x, 23)));
		helper.assertTrue(rough != null && rough.score() < 0, msg("rough but passable ground is used when there is nothing better, at a cost; got " + rough));

		// A 45-block step every 8 blocks: every footprint spans one, more than a site may stand on.
		Field cliffs = new Field((x, z) -> Math.floorMod(x, 16) < 8 ? 64 : 109);
		helper.assertTrue(FarChooser.choose(spec("test/flat", FarSites.Terrain.ANY), cliffs) == null,
				msg("cliffs (more than " + FarChooser.MAX_SPREAD + " blocks of spread under the footprint) are refused"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chooserPrefersHighOrLowGround(GameTestHelper helper) {
		Field mesas = mesas();
		Columns top = (x, z) -> mesas.floor(x, z) == 100;
		Columns plain = (x, z) -> mesas.floor(x, z) == 64;

		FarChooser.Choice high = FarChooser.choose(spec("test/relief", FarSites.Terrain.HIGH), mesas);
		helper.assertTrue(high != null && footprint(high.target(), top) && high.target().getY() == 100 && high.score() > 0,
				msg("high: on a mesa top, above the ground around; got " + high));
		FarChooser.Choice low = FarChooser.choose(spec("test/relief", FarSites.Terrain.LOW), mesas);
		helper.assertTrue(low != null && footprint(low.target(), plain) && low.target().getY() == 64 && low.score() > 0,
				msg("low: on the plain, below the mesas around; got " + low));

		for (int i = 0; i < 16; i++) {
			FarChooser.Choice h = FarChooser.choose(spec("test/relief/" + i, FarSites.Terrain.HIGH), mesas);
			FarChooser.Choice l = FarChooser.choose(spec("test/relief/" + i, FarSites.Terrain.LOW), mesas);
			helper.assertTrue(h != null && h.target().getY() == 100 && l != null && l.target().getY() == 64,
					msg("key " + i + ": high on a mesa, low on the plain; got " + h + " and " + l));
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chooserRefusesWetGround(GameTestHelper helper) {
		Field sea = new Field((x, z) -> 40).wet((x, z) -> true);
		helper.assertTrue(FarChooser.choose(spec("test/wet", FarSites.Terrain.ANY), sea) == null, msg("all sea: no target after every pass (no dry ground)"));

		// Sea to the north; land from 100 blocks south of FROM.
		int coast = FROM.getZ() + 100;
		Field shore = new Field((x, z) -> z >= coast ? 70 : 40).wet((x, z) -> z < coast);
		FarChooser.Choice choice = FarChooser.choose(spec("test/wet", FarSites.Terrain.ANY), shore);
		helper.assertTrue(choice != null && footprint(choice.target(), (x, z) -> z >= coast) && choice.target().getY() == 70,
				msg("a coast: the whole footprint is on the dry land; got " + choice));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chooserKeepsToBand(GameTestHelper helper) {
		Field flat = flat();

		for (int i = 0; i < 12; i++) {
			FarChooser.Choice choice = FarChooser.choose(spec("test/band/" + i, FarSites.Terrain.ANY), flat);
			double distance = choice == null ? -1 : horizontal(FROM, choice.target());
			helper.assertTrue(distance >= MIN - 1 && distance <= MAX + 1, msg("key " + i + ": the target is " + MIN + " to " + MAX + " blocks out; got " + distance));
		}

		// Water to 640 blocks out: every candidate in the band is wet, so the last pass looks from the band's edge to twice as far.
		int dry = MAX + 40;
		Columns lake = (x, z) -> horizontal(FROM, x, z) < dry;
		FarChooser.Choice beyond = FarChooser.choose(spec("test/band/lake", FarSites.Terrain.ANY), new Field((x, z) -> 64).wet(lake));
		double distance = beyond == null ? -1 : horizontal(FROM, beyond.target());
		helper.assertTrue(beyond != null && footprint(beyond.target(), (x, z) -> !lake.test(x, z)) && distance > MAX && distance <= 2 * MAX + 1,
				msg("a drowned band: past its edge, at most twice as far; got " + beyond + " at " + distance));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chooserPrefersBearing(GameTestHelper helper) {
		Field flat = flat();
		Vec3 north = new Vec3(0, 0, -1);

		for (int i = 0; i < 8; i++) {
			FarChooser.Choice choice = FarChooser.choose(spec("test/bearing/" + i, FarSites.Terrain.ANY, north, 60f, List.of()), flat);
			double off = choice == null ? 999 : offBearing(choice.target().getX(), choice.target().getZ(), north);
			helper.assertTrue(off <= 31, msg("key " + i + ": within 30 degrees of north (a 60 degree spread); got " + choice + ", " + off + " degrees off"));
		}

		Vec3 east = new Vec3(1, 0, 0);
		FarChooser.Choice eastward = FarChooser.choose(spec("test/bearing/east", FarSites.Terrain.ANY, east, 60f, List.of()), flat);
		helper.assertTrue(eastward != null && eastward.target().getX() > FROM.getX() + MIN / 2 && offBearing(eastward.target().getX(), eastward.target().getZ(), east) <= 31,
				msg("east is +x; got " + eastward));

		// The preferred bearings under water: the whole band next, the dry bearing nearest the preferred one first.
		Field wetNorth = new Field((x, z) -> 64).wet((x, z) -> offBearing(x, z, north) <= 40);
		FarChooser.Choice aside = FarChooser.choose(spec("test/bearing/wet", FarSites.Terrain.ANY, north, 60f, List.of()), wetNorth);
		double off = aside == null ? -1 : offBearing(aside.target().getX(), aside.target().getZ(), north);
		helper.assertTrue(off > 40 && off <= 60, msg("north under water: just beside it, 40 to 60 degrees off; got " + aside + ", " + off + " degrees off"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chooserIsDeterministic(GameTestHelper helper) {
		helper.assertTrue(FarChooser.seed("mission/a", 1L) == FarChooser.seed("mission/a", 1L), msg("a key's seed is stable"));
		helper.assertTrue(FarChooser.seed("mission/a", 1L) != FarChooser.seed("mission/b", 1L) && FarChooser.seed("mission/a", 1L) != FarChooser.seed("mission/a", 2L),
				msg("the seed changes with the key and with the world's seed"));

		Field mesas = mesas();
		FarChooser.Spec spec = spec("test/determinism", FarSites.Terrain.HIGH);
		FarChooser.Choice once = FarChooser.choose(spec, mesas);
		helper.assertTrue(once != null && once.equals(FarChooser.choose(spec, mesas)), msg("the same request on the same ground gives the same target; got " + once));

		// Sliced at 64 samples a tick (far.samplesPerTick), the chooser ends where the one-shot run does.
		FarChooser stepped = new FarChooser(spec);
		int steps = 0;

		while (!stepped.done() && steps < 10_000) {
			int used = stepped.step(mesas, 64);
			helper.assertTrue(used <= 64 + 32, msg("a step spends about its budget (only the last candidate may overshoot); spent " + used));
			steps++;
		}

		helper.assertTrue(stepped.done() && steps > 1 && once != null && once.equals(stepped.choice()),
				msg("sliced over " + steps + " steps, the same target; got " + stepped.choice()));

		Field flat = flat();
		Set<BlockPos> targets = new HashSet<>();

		for (int i = 0; i < 8; i++) {
			FarChooser.Choice choice = FarChooser.choose(spec("test/key/" + i, FarSites.Terrain.ANY), flat);
			helper.assertTrue(choice != null, msg("flat ground always gives a target"));
			targets.add(choice.target());
		}

		helper.assertTrue(targets.size() > 1, msg("different keys turn the rings differently, so their targets differ; got " + targets));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chooserKeepsAway(GameTestHelper helper) {
		Field flat = flat();
		FarChooser.Choice first = FarChooser.choose(spec("test/keep", FarSites.Terrain.ANY), flat);
		helper.assertTrue(first != null, msg("flat ground gives a target"));
		FarChooser.Choice kept = FarChooser.choose(spec("test/keep", FarSites.Terrain.ANY, null, 360f, List.of(new FarChooser.Keep(first.target(), 200))), flat);
		helper.assertTrue(kept != null && horizontal(first.target(), kept.target()) >= 200,
				msg("a kept point (another site, a refused target) is kept 200 blocks away; got " + kept + " from " + first));

		FarChooser.Choice west = FarChooser.choose(spec("test/keep", FarSites.Terrain.ANY), flat().structure((x, z) -> x >= FROM.getX()));
		helper.assertTrue(west != null && west.target().getX() < FROM.getX(), msg("a structure could start anywhere east: the target is west; got " + west));
		FarChooser.Choice north = FarChooser.choose(spec("test/keep", FarSites.Terrain.ANY), flat().biome((x, z) -> z < FROM.getZ()));
		helper.assertTrue(north != null && north.target().getZ() < FROM.getZ(), msg("only northern biomes allowed: the target is north; got " + north));
		helper.assertTrue(FarChooser.choose(spec("test/keep", FarSites.Terrain.ANY, null, 360f, List.of(new FarChooser.Keep(FROM, 2 * MAX + 100))), flat) == null,
				msg("kept away from everything it could reach: no target"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the wording

	@GameTest(maxTicks = 20)
	public void describeWordsDistanceAndDirection(GameTestHelper helper) {
		helper.assertTrue(key(FarSites.describe(FROM, FROM.offset(60, 0, 70))).equals("message.oldrepublic.far.close"), msg("under 100 blocks: close by"));
		// API-CHECK: TranslatableContents.equals compares the key and the arguments (MutableComponent.equals is in the dumps).
		helper.assertTrue(FarSites.describe(FROM, FROM.offset(0, 5, -549)).equals(about(550, "n")), msg("549 north: about 550 blocks to the north"));
		helper.assertTrue(FarSites.describe(FROM, FROM.offset(400, -10, 400)).equals(about(550, "se")), msg("566 south-east: about 550 blocks to the south-east"));
		helper.assertTrue(FarSites.describe(FROM, FROM.offset(-80, 0, 70)).equals(about(100, "sw")), msg("106 south-west: about 100 blocks to the south-west"));
		helper.assertTrue(FarSites.describe(FROM, FROM.offset(0, 0, 125)).equals(about(150, "s")), msg("125 rounds up to 150"));

		String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		int[][] steps = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

		for (int i = 0; i < names.length; i++) {
			String name = FarSites.compass(steps[i][0] * 300, steps[i][1] * 300);
			helper.assertTrue(name.equals(names[i]), msg("a step of " + steps[i][0] + ", " + steps[i][1] + " is " + names[i] + " (north is -z); got " + name));
		}

		double inside = Math.toRadians(22);
		double outside = Math.toRadians(23);
		helper.assertTrue(FarSites.compass(Math.sin(inside), -Math.cos(inside)).equals("n") && FarSites.compass(Math.sin(outside), -Math.cos(outside)).equals("ne"),
				msg("each direction covers 22.5 degrees either side"));

		// The tracker's rough distance rounds the same way and is 0 ("close by") under 100 blocks.
		helper.assertTrue(MissionRules.roughDistance(0) == 0 && MissionRules.roughDistance(99.9) == 0 && MissionRules.roughDistance(100) == 100
				&& MissionRules.roughDistance(124) == 100 && MissionRules.roughDistance(125) == 150 && MissionRules.roughDistance(549) == 550
				&& MissionRules.roughDistance(575) == 600, msg("the tracker's ~distance is rounded to 50 from 100 blocks out"));
		helper.succeed();
	}

	private static String key(Component component) {
		return component.getContents() instanceof TranslatableContents text ? text.getKey() : "";
	}

	private static Component about(long blocks, String direction) {
		return Component.translatable("message.oldrepublic.far.about", blocks, Component.translatable("message.oldrepublic.far.dir." + direction));
	}

	// ---------------------------------------------------------------- the saved requests

	@GameTest(maxTicks = 20)
	public void farSiteDataRoundTrip(GameTestHelper helper) {
		Identifier template = Identifier.fromNamespaceAndPath("oldrepublic_test", "far/no_anchors");
		UUID owner = new UUID(0x0123456789ABCDEFL, 0x0FEDCBA987654321L);
		FarSiteData.Shape band = new FarSiteData.Shape(template, new BlockPos(10, 70, -20), 300, 600, 0, null);
		FarSiteData.Prefs prefs = new FarSiteData.Prefs(new Vec3(0.6, 0, -0.8), 60f, PlanetWorldgen.IS_SELVAREN, PlanetWorldgen.SELVAREN_MEADOW,
				List.of(new BlockPos(1, 2, 3), new BlockPos(-400, 64, 90)), 96, FarSites.Terrain.HIGH, 5, SiteKind.MISSION_SITE, true);
		BlockPos refusedTarget = new BlockPos(330, 66, -300);
		BlockPos target = new BlockPos(-280, 71, 150);
		BoundingBox box = new BoundingBox(-295, 65, 135, -265, 80, 165);
		BlockPos stand = new BlockPos(-280, 66, 150);
		FarSiteData.Placed placed = new FarSiteData.Placed(SiteKind.MISSION_SITE, template, box, Rotation.CLOCKWISE_90, new BlockPos(-265, 65, 135), stand);

		FarSiteData.Entry choosing = FarSiteData.Entry.choosing("test/far/choosing", Level.OVERWORLD, null, band, FarSiteData.Prefs.NONE, 100L);
		FarSiteData.Entry loading = FarSiteData.Entry.choosing("test/far/loading", Level.OVERWORLD, owner, band, prefs, 200L).loading(target, 4, "loading", 260L);
		FarSiteData.Entry done = FarSiteData.Entry.choosing("test/far/placed", Level.NETHER, owner, band, prefs, 300L)
				.loading(refusedTarget, 3, "loading", 310L)
				.retry("built", 320L)
				.loading(target, 3, "loading", 330L)
				.placed(placed, null, "clearing", 400L);
		FarSiteData.Entry point = FarSiteData.Entry.choosing("test/far/point", Level.OVERWORLD, null, new FarSiteData.Shape(null, BlockPos.ZERO, 200, 400, 16, null),
				FarSiteData.Prefs.NONE, 500L).failed("no_dry_ground", 520L);
		FarSiteData.Entry stamp = FarSiteData.Entry.choosing("test/far/stamp", Level.OVERWORLD, null,
				new FarSiteData.Shape(template, new BlockPos(5, 40, 5), 0, 0, 0, Rotation.COUNTERCLOCKWISE_90), FarSiteData.Prefs.NONE, 600L);

		FarSiteData.Data data = new FarSiteData.Data();

		for (FarSiteData.Entry entry : List.of(choosing, loading, done, point, stamp)) {
			data.put(entry);
		}

		JsonElement json = FarSiteData.Data.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		FarSiteData.Data back = FarSiteData.Data.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.all().equals(data.all()), msg("every request survives the codec: " + json));
		helper.assertTrue(FarSiteData.Data.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().all().isEmpty(), msg("an empty value reads as no requests"));

		JsonObject bare = FarSiteData.Entry.CODEC.encodeStart(JsonOps.INSTANCE, loading).getOrThrow().getAsJsonObject();
		bare.remove("prefs");
		helper.assertTrue(FarSiteData.Entry.CODEC.parse(JsonOps.INSTANCE, bare).getOrThrow().prefs().equals(FarSiteData.Prefs.NONE),
				msg("an entry saved without preferences reads back with none"));

		// What callers see.
		FarSites.Status chooses = choosing.status();
		helper.assertTrue(chooses.phase() == FarSites.Phase.CHOOSING && chooses.target() == null && chooses.pointer() == null && choosing.active(),
				msg("choosing: no target yet, nothing to point at"));
		helper.assertTrue(target.equals(loading.status().pointer()) && loading.active() && loading.ticketRadius() == 4, msg("loading: guidance points at the target"));
		FarSiteData.Entry restored = back.get("test/far/placed");
		FarSites.Status placedStatus = restored == null ? chooses : restored.status();
		RuntimeSites.Placement placement = placedStatus.placement();
		helper.assertTrue(placedStatus.placed() && placement != null && placement.origin().equals(stand) && placement.piece().box().equals(box)
				&& placement.piece().rotation() == Rotation.CLOCKWISE_90 && placement.piece().kind() == SiteKind.MISSION_SITE,
				msg("placed: the placement comes back from the save; got " + placedStatus));
		helper.assertTrue(stand.equals(placedStatus.pointer()), msg("placed, its template without a HEART anchor: guidance points at the stand"));
		helper.assertTrue(done.refused().equals(List.of(refusedTarget)) && target.equals(done.target()) && !done.active(),
				msg("a refused target is remembered so the chooser keeps away from it"));
		FarSites.Status failed = point.status();
		helper.assertTrue(failed.failed() && failed.detail().equals("no_dry_ground") && failed.placement() == null && !point.active(), msg("failed: the reason is kept"));
		helper.assertTrue(stamp.shape().fixed() && !band.fixed() && stamp.shape().fixedRotation() == Rotation.COUNTERCLOCKWISE_90, msg("a fixed stamp keeps its turn"));

		// Read only: polling a key nobody asked for requests nothing.
		FarSites.Status unknown = FarSites.poll(helper.getLevel(), "test/far/never_requested");
		helper.assertTrue(unknown.failed() && unknown.detail().equals("unknown") && unknown.pointer() == null, msg("an unknown key polls as failed, \"unknown\""));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void farSiteDataCapKeepsWorkingRequests(GameTestHelper helper) {
		FarSiteData.Shape shape = new FarSiteData.Shape(null, BlockPos.ZERO, 200, 400, 16, null);
		FarSiteData.Data data = new FarSiteData.Data();
		data.put(FarSiteData.Entry.choosing("test/cap/working", Level.OVERWORLD, null, shape, FarSiteData.Prefs.NONE, 0L));
		data.put(FarSiteData.Entry.choosing("test/cap/oldest", Level.OVERWORLD, null, shape, FarSiteData.Prefs.NONE, 1L).failed("no_dry_ground", 1L));

		for (int i = 2; i < FarSiteData.Data.MAX_ENTRIES; i++) {
			data.put(FarSiteData.Entry.choosing("test/cap/" + i, Level.OVERWORLD, null, shape, FarSiteData.Prefs.NONE, i).failed("no_dry_ground", i));
		}

		helper.assertTrue(data.all().size() == FarSiteData.Data.MAX_ENTRIES, msg("filled to the cap"));
		data.put(FarSiteData.Entry.choosing("test/cap/new", Level.OVERWORLD, null, shape, FarSiteData.Prefs.NONE, 10_000L));
		helper.assertTrue(data.all().size() == FarSiteData.Data.MAX_ENTRIES && data.get("test/cap/oldest") == null && data.get("test/cap/working") != null
				&& data.get("test/cap/new") != null, msg("past the cap the oldest finished request goes, never a working one"));

		FarSiteData.Entry working = data.get("test/cap/working");
		FarSiteData.Entry moved = working == null ? null : working.loading(new BlockPos(300, 64, 0), 3, "loading", 10_001L);
		helper.assertTrue(moved != null, msg("the working request is still there"));
		data.put(moved);
		helper.assertTrue(data.all().size() == FarSiteData.Data.MAX_ENTRIES && data.all().getFirst().equals(moved), msg("a request with the same key is replaced where it stands"));

		FarSiteData.Data copy = data.copy();
		helper.assertTrue(copy.remove("test/cap/new") && !copy.remove("test/cap/new") && data.get("test/cap/new") != null,
				msg("a copy is independent, and remove says whether the key was there"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- site meta

	@GameTest(maxTicks = 20)
	public void siteMetaDefaultsAndParsing(GameTestHelper helper) {
		Identifier template = Identifier.fromNamespaceAndPath("oldrepublic_test", "far/meta");
		SiteMeta d = SiteMeta.DEFAULT;
		helper.assertTrue(d.buried() == 1 && d.maxSlope() == 4 && d.clearance() == 8 && d.supportDepth() == 8 && d.rampRings() == 2 && d.clearAbove() == 6
				&& d.spawnRadius() == 64 && d.biomes() == null && d.links().isEmpty(), msg("the defaults of docs/STRUCTURES.md 7: " + d));
		helper.assertTrue(SiteMeta.parse(template, JsonParser.parseString("{}")).equals(SiteMeta.DEFAULT), msg("an empty file is the defaults"));

		SiteMeta full = SiteMeta.parse(template, JsonParser.parseString("""
				{"buried": 3, "max_slope": 5, "clearance": 12, "support_depth": 10, "ramp_rings": 3, "clear_above": 9, "terrain": "high",
				 "biomes": "#oldrepublic:is_selvaren", "spawn_radius": 48, "links": {"oldrepublic:planet/hymnal_halls": [1, -30, 2]}}
				"""));
		helper.assertTrue(full.buried() == 3 && full.maxSlope() == 5 && full.clearance() == 12 && full.supportDepth() == 10 && full.rampRings() == 3
				&& full.clearAbove() == 9 && full.terrain() == FarSites.Terrain.HIGH && PlanetWorldgen.IS_SELVAREN.equals(full.biomes()) && full.spawnRadius() == 48,
				msg("every field is read: " + full));
		helper.assertTrue(new BlockPos(1, -30, 2).equals(full.link(OldRepublicMod.id("planet/hymnal_halls"))) && full.link(OldRepublicMod.id("planet/other")) == null,
				msg("links give a companion's offset"));

		SiteMeta partial = SiteMeta.parse(template, JsonParser.parseString("{\"terrain\": \"low\", \"colour\": \"red\"}"));
		helper.assertTrue(partial.terrain() == FarSites.Terrain.LOW && partial.buried() == d.buried() && partial.clearance() == d.clearance() && partial.links().isEmpty(),
				msg("a missing field keeps its default and an unknown one is skipped: " + partial));

		for (String bad : List.of("{\"buried\": -1}", "{\"buried\": 257}", "{\"max_slope\": 2.5}", "{\"clearance\": \"wide\"}", "{\"terrain\": \"steep\"}",
				"{\"biomes\": \"oldrepublic:is_selvaren\"}", "{\"links\": {\"oldrepublic:planet/hymnal_halls\": [1, 2]}}", "[]")) {
			helper.assertTrue(fails(template, bad), msg("a malformed file fails: " + bad));
		}

		// The loaded files: a template without one gets the defaults and keeps the caller's buried; one with a file gives its own.
		Identifier missing = Identifier.fromNamespaceAndPath("oldrepublic_test", "far/no_meta");
		helper.assertTrue(SiteMeta.find(missing) == null && SiteMeta.of(missing).equals(SiteMeta.DEFAULT) && SiteMeta.buried(missing, 7) == 7,
				msg("no file: the defaults, and the caller's own buried"));
		Identifier cache = OldRepublicMod.id("mission/smuggler_cache");
		SiteMeta loaded = SiteMeta.find(cache);
		helper.assertTrue(loaded != null && SiteMeta.of(cache).equals(loaded) && SiteMeta.buried(cache, 99) == loaded.buried(),
				msg("site_meta/mission/smuggler_cache.json is loaded, and its buried wins over the caller's"));
		helper.succeed();
	}

	private static boolean fails(Identifier template, String json) {
		try {
			SiteMeta.parse(template, JsonParser.parseString(json));
			return false;
		} catch (RuntimeException e) {
			return true;
		}
	}

	// ---------------------------------------------------------------- mission variants

	@GameTest(maxTicks = 20)
	public void missionVariantByKey(GameTestHelper helper) {
		Identifier base = OldRepublicMod.id("mission/fence_camp");
		Identifier second = OldRepublicMod.id("mission/fence_camp_2");
		helper.assertTrue(MissionRules.variant(base, "test/variant/0", id -> false).equals(base), msg("no variants: the template itself"));

		Set<Identifier> picked = new HashSet<>();

		for (int i = 0; i < 32; i++) {
			String key = "test/variant/" + i;
			Identifier choice = MissionRules.variant(base, key, second::equals);
			helper.assertTrue(choice.equals(MissionRules.variant(base, key, second::equals)), msg("the same key always picks the same variant"));
			picked.add(choice);
		}

		helper.assertTrue(picked.equals(Set.of(base, second)), msg("keys pick both the template and its _2: " + picked));

		// A gap ends the list: with _2 and _4 but no _3, _4 is never picked.
		Identifier fourth = OldRepublicMod.id("mission/fence_camp_4");
		Set<Identifier> gap = new HashSet<>();

		for (int i = 0; i < 32; i++) {
			gap.add(MissionRules.variant(base, "test/variant/" + i, id -> id.equals(second) || id.equals(fourth)));
		}

		helper.assertTrue(gap.equals(Set.of(base, second)), msg("variants stop at the first missing number: " + gap));

		Set<Identifier> many = new HashSet<>();

		for (int i = 0; i < 64; i++) {
			many.add(MissionRules.variant(base, "test/variant/" + i, id -> true));
		}

		helper.assertTrue(many.size() == MissionRules.MAX_VARIANTS && !many.contains(OldRepublicMod.id("mission/fence_camp_" + (MissionRules.MAX_VARIANTS + 1))),
				msg("at most " + MissionRules.MAX_VARIANTS + " variants in all; got " + many.size()));

		// The real templates: each mission site's pick exists, and C4's wreck is always the camp with the freighter.
		ServerLevel level = helper.getLevel();
		Predicate<Identifier> exists = id -> level.getStructureManager().get(id).isPresent();
		Set<Identifier> fence = new HashSet<>();

		for (int i = 0; i < 32; i++) {
			fence.add(MissionRules.variant(base, "test/variant/" + i, exists));
		}

		helper.assertTrue(fence.equals(Set.of(base, second)), msg("fence_camp and fence_camp_2 are both picked: " + fence));

		for (MissionDefinition def : MissionLoader.all()) {
			Identifier site = def.site();

			if (site == null) {
				continue;
			}

			for (int i = 0; i < 8; i++) {
				Identifier pick = MissionRules.variant(site, "test/variant/" + def.id() + "/" + i, exists);
				helper.assertTrue(exists.test(pick), msg(def.id() + ": the picked template " + pick + " exists"));
			}
		}

		MissionDefinition c4 = MissionLoader.get("c4_off_the_books");
		Identifier wreck = OldRepublicMod.id("mission/raider_camp_small_2");
		helper.assertTrue(c4 != null && wreck.equals(c4.site()) && MissionRules.variant(wreck, "test/variant/c4", exists).equals(wreck),
				msg("C4 Off the Books always gets the camp with the crashed freighter"));
		helper.succeed();
	}
}

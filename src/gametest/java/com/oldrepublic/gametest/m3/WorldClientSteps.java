package com.oldrepublic.gametest.m3;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.puzzle.CounterweightStoneBlock;
import com.oldrepublic.world.puzzle.PuzzleBlocks;
import com.oldrepublic.world.puzzle.PuzzleStates;
import com.oldrepublic.world.puzzle.VeiledStoneBlock;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 client test steps for the world feature (sites, puzzles, outposts and the worldgen
 * step). {@code ExplorationClientTest} runs them in the shared seeded exploration world (spec
 * 17.2). Screenshots are {@code oldrepublic_m3_<nn>_<what>} with {@code nn} from 10 to 19.
 *
 * <p><b>Worldgen step.</b> The five structures are placed with {@code /oldrepublic site place
 * <structure> <pos>} (the vanilla {@code place structure} path, registered like worldgen) and the
 * Jedi Echo Shrine with {@code /oldrepublic site place echo_shrine_jedi <pos>}, each on its own spot
 * on a ring {@link #RING} blocks or more around where the steps start, 60 degrees apart, on dry
 * ground when one of the {@link #CANDIDATES} candidate spots of the bearing is dry. Each area is
 * force-loaded first. On the server, through {@link StructureLocator#piecesNear} and
 * {@link StructureLocator#sitePieces}, every placed site must have exactly its template pieces
 * (both pilgrim niches included), every {@code PEDESTAL*} sidecar cell must hold a
 * {@code holocron_pedestal} and no anchor cell may hold stray terrain (ground, stone, sand, snow,
 * leaves, logs or a fluid).
 *
 * <p><b>Screenshots</b>, taken in creative mode, flying, first person (so the site presence and
 * its ambience run, and nothing attacks):
 * <ul>
 * <li>10 {@code watchers_rest_courtyard}: the courtyard from above its north-west corner;</li>
 * <li>19 {@code veiled_stone_before} and {@code veiled_stone_after}: the courtyard's stair wall,
 * then revealed through {@link VeiledStoneBlock#reveal} (the Sense provider's path);</li>
 * <li>11 {@code listening_hall_ledges}: the north wall's step stones out as ledges after all three
 * counterweights were sunk through {@link CounterweightStoneBlock#sink} (the Push path);</li>
 * <li>12 {@code sith_tomb_gate}: through the obelisk gateway down the wind-cut steps;</li>
 * <li>13 {@code processional_vent_tell}: a processional vent in its TELL phase, with the player
 * beside it on the first landing; the vent must reach TELL within {@link #VENT_TELL_TIMEOUT}
 * ticks (the site tracker kicks the vents);</li>
 * <li>14 {@code burial_chamber_urns}: the chamber from its south-east corner, the four urns on
 * their plinths;</li>
 * <li>15 {@code wreck_hull}, 16 {@code republic_outpost}, 17 {@code imperial_outpost}, 18
 * {@code echo_shrine}: each from above, outside it.</li>
 * </ul>
 *
 * <p>Side effects, all far from spawn: the placed sites stay in the world (the shrine is recorded
 * in {@code WorldSites} by the command, as every command placement is); entering the sites runs
 * the site presence, so the player may discover them; site spawners and terminals near the
 * vantage points fire. The player's position, game mode (survival, as the driver sets it), the
 * force-loaded chunks and the camera are put back.
 */
public final class WorldClientSteps {
	/** The nearest distance of the site spots from where the steps start, in blocks. */
	private static final int RING = 320;
	/** Spots tried per bearing for dry ground; each further one is 13 degrees on and 64 blocks out. */
	private static final int CANDIDATES = 6;
	/** Sample columns of a spot (the spot and 24 blocks each way) that must end on dry ground. */
	private static final int[][] SAMPLES = {{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}};
	/** The 17.2 bound for a processional vent to reach TELL with the player beside it. */
	private static final int VENT_TELL_TIMEOUT = 1200;
	/** Ticks a force-loaded area gets to generate and load. */
	private static final int LOAD_TIMEOUT = 2400;
	/** Ticks the client gets to receive a far area after a teleport. */
	private static final int CLIENT_CHUNK_TIMEOUT = 600;
	/** Ticks for the view to settle (chunk meshes, particles) after a teleport within a site. */
	private static final int SETTLE = 20;
	/** Ticks for the view to settle after a teleport to another site. */
	private static final int ARRIVE = 60;
	/** Eye height of a standing player. */
	private static final double EYE = 1.62;
	/** How far an overview camera may rise to see over a hill between it and its site. */
	private static final int VIEW_RISE = 24;

	/**
	 * One site to place: the command argument, its kind, the start (or only) template, the template
	 * pieces every placement has, how far its pieces reach from the spot (the start chunk's corner
	 * for a structure, the stand for the shrine) and whether it is a worldgen structure.
	 */
	private record Plan(String command, SiteKind kind, Identifier start, Map<Identifier, Integer> pieces, int reach, boolean structure) {
	}

	/** A placed site: its plan, the spot it was placed at and every piece found there. */
	private record Placed(Plan plan, BlockPos spot, List<SitePiece> pieces) {
		SitePiece piece(String template) {
			Identifier id = OldRepublicMod.id(template);

			for (SitePiece piece : pieces) {
				if (piece.template().equals(id)) {
					return piece;
				}
			}

			throw new AssertionError(plan.command() + ": no " + id + " piece");
		}
	}

	private static final Plan JEDI_RUIN = new Plan("jedi_ruin", SiteKind.JEDI_RUIN, OldRepublicMod.id("jedi_ruin/courtyard"), Map.of(
			OldRepublicMod.id("jedi_ruin/courtyard"), 1,
			OldRepublicMod.id("jedi_ruin/stair"), 1,
			OldRepublicMod.id("jedi_ruin/listening_hall"), 1,
			OldRepublicMod.id("jedi_ruin/pilgrim_niche"), 1,
			OldRepublicMod.id("jedi_ruin/anchor_cache"), 1), 48, true);
	private static final Plan SITH_TOMB = new Plan("sith_tomb", SiteKind.SITH_TOMB, OldRepublicMod.id("sith_tomb/gate"), Map.of(
			OldRepublicMod.id("sith_tomb/gate"), 1,
			OldRepublicMod.id("sith_tomb/path"), 1,
			OldRepublicMod.id("sith_tomb/dig_camp"), 1,
			OldRepublicMod.id("sith_tomb/processional"), 1,
			OldRepublicMod.id("sith_tomb/burial_chamber"), 1,
			OldRepublicMod.id("sith_tomb/pilgrim_niche"), 1,
			OldRepublicMod.id("sith_tomb/rubble_room"), 1), 80, true);
	private static final Plan WRECK_FIELD = new Plan("wreck_field", SiteKind.WRECK_FIELD, OldRepublicMod.id("wreck_field/hull"), Map.of(
			OldRepublicMod.id("wreck_field/hull"), 1,
			OldRepublicMod.id("wreck_field/path"), 1,
			OldRepublicMod.id("wreck_field/salvage_camp"), 1,
			OldRepublicMod.id("wreck_field/scar"), 2), 64, true);
	private static final Plan REPUBLIC_OUTPOST = new Plan("republic_outpost", SiteKind.REPUBLIC_OUTPOST, OldRepublicMod.id("outpost/republic_post"),
			Map.of(OldRepublicMod.id("outpost/republic_post"), 1), 32, true);
	private static final Plan IMPERIAL_OUTPOST = new Plan("imperial_outpost", SiteKind.IMPERIAL_OUTPOST, OldRepublicMod.id("outpost/imperial_post"),
			Map.of(OldRepublicMod.id("outpost/imperial_post"), 1), 32, true);
	private static final Plan ECHO_SHRINE = new Plan("echo_shrine_jedi", SiteKind.ECHO_SHRINE, OldRepublicMod.id("site/echo_shrine_jedi"),
			Map.of(OldRepublicMod.id("site/echo_shrine_jedi"), 1), 16, false);
	/** In slot order: slot {@code i} lies on the bearing {@code 60 * i} degrees. */
	private static final List<Plan> PLANS = List.of(JEDI_RUIN, SITH_TOMB, WRECK_FIELD, REPUBLIC_OUTPOST, IMPERIAL_OUTPOST, ECHO_SHRINE);

	public static void run(ClientGameTestContext context, TestServerContext server) {
		double[] origin = new double[5];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			origin[0] = p.getX();
			origin[1] = p.getY();
			origin[2] = p.getZ();
			origin[3] = p.getYRot();
			origin[4] = p.getXRot();
		});
		int bx = Mth.floor(origin[0]);
		int bz = Mth.floor(origin[2]);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		try {
			Placed[] placed = new Placed[PLANS.size()];

			for (int slot = 0; slot < PLANS.size(); slot++) {
				placed[slot] = place(server, PLANS.get(slot), slot, bx, bz);
			}

			server.runCommand("gamemode creative @p");
			// The halls, the processional and the burial chamber are unlit stone: night vision so the review can see them.
			server.runCommand("effect give @p minecraft:night_vision infinite 0 true");
			context.waitTicks(2);

			Placed ruin = placed[0];
			Placed tomb = placed[1];
			courtyard(context, server, ruin);
			listeningHall(context, server, ruin);
			gate(context, server, tomb);
			ventTell(context, server, tomb);
			burialChamber(context, server, tomb);
			overview(context, server, placed[2].piece("wreck_field/hull"), new BlockPos(20, 14, -9), new BlockPos(20, 7, 9), "oldrepublic_m3_15_wreck_hull");
			overview(context, server, placed[3].piece("outpost/republic_post"), new BlockPos(-5, 9, -5), new BlockPos(11, 2, 11),
					"oldrepublic_m3_16_republic_outpost");
			overview(context, server, placed[4].piece("outpost/imperial_post"), new BlockPos(-5, 9, -5), new BlockPos(11, 2, 11),
					"oldrepublic_m3_17_imperial_outpost");
			overview(context, server, placed[5].piece("site/echo_shrine_jedi"), new BlockPos(12, 13, -4), new BlockPos(7, 10, 3),
					"oldrepublic_m3_18_echo_shrine");
		} finally {
			// Put back what can be put back: game mode, effects, position, force-loading and camera.
			server.runCommand("gamemode survival @p");
			server.runCommand("effect clear @p minecraft:night_vision");
			server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", origin[0], origin[1], origin[2], origin[3], origin[4]));
			server.runCommand("forceload remove all");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(20);
		}
	}

	// ---------------------------------------------------------------- the worldgen step

	/**
	 * Places one site on its slot's spot and checks it (the worldgen step of 17.2): force-loads the
	 * area, runs {@code /oldrepublic site place}, finds the site's pieces and checks their templates
	 * and anchor cells. Returns the placed site; fails the test when anything is off.
	 */
	private static Placed place(TestServerContext server, Plan plan, int slot, int bx, int bz) {
		BlockPos spot = server.computeOnServer(s -> spot(player(s).level(), slot, bx, bz));
		// A structure's pieces reach out from its start chunk's corner, the shrine's from its stand.
		int cx = plan.structure() ? SectionPos.blockToSectionCoord(spot.getX()) << 4 : spot.getX();
		int cz = plan.structure() ? SectionPos.blockToSectionCoord(spot.getZ()) << 4 : spot.getZ();
		int reach = plan.reach();
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", cx - reach, cz - reach, cx + reach, cz + reach));
		server.waitFor(s -> loaded(player(s).level(), cx, cz, reach), LOAD_TIMEOUT);
		server.runCommand(String.format(Locale.ROOT, "oldrepublic site place %s %d %d %d", plan.command(), spot.getX(), spot.getY(), spot.getZ()));

		BlockPos near = new BlockPos(cx, spot.getY(), cz);
		List<SitePiece> pieces = server.computeOnServer(s -> pieces(player(s).level(), plan, near));

		if (pieces == null) {
			throw new AssertionError(plan.command() + ": nothing was placed at " + spot.toShortString() + " (see the command's reply in the log)");
		}

		Placed placed = new Placed(plan, spot, pieces);
		String failure = server.computeOnServer(s -> check(player(s).level(), placed));

		if (failure != null) {
			throw new AssertionError(plan.command() + " at " + spot.toShortString() + ": " + failure);
		}

		OldRepublicMod.LOGGER.info("World client step: placed {} at {} ({} pieces, start {} {})", plan.command(), spot.toShortString(), pieces.size(),
				pieces.getFirst().origin().toShortString(), pieces.getFirst().rotation());
		return placed;
	}

	/**
	 * The spot of slot {@code slot}: the first of {@link #CANDIDATES} points on the slot's bearing
	 * whose {@link #SAMPLES} columns all end on dry ground, else the driest of them, at its surface
	 * height. Loads (and if needed generates) only the sample chunks.
	 */
	private static BlockPos spot(ServerLevel level, int slot, int bx, int bz) {
		BlockPos best = null;
		int bestDry = -1;

		for (int k = 0; k < CANDIDATES && bestDry < SAMPLES.length; k++) {
			double bearing = Math.toRadians(slot * 60.0 + k * 13.0);
			int radius = RING + k * 64;
			int x = bx + Mth.floor(Math.cos(bearing) * radius);
			int z = bz + Mth.floor(Math.sin(bearing) * radius);
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

	/** True when the column at x, z ends on something that is not a fluid (no sea, lake or river). */
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
	 * Why the placed site is wrong, or null: the template pieces must be exactly the plan's, every
	 * {@code PEDESTAL*} cell must hold a pedestal and no anchor cell may hold stray terrain.
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

					if (anchor.name().startsWith("PEDESTAL") && !state.is(PuzzleBlocks.HOLOCRON_PEDESTAL)) {
						return piece.template() + " " + anchor + " at " + cell.toShortString() + " holds " + state + ", not a holocron pedestal";
					}

					if (strayTerrain(state)) {
						return piece.template() + " " + anchor + " at " + cell.toShortString() + " holds stray terrain " + state;
					}
				}
			}
		}

		return null;
	}

	/** Natural ground, rock, sand, snow, foliage or a fluid: nothing an anchor cell should hold. */
	private static boolean strayTerrain(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.SNOW)
				|| state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.GRAVEL)
				|| state.is(Blocks.SANDSTONE) || state.is(Blocks.CLAY) || !state.getFluidState().isEmpty();
	}

	// ---------------------------------------------------------------- screenshots

	/** 10 and 19: the courtyard from above its north-west corner, then its stair wall before and after Sense. */
	private static void courtyard(ClientGameTestContext context, TestServerContext server, Placed ruin) {
		SitePiece courtyard = ruin.piece("jedi_ruin/courtyard");
		arrive(context, server, feet(courtyard, 3, 12, 1), centre(courtyard, 16, 6, 17), true);
		context.takeScreenshot("oldrepublic_m3_10_watchers_rest_courtyard");

		// The 3x3 veiled wall under the half dome hides the stair mouth; the camera stands over the pool.
		BlockPos mouth = SiteAnchors.find(courtyard, SiteAnchors.Anchor.STAIR_MOUTH);

		if (mouth == null) {
			throw new AssertionError("the courtyard has no STAIR_MOUTH");
		}

		look(context, server, feet(courtyard, 15, 4, 12), centre(courtyard, 15, 5, 18), false);
		context.takeScreenshot("oldrepublic_m3_19_veiled_stone_before");
		int revealed = server.computeOnServer(s -> VeiledStoneBlock.reveal(player(s).level(), mouth, SiteBalance.get().veilRevealTicks));

		if (revealed <= 0) {
			throw new AssertionError("the veiled stone at the courtyard's stair mouth " + mouth.toShortString() + " did not reveal");
		}

		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_19_veiled_stone_after");
	}

	/** 11: the Listening Hall's north wall with all six step stones out after the three counterweights sank. */
	private static void listeningHall(ClientGameTestContext context, TestServerContext server, Placed ruin) {
		SitePiece hall = ruin.piece("jedi_ruin/listening_hall");
		arrive(context, server, feet(hall, 17, 6, 19), centre(hall, 8, 6, 6), false);
		SiteAnchors.Anchor[] counterweights = {SiteAnchors.Anchor.COUNTERWEIGHT_W, SiteAnchors.Anchor.COUNTERWEIGHT_E, SiteAnchors.Anchor.COUNTERWEIGHT_S};

		for (SiteAnchors.Anchor anchor : counterweights) {
			BlockPos counterweight = SiteAnchors.find(hall, anchor);
			boolean sank = counterweight != null && server.computeOnServer(s -> CounterweightStoneBlock.sink(player(s).level(), counterweight, null));

			if (!sank) {
				throw new AssertionError("the Listening Hall's " + anchor + " did not sink at " + counterweight);
			}
		}

		// The repeaters and the step stones sliding out, with margin.
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_m3_11_listening_hall_ledges");
	}

	/** 12: through the obelisk gateway and down the wind-cut steps, from its north side. */
	private static void gate(ClientGameTestContext context, TestServerContext server, Placed tomb) {
		SitePiece gate = tomb.piece("sith_tomb/gate");
		arrive(context, server, feet(gate, 8, 6, 0), centre(gate, 8, 4, 12), true);
		context.takeScreenshot("oldrepublic_m3_12_sith_tomb_gate");
	}

	/**
	 * 13: on the processional's first landing beside its upper vents, which the site tracker kicks
	 * while the player is in the processional; a vent must reach TELL within
	 * {@link #VENT_TELL_TIMEOUT} ticks, and the camera turns to the one that does.
	 */
	private static void ventTell(ClientGameTestContext context, TestServerContext server, Placed tomb) {
		SitePiece processional = tomb.piece("sith_tomb/processional");
		Vec3 stand = feet(processional, 5, 7, 8);
		BlockPos facing = SiteAnchors.find(processional, SiteAnchors.Anchor.VENT_2);

		if (facing == null) {
			throw new AssertionError("the processional has no VENT_2");
		}

		arrive(context, server, stand, Vec3.atCenterOf(facing).add(0, 0.5, 0), false);
		BlockPos[] telling = new BlockPos[1];
		SiteAnchors.Anchor[] vents = {SiteAnchors.Anchor.VENT_1, SiteAnchors.Anchor.VENT_2, SiteAnchors.Anchor.VENT_3, SiteAnchors.Anchor.VENT_4};
		server.waitFor(s -> {
			ServerLevel level = player(s).level();

			for (SiteAnchors.Anchor anchor : vents) {
				for (BlockPos vent : SiteAnchors.all(processional, anchor)) {
					BlockState state = level.getBlockState(vent);

					if (state.is(PuzzleBlocks.TOMB_VENT) && state.getValue(PuzzleStates.PHASE) == PuzzleStates.VentPhase.TELL) {
						telling[0] = vent;
						return true;
					}
				}
			}

			return false;
		}, VENT_TELL_TIMEOUT);

		if (!telling[0].equals(facing)) {
			look(context, server, stand, Vec3.atCenterOf(telling[0]).add(0, 0.5, 0), false);
		} else {
			context.waitTicks(3);
		}

		context.takeScreenshot("oldrepublic_m3_13_processional_vent_tell");
		OldRepublicMod.LOGGER.info("World client step: processional vent {} reached TELL", telling[0].toShortString());
	}

	/** 14: the burial chamber from its south-east corner: the dais, the keeper's wall and the four urns. */
	private static void burialChamber(ClientGameTestContext context, TestServerContext server, Placed tomb) {
		SitePiece chamber = tomb.piece("sith_tomb/burial_chamber");
		look(context, server, feet(chamber, 21, 8, 22), centre(chamber, 10, 2, 10), false);
		context.takeScreenshot("oldrepublic_m3_14_burial_chamber_urns");
	}

	/** 15 to 18: a whole site from above, outside it; the stand is raised clear of the ground there. */
	private static void overview(ClientGameTestContext context, TestServerContext server, SitePiece piece, BlockPos stand, BlockPos target, String screenshot) {
		Vec3 look = centre(piece, target.getX(), target.getY(), target.getZ());
		Vec3 feet = server.computeOnServer(s -> overTerrain(player(s), feet(piece, stand.getX(), stand.getY(), stand.getZ()), look, screenshot));
		arrive(context, server, feet, look, false);
		context.takeScreenshot(screenshot);
	}

	/**
	 * {@code feet} raised over the ground of its column, then further, 2 blocks at a time up to
	 * {@link #VIEW_RISE}, while natural terrain stands between the eye and {@code target} (a hill
	 * between the camera and an outpost). The site's own walls may block the line: only terrain counts.
	 */
	private static Vec3 overTerrain(ServerPlayer p, Vec3 feet, Vec3 target, String screenshot) {
		ServerLevel level = p.level();
		Vec3 stand = clear(level, feet);

		for (int rise = 0; rise <= VIEW_RISE; rise += 2) {
			Vec3 at = stand.add(0, rise, 0);
			BlockHitResult hit = level.clip(new ClipContext(at.add(0, EYE, 0), target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));

			if (hit.getType() == HitResult.Type.MISS || !strayTerrain(level.getBlockState(hit.getBlockPos()))) {
				if (rise > 0) {
					OldRepublicMod.LOGGER.info("World client step {}: the camera rose {} blocks to see over the ground", screenshot, rise);
				}

				return at;
			}
		}

		OldRepublicMod.LOGGER.warn("World client step {}: terrain still blocks the view after rising {} blocks", screenshot, VIEW_RISE);
		return stand;
	}

	// ---------------------------------------------------------------- camera

	/** Teleports to another site and waits until the client has the area and has drawn it. */
	private static void arrive(ClientGameTestContext context, TestServerContext server, Vec3 feet, Vec3 target, boolean aboveGround) {
		Vec3 stand = teleport(server, feet, target, aboveGround);
		int chunkX = SectionPos.blockToSectionCoord(Mth.floor(stand.x));
		int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(stand.z));
		context.waitFor(client -> client.level != null && client.level.hasChunk(chunkX, chunkZ), CLIENT_CHUNK_TIMEOUT);
		context.waitTicks(ARRIVE);
	}

	/** Moves within a site and lets the view settle. */
	private static void look(ClientGameTestContext context, TestServerContext server, Vec3 feet, Vec3 target, boolean aboveGround) {
		teleport(server, feet, target, aboveGround);
		context.waitTicks(SETTLE);
	}

	/**
	 * Puts the player's feet at {@code feet} (raised to just above the ground of that column when
	 * {@code aboveGround}), looking at {@code target}, flying so they hold still. Returns the stand.
	 */
	private static Vec3 teleport(TestServerContext server, Vec3 feet, Vec3 target, boolean aboveGround) {
		Vec3 stand = aboveGround ? server.computeOnServer(s -> clear(player(s).level(), feet)) : feet;
		server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f facing %.3f %.3f %.3f", stand.x, stand.y, stand.z, target.x, target.y, target.z));
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

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private WorldClientSteps() {
	}
}

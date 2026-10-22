package com.oldrepublic.gametest.m4;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.travel.ShipClientState;
import com.oldrepublic.client.travel.ShipScreen;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipActions;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipMenu;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelBlocks;
import com.oldrepublic.travel.TravelData;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.TravelVisits;
import com.oldrepublic.travel.network.FlightPhasePayload;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 client test steps for the ship and travel (W3). {@code M4ClientTest} runs them in the
 * shared seeded world (spec MILESTONE_4.md 17.2). Screenshots are {@code oldrepublic_m4_<nn>_<what>}
 * with {@code nn} from 30 to 39:
 * <ul>
 * <li>30 {@code hulk_tarp}: the Chapter Four hulk under its tarp in its cradle ({@code /oldrepublic ship hulk});</li>
 * <li>31 {@code hulk_sockets}: the Repairs tab of the hulk with two of the four sockets fitted (the plating
 * and the power core, through the socket buttons' server code);</li>
 * <li>37 {@code landing_beacon}: a landing beacon just placed with a real use, its pad laid and its lamp
 * blinking;</li>
 * <li>36 {@code screen_<tab>}: each tab of the repaired ship's screen, opened with a real use from the
 * pilot's seat (status, repairs, nav, hold, crew); 38 {@code destination_map}: the Nav tab's star chart;</li>
 * <li>32 {@code takeoff_dust}, 33 {@code climb}, 34 {@code hyperspace}, 35 {@code landing}: the flight home
 * to the beacon's pad, launched with the Nav tab's Launch button and run with real ticks;</li>
 * <li>39 {@code ship_damage}: the landed ship at 15 hull: the damage burst, sparks and smoke.</li>
 * </ul>
 * Everything happens on a grass stage {@value #LIFT} blocks above the player (open sky, flat
 * natural ground, so the cradle search and the beacon's area check pass the same way every run).
 * The player boards and opens the screen with real use presses; when a press does not reach the
 * ship (a cradle prop in the way), the server does the same thing and the log says so.
 *
 * <p><b>Assertions</b>, on the server: the hulk command makes a HULK record with a body; the fitted
 * sockets are exactly the two fitted; the repaired, fuelled ship is READY; the beacon stands where it
 * was placed and the player's home pad is the area beside it; the launch is to HOME; the flight
 * camera goes out for the climb and back at the Settle (when the client option is on); after the
 * landing the player is off the ship, beside it on the stage, and the ship is READY at the home pad
 * with exactly one jump's fuel and wear taken; at 15 hull the ship is DISABLED and its body shows
 * the failing wear level.
 *
 * <p>Put back at the end: the stage and everything on it (cradle, beacon, pad), the ship (record and
 * body; a ship the player had before is restored), the cradle's site record, the player's travel
 * data and landing record, game mode (survival, as the driver sets it), camera and position. Not put
 * back: the story's ship and landing hooks have been told (the player is not in Chapter Four here).
 */
public final class TravelClientSteps {
	/** Height of the stage above the player's starting position, its highest floor, half-width and air above. */
	private static final int LIFT = 60;
	private static final int MAX_STAGE_Y = 260;
	private static final int HALF = 48;
	private static final int AIR_ABOVE = 12;
	/** Layers per fill command (97 x 97 x 3 stays under the command's 32768-block limit). */
	private static final int SLAB = 3;
	/** The home pad's centre lies this far from the stage centre, on the side away from the hulk. */
	private static final int PAD_OUT = 16;
	/** The beacon's placer stands this far from the post, between it and the pad. */
	private static final int PLACER_BACK = 3;
	/** Standing eye height. */
	private static final double EYE = 1.62;
	/** How far from the body's centre the player stands to use it (its box is 7 wide; the reach is 3). */
	private static final int[] USE_DISTANCES = {5, 6};
	private static final int SETTLE = 30;
	private static final int SCREEN_TIMEOUT = 40;
	/** Use presses tried beside the body, 5 ticks apart. */
	private static final int USE_PRESSES = 4;
	private static final int PHASE_TIMEOUT = 600;
	private static final int LAND_TIMEOUT = 400;
	/** The landing shot is taken once the descending ship is this low over its pad (its dust starts below 8). */
	private static final double LANDING_ALTITUDE = 5;
	/** The hull of the damage shot: below the jump minimum, so DISABLED and failing. */
	private static final int DAMAGED_HULL = 15;
	private static final String[] TABS = {"status", "repairs", "nav", "hold", "crew"};
	private static final String LAUNCH_KEY = "screen.oldrepublic.ship.nav.launch";
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;

	/** What the steps change on the player and put back. */
	private record Saved(TravelData travel, int visits, Optional<ShipRecord> ship, List<WorldSites.SiteRecord> cradles) {
	}

	/** Runs the travel steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
		Saved saved = server.computeOnServer(s -> save(player(s)));
		boolean home = server.computeOnServer(s -> player(s).level().dimension() == Level.OVERWORLD);

		if (!home) {
			throw new AssertionError("The travel steps need the player in the overworld (the beacon works on the home world only)");
		}

		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, MAX_STAGE_Y), start.getZ());
		stage.build(server);

		try {
			server.runCommand("gamemode survival @p");
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
			standOnStage(context, server, stage.centre());
			ShipBalance.Travel travel = server.computeOnServer(s -> ShipBalance.travel());

			BlockPos hulk = hulk(context, server, stage);
			Direction toHulk = cardinal(hulk.getX() - stage.x(), hulk.getZ() - stage.z());
			sockets(context, server, stage, hulk, toHulk);
			repair(context, server);
			BlockPos pad = beacon(context, server, stage, toHulk.getOpposite(), travel);
			board(context, server, hulk, toHulk);
			screens(context, server);
			flight(context, server, stage, pad, travel);
			damage(context, server, pad, toHulk);
		} finally {
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			restore(server, saved);
			stage.clear(server);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				TravelAttachments.set(p, saved.travel());
				p.setAttached(TravelVisits.VISITS, saved.visits());
			});
			server.runCommand("gamemode survival @p");
			server.runCommand("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
			context.waitTicks(10);
		}
	}

	// ---------------------------------------------------------------- the stage

	/** The stage: a grass floor at {@code (x, y - 1, z)} around the player's spot, with air above it. */
	private record Stage(int x, int y, int z) {
		void build(TestServerContext server) {
			fill(server, y, y + AIR_ABOVE, "minecraft:air");
			fill(server, y - 1, y - 1, "minecraft:grass_block");
		}

		/** Removes the floor and everything on it (cradle, beacon, pad), and items dropped near it. */
		void clear(TestServerContext server) {
			fill(server, y - 1, y + AIR_ABOVE, "minecraft:air");
			server.runCommand("kill @e[type=minecraft:item,x=" + x + ",y=" + y + ",z=" + z + ",distance=.." + (HALF + 16) + "]");
		}

		BlockPos centre() {
			return new BlockPos(x, y, z);
		}

		private void fill(TestServerContext server, int fromY, int toY, String block) {
			for (int low = fromY; low <= toY; low += SLAB) {
				int high = Math.min(low + SLAB - 1, toY);
				server.runCommand("fill " + (x - HALF) + " " + low + " " + (z - HALF) + " " + (x + HALF) + " " + high + " " + (z + HALF) + " " + block);
			}
		}
	}

	// ---------------------------------------------------------------- the steps

	/** 30: the hulk command on the stage; returns the hulk's stand. */
	private static BlockPos hulk(ClientGameTestContext context, TestServerContext server, Stage stage) {
		server.runCommand("oldrepublic ship hulk @p");
		ShipRecord record = server.computeOnServer(s -> ShipRegistry.of(s, player(s).getUUID()).orElse(null));

		if (record == null || record.state() != ShipState.HULK) {
			throw new AssertionError("/oldrepublic ship hulk made no HULK record: " + record);
		}

		BlockPos hulk = record.pos().pos();
		boolean body = server.computeOnServer(s -> Ships.ship(s, player(s).getUUID()) != null);

		if (!body || hulk.getY() != stage.y()) {
			throw new AssertionError("The hulk " + (body ? "stands at " + hulk.toShortString() + ", off the stage" : "has no body"));
		}

		Direction toHulk = cardinal(hulk.getX() - stage.x(), hulk.getZ() - stage.z());
		Vec3 ship = Vec3.atBottomCenterOf(hulk);
		Vec3 eye = ship.add(dx(toHulk.getOpposite(), 12) + dx(toHulk.getClockWise(), 7), 6, dz(toHulk.getOpposite(), 12) + dz(toHulk.getClockWise(), 7));
		server.runCommand("gamemode spectator @p");
		view(server, eye, ship.add(0, 1.5, 0));
		context.waitTicks(SETTLE);
		context.takeScreenshot("oldrepublic_m4_30_hulk_tarp");
		standOnStage(context, server, stage.centre());
		server.runCommand("gamemode survival @p");
		return hulk;
	}

	/** 31: the plating and the power core fitted (the socket buttons' code), then the hulk's Repairs tab. */
	private static void sockets(ClientGameTestContext context, TestServerContext server, Stage stage, BlockPos hulk, Direction toHulk) {
		boolean[] done = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ShipBalance b = ShipBalance.get();
			p.getInventory().add(new ItemStack(M3Items.DURASTEEL_PLATE, ShipRules.platesNeeded(b)));
			p.getInventory().add(new ItemStack(M4Items.POWER_CORE));
			ShipActions.fitSocket(p, ShipRules.SOCKET_HULL);
			ShipActions.fitSocket(p, ShipRules.SOCKET_CORE_INDEX);
			ShipRecord r = ShipRegistry.of(s, p.getUUID()).orElseThrow();
			boolean[] fitted = new boolean[ShipRules.SOCKETS];

			for (int socket = 0; socket < ShipRules.SOCKETS; socket++) {
				fitted[socket] = ShipRules.socketDone(r, socket, b);
			}

			return fitted;
		});

		if (!done[ShipRules.SOCKET_HULL] || !done[ShipRules.SOCKET_CORE_INDEX] || done[ShipRules.SOCKET_MOTIVATOR_INDEX] || done[ShipRules.SOCKET_NAV_INDEX]) {
			throw new AssertionError("Fitting the plating and the core left the sockets at " + Arrays.toString(done));
		}

		useBody(context, server, hulk, toHulk, "the hulk's screen", c -> c.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen),
				s -> ShipMenu.open(player(s), ShipRegistry.of(s, player(s).getUUID()).orElseThrow()));
		tab(context, "repairs", false);
		context.takeScreenshot("oldrepublic_m4_31_hulk_sockets");
		closeScreen(context, server);
		standOnStage(context, server, stage.centre());
	}

	/** The other two sockets and a full tank by command (the stand-up effect plays): the ship is READY. */
	private static void repair(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic ship repair @p");
		server.runCommand("oldrepublic ship fuel @p");
		context.waitTicks(SETTLE);
		ShipRecord r = server.computeOnServer(s -> ShipRegistry.of(s, player(s).getUUID()).orElseThrow());

		if (r.state() != ShipState.READY || r.hull() != 100 || r.fuel() != ShipRules.tankSize(r, ShipBalance.get())) {
			throw new AssertionError("The repaired, fuelled ship is not READY at full hull and tank: " + r.state() + ", hull " + r.hull() + ", fuel " + r.fuel());
		}
	}

	/**
	 * 37: a landing beacon placed with a real use, {@code away} from the hulk: the placer stands
	 * between the post and the pad facing the post, so the pad is laid on their side. Returns the
	 * home pad.
	 */
	private static BlockPos beacon(ClientGameTestContext context, TestServerContext server, Stage stage, Direction away, ShipBalance.Travel travel) {
		int reach = travel.beaconArea / 2 + 1;
		BlockPos centre = stage.centre().relative(away, PAD_OUT);
		BlockPos post = centre.relative(away, reach);
		BlockPos placer = post.relative(away.getOpposite(), PLACER_BACK);
		server.runCommand("item replace entity @p weapon.mainhand with oldrepublic:landing_beacon");
		Vec3 eye = Vec3.atBottomCenterOf(placer).add(0, EYE, 0);
		// The top face of the ground block under the post: nothing lies on the ray before it.
		view(server, eye, Vec3.atBottomCenterOf(post));
		context.waitTicks(10);
		context.getInput().pressKey(USE);
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m4_37_landing_beacon");
		Optional<GlobalPos> home = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			return level.getBlockState(post).is(TravelBlocks.LANDING_BEACON) ? TravelAttachments.get(player(s)).homePad() : Optional.<GlobalPos>empty();
		});

		if (home.isEmpty() || home.get().pos().getX() != centre.getX() || home.get().pos().getZ() != centre.getZ()) {
			throw new AssertionError("A beacon placed at " + post.toShortString() + " should stand there and make " + centre.toShortString()
					+ " the home pad; the home pad is " + home.map(g -> g.pos().toShortString()).orElse("none"));
		}

		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		context.waitTicks(20);
		return home.get().pos();
	}

	/** The owner boards with a real use. */
	private static void board(ClientGameTestContext context, TestServerContext server, BlockPos ship, Direction toHulk) {
		useBody(context, server, ship, toHulk, "boarding", c -> server.computeOnServer(s -> Ships.isAboard(player(s))), s -> {
			ShipBody body = Ships.ship(s, player(s).getUUID());

			if (body == null || !player(s).startRiding(body, true, false)) {
				throw new AssertionError("The owner could not board their READY ship");
			}
		});
	}

	/** 36 and 38: the screen from the pilot's seat, each tab and the star chart; then Launch to HOME. */
	private static void screens(ClientGameTestContext context, TestServerContext server) {
		context.getInput().lookAt(context.computeOnClient(mc -> mc.player.getYRot()), 10f);
		context.waitTicks(5);
		context.getInput().pressKey(USE);

		if (!poll(context, c -> c.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen), SCREEN_TIMEOUT)) {
			OldRepublicMod.LOGGER.warn("Travel client step: a use from the pilot's seat opened no ship screen; opening it on the server");
			server.runOnServer(s -> ShipMenu.open(player(s), ShipRegistry.of(s, player(s).getUUID()).orElseThrow()));
		}

		for (String id : TABS) {
			tab(context, id, false);
			context.takeScreenshot("oldrepublic_m4_36_screen_" + id);
		}

		tab(context, "nav", true);
		context.takeScreenshot("oldrepublic_m4_38_destination_map");
		TravelService.Refusal refusal = server.computeOnServer(s -> TravelService.canLaunch(player(s), Destination.HOME));

		if (refusal != TravelService.Refusal.NONE) {
			throw new AssertionError("The flight home is refused: " + refusal);
		}

		tab(context, "nav", false);
		context.clickScreenButton(LAUNCH_KEY);
	}

	/** 32 to 35: the flight home with real ticks; then the landing is checked. */
	private static void flight(ClientGameTestContext context, TestServerContext server, Stage stage, BlockPos pad, ShipBalance.Travel t) {
		ShipBalance b = server.computeOnServer(s -> ShipBalance.get());
		ShipRecord before = server.computeOnServer(s -> ShipRegistry.of(s, player(s).getUUID()).orElseThrow());
		context.waitFor(mc -> phase() == ShipRules.PHASE_SPOOL, SCREEN_TIMEOUT);
		Destination to = server.computeOnServer(s -> ShipRegistry.of(s, player(s).getUUID()).flatMap(ShipRecord::flight).map(ShipRecord.Flight::to).orElse(null));

		if (to != Destination.HOME) {
			throw new AssertionError("Launch from the Nav tab started a flight to " + to + ", not HOME");
		}

		// 32: from behind, while the engines spool and the dust blows out; first person again before the climb.
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(t.spoolTicks * 3 / 5);
		context.takeScreenshot("oldrepublic_m4_32_takeoff_dust");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		// 33: the flight camera goes out behind the ship by itself.
		context.waitFor(mc -> phase() == ShipRules.PHASE_CLIMB, PHASE_TIMEOUT);
		context.waitTicks(3);
		boolean shipCamera = context.computeOnClient(mc -> ClientConfig.get().shipCamera);
		CameraType climbing = context.computeOnClient(mc -> mc.options.getCameraType());

		if (shipCamera && climbing != CameraType.THIRD_PERSON_BACK) {
			throw new AssertionError("The flight camera did not go out for the climb: " + climbing);
		}

		context.waitTicks(t.climbTicks / 2);
		context.takeScreenshot("oldrepublic_m4_33_climb");

		// 34: inside the jump.
		context.waitFor(mc -> phase() == ShipRules.PHASE_HYPERSPACE, PHASE_TIMEOUT);
		context.waitTicks(t.hyperspaceTicks * 2 / 5);
		context.takeScreenshot("oldrepublic_m4_34_hyperspace");

		// 35: low over the pad, the dust rising.
		context.waitFor(mc -> {
			FlightPhasePayload f = ShipClientState.flight();
			Entity ship = mc.player.getVehicle();
			return f != null && f.phase() == ShipRules.PHASE_DESCENT && ship != null && ship.getY() - f.padY() < LANDING_ALTITUDE;
		}, PHASE_TIMEOUT);
		context.waitTicks(2);
		context.takeScreenshot("oldrepublic_m4_35_landing");

		context.waitFor(mc -> ShipClientState.flight() == null, LAND_TIMEOUT);
		server.waitFor(s -> !Ships.isAboard(player(s)) && ShipRegistry.of(s, player(s).getUUID()).map(r -> r.flight().isEmpty()).orElse(false), LAND_TIMEOUT);
		context.waitTicks(10);
		CameraType landed = context.computeOnClient(mc -> mc.options.getCameraType());

		if (shipCamera && !landed.isFirstPerson()) {
			throw new AssertionError("The flight camera was not put back at the Settle: " + landed);
		}

		ShipRecord expected = ShipRules.afterJump(before, b);
		String problem = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ShipRecord r = ShipRegistry.of(s, p.getUUID()).orElseThrow();
			ShipBody ship = Ships.ship(s, p.getUUID());

			if (r.state() != ShipState.READY || r.hull() != expected.hull() || r.fuel() != expected.fuel()) {
				return "the landed ship is " + r.state() + " at hull " + r.hull() + ", fuel " + r.fuel() + " (expected READY, " + expected.hull() + ", " + expected.fuel() + ")";
			}

			if (horizontal(r.pos().pos(), pad) > 2 || r.pos().dimension() != Level.OVERWORLD) {
				return "the ship landed at " + r.pos().pos().toShortString() + ", not on the home pad " + pad.toShortString();
			}

			if (ship == null || p.distanceTo(ship) > 9 || p.getY() < stage.y() - 0.5) {
				return "the player is not beside the ship on the stage after the Settle (at " + p.blockPosition().toShortString() + ")";
			}

			return null;
		});

		if (problem != null) {
			throw new AssertionError("After the flight home: " + problem);
		}
	}

	/** 39: the landed ship knocked down to 15 hull: the damage burst, then sparks and smoke at a nacelle. */
	private static void damage(ClientGameTestContext context, TestServerContext server, BlockPos pad, Direction toHulk) {
		Vec3 ship = Vec3.atBottomCenterOf(pad);
		Direction side = toHulk.getClockWise();
		Vec3 eye = ship.add(dx(side, 11) + dx(toHulk, 4), 4, dz(side, 11) + dz(toHulk, 4));
		server.runCommand("gamemode spectator @p");
		view(server, eye, ship.add(0, 1.5, 0));
		context.waitTicks(SETTLE);
		server.runCommand("oldrepublic ship hull @p " + DAMAGED_HULL);
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m4_39_ship_damage");
		String problem = server.computeOnServer(s -> {
			ShipRecord r = ShipRegistry.of(s, player(s).getUUID()).orElseThrow();
			ShipBody body = Ships.ship(s, r.owner());

			if (r.state() != ShipState.DISABLED || body == null || body.wearLevel() != ShipRules.WEAR_FAILING) {
				return r.state() + ", body wear " + (body == null ? "no body" : body.wearLevel());
			}

			return null;
		});

		if (problem != null) {
			throw new AssertionError("At " + DAMAGED_HULL + " hull the ship should be DISABLED and failing: " + problem);
		}

		server.runCommand("oldrepublic ship hull @p 100");
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- using the body

	/** A check of what a use did, read from the test thread. */
	private interface Done {
		boolean check(ClientGameTestContext context);
	}

	/** What the server does when the real use did not reach the body. */
	private interface Fallback {
		void run(MinecraftServer server);
	}

	/**
	 * Stands the player beside the body at {@code ship} (the side facing the stage centre first, then
	 * the others), looking at it, and presses use until {@code done}; when no spot or press works,
	 * {@code fallback} runs on the server and the log says so.
	 */
	private static void useBody(ClientGameTestContext context, TestServerContext server, BlockPos ship, Direction toHulk, String what, Done done, Fallback fallback) {
		BlockPos stand = server.computeOnServer(s -> standpoint(player(s).level(), ship, toHulk.getOpposite()));

		if (stand != null) {
			Vec3 eye = Vec3.atBottomCenterOf(stand).add(0, EYE, 0);
			view(server, eye, Vec3.atBottomCenterOf(ship).add(0, 1.5, 0));
			context.waitTicks(10);

			for (int i = 0; i < USE_PRESSES; i++) {
				context.getInput().pressKey(USE);
				context.waitTicks(4);

				if (done.check(context)) {
					return;
				}
			}
		}

		OldRepublicMod.LOGGER.warn("Travel client step: a use beside the ship did not reach it for {} (stand {}); doing it on the server", what, stand);
		server.runOnServer(fallback::run);

		if (!poll(context, done, SCREEN_TIMEOUT)) {
			throw new AssertionError("Neither a use nor the server could do " + what);
		}
	}

	/**
	 * A free spot beside the body at {@code ship} within reach of its box, with clear air to it at eye
	 * level: {@code first} side first, then the others. Null when there is none.
	 */
	private static @Nullable BlockPos standpoint(ServerLevel level, BlockPos ship, Direction first) {
		for (Direction side : List.of(first, first.getClockWise(), first.getCounterClockWise(), first.getOpposite())) {
			for (int distance : USE_DISTANCES) {
				BlockPos feet = ship.relative(side, distance);

				if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir() || level.getBlockState(feet.below()).isAir()) {
					continue;
				}

				boolean clear = true;

				for (int k = 4; k < distance && clear; k++) {
					clear = level.getBlockState(ship.relative(side, k).above()).isAir();
				}

				if (clear) {
					return feet;
				}
			}
		}

		return null;
	}

	// ---------------------------------------------------------------- the screen

	/** Waits for the ship screen to show tab {@code id} (it has the owner's tabs once its status arrived). */
	private static void tab(ClientGameTestContext context, String id, boolean chart) {
		context.waitFor(mc -> mc.gui.screen() instanceof ShipScreen screen && screen.openTab(id, chart), SCREEN_TIMEOUT);
		context.waitTicks(8);
	}

	private static void closeScreen(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> player(s).closeContainer());
		context.waitFor(mc -> mc.gui.screen() == null, SCREEN_TIMEOUT);
	}

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** The phase of the player's flight as the client knows it, or {@link ShipRules#PHASE_GROUND}. */
	private static int phase() {
		FlightPhasePayload f = ShipClientState.flight();
		return f == null ? ShipRules.PHASE_GROUND : f.phase();
	}

	/** Puts the player on the stage at {@code pos} (feet), facing south. */
	private static void standOnStage(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 0", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
		context.waitTicks(10);
	}

	/** Puts the player's eye at {@code eye}, looking at {@code at} (a server teleport, so both sides agree). */
	private static void view(TestServerContext server, Vec3 eye, Vec3 at) {
		Vec3 d = at.subtract(eye);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
		server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - EYE, eye.z, yaw, pitch));
	}

	/** The horizontal direction nearest to ({@code dx}, {@code dz}). */
	private static Direction cardinal(int dx, int dz) {
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}

		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	private static double dx(Direction d, double distance) {
		return d.getStepX() * distance;
	}

	private static double dz(Direction d, double distance) {
		return d.getStepZ() * distance;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double x = a.getX() - b.getX();
		double z = a.getZ() - b.getZ();
		return Math.sqrt(x * x + z * z);
	}

	/** Checks {@code done} every tick for up to {@code ticks} ticks. */
	private static boolean poll(ClientGameTestContext context, Done done, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (done.check(context)) {
				return true;
			}

			context.waitTick();
		}

		return false;
	}

	// ---------------------------------------------------------------- putting back

	private static Saved save(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		UUID id = p.getUUID();
		List<WorldSites.SiteRecord> cradles = WorldSites.all(server).stream()
				.filter(site -> site.kind() == SiteKind.SHIP_CRADLE && id.equals(site.owner())).toList();
		return new Saved(TravelAttachments.get(p), p.getAttachedOrCreate(TravelVisits.VISITS), ShipRegistry.of(server, id), cradles);
	}

	/**
	 * Off the ship and out of its screen; the test's ship gone (record, then body) and any earlier one
	 * put back; the cradle records this run added removed, every other site record kept in order.
	 */
	private static void restore(TestServerContext server, Saved saved) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			UUID id = p.getUUID();
			p.closeContainer();
			p.stopRiding();
			ShipBody body = Ships.ship(s, id);
			ShipRegistry.Data ships = ShipRegistry.snapshot(s);
			ships.remove(id);
			saved.ship().ifPresent(ships::put);
			ShipRegistry.commit(s, ships);

			if (body != null && saved.ship().map(r -> !r.body().equals(body.getUUID())).orElse(true)) {
				body.ejectPassengers();
				body.discard();
			}

			saved.ship().ifPresent(r -> Ships.restore(s, id, true));
			WorldSites.Data sites = WorldSites.snapshot(s);
			WorldSites.Data kept = new WorldSites.Data();

			for (WorldSites.SiteRecord site : sites.all()) {
				if (site.kind() != SiteKind.SHIP_CRADLE || !id.equals(site.owner()) || saved.cradles().contains(site)) {
					kept.add(site);
				}
			}

			sites.expiredCairnOwners().forEach(kept::expireCairnOwner);
			WorldSites.commit(s, kept);
		});
	}

	private TravelClientSteps() {
	}
}

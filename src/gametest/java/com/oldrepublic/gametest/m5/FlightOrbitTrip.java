package com.oldrepublic.gametest.m5;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.travel.FlightClient;
import com.oldrepublic.client.travel.Piloting;
import com.oldrepublic.client.travel.ShipClientState;
import com.oldrepublic.client.travel.ShipScreen;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.FlightRules;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.network.FlightStatusPayload;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The orbit trip of the flight client test (spec MILESTONE_5.md 4.5 to 4.7 and 19.2; W1's file).
 * {@code FlightClientTest} calls {@link #run} right after {@link FlightSpike#run}, on the same
 * dedicated server ({@code allow-flight=false}, Peaceful, every destination unlocked), so the server
 * checks every move the pilot's client sends.
 *
 * <p><b>Steps.</b> The spike put its pilot off in the air, so the rescue first flies the ship home
 * to its pad (4.10); the trip waits for it to rest there, puts the owner beside it and boards. Then,
 * at the helm by a real press of the Helm key:
 * <ol>
 * <li>the climb: up on the thrusters, then the nose 60° up under thrust and Jump, heading for the
 * world's centre, through the atmosphere line at {@code flight.orbitCeiling} into
 * {@code orbit_home};</li>
 * <li>a stop in the orbit while the crossings' cooldown runs out;</li>
 * <li>the dive back: down on the descent thrusters (Sneak), the nose level, through
 * {@code flight.atmosphereLine} into the home world's sky;</li>
 * <li>the climb again, once the cooldown has run out, into {@code orbit_home}, and a stop;</li>
 * <li>the jump: the inventory key opens the ship screen at the helm; on the Nav tab a click on the
 * mode button lists the jumps, a click picks Saltgate's row, and Jump with its confirm send the ship
 * through the Jump and Hyperspace phases into {@code orbit_ghessar}, where the helm comes back;</li>
 * <li>the dive into Ghessar's sky as before; then across to Saltgate's pad by hand, down over it
 * (fast while high, then slower than {@code flight.landMaxSpeed}) until the landing check says
 * {@code HUB}, and the Helm key: the glide onto the first free bay, the landing and the Settle.</li>
 * </ol>
 * All flying is real key holds and the pilot's look, one tick at a time, as in the spike; the ship
 * screen is worked with real mouse clicks, its tab aside.
 *
 * <p><b>Assertions</b>, on the server unless said otherwise, each failure carrying both sides' view:
 * the ship rests {@code READY} after the spike with the hull and a cell for the jump; the Helm key
 * hands it over ({@code FLYING}, flown by its owner on both sides, risen {@code helmRiseBlocks}); the
 * atmosphere overlay comes up on the client only once the ship is past the line; each crossing puts
 * the ship in the other world where the crossing rule ({@link FlightRules#toOrbit},
 * {@link FlightRules#toSky}) puts it for its place at the line (or where
 * {@link FlightRules#clearArrival} moved it off blocks there), still {@code FLYING} with no autopilot
 * run and its record's last landing spot unchanged, the pilot flying it again on their client, whose
 * status carries that world's top speed and the orbit cue only in a sky; the confirm sends the ship
 * on a run to Saltgate, which comes out of hyperspace in {@code orbit_ghessar} above a Saltgate bay
 * divided by 8 at {@code flight.jumpArrivalY} (above (0, 0) while Saltgate was not placed), with
 * the helm back, one cell spent and the last landing kept; Saltgate is placed; over its pad the
 * landing check says {@code HUB}; the Helm key starts the landing on the client; afterwards the ship
 * rests on one of Saltgate's bays as the Settle leaves it ({@code READY} with the given ship's full
 * tank), the owner's last landing, the owner off it and their client off the helm.
 *
 * <p><b>Shortcuts</b>, each logged ({@code Orbit trip shortcut: ...}) and listed at the end: the
 * owner is put beside the ship's pad by a server teleport (the spike left them where it touched
 * down) and boards on the server, as in the spike; the Nav tab is opened through
 * {@link ShipScreen#openTab}; Saltgate's row is chosen through {@link ShipScreen#selectDestination}
 * only if the click on it did not enable Jump; any sandstorm on Ghessar is stopped by command for the
 * landing shot.
 *
 * <p>Every wait is time-boxed and fails with what it waited for. Screenshots, in the lead's band:
 * {@code oldrepublic_m5_091_orbit_liftoff} (early in the climb), {@code 092_atmosphere_line} (the
 * overlay at the line), {@code 093_orbit} (stopped in {@code orbit_home}) and
 * {@code 094_saltgate_landing} (the struts down over the bay). Afterwards the owner stands at
 * Saltgate beside their ship; the camera is put back to first person. W3's members called:
 * {@link PlanetHubs#hub} and {@link PlanetHubs#padBays} (and its {@code planet sandstorm stop}
 * command); W2's orbits are flown through, but none of its members is called.
 */
public final class FlightOrbitTrip {
	// ---------------------------------------------------------------- time boxes (ticks)

	/** The spike's rescue run home: the Jump, Hyperspace, Descent and Settle (about 200 ticks), and the waits for chunks. */
	private static final int REST_TIMEOUT = 900;
	/** The ship's body on the server and on the client once the owner stands beside it; boarding. */
	private static final int BODY_TIMEOUT = 200;
	private static final int BOARD_TIMEOUT = 60;
	/** The Spool (40) and the rise (20), with slack. */
	private static final int HELM_TIMEOUT = 200;
	/** From the hand-over (or the sky) to the crossing into the orbit: the climb and the wait for the arrival's chunks. */
	private static final int CLIMB_TIMEOUT = 700;
	/** The pilot's client flying the ship again after a crossing or a jump (it loads the new world first). */
	private static final int REPILOT_TIMEOUT = 200;
	/** The pilot's status catching up with a new world (it is sent every 10 ticks). */
	private static final int STATUS_TIMEOUT = 30;
	/** Coming to a stop in an orbit. */
	private static final int STOP_TIMEOUT = 120;
	/** Down on the descent thrusters (0.4 blocks a tick) from an orbit's arrival height to the line, and the crossing. */
	private static final int SINK_TIMEOUT = 800;
	/** The ship screen opening, its view of the ship in orbit, closing; the server taking the ship after the confirm. */
	private static final int SCREEN_TIMEOUT = 60;
	private static final int LAUNCH_TIMEOUT = 60;
	/** Beyond the Jump, its longest wait for the arrival and the Hyperspace phase. */
	private static final int JUMP_SLACK = 200;
	/** Saltgate placed on Ghessar (the jump asked for it). */
	private static final int HUB_TIMEOUT = 1200;
	/** Across to the pad and about 200 blocks down to it. */
	private static final int DESCENT_TIMEOUT = 1400;
	/** The Helm key starting the landing; the glide, the landing and the Settle; the client leaving the helm. */
	private static final int LAND_START_TIMEOUT = 40;
	private static final int LAND_TIMEOUT = 400;
	private static final int RELEASE_TIMEOUT = 60;

	// ---------------------------------------------------------------- flying

	/** The first climb starts with this many ticks straight up on the thrusters (clear of trees), then the nose goes up. */
	private static final int LIFT_TICKS = 20;
	/** The climb's nose: Minecraft's pitch, negative is up (the courier's pitch limit). */
	private static final float CLIMB_PITCH = -60f;
	/** Ticks past the cooldown the trip waits before the next crossing. */
	private static final int COOLDOWN_SLACK = 10;
	/** Closer than this to (0, 0) the climb keeps its heading instead of turning for the world's centre. */
	private static final double CENTRE_NEAR = 8.0;
	/** Server samples kept for matching an arrival to the place the ship crossed the line from (a crossing waits at most 60 ticks). */
	private static final int TRAIL = 80;
	/** An arrival this close to where the crossing or jump rule puts it passes (blocks). */
	private static final double ARRIVAL_SLACK = 3.0;
	/** The rise before the hand-over may fall this short of {@code flight.helmRiseBlocks}. */
	private static final double RISE_SLACK = 0.5;
	/** Stopped: slower than this (blocks a tick) for {@link #STILL_TICKS} ticks, once the nose has had time to level. */
	private static final double STILL_SPEED = 0.03;
	private static final int STILL_TICKS = 5;
	private static final int LEVEL_TICKS = 12;
	/** Speeds within this of the one wanted are left alone; the brake goes on above it. */
	private static final double SPEED_BAND = 0.03;
	/** The approach to the pad: the most speed across, the speed wanted per block still to go, and when it is over the pad. */
	private static final double APPROACH_SPEED = 0.8;
	private static final double APPROACH_GAIN = 0.1;
	private static final double OVER_PAD = 2.0;
	/** Only looks along the way to the pad thrusts toward it. */
	private static final float FACING = 30f;
	/** The descent starts within this of the pad's centre, moving slower than {@link #DESCEND_FLAT} across. */
	private static final double DESCEND_WITHIN = 6.0;
	private static final double DESCEND_FLAT = 0.3;
	/** Below this height over the ground the descent goes at a walk, under {@code flight.landMaxSpeed} (0.25). */
	private static final double SLOW_GAP = 14.0;
	private static final double WALK_SPEED = 0.12;
	/** Ready to land: the check says HUB and the ship moves slower than this for this many ticks. */
	private static final double CALM_SPEED = 0.1;
	private static final int CALM_TICKS = 3;
	/** A landing check that is neither too high, too fast nor HUB for this long over the pad fails the trip. */
	private static final int WRONG_TICKS = 30;

	// ---------------------------------------------------------------- shots and logs

	/** Shot 091 this many ticks into the first climb; shot 092 this many ticks after the overlay came up (it covers the screen in 12). */
	private static final int LIFTOFF_SHOT = 30;
	private static final int ATMOSPHERE_SHOT = 8;
	/** Shot 094 when the struts go down over the bay, or this many ticks into the landing at the latest. */
	private static final int LANDING_SHOT_LATEST = 60;
	private static final int LOG_EVERY = 40;

	// ---------------------------------------------------------------- the ship screen (MILESTONE_5.md 13.2)

	private static final String NAV_MODE_LAND = "screen.oldrepublic.ship.nav.mode.land";
	private static final String NAV_MODE_JUMP = "screen.oldrepublic.ship.nav.mode.jump";
	private static final String NAV_JUMP = "screen.oldrepublic.ship.nav.jump";
	private static final String NAV_CONFIRM = "screen.oldrepublic.ship.nav.confirm";
	/** The Nav tab's list as {@code ShipScreen} lays it out (its WIDTH, HEIGHT and ROW_* constants and BUTTON_Y). */
	private static final int SCREEN_W = 272;
	private static final int SCREEN_H = 204;
	private static final int ROW_X = 12;
	private static final int ROW_W = 248;
	private static final int ROW_Y = 42;
	private static final int ROW_H = 22;
	private static final int ROW_STEP = 24;
	private static final int ROW_STEP_TIGHT = 22;
	private static final int BUTTON_Y = 178;

	/** The keys the trip holds. */
	private enum Key {
		FORWARD(o -> o.keyUp),
		BACK(o -> o.keyDown),
		JUMP(o -> o.keyJump),
		SNEAK(o -> o.keyShift);

		final Function<Options, KeyMapping> mapping;

		Key(Function<Options, KeyMapping> mapping) {
			this.mapping = mapping;
		}
	}

	/** The server's balance the trip measures against (read once, on the server). */
	private record Numbers(FlightBalance flight, ShipBalance ship, ShipBalance.Travel travel) {
		static Numbers read() {
			return new Numbers(FlightBalance.get(), ShipBalance.get(), ShipBalance.travel());
		}
	}

	/** What the server holds: the owner's ship's body and record, and the owner. */
	private record ServerView(@Nullable ResourceKey<Level> level, @Nullable Vec3 pos, @Nullable ShipState state, Optional<ShipRecord.Flight> flight,
			boolean busy, int hull, int fuel, int upgrades, @Nullable GlobalPos pad, boolean aboard, boolean piloting, boolean helmUnlocked, int players) {
		boolean in(ResourceKey<Level> key) {
			return key.equals(level);
		}

		String levelName() {
			return level == null ? "no world (no body loaded)" : level.identifier().toString();
		}

		String describe() {
			return "server: ship " + fmt(pos) + " in " + levelName() + ", record " + state
					+ flight.map(f -> " on a run to " + f.to().id() + " (phase " + f.phase() + ")").orElse("") + (busy ? " (busy)" : "") + ", hull " + hull
					+ ", fuel " + fuel + ", last landing " + where(pad) + ", owner " + (piloting ? "piloting" : aboard ? "aboard" : "not aboard") + ", helm "
					+ (helmUnlocked ? "unlocked" : "locked") + ", " + players + " player(s)";
		}
	}

	/** What the pilot's client shows. */
	private record ClientView(boolean connected, @Nullable ResourceKey<Level> level, @Nullable Vec3 ship, float yaw, boolean piloting,
			@Nullable FlightStatusPayload status, boolean atmosphere, @Nullable String screen, boolean gear) {
		String describe() {
			FlightStatusPayload s = status;
			return "client: " + (connected ? "" : "disconnected, ") + "in " + (level == null ? "no world" : level.identifier().toString()) + ", ship "
					+ fmt(ship) + (piloting ? " piloted" : " not piloted") + ", helm "
					+ (s == null ? "none" : s.helm() + " (landing " + FlightRules.Landing.of(s.landing()) + ", cooldown " + s.cooldown() + ")")
					+ (atmosphere ? ", atmosphere overlay up" : "") + (screen == null ? "" : ", screen " + screen);
		}
	}

	/** An arrival matched to the rule's place for one of the ship's places before it crossed: how far off, how many samples back, and the rule's place. */
	private record Match(double distance, int back, Vec3 expected) {
	}

	private final ClientGameTestContext context;
	private final TestDedicatedServerContext server;
	private final UUID owner;
	private final TestInput input;
	private final boolean invert;
	private final Numbers numbers;
	private final Set<Key> held = EnumSet.noneOf(Key.class);
	private final List<String> shortcuts = new ArrayList<>();
	/** Client ticks the trip has waited. */
	private int ticks;
	/** The trip's tick when the server last showed the ship across a sky or orbit line (the cooldown runs from it). */
	private int crossedAt = Integer.MIN_VALUE / 2;
	/** The ship's last landing spot at the start: its record keeps it until the landing by hand. */
	private @Nullable GlobalPos homePad;

	/** Runs the trip on the driver's dedicated server, after the flight spike; throws when a step fails or does not happen in time. */
	public static void run(ClientGameTestContext context, TestDedicatedServerContext server) {
		UUID owner = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getUUID());
		FlightOrbitTrip trip = new FlightOrbitTrip(context, server, owner);

		try {
			trip.board();
			trip.takeHelm();
			trip.climb(true);
			trip.holdStill("the stop in orbit");
			trip.orbitShot();
			trip.sink(Planets.Planet.HOME);
			trip.climbBack();
			trip.holdStill("the second stop in orbit");
			trip.jumpToGhessar();
			trip.sink(Planets.Planet.GHESSAR);
			trip.landAtSaltgate();
			trip.log("done; shortcuts: {}", trip.shortcuts.isEmpty() ? "none" : String.join("; ", trip.shortcuts));
		} finally {
			trip.cleanUp();
		}
	}

	private FlightOrbitTrip(ClientGameTestContext context, TestDedicatedServerContext server, UUID owner) {
		this.context = context;
		this.server = server;
		this.owner = owner;
		this.input = context.getInput();
		this.invert = context.computeOnClient(mc -> ClientConfig.get().invertShipPitch);
		this.numbers = server.computeOnServer(s -> Numbers.read());
	}

	// ---------------------------------------------------------------- the start

	/**
	 * Waits for the spike's rescue run to bring the ship home and checks it can make the trip; puts
	 * the owner beside the pad and boards on the server, as the spike does.
	 */
	private void board() {
		String leg = "the start";
		server.runCommand("oldrepublic ship helm @p");
		ServerView rest = awaitServer(REST_TIMEOUT, "the ship to rest on its pad after the flight spike's rescue run", v -> v.state() != null && !v.busy());

		if (rest.state() != ShipState.READY) {
			throw fail(leg, "the ship rests " + rest.state() + ", not READY; " + rest.describe());
		}

		ShipBalance b = numbers.ship();

		if (rest.hull() < b.jumpMinHull || rest.fuel() < b.cellsPerJump) {
			throw fail(leg, "the ship could not make the jump: hull " + rest.hull() + " (at least " + b.jumpMinHull + "), fuel " + rest.fuel() + " (at least "
					+ b.cellsPerJump + ")");
		}

		homePad = rest.pad();
		log("{}: the ship rests at {}, hull {}, fuel {}", leg, where(homePad), rest.hull(), rest.fuel());
		String moved = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayer(owner);
			ShipRecord r = ShipRegistry.of(s, owner).orElse(null);
			ServerLevel level = r == null ? null : s.getLevel(r.pos().dimension());

			if (p == null || r == null || level == null || p.level() != level) {
				return null;
			}

			Vec3 base = Vec3.atBottomCenterOf(r.pos().pos());
			double away = p.position().distanceTo(base);
			Vec3 spot = Ships.besidePad(level, base, r.yaw(), Ships.interceptor(r), p);
			p.teleportTo(spot.x, spot.y, spot.z);
			return String.format(Locale.ROOT, "%s, %.0f blocks from where the spike left them", fmt(spot), away);
		});

		if (moved == null) {
			throw fail(leg, "the owner is not in their ship's world to board it; " + rest.describe());
		}

		shortcut("put the owner beside their ship's pad by a server teleport, at " + moved);
		awaitServer(BODY_TIMEOUT, "the ship's body on its pad on the server", v -> v.pos() != null);
		int id = server.computeOnServer(s -> {
			ShipBody body = Ships.ship(s, owner);
			return body == null ? -1 : body.getId();
		});
		awaitClient(BODY_TIMEOUT, "the ship's body on the owner's client", mc -> mc.level != null && mc.level.getEntity(id) instanceof ShipBody);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayer(owner);
			ShipBody body = Ships.ship(s, owner);

			if (p == null || body == null || !p.startRiding(body, true, false)) {
				throw new AssertionError("The orbit trip (MILESTONE_5.md 19.2) failed in the start: the owner could not board their ship");
			}
		});
		shortcut("the owner boarded on the server (startRiding), as the flight spike does");
		awaitClient(BOARD_TIMEOUT, "the owner aboard their ship on the client", mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody);
		input.lookAt(client().yaw(), 0f);
		tick(5);
	}

	/** A real press of the Helm key: the Spool, the rise and the hand-over (4.2). */
	private void takeHelm() {
		String leg = "taking the helm";
		Vec3 rest = view().pos();

		if (rest == null) {
			throw fail(leg, "the ship's body is not loaded on the server; " + views());
		}

		tap(FlightClient.HELM);
		awaitClient(HELM_TIMEOUT, "the Helm key to hand the ship to its pilot", mc -> Piloting.isPiloting());
		ServerView v = view();

		if (v.state() != ShipState.FLYING || !v.piloting() || v.pos() == null) {
			throw fail(leg, "at the hand-over the ship is not flown by its owner; " + views());
		}

		double risen = v.pos().y - rest.y;

		if (risen < numbers.flight().helmRiseBlocks - RISE_SLACK) {
			throw fail(leg, "the ship rose " + fmt2(risen) + " blocks before the hand-over (flight.helmRiseBlocks is " + numbers.flight().helmRiseBlocks + ")");
		}

		cameraBehind();
		log("{}: the owner has the helm {} blocks over where the ship rested", leg, fmt2(risen));
	}

	// ---------------------------------------------------------------- sky and orbit (4.7)

	/**
	 * Forward and Jump with the nose 60° up until the server has the ship in {@code orbit_home}; the
	 * first climb starts straight up and takes shots 091 and 092. The climb heads for the world's
	 * centre: the arrival (x and z divided by 8) then lands in, and the stop after it drifts toward,
	 * the column around (0, 0) that W2's asteroids keep clear.
	 */
	private void climb(boolean first) {
		String leg = first ? "the climb into orbit" : "the second climb into orbit";
		ResourceKey<Level> orbit = Planets.orbit(Planets.Planet.HOME);
		Vec3 from = view().pos();
		float yaw = from == null || from.horizontalDistanceSqr() < CENTRE_NEAR * CENTRE_NEAR ? client().yaw() : yawToward(-from.x, -from.z);
		log("{}: from {}, heading {} (toward the world's centre)", leg, fmt(from), fmt1(yaw));
		List<Vec3> sky = new ArrayList<>();
		int line = -1;
		boolean lineShot = !first;
		// The overlay counts once it comes up: one still clearing from the last crossing does not.
		boolean overlay = client().atmosphere();

		for (int t = 0; ; t++) {
			if (t >= CLIMB_TIMEOUT) {
				throw fail(leg, "the ship did not cross into " + orbit.identifier() + " within " + CLIMB_TIMEOUT + " ticks; " + views());
			}

			boolean lift = first && t < LIFT_TICKS;
			step(lift ? EnumSet.of(Key.JUMP) : EnumSet.of(Key.FORWARD, Key.JUMP), yaw, lift ? 0f : CLIMB_PITCH);
			ServerView v = view();

			if (first && t == LIFTOFF_SHOT) {
				context.takeScreenshot("oldrepublic_m5_091_orbit_liftoff");
			}

			if (v.in(orbit)) {
				if (!lineShot) {
					// Across before the shot's tick: the overlay still covers the orbit's loading.
					context.takeScreenshot("oldrepublic_m5_092_atmosphere_line");
				}

				arrivedInOrbit(leg, v, sky);
				return;
			}

			if (!v.in(Level.OVERWORLD) || v.pos() == null) {
				throw fail(leg, "the ship left the home world's sky without reaching the orbit; " + views());
			}

			sky.addFirst(v.pos());
			trim(sky);
			boolean up = client().atmosphere();
			boolean cameUp = up && !overlay;
			overlay = up;

			if (line < 0 && cameUp) {
				line = t;
				log("{}: the atmosphere overlay came up with the ship at y {} on the server", leg, fmt1(v.pos().y));

				if (v.pos().y <= numbers.flight().orbitCeiling) {
					throw fail(leg, "the atmosphere overlay came up below the line (y " + numbers.flight().orbitCeiling + "); " + v.describe());
				}
			}

			if (!lineShot && line >= 0 && t - line >= ATMOSPHERE_SHOT) {
				context.takeScreenshot("oldrepublic_m5_092_atmosphere_line");
				lineShot = true;
			}

			if (t % LOG_EVERY == 0) {
				progress(leg, t, v);
			}
		}
	}

	/** Across into {@code orbit_home}: where the crossing rule puts the ship for its place at the line, then the pilot flying it again. */
	private void arrivedInOrbit(String leg, ServerView v, List<Vec3> sky) {
		crossedAt = ticks;
		hold(EnumSet.noneOf(Key.class));
		FlightBalance b = numbers.flight();
		Vec3 at = Objects.requireNonNull(v.pos());
		Match match = nearest(at, sky, s -> FlightRules.toOrbit(s.x, s.z, b));
		log("{}: across into {} at {}, {} from the crossing rule's {} ({} samples back); {} blocks from the orbit's centre (asteroids keep 40 clear)", leg,
				v.levelName(), fmt(at), fmt2(match.distance()), fmt(match.expected()), match.back(), fmt1(Math.sqrt(at.x * at.x + at.z * at.z)));

		if (match.distance() > ARRIVAL_SLACK && !cleared(leg, at, match)) {
			throw fail(leg, "the ship arrived in orbit at " + fmt(at) + ", " + fmt2(match.distance()) + " blocks from where the crossing rule puts it ("
					+ fmt(match.expected()) + ")");
		}

		repilot(leg, Planets.orbit(Planets.Planet.HOME));
		checkAtHelm(leg, true);
	}

	/** The brake (Back while the ship still moves along its heading) with the nose level, until the ship holds still. */
	private void holdStill(String leg) {
		List<Vec3> trail = new ArrayList<>();
		int still = 0;

		for (int t = 0; ; t++) {
			if (t >= STOP_TIMEOUT) {
				throw fail(leg, "the ship did not come to a stop within " + STOP_TIMEOUT + " ticks; " + views());
			}

			ClientView c = client();

			if (c.ship() != null) {
				trail.addFirst(c.ship());
				trim(trail);
			}

			Vec3 motion = motion(trail);
			step(along(motion, c.yaw()) > SPEED_BAND ? EnumSet.of(Key.BACK) : EnumSet.noneOf(Key.class), c.yaw(), 0f);
			still = trail.size() > 1 && motion.length() < STILL_SPEED ? still + 1 : 0;

			if (still >= STILL_TICKS && t >= LEVEL_TICKS) {
				log("{}: the ship holds still at {}", leg, fmt(view().pos()));
				return;
			}
		}
	}

	/** Shot 093 in {@code orbit_home} once the crossings' cooldown has run out. */
	private void orbitShot() {
		String leg = "the stop in orbit";
		waitOutCooldown();
		ServerView v = view();

		if (!v.in(Planets.orbit(Planets.Planet.HOME)) || !v.piloting()) {
			throw fail(leg, "the ship is not flown in the home world's orbit; " + views());
		}

		context.takeScreenshot("oldrepublic_m5_093_orbit");
		log("{}: shot 093 at {}", leg, fmt(v.pos()));
	}

	/** Down out of {@code planet}'s orbit on the descent thrusters, the nose level, until the server has the ship in the planet's sky. */
	private void sink(Planets.Planet planet) {
		String leg = "the dive into " + planet.id() + "'s sky";
		ResourceKey<Level> orbit = Planets.orbit(planet);
		ResourceKey<Level> sky = planet.level();
		waitOutCooldown();
		float yaw = client().yaw();
		List<Vec3> trail = new ArrayList<>();
		boolean line = false;
		// The overlay counts once it comes up: one still clearing from the last crossing does not.
		boolean overlay = client().atmosphere();

		for (int t = 0; ; t++) {
			if (t >= SINK_TIMEOUT) {
				throw fail(leg, "the ship did not cross into " + sky.identifier() + " within " + SINK_TIMEOUT + " ticks (rock in the way?); " + views());
			}

			step(EnumSet.of(Key.SNEAK), yaw, 0f);
			ServerView v = view();

			if (v.in(sky)) {
				arrivedInSky(leg, v, trail, planet);
				return;
			}

			if (!v.in(orbit) || v.pos() == null) {
				throw fail(leg, "the ship left " + orbit.identifier() + " without reaching the line; " + views());
			}

			trail.addFirst(v.pos());
			trim(trail);
			boolean up = client().atmosphere();
			boolean cameUp = up && !overlay;
			overlay = up;

			if (!line && cameUp) {
				line = true;
				log("{}: the atmosphere overlay came up with the ship at y {} on the server", leg, fmt1(v.pos().y));

				if (v.pos().y >= numbers.flight().atmosphereLine) {
					throw fail(leg, "the atmosphere overlay came up above the line (y " + numbers.flight().atmosphereLine + "); " + v.describe());
				}
			}

			if (t % LOG_EVERY == 0) {
				progress(leg, t, v);
			}
		}
	}

	/** Across into {@code planet}'s sky: where the crossing rule puts the ship for its place at the line, then the pilot flying it again. */
	private void arrivedInSky(String leg, ServerView v, List<Vec3> orbit, Planets.Planet planet) {
		crossedAt = ticks;
		hold(EnumSet.noneOf(Key.class));
		FlightBalance b = numbers.flight();
		Vec3 at = Objects.requireNonNull(v.pos());
		Match match = nearest(at, orbit, o -> FlightRules.toSky(o.x, o.z, b));
		log("{}: across into {} at {}, {} from the crossing rule's {} ({} samples back)", leg, v.levelName(), fmt(at), fmt2(match.distance()),
				fmt(match.expected()), match.back());

		if (match.distance() > ARRIVAL_SLACK && !cleared(leg, at, match)) {
			throw fail(leg, "the ship arrived in the sky at " + fmt(at) + ", " + fmt2(match.distance()) + " blocks from where the crossing rule puts it ("
					+ fmt(match.expected()) + ")");
		}

		repilot(leg, planet.level());
		checkAtHelm(leg, false);
	}

	/** Hovers in the home world's sky until the cooldown has run out, then climbs into the orbit again. */
	private void climbBack() {
		waitOutCooldown();
		climb(false);
	}

	/** Hovers, the nose level, until the crossings' cooldown since the last crossing has run out. */
	private void waitOutCooldown() {
		float yaw = client().yaw();

		while (ticks - crossedAt < numbers.flight().transitionCooldown + COOLDOWN_SLACK) {
			step(EnumSet.noneOf(Key.class), yaw, 0f);
		}
	}

	/** The pilot's client flies the ship again in {@code level}, on the server's new body; the keys still wanted are pressed again. */
	private void repilot(String leg, ResourceKey<Level> level) {
		awaitClient(REPILOT_TIMEOUT, leg + ": the pilot's client to fly the ship again in " + level.identifier(),
				mc -> mc.level != null && mc.level.dimension().equals(level) && mc.gui.screen() == null && Piloting.isPiloting());
		rehold();
	}

	/**
	 * At the helm after a crossing or a jump: the record {@code FLYING} with no autopilot run and its
	 * last landing spot kept, the owner flying it on the server, and the pilot's status carrying the
	 * top speed of an orbit or a sky and the orbit cue only in a sky.
	 */
	private void checkAtHelm(String leg, boolean inOrbit) {
		ServerView v = view();

		if (v.state() != ShipState.FLYING || v.flight().isPresent() || !v.piloting()) {
			throw fail(leg, "the ship is not flown by its owner at the helm; " + views());
		}

		if (!Objects.equals(homePad, v.pad())) {
			throw fail(leg, "the record's last landing spot moved to " + where(v.pad()) + " (it stays " + where(homePad) + " until a landing by hand)");
		}

		int upgrades = v.upgrades();
		float top = server.computeOnServer(s -> FlightRules.profile(FlightBalance.get(), upgrades, inOrbit).top());
		int ceiling = inOrbit ? -1 : numbers.flight().orbitCeiling;

		for (int waited = 0; !statusFits(top, ceiling); waited++) {
			if (waited >= STATUS_TIMEOUT) {
				throw fail(leg, "the pilot's status does not fit " + (inOrbit ? "an orbit" : "a sky") + " (top speed " + top + ", orbit cue " + ceiling + "): "
						+ client().describe() + (client().status() == null ? "" : ", its top speed " + client().status().profile().top()
								+ ", its orbit cue " + client().status().ceiling()));
			}

			tick();
		}
	}

	private boolean statusFits(float top, int ceiling) {
		return context.computeOnClient(mc -> {
			FlightStatusPayload s = FlightClient.status();
			return s != null && s.helm() == FlightStatusPayload.HELM_PILOTED && Math.abs(s.profile().top() - top) < 1.0e-4f && s.ceiling() == ceiling;
		});
	}

	// ---------------------------------------------------------------- the jump (4.7)

	/**
	 * The inventory key opens the ship screen at the helm; on the Nav tab the mode button, Saltgate's
	 * row, Jump and its confirm, all clicked with the mouse; then the Jump and Hyperspace phases and
	 * the helm again in {@code orbit_ghessar}.
	 */
	private void jumpToGhessar() {
		String leg = "the jump to Ghessar's orbit";
		releaseAll();
		tick(2);
		ServerView before = view();
		tap(o -> o.keyInventory);
		awaitClient(SCREEN_TIMEOUT, "the inventory key at the helm to open the ship screen", mc -> mc.gui.screen() instanceof ShipScreen);
		awaitClient(SCREEN_TIMEOUT, "the ship screen's view of the ship flown in the home world's orbit", mc -> {
			ShipStatusPayload s = ShipClientState.status();
			return s != null && s.viewerIsOwner() && s.air().flying() && s.air().orbit() == Planets.Planet.HOME.ordinal() && !s.air().jumps().isEmpty();
		});

		if (!context.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen screen && screen.openTab("nav", false))) {
			throw fail(leg, "the ship screen has no Nav tab for its owner");
		}

		shortcut("opened the ship screen's Nav tab with ShipScreen#openTab, the client tests' seam for a click on the tab");
		tick(5);

		if (!clickButton(NAV_MODE_LAND)) {
			throw fail(leg, "the Nav tab shows no enabled mode button in orbit: " + describeButtons(NAV_MODE_LAND));
		}

		tick(3);

		if (buttonCentre(NAV_MODE_JUMP) == null) {
			throw fail(leg, "the mode button did not switch the Nav tab to the jumps: " + describeButtons(NAV_MODE_JUMP));
		}

		List<ShipStatusPayload.Row> jumps = jumpRows();
		int row = -1;

		for (int i = 0; i < jumps.size(); i++) {
			if (jumps.get(i).destination() == Destination.SALTGATE.ordinal()) {
				row = i;
			}
		}

		if (row < 0) {
			throw fail(leg, "Saltgate's orbit is not on the jump list: " + describe(jumps));
		}

		TravelService.Refusal refusal = refusal(jumps.get(row).refusal());

		if (refusal != TravelService.Refusal.NONE) {
			throw fail(leg, "the jump to Ghessar's orbit is refused (" + refusal + "): " + describe(jumps));
		}

		boolean clicked = clickRow(row, jumps.size());
		tick(3);

		if (!clicked || buttonCentre(NAV_JUMP) == null) {
			shortcut("chose Saltgate's jump row with ShipScreen#selectDestination: the click on row " + (row + 1) + " of " + jumps.size()
					+ (clicked ? " did not enable Jump" : " could not be made (the row is not shown)"));

			if (!context.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen screen && screen.selectDestination(Destination.SALTGATE.ordinal()))) {
				throw fail(leg, "the Nav tab does not list Saltgate");
			}

			tick(2);
		}

		if (!clickButton(NAV_JUMP)) {
			throw fail(leg, "the Jump button is not enabled with Saltgate chosen: " + describeButtons(NAV_JUMP));
		}

		tick(2);

		if (!clickButton(NAV_CONFIRM)) {
			throw fail(leg, "the Jump button did not ask for its confirm: " + describeButtons(NAV_CONFIRM));
		}

		ServerView launched = awaitServer(LAUNCH_TIMEOUT, "the server to take the ship for the jump after the confirm (jump list: " + describe(jumpRows()) + ")",
				v -> v.flight().isPresent());
		Destination to = launched.flight().orElseThrow().to();

		if (to != Destination.SALTGATE) {
			throw fail(leg, "the Nav tab's jump went to " + to.id() + ", not Saltgate: the click on Saltgate's row chose another");
		}

		awaitClient(SCREEN_TIMEOUT, "the ship screen to close for the jump", mc -> mc.gui.screen() == null);
		log("{}: the confirm sent the ship on a run to {} ({})", leg, to.id(), launched.state());
		ResourceKey<Level> orbit = Planets.orbit(Planets.Planet.GHESSAR);
		ShipBalance.Travel t = numbers.travel();
		ServerView arrived = awaitServer(t.jumpTicks + t.hyperspaceMaxWait + t.hyperspaceTicks + JUMP_SLACK,
				"the ship to come out of hyperspace in " + orbit.identifier() + " with the helm handed back",
				v -> v.in(orbit) && v.state() == ShipState.FLYING && v.flight().isEmpty());
		// A jump starts a new helm run: no crossing cooldown.
		crossedAt = Integer.MIN_VALUE / 2;
		Vec3 at = Objects.requireNonNull(arrived.pos());
		List<Vec3> spots = server.computeOnServer(s -> {
			FlightBalance b = FlightBalance.get();
			List<Vec3> list = new ArrayList<>();
			list.add(FlightRules.orbitArrival(null, b));
			BlockPos hub = PlanetHubs.hub(s, Planets.Planet.GHESSAR);

			if (hub != null) {
				list.add(FlightRules.orbitArrival(Vec3.atBottomCenterOf(hub), b));
			}

			for (BlockPos bay : PlanetHubs.padBays(s, Planets.Planet.GHESSAR)) {
				list.add(FlightRules.orbitArrival(Vec3.atBottomCenterOf(bay), b));
			}

			return list;
		});
		Match match = nearest(at, spots, Function.identity());
		log("{}: out of hyperspace at {}, {} from the jump rule's {} (of {})", leg, fmt(at), fmt2(match.distance()), fmt(match.expected()), spots.size());

		if (match.distance() > ARRIVAL_SLACK && !cleared(leg, at, match)) {
			throw fail(leg, "the ship came out of hyperspace at " + fmt(at) + ", " + fmt2(match.distance()) + " blocks from the nearest place the jump rule"
					+ " allows (above a Saltgate bay / 8, or above (0, 0) before Saltgate is placed, at y " + numbers.flight().jumpArrivalY + ")");
		}

		if (arrived.fuel() != before.fuel() - numbers.ship().cellsPerJump) {
			throw fail(leg, "the jump took " + (before.fuel() - arrived.fuel()) + " cells, not " + numbers.ship().cellsPerJump);
		}

		repilot(leg, orbit);
		cameraBehind();
		checkAtHelm(leg, true);
	}

	// ---------------------------------------------------------------- Saltgate (4.5, 4.6)

	/** Saltgate's pad, across and down to it by hand, the landing check's {@code HUB}, the Helm key and the landing onto a bay. */
	private void landAtSaltgate() {
		String leg = "the landing at Saltgate";
		BlockPos anchor = awaitHub(leg);
		List<BlockPos> bays = server.computeOnServer(s -> PlanetHubs.padBays(s, Planets.Planet.GHESSAR));
		log("{}: Saltgate's pad centre is {}, its bays {}", leg, anchor.toShortString(), bays);
		server.runCommand("oldrepublic planet sandstorm stop");
		shortcut("stopped any sandstorm on Ghessar (/oldrepublic planet sandstorm stop) for a clear landing shot");
		overThePad(leg, Vec3.atBottomCenterOf(anchor));
		land(leg, bays, anchor);
	}

	/** Hovers in Ghessar's sky until Saltgate is placed (the jump asked for it, and the dive did again). */
	private BlockPos awaitHub(String leg) {
		float yaw = client().yaw();

		for (int waited = 0; ; waited++) {
			BlockPos hub = server.computeOnServer(s -> PlanetHubs.hub(s, Planets.Planet.GHESSAR));

			if (hub != null) {
				return hub;
			}

			if (waited >= HUB_TIMEOUT) {
				throw fail(leg, "Saltgate was not placed on Ghessar within " + HUB_TIMEOUT + " ticks of the ship's arrival in its sky; " + views());
			}

			step(EnumSet.noneOf(Key.class), yaw, 0f);
		}
	}

	/**
	 * Across to the pad's centre (faster the farther it is, the brake near it), then down over it:
	 * fast while high above the ground, at a walk under {@code flight.landMaxSpeed} below
	 * {@link #SLOW_GAP}, only while the landing check says too high. Ends when the check says
	 * {@code HUB} with the ship nearly still.
	 */
	private void overThePad(String leg, Vec3 pad) {
		List<Vec3> trail = new ArrayList<>();
		FlightRules.Landing seen = null;
		int calm = 0;
		int wrong = 0;

		for (int t = 0; ; t++) {
			if (t >= DESCENT_TIMEOUT) {
				throw fail(leg, "the ship was not over Saltgate's pad, low and slow enough to land, within " + DESCENT_TIMEOUT + " ticks; " + views());
			}

			ServerView v = view();

			if (!v.in(Planets.GHESSAR) || v.pos() == null || !v.piloting()) {
				throw fail(leg, "the ship is no longer flown in Ghessar's sky; " + views());
			}

			ClientView c = client();

			if (c.ship() != null) {
				trail.addFirst(c.ship());
				trim(trail);
			}

			Vec3 motion = motion(trail);
			double dx = pad.x - v.pos().x;
			double dz = pad.z - v.pos().z;
			double across = Math.sqrt(dx * dx + dz * dz);
			double gap = groundGap();
			FlightStatusPayload status = c.status();
			FlightRules.Landing check = status == null ? FlightRules.Landing.TOO_HIGH : FlightRules.Landing.of(status.landing());
			boolean low = gap <= SLOW_GAP;
			float yaw = across > OVER_PAD ? yawToward(dx, dz) : c.yaw();
			Set<Key> keys = EnumSet.noneOf(Key.class);
			// Across: toward the pad, slower the nearer it is; low over it, only back when it drifted off, else the brake.
			double closing = across < 1.0e-3 ? 0 : (motion.x * dx + motion.z * dz) / across;
			double wanted = Math.min(APPROACH_SPEED, APPROACH_GAIN * Math.max(0, across - OVER_PAD));
			boolean seek = across > (low ? DESCEND_WITHIN : OVER_PAD);
			boolean facing = Math.abs(Mth.wrapDegrees(yaw - c.yaw())) <= FACING;

			if (seek && facing && closing < wanted - SPEED_BAND) {
				keys.add(Key.FORWARD);
			} else if (along(motion, c.yaw()) > SPEED_BAND && (!seek || closing > wanted + SPEED_BAND)) {
				keys.add(Key.BACK);
			}

			// Down: over the pad while the check says too high; at a walk near the ground.
			double flat = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

			if (across <= DESCEND_WITHIN && flat < DESCEND_FLAT && check == FlightRules.Landing.TOO_HIGH && (!low || motion.y > -WALK_SPEED)) {
				keys.add(Key.SNEAK);
			}

			step(keys, yaw, 0f);

			if (check != seen) {
				log("{}: the landing check says {} ({} blocks over the ground, {} from the pad's centre)", leg, check, fmt1(gap), fmt1(across));
				seen = check;
			}

			calm = check == FlightRules.Landing.HUB && motion.length() < CALM_SPEED ? calm + 1 : 0;

			if (calm >= CALM_TICKS) {
				log("{}: over the pad, {} blocks over the ground and {} from its centre; the check says HUB", leg, fmt1(gap), fmt1(across));
				return;
			}

			boolean other = check != FlightRules.Landing.TOO_HIGH && check != FlightRules.Landing.TOO_FAST && check != FlightRules.Landing.HUB;
			wrong = other ? wrong + 1 : 0;

			if (wrong >= WRONG_TICKS) {
				throw fail(leg, "over Saltgate's pad the landing check says " + check + ", not HUB (" + fmt1(across) + " blocks from its centre, " + fmt1(gap)
						+ " over the ground); " + views());
			}

			if (t % LOG_EVERY == 0) {
				progress(leg, t, v);
			}
		}
	}

	/** The Helm key over the pad; shot 094 as the struts go down; the ship resting on a bay, the owner's last landing, the owner off it. */
	private void land(String leg, List<BlockPos> bays, BlockPos anchor) {
		releaseAll();
		tick(2);
		tap(FlightClient.HELM);
		awaitClient(LAND_START_TIMEOUT, "the Helm key over Saltgate's pad to start the landing", mc -> {
			FlightStatusPayload s = FlightClient.status();
			return s != null && s.helm() == FlightStatusPayload.HELM_LANDING;
		});
		log("{}: the Helm key started the landing", leg);
		boolean shot = false;

		for (int t = 0; ; t++) {
			if (t >= LAND_TIMEOUT) {
				throw fail(leg, "the landing did not end within " + LAND_TIMEOUT + " ticks; " + views());
			}

			ClientView c = client();

			if (!shot && (c.gear() || t >= LANDING_SHOT_LATEST)) {
				context.takeScreenshot("oldrepublic_m5_094_saltgate_landing");
				shot = true;
				log("{}: shot 094 {} ticks into the landing, the ship at {}", leg, t, fmt(c.ship()));
			}

			ServerView v = view();

			if (v.state() != null && !v.busy() && !v.aboard()) {
				break;
			}

			tick();
		}

		ServerView landed = view();
		GlobalPos spot = landed.pad();
		// The Settle's state for the hull and fuel the jump left: READY with the given ship's full tank.
		ShipState rest = server.computeOnServer(s -> ShipRegistry.of(s, owner).map(r -> ShipRules.settleState(r, ShipBalance.get())).orElse(null));

		if (rest == null || landed.state() != rest || landed.flight().isPresent() || spot == null || !spot.dimension().equals(Planets.GHESSAR)) {
			throw fail(leg, "after the landing the ship is not resting on Ghessar as the Settle leaves it (" + rest + "); " + landed.describe());
		}

		if (rest != ShipState.READY) {
			log("{}: the ship rests {}, not READY: the jump left it short of hull or fuel", leg, rest);
		}

		int bay = bays.indexOf(spot.pos());

		if (bay < 0) {
			throw fail(leg, "the ship landed at " + spot.pos().toShortString() + ", not on one of Saltgate's bays " + bays + " (pad centre "
					+ anchor.toShortString() + "; a bay with blocks in the ship's landing box sends it to the overflow rings)");
		}

		GlobalPos last = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayer(owner);
			return p == null ? null : TravelAttachments.get(p).lastPad().orElse(null);
		});

		if (!spot.equals(last)) {
			throw fail(leg, "the owner's last landing is " + where(last) + ", not the bay " + where(spot));
		}

		awaitClient(RELEASE_TIMEOUT, "the pilot's client to leave the helm after the Settle", mc -> FlightClient.status() == null && !Piloting.isPiloting());
		log("{}: the ship rests on Saltgate's bay {} at {}; the owner is off it", leg, bay + 1, spot.pos().toShortString());
	}

	/** The ship's base over the highest ground under its footprint (the heightmap, 9 columns), on the server. */
	private double groundGap() {
		return server.computeOnServer(s -> {
			ShipBody ship = Ships.ship(s, owner);

			if (ship == null) {
				return Double.NaN;
			}

			double half = ShipBody.footprintHalf(ship.interceptor()) - 0.5;
			int top = Integer.MIN_VALUE;

			for (int i = -1; i <= 1; i++) {
				for (int k = -1; k <= 1; k++) {
					top = Math.max(top, ship.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(ship.getX() + i * half),
							Mth.floor(ship.getZ() + k * half)));
				}
			}

			return ship.getY() - top;
		});
	}

	// ---------------------------------------------------------------- the ship screen's mouse

	/** The jump rows of the ship screen's last view (empty without one). */
	private List<ShipStatusPayload.Row> jumpRows() {
		return context.computeOnClient(mc -> {
			ShipStatusPayload s = ShipClientState.status();
			return s == null ? List.<ShipStatusPayload.Row>of() : s.air().jumps();
		});
	}

	/** Clicks row {@code index} of {@code count} on the Nav tab's list where ShipScreen draws it; false when it is not shown. */
	private boolean clickRow(int index, int count) {
		int step = ROW_Y + (count - 1) * ROW_STEP + ROW_H <= BUTTON_Y - 4 ? ROW_STEP : ROW_STEP_TIGHT;
		int height = step - (ROW_STEP - ROW_H);
		int shown = Math.max(0, Math.min(count, (BUTTON_Y - 4 - ROW_Y - height) / step + 1));

		if (index >= shown) {
			return false;
		}

		double[] origin = context.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen screen
				? new double[] {(screen.width - SCREEN_W) / 2, (screen.height - SCREEN_H) / 2} : null);

		if (origin == null) {
			return false;
		}

		clickAt(origin[0] + ROW_X + ROW_W / 2.0, origin[1] + ROW_Y + index * step + height / 2.0);
		return true;
	}

	/** Moves the mouse to GUI point ({@code gx}, {@code gy}) of the open screen and left-clicks. */
	private void clickAt(double gx, double gy) {
		double[] at = context.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();
			return screen == null ? null
					: new double[] {gx * mc.getWindow().getScreenWidth() / (double) screen.width, gy * mc.getWindow().getScreenHeight() / (double) screen.height};
		});

		if (at == null) {
			return;
		}

		input.setCursorPos(at[0], at[1]);
		tick();
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		tick(2);
	}

	/** GUI centre of the shown, enabled button labelled with lang key {@code key}, or null. */
	private double @Nullable [] buttonCentre(String key) {
		return context.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();

			if (screen == null) {
				return null;
			}

			for (GuiEventListener child : screen.children()) {
				if (child instanceof Button button && button.visible && button.active && button.getMessage().getContents() instanceof TranslatableContents text
						&& text.getKey().equals(key)) {
					return new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0};
				}
			}

			return null;
		});
	}

	/** Clicks the shown, enabled button labelled {@code key} with the mouse; false when there is none. */
	private boolean clickButton(String key) {
		double[] centre = buttonCentre(key);

		if (centre == null) {
			return false;
		}

		clickAt(centre[0], centre[1]);
		return true;
	}

	/** The buttons labelled {@code key} on the open screen with their place and state (failure messages). */
	private String describeButtons(String key) {
		return context.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();

			if (screen == null) {
				return "no screen";
			}

			List<String> found = new ArrayList<>();

			for (GuiEventListener child : screen.children()) {
				if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
					found.add(button.getX() + "," + button.getY() + (button.visible ? "" : " hidden") + (button.active ? "" : " disabled"));
				}
			}

			return screen.getClass().getSimpleName() + " " + found;
		});
	}

	private static TravelService.Refusal refusal(int ordinal) {
		TravelService.Refusal[] all = TravelService.Refusal.values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TravelService.Refusal.UNAVAILABLE;
	}

	private static String describe(List<ShipStatusPayload.Row> rows) {
		Destination[] all = Destination.values();
		List<String> parts = new ArrayList<>();

		for (ShipStatusPayload.Row row : rows) {
			String name = row.destination() >= 0 && row.destination() < all.length ? all[row.destination()].id() : "#" + row.destination();
			parts.add(name + " " + refusal(row.refusal()));
		}

		return parts.isEmpty() ? "none" : String.join(", ", parts);
	}

	// ---------------------------------------------------------------- input

	/** One tick of flight: this tick's keys held through real key events, and the pilot's look (the nose's pitch). */
	private void step(Set<Key> keys, float yaw, float pitch) {
		hold(keys);

		if (context.computeOnClient(mc -> mc.player != null)) {
			input.lookAt(yaw, invert ? -pitch : pitch);
		}

		tick();
	}

	/** Holds the wanted keys and lets go of the others. */
	private void hold(Set<Key> wanted) {
		for (Key key : Key.values()) {
			if (wanted.contains(key) && held.add(key)) {
				input.holdKey(key.mapping);
			} else if (!wanted.contains(key) && held.remove(key)) {
				input.releaseKey(key.mapping);
			}
		}
	}

	/** After a screen (a new world's loading, the ship screen) the game let go of every key: the ones still wanted are pressed again. */
	private void rehold() {
		for (Key key : held) {
			input.releaseKey(key.mapping);
			input.holdKey(key.mapping);
		}
	}

	private void releaseAll() {
		for (Key key : held) {
			input.releaseKey(key.mapping);
		}

		held.clear();
	}

	/** A real tap: held for two ticks (pressKey presses and releases within one frame). */
	private void tap(KeyMapping key) {
		input.holdKey(key);
		tick(2);
		input.releaseKey(key);
	}

	private void tap(Function<Options, KeyMapping> key) {
		input.holdKey(key);
		tick(2);
		input.releaseKey(key);
	}

	private void cameraBehind() {
		if (context.computeOnClient(mc -> mc.options.getCameraType()) != CameraType.THIRD_PERSON_BACK) {
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		}
	}

	/** Keys let go, the ship screen closed, the camera first person again. */
	private void cleanUp() {
		releaseAll();

		if (context.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen)) {
			context.setScreen(() -> null);
		}

		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	// ---------------------------------------------------------------- views and waits

	private ServerView view() {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayer(owner);
			ShipRecord r = ShipRegistry.of(s, owner).orElse(null);
			ShipBody ship = Ships.ship(s, owner);
			boolean aboard = p != null && ship != null && p.getVehicle() == ship;
			boolean piloting = aboard && ship.piloted() && ship.getControllingPassenger() == p;
			return new ServerView(ship == null ? null : ship.level().dimension(), ship == null ? null : ship.position(), r == null ? null : r.state(),
					r == null ? Optional.<ShipRecord.Flight>empty() : r.flight(), r != null && Ships.busy(r), r == null ? 0 : r.hull(), r == null ? 0 : r.fuel(),
					r == null ? 0 : r.upgrades(), r == null ? null : r.pos(), aboard, piloting, p != null && TravelAttachments.get(p).helm(),
					s.getPlayerList().getPlayerCount());
		});
	}

	private ClientView client() {
		return context.computeOnClient(mc -> {
			LocalPlayer player = mc.player;
			ShipBody ship = player != null && player.getVehicle() instanceof ShipBody body ? body : null;
			Screen screen = mc.gui.screen();
			return new ClientView(mc.getConnection() != null && mc.level != null && player != null, mc.level == null ? null : mc.level.dimension(),
					ship == null ? null : ship.position(), ship != null ? ship.getYRot() : player != null ? player.getYRot() : 0f, Piloting.isPiloting(),
					FlightClient.status(), FlightClient.atmosphereShowing(), screen == null ? null : screen.getClass().getSimpleName(),
					ship != null && ship.gearDown());
		});
	}

	private String views() {
		return view().describe() + "; " + client().describe();
	}

	/** Waits, a tick at a time, until the server's view passes {@code done}; fails after {@code timeout} ticks. */
	private ServerView awaitServer(int timeout, String what, Predicate<ServerView> done) {
		for (int waited = 0; ; waited++) {
			ServerView v = view();

			if (done.test(v)) {
				return v;
			}

			if (waited >= timeout) {
				throw timedOut(what, timeout);
			}

			tick();
		}
	}

	/** Waits, a tick at a time, until the client passes {@code done}; fails after {@code timeout} ticks. */
	private void awaitClient(int timeout, String what, Predicate<Minecraft> done) {
		for (int waited = 0; !context.computeOnClient(mc -> done.test(mc)); waited++) {
			if (waited >= timeout) {
				throw timedOut(what, timeout);
			}

			tick();
		}
	}

	private void tick() {
		context.waitTick();
		ticks++;
	}

	private void tick(int count) {
		for (int i = 0; i < count; i++) {
			tick();
		}
	}

	// ---------------------------------------------------------------- measures

	/** The arrival's distance to the rule's place for the nearest of the places the ship had before it crossed. */
	private static Match nearest(Vec3 at, List<Vec3> before, Function<Vec3, Vec3> rule) {
		Match best = new Match(Double.MAX_VALUE, -1, Vec3.ZERO);

		for (int i = 0; i < before.size(); i++) {
			Vec3 expected = rule.apply(before.get(i));
			double distance = at.distanceTo(expected);

			if (distance < best.distance()) {
				best = new Match(distance, i, expected);
			}
		}

		return best;
	}

	/**
	 * Whether an arrival off its rule's place is the clear-place search's doing (rock there:
	 * {@link FlightRules#clearArrival} looks up to {@code CLEAR_UP} blocks straight up, then up to
	 * {@code CLEAR_SIDE} sideways at the same height); logged when it is.
	 */
	private boolean cleared(String leg, Vec3 at, Match match) {
		Vec3 off = at.subtract(match.expected());
		double across = Math.sqrt(off.x * off.x + off.z * off.z);
		boolean up = across <= ARRIVAL_SLACK && off.y >= -ARRIVAL_SLACK && off.y <= FlightRules.CLEAR_UP + ARRIVAL_SLACK;
		boolean aside = Math.abs(off.y) <= ARRIVAL_SLACK && across <= FlightRules.CLEAR_SIDE + ARRIVAL_SLACK;

		if (up || aside) {
			log("{}: the arrival is {} up and {} across from the rule's place: moved clear of blocks there (FlightRules.clearArrival)", leg, fmt1(off.y),
					fmt1(across));
		}

		return up || aside;
	}

	/** The last tick's motion of the newest-first {@code trail}. */
	private static Vec3 motion(List<Vec3> trail) {
		return trail.size() > 1 ? trail.get(0).subtract(trail.get(1)) : Vec3.ZERO;
	}

	/** The part of {@code motion} along the level heading {@code yaw}. */
	private static double along(Vec3 motion, float yaw) {
		double rad = Math.toRadians(yaw);
		return motion.x * -Math.sin(rad) + motion.z * Math.cos(rad);
	}

	/** Minecraft's yaw looking along ({@code dx}, {@code dz}). */
	private static float yawToward(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static void trim(List<Vec3> trail) {
		while (trail.size() > TRAIL) {
			trail.removeLast();
		}
	}

	// ---------------------------------------------------------------- logs

	private void log(String format, Object... args) {
		OldRepublicMod.LOGGER.info("Orbit trip t=" + ticks + ": " + format, args);
	}

	private void progress(String leg, int t, ServerView v) {
		log("{} t={}: {}; {}", leg, t, v.describe(), client().describe());
	}

	private void shortcut(String what) {
		shortcuts.add(what);
		OldRepublicMod.LOGGER.info("Orbit trip shortcut: {}", what);
	}

	private AssertionError fail(String leg, String problem) {
		String message = "The orbit trip (MILESTONE_5.md 19.2) failed in " + leg + ": " + problem;
		OldRepublicMod.LOGGER.error(message);
		return new AssertionError(message);
	}

	private AssertionError timedOut(String what, int timeout) {
		return fail("waiting for " + what, "not within " + timeout + " ticks; " + views());
	}

	private static String fmt(@Nullable Vec3 v) {
		return v == null ? "none" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}

	private static String fmt1(double d) {
		return String.format(Locale.ROOT, "%.1f", d);
	}

	private static String fmt2(double d) {
		return String.format(Locale.ROOT, "%.2f", d);
	}

	private static String where(@Nullable GlobalPos pos) {
		return pos == null ? "none" : pos.pos().toShortString() + " in " + pos.dimension().identifier();
	}
}

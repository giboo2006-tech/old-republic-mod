package com.oldrepublic.gametest.m5;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.travel.FlightClient;
import com.oldrepublic.client.travel.Piloting;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;

import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The free-flight spike's steps and assertions (spec MILESTONE_5.md 4.1 and 19.2; W1's file).
 * {@code FlightClientTest} connects to a dedicated server with {@code allow-flight=false}, so the
 * server checks every move the pilot's client sends, gives the player a ship and calls {@link #run}.
 *
 * <p><b>Steps.</b> The helm is unlocked by command and the owner boards on the server (the M4 client
 * steps board with real use presses). A real press of the Helm key takes the helm: the Spool, the
 * 4-block rise and the hand-over (4.2). Then 400 ticks of scripted input, all of it real key holds
 * and the pilot's look, one tick at a time:
 * <ul>
 * <li>ticks 0 to 49, forward and jump with the nose 20° up: the climb after the lift-off;</li>
 * <li>50 to 109, forward, level: up to full speed;</li>
 * <li>110 to 154, forward while the look turns 4° a tick: half a circle at full speed;</li>
 * <li>155 to 214, forward and sprint: the boost, which the server grants and meters;</li>
 * <li>215 to 244, forward with the nose 35° down: the dive;</li>
 * <li>245 to 257, back: the brake (13 ticks stop the ship from full speed);</li>
 * <li>258 to 299, nothing: the ship hovers;</li>
 * <li>300 to 399, sneak: down to the ground, the landing.</li>
 * </ul>
 *
 * <p><b>Assertions</b> (the pass criteria of 4.1). On taking the helm: the server's record is
 * {@code FLYING}, its body piloted and controlled by the owner, the client flies it, and a
 * first-person pilot was switched to the camera behind the ship. Every tick: the pilot is aboard on
 * their client. Every 20 ticks: the client is connected and the server has one player; the pilot is
 * at the helm on both sides; the server's ship is within 1.5 blocks of the client's ship as it stood
 * at one of the last ten ticks (the server follows the pilot's packets, a tick or more behind; the
 * same-tick gap and the lag are logged too); the camera is third person from behind and behind the
 * ship's centre along its heading. At the end: no more than two server corrections in the run (the
 * pilot's client counts every time the server put the ship somewhere it did not fly to), and the
 * ship has touched down. Problems with the gap or the camera are collected and reported together at
 * the end; losing the pilot or the connection stops the run at once. Every check is logged with both
 * positions, the lag, the speed, the corrections and the camera, for CI.
 *
 * <p>Afterwards the server puts the pilot off, as when they leave mid-flight (4.10): the helm is
 * released and the ship goes back to its last landing spot. That is logged, not asserted (the server
 * tests cover it). Screenshots, in W1's band: {@code oldrepublic_m5_108_spike_turn} (mid-turn at full
 * speed) and {@code oldrepublic_m5_109_spike_dive}. The orbit trip to Saltgate joins the spike once
 * the orbits exist.
 */
public final class FlightSpike {
	/** The scripted flight's length and how often both sides are compared. */
	private static final int FLIGHT_TICKS = 400;
	private static final int CHECK_EVERY = 20;
	/** The pass criteria (4.1): the server's ship this close to the client's, and at most this many corrections. */
	private static final double MAX_DRIFT = 1.5;
	private static final int MAX_CORRECTIONS = 2;
	/** The server's ship is matched against the client's positions of this many past ticks as well. */
	private static final int LAG_TICKS = 10;
	/** Boarding; the Spool (40 ticks) and the rise (20), with slack. */
	private static final int BOARD_TIMEOUT = 40;
	private static final int HELM_TIMEOUT = 200;
	/** Touched down: on the ground on the client, or the ship's base this close above the ground under its centre on the server. */
	private static final double ON_GROUND = 0.5;
	/** Ticks to watch the helm's release after the pilot is put off. */
	private static final int RELEASE_TICKS = 20;

	/** Where each part of the script ends (exclusive). */
	private static final int CLIMB_END = 50;
	private static final int CRUISE_END = 110;
	private static final int TURN_END = 155;
	private static final int BOOST_END = 215;
	private static final int DIVE_END = 245;
	private static final int BRAKE_END = 258;
	private static final int HOVER_END = 300;
	/** The nose for the climb and the dive (Minecraft's pitch: negative is up), and the look's turn (degrees per tick). */
	private static final float CLIMB_PITCH = -20f;
	private static final float DIVE_PITCH = 35f;
	private static final float TURN_RATE = 4f;
	/** The flight ticks of the screenshots. */
	private static final int TURN_SHOT = 135;
	private static final int DIVE_SHOT = 235;

	/** The keys the script holds. */
	private enum Key {
		FORWARD(o -> o.keyUp),
		BACK(o -> o.keyDown),
		JUMP(o -> o.keyJump),
		SNEAK(o -> o.keyShift),
		SPRINT(o -> o.keySprint);

		final Function<Options, KeyMapping> mapping;

		Key(Function<Options, KeyMapping> mapping) {
			this.mapping = mapping;
		}
	}

	/** One tick of the script: the keys held and the look (the pitch the nose should take). */
	private record Step(Set<Key> keys, float yaw, float pitch) {
	}

	/** The flight's start: the ship's heading and the client's correction count at the hand-over. */
	private record Start(float yaw, int corrections) {
	}

	/** What the pilot's client shows at a check. */
	private record ClientView(boolean connected, boolean piloting, @Nullable Vec3 ship, float yaw, int corrections, CameraType cameraType,
			boolean detached, @Nullable Vec3 camera, boolean chunk) {
	}

	/** What the server holds at a check. */
	private record ServerView(int players, boolean piloting, @Nullable Vec3 ship, @Nullable ShipState state) {
	}

	/** The server's ship's distance to the nearest of the client's recent positions, and how many ticks back that one is. */
	private record Match(double distance, int lag) {
	}

	/** What the flight measured, and what failed. */
	private static final class Tally {
		final List<String> failures = new ArrayList<>();
		double drift;
		double sameTick;
		int lag;
		double topSpeed;
		double nearestCamera = Double.MAX_VALUE;

		void speed(List<Vec3> trail) {
			if (trail.size() > 1) {
				topSpeed = Math.max(topSpeed, trail.get(0).distanceTo(trail.get(1)));
			}
		}

		void note(Match match, double raw, double behind) {
			drift = Math.max(drift, match.distance());
			sameTick = Math.max(sameTick, raw);
			lag = Math.max(lag, match.lag());

			if (!Double.isNaN(behind)) {
				nearestCamera = Math.min(nearestCamera, behind);
			}
		}

		void fail(String problem) {
			failures.add(problem);
			OldRepublicMod.LOGGER.error("Flight spike: {}", problem);
		}
	}

	/** Runs the spike on the driver's dedicated server; throws when a pass criterion of 4.1 fails. */
	public static void run(ClientGameTestContext context, TestDedicatedServerContext server) {
		UUID owner = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getUUID());
		Set<Key> held = EnumSet.noneOf(Key.class);

		try {
			server.runCommand("oldrepublic ship helm @p");
			board(context, server, owner);
			Start start = takeHelm(context, server, owner);
			fly(context, server, owner, start, held);
		} finally {
			for (Key key : held) {
				context.getInput().releaseKey(key.mapping);
			}

			held.clear();
			leave(context, server, owner);
		}
	}

	// ---------------------------------------------------------------- the helm

	/** The owner boards on the server and looks along the ship. */
	private static void board(ClientGameTestContext context, TestDedicatedServerContext server, UUID owner) {
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayer(owner);
			ShipBody ship = Ships.ship(s, owner);

			if (p == null || ship == null || !p.startRiding(ship, true, false)) {
				throw new AssertionError("The owner could not board their ship for the flight spike" + (ship == null ? ": it has no body" : ""));
			}
		});
		context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody, BOARD_TIMEOUT);
		float yaw = context.computeOnClient(mc -> mc.player.getVehicle().getYRot());
		context.getInput().lookAt(yaw, 0f);
		context.waitTicks(5);
	}

	/** A real press of the Helm key; waits for the hand-over and checks both sides and the camera. */
	private static Start takeHelm(ClientGameTestContext context, TestDedicatedServerContext server, UUID owner) {
		CameraType before = context.computeOnClient(mc -> mc.options.getCameraType());
		TestInput input = context.getInput();
		// A real tap lasts a tick or two (pressKey presses and releases within one frame).
		input.holdKey(FlightClient.HELM);
		context.waitTicks(2);
		input.releaseKey(FlightClient.HELM);

		for (int waited = 0; !context.computeOnClient(mc -> Piloting.isPiloting()); waited++) {
			if (waited >= HELM_TIMEOUT) {
				throw new AssertionError("The Helm key did not hand the ship to its pilot within " + HELM_TIMEOUT + " ticks: "
						+ server.computeOnServer(s -> helmProblem(s, owner)));
			}

			context.waitTick();
		}

		ServerView handedOver = server.computeOnServer(srv -> serverView(srv, owner));

		if (handedOver.state() != ShipState.FLYING || !handedOver.piloting()) {
			throw new AssertionError("At the hand-over the server's ship is " + handedOver.state() + (handedOver.piloting() ? "" : " and not flown by its owner"));
		}

		boolean shipCamera = context.computeOnClient(mc -> ClientConfig.get().shipCamera);
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());

		if (shipCamera && before.isFirstPerson() && camera != CameraType.THIRD_PERSON_BACK) {
			throw new AssertionError("Taking the helm did not switch the first-person pilot to the camera behind the ship: " + camera);
		}

		if (camera != CameraType.THIRD_PERSON_BACK) {
			// The ship camera option is off (or the pilot chose another camera): the spike flies from behind all the same.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		}

		Start start = context.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody ship
				? new Start(ship.getYRot(), ship.corrections()) : null);

		if (start == null) {
			throw new AssertionError("The pilot left their ship at the hand-over");
		}

		OldRepublicMod.LOGGER.info("Flight spike: {} has the helm at {} facing {}; camera {}", owner, fmt(handedOver.ship()), start.yaw(), camera);
		return start;
	}

	/** Why the helm was not taken, as far as the server shows. */
	private static String helmProblem(MinecraftServer server, UUID owner) {
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		ShipRecord record = ShipRegistry.of(server, owner).orElse(null);
		ShipBody ship = Ships.ship(server, owner);
		return "on the server the ship is " + (record == null ? "missing" : record.state() + (record.flight().isPresent() ? " on the autopilot" : ""))
				+ (ship != null && ship.piloted() ? ", piloted" : ", not piloted")
				+ (p != null && ship != null && p.getVehicle() == ship ? ", the owner aboard" : ", the owner not aboard")
				+ ", the helm " + (p != null && TravelAttachments.get(p).helm() ? "unlocked" : "locked");
	}

	// ---------------------------------------------------------------- the flight

	/** The 400 scripted ticks, the checks every 20 and the end checks. */
	private static void fly(ClientGameTestContext context, TestDedicatedServerContext server, UUID owner, Start start, Set<Key> held) {
		TestInput input = context.getInput();
		boolean invert = context.computeOnClient(mc -> ClientConfig.get().invertShipPitch);
		List<Vec3> trail = new ArrayList<>();
		Tally tally = new Tally();

		for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
			Step step = script(tick, start.yaw());
			hold(input, held, step.keys());
			input.lookAt(step.yaw(), invert ? -step.pitch() : step.pitch());
			context.waitTick();
			int done = tick + 1;
			Vec3 now = context.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody ship ? ship.position() : null);

			if (now == null) {
				throw new AssertionError("Tick " + done + " of the flight spike: the pilot is no longer aboard on their client");
			}

			trail.addFirst(now);

			if (trail.size() > LAG_TICKS + 1) {
				trail.removeLast();
			}

			tally.speed(trail);

			if (done % CHECK_EVERY == 0) {
				check(context, server, owner, done, trail, tally);
			}

			if (done == TURN_SHOT) {
				context.takeScreenshot("oldrepublic_m5_108_spike_turn");
			} else if (done == DIVE_SHOT) {
				context.takeScreenshot("oldrepublic_m5_109_spike_dive");
			}
		}

		// Still sneaking: the ship rests on the ground.
		boolean down = context.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody ship && ship.onGround());
		double gap = server.computeOnServer(s -> {
			ShipBody ship = Ships.ship(s, owner);
			return ship == null ? Double.NaN : ship.getY() - ship.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ship.getBlockX(), ship.getBlockZ());
		});
		int corrections = context.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody ship
				? ship.corrections() : start.corrections()) - start.corrections();
		OldRepublicMod.LOGGER.info(String.format(Locale.ROOT,
				"Flight spike summary: worst gap %.3f (same tick %.3f, lag up to %d ticks), top speed %.3f, %d corrections, camera at least %.2f behind,"
						+ " touched down %s (client %s, %.2f above the ground on the server)",
				tally.drift, tally.sameTick, tally.lag, tally.topSpeed, corrections, tally.nearestCamera, down || gap <= ON_GROUND, down, gap));

		if (corrections > MAX_CORRECTIONS) {
			tally.fail("the server corrected the ship " + corrections + " times (at most " + MAX_CORRECTIONS + ")");
		}

		if (!down && !(gap <= ON_GROUND)) {
			tally.fail("after 100 ticks of descent the ship has not touched down (" + String.format(Locale.ROOT, "%.2f", gap) + " above the ground)");
		}

		if (!tally.failures.isEmpty()) {
			throw new AssertionError("The flight spike (MILESTONE_5.md 4.1) failed: " + String.join("; ", tally.failures));
		}
	}

	/** The input for flight tick {@code tick} of a ship that started facing {@code yaw}. */
	private static Step script(int tick, float yaw) {
		float turned = yaw + TURN_RATE * (TURN_END - CRUISE_END);

		if (tick < CLIMB_END) {
			return new Step(EnumSet.of(Key.FORWARD, Key.JUMP), yaw, CLIMB_PITCH);
		}

		if (tick < CRUISE_END) {
			return new Step(EnumSet.of(Key.FORWARD), yaw, 0f);
		}

		if (tick < TURN_END) {
			return new Step(EnumSet.of(Key.FORWARD), yaw + TURN_RATE * (tick - CRUISE_END + 1), 0f);
		}

		if (tick < BOOST_END) {
			return new Step(EnumSet.of(Key.FORWARD, Key.SPRINT), turned, 0f);
		}

		if (tick < DIVE_END) {
			return new Step(EnumSet.of(Key.FORWARD), turned, DIVE_PITCH);
		}

		if (tick < BRAKE_END) {
			return new Step(EnumSet.of(Key.BACK), turned, 0f);
		}

		if (tick < HOVER_END) {
			return new Step(EnumSet.noneOf(Key.class), turned, 0f);
		}

		return new Step(EnumSet.of(Key.SNEAK), turned, 0f);
	}

	/** Holds this tick's keys and lets go of the others, through real key events. */
	private static void hold(TestInput input, Set<Key> held, Set<Key> wanted) {
		for (Key key : Key.values()) {
			if (wanted.contains(key) && held.add(key)) {
				input.holdKey(key.mapping);
			} else if (!wanted.contains(key) && held.remove(key)) {
				input.releaseKey(key.mapping);
			}
		}
	}

	/** One check (every 20 ticks): connection and pilot at once; the gap and the camera are collected. */
	private static void check(ClientGameTestContext context, TestDedicatedServerContext server, UUID owner, int tick, List<Vec3> trail, Tally tally) {
		ClientView c = context.computeOnClient(FlightSpike::clientView);
		ServerView s = server.computeOnServer(srv -> serverView(srv, owner));

		if (!c.connected() || s.players() != 1) {
			throw new AssertionError("Tick " + tick + " of the flight spike: the pilot is disconnected (" + s.players() + " players on the server)");
		}

		Vec3 clientShip = c.ship();
		Vec3 serverShip = s.ship();

		if (!c.piloting() || !s.piloting() || clientShip == null || serverShip == null) {
			throw new AssertionError("Tick " + tick + " of the flight spike: the pilot is no longer at the helm (client " + (c.piloting() ? "piloting" : "not piloting")
					+ ", server " + (s.piloting() ? "piloting" : "not piloting") + ", the ship " + s.state() + ")");
		}

		Match match = match(serverShip, trail);
		double raw = serverShip.distanceTo(clientShip);
		Vec3 camera = c.camera();
		double behind = camera == null ? Double.NaN : behind(clientShip, c.yaw(), camera);
		double speed = trail.size() > 1 ? trail.get(0).distanceTo(trail.get(1)) : 0;
		tally.note(match, raw, behind);
		OldRepublicMod.LOGGER.info(String.format(Locale.ROOT,
				"Flight spike t=%d: client %s yaw %.1f speed %.3f; server %s; gap %.3f (%d ticks back; same tick %.3f); %d corrections; camera %s %.2f behind;"
						+ " chunk loaded %s",
				tick, fmt(clientShip), c.yaw(), speed, fmt(serverShip), match.distance(), match.lag(), raw, c.corrections(), c.cameraType(), behind, c.chunk()));

		if (match.distance() > MAX_DRIFT) {
			tally.fail(String.format(Locale.ROOT, "at tick %d the server's ship %s is %.2f blocks from the client's path (now at %s)", tick, fmt(serverShip),
					match.distance(), fmt(clientShip)));
		}

		if (c.cameraType() != CameraType.THIRD_PERSON_BACK || !c.detached()) {
			tally.fail("at tick " + tick + " the camera is " + c.cameraType() + (c.detached() ? "" : ", in the pilot's head"));
		} else if (!(behind > 0)) {
			tally.fail(String.format(Locale.ROOT, "at tick %d the camera at %s is not behind the ship at %s (%.2f along its heading)", tick, fmt(camera),
					fmt(clientShip), behind));
		}
	}

	private static ClientView clientView(Minecraft mc) {
		LocalPlayer player = mc.player;
		ShipBody ship = player != null && player.getVehicle() instanceof ShipBody body ? body : null;
		Camera camera = mc.gameRenderer.mainCamera();
		return new ClientView(mc.getConnection() != null && mc.level != null && player != null, Piloting.isPiloting(), ship == null ? null : ship.position(),
				ship == null ? 0f : ship.getYRot(), ship == null ? 0 : ship.corrections(), mc.options.getCameraType(), camera.isDetached(),
				camera.isInitialized() ? camera.position() : null, ship != null && mc.level != null && mc.level.hasChunkAt(ship.blockPosition()));
	}

	private static ServerView serverView(MinecraftServer server, UUID owner) {
		ServerPlayer pilot = server.getPlayerList().getPlayer(owner);
		ShipBody ship = Ships.ship(server, owner);
		boolean piloting = pilot != null && ship != null && pilot.getVehicle() == ship && ship.piloted() && ship.getControllingPassenger() == pilot;
		return new ServerView(server.getPlayerList().getPlayerCount(), piloting, ship == null ? null : ship.position(),
				ShipRegistry.of(server, owner).map(ShipRecord::state).orElse(null));
	}

	private static Match match(Vec3 server, List<Vec3> trail) {
		double best = Double.MAX_VALUE;
		int lag = -1;

		for (int i = 0; i < trail.size(); i++) {
			double distance = server.distanceTo(trail.get(i));

			if (distance < best) {
				best = distance;
				lag = i;
			}
		}

		return new Match(best, lag);
	}

	/** How far the camera is behind the ship's centre, along the ship's heading (positive: behind it). */
	private static double behind(Vec3 ship, float yaw, Vec3 camera) {
		double rad = Math.toRadians(yaw);
		Vec3 toShip = ship.subtract(camera);
		return toShip.x * -Math.sin(rad) + toShip.z * Math.cos(rad);
	}

	// ---------------------------------------------------------------- afterwards

	/**
	 * The server puts the pilot off, as when they leave mid-flight (4.10): the helm is released and
	 * the ship goes back to its last landing spot. Logged, not asserted; the camera is put back to
	 * first person.
	 */
	private static void leave(ClientGameTestContext context, TestDedicatedServerContext server, UUID owner) {
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayer(owner);

			if (p != null && p.getVehicle() instanceof ShipBody) {
				p.stopRiding();
			}
		});
		context.waitTicks(RELEASE_TICKS);
		String ship = server.computeOnServer(s -> ShipRegistry.of(s, owner).map(r -> r.state() + " at " + r.pos().pos().toShortString()).orElse("gone"));
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());
		OldRepublicMod.LOGGER.info("Flight spike: the pilot was put off; the ship is {}, the camera {}", ship, camera);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private static String fmt(@Nullable Vec3 v) {
		return v == null ? "none" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}

	private FlightSpike() {
	}
}

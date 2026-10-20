package com.oldrepublic.gametest.m5;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.travel.FlightClient;
import com.oldrepublic.client.travel.Piloting;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.FlightBalance;
import com.oldrepublic.travel.FlightRules.Landing;
import com.oldrepublic.travel.Interceptor;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelData;
import com.oldrepublic.travel.TravelEffects;
import com.oldrepublic.travel.TravelVisits;
import com.oldrepublic.travel.network.FlightStatusPayload;

import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for free flight and the ship (W1). {@code M5ClientTest} runs them in
 * the shared seeded world (spec MILESTONE_5.md 19.2), after the planet steps. Screenshots are
 * {@code oldrepublic_m5_<nnn>_<what>}; W1's band is 100 to 109, and the flight spike
 * ({@link FlightSpike}) takes 108 and 109:
 * <ul>
 * <li>100 {@code helm_hud}: flying by hand in the sky, a run forward at speed, from the camera behind
 * the ship;</li>
 * <li>102 {@code atmosphere_up} and 103 {@code atmosphere_down}: the atmosphere overlay as the ship
 * crosses the line up into an orbit and down into a sky;</li>
 * <li>101 {@code landing_hint}: hovering a few blocks over flat ground, the HUD's "Y: Land";</li>
 * <li>107 {@code field_landing}: the landing the Helm key started, part way down;</li>
 * <li>104 {@code refit_interceptor}: the shipwright's refit to the interceptor, in the Watch's paint,
 * through the refit's steam and sparks; 105 {@code interceptor_imperial} and 106
 * {@code interceptor_vasch}: the interceptor in the other two paints, from other sides.</li>
 * </ul>
 * Everything happens on a grass stage {@value #LIFT} blocks above the player (open sky and flat
 * ground, the Milestone 4 travel steps' stage), with the player's own ship: a repaired courier
 * given at the stage's centre, the helm unlocked with {@code /oldrepublic ship helm}.
 *
 * <p><b>Real input.</b> The owner boards on the server; then the Helm key takes the helm and lands,
 * and Jump, Forward, Back and Sneak fly the ship, all of them real key holds of one or more ticks.
 * The game-test world has no orbits until the space feature is in, so the two atmosphere shots send
 * the {@code atmosphere} event the helm sends at the line, start and end, to the pilot hovering in
 * the sky; the real crossing is the flight client test's. The refit is the shipwright's dialogue
 * action ({@code ship:refit interceptor}), the paints are set on the record as the ship's path
 * would set them.
 *
 * <p><b>Assertions</b>, on the server unless said otherwise: the given ship is READY with a body and
 * the helm unlocked; after the Helm key the record is FLYING with no autopilot run, its body piloted
 * by the owner and risen {@code flight.helmRiseBlocks}, and the client pilots it; after the flight
 * the ship has climbed over the stage and both sides agree on where it hovers; on the client, each
 * atmosphere event puts the overlay up and its end clears it, the pilot still flying; on the client,
 * the landing check passes a few blocks over the stage; the Helm key starts the landing (on the
 * client, the helm's landing stage), after which the ship is READY on the stage at the spot under
 * it, the owner's last landing, with the owner off it beside it and the client's helm released; the
 * refit is paid (the credits and the alloy plates given for it are gone) and makes an interceptor
 * record at 80 hull whose new body the client sees; each paint reaches the client's body.
 *
 * <p>Put back at the end: the stage and everything on it, the ship (record and body; a ship the
 * player had before is restored), the player's travel data and planet visits, game mode (survival,
 * as the driver sets it), camera and position. Not put back: the story's helm, ship and landing hooks
 * have been told (the player is in no chapter that waits for them here).
 */
public final class FlightClientSteps {
	/** Height of the stage above the player's starting position, its highest floor, half-width and the air cleared above it. */
	private static final int LIFT = 60;
	private static final int MAX_STAGE_Y = 240;
	private static final int HALF = 32;
	private static final int AIR_ABOVE = 24;
	/** Layers per fill command (65 x 65 x 7 stays under the command's 32768-block limit). */
	private static final int SLAB = 7;
	/** The player stands this far west of the ship's centre before boarding (the courier is 7 blocks wide). */
	private static final int ASIDE = 6;
	/** Standing eye height. */
	private static final double EYE = 1.62;

	/** Ticks for boarding, for the Helm key's take-off (the Spool and the rise are 60 by default), for a view to settle and for the client to hear of a change. */
	private static final int BOARD_TIMEOUT = 40;
	private static final int HELM_TIMEOUT = 200;
	private static final int SETTLE = 20;
	private static final int SYNC_TIMEOUT = 40;

	/** The flight before the HUD shot: the climb, the pauses, the run forward (the shot ends it) and the brake (0.04 a tick of thrust, 0.08 of brake). */
	private static final int CLIMB_TICKS = 20;
	private static final int PAUSE_TICKS = 8;
	private static final int CRUISE_TICKS = 14;
	private static final int BRAKE_TICKS = 7;
	/** After the climb the ship's base is at least this high over the stage. */
	private static final double MIN_HEIGHT = 8;
	/** The two sides agree on a hovering ship's place to within this (blocks). */
	private static final double MAX_GAP = 1.0;

	/** The atmosphere shot this many ticks after the overlay came up (it covers the screen in 12), and the ticks it gets to clear after its end. */
	private static final int ATMOSPHERE_SHOT = 11;
	private static final int ATMOSPHERE_CLEAR = 80;

	/** Sneak is let go once the ship's base is this high over the stage (the thrusters stop it about a block lower, inside {@code flight.landHeight}). */
	private static final double HINT_HEIGHT = 5.0;
	private static final int DESCENT_TIMEOUT = 120;
	/** Ticks the landing check gets to pass once the ship hovers (it runs every 5). */
	private static final int HINT_TIMEOUT = 60;
	/** The field landing's shot, ticks into the server's descent; the ticks the descent and the Settle get. */
	private static final int LANDING_SHOT = 8;
	private static final int LAND_TIMEOUT = 200;
	/** Ticks the refit gets to swap the body ({@code Ships.tickBody} looks every 10). */
	private static final int REFIT_TIMEOUT = 60;
	/** Ticks from the new body on the client to its shot: the refit's sparks still fly. */
	private static final int REFIT_SHOT = 4;
	/** The paint shots this many ticks after the client's body shows the paint. */
	private static final int PAINT_SHOT = 3;

	private static final Function<Options, KeyMapping> FORWARD = o -> o.keyUp;
	private static final Function<Options, KeyMapping> BACK = o -> o.keyDown;
	private static final Function<Options, KeyMapping> JUMP = o -> o.keyJump;
	private static final Function<Options, KeyMapping> SNEAK = o -> o.keyShift;

	/** What the steps change on the player and put back. */
	private record Saved(TravelData travel, int visits, Optional<ShipRecord> ship, Vec3 pos, float yaw, float pitch) {
	}

	/** A camera around the landed ship, in its own frame: to its right, above its base and ahead of it (blocks). */
	private record View(double right, double up, double ahead) {
	}

	/** The refit's view, from ahead on the right; the Imperial paint's from behind on the left (the engines); the Vasch paint's from the right, higher. */
	private static final View FRONT = new View(6, 4, 8);
	private static final View REAR = new View(-6, 3.5, -8);
	private static final View SIDE = new View(9, 5, 1);

	/** The movement keys held now, so every one is let go of whatever happens. */
	private static final class Held {
		private final TestInput input;
		private final Set<Function<Options, KeyMapping>> keys = new HashSet<>();

		Held(TestInput input) {
			this.input = input;
		}

		void hold(Function<Options, KeyMapping> key) {
			if (keys.add(key)) {
				input.holdKey(key);
			}
		}

		void release(Function<Options, KeyMapping> key) {
			if (keys.remove(key)) {
				input.releaseKey(key);
			}
		}

		void releaseAll() {
			for (Function<Options, KeyMapping> key : List.copyOf(keys)) {
				release(key);
			}
		}
	}

	/** Runs the flight steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		Saved saved = server.computeOnServer(s -> save(player(s)));
		boolean home = server.computeOnServer(s -> player(s).level().dimension().equals(Level.OVERWORLD));

		if (!home) {
			throw new AssertionError("The flight steps need the player in the overworld (their stage is built there)");
		}

		BlockPos start = BlockPos.containing(saved.pos());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, MAX_STAGE_Y), start.getZ());
		Held held = new Held(context.getInput());
		stage.build(server);

		try {
			server.runCommand("gamemode survival @p");
			standOnStage(context, server, stage.centre().west(ASIDE));
			UUID owner = server.computeOnServer(s -> player(s).getUUID());
			giveShip(server, stage, owner);
			board(context, server, owner);
			float yaw = takeHelm(context, server, owner, stage);
			cruise(context, server, owner, stage, yaw, held);
			atmosphere(context, server, owner, true, "oldrepublic_m5_102_atmosphere_up");
			atmosphere(context, server, owner, false, "oldrepublic_m5_103_atmosphere_down");
			landingHint(context, server, owner, stage, held);
			fieldLanding(context, server, owner, stage);
			refit(context, server, owner);
			paint(context, server, owner, ShipBody.PAINT_IMPERIAL, REAR, "oldrepublic_m5_105_interceptor_imperial");
			paint(context, server, owner, ShipBody.PAINT_VASCH, SIDE, "oldrepublic_m5_106_interceptor_vasch");
		} finally {
			held.releaseAll();
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			restore(server, saved);
			stage.clear(server);
			server.runCommand("gamemode survival @p");
			Vec3 back = saved.pos();
			server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", back.x, back.y, back.z, saved.yaw(), saved.pitch()));
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

		/** Removes the floor and everything on it, and items dropped near it. */
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

	// ---------------------------------------------------------------- the helm

	/** A repaired courier at the stage's centre facing south, in the Watch's paint with an empty hold; the helm unlocked by command. */
	private static void giveShip(TestServerContext server, Stage stage, UUID owner) {
		server.runOnServer(s -> {
			Ships.giveAt(player(s), stage.centre(), 0f);
			Ships.update(s, owner, r -> ShipRules.withPaint(r.withHold(List.of()), ShipBody.PAINT_WATCH));
		});
		server.runCommand("oldrepublic ship helm @p");
		String problem = server.computeOnServer(s -> {
			ShipRecord r = ShipRegistry.of(s, owner).orElse(null);

			if (r == null || r.state() != ShipState.READY || Ships.ship(s, owner) == null) {
				return "the given ship is " + (r == null ? "missing" : r.state()) + (Ships.ship(s, owner) == null ? ", with no body" : "");
			}

			return TravelAttachments.get(player(s)).helm() ? null : "/oldrepublic ship helm did not unlock the helm";
		});

		if (problem != null) {
			throw new AssertionError("Setting up the flight steps: " + problem);
		}
	}

	/** The owner boards on the server and looks along the ship. */
	private static void board(ClientGameTestContext context, TestServerContext server, UUID owner) {
		server.runOnServer(s -> {
			ShipBody ship = Ships.ship(s, owner);

			if (ship == null || !player(s).startRiding(ship, true, false)) {
				throw new AssertionError("The owner could not board their ship on the stage");
			}
		});

		if (!pollClient(context, mc -> clientShip(mc) != null, BOARD_TIMEOUT)) {
			throw new AssertionError("The client never showed the owner aboard their ship");
		}

		float yaw = context.computeOnClient(mc -> mc.player.getVehicle().getYRot());
		context.getInput().lookAt(yaw, 0f);
		context.waitTicks(5);
	}

	/**
	 * A real press of the Helm key: the Spool, the rise and the hand-over (4.2), checked on both
	 * sides; the camera goes behind the ship. Returns the ship's heading at the hand-over.
	 */
	private static float takeHelm(ClientGameTestContext context, TestServerContext server, UUID owner, Stage stage) {
		tapHelm(context);

		if (!pollClient(context, mc -> Piloting.isPiloting(), HELM_TIMEOUT)) {
			throw new AssertionError("The Helm key did not hand the ship to its pilot within " + HELM_TIMEOUT + " ticks: "
					+ server.computeOnServer(s -> describe(s, owner)));
		}

		String problem = server.computeOnServer(s -> {
			ShipRecord r = ShipRegistry.of(s, owner).orElse(null);
			ShipBody ship = Ships.ship(s, owner);

			if (r == null || ship == null || r.state() != ShipState.FLYING || r.flight().isPresent() || !piloted(s, owner)) {
				return describe(s, owner);
			}

			int rise = FlightBalance.get().helmRiseBlocks;
			return ship.getY() >= stage.y() + rise - 0.01 ? null
					: String.format(Locale.ROOT, "the ship's base is at %.2f, not %d blocks over the stage at %d", ship.getY(), rise, stage.y());
		});

		if (problem != null) {
			throw new AssertionError("At the hand-over: " + problem);
		}

		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		return context.computeOnClient(mc -> mc.player.getVehicle().getYRot());
	}

	/** A real tap of the Helm key (pressKey presses and lets go within one frame; a player's tap lasts a tick or two). */
	private static void tapHelm(ClientGameTestContext context) {
		context.getInput().holdKey(FlightClient.HELM);
		context.waitTicks(2);
		context.getInput().releaseKey(FlightClient.HELM);
	}

	// ---------------------------------------------------------------- flying

	/**
	 * 100: Jump climbs, Forward runs the ship up to speed (the shot, the HUD's speed up), Back brakes
	 * it; then the ship hovers over the stage, and both sides agree on where.
	 */
	private static void cruise(ClientGameTestContext context, TestServerContext server, UUID owner, Stage stage, float yaw, Held held) {
		context.getInput().lookAt(yaw, 0f);
		held.hold(JUMP);
		context.waitTicks(CLIMB_TICKS);
		held.release(JUMP);
		context.waitTicks(PAUSE_TICKS);
		held.hold(FORWARD);
		context.waitTicks(CRUISE_TICKS);
		context.takeScreenshot("oldrepublic_m5_100_helm_hud");
		held.release(FORWARD);
		held.hold(BACK);
		context.waitTicks(BRAKE_TICKS);
		held.release(BACK);
		context.waitTicks(PAUSE_TICKS);

		Vec3 client = context.computeOnClient(mc -> Piloting.isPiloting() ? mc.player.getVehicle().position() : null);
		Vec3 ship = server.computeOnServer(s -> piloted(s, owner) ? Ships.ship(s, owner).position() : null);

		if (client == null || ship == null) {
			throw new AssertionError("After the climb and the run the pilot is no longer at the helm (client " + (client == null ? "not piloting" : "piloting")
					+ "): " + server.computeOnServer(s -> describe(s, owner)));
		}

		OldRepublicMod.LOGGER.info("Flight client steps: hovering at {} (server {}), {} blocks over the stage", fmt(client), fmt(ship),
				String.format(Locale.ROOT, "%.2f", client.y - stage.y()));

		if (client.distanceTo(ship) > MAX_GAP) {
			throw new AssertionError("The hovering ship is at " + fmt(client) + " on the client and " + fmt(ship) + " on the server");
		}

		if (ship.y < stage.y() + MIN_HEIGHT) {
			throw new AssertionError("Jump did not climb the ship " + MIN_HEIGHT + " blocks over the stage: its base is at " + fmt(ship));
		}
	}

	/**
	 * 102 and 103: the atmosphere overlay, up into an orbit or down into a sky, from the event the
	 * helm sends at the line ({@code atmosphere}, start), then its end; the overlay clears and the
	 * pilot flies on.
	 */
	private static void atmosphere(ClientGameTestContext context, TestServerContext server, UUID owner, boolean up, String shot) {
		int variant = up ? TravelEffects.ATMOSPHERE_UP : TravelEffects.ATMOSPHERE_DOWN;
		String way = up ? "up" : "down";
		atmosphereEvent(server, owner, TravelEffects.ATMOSPHERE_START, variant);

		if (!pollClient(context, mc -> FlightClient.atmosphereShowing(), SYNC_TIMEOUT)) {
			throw new AssertionError("The atmosphere event (" + way + ") put up no overlay");
		}

		context.waitTicks(ATMOSPHERE_SHOT);
		context.takeScreenshot(shot);
		atmosphereEvent(server, owner, TravelEffects.ATMOSPHERE_END, variant);

		if (!pollClient(context, mc -> !FlightClient.atmosphereShowing(), ATMOSPHERE_CLEAR)) {
			throw new AssertionError("The atmosphere overlay (" + way + ") did not clear within " + ATMOSPHERE_CLEAR + " ticks of its end");
		}

		if (!context.computeOnClient(mc -> Piloting.isPiloting())) {
			throw new AssertionError("The pilot left the helm during the atmosphere overlay (" + way + ")");
		}
	}

	/** The {@code atmosphere} event to the pilot, as the helm sends it at the line. */
	private static void atmosphereEvent(TestServerContext server, UUID owner, float stage, int variant) {
		server.runOnServer(s -> {
			ShipBody ship = Ships.ship(s, owner);

			if (ship == null) {
				throw new AssertionError("The ship's body is gone before the atmosphere event");
			}

			EffectEvents.toPlayer(player(s), TravelEffects.ATMOSPHERE, ship.position(), null, ship.getId(), stage, variant);
		});
	}

	// ---------------------------------------------------------------- the landing

	/** 101: Sneak brings the ship down to a few blocks over the stage; hovering there, the landing check passes and the HUD says "Y: Land". */
	private static void landingHint(ClientGameTestContext context, TestServerContext server, UUID owner, Stage stage, Held held) {
		double low = stage.y() + HINT_HEIGHT;
		held.hold(SNEAK);
		boolean down = pollClient(context, mc -> {
			ShipBody ship = clientShip(mc);
			return ship != null && ship.getY() <= low;
		}, DESCENT_TIMEOUT);
		held.release(SNEAK);

		if (!down) {
			throw new AssertionError("Sneak did not bring the ship down to " + HINT_HEIGHT + " blocks over the stage within " + DESCENT_TIMEOUT + " ticks: "
					+ server.computeOnServer(s -> describe(s, owner)));
		}

		if (!pollClient(context, mc -> landing() == Landing.OK, HINT_TIMEOUT)) {
			Landing said = context.computeOnClient(mc -> landing());
			Vec3 at = context.computeOnClient(mc -> {
				ShipBody ship = clientShip(mc);
				return ship == null ? null : ship.position();
			});
			throw new AssertionError("Hovering at " + fmt(at) + " over the flat stage at " + stage.y() + ", the landing check says " + said);
		}

		context.waitTicks(2);
		context.takeScreenshot("oldrepublic_m5_101_landing_hint");
	}

	/**
	 * 107: the Helm key lands the ship (4.6): the server's descent onto the spot below (the shot part
	 * way down), the Settle, the owner put off; then the landing is checked.
	 */
	private static void fieldLanding(ClientGameTestContext context, TestServerContext server, UUID owner, Stage stage) {
		tapHelm(context);

		if (!pollClient(context, mc -> helm() == FlightStatusPayload.HELM_LANDING, SYNC_TIMEOUT)) {
			throw new AssertionError("The Helm key over a spot that passes did not start the landing: " + server.computeOnServer(s -> describe(s, owner)));
		}

		context.waitTicks(LANDING_SHOT);
		context.takeScreenshot("oldrepublic_m5_107_field_landing");
		boolean landed = pollServer(context, server, s -> ShipRegistry.of(s, owner).map(r -> r.state() == ShipState.READY && r.flight().isEmpty()).orElse(false)
				&& !Ships.isAboard(player(s)), LAND_TIMEOUT);

		if (!landed) {
			throw new AssertionError("The landing did not end within " + LAND_TIMEOUT + " ticks: " + server.computeOnServer(s -> describe(s, owner)));
		}

		String problem = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ShipRecord r = ShipRegistry.of(s, owner).orElseThrow();
			ShipBody ship = Ships.ship(s, owner);
			BlockPos at = r.pos().pos();

			if (!r.pos().dimension().equals(Level.OVERWORLD) || at.getY() != stage.y() || Math.abs(at.getX() - stage.x()) > HALF
					|| Math.abs(at.getZ() - stage.z()) > HALF) {
				return "the ship landed at " + at.toShortString() + " in " + r.pos().dimension().identifier() + ", not on the stage";
			}

			if (!TravelAttachments.get(p).lastPad().equals(Optional.of(r.pos()))) {
				return "the owner's last landing is " + TravelAttachments.get(p).lastPad().map(g -> g.pos().toShortString()).orElse("none") + ", not "
						+ at.toShortString();
			}

			if (ship == null || ship.position().distanceTo(Vec3.atBottomCenterOf(at)) > 1.0) {
				return "the body is not on the landing spot " + at.toShortString() + (ship == null ? " (no body)" : " but at " + fmt(ship.position()));
			}

			if (p.distanceTo(ship) > 10 || p.getY() < stage.y() - 0.5) {
				return "the owner is not beside the ship on the stage after the Settle (at " + fmt(p.position()) + ")";
			}

			return null;
		});

		if (problem != null) {
			throw new AssertionError("After the field landing: " + problem);
		}

		if (!pollClient(context, mc -> FlightClient.status() == null, SYNC_TIMEOUT)) {
			throw new AssertionError("After the field landing the client still shows the helm (" + context.computeOnClient(mc -> helm()) + ")");
		}

		OldRepublicMod.LOGGER.info("Flight client steps: landed at {}", server.computeOnServer(s -> ShipRegistry.of(s, owner).orElseThrow().pos().pos().toShortString()));
	}

	// ---------------------------------------------------------------- the shipwright

	/**
	 * 104: the refit to the interceptor ({@code ship:refit interceptor}, 4.9), from a spectator's view
	 * ahead of the ship: paid with the credits and the alloy plates given for it, an interceptor
	 * record at its most hull, the new body on the client (the shot, through the refit's sparks).
	 */
	private static void refit(ClientGameTestContext context, TestServerContext server, UUID owner) {
		server.runCommand("gamemode spectator @p");
		look(context, server, owner, FRONT);
		FlightBalance b = server.computeOnServer(s -> FlightBalance.get());
		int[] before = server.computeOnServer(s -> new int[] {Credits.count(player(s), false), count(player(s), M4Items.ALLOY_PLATE)});
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Credits.give(p, b.refitCredits);
			p.getInventory().add(new ItemStack(M4Items.ALLOY_PLATE, b.refitAlloy));
			DialogueActions.ArgAction refit = DialogueActions.getArg("ship:refit");

			if (refit == null) {
				throw new AssertionError("The shipwright's ship:refit action is not registered");
			}

			refit.run(p, null, "interceptor");
		});
		String problem = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ShipRecord r = ShipRegistry.of(s, owner).orElseThrow();

			if (!r.has(ShipRecord.CLASS_INTERCEPTOR)) {
				return "the record is still a courier (the refit was refused)";
			}

			if (r.hull() != ShipRules.maxHull(r) || r.state() != ShipState.READY) {
				return "the interceptor is " + r.state() + " at hull " + r.hull() + " (expected READY at " + ShipRules.maxHull(r) + ")";
			}

			int credits = Credits.count(p, false);
			int alloy = count(p, M4Items.ALLOY_PLATE);
			return credits == before[0] && alloy == before[1] ? null
					: "it was not paid: " + credits + " credits and " + alloy + " alloy plates left, " + before[0] + " and " + before[1] + " before the gift";
		});

		if (problem != null) {
			throw new AssertionError("The refit to the interceptor: " + problem);
		}

		if (!pollServer(context, server, s -> Ships.ship(s, owner) instanceof Interceptor, REFIT_TIMEOUT)) {
			throw new AssertionError("The refit did not put an interceptor's body on the pad within " + REFIT_TIMEOUT + " ticks: "
					+ server.computeOnServer(s -> describe(s, owner)));
		}

		int body = server.computeOnServer(s -> Ships.ship(s, owner).getId());

		if (!pollClient(context, mc -> mc.level != null && mc.level.getEntity(body) instanceof Interceptor, SYNC_TIMEOUT)) {
			throw new AssertionError("The client never received the interceptor's body");
		}

		context.waitTicks(REFIT_SHOT);
		context.takeScreenshot("oldrepublic_m5_104_refit_interceptor");
	}

	/** 105 and 106: the interceptor in another paint (set on the record), from {@code view}, once the client's body shows it. */
	private static void paint(ClientGameTestContext context, TestServerContext server, UUID owner, int paint, View view, String shot) {
		look(context, server, owner, view);
		server.runOnServer(s -> Ships.update(s, owner, r -> ShipRules.withPaint(r, paint)));
		int body = server.computeOnServer(s -> Ships.ship(s, owner).getId());

		if (!pollClient(context, mc -> mc.level != null && mc.level.getEntity(body) instanceof Interceptor ship && ship.paint() == paint, SYNC_TIMEOUT)) {
			throw new AssertionError("The client's interceptor never showed paint " + paint);
		}

		context.waitTicks(PAINT_SHOT);
		context.takeScreenshot(shot);
	}

	/** Puts the (spectating) player's eye at {@code view} around the landed ship, looking at its middle, and lets the view settle. */
	private static void look(ClientGameTestContext context, TestServerContext server, UUID owner, View view) {
		Vec3[] eyeAndTarget = server.computeOnServer(s -> {
			ShipRecord r = ShipRegistry.of(s, owner).orElseThrow();
			Vec3 base = Vec3.atBottomCenterOf(r.pos().pos());
			return new Vec3[] {base.add(ShipBody.local(view.right(), view.up(), view.ahead(), r.yaw())), base.add(0, 1.2, 0)};
		});
		Vec3 eye = eyeAndTarget[0];
		Vec3 d = eyeAndTarget[1].subtract(eye);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
		server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - EYE, eye.z, yaw, pitch));
		context.waitTicks(SETTLE);
	}

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Whether the owner flies their ship by hand: aboard it, its piloted flag set and the owner its controller. */
	private static boolean piloted(MinecraftServer server, UUID owner) {
		ServerPlayer p = player(server);
		ShipBody ship = Ships.ship(server, owner);
		return ship != null && p.getVehicle() == ship && ship.piloted() && ship.getControllingPassenger() == p;
	}

	/** The ship the local player rides, or null. */
	private static @Nullable ShipBody clientShip(Minecraft mc) {
		return mc.player != null && mc.player.getVehicle() instanceof ShipBody ship ? ship : null;
	}

	/** The helm's stage as the pilot's client last heard it ({@link FlightStatusPayload#HELM_NONE} when off the helm). */
	private static int helm() {
		FlightStatusPayload status = FlightClient.status();
		return status == null ? FlightStatusPayload.HELM_NONE : status.helm();
	}

	/** The landing check the pilot's HUD shows while flying by hand, or null. */
	private static @Nullable Landing landing() {
		FlightStatusPayload status = FlightClient.status();
		return status == null || status.helm() != FlightStatusPayload.HELM_PILOTED ? null : Landing.of(status.landing());
	}

	/** What the server shows of the owner's ship, for a failure's message. */
	private static String describe(MinecraftServer server, UUID owner) {
		ServerPlayer p = player(server);
		ShipRecord r = ShipRegistry.of(server, owner).orElse(null);
		ShipBody ship = Ships.ship(server, owner);
		return "on the server the ship is " + (r == null ? "missing" : r.state() + (r.flight().isPresent() ? " on the autopilot" : "") + " at hull " + r.hull())
				+ (ship == null ? ", with no body" : (ship.piloted() ? ", piloted" : ", not piloted") + " at " + fmt(ship.position()))
				+ (ship != null && p.getVehicle() == ship ? ", the owner aboard" : ", the owner not aboard")
				+ ", the helm " + (TravelAttachments.get(p).helm() ? "unlocked" : "locked");
	}

	/** Puts the player on the stage at {@code pos} (feet), facing south. */
	private static void standOnStage(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 0", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
		context.waitTicks(10);
	}

	/** Checks {@code done} on the client every tick for up to {@code ticks} ticks. */
	private static boolean pollClient(ClientGameTestContext context, Predicate<Minecraft> done, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (context.computeOnClient(mc -> done.test(mc))) {
				return true;
			}

			context.waitTick();
		}

		return context.computeOnClient(mc -> done.test(mc));
	}

	/** Checks {@code done} on the server every tick for up to {@code ticks} ticks. */
	private static boolean pollServer(ClientGameTestContext context, TestServerContext server, Predicate<MinecraftServer> done, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (server.computeOnServer(s -> done.test(s))) {
				return true;
			}

			context.waitTick();
		}

		return server.computeOnServer(s -> done.test(s));
	}

	private static int count(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.is(item)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static String fmt(@Nullable Vec3 v) {
		return v == null ? "none" : String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}

	// ---------------------------------------------------------------- putting back

	private static Saved save(ServerPlayer p) {
		return new Saved(TravelAttachments.get(p), p.getAttachedOrCreate(TravelVisits.VISITS), ShipRegistry.of(p.level().getServer(), p.getUUID()),
				p.position(), p.getYRot(), p.getXRot());
	}

	/**
	 * Out of any screen and off the ship; the test's ship gone (record first, so a helm still running
	 * ends at its next tick, then the body) and any earlier one put back; the travel data and visits
	 * as they were.
	 */
	private static void restore(TestServerContext server, Saved saved) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			UUID id = p.getUUID();
			p.closeContainer();
			ShipBody body = Ships.ship(s, id);
			ShipRegistry.Data ships = ShipRegistry.snapshot(s);
			ships.remove(id);
			saved.ship().ifPresent(ships::put);
			ShipRegistry.commit(s, ships);
			p.stopRiding();

			if (body != null && saved.ship().map(r -> !r.body().equals(body.getUUID())).orElse(true)) {
				body.ejectPassengers();
				body.discard();
			}

			saved.ship().ifPresent(r -> Ships.restore(s, id, true));
			TravelAttachments.set(p, saved.travel());
			p.setAttached(TravelVisits.VISITS, saved.visits());
		});
	}

	private FlightClientSteps() {
	}
}

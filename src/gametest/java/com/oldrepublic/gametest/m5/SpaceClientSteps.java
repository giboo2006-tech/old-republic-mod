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
import com.oldrepublic.client.space.SpaceCrew;
import com.oldrepublic.client.travel.FlightClient;
import com.oldrepublic.client.travel.Piloting;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.space.BlockadeGunship;
import com.oldrepublic.space.ShipBolt;
import com.oldrepublic.space.ShipCombat;
import com.oldrepublic.space.SilentSpindle;
import com.oldrepublic.space.SpaceBalance;
import com.oldrepublic.space.SpaceEncounters;
import com.oldrepublic.space.SpaceEntities;
import com.oldrepublic.space.SpaceRules;
import com.oldrepublic.space.Starfighter;
import com.oldrepublic.space.network.ShipCombatStatusPayload;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelData;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for space and ship combat (spec MILESTONE_5.md 5 and 19.2; W2's file).
 * {@code M5ClientTest} runs them in the shared seeded world after the planet and flight steps.
 * Screenshots are in W2's band, {@code oldrepublic_m5_110} to {@code _124}.
 *
 * <p><b>The orbits</b> (5.1), in creative flight, first person, the {@code Standard} preset and the
 * space sky on. In each of {@code orbit_home}, {@code orbit_selvaren}, {@code orbit_varkhast},
 * {@code orbit_ghessar} and {@code orbit_tyrvann} the player stands on the arrival column at y 100,
 * where the planet's rim sits 15 degrees below the horizon: {@code 110_orbit_home} to
 * {@code 114_orbit_tyrvann}. All five look west and 5 degrees down, where the moon
 * ({@code moon_angle} 78) must hang low over the planet's rim: the check of the orbit type's angle
 * sign, since the same transform puts the sun ({@code sun_angle} 325) 55 degrees up in the east.
 * Tyrvann's orbit is shot again at Reduced, High and Cinematic ({@code 115} to {@code 117}: the
 * planet's grid and the storm flashes follow the preset). In Varkhast's Ash Ring the server looks for the asteroid nearest a point of the field
 * (the heightmaps of the loaded chunks, every second column) and the camera stands 18 blocks back
 * from it and 6 up ({@code 118_asteroid_field}); finding none fails the step. In {@code orbit_home}
 * the rain check of 5.1: the overworld rains until every level's rain has eased in (110 ticks), and
 * the client in the orbit must hold rain level 0 (its own level on the server is logged); then
 * {@code /weather clear}, and the same wait again before the shot, so no rain packet lands in it.
 *
 * <p><b>Ship combat</b> (5.3, 5.4, 5.5, 5.6, 5.10), over the arrival column of {@code orbit_tyrvann},
 * clear of asteroids. The steps start from {@code /oldrepublic space reset} and unlock the helm with
 * {@code /oldrepublic ship helm}. The server gives the player a ship there with cannons, shield and
 * missile rack and six concussion missiles, and boards them; they fly it in survival. A real press of
 * the Helm key takes the helm (the Spool and the rise, at most 200 ticks), the camera goes behind the
 * ship. The server then puts three fighters without AI in the ship's frame: a kajidic raider 34
 * blocks ahead and 3.5 up (inside the lock cone, above the cannons' line), a Clan Hask escort and a
 * Hymn interceptor that is neutral, both out of the cannons' line and the cone (the HUD's red, green
 * and white brackets). The attack and use buttons are held for 40 ticks while the raider sweeps from
 * 8 blocks left to 8 right: the cannons fire and heat, the lock completes. {@code 119_combat_hud},
 * then the HUD's numbers must say: the pilot's role, the lock complete on the raider, cannon heat
 * above 0, six missiles. The attack button is let go; the server fires two raider bolts (6 damage
 * each) at the ship, 3 ticks apart: the shield must drop, {@code 120_shield_hit} two ticks after the
 * hit, and the HUD's shield is below its most. The raider moves nearer and the server kills it with
 * a ship weapon strike that has no shooter (nobody is paid XP, credits or a story kill):
 * {@code 121_fighter_exploding} two ticks later; the raider is gone, the HUD's lock is lost, and
 * letting go of the use button fires nothing (six missiles still in the hold). Then the blockade
 * battle with Clan Hask (6.5) is started with its gunship 52 blocks ahead
 * ({@link SpaceEncounters#startBlockadeAt}, whatever the story says): its shield generator must
 * appear within 60 ticks; the battle runs 30 ticks, the hull topped up every 5 so its fire never
 * disables the ship (that would count as a failed blockade with the story); {@code 122_blockade_gunship};
 * the encounter must be a running blockade with the clan and its gunship, and
 * {@code /oldrepublic space reset} must end it.
 *
 * <p><b>The Silent Spindle</b> (5.7), in creative flight, first person. The station is placed with
 * {@code /oldrepublic space spindle place} unless it already is, and checked as placed: a derelict
 * station whose seven anchors have one cell each inside it, the log terminal an observer, the vault
 * a chest, the deck and the four droid posts two cells of air over a block. The helm run is ended and
 * the ship is given anew on the landing deck, nose to the corridor (so nobody leaves a flying ship,
 * 4.10); the player looks over the deck from off its south-east corner ({@code 123_spindle_landing_deck},
 * once the client has every chunk of the station) and, with night vision, at the vault chest through
 * the gap in its bars ({@code 124_spindle_vault}).
 *
 * <p>Each part's problems are collected and reported together at the end; a part that stops (a
 * timeout, a missing body) is reported and the next part still runs. Every step logs what it saw.
 *
 * <p><b>Afterwards</b> the held buttons are let go and {@code /oldrepublic space reset} ends any
 * encounter; the test's fighters are removed; the helm run is ended, the test's ship body removed,
 * the ship registry put back as it was and the player's own ship placed again at its pad (when they
 * had one); the travel data (the helm's unlock among it), level, position, look and survival mode
 * are put back, night vision and slow falling cleared, the overworld's weather cleared, and the
 * camera, preset and space sky option restored. Left behind: the five orbits' generated chunks, and
 * the Silent Spindle, which is placed once per world, with the droids it posted for the day.
 */
public final class SpaceClientSteps {
	// ---------------------------------------------------------------- timing (ticks)

	/** The client gets this long to reach a level with the chunk under the camera. */
	private static final int CLIENT_CHUNK_TIMEOUT = 600;
	/** After a move to another area: chunk meshes and the sky settle. */
	private static final int ARRIVE = 60;
	/** Extra ticks a vista gets to draw its far chunks before its shot. */
	private static final int VISTA = 60;
	/** After a move within an area, a preset change or a look. */
	private static final int SETTLE = 20;
	/** Every level's rain eases by 0.01 a tick, 100 ticks from clear to full rain and back: with slack. */
	private static final int RAIN_EASE = 110;
	/** Boarding, and the helm: the Spool (40 ticks) and the 4-block rise (20), with slack. */
	private static final int BOARD_TIMEOUT = 40;
	private static final int HELM_TIMEOUT = 200;
	/** The triggers are held this long for the combat HUD: the lock ({@code space.lockTicks}, 30) completes inside it. */
	private static final int HOLD_TICKS = 40;
	/** Ticks between the two bolts at the shield, and how long their hit may take to land. */
	private static final int SHOT_GAP = 3;
	private static final int HIT_TIMEOUT = 40;
	/** Ticks from a hit or a kill to its shot: the shimmer or the explosion at its brightest. */
	private static final int FLASH = 2;
	/** Ticks the raider holds its last place before the kill, so its client position has caught up. */
	private static final int HOLD_STILL = 4;
	/** The gunship's generator joins it on its first ticks; the battle then runs this long before the shot, the hull topped up every few ticks. */
	private static final int GUNSHIP_TIMEOUT = 60;
	private static final int BATTLE_TICKS = 30;
	private static final int TOP_UP_EVERY = 5;

	// ---------------------------------------------------------------- places

	/** Each orbit's camera: the arrival column at y 100 (5.1). */
	private static final Vec3 ORBIT_FEET = new Vec3(0.5, 100, 0.5);
	/** Every orbit shot looks west and 5 degrees down: the moon low over the planet's rim. The asteroid step arrives looking east. */
	private static final float WEST = 90f;
	private static final float EAST = -90f;
	private static final float MOON_PITCH = 5f;
	/** The asteroid shot: a point of the Ash Ring (one cluster per 2 chunks) outside the arrival column's clear radius (40). */
	private static final Vec3 FIELD_POINT = new Vec3(96.5, 170, 64.5);
	/** The chunks around the point that are waited for, and how far (in blocks, every second column) the asteroid is looked for. */
	private static final int FIELD_CHUNKS = 3;
	private static final int FIELD_SEARCH = 48;
	private static final int FIELD_STRIDE = 2;
	/** The camera: this far back from the rock toward the point and this far over its top, looking 2 under its top; raised out of rock at most this far. */
	private static final double FIELD_BACK = 18;
	private static final double FIELD_RISE = 6;
	private static final double FIELD_LOOK_DROP = 2;
	private static final int FIELD_RAISE = 24;
	/** The combat: the ship's pad on the arrival column of orbit_tyrvann (no asteroid within 28 blocks), the player beside it. */
	private static final BlockPos SHIP_PAD = new BlockPos(0, 120, 0);
	private static final Vec3 COMBAT_FEET = new Vec3(0.5, 121, -6.5);
	/** Concussion missiles in the test's hold. */
	private static final int MISSILES = 6;
	/** The courier's hull, to top up to during the battle ({@code Ships.update} keeps it to the class's most). */
	private static final int FULL_HULL = 100;
	/** The raider's sweep in the ship's frame (ahead, right, up): inside the 20-degree lock cone and 3.6 blocks over the cannons' line. */
	private static final double RAIDER_AHEAD = 34;
	private static final double RAIDER_SWEEP = 8;
	private static final double RAIDER_ABOVE = 3.5;
	/** The escort and the stranger: out of the cannons' line, and the stranger 28 degrees off the nose (outside the cone). */
	private static final double[] ESCORT = {46, 15, 5};
	private static final double[] STRANGER = {52, -28, -3};
	/** Where the raider dies, nearer the camera. */
	private static final double[] BURST_AT = {22, 5, 2.5};
	/** Each bolt at the shield. */
	private static final float SHOT_DAMAGE = 6f;
	/** The kill: only a ship weapon hurts a fighter. */
	private static final float KILL = 10_000f;
	/** The gunship's middle, broadside across the ship's path. */
	private static final double[] GUNSHIP_AT = {52, 0, -3};
	/**
	 * The Spindle, template-local ({@code station/spindle}, x east, y up, z south): the deck camera
	 * off the landing deck's south-east corner, 9 over it, looking across it at the spine; the vault
	 * camera in the ring junction, looking through the gap in the bars at the vault chest; the
	 * corridor's mouth, where the parked ship's nose points.
	 */
	private static final BlockPos DECK_CAMERA = new BlockPos(58, 26, 52);
	private static final BlockPos DECK_LOOK = new BlockPos(43, 18, 30);
	private static final BlockPos VAULT_CAMERA = new BlockPos(29, 17, 16);
	private static final BlockPos CORRIDOR = new BlockPos(40, 17, 26);
	/** The station's anchors (its sidecar), each one cell. */
	private static final List<SiteAnchors.Anchor> STATION_ANCHORS = List.of(SiteAnchors.Anchor.SPINDLE_DECK, SiteAnchors.Anchor.SPINDLE_LOG,
			SiteAnchors.Anchor.SPINDLE_VAULT, SiteAnchors.Anchor.SPINDLE_POST_1, SiteAnchors.Anchor.SPINDLE_POST_2, SiteAnchors.Anchor.SPINDLE_POST_3,
			SiteAnchors.Anchor.SPINDLE_POST_4);
	private static final Vec3 UP = new Vec3(0, 1, 0);

	// ---------------------------------------------------------------- screenshots

	/** An orbit, its shot and the look (Minecraft's yaw and pitch). */
	private record Shot(Planets.Planet planet, String name, float yaw, float pitch) {
	}

	private static final List<Shot> ORBIT_SHOTS = List.of(
			new Shot(Planets.Planet.HOME, "oldrepublic_m5_110_orbit_home", WEST, MOON_PITCH),
			new Shot(Planets.Planet.SELVAREN, "oldrepublic_m5_111_orbit_selvaren", WEST, MOON_PITCH),
			new Shot(Planets.Planet.VARKHAST, "oldrepublic_m5_112_orbit_varkhast", WEST, MOON_PITCH),
			new Shot(Planets.Planet.GHESSAR, "oldrepublic_m5_113_orbit_ghessar", WEST, MOON_PITCH),
			new Shot(Planets.Planet.TYRVANN, "oldrepublic_m5_114_orbit_tyrvann", WEST, MOON_PITCH));
	private static final List<ClientConfig.Preset> PRESETS = List.of(ClientConfig.Preset.REDUCED, ClientConfig.Preset.HIGH, ClientConfig.Preset.CINEMATIC);
	private static final List<String> PRESET_SHOTS = List.of("oldrepublic_m5_115_orbit_tyrvann_reduced", "oldrepublic_m5_116_orbit_tyrvann_high",
			"oldrepublic_m5_117_orbit_tyrvann_cinematic");
	private static final String ASTEROID_SHOT = "oldrepublic_m5_118_asteroid_field";
	private static final String HUD_SHOT = "oldrepublic_m5_119_combat_hud";
	private static final String SHIELD_SHOT = "oldrepublic_m5_120_shield_hit";
	private static final String BURST_SHOT = "oldrepublic_m5_121_fighter_exploding";
	private static final String GUNSHIP_SHOT = "oldrepublic_m5_122_blockade_gunship";
	private static final String DECK_SHOT = "oldrepublic_m5_123_spindle_landing_deck";
	private static final String VAULT_SHOT = "oldrepublic_m5_124_spindle_vault";

	// ---------------------------------------------------------------- state

	/** The buttons the combat holds. */
	private enum Key {
		ATTACK(o -> o.keyAttack),
		USE(o -> o.keyUse);

		final Function<Options, KeyMapping> mapping;

		Key(Function<Options, KeyMapping> mapping) {
			this.mapping = mapping;
		}
	}

	/** What is put back on the server afterwards: the player's place, their travel data and the ships. */
	private record Saved(ResourceKey<Level> level, Vec3 pos, float yaw, float pitch, TravelData travel, ShipRegistry.Data ships, boolean hadShip) {
	}

	/** What is put back on the client: the camera and the comfort settings the steps change. */
	private record ClientSaved(CameraType camera, ClientConfig.Preset preset, boolean spaceSky) {
	}

	/** What the steps made, for the next step and the clean-up. */
	private static final class Scene {
		final UUID owner;
		final Set<Key> held = EnumSet.noneOf(Key.class);
		final List<UUID> fighters = new ArrayList<>();
		@Nullable UUID raider;
		boolean gaveShip;

		Scene(UUID owner) {
			this.owner = owner;
		}
	}

	/** A ship's frame: its middle and its forward, right and up (unit vectors). */
	private record Frame(Vec3 centre, Vec3 forward, Vec3 right, Vec3 up) {
		static Frame of(Entity ship) {
			Vec3 forward = Vec3.directionFromRotation(ship.getXRot(), ship.getYRot());
			Vec3 flat = forward.cross(UP);
			Vec3 right = flat.lengthSqr() < 1.0e-6 ? ShipBody.local(1, 0, 0, ship.getYRot()) : flat.normalize();
			return new Frame(ship.getBoundingBox().getCenter(), forward, right, right.cross(forward));
		}

		Vec3 at(double ahead, double side, double above) {
			return centre.add(forward.scale(ahead)).add(right.scale(side)).add(up.scale(above));
		}

		Vec3 at(double[] offset) {
			return at(offset[0], offset[1], offset[2]);
		}
	}

	/** Where the asteroid camera stands and what it looks at. */
	private record View(Vec3 feet, Vec3 target, BlockPos rock) {
	}

	/** The placed station and the steps' places on it, in world space. */
	private record Station(SitePiece piece, BlockPos deck, float deckYaw, Vec3 deckCamera, Vec3 deckLook, Vec3 vaultCamera, Vec3 vaultLook) {
	}

	// ---------------------------------------------------------------- the steps

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Saved saved = server.computeOnServer(SpaceClientSteps::save);
		ClientSaved view = context.computeOnClient(mc -> new ClientSaved(mc.options.getCameraType(), ClientConfig.get().preset, ClientConfig.get().spaceSky));
		Scene scene = new Scene(server.computeOnServer(s -> player(s).getUUID()));
		List<String> failures = new ArrayList<>();

		try {
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				ClientConfig config = ClientConfig.get();
				config.preset = ClientConfig.Preset.STANDARD;
				config.spaceSky = true;
			});
			server.runCommand("gamemode creative @p");
			part(failures, "the orbits", () -> orbits(context, server, failures));
			part(failures, "ship combat", () -> combat(context, server, scene, failures));
			part(failures, "the Silent Spindle", () -> spindle(context, server, scene, failures));
		} finally {
			restore(context, server, saved, view, scene, failures);
		}

		if (!failures.isEmpty()) {
			throw new AssertionError("The space client steps (MILESTONE_5.md 19.2) failed: " + String.join("; ", failures));
		}
	}

	/** Runs one part; a part that stops is reported with the failures and the next part runs. */
	private static void part(List<String> failures, String name, Runnable steps) {
		log("{}", name);

		try {
			steps.run();
		} catch (AssertionError | RuntimeException e) {
			OldRepublicMod.LOGGER.error("Space client step: {} stopped", name, e);
			failures.add(name + " stopped: " + e.getMessage());
		}
	}

	// ---------------------------------------------------------------- the orbits (5.1)

	private static void orbits(ClientGameTestContext context, TestServerContext server, List<String> failures) {
		for (Shot shot : ORBIT_SHOTS) {
			Planets.Planet planet = shot.planet();
			arrive(context, server, planet, ORBIT_FEET, rotation(shot.yaw(), shot.pitch()));

			if (planet == Planets.Planet.HOME) {
				rainCheck(context, server, failures);
			}

			context.waitTicks(VISTA);
			context.takeScreenshot(shot.name());
			log("{}: orbit_{} from the arrival column at Standard, yaw {} pitch {}", shot.name(), planet.id(), shot.yaw(), shot.pitch());

			if (planet == Planets.Planet.VARKHAST) {
				part(failures, "the asteroid field", () -> asteroidField(context, server, failures));
			} else if (planet == Planets.Planet.TYRVANN) {
				presets(context);
			}
		}
	}

	/**
	 * 5.1: the client holds its rain at 0 in an orbit. The overworld rains until every level's rain
	 * has eased in, so the orbit's own level sends no more rain packets and the client's level is
	 * what its hold left; then the weather clears and eases out again before the orbit's shot.
	 */
	private static void rainCheck(ClientGameTestContext context, TestServerContext server, List<String> failures) {
		ResourceKey<Level> key = Planets.orbit(Planets.Planet.HOME);
		server.runCommand("execute in minecraft:overworld run weather rain");
		context.waitTicks(RAIN_EASE);
		float orbitRain = server.computeOnServer(s -> orbit(s, Planets.Planet.HOME).getRainLevel(1f));
		boolean dry = context.computeOnClient(mc -> mc.level != null && mc.level.dimension().equals(key) && mc.level.getRainLevel(1f) < 0.01f);
		String seen = context.computeOnClient(mc -> mc.level == null ? "no level"
				: String.format(Locale.ROOT, "%s at rain level %.2f", mc.level.dimension().identifier(), mc.level.getRainLevel(1f)));
		log("rain check: the overworld rains, orbit_home's own rain level is {}; the client is in {}", orbitRain, seen);
		check(failures, dry, "while the overworld rains (orbit_home's own rain level " + orbitRain + "), the client in orbit_home should hold rain level 0 (5.1),"
				+ " but is in " + seen);
		server.runCommand("execute in minecraft:overworld run weather clear");
		context.waitTicks(RAIN_EASE);
	}

	/** Tyrvann's orbit at Reduced, High and Cinematic, then back to Standard. */
	private static void presets(ClientGameTestContext context) {
		for (int i = 0; i < PRESETS.size(); i++) {
			ClientConfig.Preset preset = PRESETS.get(i);
			context.runOnClient(mc -> ClientConfig.get().preset = preset);
			context.waitTicks(SETTLE);
			context.takeScreenshot(PRESET_SHOTS.get(i));
			log("{}: orbit_tyrvann at {}", PRESET_SHOTS.get(i), preset);
		}

		context.runOnClient(mc -> ClientConfig.get().preset = ClientConfig.Preset.STANDARD);
	}

	/** The asteroid nearest a point of Varkhast's Ash Ring, seen from 18 blocks. */
	private static void asteroidField(ClientGameTestContext context, TestServerContext server, List<String> failures) {
		Planets.Planet planet = Planets.Planet.VARKHAST;
		ResourceKey<Level> key = Planets.orbit(planet);
		arrive(context, server, planet, FIELD_POINT, rotation(EAST, 0f));

		try {
			server.waitFor(s -> chunksAround(orbit(s, planet), FIELD_POINT, FIELD_CHUNKS), CLIENT_CHUNK_TIMEOUT);
		} catch (AssertionError timedOut) {
			log("not every chunk within {} of the field point is loaded; looking among those that are", FIELD_CHUNKS);
		}

		View field = server.computeOnServer(s -> asteroidView(orbit(s, planet), FIELD_POINT));

		if (field == null) {
			failures.add("no asteroid within " + FIELD_SEARCH + " blocks of " + fmt(FIELD_POINT) + " in orbit_varkhast (the Ash Ring, one cluster per 2 chunks)");
			return;
		}

		teleport(server, key, field.feet(), facing(field.target()));
		context.waitTicks(SETTLE + VISTA);
		context.takeScreenshot(ASTEROID_SHOT);
		log("{}: the asteroid at {} from {}", ASTEROID_SHOT, field.rock().toShortString(), fmt(field.feet()));
	}

	/** Whether every chunk within {@code radius} chunks of {@code at} is loaded. */
	private static boolean chunksAround(ServerLevel level, Vec3 at, int radius) {
		int cx = SectionPos.blockToSectionCoord(Mth.floor(at.x));
		int cz = SectionPos.blockToSectionCoord(Mth.floor(at.z));

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!level.hasChunk(cx + dx, cz + dz)) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * The camera on the asteroid nearest {@code from}: the top block of the nearest column with rock
	 * (the orbit holds nothing else), the camera back toward {@code from} and over it, raised out of
	 * any rock. Null when no loaded column within {@link #FIELD_SEARCH} has any.
	 */
	private static @Nullable View asteroidView(ServerLevel level, Vec3 from) {
		int x0 = Mth.floor(from.x);
		int z0 = Mth.floor(from.z);
		BlockPos rock = null;
		double best = Double.MAX_VALUE;

		for (int x = x0 - FIELD_SEARCH; x <= x0 + FIELD_SEARCH; x += FIELD_STRIDE) {
			for (int z = z0 - FIELD_SEARCH; z <= z0 + FIELD_SEARCH; z += FIELD_STRIDE) {
				if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
					continue;
				}

				// The first free height: one over the column's top block, the bottom of the world when it is empty.
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;

				if (top < level.getMinY()) {
					continue;
				}

				double distance = from.distanceToSqr(x + 0.5, top + 0.5, z + 0.5);

				if (distance < best) {
					best = distance;
					rock = new BlockPos(x, top, z);
				}
			}
		}

		if (rock == null) {
			return null;
		}

		Vec3 centre = Vec3.atCenterOf(rock);
		Vec3 back = new Vec3(from.x - centre.x, 0, from.z - centre.z);
		back = back.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : back.normalize();
		BlockPos.MutableBlockPos feet = BlockPos.containing(centre.add(back.scale(FIELD_BACK)).add(0, FIELD_RISE, 0)).mutable();

		for (int i = 0; i < FIELD_RAISE && !(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()); i++) {
			feet.move(0, 1, 0);
		}

		return new View(Vec3.atBottomCenterOf(feet), centre.subtract(0, FIELD_LOOK_DROP, 0), rock);
	}

	// ---------------------------------------------------------------- ship combat (5.3 to 5.6, 5.10)

	private static void combat(ClientGameTestContext context, TestServerContext server, Scene scene, List<String> failures) {
		// A clean slate: no encounter, shield, weapon or patrol state from before.
		server.runCommand("oldrepublic space reset");
		server.runCommand("oldrepublic ship helm @p");
		arrive(context, server, Planets.Planet.TYRVANN, COMBAT_FEET, facing(Vec3.atBottomCenterOf(SHIP_PAD)));
		board(context, server, scene);
		takeHelm(context, server, scene.owner);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.getInput().lookAt(0f, 0f);
		context.waitTicks(SETTLE);
		Frame frame = server.computeOnServer(s -> Frame.of(body(s, scene.owner)));
		log("the ship's frame: middle {}, nose {}", fmt(frame.centre()), fmt(frame.forward()));
		server.runOnServer(s -> spawnFighters(s, scene, frame));
		combatHud(context, server, scene, frame, failures);
		shieldHit(context, server, scene, failures);
		fighterExploding(context, server, scene, frame, failures);
		blockade(context, server, scene, failures);
	}

	/** The armed ship at its pad with six missiles, its owner aboard on the server and then on their client, in survival. */
	private static void board(ClientGameTestContext context, TestServerContext server, Scene scene) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Ships.giveAt(p, SHIP_PAD, 0f);
			scene.gaveShip = true;
			Ships.update(s, scene.owner, r -> r.withUpgrades(r.upgrades() | ShipRecord.UPGRADE_CANNONS | ShipRecord.UPGRADE_SHIELD | ShipRecord.UPGRADE_MISSILES)
					.withHold(List.of(new ItemStack(M5Items.CONCUSSION_MISSILE, MISSILES))));
			ShipBody ship = Ships.ship(s, scene.owner);

			if (ship == null || !p.startRiding(ship, true, false)) {
				throw new AssertionError("the owner could not board the armed ship at " + SHIP_PAD.toShortString() + (ship == null ? ": it has no body" : ""));
			}
		});
		// Aboard, so nothing falls: the helm is flown in survival, as in play.
		server.runCommand("gamemode survival @p");

		try {
			context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof ShipBody, BOARD_TIMEOUT);
		} catch (AssertionError timedOut) {
			throw new AssertionError("the client does not show the owner aboard within " + BOARD_TIMEOUT + " ticks", timedOut);
		}

		context.getInput().lookAt(0f, 0f);
		context.waitTicks(5);
	}

	/** A real tap of the Helm key; waits for the hand-over (the Spool and the rise). */
	private static void takeHelm(ClientGameTestContext context, TestServerContext server, UUID owner) {
		TestInput input = context.getInput();
		input.holdKey(FlightClient.HELM);
		context.waitTicks(2);
		input.releaseKey(FlightClient.HELM);

		for (int waited = 0; !context.computeOnClient(mc -> Piloting.isPiloting()); waited++) {
			if (waited >= HELM_TIMEOUT) {
				throw new AssertionError("the Helm key did not hand the armed ship to its pilot within " + HELM_TIMEOUT + " ticks: "
						+ server.computeOnServer(s -> helmState(s, owner)));
			}

			context.waitTick();
		}

		log("the pilot has the helm over orbit_tyrvann: {}", server.computeOnServer(s -> helmState(s, owner)));
	}

	private static String helmState(MinecraftServer server, UUID owner) {
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		ShipBody ship = Ships.ship(server, owner);
		return "the ship " + ShipRegistry.of(server, owner).map(r -> r.state().name()).orElse("missing")
				+ (ship != null && ship.piloted() ? ", piloted" : ", not piloted")
				+ (p != null && ship != null && p.getVehicle() == ship ? ", the owner aboard" : ", the owner not aboard")
				+ ", the helm " + (p != null && TravelAttachments.get(p).helm() ? "unlocked" : "locked");
	}

	/** The raider (hostile), the escort (allied) and the stranger (neutral), without AI, in the ship's frame. */
	private static void spawnFighters(MinecraftServer server, Scene scene, Frame frame) {
		ServerLevel level = orbit(server, Planets.Planet.TYRVANN);
		UUID raider = fighter(level, SpaceEntities.KAJIDIC_RAIDER, SpaceRules.Side.HOSTILE, frame.at(RAIDER_AHEAD, -RAIDER_SWEEP, RAIDER_ABOVE), frame.right());
		scene.raider = raider;
		scene.fighters.add(raider);
		scene.fighters.add(fighter(level, SpaceEntities.HASK_FIGHTER, SpaceRules.Side.ALLIED, frame.at(ESCORT), frame.forward()));
		scene.fighters.add(fighter(level, SpaceEntities.HYMN_INTERCEPTOR, SpaceRules.Side.NEUTRAL, frame.at(STRANGER), frame.right().reverse()));
	}

	private static UUID fighter(ServerLevel level, EntityType<? extends Starfighter> type, SpaceRules.Side side, Vec3 middle, Vec3 heading) {
		Starfighter fighter = type.create(level, EntitySpawnReason.EVENT);

		if (fighter == null) {
			throw new AssertionError("could not create the " + side + " fighter");
		}

		fighter.setup(side, false, SpaceBalance.get());
		fighter.setNoAi(true);
		place(fighter, middle, heading);

		if (!level.addFreshEntity(fighter)) {
			throw new AssertionError("could not add the " + side + " fighter at " + fmt(middle));
		}

		return fighter.getUUID();
	}

	/** Puts a fighter's middle at {@code middle}, its nose along {@code heading}. */
	private static void place(Starfighter fighter, Vec3 middle, Vec3 heading) {
		float yaw = SpaceRules.yawOf(heading);
		float pitch = SpaceRules.pitchOf(heading);
		fighter.snapTo(middle.x, middle.y - fighter.getBbHeight() / 2, middle.z, yaw, pitch);
		fighter.setYHeadRot(yaw);
		fighter.setYBodyRot(yaw);
	}

	private static @Nullable Starfighter raider(MinecraftServer server, Scene scene) {
		ServerLevel level = Planets.orbitLevel(server, Planets.Planet.TYRVANN);
		return scene.raider != null && level != null && level.getEntity(scene.raider) instanceof Starfighter raider && raider.isAlive() ? raider : null;
	}

	private static void moveRaider(MinecraftServer server, Scene scene, Vec3 middle, Vec3 heading) {
		Starfighter raider = raider(server, scene);

		if (raider == null) {
			throw new AssertionError("the raider is gone before it was shot down");
		}

		place(raider, middle, heading);
	}

	/**
	 * Both triggers held for 40 ticks while the raider sweeps across the nose: the cannons fire and
	 * heat, the lock completes. Then the HUD's numbers.
	 */
	private static void combatHud(ClientGameTestContext context, TestServerContext server, Scene scene, Frame frame, List<String> failures) {
		TestInput input = context.getInput();
		hold(input, scene, Key.ATTACK);
		hold(input, scene, Key.USE);

		for (int tick = 1; tick <= HOLD_TICKS; tick++) {
			Vec3 at = frame.at(RAIDER_AHEAD, -RAIDER_SWEEP + 2 * RAIDER_SWEEP * tick / HOLD_TICKS, RAIDER_ABOVE);
			server.runOnServer(s -> moveRaider(s, scene, at, frame.right()));
			context.waitTick();
		}

		context.takeScreenshot(HUD_SHOT);
		ShipCombatStatusPayload hud = context.computeOnClient(mc -> SpaceCrew.status());
		int raiderId = server.computeOnServer(s -> {
			Starfighter raider = raider(s, scene);
			return raider == null ? -2 : raider.getId();
		});
		log("{}: role {}, shield {}/{}, cannon heat {}, missiles {}, lock on {} at {} (the raider is {})", HUD_SHOT, hud.role(), hud.shield(), hud.shieldMax(),
				hud.cannonHeat(), hud.missiles(), hud.lockTarget(), hud.lockProgress(), raiderId);
		check(failures, hud.role() == ShipCombatStatusPayload.PILOT, "the combat HUD's role is " + hud.role() + ", not the pilot's");
		check(failures, hud.lockTarget() == raiderId && hud.lockProgress() >= 0.999f,
				"after " + HOLD_TICKS + " ticks of the use button the lock is on " + hud.lockTarget() + " at " + hud.lockProgress() + ", not complete on the raider (" + raiderId + ")");
		check(failures, hud.cannonHeat() > 0f, "after " + HOLD_TICKS + " ticks of the attack button the cannons have no heat");
		check(failures, hud.missiles() == MISSILES, "the HUD counts " + hud.missiles() + " missiles, not " + MISSILES);
		release(input, scene, Key.ATTACK);
	}

	/** Two raider bolts at the ship: the shield takes them. */
	private static void shieldHit(ClientGameTestContext context, TestServerContext server, Scene scene, List<String> failures) {
		float before = server.computeOnServer(s -> ShipCombat.shieldPoints(player(s)));

		for (int shot = 0; shot < 2; shot++) {
			server.runOnServer(s -> fireAtShip(s, scene));
			context.waitTicks(SHOT_GAP);
		}

		try {
			server.waitFor(s -> ShipCombat.shieldPoints(player(s)) < before - 1f, HIT_TIMEOUT);
		} catch (AssertionError timedOut) {
			failures.add("the raider's bolts did not reach the shield within " + HIT_TIMEOUT + " ticks (shield " + before + ")");
		}

		context.waitTicks(FLASH);
		context.takeScreenshot(SHIELD_SHOT);
		float after = server.computeOnServer(s -> ShipCombat.shieldPoints(player(s)));
		ShipCombatStatusPayload hud = context.computeOnClient(mc -> SpaceCrew.status());
		log("{}: the shield from {} to {}; the HUD shows {}/{}", SHIELD_SHOT, before, after, hud.shield(), hud.shieldMax());
		check(failures, hud.shield() < hud.shieldMax(), "after the hit the HUD's shield is still full (" + hud.shield() + "/" + hud.shieldMax() + ")");
	}

	/** A raider bolt from the raider at the ship's middle. */
	private static void fireAtShip(MinecraftServer server, Scene scene) {
		Starfighter raider = raider(server, scene);
		ShipBody ship = Ships.ship(server, scene.owner);

		if (raider == null || ship == null || !(raider.level() instanceof ServerLevel level)) {
			throw new AssertionError("the raider or the ship is gone before the shots at the shield");
		}

		SpaceBalance b = SpaceBalance.get();
		Vec3 from = raider.getBoundingBox().getCenter();
		Vec3 dir = ship.getBoundingBox().getCenter().subtract(from).normalize();
		ShipBolt.fire(level, raider, null, ShipBolt.Team.HOSTILE, ShipBolt.Style.RAIDER, from.add(dir.scale(2.5)), dir.scale(b.boltSpeed), SHOT_DAMAGE,
				b.boltRange);
	}

	/** The raider, nearer, shot down by a ship weapon strike that pays nobody; the lock is lost and no missile flies. */
	private static void fighterExploding(ClientGameTestContext context, TestServerContext server, Scene scene, Frame frame, List<String> failures) {
		Vec3 at = frame.at(BURST_AT);
		server.runOnServer(s -> moveRaider(s, scene, at, frame.right()));
		context.waitTicks(HOLD_STILL);
		server.runOnServer(s -> {
			Starfighter raider = raider(s, scene);
			ShipBody ship = Ships.ship(s, scene.owner);

			if (raider == null || ship == null || !(raider.level() instanceof ServerLevel level)) {
				throw new AssertionError("the raider or the ship is gone before the kill");
			}

			raider.hurtServer(level, ShipBolt.strikeOf(level, ship, null), KILL);
		});
		context.waitTicks(FLASH);
		context.takeScreenshot(BURST_SHOT);
		boolean gone = server.computeOnServer(s -> raider(s, scene) == null);
		check(failures, gone, "the raider survived a ship weapon strike of " + KILL);

		try {
			context.waitFor(mc -> SpaceCrew.status().lockTarget() < 0, SETTLE);
		} catch (AssertionError timedOut) {
			failures.add("the HUD still shows a lock " + SETTLE + " ticks after its target was shot down");
		}

		release(context.getInput(), scene, Key.USE);
		context.waitTicks(5);
		int missiles = server.computeOnServer(s -> missiles(s, scene.owner));
		log("{}: the raider is {}; {} missiles in the hold after the use button was let go", BURST_SHOT, gone ? "gone" : "still there", missiles);
		check(failures, missiles == MISSILES, "letting go of the use button after the lock's target died fired a missile (" + missiles + " left of " + MISSILES + ")");
	}

	private static int missiles(MinecraftServer server, UUID owner) {
		return ShipRegistry.of(server, owner)
				.map(r -> r.hold().stream().filter(stack -> stack.is(M5Items.CONCUSSION_MISSILE)).mapToInt(ItemStack::getCount).sum())
				.orElse(-1);
	}

	/** The blockade battle with Clan Hask, its gunship ahead; the hull kept up for the shot; then the reset ends it. */
	private static void blockade(ClientGameTestContext context, TestServerContext server, Scene scene, List<String> failures) {
		server.runOnServer(s -> SpaceEncounters.startBlockadeAt(player(s), Frame.of(body(s, scene.owner)).at(GUNSHIP_AT), false, true));

		try {
			server.waitFor(s -> generatorUp(s, scene.owner), GUNSHIP_TIMEOUT);
		} catch (AssertionError timedOut) {
			failures.add("the blockade's gunship has no shield generator " + GUNSHIP_TIMEOUT + " ticks after the battle started");
		}

		for (int waited = 0; waited < BATTLE_TICKS; waited += TOP_UP_EVERY) {
			// The battle's fire must not disable the ship during the shot: that would count as a failed blockade with the story (6.5).
			server.runOnServer(s -> Ships.update(s, scene.owner, r -> r.withHull(FULL_HULL)));
			context.waitTicks(TOP_UP_EVERY);
		}

		context.takeScreenshot(GUNSHIP_SHOT);
		SpaceEncounters.Snapshot battle = server.computeOnServer(s -> SpaceEncounters.snapshot(scene.owner));
		boolean running = server.computeOnServer(s -> SpaceEncounters.blockadeRunning(player(s)));
		log("{}: {}", GUNSHIP_SHOT, battle == null ? "no encounter"
				: battle.type() + " with " + battle.members().size() + " ships, wave " + battle.waveIndex() + (battle.clan() ? ", the clan's escort" : ""));
		check(failures, running && battle != null && battle.type().equals("BLOCKADE") && battle.clan() && battle.gunship() != null,
				"the encounter is not a running blockade with the clan and its gunship: " + battle);
		server.runCommand("oldrepublic space reset");
		boolean ended = server.computeOnServer(s -> SpaceEncounters.snapshot(scene.owner) == null);
		check(failures, ended, "the blockade outlived /oldrepublic space reset");
		server.runOnServer(s -> discardFighters(s, scene));
	}

	private static boolean generatorUp(MinecraftServer server, UUID owner) {
		SpaceEncounters.Snapshot battle = SpaceEncounters.snapshot(owner);
		ServerLevel level = Planets.orbitLevel(server, Planets.Planet.TYRVANN);
		return battle != null && battle.gunship() != null && level != null && level.getEntity(battle.gunship()) instanceof BlockadeGunship gunship
				&& gunship.generator(level) != null;
	}

	private static void discardFighters(MinecraftServer server, Scene scene) {
		for (UUID id : scene.fighters) {
			for (ServerLevel level : server.getAllLevels()) {
				Entity fighter = level.getEntity(id);

				if (fighter != null) {
					fighter.discard();
				}
			}
		}

		scene.fighters.clear();
		scene.raider = null;
	}

	// ---------------------------------------------------------------- the Silent Spindle (5.7)

	private static void spindle(ClientGameTestContext context, TestServerContext server, Scene scene, List<String> failures) {
		ResourceKey<Level> key = Planets.orbit(Planets.Planet.HOME);
		server.runCommand("gamemode creative @p");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

		if (!server.computeOnServer(s -> SilentSpindle.record(s) != null)) {
			server.runCommand("oldrepublic space spindle place");
		}

		Station station = server.computeOnServer(SpaceClientSteps::station);

		if (station == null) {
			throw new AssertionError("the Silent Spindle is not placed in orbit_home, or has no deck or vault (/oldrepublic space spindle place)");
		}

		String problem = server.computeOnServer(s -> stationProblem(orbit(s, Planets.Planet.HOME), station.piece()));

		if (problem != null) {
			failures.add(problem);
		}

		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (p.getVehicle() != null) {
				// The helm run ends first (a new ship where the pilot sits), so the move is no pilot leaving a flying ship (4.10).
				Ships.giveAt(p, BlockPos.containing(p.position()), p.getYRot());
				p.stopRiding();
			}

			TravelService.moveAcross(p, orbit(s, Planets.Planet.HOME), station.deckCamera(), p.getYRot());
			Ships.giveAt(p, station.deck(), station.deckYaw());
			scene.gaveShip = true;
			fly(p);
		});
		teleport(server, key, station.deckCamera(), facing(station.deckLook()));
		waitForClient(context, key, station.deckCamera());
		BoundingBox box = station.piece().box();

		try {
			context.waitFor(mc -> mc.level != null && mc.level.dimension().equals(key) && box.intersectingChunks().allMatch(c -> mc.level.hasChunk(c.x(), c.z())),
					CLIENT_CHUNK_TIMEOUT);
		} catch (AssertionError timedOut) {
			failures.add("the client did not have every chunk of the Silent Spindle within " + CLIENT_CHUNK_TIMEOUT + " ticks");
		}

		context.waitTicks(VISTA);
		context.takeScreenshot(DECK_SHOT);
		log("{}: the station at {}, the ship on its deck at {}", DECK_SHOT, station.piece().origin().toShortString(), station.deck().toShortString());
		server.runCommand("effect give @p minecraft:night_vision infinite 0 true");
		teleport(server, key, station.vaultCamera(), facing(station.vaultLook()));
		context.waitTicks(SETTLE + SETTLE);
		context.takeScreenshot(VAULT_SHOT);
		log("{}: the vault chest at {}", VAULT_SHOT, fmt(station.vaultLook()));
	}

	/** The placed station and the steps' places on it, or null when it is not placed or has no deck or vault. */
	private static @Nullable Station station(MinecraftServer server) {
		WorldSites.SiteRecord record = SilentSpindle.record(server);
		ServerLevel home = Planets.orbitLevel(server, Planets.Planet.HOME);
		SitePiece piece = record == null || home == null ? null : RuntimeSites.pieceOf(home, record);
		BlockPos deck = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.SPINDLE_DECK);
		BlockPos vault = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.SPINDLE_VAULT);

		if (piece == null || deck == null || vault == null) {
			return null;
		}

		Vec3 nose = Vec3.atCenterOf(SiteAnchors.toWorld(piece, CORRIDOR)).subtract(Vec3.atCenterOf(deck));
		return new Station(piece, deck, SpaceRules.yawOf(nose), Vec3.atBottomCenterOf(SiteAnchors.toWorld(piece, DECK_CAMERA)),
				Vec3.atCenterOf(SiteAnchors.toWorld(piece, DECK_LOOK)), Vec3.atBottomCenterOf(SiteAnchors.toWorld(piece, VAULT_CAMERA)), Vec3.atCenterOf(vault));
	}

	/** What is wrong with the station as placed (its kind and its anchors), or null. */
	private static @Nullable String stationProblem(ServerLevel level, SitePiece piece) {
		List<String> problems = new ArrayList<>();

		if (piece.kind() != SiteKind.DERELICT_STATION) {
			problems.add("its kind is " + piece.kind());
		}

		for (SiteAnchors.Anchor anchor : STATION_ANCHORS) {
			List<BlockPos> cells = SiteAnchors.all(piece, anchor);

			if (cells.size() != 1) {
				problems.add(anchor + " has " + cells.size() + " cells");
				continue;
			}

			BlockPos cell = cells.getFirst();

			if (!piece.box().isInside(cell)) {
				problems.add(anchor + " at " + cell.toShortString() + " is outside the station");
				continue;
			}

			switch (anchor) {
				case SPINDLE_LOG -> {
					if (!level.getBlockState(cell).is(Blocks.OBSERVER)) {
						problems.add("the log terminal at " + cell.toShortString() + " is " + level.getBlockState(cell));
					}
				}
				case SPINDLE_VAULT -> {
					if (!level.getBlockState(cell).is(Blocks.CHEST)) {
						problems.add("the vault at " + cell.toShortString() + " is " + level.getBlockState(cell));
					}
				}
				default -> {
					if (!level.getBlockState(cell).isAir() || !level.getBlockState(cell.above()).isAir() || level.getBlockState(cell.below()).isAir()) {
						problems.add(anchor + " at " + cell.toShortString() + " is not two cells of air over a block");
					}
				}
			}
		}

		return problems.isEmpty() ? null : "the Silent Spindle as placed: " + String.join(", ", problems);
	}

	// ---------------------------------------------------------------- saving and restoring

	private static Saved save(MinecraftServer server) {
		ServerPlayer p = player(server);
		return new Saved(p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), TravelAttachments.get(p), ShipRegistry.snapshot(server),
				ShipRegistry.of(server, p.getUUID()).isPresent());
	}

	/** Puts back what the steps changed (see the class comment). */
	private static void restore(ClientGameTestContext context, TestServerContext server, Saved saved, ClientSaved view, Scene scene, List<String> failures) {
		TestInput input = context.getInput();

		for (Key key : List.copyOf(scene.held)) {
			release(input, scene, key);
		}

		server.runCommand("oldrepublic space reset");
		server.runOnServer(s -> {
			discardFighters(s, scene);
			ServerPlayer p = player(s);

			if (scene.gaveShip) {
				// Ends any helm run and puts the pilot off; then the test's body goes and the ships are as they were.
				Ships.giveAt(p, BlockPos.containing(p.position()), p.getYRot());
				ShipBody left = Ships.ship(s, p.getUUID());

				if (left != null) {
					left.discard();
				}

				ShipRegistry.commit(s, saved.ships());

				if (saved.hadShip()) {
					Ships.restore(s, p.getUUID(), true);
				}
			}

			p.stopRiding();
			TravelAttachments.set(p, saved.travel());
			ServerLevel back = s.getLevel(saved.level());

			if (back != null && p.level() != back) {
				TravelService.moveAcross(p, back, saved.pos(), saved.yaw());
			}
		});
		in(server, saved.level(), String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", saved.pos().x, saved.pos().y, saved.pos().z, saved.yaw(),
				saved.pitch()));
		server.runCommand("gamemode survival @p");
		server.runCommand("effect clear @p minecraft:night_vision");
		server.runCommand("effect clear @p minecraft:slow_falling");
		server.runCommand("execute in minecraft:overworld run weather clear");
		context.runOnClient(mc -> {
			mc.options.setCameraType(view.camera());
			ClientConfig config = ClientConfig.get();
			config.preset = view.preset();
			config.spaceSky = view.spaceSky();
		});

		try {
			context.waitFor(mc -> mc.level != null && mc.level.dimension().equals(saved.level()), CLIENT_CHUNK_TIMEOUT);
		} catch (AssertionError timedOut) {
			check(failures, false, "the client did not get back to " + saved.level().identifier() + " within " + CLIENT_CHUNK_TIMEOUT + " ticks");
		}

		context.waitTicks(ARRIVE);
	}

	// ---------------------------------------------------------------- helpers

	/** Crosses to {@code planet}'s orbit (or moves within it), feet at {@code feet} turned by {@code look} ({@link #rotation}, {@link #facing}), and waits for the client. */
	private static void arrive(ClientGameTestContext context, TestServerContext server, Planets.Planet planet, Vec3 feet, String look) {
		ResourceKey<Level> key = Planets.orbit(planet);
		server.runOnServer(s -> {
			ServerLevel level = orbit(s, planet);
			ServerPlayer p = player(s);

			if (p.level() != level) {
				TravelService.moveAcross(p, level, feet, p.getYRot());
			}
		});
		teleport(server, key, feet, look);
		waitForClient(context, key, feet);
	}

	/** A teleport within the player's level: feet at {@code feet}, turned by {@code look}, flying (in creative) so they hold still. */
	private static void teleport(TestServerContext server, ResourceKey<Level> key, Vec3 feet, String look) {
		in(server, key, String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %s", feet.x, feet.y, feet.z, look));
		server.runOnServer(s -> fly(player(s)));
	}

	/** The {@code tp} command's look at a point (from the eyes). */
	private static String facing(Vec3 target) {
		return String.format(Locale.ROOT, "facing %.3f %.3f %.3f", target.x, target.y, target.z);
	}

	/** The {@code tp} command's look as a yaw and a pitch. */
	private static String rotation(float yaw, float pitch) {
		return String.format(Locale.ROOT, "%.1f %.1f", yaw, pitch);
	}

	/** Waits until the client is in {@code key} with the chunk of {@code at}, then lets the view settle. */
	private static void waitForClient(ClientGameTestContext context, ResourceKey<Level> key, Vec3 at) {
		int chunkX = SectionPos.blockToSectionCoord(Mth.floor(at.x));
		int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(at.z));

		try {
			context.waitFor(mc -> mc.level != null && mc.level.dimension().equals(key) && mc.level.hasChunk(chunkX, chunkZ), CLIENT_CHUNK_TIMEOUT);
		} catch (AssertionError timedOut) {
			throw new AssertionError("the client did not reach " + key.identifier() + " with the chunk at " + fmt(at) + " within " + CLIENT_CHUNK_TIMEOUT
					+ " ticks", timedOut);
		}

		context.waitTicks(ARRIVE);
	}

	private static void fly(ServerPlayer p) {
		if (p.getAbilities().mayfly) {
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
	}

	private static void hold(TestInput input, Scene scene, Key key) {
		if (scene.held.add(key)) {
			input.holdKey(key.mapping);
		}
	}

	private static void release(TestInput input, Scene scene, Key key) {
		if (scene.held.remove(key)) {
			input.releaseKey(key.mapping);
		}
	}

	private static void in(TestServerContext server, ResourceKey<Level> key, String command) {
		server.runCommand("execute in " + key.identifier() + " run " + command);
	}

	private static ServerLevel orbit(MinecraftServer server, Planets.Planet planet) {
		ServerLevel level = Planets.orbitLevel(server, planet);

		if (level == null) {
			throw new AssertionError("the world has no orbit_" + planet.id());
		}

		return level;
	}

	/** The owner's ship body, which the step needs loaded. */
	private static ShipBody body(MinecraftServer server, UUID owner) {
		ShipBody ship = Ships.ship(server, owner);

		if (ship == null) {
			throw new AssertionError("the owner's ship has no loaded body: " + helmState(server, owner));
		}

		return ship;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(List<String> failures, boolean ok, String problem) {
		if (!ok) {
			failures.add(problem);
			OldRepublicMod.LOGGER.error("Space client step failed: {}", problem);
		}
	}

	private static void log(String message, Object... args) {
		OldRepublicMod.LOGGER.info("Space client step: " + message, args);
	}

	private static String fmt(Vec3 v) {
		return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
	}

	private SpaceClientSteps() {
	}
}

package com.oldrepublic.gametest.m4;

import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.blaster.BlasterBalance;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.jet.JetPack;
import com.oldrepublic.equipment.melee.MeleeBalance;
import com.oldrepublic.equipment.melee.MeleeCombat;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Milestone 4 client test steps for blasters, vibroweapons, armour and mods (W4). {@code M4ClientTest}
 * runs them in the shared seeded world (spec MILESTONE_4.md 17.2). Screenshots are
 * {@code oldrepublic_m4_<nn>_<what>} with {@code nn} from 40 to 49:
 * <ul>
 * <li>40 to 45: the pistol, the rifle and the heavy repeater aimed and firing at a target dummy, each
 * in first person (reticle, heat gauge, view model) and third person from the front (arm pose);</li>
 * <li>46: the heat gauge just after a perfect vent (Weapon Action at 80 heat);</li>
 * <li>47: the four Milestone 4 armour sets on armour stands;</li>
 * <li>48: a jet pack burst in the air, from behind;</li>
 * <li>49: the electrostaff charged.</li>
 * </ul>
 * The steps build a flat stone stage 40 blocks above wherever the player stands, stand the player on
 * it facing south, and use real inputs (use, jump, the Weapon Action key) wherever the feature reads
 * one; the server confirms each step did what its screenshot claims. Everything is removed at the
 * end: the dummy, stands and bolts near the stage, the stage, the player's armour and held item; the
 * camera goes back to first person and the player back where they were.
 */
public final class GearClientSteps {
	/** Height of the stage above the player's starting position, and its half-width, depth behind and ahead. */
	private static final int LIFT = 40;
	private static final int HALF_WIDTH = 10;
	private static final int BACK = 8;
	private static final int FRONT = 14;
	/** How far ahead the target dummy stands. */
	private static final int DUMMY_AHEAD = 8;
	/** Tag of everything the steps summon, so cleanup never touches anyone else's entities. */
	private static final String TAG = "or_m4_gear";
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;
	private static final Function<Options, KeyMapping> JUMP = o -> o.keyJump;
	/** The Weapon Action key's default binding (spec 11.1). */
	private static final int WEAPON_ACTION = InputConstants.KEY_N;
	private static final String[] SLOTS = {"head", "chest", "legs", "feet"};
	/** Ticks between chained jet bursts after the 2-tick press: one every 10 ticks, about when the last one tops out. */
	private static final int CHAIN_GAP = 8;
	/** Ticks to wait after the chain: down again and well past the server's 80-tick floating limit. */
	private static final int AFTER_CHAIN = 100;

	/** Runs the gear steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());
		stage.build(server);

		try {
			blaster(context, server, stage, "blaster_pistol", 14, 40, "pistol");
			blaster(context, server, stage, "blaster_rifle", 14, 42, "rifle");
			blaster(context, server, stage, "heavy_repeater", 30, 44, "repeater");
			perfectVent(context, server, stage);
			armour(context, server, stage);
			jetPack(context, server, stage);
			electrostaff(context, server, stage);
		} finally {
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			stage.clear(server);
			undress(server);
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
			server.runCommand("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
			context.waitTicks(10);
		}
	}

	/**
	 * The jet pack against the server's floating check (spec 6.4 and 17.2), for the dedicated-server
	 * leg ({@code allow-flight=false}) of the lead's travel client test; it works on any server
	 * context. On a stage like the gear steps' one, in survival, with the full Mandalorian set and a
	 * jet pack (three charges with Hunter's Tenacity): jump, chain every charge, then wait until well
	 * after landing. Fails unless every charge fired and the player is still connected. No screenshot;
	 * the armour, stage and position are put back after.
	 */
	public static void jetChain(ClientGameTestContext context, TestServerContext server) {
		BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());
		stage.build(server);

		try {
			reset(context, server, stage);
			undress(server);
			server.runCommand("gamemode survival @p");
			// A landing outside the burst's landing window still hurts; this keeps the check about the kick.
			server.runCommand("effect give @p minecraft:resistance 30 4 true");
			server.runCommand("oldrepublic armour give @p mandalorian");
			server.runCommand("oldrepublic gear fit @p chest jet_pack");
			context.waitTicks(10);
			int max = server.computeOnServer(s -> JetPack.maxCharges(player(s), EquipmentM4Balance.mods()));
			context.getInput().holdKeyFor(JUMP, 2);
			context.waitTicks(4);

			for (int burst = 0; burst < max; burst++) {
				context.getInput().holdKeyFor(JUMP, 2);
				context.waitTicks(CHAIN_GAP);
			}

			int left = server.computeOnServer(s -> JetPack.charges(player(s)));

			if (max < 3 || left != 0) {
				throw new AssertionError("The jet chain fired " + (max - left) + " of " + max + " charges (Hunter's Tenacity should make it 3)");
			}

			context.waitTicks(AFTER_CHAIN);
			boolean connected = context.computeOnClient(mc -> mc.player != null && mc.getConnection() != null)
					&& server.computeOnServer(s -> !s.getPlayerList().getPlayers().isEmpty());

			if (!connected) {
				throw new AssertionError("A chain of " + max + " jet bursts got the player kicked for floating");
			}
		} finally {
			stage.clear(server);
			undress(server);
			server.runCommand("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
			context.waitTicks(10);
		}
	}

	/** The stage: a stone floor at {@code (x, y - 1, z)} around the player's spot, with air above it. */
	private record Stage(int x, int y, int z) {
		void build(TestServerContext server) {
			server.runCommand(fill(y, y + 10, "minecraft:air"));
			server.runCommand(fill(y - 1, y - 1, "minecraft:smooth_stone"));
		}

		void clear(TestServerContext server) {
			removeActors(server);
			server.runCommand(fill(y - 1, y + 10, "minecraft:air"));
		}

		/** Removes the dummy, the stands and every bolt within 48 blocks of the stage. */
		void removeActors(TestServerContext server) {
			String near = "x=" + x + ",y=" + y + ",z=" + z + ",distance=..48";
			server.runCommand("kill @e[tag=" + TAG + "," + near + "]");
			server.runCommand("kill @e[type=oldrepublic:blaster_bolt," + near + "]");
		}

		private String fill(int fromY, int toY, String block) {
			return "fill " + (x - HALF_WIDTH) + " " + fromY + " " + (z - BACK) + " " + (x + HALF_WIDTH) + " " + toY + " " + (z + FRONT) + " " + block;
		}

		/** A stage position, {@code dx} east and {@code dz} south of the player's spot, as command coordinates. */
		String at(int dx, int dz) {
			return (x + dx) + " " + y + " " + (z + dz);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Back to the start: actors gone, the player on the spot facing south, first person. */
	private static void reset(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stage.removeActors(server);
		server.runCommand("tp @p " + stage.at(0, 0) + " 0 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().lookAt(0, 0);
		context.waitTicks(10);
	}

	private static void undress(TestServerContext server) {
		for (String slot : SLOTS) {
			server.runCommand("item replace entity @p armor." + slot + " with minecraft:air");
		}
	}

	/** A still husk ahead, facing the player, that no bolt can hurt: the target dummy. */
	private static void dummy(TestServerContext server, Stage stage) {
		server.runCommand("summon minecraft:husk " + stage.at(0, DUMMY_AHEAD) + " {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"" + TAG + "\"]}");
		server.runCommand("effect give @e[type=minecraft:husk,tag=" + TAG + "] minecraft:resistance infinite 4 true");
	}

	/**
	 * Two screenshots of one blaster firing at the dummy, numbered {@code number} (first person) and
	 * {@code number + 1} (third person, from the front): use is held for {@code holdTicks} and the
	 * picture taken while it is still held.
	 */
	private static void blaster(ClientGameTestContext context, TestServerContext server, Stage stage, String item, int holdTicks, int number, String name) {
		reset(context, server, stage);
		dummy(server, stage);
		server.runCommand("item replace entity @p weapon.mainhand with oldrepublic:" + item);
		// Past the draw delay, so the first pull fires.
		context.waitTicks(15);
		fire(context, holdTicks, "oldrepublic_m4_" + number + "_" + name + "_first_person");
		float heat = server.computeOnServer(s -> Blasters.heat(player(s).getMainHandItem()).heat());

		if (heat <= 0f) {
			throw new AssertionError("Holding use with the " + item + " fired nothing: screenshot " + number + " would show an idle gun");
		}

		server.runCommand("oldrepublic gear heat @p 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		// The arms come down after a shot; the next pull raises them again.
		context.waitTicks(30);
		fire(context, holdTicks, "oldrepublic_m4_" + (number + 1) + "_" + name + "_third_person");
		server.runCommand("oldrepublic gear heat @p 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** Holds use for {@code ticks}, takes the screenshot while it is held, then lets go. */
	private static void fire(ClientGameTestContext context, int ticks, String screenshot) {
		context.getInput().holdKey(USE);

		try {
			context.waitTicks(ticks);
			context.takeScreenshot(screenshot);
		} finally {
			context.getInput().releaseKey(USE);
		}

		context.waitTicks(5);
	}

	/** 46: the pistol at 80 heat, Weapon Action pressed: the gauge's perfect-vent sweep and the free-shot dots. */
	private static void perfectVent(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("item replace entity @p weapon.mainhand with oldrepublic:blaster_pistol");
		context.waitTicks(10);
		// The heat holds for coolDelay (16) ticks after it is set, well inside the 70 to 90 band.
		server.runCommand("oldrepublic gear heat @p 80");
		context.waitTicks(2);
		context.getInput().holdKeyFor(WEAPON_ACTION, 2);
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m4_46_perfect_vent");
		boolean perfect = server.computeOnServer(s -> Blasters.heat(player(s).getMainHandItem()).freeShots() == BlasterBalance.get().perfectVentFreeShots);

		if (!perfect) {
			throw new AssertionError("Weapon Action at 80 heat was not a perfect vent: screenshot 46 would show the wrong gauge");
		}

		context.waitTicks(30);
	}

	/** 47: the Watch battle armour, Sith war armour, Mandalorian armour and spacer's gear on stands, from the front. */
	private static void armour(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		String[][] sets = {
			{"watch_battle_helmet", "watch_battle_chest", "watch_battle_legs", "watch_battle_boots"},
			{"sith_war_helmet", "sith_war_chest", "sith_war_legs", "sith_war_boots"},
			{"mandalorian_helmet", "mandalorian_chest", "mandalorian_legs", "mandalorian_boots"},
			{"spacer_cap", "spacer_jacket", "spacer_trousers", "spacer_boots"},
		};
		int[] columns = {-5, -2, 2, 5};

		for (int i = 0; i < sets.length; i++) {
			String tag = TAG + "_stand_" + i;
			server.runCommand("summon minecraft:armor_stand " + stage.at(columns[i], -3) + " {Rotation:[0f,0f],ShowArms:1b,Tags:[\"" + TAG + "\",\"" + tag + "\"]}");

			for (int s = 0; s < SLOTS.length; s++) {
				server.runCommand("item replace entity @e[type=minecraft:armor_stand,tag=" + tag + ",limit=1] armor." + SLOTS[s] + " with oldrepublic:" + sets[i][s]);
			}
		}

		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.getInput().lookAt(0, 10);
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_m4_47_armour_sets");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(5);
	}

	/** 48: spacer's gear with a jet pack; a jump, then jump again in the air: the burst's flame jets, from behind. */
	private static void jetPack(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		undress(server);
		server.runCommand("oldrepublic armour give @p spacer");
		server.runCommand("oldrepublic gear fit @p chest jet_pack");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		// The pack's pips (the JET attachment) reach the client before the key is read.
		context.waitTicks(10);
		context.getInput().holdKeyFor(JUMP, 2);
		context.waitTicks(3);
		context.getInput().holdKeyFor(JUMP, 2);
		context.waitTicks(2);
		context.takeScreenshot("oldrepublic_m4_48_jet_pack_burst");
		int[] charges = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return new int[] {JetPack.charges(p), JetPack.maxCharges(p, EquipmentM4Balance.mods())};
		});

		if (charges[0] >= charges[1]) {
			throw new AssertionError("Jump in the air with a jet pack did not burst (" + charges[0] + " of " + charges[1] + " charges left)");
		}

		// Land before the next step.
		context.waitTicks(40);
		undress(server);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(5);
	}

	/** 49: the electrostaff after a Weapon Action press and the full charge time: the emitters crackle. */
	private static void electrostaff(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("item replace entity @p weapon.mainhand with oldrepublic:electrostaff");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		int chargeTicks = server.computeOnServer(s -> MeleeBalance.get().staffChargeTicks);
		context.getInput().holdKeyFor(WEAPON_ACTION, 2);
		context.waitTicks(chargeTicks + 6);
		context.takeScreenshot("oldrepublic_m4_49_electrostaff_charged");
		boolean charged = server.computeOnServer(s -> MeleeCombat.staffCharged(player(s)));

		if (!charged) {
			throw new AssertionError("Weapon Action with the electrostaff did not charge it: screenshot 49 would show a plain staff");
		}

		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(5);
	}

	private GearClientSteps() {
	}
}

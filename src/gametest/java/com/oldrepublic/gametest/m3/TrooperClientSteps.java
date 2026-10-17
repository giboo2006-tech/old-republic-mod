package com.oldrepublic.gametest.m3;

import java.util.function.Function;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.enemy.trooper.Reinforcements;
import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperEntity;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 client test steps for the trooper feature (the two armies, the frag grenade, the
 * flare and era armour). {@code ExplorationClientTest} runs them in the shared seeded exploration
 * world (spec 17.2). Screenshots are {@code oldrepublic_m3_<nn>_<what>} with {@code nn} from 40 to
 * 49.
 *
 * <p>The steps build a flat stone stage 40 blocks above wherever the player stands (so they do not
 * depend on the terrain the earlier steps left the player on), stand the player on it facing south,
 * and use the trooper debug commands for everything else. Troopers in the role line-ups have no AI
 * so they hold still; the ones that act (grenade, flare, squads) keep it. Armour is shown on armour
 * stands and on the player in third person. Everything is removed again at the end: the troopers,
 * stands and grenades near the stage, the stage, the player's armour; the camera goes back to first
 * person, robe models back on, and the player back where they were.
 */
public final class TrooperClientSteps {
	/** Height of the stage above the player's starting position, and the stage's half-width and depth. */
	private static final int LIFT = 40;
	private static final int HALF_WIDTH = 16;
	private static final int BACK = 12;
	private static final int FRONT = 28;
	/** Armour stand tag, so cleanup never touches anyone else's stands. */
	private static final String STAND_TAG = "or_m3_trooper_stand";
	private static final Function<Options, KeyMapping> INVENTORY = o -> o.keyInventory;
	/** The vanilla survival inventory: its size and the chest armour slot's top-left corner. */
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int CHEST_SLOT_X = 8;
	private static final int CHEST_SLOT_Y = 26;

	/** Runs the trooper steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());
		stage.build(server);

		try {
			roles(context, server, stage, "republic", new String[] {"rifleman", "medic", "sergeant", "officer"}, "oldrepublic_m3_40_republic_roles");
			roles(context, server, stage, "imperial", new String[] {"rifleman", "grenadier", "sergeant", "officer"}, "oldrepublic_m3_41_imperial_roles");
			squadsFiring(context, server, stage);
			downed(context, server, stage);
			grenade(context, server, stage);
			flare(context, server, stage);
			armour(context, server, stage);
			tooltip(context);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				ClientConfig.get().robeModels = true;
			});
			stage.clear(server);
			for (String slot : new String[] {"head", "chest", "legs", "feet"}) {
				server.runCommand("item replace entity @p armor." + slot + " with minecraft:air");
			}

			server.runCommand("tp @p " + start.getX() + " " + start.getY() + " " + start.getZ());
			context.waitTicks(10);
		}
	}

	/** The stage: a stone floor {@code (x, y - 1, z)} centred on the player's spot, with air above it. */
	private record Stage(int x, int y, int z) {
		void build(TestServerContext server) {
			server.runCommand(fill(y, y + 12, "minecraft:air"));
			server.runCommand(fill(y - 1, y - 1, "minecraft:smooth_stone"));
		}

		void clear(TestServerContext server) {
			removeActors(server);
			server.runCommand(fill(y - 1, y + 12, "minecraft:air"));
		}

		/**
		 * Kills every trooper, grenade, bolt and test stand within 48 blocks of the stage, after calling
		 * off any flare reinforcements still on their way to those troopers' squads.
		 */
		void removeActors(TestServerContext server) {
			Vec3 centre = Vec3.atBottomCenterOf(new BlockPos(x, y, z));
			AABB around = new AABB(centre, centre).inflate(48);
			server.runOnServer(s -> {
				for (ServerPlayer player : s.getPlayerList().getPlayers()) {
					for (TrooperEntity trooper : player.level().getEntitiesOfClass(TrooperEntity.class, around, t -> true)) {
						Reinforcements.cancel(Squads.key(trooper));
					}
				}
			});
			String near = "x=" + x + ",y=" + y + ",z=" + z + ",distance=..48";
			server.runCommand("kill @e[type=oldrepublic:republic_trooper," + near + "]");
			server.runCommand("kill @e[type=oldrepublic:sith_trooper," + near + "]");
			server.runCommand("kill @e[type=oldrepublic:frag_grenade," + near + "]");
			server.runCommand("kill @e[type=oldrepublic:blaster_bolt," + near + "]");
			server.runCommand("kill @e[type=minecraft:armor_stand,tag=" + STAND_TAG + "," + near + "]");
		}

		private String fill(int fromY, int toY, String block) {
			return "fill " + (x - HALF_WIDTH) + " " + fromY + " " + (z - BACK) + " " + (x + HALF_WIDTH) + " " + toY + " " + (z + FRONT) + " " + block;
		}

		/** A stage position, {@code dx} east and {@code dz} south of the player's spot, as command coordinates. */
		String at(int dx, int dz) {
			return (x + dx) + " " + y + " " + (z + dz);
		}
	}

	/** Back to the start: actors gone, the player on the spot facing south, first person. */
	private static void reset(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stage.removeActors(server);
		server.runCommand("tp @p " + stage.at(0, 0) + " 0 0");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().lookAt(0, 0);
		context.waitTicks(10);
	}

	/** Spawns one trooper and freezes it facing north (toward the player), so line-ups hold still. */
	private static void standStill(TestServerContext server, Stage stage, String army, String role, int dx, int dz) {
		server.runCommand("oldrepublic trooper spawn " + army + " " + role + " " + stage.at(dx, dz));
		String type = army.equals("republic") ? "oldrepublic:republic_trooper" : "oldrepublic:sith_trooper";
		String near = "x=" + (stage.x() + dx) + ",y=" + stage.y() + ",z=" + (stage.z() + dz) + ",distance=..2";
		server.runCommand("execute as @e[type=" + type + "," + near + ",limit=1,sort=nearest] run data merge entity @s {NoAI:1b,Rotation:[180f,0f]}");
	}

	/** 40 and 41: four roles of one army in a row 8 blocks ahead. */
	private static void roles(ClientGameTestContext context, TestServerContext server, Stage stage, String army, String[] roles, String screenshot) {
		reset(context, server, stage);

		for (int i = 0; i < roles.length; i++) {
			standStill(server, stage, army, roles[i], -3 + 2 * i, 8);
		}

		context.getInput().lookAt(0, 8);
		context.waitTicks(20);
		context.takeScreenshot(screenshot);
	}

	/** 42: a Republic and an Imperial squad, both army-incident, firing at each other across the view. */
	private static void squadsFiring(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("oldrepublic trooper squad republic " + stage.at(-9, 14) + " army");
		server.runCommand("oldrepublic trooper squad imperial " + stage.at(9, 14) + " army");
		context.getInput().lookAt(0, 6);
		context.waitFor(TrooperClientSteps::boltInFlight, 200);
		context.waitTicks(2);
		context.takeScreenshot("oldrepublic_m3_42_squad_firing");
	}

	private static boolean boltInFlight(Minecraft mc) {
		if (mc.level == null) {
			return false;
		}

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof BlasterBolt) {
				return true;
			}
		}

		return false;
	}

	/** 43: a downed Republic trooper at 5 blocks. */
	private static void downed(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("oldrepublic trooper downed republic " + stage.at(0, 5));
		context.getInput().lookAt(0, 25);
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_m3_43_downed_trooper");
	}

	/** 44 and 45: a grenadier's tell, then the landed grenade's fuse (it lands 4 blocks to the side of the player). */
	private static void grenade(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("oldrepublic trooper spawn imperial grenadier " + stage.at(0, 10));
		context.getInput().lookAt(0, 6);
		context.waitTicks(20);
		server.runCommand("oldrepublic trooper act grenade " + stage.at(0, 10) + " " + stage.at(4, 3));
		// Partway into the 20-tick tell: arm drawn back, indicator blinking.
		context.waitTicks(8);
		context.takeScreenshot("oldrepublic_m3_44_grenade_tell");
		context.waitFor(TrooperClientSteps::grenadeLanded, 80);
		context.getInput().lookAt(30, 30);
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_m3_45_grenade_fuse");
		// Let it burst before the next step.
		context.waitTicks(50);
	}

	private static boolean grenadeLanded(Minecraft mc) {
		if (mc.level == null) {
			return false;
		}

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof FragGrenade grenade && grenade.landed()) {
				return true;
			}
		}

		return false;
	}

	/** 46: an Imperial sergeant's flare, climbing and hanging overhead. */
	private static void flare(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		server.runCommand("oldrepublic trooper spawn imperial sergeant " + stage.at(0, 10));
		context.waitTicks(20);
		server.runCommand("oldrepublic trooper act flare " + stage.at(0, 10));
		context.getInput().lookAt(0, -35);
		// The 30-tick tell, then about half of the climb.
		context.waitTicks(45);
		context.takeScreenshot("oldrepublic_m3_46_flare");
		// Its reinforcements are called off with it.
		stage.removeActors(server);
		context.waitTicks(20);
	}

	/**
	 * 47 and 48: the four sets and both rank 3 robes on armour stands behind the player, who wears
	 * the Jedi robes, seen in third person from the front; first with robe models, then plain.
	 */
	private static void armour(ClientGameTestContext context, TestServerContext server, Stage stage) {
		reset(context, server, stage);
		String[][] sets = {
			{"republic_trooper_helmet", "republic_trooper_chestplate", "republic_trooper_leggings", "republic_trooper_boots"},
			{"sith_trooper_helmet", "sith_trooper_chestplate", "sith_trooper_leggings", "sith_trooper_boots"},
			{"jedi_hood", "jedi_robe", "jedi_trousers", "jedi_boots"},
			{"jedi_hood", "jedi_knight_robe", "jedi_trousers", "jedi_boots"},
			{"sith_hood", "sith_robe", "sith_trousers", "sith_boots"},
			{"sith_hood", "sith_lord_robe", "sith_trousers", "sith_boots"},
		};
		int[] columns = {-7, -5, -3, 3, 5, 7};
		String[] slots = {"head", "chest", "legs", "feet"};

		for (int i = 0; i < sets.length; i++) {
			String tag = STAND_TAG + "_" + i;
			server.runCommand("summon minecraft:armor_stand " + stage.at(columns[i], -3) + " {Rotation:[0f,0f],ShowArms:1b,Tags:[\"" + STAND_TAG + "\",\"" + tag + "\"]}");

			for (int s = 0; s < slots.length; s++) {
				server.runCommand("item replace entity @e[type=minecraft:armor_stand,tag=" + tag + ",limit=1] armor." + slots[s] + " with oldrepublic:" + sets[i][s]);
			}
		}

		for (String slot : slots) {
			server.runCommand("item replace entity @p armor." + slot + " with minecraft:air");
		}

		server.runCommand("oldrepublic armour give @p jedi_robes");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.getInput().lookAt(0, 10);
		context.waitTicks(30);
		context.takeScreenshot("oldrepublic_m3_47_armour_robe_models");

		context.runOnClient(mc -> ClientConfig.get().robeModels = false);
		context.waitTicks(5);
		context.takeScreenshot("oldrepublic_m3_48_armour_plain");
		context.runOnClient(mc -> ClientConfig.get().robeModels = true);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(5);
	}

	/** 49: the inventory open with the cursor on the worn robe: the live set line under its name. */
	private static void tooltip(ClientGameTestContext context) {
		context.getInput().pressKey(INVENTORY);
		context.waitFor(mc -> mc.gui.screen() != null, 40);
		context.waitTicks(5);
		double[] cursor = context.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();
			double scaleX = mc.getWindow().getScreenWidth() / (double) screen.width;
			double scaleY = mc.getWindow().getScreenHeight() / (double) screen.height;
			int left = (screen.width - INVENTORY_WIDTH) / 2;
			int top = (screen.height - INVENTORY_HEIGHT) / 2;
			return new double[] {(left + CHEST_SLOT_X + 8) * scaleX, (top + CHEST_SLOT_Y + 8) * scaleY};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(5);
		context.takeScreenshot("oldrepublic_m3_49_set_tooltip");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private TrooperClientSteps() {
	}
}

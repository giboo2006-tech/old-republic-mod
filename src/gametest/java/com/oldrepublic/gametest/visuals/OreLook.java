package com.oldrepublic.gametest.visuals;

import java.util.List;
import java.util.Locale;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.M7Items;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Pictures of the ores and materials ({@code oldrepublic_look_ores_<nn>_<what>}, MILESTONE_7.md 4 and 14): a meteor crater
 * of each start from above and from its rim, the starfall ores in stone and deepslate beside the meteorite's shell, a seam
 * of Tyrvann's clan iron in basalt, and the new blocks and items in the inventory. Runs only when a visuals run names it
 * ({@link Visuals#named}).
 *
 * <p>The superflat world is too thin for a crater, whose bowl goes 14 blocks under its ground line, so each crater is placed
 * ({@code /place template}, without the worldgen's terrain fitting) into a plateau of raised ground beside the stage: the
 * fresh crater south of it, the old one east, the deep one west. The plateaus' grass is at {@link #GROUND}, each template's
 * ground line. While the craters are shot the player hovers over each as a spectator, so the client has the area; the
 * pictures are taken through the free camera.
 */
public final class OreLook {
	/** The superflat ground's top block. */
	private static final int FLAT_TOP = -61;
	/** The plateaus' grass: each crater template's ground line (its template y 13) is placed here. */
	private static final int GROUND = -51;
	/**
	 * Layers a crater template is buried (tools/assets/structures/or/worldgen_or.py): its ground's top block is template
	 * y {@code BURIED - 1}.
	 */
	private static final int BURIED = 14;
	/** Blocks of plateau round each template. */
	private static final int MARGIN = 6;
	/** The most blocks one fill may change (the default of the command block modification limit). */
	private static final int FILL_LIMIT = 32768;
	private static final int LOAD_TIMEOUT = 1200;
	private static final int SEEN_TIMEOUT = 60;
	/** Ticks the client gets to draw a changed or newly seen area. */
	private static final int DRAW_TICKS = 40;
	/** Further ticks the fresh crater's signal smoke gets to rise before the rim picture. */
	private static final int SMOKE_TICKS = 160;
	/** The player's height over the plateau while its crater is shot. */
	private static final double HOVER = 24;
	/** The top-down camera's height over the plateau, enough to see a whole template. */
	private static final double ABOVE = 38;
	/** The rim camera: blocks outside the bowl's radius, eye height over the rim's top block, and its pitch. */
	private static final int RIM_OUT = 2;
	private static final double RIM_EYE = 2.2;
	private static final float RIM_PITCH = 24f;
	/** Where the camera stands for the stage's rock walls: in front of them, looking north. */
	private static final Vec3 WALL_CAMERA = new Vec3(-0.5, -58.0, 4.0);
	private static final float WALL_PITCH = 5f;

	/**
	 * A crater start placed for the pictures: its template's name, where the template's corner goes (x, z), its size, the
	 * bowl's radius (tools/assets/structures/or/starfall_crater.py) and the side its rim picture is taken from (the deep
	 * crater's from the north, so the probe on its south rim is in the picture).
	 */
	private record Crater(String name, int originX, int originZ, Vec3i size, double radius, boolean rimFromNorth) {
		Identifier template() {
			return OldRepublicMod.id("wilds/starfall_crater/" + name);
		}

		int centreX() {
			return originX + size.getX() / 2;
		}

		int centreZ() {
			return originZ + size.getZ() / 2;
		}

		int minX() {
			return originX - MARGIN;
		}

		int maxX() {
			return originX + size.getX() - 1 + MARGIN;
		}

		int minZ() {
			return originZ - MARGIN;
		}

		int maxZ() {
			return originZ + size.getZ() - 1 + MARGIN;
		}
	}

	private static final List<Crater> CRATERS = List.of(
			new Crater("fresh", -28, 36, new Vec3i(57, 22, 39), 8.5, false),
			new Crater("old", 35, -20, new Vec3i(55, 20, 41), 10.5, false),
			new Crater("deep", -82, -16, new Vec3i(45, 22, 33), 7.5, true));

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		server.runCommand("weather clear");

		// The rock walls and the inventory on the stage.
		server.runOnServer(s -> Visuals.player(s).teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 6));
		oreWall(context, server);
		clanIronSeam(context, server);
		Visuals.release(context, server);
		inventory(context, server);

		// The craters: all three are built first, so the fresh one's smoke has risen by its rim picture.
		for (Crater crater : CRATERS) {
			build(server, crater);
		}

		server.runCommand("gamemode spectator @a");

		for (int i = 0; i < CRATERS.size(); i++) {
			crater(context, server, CRATERS.get(i), 4 + i * 2);
		}

		Visuals.release(context, server);
		server.runCommand("forceload remove all");
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> Visuals.player(s).teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z));
		context.waitTicks(DRAW_TICKS);
	}

	// ---------------------------------------------------------------- the stage

	/**
	 * 01: a rock wall on the stage, from the west: stone with a vein of starfall ore, a tuff seam, deepslate with a vein
	 * of deepslate starfall ore, then (past a gap) a block of starfall rock, the meteorite's shell.
	 */
	private static void oreWall(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("fill -7 -60 -3 -4 -57 -3 minecraft:stone");
		server.runCommand("fill -3 -60 -3 -3 -57 -3 minecraft:tuff");
		server.runCommand("fill -2 -60 -3 1 -57 -3 minecraft:deepslate[axis=y]");
		server.runCommand("fill 3 -60 -3 6 -57 -3 " + OldRepublicMod.id("starfall_rock"));
		setBlocks(server, "starfall_ore", new int[][] {{-6, -59}, {-5, -59}, {-5, -58}, {-4, -58}});
		setBlocks(server, "deepslate_starfall_ore", new int[][] {{-1, -58}, {0, -58}, {0, -59}, {1, -60}});
		context.waitTicks(10);
		Visuals.camera(context, server, WALL_CAMERA, 180f, WALL_PITCH);
		context.waitTicks(10);
		shot(context, "01_ores_stone_deepslate");
	}

	/** 02: the wall rebuilt as one of Tyrvann's basalt ridges, with smooth basalt and blackstone, cut by two seams of clan iron ore. */
	private static void clanIronSeam(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("fill -7 -60 -3 6 -56 -3 minecraft:basalt[axis=y]");
		server.runCommand("fill -7 -60 -3 -6 -59 -3 minecraft:smooth_basalt");
		server.runCommand("fill 4 -57 -3 6 -56 -3 minecraft:blackstone");
		server.runCommand("fill 5 -60 -3 6 -60 -3 minecraft:smooth_basalt");
		setBlocks(server, "clan_iron_ore", new int[][] {{-5, -60}, {-4, -59}, {-3, -59}, {-2, -58}, {-1, -58}, {0, -57}, {1, -57}, {1, -56}});
		setBlocks(server, "clan_iron_ore", new int[][] {{3, -60}, {4, -60}, {4, -59}});
		context.waitTicks(10);
		Visuals.camera(context, server, WALL_CAMERA, 180f, WALL_PITCH);
		context.waitTicks(10);
		shot(context, "02_clan_iron_seam");
		server.runCommand("fill -7 -60 -3 6 -56 -3 minecraft:air");
	}

	/** Sets {@code block} (an oldrepublic block) at each (x, y) of the wall's row, z -3. */
	private static void setBlocks(TestServerContext server, String block, int[][] cells) {
		for (int[] cell : cells) {
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d -3 %s", cell[0], cell[1], OldRepublicMod.id(block)));
		}
	}

	/**
	 * 03: the survival inventory with the new blocks and items in the hotbar (the four blocks, raw starfall, the starfall
	 * and hymnsteel ingots, clan iron) and in the bag the other hymnsteel materials beside vanilla's raw iron and iron ingot.
	 */
	private static void inventory(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z);
			List<ItemStack> hotbar = List.of(new ItemStack(M7Items.STARFALL_ORE), new ItemStack(M7Items.DEEPSLATE_STARFALL_ORE),
					new ItemStack(M7Items.STARFALL_ROCK, 16), new ItemStack(M7Items.RAW_STARFALL, 24), new ItemStack(M7Items.STARFALL_INGOT, 9),
					new ItemStack(M7Items.CLAN_IRON_ORE), new ItemStack(M5Items.CLAN_IRON, 6), new ItemStack(M7Items.HYMNSTEEL_INGOT, 2));
			// API-CHECK: Items.RAW_IRON (this repository uses Items.IRON_INGOT)
			List<ItemStack> bag = List.of(new ItemStack(M5Items.HYMNSTONE_SHARD, 4), new ItemStack(M5Items.SUNSTONE, 3), new ItemStack(Items.RAW_IRON, 24),
					new ItemStack(Items.IRON_INGOT, 9));

			for (int i = 0; i < hotbar.size(); i++) {
				p.getInventory().setItem(i, hotbar.get(i));
			}

			// The bag's first row starts at slot 9.
			for (int i = 0; i < bag.size(); i++) {
				p.getInventory().setItem(9 + i, bag.get(i));
			}

			p.getInventory().setSelectedSlot(0);
		});
		context.waitTicks(10);
		context.getInput().pressKey(o -> o.keyInventory);
		context.waitFor(mc -> mc.gui.screen() != null, SEEN_TIMEOUT);
		context.waitTicks(10);
		shot(context, "03_inventory");
		context.setScreen(() -> null);
		context.waitTicks(5);
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
	}

	// ---------------------------------------------------------------- the craters

	/** Loads the crater's plateau, raises it to {@link #GROUND} and places the template into it. */
	private static void build(TestServerContext server, Crater crater) {
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", crater.minX(), crater.minZ(), crater.maxX(), crater.maxZ()));
		server.waitFor(s -> loaded(Visuals.player(s).level(), crater), LOAD_TIMEOUT);
		fill(server, crater, FLAT_TOP, GROUND - 2, "minecraft:stone");
		fill(server, crater, GROUND - 1, GROUND - 1, "minecraft:dirt");
		fill(server, crater, GROUND, GROUND, "minecraft:grass_block");
		server.runCommand(String.format(Locale.ROOT, "place template %s %d %d %d", crater.template(), crater.originX(), GROUND - (BURIED - 1),
				crater.originZ()));
	}

	/** Fills the plateau's layers {@code fromY} to {@code toY} with {@code block}, as many layers a command as the fill limit allows. */
	private static void fill(TestServerContext server, Crater crater, int fromY, int toY, String block) {
		int area = (crater.maxX() - crater.minX() + 1) * (crater.maxZ() - crater.minZ() + 1);
		int layers = Math.max(1, FILL_LIMIT / area);

		for (int y = fromY; y <= toY; y += layers) {
			server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", crater.minX(), y, crater.minZ(), crater.maxX(),
					Math.min(toY, y + layers - 1), crater.maxZ(), block));
		}
	}

	/**
	 * {@code first} and the next number: the crater from straight above (north up), then from just outside its rim at a
	 * standing eye's height, looking across the bowl.
	 */
	private static void crater(ClientGameTestContext context, TestServerContext server, Crater crater, int first) {
		server.runOnServer(s -> Visuals.player(s).teleportTo(crater.centreX() + 0.5, GROUND + HOVER, crater.centreZ() + 0.5));
		context.waitFor(mc -> drawn(mc, crater), LOAD_TIMEOUT);
		context.waitTicks(DRAW_TICKS);
		Visuals.camera(context, server, new Vec3(crater.centreX() + 0.5, GROUND + ABOVE, crater.centreZ() + 0.5), 180f, 90f);
		context.waitTicks(DRAW_TICKS);
		shot(context, String.format(Locale.ROOT, "%02d_%s_above", first, crater.name()));

		if (crater.name().equals("fresh")) {
			context.waitTicks(SMOKE_TICKS);
		}

		int out = (int) Math.round(crater.radius()) + RIM_OUT;
		int rimZ = crater.centreZ() + (crater.rimFromNorth() ? -out : out);
		int rimTop = server.computeOnServer(s -> Visuals.player(s).level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, crater.centreX(), rimZ));
		Visuals.camera(context, server, new Vec3(crater.centreX() + 0.5, rimTop + RIM_EYE, rimZ + 0.5), crater.rimFromNorth() ? 0f : 180f, RIM_PITCH);
		context.waitTicks(DRAW_TICKS);
		shot(context, String.format(Locale.ROOT, "%02d_%s_rim", first + 1, crater.name()));
	}

	/** Whether the server holds every chunk of the crater's plateau. */
	private static boolean loaded(ServerLevel level, Crater crater) {
		for (int chunkX = SectionPos.blockToSectionCoord(crater.minX()); chunkX <= SectionPos.blockToSectionCoord(crater.maxX()); chunkX++) {
			for (int chunkZ = SectionPos.blockToSectionCoord(crater.minZ()); chunkZ <= SectionPos.blockToSectionCoord(crater.maxZ()); chunkZ++) {
				if (!level.hasChunk(chunkX, chunkZ)) {
					return false;
				}
			}
		}

		return true;
	}

	/** Whether the client holds every chunk of the crater's plateau. */
	private static boolean drawn(Minecraft mc, Crater crater) {
		if (mc.level == null) {
			return false;
		}

		for (int chunkX = SectionPos.blockToSectionCoord(crater.minX()); chunkX <= SectionPos.blockToSectionCoord(crater.maxX()); chunkX++) {
			for (int chunkZ = SectionPos.blockToSectionCoord(crater.minZ()); chunkZ <= SectionPos.blockToSectionCoord(crater.maxZ()); chunkZ++) {
				if (mc.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}

		return true;
	}

	private static void shot(ClientGameTestContext context, String what) {
		context.takeScreenshot("oldrepublic_look_ores_" + what);
	}

	private OreLook() {
	}
}

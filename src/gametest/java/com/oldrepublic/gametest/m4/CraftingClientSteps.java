package com.oldrepublic.gametest.m4;

import java.util.Locale;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.crafting.EngineeringBenchScreen;
import com.oldrepublic.client.quest.JournalScreen;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.crafting.network.BenchActionPayload;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStaffs;
import com.oldrepublic.saber.SaberTechniques;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Milestone 4 client test steps for the Engineering Bench, crafting and the saberstaff (W5).
 * {@code M4ClientTest} runs them in the shared seeded world (spec MILESTONE_4.md 17.2).
 * Screenshots are {@code oldrepublic_m4_<nn>_<what>}, band 50 to 59:
 *
 * <ul>
 * <li>50 {@code bench_craft}: the Craft tab, the blaster pistol selected, its parts in the tray;</li>
 * <li>51 {@code bench_modify}: the Modify tab with a pistol and a focused barrel in its slots;</li>
 * <li>52 {@code bench_salvage}: the Salvage tab previewing the Masterwork pistol's returns;</li>
 * <li>53 {@code bench_disciplines}: the Disciplines tab (Armstech 3, Artifice 2);</li>
 * <li>54 {@code masterwork_craft}: the moment a Masterwork craft finishes (pity at 10);</li>
 * <li>55 {@code crafting_tab}: the journal's Crafting tab;</li>
 * <li>56 {@code saberstaff_two_blades} and 57 {@code saberstaff_one_blade}: the lit staff in third
 * person, before and after the Weapon Action;</li>
 * <li>58 {@code workbench_lens}: a Masterwork honed lens in the Lightsaber Workbench's lens slot;</li>
 * <li>59 {@code staff_whirl}: the staff's whirl as it strikes.</li>
 * </ul>
 *
 * <p>Everything happens on a small stage built high above the player in their current level, so
 * the terrain never blocks the view. The player's crafting data, held item, position and camera
 * are put back afterwards, and the stage, both benches and every item the steps made are removed.
 */
public final class CraftingClientSteps {
	/** The stage's floor height: above the seeded world's terrain. */
	private static final int STAGE_Y = 236;
	/** The Engineering Bench stands this many blocks south (+Z) of the player, the workbench beside it. */
	private static final int BENCH_DZ = 2;
	private static final int WORKBENCH_DX = 3;
	private static final String PISTOL = OldRepublicMod.id("blaster_pistol").toString();

	public static void run(ClientGameTestContext context, TestServerContext server) {
		double[] origin = new double[5];
		CraftingData[] before = new CraftingData[1];
		ItemStack[] held = new ItemStack[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			origin[0] = p.getX();
			origin[1] = p.getY();
			origin[2] = p.getZ();
			origin[3] = p.getYRot();
			origin[4] = p.getXRot();
			before[0] = CraftingAttachments.get(p);
			held[0] = p.getMainHandItem().copy();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			// Armstech 3 with the Masterwork pity full, Artifice 2 (the lens slot), and two trainer schematics known.
			CraftingBalance b = CraftingBalance.get();
			CraftingData data = CraftingData.EMPTY
					.withProgress(new Progress(Discipline.ARMSTECH, 3, levelXp(b, 3) + 140, b.pityCrafts))
					.withProgress(new Progress(Discipline.ARTIFICE, 2, levelXp(b, 2) + 60, 4))
					.withKnown(OldRepublicMod.id("heavy_repeater"))
					.withKnown(OldRepublicMod.id("lens_honed_masterwork"))
					.withFirstCraft(OldRepublicMod.id("grip_staff"))
					.withChosenAt(0L);
			CraftingAttachments.set(p, data);
		});
		int bx = (int) Math.floor(origin[0]);
		int bz = (int) Math.floor(origin[2]);
		BlockPos bench = new BlockPos(bx, STAGE_Y, bz + BENCH_DZ);
		BlockPos workbench = new BlockPos(bx + WORKBENCH_DX, STAGE_Y, bz + BENCH_DZ);

		try {
			buildStage(server, bx, bz, "minecraft:polished_deepslate", "minecraft:deepslate_tiles");
			server.runCommand(String.format(Locale.ROOT, "execute at @p run setblock %d %d %d %s[facing=north]", bench.getX(), bench.getY(), bench.getZ(),
					OldRepublicMod.id("engineering_bench")));
			server.runCommand(String.format(Locale.ROOT, "execute at @p run setblock %d %d %d %s", workbench.getX(), workbench.getY(), workbench.getZ(),
					OldRepublicMod.id("lightsaber_workbench")));
			server.runCommand(String.format(Locale.ROOT, "execute as @p at @s run tp @s %d.5 %d %d.5 0 20", bx, STAGE_Y, bz));
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(30);

			bench(context, server, bench);
			craftingTab(context);
			saberstaff(context, server);
			lens(context, server, workbench);
		} finally {
			// Put everything back: the open screen, crafting data, held item, stage, position and camera.
			context.setScreen(() -> null);
			context.waitTicks(5);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				CraftingAttachments.set(p, before[0]);
				p.setItemInHand(InteractionHand.MAIN_HAND, held[0]);
			});
			server.runCommand(String.format(Locale.ROOT, "execute at @p run setblock %d %d %d minecraft:air", bench.getX(), bench.getY(), bench.getZ()));
			server.runCommand(String.format(Locale.ROOT, "execute at @p run setblock %d %d %d minecraft:air", workbench.getX(), workbench.getY(), workbench.getZ()));
			buildStage(server, bx, bz, "minecraft:air", "minecraft:air");
			server.runCommand(String.format(Locale.ROOT, "execute as @p at @s run tp @s %.3f %.3f %.3f %.1f %.1f", origin[0], origin[1], origin[2], origin[3], origin[4]));
			server.runCommand("kill @e[type=minecraft:item,distance=..12]");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(20);
		}
	}

	/** A floor and a dark back wall behind the benches, in the player's level. */
	private static void buildStage(TestServerContext server, int bx, int bz, String floor, String wall) {
		server.runCommand(String.format(Locale.ROOT, "execute at @p run fill %d %d %d %d %d %d minecraft:air", bx - 6, STAGE_Y, bz - 4, bx + 7, STAGE_Y + 6, bz + 7));
		server.runCommand(String.format(Locale.ROOT, "execute at @p run fill %d %d %d %d %d %d %s", bx - 6, STAGE_Y - 1, bz - 4, bx + 7, STAGE_Y - 1, bz + 7, floor));
		server.runCommand(String.format(Locale.ROOT, "execute at @p run fill %d %d %d %d %d %d %s", bx - 6, STAGE_Y, bz + 6, bx + 7, STAGE_Y + 5, bz + 6, wall));
	}

	// ---------------------------------------------------------------- the Engineering Bench (50 to 54)

	private static void bench(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		open(context, server, pos, EngineeringBenchScreen.class);

		// 50: the Craft tab with the pistol selected and its parts in the tray.
		onBench(server, (p, menu) -> {
			tab(p, menu, EngineeringBenchMenu.TAB_CRAFT);
			menu.getSlot(EngineeringBenchMenu.TRAY_START).set(new ItemStack(M3Items.DURASTEEL_PLATE, 3));
			menu.getSlot(EngineeringBenchMenu.TRAY_START + 1).set(new ItemStack(Items.IRON_INGOT, 2));
			menu.getSlot(EngineeringBenchMenu.TRAY_START + 2).set(new ItemStack(Items.REDSTONE, 2));
			menu.getSlot(EngineeringBenchMenu.TRAY_START + 3).set(new ItemStack(Items.COPPER_INGOT, 1));
		});
		context.waitTicks(5);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof EngineeringBenchScreen screen) {
				screen.selectForTest(OldRepublicMod.id("blaster_pistol"));
			}
		});
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m4_50_bench_craft");

		// 54: the craft runs and, with the pity count full, finishes as a Masterwork.
		Schematic pistol = Schematics.get(OldRepublicMod.id("blaster_pistol"));

		if (pistol == null) {
			throw new AssertionError("The blaster_pistol schematic is not loaded; the bench steps cannot craft");
		}

		onBench(server, (p, menu) -> BenchService.handle(p, new BenchActionPayload(EngineeringBenchMenu.TAB_CRAFT, PISTOL, BenchActionPayload.CRAFT)));
		server.waitFor(s -> player(s).containerMenu instanceof EngineeringBenchMenu menu && menu.data(EngineeringBenchMenu.DATA_TOTAL) == 0
				&& !menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem().isEmpty(), pistol.craftTicks() + 60);
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m4_54_masterwork_craft");
		String quality = server.computeOnServer(s -> player(s).containerMenu instanceof EngineeringBenchMenu menu
				? Crafting.quality(menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem()).name() : "no bench");

		if (!Quality.MASTERWORK.name().equals(quality)) {
			throw new AssertionError("The craft with a full pity count gave " + quality + ", not a Masterwork");
		}

		context.waitTicks(30);

		// 51: the Modify tab, a fresh pistol and a focused barrel in its slots.
		onBench(server, (p, menu) -> {
			tab(p, menu, EngineeringBenchMenu.TAB_MODIFY);
			menu.getSlot(EngineeringBenchMenu.GEAR).set(new ItemStack(EnemyItems.BLASTER_PISTOL));
			menu.getSlot(EngineeringBenchMenu.MOD).set(new ItemStack(M4Items.MOD_FOCUSED_BARREL));
		});
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m4_51_bench_modify");
		onBench(server, (p, menu) -> BenchService.handle(p, new BenchActionPayload(EngineeringBenchMenu.TAB_MODIFY, "", BenchActionPayload.FIT)));
		context.waitTicks(15);

		// 52: the Salvage tab previewing the Masterwork pistol.
		onBench(server, (p, menu) -> {
			tab(p, menu, EngineeringBenchMenu.TAB_SALVAGE);
			ItemStack made = menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem().copy();
			menu.getSlot(EngineeringBenchMenu.OUTPUT).set(ItemStack.EMPTY);
			menu.getSlot(EngineeringBenchMenu.SALVAGE).set(made);
		});
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m4_52_bench_salvage");

		// 53: the Disciplines tab.
		onBench(server, (p, menu) -> tab(p, menu, EngineeringBenchMenu.TAB_DISCIPLINES));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m4_53_bench_disciplines");

		// Empty every slot the steps filled, so closing returns nothing.
		onBench(server, (p, menu) -> {
			for (int slot = EngineeringBenchMenu.TRAY_START; slot < EngineeringBenchMenu.INV_START; slot++) {
				menu.getSlot(slot).set(ItemStack.EMPTY);
			}

			menu.broadcastChanges();
		});
		context.waitTicks(2);
		context.setScreen(() -> null);
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- the journal (55)

	private static void craftingTab(ClientGameTestContext context) {
		context.runOnClient(client -> JournalScreen.openOnTab("crafting"));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m4_55_crafting_tab");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- the saberstaff (56, 57, 59)

	private static void saberstaff(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			SaberBalance b = SaberToggle.balance();
			ItemStack staff = SaberItems.buildStaff(crystal(p, BladeColor.BLUE), crystal(p, BladeColor.BLUE), BladeColor.BLUE, Emitter.GUARDIAN, b);
			SaberToggle.setIgnited(staff, true, p.level().getGameTime());
			p.setItemInHand(InteractionHand.MAIN_HAND, staff);
		});
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(20);
		context.takeScreenshot("oldrepublic_m4_56_saberstaff_two_blades");

		server.runOnServer(s -> SaberStaffs.toggleSingle(player(s), player(s).getMainHandItem()));
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_m4_57_saberstaff_one_blade");

		server.runOnServer(s -> SaberStaffs.toggleSingle(player(s), player(s).getMainHandItem()));
		context.waitTicks(15);
		boolean whirl = server.computeOnServer(s -> SaberTechniques.startHeavy(player(s)));

		if (!whirl) {
			throw new AssertionError("The heavy attack with a lit saberstaff did not start a whirl");
		}

		context.waitTicks(SaberToggle.balance().whirlWindup + 2);
		context.takeScreenshot("oldrepublic_m4_59_staff_whirl");
		context.waitTicks(40);

		server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- the Lightsaber Workbench (58)

	private static void lens(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
		open(context, server, pos, LightsaberWorkbenchScreen.class);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (!(p.containerMenu instanceof LightsaberWorkbenchMenu menu)) {
				throw new AssertionError("The Lightsaber Workbench menu is not open on the server");
			}

			menu.getSlot(SaberAssembly.CRYSTAL).set(SaberAssembly.crystalStack(crystal(p, BladeColor.GREEN)));
			menu.getSlot(SaberAssembly.EMITTER).set(new ItemStack(SaberItems.EMITTER_DUELIST));
			menu.getSlot(SaberAssembly.GRIP).set(new ItemStack(SaberItems.GRIP_RIDGED));
			menu.getSlot(SaberAssembly.POMMEL).set(new ItemStack(SaberItems.POMMEL_BALANCED));
			menu.getSlot(SaberAssembly.LENS).set(Crafting.applyQuality(new ItemStack(SaberItems.LENS_HONED), Quality.MASTERWORK));
			menu.broadcastChanges();
		});
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_m4_58_workbench_lens");

		server.runOnServer(s -> {
			if (player(s).containerMenu instanceof LightsaberWorkbenchMenu menu) {
				for (int slot = 0; slot < SaberAssembly.SIZE; slot++) {
					menu.getSlot(slot).set(ItemStack.EMPTY);
				}

				menu.broadcastChanges();
			}
		});
		context.waitTicks(2);
		context.setScreen(() -> null);
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- helpers

	/** Opens the block menu at {@code pos} for the player (as a right click does) and waits for its screen. */
	private static void open(ClientGameTestContext context, TestServerContext server, BlockPos pos, Class<?> screen) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);

			if (provider == null) {
				throw new AssertionError("No menu at " + pos + ": " + level.getBlockState(pos));
			}

			p.openMenu(provider);
		});
		context.waitFor(client -> screen.isInstance(client.gui.screen()));
		context.waitTicks(5);
	}

	private interface BenchAction {
		void run(ServerPlayer p, EngineeringBenchMenu menu);
	}

	/** Runs {@code action} on the server with the player's open bench menu, then syncs it. */
	private static void onBench(TestServerContext server, BenchAction action) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (!(p.containerMenu instanceof EngineeringBenchMenu menu)) {
				throw new AssertionError("The Engineering Bench menu is not open on the server");
			}

			action.run(p, menu);
			menu.broadcastChanges();
		});
	}

	/** Switches the bench to {@code tab} as the screen's tab strip does. */
	private static void tab(ServerPlayer p, AbstractContainerMenu menu, int tab) {
		menu.clickMenuButton(p, tab);
	}

	private static CrystalAttunement crystal(ServerPlayer p, BladeColor color) {
		return new CrystalAttunement(p.getUUID(), p.getPlainTextName(), color.rgb());
	}

	private static int levelXp(CraftingBalance b, int level) {
		return b.levelXp.length == 0 ? 0 : b.levelXp[Math.max(0, Math.min(b.levelXp.length - 1, level - 1))];
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private CraftingClientSteps() {
	}
}

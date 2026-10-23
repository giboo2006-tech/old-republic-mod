package com.oldrepublic.gametest.visuals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Close-up pictures of the lightsaber hilts themselves ({@code oldrepublic_look_hilts_<nn>_<what>}), so a change to how
 * hilts are modelled can be compared picture for picture: the six hilts the game hands out most (the Jedi's Guardian,
 * Wrapped, Weighted in blue; the Sith's Duelist, Ridged, Balanced in red; the Collector's Reliquary, Wrapped, Archivist in
 * amber; the legendary Cantor, Sunforged, Hask in green; the amber training saber; the violet Duelist saberstaff) dark, in a
 * row from both sides and from the front; four of them alone and large from two three-quarter views; five of them lit by
 * day and by night; the inventory with the sabers and every part; the Lightsaber Workbench with parts in its sockets; a
 * dark saber in first person; a stand-in's hand from close by; and a thrown saber in flight.
 *
 * <p>The hilts on show are item displays (no hand, no arm, no display transform: {@code item_display:"none"}), scaled up
 * and centred on the camera's eye line, summoned with the stack written out as the game saves it. The first numbers are
 * kept for every later run (new hilts and parts are added at the end), and this look runs only when a visuals run names
 * it ({@link Visuals#named}).
 */
public final class HiltLook {
	private static final String TAG = "or_look_hilt";
	private static final int SEEN_TIMEOUT = 60;
	/** The row's line, two and a half blocks in front of the stage's wall. */
	private static final double ROW_Z = Visuals.SPOT.z - 1.5;
	/** The camera's eye height above the floor. */
	private static final double EYE = 1.15;
	/** Model units of the single hilts (pommel foot to emitter top) and of the saberstaff, as the generator builds them. */
	private static final float SINGLE_LOW = -2f;
	private static final float SINGLE_HIGH = 6f;
	private static final float STAFF_LOW = 2.286f;
	private static final float STAFF_HIGH = 13.714f;

	/** One hilt on show: its stack (built on the server) and the model-unit span used to centre it. */
	private record Shown(ItemStack stack, float low, float high) {
	}

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		server.runOnServer(s -> Visuals.player(s).teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 6));

		// The row, dark: one side, the other, the front.
		row(context, server, false, 2f, -90f);
		Visuals.camera(context, server, rowCamera(3.2), 180f, 0f);
		shot(context, "01_row_side_a");
		row(context, server, false, 2f, 90f);
		Visuals.camera(context, server, rowCamera(3.2), 180f, 0f);
		shot(context, "02_row_side_b");
		row(context, server, false, 2f, 180f);
		Visuals.camera(context, server, rowCamera(3.2), 180f, 0f);
		shot(context, "03_row_front");

		// Four hilts alone and large, from two three-quarter views each.
		String[] names = {"guardian", "duelist", "reliquary", "legendary"};

		for (int i = 0; i < names.length; i++) {
			int which = i;
			alone(context, server, which, -60f);
			Visuals.camera(context, server, new Vec3(Visuals.SPOT.x, Visuals.SPOT.y + EYE, ROW_Z + 1.6), 180f, 0f);
			shot(context, String.format(Locale.ROOT, "%02d_%s_a", 4 + i * 2, names[i]));
			alone(context, server, which, 120f);
			Visuals.camera(context, server, new Vec3(Visuals.SPOT.x, Visuals.SPOT.y + EYE, ROW_Z + 1.6), 180f, 0f);
			shot(context, String.format(Locale.ROOT, "%02d_%s_b", 5 + i * 2, names[i]));
		}

		// Five lit, by day and by night (the staff's two blades would cross the others).
		row(context, server, true, 1f, -60f);
		Visuals.camera(context, server, rowCamera(3.4), 180f, -4f);
		shot(context, "12_row_lit");
		Visuals.time(server, true);
		Visuals.camera(context, server, rowCamera(3.4), 180f, -4f);
		shot(context, "13_row_lit_night");
		Visuals.time(server, false);
		clear(server);
		Visuals.release(context, server);

		inventory(context, server);
		workbench(context, server);
		firstPerson(context, server);
		hand(context, server);
		thrown(context, server);

		clear(server);
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
		Visuals.release(context, server);
	}

	/** Where the camera stands to see the whole row: {@code back} blocks in front of it, at eye height. */
	private static Vec3 rowCamera(double back) {
		return new Vec3(Visuals.SPOT.x, Visuals.SPOT.y + EYE, ROW_Z + back);
	}

	/** The six hilts, or the five single sabers lit, in a row a block apart, each turned to {@code yaw}. */
	private static void row(ClientGameTestContext context, TestServerContext server, boolean lit, float scale, float yaw) {
		clear(server);
		List<String> commands = server.computeOnServer(s -> {
			List<Shown> shown = shown(Visuals.player(s), lit);
			List<String> out = new ArrayList<>();

			for (int i = 0; i < shown.size(); i++) {
				double x = Visuals.SPOT.x + (i - (shown.size() - 1) / 2.0) * (lit ? 1.1 : 1.0);
				out.add(summon(s, shown.get(i), x, ROW_Z, scale, yaw, lit));
			}

			return out;
		});
		show(context, server, commands);
	}

	/** Hilt {@code which} of the row (dark) alone, three times life size, turned to {@code yaw}. */
	private static void alone(ClientGameTestContext context, TestServerContext server, int which, float yaw) {
		clear(server);
		List<String> commands = server.computeOnServer(s -> List.of(summon(s, shown(Visuals.player(s), false).get(which), Visuals.SPOT.x, ROW_Z, 3f, yaw, false)));
		show(context, server, commands);
	}

	/** Runs the summons and waits for the displays to reach the client. */
	private static void show(ClientGameTestContext context, TestServerContext server, List<String> commands) {
		commands.forEach(server::runCommand);
		int placed = server.computeOnServer(s -> displays(s).size());

		if (placed != commands.size()) {
			OldRepublicMod.LOGGER.warn("Hilt look: {} of {} hilt displays were placed", placed, commands.size());
		}

		context.waitTicks(20);
	}

	/**
	 * The hilts on show, built for {@code p}: dark, all six (Guardian/Wrapped/Weighted blue, Duelist/Ridged/Balanced red,
	 * Reliquary/Wrapped/Archivist amber, Cantor/Sunforged/Hask green, the training saber, the Duelist saberstaff violet); lit,
	 * the five single sabers.
	 */
	private static List<Shown> shown(ServerPlayer p, boolean lit) {
		List<Shown> out = new ArrayList<>();
		out.add(single(saber(p, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED), p, lit));
		out.add(single(saber(p, BladeColor.RED, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED), p, lit));
		out.add(single(saber(p, BladeColor.AMBER, Emitter.RELIQUARY, Grip.WRAPPED, Pommel.ARCHIVIST), p, lit));
		out.add(single(saber(p, BladeColor.GREEN, Emitter.CANTOR, Grip.SUNFORGED, Pommel.HASK), p, lit));
		out.add(single(new ItemStack(SaberItems.TRAINING_SABER), p, lit));

		if (!lit) {
			ItemStack staff = SaberItems.buildStaff(crystal(p, BladeColor.VIOLET), crystal(p, BladeColor.VIOLET), BladeColor.VIOLET, Emitter.DUELIST,
					SaberToggle.balance());
			out.add(new Shown(staff, STAFF_LOW, STAFF_HIGH));
		}

		return out;
	}

	private static Shown single(ItemStack stack, ServerPlayer p, boolean lit) {
		if (lit) {
			SaberToggle.setIgnited(stack, true, p.level().getGameTime() - 100);
		}

		return new Shown(stack, SINGLE_LOW, SINGLE_HIGH);
	}

	/**
	 * The summon command of an item display showing {@code shown} at {@code scale}, turned to {@code yaw}, with the hilt's
	 * middle (or, lit, the middle of hilt and blade) on the camera's eye line above ({@code x}, {@code z}).
	 */
	private static String summon(MinecraftServer s, Shown shown, double x, double z, float scale, float yaw, boolean lit) {
		// The display draws the model's centre (8, 8, 8) at its position: lift it so the middle of what shows is at eye height.
		float top = lit ? 31f : shown.high();
		double middle = ((shown.low() + top) / 2f - 8f) / 16f * scale;
		double y = Visuals.SPOT.y + EYE - middle;
		var ops = s.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		String item = ItemStack.CODEC.encodeStart(ops, shown.stack()).getOrThrow().toString();
		return String.format(Locale.ROOT,
				"summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"%s\"],Rotation:[%.1ff,0f],item_display:\"none\",item:%s,"
						+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
				x, y, z, TAG, yaw, item, scale, scale, scale);
	}

	private static List<Entity> displays(MinecraftServer s) {
		ServerLevel level = Visuals.player(s).level();
		return level.getEntitiesOfClass(Entity.class, new AABB(Visuals.SPOT, Visuals.SPOT).inflate(16), e -> e.entityTags().contains(TAG));
	}

	private static void clear(TestServerContext server) {
		server.runOnServer(s -> displays(s).forEach(Entity::discard));
	}

	/** 14: the survival inventory with the six sabers in the hotbar and every hilt part in the bag. */
	private static void inventory(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z);
			List<Shown> shown = shown(p, false);

			for (int i = 0; i < shown.size(); i++) {
				p.getInventory().setItem(i, shown.get(i).stack());
			}

			p.getInventory().setSelectedSlot(0);
		});
		server.runCommand("execute as @a run oldrepublic saber parts");
		context.waitTicks(10);
		context.getInput().pressKey(o -> o.keyInventory);
		context.waitFor(mc -> mc.gui.screen() != null, SEEN_TIMEOUT);
		context.waitTicks(10);
		shot(context, "14_inventory");
		context.setScreen(() -> null);
		context.waitTicks(5);
		server.runCommand("clear @a");
	}

	/** 15: the Lightsaber Workbench with a green crystal, the Duelist emitter, the Ridged grip and the Balanced pommel in place. */
	private static void workbench(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = BlockPos.containing(Visuals.SPOT.x + 2, Visuals.SPOT.y, Visuals.SPOT.z + 2);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d %s", pos.getX(), pos.getY(), pos.getZ(), OldRepublicMod.id("lightsaber_workbench")));
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ServerLevel level = p.level();
			MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);

			if (provider == null) {
				throw new AssertionError("No Lightsaber Workbench menu at " + pos + ": " + level.getBlockState(pos));
			}

			p.openMenu(provider);
		});
		context.waitFor(mc -> mc.gui.screen() instanceof LightsaberWorkbenchScreen, SEEN_TIMEOUT);
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);

			if (p.containerMenu instanceof LightsaberWorkbenchMenu menu) {
				menu.getSlot(SaberAssembly.CRYSTAL).set(SaberAssembly.crystalStack(crystal(p, BladeColor.GREEN)));
				menu.getSlot(SaberAssembly.EMITTER).set(new ItemStack(SaberItems.EMITTER_DUELIST));
				menu.getSlot(SaberAssembly.GRIP).set(new ItemStack(SaberItems.GRIP_RIDGED));
				menu.getSlot(SaberAssembly.POMMEL).set(new ItemStack(SaberItems.POMMEL_BALANCED));
				menu.broadcastChanges();
			}
		});
		context.waitTicks(15);
		shot(context, "15_workbench");
		server.runOnServer(s -> {
			if (Visuals.player(s).containerMenu instanceof LightsaberWorkbenchMenu menu) {
				for (int slot = 0; slot < SaberAssembly.SIZE; slot++) {
					menu.getSlot(slot).set(ItemStack.EMPTY);
				}

				menu.broadcastChanges();
			}
		});
		context.setScreen(() -> null);
		context.waitTicks(5);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:air", pos.getX(), pos.getY(), pos.getZ()));
	}

	/** 16: a dark Guardian, Wrapped, Weighted saber in first person, the view tipped down to the hand. */
	private static void firstPerson(ClientGameTestContext context, TestServerContext server) {
		hold(context, server, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 25f);
		shot(context, "16_first_person_dark");
	}

	/** 17: a stand-in holding a dark Duelist, Ridged, Balanced saber, seen from beside its hand. */
	private static void hand(ClientGameTestContext context, TestServerContext server) {
		ItemStack saber = server.computeOnServer(s -> saber(Visuals.player(s), BladeColor.RED, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED));
		Visuals.standIn(context, server, Visuals.SPOT, 0f, saber, ItemStack.EMPTY);
		Visuals.camera(context, server, Visuals.SPOT.add(-1.0, 0.95, 0.6), -115f, 15f);
		shot(context, "17_hand_close");
		Visuals.clearStandIns(server);
		Visuals.release(context, server);
	}

	/** 18: a lit saber thrown at a husk, a few ticks into its flight, from first person. */
	private static void thrown(ClientGameTestContext context, TestServerContext server) {
		hold(context, server, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		context.getInput().pressKey(InputConstants.KEY_U);
		server.waitFor(s -> SaberComponents.isIgnited(Visuals.player(s).getMainHandItem()), SEEN_TIMEOUT);
		context.waitTicks(12);
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.1f %.1f %.1f {PersistenceRequired:1b,NoAI:1b,Rotation:[180f,0f],Tags:[\"%s\"]}",
				Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 7, TAG));
		server.runCommand("execute as @a run oldrepublic energy fill");
		context.waitTicks(10);
		server.runCommand("execute as @a run oldrepublic saber throw");
		context.waitTicks(4);
		shot(context, "18_thrown");
		context.waitTicks(40);
		server.runCommand("execute as @a run oldrepublic saber recall");
		context.waitTicks(20);
		clear(server);
	}

	/** A dark saber of these parts in the main hand (slot 0), the off hand emptied. */
	private static void hold(ClientGameTestContext context, TestServerContext server, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.getInventory().setSelectedSlot(0);
			p.setItemInHand(InteractionHand.MAIN_HAND, saber(p, color, emitter, grip, pommel));
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
		context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().getItem() instanceof LightsaberItem
				&& SaberComponents.config(mc.player.getMainHandItem()).emitter() == emitter, SEEN_TIMEOUT);
	}

	private static CrystalAttunement crystal(ServerPlayer p, BladeColor color) {
		return new CrystalAttunement(p.getUUID(), p.getPlainTextName(), color.rgb());
	}

	private static ItemStack saber(ServerPlayer p, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
		return SaberItems.buildSaber(crystal(p, color), color, emitter, grip, pommel, SaberToggle.balance());
	}

	private static void shot(ClientGameTestContext context, String what) {
		context.takeScreenshot("oldrepublic_look_hilts_" + what);
	}

	private HiltLook() {
	}
}

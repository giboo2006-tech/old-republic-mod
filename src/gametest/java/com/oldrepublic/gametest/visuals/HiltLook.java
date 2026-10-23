package com.oldrepublic.gametest.visuals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltMaterials;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStaffs;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.saber.throwing.ThrownSaber;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
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
 * <p>Milestone 7's hilts follow (MILESTONE_7.md 14): the new craftable designs and the four boss sets, each in a row dark
 * and lit, in their materials; one hilt in all eight materials; the Warden's crossguard lit close up; the Penitent's curved
 * blade; a saberstaff with each of the two; the inventory with the new parts in a spread of materials; the Forge tab; two
 * new hilts in first person; and a flying saber seen from beside its path, drawn as its own hilt.
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
	/** Where a lit single blade ends (model y), and a staff's middle, which a lit staff's two blades are centred on. */
	private static final float SINGLE_TIP = 31f;
	private static final float STAFF_MIDDLE = 8f;
	/**
	 * How the Milestone 7 hilts are turned (an item display's yaw; the display turns the model half round, so yaw 180 shows
	 * its +z side with its +x to the right). In a row, three quarters: the thumb side (+x, where the Penitent's blade leans)
	 * and the +z side (where one of the Warden's quillons points) each half toward the camera. Alone, the Warden with its
	 * quillons across the view and its thumb side toward the camera, as first person sees it; the Penitent with its curve
	 * across the view, leaning right.
	 */
	private static final float M7_YAW = 225f;
	private static final float WARDEN_YAW = -60f;
	private static final float PENITENT_YAW = 210f;
	/** The Warden's close-up: the model height on the eye line (its quillons, y 5.26) and the camera's distance. */
	private static final float WARDEN_CLOSE_CENTRE = 5.5f;
	private static final double CLOSE = 1.6;
	/** How far out (blocks from the thrower's spot) the flying saber is when its picture is taken, beside its path. */
	private static final double THROWN_OUT = 4.0;
	/** Every part Milestone 7 adds (6.3): the new craftable designs and the boss sets'. */
	private static final HiltParts.Part[] NEW_PARTS = {Emitter.VANGUARD, Emitter.WARDEN, Emitter.WATCHMAN, Emitter.PENITENT, Emitter.CHOIR,
			Grip.BANDED, Grip.KNURLED, Grip.FLUTED, Grip.RELIQUARY, Grip.WATCHMAN, Grip.PENITENT, Grip.CHOIR,
			Pommel.RINGED, Pommel.DOMED, Pommel.WATCHMAN, Pommel.PENITENT, Pommel.CHOIR};

	/** One hilt on show: its stack (built on the server) and the model-unit span used to centre it. */
	private record Shown(ItemStack stack, float low, float high) {
		/** The model height to put on the eye line: the hilt's middle, or lit, the middle of hilt and blade (a staff's centre). */
		float centre(boolean lit) {
			if (!lit) {
				return (low + high) / 2f;
			}

			return SaberStaffs.isStaff(stack) ? STAFF_MIDDLE : (low + SINGLE_TIP) / 2f;
		}
	}

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		offStage(server);

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

		milestone7(context, server);

		clear(server);
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
		Visuals.release(context, server);
	}

	/** 19 to 32: Milestone 7's hilts. */
	private static void milestone7(ClientGameTestContext context, TestServerContext server) {
		Visuals.release(context, server);
		offStage(server);

		// The new craftable designs, then the four sets: dark and large, then lit.
		rowOf(context, server, p -> craftable(p, false), false, 2f, M7_YAW, 1.0);
		Visuals.camera(context, server, rowCamera(3.2), 180f, 0f);
		shot(context, "19_craftable_dark");
		rowOf(context, server, p -> craftable(p, true), true, 1f, M7_YAW, 1.1);
		Visuals.camera(context, server, rowCamera(3.4), 180f, -4f);
		shot(context, "20_craftable_lit");
		rowOf(context, server, p -> sets(p, false), false, 2f, M7_YAW, 1.1);
		Visuals.camera(context, server, rowCamera(3.2), 180f, 0f);
		shot(context, "21_sets_dark");
		rowOf(context, server, p -> sets(p, true), true, 1f, M7_YAW, 1.1);
		Visuals.camera(context, server, rowCamera(3.4), 180f, -4f);
		shot(context, "22_sets_lit");

		// One hilt in every material, steel to hymnsteel.
		rowOf(context, server, HiltLook::materials, false, 2f, M7_YAW, 0.8);
		Visuals.camera(context, server, rowCamera(3.2), 180f, 0f);
		shot(context, "23_materials");

		// The crossguard and the curve, lit: the Warden close up, the Penitent whole, then a saberstaff of each.
		one(context, server, p -> lit(saber(p, BladeColor.GREEN, Emitter.WARDEN, Grip.KNURLED, Pommel.DOMED, all(HiltMaterial.STARFALL)), p),
				WARDEN_CLOSE_CENTRE, 3f, WARDEN_YAW, CLOSE);
		shot(context, "24_warden_lit_close");
		one(context, server, p -> lit(saber(p, BladeColor.RED, Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT, all(HiltMaterial.CLAN_IRON)), p),
				(SINGLE_LOW + SINGLE_TIP) / 2f, 1.5f, PENITENT_YAW, 2.6);
		shot(context, "25_penitent_lit");
		one(context, server, p -> lit(staff(p, BladeColor.RED, Emitter.PENITENT, all(HiltMaterial.CLAN_IRON)), p), STAFF_MIDDLE, 1f, PENITENT_YAW, 2.8);
		shot(context, "26_staff_penitent_lit");
		one(context, server, p -> lit(staff(p, BladeColor.BLUE, Emitter.WARDEN, all(HiltMaterial.DURASTEEL)), p), STAFF_MIDDLE, 1f, WARDEN_YAW, 2.8);
		shot(context, "27_staff_warden_lit");
		clear(server);
		Visuals.release(context, server);

		inventoryParts(context, server);
		forge(context, server);

		// Two new hilts in first person: the Warden dark, as 16 shows the Guardian, and the Penitent lit.
		hold(context, server, BladeColor.GREEN, Emitter.WARDEN, Grip.KNURLED, Pommel.DOMED, all(HiltMaterial.STARFALL), false);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 25f);
		shot(context, "30_first_person_warden");
		hold(context, server, BladeColor.RED, Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT, all(HiltMaterial.CLAN_IRON), true);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		shot(context, "31_first_person_penitent_lit");

		thrownClose(context, server);
	}

	/** Moves the player six blocks south of the stage, behind the cameras. */
	private static void offStage(TestServerContext server) {
		server.runOnServer(s -> Visuals.player(s).teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 6));
	}

	/** Where the camera stands to see the whole row: {@code back} blocks in front of it, at eye height. */
	private static Vec3 rowCamera(double back) {
		return new Vec3(Visuals.SPOT.x, Visuals.SPOT.y + EYE, ROW_Z + back);
	}

	/** The six hilts, or the five single sabers lit, in a row a block apart, each turned to {@code yaw}. */
	private static void row(ClientGameTestContext context, TestServerContext server, boolean lit, float scale, float yaw) {
		rowOf(context, server, p -> shown(p, lit), lit, scale, yaw, lit ? 1.1 : 1.0);
	}

	/** The hilts {@code what} builds, in a row {@code spacing} blocks apart across the stage, each turned to {@code yaw}. */
	private static void rowOf(ClientGameTestContext context, TestServerContext server, Function<ServerPlayer, List<Shown>> what, boolean lit,
			float scale, float yaw, double spacing) {
		clear(server);
		List<String> commands = server.computeOnServer(s -> {
			List<Shown> shown = what.apply(Visuals.player(s));
			List<String> out = new ArrayList<>();

			for (int i = 0; i < shown.size(); i++) {
				double x = Visuals.SPOT.x + (i - (shown.size() - 1) / 2.0) * spacing;
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

	/**
	 * The saber {@code make} builds alone at {@code scale}, turned to {@code yaw}, model height {@code centre} on the eye
	 * line, and the camera {@code back} blocks in front of it.
	 */
	private static void one(ClientGameTestContext context, TestServerContext server, Function<ServerPlayer, ItemStack> make, float centre, float scale,
			float yaw, double back) {
		clear(server);
		List<String> commands = server.computeOnServer(s -> List.of(summon(s, make.apply(Visuals.player(s)), centre, Visuals.SPOT.x, ROW_Z, scale, yaw)));
		show(context, server, commands);
		Visuals.camera(context, server, rowCamera(back), 180f, 0f);
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

	/**
	 * The new craftable designs (MILESTONE_7.md 6.3), each at least once and in five materials: Vanguard/Banded/Ringed in
	 * durasteel, Warden/Knurled/Domed in starfall iron, Guardian/Fluted/Ringed in aurelite, Duelist/Banded/Domed with a sunstone
	 * emitter and pommel, Vanguard/Knurled/Weighted in duskglass; dark, also a Vanguard saberstaff in clan iron.
	 */
	private static List<Shown> craftable(ServerPlayer p, boolean lit) {
		List<Shown> out = new ArrayList<>();
		out.add(single(saber(p, BladeColor.BLUE, Emitter.VANGUARD, Grip.BANDED, Pommel.RINGED, all(HiltMaterial.DURASTEEL)), p, lit));
		out.add(single(saber(p, BladeColor.GREEN, Emitter.WARDEN, Grip.KNURLED, Pommel.DOMED, all(HiltMaterial.STARFALL)), p, lit));
		out.add(single(saber(p, BladeColor.YELLOW, Emitter.GUARDIAN, Grip.FLUTED, Pommel.RINGED, all(HiltMaterial.AURELITE)), p, lit));
		out.add(single(saber(p, BladeColor.ORANGE, Emitter.DUELIST, Grip.BANDED, Pommel.DOMED,
				new HiltMaterials(HiltMaterial.SUNSTONE, HiltMaterial.STEEL, HiltMaterial.SUNSTONE)), p, lit));
		out.add(single(saber(p, BladeColor.VIOLET, Emitter.VANGUARD, Grip.KNURLED, Pommel.WEIGHTED, all(HiltMaterial.DUSKGLASS)), p, lit));

		if (!lit) {
			out.add(new Shown(staff(p, BladeColor.CYAN, Emitter.VANGUARD, all(HiltMaterial.CLAN_IRON)), STAFF_LOW, STAFF_HIGH));
		}

		return out;
	}

	/**
	 * The four boss sets (7.1) in the materials they drop in: the Collector's (its new grip in starfall iron, the rest steel),
	 * Kaast's Watchman in aurelite, Ulvaine's Penitent in clan iron, the First Cantor's Choir in hymnsteel.
	 */
	private static List<Shown> sets(ServerPlayer p, boolean lit) {
		return List.of(
				single(saber(p, BladeColor.AMBER, Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST,
						new HiltMaterials(HiltMaterial.STEEL, HiltMaterial.STARFALL, HiltMaterial.STEEL)), p, lit),
				single(saber(p, BladeColor.YELLOW, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN, all(HiltMaterial.AURELITE)), p, lit),
				single(saber(p, BladeColor.RED, Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT, all(HiltMaterial.CLAN_IRON)), p, lit),
				single(saber(p, BladeColor.CYAN, Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR, all(HiltMaterial.HYMNSTEEL)), p, lit));
	}

	/** One hilt (Vanguard, Banded, Ringed) in each of the eight materials (3.1), steel first. */
	private static List<Shown> materials(ServerPlayer p) {
		List<Shown> out = new ArrayList<>();

		for (HiltMaterial material : HiltMaterial.values()) {
			out.add(single(saber(p, BladeColor.BLUE, Emitter.VANGUARD, Grip.BANDED, Pommel.RINGED, all(material)), p, false));
		}

		return out;
	}

	private static Shown single(ItemStack stack, ServerPlayer p, boolean lit) {
		if (lit) {
			lit(stack, p);
		}

		return new Shown(stack, SINGLE_LOW, SINGLE_HIGH);
	}

	/** {@code stack} lit long enough ago that its blade is fully out. */
	private static ItemStack lit(ItemStack stack, ServerPlayer p) {
		SaberToggle.setIgnited(stack, true, p.level().getGameTime() - 100);
		return stack;
	}

	/**
	 * The summon command of an item display showing {@code shown} at {@code scale}, turned to {@code yaw}, with the hilt's
	 * middle (or, lit, the middle of hilt and blade) on the camera's eye line above ({@code x}, {@code z}).
	 */
	private static String summon(MinecraftServer s, Shown shown, double x, double z, float scale, float yaw, boolean lit) {
		return summon(s, shown.stack(), shown.centre(lit), x, z, scale, yaw);
	}

	/** The summon command of an item display showing {@code stack} at {@code scale}, turned to {@code yaw}, model height {@code centre} at eye height. */
	private static String summon(MinecraftServer s, ItemStack stack, float centre, double x, double z, float scale, float yaw) {
		// The display draws the model's centre (8, 8, 8) at its position: lift it so the middle of what shows is at eye height.
		double middle = (centre - 8f) / 16f * scale;
		double y = Visuals.SPOT.y + EYE - middle;
		var ops = s.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		String item = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().toString();
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
		openInventory(context);
		shot(context, "14_inventory");
		context.setScreen(() -> null);
		context.waitTicks(5);
		server.runCommand("clear @a");
	}

	/**
	 * 28: the survival inventory with Milestone 7's hilts in the hotbar (the Warden and the Penitent lit, so their inventory
	 * icons show the blade stub) and every new part in the bag, each in another material, then four older parts in new ones.
	 */
	private static void inventoryParts(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z);
			List<ItemStack> hotbar = List.of(
					saber(p, BladeColor.BLUE, Emitter.VANGUARD, Grip.BANDED, Pommel.RINGED, all(HiltMaterial.DURASTEEL)),
					lit(saber(p, BladeColor.GREEN, Emitter.WARDEN, Grip.KNURLED, Pommel.DOMED, all(HiltMaterial.STARFALL)), p),
					saber(p, BladeColor.YELLOW, Emitter.GUARDIAN, Grip.FLUTED, Pommel.RINGED, all(HiltMaterial.AURELITE)),
					saber(p, BladeColor.AMBER, Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST,
							new HiltMaterials(HiltMaterial.STEEL, HiltMaterial.STARFALL, HiltMaterial.STEEL)),
					saber(p, BladeColor.YELLOW, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN, all(HiltMaterial.AURELITE)),
					lit(saber(p, BladeColor.RED, Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT, all(HiltMaterial.CLAN_IRON)), p),
					saber(p, BladeColor.CYAN, Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR, all(HiltMaterial.HYMNSTEEL)),
					lit(staff(p, BladeColor.BLUE, Emitter.WARDEN, all(HiltMaterial.DURASTEEL)), p),
					staff(p, BladeColor.RED, Emitter.PENITENT, all(HiltMaterial.CLAN_IRON)));

			for (int i = 0; i < hotbar.size(); i++) {
				p.getInventory().setItem(i, hotbar.get(i));
			}

			HiltMaterial[] materials = HiltMaterial.values();
			List<ItemStack> bag = new ArrayList<>();

			for (int i = 0; i < NEW_PARTS.length; i++) {
				bag.add(SaberItems.partStack(NEW_PARTS[i], materials[i % materials.length]));
			}

			bag.add(SaberItems.partStack(Emitter.GUARDIAN, HiltMaterial.AURELITE));
			bag.add(SaberItems.partStack(Grip.RIDGED, HiltMaterial.CLAN_IRON));
			bag.add(SaberItems.partStack(Pommel.WEIGHTED, HiltMaterial.HYMNSTEEL));
			bag.add(SaberItems.partStack(Grip.STAFF, HiltMaterial.STARFALL));

			// The bag's slots follow the hotbar's nine.
			for (int i = 0; i < bag.size(); i++) {
				p.getInventory().setItem(9 + i, bag.get(i));
			}

			p.getInventory().setSelectedSlot(0);
		});
		context.waitTicks(10);
		openInventory(context);
		shot(context, "28_inventory_parts");
		context.setScreen(() -> null);
		context.waitTicks(5);
		server.runCommand("clear @a");
	}

	/** Opens the inventory with the inventory key and moves the pointer to the window's corner, so no tooltip covers it. */
	private static void openInventory(ClientGameTestContext context) {
		context.getInput().pressKey(o -> o.keyInventory);
		context.waitFor(mc -> mc.gui.screen() != null, SEEN_TIMEOUT);
		// API-CHECK: TestInput#setCursorPos(double, double) moves the pointer (the repository's tests only press keys).
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(10);
	}

	/** 15: the Lightsaber Workbench with a green crystal, the Duelist emitter, the Ridged grip and the Balanced pommel in place. */
	private static void workbench(ClientGameTestContext context, TestServerContext server) {
		BlockPos pos = openWorkbench(context, server);
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
		closeWorkbench(context, server, pos);
	}

	/**
	 * 29: the Forge tab (5.3) re-forging a steel Warden emitter in its socket in aurelite, the player carrying the shards and
	 * credits it costs.
	 */
	private static void forge(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("clear @a");
		BlockPos pos = openWorkbench(context, server);
		server.runCommand(String.format(Locale.ROOT, "give @a %s 4", HiltMaterial.AURELITE.itemId()));
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.getInventory().add(new ItemStack(M4Items.CREDIT_CHIT, 99));

			if (p.containerMenu instanceof LightsaberWorkbenchMenu menu) {
				menu.getSlot(SaberAssembly.CRYSTAL).set(SaberAssembly.crystalStack(crystal(p, BladeColor.GREEN)));
				menu.getSlot(SaberAssembly.EMITTER).set(SaberItems.partStack(Emitter.WARDEN, HiltMaterial.STEEL));
				menu.getSlot(SaberAssembly.GRIP).set(SaberItems.partStack(Grip.KNURLED, HiltMaterial.STARFALL));
				menu.getSlot(SaberAssembly.POMMEL).set(SaberItems.partStack(Pommel.DOMED, HiltMaterial.STARFALL));
				menu.broadcastChanges();
			}
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof LightsaberWorkbenchScreen screen) {
				screen.showForgeForTest(Emitter.WARDEN, HiltMaterial.AURELITE);
			}
		});
		// API-CHECK: TestInput#setCursorPos(double, double), as in openInventory.
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(15);
		shot(context, "29_forge");
		context.runOnClient(mc -> LightsaberWorkbenchScreen.resetTabForTest());
		closeWorkbench(context, server, pos);
		server.runCommand("clear @a");
	}

	/** Places a Lightsaber Workbench beside the stage's spot and opens it for the player; returns where it is. */
	private static BlockPos openWorkbench(ClientGameTestContext context, TestServerContext server) {
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
		return pos;
	}

	/** Empties the workbench's sockets, closes it and removes it. */
	private static void closeWorkbench(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
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
		hold(context, server, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT, false);
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
		hold(context, server, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT, false);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		context.getInput().pressKey(InputConstants.KEY_U);
		server.waitFor(s -> SaberComponents.isIgnited(Visuals.player(s).getMainHandItem()), SEEN_TIMEOUT);
		context.waitTicks(12);
		husk(server);
		readyThrow(server);
		context.waitTicks(10);
		server.runCommand("execute as @a run oldrepublic saber throw");
		context.waitTicks(4);
		shot(context, "18_thrown");
		context.waitTicks(40);
		server.runCommand("execute as @a run oldrepublic saber recall");
		context.waitTicks(20);
		clear(server);
	}

	/**
	 * 32: a lit Watchman saber in aurelite thrown at a husk, seen from a camera beside its path once it is a few blocks out:
	 * the flying saber is its own hilt and blade, spinning, inside the disc of its sweep (MILESTONE_7.md 10.5).
	 */
	private static void thrownClose(ClientGameTestContext context, TestServerContext server) {
		hold(context, server, BladeColor.BLUE, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN, all(HiltMaterial.AURELITE), true);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		husk(server);
		readyThrow(server);
		// Beside the path, level with the flying saber (it leaves from the thrower's chest), looking across it.
		Visuals.camera(context, server, new Vec3(Visuals.SPOT.x + 2.4, Visuals.SPOT.y + 1.7, Visuals.SPOT.z + THROWN_OUT + 0.5), 90f, 6f);
		server.runCommand("execute as @a run oldrepublic saber throw");

		// Not a waitFor: a throw that does not happen should cost this picture, not the rest of the run.
		for (int tick = 0; tick < SEEN_TIMEOUT && context.computeOnClient(HiltLook::thrownOut) < THROWN_OUT; tick++) {
			context.waitTicks(1);
		}

		if (context.computeOnClient(HiltLook::thrownOut) < THROWN_OUT) {
			OldRepublicMod.LOGGER.warn("Hilt look: the thrown saber never got {} blocks out", THROWN_OUT);
		}

		shot(context, "32_thrown_close");
		context.waitTicks(40);
		server.runCommand("execute as @a run oldrepublic saber recall");
		context.waitTicks(20);
		Visuals.release(context, server);
		clear(server);
	}

	/** A still husk seven blocks south of the stage's spot, facing it: the throws' target. */
	private static void husk(TestServerContext server) {
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.1f %.1f %.1f {PersistenceRequired:1b,NoAI:1b,Rotation:[180f,0f],Tags:[\"%s\"]}",
				Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 7, TAG));
	}

	/** Saber Throw unlocked as the story unlocks it (the fourth chapter and the skill), its cooldown over, the Force energy full. */
	private static void readyThrow(TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic quest set 4");
		server.runCommand("execute as @a run oldrepublic skills grant saber_throw");
		server.runOnServer(s -> Progression.setCooldown(Visuals.player(s), SaberThrow.ID, 0));
		server.runCommand("execute as @a run oldrepublic energy fill");
	}

	/** How far (across the ground) the first flying saber the client knows of is from the stage's spot, or -1 when there is none. */
	private static double thrownOut(Minecraft mc) {
		if (mc.level != null) {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof ThrownSaber saber) {
					return Math.hypot(saber.getX() - Visuals.SPOT.x, saber.getZ() - Visuals.SPOT.z);
				}
			}
		}

		return -1;
	}

	/** A saber of these parts and materials in the main hand (slot 0), lit or dark, the off hand emptied. */
	private static void hold(ClientGameTestContext context, TestServerContext server, BladeColor color, Emitter emitter, Grip grip, Pommel pommel,
			HiltMaterials materials, boolean lit) {
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ItemStack saber = saber(p, color, emitter, grip, pommel, materials);

			if (lit) {
				lit(saber, p);
			}

			p.getInventory().setSelectedSlot(0);
			p.setItemInHand(InteractionHand.MAIN_HAND, saber);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
		context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().getItem() instanceof LightsaberItem
				&& SaberComponents.config(mc.player.getMainHandItem()).emitter() == emitter
				&& SaberComponents.config(mc.player.getMainHandItem()).materials().equals(materials)
				&& SaberComponents.isIgnited(mc.player.getMainHandItem()) == lit, SEEN_TIMEOUT);
	}

	private static CrystalAttunement crystal(ServerPlayer p, BladeColor color) {
		return new CrystalAttunement(p.getUUID(), p.getPlainTextName(), color.rgb());
	}

	/** The same material for every part. */
	private static HiltMaterials all(HiltMaterial material) {
		return new HiltMaterials(material, material, material);
	}

	private static ItemStack saber(ServerPlayer p, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
		return saber(p, color, emitter, grip, pommel, HiltMaterials.DEFAULT);
	}

	private static ItemStack saber(ServerPlayer p, BladeColor color, Emitter emitter, Grip grip, Pommel pommel, HiltMaterials materials) {
		return SaberItems.buildSaber(crystal(p, color), color, emitter, grip, pommel, materials, SaberToggle.balance());
	}

	/** A saberstaff of {@code emitter} with its emitters and grip in {@code materials}, two crystals of {@code color}. */
	private static ItemStack staff(ServerPlayer p, BladeColor color, Emitter emitter, HiltMaterials materials) {
		return SaberItems.buildStaff(crystal(p, color), crystal(p, color), color, emitter, materials, SaberToggle.balance());
	}

	private static void shot(ClientGameTestContext context, String what) {
		context.takeScreenshot("oldrepublic_look_hilts_" + what);
	}

	private HiltLook() {
	}
}

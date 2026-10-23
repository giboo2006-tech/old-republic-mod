package com.oldrepublic.gametest.visuals;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.registry.M7Items;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pictures of the weapons ({@code oldrepublic_look_weapons_<nn>_<what>}, MILESTONE_7.md 9.3 and 14): the pistol, rifle,
 * repeater, vibroblade, vibrosword, electrostaff and the four new weapons in the hand, in first and third person and on
 * a stand-in. Runs only when a visuals run names it ({@link Visuals#named}).
 *
 * <ul>
 * <li>01: the survival inventory with every weapon below and the Aurelite Barrel and Duskglass Cell (the flat icons);</li>
 * <li>02 to 34, three for each weapon in turn (the pistol, rifle, heavy repeater, Kaast's Longshot, scatter blaster,
 * carbine, vibroblade, vibrosword, electrostaff, vibro-axe and force pike): in first person, the view tipped down a
 * little ({@code _first_person}); on a stand-in from its weapon side ({@code _side}); and on the stand-in close up, three
 * quarters from the front right ({@code _close}). A stand-in's blaster is raised to aim (its trigger held, as the player
 * holds it); its melee weapon hangs at rest;</li>
 * <li>35 and 36: a scatter blast a few ticks out, in first person and from above beside the line of fire;</li>
 * <li>37 and 38: a carbine burst, all three bolts in the air, in first person and from the side;</li>
 * <li>39: a Republic medic (pistol) and an Imperial rifleman, held still, close up: troopers show the in-hand models.</li>
 * </ul>
 *
 * <p>The first-person shots are fired with the real use key. The free camera has no use key, so the shots seen from it
 * are pulled on the server as the use key pulls them ({@link Blasters#tryFire}, past the draw delay), and the stand-ins
 * are told to use their blasters; both are logged as shortcuts.
 */
public final class WeaponLook {
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;
	private static final int SEEN_TIMEOUT = 60;
	/** Ticks a blaster is in hand before its first pull: past every draw delay. */
	private static final int DRAWN = 20;
	/** Ticks from the pull to the picture: a scatter's pellets about five blocks out, a carbine's third bolt just gone. */
	private static final int SCATTER_FLIGHT = 3;
	private static final int BURST_FLIGHT = 5;
	/** The free camera's views of a stand-in on {@link Visuals#SPOT} facing south: an aimed blaster from its gun side. */
	private static final Vec3 AIM_SIDE = Visuals.SPOT.add(-1.5, 1.45, 0.75);
	/** A melee weapon at rest from the same side, further back and higher, so a long haft fits. */
	private static final Vec3 REST_SIDE = Visuals.SPOT.add(-2.1, 1.1, 0.45);
	/** Three quarters from the front right, close: an aimed blaster, and a weapon at rest. */
	private static final Vec3 AIM_CLOSE = Visuals.SPOT.add(-1.1, 1.65, 2.1);
	private static final Vec3 AIM_CLOSE_AT = Visuals.SPOT.add(-0.15, 1.45, 1.15);
	private static final Vec3 REST_CLOSE = Visuals.SPOT.add(-1.2, 1.15, 1.3);
	private static final Vec3 REST_CLOSE_AT = Visuals.SPOT.add(-0.3, 0.7, 0.45);

	/** One weapon on show: its name in the pictures and its item, as registered. */
	private record Weapon(String name, Item item) {
		boolean blaster() {
			return item instanceof BlasterItem;
		}
	}

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		List<Weapon> weapons = List.of(
				new Weapon("pistol", EnemyItems.BLASTER_PISTOL),
				new Weapon("rifle", EnemyItems.BLASTER_RIFLE),
				new Weapon("repeater", GearItems.HEAVY_REPEATER),
				new Weapon("longshot", GearItems.KAAST_LONGSHOT),
				new Weapon("scatter_blaster", M7Items.SCATTER_BLASTER),
				new Weapon("carbine", M7Items.BLASTER_CARBINE),
				new Weapon("vibroblade", EnemyItems.VIBROBLADE),
				new Weapon("vibrosword", GearItems.VIBROSWORD),
				new Weapon("electrostaff", GearItems.ELECTROSTAFF),
				new Weapon("vibro_axe", M7Items.VIBRO_AXE),
				new Weapon("force_pike", M7Items.FORCE_PIKE));

		inventory(context, server, weapons);
		int number = 2;

		for (Weapon weapon : weapons) {
			inHand(context, server, weapon, number);
			number += 3;
		}

		scatterBlast(context, server, number);
		carbineBurst(context, server, number + 2);
		troopers(context, server, number + 4);

		Visuals.clearStandIns(server);
		server.runCommand("kill @e[type=oldrepublic:blaster_bolt]");
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
		Visuals.release(context, server);
	}

	/** 01: the survival inventory with the weapons in the hotbar and the bag, the two blaster mods after them. */
	private static void inventory(ClientGameTestContext context, TestServerContext server, List<Weapon> weapons) {
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z);

			for (int i = 0; i < weapons.size(); i++) {
				p.getInventory().setItem(i, new ItemStack(weapons.get(i).item()));
			}

			p.getInventory().setItem(weapons.size(), new ItemStack(M7Items.MOD_AURELITE_BARREL));
			p.getInventory().setItem(weapons.size() + 1, new ItemStack(M7Items.MOD_DUSKGLASS_CELL));
			p.getInventory().setSelectedSlot(0);
		});
		context.waitTicks(10);
		context.getInput().pressKey(o -> o.keyInventory);
		context.waitFor(mc -> mc.gui.screen() != null, SEEN_TIMEOUT);
		context.waitTicks(10);
		shot(context, 1, "inventory");
		context.setScreen(() -> null);
		context.waitTicks(5);
		server.runCommand("clear @a");
		server.runCommand("gamemode creative @a");
	}

	/**
	 * {@code number} to {@code number + 2}: {@code weapon} in first person, then on a stand-in from its weapon side and close
	 * up three quarters from the front right; a blaster raised to aim, a melee weapon at rest.
	 */
	private static void inHand(ClientGameTestContext context, TestServerContext server, Weapon weapon, int number) {
		hold(context, server, weapon.item());
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 10f);
		shot(context, number, weapon.name() + "_first_person");

		Visuals.standIn(context, server, Visuals.SPOT, 0f, new ItemStack(weapon.item()), ItemStack.EMPTY);

		if (weapon.blaster()) {
			log("the stand-in told to aim its " + weapon.name() + " (use it)");
			server.runOnServer(s -> Visuals.standIn(s).startUsingItem(InteractionHand.MAIN_HAND));
			context.waitTicks(4);
			Visuals.camera(context, server, AIM_SIDE, -90f, 0f);
			shot(context, number + 1, weapon.name() + "_side");
			cameraAt(context, server, AIM_CLOSE, AIM_CLOSE_AT);
			shot(context, number + 2, weapon.name() + "_close");
			server.runOnServer(s -> Visuals.standIn(s).stopUsingItem());
		} else {
			Visuals.camera(context, server, REST_SIDE, -90f, 8f);
			shot(context, number + 1, weapon.name() + "_side");
			cameraAt(context, server, REST_CLOSE, REST_CLOSE_AT);
			shot(context, number + 2, weapon.name() + "_close");
		}

		Visuals.clearStandIns(server);
		Visuals.release(context, server);
	}

	/** {@code number}: a scatter blast in first person (the use key); {@code number + 1}: one from above, beside the line of fire. */
	private static void scatterBlast(ClientGameTestContext context, TestServerContext server, int number) {
		hold(context, server, M7Items.SCATTER_BLASTER);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		context.waitTicks(DRAWN);
		context.getInput().holdKeyFor(USE, 1);
		context.waitTicks(SCATTER_FLIGHT - 1);
		shot(context, number, "scatter_blast_first_person");
		fired(server, "scatter blast in first person");
		settle(context, server);

		cameraAt(context, server, Visuals.SPOT.add(3.5, 3.5, 1.5), Visuals.SPOT.add(0.0, 1.5, 5.5));
		pull(server);
		context.waitTicks(SCATTER_FLIGHT);
		shot(context, number + 1, "scatter_blast_above");
		settle(context, server);
		Visuals.release(context, server);
	}

	/** {@code number}: a carbine burst in first person (the use key); {@code number + 1}: one from the side, all three bolts out. */
	private static void carbineBurst(ClientGameTestContext context, TestServerContext server, int number) {
		hold(context, server, M7Items.BLASTER_CARBINE);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		context.waitTicks(DRAWN);
		context.getInput().holdKeyFor(USE, 1);
		context.waitTicks(BURST_FLIGHT - 1);
		shot(context, number, "carbine_burst_first_person");
		fired(server, "carbine burst in first person");
		settle(context, server);

		// The bolts leave 2 ticks apart at 2.5 blocks a tick: from 2.5 to 12.5 blocks out when the picture is taken.
		cameraAt(context, server, Visuals.SPOT.add(7.0, 1.6, 7.0), Visuals.SPOT.add(0.0, 1.45, 7.0));
		pull(server);
		context.waitTicks(BURST_FLIGHT);
		shot(context, number + 1, "carbine_burst_side");
		settle(context, server);
		Visuals.release(context, server);
	}

	/** {@code number}: a Republic medic (pistol) and an Imperial rifleman (rifle), held still, close up from the front right. */
	private static void troopers(ClientGameTestContext context, TestServerContext server, int number) {
		server.runOnServer(s -> Visuals.player(s).teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 6));
		trooper(server, "republic", "oldrepublic:republic_trooper", "medic", -1);
		trooper(server, "imperial", "oldrepublic:sith_trooper", "rifleman", 1);
		context.waitTicks(20);
		cameraAt(context, server, Visuals.SPOT.add(-2.0, 1.45, 3.0), Visuals.SPOT.add(0.5, 0.95, 0.5));
		shot(context, number, "troopers");
		server.runOnServer(s -> troopersNear(s).forEach(Entity::discard));
		Visuals.release(context, server);
	}

	/** Spawns a trooper of {@code army} and {@code role} at {@code dx} blocks east of the spot, frozen facing south. */
	private static void trooper(TestServerContext server, String army, String type, String role, int dx) {
		int x = (int) Math.floor(Visuals.SPOT.x) + dx;
		int y = (int) Math.floor(Visuals.SPOT.y);
		int z = (int) Math.floor(Visuals.SPOT.z);
		server.runCommand(String.format(Locale.ROOT, "oldrepublic trooper spawn %s %s %d %d %d", army, role, x, y, z));
		server.runCommand(String.format(Locale.ROOT,
				"execute as @e[type=%s,x=%d,y=%d,z=%d,distance=..2,limit=1,sort=nearest] run data merge entity @s {NoAI:1b,Rotation:[0f,0f]}",
				type, x, y, z));
	}

	private static List<TrooperEntity> troopersNear(MinecraftServer s) {
		return Visuals.player(s).level().getEntitiesOfClass(TrooperEntity.class, new AABB(Visuals.SPOT, Visuals.SPOT).inflate(16), e -> true);
	}

	/** {@code item} in the main hand (slot 0), the off hand emptied; waits until the client has it. */
	private static void hold(ClientGameTestContext context, TestServerContext server, Item item) {
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.getInventory().setSelectedSlot(0);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
		context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().is(item), SEEN_TIMEOUT);
	}

	/** Pulls the trigger of the player's blaster on the server as the use key would, past the draw delay. */
	private static void pull(TestServerContext server) {
		log("the trigger pulled on the server (the free camera has no use key)");
		boolean fired = server.computeOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ItemStack gun = p.getMainHandItem();

			if (!(gun.getItem() instanceof BlasterItem blaster)) {
				return false;
			}

			Blasters.drawnLongAgo(p, gun);
			return Blasters.tryFire(p, gun, blaster, p.level().getGameTime());
		});

		if (!fired) {
			OldRepublicMod.LOGGER.warn("Weapon look: the pulled trigger did not fire; the next picture shows no bolts");
		}
	}

	/** Warns when the blaster in hand is still cold after {@code what}: its picture shows no shot. */
	private static void fired(TestServerContext server, String what) {
		float heat = server.computeOnServer(s -> Blasters.heat(Visuals.player(s).getMainHandItem()).heat());

		if (heat <= 0f) {
			OldRepublicMod.LOGGER.warn("Weapon look: the {} fired nothing; its picture shows no shot", what);
		}
	}

	/** Lets the bolts fly out, removes any still about and cools the gun for the next picture. */
	private static void settle(ClientGameTestContext context, TestServerContext server) {
		context.waitTicks(10);
		server.runCommand("kill @e[type=oldrepublic:blaster_bolt]");
		server.runCommand("oldrepublic gear heat @p 0");
		context.waitTicks(10);
	}

	/** Looks through a free camera at {@code eye}, turned toward {@code at}. */
	private static void cameraAt(ClientGameTestContext context, TestServerContext server, Vec3 eye, Vec3 at) {
		Vec3 d = at.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) Math.toDegrees(Math.atan2(-d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		Visuals.camera(context, server, eye, yaw, pitch);
	}

	private static void log(String shortcut) {
		OldRepublicMod.LOGGER.info("Weapon look: shortcut, {}", shortcut);
	}

	private static void shot(ClientGameTestContext context, int number, String what) {
		context.takeScreenshot(String.format(Locale.ROOT, "oldrepublic_look_weapons_%02d_%s", number, what));
	}

	private WeaponLook() {
	}
}

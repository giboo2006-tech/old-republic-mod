package com.oldrepublic.gametest.visuals;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Close-up pictures of the lightsaber in hand ({@code oldrepublic_look_saber_<nn>_<what>}): a lit blue saber (Guardian,
 * Wrapped, Weighted) in first person idle, mid-swing and on guard; in third person from behind and in front; from a free
 * camera beside and in front of the player, by day and by night; half way through ignition; a row of armour stands holding
 * every blade colour; the saberstaff; and two blades, one in each hand. The saber is lit with the real Toggle Lightsaber key
 * (U); the swings are real attack presses; the guard is the use key held. The armour stands' and the off hand's sabers are
 * made lit (as the toggle makes them), logged as shortcuts.
 */
public final class SaberLook {
	private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;
	/** Ticks for the blade to grow out after the toggle and settle. */
	private static final int LIT_TICKS = 12;
	private static final int SEEN_TIMEOUT = 60;
	private static final String STAND_TAG = "or_look_saber";
	/** The armour stands' row: two blocks in front of the wall. */
	private static final double STAND_Z = Visuals.SPOT.z - 2.0;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		give(context, server, BladeColor.BLUE);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		toggle(context, server, true);

		// First person, by day: idle, mid-swing, on guard.
		shot(context, "01_first_person");
		swing(context);
		shot(context, "02_first_person_swing");
		context.waitTicks(10);
		context.getInput().holdKey(USE);
		context.waitTicks(8);
		shot(context, "03_first_person_guard");
		context.getInput().releaseKey(USE);
		context.waitTicks(6);

		// Third person, by day.
		Visuals.stand(context, server, CameraType.THIRD_PERSON_BACK, 10f);
		shot(context, "04_third_person_back");
		Visuals.stand(context, server, CameraType.THIRD_PERSON_FRONT, 10f);
		shot(context, "05_third_person_front");

		// Free camera, by day: beside the player (on their right, looking east) and in front (looking north, the wall behind).
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		Visuals.camera(context, server, Visuals.SPOT.add(-1.9, 1.35, 0.35), -90f, 6f);
		shot(context, "06_side");
		Visuals.camera(context, server, Visuals.SPOT.add(0.0, 1.35, 2.3), 180f, 8f);
		shot(context, "07_front");

		// By night.
		Visuals.time(server, true);
		context.waitTicks(4);
		shot(context, "08_front_night");
		Visuals.camera(context, server, Visuals.SPOT.add(-1.9, 1.35, 0.35), -90f, 6f);
		shot(context, "09_side_night");

		// Half way through ignition, from beside.
		toggle(context, server, false);
		context.getInput().pressKey(InputConstants.KEY_U);
		context.waitFor(mc -> mc.player != null && SaberComponents.isIgnited(mc.player.getMainHandItem()), SEEN_TIMEOUT);
		context.waitTicks(2);
		shot(context, "10_igniting_night");
		context.waitTicks(LIT_TICKS);

		// Player camera by night: first person idle and third person from behind mid-swing.
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		shot(context, "11_first_person_night");
		swing(context);
		shot(context, "12_first_person_swing_night");
		context.waitTicks(10);
		Visuals.stand(context, server, CameraType.THIRD_PERSON_BACK, 10f);
		swing(context);
		shot(context, "13_third_person_swing_night");
		context.waitTicks(10);

		// Every blade colour, on a row of armour stands in front of the wall.
		colours(context, server);
		Visuals.camera(context, server, Visuals.SPOT.add(0.0, 1.2, 3.2), 180f, 6f);
		shot(context, "14_colours_night");
		server.runCommand("kill @e[type=minecraft:armor_stand,tag=" + STAND_TAG + "]");

		// The saberstaff, then a blade in each hand, from in front.
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			log("a lit saberstaff put in the main hand, as the toggle lights it");
			ItemStack staff = SaberItems.buildStaff(crystal(p, BladeColor.VIOLET), crystal(p, BladeColor.VIOLET), BladeColor.VIOLET, Emitter.DUELIST,
					SaberToggle.balance());
			SaberToggle.setIgnited(staff, true, p.level().getGameTime() - 100);
			p.setItemInHand(InteractionHand.MAIN_HAND, staff);
		});
		context.waitTicks(10);
		Visuals.camera(context, server, Visuals.SPOT.add(0.0, 1.35, 2.6), 180f, 8f);
		shot(context, "15_staff_night");

		give(context, server, BladeColor.BLUE);
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			log("both sabers made lit and a green one put in the off hand, as the toggle lights them");
			SaberToggle.setIgnited(p.getMainHandItem(), true, p.level().getGameTime() - 100);
			ItemStack off = saber(p, BladeColor.GREEN, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED);
			SaberToggle.setIgnited(off, true, p.level().getGameTime() - 100);
			p.setItemInHand(InteractionHand.OFF_HAND, off);
		});
		context.waitTicks(10);
		shot(context, "16_two_blades_night");

		Visuals.release(context, server);
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
		Visuals.time(server, false);
		context.waitTicks(5);
	}

	/** A dark saber of {@code color} in the main hand, the off hand emptied. */
	private static void give(ClientGameTestContext context, TestServerContext server, BladeColor color) {
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.getInventory().setSelectedSlot(0);
			p.setItemInHand(InteractionHand.MAIN_HAND, saber(p, color, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED));
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
		context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().getItem() instanceof LightsaberItem
				&& SaberComponents.config(mc.player.getMainHandItem()).color() == color, SEEN_TIMEOUT);
	}

	private static CrystalAttunement crystal(ServerPlayer p, BladeColor color) {
		return new CrystalAttunement(p.getUUID(), p.getPlainTextName(), color.rgb());
	}

	private static ItemStack saber(ServerPlayer p, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
		return SaberItems.buildSaber(crystal(p, color), color, emitter, grip, pommel, SaberToggle.balance());
	}

	/** Presses the toggle key and waits until the main hand's saber is lit (or dark) and grown out. */
	private static void toggle(ClientGameTestContext context, TestServerContext server, boolean lit) {
		context.getInput().pressKey(InputConstants.KEY_U);
		server.waitFor(s -> SaberComponents.isIgnited(Visuals.player(s).getMainHandItem()) == lit, SEEN_TIMEOUT);
		context.waitFor(mc -> mc.player != null && SaberComponents.isIgnited(mc.player.getMainHandItem()) == lit, SEEN_TIMEOUT);
		context.waitTicks(LIT_TICKS);
	}

	/** An attack press, then two ticks, so the picture catches the blade mid-swing. */
	private static void swing(ClientGameTestContext context) {
		context.waitFor(mc -> mc.player != null && mc.player.getAttackStrengthScale(0.5f) >= 1f, SEEN_TIMEOUT);
		context.getInput().pressKey(ATTACK);
		context.waitTicks(2);
	}

	/** Seven armour stands with arms, a lit saber of each colour in hand, in a row two blocks in front of the wall. */
	private static void colours(ClientGameTestContext context, TestServerContext server) {
		BladeColor[] colours = {BladeColor.RED, BladeColor.ORANGE, BladeColor.YELLOW, BladeColor.GREEN, BladeColor.CYAN, BladeColor.BLUE,
				BladeColor.VIOLET};

		for (int i = 0; i < colours.length; i++) {
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:armor_stand %.2f %.2f %.2f {ShowArms:1b,NoGravity:1b,Tags:[\"%s\"],Rotation:[0f,0f]}",
					standX(i), Visuals.SPOT.y, STAND_Z, STAND_TAG));
		}

		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ServerLevel level = p.level();
			log("seven armour stands given lit sabers of every colour, as the toggle lights them");
			p.teleportTo(Visuals.SPOT.x, Visuals.SPOT.y, Visuals.SPOT.z + 6);

			for (int i = 0; i < colours.length; i++) {
				Vec3 at = new Vec3(standX(i), Visuals.SPOT.y, STAND_Z);
				List<ArmorStand> found = level.getEntitiesOfClass(ArmorStand.class, new AABB(at, at).inflate(0.3), stand -> !stand.isMarker());

				if (found.isEmpty()) {
					throw new AssertionError("No armour stand for the " + colours[i] + " blade at " + at);
				}

				ItemStack lit = saber(p, colours[i], Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED);
				SaberToggle.setIgnited(lit, true, level.getGameTime() - 100);
				found.getFirst().setItemSlot(EquipmentSlot.MAINHAND, lit);
			}
		});
		context.waitTicks(10);
	}

	private static double standX(int i) {
		return Visuals.SPOT.x - 4.5 + i * 1.5;
	}

	private static void shot(ClientGameTestContext context, String what) {
		context.takeScreenshot("oldrepublic_look_saber_" + what);
	}

	private static void log(String shortcut) {
		OldRepublicMod.LOGGER.info("Saber look: shortcut, {}", shortcut);
	}

	private SaberLook() {
	}
}

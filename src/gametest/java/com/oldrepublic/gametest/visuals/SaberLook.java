package com.oldrepublic.gametest.visuals;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
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
 * Wrapped, Weighted) in first person at rest, through a forehand cut a tick at a time, on the backhand that follows and on
 * guard; in third person from behind, in front and in front mid-swing; a stand-in (a mannequin, since 26.2 draws the
 * player only from their own camera) from the side, in front and three-quarters, and on guard; by night in first person,
 * mid-swing, half way through ignition and in third person mid-swing; a row of armour stands holding every blade colour;
 * the saberstaff in first person at rest and mid-twirl and on the stand-in; two blades, one in each hand, in first person
 * and on the stand-in; a blade clash and a bolt deflection by night. The player's saber is lit with the real Toggle Lightsaber key (U); the player's swings are real
 * attack presses and the guard is the use key held. The stand-in's, the armour stands', the staff's and the off hand's
 * sabers are made lit (as the toggle makes them), the stand-in is told to guard and the clash and deflection are sent as
 * the guard sends them, logged as shortcuts. (A mannequin
 * never plays a swing, so the third-person swings are the player's own, from behind and in front.)
 */
public final class SaberLook {
	private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;
	/** Ticks for the blade to grow out after the toggle and settle. */
	private static final int LIT_TICKS = 12;
	private static final int SEEN_TIMEOUT = 60;
	private static final String STAND_TAG = "or_look_saber";
	/** The armour stands' row: two blades in front of the wall. */
	private static final double STAND_Z = Visuals.SPOT.z - 2.0;
	/** The free camera's views of a stand-in on {@link Visuals#SPOT} facing south: from its saber side, in front, three-quarters. */
	private static final Vec3 SIDE = Visuals.SPOT.add(-1.9, 1.35, 0.35);
	private static final Vec3 FRONT = Visuals.SPOT.add(0.0, 1.35, 2.3);
	private static final Vec3 THREE_QUARTER = Visuals.SPOT.add(-1.6, 1.4, 1.7);
	private static final float THREE_QUARTER_YAW = -136.7f;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		give(context, server, BladeColor.BLUE);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		toggle(context, server, true);

		// First person, by day: at rest, a forehand a tick at a time, the backhand after it, on guard.
		shot(context, "01_first_person");
		ready(context);
		context.getInput().pressKey(ATTACK);
		context.waitTicks(1);
		shot(context, "02_first_person_swing_a");
		context.waitTicks(1);
		shot(context, "03_first_person_swing_b");
		context.waitTicks(1);
		shot(context, "04_first_person_swing_c");
		swing(context);
		shot(context, "05_first_person_backhand");
		context.waitTicks(10);
		context.getInput().holdKey(USE);
		context.waitTicks(8);
		shot(context, "06_first_person_guard");
		context.getInput().releaseKey(USE);
		context.waitTicks(6);

		// Third person, by day: from behind, in front, and in front mid-swing.
		Visuals.stand(context, server, CameraType.THIRD_PERSON_BACK, 10f);
		shot(context, "07_third_person_back");
		Visuals.stand(context, server, CameraType.THIRD_PERSON_FRONT, 10f);
		shot(context, "08_third_person_front");
		swing(context);
		shot(context, "09_third_person_front_swing");
		context.waitTicks(10);

		// The stand-in, by day: beside it (on its saber side, looking east), in front (looking north, the wall behind) and
		// three-quarters from the front right; then on guard.
		standIn(context, server, saberOf(server, BladeColor.BLUE), ItemStack.EMPTY);
		Visuals.camera(context, server, SIDE, -90f, 6f);
		shot(context, "10_stand_in_side");
		Visuals.camera(context, server, FRONT, 180f, 8f);
		shot(context, "11_stand_in_front");
		Visuals.camera(context, server, THREE_QUARTER, THREE_QUARTER_YAW, 8f);
		shot(context, "12_stand_in_three_quarter");
		log("the stand-in told to raise its guard (use its saber)");
		server.runOnServer(s -> Visuals.standIn(s).startUsingItem(InteractionHand.MAIN_HAND));
		context.waitTicks(4);
		shot(context, "13_stand_in_guard");
		server.runOnServer(s -> Visuals.standIn(s).stopUsingItem());
		Visuals.clearStandIns(server);

		// By night: first person at rest, mid-swing and half way through ignition, then third person from behind mid-swing.
		Visuals.time(server, true);
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		shot(context, "14_first_person_night");
		swing(context);
		shot(context, "15_first_person_swing_night");
		context.waitTicks(10);
		toggle(context, server, false);
		context.getInput().pressKey(InputConstants.KEY_U);
		// The blade shoots out and settles within a few ticks: the shot is taken one tick after this client sees it lit, once
		// the hand has the lit stack.
		context.waitFor(mc -> mc.player != null && SaberComponents.isIgnited(mc.player.getMainHandItem()), SEEN_TIMEOUT);
		context.waitTicks(1);
		shot(context, "16_igniting_night");
		context.waitTicks(LIT_TICKS);
		Visuals.stand(context, server, CameraType.THIRD_PERSON_BACK, 10f);
		swing(context);
		shot(context, "17_third_person_swing_night");
		context.waitTicks(10);

		// The stand-in three-quarters by night, then every blade colour on a row of armour stands in front of the wall.
		standIn(context, server, saberOf(server, BladeColor.BLUE), ItemStack.EMPTY);
		Visuals.camera(context, server, THREE_QUARTER, THREE_QUARTER_YAW, 8f);
		shot(context, "18_stand_in_night");
		Visuals.clearStandIns(server);
		colours(context, server);
		Visuals.camera(context, server, Visuals.SPOT.add(0.0, 1.2, 3.2), 180f, 6f);
		shot(context, "19_colours_night");
		server.runCommand("kill @e[type=minecraft:armor_stand,tag=" + STAND_TAG + "]");

		// The saberstaff, then a blade in each hand: in first person (the staff at rest and mid-twirl) and on the stand-in,
		// from in front.
		server.runOnServer(s -> {
			log("a lit saberstaff put in the main hand, as the toggle lights it");
			Visuals.player(s).setItemInHand(InteractionHand.MAIN_HAND, staff(Visuals.player(s)));
		});
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		shot(context, "20_staff_first_person_night");
		swing(context);
		shot(context, "21_staff_twirl_night");
		context.waitTicks(10);
		standIn(context, server, server.computeOnServer(s -> staff(Visuals.player(s))), ItemStack.EMPTY);
		Visuals.camera(context, server, FRONT.add(0.0, 0.0, 0.4), 180f, 8f);
		shot(context, "22_staff_stand_in_night");
		Visuals.clearStandIns(server);

		give(context, server, BladeColor.BLUE);
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			log("both sabers made lit and a green one put in the off hand, as the toggle lights them");
			SaberToggle.setIgnited(p.getMainHandItem(), true, p.level().getGameTime() - 100);
			p.setItemInHand(InteractionHand.OFF_HAND, lit(p, BladeColor.GREEN, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED));
		});
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		shot(context, "23_two_blades_first_person_night");
		standIn(context, server, saberOf(server, BladeColor.BLUE),
				server.computeOnServer(s -> lit(Visuals.player(s), BladeColor.GREEN, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED)));
		Visuals.camera(context, server, THREE_QUARTER, THREE_QUARTER_YAW, 8f);
		shot(context, "24_two_blades_stand_in_night");
		Visuals.clearStandIns(server);

		// A blade clash and a bolt deflection at chest height by night, from in front, as their sparks fly.
		Visuals.camera(context, server, FRONT, 180f, 8f);
		contact(context, server, SaberEffects.CLASH, "25_clash_night");
		contact(context, server, SaberEffects.DEFLECT, "26_deflect_night");

		Visuals.release(context, server);
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
		Visuals.time(server, false);
		context.waitTicks(5);
	}

	/**
	 * Sends {@code effect} to the player as the guard sends it, at chest height on {@link Visuals#SPOT} with its sparks thrown
	 * across the view, and takes {@code what} two ticks later.
	 */
	private static void contact(ClientGameTestContext context, TestServerContext server, Identifier effect, String what) {
		server.runOnServer(s -> {
			log("a " + effect.getPath() + " effect sent at the spot, as the guard sends one");
			EffectEvents.toPlayer(Visuals.player(s), effect, Visuals.SPOT.add(0.0, 1.4, 0.0), new Vec3(1.0, 0.0, 0.0), -1, 1f,
					BladeColor.BLUE.ordinal());
		});
		context.waitTicks(2);
		shot(context, what);
		context.waitTicks(12);
	}

	/** A stand-in on {@link Visuals#SPOT} facing south, holding {@code main} and {@code off}. */
	private static void standIn(ClientGameTestContext context, TestServerContext server, ItemStack main, ItemStack off) {
		log("a stand-in mannequin given lit sabers, as the toggle lights them");
		Visuals.standIn(context, server, Visuals.SPOT, 0f, main, off);
	}

	/** A lit saber of {@code color} (Guardian, Wrapped, Weighted), attuned to the player. */
	private static ItemStack saberOf(TestServerContext server, BladeColor color) {
		return server.computeOnServer(s -> lit(Visuals.player(s), color, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED));
	}

	private static ItemStack lit(ServerPlayer p, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
		ItemStack lit = saber(p, color, emitter, grip, pommel);
		SaberToggle.setIgnited(lit, true, p.level().getGameTime() - 100);
		return lit;
	}

	/** A lit violet saberstaff (Duelist emitters). */
	private static ItemStack staff(ServerPlayer p) {
		ItemStack staff = SaberItems.buildStaff(crystal(p, BladeColor.VIOLET), crystal(p, BladeColor.VIOLET), BladeColor.VIOLET, Emitter.DUELIST,
				SaberToggle.balance());
		SaberToggle.setIgnited(staff, true, p.level().getGameTime() - 100);
		return staff;
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
		ready(context);
		context.getInput().pressKey(ATTACK);
		context.waitTicks(2);
	}

	/** Waits until the player's attack has recovered, so the next press swings at once. */
	private static void ready(ClientGameTestContext context) {
		context.waitFor(mc -> mc.player != null && mc.player.getAttackStrengthScale(0.5f) >= 1f, SEEN_TIMEOUT);
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

				found.getFirst().setItemSlot(EquipmentSlot.MAINHAND, lit(p, colours[i], Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED));
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

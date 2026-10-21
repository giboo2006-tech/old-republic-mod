package com.oldrepublic.gametest.visuals;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Close-up pictures of the kinetic Force powers ({@code oldrepublic_look_force_kinetic_<nn>_<what>}): Push charged, released,
 * landing on three husks and settling, from behind, from the side, in first person and by night, and a Focused Blast into a
 * wall; Pull reaching and dragging a husk in; Lift holding a husk up and slamming it down; Jump gathering, leaving the ground
 * and landing with Crushing Descent; a Speed run from behind and in front; Stasis holding a husk and breaking; Choke holding
 * a husk and dropping it; Barrier stopping arrows and its pulse. A patch of coarse dirt in front of the player shows the
 * dust in the ground's own colour. The powers are driven with {@code /oldrepublic force use}; the husks stand still under
 * slowness and are removed after each power. The player is made awakened and taught the powers (logged shortcuts); the
 * floor, the time and the difficulty are put back at the end.
 */
public final class ForceKineticLook {
	private static final Function<Options, KeyMapping> FORWARD = o -> o.keyUp;
	private static final Function<Options, KeyMapping> SPRINT = o -> o.keySprint;
	/** Tag of every husk, arrow and wall the steps make, so cleanup never touches anyone else's. */
	private static final String TAG = "or_look_kinetic";
	private static final String HUSK = "{PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"" + TAG
			+ "\"],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}";
	/** Where the steps' creatures and arrows may be: the stage and the air above it. */
	private static final AABB AREA = new AABB(-24, -70, -24, 24, -30, 30);
	/** Ticks for a summoned husk to reach the client and settle. */
	private static final int SETTLE = 10;
	/** Ticks of a held Push or Jump before its release: a full charge. */
	private static final int CHARGE = 18;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		boolean peaceful = server.computeOnServer(s -> Visuals.player(s).level().getDifficulty() == Difficulty.PEACEFUL);

		try {
			setUp(context, server, peaceful);
			push(context, server);
			pull(context, server);
			lift(context, server);
			jump(context, server);
			speed(context, server);
			stasis(context, server);
			choke(context, server);
			barrier(context, server);
		} finally {
			removeAll(server);
			context.getInput().releaseKey(SPRINT);
			context.getInput().releaseKey(FORWARD);
			server.runCommand("execute as @p run oldrepublic push branch none");
			server.runCommand("fill -6 -61 -3 6 -61 10 minecraft:smooth_stone");
			server.runCommand("fill -6 -60 9 6 -56 9 minecraft:air");
			server.runCommand("effect clear @a");

			if (peaceful) {
				server.runCommand("difficulty peaceful");
			}

			Visuals.time(server, false);
			Visuals.stand(context, server, CameraType.FIRST_PERSON, 0f);
		}
	}

	/** Awakens the player, teaches the eight powers and Crushing Descent, and lays the dirt patch the dust comes from. */
	private static void setUp(ClientGameTestContext context, TestServerContext server, boolean peaceful) {
		if (peaceful) {
			log("difficulty Normal for the steps (Peaceful removes the husks); put back afterwards");
			server.runCommand("difficulty normal");
		}

		log("the player is awakened and taught the kinetic powers and Crushing Descent by command");
		server.runCommand("execute as @p run oldrepublic quest set 6");
		server.runCommand("oldrepublic force grant all @p");

		for (String id : new String[] {"force_speed", "force_barrier", "force_choke", "force_stasis", "jump_crushing_descent"}) {
			server.runCommand("oldrepublic force grant " + id + " @p");
		}

		server.runCommand("fill -6 -61 -3 6 -61 10 minecraft:coarse_dirt");
		Visuals.time(server, false);
		// The skill and Force attachments sync, and the skills feature refreshes once a second.
		context.waitTicks(30);
	}

	// ---------------------------------------------------------------- Push

	/**
	 * Push on three husks 4 to 5 blocks ahead: the charge, the release, the impact and the settling dust from behind; the
	 * travel and impact from the side; the charge and release in first person; a release by night; and a Focused Blast that
	 * slams a husk into a wall.
	 */
	private static void push(ClientGameTestContext context, TestServerContext server) {
		back(context, server, 8f);
		threeHusks(context, server);
		use(server, "force_push", "start");
		context.waitTicks(CHARGE - 2);
		shot(context, "01_push_charge");
		context.waitTicks(2);
		use(server, "force_push", "release");
		context.waitTicks(2);
		shot(context, "02_push_release");
		context.waitTicks(3);
		shot(context, "03_push_impact");
		context.waitTicks(18);
		shot(context, "04_push_settling");

		// From the side: the caster on the left (not drawn by the free camera), the husks on the right.
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 8f);
		threeHusks(context, server);
		Visuals.camera(context, server, new Vec3(-5.5, -58.3, 3.0), -90f, 6f);
		use(server, "force_push", "start");
		context.waitTicks(CHARGE);
		use(server, "force_push", "release");
		context.waitTicks(3);
		shot(context, "05_push_side_travel");
		context.waitTicks(3);
		shot(context, "06_push_side_impact");

		// First person.
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 6f);
		threeHusks(context, server);
		use(server, "force_push", "start");
		context.waitTicks(CHARGE - 2);
		shot(context, "07_push_first_person_charge");
		context.waitTicks(2);
		use(server, "force_push", "release");
		context.waitTicks(2);
		shot(context, "08_push_first_person_release");
		context.waitTicks(10);

		// By night, from behind.
		Visuals.time(server, true);
		back(context, server, 8f);
		threeHusks(context, server);
		use(server, "force_push", "start");
		context.waitTicks(CHARGE);
		use(server, "force_push", "release");
		context.waitTicks(4);
		shot(context, "09_push_night");
		context.waitTicks(10);
		Visuals.time(server, false);

		// Focused Blast: one husk slammed into a wall of stone bricks 9 blocks ahead, from the side.
		log("the Focused Blast branch set by command for one push; put back afterwards");
		server.runCommand("execute as @p run oldrepublic push branch focused_blast");
		server.runCommand("fill -3 -60 9 4 -57 9 minecraft:stone_bricks");
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 4f);
		removeAll(server);
		refill(context, server);
		husk(server, 0.5, 6.5);
		context.waitTicks(SETTLE);
		Visuals.camera(context, server, new Vec3(-5.0, -58.2, 6.5), -90f, 4f);
		use(server, "force_push", "start");
		context.waitTicks(CHARGE);
		use(server, "force_push", "release");
		context.waitTicks(7);
		shot(context, "10_push_focused_slam");
		context.waitTicks(10);
		server.runCommand("execute as @p run oldrepublic push branch none");
		server.runCommand("fill -3 -60 9 4 -57 9 minecraft:air");
		removeAll(server);
	}

	private static void threeHusks(ClientGameTestContext context, TestServerContext server) {
		removeAll(server);
		refill(context, server);
		husk(server, -1.5, 4.5);
		husk(server, 0.5, 5.5);
		husk(server, 2.5, 4.5);
		context.waitTicks(SETTLE);
	}

	// ---------------------------------------------------------------- Pull, Lift and Jump

	/** Pull on a husk 8 blocks ahead, from behind: the reach and drag, then the arrival at the caster's feet. */
	private static void pull(ClientGameTestContext context, TestServerContext server) {
		back(context, server, 6f);
		removeAll(server);
		refill(context, server);
		husk(server, 0.5, 8.5);
		context.waitTicks(SETTLE);
		use(server, "force_pull", "start");
		context.waitTicks(5);
		shot(context, "11_pull_drag");
		context.waitTicks(8);
		shot(context, "12_pull_arrive");
		context.waitTicks(10);
		removeAll(server);
	}

	/** Lift on a husk 5 blocks ahead: held up, from behind and from the side, then the slam from the side. */
	private static void lift(ClientGameTestContext context, TestServerContext server) {
		back(context, server, 4f);
		removeAll(server);
		refill(context, server);
		husk(server, 0.5, 5.5);
		context.waitTicks(SETTLE);
		use(server, "force_lift", "start");
		context.waitTicks(16);
		shot(context, "13_lift_held");
		Visuals.camera(context, server, new Vec3(-4.2, -57.9, 5.5), -90f, 8f);
		context.waitTicks(4);
		shot(context, "14_lift_held_side");
		use(server, "force_lift", "release");
		context.waitTicks(7);
		shot(context, "15_lift_slam");
		context.waitTicks(10);
		removeAll(server);
	}

	/** Jump with Crushing Descent, from behind: the gather, the take-off and the landing's shockwave. */
	private static void jump(ClientGameTestContext context, TestServerContext server) {
		back(context, server, 12f);
		refill(context, server);
		use(server, "force_jump", "start");
		context.waitTicks(14);
		shot(context, "16_jump_gather");
		use(server, "force_jump", "release");
		context.waitTicks(3);
		shot(context, "17_jump_launch");
		await(context, mc -> mc.player != null && !mc.player.onGround(), 20);
		await(context, mc -> mc.player != null && mc.player.onGround(), 120);
		context.waitTicks(2);
		shot(context, "18_jump_landing");
		context.waitTicks(20);
	}

	// ---------------------------------------------------------------- Speed, Stasis, Choke and Barrier

	/** A Speed run, sprinting south: from behind, then from in front. */
	private static void speed(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("effect clear @a");
		back(context, server, 10f);
		refill(context, server);
		use(server, "force_speed", "start");
		run(context, 14);
		shot(context, "19_speed_run_back");
		stopRunning(context);
		Visuals.stand(context, server, CameraType.THIRD_PERSON_FRONT, 10f);
		Visuals.hud(context, false);
		run(context, 12);
		shot(context, "20_speed_run_front");
		stopRunning(context);
		use(server, "force_speed", "cancel");
		context.waitTicks(10);
	}

	private static void run(ClientGameTestContext context, int ticks) {
		context.getInput().holdKey(FORWARD);
		context.getInput().holdKey(SPRINT);
		context.waitTicks(ticks);
	}

	private static void stopRunning(ClientGameTestContext context) {
		context.getInput().releaseKey(SPRINT);
		context.getInput().releaseKey(FORWARD);
		context.waitTicks(2);
	}

	/** Stasis on a husk 5 blocks ahead: held, from behind and close up, then broken by a hit. */
	private static void stasis(ClientGameTestContext context, TestServerContext server) {
		back(context, server, 5f);
		removeAll(server);
		refill(context, server);
		husk(server, 0.5, 5.5);
		context.waitTicks(SETTLE);
		use(server, "force_stasis", "start");
		context.waitTicks(14);
		shot(context, "21_stasis_held");
		Visuals.camera(context, server, new Vec3(-1.4, -58.3, 3.7), -46f, 12f);
		context.waitTicks(2);
		shot(context, "22_stasis_held_close");
		log("the held husk is hit for 9 by command, which breaks the stasis");
		server.runCommand("damage @e[tag=" + TAG + ",limit=1] 9");
		context.waitTicks(3);
		shot(context, "23_stasis_break");
		context.waitTicks(20);
		removeAll(server);
	}

	/** Choke on a husk 5 blocks ahead: held, in first person and from the side, then dropped. */
	private static void choke(ClientGameTestContext context, TestServerContext server) {
		Visuals.stand(context, server, CameraType.FIRST_PERSON, 2f);
		removeAll(server);
		refill(context, server);
		husk(server, 0.5, 5.5);
		context.waitTicks(SETTLE);
		use(server, "force_choke", "start");
		context.waitTicks(12);
		shot(context, "24_choke_first_person");
		Visuals.camera(context, server, new Vec3(-3.6, -58.0, 5.5), -90f, 4f);
		context.waitTicks(2);
		shot(context, "25_choke_held_side");
		use(server, "force_choke", "release");
		context.waitTicks(5);
		shot(context, "26_choke_drop");
		context.waitTicks(15);
		removeAll(server);
	}

	/** Barrier raised, two arrows stopped on it from the front right, then the pulse on release; from behind. */
	private static void barrier(ClientGameTestContext context, TestServerContext server) {
		back(context, server, 6f);
		removeAll(server);
		refill(context, server);
		use(server, "force_barrier", "start");
		context.waitTicks(10);
		shot(context, "27_barrier_raised");
		arrow(server, 3.5, 5.5);
		context.waitTicks(1);
		arrow(server, 4.5, 4.5);
		context.waitTicks(3);
		shot(context, "28_barrier_impact");
		context.waitTicks(30);
		use(server, "force_barrier", "release");
		context.waitTicks(3);
		shot(context, "29_barrier_pulse");
		context.waitTicks(20);
		removeAll(server);
	}

	/** An arrow from {@code (x, z)} at shoulder height flying at the player's chest. */
	private static void arrow(TestServerContext server, double x, double z) {
		Vec3 from = new Vec3(x, -58.6, z);
		Vec3 to = Visuals.SPOT.add(0, 1.1, 0);
		Vec3 v = to.subtract(from).normalize().scale(1.4);
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:arrow %.2f %.2f %.2f {Motion:[%.3fd,%.3fd,%.3fd],Tags:[\"%s\"]}", from.x, from.y,
				from.z, v.x, v.y, v.z, TAG));
	}

	// ---------------------------------------------------------------- helpers

	/** On the spot in third person from behind, the HUD hidden. */
	private static void back(ClientGameTestContext context, TestServerContext server, float pitch) {
		Visuals.stand(context, server, CameraType.THIRD_PERSON_BACK, pitch);
		Visuals.hud(context, false);
	}

	/** Waits until {@code done} holds on the client, at most {@code ticks} ticks, without failing the run when it never does. */
	private static void await(ClientGameTestContext context, Predicate<Minecraft> done, int ticks) {
		for (int i = 0; i < ticks && !context.computeOnClient(mc -> done.test(mc)); i++) {
			context.waitTicks(1);
		}
	}

	private static void husk(TestServerContext server, double x, double z) {
		server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.2f -60 %.2f %s", x, z, HUSK));
	}

	/** Discards (no drops, no death effects) everything the steps made. */
	private static void removeAll(TestServerContext server) {
		server.runOnServer(s -> {
			ServerLevel level = Visuals.player(s).level();

			for (Entity entity : level.getEntities((Entity) null, AREA, e -> e.entityTags().contains(TAG))) {
				entity.discard();
			}
		});
	}

	private static void refill(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @p run oldrepublic energy fill");
		server.runCommand("execute as @p run oldrepublic force cooldowns");
		context.waitTicks(2);
	}

	private static void use(TestServerContext server, String power, String action) {
		server.runCommand("execute as @p run oldrepublic force use " + power + " " + action);
	}

	private static void shot(ClientGameTestContext context, String what) {
		context.takeScreenshot("oldrepublic_look_force_kinetic_" + what);
	}

	private static void log(String shortcut) {
		OldRepublicMod.LOGGER.info("Force kinetic look: shortcut, {}", shortcut);
	}

	private ForceKineticLook() {
	}
}

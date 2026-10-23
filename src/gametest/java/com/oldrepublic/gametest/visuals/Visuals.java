package com.oldrepublic.gametest.visuals;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared staging for the close-up pictures of {@link com.oldrepublic.gametest.VisualsClientTest}: which look steps run, a
 * plain stage on the superflat test world, and a free camera.
 *
 * <p>The {@code visuals} workflow sets {@code OLDREPUBLIC_VISUALS} to a comma-separated list of look steps (or {@code all});
 * then only {@code VisualsClientTest} runs ({@link #only()}: every other client test returns at once) and only the named
 * steps, so a change to how something looks is seen in minutes instead of after the full client run. Without the variable
 * (the build workflow) every client test runs, {@code VisualsClientTest} with all its steps.
 *
 * <p>The free camera is an invisible marker armour stand the client is told to look through ({@link #camera}). 26.2 draws
 * the local player only from the player's own camera, so a free camera shows the world and other entities but not the
 * player: for pictures of a player-shaped body from any side, a mannequin stands in ({@link #standIn}), drawn with the
 * player model, the held items and the saber poses. The HUD is hidden while the free camera is in use.
 */
public final class Visuals {
	/** The look steps a visuals-only run asks for, lower case; empty in a full run. */
	private static final List<String> ONLY = parse(System.getenv("OLDREPUBLIC_VISUALS"));
	/** Where the player stands, facing south (+Z): on the superflat floor, the stage's middle. */
	public static final Vec3 SPOT = new Vec3(0.5, -60, 0.5);
	/** Tag of the camera stands, so cleanup never touches anyone else's entities. */
	private static final String CAMERA_TAG = "or_look_camera";
	/** Tag of the stand-in mannequins. */
	private static final String STAND_IN_TAG = "or_look_stand_in";

	private static List<String> parse(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}

		return Arrays.stream(value.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
	}

	/** True in a visuals-only run: every client test but {@code VisualsClientTest} returns at once. */
	public static boolean only() {
		return !ONLY.isEmpty();
	}

	/** Whether the look steps {@code name} run: always in a full run, else when named or with {@code all}. */
	public static boolean wanted(String name) {
		return ONLY.isEmpty() || ONLY.contains("all") || ONLY.contains(name.toLowerCase(Locale.ROOT));
	}

	/**
	 * Whether a visuals-only run names {@code name} itself ({@code all} does not count, and a full run never does): for
	 * pictures too slow for every run, such as the structure tour.
	 */
	public static boolean named(String name) {
		return ONLY.contains(name.toLowerCase(Locale.ROOT));
	}

	/** The test's only player. */
	public static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/**
	 * Builds the stage: a smooth stone floor 21 blocks square around {@link #SPOT}, a black wall 4 blocks north of it (behind
	 * the player, so a camera in front sees blades against it) and the air cleared above; open sky everywhere else.
	 */
	public static void stage(TestServerContext server) {
		server.runCommand("fill -10 -61 -10 10 -61 10 minecraft:smooth_stone");
		server.runCommand("fill -10 -60 -10 10 -50 10 minecraft:air");
		server.runCommand("fill -6 -60 -4 6 -55 -4 minecraft:black_concrete");
		server.runCommand("weather clear");
		server.runCommand("gamemode creative @a");
		server.runCommand("effect clear @a");
	}

	/** Noon, or deep night (no moon in the way at 18000: it is overhead). */
	public static void time(TestServerContext server, boolean night) {
		server.runCommand(night ? "time set 18000" : "time set noon");
	}

	/** Puts the player on {@link #SPOT} facing south, the client's camera type set and the view at {@code pitch}. */
	public static void stand(ClientGameTestContext context, TestServerContext server, CameraType camera, float pitch) {
		release(context, server);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(SPOT.x, SPOT.y, SPOT.z);
			p.setYRot(0f);
			p.setYHeadRot(0f);
			p.setYBodyRot(0f);
		});
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		context.getInput().lookAt(0f, pitch);
		context.waitTicks(10);
	}

	/**
	 * Looks through a free camera at {@code eye} with {@code yaw} and {@code pitch} (degrees, Minecraft's convention: yaw 0
	 * faces south, 90 west, -90 east, 180 north; pitch down is positive). The HUD is hidden until {@link #release}.
	 */
	public static void camera(ClientGameTestContext context, TestServerContext server, Vec3 eye, float yaw, float pitch) {
		release(context, server);
		server.runCommand(String.format(Locale.ROOT,
				"summon minecraft:armor_stand %.3f %.3f %.3f {Marker:1b,Invisible:1b,NoGravity:1b,Silent:1b,Tags:[\"%s\"],Rotation:[%.1ff,%.1ff]}",
				eye.x, eye.y, eye.z, CAMERA_TAG, yaw, pitch));
		context.waitFor(mc -> stand(mc, eye) != null, 60);
		context.runOnClient(mc -> {
			Entity stand = stand(mc, eye);
			stand.setYRot(yaw);
			stand.setXRot(pitch);
			stand.setYHeadRot(yaw);
			mc.setCameraEntity(stand);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		hud(context, false);
		context.waitTicks(4);
	}

	/** Back to the player's own camera, the HUD shown, and every free camera stand removed. */
	public static void release(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(mc -> {
			if (mc.player != null) {
				mc.setCameraEntity(mc.player);
			}
		});
		hud(context, true);
		server.runCommand("kill @e[type=minecraft:armor_stand,tag=" + CAMERA_TAG + "]");
	}

	/**
	 * Hides or shows the HUD (and with it the first-person hand) as F1 does: the key is pressed when the HUD is not already
	 * as asked (26.2 keeps the flag on {@code Minecraft.gui.hud}).
	 */
	public static void hud(ClientGameTestContext context, boolean shown) {
		if (context.computeOnClient(mc -> mc.gui.hud.isHidden()) != shown) {
			return;
		}

		context.getInput().pressKey(InputConstants.KEY_F1);
		context.waitFor(mc -> mc.gui.hud.isHidden() != shown, 20);
	}

	/**
	 * Puts a stand-in for the player at {@code at} facing {@code yaw}: a mannequin (drawn like a player, with no name or label,
	 * that nothing pushes) holding {@code main} and {@code off}, and moves the player six blocks south, out of the way. It
	 * stays until {@link #clearStandIns}; any earlier stand-in is removed first.
	 */
	public static void standIn(ClientGameTestContext context, TestServerContext server, Vec3 at, float yaw, ItemStack main, ItemStack off) {
		clearStandIns(server);
		server.runCommand(String.format(Locale.ROOT,
				"summon minecraft:mannequin %.3f %.3f %.3f {Tags:[\"%s\"],Rotation:[%.1ff,0f],hide_description:1b,immovable:1b}", at.x, at.y, at.z,
				STAND_IN_TAG, yaw));
		int id = server.computeOnServer(s -> {
			LivingEntity standIn = standIn(s);
			standIn.setYHeadRot(yaw);
			standIn.setYBodyRot(yaw);
			standIn.setItemSlot(EquipmentSlot.MAINHAND, main);
			standIn.setItemSlot(EquipmentSlot.OFFHAND, off);
			player(s).teleportTo(SPOT.x, SPOT.y, SPOT.z + 6);
			return standIn.getId();
		});
		// Tags stay on the server, but the client's copy of the stand-in has the server's entity id: wait for it with its sabers.
		context.waitFor(mc -> mc.level != null && mc.level.getEntity(id) instanceof LivingEntity standIn && !standIn.getMainHandItem().isEmpty()
				&& standIn.getOffhandItem().isEmpty() == off.isEmpty(), 60);
		context.waitTicks(4);
	}

	/** The stand-in mannequin ({@link #standIn}), server side. */
	public static LivingEntity standIn(MinecraftServer server) {
		ServerLevel level = player(server).level();
		List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, new AABB(SPOT, SPOT).inflate(16), e -> e.entityTags().contains(STAND_IN_TAG));

		if (found.isEmpty()) {
			throw new AssertionError("No stand-in mannequin near the stage");
		}

		return found.getFirst();
	}

	/** Removes every stand-in mannequin at once (discarded, not killed, so no body falls over in the next picture). */
	public static void clearStandIns(TestServerContext server) {
		server.runOnServer(s -> player(s).level()
				.getEntitiesOfClass(LivingEntity.class, new AABB(SPOT, SPOT).inflate(16), e -> e.entityTags().contains(STAND_IN_TAG))
				.forEach(Entity::discard));
	}

	private static ArmorStand stand(Minecraft mc, Vec3 eye) {
		if (mc.level == null) {
			return null;
		}

		List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, new AABB(eye, eye).inflate(0.25), ArmorStand::isMarker);
		return stands.isEmpty() ? null : stands.getFirst();
	}

	private Visuals() {
	}
}

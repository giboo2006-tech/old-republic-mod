package com.oldrepublic.gametest.m2;

import java.util.List;
import java.util.Locale;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.force.SenseReveal;
import com.oldrepublic.client.skill.ForceScreen;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.lift.ForceLift;
import com.oldrepublic.force.lift.LiftBalance;
import com.oldrepublic.force.sense.ForceSense;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.skill.SkillIds;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Screenshot steps of the force feature for the M2 client test (spec 16.2): Pull mid-drag; Jump
 * gather, launch and landing; a Heal channel; Lightning charge, arcs on a target, a chain and the
 * frame after the stop (nothing may remain). The powers are driven with {@code /oldrepublic force
 * use} so the steps do not depend on slot assignment. Screenshots are {@code oldrepublic_m2_4x_*}.
 * The Milestone 3 steps ({@link #runM3}, screenshots {@code oldrepublic_m3_30} to {@code 34}) run
 * from the M3 client test.
 */
public final class ForceClientSteps {
	/** Where the player stands, facing south (+Z), on the superflat test world. */
	private static final String STAND = "0 -60 0";
	private static final String HUSK = "{PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}";

	/** Floor height of the Milestone 3 stage: above the seeded exploration world's terrain, below the holocron steps' stage. */
	private static final int M3_STAGE_Y = 224;
	/** The M3 stage reaches this far east and west of the player's column, north ({@code BACK}) and south ({@code FRONT}). */
	private static final int M3_HALF_WIDTH = 9;
	private static final int M3_BACK = 8;
	private static final int M3_FRONT = 14;
	private static final int M3_HEADROOM = 8;
	/** Tag on every creature the M3 steps summon, so their cleanup touches nothing else. */
	private static final String M3_TAG = "or_m3_force";
	/** W1's rubble block, looked up by id so these steps do not depend on the world feature's classes. */
	private static final Identifier RUBBLE = OldRepublicMod.id("collapsed_rubble");
	/** Ticks of the windup tell sent for the Danger Sense screenshot. */
	private static final int WINDUP_TICKS = 12;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("time set noon");
		server.runCommand("gamemode survival @a");
		server.runCommand("effect give @a resistance infinite 4 true");
		server.runCommand("execute as @a run oldrepublic quest set 4");
		server.runCommand("oldrepublic force grant all @p");
		server.runCommand("tp @a " + STAND + " 0 0");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(20);

		pull(context, server);
		jump(context, server);
		heal(context, server);
		lightning(context, server);

		server.runCommand("kill @e[type=minecraft:husk]");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
	}

	/**
	 * Milestone 3 screenshots of the force feature (spec 17.2, 30 to 34): the Sense reveal, a Danger
	 * Sense prickle, Lift on a zombie and on rubble, and the Exploration tab. Called first by
	 * {@code m3/HolocronClientSteps.run}; leaves the player standing with no screen open.
	 *
	 * <p>The steps run on a stone stage built {@link #M3_STAGE_Y} high over the player's column (so
	 * the seeded world's terrain does not matter), grant Sense, Lift and Danger Sense by command,
	 * cast Sense with the real H key (and fail if no reveal arrives), and drive Lift with
	 * {@code /oldrepublic force use}. Every creature they summon carries {@link #M3_TAG} and is
	 * discarded again; the stage is removed and the player put back where they stood, in first
	 * person. The grants stay. If the player is not awakened yet, Chapter One is marked complete
	 * (a logged shortcut).
	 */
	public static void runM3(ClientGameTestContext context, TestServerContext server) {
		double[] origin = server.computeOnServer(s -> {
			ServerPlayer p = firstPlayer(s);
			return new double[] {p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()};
		});
		M3Stage stage = new M3Stage(Mth.floor(origin[0]), M3_STAGE_Y, Mth.floor(origin[2]));
		stage.build(server);

		try {
			setUpM3(context, server, stage);
			senseReveal(context, server, stage);
			dangerSense(context, server, stage);
			liftZombie(context, server, stage);
			liftRubble(context, server, stage);
			explorationTab(context);
		} finally {
			context.setScreen(() -> null);
			useM3(server, ForceLift.ID, "cancel");
			removeM3Creatures(server, stage);
			stage.clear(server);
			server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", origin[0], origin[1], origin[2], origin[3], origin[4]));
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(10);
		}
	}

	/** Awakens the player if needed, grants Sense, Lift and Danger Sense, and stands them on the stage. */
	private static void setUpM3(ClientGameTestContext context, TestServerContext server, M3Stage stage) {
		boolean awakened = server.computeOnServer(s -> Progression.get(firstPlayer(s)).isAwakened());

		if (!awakened) {
			OldRepublicMod.LOGGER.info("Force client steps (M3): the player is not awakened; shortcut: quest set {}", QuestStage.COMPLETE);
			server.runCommand("execute as @p run oldrepublic quest set " + QuestStage.COMPLETE.ordinal());
		}

		server.runCommand("oldrepublic force grant " + ForceSense.ID + " @p");
		server.runCommand("oldrepublic force grant " + ForceLift.ID + " @p");
		server.runCommand("oldrepublic force grant " + SkillIds.SENSE_DANGER + " @p");
		server.runCommand(stage.tp(0, 0));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		// The skill and Force attachments sync, and the skills feature refreshes once a second.
		context.waitTicks(30);
	}

	/**
	 * 30: Sense cast with the real H key: thin dust streams run from the player toward a hostile husk,
	 * a cow and a villager hidden behind a wall, and settle into mote clusters at their chests. No
	 * outline, glow or ring may appear.
	 */
	private static void senseReveal(ClientGameTestContext context, TestServerContext server, M3Stage stage) {
		refillM3(context, server);
		server.runCommand(stage.fill(-4, 0, 9, -1, 2, 9, "minecraft:mossy_stone_bricks"));
		summonStill(server, stage, "minecraft:husk", 2, 7);
		summonStill(server, stage, "minecraft:cow", -5, 5);
		// Behind the wall: out of the player's sight, so it counts as hidden.
		summonStill(server, stage, "minecraft:villager", -3, 11);
		context.getInput().lookAt(0f, 8f);
		context.waitTicks(15);

		context.getInput().pressKey(ForceKeys.FORCE_SENSE);
		context.waitFor(client -> !SenseReveal.points().isEmpty(), 20);
		int points = context.computeOnClient(client -> SenseReveal.points().size());

		if (points < 3) {
			throw new AssertionError("Force Sense with H revealed " + points + " points; expected the husk, the cow and the hidden villager");
		}

		// The inhale, then the streams (from tick 6) and the first clusters.
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_m3_30_sense_reveal");
		context.waitTicks(10);
		removeM3Creatures(server, stage);
		server.runCommand(stage.fill(-4, 0, 9, -1, 2, 9, "minecraft:air"));
	}

	/**
	 * 31: Danger Sense in first person: a husk behind the player to the right starts an attack tell
	 * ({@code npc_windup} variant 0, sent from the server thread), and a faint dust tick shows at the
	 * lower right edge of the view while the prickle plays from that side.
	 */
	private static void dangerSense(ClientGameTestContext context, TestServerContext server, M3Stage stage) {
		server.runCommand(stage.tp(0, 0));
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().lookAt(0f, 0f);
		summonStill(server, stage, "minecraft:husk", -3, -5);
		context.waitTicks(15);

		server.runOnServer(s -> {
			ServerPlayer p = firstPlayer(s);
			Entity husk = m3Creatures(p.level(), stage).stream().filter(e -> e.getType() == EntityTypes.HUSK).findFirst()
					.orElseThrow(() -> new AssertionError("the Danger Sense husk is missing"));
			Vec3 toPlayer = p.getEyePosition().subtract(husk.getEyePosition()).normalize();
			EffectEvents.toPlayer(p, SaberEffects.NPC_WINDUP, husk.getEyePosition(), toPlayer, husk.getId(), WINDUP_TICKS, 0);
		});
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m3_31_danger_sense_edge");
		context.waitTicks(20);
		removeM3Creatures(server, stage);
	}

	/** 32: a zombie held 2.5 blocks up by Lift: the raised palm, the shimmer column, falling grit, the flailing target. */
	private static void liftZombie(ClientGameTestContext context, TestServerContext server, M3Stage stage) {
		refillM3(context, server);
		server.runCommand(stage.tp(0, 0));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		server.runOnServer(s -> spawnZombie(firstPlayer(s), stage, 0, 6));
		context.getInput().lookAt(0f, 6f);
		context.waitTicks(15);

		useM3(server, ForceLift.ID, "start");
		// The 10-tick rise, then a moment of hanging.
		context.waitTicks(16);

		if (!lifting(server)) {
			removeM3Creatures(server, stage);
			throw new AssertionError("Force Lift did not take hold of the zombie; screenshot 32 would show no lift");
		}

		context.takeScreenshot("oldrepublic_m3_32_lift_zombie");
		useM3(server, ForceLift.ID, "release");
		// The slam lands and its dust settles.
		context.waitTicks(30);
		removeM3Creatures(server, stage);
	}

	/**
	 * 33: Lift on {@code oldrepublic:collapsed_rubble}, channelled until the rubble's 20 ticks are
	 * done. When the block is not registered, or its handler does not take the lift, a mossy
	 * cobblestone stand-in shows the caster's lift pose through the start event alone (logged).
	 */
	private static void liftRubble(ClientGameTestContext context, TestServerContext server, M3Stage stage) {
		refillM3(context, server);
		server.runCommand(stage.tp(0, 0));
		boolean registered = server.computeOnServer(s -> BuiltInRegistries.BLOCK.containsKey(RUBBLE));
		BlockPos target = stage.pos(0, 0, 5);
		server.runCommand(stage.setblock(0, 0, 5, registered ? RUBBLE.toString() : "minecraft:mossy_cobblestone"));
		context.getInput().lookAt(target);
		context.waitTicks(10);
		boolean lifting = false;

		if (registered) {
			useM3(server, ForceLift.ID, "start");
			context.waitTicks(10);
			lifting = lifting(server);
		}

		if (!lifting) {
			OldRepublicMod.LOGGER.info("Force client steps (M3): {}; screenshot 33 uses a mossy cobblestone stand-in (lift pose only)",
					registered ? "collapsed_rubble did not take the lift" : "collapsed_rubble is not registered");
			server.runCommand(stage.setblock(0, 0, 5, "minecraft:mossy_cobblestone"));
			server.runOnServer(s -> liftEvent(firstPlayer(s), target, ForceEffects.LIFT_START));
			context.waitTicks(10);
		}

		context.takeScreenshot("oldrepublic_m3_33_lift_rubble");

		if (lifting) {
			// The rest of the rubble's 20-tick channel, then let go.
			context.waitTicks(14);
			useM3(server, ForceLift.ID, "release");
		} else {
			server.runOnServer(s -> liftEvent(firstPlayer(s), target, ForceEffects.LIFT_END));
		}

		context.waitTicks(20);
		server.runCommand(stage.setblock(0, 0, 5, "minecraft:air"));
	}

	/** 34: the Force screen on the Exploration tab, with Force Sense selected. */
	private static void explorationTab(ClientGameTestContext context) {
		context.runOnClient(client -> ForceScreen.openOn(client, SkillIds.TREE_EXPLORATION, SkillIds.FORCE_SENSE));
		context.waitForScreen(ForceScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_34_exploration_tree_tab");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** The stand-in's lift start or end, as the server sends it for a block target. */
	private static void liftEvent(ServerPlayer player, BlockPos block, int phase) {
		EffectEvents.toTracking(player, ForceEffects.LIFT, Vec3.atCenterOf(block), player.getLookAngle(), -1,
				phase == ForceEffects.LIFT_START ? new LiftBalance().maxHoldTicks / 20f : 0f, ForceEffects.packCaster(player.getId(), phase));
	}

	private static boolean lifting(TestServerContext server) {
		return server.computeOnServer(s -> {
			ForceAbility lift = Abilities.get(ForceLift.ID);
			return lift != null && lift.isActive(firstPlayer(s));
		});
	}

	/** A creature at the stage spot {@code (dx, dz)}, facing the player, slowed to a standstill and tagged for cleanup. */
	private static void summonStill(TestServerContext server, M3Stage stage, String type, int dx, int dz) {
		server.runCommand("summon " + type + " " + stage.at(dx, 0, dz) + " {PersistenceRequired:1b,Rotation:[180f,0f],Tags:[\"" + M3_TAG
				+ "\"],active_effects:[{id:\"minecraft:slowness\",amplifier:5,duration:-1,show_particles:0b}]}");
	}

	/** A plain zombie (a leather cap keeps it from burning at noon), slowed and tagged like {@link #summonStill}. */
	private static void spawnZombie(ServerPlayer player, M3Stage stage, int dx, int dz) {
		ServerLevel level = player.level();
		Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);

		if (zombie == null) {
			throw new AssertionError("could not create the Lift zombie");
		}

		Vec3 feet = Vec3.atBottomCenterOf(stage.pos(dx, 0, dz));
		zombie.snapTo(feet.x, feet.y, feet.z, 180f, 0f);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MobEffectInstance.INFINITE_DURATION, 5, false, false));
		zombie.setPersistenceRequired();
		zombie.addTag(M3_TAG);
		level.addFreshEntity(zombie);
	}

	private static List<Entity> m3Creatures(ServerLevel level, M3Stage stage) {
		return level.getEntities((Entity) null, stage.box(), e -> e.entityTags().contains(M3_TAG));
	}

	/** Discards (no drops, no death effects) every creature the steps summoned. */
	private static void removeM3Creatures(TestServerContext server, M3Stage stage) {
		server.runOnServer(s -> m3Creatures(firstPlayer(s).level(), stage).forEach(Entity::discard));
	}

	private static void refillM3(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @p run oldrepublic energy fill");
		server.runCommand("execute as @p run oldrepublic force cooldowns");
		context.waitTicks(2);
	}

	private static void useM3(TestServerContext server, String power, String action) {
		server.runCommand("execute as @p run oldrepublic force use " + power + " " + action);
	}

	private static ServerPlayer firstPlayer(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/**
	 * The Milestone 3 stage: a smooth stone floor at {@code y - 1} around the player's column
	 * {@code (x, z)}, with air above it. Offsets are east ({@code dx}), up ({@code dy}) and south
	 * ({@code dz}) of the player's spot.
	 */
	private record M3Stage(int x, int y, int z) {
		void build(TestServerContext server) {
			server.runCommand(fill(-M3_HALF_WIDTH, 0, -M3_BACK, M3_HALF_WIDTH, M3_HEADROOM, M3_FRONT, "minecraft:air"));
			server.runCommand(fill(-M3_HALF_WIDTH, -1, -M3_BACK, M3_HALF_WIDTH, -1, M3_FRONT, "minecraft:smooth_stone"));
		}

		void clear(TestServerContext server) {
			server.runCommand(fill(-M3_HALF_WIDTH, -1, -M3_BACK, M3_HALF_WIDTH, M3_HEADROOM, M3_FRONT, "minecraft:air"));
		}

		String fill(int dx1, int dy1, int dz1, int dx2, int dy2, int dz2, String block) {
			return "fill " + at(dx1, dy1, dz1) + " " + at(dx2, dy2, dz2) + " " + block;
		}

		String setblock(int dx, int dy, int dz, String block) {
			return "setblock " + at(dx, dy, dz) + " " + block;
		}

		/** The player on the spot {@code (dx, dz)}, facing south. */
		String tp(int dx, int dz) {
			return String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 0", x + dx, y, z + dz);
		}

		String at(int dx, int dy, int dz) {
			return (x + dx) + " " + (y + dy) + " " + (z + dz);
		}

		BlockPos pos(int dx, int dy, int dz) {
			return new BlockPos(x + dx, y + dy, z + dz);
		}

		AABB box() {
			return new AABB(x - M3_HALF_WIDTH, y - 1, z - M3_BACK, x + M3_HALF_WIDTH + 1, y + M3_HEADROOM + 1, z + M3_FRONT + 1);
		}
	}

	private static void refill(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @a run oldrepublic energy fill");
		server.runCommand("execute as @a run oldrepublic force cooldowns");
		context.waitTicks(2);
	}

	private static void use(TestServerContext server, String power, String action) {
		server.runCommand("execute as @a run oldrepublic force use " + power + " " + action);
	}

	private static void pull(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("summon husk 0 -60 9 " + HUSK);
		context.getInput().lookAt(0, 5);
		context.waitTicks(20);
		use(server, "force_pull", "start");
		// Reach (3 ticks), then the husk is on its way.
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m2_40_pull_drag");
		context.waitTicks(30);
		server.runCommand("kill @e[type=minecraft:husk]");
	}

	private static void jump(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("tp @a " + STAND + " 0 0");
		context.waitTicks(10);
		use(server, "force_jump", "start");
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_m2_41_jump_gather");
		context.waitTicks(6);
		use(server, "force_jump", "release");
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m2_42_jump_launch");
		context.waitTicks(60);
		context.takeScreenshot("oldrepublic_m2_43_jump_landing");
		context.waitTicks(20);
	}

	private static void heal(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("effect clear @a resistance");
		server.runCommand("damage @p 8 minecraft:generic");
		server.runCommand("effect give @a resistance infinite 4 true");
		context.waitTicks(30);
		use(server, "force_heal", "start");
		context.waitTicks(25);
		context.takeScreenshot("oldrepublic_m2_44_heal_channel");
		use(server, "force_heal", "release");
		context.waitTicks(30);
	}

	private static void lightning(ClientGameTestContext context, TestServerContext server) {
		refill(context, server);
		server.runCommand("tp @a " + STAND + " 0 0");
		server.runCommand("summon husk 0 -60 6 " + HUSK);
		context.getInput().lookAt(0, 8);
		context.waitTicks(20);

		use(server, "force_lightning", "start");
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m2_45_lightning_charge");
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_m2_46_lightning_arcs");
		use(server, "force_lightning", "release");
		context.waitTicks(40);

		// Chain Lightning: two more husks within 4 blocks of the first.
		refill(context, server);
		server.runCommand("oldrepublic force grant lightning_chain @p");
		server.runCommand("summon husk 2 -60 7 " + HUSK);
		server.runCommand("summon husk -2 -60 7 " + HUSK);
		context.waitTicks(10);
		use(server, "force_lightning", "start");
		context.waitTicks(22);
		context.takeScreenshot("oldrepublic_m2_47_lightning_chain");
		use(server, "force_lightning", "release");
		// The frame after the stop: no arc may remain (smoke wisps at the hand only).
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m2_48_lightning_after_stop");
		context.waitTicks(20);
	}

	private ForceClientSteps() {
	}
}

package com.oldrepublic.gametest.m4;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.boss.kaast.CampLayout;
import com.oldrepublic.boss.kaast.CampMine;
import com.oldrepublic.boss.kaast.KaastFight;
import com.oldrepublic.boss.kaast.KaastSession;
import com.oldrepublic.boss.kaast.Rattle;
import com.oldrepublic.boss.kaast.TorvinKaast;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.creature.AshHound;
import com.oldrepublic.enemy.creature.CreatureEntities;
import com.oldrepublic.enemy.creature.Hollowhorn;
import com.oldrepublic.enemy.droid.DroidBalance;
import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.enemy.droid.SecurityDroid;
import com.oldrepublic.enemy.mandalorian.HunterBalance;
import com.oldrepublic.enemy.mandalorian.HunterEntities;
import com.oldrepublic.enemy.mandalorian.HunterNet;
import com.oldrepublic.enemy.mandalorian.HunterRocket;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.enemy.mandalorian.Netting;
import com.oldrepublic.force.ForceM4Feature;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.force.choke.ChokedTargets;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 client test steps for the new powers, enemies and Kaast (W6). {@code M4ClientTest}
 * runs them in the shared seeded world (spec MILESTONE_4.md 17.2). Screenshots are
 * {@code oldrepublic_m4_<nn>_<what>} with {@code nn} from 60 to 69:
 * <ul>
 * <li>60 {@code speed_dust_trail}: a Force Speed run, sprinting away from the camera, the footfall dust behind;</li>
 * <li>61 {@code barrier_stopping_bolts}: a held Barrier, one hunter bolt already stopped on it and the next on its way;</li>
 * <li>62 {@code choke_on_hunter}: a Mandalorian hunter lifted in Choke;</li>
 * <li>63 {@code hunter_flamer_tell}: a hunter's wrist raised in the flamer's tell, close in;</li>
 * <li>64 {@code droid_shield}: a security droid under half health with its frontal plate up, a bolt stopped on it;</li>
 * <li>65 {@code hollowhorn_charge_tell}: a hollowhorn's head-down snort before a charge;</li>
 * <li>66 {@code ash_hound_lunge}: an ash hound of a pack in the air, leaping at the player;</li>
 * <li>67 {@code kaast_phase1}, 68 {@code kaast_phase2}, 69 {@code kaast_phase3}: Torvin Kaast on his nest
 * with Rattle, off it after Rattle flies, and with the axe drawn (Kaast in each phase, through
 * {@code /oldrepublic kaast phase}); 69 {@code kaast_yield}: knelt, his helmet off (the yield comes in
 * phase 3, so it shares the number, as W1's biome shots share 10 and 11).</li>
 * </ul>
 *
 * <p>The steps build a stone stage 40 blocks above wherever the player stands, in the player's own
 * level, with a rail two blocks high round its edge, and stand the player on it. Shortcuts, each logged: the player is
 * made awakened and taught Speed, Barrier and Choke, and their energy is refilled before each power;
 * the powers are started on the server as their key would ({@code onInput}); the foes are spawned
 * beside the player (the barrier's shooter and the droid without AI, so they hold still); the droid's
 * health is set to just above half before a hit; the Kaast fight starts at once in a hand-made camp
 * on the stage ({@code KaastFight.begin}), the hunters he calls in phase 3 are sent away, and the
 * yield comes from one heavy hit. Difficulty is raised from Peaceful to Normal for the steps when it
 * was Peaceful. The server confirms each step did what its screenshot claims. Everything is put back
 * at the end: the fight, the foes, shots and mines near the stage, the stage itself, the player's
 * Force and story data, health, camera and position.
 */
public final class FoesClientSteps {
	/** Height of the stage above the player's starting position. */
	private static final int LIFT = 40;
	/** The stage's half-width, depth behind the spot and length ahead of it (south, +Z), and the air cleared above it. */
	private static final int HALF_WIDTH = 12;
	private static final int BACK = 8;
	private static final int FRONT = 22;
	private static final int HEADROOM = 14;
	/** Tag of everything the steps spawn, so cleanup never touches anyone else's entities. */
	private static final String TAG = "or_m4_foes";
	private static final Function<Options, KeyMapping> FORWARD = o -> o.keyUp;
	private static final Function<Options, KeyMapping> SPRINT = o -> o.keySprint;
	/** Ticks of running before the dust trail's picture. */
	private static final int RUN_TICKS = 16;
	/** How far ahead the barrier's shooter stands, and the ticks between its two bolts. */
	private static final int SHOOTER_AHEAD = 8;
	private static final int BOLT_GAP = 2;
	/** How far ahead the choked hunter stands, and the ticks of lift before its picture. */
	private static final int CHOKE_AHEAD = 6;
	private static final int CHOKE_LIFT_TICKS = 12;
	/** The flamer hunter's pen: 3 blocks ahead, walled on three sides so it cannot back out of flamer range. */
	private static final int FLAMER_AHEAD = 3;
	private static final int DROID_AHEAD = 5;
	private static final int HOLLOWHORN_AHEAD = 10;
	/** The ticks the client gets to see a tell, then a pack's first leap, and Kaast's phase transition to end, before the step fails. */
	private static final int TELL_TIMEOUT = 100;
	private static final int LUNGE_TIMEOUT = 240;
	private static final int TRANSITION_TIMEOUT = 100;
	/** The camp on the stage: the pit's centre, the nest (a pillar), the player's spot, the ring's radius. */
	private static final int HEART_AHEAD = 12;
	private static final int NEST_AHEAD = 19;
	private static final int NEST_HEIGHT = 3;
	private static final int KAAST_SPOT = 8;
	private static final double RING = 9.0;
	/** Ticks into each Kaast phase before its picture, and from the yielding blow to the yield's. */
	private static final int PHASE1_TICKS = 40;
	private static final int PHASE_TICKS = 30;
	private static final int YIELD_TICKS = 25;

	/** What the steps change on the player, put back at the end. */
	private record Before(Vec3 position, float yaw, float pitch, ForceData force, StoryData story, boolean peaceful) {
	}

	/** Runs the foes steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		Before before = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return new Before(p.position(), p.getYRot(), p.getXRot(), Progression.get(p).copy(), Story.get(p).copy(),
					p.level().getDifficulty() == Difficulty.PEACEFUL);
		});
		BlockPos start = BlockPos.containing(before.position());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());

		if (before.peaceful()) {
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, difficulty Normal for the steps (Peaceful removes the foes); put back afterwards");
			server.runCommand("difficulty normal");
		}

		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			stage.build(p.level());
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the player is awakened and taught Speed, Barrier and Choke (put back afterwards)");
			ForceData data = Progression.get(p);

			if (!data.isAwakened()) {
				data.questStage = QuestStage.FOCUS.ordinal();
			}

			data.abilities.addAll(List.of(ForceSpeed.ID, ForceBarrier.ID, ForceChoke.ID));
			Progression.commit(p, data);
		});

		try {
			speed(context, server, stage);
			barrier(context, server, stage);
			choke(context, server, stage);
			flamerTell(context, server, stage);
			droidShield(context, server, stage);
			hollowhornTell(context, server, stage);
			houndLunge(context, server, stage);
			kaast(context, server, stage);
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				KaastFight.reset(p);
				Dialogues.close(p);
				stage.removeActors(p);
				stage.clear(p.level());
				Progression.commit(p, before.force());
				Story.commit(p, before.story());
				p.setHealth(p.getMaxHealth());
				p.teleportTo(before.position().x, before.position().y, before.position().z);
			});
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.getInput().lookAt(before.yaw(), before.pitch());

			if (before.peaceful()) {
				server.runCommand("difficulty peaceful");
			}

			context.waitTicks(10);
		}
	}

	// ---------------------------------------------------------------- the stage

	/** The stage: a smooth stone floor at {@code y - 1} round the spot {@code (x, y, z)}, air above, a rail two blocks high round its edge. */
	private record Stage(int x, int y, int z) {
		void build(ServerLevel level) {
			fill(level, Blocks.AIR.defaultBlockState(), true);
		}

		void clear(ServerLevel level) {
			fill(level, Blocks.AIR.defaultBlockState(), false);
		}

		private void fill(ServerLevel level, BlockState air, boolean build) {
			BlockState floor = build ? Blocks.SMOOTH_STONE.defaultBlockState() : air;

			for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
				for (int dz = -BACK; dz <= FRONT; dz++) {
					boolean edge = Math.abs(dx) == HALF_WIDTH || dz == -BACK || dz == FRONT;
					level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floor, Block.UPDATE_CLIENTS);

					for (int up = 0; up <= HEADROOM; up++) {
						level.setBlock(new BlockPos(x + dx, y + up, z + dz), edge && up < 2 ? floor : air, Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		/** The block at the stage's floor level, {@code dx} east and {@code dz} south of the spot. */
		BlockPos at(int dx, int dz) {
			return new BlockPos(x + dx, y, z + dz);
		}

		/** Everything near the stage that the steps may leave: their own foes, shots, nets, mines, Rattle and Kaast's called hunters. */
		AABB area() {
			return new AABB(x - HALF_WIDTH, y - 2, z - BACK, x + HALF_WIDTH + 1, y + HEADROOM + 1, z + FRONT + 1).inflate(24.0);
		}

		/** Ends the player's powers and net and removes what the steps spawned. */
		void removeActors(ServerPlayer p) {
			ForceM4Feature.forgetAll(p, false);

			if (Netting.isNetted(p)) {
				Netting.free(p, Netting.FREED_GONE);
			}

			for (Entity entity : p.level().getEntitiesOfClass(Entity.class, area(), FoesClientSteps::leftBehind)) {
				entity.discard();
			}
		}
	}

	private static boolean leftBehind(Entity e) {
		return e.entityTags().contains(TAG) || e instanceof BlasterBolt || e instanceof HunterRocket || e instanceof HunterNet || e instanceof CampMine
				|| e instanceof Rattle || e instanceof MandalorianHunter hunter && !(hunter instanceof TorvinKaast) && hunter.isFightSpawn();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/**
	 * Back to the spot {@code dz} south of the stage's: actors gone, health and energy full, cooldowns
	 * clear, the camera as asked, looking south and {@code pitch} down.
	 */
	private static void stand(ClientGameTestContext context, TestServerContext server, Stage stage, int dz, CameraType camera, float pitch) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			stage.removeActors(p);
			Vec3 at = Vec3.atBottomCenterOf(stage.at(0, dz));
			p.teleportTo(at.x, at.y, at.z);
			p.setHealth(p.getMaxHealth());
			refill(p);
		});
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		context.getInput().lookAt(0f, pitch);
		context.waitTicks(10);
	}

	private static void refill(ServerPlayer p) {
		ForceData data = Progression.get(p);
		data.energy = data.maxEnergy;
		Progression.commit(p, data);

		for (String id : List.of(ForceSpeed.ID, ForceBarrier.ID, ForceChoke.ID)) {
			Progression.setCooldown(p, id, 0);
		}
	}

	/** Presses a power's key on the server's side, as the key's packet would. */
	private static void use(ServerPlayer p, String id, AbilityAction action) {
		ForceAbility ability = Abilities.get(id);

		if (ability == null) {
			throw new AssertionError("Power " + id + " is not registered");
		}

		ability.onInput(p, action);
	}

	private static void check(boolean ok, String failure) {
		if (!ok) {
			throw new AssertionError(failure);
		}
	}

	/** The server's entity {@code id} near the player, as {@code type}. */
	private static <T extends Entity> T entity(MinecraftServer s, int id, Class<T> type) {
		Entity entity = player(s).level().getEntity(id);

		if (!type.isInstance(entity)) {
			throw new AssertionError("Entity " + id + " is gone, or is not a " + type.getSimpleName());
		}

		return type.cast(entity);
	}

	/** Whether the client sees entity {@code id} as a {@code type} that passes {@code test}. */
	private static <T extends Entity> boolean seen(Minecraft mc, int id, Class<T> type, Predicate<T> test) {
		Entity entity = mc.level == null ? null : mc.level.getEntity(id);
		return type.isInstance(entity) && test.test(type.cast(entity));
	}

	/** Yaw and pitch from {@code eye} to {@code target}. */
	private static float[] angles(Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90f;
		float pitch = (float) -(Math.atan2(d.y, d.horizontalDistance()) * (180.0 / Math.PI));
		return new float[] {yaw, pitch};
	}

	/** Turns the player's view to entity {@code id}'s chest (the server learns the turn on the next tick). */
	private static void aim(ClientGameTestContext context, TestServerContext server, int id) {
		float[] view = server.computeOnServer(s -> angles(player(s).getEyePosition(), ForcePowers.chest(entity(s, id, Entity.class))));
		context.getInput().lookAt(view[0], view[1]);
		context.waitTicks(3);
	}

	/** Places {@code mob} (see {@link #place}) and adds it to the level. */
	private static <T extends Mob> T add(ServerLevel level, @Nullable T mob, BlockPos at, float yaw) {
		if (mob == null) {
			throw new AssertionError("A foe could not be created at " + at);
		}

		place(mob, at, yaw);

		if (!level.addFreshEntity(mob)) {
			throw new AssertionError("A foe could not be added at " + at);
		}

		return mob;
	}

	/** Puts {@code mob} at the middle of {@code at} facing {@code yaw}, tagged as the steps' own and kept from despawning. */
	private static void place(Mob mob, BlockPos at, float yaw) {
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.addTag(TAG);
		mob.setPersistenceRequired();
	}

	/** A Mandalorian hunter at {@code at} facing north (toward the spot), without AI when {@code still}. */
	private static MandalorianHunter hunter(ServerLevel level, BlockPos at, boolean still) {
		MandalorianHunter hunter = HunterEntities.MANDALORIAN_HUNTER.create(level, EntitySpawnReason.EVENT);

		if (hunter != null) {
			hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			hunter.setNoAi(still);
		}

		return add(level, hunter, at, 180f);
	}

	// ---------------------------------------------------------------- 60 to 62: the powers

	/** 60: Force Speed cast, then a sprint south, away from the camera: dust from each footfall and the bent-air streaks. */
	private static void speed(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, -4, CameraType.THIRD_PERSON_BACK, 10f);
		double from = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			use(p, ForceSpeed.ID, AbilityAction.START);
			check(ForceSpeed.isRunning(p), "Force Speed did not start: screenshot 60 would show a plain walk");
			return p.getZ();
		});
		context.getInput().holdKey(FORWARD);
		context.getInput().holdKey(SPRINT);

		try {
			context.waitTicks(RUN_TICKS);
			context.takeScreenshot("oldrepublic_m4_60_speed_dust_trail");
		} finally {
			context.getInput().releaseKey(SPRINT);
			context.getInput().releaseKey(FORWARD);
		}

		boolean ran = server.computeOnServer(s -> ForceSpeed.isRunning(player(s)) && player(s).getZ() - from > 2.0);
		check(ran, "The Force Speed run did not carry the player forward: screenshot 60 would show no trail");
		server.runOnServer(s -> ForceM4Feature.forgetAll(player(s), false));
		context.waitTicks(10);
	}

	/** 61: Barrier held, a still hunter 8 blocks ahead fires two bolts; the first stops on the barrier, the second is in flight. */
	private static void barrier(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.THIRD_PERSON_BACK, 5f);
		int shooter = server.computeOnServer(s -> hunter(player(s).level(), stage.at(0, SHOOTER_AHEAD), true).getId());
		// Tracked on the client before the bolts fly.
		context.waitTicks(10);
		float health = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			use(p, ForceBarrier.ID, AbilityAction.START);
			check(ForceBarrier.isChannelling(p), "Force Barrier did not go up: screenshot 61 would show bolts hitting the player");
			return p.getHealth();
		});
		context.waitTicks(6);
		List<Integer> bolts = new ArrayList<>();

		for (int i = 0; i < 2; i++) {
			bolts.add(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				HunterBalance b = HunterBalance.get();
				return entity(s, shooter, MandalorianHunter.class)
						.fireBolt(p.level(), p, b.rifleDamage, b.boltSpeed, b.boltLifetimeTicks, 0f, 0f, 0f, ModSounds.BLASTER_FIRE).getId();
			}));
			context.waitTicks(BOLT_GAP);
		}

		context.takeScreenshot("oldrepublic_m4_61_barrier_stopping_bolts");
		context.waitTicks(6);
		boolean held = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean stopped = bolts.stream().allMatch(id -> p.level().getEntity(id) == null);
			boolean up = ForceBarrier.isChannelling(p);
			use(p, ForceBarrier.ID, AbilityAction.RELEASE);
			return stopped && up && p.getHealth() >= health;
		});
		check(held, "The Barrier did not stop both bolts unhurt: screenshot 61 would show the wrong thing");
	}

	/** 62: a hunter 6 blocks ahead, its AI back on, caught in Choke and lifted; from behind the caster. */
	private static void choke(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.THIRD_PERSON_BACK, 5f);
		int id = server.computeOnServer(s -> hunter(player(s).level(), stage.at(0, CHOKE_AHEAD), true).getId());
		context.waitTicks(10);
		// Choke's cone is narrow: the view goes onto the hunter's chest, as a player aims it.
		aim(context, server, id);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			MandalorianHunter hunter = entity(s, id, MandalorianHunter.class);
			hunter.setNoAi(false);
			use(p, ForceChoke.ID, AbilityAction.START);
			check(ForceChoke.isChoking(p) && ChokedTargets.isChoked(hunter), "Force Choke did not take the hunter: screenshot 62 would show a free hunter");
		});
		context.waitTicks(CHOKE_LIFT_TICKS);
		context.takeScreenshot("oldrepublic_m4_62_choke_on_hunter");
		boolean held = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean choked = ChokedTargets.isChoked(entity(s, id, MandalorianHunter.class));
			use(p, ForceChoke.ID, AbilityAction.RELEASE);
			return choked;
		});
		check(held, "The hunter was out of Choke by the time of screenshot 62");
	}

	// ---------------------------------------------------------------- 63 to 66: the foes

	/** 63: a hunter penned 3 blocks ahead, its AI on and the player its target: the flamer's tell (wrist up, pilot flame). */
	private static void flamerTell(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 15f);
		int id = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			pen(level, stage, Blocks.STONE_BRICKS.defaultBlockState());
			return hunter(level, stage.at(0, FLAMER_AHEAD), true).getId();
		});
		context.waitTicks(10);
		aim(context, server, id);

		try {
			server.runOnServer(s -> {
				MandalorianHunter hunter = entity(s, id, MandalorianHunter.class);
				hunter.setNoAi(false);
				hunter.setTarget(player(s));
			});
			context.waitFor(mc -> seen(mc, id, MandalorianHunter.class, h -> h.gadget() == MandalorianHunter.GADGET_FLAMER), TELL_TIMEOUT);
			context.waitTicks(4);
			context.takeScreenshot("oldrepublic_m4_63_hunter_flamer_tell");
			boolean telling = server.computeOnServer(s -> {
				MandalorianHunter hunter = entity(s, id, MandalorianHunter.class);
				return hunter.gadget() == MandalorianHunter.GADGET_FLAMER || hunter.isFlaming();
			});
			check(telling, "The hunter was not in its flamer tell at screenshot 63");
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				// Gone before the tell ends, so the flame never lights.
				stage.removeActors(p);
				pen(p.level(), stage, Blocks.AIR.defaultBlockState());
			});
		}
	}

	/** The flamer hunter's pen, two blocks high: a back wall and two side walls round its spot, open toward the player. */
	private static void pen(ServerLevel level, Stage stage, BlockState state) {
		for (int up = 0; up < 2; up++) {
			for (int dz = FLAMER_AHEAD - 1; dz <= FLAMER_AHEAD + 1; dz++) {
				level.setBlockAndUpdate(stage.at(-1, dz).above(up), state);
				level.setBlockAndUpdate(stage.at(1, dz).above(up), state);
			}

			level.setBlockAndUpdate(stage.at(0, FLAMER_AHEAD + 1).above(up), state);
		}
	}

	/** 64: a still security droid 5 blocks ahead, hit to just under half health (the plate comes up), then a bolt at its front. */
	private static void droidShield(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 10f);
		int id = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockPos at = stage.at(0, DROID_AHEAD);

			if (!(Droids.spawnSecurity(level, at, null, false) instanceof SecurityDroid droid)) {
				throw new AssertionError("The security droid could not spawn");
			}

			// Turned to face the player, and still.
			place(droid, at, 180f);
			droid.setNoAi(true);
			return droid.getId();
		});
		context.waitTicks(10);
		aim(context, server, id);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			SecurityDroid droid = entity(s, id, SecurityDroid.class);
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the droid's health is set just above half before a hit");
			droid.setHealth(droid.getMaxHealth() * DroidBalance.get().shieldAt + 1f);
			droid.invulnerableTime = 0;
			droid.hurtServer(level, level.damageSources().magic(), 2f);
			check(droid.isShielded(), "The droid's shield did not come up under half health: screenshot 64 would show no plate");
		});
		context.waitTicks(4);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			SecurityDroid droid = entity(s, id, SecurityDroid.class);
			Vec3 chest = ForcePowers.chest(droid);
			Vec3 from = chest.add(0, 0, -3);
			BlasterBolt bolt = BlasterBolt.create(level, null, from, 4f, 20, BlasterBolt.Style.REPUBLIC, false);
			bolt.shoot(chest.x - from.x, chest.y - from.y, chest.z - from.z, 1.2f, 0f);
			level.addFreshEntity(bolt);
		});
		context.waitTicks(3);
		context.takeScreenshot("oldrepublic_m4_64_droid_shield");
		check(server.computeOnServer(s -> entity(s, id, SecurityDroid.class).isShielded()), "The droid's shield was down by screenshot 64");
	}

	/** 65: a grown hollowhorn 10 blocks ahead, provoked: its head-down snort and hoof scrape before the charge. */
	private static void hollowhornTell(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 8f);
		int id = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			Hollowhorn horn = CreatureEntities.HOLLOWHORN.create(level, EntitySpawnReason.EVENT);

			if (horn != null) {
				horn.setCalf(false);
			}

			return add(level, horn, stage.at(0, HOLLOWHORN_AHEAD), 180f).getId();
		});
		context.waitTicks(10);
		aim(context, server, id);
		server.runOnServer(s -> entity(s, id, Hollowhorn.class).angerHerd(player(s).level(), player(s), true));
		context.waitFor(mc -> seen(mc, id, Hollowhorn.class, h -> h.state() == Hollowhorn.STATE_TELL), TELL_TIMEOUT);
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m4_65_hollowhorn_charge_tell");
		int state = server.computeOnServer(s -> entity(s, id, Hollowhorn.class).state());
		check(state == Hollowhorn.STATE_TELL, "The hollowhorn was out of its charge tell at screenshot 65 (state " + state + ")");
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	/** 66: three ash hounds 8 to 10 blocks ahead hunting the player; the picture is taken as one leaps, the view turned onto it. */
	private static void houndLunge(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 10f);
		int[] pack = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			int[][] spots = {{-3, 8}, {0, 10}, {3, 8}};
			int[] ids = new int[spots.length];

			for (int i = 0; i < spots.length; i++) {
				AshHound hound = add(level, CreatureEntities.ASH_HOUND.create(level, EntitySpawnReason.EVENT), stage.at(spots[i][0], spots[i][1]), 180f);
				hound.setTarget(p);
				ids[i] = hound.getId();
			}

			return ids;
		});
		int[] leaper = {-1};
		context.waitFor(mc -> {
			for (int id : pack) {
				if (seen(mc, id, AshHound.class, h -> h.state() == AshHound.STATE_LEAP)) {
					leaper[0] = id;
					return true;
				}
			}

			return false;
		}, LUNGE_TIMEOUT);
		float[] view = context.computeOnClient(mc -> {
			Entity hound = mc.level == null ? null : mc.level.getEntity(leaper[0]);
			return hound == null || mc.player == null ? new float[] {0f, 10f} : angles(mc.player.getEyePosition(), ForcePowers.chest(hound));
		});
		context.getInput().lookAt(view[0], view[1]);
		// One frame with the turned view; the leap lasts longer.
		context.waitTicks(1);
		context.takeScreenshot("oldrepublic_m4_66_ash_hound_lunge");
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	// ---------------------------------------------------------------- 67 to 69: Torvin Kaast

	/**
	 * 67 to 69: Kaast's fight in a camp on the stage (the pit 12 blocks ahead, a nest pillar 19 ahead,
	 * four mine spots, no gate, the player 4 blocks inside the ring). Phase 1 on the nest with Rattle;
	 * {@code /oldrepublic kaast phase @p 2} (Rattle flies, he comes down); {@code phase @p 3} (the axe,
	 * Rattle back; the hunters he calls are sent away so the picture is his); then a blow past his
	 * yield floor once the transition is over, and {@code /oldrepublic kaast reset @p}.
	 */
	private static void kaast(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, KAAST_SPOT, CameraType.FIRST_PERSON, 0f);
		int id = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			KaastFight.reset(p);

			for (int up = 0; up < NEST_HEIGHT; up++) {
				level.setBlockAndUpdate(stage.at(0, NEST_AHEAD).above(up), Blocks.STONE_BRICKS.defaultBlockState());
			}

			BlockPos heart = stage.at(0, HEART_AHEAD);
			List<BlockPos> mines = List.of(heart.offset(5, 0, 5), heart.offset(-5, 0, 5), heart.offset(5, 0, -5), heart.offset(-5, 0, -5));
			CampLayout layout = new CampLayout(heart, List.of(stage.at(0, NEST_AHEAD).above(NEST_HEIGHT)), mines, List.of(), RING);
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, Kaast's fight starts at once in a camp built on the stage (KaastFight.begin)");
			KaastSession session = KaastFight.begin(p.getUUID(), level, layout, false, false);
			TorvinKaast boss = session == null ? null : session.kaast();

			if (session == null || boss == null || !session.isParticipant(p.getUUID())) {
				throw new AssertionError("Kaast's fight did not begin with the player listed");
			}

			return boss.getId();
		});

		try {
			context.waitTicks(PHASE1_TICKS);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m4_67_kaast_phase1");
			checkPhase(server, 1, "screenshot 67 is not phase 1 with Rattle", session -> session.rattle() != null);
			heal(server);

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, /oldrepublic kaast phase @p 2");
			server.runCommand("oldrepublic kaast phase @p 2");
			context.waitTicks(PHASE_TICKS);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m4_68_kaast_phase2");
			checkPhase(server, 2, "screenshot 68 is not phase 2 with Rattle gone", session -> session.rattle() == null);
			heal(server);

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, /oldrepublic kaast phase @p 3; the hunters he calls are sent away");
			server.runCommand("oldrepublic kaast phase @p 3");
			server.runOnServer(s -> {
				for (Entity called : player(s).level().getEntitiesOfClass(MandalorianHunter.class, stage.area(),
						h -> !(h instanceof TorvinKaast) && h.isFightSpawn())) {
					called.discard();
				}
			});
			context.waitTicks(PHASE_TICKS);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m4_69_kaast_phase3");
			checkPhase(server, 3, "screenshot 69 (phase 3) has no axe drawn", session -> session.kaast() != null && session.kaast().isAxeDrawn());
			heal(server);

			waitOutTransition(context, server);
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, one blow past Kaast's yield floor");
			server.runOnServer(s -> {
				ServerLevel level = player(s).level();
				TorvinKaast boss = entity(s, id, TorvinKaast.class);
				boss.invulnerableTime = 0;
				boss.hurtServer(level, level.damageSources().magic(), boss.getMaxHealth() * 2f);
				KaastSession session = KaastFight.session(player(s).getUUID());
				check(session != null && session.isYielded() && boss.isKnelt(), "Kaast did not yield to the blow: no yield for screenshot 69");
			});
			context.waitTicks(YIELD_TICKS);
			// A Chapter Four duel would open the choice here; the picture is of him, so it is closed.
			server.runOnServer(s -> Dialogues.close(player(s)));
			context.setScreen(() -> null);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m4_69_kaast_yield");
		} finally {
			server.runCommand("oldrepublic kaast reset @p");
		}

		boolean reset = server.computeOnServer(s -> KaastFight.session(player(s).getUUID()) == null && player(s).level().getEntity(id) == null);
		check(reset, "/oldrepublic kaast reset left the fight or Kaast behind");
	}

	/** Fails with {@code failure} unless the player's fight is in {@code phase} and passes {@code test}. */
	private static void checkPhase(TestServerContext server, int phase, String failure, Predicate<KaastSession> test) {
		boolean ok = server.computeOnServer(s -> {
			KaastSession session = KaastFight.session(player(s).getUUID());
			return session != null && session.phase() == phase && session.kaast() != null && session.kaast().phase() == phase && test.test(session);
		});
		check(ok, failure);
	}

	/** Kaast takes no damage while a phase begins; waits until his phase 3 transition is over. */
	private static void waitOutTransition(ClientGameTestContext context, TestServerContext server) {
		for (int tick = 0; tick < TRANSITION_TIMEOUT; tick++) {
			boolean over = server.computeOnServer(s -> {
				KaastSession session = KaastFight.session(player(s).getUUID());
				return session == null || !session.isTransitioning();
			});

			if (over) {
				return;
			}

			context.waitTicks(1);
		}

		throw new AssertionError("Kaast's phase 3 transition did not end");
	}

	/** Keeps the player in the fight: his rifle and axe still hurt through the driver's Resistance. */
	private static void heal(TestServerContext server) {
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
	}

	private FoesClientSteps() {
	}
}

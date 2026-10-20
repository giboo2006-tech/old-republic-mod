package com.oldrepublic.gametest.m5;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.BossEntities;
import com.oldrepublic.boss.CollectorHooks;
import com.oldrepublic.boss.cantor.CantorChoir;
import com.oldrepublic.boss.cantor.CantorEntities;
import com.oldrepublic.boss.cantor.CantorSession;
import com.oldrepublic.boss.cantor.CantorVerses;
import com.oldrepublic.boss.cantor.FirstCantor;
import com.oldrepublic.boss.cantor.LongHymn;
import com.oldrepublic.boss.kaast.KaastEntities;
import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidRules;
import com.oldrepublic.boss.raid.RaidSession;
import com.oldrepublic.boss.raid.UndercroftLayout;
import com.oldrepublic.boss.ulvaine.DarthUlvaine;
import com.oldrepublic.boss.ulvaine.HeartLayout;
import com.oldrepublic.boss.ulvaine.HymnResonator;
import com.oldrepublic.boss.ulvaine.UlvaineBalance;
import com.oldrepublic.boss.ulvaine.UlvaineEntities;
import com.oldrepublic.boss.ulvaine.UlvaineFight;
import com.oldrepublic.boss.ulvaine.UlvaineRules;
import com.oldrepublic.boss.ulvaine.UlvaineSession;
import com.oldrepublic.client.boss.music.BossMusicService;
import com.oldrepublic.client.saber.SaberAnimations;
import com.oldrepublic.client.saber.SaberPose;
import com.oldrepublic.enemy.cartel.CartelEnforcer;
import com.oldrepublic.enemy.cartel.CartelEnforcers;
import com.oldrepublic.enemy.hask.HaskWarrior;
import com.oldrepublic.enemy.hask.HaskWarriors;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.enemy.hymn.Hymnsworns;
import com.oldrepublic.enemy.scour.SandScour;
import com.oldrepublic.enemy.scour.Scours;
import com.oldrepublic.enemy.stormback.Stormback;
import com.oldrepublic.enemy.stormback.Stormbacks;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.ModSounds;

import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for the Milestone 5 foes, Ulvaine and the Long Hymn (W8). {@code M5ClientTest} runs them
 * in the shared seeded world (spec MILESTONE_5.md 19.2). They begin with {@code bossMusicServiceRegistry}: the client's
 * {@code BossMusicService} holds every boss id with its loops in track order (the Collector's three, Kaast's three, one
 * each for Ulvaine and the First Cantor) and the entity type its watchdog looks for, and an id nobody registered has none.
 * Screenshots are {@code oldrepublic_m5_<nnn>_<what>}, 185 to 199:
 * <ul>
 * <li>185 {@code cartel_bruiser_windup}: a Vessba bruiser drawing his vibrosword back in the {@code npc_windup} tell
 * before his first cut, close in front;</li>
 * <li>186 {@code hask_jet_slam_tell}: a Clan Hask blade warrior crouched, its jet pack whining, before the jet-slam;</li>
 * <li>187 {@code hymnsworn_storm_call_tell}: a storm-caller with its arms up and static crawling over the patch where the
 * player stands, from behind the player;</li>
 * <li>188 {@code scour_burst_tell}: the sand boiling over a burrowed sand scour in front of the player; 189
 * {@code scour_burst}: the scour erupting out of it;</li>
 * <li>190 {@code stormback_charge_tell}: a stormback's head lowered, pawing grit, before its charge;</li>
 * <li>191 {@code stormback_charged}: a stormback charged by the storm, its plates crackling;</li>
 * <li>192 {@code ulvaine_phase1}: Darth Ulvaine in the duel; 193 {@code ulvaine_phase2}: the storm, her storm pose and a
 * chanter at each pylon; 194 {@code ulvaine_phase3}: the Hymn with the Crescendo's four resonators lit; 195
 * {@code ulvaine_yield}: kneeling;</li>
 * <li>196 {@code cantor_phase1_choir}: the First Cantor shielded by his four choir echoes; 197 {@code cantor_phase2_verses}:
 * the basalt verse's marks under the raiders; 198 {@code cantor_phase3_hymn}: a floor ring singing. Each with four
 * raiders, from behind the player;</li>
 * <li>199 {@code cantor_wipe}: the undercroft after a wipe, from where the last raider fled: the other three fallen, the
 * First Cantor and his choir gone.</li>
 * </ul>
 *
 * <p>The steps build a stone stage 40 blocks above wherever the player stands, in the player's own level, with a rail two
 * blocks high round its edge, and stand the player on it facing south. The foes spawn through their own spawn calls (the
 * spawner groups' and hubs'), turned to face the player. Shortcuts, each logged: the bruiser and the Hask warrior are set
 * on the player as a fight they started (an engagement); the Hask warrior is held where it stands (its speed times zero),
 * so it tells its jet-slam from 8 blocks instead of walking in; the scour hunts over a patch of smooth sandstone laid on
 * the stage; the player's blow that provokes the stormback is dealt on the server; the charged stormback stands without
 * AI and is charged as a ridge strike would charge it, without the bolt. Ulvaine's fight starts at once in a Heart laid
 * out on the stage ({@code UlvaineFight.begin}), its hooks recorded instead of reaching the story, so no choice opens;
 * phases 2 and 3 come through {@code UlvaineFight.phase} (as {@code /oldrepublic ulvaine phase}), the pylon chanters are
 * sent away in phase 3 (the players would have killed them in the storm), and the Crescendo and the yield each come from
 * one blow. The Long Hymn starts at once in an undercroft laid out on the stage ({@code LongHymn.start}, no countdown); the
 * other three raiders are mock players with no client (as {@code GameTestHelper#makeMockServerPlayerInLevel} makes them),
 * whose join lines are waited out before the Hymn; phases 2 and 3 come through {@code forcePhase} with the transitions
 * skipped and the basalt verse sung at once (as {@code /oldrepublic raid phase} and {@code verse}); for the wipe the three
 * fall (their health set to 0: they stay listed, as on the death screen) and the player flees the undercroft, and the
 * Hymn then stops by its own rule, 200 ticks with no raider alive inside. Difficulty is raised from Peaceful to Normal for
 * the steps when it was Peaceful, and the player is shielded for the steps (Resistance V, its particles and icon hidden):
 * the fights' blows still land, knock back and are told, but none can kill the player in the middle of a picture (an
 * invulnerable player would not do: no foe would take them as a target). The server confirms each step did what its
 * screenshot claims, and the client that the
 * fights' music was asked for and stopped. Everything is put back at the end: the fights, the foes, the mock raiders
 * (their leave lines waited out, so the next steps' pictures are clean), the stage itself, the player's Force and story
 * data, health, camera and position.
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
	private static final String TAG = "or_m5_foes";
	/** A foe turned to face the player, who looks south from the spot: north. */
	private static final float FACING_PLAYER = 180f;
	/** An engagement long enough for any tell to come. */
	private static final int ENGAGE_TICKS = 400;
	/** The modifier that holds the jet-slam's warrior where it stands. */
	private static final Identifier HOLD = OldRepublicMod.id("m5_foes_steps_hold");
	/** A boss id nobody registers music for. */
	private static final String UNKNOWN_BOSS = "oldrepublic_m5_no_such_boss";
	/** The player's shield: Resistance V (no damage gets through), long enough for every step. */
	private static final int SHIELD_AMPLIFIER = 4;
	private static final int SHIELD_TICKS = 20 * 60 * 20;

	/** How far ahead (south) of the spot each foe starts. */
	private static final int BRUISER_AHEAD = 3;
	private static final int HASK_AHEAD = 8;
	private static final int CALLER_AHEAD = 10;
	private static final int SCOUR_AHEAD = 7;
	private static final int STORMBACK_AHEAD = 10;
	private static final int CHARGED_AHEAD = 6;
	/** The sandstone patch the scour hunts over: this many blocks either side of the spot, from 1 to 9 ahead. */
	private static final int SAND_HALF_WIDTH = 4;
	private static final int SAND_NEAR = 1;
	private static final int SAND_FAR = 9;

	/** The Heart on the stage: her spot 12 ahead, the hall's radius, and the player's spot 3 ahead (9 blocks inside). */
	private static final int HEART_AHEAD = 12;
	private static final double HEART_RADIUS = 12.0;
	private static final int DUEL_SPOT = 3;
	/** The Crescendo's blow leaves her this far under its mark (a fraction of her health), well above her yield floor. */
	private static final float CRESCENDO_MARGIN = 0.05f;

	/** The undercroft on the stage: the First Cantor's spot 13 ahead, the player's spot 1 ahead, and where the player flees to. */
	private static final int CANTOR_AHEAD = 13;
	private static final int RAID_SPOT = 1;
	private static final int FLED_SPOT = -6;
	/** The other three raiders: (east, south) of the spot, and their names. */
	private static final int[][] RAIDER_SPOTS = {{-3, 3}, {3, 3}, {-6, 5}};
	private static final String[] RAIDER_NAMES = {"Hymn_Raider_2", "Hymn_Raider_3", "Hymn_Raider_4"};
	private static final int RAIDERS = RAIDER_SPOTS.length + 1;

	/** The ticks the client gets to see an entity or a change, a tell to come, the music to be asked for, before the step fails. */
	private static final int SEEN_TIMEOUT = 60;
	private static final int TELL_TIMEOUT = 200;
	private static final int BURST_TIMEOUT = 40;
	private static final int MUSIC_TIMEOUT = 40;
	/** The ticks the server gets for a phase transition, the First Cantor's rise and the wipe (200 ticks by rule), before the step fails. */
	private static final int TRANSITION_TIMEOUT = 120;
	private static final int RISE_TIMEOUT = 120;
	private static final int WIPE_TIMEOUT = 360;
	/** A chat line fades 200 ticks after it arrives: the raiders' join and leave lines are waited out this long. */
	private static final int CHAT_FADE_TICKS = 210;

	/** Ticks into each tell or state before its picture. */
	private static final int WINDUP_SHOW = 4;
	private static final int SLAM_SHOW = 6;
	private static final int CALL_SHOW = 12;
	private static final int BURST_TELL_SHOW = 4;
	private static final int BURST_SHOW = 1;
	private static final int CHARGE_TELL_SHOW = 6;
	private static final int CHARGED_SHOW = 10;
	/** Ticks into each of Ulvaine's phases, after the Crescendo begins and after the yielding blow, before the picture. */
	private static final int PHASE1_TICKS = 40;
	private static final int PHASE_TICKS = 30;
	private static final int CRESCENDO_TICKS = 20;
	private static final int YIELD_TICKS = 25;
	/** Ticks after the First Cantor's rise, the basalt verse and the Hymn's start before each picture; after the wipe before its. */
	private static final int RISEN_TICKS = 20;
	private static final int VERSE_TICKS = 20;
	private static final int HYMN_TICKS = 40;
	private static final int WIPE_SHOW = 4;

	/** What the steps change on the player, put back at the end. */
	private record Before(Vec3 position, float yaw, float pitch, ForceData force, StoryData story, boolean peaceful,
			@Nullable MobEffectInstance resistance) {
	}

	/** Runs the foes steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		bossMusicServiceRegistry(context);
		Before before = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			MobEffectInstance resistance = p.getEffect(MobEffects.RESISTANCE);
			return new Before(p.position(), p.getYRot(), p.getXRot(), Progression.get(p).copy(), Story.get(p).copy(),
					p.level().getDifficulty() == Difficulty.PEACEFUL, resistance == null ? null : new MobEffectInstance(resistance));
		});
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());
		BlockPos start = BlockPos.containing(before.position());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());
		// The mock raiders while they are logged in, and the server tick the last of them left at (their leave lines fade from there).
		List<ServerPlayer> raiders = new ArrayList<>();
		int[] leftAt = {-1};

		if (before.peaceful()) {
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, difficulty Normal for the steps (Peaceful removes the foes and stops the fights); put back afterwards");
			server.runCommand("difficulty normal");
		}

		OldRepublicMod.LOGGER.info("Foes client step: shortcut, the player is shielded for the steps (Resistance V, hidden) so no blow kills them"
				+ " mid-picture; put back afterwards");
		server.runOnServer(s -> stage.build(player(s).level()));

		try {
			bruiserWindup(context, server, stage);
			haskSlamTell(context, server, stage);
			stormCallTell(context, server, stage);
			scourBurst(context, server, stage);
			stormbackTell(context, server, stage);
			stormbackCharged(context, server, stage);
			ulvaine(context, server, stage);
			cantor(context, server, stage, raiders, leftAt);
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				UlvaineFight.reset(p);
				CantorSession hymn = LongHymn.sessionIn(p.level(), undercroft(stage));

				if (hymn != null) {
					hymn.stop(true);
				}

				if (!raiders.isEmpty()) {
					leftAt[0] = s.getTickCount();
				}

				logOut(s, raiders);
				stage.removeActors(p);
				stage.clear(p.level());
				Progression.commit(p, before.force());
				Story.commit(p, before.story());
				p.removeEffect(MobEffects.SLOWNESS);
				p.removeEffect(MobEffects.RESISTANCE);

				if (before.resistance() != null) {
					p.addEffect(new MobEffectInstance(before.resistance()));
				}

				p.setHealth(p.getMaxHealth());
				p.teleportTo(before.position().x, before.position().y, before.position().z);
			});
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(camera));
			context.getInput().lookAt(before.yaw(), before.pitch());

			if (before.peaceful()) {
				server.runCommand("difficulty peaceful");
			}

			context.waitTicks(10);

			if (leftAt[0] >= 0) {
				int since = server.computeOnServer(MinecraftServer::getTickCount) - leftAt[0];

				if (since < CHAT_FADE_TICKS) {
					context.waitTicks(CHAT_FADE_TICKS - since);
				}
			}
		}

		boolean quiet = context.computeOnClient(mc -> BossMusicService.wanted() == null);
		check(quiet, "Boss music was still asked for after the foes steps put everything back");
	}

	// ---------------------------------------------------------------- the boss music registry

	/**
	 * {@code bossMusicServiceRegistry}: every boss id is registered with its loops in track order and the entity type its
	 * watchdog looks for; an id nobody registered has no loops and no type.
	 */
	private static void bossMusicServiceRegistry(ClientGameTestContext context) {
		String problems = context.computeOnClient(mc -> {
			List<String> out = new ArrayList<>();
			expectMusic(out, BossMusicPayload.COLLECTOR, BossEntities.ORREN_VASCH,
					List.of(ModSounds.MUSIC_COLLECTOR_ARCHIVIST, ModSounds.MUSIC_COLLECTOR_SINGING_WALL, ModSounds.MUSIC_COLLECTOR_UNKEPT));
			expectMusic(out, BossMusicPayload.KAAST, KaastEntities.TORVIN_KAAST,
					List.of(ModSounds.MUSIC_KAAST_HUNT, ModSounds.MUSIC_KAAST_TRAP, ModSounds.MUSIC_KAAST_LAST_STAND));
			expectMusic(out, BossMusicPayload.ULVAINE, UlvaineEntities.DARTH_ULVAINE, List.of(ModSounds.MUSIC_BOSS_ULVAINE));
			expectMusic(out, BossMusicPayload.CANTOR, CantorEntities.FIRST_CANTOR, List.of(ModSounds.MUSIC_BOSS_CANTOR));

			if (BossMusicService.isRegistered(UNKNOWN_BOSS) || !BossMusicService.tracks(UNKNOWN_BOSS).isEmpty()
					|| BossMusicService.bossType(UNKNOWN_BOSS) != null) {
				out.add("an id nobody registered answers as registered");
			}

			return String.join("; ", out);
		});
		check(problems.isEmpty(), "BossMusicService's registry is wrong: " + problems);
	}

	private static void expectMusic(List<String> problems, String bossId, EntityType<?> boss, List<SoundEvent> tracks) {
		if (!BossMusicService.isRegistered(bossId)) {
			problems.add(bossId + " is not registered");
			return;
		}

		if (!BossMusicService.tracks(bossId).equals(tracks)) {
			problems.add(bossId + " plays " + BossMusicService.tracks(bossId) + ", not " + tracks);
		}

		if (BossMusicService.bossType(bossId) != boss) {
			problems.add(bossId + "'s watchdog looks for " + BossMusicService.bossType(bossId) + ", not " + boss);
		}
	}

	// ---------------------------------------------------------------- the stage

	/** The stage: a smooth stone floor at {@code y - 1} round the spot {@code (x, y, z)}, air above, a rail two blocks high round its edge. */
	private record Stage(int x, int y, int z) {
		void build(ServerLevel level) {
			fill(level, true);
		}

		void clear(ServerLevel level) {
			fill(level, false);
		}

		private void fill(ServerLevel level, boolean build) {
			BlockState air = Blocks.AIR.defaultBlockState();
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

		/** Everything near the stage that the steps may leave. */
		AABB area() {
			return new AABB(x - HALF_WIDTH, y - 2, z - BACK, x + HALF_WIDTH + 1, y + HEADROOM + 1, z + FRONT + 1).inflate(24.0);
		}

		/** Removes what the steps spawned: their own foes, and the fights' chanters, shades, resonators and choir. */
		void removeActors(ServerPlayer p) {
			for (Entity entity : p.level().getEntitiesOfClass(Entity.class, area(), FoesClientSteps::leftBehind)) {
				entity.discard();
			}
		}
	}

	private static boolean leftBehind(Entity e) {
		return e.entityTags().contains(TAG) || e instanceof CartelEnforcer || e instanceof HaskWarrior || e instanceof Hymnsworn
				|| e instanceof SandScour || e instanceof Stormback || e instanceof DarthUlvaine || e instanceof HymnResonator
				|| e instanceof FirstCantor || e instanceof CantorChoir;
	}

	/** Lays {@code state} as the floor of the scour's patch. */
	private static void sand(ServerLevel level, Stage stage, BlockState state) {
		for (int dx = -SAND_HALF_WIDTH; dx <= SAND_HALF_WIDTH; dx++) {
			for (int dz = SAND_NEAR; dz <= SAND_FAR; dz++) {
				level.setBlock(stage.at(dx, dz).below(), state, Block.UPDATE_CLIENTS);
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		// The test's own player joined first; the mock raiders, while there are any, come after it.
		return server.getPlayerList().getPlayers().getFirst();
	}

	/**
	 * Back to the spot {@code dz} south of the stage's: actors gone, health full, no slowing, shielded, the camera as asked,
	 * looking south and {@code pitch} down.
	 */
	private static void stand(ClientGameTestContext context, TestServerContext server, Stage stage, int dz, CameraType camera, float pitch) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			stage.removeActors(p);
			put(p, stage, dz);
		});
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		context.getInput().lookAt(0f, pitch);
		context.waitTicks(10);
	}

	/** In a fight, before a picture: the player back on their spot (a blow may have knocked them off it), healed, not slowed, shielded. */
	private static void back(ClientGameTestContext context, TestServerContext server, Stage stage, int dz) {
		server.runOnServer(s -> put(player(s), stage, dz));
		context.waitTicks(2);
	}

	private static void put(ServerPlayer p, Stage stage, int dz) {
		Vec3 at = Vec3.atBottomCenterOf(stage.at(0, dz));
		p.teleportTo(at.x, at.y, at.z);
		p.removeEffect(MobEffects.SLOWNESS);
		p.setHealth(p.getMaxHealth());
		// Hidden: no swirl round the player and no icon in the corner of the pictures.
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, SHIELD_TICKS, SHIELD_AMPLIFIER, false, false, false));
	}

	private static void check(boolean ok, String failure) {
		if (!ok) {
			throw new AssertionError(failure);
		}
	}

	/** Waits a tick at a time until {@code done} holds on the server; fails with {@code failure} after {@code timeout} ticks. */
	private static void awaitServer(ClientGameTestContext context, TestServerContext server, Predicate<MinecraftServer> done, int timeout,
			String failure) {
		for (int tick = 0; tick <= timeout; tick++) {
			if (server.computeOnServer(done::test)) {
				return;
			}

			context.waitTicks(1);
		}

		throw new AssertionError(failure);
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

	/**
	 * A foe its spawn call made at {@code at}: put on the middle of the block facing the player, tagged as the steps' own and
	 * kept from despawning.
	 */
	private static <T extends Mob> T placed(@Nullable T mob, BlockPos at, String what) {
		if (mob == null) {
			throw new AssertionError(what + " could not spawn at " + at);
		}

		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, FACING_PLAYER, 0f);
		mob.setYHeadRot(FACING_PLAYER);
		mob.setYBodyRot(FACING_PLAYER);
		mob.addTag(TAG);
		mob.setPersistenceRequired();
		return mob;
	}

	/** A blow from {@code p} that no guard, parry or armour turns: indirect magic the player caused. */
	private static boolean blow(ServerPlayer p, LivingEntity target, float amount) {
		ServerLevel level = p.level();
		target.invulnerableTime = 0;
		return target.hurtServer(level, level.damageSources().source(DamageTypes.INDIRECT_MAGIC, null, p), amount);
	}

	// ---------------------------------------------------------------- 185 to 191: the foes

	/** 185: a Vessba bruiser 3 blocks ahead, set on the player, drawing his vibrosword back before his first cut. */
	private static void bruiserWindup(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 5f);
		int id = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos at = stage.at(0, BRUISER_AHEAD);
			CartelEnforcer bruiser = placed(CartelEnforcers.spawn(level, at, CartelEnforcer.Variant.BRUISER, null), at, "The bruiser");
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the bruiser is set on the player as a fight he started (an engagement)");
			bruiser.standing().engage(p.getUUID(), level.getGameTime() + ENGAGE_TICKS);
			bruiser.setTarget(p);
			return bruiser.getId();
		});
		// Tracked on the client, the view on him, before his string begins.
		context.waitTicks(4);
		aim(context, server, id);
		context.waitFor(mc -> seen(mc, id, CartelEnforcer.class, FoesClientSteps::windingUp), TELL_TIMEOUT);
		context.waitTicks(WINDUP_SHOW);
		context.takeScreenshot("oldrepublic_m5_185_cartel_bruiser_windup");
		boolean told = server.computeOnServer(s -> entity(s, id, CartelEnforcer.class).tellLog().tells() > 0);
		check(told, "The bruiser's tell log holds no tell: screenshot 185 would show no wind-up");
		// Gone before the shove that follows the cut.
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	/** Whether the client poses {@code bruiser} in the {@code npc_windup} tell. */
	private static boolean windingUp(CartelEnforcer bruiser) {
		SaberPose pose = SaberAnimations.pose(bruiser, 0f);
		return pose != null && pose.kind() == SaberAnimations.Kind.NPC_WINDUP;
	}

	/** 186: a Clan Hask blade warrior 8 blocks ahead, held where it stands, crouched in the jet-slam's tell. */
	private static void haskSlamTell(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 5f);
		int id = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos at = stage.at(0, HASK_AHEAD);
			HaskWarrior warrior = placed(HaskWarriors.spawn(level, at, HaskWarrior.Variant.BLADE, null), at, "The Hask warrior");
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the Hask warrior is set on the player (an engagement) and held where it stands"
					+ " (its speed times zero), so its jet-slam comes from 8 blocks");
			AttributeInstance speed = warrior.getAttribute(Attributes.MOVEMENT_SPEED);

			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}

			warrior.standing().engage(p.getUUID(), level.getGameTime() + ENGAGE_TICKS);
			warrior.setTarget(p);
			return warrior.getId();
		});
		context.waitTicks(4);
		aim(context, server, id);
		context.waitFor(mc -> seen(mc, id, HaskWarrior.class, w -> w.action() == HaskWarrior.ACTION_SLAM), TELL_TIMEOUT);
		context.waitTicks(SLAM_SHOW);
		context.takeScreenshot("oldrepublic_m5_186_hask_jet_slam_tell");
		boolean telling = server.computeOnServer(s -> entity(s, id, HaskWarrior.class).action() == HaskWarrior.ACTION_SLAM);
		check(telling, "The Hask warrior was out of its jet-slam tell at screenshot 186");
		// Gone before the tell ends, so the slam never flies.
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	/** 187: a storm-caller 10 blocks ahead, its storm call's tell on the patch where the player stands; from behind the player. */
	private static void stormCallTell(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.THIRD_PERSON_BACK, 20f);
		int id = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos at = stage.at(0, CALLER_AHEAD);
			Hymnsworn caller = placed(Hymnsworns.spawn(p.level(), at, Hymnsworn.Variant.CALLER, false, null), at, "The storm-caller");
			caller.setTarget(p);
			return caller.getId();
		});
		context.waitFor(mc -> seen(mc, id, Hymnsworn.class, h -> h.action() == Hymnsworn.ACTION_CALL), TELL_TIMEOUT);
		context.waitTicks(CALL_SHOW);
		context.takeScreenshot("oldrepublic_m5_187_hymnsworn_storm_call_tell");
		boolean calling = server.computeOnServer(s -> entity(s, id, Hymnsworn.class).action() == Hymnsworn.ACTION_CALL);
		check(calling, "The storm-caller was out of its call's tell at screenshot 187");
		// Gone before the strike.
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	/** 188 and 189: a burrowed scour 7 blocks ahead hunting the player over a patch of sandstone: its burst's tell, then the burst. */
	private static void scourBurst(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 30f);
		int id = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the scour hunts over a patch of smooth sandstone laid on the stage");
			sand(level, stage, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
			BlockPos at = stage.at(0, SCOUR_AHEAD);
			SandScour scour = placed(Scours.spawn(level, at, false, null), at, "The scour");
			check(scour.isBurrowed(), "The scour did not spawn burrowed");
			return scour.getId();
		});

		try {
			context.waitFor(mc -> seen(mc, id, SandScour.class, scour -> scour.action() == SandScour.ACTION_BURST), TELL_TIMEOUT);
			context.waitTicks(BURST_TELL_SHOW);
			context.takeScreenshot("oldrepublic_m5_188_scour_burst_tell");
			boolean telling = server.computeOnServer(s -> {
				SandScour scour = entity(s, id, SandScour.class);
				return scour.isBurrowed() && scour.action() == SandScour.ACTION_BURST;
			});
			check(telling, "The scour was not in its burst's tell under the sand at screenshot 188");
			context.waitFor(mc -> seen(mc, id, SandScour.class, scour -> !scour.isBurrowed()), BURST_TIMEOUT);
			context.waitTicks(BURST_SHOW);
			context.takeScreenshot("oldrepublic_m5_189_scour_burst");
			boolean burst = server.computeOnServer(s -> {
				SandScour scour = entity(s, id, SandScour.class);
				return !scour.isBurrowed() && scour.tellLog().heavyHits() > 0 && scour.tellLog().untold() == 0;
			});
			check(burst, "The scour had not burst out of the sand behind its tell by screenshot 189");
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				stage.removeActors(p);
				sand(p.level(), stage, Blocks.SMOOTH_STONE.defaultBlockState());
				p.removeEffect(MobEffects.SLOWNESS);
			});
		}
	}

	/** 190: a stormback 10 blocks ahead, struck once by the player, in its charge's tell. */
	private static void stormbackTell(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 5f);
		int id = server.computeOnServer(s -> {
			BlockPos at = stage.at(0, STORMBACK_AHEAD);
			return placed(Stormbacks.spawn(player(s).level(), at, null), at, "The stormback").getId();
		});
		context.waitTicks(4);
		aim(context, server, id);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Stormback beast = entity(s, id, Stormback.class);
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the player's provoking blow on the stormback (0.5) is dealt on the server");
			beast.invulnerableTime = 0;
			beast.hurtServer(p.level(), p.level().damageSources().playerAttack(p), 0.5f);
			check(beast.isAngryAt(p), "The struck stormback is not angry at the player");
		});
		context.waitFor(mc -> seen(mc, id, Stormback.class, beast -> beast.state() == Stormback.STATE_TELL), TELL_TIMEOUT);
		context.waitTicks(CHARGE_TELL_SHOW);
		context.takeScreenshot("oldrepublic_m5_190_stormback_charge_tell");
		int state = server.computeOnServer(s -> entity(s, id, Stormback.class).state());
		check(state == Stormback.STATE_TELL, "The stormback was out of its charge's tell at screenshot 190 (state " + state + ")");
		// Gone before it charges.
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	/** 191: a still stormback 6 blocks ahead, charged by the storm. */
	private static void stormbackCharged(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, 0, CameraType.FIRST_PERSON, 8f);
		int id = server.computeOnServer(s -> {
			BlockPos at = stage.at(0, CHARGED_AHEAD);
			Stormback beast = placed(Stormbacks.spawn(player(s).level(), at, null), at, "The stormback");
			beast.setNoAi(true);
			return beast.getId();
		});
		context.waitTicks(4);
		aim(context, server, id);
		server.runOnServer(s -> {
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the stormback stands without AI and is charged as a ridge strike charges it"
					+ " (Stormback.charge), without the bolt");
			entity(s, id, Stormback.class).charge(player(s).level());
		});
		context.waitFor(mc -> seen(mc, id, Stormback.class, Stormback::isCharged), SEEN_TIMEOUT);
		context.waitTicks(CHARGED_SHOW);
		context.takeScreenshot("oldrepublic_m5_191_stormback_charged");
		check(server.computeOnServer(s -> entity(s, id, Stormback.class).isCharged()), "The stormback's charge had run out by screenshot 191");
		server.runOnServer(s -> stage.removeActors(player(s)));
	}

	// ---------------------------------------------------------------- 192 to 195: Darth Ulvaine

	/**
	 * 192 to 195: Ulvaine's fight in a Heart laid out on the stage (her spot 12 blocks ahead, the hall 12 across, the player
	 * 3 blocks in, the pylons 6 either side of her and 3 behind, the resonators round her): the duel; the storm through
	 * {@code UlvaineFight.phase}; the Hymn, the pylon chanters sent away and the transition waited out, then a blow that
	 * brings the Crescendo; then a blow past her yield floor. The choice is not opened (the hooks are recorded); she is
	 * spared through {@code UlvaineFight.resolve} and the fight reset at the end.
	 */
	private static void ulvaine(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, DUEL_SPOT, CameraType.FIRST_PERSON, 0f);
		CollectorHooks.Recorder hooks = new CollectorHooks.Recorder();
		int id = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			UlvaineFight.reset(p);
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, Ulvaine's fight starts at once in a Heart laid out on the stage (UlvaineFight.begin),"
					+ " its hooks recorded instead of reaching the story");
			UlvaineSession session = UlvaineFight.begin(p.getUUID(), p.level(), heart(stage), false, false, hooks);
			DarthUlvaine u = session == null ? null : session.ulvaine();

			if (session == null || u == null || !session.isParticipant(p.getUUID())) {
				throw new AssertionError("Ulvaine's fight did not begin with the player listed");
			}

			return u.getId();
		});

		try {
			context.waitFor(mc -> ModSounds.MUSIC_BOSS_ULVAINE.equals(BossMusicService.wanted()), MUSIC_TIMEOUT);
			context.waitTicks(PHASE1_TICKS);
			back(context, server, stage, DUEL_SPOT);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m5_192_ulvaine_phase1");
			checkUlvaine(server, UlvaineRules.PHASE_DUEL, "screenshot 192 is not her duel", session -> true);

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, UlvaineFight.phase 2 (/oldrepublic ulvaine phase)");
			server.runOnServer(s -> check(UlvaineFight.phase(player(s), UlvaineRules.PHASE_STORM), "UlvaineFight.phase found no fight"));
			context.waitTicks(PHASE_TICKS);
			back(context, server, stage, DUEL_SPOT);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m5_193_ulvaine_phase2");
			checkUlvaine(server, UlvaineRules.PHASE_STORM, "screenshot 193 is not the storm with a chanter at each pylon",
					session -> session.chanters().size() == session.layout().pylons().size());

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, UlvaineFight.phase 3; the pylon chanters are sent away (the players kill them in the storm)");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				check(UlvaineFight.phase(p, UlvaineRules.PHASE_HYMN), "UlvaineFight.phase found no fight");
				UlvaineSession session = UlvaineFight.session(p.getUUID());

				if (session != null) {
					for (Hymnsworn chanter : session.chanters()) {
						chanter.discard();
					}
				}
			});
			awaitServer(context, server, s -> {
				UlvaineSession session = UlvaineFight.session(player(s).getUUID());
				return session == null || !session.isTransitioning();
			}, TRANSITION_TIMEOUT, "Ulvaine's phase 3 transition did not end");
			OldRepublicMod.LOGGER.info("Foes client step: shortcut, one blow brings her under the Crescendo's mark");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				UlvaineSession session = UlvaineFight.session(p.getUUID());
				DarthUlvaine u = session == null ? null : session.ulvaine();
				check(u != null && session.chanters().isEmpty(), "Ulvaine's fight was gone, or a chanter stood, before the Crescendo's blow");
				float mark = u.getMaxHealth() * (UlvaineBalance.get().crescendoAt - CRESCENDO_MARGIN);
				// Her hits are scaled as the session scales them (no pylon cut: the chanters are gone).
				blow(p, u, (u.getHealth() - mark) / UlvaineRules.damageScale(u.scaledMaxHealth()));
				check(session.crescendoBegun() && session.resonators().size() == 4, "The blow did not bring the Crescendo and its four resonators");
			});
			AABB hall = stage.area();
			context.waitFor(mc -> mc.level != null && mc.level.getEntitiesOfClass(HymnResonator.class, hall).size() >= 4, SEEN_TIMEOUT);
			context.waitTicks(CRESCENDO_TICKS);
			back(context, server, stage, DUEL_SPOT);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m5_194_ulvaine_phase3");
			checkUlvaine(server, UlvaineRules.PHASE_HYMN, "screenshot 194 is not the Hymn with the four resonators lit", session -> session.resonators().size() == 4);

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, one blow past her yield floor");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				UlvaineSession session = UlvaineFight.session(p.getUUID());
				DarthUlvaine u = session == null ? null : session.ulvaine();
				check(u != null, "Ulvaine's fight was gone before the yielding blow");
				// In the Heart and alive as the blow lands, so her yield is handed to the player at once.
				put(p, stage, DUEL_SPOT);
				blow(p, u, u.getMaxHealth() * 2f / UlvaineRules.damageScale(u.scaledMaxHealth()));
				check(session.isYielded() && hooks.calls(CollectorHooks.Kind.YIELDED).size() == 1, "Ulvaine did not yield to the blow");
			});
			context.waitFor(mc -> seen(mc, id, DarthUlvaine.class, DarthUlvaine::isKneeling), SEEN_TIMEOUT);
			context.waitTicks(YIELD_TICKS);
			back(context, server, stage, DUEL_SPOT);
			aim(context, server, id);
			context.takeScreenshot("oldrepublic_m5_195_ulvaine_yield");
			boolean knelt = server.computeOnServer(s -> {
				DarthUlvaine u = entity(s, id, DarthUlvaine.class);
				return u.isKneeling() && u.isYielded() && UlvaineFight.yieldOpen(player(s).getUUID());
			});
			check(knelt, "Ulvaine was not kneeling in her yield at screenshot 195");
			boolean phases = hooks.calls(CollectorHooks.Kind.PHASE).stream().map(CollectorHooks.Call::phase).toList()
					.equals(List.of(UlvaineRules.PHASE_STORM, UlvaineRules.PHASE_HYMN));
			check(phases, "Ulvaine's fight reported the phases " + hooks.calls(CollectorHooks.Kind.PHASE) + ", not 2 and 3");
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);

				if (UlvaineFight.yieldOpen(p.getUUID())) {
					UlvaineFight.resolve(p, BossOutcome.SPARED, new CollectorHooks.Recorder());
				}

				UlvaineFight.reset(p);
			});
		}

		boolean gone = server.computeOnServer(s -> UlvaineFight.session(player(s).getUUID()) == null && player(s).level().getEntity(id) == null
				&& !UlvaineFight.yieldOpen(player(s).getUUID()));
		check(gone, "Ulvaine's fight, her yield or Ulvaine herself was left behind");
		context.waitFor(mc -> BossMusicService.wanted() == null, MUSIC_TIMEOUT);
	}

	/** The Heart on the stage. */
	private static HeartLayout heart(Stage stage) {
		return new HeartLayout(stage.at(0, HEART_AHEAD), HEART_RADIUS, UlvaineBalance.get().heartHeight,
				List.of(stage.at(-9, 20), stage.at(9, 20), stage.at(9, 4), stage.at(-9, 4)),
				List.of(stage.at(-6, HEART_AHEAD + 3), stage.at(6, HEART_AHEAD + 3)),
				List.of(stage.at(-5, 9), stage.at(5, 9), stage.at(5, 16), stage.at(-5, 16)),
				stage.at(0, HEART_AHEAD - 4));
	}

	/** Fails with {@code failure} unless the player's fight is in {@code phase} (hers too) and passes {@code test}. */
	private static void checkUlvaine(TestServerContext server, int phase, String failure, Predicate<UlvaineSession> test) {
		boolean ok = server.computeOnServer(s -> {
			UlvaineSession session = UlvaineFight.session(player(s).getUUID());
			DarthUlvaine u = session == null ? null : session.ulvaine();
			return session != null && u != null && session.phase() == phase && u.phase() == phase && test.test(session);
		});
		check(ok, failure);
	}

	// ---------------------------------------------------------------- 196 to 199: the Long Hymn

	/**
	 * 196 to 199: the Long Hymn in an undercroft laid out on the stage (the First Cantor's spot 13 blocks ahead, the choir 5
	 * either side of it, the rings and silencing points round it), with four raiders: the player 1 block ahead of the spot and
	 * three mock raiders 3 to 6 blocks round the player. The choir's phase once he has risen; the verses with the basalt
	 * verse sung; the Hymn with its first ring singing; then the wipe, by its rule, seen from where the player fled.
	 */
	private static void cantor(ClientGameTestContext context, TestServerContext server, Stage stage, List<ServerPlayer> raiders, int[] leftAt) {
		stand(context, server, stage, RAID_SPOT, CameraType.THIRD_PERSON_BACK, 5f);
		OldRepublicMod.LOGGER.info("Foes client step: shortcut, the other three raiders are mock players with no client"
				+ " (as GameTestHelper#makeMockServerPlayerInLevel makes them); their join lines are waited out before the Hymn");
		int[] ids = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			int[] out = new int[RAIDER_SPOTS.length];

			for (int i = 0; i < RAIDER_SPOTS.length; i++) {
				ServerPlayer raider = logIn(s, level, stage.at(RAIDER_SPOTS[i][0], RAIDER_SPOTS[i][1]), RAIDER_NAMES[i]);
				raiders.add(raider);
				out[i] = raider.getId();
			}

			return out;
		});
		int joinedAt = server.computeOnServer(MinecraftServer::getTickCount);
		CantorSession[] hymn = {null};

		try {
			context.waitFor(mc -> {
				for (int id : ids) {
					if (!seen(mc, id, Player.class, raider -> true)) {
						return false;
					}
				}

				return true;
			}, SEEN_TIMEOUT);
			int since = server.computeOnServer(MinecraftServer::getTickCount) - joinedAt;

			if (since < CHAT_FADE_TICKS) {
				context.waitTicks(CHAT_FADE_TICKS - since);
			}

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the Long Hymn starts at once in an undercroft laid out on the stage (LongHymn.start, no countdown)");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				hymn[0] = LongHymn.start(p.level(), undercroft(stage), p, 0);
				check(hymn[0] != null, "The Long Hymn did not start");
			});
			context.waitFor(mc -> ModSounds.MUSIC_BOSS_CANTOR.equals(BossMusicService.wanted()), MUSIC_TIMEOUT);
			awaitServer(context, server, s -> hymn[0].cantor() != null && !hymn[0].isRising(), RISE_TIMEOUT, "The First Cantor did not rise");
			int cantor = server.computeOnServer(s -> {
				FirstCantor c = hymn[0].cantor();
				RaidSession raid = hymn[0].raid();
				int count = raid == null ? 0 : raid.count();
				check(c != null && count == RAIDERS, "The Long Hymn listed " + count + " raiders, not " + RAIDERS);
				return c.getId();
			});

			context.waitTicks(RISEN_TICKS);
			back(context, server, stage, RAID_SPOT);
			aim(context, server, cantor);
			context.takeScreenshot("oldrepublic_m5_196_cantor_phase1_choir");
			checkCantor(server, hymn, RaidRules.PHASE_CHOIR, "screenshot 196 is not the choir's phase, the four echoes singing round him and his health for four",
					session -> session.shielded() && session.choir().size() == 4
							&& Math.abs(session.maxHealth() - RaidRules.maxHealth(RAIDERS, RaidBalance.get())) < 1.0e-3);

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the Hymn jumps to the verses (forcePhase, as /oldrepublic raid phase), the transition"
					+ " skipped, and the basalt verse is sung at once (as /oldrepublic raid verse)");
			server.runOnServer(s -> {
				CantorSession session = hymn[0];
				FirstCantor c = session.cantor();
				check(c != null && session.forcePhase(RaidRules.PHASE_VERSES), "The Hymn did not move on to the verses");
				session.skipTransition();
				session.verses().forceVerse(c, CantorVerses.Verse.BASALT);
			});
			context.waitTicks(VERSE_TICKS);
			back(context, server, stage, RAID_SPOT);
			aim(context, server, cantor);
			context.takeScreenshot("oldrepublic_m5_197_cantor_phase2_verses");
			checkCantor(server, hymn, RaidRules.PHASE_VERSES, "screenshot 197 is not the verses with the basalt marks down for four",
					session -> session.verses().marks().size() == RaidRules.basaltMarks(RAIDERS, RaidBalance.get()));

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the Hymn jumps to its last phase (forcePhase), the transition skipped");
			server.runOnServer(s -> {
				CantorSession session = hymn[0];
				check(session.forcePhase(RaidRules.PHASE_HYMN), "The Hymn did not move on to its last phase");
				session.skipTransition();
			});
			context.waitTicks(HYMN_TICKS);
			back(context, server, stage, RAID_SPOT);
			aim(context, server, cantor);
			context.takeScreenshot("oldrepublic_m5_198_cantor_phase3_hymn");
			checkCantor(server, hymn, RaidRules.PHASE_HYMN, "screenshot 198 is not the Hymn with a ring singing",
					session -> session.engine().isRunning() && session.engine().singingRing() >= 0);

			OldRepublicMod.LOGGER.info("Foes client step: shortcut, the other three raiders fall (their health set to 0: listed, as on the death screen)"
					+ " and the player flees the undercroft; the wipe then comes by its rule");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);

				for (ServerPlayer raider : raiders) {
					raider.setHealth(0f);
				}

				put(p, stage, FLED_SPOT);
				check(!undercroft(stage).inside(p.position()), "The player's flight did not leave the undercroft");
			});
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.getInput().lookAt(0f, 12f);
			awaitServer(context, server, s -> hymn[0].isOver(), WIPE_TIMEOUT, "The Long Hymn did not wipe with no raider alive inside");
			context.waitTicks(WIPE_SHOW);
			context.takeScreenshot("oldrepublic_m5_199_cantor_wipe");
			boolean wiped = server.computeOnServer(s -> {
				CantorSession session = hymn[0];
				RaidSession raid = session.raid();
				ServerLevel level = player(s).level();
				return !session.isCleared() && raid != null && raid.isEnded() && level.getEntity(cantor) == null
						&& LongHymn.sessionIn(level, undercroft(stage)) == null;
			});
			check(wiped, "The Long Hymn did not end in a wipe by screenshot 199 (cleared, still running, or the First Cantor left behind)");
			context.waitFor(mc -> BossMusicService.wanted() == null, MUSIC_TIMEOUT);
		} finally {
			server.runOnServer(s -> {
				CantorSession session = hymn[0];

				if (session != null && !session.isOver()) {
					session.stop(true);
				}

				if (!raiders.isEmpty()) {
					leftAt[0] = s.getTickCount();
				}

				logOut(s, raiders);
			});
		}
	}

	/** The undercroft on the stage. */
	private static UndercroftLayout undercroft(Stage stage) {
		return new UndercroftLayout(stage.at(-11, -3).below(), stage.at(11, FRONT - 1).above(6), stage.at(0, CANTOR_AHEAD),
				stage.at(0, CANTOR_AHEAD - 3), null,
				List.of(stage.at(5, 9), stage.at(-5, 9), stage.at(-5, 17), stage.at(5, 17)),
				List.of(stage.at(-8, 7), stage.at(8, 7), stage.at(0, 19)),
				List.of(stage.at(0, 8), stage.at(5, 14), stage.at(-5, 14)));
	}

	/** Fails with {@code failure} unless the Hymn runs in {@code phase} (his too) with all four raiders listed and passes {@code test}. */
	private static void checkCantor(TestServerContext server, CantorSession[] hymn, int phase, String failure, Predicate<CantorSession> test) {
		boolean ok = server.computeOnServer(s -> {
			CantorSession session = hymn[0];
			FirstCantor c = session == null ? null : session.cantor();
			RaidSession raid = session == null ? null : session.raid();
			return c != null && raid != null && !session.isOver() && session.phase() == phase && c.phase() == phase && raid.count() == RAIDERS
					&& test.test(session);
		});
		check(ok, failure);
	}

	/**
	 * Logs a mock raider in at {@code at}, facing south, as {@code GameTestHelper#makeMockServerPlayerInLevel} does (the
	 * recipe of W6's {@code ForceClientSteps}): a connection whose only other end is an embedded channel, so whatever the
	 * server sends it goes nowhere. It is never ticked, so it takes no harm and never dies of its own accord.
	 */
	private static ServerPlayer logIn(MinecraftServer s, ServerLevel level, BlockPos at, String name) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer raider = new ServerPlayer(s, level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		// EmbeddedChannel is on the gametest classpath: W6's identical recipe (m5/ForceClientSteps) compiled and ran on CI at c532e60.
		new EmbeddedChannel(connection);
		s.getPlayerList().placeNewPlayer(connection, raider, cookie);
		raider.setGameMode(GameType.SURVIVAL);
		Vec3 feet = Vec3.atBottomCenterOf(at);
		raider.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0f, 0f, false);
		raider.setYHeadRot(0f);
		return raider;
	}

	/** Logs every mock raider out (the leave event fires) and forgets them. */
	private static void logOut(MinecraftServer s, List<ServerPlayer> raiders) {
		for (ServerPlayer raider : raiders) {
			if (!raider.hasDisconnected()) {
				s.getPlayerList().remove(raider);
			}
		}

		raiders.clear();
	}

	private FoesClientSteps() {
	}
}

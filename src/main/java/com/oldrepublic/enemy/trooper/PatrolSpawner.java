package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.story.ChapterThree;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The natural patrol spawner (spec 7.6). Every {@code patrols.intervalTicks} each eligible player
 * (Chapter Three SUMMONS or later, in the overworld, standing on the surface, not on Peaceful) has
 * a {@code patrols.chance} roll for one squad of the army whose outpost is nearer (else a random
 * army). The squad appears 48 to 80 blocks away, out of the player's sight, on {@code #story_ground}
 * in loaded chunks, clear of mission sites and the Reliquary, and walks a route that passes 16
 * blocks from the player and goes on 96 blocks beyond ({@link Troopers#startPatrol}).
 *
 * <p>Caps: one patrol within 128 blocks of a player, six per server. Every
 * {@code patrols.despawnCheckTicks} a squad whose members are all more than
 * {@code patrols.despawnDistance} from every player is discarded as a whole, and every patrol goes
 * when the server stops. Patrol members are never saved.
 */
public final class PatrolSpawner {
	/** Spawn points tried per roll before giving up until the next one. */
	private static final int PLACEMENT_TRIES = 12;
	/** The height of a trooper's head above its feet, for the sight check. */
	private static final double HEAD_HEIGHT = 1.6;

	private static final PatrolState LIVE = new PatrolState();

	/** What a spawn attempt did. */
	public enum Result {
		SPAWNED,
		/** A patrol is already near the player, or the server has its cap. */
		CAPPED,
		/** No hidden story ground in range clear of mission sites and the Reliquary. */
		NO_SITE,
		/** Patrols only walk the overworld. */
		WRONG_DIMENSION,
		PEACEFUL,
		/** The squad itself could not be placed. */
		FAILED
	}

	/** The outcome of a spawn attempt: the squad when {@link Result#SPAWNED}, else empty. */
	public record Outcome(Result result, List<LivingEntity> squad, @Nullable BlockPos at) {
		static Outcome of(Result result) {
			return new Outcome(result, List.of(), null);
		}
	}

	/** Registers the tick, the server stop and the {@code /oldrepublic patrol} commands. Reads no balance. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PatrolSpawner::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LIVE.clear());
		PatrolCommands.init();
	}

	/** The server's live patrols. Game tests use a detached {@link PatrolState} instead. */
	static PatrolState live() {
		return LIVE;
	}

	private static void tick(MinecraftServer server) {
		PatrolBalance b = PatrolBalance.get();
		int tick = server.getTickCount();

		if (b.despawnCheckTicks > 0 && tick % b.despawnCheckTicks == 0) {
			LIVE.despawnFar(PatrolSpawner::playerPositions, b.despawnDistance);
		}

		if (!b.enabled) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (PatrolRules.rollDue(tick, player.getUUID().hashCode(), b.intervalTicks) && eligible(player)
					&& player.getRandom().nextFloat() < b.chance) {
				trySpawn(player, b);
			}
		}
	}

	/** The natural conditions of spec 7.6 (the debug command skips the stage and the surface check). */
	static boolean eligible(ServerPlayer player) {
		ServerLevel level = player.level();
		return !player.isSpectator() && level.dimension() == Level.OVERWORLD && level.getDifficulty() != Difficulty.PEACEFUL
				&& ChapterThree.atLeast(player, ChapterThreeStage.SUMMONS) && onSurface(level, player.blockPosition());
	}

	/** Standing on the surface: nothing but leaves (or air) above the player's feet. */
	private static boolean onSurface(ServerLevel level, BlockPos feet) {
		return feet.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
	}

	/**
	 * Tries to spawn a patrol for {@code player} now, ignoring the roll and the Chapter Three stage
	 * but honouring the dimension, Peaceful, the caps and the placement rules.
	 */
	public static Outcome trySpawn(ServerPlayer player, PatrolBalance b) {
		ServerLevel level = player.level();

		if (level.dimension() != Level.OVERWORLD) {
			return Outcome.of(Result.WRONG_DIMENSION);
		}

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return Outcome.of(Result.PEACEFUL);
		}

		if (!PatrolRules.capsAllow(LIVE.near(level, player.position(), b.perPlayerRange), LIVE.size(), b)) {
			return Outcome.of(Result.CAPPED);
		}

		RandomSource random = level.getRandom();
		List<BlockPos> sites = guardedSites(level);

		for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
			Vec3 offset = PatrolRules.spawnOffset(random, b);
			BlockPos spot = ground(level, player.getX() + offset.x, player.getZ() + offset.z);

			if (spot == null) {
				continue;
			}

			Vec3 start = Vec3.atBottomCenterOf(spot);

			if (!hidden(player, start)) {
				continue;
			}

			List<Vec3> route = PatrolRules.route(start, player.position(), random.nextBoolean(), b);

			if (!PatrolRules.clearOfSites(PatrolRules.samples(start, route, PatrolRules.ROUTE_SAMPLE_STEP), sites, b.siteClearance)) {
				continue;
			}

			Faction army = army(player, b, random);
			List<LivingEntity> squad = Troopers.spawnSquad(level, spot, army, Troopers.defaultSquad(army, Progression.get(player).level), null, IncidentMode.NONE);

			if (squad.isEmpty()) {
				return Outcome.of(Result.FAILED);
			}

			List<BlockPos> points = new ArrayList<>(route.size());

			for (Vec3 point : route) {
				points.add(onGround(level, point));
			}

			Troopers.startPatrol(squad, points);
			UUID id = Troopers.squadId(squad.get(0));
			LIVE.add(id != null ? id : UUID.randomUUID(), squad);
			return new Outcome(Result.SPAWNED, squad, spot);
		}

		return Outcome.of(Result.NO_SITE);
	}

	/** Adds a reinforcement to the live patrol of {@code squad}, so it despawns and clears with it. */
	static void join(UUID squad, LivingEntity member) {
		LIVE.join(squad, member);
	}

	/** Discards every live patrol; returns how many squads went. */
	public static int clear() {
		return LIVE.clear();
	}

	/** The army whose outpost terminal is nearer within {@code outpostBiasRange}, else a random one. */
	private static Faction army(ServerPlayer player, PatrolBalance b, RandomSource random) {
		return PatrolRules.army(terminalDistanceSqr(player, Faction.REPUBLIC, b), terminalDistanceSqr(player, Faction.SITH_EMPIRE, b), random);
	}

	private static double terminalDistanceSqr(ServerPlayer player, Faction army, PatrolBalance b) {
		BlockPos terminal = Outposts.nearestTerminal(player, army, b.outpostBiasRange);

		if (terminal == null) {
			return -1;
		}

		double dx = terminal.getX() + 0.5 - player.getX();
		double dz = terminal.getZ() + 0.5 - player.getZ();
		return dx * dx + dz * dz;
	}

	/** The mission sites and the Reliquary in this level, which patrols keep {@code siteClearance} from. */
	private static List<BlockPos> guardedSites(ServerLevel level) {
		List<BlockPos> sites = new ArrayList<>();

		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if ((record.kind() == SiteKind.MISSION_SITE || record.kind() == SiteKind.RELIQUARY) && record.dimension().equals(level.dimension())) {
				sites.add(record.origin());
			}
		}

		return sites;
	}

	/**
	 * The standing spot at the top of the column at {@code x, z}, or null when its chunk is not
	 * loaded, the ground is not {@code #story_ground}, or there is no room for a trooper (liquid or a
	 * solid block at the feet or head).
	 */
	private static @Nullable BlockPos ground(ServerLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, level.getMinY(), z);

		if (!level.isLoaded(column)) {
			return null;
		}

		BlockPos feet = new BlockPos(column.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());

		if (!level.getBlockState(feet.below()).is(MeetingSites.STORY_GROUND)) {
			return null;
		}

		return roomy(level, feet) && roomy(level, feet.above()) ? feet : null;
	}

	private static boolean roomy(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.blocksMotion() && level.getFluidState(pos).isEmpty();
	}

	/** A route point put on the ground where its chunk is loaded; elsewhere it keeps the start's height. */
	private static BlockPos onGround(ServerLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);

		if (!level.isLoaded(pos)) {
			return pos;
		}

		return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
	}

	/** Out of the player's sight: outside their view, or with blocks between their eyes and a trooper's head there. */
	private static boolean hidden(ServerPlayer player, Vec3 feet) {
		Vec3 eye = player.getEyePosition();
		Vec3 head = feet.add(0, HEAD_HEIGHT, 0);

		if (PatrolRules.outsideView(eye, player.getLookAngle(), head)) {
			return true;
		}

		return player.level().clip(new ClipContext(eye, head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
	}

	/** The positions of the players a patrol counts as near, in {@code level}: spectators do not count. */
	private static List<Vec3> playerPositions(@Nullable Level level) {
		List<Vec3> positions = new ArrayList<>();

		if (level instanceof ServerLevel server) {
			for (ServerPlayer player : server.players()) {
				if (!player.isSpectator()) {
					positions.add(player.position());
				}
			}
		}

		return positions;
	}

	private PatrolSpawner() {
	}
}

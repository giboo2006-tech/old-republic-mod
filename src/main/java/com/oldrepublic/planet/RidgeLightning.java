package com.oldrepublic.planet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tyrvann's lightning on the high ground (spec MILESTONE_5.md 3.3), and what real lightning leaves:
 * <ul>
 * <li><b>Ridge strikes</b>: every {@code planets.ridgeIntervalTicks} ticks a player spends in the
 * basalt ridges above {@code planets.ridgeMinY} under open sky, a real bolt strikes
 * {@code planets.ridgeDistance} blocks from them; a stormback within
 * {@code planets.ridgeStormbackRange} draws it instead (the bolt charges it, W8). Never within
 * {@code planets.ridgeHubExclusion} blocks of the holdfast, whose rods keep the storm off the camp.
 * It is weather: it strikes on Peaceful too; {@code planets.ridgeLightning} off stops it.</li>
 * <li><b>Stormglass</b>: real lightning (not a cosmetic bolt) that strikes exposed basalt on Tyrvann
 * grows a stormglass cluster on it half the time ({@code planets.stormglassChance}), with static and
 * a crackle ({@code stormglass} effect). The strike hook is {@code LightningStrikeMixin}.</li>
 * </ul>
 */
public final class RidgeLightning {
	/** W8's stormback, matched by id so the ridges need no compile-time link to the foe. */
	public static final Identifier STORMBACK = OldRepublicMod.id("stormback");
	/** Players are checked this often. */
	private static final int CHECK_TICKS = 20;

	/** When each player on the ridges is due their next strike. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	/**
	 * Where a ridge strike near a player at {@code player} lands (pure, for the tests): on the
	 * stormback at {@code stormback} when one is within {@code planets.ridgeStormbackRange}; else
	 * {@code planets.ridgeDistance} blocks away in a random direction. Null when the point lies within
	 * {@code planets.ridgeHubExclusion} of the holdfast's centre {@code hub} (horizontally).
	 */
	public static @Nullable Vec3 target(Vec3 player, @Nullable Vec3 stormback, BlockPos hub, RandomSource random, PlanetBalance balance) {
		Vec3 point;

		if (stormback != null && stormback.distanceToSqr(player) <= balance.ridgeStormbackRange * balance.ridgeStormbackRange) {
			point = stormback;
		} else {
			int[] range = balance.ridgeDistance;
			double min = range.length == 0 ? 4 : Math.max(0, range[0]);
			double max = range.length == 0 ? 10 : Math.max(min, range[range.length - 1]);
			double angle = random.nextDouble() * Mth.TWO_PI;
			double distance = min + random.nextDouble() * (max - min);
			point = player.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
		}

		double dx = point.x - (hub.getX() + 0.5);
		double dz = point.z - (hub.getZ() + 0.5);
		return dx * dx + dz * dz < balance.ridgeHubExclusion * balance.ridgeHubExclusion ? null : point;
	}

	/** Whether {@code player} stands where ridge lightning finds them: the basalt biome, above the line, under open sky. */
	public static boolean onRidge(ServerLevel level, ServerPlayer player, PlanetBalance balance) {
		BlockPos at = player.blockPosition();
		return player.getY() >= balance.ridgeMinY && level.getBiome(at).is(PlanetWorldgen.TYRVANN_BASALT)
				&& level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/** Every {@link #CHECK_TICKS}: the strikes of the players on Tyrvann's ridges that are due. */
	static void tick(MinecraftServer server) {
		ServerLevel level = Planets.level(server, Planets.Planet.TYRVANN);

		if (level == null || level.getGameTime() % CHECK_TICKS != 0) {
			return;
		}

		PlanetBalance balance = PlanetBalance.get();
		long now = level.getGameTime();
		Set<UUID> present = new HashSet<>();

		for (ServerPlayer player : level.players()) {
			if (!balance.ridgeLightning || player.isSpectator() || !player.isAlive() || !onRidge(level, player, balance)) {
				continue;
			}

			present.add(player.getUUID());
			Long due = NEXT.get(player.getUUID());

			// The clock starts when the player reaches the ridges, so arriving is never met by a bolt.
			if (due == null || now >= due) {
				NEXT.put(player.getUUID(), now + Hymnal.between(level.getRandom(), balance.ridgeIntervalTicks));
			}

			if (due != null && now >= due) {
				strike(level, player, balance);
			}
		}

		NEXT.keySet().retainAll(present);
	}

	/** Forgets every clock (server stop). */
	static void clear() {
		NEXT.clear();
	}

	/** One ridge strike near {@code player}: a real bolt on the ground, or on a stormback close by. */
	static void strike(ServerLevel level, ServerPlayer player, PlanetBalance balance) {
		PlanetSiteData.Hub hub = PlanetSiteData.hub(level.getServer(), Planets.Planet.TYRVANN);
		Entity stormback = nearestStormback(level, player.position(), balance.ridgeStormbackRange);
		Vec3 point = target(player.position(), stormback == null ? null : stormback.position(), hub == null ? BlockPos.ZERO : hub.centre(),
				level.getRandom(), balance);

		if (point == null || !level.hasChunkAt(BlockPos.containing(point))) {
			return;
		}

		if (stormback == null) {
			point = new Vec3(point.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(point.x), Mth.floor(point.z)), point.z);
		}

		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

		if (bolt != null) {
			bolt.snapTo(point);
			level.addFreshEntity(bolt);
		}
	}

	private static @Nullable Entity nearestStormback(ServerLevel level, Vec3 centre, double range) {
		Entity nearest = null;
		double best = range * range;

		for (Entity entity : level.getEntities((Entity) null, new AABB(centre, centre).inflate(range),
				e -> e.isAlive() && STORMBACK.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())))) {
			double distance = entity.position().distanceToSqr(centre);

			if (distance <= best) {
				best = distance;
				nearest = entity;
			}
		}

		return nearest;
	}

	// ---------------------------------------------------------------- stormglass

	/** A real bolt struck (the strike hook): on Tyrvann, the stormglass roll on what it hit. */
	public static void onStrike(ServerLevel level, LightningBolt bolt) {
		if (!level.dimension().equals(Planets.TYRVANN)) {
			return;
		}

		Vec3 pos = bolt.position();
		// The block the bolt stands on, as vanilla's own strike position reads it.
		BlockPos struck = BlockPos.containing(pos.x, pos.y - 1.0E-6, pos.z);
		formStormglass(level, struck, level.getRandom().nextFloat(), PlanetBalance.get().stormglassChance);
	}

	/**
	 * Grows a stormglass cluster on the basalt at {@code struck} when {@code roll} is under
	 * {@code chance} and the cell above is open (air, or the strike's own fire). A test seam: the
	 * strike hook rolls, the tests pass the roll. Returns whether a cluster grew.
	 */
	public static boolean formStormglass(ServerLevel level, BlockPos struck, float roll, double chance) {
		if (roll >= chance || !isBasalt(level.getBlockState(struck))) {
			return false;
		}

		BlockPos above = struck.above();
		BlockState there = level.getBlockState(above);
		BlockState cluster = PlanetBlocks.STORMGLASS_CLUSTER.defaultBlockState();

		if (!(there.isAir() || there.is(BlockTags.FIRE)) || !cluster.canSurvive(level, above)) {
			return false;
		}

		level.setBlock(above, cluster, Block.UPDATE_ALL);
		EffectEvents.around(level, PlanetEffects.STORMGLASS, Vec3.atCenterOf(above), null, -1, 1f, 0);
		return true;
	}

	/** Basalt in any of its three forms. */
	public static boolean isBasalt(BlockState state) {
		return state.is(Blocks.BASALT) || state.is(Blocks.POLISHED_BASALT) || state.is(Blocks.SMOOTH_BASALT);
	}

	private RidgeLightning() {
	}
}

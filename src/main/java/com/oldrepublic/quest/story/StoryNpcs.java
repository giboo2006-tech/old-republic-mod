package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteChecks;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Chapter Five's people and fights in the world (spec MILESTONE_5.md 6.3, 6.4): the story persons
 * spawned for one player (Dask at {@code NPC_DASK}, the debtor at {@code NPC_DEBTOR}, Clan Hask's
 * champion beside Varra), and the chapter's fights (the debtor's salvagers, the matriarch's brood,
 * the counting-house's guards and the clan warriors at the player's side).
 *
 * <p>Nothing here is saved. A story person is not saved ({@link HubNpc#setStoryOwner}) and is spawned
 * again while the story needs it; every fighter carries {@link #STORY_TAG}, so one left in a chunk
 * that unloaded is discarded when it loads again ({@link ChapterFiveService#isLeftover}). A fight is
 * kept in memory per owner and ends when its owner leaves it (another level, or past its range): its
 * fighters go, and the story spawns the fight again when the owner comes back (the M4 leave rule).
 */
final class StoryNpcs {
	/** Every Chapter Five person and fighter carries this tag. */
	static final String STORY_TAG = "oldrepublic.c5_story";
	/** A story person is spawned when its owner comes this close to its spot, and looked for this far around it. */
	static final int PERSON_RANGE = 48;
	/** A fight ends when its owner is this far from where it began. */
	static final int LEAVE_RANGE = 80;
	/** A missing fighter counts as dead only while its owner is this close to the fight (the chunk is surely loaded). */
	private static final int RECOUNT_RANGE = 40;

	/** One owner's fight: its id (a mission id), where it is, and its fighters on both sides. */
	record Fight(String id, ResourceKey<Level> dimension, BlockPos at, List<UUID> foes, List<UUID> allies) {
	}

	/** Each owner's running fight. */
	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	// ---------------------------------------------------------------- places

	/** The runtime piece of {@code kind} holding {@code pos} in {@code level}, or null (not placed, or no record yet). */
	static @Nullable SitePiece piece(ServerLevel level, @Nullable BlockPos pos, SiteKind kind) {
		return pos == null ? null : StructureLocator.runtimePieceAt(level, pos, kind);
	}

	/** {@code planet}'s hub piece, or null while the hub is not placed. */
	static @Nullable SitePiece hub(ServerLevel level, Planets.Planet planet) {
		return piece(level, PlanetHubs.hub(level.getServer(), planet), SiteKind.PLANET_HUB);
	}

	/**
	 * Where {@code anchor} is in the hub of {@code planet}, or near its centre when the template has no
	 * such anchor ({@code fallback} blocks east of it, on the ground): null while the hub is not placed.
	 */
	static @Nullable BlockPos hubAnchor(ServerLevel level, Planets.Planet planet, SiteAnchors.Anchor anchor, int fallback) {
		BlockPos centre = PlanetHubs.hub(level.getServer(), planet);

		if (centre == null) {
			return null;
		}

		SitePiece piece = piece(level, centre, SiteKind.PLANET_HUB);
		BlockPos found = piece == null ? null : StructureLocator.anchor(piece, anchor);
		return found != null ? found : ground(level, centre.offset(fallback, 0, 0));
	}

	/** The surface at {@code pos}'s column when its chunk is loaded, else {@code pos}. */
	static BlockPos ground(ServerLevel level, BlockPos pos) {
		if (!level.hasChunkAt(pos)) {
			return pos;
		}

		return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
	}

	/** Whether {@code p} is in {@code level} and within {@code range} blocks of {@code pos} (horizontally). */
	static boolean near(ServerPlayer p, ResourceKey<Level> level, BlockPos pos, double range) {
		if (!p.level().dimension().equals(level)) {
			return false;
		}

		double dx = p.getX() - (pos.getX() + 0.5);
		double dz = p.getZ() - (pos.getZ() + 0.5);
		return dx * dx + dz * dz <= range * range;
	}

	// ---------------------------------------------------------------- persons

	/** The owner's story person of {@code variant} within {@link #PERSON_RANGE} of {@code near}, or null. */
	static @Nullable HubNpc find(ServerLevel level, UUID owner, String variant, BlockPos near) {
		List<HubNpc> found = level.getEntitiesOfClass(HubNpc.class, new AABB(near).inflate(PERSON_RANGE),
				npc -> npc.isAlive() && owner.equals(npc.storyOwner()) && variant.equals(npc.variant()));
		return found.isEmpty() ? null : found.getFirst();
	}

	/**
	 * The owner's story person of {@code variant} at {@code at}: the one already there, or a new one
	 * when the owner is near enough to see it come (null while the owner is away or the spawn failed).
	 */
	static @Nullable HubNpc ensure(ServerPlayer owner, BlockPos at, String variant) {
		ServerLevel level = owner.level();

		if (!near(owner, level.dimension(), at, PERSON_RANGE) || !level.hasChunkAt(at)) {
			return null;
		}

		HubNpc existing = find(level, owner.getUUID(), variant, at);
		return existing != null ? existing : HubNpcs.spawnStory(level, at, variant, owner.getUUID(), STORY_TAG);
	}

	/** Sends the owner's story persons of {@code variant} near {@code near} away (after a choice, or a mission's end). */
	static void remove(ServerLevel level, UUID owner, String variant, BlockPos near) {
		for (HubNpc npc : level.getEntitiesOfClass(HubNpc.class, new AABB(near).inflate(PERSON_RANGE),
				n -> owner.equals(n.storyOwner()) && variant.equals(n.variant()))) {
			npc.discard();
		}
	}

	/** Sends every story person of the owner's around them away (a forgotten player, a reset). */
	static void removeAll(ServerPlayer owner) {
		for (HubNpc npc : owner.level().getEntitiesOfClass(HubNpc.class, owner.getBoundingBox().inflate(LEAVE_RANGE),
				n -> owner.getUUID().equals(n.storyOwner()))) {
			npc.discard();
		}
	}

	// ---------------------------------------------------------------- fights

	/** Whether the owner's fight {@code id} is running. */
	static boolean fighting(ServerPlayer p, String id) {
		Fight fight = FIGHTS.get(p.getUUID());
		return fight != null && fight.id().equals(id);
	}

	/**
	 * Begins the owner's fight {@code id} at {@code at} with these fighters (any running fight of the
	 * owner's ends first). Each gets {@link #STORY_TAG} and the owner's tag.
	 */
	static void startFight(ServerPlayer p, String id, BlockPos at, List<? extends LivingEntity> foes, List<? extends LivingEntity> allies) {
		endFight(p);
		FIGHTS.put(p.getUUID(), new Fight(id, p.level().dimension(), at.immutable(), tagged(p, foes), tagged(p, allies)));
	}

	private static List<UUID> tagged(ServerPlayer p, List<? extends LivingEntity> fighters) {
		List<UUID> ids = new ArrayList<>(fighters.size());

		for (LivingEntity fighter : fighters) {
			fighter.addTag(STORY_TAG);

			if (StoryTags.owner(fighter) == null) {
				StoryTags.setOwner(fighter, p.getUUID());
			}

			ids.add(fighter.getUUID());
		}

		return ids;
	}

	/**
	 * The owner's foes of fight {@code id} still standing, or -1 when that fight is not running. A
	 * foe missing from the world counts as fallen only while the owner stands near the loaded fight
	 * (a foe that wandered into an unloaded chunk is not counted either way).
	 */
	static int foesLeft(ServerPlayer p, String id) {
		Fight fight = FIGHTS.get(p.getUUID());

		if (fight == null || !fight.id().equals(id)) {
			return -1;
		}

		ServerLevel level = p.level();
		boolean watching = near(p, fight.dimension(), fight.at(), RECOUNT_RANGE) && SiteChecks.entitiesReady(level, fight.at(), 1);
		int left = 0;

		for (UUID foe : fight.foes()) {
			Entity entity = p.level().dimension().equals(fight.dimension()) ? level.getEntity(foe) : null;

			if (entity != null ? entity.isAlive() : !watching) {
				left++;
			}
		}

		return left;
	}

	/** How many foes fight {@code id} began with (0 when it is not running). */
	static int foesAtStart(ServerPlayer p, String id) {
		Fight fight = FIGHTS.get(p.getUUID());
		return fight == null || !fight.id().equals(id) ? 0 : fight.foes().size();
	}

	/** Whether the owner has left their fight: another level, or past {@link #LEAVE_RANGE}. */
	static boolean leftFight(ServerPlayer p) {
		Fight fight = FIGHTS.get(p.getUUID());
		return fight != null && !near(p, fight.dimension(), fight.at(), LEAVE_RANGE);
	}

	/** Ends the owner's fight, if any: its fighters still in the world go (those in unloaded chunks go when they load). */
	static void endFight(ServerPlayer p) {
		Fight fight = FIGHTS.remove(p.getUUID());

		if (fight == null) {
			return;
		}

		ServerLevel level = p.level().getServer().getLevel(fight.dimension());

		if (level == null) {
			return;
		}

		for (List<UUID> side : List.of(fight.foes(), fight.allies())) {
			for (UUID id : side) {
				Entity entity = level.getEntity(id);

				if (entity != null) {
					entity.discard();
				}
			}
		}
	}

	/** The id of the owner's running fight, or null. */
	static @Nullable String fightId(ServerPlayer p) {
		Fight fight = FIGHTS.get(p.getUUID());
		return fight == null ? null : fight.id();
	}

	static void forget(ServerPlayer p) {
		endFight(p);
	}

	static void clear() {
		FIGHTS.clear();
	}

	private StoryNpcs() {
	}
}

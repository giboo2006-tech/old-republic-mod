package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.CrystalPoacher;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.enemy.Poachers;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Yielding;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.StoryBalance;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mission sites (spec 3.6, 5.4, 5.5): placing a runtime camp on the mission rings, finding the
 * worldgen site of a survey or secure contract, the site's anchors, the entities that spawn when the
 * owner arrives, and the tags that tie every spawned entity to its owner and mission.
 *
 * <p>Every mission spawn carries {@link #MISSION_TAG}, {@code oldrepublic.mission.<missionId>}, an
 * owner tag ({@link StoryTags#setOwner}) and one role tag ({@link #ROLE_PREFIX}{@code <role>}); never
 * the Chapter Two encounter tags, so the story feature leaves them alone.
 */
final class MissionSites {
	/** On every mission entity. */
	static final String MISSION_TAG = "oldrepublic.mission";
	/** {@code oldrepublic.mission.<missionId>}: the mission the entity belongs to. */
	static final String MISSION_PREFIX = MISSION_TAG + ".";
	/** {@code oldrepublic.mission_role.<role>}: what the entity is in its mission. */
	static final String ROLE_PREFIX = "oldrepublic.mission_role.";

	/** A fighter whose defeat counts toward the objective. */
	static final String ROLE_ENEMY = "enemy";
	/** The R2 chief or the I1 fence. */
	static final String ROLE_CHIEF = "chief";
	/** The yielding sergeant of I2 or of an intercept. */
	static final String ROLE_SERGEANT = "sergeant";
	/** A downed trooper waiting to be tended. */
	static final String ROLE_DOWNED = "downed";
	/** An escort, its prisoner, or any mission trooper walking off on its own (cleanup leaves them be). */
	static final String ROLE_ESCORT = "escort";
	/** The freed settler at the Republic outpost (a cameo). */
	static final String ROLE_CAMEO = "cameo";

	/** How far around a site its owner's mission entities are looked for. */
	static final double SEARCH_RADIUS = 48;
	/** Ring radius of the fallback spawn spots when a site has no anchor for them. */
	private static final int FALLBACK_RING = 4;
	/** Extra clearance around a camp's footprint for the placement search. */
	private static final int CLEARANCE_MARGIN = 4;
	/** The mission templates' lowest layer is ground (gen_structures_story_m3.py): it is set into the terrain, as M2's camps. */
	private static final int SITE_BURIED = 1;
	/** Rings of the forward fallback's close search, inside the mission fallback rings (48 and 80). */
	private static final int[] NEAR_RINGS = {16, 24, 32, 40};

	// ---------------------------------------------------------------- placement

	/**
	 * Places the mission's runtime camp on the mission rings around the player and returns its stand,
	 * or null when no spot passed (the caller retries). With {@code story.placeStructures} off, no
	 * blocks are placed: the M2 meeting search picks a spot and the entities stand around it.
	 */
	static @Nullable BlockPos place(ServerPlayer p, MissionDefinition def, UUID owner) {
		Identifier template = def.site();

		if (template == null) {
			return null;
		}

		ServerLevel level = p.level();
		MissionBalance b = MissionFeature.balance();
		StoryBalance story = StoryService.balance();
		int footprint = def.count("footprint", 13);
		MeetingSites.Rules rules = new MeetingSites.Rules(footprint, footprint + CLEARANCE_MARGIN, story.maxSlope, forbidden(level, footprint),
				StoryService.otherSites(p), b.siteSeparation);

		if (!story.placeStructures) {
			return MeetingSites.find(level, p.position(), p.getLookAngle(), b.siteRings, b.siteFallback, story.siteAngles, story.fallbackForward, rules);
		}

		try {
			RuntimeSites.Placement placement = RuntimeSites.placeSurface(level, template, p.position(), p.getLookAngle(), b.siteRings, b.siteFallback, rules, SITE_BURIED,
					owner);
			return placement == null ? null : placement.origin();
		} catch (IllegalArgumentException | IllegalStateException e) {
			OldRepublicMod.LOGGER.warn("Mission site {} could not be placed for {}: {}", template, p.getName().getString(), e.getMessage());
			return null;
		}
	}

	/**
	 * The forward fallback after repeated failures: the best spot of the M2 meeting search (which
	 * never fails; it ends straight ahead of the player), with the camp stamped there when it fits.
	 * When the camp cannot be stamped either, the mission still runs around the bare spot.
	 */
	static BlockPos placeForward(ServerPlayer p, MissionDefinition def, UUID owner) {
		ServerLevel level = p.level();
		MissionBalance b = MissionFeature.balance();
		StoryBalance story = StoryService.balance();
		int footprint = def.count("footprint", 13);
		MeetingSites.Rules rules = new MeetingSites.Rules(footprint, footprint + CLEARANCE_MARGIN, story.maxSlope, forbidden(level, footprint),
				StoryService.otherSites(p), b.siteSeparation);
		Identifier template = def.site();

		if (story.placeStructures && template != null) {
			try {
				// Close rings with every site check first: with a small view distance the mission rings reach unloaded chunks.
				RuntimeSites.Placement near = RuntimeSites.placeSurface(level, template, p.position(), p.getLookAngle(), NEAR_RINGS, new int[0], rules,
						SITE_BURIED, owner);

				if (near != null) {
					return near.origin();
				}
			} catch (IllegalArgumentException | IllegalStateException e) {
				OldRepublicMod.LOGGER.warn("Mission site {} could not be placed near {}: {}", template, p.getName().getString(), e.getMessage());
			}
		}

		BlockPos stand = MeetingSites.find(level, p.position(), p.getLookAngle(), b.siteRings, b.siteFallback, story.siteAngles, story.fallbackForward, rules);

		if (story.placeStructures && template != null) {
			Rotation rotation = RuntimeSites.facing(entranceToward(stand, p.position()));

			if (!RuntimeSites.fits(level, template, stand, rotation, SITE_BURIED)) {
				OldRepublicMod.LOGGER.warn("Mission site {} at {} for {} would overlap another site or a build; the mission runs around the bare spot", template,
						stand, p.getName().getString());
				return stand;
			}

			try {
				return RuntimeSites.placeAt(level, template, stand, rotation, SITE_BURIED, owner).origin();
			} catch (IllegalArgumentException | IllegalStateException e) {
				OldRepublicMod.LOGGER.warn("Mission site {} has no room at {} for {}; the mission runs around the bare spot: {}", template, stand,
						p.getName().getString(), e.getMessage());
			}
		}

		return stand;
	}

	/** The side of a camp at {@code stand} that faces {@code toward} (its entrance). */
	private static Direction entranceToward(BlockPos stand, Vec3 toward) {
		double dx = toward.x - (stand.getX() + 0.5);
		double dz = toward.z - (stand.getZ() + 0.5);

		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}

		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * Stand positions inside spawn protection (the M2 Crossing rule, with the camp's half-width added
	 * so no part of it lies in the protected square).
	 */
	private static Predicate<BlockPos> forbidden(ServerLevel level, int footprint) {
		StoryBalance b = StoryService.balance();

		if (!b.respectSpawnProtection || level.dimension() != Level.OVERWORLD || b.spawnProtectionRadius <= 0) {
			return pos -> false;
		}

		BlockPos spawn = level.getRespawnData().pos();
		int reach = b.spawnProtectionRadius + footprint / 2 + 1;
		return pos -> Math.abs(pos.getX() - spawn.getX()) <= reach && Math.abs(pos.getZ() - spawn.getZ()) <= reach;
	}

	/**
	 * The runtime camp standing at {@code site}, or null (no template placed, or it is not loaded).
	 * Only a mission camp answers: an outpost or another site whose box also holds the stand would
	 * have no crate, book or spawn anchors.
	 */
	static @Nullable SitePiece piece(ServerLevel level, BlockPos site) {
		return level.hasChunkAt(site.getX(), site.getZ()) ? StructureLocator.runtimePieceAt(level, site, SiteKind.MISSION_SITE) : null;
	}

	/**
	 * Looks for the nearest site of any of {@code kinds} within {@code radius} that {@code exclude}
	 * does not rule out, and calls back once with it, or with null when there is none. Searches go
	 * through the locator's queue; the callback never runs if the player leaves first.
	 */
	static void nearest(ServerPlayer p, List<SiteKind> kinds, int radius, @Nullable Predicate<SitePiece> exclude, Consumer<@Nullable SitePiece> callback) {
		Vec3 from = p.position();
		int[] pending = {kinds.size()};
		SitePiece[] best = {null};

		for (SiteKind kind : kinds) {
			StructureLocator.find(p, kind, radius, exclude, piece -> {
				if (piece != null && (best[0] == null || distanceSq(piece, from) < distanceSq(best[0], from))) {
					best[0] = piece;
				}

				if (--pending[0] == 0) {
					callback.accept(best[0]);
				}
			});
		}
	}

	/** Horizontal distance squared from {@code from} to the piece's centre. */
	private static double distanceSq(SitePiece piece, Vec3 from) {
		BlockPos centre = piece.box().getCenter();
		double dx = centre.getX() + 0.5 - from.x;
		double dz = centre.getZ() + 0.5 - from.z;
		return dx * dx + dz * dz;
	}

	/** The site's heart (the survey target): its {@code HEART} anchor anywhere in the structure, else the piece's centre. */
	static BlockPos heart(SitePiece piece) {
		BlockPos heart = StructureLocator.anchor(piece, SiteAnchors.Anchor.HEART);
		return heart != null ? heart : piece.box().getCenter();
	}

	/** Where a secure contract's guards stand: the {@code SURFACE_GUARD} anchor, else the ground above the piece's centre. */
	static BlockPos surfaceGuard(ServerLevel level, SitePiece piece) {
		BlockPos guard = StructureLocator.anchor(piece, SiteAnchors.Anchor.SURFACE_GUARD);

		if (guard != null) {
			return guard;
		}

		BlockPos centre = piece.box().getCenter();
		return new BlockPos(centre.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()), centre.getZ());
	}

	/**
	 * The world positions of {@code anchors} in the piece, in order; anchors the piece lacks (or every
	 * one when there is no piece) fall back to spots on a small ring around {@code site} on the ground.
	 */
	static List<BlockPos> spots(ServerLevel level, @Nullable SitePiece piece, BlockPos site, List<SiteAnchors.Anchor> anchors) {
		List<BlockPos> spots = new ArrayList<>(anchors.size());

		for (int i = 0; i < anchors.size(); i++) {
			BlockPos found = piece == null ? null : SiteAnchors.find(piece, anchors.get(i));
			spots.add(found != null ? found : ringSpot(level, site, i, anchors.size()));
		}

		return spots;
	}

	/** One anchor's position in the piece, or the site itself without one. */
	static BlockPos spot(ServerLevel level, @Nullable SitePiece piece, BlockPos site, SiteAnchors.Anchor anchor) {
		return spots(level, piece, site, List.of(anchor)).getFirst();
	}

	private static BlockPos ringSpot(ServerLevel level, BlockPos centre, int index, int count) {
		double angle = index * Math.PI * 2 / Math.max(1, count);
		int x = centre.getX() + (int) Math.round(Math.cos(angle) * FALLBACK_RING);
		int z = centre.getZ() + (int) Math.round(Math.sin(angle) * FALLBACK_RING);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	// ---------------------------------------------------------------- spawning

	/** Most blocks a spawn spot is lifted when its anchor cell holds a solid block (a crate, a sandbag). */
	private static final int MAX_LIFT = 3;

	/** {@code at}, or the first cell above it that does not block movement, so nobody spawns inside a block. */
	static BlockPos stand(ServerLevel level, BlockPos at) {
		BlockPos pos = at;

		for (int i = 0; i < MAX_LIFT && level.getBlockState(pos).blocksMotion(); i++) {
			pos = pos.above();
		}

		return pos;
	}

	/** {@code count} crystal poachers, one per spot (spots are reused when there are fewer). */
	static List<LivingEntity> spawnCrew(ServerLevel level, UUID owner, String missionId, List<BlockPos> spots, int count) {
		List<LivingEntity> crew = new ArrayList<>();

		for (int i = 0; i < count && !spots.isEmpty(); i++) {
			for (LivingEntity poacher : Poachers.spawnCrew(level, stand(level, spots.get(i % spots.size())), 1, owner)) {
				// Not a Chapter Two crew: the Reckoning counts the deaths of StoryTags.CREW members.
				poacher.removeTag(StoryTags.CREW);
				crew.add(tag(poacher, owner, missionId, ROLE_ENEMY));
			}
		}

		return crew;
	}

	/** Bound sentinels of {@code variant}, one per spot. */
	static List<LivingEntity> spawnSentinels(ServerLevel level, UUID owner, String missionId, List<BlockPos> spots, int count, SentinelVariant variant) {
		List<LivingEntity> sentinels = new ArrayList<>();

		for (int i = 0; i < count && !spots.isEmpty(); i++) {
			sentinels.add(tag(Sentinels.spawn(level, stand(level, spots.get(i % spots.size())), variant, true, owner), owner, missionId, ROLE_ENEMY));
		}

		return sentinels;
	}

	/** Downed troopers of {@code army} the owner can tend, one per spot. */
	static List<LivingEntity> spawnDowned(ServerLevel level, UUID owner, String missionId, List<BlockPos> spots, Faction army) {
		List<LivingEntity> downed = new ArrayList<>();

		for (BlockPos at : spots) {
			downed.add(tag(Troopers.spawnDowned(level, stand(level, at), army, owner), owner, missionId, ROLE_DOWNED));
		}

		return downed;
	}

	/**
	 * A squad of {@code army} at {@code at}: the sergeant gets the {@link #ROLE_SERGEANT} role (W4
	 * picks his yield dialogue from the mission tag), everyone else {@link #ROLE_ENEMY}.
	 */
	static List<LivingEntity> spawnSquad(ServerLevel level, UUID owner, String missionId, BlockPos at, Faction army, List<TrooperRole> roles,
			IncidentMode incident) {
		List<LivingEntity> squad = new ArrayList<>();

		for (LivingEntity trooper : Troopers.spawnSquad(level, stand(level, at), army, roles, owner, incident)) {
			squad.add(tag(trooper, owner, missionId, Troopers.role(trooper) == TrooperRole.SERGEANT ? ROLE_SERGEANT : ROLE_ENEMY));
		}

		return squad;
	}

	/** A named poacher captain (the R2 chief or the I1 fence), owned so only the owner decides her fate. */
	static @Nullable PoacherCaptain spawnCaptain(ServerLevel level, UUID owner, String missionId, BlockPos at, String nameKey, double yieldFraction) {
		PoacherCaptain captain = EnemyEntities.POACHER_CAPTAIN.spawn(level, stand(level, at), EntitySpawnReason.STRUCTURE);

		if (captain == null) {
			return null;
		}

		captain.setCustomName(Component.translatable(nameKey));
		Yielding.setYieldFraction(captain, yieldFraction);
		captain.setOwner(owner);
		captain.setEncounter(MISSION_PREFIX + missionId);
		tag(captain, owner, missionId, ROLE_CHIEF);
		return captain;
	}

	/** A named crystal poacher in a chief's place (Hessa's lieutenant): no yield, fights to the end. */
	static @Nullable LivingEntity spawnLieutenant(ServerLevel level, UUID owner, String missionId, BlockPos at, String nameKey) {
		CrystalPoacher lieutenant = EnemyEntities.CRYSTAL_POACHER.spawn(level, stand(level, at), EntitySpawnReason.STRUCTURE);

		if (lieutenant == null) {
			return null;
		}

		lieutenant.setCustomName(Component.translatable(nameKey));
		return tag(lieutenant, owner, missionId, ROLE_CHIEF);
	}

	/** Ties a spawned entity to its owner and mission and keeps it from despawning. */
	static <T extends Entity> T tag(T entity, UUID owner, String missionId, String role) {
		StoryTags.setOwner(entity, owner);
		entity.addTag(MISSION_TAG);
		entity.addTag(MISSION_PREFIX + missionId);
		entity.addTag(ROLE_PREFIX + role);

		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
		}

		return entity;
	}

	// ---------------------------------------------------------------- reading tags

	static boolean isMember(Entity entity) {
		return entity.entityTags().contains(MISSION_TAG);
	}

	/** The mission id an entity belongs to, or null. */
	static @Nullable String missionOf(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(MISSION_PREFIX)) {
				return tag.substring(MISSION_PREFIX.length());
			}
		}

		return null;
	}

	static boolean hasRole(Entity entity, String role) {
		return entity.entityTags().contains(ROLE_PREFIX + role);
	}

	/** The owner's loaded entities of {@code missionId} around {@code site} that pass {@code filter}. */
	static List<Entity> members(ServerLevel level, UUID owner, String missionId, BlockPos site, Predicate<Entity> filter) {
		return members(level, owner, missionId, site, SEARCH_RADIUS, filter);
	}

	/** As {@link #members(ServerLevel, UUID, String, BlockPos, Predicate)} within {@code radius} blocks. */
	static List<Entity> members(ServerLevel level, UUID owner, String missionId, BlockPos site, double radius, Predicate<Entity> filter) {
		return level.getEntities((Entity) null, new AABB(site).inflate(radius), e -> isMember(e) && StoryTags.isOwnedBy(e, owner)
				&& missionId.equals(missionOf(e)) && filter.test(e));
	}

	/**
	 * Ends a finished or abandoned mission's presence around its site (the blocks stay): troopers
	 * still standing fall back on foot (W4 removes them out of sight), every other fighter goes, and
	 * untended downed troopers start bleeding out at {@code bleedAt}. Escorts and troopers already
	 * walking off keep their own walk.
	 */
	static void cleanup(ServerLevel level, UUID owner, String missionId, @Nullable BlockPos site, long bleedAt) {
		if (site == null || !level.hasChunkAt(site.getX(), site.getZ())) {
			return;
		}

		Vec3 centre = Vec3.atCenterOf(site);
		int removed = 0;

		for (Entity entity : members(level, owner, missionId, site, e -> !hasRole(e, ROLE_ESCORT))) {
			if (hasRole(entity, ROLE_DOWNED)) {
				if (Troopers.isDowned(entity)) {
					Troopers.bleedOut(entity, bleedAt);
				}
			} else if (entity instanceof TrooperEntity trooper && trooper.isAlive()) {
				walkOff(trooper, centre);
			} else {
				entity.discard();
				removed++;
			}
		}

		if (removed > 0) {
			OldRepublicMod.LOGGER.debug("Removed {} mission entities of {} around {}", removed, missionId, site);
		}
	}

	/** A trooper of a mission walks off under its own steering and is never cleaned up again. */
	static void walkOff(TrooperEntity trooper, Vec3 awayFrom) {
		trooper.addTag(ROLE_PREFIX + ROLE_ESCORT);

		if (!trooper.isLeaving()) {
			trooper.leave(null, awayFrom);
		}
	}

	private MissionSites() {
	}
}

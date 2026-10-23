package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.identity.CaptiveOutcome;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.Settlers;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke on the Ridge: the raider stronghold's site, its people, the Captain's and the captives'
 * fates, the Poachers' Ledger, completion and cleanup. The stronghold ({@code story/raider_camp}) is
 * placed far away through {@link FarSites} when the mission starts (docs/MILESTONE_6.md 3.5), or by
 * the M2 near search when far placement fails; its people appear at its anchors the first time the
 * owner comes close. All of them are persistent and carry the owner's tag, so kills by anyone count
 * for the owner.
 */
final class Reckoning {
	/** Prefix of the captive group id; the spawn time follows, so every run is its own group. */
	static final String GROUP_PREFIX = "smoke_on_the_ridge_";
	/** How long the crew (or the Captain) must be missing from a loaded camp before it counts as gone. */
	private static final int ABSENT_TICKS = 100;
	/** The camp counts as loaded and watched when the owner is this close. */
	private static final double WATCH_DISTANCE = 32;
	private static final double SEARCH_RADIUS = 64;
	/** The command's stronghold stands at least this far ahead of the player (M2's {@code here}), and 8 blocks beyond its own half-width. */
	private static final int HERE_DISTANCE = 24;
	private static final int HERE_GAP = 8;

	/** Owner -> game time their crew (or Captain) was first seen missing while watched. */
	private static final Map<UUID, Long> CREW_ABSENT = new HashMap<>();
	private static final Map<UUID, Long> CAPTAIN_ABSENT = new HashMap<>();

	/** The captive group id of the current run. */
	static String groupId(StoryData data) {
		return GROUP_PREFIX + data.encounterSpawnedAt;
	}

	/**
	 * Starts the mission: asks for the raider stronghold {@code story.strongholdRange} from the
	 * mentor's camp (an Independent: from the player) on high ground (a bare ground point when
	 * {@code story.placeStructures} is off). Asked again on later slow ticks while it is chosen and
	 * loaded (the request is idempotent); when far placement fails, the M2 search 96 to 160 blocks out
	 * takes over. Does nothing outside the overworld or when a site exists already.
	 */
	static void begin(ServerPlayer player, StoryData data) {
		if (data.encounterState() != EncounterState.NOT_PLACED || !StoryService.inOverworld(player)) {
			return;
		}

		settle(player, FarSites.request(player.level(), strongholdRequest(player, data)));
	}

	private static FarSites.Request strongholdRequest(ServerPlayer player, StoryData data) {
		StoryBalance b = StoryService.balance();
		UUID owner = player.getUUID();
		String key = StorySites.strongholdKey(owner);
		BlockPos from = origin(player, data);
		FarSites.Request request = b.placeStructures
				? StorySites.request(key, owner, StoryStructures.RAIDER_CAMP, from, b.strongholdRange)
				: StorySites.point(key, owner, from, b.strongholdRange, b.encounterFootprint);
		return request.preferring(FarSites.Terrain.HIGH).avoiding(avoid(player, data), b.siteMinSeparation);
	}

	/** Where the mission is measured from: the mentor's camp, or for an Independent (or without a camp) the player. */
	private static BlockPos origin(ServerPlayer player, StoryData data) {
		return data.storyPath() != Path.INDEPENDENT && data.camp != null ? data.camp : player.blockPosition();
	}

	/** The other players' sites and the owner's own, which the stronghold keeps away from. */
	private static List<BlockPos> avoid(ServerPlayer player, StoryData data) {
		List<BlockPos> avoid = Crossing.otherSites(player);
		Crossing.addSites(avoid, data);
		return avoid;
	}

	/**
	 * Takes the far request's answer while nothing is placed: the stronghold's heart (or the ground
	 * point) once placed, the near search when it failed, nothing while it is under way.
	 */
	static void settle(ServerPlayer player, FarSites.Status status) {
		StoryData data = Story.get(player);

		if (data.encounterState() != EncounterState.NOT_PLACED) {
			return;
		}

		BlockPos heart = StorySites.placedAt(status);

		if (heart != null) {
			placed(player, heart);
		} else if (!StorySites.pending(status)) {
			placed(player, near(player, data));
		}
	}

	/**
	 * Far placement failed: the M2 search 96 to 160 blocks from the camp (or the player) with the
	 * stronghold's own size from its site meta, then (with {@code story.placeStructures}) the
	 * stronghold placed there with its gate toward that origin. Returns the site: the stronghold's
	 * heart, or the spot when nothing could be placed.
	 */
	private static BlockPos near(ServerPlayer player, StoryData data) {
		ServerLevel level = player.level();
		StoryBalance b = StoryService.balance();
		Vec3 origin = data.storyPath() != Path.INDEPENDENT && data.camp != null ? Vec3.atBottomCenterOf(data.camp) : player.position();
		MeetingSites.Rules rules = StorySites.nearRules(level, StoryStructures.RAIDER_CAMP, b.encounterFootprint, b.encounterClearance, b.maxSlope,
				Crossing.forbidden(level), avoid(player, data), b.siteMinSeparation);
		int mid = (b.encounterMinDist + b.encounterMaxDist) / 2;
		int[] rings = {b.encounterMinDist, mid, b.encounterMaxDist};
		int forward = Math.max(b.fallbackForward * 2, StorySites.halfSide(level, StoryStructures.RAIDER_CAMP, StoryStructures.RAIDER_SIZE / 2) + HERE_GAP);
		BlockPos site = MeetingSites.find(level, origin, player.getLookAngle(), rings, b.encounterFallback, b.siteAngles, forward, rules);
		RuntimeSites.Placement placement = b.placeStructures ? placeStronghold(level, site, origin, player.getUUID()) : null;
		return placement != null ? StorySites.heart(placement) : site;
	}

	/** The stronghold centred on {@code stand} with its gate toward {@code toward}, and recorded; null (logged) when it cannot be placed there. */
	private static RuntimeSites.@Nullable Placement placeStronghold(ServerLevel level, BlockPos stand, Vec3 toward, UUID owner) {
		try {
			return RuntimeSites.placeAt(level, StoryStructures.RAIDER_CAMP, stand, StorySites.facingToward(stand, toward),
					StorySites.buried(StoryStructures.RAIDER_CAMP, 1), owner);
		} catch (IllegalArgumentException | IllegalStateException e) {
			OldRepublicMod.LOGGER.warn("Could not place the raider stronghold at {}: {}", stand, e.getMessage());
			return null;
		}
	}

	/** The stronghold stands at {@code site}: the mission's state, the journal, and the brief with the way to the smoke. */
	private static void placed(ServerPlayer player, BlockPos site) {
		record(player, site);
		Journal.unlockEntry(player, JournalEntries.SMOKE_ON_THE_RIDGE);
		Journal.unlockCodex(player, JournalEntries.CODEX_THE_FRONTIER);
		BlockPos smoke = smoke(player.level(), player.getUUID(), site);
		StorySites.noteWay(player, JournalEntries.SMOKE_ON_THE_RIDGE, smoke);
		Component way = FarSites.describe(player.blockPosition(), smoke);
		String mentor = StoryRules.mentorOf(Story.get(player).storyPath());

		if (mentor != null) {
			StoryService.mentorSays(player, MentorRole.byId(mentor), "reckoning.brief", way);
		} else {
			player.sendSystemMessage(Component.translatable("message.oldrepublic.story.smoke_seen", way));
		}
	}

	/** The mission's site, with the fates of a fresh run. */
	private static void record(ServerPlayer player, BlockPos site) {
		StoryData data = Story.get(player);
		data.encounterPos = site;
		data.encounterState = EncounterState.PLACED.ordinal();
		data.captain = CaptainFate.NONE.ordinal();
		data.captives = CaptivesFate.NONE.ordinal();
		data.captainId = null;
		Story.commit(player, data);
	}

	/**
	 * The command's stronghold ({@code /oldrepublic story reckoning <player> here}): any far request is
	 * dropped and the stronghold is placed now ahead of the player, its gate toward them, with no
	 * brief; a bare site there when it cannot be placed or {@code story.placeStructures} is off.
	 */
	static void here(ServerPlayer player) {
		ServerLevel level = player.level();
		FarSites.cancel(level, StorySites.strongholdKey(player.getUUID()));
		Vec3 flat = player.getLookAngle().horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		int distance = Math.max(HERE_DISTANCE, StorySites.halfSide(level, StoryStructures.RAIDER_CAMP, StoryStructures.RAIDER_SIZE / 2) + HERE_GAP);
		Vec3 ahead = player.position().add(dir.scale(distance));
		BlockPos stand = BlockPos.containing(StoryMentors.ground(level, ahead.x, ahead.z));
		RuntimeSites.Placement placement = StoryService.balance().placeStructures ? placeStronghold(level, stand, player.position(), player.getUUID()) : null;
		StoryData data = Story.get(player);
		data.pathStage = Math.max(data.pathStage, PathStage.RECKONING.ordinal());
		Story.commit(player, data);
		record(player, placement != null ? StorySites.heart(placement) : stand);
	}

	/** The smoke the player sees on the ridge (the Calling's target): the stronghold's {@code SIGNAL_FIRE}, else its site. */
	static BlockPos smoke(ServerLevel level, UUID owner, BlockPos site) {
		BlockPos fire = StorySites.anchor(StorySites.ownedPiece(level, owner, StoryStructures.RAIDER_CAMP, site), SiteAnchors.Anchor.SIGNAL_FIRE);
		return fire != null ? fire : site;
	}

	/** Where the stronghold's people stand: its anchors, with the old camp's layout as the fallback. */
	private static StorySites.RaiderSpots spots(ServerLevel level, UUID owner, BlockPos site) {
		SitePiece camp = StorySites.ownedPiece(level, owner, StoryStructures.RAIDER_CAMP, site);
		return StorySites.raiderSpots(level, camp, site);
	}

	/** Every tick: the ash drifting toward the smoke (toward the far request's target while it is placed) while the journal is held. */
	static void tickCalling(ServerPlayer player, StoryData data) {
		if (!StoryService.callingDue(player)) {
			return;
		}

		EncounterState state = data.encounterState();
		BlockPos target = null;

		if (data.encounterPos != null && (state == EncounterState.PLACED || state == EncounterState.SPAWNED) && !data.captainFate().resolved()) {
			target = smoke(player.level(), player.getUUID(), data.encounterPos);
		} else if (state == EncounterState.NOT_PLACED && data.stage() == PathStage.RECKONING) {
			target = StorySites.pendingPointer(player.level(), StorySites.strongholdKey(player.getUUID()));
		}

		if (target != null) {
			StoryService.calling(player, target, QuestEffects.CALLING_SMOKE);
		}
	}

	/** Slow tick: start (Independent), spawn on approach, settle fates, complete, clean up. */
	static void tick(ServerPlayer player, StoryData data) {
		if (!StoryService.inOverworld(player)) {
			return;
		}

		StoryBalance b = StoryService.balance();

		if (data.storyPath() == Path.INDEPENDENT && data.stage() == PathStage.NONE && data.crossing() == CrossingStage.DECIDED
				&& (StoryService.now(player) - data.decidedAt >= b.independentDelayTicks || QuestItems.holdsJournal(player))) {
			StoryRules.advancePath(data, PathStage.RECKONING, PathStage.NONE);
			Story.commit(player, data);
			begin(player, data);
			return;
		}

		if (data.stage() == PathStage.RECKONING && data.encounterState() == EncounterState.NOT_PLACED) {
			begin(player, data);
			return;
		}

		BlockPos site = data.encounterPos;

		if (site == null) {
			return;
		}

		double distSq = player.blockPosition().distSqr(site);

		switch (data.encounterState()) {
			case PLACED -> {
				if (distSq <= sq(b.encounterSpawnDistance)) {
					spawn(player, data);
				}
			}
			case SPAWNED -> watch(player, data, distSq);
			case DONE -> {
				if (distSq > sq(b.cleanupDistance)) {
					cleanup(player.level(), player.getUUID(), site);
				}
			}
			case NOT_PLACED -> {
			}
		}
	}

	/** Spawns the crew, the hired acolyte, the Captain and the captives (owner's first approach). */
	static void spawn(ServerPlayer player, StoryData data) {
		BlockPos site = data.encounterPos;

		if (site == null || data.encounterState() != EncounterState.PLACED) {
			return;
		}

		ServerLevel level = player.level();
		StoryBalance b = StoryService.balance();
		UUID owner = player.getUUID();
		data.encounterState = EncounterState.SPAWNED.ordinal();
		data.encounterSpawnedAt = StoryService.now(player);
		Story.commit(player, data);

		int extras = Math.min(b.extraMax, b.extraPerPlayer * awakenedNearby(player, b.extraPlayerRadius));
		StorySites.RaiderSpots places = spots(level, owner, site);
		List<BlockPos> spots = places.crew();
		int crew = 0;

		for (int i = 0; i < b.poachers + extras; i++) {
			BlockPos spot = spots.get(i % spots.size()).offset(i / spots.size(), 0, 0);

			if (member(level, EnemyEntities.CRYSTAL_POACHER, spot, site, owner, StoryTags.CREW) != null) {
				crew++;
			}
		}

		String encounter = groupId(data);

		if (member(level, EnemyEntities.SITH_ACOLYTE, places.acolyte(), site, owner, StoryTags.CREW) instanceof SithAcolyte deserter) {
			// "A deserter the Captain hired": hostile, in the deserter's robes.
			deserter.setMode(SithAcolyte.Mode.HOSTILE);
			deserter.setSkin(SithAcolyte.Skin.DESERTER);
			deserter.setOwner(owner);
			deserter.setEncounter(encounter);
			crew++;
		}

		Entity captain = member(level, EnemyEntities.POACHER_CAPTAIN, places.captain(), site, owner, StoryTags.CAPTAIN);

		if (captain instanceof PoacherCaptain hessa) {
			hessa.setCustomName(Component.translatable("npc.oldrepublic.hessa_dray"));
			hessa.setOwner(owner);
			hessa.setEncounter(encounter);
		}

		List<BlockPos> posts = places.captives();
		List<Entity> captives = Settlers.spawnCaptives(level, posts.subList(0, Math.min(posts.size(), b.captiveCount)), owner, groupId(data));

		for (Entity captive : captives) {
			StoryTags.setOwner(captive, owner);
			captive.addTag(StoryTags.ENCOUNTER);
		}

		StoryData fresh = Story.get(player);
		fresh.captainId = captain == null ? null : captain.getUUID();
		// If the Captain could not be spawned her part of the mission cannot happen; count it as fled.
		fresh.captain = captain == null ? CaptainFate.FLED.ordinal() : CaptainFate.NONE.ordinal();
		fresh.counters.put(StoryRules.CREW_TOTAL, crew);
		fresh.counters.put(StoryRules.CREW_DOWN, 0);
		Story.commit(player, fresh);
	}

	/** Spawns one encounter member facing the camp's centre, tagged with the owner and its role. */
	private static @Nullable Entity member(ServerLevel level, EntityType<?> type, BlockPos at, BlockPos centre, UUID owner, String roleTag) {
		Entity entity = type.spawn(level, at, EntitySpawnReason.STRUCTURE);

		if (entity == null) {
			return null;
		}

		double dx = centre.getX() - at.getX();
		double dz = centre.getZ() - at.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
		entity.setYHeadRot(yaw);
		StoryTags.setOwner(entity, owner);
		entity.addTag(StoryTags.ENCOUNTER);
		entity.addTag(roleTag);

		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
		}

		return entity;
	}

	/** Other awakened players near the owner when the camp comes alive. */
	private static int awakenedNearby(ServerPlayer owner, double radius) {
		int count = 0;

		for (ServerPlayer other : owner.level().getPlayers(p -> true)) {
			if (other != owner && other.distanceToSqr(owner) <= radius * radius && Progression.get(other).isAwakened()) {
				count++;
			}
		}

		return count;
	}

	/** While the camp is alive: the Captain yielding, the crew falling, captives left behind, completion. */
	private static void watch(ServerPlayer player, StoryData data, double distSq) {
		StoryBalance b = StoryService.balance();
		ServerLevel level = player.level();
		boolean watched = distSq <= sq(WATCH_DISTANCE) && data.encounterPos != null && level.hasChunkAt(data.encounterPos.getX(), data.encounterPos.getZ());
		long now = StoryService.now(player);
		boolean changed = false;

		if (data.captainFate() == CaptainFate.NONE && data.captainId != null && level.getEntity(data.captainId) instanceof Yieldable yieldable
				&& yieldable.isYielded()) {
			data.captain = CaptainFate.YIELDED.ordinal();
			changed = true;
		}

		if (watched && !data.captainFate().resolved() && data.captainId != null && level.getEntity(data.captainId) == null) {
			if (now - CAPTAIN_ABSENT.computeIfAbsent(player.getUUID(), k -> now) >= ABSENT_TICKS) {
				// She died or vanished while the owner was away; the ledger fell with her.
				data.captain = CaptainFate.DIED.ordinal();
				Journal.unlockEntry(player, JournalEntries.SHE_FELL);
				changed = true;
			}
		} else {
			CAPTAIN_ABSENT.remove(player.getUUID());
		}

		boolean crewDown = StoryRules.crewDown(data);

		if (!crewDown && watched && countAlive(level, player.getUUID(), data.encounterPos, StoryTags.CREW) == 0) {
			if (now - CREW_ABSENT.computeIfAbsent(player.getUUID(), k -> now) >= ABSENT_TICKS) {
				data.counters.put(StoryRules.CREW_DOWN, data.counter(StoryRules.CREW_TOTAL));
				crewDown = true;
				changed = true;
			}
		} else {
			CREW_ABSENT.remove(player.getUUID());
		}

		if (changed) {
			Story.commit(player, data);
		}

		if (StoryRules.captivesAbandoned(data, crewDown, Math.sqrt(distSq), b.abandonDistance)) {
			// Walking away from the bound captives is the choice to leave them.
			Settlers.resolveCaptives(player, groupId(data), CaptiveOutcome.ABANDONED, null);
			data = Story.get(player);
		}

		checkComplete(player, data);
	}

	private static int countAlive(ServerLevel level, UUID owner, @Nullable BlockPos site, String tag) {
		if (site == null) {
			return 0;
		}

		return level.getEntities((Entity) null, new AABB(site).inflate(SEARCH_RADIUS),
				e -> e.isAlive() && e.entityTags().contains(tag) && StoryTags.isOwnedBy(e, owner)).size();
	}

	/** A hostile crew member of this owner's camp died (anyone's kill counts). */
	static void onCrewDied(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (data.encounterState() != EncounterState.SPAWNED) {
			return;
		}

		data.counters.merge(StoryRules.CREW_DOWN, 1, Integer::sum);
		Story.commit(player, data);
		checkComplete(player, data);
	}

	/** The owner decided the yielded Captain's fate (spare, execute) or let her flee. */
	static void onCaptainResolved(ServerPlayer player, LivingEntity captain, YieldOutcome outcome) {
		StoryData data = Story.get(player);

		if (data.encounterState() != EncounterState.SPAWNED || !captain.getUUID().equals(data.captainId) || data.captainFate().resolved()) {
			return;
		}

		StoryBalance b = StoryService.balance();
		ServerLevel level = player.level();

		switch (outcome) {
			case SPARED -> {
				data.captain = CaptainFate.SPARED.ordinal();
				StoryService.shift(player, "reckoning.captain", b.captainSpareAlignment, "quest.oldrepublic.c2.reason.captain_spared");
				giveLedger(player);
				Journal.unlockEntry(player, JournalEntries.A_LIFE_SPARED);
			}
			case EXECUTED -> {
				data.captain = CaptainFate.EXECUTED.ordinal();
				StoryService.shift(player, "reckoning.captain", b.captainExecuteAlignment, "quest.oldrepublic.c2.reason.captain_executed");
				dropLedger(level, captain.position());
				Journal.unlockEntry(player, JournalEntries.A_DEBT_COLLECTED);
			}
			case FLED -> {
				data.captain = CaptainFate.FLED.ordinal();
				StoryService.shift(player, "reckoning.captain", b.captainFledAlignment, "quest.oldrepublic.c2.reason.captain_fled");
				BlockPos post = data.encounterPos == null ? captain.blockPosition() : spots(level, player.getUUID(), data.encounterPos).captain();
				dropLedger(level, Vec3.atBottomCenterOf(post));
				Journal.unlockEntry(player, JournalEntries.SHE_RAN);
			}
		}

		Story.commit(player, data);
		checkComplete(player, data);
	}

	/**
	 * The Captain died. Killed without a decision (never yielded, or yielded and then killed some
	 * other way) she simply fell: no alignment change, and the ledger drops where she lay. The owner
	 * may be offline; the ledger still drops and the next watch marks her fate.
	 */
	static void onCaptainDied(@Nullable ServerPlayer player, LivingEntity captain, ServerLevel level) {
		if (player == null) {
			dropLedger(level, captain.position());
			return;
		}

		StoryData data = Story.get(player);

		if (data.encounterState() != EncounterState.SPAWNED || !captain.getUUID().equals(data.captainId) || data.captainFate().resolved()) {
			return;
		}

		data.captain = CaptainFate.DIED.ordinal();
		Story.commit(player, data);
		dropLedger(level, captain.position());
		Journal.unlockEntry(player, JournalEntries.SHE_FELL);
		checkComplete(player, data);
	}

	/** The owner chose for the captives (or left them). */
	static void onCaptivesResolved(ServerPlayer player, String groupId, CaptiveOutcome outcome) {
		StoryData data = Story.get(player);

		if (data.encounterState() != EncounterState.SPAWNED || !groupId.equals(groupId(data)) || data.captivesFate() != CaptivesFate.NONE) {
			return;
		}

		StoryBalance b = StoryService.balance();
		data.captives = CaptivesFate.of(outcome).ordinal();
		Story.commit(player, data);

		switch (outcome) {
			case FREED -> {
				StoryService.shift(player, "reckoning.captives", b.captivesFreeAlignment, "quest.oldrepublic.c2.reason.captives_freed");
				Identity.changeReputation(player, Faction.FRONTIER, b.captivesFreedFrontierRep, "reckoning.captives", true);
				Journal.unlockEntry(player, JournalEntries.CUT_LOOSE);
			}
			case EXPLOITED -> {
				StoryService.shift(player, "reckoning.captives", b.captivesPayAlignment, "quest.oldrepublic.c2.reason.captives_paid");
				Identity.changeReputation(player, Faction.FRONTIER, b.captivesPaidFrontierRep, "reckoning.captives", true);
				Journal.unlockEntry(player, JournalEntries.THE_PRICE_OF_FREEDOM);
			}
			case ABANDONED -> {
				StoryService.shift(player, "reckoning.captives", b.captivesLeaveAlignment, "quest.oldrepublic.c2.reason.captives_left");
				Identity.changeReputation(player, Faction.FRONTIER, b.captivesLeftFrontierRep, "reckoning.captives", true);
				Journal.unlockEntry(player, JournalEntries.LEFT_BOUND);
			}
		}

		checkComplete(player, Story.get(player));
	}

	private static void checkComplete(ServerPlayer player, StoryData data) {
		boolean crewDown = StoryRules.crewDown(data);

		if (data.encounterState() == EncounterState.SPAWNED && StoryRules.reckoningComplete(data, crewDown)) {
			complete(player, data);
		}
	}

	/**
	 * Smoke on the Ridge is done. Path players report back to their mentor; an Independent finishes
	 * the chapter ("No Master": three skill points, the fourth slot through the {@code first_rank}
	 * milestone, Frontier reputation). Also used by {@code /oldrepublic story reckoning complete}.
	 */
	static void complete(ServerPlayer player, StoryData data) {
		if (data.encounterState() == EncounterState.DONE || data.hasFlag(StoryRules.RECKONING_DONE)) {
			return;
		}

		Path path = data.storyPath();
		boolean independent = path == Path.INDEPENDENT;
		data.encounterState = EncounterState.DONE.ordinal();
		data.flags.add(StoryRules.RECKONING_DONE);

		if (data.stage() == PathStage.RECKONING) {
			StoryRules.advancePath(data, StoryRules.afterReckoning(path), PathStage.RECKONING);
		}

		boolean chapterDone = independent && data.stage() == PathStage.COMPLETE;

		if (chapterDone) {
			data.flags.add(Story.NO_MASTER);
		}

		Story.commit(player, data);
		CREW_ABSENT.remove(player.getUUID());
		CAPTAIN_ABSENT.remove(player.getUUID());

		StoryBalance b = StoryService.balance();
		Progression.addXp(player, b.reckoningXp, "quest");
		Progression.grantSkillPoints(player, independent ? b.reckoningSpIndependent : b.reckoningSp);
		Progression.grantResonanceMilestone(player, "reckoning", b.resonanceReckoning);

		if (chapterDone) {
			Identity.changeReputation(player, Faction.FRONTIER, b.independentFrontierRep, "reckoning.independent", true);
			Progression.grantResonanceMilestone(player, "first_rank", b.resonanceFirstRank);
			Journal.unlockEntry(player, JournalEntries.NO_MASTER);
			StoryService.milestone(player, data, QuestEffects.LESSON_CHAPTER);
		} else {
			StoryService.milestone(player, data, QuestEffects.LESSON_RECKONING);
			String mentor = StoryRules.mentorOf(path);

			if (mentor != null) {
				player.sendSystemMessage(Component.translatable("message.oldrepublic.story.report_back", StoryService.mentorName(MentorRole.byId(mentor))));
			}
		}
	}

	/** Gives the ledger (the spared Captain hands it over); drops it at the player if the inventory is full. */
	private static void giveLedger(ServerPlayer player) {
		ItemStack ledger = new ItemStack(QuestItems.POACHERS_LEDGER);

		if (!player.getInventory().add(ledger)) {
			dropLedger(player.level(), player.position());
		}
	}

	private static void dropLedger(ServerLevel level, Vec3 at) {
		ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, new ItemStack(QuestItems.POACHERS_LEDGER), 0, 0.2, 0);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	/**
	 * Whether an encounter entity that just loaded has no place in the owner's story any more: the
	 * mission was reset, or it is done and the owner is far away.
	 */
	static boolean isLeftover(Entity entity, ServerPlayer owner, StoryData data) {
		if (!StoryTags.isEncounterMember(entity)) {
			return false;
		}

		return switch (data.encounterState()) {
			case NOT_PLACED, PLACED -> true;
			case SPAWNED -> false;
			case DONE -> owner.level() != entity.level() || owner.distanceToSqr(entity) > sq(StoryService.balance().cleanupDistance);
		};
	}

	/** Removes the owner's remaining encounter entities around a finished camp (the blocks stay). */
	static void cleanup(ServerLevel level, UUID owner, BlockPos site) {
		if (!level.hasChunkAt(site.getX(), site.getZ())) {
			return;
		}

		for (Entity entity : level.getEntities((Entity) null, new AABB(site).inflate(SEARCH_RADIUS),
				e -> StoryTags.isEncounterMember(e) && StoryTags.isOwnedBy(e, owner))) {
			entity.discard();
		}
	}

	static void forget(ServerPlayer player) {
		CREW_ABSENT.remove(player.getUUID());
		CAPTAIN_ABSENT.remove(player.getUUID());
	}

	static void clear() {
		CREW_ABSENT.clear();
		CAPTAIN_ABSENT.clear();
	}

	private static double sq(double value) {
		return value * value;
	}

	private Reckoning() {
	}
}

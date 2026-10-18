package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The story side of the mentors and of Brenn Ostrak: spawning, finding them by UUID, replacing a
 * lost one, removing duplicates, talking to them, summoning them, and sparring with them. There is
 * never more than one mentor per owner and role: the UUID in the story data is the only one wanted,
 * and every other copy removes itself.
 */
public final class StoryMentors {
	/** Duel profile ids of the saber feature. */
	static final String PROFILE_MASTER = "jedi_master";
	static final String PROFILE_TRAINING = "training";
	/** Dialogue opened by meditating as an Independent ("Reach out") or far from the mentor ("Ask them to come"). */
	static final Identifier REACH_OUT_DIALOGUE = OldRepublicMod.id("independent");
	static final Identifier BRENN_DIALOGUE = OldRepublicMod.id("brenn");

	/** "player uuid:role" -> game time the owner was first near the spot with that mentor missing. */
	private static final Map<String, Long> MISSING_SINCE = new HashMap<>();
	/** Acolytes walking out of the story (Brenn spared): entity UUID -> game time to remove them. */
	private static final Map<UUID, Long> LEAVING = new HashMap<>();
	private static final int LEAVE_TICKS = 200;
	private static final double LEAVE_DISTANCE = 20;

	// --- Values the mentor entity asks for

	/** How far the mentor may stray from its anchor: close at the Crossing, the camp leash later. */
	public static int leash(MentorEntity mentor) {
		return atCrossing(mentor) ? 2 : StoryService.balance().mentorLeash;
	}

	public static double departDistance() {
		return StoryService.balance().departDistance;
	}

	public static int departTimeout() {
		return StoryService.balance().departTimeoutTicks;
	}

	public static int attackCooldown() {
		return StoryService.balance().mentorAttackCooldownTicks;
	}

	/** Within this distance the mentor turns to face the owner (further at the Crossing). */
	public static double faceDistance(MentorEntity mentor) {
		StoryBalance b = StoryService.balance();
		return atCrossing(mentor) ? b.mentorFaceDistance : b.mentorLookDistance;
	}

	private static boolean atCrossing(MentorEntity mentor) {
		ServerPlayer owner = mentor.ownerPlayer();

		if (owner == null) {
			return false;
		}

		CrossingStage stage = Story.get(owner).crossing();
		return stage == CrossingStage.VOICES || stage == CrossingStage.MEETING;
	}

	/**
	 * False for a copy that should not exist: its owner is online and the story remembers a
	 * different mentor (or none) for this role. Mentors without an owner, or whose owner is offline,
	 * are kept.
	 */
	public static boolean stillWanted(MentorEntity mentor) {
		ServerPlayer owner = mentor.ownerPlayer();
		return owner == null || isWantedCopy(mentor, Story.get(owner));
	}

	/** Whether this mentor is the one the owner's story data points at (any other copy is a duplicate). */
	public static boolean isWantedCopy(MentorEntity mentor, StoryData ownerData) {
		return mentor.getUUID().equals(ownerData.mentor(mentor.role().id()));
	}

	// --- Interaction

	/** The player used (right-clicked) a mentor. */
	public static void onUsed(ServerPlayer player, MentorEntity mentor) {
		MentorRole role = mentor.role();

		if (!mentor.isOwnedBy(player)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.not_yours." + role.id()));
			return;
		}

		if (mentor.isSparring() || Spar.inSpar(player)) {
			return;
		}

		StoryData data = Story.get(player);

		if (data.crossing() == CrossingStage.MEETING) {
			Dialogues.open(player, role.crossingDialogue(), mentor);
		} else if (data.storyPath() == role.path() && chapterFourTalk(data)) {
			// Chapter Four's briefing and report (spec MILESTONE_4.md 8.2, 8.6) have their own dialogue.
			Dialogues.open(player, OldRepublicMod.id(role == MentorRole.JEDI ? "c4_hub_saelen" : "c4_hub_sorn"), mentor);
		} else if (data.storyPath() == role.path()) {
			Dialogues.open(player, role.mentorDialogue(), mentor);
		} else {
			StoryService.mentorSays(player, role, "not_now");
		}
	}

	/** Whether the mentor has Chapter Four business with the player: the briefing (SEEKER) or the report (AFTERMATH). */
	static boolean chapterFourTalk(StoryData data) {
		boolean brief = data.chapter4.stage() == ChapterFourStage.SEEKER && !data.hasFlag(ChapterFourRules.BRIEFED);
		return brief || KaastDuel.reportReady(data);
	}

	/** A player struck a mentor outside a spar: a line, and trust -5 from the owner. Rate-limited by the entity. */
	public static void onAttacked(ServerPlayer player, MentorEntity mentor) {
		MentorRole role = mentor.role();
		StoryService.mentorSays(player, role, "attacked");

		if (mentor.isOwnedBy(player) && Story.get(player).storyPath() == role.path()) {
			StoryService.trust(player, role, -Identity.balance().trustAttackPenalty, "quest.oldrepublic.c2.reason.attacked");
		}
	}

	/** The player used Brenn: the rival's dialogue (it routes on the story state). */
	static boolean onRivalUsed(ServerPlayer player, Mob rival) {
		if (!StoryTags.isOwnedBy(rival, player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.not_yours.brenn"));
			return true;
		}

		if (Spar.inSpar(player)) {
			return false;
		}

		Dialogues.open(player, BRENN_DIALOGUE, rival);
		return true;
	}

	// --- Finding and spawning

	/** The live mentor with this UUID in the overworld, or null if it is not loaded or gone. */
	static @Nullable MentorEntity find(MinecraftServer server, @Nullable UUID id) {
		if (id == null) {
			return null;
		}

		Entity entity = server.overworld().getEntity(id);
		return entity instanceof MentorEntity mentor && mentor.isAlive() ? mentor : null;
	}

	/** The player's mentor for {@code role}, if loaded. */
	static @Nullable MentorEntity mentor(ServerPlayer player, MentorRole role) {
		return find(player.level().getServer(), Story.get(player).mentor(role.id()));
	}

	/**
	 * Spawns a mentor owned by {@code owner}. The story data is pointed at it before it enters the
	 * world, so the duplicate check never removes the new one. The caller commits the data.
	 */
	static @Nullable MentorEntity spawn(ServerLevel level, StoryData data, MentorRole role, ServerPlayer owner, Vec3 pos, float yaw,
			BlockPos anchor, int leash) {
		MentorEntity mentor = MentorEntities.type(role).create(level, EntitySpawnReason.STRUCTURE);

		if (mentor == null) {
			return null;
		}

		mentor.snapTo(pos.x, pos.y, pos.z, yaw, 0f);
		mentor.setYHeadRot(yaw);
		mentor.setYBodyRot(yaw);
		mentor.setOwner(owner.getUUID());
		StoryTags.setOwner(mentor, owner.getUUID());
		mentor.setAnchor(anchor, leash);
		mentor.setPersistenceRequired();
		data.setMentor(role.id(), mentor.getUUID());
		Story.commit(owner, data);
		level.addFreshEntity(mentor);
		return mentor;
	}

	/** Feet position on the ground of a column. */
	static Vec3 ground(ServerLevel level, double x, double z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
		return new Vec3(x, y, z);
	}

	/** Where each mentor stands at the Crossing: half the spacing either side of the meeting point. */
	static Vec3 crossingSpot(ServerLevel level, BlockPos meeting, MentorRole role) {
		double half = StoryService.balance().mentorSpacing / 2.0;
		double x = meeting.getX() + 0.5 + (role == MentorRole.JEDI ? -half : half);
		return ground(level, x, meeting.getZ() + 0.5);
	}

	/**
	 * Makes sure both Crossing mentors stand at the meeting point. With {@code immediate} false a
	 * missing one is replaced only after the owner has been near it for a while (it may simply still
	 * be loading); a stale copy that loads later removes itself.
	 */
	static void ensureCrossingMentors(ServerPlayer player, StoryData data, boolean immediate) {
		if (data.meeting == null || !StoryService.inOverworld(player)) {
			return;
		}

		ServerLevel level = player.level();

		for (MentorRole role : MentorRole.values()) {
			String key = key(player, role);

			if (find(level.getServer(), data.mentor(role.id())) != null) {
				MISSING_SINCE.remove(key);
				continue;
			}

			if (!immediate && !missingLongEnough(player, key)) {
				continue;
			}

			MISSING_SINCE.remove(key);
			Vec3 at = crossingSpot(level, data.meeting, role);
			float yaw = role == MentorRole.JEDI ? -90f : 90f;
			MentorEntity mentor = spawn(level, data, role, player, at, yaw, BlockPos.containing(at), 2);

			if (mentor != null) {
				mentor.setBladeLit(true);
			}
		}
	}

	private static boolean missingLongEnough(ServerPlayer player, String key) {
		long now = StoryService.now(player);
		long since = MISSING_SINCE.computeIfAbsent(key, k -> now);
		return now - since >= StoryService.balance().lostMentorTicks;
	}

	private static String key(ServerPlayer player, MentorRole role) {
		return player.getUUID() + ":" + role.id();
	}

	/**
	 * Brenn Ostrak for a Sith-path player: the loaded one, or (with {@code spawnIfMissing}) a new one
	 * at the camp. Null once he was spared or killed, or when the acolyte entity is not available.
	 */
	static @Nullable Mob rival(ServerPlayer player, StoryData data, boolean spawnIfMissing) {
		if (data.hasFlag(StoryRules.BRENN_SPARED) || data.hasFlag(StoryRules.BRENN_KILLED)) {
			return null;
		}

		ServerLevel overworld = player.level().getServer().overworld();
		Entity existing = data.rival == null ? null : overworld.getEntity(data.rival);

		if (existing instanceof Mob mob && mob.isAlive()) {
			return mob;
		}

		if (!spawnIfMissing || data.camp == null || player.level() != overworld) {
			return null;
		}

		SithAcolyte brenn = EnemyEntities.SITH_ACOLYTE.create(overworld, EntitySpawnReason.STRUCTURE);

		if (brenn == null) {
			return null;
		}

		Vec3 at = ground(overworld, data.camp.getX() + 2.5, data.camp.getZ() - 1.5);
		brenn.snapTo(at.x, at.y, at.z, 180f, 0f);
		// RIVAL mode: neutral, persistent, fights only inside a spar and yields to its owner.
		brenn.setMode(SithAcolyte.Mode.RIVAL);
		brenn.setSkin(SithAcolyte.Skin.BRENN);
		brenn.setOwner(player.getUUID());
		StoryTags.setOwner(brenn, player.getUUID());
		brenn.addTag(StoryTags.RIVAL);
		brenn.setCustomName(Component.translatable("npc.oldrepublic.brenn.name"));
		data.rival = brenn.getUUID();
		Story.commit(player, data);
		overworld.addFreshEntity(brenn);
		return brenn;
	}

	/** Brenn walks out of the story (spared): he heads away and is removed out of sight. */
	static void leave(Mob mob, Vec3 from) {
		Vec3 away = mob.position().subtract(from).horizontal();
		Vec3 dir = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		Vec3 target = mob.position().add(dir.scale(LEAVE_DISTANCE));
		mob.setTarget(null);
		mob.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
		LEAVING.put(mob.getUUID(), mob.level().getGameTime() + LEAVE_TICKS);
	}

	/** The mentor who was not chosen says goodbye, retracts their blade and walks away. */
	static void depart(ServerPlayer owner, MentorEntity mentor, String line) {
		MentorRole role = mentor.role();
		StoryService.mentorSays(owner, role, line);
		mentor.setBladeLit(false);
		mentor.depart(owner.position());
		EffectEvents.around(owner.level(), QuestEffects.MENTOR_DEPART, mentor.position(), null, mentor.getId(), 1f, role.ordinal());
	}

	// --- Sparring

	/**
	 * Starts a spar with a mentor using a duel profile. The mentor lights its blade for it and
	 * retracts it afterwards; {@code onEnd} runs once with the result.
	 *
	 * @return false if the spar could not start (a message is shown)
	 */
	static boolean sparWithMentor(ServerPlayer player, MentorEntity mentor, String profile, SparRules rules, Consumer<SparResult> onEnd) {
		mentor.setProfile(profile);
		mentor.setSparPartner(player);
		mentor.setBladeLit(true);
		boolean started = Spar.start(player, mentor, rules, result -> {
			endMentorSpar(mentor);
			onEnd.accept(result);
		});

		if (!started) {
			endMentorSpar(mentor);
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.spar_unavailable"));
		}

		return started;
	}

	private static void endMentorSpar(MentorEntity mentor) {
		mentor.setSparPartner(null);
		mentor.setProfile(PROFILE_MASTER);

		if (mentor.isAlive()) {
			mentor.setBladeLit(false);
		}
	}

	/** Starts a spar with Brenn. Returns false (with a message) if it could not start. */
	static boolean sparWithRival(ServerPlayer player, Mob rival, SparRules rules, Consumer<SparResult> onEnd) {
		boolean started = Spar.start(player, rival, rules, onEnd);

		if (!started) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.spar_unavailable"));
		}

		return started;
	}

	// --- Reaching out and summoning (journal meditation)

	/**
	 * Whether a finished meditation should open the reach-out dialogue: an Independent who has not
	 * joined anyone yet ("Reach out"), or a path player in the overworld far from their mentor
	 * ("Ask them to come"), with no summon already on the way.
	 */
	public static boolean reachOutAvailable(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (data.crossing() != CrossingStage.DECIDED || !StoryService.inOverworld(player) || data.summonAt >= 0) {
			return false;
		}

		Path path = data.storyPath();

		if (path == Path.INDEPENDENT) {
			return !data.hasFlag(StoryRules.LATE_JOINED);
		}

		if (StoryRules.mentorOf(path) == null) {
			return false;
		}

		double far = StoryService.balance().summonMinDistance;
		return data.camp == null || player.blockPosition().distSqr(data.camp) > far * far;
	}

	/** Opens the reach-out dialogue if it applies. Never counts as the day's solo meditation. */
	public static boolean reachOut(ServerPlayer player) {
		if (reachOutAvailable(player)) {
			Dialogues.open(player, REACH_OUT_DIALOGUE, null);
		}

		return false;
	}

	/** Sends for {@code role}'s mentor: they arrive near the player after the summon delay. */
	static void summon(ServerPlayer player, StoryData data, MentorRole role) {
		data.summonRole = role.id();
		data.summonAt = StoryService.now(player) + StoryService.balance().mentorSummonTicks;
		Story.commit(player, data);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.story.summoned", StoryService.mentorName(role)));
	}

	/**
	 * The summoned mentor arrives near the player: a fresh camp is placed on a nearby open spot and
	 * becomes the anchor (the old camp stays as it is). The mentor is moved there, or replaced if it
	 * cannot be found; Brenn follows the Overseer.
	 */
	static void arrive(ServerPlayer player, StoryData data) {
		MentorRole role = MentorRole.byId(data.summonRole);
		ServerLevel level = player.level();
		StoryBalance b = StoryService.balance();
		MeetingSites.Rules rules = new MeetingSites.Rules(StoryStructures.CAMP_SIZE, StoryStructures.CAMP_SIZE + 2, b.maxSlope,
				Crossing.forbidden(level), Crossing.otherSites(player), b.siteMinSeparation);
		BlockPos camp = MeetingSites.find(level, player.position(), player.getLookAngle(), b.summonRings, b.summonFallback, b.siteAngles,
				b.fallbackForward / 2, rules);
		StoryStructures.placeCamp(level, role, camp, b.placeStructures);

		data.camp = camp;
		data.summonAt = -1;
		data.summonRole = "";
		Story.commit(player, data);

		Vec3 at = ground(level, camp.getX() + 0.5, camp.getZ() + 0.5);
		MentorEntity mentor = mentor(player, role);

		if (mentor != null) {
			mentor.snapTo(at.x, at.y, at.z, mentor.getYRot(), 0f);
			mentor.setAnchor(camp, b.mentorLeash);
		} else {
			spawn(level, data, role, player, at, player.getYRot() + 180f, camp, b.mentorLeash);
		}

		if (role == MentorRole.SITH) {
			Mob rival = rival(player, data, false);

			if (rival != null) {
				Vec3 spot = ground(level, camp.getX() + 2.5, camp.getZ() - 1.5);
				rival.snapTo(spot.x, spot.y, spot.z, rival.getYRot(), 0f);
			}
		}

		StoryService.mentorSays(player, role, "arrived");
	}

	// --- Ticking

	/** Slow per-player tick: Crossing mentors, a lost path mentor, a summon arriving. */
	static void tick(ServerPlayer player, StoryData data) {
		CrossingStage crossing = data.crossing();
		StoryBalance b = StoryService.balance();

		if ((crossing == CrossingStage.VOICES || crossing == CrossingStage.MEETING) && data.meeting != null && StoryService.inOverworld(player)
				&& player.blockPosition().distSqr(data.meeting) <= b.mentorSpawnDistance * b.mentorSpawnDistance) {
			ensureCrossingMentors(player, data, false);
			return;
		}

		if (crossing != CrossingStage.DECIDED || !StoryService.inOverworld(player)) {
			return;
		}

		if (data.summonAt >= 0) {
			if (StoryService.now(player) >= data.summonAt) {
				arrive(player, data);
			}

			return;
		}

		String mentorId = StoryRules.mentorOf(data.storyPath());

		if (mentorId == null || data.camp == null) {
			return;
		}

		MentorRole role = MentorRole.byId(mentorId);
		String key = key(player, role);

		if (find(player.level().getServer(), data.mentor(mentorId)) != null
				|| player.blockPosition().distSqr(data.camp) > b.lostMentorRadius * b.lostMentorRadius) {
			MISSING_SINCE.remove(key);
			return;
		}

		if (missingLongEnough(player, key)) {
			MISSING_SINCE.remove(key);
			Vec3 at = ground(player.level(), data.camp.getX() + 0.5, data.camp.getZ() + 0.5);
			spawn(player.level(), data, role, player, at, 0f, data.camp, b.mentorLeash);
			OldRepublicMod.LOGGER.info("Replaced the lost {} mentor of {}", mentorId, player.getName().getString());
		}
	}

	/** Server tick: removes acolytes that have walked out of the story. */
	static void tickPending(MinecraftServer server) {
		if (LEAVING.isEmpty()) {
			return;
		}

		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();

		for (Iterator<Map.Entry<UUID, Long>> it = LEAVING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			Entity entity = overworld.getEntity(entry.getKey());

			if (entity == null || !entity.isAlive()) {
				it.remove();
			} else if (now >= entry.getValue()) {
				entity.discard();
				it.remove();
			}
		}
	}

	static void forget(ServerPlayer player) {
		for (MentorRole role : MentorRole.values()) {
			MISSING_SINCE.remove(key(player, role));
		}
	}

	static void clear() {
		MISSING_SINCE.clear();
		LEAVING.clear();
	}

	private StoryMentors() {
	}
}

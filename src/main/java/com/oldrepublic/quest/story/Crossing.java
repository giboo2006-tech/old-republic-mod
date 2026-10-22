package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Crossing: the meeting point, the voices' pull, arriving, and the choice (join the Jedi, join
 * the Sith, or walk alone), plus an Independent's one later join. The choice itself is confirmed in
 * the mentors' dialogues, which call {@link #join} and {@link #decline} through dialogue actions.
 *
 * <p>The meeting point is the {@code HEART} of {@code story/crossing}, placed far away through
 * {@link FarSites} (docs/MILESTONE_6.md 3.5); after the choice the chosen side's encampment is stamped
 * over it once the player has walked away. When far placement fails, the M2 near search and the M2
 * travel camp take over.
 */
final class Crossing {
	/**
	 * Asks for the Crossing: {@code story/crossing} {@code story.crossingRange} from the player on
	 * flat ground (a bare ground point when {@code story.placeStructures} is off). Asked again on
	 * later ticks while it is chosen and loaded (the request is idempotent); the meeting point is set
	 * once it is placed, or from the near search when it fails. Overworld only.
	 */
	static void chooseMeeting(ServerPlayer player, StoryData data) {
		if (data.meeting != null || !StoryService.inOverworld(player)) {
			return;
		}

		settle(player, FarSites.request(player.level(), crossingRequest(player)));
	}

	private static FarSites.Request crossingRequest(ServerPlayer player) {
		StoryBalance b = StoryService.balance();
		UUID owner = player.getUUID();
		String key = StorySites.crossingKey(owner);
		FarSites.Request request = b.placeStructures
				? StorySites.request(key, owner, StoryStructures.CROSSING, player.blockPosition(), b.crossingRange)
				: StorySites.point(key, owner, player.blockPosition(), b.crossingRange, b.meetingFootprint);
		return request.preferring(FarSites.Terrain.FLAT).avoiding(otherSites(player), b.siteMinSeparation);
	}

	/**
	 * Takes the far request's answer while the voices call and no meeting point is set: the meeting at
	 * the placed Crossing's heart, the near search when it failed, nothing while it is under way.
	 */
	static void settle(ServerPlayer player, FarSites.Status status) {
		StoryData data = Story.get(player);

		if (data.crossing() != CrossingStage.VOICES || data.meeting != null) {
			return;
		}

		BlockPos heart = StorySites.placedAt(status);

		if (heart != null) {
			meetAt(player, heart);
		} else if (!StorySites.pending(status)) {
			meetNear(player);
		}
	}

	/** Far placement failed (or a command wants the meeting now): the M2 search around the player, no blocks placed. */
	static void meetNear(ServerPlayer player) {
		ServerLevel level = player.level();
		StoryBalance b = StoryService.balance();
		MeetingSites.Rules rules = new MeetingSites.Rules(b.meetingFootprint, b.meetingClearance, b.maxSlope, forbidden(level), otherSites(player),
				b.siteMinSeparation);
		meetAt(player, MeetingSites.find(level, player.position(), player.getLookAngle(), b.meetingRings, b.meetingFallback, b.siteAngles,
				b.fallbackForward, rules));
	}

	/** The meeting point is set: the way is told and noted, and both mentors stand there once its ground is loaded. */
	private static void meetAt(ServerPlayer player, BlockPos meeting) {
		StoryData data = Story.get(player);
		data.meeting = meeting;
		data.meetingDim = player.level().dimension().identifier().toString();
		Story.commit(player, data);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.story.voices_way", FarSites.describe(player.blockPosition(), meeting)));
		StorySites.noteWay(player, JournalEntries.TWO_VOICES, meeting);
		StoryMentors.ensureCrossingMentors(player, Story.get(player), true);
	}

	/**
	 * Stand positions inside spawn protection, when the server switch asks for it to be respected.
	 * The footprint's half-width is added so no part of a site lies in the protected square.
	 */
	static Predicate<BlockPos> forbidden(ServerLevel level) {
		StoryBalance b = StoryService.balance();

		if (!b.respectSpawnProtection || level.dimension() != Level.OVERWORLD) {
			return pos -> false;
		}

		// MinecraftServer exposes no spawn-protection radius getter in 26.2, so the radius comes from story balance.
		int radius = b.spawnProtectionRadius;

		if (radius <= 0) {
			return pos -> false;
		}

		BlockPos spawn = level.getRespawnData().pos();
		int reach = radius + b.encounterFootprint / 2 + 1;
		return pos -> Math.abs(pos.getX() - spawn.getX()) <= reach && Math.abs(pos.getZ() - spawn.getZ()) <= reach;
	}

	/**
	 * Story and mission sites of the other online players, which new sites keep away from (the M2
	 * separation list, extended in M3 3.6 with the Chapter Three sites and the active mission's camp).
	 */
	static List<BlockPos> otherSites(ServerPlayer player) {
		List<BlockPos> sites = new ArrayList<>();

		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player) {
				addSites(sites, Story.get(other));
				MissionData missions = MissionService.view(other);
				MissionData.Active active = missions == null ? null : missions.active;

				if (active != null && active.site != null) {
					sites.add(active.site);
				}
			}
		}

		return sites;
	}

	/**
	 * One player's story sites: the M2 meeting point, camp and raider camp, and the Chapter Three
	 * Courier's Fall, Echo Shrines and Reliquary heart.
	 */
	static void addSites(List<BlockPos> sites, StoryData data) {
		if (data.meeting != null) {
			sites.add(data.meeting);
		}

		if (data.camp != null && !data.camp.equals(data.meeting)) {
			sites.add(data.camp);
		}

		if (data.encounterPos != null) {
			sites.add(data.encounterPos);
		}

		StoryData.Chapter3 c = data.chapter3;

		if (c.fall() != null) {
			sites.add(c.fall());
		}

		sites.addAll(c.shrines());

		if (c.reliquary() != null) {
			sites.add(c.reliquary());
		}
	}

	/** Every tick: the pull toward the meeting point (toward the far request's target while it is placed) while the journal is held. */
	static void tickCalling(ServerPlayer player, StoryData data) {
		if (data.crossing() != CrossingStage.VOICES || !StoryService.callingDue(player)) {
			return;
		}

		BlockPos target = data.meeting != null ? data.meeting : StorySites.pendingPointer(player.level(), StorySites.crossingKey(player.getUUID()));

		if (target != null) {
			StoryService.calling(player, target, QuestEffects.CALLING_VOICES);
		}
	}

	/** Slow tick: the far request until the meeting point is set, arrival there, and an encampment waiting to be stamped. */
	static void tick(ServerPlayer player, StoryData data) {
		if (!StoryService.inOverworld(player)) {
			return;
		}

		if (data.crossing() == CrossingStage.VOICES) {
			if (data.meeting == null) {
				chooseMeeting(player, data);
			} else if (player.blockPosition().distSqr(data.meeting) <= sq(StoryService.balance().arrivalDistance)) {
				arrive(player, data);
			}

			return;
		}

		if (data.hasFlag(StoryRules.CAMP_PENDING)) {
			placePendingCamp(player, data);
		}
	}

	/** The owner reached the meeting point. */
	static void arrive(ServerPlayer player, StoryData data) {
		if (!StoryRules.advanceCrossing(data, CrossingStage.MEETING, CrossingStage.VOICES)) {
			return;
		}

		Story.commit(player, data);
		// From here on alignment and reputation changes are shown (identity's gradual introduction).
		Identity.recordChoice(player, Identity.INTRODUCED);
		Journal.unlockEntry(player, JournalEntries.THE_CROSSING);
		Journal.unlockCodex(player, JournalEntries.CODEX_LIGHT_AND_DARK);
		Journal.unlockCodex(player, JournalEntries.CODEX_JEDI_ORDER);
		Journal.unlockCodex(player, JournalEntries.CODEX_SITH_EMPIRE);
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.arrived"));
	}

	/**
	 * "I will learn from you" confirmed: at the Crossing the choice is made; for an Independent it is
	 * the one late join. Refused (with the reason) when the mentor would not take the player now.
	 */
	static void join(ServerPlayer player, MentorRole role) {
		StoryData data = Story.get(player);
		String blocker = StoryRules.joinBlocker(data, role.path(), Identity.alignment(player), Identity.reputation(player, role.faction()));

		if (blocker != null) {
			player.sendSystemMessage(Component.translatable(blocker));
			return;
		}

		if (data.crossing() == CrossingStage.MEETING) {
			joinAtCrossing(player, data, role);
		} else {
			lateJoin(player, data, role);
		}
	}

	private static void joinAtCrossing(ServerPlayer player, StoryData data, MentorRole role) {
		if (!StoryRules.choose(data, role.path(), StoryService.now(player))) {
			return;
		}

		MentorRole other = role == MentorRole.JEDI ? MentorRole.SITH : MentorRole.JEDI;
		MentorEntity leaving = StoryMentors.mentor(player, other);
		MentorEntity chosen = StoryMentors.mentor(player, role);
		data.camp = data.meeting;
		data.setMentor(other.id(), null);
		data.flags.add(StoryRules.CAMP_PENDING);
		Story.commit(player, data);

		// The story path is stored first, so the path-changed hook sees nothing to correct.
		Identity.setPath(player, role.path(), false);
		choiceRewards(player);
		joinEntries(player, role);

		if (leaving != null) {
			StoryMentors.depart(player, leaving, "farewell");
		}

		if (chosen != null && data.camp != null) {
			Vec3 at = StoryMentors.campSpot(player.level(), data.camp);
			chosen.snapTo(at.x, at.y, at.z, chosen.getYRot(), 0f);
			chosen.setAnchor(data.camp, StoryService.balance().mentorLeash);
			chosen.setBladeLit(false);
		}

		StoryService.mentorSays(player, role, "welcome");
		placePendingCamp(player, data);

		if (role == MentorRole.SITH) {
			StoryMentors.rival(player, data, true);
		}
	}

	private static void lateJoin(ServerPlayer player, StoryData data, MentorRole role) {
		if (!StoryRules.lateJoin(data, role.path())) {
			return;
		}

		Story.commit(player, data);
		Identity.setPath(player, role.path(), false);
		joinEntries(player, role);
		StoryMentors.summon(player, data, role);
	}

	private static void joinEntries(ServerPlayer player, MentorRole role) {
		Journal.unlockEntry(player, role == MentorRole.JEDI ? JournalEntries.THE_WATCHWOMAN : JournalEntries.THE_OVERSEER);
		Journal.unlockCodex(player, role == MentorRole.JEDI ? JournalEntries.CODEX_JEDI_WATCH : JournalEntries.CODEX_SITH_OVERSEERS);
	}

	/** "Neither of you" confirmed: the Independent path, Unbound, and both mentors leave. */
	static void decline(ServerPlayer player) {
		StoryData data = Story.get(player);
		List<MentorEntity> leaving = new ArrayList<>();

		for (MentorRole role : MentorRole.values()) {
			MentorEntity mentor = StoryMentors.mentor(player, role);

			if (mentor != null) {
				leaving.add(mentor);
			}
		}

		if (!StoryRules.choose(data, Path.INDEPENDENT, StoryService.now(player))) {
			return;
		}

		data.jediMentor = null;
		data.sithMentor = null;
		Story.commit(player, data);
		Identity.setPath(player, Path.INDEPENDENT, false);
		Skills.grant(player, SkillIds.UNBOUND);
		choiceRewards(player);
		Journal.unlockEntry(player, JournalEntries.MY_OWN_WAY);

		for (MentorEntity mentor : leaving) {
			StoryMentors.depart(player, mentor, "farewell_alone");
		}
	}

	/** Any confirmed Crossing choice: XP and the {@code crossing} Resonance milestone. */
	private static void choiceRewards(ServerPlayer player) {
		StoryBalance b = StoryService.balance();
		Progression.addXp(player, b.crossingXp, "quest");
		Progression.grantResonanceMilestone(player, "crossing", b.resonanceCrossing);
	}

	/**
	 * Builds the chosen mentor's camp (docs/MILESTONE_6.md 3.5). Over a placed Crossing, the side's
	 * encampment is stamped at the Crossing's origin and rotation once the player is
	 * {@code story.encampmentStampDistance} from it: by far placement, which keeps the area loaded
	 * for the stamp, when no cell of the player's would be lost; otherwise (or when far placement
	 * fails) here once the area is loaded, leaving the player's cells as they are
	 * ({@link StorySites#stampOver}). Without a placed Crossing (an old save, or the near search), the
	 * M2 travel camp goes on the meeting point once the player is clear of its ground.
	 */
	private static void placePendingCamp(ServerPlayer player, StoryData data) {
		if (!data.hasFlag(StoryRules.CAMP_PENDING) || !StoryService.inOverworld(player)) {
			return;
		}

		ServerLevel level = player.level();
		BlockPos meeting = data.meeting;
		SitePiece crossing = meeting == null ? null : StorySites.ownedPiece(level, player.getUUID(), StoryStructures.CROSSING, meeting);

		if (crossing == null || meeting == null) {
			placeTravelCamp(player, data);
			return;
		}

		if (!StorySites.stampDue(player.blockPosition(), meeting, StoryService.balance().encampmentStampDistance)) {
			return;
		}

		Identifier encampment = StoryStructures.encampment(MentorRole.of(data.storyPath()));
		// API-CHECK: Request.at's origin is the template origin (local 0,0,0), as SitePiece.origin() is (J1's RuntimeSites.placeExact; not run here).
		FarSites.Status status = FarSites.request(level,
				FarSites.Request.at(StorySites.encampmentKey(player.getUUID()), player.getUUID(), encampment, crossing.origin(), crossing.rotation())
						// Far placement stamps the whole template, so it only does so where nothing of the player's is in the way.
						.check((l, spot) -> StorySites.keptCells(l, spot.box(), crossing).isEmpty()));

		if (status.placed() || (status.failed() && stampEncampment(level, encampment, crossing))) {
			campStamped(player);
		}
	}

	/**
	 * The pending camp built now, whatever the player's distance (the {@code story camp} command and
	 * the tests): the encampment stamped over the placed Crossing, keeping the player's cells, else
	 * the M2 travel camp once the player is clear of its ground. Returns whether the camp stands now:
	 * false when none is pending, while the player stands inside the Crossing (no tent is built around
	 * them), or while its ground is not loaded.
	 */
	static boolean buildCampNow(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (!data.hasFlag(StoryRules.CAMP_PENDING) || !StoryService.inOverworld(player)) {
			return false;
		}

		ServerLevel level = player.level();
		BlockPos meeting = data.meeting;
		SitePiece crossing = meeting == null ? null : StorySites.ownedPiece(level, player.getUUID(), StoryStructures.CROSSING, meeting);

		if (crossing == null) {
			placeTravelCamp(player, data);
		} else if (!crossing.box().inflatedBy(1).isInside(player.blockPosition())) {
			String key = StorySites.encampmentKey(player.getUUID());

			if (FarSites.poll(level, key).placed() || stampEncampment(level, StoryStructures.encampment(MentorRole.of(data.storyPath())), crossing)) {
				// A far stamp still under way is dropped, so the camp is not stamped twice.
				FarSites.cancel(level, key);
				campStamped(player);
			}
		}

		return !Story.get(player).hasFlag(StoryRules.CAMP_PENDING);
	}

	/** The encampment stamped over the Crossing here, keeping the player's cells: whether it stands (with no template the Crossing is the camp). */
	private static boolean stampEncampment(ServerLevel level, Identifier encampment, SitePiece crossing) {
		return level.getStructureManager().get(encampment).isEmpty() || StorySites.stampOver(level, encampment, crossing);
	}

	/** The M2 camp: the travel camp on the meeting point once the player is clear of its ground, so no tent is built around them. */
	private static void placeTravelCamp(ServerPlayer player, StoryData data) {
		BlockPos camp = data.camp;

		if (camp == null) {
			return;
		}

		int clear = StoryStructures.CAMP_SIZE / 2 + 2;
		BlockPos at = player.blockPosition();

		if (Math.abs(at.getX() - camp.getX()) <= clear && Math.abs(at.getZ() - camp.getZ()) <= clear) {
			return;
		}

		MentorRole role = MentorRole.of(data.storyPath());
		StoryStructures.placeCamp(player.level(), role, camp, StoryService.balance().placeStructures);
		StoryData fresh = Story.get(player);
		fresh.flags.remove(StoryRules.CAMP_PENDING);
		Story.commit(player, fresh);
	}

	/**
	 * The encampment stands over the Crossing: the flag goes, and while the camp is still there (no
	 * summon moved it) the mentor and Brenn are set back on its ground.
	 */
	private static void campStamped(ServerPlayer player) {
		StoryData data = Story.get(player);
		data.flags.remove(StoryRules.CAMP_PENDING);
		Story.commit(player, data);
		StoryData fresh = Story.get(player);

		if (fresh.camp != null && fresh.camp.equals(fresh.meeting)) {
			StoryMentors.settleAtCamp(player, fresh);
		}
	}

	private static double sq(double value) {
		return value * value;
	}

	private Crossing() {
	}
}

package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Crossing: the meeting point, the voices' pull, arriving, and the choice (join the Jedi, join
 * the Sith, or walk alone), plus an Independent's one later join. The choice itself is confirmed in
 * the mentors' dialogues, which call {@link #join} and {@link #decline} through dialogue actions.
 */
final class Crossing {
	/** Picks the meeting point around the player and puts both mentors there at once. */
	static void chooseMeeting(ServerPlayer player, StoryData data) {
		ServerLevel level = player.level();
		StoryBalance b = StoryService.balance();
		MeetingSites.Rules rules = new MeetingSites.Rules(b.meetingFootprint, b.meetingClearance, b.maxSlope, forbidden(level), otherSites(player),
				b.siteMinSeparation);
		BlockPos site = MeetingSites.find(level, player.position(), player.getLookAngle(), b.meetingRings, b.meetingFallback, b.siteAngles,
				b.fallbackForward, rules);
		data.meeting = site;
		data.meetingDim = level.dimension().identifier().toString();
		Story.commit(player, data);
		StoryMentors.ensureCrossingMentors(player, data, true);
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

		// API-CHECK: MinecraftServer#getSpawnProtectionRadius() (probe request in tools/api-probe/story.txt).
		int radius = level.getServer().getSpawnProtectionRadius();

		if (radius <= 0) {
			return pos -> false;
		}

		BlockPos spawn = level.getRespawnData().pos();
		int reach = radius + b.encounterFootprint / 2 + 1;
		return pos -> Math.abs(pos.getX() - spawn.getX()) <= reach && Math.abs(pos.getZ() - spawn.getZ()) <= reach;
	}

	/** Story sites of the other online players, which new sites keep away from. */
	static List<BlockPos> otherSites(ServerPlayer player) {
		List<BlockPos> sites = new ArrayList<>();

		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player) {
				addSites(sites, Story.get(other));
			}
		}

		return sites;
	}

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
	}

	/** Every tick: the pull toward the meeting point while the journal is held. */
	static void tickCalling(ServerPlayer player, StoryData data) {
		if (data.crossing() == CrossingStage.VOICES && data.meeting != null) {
			StoryService.calling(player, data.meeting, QuestEffects.CALLING_VOICES);
		}
	}

	/** Slow tick: arrival at the meeting point, and a camp waiting for the player to step off its ground. */
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

		if (data.hasFlag(StoryRules.CAMP_PENDING) && data.camp != null) {
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
			Vec3 at = StoryMentors.ground(player.level(), data.camp.getX() + 0.5, data.camp.getZ() + 0.5);
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
	 * Places the chosen mentor's camp at the meeting point once the player is clear of its ground,
	 * so no tent is built around them.
	 */
	private static void placePendingCamp(ServerPlayer player, StoryData data) {
		BlockPos camp = data.camp;

		if (camp == null || !data.hasFlag(StoryRules.CAMP_PENDING) || !StoryService.inOverworld(player)) {
			return;
		}

		int clear = StoryStructures.CAMP_SIZE / 2 + 2;
		BlockPos at = player.blockPosition();

		if (Math.abs(at.getX() - camp.getX()) <= clear && Math.abs(at.getZ() - camp.getZ()) <= clear) {
			return;
		}

		MentorRole role = MentorRole.of(data.storyPath());
		StoryStructures.placeCamp(player.level(), role, camp, StoryService.balance().placeStructures);
		data.flags.remove(StoryRules.CAMP_PENDING);
		Story.commit(player, data);
	}

	private static double sq(double value) {
		return value * value;
	}

	private Crossing() {
	}
}

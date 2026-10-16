package com.oldrepublic.quest.story;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * After Smoke on the Ridge: the debrief, the rank trial (a spar with the master, or with Brenn for
 * the Sith), Brenn's fate, and the daily sessions (guided meditation, the Sith's sparring session,
 * and the Atone or Embrace session while alignment holds the player back).
 */
final class Trials {
	static final String FORCE_HEAL = "force_heal";
	static final String FORCE_LIGHTNING = "force_lightning";

	// --- Debrief

	/**
	 * "Tell her what happened": the mentor reacts to the Captain's and the captives' fates, trust and
	 * reputation move by the mentor's creed, and the trial opens.
	 */
	static void debrief(ServerPlayer player) {
		StoryData data = Story.get(player);
		String mentorId = StoryRules.mentorOf(data.storyPath());

		if (data.stage() != PathStage.DEBRIEF || mentorId == null || !data.flags.add(StoryRules.DEBRIEF_HEARD)) {
			return;
		}

		StoryRules.advancePath(data, PathStage.TRIAL, PathStage.DEBRIEF);
		Story.commit(player, data);

		MentorRole role = MentorRole.byId(mentorId);
		boolean jedi = role == MentorRole.JEDI;
		StoryBalance b = StoryService.balance();
		CaptainFate captain = data.captainFate();
		CaptivesFate captives = data.captivesFate();
		String reason = "quest.oldrepublic.c2.reason.debrief";

		StoryService.mentorSays(player, role, "debrief.captain." + captain.name().toLowerCase(Locale.ROOT));
		StoryService.mentorSays(player, role, "debrief.captives." + captives.name().toLowerCase(Locale.ROOT));

		int trust = b.reckoningTrust;
		int rep = b.reckoningRep;

		switch (captain) {
			case SPARED -> {
				trust += jedi ? b.captainSparedJediTrust : b.captainSparedSithTrust;
				rep += jedi ? b.captainSparedJediRep : b.captainSparedSithRep;
			}
			case EXECUTED -> {
				trust += jedi ? b.captainExecutedJediTrust : b.captainExecutedSithTrust;
				rep += jedi ? b.captainExecutedJediRep : b.captainExecutedSithRep;
			}
			case FLED -> trust += jedi ? b.captainFledJediTrust : b.captainFledSithTrust;
			default -> {
			}
		}

		switch (captives) {
			case FREED -> {
				trust += jedi ? b.captivesFreedJediTrust : b.captivesFreedSithTrust;
				rep += jedi ? b.captivesFreedJediRep : b.captivesFreedSithRep;
			}
			case EXPLOITED -> {
				trust += jedi ? b.captivesPaidJediTrust : b.captivesPaidSithTrust;
				rep += jedi ? b.captivesPaidJediRep : b.captivesPaidSithRep;
			}
			case ABANDONED -> trust += jedi ? b.captivesLeftJediTrust : b.captivesLeftSithTrust;
			case NONE -> {
			}
		}

		StoryService.trust(player, role, trust, reason);
		Identity.changeReputation(player, role.faction(), rep, "reckoning.debrief." + role.id(), true);

		if (QuestItems.ownsLedger(player)) {
			StoryData fresh = Story.get(player);

			if (fresh.flags.add(StoryRules.LEDGER_DELIVERED)) {
				Story.commit(player, fresh);
			}

			StoryService.mentorSays(player, role, "debrief.ledger");
		}

		Journal.unlockEntry(player, jedi ? JournalEntries.THE_WATCHWOMAN_LISTENS : JournalEntries.THE_OVERSEER_WEIGHS);
		StoryService.mentorSays(player, role, "debrief.trial");
	}

	// --- The rank trial

	/** Why the trial cannot begin now (a lang key), or null. */
	static @Nullable String trialBlocker(ServerPlayer player, StoryData data) {
		Path path = data.storyPath();
		String mentorId = StoryRules.mentorOf(path);

		if (data.stage() != PathStage.TRIAL || mentorId == null) {
			return "message.oldrepublic.story.blocked.not_now";
		}

		StoryBalance b = StoryService.balance();
		MentorRole role = MentorRole.byId(mentorId);
		StoryRules.TrialRequirements req = new StoryRules.TrialRequirements(b.trialMinLevel, b.trialMinTrust, b.trialMinRep);
		return StoryRules.trialBlocker(path, req, Progression.get(player).level, Identity.trust(player, mentorId),
				Identity.reputation(player, role.faction()), Identity.alignment(player), StoryService.now(player), data.trialRetryAt);
	}

	/** "I am ready for the trial": checks the requirements, then starts the trial spar. */
	static void startTrial(ServerPlayer player, @Nullable Entity speaker) {
		StoryData data = Story.get(player);
		String blocker = trialBlocker(player, data);

		if (blocker != null) {
			player.sendSystemMessage(Component.translatable(blocker));

			if (StoryRules.alignmentBlocksTrial(data.storyPath(), Identity.alignment(player)) && data.stage() == PathStage.TRIAL) {
				Journal.unlockEntry(player, data.storyPath() == Path.JEDI ? JournalEntries.THE_DARK_CLOUDS : JournalEntries.STILL_TOO_SOFT);
			}

			return;
		}

		if (Spar.inSpar(player) || data.hasFlag(StoryRules.TRIAL_WON)) {
			return;
		}

		StoryBalance b = StoryService.balance();

		if (data.storyPath() == Path.JEDI) {
			MentorEntity mentor = speaker instanceof MentorEntity m ? m : StoryMentors.mentor(player, MentorRole.JEDI);

			if (mentor != null) {
				SparRules rules = new SparRules(SparRules.Goal.YIELD, 0, b.masterYield, b.jediTrialDamageScale);

				if (StoryMentors.sparWithMentor(player, mentor, StoryMentors.PROFILE_MASTER, rules, result -> onTrialEnded(player, result))) {
					StoryService.mentorSays(player, MentorRole.JEDI, "trial.begin");
				}
			}

			return;
		}

		Mob rival = StoryMentors.rival(player, data, true);

		if (rival == null) {
			// Brenn is gone for good (or cannot appear): the Overseer judges the student without the duel.
			passTrial(player);
			return;
		}

		SparRules rules = new SparRules(SparRules.Goal.YIELD, 0, b.rivalTrialYield, b.sithTrialDamageScale);

		if (StoryMentors.sparWithRival(player, rival, rules, result -> onTrialEnded(player, result))) {
			StoryService.mentorSays(player, MentorRole.SITH, "trial.begin");
		}
	}

	private static void onTrialEnded(ServerPlayer player, SparResult result) {
		if (player.isRemoved()) {
			return;
		}

		StoryData data = Story.get(player);
		MentorRole role = MentorRole.of(data.storyPath());

		switch (result) {
			case WON -> {
				if (role == MentorRole.JEDI) {
					passTrial(player);
				} else if (data.flags.add(StoryRules.TRIAL_WON)) {
					// Brenn kneels: the Overseer waits for the student to decide.
					Story.commit(player, data);
					StoryService.mentorSays(player, role, "trial.decide");
					Mob rival = StoryMentors.rival(player, data, false);

					if (rival != null) {
						Dialogues.open(player, StoryMentors.BRENN_DIALOGUE, rival);
					}
				}
			}
			case LOST -> {
				data.trialRetryAt = StoryService.now(player) + StoryService.balance().trialRetryTicks;
				Story.commit(player, data);
				StoryService.mentorSays(player, role, "trial.lost");
			}
			case INTERRUPTED -> StoryService.mentorSays(player, role, "spar.interrupted");
			case ENDED -> {
			}
		}
	}

	/** Whether the Sith student won the duel and Brenn's fate is still open. */
	static boolean brennDecisionPending(StoryData data) {
		return data.storyPath() == Path.SITH && data.stage() == PathStage.TRIAL && data.hasFlag(StoryRules.TRIAL_WON)
				&& !data.hasFlag(StoryRules.BRENN_SPARED) && !data.hasFlag(StoryRules.BRENN_KILLED);
	}

	/** Spare Brenn: "Weak. But you won." He leaves the camp; the trial still passes. */
	static void spareBrenn(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (!brennDecisionPending(data)) {
			return;
		}

		Mob rival = StoryMentors.rival(player, data, false);
		data.flags.add(StoryRules.BRENN_SPARED);
		data.rival = null;
		Story.commit(player, data);

		StoryBalance b = StoryService.balance();
		StoryService.shift(player, "sith_trial.brenn", b.brennSpareAlignment, "quest.oldrepublic.c2.reason.brenn_spared");
		StoryService.trust(player, MentorRole.SITH, b.brennSpareTrust, "quest.oldrepublic.c2.reason.brenn_spared");
		StoryService.mentorSays(player, MentorRole.SITH, "trial.spared");

		if (rival != null) {
			if (rival instanceof Yieldable yieldable && yieldable.isYielded()) {
				yieldable.setYielded(false);
			}

			StoryMentors.leave(rival, player.position());
		}

		passTrial(player);
	}

	/** Kill Brenn: the Overseer approves. */
	static void killBrenn(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (!brennDecisionPending(data)) {
			return;
		}

		Mob rival = StoryMentors.rival(player, data, false);
		data.flags.add(StoryRules.BRENN_KILLED);
		data.rival = null;
		Story.commit(player, data);

		StoryBalance b = StoryService.balance();
		StoryService.shift(player, "sith_trial.brenn", b.brennKillAlignment, "quest.oldrepublic.c2.reason.brenn_killed");
		StoryService.trust(player, MentorRole.SITH, b.brennKillTrust, "quest.oldrepublic.c2.reason.brenn_killed");

		if (rival != null) {
			if (rival instanceof Yieldable yieldable && yieldable.isYielded()) {
				// Out of the yielded state, so the blow is not stopped as an attack on someone who surrendered.
				yieldable.setYielded(false);
			}

			rival.kill(player.level());
		}

		StoryService.mentorSays(player, MentorRole.SITH, "trial.killed");
		passTrial(player);
	}

	/** Brenn yielded through the identity feature's yield mechanic while his fate was open. */
	static void onRivalYieldResolved(ServerPlayer player, LivingEntity rival, YieldOutcome outcome) {
		if (!brennDecisionPending(Story.get(player))) {
			return;
		}

		if (outcome == YieldOutcome.EXECUTED) {
			killBrenn(player);
		} else {
			spareBrenn(player);
		}
	}

	/**
	 * Brenn died. While his fate was open that is the kill; otherwise he is simply gone and a new one
	 * appears at the camp when the story needs him again (lesson, trial, session).
	 */
	static void onRivalDied(ServerPlayer player, LivingEntity rival) {
		StoryData data = Story.get(player);

		if (!rival.getUUID().equals(data.rival)) {
			return;
		}

		if (brennDecisionPending(data)) {
			killBrenn(player);
			return;
		}

		data.rival = null;
		Story.commit(player, data);
	}

	/** The trial is passed: rank 2 and its rewards. Also used by {@code /oldrepublic story trial win}. */
	static void passTrial(ServerPlayer player) {
		StoryData data = Story.get(player);
		String mentorId = StoryRules.mentorOf(data.storyPath());

		if (mentorId == null || !StoryRules.advancePath(data, PathStage.COMPLETE, PathStage.TRIAL)) {
			return;
		}

		data.flags.add(StoryRules.TRIAL_WON);
		Story.commit(player, data);

		MentorRole role = MentorRole.byId(mentorId);
		boolean jedi = role == MentorRole.JEDI;
		StoryBalance b = StoryService.balance();
		Identity.promote(player, 2);
		Skills.grant(player, jedi ? FORCE_HEAL : FORCE_LIGHTNING);
		Progression.grantSkillPoints(player, b.trialSp);
		Progression.addXp(player, b.trialXp, "quest");
		Identity.changeReputation(player, role.faction(), b.trialRep, "trial." + role.id(), true);
		Progression.grantResonanceMilestone(player, "first_rank", b.resonanceFirstRank);
		Journal.unlockEntry(player, jedi ? JournalEntries.PADAWAN_OF_THE_WATCH : JournalEntries.APPRENTICE);
		Journal.unlockCodex(player, jedi ? JournalEntries.CODEX_FORCE_HEAL : JournalEntries.CODEX_FORCE_LIGHTNING);
		StoryService.milestone(player, data, QuestEffects.LESSON_CHAPTER);
		StoryService.mentorSays(player, role, "trial.passed");
	}

	// --- Daily sessions

	/** Trust the next step of the path needs (see {@link StoryRules#trustNeeded}). */
	private static int trustNeeded(StoryData data) {
		StoryBalance b = StoryService.balance();
		return StoryRules.trustNeeded(data.stage(), b.lessonTwoTrust, b.trialMinTrust);
	}

	/** Whether today's session with the mentor is open (see {@link StoryRules#sessionAvailable}). */
	static boolean sessionOpen(ServerPlayer player, StoryData data) {
		String mentorId = StoryRules.mentorOf(data.storyPath());
		return mentorId != null && StoryRules.sessionAvailable(data, StoryService.now(player), Identity.trust(player, mentorId), trustNeeded(data));
	}

	/** Whether today's Atone (Jedi) or Embrace (Sith) session is open. */
	static boolean atoneOpen(ServerPlayer player, StoryData data) {
		return StoryRules.atoneAvailable(data, Identity.alignment(player), StoryService.now(player));
	}

	/**
	 * Whether a meditation near the mentor would be a guided one: the Jedi's daily session, or the
	 * Atone / Embrace session of either path. The Sith's daily session is a spar instead.
	 */
	static boolean guidedSessionReady(ServerPlayer player) {
		StoryData data = Story.get(player);
		String mentorId = StoryRules.mentorOf(data.storyPath());

		if (mentorId == null || data.crossing() != CrossingStage.DECIDED) {
			return false;
		}

		MentorEntity mentor = StoryMentors.find(player.level().getServer(), data.mentor(mentorId));
		double radius = StoryService.balance().sessionRadius;

		if (mentor == null || mentor.level() != player.level() || mentor.distanceToSqr(player) > radius * radius) {
			return false;
		}

		return (data.storyPath() == Path.JEDI && sessionOpen(player, data)) || atoneOpen(player, data);
	}

	/**
	 * A meditation near the mentor finished: gives the open guided sessions (the Jedi's daily session
	 * and, while alignment holds the player back, Atone or Embrace).
	 *
	 * @return whether a guided session was given (the solo daily reward is then skipped)
	 */
	static boolean completeGuidedSession(ServerPlayer player) {
		if (!guidedSessionReady(player)) {
			return false;
		}

		StoryData data = Story.get(player);
		MentorRole role = MentorRole.of(data.storyPath());
		StoryBalance b = StoryService.balance();
		boolean session = role == MentorRole.JEDI && sessionOpen(player, data);
		boolean atone = atoneOpen(player, data);
		int day = StoryService.day(player);

		if (session) {
			data.sessionDay = day;
		}

		if (atone) {
			data.atoneDay = day;
		}

		Story.commit(player, data);

		if (session) {
			sessionRewards(player, role);
		}

		if (atone) {
			int amount = role == MentorRole.JEDI ? b.atoneAlignment : -b.atoneAlignment;
			// A major shift with a per-day source id: once per in-game day, beyond the minor cap.
			StoryService.shift(player, "story.atone." + day, amount, "quest.oldrepublic.c2.reason." + (role == MentorRole.JEDI ? "atone" : "embrace"));
			StoryService.trust(player, role, b.atoneTrust, "quest.oldrepublic.c2.reason.atone");
			StoryService.mentorSays(player, role, role == MentorRole.JEDI ? "session.atone" : "session.embrace");
		}

		return true;
	}

	/** The Sith's daily session: a spar with Brenn if he is still around, otherwise a drill with the Overseer. */
	static void startSithSession(ServerPlayer player, @Nullable Entity speaker) {
		StoryData data = Story.get(player);

		if (data.storyPath() != Path.SITH || !sessionOpen(player, data) || Spar.inSpar(player)) {
			return;
		}

		StoryBalance b = StoryService.balance();
		Mob rival = StoryMentors.rival(player, data, true);

		if (rival != null) {
			SparRules rules = new SparRules(SparRules.Goal.TECHNIQUE_HITS, b.techniqueLessonCount, b.rivalLessonYield, 1f);
			StoryMentors.sparWithRival(player, rival, rules, result -> onSithSessionEnded(player, result));
			return;
		}

		MentorEntity mentor = speaker instanceof MentorEntity m ? m : StoryMentors.mentor(player, MentorRole.SITH);

		if (mentor != null) {
			SparRules rules = new SparRules(SparRules.Goal.PARRIES, b.parryLessonCount, b.masterYield, 1f);
			StoryMentors.sparWithMentor(player, mentor, StoryMentors.PROFILE_TRAINING, rules, result -> onSithSessionEnded(player, result));
		}
	}

	private static void onSithSessionEnded(ServerPlayer player, SparResult result) {
		if (player.isRemoved()) {
			return;
		}

		StoryData data = Story.get(player);

		if (result != SparResult.WON || !sessionOpen(player, data)) {
			if (result == SparResult.LOST) {
				StoryService.mentorSays(player, MentorRole.SITH, "session.lost");
			}

			return;
		}

		data.sessionDay = StoryService.day(player);
		Story.commit(player, data);
		sessionRewards(player, MentorRole.SITH);
	}

	private static void sessionRewards(ServerPlayer player, MentorRole role) {
		StoryBalance b = StoryService.balance();
		Progression.addXp(player, b.sessionXp, "quest");
		StoryService.trust(player, role, b.sessionTrust, "quest.oldrepublic.c2.reason.session");
		StoryService.mentorSays(player, role, "session.done");
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.session_done", b.sessionXp));
	}

	private Trials() {
	}
}

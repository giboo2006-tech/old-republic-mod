package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The two lessons of each mentor path. Lesson one teaches Force Pull and counts pulls (Jedi: five
 * pulled items or hostiles; Sith: three hostiles pulled and killed within the window). Lesson two is
 * a spar that teaches a saber form (Jedi: parry three of the master's strikes; Sith: land three heavy
 * attacks or lunges on Brenn). Rewards are guarded by the stage change.
 */
final class Lessons {
	public static final String FORCE_PULL = "force_pull";
	static final String FORM_SORESU = "form_soresu";
	static final String FORM_ATARU = "form_ataru";

	/** Hostile entity UUID to the Sith student who pulled it and when. */
	private record Pulled(UUID player, long time) {
	}

	private static final Map<UUID, Pulled> PULLED = new HashMap<>();
	/** Parries or technique hits in the current lesson-two spar, for the progress line only (the spar decides). */
	private static final Map<UUID, Integer> SPAR_PROGRESS = new HashMap<>();

	/** "Teach me": begins lesson one (Force Pull is granted for free). */
	static void beginLessonOne(ServerPlayer player) {
		StoryData data = Story.get(player);
		Path path = data.storyPath();
		MentorRole role = MentorRole.of(path);

		if (data.stage() != PathStage.LESSON_ONE || data.hasFlag(StoryRules.LESSON_ONE_BEGUN) || StoryRules.mentorOf(path) == null) {
			return;
		}

		String blocker = StoryRules.lessonBlocker(path, Identity.trust(player, role.id()), 0, Identity.alignment(player));

		if (blocker != null) {
			player.sendSystemMessage(Component.translatable(blocker));
			return;
		}

		data.flags.add(StoryRules.LESSON_ONE_BEGUN);
		data.counters.remove(StoryRules.PULL_TARGETS);
		data.counters.remove(StoryRules.PULL_KILLS);
		Story.commit(player, data);
		Skills.grant(player, FORCE_PULL);
		StoryService.mentorSays(player, role, "lesson_one.task");
	}

	/** A Force ability affected something (from {@code QuestHooks.onAbilityHit}). */
	static void onAbilityHit(ServerPlayer player, String abilityId, @Nullable Entity target) {
		if (!FORCE_PULL.equals(abilityId)) {
			return;
		}

		StoryData data = Story.get(player);

		if (data.stage() != PathStage.LESSON_ONE || !data.hasFlag(StoryRules.LESSON_ONE_BEGUN)) {
			return;
		}

		StoryBalance b = StoryService.balance();

		if (data.storyPath() == Path.JEDI) {
			// A batch of loose items (null target) counts as one pull; so does each hostile creature.
			if (target == null || target instanceof Enemy) {
				int count = data.counters.merge(StoryRules.PULL_TARGETS, 1, Integer::sum);
				Story.commit(player, data);
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.progress.pull", Math.min(count, b.pullLessonTargets), b.pullLessonTargets));

				if (count >= b.pullLessonTargets) {
					completeLessonOne(player);
				}
			}
		} else if (data.storyPath() == Path.SITH && target instanceof LivingEntity && target instanceof Enemy) {
			PULLED.put(target.getUUID(), new Pulled(player.getUUID(), StoryService.now(player)));
		}
	}

	/** A creature died: counts for a Sith student who pulled it and struck it down in time. */
	static void onEntityDeath(LivingEntity entity, DamageSource source) {
		Pulled pulled = PULLED.remove(entity.getUUID());

		if (pulled == null || !(source.getEntity() instanceof ServerPlayer player) || !player.getUUID().equals(pulled.player())) {
			return;
		}

		StoryBalance b = StoryService.balance();

		if (StoryService.now(player) - pulled.time() > b.sithPullKillWindowTicks) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.progress.too_slow"));
			return;
		}

		StoryData data = Story.get(player);

		if (data.stage() != PathStage.LESSON_ONE || data.storyPath() != Path.SITH || !data.hasFlag(StoryRules.LESSON_ONE_BEGUN)) {
			return;
		}

		int count = data.counters.merge(StoryRules.PULL_KILLS, 1, Integer::sum);
		Story.commit(player, data);
		player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.progress.pull_kill", Math.min(count, b.sithPullKills), b.sithPullKills));

		if (count >= b.sithPullKills) {
			completeLessonOne(player);
		}
	}

	/** Lesson one done: Lesson two waits. Also used by {@code /oldrepublic story lesson complete}. */
	static void completeLessonOne(ServerPlayer player) {
		StoryData data = Story.get(player);

		if (!StoryRules.advancePath(data, PathStage.LESSON_TWO, PathStage.LESSON_ONE)) {
			return;
		}

		Story.commit(player, data);
		MentorRole role = MentorRole.of(data.storyPath());
		lessonRewards(player, role, "lesson_one");
		Journal.unlockEntry(player, role == MentorRole.JEDI ? JournalEntries.THE_REACH : JournalEntries.WHAT_IS_OWED);
		StoryService.milestone(player, data, QuestEffects.LESSON_ONE);
		StoryService.mentorSays(player, role, "lesson_one.done");
	}

	/** "I am ready": starts the lesson-two spar if trust allows. */
	static void beginLessonTwo(ServerPlayer player, @Nullable Entity speaker) {
		StoryData data = Story.get(player);
		Path path = data.storyPath();

		if (data.stage() != PathStage.LESSON_TWO || StoryRules.mentorOf(path) == null || Spar.inSpar(player)) {
			return;
		}

		MentorRole role = MentorRole.of(path);
		StoryBalance b = StoryService.balance();
		String blocker = StoryRules.lessonBlocker(path, Identity.trust(player, role.id()), b.lessonTwoTrust, Identity.alignment(player));

		if (blocker != null) {
			player.sendSystemMessage(Component.translatable(blocker));
			return;
		}

		boolean started;
		SPAR_PROGRESS.remove(player.getUUID());

		if (role == MentorRole.JEDI) {
			MentorEntity mentor = speaker instanceof MentorEntity m ? m : StoryMentors.mentor(player, role);

			if (mentor == null) {
				return;
			}

			SparRules rules = new SparRules(SparRules.Goal.PARRIES, b.parryLessonCount, b.masterYield, 1f);
			started = StoryMentors.sparWithMentor(player, mentor, StoryMentors.PROFILE_TRAINING, rules, result -> onLessonTwoEnded(player, result));
		} else {
			Mob rival = StoryMentors.rival(player, data, true);

			if (rival == null) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.story.spar_unavailable"));
				return;
			}

			SparRules rules = new SparRules(SparRules.Goal.TECHNIQUE_HITS, b.techniqueLessonCount, b.rivalLessonYield, 1f);
			started = StoryMentors.sparWithRival(player, rival, rules, result -> onLessonTwoEnded(player, result));
		}

		if (started) {
			StoryData fresh = Story.get(player);

			if (fresh.flags.add(StoryRules.LESSON_TWO_BEGUN)) {
				Story.commit(player, fresh);
			}

			StoryService.mentorSays(player, role, "lesson_two.begin");
		}
	}

	private static void onLessonTwoEnded(ServerPlayer player, SparResult result) {
		SPAR_PROGRESS.remove(player.getUUID());

		if (player.isRemoved()) {
			return;
		}

		MentorRole role = MentorRole.of(Story.get(player).storyPath());

		switch (result) {
			case WON -> completeLessonTwo(player);
			case LOST -> StoryService.mentorSays(player, role, "lesson_two.lost");
			case INTERRUPTED -> StoryService.mentorSays(player, role, "spar.interrupted");
			case ENDED -> {
			}
		}
	}

	/** Lesson two done: the form is taught and Smoke on the Ridge (or the debrief) follows. */
	static void completeLessonTwo(ServerPlayer player) {
		StoryData data = Story.get(player);
		PathStage next = StoryRules.afterLessonTwo(data);

		if (!StoryRules.advancePath(data, next, PathStage.LESSON_TWO)) {
			return;
		}

		Story.commit(player, data);
		MentorRole role = MentorRole.of(data.storyPath());
		Skills.grant(player, role == MentorRole.JEDI ? FORM_SORESU : FORM_ATARU);
		lessonRewards(player, role, "lesson_two");
		Journal.unlockEntry(player, role == MentorRole.JEDI ? JournalEntries.THE_STILL_BLADE : JournalEntries.RELENTLESS);
		Journal.unlockCodex(player, JournalEntries.CODEX_SABER_FORMS);
		StoryService.milestone(player, data, QuestEffects.LESSON_TWO);
		StoryService.mentorSays(player, role, "lesson_two.done");

		if (next == PathStage.RECKONING) {
			Reckoning.begin(player, Story.get(player));
		}
	}

	private static void lessonRewards(ServerPlayer player, MentorRole role, String lesson) {
		StoryBalance b = StoryService.balance();
		Progression.addXp(player, b.lessonXp, "quest");
		StoryService.trust(player, role, b.lessonTrust, "quest.oldrepublic.c2.reason." + lesson);
		Identity.changeReputation(player, role.faction(), b.lessonRep, lesson + "." + role.id(), true);
	}

	/** Progress lines during the lesson-two spar (the spar itself counts and decides). */
	static void onSaberTechnique(ServerPlayer player, String technique, @Nullable LivingEntity target, boolean hit) {
		if (!hit || !Spar.inSpar(player)) {
			return;
		}

		StoryData data = Story.get(player);

		if (data.stage() != PathStage.LESSON_TWO) {
			return;
		}

		StoryBalance b = StoryService.balance();
		boolean jedi = data.storyPath() == Path.JEDI;
		boolean counts = jedi ? "parry".equals(technique) : "heavy".equals(technique) || "lunge".equals(technique);

		if (!counts) {
			return;
		}

		int needed = jedi ? b.parryLessonCount : b.techniqueLessonCount;
		int count = SPAR_PROGRESS.merge(player.getUUID(), 1, Integer::sum);
		String key = jedi ? "message.oldrepublic.story.progress.parry" : "message.oldrepublic.story.progress.technique";
		player.sendOverlayMessage(Component.translatable(key, Math.min(count, needed), needed));
	}

	/** Slow tick: forgets pulls whose kill window has passed. */
	static void tick(ServerPlayer player) {
		if (PULLED.isEmpty()) {
			return;
		}

		long now = StoryService.now(player);
		int window = StoryService.balance().sithPullKillWindowTicks;

		for (Iterator<Pulled> it = PULLED.values().iterator(); it.hasNext();) {
			Pulled pulled = it.next();

			if (pulled.player().equals(player.getUUID()) && now - pulled.time() > window) {
				it.remove();
			}
		}
	}

	static void forget(ServerPlayer player) {
		SPAR_PROGRESS.remove(player.getUUID());
		PULLED.values().removeIf(pulled -> pulled.player().equals(player.getUUID()));
	}

	static void clear() {
		PULLED.clear();
		SPAR_PROGRESS.clear();
	}

	private Lessons() {
	}
}

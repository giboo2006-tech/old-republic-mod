package com.oldrepublic.quest.story;

import java.util.Arrays;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;

import net.minecraft.server.level.ServerPlayer;

/**
 * The story's dialogue conditions and actions, used by {@code crossing_*}, {@code mentor_*},
 * {@code brenn} and {@code independent}. Every action checks the story state again on the server,
 * so a stale or forged choice changes nothing.
 *
 * <p>Conditions (the argument is the JSON value as text):
 * <ul>
 * <li>{@code story:stage}: the path stage is one of a comma-separated list ({@code "lesson_one,lesson_two"}).</li>
 * <li>{@code story:crossing}: the Crossing stage ({@code "meeting"}).</li>
 * <li>{@code story:flag}: a story flag is set ({@code "!flag"}: not set).</li>
 * <li>{@code story:can_join}: {@code "jedi"} or {@code "sith"} would take the player now.</li>
 * <li>{@code story:lesson_two}, {@code story:trial}: lesson two or the trial may begin.</li>
 * <li>{@code story:session}, {@code story:atone}: today's session, or Atone / Embrace, is open.</li>
 * <li>{@code story:brenn_pending}: Brenn kneels and his fate is open.</li>
 * <li>{@code story:summon}: the player may send for their mentor.</li>
 * </ul>
 */
final class StoryDialogue {
	private static final String NOT_NOW = "message.oldrepublic.story.blocked.not_now";

	static void init() {
		DialogueConditions.register("story:stage", (p, speaker, arg) -> {
			String stage = Story.get(p).stage().name().toLowerCase(Locale.ROOT);
			return Arrays.stream(arg.split(",")).map(String::trim).anyMatch(stage::equals) ? null : NOT_NOW;
		});
		DialogueConditions.register("story:crossing", (p, speaker, arg) ->
				Story.get(p).crossing().name().equalsIgnoreCase(arg.trim()) ? null : NOT_NOW);
		DialogueConditions.register("story:flag", (p, speaker, arg) -> {
			boolean negate = arg.startsWith("!");
			boolean present = Story.get(p).hasFlag(negate ? arg.substring(1) : arg);
			return present != negate ? null : NOT_NOW;
		});
		DialogueConditions.register("story:can_join", (p, speaker, arg) -> canJoin(p, MentorRole.byId(arg.trim())));
		DialogueConditions.register("story:lesson_two", (p, speaker, arg) -> lessonTwoBlocker(p));
		DialogueConditions.register("story:trial", (p, speaker, arg) -> Trials.trialBlocker(p, Story.get(p)));
		DialogueConditions.register("story:session", (p, speaker, arg) ->
				Trials.sessionOpen(p, Story.get(p)) ? null : "message.oldrepublic.story.blocked.session_done");
		DialogueConditions.register("story:atone", (p, speaker, arg) ->
				Trials.atoneOpen(p, Story.get(p)) ? null : "message.oldrepublic.story.blocked.atone_not_needed");
		DialogueConditions.register("story:brenn_pending", (p, speaker, arg) -> Trials.brennDecisionPending(Story.get(p)) ? null : NOT_NOW);
		DialogueConditions.register("story:summon", (p, speaker, arg) -> StoryMentors.reachOutAvailable(p) ? null : NOT_NOW);

		DialogueActions.register("story:join_jedi", (p, speaker) -> Crossing.join(p, MentorRole.JEDI));
		DialogueActions.register("story:join_sith", (p, speaker) -> Crossing.join(p, MentorRole.SITH));
		DialogueActions.register("story:decline", (p, speaker) -> Crossing.decline(p));
		DialogueActions.register("story:lesson_one", (p, speaker) -> Lessons.beginLessonOne(p));
		DialogueActions.register("story:lesson_two", Lessons::beginLessonTwo);
		DialogueActions.register("story:debrief", (p, speaker) -> Trials.debrief(p));
		DialogueActions.register("story:trial", Trials::startTrial);
		DialogueActions.register("story:session", Trials::startSithSession);
		DialogueActions.register("story:brenn_spare", (p, speaker) -> Trials.spareBrenn(p));
		DialogueActions.register("story:brenn_kill", (p, speaker) -> Trials.killBrenn(p));
		DialogueActions.register("story:summon", (p, speaker) -> summon(p));
	}

	private static @Nullable String canJoin(ServerPlayer player, MentorRole role) {
		StoryData data = Story.get(player);
		return StoryRules.joinBlocker(data, role.path(), Identity.alignment(player), Identity.reputation(player, role.faction()));
	}

	private static @Nullable String lessonTwoBlocker(ServerPlayer player) {
		StoryData data = Story.get(player);
		String mentorId = StoryRules.mentorOf(data.storyPath());

		if (mentorId == null || data.stage() != PathStage.LESSON_TWO) {
			return NOT_NOW;
		}

		return StoryRules.lessonBlocker(data.storyPath(), Identity.trust(player, mentorId), StoryService.balance().lessonTwoTrust,
				Identity.alignment(player));
	}

	/** "Ask them to come": the path mentor (a path player far from the camp) is summoned. */
	private static void summon(ServerPlayer player) {
		StoryData data = Story.get(player);
		Path path = data.storyPath();

		if ((path == Path.JEDI || path == Path.SITH) && StoryMentors.reachOutAvailable(player)) {
			StoryMentors.summon(player, data, MentorRole.of(path));
		}
	}

	private StoryDialogue() {
	}
}

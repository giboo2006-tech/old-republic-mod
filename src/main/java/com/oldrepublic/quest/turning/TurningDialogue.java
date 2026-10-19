package com.oldrepublic.quest.turning;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.DialogueRedirects;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The turning's dialogue conditions, actions and redirects (spec MILESTONE_5.md 6.11, 8.2 to 8.5).
 *
 * <p>Conditions: {@code turn:offered fall|return} (that offer stands), {@code turn:stage <stage>[:<task>]} (the step, and
 * for the Return's task its phase), {@code turn:holocron} (the Fall's holocron is carried), {@code turn:held} (the
 * Return's prisoners wait at the post), {@code rank4:ready} (rank 4's first unmet requirement, as {@code key|need|have}).
 *
 * <p>Actions: {@code turn:accept}, {@code turn:decline}, {@code turn:holocron}, {@code turn:deliver}, {@code turn:other},
 * {@code turn:free trick|talk}, {@code turn:resolve release|trophy}, {@code turn:rite}, {@code rank4:ceremony} (a Master's)
 * and {@code rank4:ceremony <1-6>} (a Darth's, with the name), {@code rank4:wayfarer}, {@code rank4:mentor} (the mentor's
 * usual dialogue).
 *
 * <p>Redirects: while a Jedi's Fall is at its task, {@code c4_ansa} and {@code c4_ansa_meet} open {@code turn_ansa}, and
 * {@code c4_kaddor} and {@code c4_kaddor_meet} open {@code turn_kaddor}.
 */
final class TurningDialogue {
	static void init() {
		DialogueConditions.register("turn:offered", TurningDialogue::offered);
		DialogueConditions.register("turn:stage", TurningDialogue::stage);
		DialogueConditions.register("turn:holocron", (p, speaker, arg) -> FallTask.holocronReason(p));
		DialogueConditions.register("turn:held", (p, speaker, arg) -> ReturnTask.held(p) ? null : "turning.oldrepublic.cond.not_held");
		DialogueConditions.register("rank4:ready", (p, speaker, arg) -> RankFour.blocker(p));

		DialogueActions.register("turn:accept", (p, speaker) -> TurningOffers.accept(p));
		DialogueActions.register("turn:decline", (p, speaker) -> TurningOffers.decline(p));
		DialogueActions.register("turn:holocron", (p, speaker) -> FallTask.giveHolocron(p));
		DialogueActions.register("turn:deliver", (p, speaker) -> FallTask.deliver(p));
		DialogueActions.register("turn:other", FallTask::other);
		DialogueActions.registerArg("turn:free", (p, speaker, arg) -> ReturnTask.free(p, speaker, arg.trim()));
		DialogueActions.registerArg("turn:resolve", (p, speaker, arg) -> Confrontations.resolve(p, speaker, arg.trim()));
		DialogueActions.register("turn:rite", (p, speaker) -> Rites.rite(p));
		DialogueActions.register("rank4:ceremony", (p, speaker) -> RankFour.ceremony(p, speaker, 0));
		DialogueActions.registerArg("rank4:ceremony", (p, speaker, arg) -> RankFour.ceremony(p, speaker, parse(arg)));
		DialogueActions.register("rank4:wayfarer", (p, speaker) -> Mentors.wayfarer(p));
		DialogueActions.register("rank4:mentor", (p, speaker) -> {
			if (speaker != null) {
				Mentors.usualDialogue(p, speaker);
			}
		});

		for (String id : new String[] {"c4_ansa", "c4_ansa_meet", "c4_kaddor", "c4_kaddor_meet"}) {
			DialogueRedirects.register(id, FallTask::redirect);
		}

		StoryMentors.setOtherPathTalk(Mentors::otherPathTalk);
	}

	/** {@code turn:offered fall|return}: that offer stands now. */
	private static @Nullable String offered(ServerPlayer p, @Nullable Entity speaker, String arg) {
		StoryData.Turning t = Story.get(p).turning;
		TurningKind kind = TurningKind.byId(arg.trim());
		return kind != null && t.stage() == TurningStage.OFFERED && t.kind() == kind ? null : "turning.oldrepublic.cond.not_offered";
	}

	/**
	 * {@code turn:stage <stage>[:<task>]}: the turning is at that step (and, with a task, at that task; for the Return's
	 * task the phase: 0 held, 1 escort, 2 aboard).
	 */
	private static @Nullable String stage(ServerPlayer p, @Nullable Entity speaker, String arg) {
		String[] parts = arg.trim().split(":", 2);
		TurningStage stage = TurningStage.byId(parts[0]);
		StoryData.Turning t = Story.get(p).turning;

		if (stage == null || t.stage() != stage) {
			return "turning.oldrepublic.cond.stage";
		}

		if (parts.length < 2) {
			return null;
		}

		int task = t.kind() == TurningKind.RETURN && stage == TurningStage.TASK ? TurningRules.phase(t.task()) : t.task();
		return String.valueOf(task).equals(parts[1].trim()) ? null : "turning.oldrepublic.cond.stage";
	}

	private static int parse(String arg) {
		try {
			return Integer.parseInt(arg.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private TurningDialogue() {
	}
}

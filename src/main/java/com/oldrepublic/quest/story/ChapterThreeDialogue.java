package com.oldrepublic.quest.story;

import java.util.Arrays;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.holocron.HolocronReturns;

import net.minecraft.server.level.ServerPlayer;

/**
 * Chapter Three's and rank 3's dialogue conditions and actions (spec 6.8, W2 plan 4.3), used by
 * {@code mentor_*}, the officers, {@code c3_collector_*}, {@code c3_send_word}, {@code pathfinder}
 * and W3's gatekeeper. Every action checks the chapter again on the server, so a stale or forged
 * choice changes nothing.
 *
 * <p>Conditions (stage arguments are {@link ChapterThreeStage} names, any case):
 * <ul>
 * <li>{@code c3:stage}: the stage is at least the argument;</li>
 * <li>{@code c3:stage_is}: the stage is one of a comma-separated list;</li>
 * <li>{@code c3:leads}: at least n leads;</li>
 * <li>{@code c3:report_ready}: {@code mentor} (Jedi, Sith) or {@code officer} (Independent): the report is open;</li>
 * <li>{@code c3:vasch_message}: Vasch's recorded message is due (the gatekeeper's node);</li>
 * <li>{@code rank3:ready}: the rank 3 or Pathfinder requirements are met; the reason names the first unmet one;</li>
 * <li>{@code c3:returnable}: {@code jedi}, {@code sith}, {@code republic} or {@code imperial}: the player carries a
 * holocron that receiver takes back (4.6), so a Jedi's sealed Sith holocron opens Saelen's option too.</li>
 * </ul>
 * Actions: {@code c3:accept_lesson}, {@code c3:report} (mentor) and {@code c3:report <republic|imperial>}
 * (officer), {@code c3:send_word}, {@code c3:stance <outcome>}, {@code c3:give_back},
 * {@code c3:vasch_offer}, {@code c3:vasch_message}, {@code collector:resolve <outcome>},
 * {@code rank3:ceremony}.
 */
final class ChapterThreeDialogue {
	private static final String NOT_NOW = "message.oldrepublic.story.blocked.not_now";
	private static final String REPORT_NOT_READY = "message.oldrepublic.story.blocked.report_not_ready";
	private static final String NOT_RETURNABLE = "message.oldrepublic.story.blocked.returnable";

	static void init() {
		DialogueConditions.register("c3:stage", (p, speaker, arg) -> {
			ChapterThreeStage at = ChapterThreeRules.parseStage(arg);
			return at != null && Story.get(p).chapter3.stage().ordinal() >= at.ordinal() ? null : NOT_NOW;
		});
		DialogueConditions.register("c3:stage_is", (p, speaker, arg) -> {
			ChapterThreeStage stage = Story.get(p).chapter3.stage();
			return Arrays.stream(arg.split(",")).map(ChapterThreeRules::parseStage).anyMatch(s -> s == stage) ? null : NOT_NOW;
		});
		DialogueConditions.register("c3:leads", (p, speaker, arg) -> leadsBlocker(p, arg));
		DialogueConditions.register("c3:report_ready", (p, speaker, arg) -> {
			StoryData data = Story.get(p);
			return ChapterThreeRules.reportReady(data, data.storyPath(), !"officer".equalsIgnoreCase(arg.trim())) ? null : REPORT_NOT_READY;
		});
		DialogueConditions.register("c3:vasch_message", (p, speaker, arg) ->
				VaschBeats.messageDue(Story.get(p), ChapterThreeService.balance()) ? null : NOT_NOW);
		DialogueConditions.register("rank3:ready", (p, speaker, arg) -> RankThree.blocker(p));
		// API-CHECK: HolocronReturns.candidate(Player, String) is W3's in-tree class (not a Phase 0 contract); re-check after W3's merge.
		DialogueConditions.register("c3:returnable", (p, speaker, arg) ->
				HolocronReturns.candidate(p, arg.trim().toLowerCase(Locale.ROOT)) != null ? null : NOT_RETURNABLE);

		DialogueActions.register("c3:accept_lesson", (p, speaker) -> ChapterThreeOpenings.acceptLesson(p));
		DialogueActions.register("c3:report", (p, speaker) -> CollectorRewards.reportToMentor(p));
		DialogueActions.registerArg("c3:report", (p, speaker, arg) -> CollectorRewards.reportToOfficer(p, arg));
		DialogueActions.register("c3:send_word", (p, speaker) -> CollectorRewards.sendWord(p));
		DialogueActions.registerArg("c3:stance", (p, speaker, arg) -> CollectorRewards.stance(p, arg));
		DialogueActions.register("c3:give_back", (p, speaker) -> CollectorRewards.giveBack(p));
		DialogueActions.register("c3:vasch_offer", (p, speaker) -> CollectorRewards.vaschOffer(p));
		DialogueActions.register("c3:vasch_message", (p, speaker) -> VaschBeats.messageHeard(p));
		DialogueActions.registerArg("collector:resolve", (p, speaker, arg) -> CollectorRewards.resolve(p, arg));
		DialogueActions.register("rank3:ceremony", RankThree::ceremony);
	}

	private static @Nullable String leadsBlocker(ServerPlayer p, String arg) {
		int need;

		try {
			need = Integer.parseInt(arg.trim());
		} catch (NumberFormatException e) {
			return NOT_NOW;
		}

		return Story.get(p).chapter3.leads().size() >= need ? null : NOT_NOW;
	}

	private ChapterThreeDialogue() {
	}
}

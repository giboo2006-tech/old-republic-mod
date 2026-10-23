package com.oldrepublic.quest.story;

import java.util.Arrays;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.vendor.Vendors;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Chapter Four's dialogue conditions and actions (spec MILESTONE_4.md 8.10), used by
 * {@code c4_*}, the hub hosts' dialogues, the vendors' and the officers' requisition option. Every
 * action checks the chapter again on the server, so a stale or forged choice changes nothing.
 *
 * <p>Conditions (stage arguments are {@link ChapterFourStage} names, any case):
 * <ul>
 * <li>{@code c4:stage}: the stage is at least the argument; {@code c4:stage_is}: one of a comma-separated list;</li>
 * <li>{@code c4:clues}: at least n clues;</li>
 * <li>{@code credits}: the player carries at least n credits (inventory and a near ship's hold);</li>
 * <li>{@code c4:brief_ready}, {@code c4:report_ready}, {@code c4:yield}: the briefing, the report or Kaast's choice is open;</li>
 * <li>{@code c4:not_now}: the expedition may be declined; {@code c4:both_doors}: an Independent may still take Both Doors;</li>
 * <li>{@code c4:crystal}: a second crystal waits for its hue; {@code c4:relic}: the relic can be handed in;</li>
 * <li>{@code c4:arc} (mission id): that arc mission can be taken from this host now;</li>
 * <li>{@code c4:smuggler}: the smuggler re-issues a lost motivator today; {@code c4:peaceful}: the world is on Peaceful.</li>
 * </ul>
 * Actions: {@code c4:brief}, {@code c4:report}, {@code c4:not_now}, {@code c4:both_doors <greenwatch|proving>},
 * {@code c4:mission <id>}, {@code c4:crystal <blue|green|red>}, {@code c4:turn_in}, {@code c4:reissue},
 * {@code kaast:resolve <spare|strip|end>}, {@code vendor:open <stock id>}, {@code c4:smuggler} (the officers' motivator option).
 */
final class ChapterFourDialogue {
	private static final String NOT_NOW = "message.oldrepublic.story.blocked.not_now";
	private static final String CREDITS = "message.oldrepublic.story.blocked.credits";
	private static final Identifier SMUGGLER = OldRepublicMod.id("c4_smuggler");

	static void init() {
		DialogueConditions.register("c4:stage", (p, speaker, arg) -> {
			ChapterFourStage at = ChapterFourRules.parseStage(arg);
			return at != null && Story.get(p).chapter4.stage().ordinal() >= at.ordinal() ? null : NOT_NOW;
		});
		DialogueConditions.register("c4:stage_is", (p, speaker, arg) -> {
			ChapterFourStage stage = Story.get(p).chapter4.stage();
			return Arrays.stream(arg.split(",")).map(ChapterFourRules::parseStage).anyMatch(s -> s == stage) ? null : NOT_NOW;
		});
		DialogueConditions.register("c4:clues", (p, speaker, arg) -> ChapterFour.clues(p) >= integer(arg) ? null : NOT_NOW);
		DialogueConditions.register("credits", (p, speaker, arg) -> Credits.count(p, true) >= integer(arg) ? null : CREDITS + "|" + integer(arg));
		DialogueConditions.register("c4:brief_ready", (p, speaker, arg) -> {
			StoryData data = Story.get(p);
			return data.chapter4.stage() == ChapterFourStage.SEEKER && !data.hasFlag(ChapterFourRules.BRIEFED) ? null : NOT_NOW;
		});
		DialogueConditions.register("c4:report_ready", (p, speaker, arg) -> KaastDuel.reportReady(Story.get(p)) ? null : NOT_NOW);
		DialogueConditions.register("c4:yield", (p, speaker, arg) -> KaastDuel.choiceOpen(Story.get(p)) ? null : NOT_NOW);
		DialogueConditions.register("c4:not_now", (p, speaker, arg) -> ChapterFourRules.mayDeclineExpedition(Story.get(p).chapter4) ? null : NOT_NOW);
		DialogueConditions.register("c4:both_doors", (p, speaker, arg) -> WorldArcs.bothDoorsOpen(p, arg.trim()) ? null : NOT_NOW);
		DialogueConditions.register("c4:crystal", (p, speaker, arg) -> WorldArcs.crystalPending(Story.get(p)) ? null : NOT_NOW);
		DialogueConditions.register("c4:relic", (p, speaker, arg) -> WorldArcs.relicReady(p) ? null : NOT_NOW);
		DialogueConditions.register("c4:arc", (p, speaker, arg) -> WorldArcs.arcMissionBlocker(p, arg.trim(), speaker));
		DialogueConditions.register("c4:smuggler", (p, speaker, arg) -> ChapterFourRepairs.smugglerReissueDue(p) ? null : NOT_NOW);
		DialogueConditions.register("c4:peaceful", (p, speaker, arg) -> ChapterFourService.peaceful(p) ? null : NOT_NOW);

		DialogueActions.register("c4:brief", (p, speaker) -> ChapterFourRepairs.brief(p));
		DialogueActions.register("c4:report", (p, speaker) -> KaastDuel.report(p));
		DialogueActions.register("c4:not_now", (p, speaker) -> WorldArcs.notNow(p));
		DialogueActions.registerArg("c4:both_doors", WorldArcs::bothDoors);
		DialogueActions.registerArg("c4:mission", (p, speaker, arg) -> startArcMission(p, speaker, arg.trim()));
		DialogueActions.registerArg("c4:crystal", (p, speaker, arg) -> WorldArcs.chooseCrystal(p, arg.trim().toLowerCase(Locale.ROOT)));
		DialogueActions.register("c4:turn_in", (p, speaker) -> WorldArcs.turnIn(p));
		DialogueActions.register("c4:reissue", (p, speaker) -> ChapterFourRepairs.reissueSmugglerMotivator(p));
		DialogueActions.registerArg("kaast:resolve", (p, speaker, arg) -> KaastDuel.resolve(p, arg));
		DialogueActions.registerArg("vendor:open", ChapterFourDialogue::openVendor);
		DialogueActions.register("c4:smuggler", ChapterFourDialogue::openSmuggler);
	}

	/**
	 * {@code c4:smuggler}: an outpost officer, asked about a motivator, calls over the smuggler who
	 * trades off the books there ({@code c4_smuggler}, spoken through the officer): "A Part Off the
	 * Books" and its once-a-day re-issue (8.2). Only while the repairs run.
	 */
	private static void openSmuggler(ServerPlayer p, @Nullable Entity speaker) {
		if (Story.get(p).chapter4.stage() == ChapterFourStage.REPAIRS || ChapterFourRepairs.smugglerReissueDue(p)) {
			Dialogues.open(p, SMUGGLER, speaker);
		}
	}

	/** {@code c4:mission <id>}: an arc mission from its host, checked again ({@code c4:arc}). */
	private static void startArcMission(ServerPlayer p, @Nullable Entity speaker, String id) {
		if (WorldArcs.arcMissionBlocker(p, id, speaker) == null) {
			MissionService.startStory(p, id, speaker);
		}
	}

	/** {@code vendor:open <stock id>}: the speaker's vendor screen (the vendor checks the gate itself). */
	private static void openVendor(ServerPlayer p, @Nullable Entity speaker, String stock) {
		Identifier id = Identifier.tryParse(stock.contains(":") ? stock.trim() : "oldrepublic:" + stock.trim());

		if (id != null && speaker != null) {
			Vendors.open(p, speaker, id);
		}
	}

	private static int integer(String arg) {
		try {
			return Integer.parseInt(arg.trim());
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	private ChapterFourDialogue() {
	}
}

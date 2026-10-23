package com.oldrepublic.client.quest.turning;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Identity;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.turning.RankFourChecklistPayload;
import com.oldrepublic.quest.turning.TurningRules;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * The rank 4 checklist as this client last received it, and the lines W4's Standing panel draws from it (spec
 * MILESTONE_5.md 8.4, 8.5, 18): the checklist in the M3 rank 3 style, the "Turned" line under the rank and a Darth's
 * chosen name. The server sends the checklist when it changes ({@code rank_four_checklist}); it is hidden before the
 * first one and after a disconnect.
 *
 * <pre>{@code
 * RankFourChecklistPayload list = RankFourClient.checklist();
 * if (list.visible()) {
 *     header(RankFourClient.header(list));
 *     for (RankFourChecklistPayload.Entry e : list.entries()) requirement(RankFourClient.line(e), e.met());
 *     if (!list.done() && list.allMet()) wrapped(RankFourClient.ready(list));
 * }
 * }</pre>
 */
public final class RankFourClient {
	private static RankFourChecklistPayload checklist = RankFourChecklistPayload.HIDDEN;

	/** The last checklist received: {@link RankFourChecklistPayload#HIDDEN} before the first one and after a disconnect. */
	public static RankFourChecklistPayload checklist() {
		return checklist;
	}

	/** One requirement's line: {@code turning.oldrepublic.rank4.req.<id>} with what is needed and what the player has. */
	public static Component line(RankFourChecklistPayload.Entry entry) {
		return Component.translatable(entry.key(), entry.need(), entry.have());
	}

	/** The checklist's header: "Toward Master", "Toward Darth" or "Toward Wayfarer". */
	public static Component header(RankFourChecklistPayload list) {
		return Component.translatable("turning.oldrepublic.rank4.header." + kindId(list.kind()));
	}

	/** The line under a checklist whose every requirement holds: who holds the ceremony, and where. */
	public static Component ready(RankFourChecklistPayload list) {
		String path = switch (list.kind()) {
			case RankFourChecklistPayload.DARTH -> "sith";
			case RankFourChecklistPayload.WAYFARER -> "independent";
			default -> "jedi";
		};
		return Component.translatable("turning.oldrepublic.rank4.ready." + path);
	}

	/** Which deeds count toward rank 4 (a Sith also counts Ulvaine's writ), for the deeds line's hint. */
	public static Component deedsHint(RankFourChecklistPayload list) {
		return Component.translatable(list.kind() == RankFourChecklistPayload.DARTH ? "turning.oldrepublic.rank4.deeds.hint.sith"
				: "turning.oldrepublic.rank4.deeds.hint");
	}

	/** "Turned on day N" (the in-game day of the last rite, counted from 1 as the Standing log does), or null if never. */
	public static @Nullable Component turnedLine(Player player) {
		StoryData data = Story.peek(player);

		if (data == null || data.turning.turnedDay() < 0) {
			return null;
		}

		return Component.translatable("turning.oldrepublic.standing.turned", data.turning.turnedDay() + 1);
	}

	/** A Darth's rank title with the name chosen at the ceremony ("Darth Valcrane"), or null for anyone else. */
	public static @Nullable Component darthTitle(Player player) {
		for (int n = 1; n <= TurningRules.DARTH_NAMES; n++) {
			if (Identity.hasChoice(player, TurningRules.darthChoice(n))) {
				return Component.translatable("turning.oldrepublic.standing.darth", Component.translatable("rank4.oldrepublic.darth_name." + n));
			}
		}

		return null;
	}

	private static String kindId(int kind) {
		return switch (kind) {
			case RankFourChecklistPayload.DARTH -> "darth";
			case RankFourChecklistPayload.WAYFARER -> "wayfarer";
			default -> "master";
		};
	}

	static void receive(RankFourChecklistPayload payload) {
		checklist = payload;
	}

	static void reset() {
		checklist = RankFourChecklistPayload.HIDDEN;
	}

	private RankFourClient() {
	}
}

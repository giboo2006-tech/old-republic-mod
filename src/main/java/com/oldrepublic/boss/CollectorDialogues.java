package com.oldrepublic.boss;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.Dialogues;

import net.minecraft.server.level.ServerPlayer;

/**
 * The Collector's dialogue wiring (spec 6.8, 8.3, 8.9). W2 writes {@code c3_collector_meet},
 * {@code c3_collector_yield} and {@code c3_collector_stance}; W5 opens them from the session and owns
 * {@code reliquary_heart}, the rematch dialogue of a resolved heart.
 *
 * <ul>
 * <li>{@link Dialogues#ON_CLOSE}: when the owner's meeting dialogue closes (a choice, Escape, walking
 * away), the fight begins (8.3).</li>
 * <li>Actions {@code boss:call_echo} and {@code boss:unchained}: the owner calls the echo (8.9).</li>
 * <li>Conditions {@code boss:echo} and {@code boss:unchained} (value {@code true}, or {@code false} for
 * the opposite): whether the echo can be called now, and whether Unchained is unlocked.</li>
 * </ul>
 */
final class CollectorDialogues {
	private static final String FAIL = "dialogue.oldrepublic.reliquary_heart.fail.";

	static void init() {
		Dialogues.ON_CLOSE.register((player, dialogue, chose) -> {
			if (ArenaSession.MEET_DIALOGUE.equals(dialogue)) {
				ArenaSession session = BossArenas.sessionOwnedBy(player);

				if (session != null) {
					session.onMeetingClosed(player);
				}
			}
		});

		DialogueActions.register("boss:call_echo", (p, speaker) -> BossArenas.callEcho(p, false));
		DialogueActions.register("boss:unchained", (p, speaker) -> BossArenas.callEcho(p, true));

		DialogueConditions.register("boss:echo", (p, speaker, arg) -> check(BossArenas.echoAvailable(p), arg, "echo"));
		DialogueConditions.register("boss:unchained", (p, speaker, arg) -> check(unchained(p), arg, "unchained"));
	}

	private static boolean unchained(ServerPlayer p) {
		return BossArenas.echoAvailable(p) && BossArenas.unchainedAvailable(p);
	}

	/** Null (met) when {@code value} matches the wanted value ({@code arg} "false" wants false), else the reason key. */
	private static String check(boolean value, String arg, String reason) {
		boolean wanted = !"false".equalsIgnoreCase(arg.trim());
		return value == wanted ? null : FAIL + reason;
	}

	private CollectorDialogues() {
	}
}

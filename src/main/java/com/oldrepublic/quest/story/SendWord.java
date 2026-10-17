package com.oldrepublic.quest.story;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * "Send word" (spec 5.3, 6.6): an Independent whose Chapter Three report is open while both armies
 * hold them Hostile has no officer to report to, so a sneak-use of the journal opens
 * {@code c3_send_word} and the journal carries the report (Frontier standing instead of an army's).
 * The check reads synced data, so the journal item asks it on both sides.
 */
public final class SendWord {
	/** The dialogue the journal opens. */
	public static final Identifier DIALOGUE = OldRepublicMod.id("c3_send_word");

	/** Whether the journal may send word for this player now (client and server). */
	public static boolean eligible(Player p) {
		StoryData data = Story.peek(p);
		return data != null && ChapterThreeRules.sendWordEligible(data, data.storyPath(), Identity.tier(p, Faction.REPUBLIC),
				Identity.tier(p, Faction.SITH_EMPIRE));
	}

	/** Opens the dialogue (server side, after {@link #eligible}). */
	public static void open(ServerPlayer p) {
		Dialogues.open(p, DIALOGUE, null);
	}

	private SendWord() {
	}
}

package com.oldrepublic.quest;

import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Unlocks journal and codex entries and hands out the journal. */
public final class Journal {
	/** Unlocks a journal entry (once) and tells the owner's client the journal was updated. */
	public static boolean unlockEntry(ServerPlayer player, String id) {
		return unlock(player, JournalEntries.entryFlag(id), JournalEntries.ENTRIES.indexOf(id), 0);
	}

	/** Unlocks a codex entry (once) and tells the owner's client the journal was updated. */
	public static boolean unlockCodex(ServerPlayer player, String id) {
		return unlock(player, JournalEntries.codexFlag(id), JournalEntries.CODEX.indexOf(id), 1);
	}

	private static boolean unlock(ServerPlayer player, String flag, int index, int variant) {
		ForceData data = Progression.get(player);

		if (!data.flags.add(flag)) {
			return false;
		}

		data.counters.merge(QuestRules.JOURNAL_SEQ, 1, Integer::sum);
		Progression.commit(player, data);
		EffectEvents.toPlayer(player, QuestEffects.JOURNAL_ENTRY, player.position(), null, player.getId(), index, variant);
		return true;
	}

	/**
	 * Gives the player a journal unless they already carry one (never duplicates). Falls back to
	 * dropping it at their feet if the inventory is full.
	 */
	public static boolean giveIfMissing(ServerPlayer player) {
		if (QuestItems.ownsJournal(player)) {
			return false;
		}

		player.getInventory().placeItemBackInInventory(new ItemStack(QuestItems.WANDERERS_JOURNAL));
		return true;
	}

	private Journal() {
	}
}

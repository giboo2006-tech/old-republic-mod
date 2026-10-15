package com.oldrepublic.quest;

import com.oldrepublic.registry.ModRegistry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** Items owned by the quest feature. */
public final class QuestItems {
	public static final JournalItem WANDERERS_JOURNAL = ModRegistry.item("wanderers_journal", JournalItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	public static boolean isJournal(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() == WANDERERS_JOURNAL;
	}

	/** Whether the player holds the journal in either hand. */
	public static boolean holdsJournal(ServerPlayer player) {
		return isJournal(player.getItemInHand(InteractionHand.MAIN_HAND)) || isJournal(player.getItemInHand(InteractionHand.OFF_HAND));
	}

	public static boolean ownsJournal(ServerPlayer player) {
		return player.getInventory().contains(QuestItems::isJournal);
	}

	static void init() {
	}

	private QuestItems() {
	}
}

package com.oldrepublic.quest;

import java.util.function.Consumer;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Poachers' Ledger: Captain Hessa Dray's account book, a lore item read like a written book.
 * Its pages are fixed lang text (who buys the crystals; a lead for later chapters), so the item
 * carries no data and cannot be edited or forged.
 */
public class LedgerItem extends Item {
	/** Lang key prefix of the pages: {@code item.oldrepublic.poachers_ledger.page<n>}. */
	public static final String PAGE_PREFIX = "item.oldrepublic.poachers_ledger.page";
	public static final int PAGES = 4;

	/** Opens the reading screen. Set by the client initialiser; a no-op on a dedicated server. */
	public static Consumer<Player> clientOpener = player -> {
	};

	public LedgerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			clientOpener.accept(player);
		}

		return InteractionResult.SUCCESS;
	}
}

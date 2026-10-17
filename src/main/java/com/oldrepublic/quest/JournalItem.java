package com.oldrepublic.quest;

import java.util.function.Consumer;

import com.oldrepublic.quest.story.SendWord;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Wanderer's Journal. Using it opens the journal screen on the client; holding it is what
 * meditation and the Calling look for. The item itself carries no data: entries live in the
 * player's synced Force data. A sneak-use sends word instead when an Independent's Chapter Three
 * report has no officer to go to ({@link SendWord}); both sides check the synced story data.
 */
public class JournalItem extends Item {
	/**
	 * Opens the journal screen. Set by the client initialiser so this common class never refers
	 * to client code; a no-op on a dedicated server.
	 */
	public static Consumer<Player> clientOpener = player -> {
	};

	public JournalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown() && SendWord.eligible(player)) {
			if (player instanceof ServerPlayer serverPlayer) {
				SendWord.open(serverPlayer);
			}

			return InteractionResult.SUCCESS;
		}

		if (level.isClientSide()) {
			clientOpener.accept(player);
		}

		return InteractionResult.SUCCESS;
	}
}

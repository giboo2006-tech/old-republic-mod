package com.oldrepublic.holocron;

import java.util.function.Consumer;

import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A holocron item (spec 4.1, 4.9). Using it replays its gatekeeper for the owner of a current copy
 * and never grants anything again; a stale copy and anyone else's copy stay silent. The tooltip
 * names the gatekeeper; the owner's status line (current, stale, sealed) is added on the client.
 */
public class HolocronItem extends Item {
	private final HolocronKind kind;

	public HolocronItem(Properties properties, HolocronKind kind) {
		super(properties);
		this.kind = kind;
	}

	public HolocronKind kind() {
		return kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			replay(serverPlayer, player.getItemInHand(hand));
		}

		return InteractionResult.SUCCESS;
	}

	/** Replays the gatekeeper of a current, owned copy, or tells the player why it is silent. */
	public static void replay(ServerPlayer p, ItemStack stack) {
		HolocronStamp stamp = stack.get(ModComponents.HOLOCRON);

		if (stamp == null || HolocronCatalog.find(stamp.id()) == null || !stamp.owner().equals(p.getUUID())) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.item.not_yours"));
			return;
		}

		HolocronData data = HolocronService.data(p);

		if (!data.isAttuned(stamp.id()) || stamp.serial() != data.serial(stamp.id()) || data.returned.contains(stamp.id())) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.item.stale"));
			return;
		}

		if (HolocronAttunement.pending(p)) {
			// The attunement moment owns the dialogue screen; a replay would cancel it.
			return;
		}

		Dialogues.open(p, HolocronCatalog.dialogue(stamp.id()), null);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		HolocronStamp stamp = stack.get(ModComponents.HOLOCRON);
		HolocronCatalog.Entry entry = stamp == null ? null : HolocronCatalog.find(stamp.id());

		if (entry == null) {
			tooltip.accept(Component.translatable("holocron.oldrepublic.item.blank").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return;
		}

		tooltip.accept(Component.translatable("holocron.oldrepublic.item.gatekeeper", Component.translatable(HolocronCatalog.nameKey(entry.id())))
				.withStyle(style -> style.withColor(HolocronCatalog.tint(entry.kind()) & 0xFFFFFF)));

		if (entry.archive()) {
			tooltip.accept(Component.translatable("holocron.oldrepublic.item.archive").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}
}

package com.oldrepublic.mission;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;

/**
 * Using the props of the owner's mission camp (spec 5.4): R1's supply crate (the "leave them"
 * confirmation once the guards are down), R2's crate and I1's book (the mission item once the chief
 * or fence is dealt with, again if it was lost), and I1's campfire with the book in hand (the burn
 * confirmation). The prop is the block at its template anchor or right next to it. Other players and
 * other blocks are left to vanilla.
 */
final class MissionInteractions {
	/** How far (per axis) from its anchor cell a prop still counts as used. */
	private static final int ANCHOR_REACH = 1;

	static void register() {
		// Server side only: SUCCESS stops the block's own use (a chest opening) for the mission prop.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !level.isClientSide() && player instanceof ServerPlayer p && onUse(p, hit.getBlockPos())) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
	}

	/** Handles a use of the block at {@code pos}; true when it was one of the player's mission props. */
	static boolean onUse(ServerPlayer p, BlockPos pos) {
		Active active = MissionService.get(p).active;
		MissionDefinition base = active == null ? null : MissionService.baseDefinition(active);

		if (active == null || base == null || active.site == null || !SiteKind.MISSION_SITE.id().equals(active.siteKind)
				|| !active.has(MissionData.FLAG_SPAWNED)) {
			return false;
		}

		SitePiece piece = MissionSites.piece(p.level(), active.site);

		if (piece == null) {
			return false;
		}

		if (base.objective() == Objective.RESCUE && near(piece, SiteAnchors.Anchor.CRATE, pos)) {
			if (active.stage == Stage.DECIDE) {
				Dialogues.open(p, OldRepublicMod.id("r1_leave_confirm"), null);
			} else {
				p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.crate_guarded"));
			}

			return true;
		}

		if (base.objective() != Objective.RECOVER) {
			return false;
		}

		Item item = MissionItems.byId(base.item());
		boolean book = item == MissionItems.FENCE_BOOK;

		if (near(piece, book ? SiteAnchors.Anchor.BOOK : SiteAnchors.Anchor.CRATE, pos)) {
			if (active.stage == Stage.ACTIVE && !active.has(MissionData.FLAG_CHIEF_DONE)) {
				p.sendOverlayMessage(Component.translatable(book ? "message.oldrepublic.mission.book_guarded" : "message.oldrepublic.mission.crate_locked"));
			} else if (!MissionService.issueItem(p, base)) {
				p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.prop_empty"));
			}

			return true;
		}

		if (book && active.stage == Stage.RETURN && near(piece, SiteAnchors.Anchor.FIRE, pos) && MissionItems.carries(p, item)) {
			Dialogues.open(p, OldRepublicMod.id("i1_burn_confirm"), null);
			return true;
		}

		return false;
	}

	private static boolean near(SitePiece piece, SiteAnchors.Anchor anchor, BlockPos pos) {
		BlockPos at = SiteAnchors.find(piece, anchor);
		return at != null && Math.abs(at.getX() - pos.getX()) <= ANCHOR_REACH && Math.abs(at.getY() - pos.getY()) <= ANCHOR_REACH
				&& Math.abs(at.getZ() - pos.getZ()) <= ANCHOR_REACH;
	}

	private MissionInteractions() {
	}
}

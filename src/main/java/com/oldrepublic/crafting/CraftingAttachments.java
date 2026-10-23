package com.oldrepublic.crafting;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/** The player's crafting attachment (spec MILESTONE_4.md 12.1). The value is immutable, so it needs no seal. */
public final class CraftingAttachments {
	/** Persistent, kept on death, synced to its owner only. */
	public static final AttachmentType<CraftingData> CRAFTING = AttachmentRegistry.create(
			OldRepublicMod.id("crafting"),
			builder -> builder
					.initializer(() -> CraftingData.EMPTY)
					.persistent(CraftingData.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodec(CraftingData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	public static CraftingData get(Player p) {
		return p.getAttachedOrCreate(CRAFTING);
	}

	public static void set(Player p, CraftingData data) {
		p.setAttached(CRAFTING, data);
	}

	public static void init() {
	}

	private CraftingAttachments() {
	}
}

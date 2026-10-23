package com.oldrepublic.travel;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/** The player's travel attachment (spec MILESTONE_4.md 12.1). The value is immutable, so it needs no seal. */
public final class TravelAttachments {
	/** Persistent, kept on death, synced to its owner only. */
	public static final AttachmentType<TravelData> TRAVEL = AttachmentRegistry.create(
			OldRepublicMod.id("travel"),
			builder -> builder
					.initializer(() -> TravelData.EMPTY)
					.persistent(TravelData.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodec(TravelData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	public static TravelData get(Player p) {
		return p.getAttachedOrCreate(TRAVEL);
	}

	public static void set(Player p, TravelData data) {
		p.setAttached(TRAVEL, data);
	}

	public static void init() {
	}

	private TravelAttachments() {
	}
}

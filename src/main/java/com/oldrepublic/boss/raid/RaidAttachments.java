package com.oldrepublic.boss.raid;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/** The {@code oldrepublic:raid} player attachment (spec MILESTONE_5.md 12.4, 14.1): the player's {@link RaidData}. */
public final class RaidAttachments {
	/** Persistent, kept on death and dimension change, synced to its player only (the rank 4 checklist reads it). */
	public static final AttachmentType<RaidData> RAID = AttachmentRegistry.create(
			OldRepublicMod.id("raid"),
			builder -> builder
					.initializer(() -> RaidData.EMPTY)
					.persistent(RaidData.CODEC)
					.copyOnDeath()
					// An immutable record: the synced value is never changed in place, so it needs no frozen view.
					.syncWith(ByteBufCodecs.fromCodec(RaidData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	/** Loads the class (registers the attachment). Called from {@code RaidFeature}. */
	public static void init() {
	}

	private RaidAttachments() {
	}
}

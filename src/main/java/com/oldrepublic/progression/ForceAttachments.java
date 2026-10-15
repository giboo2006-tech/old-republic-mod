package com.oldrepublic.progression;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ForceAttachments {
	/**
	 * The player's Force progression. Persistent, kept on death and dimension change, and synced
	 * only to the player it belongs to.
	 */
	public static final AttachmentType<ForceData> FORCE = AttachmentRegistry.create(
			OldRepublicMod.id("force"),
			builder -> builder
					.initializer(ForceData::new)
					.persistent(ForceData.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodec(ForceData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	public static void init() {
	}

	private ForceAttachments() {
	}
}

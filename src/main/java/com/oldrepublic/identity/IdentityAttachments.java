package com.oldrepublic.identity;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/** The identity attachment ({@code oldrepublic:identity}). */
public final class IdentityAttachments {
	/**
	 * The player's identity. Persistent, kept on death and dimension change, and synced only to the
	 * player it belongs to.
	 */
	public static final AttachmentType<IdentityData> IDENTITY = AttachmentRegistry.create(
			OldRepublicMod.id("identity"),
			builder -> builder
					.initializer(IdentityData::new)
					.persistent(IdentityData.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodec(IdentityData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	static void init() {
	}

	private IdentityAttachments() {
	}
}

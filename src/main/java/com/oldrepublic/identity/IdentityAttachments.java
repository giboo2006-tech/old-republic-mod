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
					.initializer(() -> new IdentityData().seal())
					.persistent(IdentityData.CODEC.<IdentityData>xmap(IdentityData::seal, data -> data))
					.copyOnDeath()
					// Encoded later on the network thread: send the frozen copy, never the live object.
					.syncWith(ByteBufCodecs.fromCodec(IdentityData.CODEC).<IdentityData>map(data -> data, IdentityData::syncView), AttachmentSyncPredicate.targetOnly())
	);

	static void init() {
	}

	private IdentityAttachments() {
	}
}

package com.oldrepublic.holocron;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/** The {@code oldrepublic:holocrons} player attachment (spec 12.1). */
public final class HolocronAttachments {
	/** The player's {@link HolocronData}. Persistent, kept on death and dimension change, synced to the owner only. */
	public static final AttachmentType<HolocronData> HOLOCRONS = AttachmentRegistry.create(
			OldRepublicMod.id("holocrons"),
			builder -> builder
					.initializer(() -> new HolocronData().seal())
					.persistent(HolocronData.CODEC.<HolocronData>xmap(HolocronData::seal, data -> data))
					.copyOnDeath()
					// Encoded later on the network thread: send the frozen copy, never the live object.
					.syncWith(ByteBufCodecs.fromCodec(HolocronData.CODEC).<HolocronData>map(data -> data, HolocronData::syncView),
							AttachmentSyncPredicate.targetOnly())
	);

	static void init() {
	}

	private HolocronAttachments() {
	}
}

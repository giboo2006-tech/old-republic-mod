package com.oldrepublic.mission;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/** The {@code oldrepublic:missions} player attachment (spec sections 5.7 and 12.1). */
public final class MissionAttachments {
	/** Missions, contracts and discovery. Persistent, kept on death and dimension change, synced to its owner only. */
	public static final AttachmentType<MissionData> MISSIONS = AttachmentRegistry.create(
			OldRepublicMod.id("missions"),
			builder -> builder
					.initializer(() -> new MissionData().seal())
					.persistent(MissionData.CODEC.<MissionData>xmap(MissionData::seal, data -> data))
					.copyOnDeath()
					// Encoded later on the network thread: send the frozen copy, never the live object.
					.syncWith(ByteBufCodecs.fromCodec(MissionData.CODEC).<MissionData>map(data -> data, MissionData::syncView),
							AttachmentSyncPredicate.targetOnly())
	);

	/** Loads the class so the attachment registers; called from {@link MissionFeature#init()}. */
	static void init() {
	}

	private MissionAttachments() {
	}
}

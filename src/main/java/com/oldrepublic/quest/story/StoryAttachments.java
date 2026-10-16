package com.oldrepublic.quest.story;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/** The {@code oldrepublic:story} player attachment (spec section 11.1). */
public final class StoryAttachments {
	/** Chapter Two progress. Persistent, kept on death and dimension change, synced to its owner only. */
	public static final AttachmentType<StoryData> STORY = AttachmentRegistry.create(
			OldRepublicMod.id("story"),
			builder -> builder
					.initializer(StoryData::new)
					.persistent(StoryData.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodec(StoryData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	public static void init() {
	}

	private StoryAttachments() {
	}
}

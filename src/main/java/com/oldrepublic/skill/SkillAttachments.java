package com.oldrepublic.skill;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

/** The {@code oldrepublic:skills} player attachment. */
public final class SkillAttachments {
	/** Owned nodes, slots and form. Persistent, kept on death and dimension change, synced to the owner only. */
	public static final AttachmentType<SkillData> SKILLS = AttachmentRegistry.create(
			OldRepublicMod.id("skills"),
			builder -> builder
					.initializer(() -> new SkillData().seal())
					.persistent(SkillData.CODEC.<SkillData>xmap(SkillData::seal, data -> data))
					.copyOnDeath()
					// Encoded later on the network thread: send the frozen copy, never the live object.
					.syncWith(ByteBufCodecs.fromCodec(SkillData.CODEC).<SkillData>map(data -> data, SkillData::syncView), AttachmentSyncPredicate.targetOnly())
	);

	static void init() {
	}

	private SkillAttachments() {
	}
}

package com.oldrepublic.saber.forge;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * The player's Forge attachment (MILESTONE_7.md 5.1): the designs they have studied. The value is
 * immutable, so it needs no seal. Its sync to the owner is the Forge's only server-to-client message:
 * the screen reads the synced copy to show which designs are known.
 */
public final class ForgeAttachments {
	/** Persistent, kept on death, synced to its owner only. */
	public static final AttachmentType<ForgeData> FORGE = AttachmentRegistry.create(
			OldRepublicMod.id("forge"),
			builder -> builder
					.initializer(() -> ForgeData.EMPTY)
					.persistent(ForgeData.CODEC)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.fromCodec(ForgeData.CODEC), AttachmentSyncPredicate.targetOnly())
	);

	public static ForgeData get(Player p) {
		return p.getAttachedOrCreate(FORGE);
	}

	public static void set(Player p, ForgeData data) {
		p.setAttached(FORGE, data);
	}

	/** Loads the class, registering {@link #FORGE}. */
	public static void init() {
	}

	private ForgeAttachments() {
	}
}

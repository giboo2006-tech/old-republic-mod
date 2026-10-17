package com.oldrepublic.quest.story;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Read access to a player's Chapter Two progress for other features, on both sides (the attachment
 * is synced to its owner). Writers live in the quest feature.
 */
public final class Story {
	/** Flag set when an Independent finished Chapter Two ("No Master"). */
	public static final String NO_MASTER = "no_master";

	/** The live server-side data. Call {@link #commit} after changing it. */
	public static StoryData get(ServerPlayer player) {
		return player.getAttachedOrCreate(StoryAttachments.STORY);
	}

	/** Stores and syncs the data to the owning client. */
	public static void commit(ServerPlayer player, StoryData data) {
		player.setAttached(StoryAttachments.STORY, data.copy().seal());
	}

	/** The synced data, or null on a client that has not received it yet. */
	public static @Nullable StoryData peek(Player player) {
		return player.getAttached(StoryAttachments.STORY);
	}

	/**
	 * Lang key of the lesson or task the player is on (for the Standing tab and the journal), or
	 * null when there is none.
	 */
	public static @Nullable String activeLessonKey(Player player) {
		StoryData data = peek(player);
		return data == null ? null : StoryRules.objectiveKey(data);
	}

	private Story() {
	}
}

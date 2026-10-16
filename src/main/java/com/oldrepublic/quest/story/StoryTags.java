package com.oldrepublic.quest.story;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;

/**
 * Story markers on entities, stored as vanilla entity tags (persistent, visible to
 * {@code /tag} for debugging). Other features read them through these helpers, for example a Sith
 * Acolyte that must know it is Brenn (a rival owned by one player) or a Poacher Captain that must
 * know whose decision her yield is.
 *
 * <ul>
 * <li>{@code oldrepublic.owner.<uuid>}: the player the entity belongs to (story NPCs, encounter mobs).</li>
 * <li>{@code oldrepublic.encounter}: part of Smoke on the Ridge (crew, Captain and captives).</li>
 * <li>{@code oldrepublic.crew}: a hostile crew member (poachers and the hired acolyte).</li>
 * <li>{@code oldrepublic.rival}: Brenn Ostrak (a Sith Acolyte in rival mode).</li>
 * <li>{@code oldrepublic.captain}: Captain Hessa Dray of this player's encounter.</li>
 * </ul>
 */
public final class StoryTags {
	public static final String OWNER_PREFIX = "oldrepublic.owner.";
	public static final String ENCOUNTER = "oldrepublic.encounter";
	public static final String CREW = "oldrepublic.crew";
	public static final String RIVAL = "oldrepublic.rival";
	public static final String CAPTAIN = "oldrepublic.captain";

	/** Marks {@code entity} as belonging to {@code owner} (replacing any earlier owner). */
	public static void setOwner(Entity entity, UUID owner) {
		for (String old : List.copyOf(entity.entityTags())) {
			if (old.startsWith(OWNER_PREFIX)) {
				entity.removeTag(old);
			}
		}

		entity.addTag(OWNER_PREFIX + owner);
	}

	/** The player this story entity belongs to, or null. */
	public static @Nullable UUID owner(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(OWNER_PREFIX)) {
				try {
					return UUID.fromString(tag.substring(OWNER_PREFIX.length()));
				} catch (IllegalArgumentException e) {
					return null;
				}
			}
		}

		return null;
	}

	public static boolean isOwnedBy(Entity entity, UUID player) {
		return entity.entityTags().contains(OWNER_PREFIX + player);
	}

	/** Part of Smoke on the Ridge (poachers, the hired acolyte, the Captain, the captives). */
	public static boolean isEncounterMember(Entity entity) {
		return entity.entityTags().contains(ENCOUNTER);
	}

	/** A hostile crew member of Smoke on the Ridge, whose death counts toward the crew's defeat. */
	public static boolean isCrew(Entity entity) {
		return entity.entityTags().contains(CREW);
	}

	/** Brenn Ostrak: an acolyte in rival mode, owned by one Sith-path player. */
	public static boolean isRival(Entity entity) {
		return entity.entityTags().contains(RIVAL);
	}

	public static boolean isCaptain(Entity entity) {
		return entity.entityTags().contains(CAPTAIN);
	}

	private StoryTags() {
	}
}

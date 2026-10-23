package com.oldrepublic.quest;

/**
 * Stages of the introductory questline "Echoes in the Stone". Stored by ordinal in
 * {@link com.oldrepublic.progression.ForceData#questStage}, so only append new stages.
 */
public enum QuestStage {
	/** Force-sensitive but unaware. No HUD, no powers. */
	STIRRING,
	/** Awakened by the dream. Must meditate with the journal to calm the disturbance. */
	FOCUS,
	/** Learned Force Push. Must push hostile creatures to practise. */
	PRACTICE,
	/** Feels the call of a crystal. Must find the crystal cave. */
	CALLING,
	/** Found the cave. Must complete the Trial of Reflection. */
	TRIAL,
	/** Holds an attuned crystal. Must gather hilt parts and build the lightsaber. */
	CONSTRUCTION,
	/** First chapter complete. */
	COMPLETE;

	public static QuestStage of(int ordinal) {
		QuestStage[] values = values();
		return values[Math.max(0, Math.min(values.length - 1, ordinal))];
	}
}

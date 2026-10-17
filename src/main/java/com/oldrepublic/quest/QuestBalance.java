package com.oldrepublic.quest;

/** Balance section {@code quest}: timings and rewards of the introductory questline. */
public final class QuestBalance {
	/** Length of the Awakening dream before the Force burst. */
	public int dreamTicks = 140;
	/** Radius of the uncontrolled burst that nudges items and small creatures. */
	public double burstRadius = 5.0;
	/** Slowness on the player after the burst (shock). */
	public int burstSlownessTicks = 60;
	/** Creatures wider than this are too heavy for the burst to move. */
	public float burstMaxEntityWidth = 0.9f;
	/** A danger-free window (no damage from mobs) needed before the timed Awakening starts. */
	public int awakeningSafeTicks = 100;
	/** Sleep this long before waking triggers the Awakening (vanilla needs 100 to skip the night). */
	public int awakeningMinSleepTicks = 100;

	/** Ticks of stillness needed to complete a meditation. */
	public int meditationTicks = 160;
	/** Crouching still this long (with the journal) starts a meditation. */
	public int meditationStartDelayTicks = 10;
	/** Maximum horizontal drift from where the meditation began, in blocks. */
	public double meditationMaxDrift = 0.15;
	public long meditationFirstXp = 60;
	/** Training meditation after the Focus stage: at most once per game day (24000 ticks). */
	public long meditationDailyXp = 40;

	public int practiceHits = 5;
	public long practiceXp = 150;

	public int callingIntervalTicks = 50;
	/** Distance at which the Calling feels faintest; used for the closeness value sent to the client. */
	public double callingFarDistance = 512;

	public long trialXp = 400;
	public long completeXp = 500;
	public int completeSkillPoints = 2;

	public int awakeningResonance = 250;
	public int trialResonance = 600;
	public int firstSaberResonance = 400;
}

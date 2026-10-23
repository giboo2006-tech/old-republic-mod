package com.oldrepublic.boss;

import com.oldrepublic.config.BalanceConfig;

/**
 * Balance section {@code collector} of {@code config/oldrepublic/balance.json} (spec section 14):
 * Orren Vasch, the Collector, his Reliquary and the rewards and ending values W2 grants from
 * {@code QuestService.onBossDefeated}. Field names are frozen (W2 reads the reward and ending keys by
 * name). Durations in ticks, distances in blocks, fractions of max health, multipliers on damage.
 *
 * <p>Keys added by W5 beyond section 14 (documented in the report and {@code docs/design/boss.md}):
 * {@code p1StringMin}, {@code p1StringMax}, {@code p1GapTicks}, {@code p3StringMin}, {@code p3StringMax},
 * {@code p3GapTicks}, {@code controlMultiplier}, {@code lungeCooldown}, {@code lungeInterruptStaggerTicks}, {@code gripBreakStaggerTicks},
 * {@code spentLiftHeight}.
 */
public final class CollectorBalance {
	/** Health 450 x (1 + perParticipant x (n - 1)), n capped at scalingCap; armour; knockback resistance. */
	public int maxHealth = 450;
	public float perParticipant = 0.6f;
	public int scalingCap = 4;
	public int armor = 10;
	public float knockbackResistance = 0.8f;

	/** Health fractions where the phases begin and where he yields; transitions are invulnerable for transitionTicks. */
	public float phase2At = 0.60f;
	public float phase3At = 0.30f;
	public float yieldAt = 0.10f;
	public int transitionTicks = 60;

	/** Phase 1 profile "collector_archivist" (also his melee in phase 2). */
	public float p1LightDamage = 6f;
	public float p1HeavyDamage = 11f;
	public float p1Guard = 140f;
	public float p1ParryChance = 0.30f;
	public float p1ParryMinStamina = 0.70f;
	public int p1WindupTicks = 10;
	public int p1RecoveryTicks = 16;
	/** Added: strings of 2 to 3 (8.3) and the gap between swings (the acolyte's swing gap). */
	public int p1StringMin = 2;
	public int p1StringMax = 3;
	public int p1GapTicks = 7;

	/** Archive Hurl. */
	public int hurlInterval = 180;
	public int hurlTellTicks = 24;
	public float hurlDamage = 8f;
	public float hurlSpeed = 1.1f;
	public float hurlGuardDrain = 30f;
	public int hurlRipStaggerTicks = 20;
	public float hurlReturnRange = 6f;
	public float hurlReturnMultiplier = 1.5f;
	public int hurlReturnStaggerTicks = 30;

	/** Repelling Palm. */
	public float palmRange = 3f;
	public int palmDwellTicks = 80;
	public int palmTellTicks = 16;
	public float palmDamage = 4f;
	public float palmKnockback = 1.4f;
	public int palmClashStaggerTicks = 20;

	/** His bolt deflection while guarding (10.7): shooters at most this many degrees off his facing (either side); the spread. */
	public float deflectConeDegrees = 70f;
	public float deflectSpreadDegrees = 15f;

	/** Resonance Ward: damage taken with 3-4 pylons singing, with 1-2. */
	public float wardHigh = 0.35f;
	public float wardLow = 0.65f;
	/** Unsung: the window after the last pylon falls silent. */
	public int unsungTicks = 100;
	public float unsungMultiplier = 1.5f;

	/** Pylon foci: resonance 30 x (1 + pylonScale x (n - 1)) and what each source takes off. */
	public float pylonResonance = 30f;
	public float pylonScale = 0.25f;
	public float pylonPullRange = 12f;
	public int pylonLiftTicks = 20;
	public float pylonSaber = 10f;
	public float pylonPush = 10f;
	public float pylonFocusedBlast = 15f;
	public float pylonThrow = 15f;
	public float pylonLightningPerSecond = 10f;
	public float pylonParriedLance = 15f;
	/** Re-sing after resingTicks while another pylon sings, at most resingPerPhase times per pylon and phase. */
	public int resingTicks = 800;
	public int regrowTellTicks = 60;
	public int resingPerPhase = 1;

	/** Resonant Lash. */
	public int lashInterval = 140;
	public int lashTellTicks = 20;
	public float lashDamage = 9f;
	public float lashSpeed = 0.8f;
	public float lashRange = 20f;
	public float lashKnockback = 0.6f;

	/** Phase 2 sentinels (+1 per extra participant, at most sentinelsMax) and the dais leash. */
	public int sentinels = 2;
	public int sentinelsMax = 4;
	public float leashRange = 8f;

	/** Phase 3 profile "collector_unkept". */
	public float p3LightDamage = 7f;
	public float p3HeavyDamage = 12f;
	public float p3Guard = 100f;
	public float p3ParryChance = 0.15f;
	public int p3WindupTicks = 8;
	public int p3RecoveryTicks = 12;
	/** Added: strings of 3 to 4 (8.3) and the gap between swings. */
	public int p3StringMin = 3;
	public int p3StringMax = 4;
	public int p3GapTicks = 7;

	/**
	 * Lunge. Added: lungeCooldown, the shortest time between two lunges, and
	 * lungeInterruptStaggerTicks, his stagger when a Push breaks the tell.
	 */
	public int lungeTellTicks = 14;
	public float lungeRange = 8f;
	public float lungeDamage = 10f;
	public int lungeCooldown = 100;
	public int lungeInterruptStaggerTicks = 20;

	/** Holocron Surge and its cover rules. */
	public int surgeInterval = 300;
	public int surgeTellTicks = 30;
	public float surgeRadius = 8f;
	public float surgeDamage = 10f;
	public float surgeKnockback = 2.0f;
	public float surgePartialMultiplier = 0.5f;
	public float surgeAirborneMultiplier = 0.5f;
	public int surgeClashTicks = 8;
	public float surgeClashShelter = 3f;

	/** Spent after a Surge. Added: spentLiftHeight (Lift lifts him 1.2; W3's slam gives the 10-tick stagger). */
	public int spentTicks = 60;
	public float spentMultiplier = 1.5f;
	public float spentLiftHeight = 1.2f;

	/** Archive Grip. Added: gripBreakStaggerTicks (he staggers when the grip is broken). */
	public int gripInterval = 400;
	public int gripTellTicks = 20;
	public int gripHoldTicks = 80;
	public float gripDamage = 2f;
	public float gripBreakDamage = 8f;
	public float gripTakenMultiplier = 1.25f;
	public int gripSurgeGap = 60;
	public int gripBreakStaggerTicks = 20;

	/** A single hit of at least this much breaks the Grip tell. */
	public float interruptHitDamage = 6f;
	/** Resistances outside Spent. */
	public float lightningMultiplier = 0.6f;
	public float saberThrowMultiplier = 0.7f;
	/** Added: control durations outside Spent (Spent takes full durations); diminishing returns always apply. */
	public float controlMultiplier = 0.5f;

	/** Threat: most damage in the window; the Hurl and Lunge target switches every threatSwitchTicks. */
	public int threatWindowTicks = 200;
	public int threatSwitchTicks = 200;

	/** Reset, seals, the first meeting, leaving participants, the heart's range and the dais start. */
	public int resetTicks = 200;
	public int sealCloseTicks = 40;
	public int meetDialogueTicks = 400;
	public int participantLeaveTicks = 100;
	public int readyRange = 48;
	public int daisStartRange = 6;

	/** Rewards W2 grants (8.8, 8.9). */
	public int ownerXp = 3000;
	public int ownerSp = 2;
	public int resonance = 600;
	public int otherHelperXp = 1000;
	public int otherHelperShards = 1;

	/** Ending values W2 applies (8.7). */
	public int spareLight = 10;
	public int spareJediOrderRep = 60;
	public int spareRepublicRep = 100;
	public int spareJediTrust = 10;
	public int spareSithTrust = -25;
	public int executeDark = -10;
	public int executeSithRep = 80;
	public int executeSithTrust = 10;
	public int executeJediTrust = -25;
	public int executeFrontierRep = 20;
	public int bindDark = -5;
	public int bindSithRep = 100;
	public int bindSithTrust = 10;
	public int releaseLight = 5;
	public int releaseFrontierRep = 50;
	public int bringInRep = 100;
	public int bringInCommendations = 2;

	/** The rematch "Echo of the Collector" (8.9). */
	public int echoFormTicks = 60;
	public float echoDamageMultiplier = 1.15f;
	public int echoXp = 600;
	public int echoDurasteel = 2;
	public int echoShards = 1;
	public float emitterChance = 0.25f;
	public int emitterPity = 4;

	/** Unchained (8.9): health and damage multipliers, the phase 3 threshold, rewards. */
	public float unchainedHealth = 1.5f;
	public float unchainedDamage = 1.25f;
	public float unchainedPhase3At = 0.40f;
	public int unchainedXp = 900;
	public int unchainedRolls = 2;

	/** Whether W2 places the Reliquary when RELIQUARY begins (else an operator uses the command). */
	public boolean placeReliquary = true;

	public static CollectorBalance get() {
		return BalanceConfig.section("collector", CollectorBalance.class, CollectorBalance::new);
	}
}

package com.oldrepublic.quest.story;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.boss.CollectorBalance;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.Commendations;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.saber.SaberItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The Collector's endings and every Collector reward (spec 8.7 to 8.9, W2 grants them all): the
 * owner's choice in the yield dialogue, a co-op helper's stance, the first clear, the other helpers'
 * daily reward, the echo rematch with its emitter pity and the Unchained mode; then the Archive's
 * fate and the report that closes the chapter (6.6). Values are W5's {@code collector} balance keys.
 */
final class CollectorRewards {
	static final Identifier MEET = OldRepublicMod.id("c3_collector_meet");
	static final Identifier YIELD = OldRepublicMod.id("c3_collector_yield");
	static final Identifier STANCE = OldRepublicMod.id("c3_collector_stance");
	static final ResourceKey<LootTable> ECHO_CACHE = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/echo_cache"));
	/** Fighters within this many blocks of the heart are in the owner's hall (the confrontation and the stance dialogues). */
	static final int HALL_REACH = 48;
	/** Salt for the emitter roll, so it never repeats the loot roll's sequence. */
	private static final long EMITTER_SALT = 0x5EEDL;

	/**
	 * W5's {@code collector} section. Numeric fields are read with explicit casts so int-or-double
	 * typing on W5's side compiles.
	 */
	// API-CHECK: boss.CollectorBalance field names/types (W5): ownerXp, ownerSp, resonance, otherHelperXp, otherHelperShards,
	// spare*, execute*, bind*, release*, bringIn*, echo*, emitterChance, emitterPity, unchainedXp, unchainedRolls, placeReliquary.
	// Checked against W5's in-tree boss/CollectorBalance.java before its merge: all 30 fields read here exist (int, float
	// emitterChance, boolean placeReliquary); re-check once W5 is merged.
	private static CollectorBalance balance() {
		return BalanceConfig.section("collector", CollectorBalance.class, CollectorBalance::new);
	}

	/** Whether the story places the Reliquary on its own ({@code collector.placeReliquary}); else only the command does. */
	static boolean placeReliquary() {
		return balance().placeReliquary;
	}

	// ---------------------------------------------------------------- c3_collector_meet

	/** "Give them back.": a small light shift (MINOR, {@code chapter_three.meetGiveBackLight}), once. */
	static void giveBack(ServerPlayer p) {
		int light = ChapterThreeService.balance().meetGiveBackLight;
		StoryData data = Story.get(p);

		if (light == 0 || !data.flags.add(ChapterThreeRules.GIVE_BACK)) {
			return;
		}

		Story.commit(p, data);
		Identity.shift(p, new AlignmentShift("collector.meet.give_back", light, AlignmentShift.Kind.MINOR, "quest.oldrepublic.c3.reason.give_back"));
	}

	/** An Independent's answer to Vasch's offer ("No." or "Not like this."): no shift, remembered. */
	static void vaschOffer(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.flags.add(ChapterThreeRules.VASCH_OFFER)) {
			Story.commit(p, data);
		}
	}

	// ---------------------------------------------------------------- the yield

	/**
	 * Vasch kneels (or the owner came back to a yielded heart): the owner's ending dialogue opens,
	 * and every listed helper in the hall whose chapter is at RELIQUARY or CONFRONTATION gets their
	 * stance dialogue.
	 */
	static void onBossYielded(ServerPlayer owner, String bossId) {
		StoryData data = Story.get(owner);

		if (data.chapter3.stage() == ChapterThreeStage.RELIQUARY) {
			ChapterThreeService.enterStage(owner, ChapterThreeStage.CONFRONTATION);
			data = Story.get(owner);
		}

		if (data.chapter3.stage() != ChapterThreeStage.CONFRONTATION || data.chapter3.outcome() >= 0) {
			return;
		}

		data.chapter3 = ChapterThreeRules.withBoss(data.chapter3, 1, -1);
		Story.commit(owner, data);
		// W5's arena session also opens these two (with Vasch as the speaker) around this hook:
		// open each only when it is not already showing, so a helper's open stance keeps its speaker.
		openUnlessShowing(owner, YIELD);

		for (ServerPlayer other : owner.level().players()) {
			StoryData theirs = Story.get(other);

			if (other != owner && BossArenas.isInArena(other) && other.distanceToSqr(owner) <= (double) HALL_REACH * HALL_REACH
					&& ChapterThreeRules.coopStage(theirs.chapter3.stage()) && theirs.chapter3.stance() < 0) {
				openUnlessShowing(other, STANCE);
			}
		}
	}

	private static void openUnlessShowing(ServerPlayer p, Identifier dialogue) {
		DialogueSession open = Dialogues.session(p);

		if (open == null || !dialogue.equals(open.dialogue())) {
			Dialogues.open(p, dialogue, null);
		}
	}

	/**
	 * {@code collector:resolve <spare|execute|bind|release|bring_in_republic|bring_in_imperial>}: the
	 * owner's ending (8.7). Its values apply once, W5 plays the scene, and the chapter moves on.
	 */
	static void resolve(ServerPlayer owner, String arg) {
		BossOutcome outcome = ChapterThreeRules.outcomeOf(arg);
		StoryData data = Story.get(owner);
		Path path = data.storyPath();
		StoryData.Chapter3 c = data.chapter3;

		if (outcome == null || c.stage() != ChapterThreeStage.CONFRONTATION || c.boss() != 1 || c.outcome() >= 0
				|| !ChapterThreeRules.resolveAllowed(path, outcome)) {
			return;
		}

		data.chapter3 = ChapterThreeRules.withBoss(c, 1, outcome.ordinal());
		Story.commit(owner, data);
		applyChoice(owner, path, outcome);
		BossArenas.resolve(owner, outcome);
		ChapterThreeService.enterStage(owner, ChapterThreeStage.AFTERMATH);
	}

	/**
	 * {@code c3:stance <spare|execute|bind|release>}: a co-op helper's own stance (8.9). It decides
	 * their alignment, standing, trust and entry; the world follows the owner. Their chapter jumps to
	 * AFTERMATH and they may make their own Archive choice. A helper the owner's choice already
	 * credited (and moved to AFTERMATH) can still pick it once ({@link ChapterThreeRules#stanceOpen}).
	 */
	static void stance(ServerPlayer p, String arg) {
		BossOutcome stance = ChapterThreeRules.outcomeOf(arg);
		StoryData data = Story.get(p);
		Path path = data.storyPath();
		StoryData.Chapter3 c = data.chapter3;

		if (stance == null || !ChapterThreeRules.stanceOpen(data) || !ChapterThreeRules.stanceAllowed(path, stance)) {
			return;
		}

		data.chapter3 = ChapterThreeRules.withStance(c, stance.ordinal());
		Story.commit(p, data);
		applyChoice(p, path, stance);
		ChapterThreeService.enterStage(p, ChapterThreeStage.AFTERMATH);
		HolocronService.markArchivePending(p);
	}

	/** One ending's values for this reader (8.7): the choice, the shift, standing, trust and the entry. */
	private static void applyChoice(ServerPlayer p, Path path, BossOutcome outcome) {
		CollectorBalance cb = balance();
		String source = ChapterThreeRules.choiceId(outcome);

		switch (outcome) {
			case SPARED -> {
				StoryService.shift(p, source, (int) cb.spareLight, "quest.oldrepublic.c3.reason.spare");

				if (path == Path.JEDI) {
					Identity.changeReputation(p, Faction.JEDI_ORDER, (int) cb.spareJediOrderRep, source, true);
					Identity.changeReputation(p, Faction.REPUBLIC, (int) cb.spareRepublicRep, source, true);
					StoryService.trust(p, MentorRole.JEDI, (int) cb.spareJediTrust, "quest.oldrepublic.c3.reason.spare");
				} else {
					StoryService.trust(p, MentorRole.SITH, (int) cb.spareSithTrust, "quest.oldrepublic.c3.reason.spare");
					StoryService.mentorSays(p, MentorRole.SITH, "c3.spared");
				}
			}
			case EXECUTED -> {
				StoryService.shift(p, source, (int) cb.executeDark, "quest.oldrepublic.c3.reason.execute");

				switch (path) {
					case SITH -> {
						Identity.changeReputation(p, Faction.SITH_EMPIRE, (int) cb.executeSithRep, source, true);
						StoryService.trust(p, MentorRole.SITH, (int) cb.executeSithTrust, "quest.oldrepublic.c3.reason.execute");
					}
					case JEDI -> StoryService.trust(p, MentorRole.JEDI, (int) cb.executeJediTrust, "quest.oldrepublic.c3.reason.execute");
					default -> Identity.changeReputation(p, Faction.FRONTIER, (int) cb.executeFrontierRep, source, true);
				}
			}
			case BOUND -> {
				StoryService.shift(p, source, (int) cb.bindDark, "quest.oldrepublic.c3.reason.bind");
				Identity.changeReputation(p, Faction.SITH_EMPIRE, (int) cb.bindSithRep, source, true);
				StoryService.trust(p, MentorRole.SITH, (int) cb.bindSithTrust, "quest.oldrepublic.c3.reason.bind");
				Journal.unlockCodex(p, JournalEntries.CODEX_THE_COLLECTOR_BOUND);
			}
			case RELEASED -> {
				StoryService.shift(p, source, (int) cb.releaseLight, "quest.oldrepublic.c3.reason.release");
				Identity.changeReputation(p, Faction.FRONTIER, (int) cb.releaseFrontierRep, source, true);
			}
			case BROUGHT_TO_REPUBLIC, BROUGHT_TO_EMPIRE -> {
				Faction army = outcome == BossOutcome.BROUGHT_TO_REPUBLIC ? Faction.REPUBLIC : Faction.SITH_EMPIRE;
				Identity.changeReputation(p, army, (int) cb.bringInRep, source, true);
				Rewards.give(p, Commendations.of(army, (int) cb.bringInCommendations));
			}
			case ECHO -> {
			}
		}

		// After the shift: a major shift is refused once its source id is already a recorded choice.
		Identity.recordChoice(p, source);
		Journal.unlockEntry(p, ChapterThreeRules.outcomeEntry(outcome));
	}

	// ---------------------------------------------------------------- defeat and rematch

	/** A fight ended for this participant (W5 calls it once per participant). */
	static void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		if (outcome == BossOutcome.ECHO) {
			// W5 reports every echo as bossId "collector" and marks the Unchained mode through
			// BossArenas.isUnchained (valid while this hook runs); the id check stays as a fallback.
			// API-CHECK: BossArenas.isUnchained(Entity) is in W5's in-tree BossArenas.java (not in the Phase 0 stub); re-check after W5's merge.
			echo(p, BossArenas.isUnchained(p) || bossId.toLowerCase(Locale.ROOT).contains("unchained"));
		} else if (p.getUUID().equals(owner)) {
			ownerDefeat(p, outcome);
		} else if (ChapterThreeRules.helperGetsFullRewards(Story.get(p))) {
			helperDefeat(p, outcome, owner);
		} else {
			otherHelper(p);
		}
	}

	/** The owner's fight is resolved: the boss state, AFTERMATH, the outcome's entry and the first-clear rewards. */
	private static void ownerDefeat(ServerPlayer p, BossOutcome outcome) {
		StoryData data = Story.get(p);
		data.chapter3 = ChapterThreeRules.withBoss(data.chapter3, 2, outcome.ordinal());
		Story.commit(p, data);
		ChapterThreeService.enterStage(p, ChapterThreeStage.AFTERMATH);
		Journal.unlockEntry(p, ChapterThreeRules.outcomeEntry(outcome));

		if (outcome == BossOutcome.BOUND) {
			Journal.unlockCodex(p, JournalEntries.CODEX_THE_COLLECTOR_BOUND);
		}

		firstClearRewards(p);
	}

	/**
	 * A co-op helper at RELIQUARY or CONFRONTATION (or who just took their stance): the full owner
	 * rewards, the world outcome noted, AFTERMATH ("You fought beside ..."), their own Archive choice.
	 * The {@link ChapterThreeRules#COOP_HELPER} flag keeps a stance they have not yet picked open.
	 */
	private static void helperDefeat(ServerPlayer p, BossOutcome outcome, UUID owner) {
		StoryData data = Story.get(p);
		data.chapter3 = ChapterThreeRules.withBoss(data.chapter3, 2, outcome.ordinal());
		data.flags.add(ChapterThreeRules.COOP_HELPER);
		Story.commit(p, data);
		ServerPlayer ownerPlayer = p.level().getServer().getPlayerList().getPlayer(owner);
		Component name = ownerPlayer != null ? ownerPlayer.getDisplayName() : Component.translatable("message.oldrepublic.story.c3.an_ally");
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.fought_beside", name));
		ChapterThreeService.addJournalNote(p, JournalEntries.THE_COLLECTOR, "quest.oldrepublic.c3.note.fought_beside");
		ChapterThreeService.enterStage(p, ChapterThreeStage.AFTERMATH);
		HolocronService.markArchivePending(p);

		if (outcome == BossOutcome.BOUND) {
			Journal.unlockCodex(p, JournalEntries.CODEX_THE_COLLECTOR_BOUND);
		}

		firstClearRewards(p);
	}

	/**
	 * The first clear (8.8), once per player (flag {@code c3.collector_sp}): XP, skill points,
	 * Resonance, the Archivist pommel and a singing crystal (dropped for the player alone if full),
	 * the codex entry and the banner.
	 *
	 * @return whether the rewards were given now
	 */
	static boolean firstClearRewards(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!data.flags.add(ChapterThreeRules.COLLECTOR_SP)) {
			return false;
		}

		Story.commit(p, data);
		CollectorBalance cb = balance();
		Progression.addXp(p, (long) cb.ownerXp, "collector");
		Progression.grantSkillPoints(p, (int) cb.ownerSp);
		Progression.grantResonanceMilestone(p, "collector", (int) cb.resonance);
		Rewards.give(p, new ItemStack(SaberItems.POMMEL_ARCHIVIST));
		Rewards.give(p, new ItemStack(M3Items.SINGING_CRYSTAL));
		Journal.unlockEntry(p, JournalEntries.THE_COLLECTOR);
		Journal.unlockCodex(p, JournalEntries.CODEX_THE_COLLECTOR);
		StoryService.milestone(p, Story.get(p), QuestEffects.LESSON_BOSS);
		return true;
	}

	/**
	 * Any other helper: 1,000 XP and a holocron shard, once per in-game day (counter
	 * {@code c3.helper_day}).
	 *
	 * @return whether the reward was given now
	 */
	static boolean otherHelper(ServerPlayer p) {
		int today = ChapterThreeService.day(p);
		StoryData data = Story.get(p);

		if (!ChapterThreeRules.helperRewardDue(data.counters.getOrDefault(ChapterThreeRules.HELPER_DAY, -1), today)) {
			return false;
		}

		data.counters.put(ChapterThreeRules.HELPER_DAY, today);
		Story.commit(p, data);
		CollectorBalance cb = balance();
		Progression.addXp(p, (long) cb.otherHelperXp, "collector_helper");
		Rewards.give(p, new ItemStack(M3Items.HOLOCRON_SHARD, Math.max(1, (int) cb.otherHelperShards)));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.helper_reward"));
		return true;
	}

	/**
	 * A won rematch, once per in-game day: XP, durasteel, a shard and the cache roll (two rolls and
	 * more XP for the Unchained mode); the emitter, at most one per clear, on an {@code emitterChance}
	 * roll per cache roll or on the {@code emitterPity}-th clear without one. An emitter the
	 * {@code echo_cache} table itself rolls is skipped, so the chance is the balance key's alone.
	 *
	 * @return whether the rewards were given now
	 */
	static boolean echo(ServerPlayer p, boolean unchained) {
		int today = ChapterThreeService.day(p);
		StoryData data = Story.get(p);

		if (!ChapterThreeRules.echoRewardDue(data.chapter3, today)) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.echo_done_today"));
			return false;
		}

		int clears = data.chapter3.echoClears();
		data.chapter3 = ChapterThreeRules.withEchoClear(data.chapter3, today);
		Story.commit(p, data);
		CollectorBalance cb = balance();
		Progression.addXp(p, (long) (unchained ? cb.unchainedXp : cb.echoXp), unchained ? "collector_unchained" : "collector_echo");
		Rewards.give(p, new ItemStack(M3Items.DURASTEEL_PLATE, Math.max(1, (int) cb.echoDurasteel)));
		Rewards.give(p, new ItemStack(M3Items.HOLOCRON_SHARD, Math.max(1, (int) cb.echoShards)));
		int rolls = unchained ? Math.max(1, (int) cb.unchainedRolls) : 1;
		boolean rolled = false;

		for (int i = 0; i < rolls; i++) {
			long seed = Rewards.seed(p, "echo_cache", clears * 8L + i);

			for (ItemStack stack : Rewards.roll(p, ECHO_CACHE, seed)) {
				// The emitter has one source, the emitterChance roll below (and the pity): at most one per clear.
				if (!stack.is(SaberItems.EMITTER_RELIQUARY)) {
					Rewards.give(p, stack);
				}
			}

			rolled |= new Random(seed ^ EMITTER_SALT).nextDouble() < (double) cb.emitterChance;
		}

		StoryData fresh = Story.get(p);
		int pity = fresh.counter(ChapterThreeRules.ECHO_PITY);
		boolean emitter = ChapterThreeRules.emitterDue(pity, rolled, (int) cb.emitterPity);

		if (emitter) {
			Rewards.give(p, new ItemStack(SaberItems.EMITTER_RELIQUARY));
		}

		fresh.counters.put(ChapterThreeRules.ECHO_PITY, emitter ? 0 : pity + 1);
		Story.commit(p, fresh);
		return true;
	}

	// ---------------------------------------------------------------- aftermath

	/** The Archive's fate (4.8): its entry, the codex for Unmake, and the hoard is decided (the report may follow). */
	static void onArchiveResolved(ServerPlayer p, ArchiveChoice choice) {
		Journal.unlockEntry(p, switch (choice) {
			case RETURN -> JournalEntries.ARCHIVE_RETURNED;
			case KEEP -> JournalEntries.ARCHIVE_KEPT;
			case UNMAKE -> JournalEntries.ARCHIVE_UNMADE;
		});

		if (choice == ArchiveChoice.UNMAKE) {
			Journal.unlockCodex(p, JournalEntries.CODEX_ARCHIVE_UNMADE);
		}

		StoryData data = Story.get(p);

		if (data.flags.add(ChapterThreeRules.HOARD_DECIDED)) {
			// Which choice it was lives only in the holocron data (12.1); the story keeps that it was made.
			Story.commit(p, data);
			Path path = data.storyPath();
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.report_due." + (path == Path.INDEPENDENT ? "independent" : MentorRole.of(path).id())));
		}
	}

	/**
	 * {@code c3:report} at the mentor: the mentor's lines react to the outcome, the hoard and, for a
	 * co-op helper, a stance that clashed with the owner's outcome; then the rewards.
	 */
	static void reportToMentor(ServerPlayer p) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();

		if (!ChapterThreeRules.reportReady(data, path, true)) {
			return;
		}

		MentorRole role = MentorRole.of(path);
		BossOutcome outcome = BossOutcome.of(data.chapter3.outcome());

		if (outcome != null) {
			StoryService.mentorSays(p, role, "c3.report.outcome." + outcome.name().toLowerCase(Locale.ROOT));
		}

		String hoard = hoard(p);

		if (hoard != null) {
			StoryService.mentorSays(p, role, "c3.report.hoard." + hoard);
		}

		if (ChapterThreeRules.clashes(data.chapter3.stance(), data.chapter3.outcome())) {
			StoryService.mentorSays(p, role, "c3.report.clash");
		}

		completeReport(p, role, null, path == Path.JEDI ? JournalEntries.REPORT_WATCH : JournalEntries.REPORT_OVERSEER);
	}

	/** {@code c3:report <republic|imperial>} at an officer above Hostile (an Independent; Wary counts). */
	static void reportToOfficer(ServerPlayer p, String armyArg) {
		Faction army = switch (armyArg.trim().toLowerCase(Locale.ROOT)) {
			case "republic" -> Faction.REPUBLIC;
			case "imperial", "empire", "sith_empire" -> Faction.SITH_EMPIRE;
			default -> null;
		};
		StoryData data = Story.get(p);

		if (army == null || !ChapterThreeRules.reportReady(data, data.storyPath(), false) || !ChapterThreeRules.officerTakesReport(Identity.tier(p, army))) {
			return;
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.report.officer." + (army == Faction.REPUBLIC ? "republic" : "imperial")));
		completeReport(p, null, army, JournalEntries.REPORT_OFFICER);
	}

	/** {@code c3:send_word}: the journal carries the report when both armies are Hostile (Frontier standing instead). */
	static void sendWord(ServerPlayer p) {
		if (!SendWord.eligible(p)) {
			return;
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.report.send_word"));
		completeReport(p, null, Faction.FRONTIER, JournalEntries.REPORT_OFFICER);
	}

	/** The report's rewards (6.6), once: XP, trust or standing, Resonance, the entry and the chapter banner; COMPLETE. */
	private static void completeReport(ServerPlayer p, @Nullable MentorRole role, @Nullable Faction faction, String entry) {
		StoryData data = Story.get(p);

		if (!data.flags.add(ChapterThreeRules.REPORT_DONE)) {
			return;
		}

		Story.commit(p, data);
		ChapterThreeBalance b = ChapterThreeService.balance();
		Progression.addXp(p, b.reportXp, "chapter_three");

		if (role != null) {
			StoryService.trust(p, role, b.reportTrust, "quest.oldrepublic.c3.reason.report");
		}

		if (faction != null) {
			Identity.changeReputation(p, faction, b.reportRep, "c3.report", true);
		}

		Progression.grantResonanceMilestone(p, "chapter_three", b.reportResonance);
		Journal.unlockEntry(p, entry);
		StoryService.milestone(p, Story.get(p), QuestEffects.LESSON_CHAPTER_THREE);
		ChapterThreeService.enterStage(p, ChapterThreeStage.COMPLETE);
	}

	/** The hoard choice ({@code return}, {@code keep}, {@code unmake}) from the player's holocron data, where alone it is kept (12.1), or null. */
	private static @Nullable String hoard(ServerPlayer p) {
		HolocronData holocrons = HolocronService.view(p);
		ArchiveChoice choice = holocrons == null ? null : holocrons.archiveChoice();
		return choice == null ? null : choice.name().toLowerCase(Locale.ROOT);
	}

	private CollectorRewards() {
	}
}

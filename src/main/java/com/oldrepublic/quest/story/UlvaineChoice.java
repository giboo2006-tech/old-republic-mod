package com.oldrepublic.quest.story;

import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.drops.BossDrops;
import com.oldrepublic.boss.ulvaine.UlvaineBalance;
import com.oldrepublic.boss.ulvaine.UlvaineFight;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.vendor.VendorItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The end of Chapter Five (spec MILESTONE_5.md 6.7, 6.9): Darth Ulvaine's yield and the owner's
 * choice ({@code c5_ulvaine_yield}), each choice's rewards, the fight's own reward (once, whatever
 * the choice), a helper's daily reward, and the report that completes the chapter.
 *
 * <p>W8 runs the fight: it calls {@code QuestHooks.onBossYielded} at 10% (on Peaceful she talks and
 * kneels at once), and after the choice {@link UlvaineFight#resolve} plays the scene and reports
 * {@code onBossDefeated} once per participant. Every reward here is guarded by a one-time flag or a
 * per-day counter, so a hook that fires twice, or a resolve with no defeat hook after it, pays once.
 */
final class UlvaineChoice {
	static final Identifier YIELD = OldRepublicMod.id("c5_ulvaine_yield");
	/** The Cantor's Emitter's legendary schematic (11.2), taught for ending her. */
	static final Identifier CANTOR_EMITTER = OldRepublicMod.id("emitter_cantor_legendary");
	/** A helper's roll (6.9): 2 sunstone, 2 stormglass shards or 2 clan iron, equally likely. */
	static final ResourceKey<LootTable> HELPER_LOOT = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/ulvaine_helper"));

	// ---------------------------------------------------------------- the yield and the choice

	/** Ulvaine yielded to her owner (W8's hook, on Peaceful too): the choice dialogue opens, owner only. */
	static void onYielded(ServerPlayer owner) {
		StoryData data = Story.get(owner);

		if (data.chapter5.stage() != ChapterFiveStage.THE_HYMNAL || data.chapter5.boss() == ChapterFiveRules.BOSS_RESOLVED) {
			return;
		}

		if (data.chapter5.boss() != ChapterFiveRules.BOSS_YIELDED) {
			data.chapter5 = data.chapter5.withBoss(ChapterFiveRules.BOSS_YIELDED, -1);
			Story.commit(owner, data);
		}

		openUnlessShowing(owner, YIELD);
	}

	/**
	 * {@code ulvaine:resolve <spare|bind|end>}: the owner's choice, checked again on the server. The
	 * choice is saved, W8 plays the scene ({@link UlvaineFight#resolve}), the choice's rewards and the
	 * fight's own reward (with the Penitent emitter, {@link BossDrops#penitentEmitter}) are paid once
	 * each, and the chapter moves to AFTERMATH.
	 */
	static void resolve(ServerPlayer owner, String arg) {
		BossOutcome outcome = ChapterFiveRules.outcomeOf(arg);
		StoryData data = Story.get(owner);

		if (outcome == null || !ChapterFiveRules.choiceOpen(data)) {
			return;
		}

		data.chapter5 = data.chapter5.withBoss(ChapterFiveRules.BOSS_RESOLVED, outcome.ordinal());
		Story.commit(owner, data);
		UlvaineFight.resolve(owner, outcome);
		applyChoice(owner, outcome);
		payFight(owner);
		// Milestone 7 (MILESTONE_7.md 7.3): the Penitent emitter, once, with the fight's pay.
		BossDrops.penitentEmitter(owner);
		ChapterFiveService.enterStage(owner, ChapterFiveStage.AFTERMATH);
	}

	/**
	 * One choice's rewards (6.7), once per choice flag: Spare (+8 light, her saber's red crystal attuned
	 * to the player), Bind (by path: Jedi +5 light and the Jedi Order +100; Sith the Sith Empire +150 and
	 * the Council's writ; Independent Clan Hask +150 and 500 credits), End (-10 dark, the Cantor's
	 * Emitter schematic, Jedi trust -10, Sith trust +10). Each gives its entry. Public for tests.
	 *
	 * @return whether the rewards were given now
	 */
	public static boolean applyChoice(ServerPlayer p, BossOutcome outcome) {
		String id = ChapterFiveRules.outcomeId(outcome);

		if (!ChapterFiveService.once(p, ChapterFiveRules.CHOICE_PREFIX + id)) {
			return false;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		String source = "ulvaine." + id;

		switch (outcome) {
			case BOUND -> bind(p, b, source);
			case EXECUTED -> {
				StoryService.shift(p, source, b.endDarkUlvaine, "quest.oldrepublic.c5.reason.end_ulvaine");
				Crafting.teach(p, CANTOR_EMITTER);
				StoryService.trust(p, MentorRole.JEDI, b.endJediTrust, "quest.oldrepublic.c5.reason.end_ulvaine");
				StoryService.trust(p, MentorRole.SITH, b.endSithTrust, "quest.oldrepublic.c5.reason.end_ulvaine");
			}
			default -> {
				StoryService.shift(p, source, b.spareLight, "quest.oldrepublic.c5.reason.spare_ulvaine");
				Rewards.give(p, SaberAssembly.crystalStack(new ModComponents.CrystalAttunement(p.getUUID(), p.getPlainTextName(), BladeColor.RED.rgb())));
			}
		}

		Identity.recordChoice(p, source);
		Journal.unlockEntry(p, ChapterFiveRules.outcomeEntry(outcome));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.ending." + id));
		return true;
	}

	/** Binding her, by the path at the moment of the choice (a player who turned mid-chapter binds for the new side, 6.9). */
	private static void bind(ServerPlayer p, ChapterFiveBalance b, String source) {
		Path path = Story.get(p).storyPath();

		if (path == Path.JEDI) {
			StoryService.shift(p, source, b.bindJediLight, "quest.oldrepublic.c5.reason.bind_ulvaine");
			Identity.changeReputation(p, Faction.JEDI_ORDER, b.bindJediRep, source, true);
		} else if (path == Path.SITH) {
			Identity.changeReputation(p, Faction.SITH_EMPIRE, b.bindSithRep, source, true);
			Rewards.give(p, new ItemStack(VendorItems.ULVAINES_WRIT));
			// The deed lives in the flag, so a lost keepsake does not lose it (8.5).
			ChapterFiveService.once(p, ChapterFive.WRIT);
		} else {
			Identity.changeReputation(p, Faction.CLAN_HASK, b.bindClanRep, source, true);
			Credits.give(p, b.bindClanCredits);
		}
	}

	// ---------------------------------------------------------------- the fight's rewards (6.7, 6.9)

	/**
	 * W8 reported the fight's end for one participant ({@link UlvaineFight#resolve} calls it once per
	 * participant): the owner's fight reward (if the choice did not pay it already), or a helper's.
	 */
	static void onDefeated(ServerPlayer p, UUID owner) {
		if (p.getUUID().equals(owner)) {
			payFight(p);
			BossDrops.penitentEmitter(p);
		} else {
			helper(p);
		}
	}

	/**
	 * The fight's own reward, once whatever the choice (flag {@code c5.ulvaine_paid}): 5,000 XP, 2 SP,
	 * 400 credits and Resonance {@code ulvaine} +600. (No banner: the {@code boss} banner names the
	 * Collector; W8's yield scene marks the fight's end, and the report has the chapter's banner.)
	 *
	 * @return whether it was paid now
	 */
	static boolean payFight(ServerPlayer p) {
		if (!ChapterFiveService.once(p, ChapterFiveRules.ULVAINE_PAID)) {
			return false;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		ChapterFiveService.xp(p, b.ulvaineXp);

		if (b.ulvaineSp > 0) {
			Progression.grantSkillPoints(p, b.ulvaineSp);
		}

		Credits.give(p, b.ulvaineCredits);
		Progression.grantResonanceMilestone(p, "ulvaine", b.ulvaineResonance);
		Journal.unlockCodex(p, JournalEntries.CODEX_DARTH_ULVAINE);
		return true;
	}

	/**
	 * A helper in another player's fight (6.9): 1,200 XP ({@code ulvaine.helperXp}) and one roll on
	 * {@code gameplay/ulvaine_helper}, once per in-game day (story counter {@code c5.helper_day}); then
	 * the Penitent grip or pommel roll ({@link BossDrops#penitentHelper}, Milestone 7).
	 *
	 * @return whether it was paid now
	 */
	static boolean helper(ServerPlayer p) {
		long day = ChapterFiveService.day(p);
		StoryData data = Story.get(p);

		if (!ChapterFiveRules.helperDue(data, day)) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.helper_done_today"));
			return false;
		}

		data.counters.put(ChapterFiveRules.HELPER_DAY, (int) day);
		Story.commit(p, data);
		Progression.addXp(p, UlvaineBalance.get().helperXp, "ulvaine_helper");
		Rewards.giveLoot(p, HELPER_LOOT, Rewards.seed(p, "ulvaine_helper", day));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.helper_reward"));
		// Milestone 7 (MILESTONE_7.md 7.3): its own daily guard; the pity is shared with her echo's roll.
		BossDrops.penitentHelper(p, day);
		return true;
	}

	// ---------------------------------------------------------------- AFTERMATH and the report

	/** AFTERMATH begins: who to report to (the path now, so a player who turned reports to the new mentor, 6.9). */
	static void onAftermath(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.report." + ChapterFiveRules.pathId(Story.get(p).storyPath())));
	}

	/** AFTERMATH's wait for an Independent: the report goes through the Compass when the journal is held. */
	static void tickAftermath(ServerPlayer p) {
		if (Story.get(p).storyPath() == Path.INDEPENDENT && QuestItems.holdsJournal(p)) {
			report(p);
		}
	}

	/**
	 * {@code c5:report}: the report (6.7) to Saelen or Sorn ({@code c5_hub_saelen}, {@code c5_hub_sorn}),
	 * or an Independent's Compass. The mentor reacts to the choice; 1,000 XP, trust +10, Resonance
	 * {@code chapter_five} +400, entry {@code report_c5}, the {@code chapter5} banner; COMPLETE. The rank
	 * 4 checklist then shows in the Standing panel (8.5, W5's data).
	 *
	 * @return whether the report was made now
	 */
	static boolean report(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!ChapterFiveRules.reportReady(data) || !ChapterFiveService.once(p, ChapterFiveRules.REPORTED)) {
			return false;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		Path path = data.storyPath();
		ChapterFiveService.mentorLine(p, "report." + ChapterFiveRules.outcomeId(data.chapter5.ulvaineOutcome()));
		ChapterFiveService.xp(p, b.reportXp);

		if (path == Path.JEDI || path == Path.SITH) {
			StoryService.trust(p, MentorRole.of(path), b.reportTrust, "quest.oldrepublic.c5.reason.report");
		}

		Progression.grantResonanceMilestone(p, "chapter_five", b.reportResonance);
		Journal.unlockEntry(p, JournalEntries.REPORT_C5);
		ChapterFiveService.banner(p, QuestEffects.LESSON_CHAPTER_FIVE);
		ChapterFiveService.enterStage(p, ChapterFiveStage.COMPLETE);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.complete"));
		return true;
	}

	private static void openUnlessShowing(ServerPlayer p, Identifier dialogue) {
		DialogueSession open = Dialogues.session(p);

		if (open == null || !dialogue.equals(open.dialogue())) {
			Dialogues.open(p, dialogue, null);
		}
	}

	private UlvaineChoice() {
	}
}

package com.oldrepublic.quest.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelData;
import com.oldrepublic.travel.TravelService;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The call and the helm (spec MILESTONE_5.md 6.1, 6.2): WAITING's wait (a day after Chapter Four,
 * or waking from a real sleep, at the next safe moment), the call itself (Saelen's and Sorn's
 * dialogues, Tessaly's echo through the Compass for an Independent), what CALLED unlocks (the
 * Saltgate destination and the helm), the three helm-lesson sub-objectives, and the repairs that
 * give old saves the unlocks their stage already earned.
 */
final class TheCall {
	static final Identifier CALL_SAELEN = OldRepublicMod.id("c5_call_saelen");
	static final Identifier CALL_SORN = OldRepublicMod.id("c5_call_sorn");
	/** Tessaly's echo speaks this many lines through the Compass. */
	private static final int COMPASS_LINES = 3;

	/** {@code tickCount} at which each sleeping player lay down. */
	private static final Map<UUID, Integer> SLEEP_START = new HashMap<>();

	/** WAITING's wait: the call plays when it is due, at a safe moment (6.1, {@link StoryMentors#safeMoment}). */
	static void check(ServerPlayer p) {
		if (ChapterFiveRules.callDue(Story.get(p), StoryService.now(p), ChapterFiveService.balance()) && StoryMentors.safeMoment(p)) {
			play(p);
		}
	}

	/**
	 * The call (6.2), also {@code /oldrepublic c5 call}: the chapter moves to CALLED (which pays and
	 * unlocks), then the path's mentor speaks. A Jedi hears Saelen and a Sith Sorn in their call
	 * dialogues (no speaker: a comm call); an Independent hears Tessaly's echo in the Compass. A
	 * player who spared Kaast hears one line more (in the dialogue's own first node, or in chat).
	 *
	 * @return whether the call played now
	 */
	static boolean play(ServerPlayer p) {
		if (!ChapterFiveService.enterStage(p, ChapterFiveStage.CALLED)) {
			return false;
		}

		StoryData data = Story.get(p);
		Path path = data.storyPath();

		if (path == Path.JEDI) {
			Dialogues.open(p, CALL_SAELEN, null);
		} else if (path == Path.SITH) {
			Dialogues.open(p, CALL_SORN, null);
		} else {
			p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.STORY_CALLING_HOLOCRON, SoundSource.PLAYERS, 0.8f, 1.1f);

			if (ChapterFiveRules.kaast(data) == KaastOutcome.SPARED) {
				ChapterFiveService.mentorLine(p, "kaast_named");
			}

			for (int i = 1; i <= COMPASS_LINES; i++) {
				ChapterFiveService.mentorLine(p, "call." + i);
			}
		}

		return true;
	}

	/** CALLED begins (6.2): 300 XP, the entry, the Saltgate destination and the helm. */
	static void onCalled(ServerPlayer p) {
		StoryData data = Story.get(p);
		data.flags.remove(ChapterFiveRules.CALL_DUE);
		Story.commit(p, data);
		ChapterFiveService.xp(p, ChapterFiveService.balance().callXp);
		Journal.unlockEntry(p, JournalEntries.THE_FACTOR_ASKS);
		repair(p);
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.called"));
	}

	/**
	 * Gives a save the unlocks its stage has earned (6.1, 4.2): the helm and Saltgate from CALLED, the
	 * holdfast from ARMED. Runs on CALLED and every slow tick, so saves past those stages get them too.
	 */
	static void repair(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!ChapterFiveRules.helmDue(data)) {
			return;
		}

		TravelData travel = TravelAttachments.get(p);

		if (!travel.helm()) {
			TravelAttachments.set(p, travel.withHelm(true));
		}

		TravelService.unlock(p, Destination.SALTGATE);

		if (ChapterFiveRules.holdfastDue(data)) {
			TravelService.unlock(p, Destination.HOLDFAST);
		}
	}

	// ---------------------------------------------------------------- the helm lesson (6.2)

	/** "Take the helm": the first flight by hand from CALLED on, once (200 XP, entry {@code the_helm}). */
	static void onHelmTaken(ServerPlayer owner) {
		if (!ChapterFive.atLeast(owner, ChapterFiveStage.CALLED) || !ChapterFiveService.once(owner, ChapterFiveRules.HELM)) {
			return;
		}

		ChapterFiveService.xp(owner, ChapterFiveService.balance().helmXp);
		Journal.unlockEntry(owner, JournalEntries.THE_HELM);
		owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.helm"));
	}

	/** "Reach orbit" and "Jump from orbit": the first orbit (300 XP, codex {@code space}) and the first jump (300 XP), once each. */
	static void onOrbitEntered(ServerPlayer owner, boolean byJump) {
		if (!ChapterFive.atLeast(owner, ChapterFiveStage.CALLED)) {
			return;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();

		if (ChapterFiveService.once(owner, ChapterFiveRules.ORBIT)) {
			ChapterFiveService.xp(owner, b.orbitXp);
			Journal.unlockCodex(owner, JournalEntries.CODEX_SPACE);
			owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.orbit"));
		}

		if (byJump && ChapterFiveService.once(owner, ChapterFiveRules.JUMP)) {
			ChapterFiveService.xp(owner, b.jumpXp);
			owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.jump"));
		}
	}

	// ---------------------------------------------------------------- sleeping (6.1)

	static void onStartSleeping(ServerPlayer p) {
		SLEEP_START.put(p.getUUID(), p.tickCount);
	}

	/** Waking from a long enough sleep while WAITING makes the call due at the next safe moment. */
	static void onStopSleeping(ServerPlayer p) {
		Integer start = SLEEP_START.remove(p.getUUID());

		if (start == null || p.tickCount - start < ChapterFiveService.balance().minSleepTicks) {
			return;
		}

		StoryData data = Story.get(p);

		if (data.chapter5.stage() == ChapterFiveStage.WAITING && data.flags.add(ChapterFiveRules.CALL_DUE)) {
			Story.commit(p, data);
		}
	}

	static void forget(ServerPlayer p) {
		SLEEP_START.remove(p.getUUID());
	}

	static void clear() {
		SLEEP_START.clear();
	}

	private TheCall() {
	}
}

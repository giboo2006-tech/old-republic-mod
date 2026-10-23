package com.oldrepublic.quest.turning;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.quest.story.Turnings;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The rites (spec MILESTONE_5.md 8.3): "Kneel" (Sorn names a fallen Jedi Sith, {@code turn_kneel}) and "Welcomed"
 * (Saelen welcomes a returned Sith into the Watch, {@code turn_welcomed}). The new mentor comes with a new camp
 * ({@code StoryMentors.comeTo}); {@code turn:rite} changes the path and applies its costs ({@link TurningCosts}), then
 * 1,000 XP, Resonance {@code the_fall} or {@code the_return} +300, the {@code turning} banner and the entry. A mentor
 * whose blade was taken in an earlier turning comes with a new one.
 */
final class Rites {
	/** The player's rite mentor is lost when they are this close to the camp and it is not there. */
	private static final double CAMP_REACH = 24.0;

	/** After the confrontation: the new mentor sets out for the player, with a new camp. */
	static void waitFor(ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;
		StoryMentors.comeTo(p, Confrontations.newRole(t.kind()), true, Rites::arrived);
	}

	/** Slow tick at RITE: the mentor is sent for again after a restart, or when lost near the camp. */
	static void tick(ServerPlayer p) {
		StoryData data = Story.get(p);
		StoryData.Turning t = data.turning;
		MentorRole role = Confrontations.newRole(t.kind());

		if (StoryMentors.isComing(p, role)) {
			return;
		}

		if (t.task() == TurningRules.RITE_WAITING) {
			StoryMentors.comeTo(p, role, true, Rites::arrived);
			return;
		}

		BlockPos camp = data.camp;

		if (TurningService.mentor(p, role) == null && camp != null && Planets.isHomeWorld(p.level())
				&& p.blockPosition().distSqr(camp) <= CAMP_REACH * CAMP_REACH) {
			setTask(p, TurningRules.RITE_WAITING);
		}
	}

	/** The new mentor came: a new blade if theirs was taken, a line, and the rite's dialogue. */
	private static void arrived(MentorEntity mentor) {
		ServerPlayer p = mentor.ownerPlayer();

		if (p == null) {
			return;
		}

		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.RITE) {
			return;
		}

		// Retracting builds the mentor's saber when the hand is empty: the new blade of 8.3.
		mentor.setBladeLit(false);
		setTask(p, TurningRules.RITE_HERE);
		TurningService.mentorSays(p, mentor.role(), "turning.oldrepublic." + t.kind().id() + ".rite.arrive");
		open(p, mentor);
	}

	/** Opens the rite's dialogue with the mentor as the speaker. */
	static boolean open(ServerPlayer p, Entity mentor) {
		TurningKind kind = Story.get(p).turning.kind();
		return Dialogues.open(p, OldRepublicMod.id(kind == TurningKind.RETURN ? "turn_welcomed" : "turn_kneel"), mentor);
	}

	/** Whether the player's rite waits on {@code mentor} (a use of the mentor reopens it). */
	static boolean waitsOn(ServerPlayer p, MentorEntity mentor) {
		StoryData.Turning t = Story.get(p).turning;
		return t.stage() == TurningStage.RITE && mentor.role() == Confrontations.newRole(t.kind()) && mentor.isOwnedBy(p);
	}

	/**
	 * {@code turn:rite}: the path changes with its costs, the turning is recorded (the identity choice, the turned day),
	 * and the rewards follow. Returns what the costs did, or null when no rite was under way.
	 */
	static TurningCosts.@Nullable Result rite(ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.RITE || t.kind() == TurningKind.NONE) {
			return null;
		}

		TurningBalance b = TurningService.balance();
		TurningKind kind = t.kind();
		long day = Clocks.day(p.level().getServer());
		TurningCosts.Result result = TurningCosts.apply(p, kind);
		String done = Turnings.doneChoice(kind);

		if (done != null) {
			Identity.recordChoice(p, done);
		}

		// The record rests at COMPLETE with the day of the rite; the band starts afresh for the other turning.
		StoryData data = Story.get(p);
		data.turning = data.turning.withStage(kind, TurningStage.COMPLETE).withTask(0).withOffer(-1L, 0, -1L).withTurnedDay(day);
		// The mentor who came with a new blade has it for good.
		data.flags.remove(kind == TurningKind.FALL ? TurningRules.BLADE_BROKEN : TurningRules.CRYSTAL_TAKEN);
		Story.commit(p, data);

		Progression.addXp(p, b.riteXp, "turning");
		Progression.grantResonanceMilestone(p, kind == TurningKind.FALL ? "the_fall" : "the_return", b.riteResonance);
		TurningService.entry(p, kind == TurningKind.FALL ? JournalEntries.KNEEL : JournalEntries.WELCOMED);
		TurningService.banner(p, QuestEffects.LESSON_TURNING);
		EffectEvents.toPlayer(p, TurningEffects.TURNING, p.position(), null, p.getId(), 1f,
				kind == TurningKind.FALL ? TurningEffects.RITE_FALL : TurningEffects.RITE_RETURN);
		p.sendSystemMessage(Component.translatable("turning.oldrepublic." + kind.id() + ".rite.done",
				Component.translatable("path.oldrepublic." + result.to().id())));
		RankFour.markDirty(p);
		return result;
	}

	private static void setTask(ServerPlayer p, int task) {
		StoryData data = Story.get(p);

		if (data.turning.task() != task) {
			data.turning = data.turning.withTask(task);
			Story.commit(p, data);
		}
	}

	private Rites() {
	}
}

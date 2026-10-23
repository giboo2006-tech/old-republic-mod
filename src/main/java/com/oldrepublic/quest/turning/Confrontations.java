package com.oldrepublic.quest.turning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.mentor.MentorDuel;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberAssembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The confrontations (spec MILESTONE_5.md 8.3): "The Last Lesson" (Saelen comes for a Jedi who served the Empire) and
 * "The Overseer's Due" (Sorn comes for a Sith who freed the prisoners). The old mentor comes at a safe moment on the home
 * world ({@code StoryMentors.comeTo}, the camp stays where it is), fights at full strength through W8's
 * {@link MentorDuel} and yields at 10%; then the choice ({@code turn_last_lesson}, {@code turn_overseers_due}, action
 * {@code turn:resolve release|trophy}). On Peaceful the confrontation is a talk and the mentor yields at once. A death
 * during it resets it: the mentor goes home and comes again after {@code confrontationResetTicks}; nothing is lost.
 */
final class Confrontations {
	/** Game time before which the old mentor does not come again (after a reset). */
	private static final Map<UUID, Long> RETRY_AT = new HashMap<>();
	/** Players whose duel is running in this server run (a restart loses it, and the confrontation starts again). */
	private static final Set<UUID> DUELLING = new HashSet<>();
	/** A waiting choice whose mentor stands farther than this sends for the mentor again. */
	private static final double CHOICE_REACH = 32.0;

	/** The mentor a turning leaves: Saelen for the Fall, Sorn for the Return. */
	static MentorRole oldRole(TurningKind kind) {
		return kind == TurningKind.RETURN ? MentorRole.SITH : MentorRole.JEDI;
	}

	/** The mentor a turning leads to: Sorn for the Fall, Saelen for the Return. */
	static MentorRole newRole(TurningKind kind) {
		return kind == TurningKind.RETURN ? MentorRole.JEDI : MentorRole.SITH;
	}

	/** After the task: the old mentor sets out for the player. */
	static void waitFor(ServerPlayer p) {
		RETRY_AT.remove(p.getUUID());
		DUELLING.remove(p.getUUID());
		StoryData.Turning t = Story.get(p).turning;
		StoryMentors.comeTo(p, oldRole(t.kind()), false, Confrontations::arrived);
	}

	/** Slow tick at CONFRONTATION. */
	static void tick(ServerPlayer p, long time) {
		StoryData.Turning t = Story.get(p).turning;
		MentorRole role = oldRole(t.kind());

		switch (t.task()) {
			case TurningRules.CONFRONT_WAITING -> {
				Long retry = RETRY_AT.get(p.getUUID());

				if ((retry == null || time >= retry) && !StoryMentors.isComing(p, role)) {
					RETRY_AT.remove(p.getUUID());
					StoryMentors.comeTo(p, role, false, Confrontations::arrived);
				}
			}
			case TurningRules.CONFRONT_DUEL -> {
				if (!DUELLING.contains(p.getUUID())) {
					// A restart ended the duel without a word: the mentor comes again.
					setTask(p, TurningRules.CONFRONT_WAITING);
				}
			}
			case TurningRules.CONFRONT_YIELDED, TurningRules.CONFRONT_TALKED -> {
				MentorEntity mentor = TurningService.mentor(p, role);

				if ((mentor == null || mentor.distanceToSqr(p) > CHOICE_REACH * CHOICE_REACH) && !StoryMentors.isComing(p, role)) {
					StoryMentors.comeTo(p, role, false, arrived -> {
						ServerPlayer owner = arrived.ownerPlayer();

						if (owner != null && Story.get(owner).turning.stage() == TurningStage.CONFRONTATION) {
							openChoice(owner, arrived);
						}
					});
				}
			}
			default -> setTask(p, TurningRules.CONFRONT_WAITING);
		}
	}

	/** The old mentor came ({@code comeTo}): the confrontation begins. */
	private static void arrived(MentorEntity mentor) {
		ServerPlayer p = mentor.ownerPlayer();

		if (p == null) {
			return;
		}

		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() == TurningStage.CONFRONTATION && t.task() == TurningRules.CONFRONT_WAITING) {
			begin(p, mentor, TurningService.peaceful(p));
		}
	}

	/**
	 * Begins the confrontation with {@code mentor} (the game tests' seam): on Peaceful a talk in which the mentor yields at
	 * once, else the duel. Without a mentor (a test) the duel is skipped as if the mentor had yielded.
	 */
	public static void begin(ServerPlayer p, @Nullable MentorEntity mentor, boolean peaceful) {
		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.CONFRONTATION) {
			return;
		}

		TurningKind kind = t.kind();
		MentorRole role = oldRole(kind);
		TurningService.mentorSays(p, role, "turning.oldrepublic." + kind.id() + ".confront.arrive");

		if (peaceful) {
			setTask(p, TurningRules.CONFRONT_TALKED);
			TurningService.mentorSays(p, role, "turning.oldrepublic." + kind.id() + ".confront.talk");

			if (mentor != null) {
				openChoice(p, mentor);
			}

			return;
		}

		setTask(p, TurningRules.CONFRONT_DUEL);

		if (mentor == null) {
			ended(p.level().getServer(), p.getUUID(), MentorDuel.Result.YIELDED);
			return;
		}

		DUELLING.add(p.getUUID());
		mentor.setBladeLit(true);
		MinecraftServer server = p.level().getServer();
		UUID player = p.getUUID();
		// The Phase 0 stub answers inside start(): the task is already DUEL, and ended() reads the record afresh.
		MentorDuel.start(p, mentor, kind == TurningKind.FALL ? MentorDuel.Kind.SAELEN : MentorDuel.Kind.SORN, result -> ended(server, player, result));
	}

	/** How the duel ended: the mentor yielded (the choice), or it reset (the player died or left). */
	private static void ended(MinecraftServer server, UUID player, MentorDuel.Result result) {
		DUELLING.remove(player);
		ServerPlayer p = server.getPlayerList().getPlayer(player);

		if (p == null) {
			return;
		}

		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.CONFRONTATION || t.task() != TurningRules.CONFRONT_DUEL) {
			return;
		}

		MentorRole role = oldRole(t.kind());
		MentorEntity mentor = TurningService.mentor(p, role);

		if (result == MentorDuel.Result.YIELDED) {
			setTask(p, TurningRules.CONFRONT_YIELDED);
			TurningService.mentorSays(p, role, "turning.oldrepublic." + t.kind().id() + ".confront.yield");

			if (mentor != null) {
				mentor.setBladeLit(false);
				openChoice(p, mentor);
			}
		} else {
			reset(p, Clocks.time(server));
		}
	}

	/**
	 * The player died during the confrontation, or the duel reset: back to its start, the mentor home, and the mentor
	 * comes again after {@code confrontationResetTicks}. A choice already won stays won.
	 */
	static void reset(ServerPlayer p, long time) {
		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.CONFRONTATION || t.task() == TurningRules.CONFRONT_YIELDED || t.task() == TurningRules.CONFRONT_TALKED) {
			return;
		}

		MentorRole role = oldRole(t.kind());
		DUELLING.remove(p.getUUID());

		if (StoryMentors.isComing(p, role)) {
			StoryMentors.cancelComing(p);
		}

		setTask(p, TurningRules.CONFRONT_WAITING);
		RETRY_AT.put(p.getUUID(), time + TurningService.balance().confrontationResetTicks);
		MentorEntity mentor = TurningService.mentor(p, role);

		if (mentor != null) {
			mentor.setBladeLit(false);
			sendHome(p, mentor);
		}
	}

	/** Game time before which the old mentor will not come again, or -1 (for the tests and the status command). */
	static long retryAt(ServerPlayer p) {
		Long retry = RETRY_AT.get(p.getUUID());
		return retry == null ? -1L : retry;
	}

	/** Opens the choice, with the mentor as the speaker. */
	static boolean openChoice(ServerPlayer p, Entity mentor) {
		TurningKind kind = Story.get(p).turning.kind();
		return Dialogues.open(p, OldRepublicMod.id(kind == TurningKind.RETURN ? "turn_overseers_due" : "turn_last_lesson"), mentor);
	}

	/** Whether the player's choice waits on {@code mentor} (a use of the mentor reopens it). */
	static boolean choiceWaits(ServerPlayer p, MentorEntity mentor) {
		StoryData.Turning t = Story.get(p).turning;
		return t.stage() == TurningStage.CONFRONTATION && (t.task() == TurningRules.CONFRONT_YIELDED || t.task() == TurningRules.CONFRONT_TALKED)
				&& mentor.role() == oldRole(t.kind()) && mentor.isOwnedBy(p);
	}

	/**
	 * {@code turn:resolve release|trophy}: let the mentor go, or take Saelen's crystal (dark -8, a green crystal attuned
	 * to the player) or break Sorn's blade (light +3, his saber is gone). Either way 1,500 XP, the entry, the mentor walks
	 * back to the camp, and the new mentor sets out for the rite.
	 */
	static void resolve(ServerPlayer p, @Nullable Entity speaker, String arg) {
		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.CONFRONTATION || (t.task() != TurningRules.CONFRONT_YIELDED && t.task() != TurningRules.CONFRONT_TALKED)) {
			return;
		}

		TurningBalance b = TurningService.balance();
		TurningKind kind = t.kind();
		MentorRole role = oldRole(kind);
		boolean trophy = "trophy".equals(arg);
		MentorEntity mentor = speaker instanceof MentorEntity m && m.role() == role ? m : TurningService.mentor(p, role);

		if (trophy) {
			if (kind == TurningKind.FALL) {
				Identity.shift(p, new AlignmentShift("turn.fall.crystal", b.crystalDark, AlignmentShift.Kind.MAJOR, "turning.oldrepublic.reason.crystal"));
				Rewards.give(p, crystal(p));
			} else {
				Identity.shift(p, new AlignmentShift("turn.return.blade", b.bladeLight, AlignmentShift.Kind.MAJOR, "turning.oldrepublic.reason.blade"));
			}
		}

		StoryData data = Story.get(p);

		if (trophy) {
			data.flags.add(kind == TurningKind.FALL ? TurningRules.CRYSTAL_TAKEN : TurningRules.BLADE_BROKEN);
		}

		data.turning = data.turning.withStage(kind, TurningStage.RITE).withTask(TurningRules.RITE_WAITING);
		Story.commit(p, data);
		RETRY_AT.remove(p.getUUID());
		DUELLING.remove(p.getUUID());

		Progression.addXp(p, b.confrontationXp, "turning");
		TurningService.entry(p, kind == TurningKind.FALL ? JournalEntries.THE_LAST_LESSON : JournalEntries.THE_OVERSEERS_DUE);
		TurningService.mentorSays(p, role, "turning.oldrepublic." + kind.id() + ".confront." + (trophy ? "trophy" : "release"));

		if (mentor != null) {
			// Retracting builds a saber when the hand is empty, so the trophy's blade goes after it.
			mentor.setBladeLit(false);

			if (trophy) {
				mentor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}

			sendHome(p, mentor);
		}

		Rites.waitFor(p);
	}

	/** Saelen's crystal: a green kyber crystal attuned to the player, named for her (a trophy; its hue is cosmetic). */
	static ItemStack crystal(ServerPlayer p) {
		ItemStack stack = SaberAssembly.crystalStack(new ModComponents.CrystalAttunement(p.getUUID(), p.getPlainTextName(), BladeColor.GREEN.rgb()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("turning.oldrepublic.crystal.name"));
		return stack;
	}

	/** The mentor walks back to the camp (it stayed where it was), and keeps to it. */
	private static void sendHome(ServerPlayer p, MentorEntity mentor) {
		BlockPos camp = Story.get(p).camp;

		if (camp != null) {
			mentor.setAnchor(camp, StoryService.balance().mentorLeash);
		}
	}

	private static void setTask(ServerPlayer p, int task) {
		StoryData data = Story.get(p);

		if (data.turning.task() != task) {
			data.turning = data.turning.withTask(task);
			Story.commit(p, data);
		}
	}

	static void forget(UUID player) {
		RETRY_AT.remove(player);
		DUELLING.remove(player);
	}

	static void clear() {
		RETRY_AT.clear();
		DUELLING.clear();
	}

	private Confrontations() {
	}
}

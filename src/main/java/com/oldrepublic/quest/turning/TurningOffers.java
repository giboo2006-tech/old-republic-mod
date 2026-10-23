package com.oldrepublic.quest.turning;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.quest.story.Turnings;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The offer of the Fall or the Return (spec MILESTONE_5.md 8.2): a Jedi at -50 or below, or a Sith at +50 or above, for
 * a whole day after Chapter Four is met at a safe moment on the home world by a messenger (an Imperial acolyte or a Watch
 * apprentice) who makes the offer ({@code turn_fall_offer}, {@code turn_return_offer}). A refusal brings the offer back
 * {@code reofferDays} later; the third ends the offers until the alignment leaves the band.
 *
 * <p>The messenger lives only in memory: one per player while the offer stands, removed when the player answers, leaves
 * the home world or logs out, and replaced at the next safe moment. One saved with its chunk is removed when it loads.
 */
final class TurningOffers {
	/** Entity tag of a messenger. */
	static final String MESSENGER_TAG = "oldrepublic.turning.messenger";
	/** The messenger greets the player this close, or after {@link #GREET_TIMEOUT} ticks wherever it is. */
	private static final double GREET_DISTANCE = 3.5;
	private static final int GREET_TIMEOUT = 300;
	/** An unanswered messenger leaves after this long, or when the player is this far away; another comes later. */
	private static final int LINGER = 6000;
	private static final double LOST_DISTANCE = 48.0;
	/** Ticks before another messenger comes when the last one left unanswered. */
	private static final int REVISIT = 2400;
	private static final int ACOLYTE_FACES = 4;
	private static final int APPRENTICE_FACES = 3;
	private static final Map<UUID, Messenger> MESSENGERS = new HashMap<>();
	/** Game time before which no new messenger comes to the player (after one left unanswered). */
	private static final Map<UUID, Long> NEXT_VISIT = new HashMap<>();

	/** One messenger on its way to, or waiting for, a player. */
	private static final class Messenger {
		final UUID entity;
		final TurningKind kind;
		final long spawnedAt;
		boolean greeted;

		Messenger(UUID entity, TurningKind kind, long spawnedAt) {
			this.entity = entity;
			this.kind = kind;
			this.spawnedAt = spawnedAt;
		}
	}

	// ---------------------------------------------------------------- the slow tick

	/** The band, a pending offer's messenger, and a new offer when one is due. */
	static void tick(ServerPlayer p, long day, long time) {
		TurningBalance b = TurningService.balance();
		StoryData data = Story.get(p);
		StoryData.Turning t = data.turning;
		boolean fallDone = Turnings.done(p, TurningKind.FALL);
		boolean returnDone = Turnings.done(p, TurningKind.RETURN);
		TurningKind candidate = b.enabled
				? TurningRules.candidate(Identity.path(p), data.storyPath(), data.chapter4.stage() == ChapterFourStage.COMPLETE, fallDone, returnDone)
				: TurningKind.NONE;
		boolean inBand = TurningRules.inBand(candidate, Identity.alignment(p), b);
		StoryData.Turning tracked = TurningRules.track(t, candidate, inBand, day, TurningRules.resting(t, fallDone, returnDone));

		if (!tracked.equals(t)) {
			boolean withdrawn = t.stage() == TurningStage.OFFERED && tracked.stage() != TurningStage.OFFERED;
			data.turning = tracked;
			Story.commit(p, data);
			t = tracked;

			if (withdrawn) {
				closeOfferDialogue(p);
				dismiss(p, false);
			}
		}

		if (t.stage() == TurningStage.OFFERED) {
			tickMessenger(p, t.kind(), time);
			return;
		}

		if (candidate != TurningKind.NONE && TurningRules.offerDue(t, day, b) && StoryMentors.safeMoment(p) && !Spar.inSpar(p)) {
			offer(p, candidate);
		}
	}

	/**
	 * Makes the offer now (the slow tick when it is due; the commands and game tests): the record goes to OFFERED and a
	 * messenger comes. Returns false when the player is not between questlines.
	 */
	public static boolean offer(ServerPlayer p, TurningKind kind) {
		StoryData data = Story.get(p);

		if (kind == TurningKind.NONE || (!TurningRules.idle(data.turning) && data.turning.stage() != TurningStage.OFFERED)) {
			return false;
		}

		data.turning = TurningRules.offered(data.turning, kind);
		Story.commit(p, data);
		NEXT_VISIT.remove(p.getUUID());
		dismiss(p, false);
		spawnMessenger(p, kind, Clocks.time(p.level().getServer()));
		return true;
	}

	/** The pending offer's messenger: comes when the moment is safe, greets the player, waits, and leaves if ignored. */
	private static void tickMessenger(ServerPlayer p, TurningKind kind, long time) {
		Messenger messenger = MESSENGERS.get(p.getUUID());
		HubNpc npc = messenger == null ? null : find(p, messenger);

		if (messenger != null && (npc == null || messenger.kind != kind)) {
			dismiss(p, false);
			messenger = null;
			npc = null;
		}

		if (npc == null) {
			Long next = NEXT_VISIT.get(p.getUUID());

			if ((next == null || time >= next) && StoryMentors.safeMoment(p) && !Spar.inSpar(p)) {
				spawnMessenger(p, kind, time);
			}

			return;
		}

		double distSqr = npc.distanceToSqr(p);

		if (distSqr > LOST_DISTANCE * LOST_DISTANCE || time - messenger.spawnedAt > LINGER || !Planets.isHomeWorld(p.level())) {
			dismiss(p, true);
			NEXT_VISIT.put(p.getUUID(), time + REVISIT);
			return;
		}

		if (distSqr > GREET_DISTANCE * GREET_DISTANCE && !messenger.greeted) {
			npc.getNavigation().moveTo(p, 0.9);
		}

		if (!messenger.greeted && (distSqr <= GREET_DISTANCE * GREET_DISTANCE || time - messenger.spawnedAt > GREET_TIMEOUT)
				&& Dialogues.session(p) == null) {
			if (openOffer(p, npc, kind)) {
				messenger.greeted = true;
				EffectEvents.toPlayer(p, TurningEffects.TURNING, p.position(), null, p.getId(), 1f, TurningEffects.OFFER);
			}
		}
	}

	private static void spawnMessenger(ServerPlayer p, TurningKind kind, long time) {
		if (!Planets.isHomeWorld(p.level())) {
			return;
		}

		ServerLevel level = p.level();
		BlockPos at = TurningService.standNear(level, p.blockPosition(), 5, 7, p.getRandom());

		if (at == null) {
			return;
		}

		boolean fall = kind == TurningKind.FALL;
		String variant = fall ? "acolyte_" + p.getRandom().nextInt(ACOLYTE_FACES) : "apprentice_" + p.getRandom().nextInt(APPRENTICE_FACES);
		HubNpc npc = HubNpcs.spawn(level, at, fall ? HubRole.ACOLYTE : HubRole.APPRENTICE, variant);

		if (npc == null) {
			return;
		}

		npc.addTag(MESSENGER_TAG);
		StoryTags.setOwner(npc, p.getUUID());
		npc.setAnchor(p.blockPosition(), 2);
		MESSENGERS.put(p.getUUID(), new Messenger(npc.getUUID(), kind, time));
	}

	/** Opens the offer with the messenger as the speaker. */
	private static boolean openOffer(ServerPlayer p, Entity speaker, TurningKind kind) {
		return Dialogues.open(p, OldRepublicMod.id(kind == TurningKind.FALL ? "turn_fall_offer" : "turn_return_offer"), speaker);
	}

	/** The player used a messenger: their own reopens the offer; anyone else's has nothing to say to them. */
	static boolean onUsed(ServerPlayer p, HubNpc npc) {
		if (!npc.entityTags().contains(MESSENGER_TAG)) {
			return false;
		}

		StoryData.Turning t = Story.get(p).turning;

		if (StoryTags.isOwnedBy(npc, p.getUUID()) && t.stage() == TurningStage.OFFERED) {
			openOffer(p, npc, t.kind());
		} else {
			p.sendOverlayMessage(Component.translatable("turning.oldrepublic.offer.not_you"));
		}

		return true;
	}

	// ---------------------------------------------------------------- answers

	/** {@code turn:accept}: the questline starts at its task. */
	static void accept(ServerPlayer p) {
		StoryData data = Story.get(p);
		StoryData.Turning t = data.turning;

		if (t.stage() != TurningStage.OFFERED || t.kind() == TurningKind.NONE) {
			return;
		}

		data.turning = t.withStage(t.kind(), TurningStage.TASK).withTask(0);
		Story.commit(p, data);
		TurningService.entry(p, t.kind() == TurningKind.FALL ? JournalEntries.THE_FALL_OFFERED : JournalEntries.THE_RETURN_OFFERED);
		p.sendSystemMessage(Component.translatable("turning.oldrepublic." + t.kind().id() + ".task.start"));
		dismiss(p, true);
		RankFour.markDirty(p);
	}

	/** {@code turn:decline}: one more refusal; the offer comes back later, or not at all after the last one. */
	static void decline(ServerPlayer p) {
		StoryData data = Story.get(p);
		StoryData.Turning t = data.turning;

		if (t.stage() != TurningStage.OFFERED) {
			return;
		}

		TurningBalance b = TurningService.balance();
		long day = Clocks.day(p.level().getServer());
		StoryData.Turning resting = TurningRules.resting(t, Turnings.done(p, TurningKind.FALL), Turnings.done(p, TurningKind.RETURN));
		StoryData.Turning next = TurningRules.decline(t, day, b, resting);
		data.turning = next;
		Story.commit(p, data);
		p.sendSystemMessage(Component.translatable(TurningRules.finalRefusal(next, b) ? "turning.oldrepublic.offer.final" : "turning.oldrepublic.offer.later",
				b.reofferDays));
		dismiss(p, true);
	}

	// ---------------------------------------------------------------- the messenger's end

	/** Removes the player's messenger: walking off when {@code walk}, else at once. */
	private static void dismiss(ServerPlayer p, boolean walk) {
		Messenger messenger = MESSENGERS.remove(p.getUUID());
		HubNpc npc = messenger == null ? null : find(p, messenger);

		if (npc == null) {
			return;
		}

		if (walk) {
			npc.setAnchor(npc.blockPosition(), 0);
			TurningService.walkOff(npc, p.position(), 80);
		} else {
			npc.discard();
		}
	}

	private static void closeOfferDialogue(ServerPlayer p) {
		DialogueSession session = Dialogues.session(p);
		Identifier open = session == null ? null : session.dialogue();

		if (open != null && (open.getPath().equals("turn_fall_offer") || open.getPath().equals("turn_return_offer"))) {
			Dialogues.close(p);
		}
	}

	private static @Nullable HubNpc find(ServerPlayer p, Messenger messenger) {
		Entity entity = p.level().getEntity(messenger.entity);
		return entity instanceof HubNpc npc && npc.isAlive() ? npc : null;
	}

	/** A messenger saved with its chunk is not the live one of anybody: it leaves. */
	static void onEntityLoad(Entity entity) {
		if (!(entity instanceof HubNpc) || !entity.entityTags().contains(MESSENGER_TAG)) {
			return;
		}

		for (Messenger messenger : MESSENGERS.values()) {
			if (messenger.entity.equals(entity.getUUID())) {
				return;
			}
		}

		TurningService.discardLater(entity);
	}

	static void forget(ServerPlayer p) {
		dismiss(p, false);
		NEXT_VISIT.remove(p.getUUID());
	}

	static void clear() {
		MESSENGERS.clear();
		NEXT_VISIT.clear();
	}

	private TurningOffers() {
	}
}

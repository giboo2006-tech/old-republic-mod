package com.oldrepublic.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.force.ForcePush;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Meditation: crouch and stay still with the journal in hand. Validated on the server every tick;
 * the client only draws the focus ring and motes from the start/break/complete events.
 *
 * <p>The first meditation (Focus stage) teaches Force Push. Afterwards it is a limited training
 * activity: it starts at most once per game day and rewards a small amount of XP, so it can never be
 * used as unlimited AFK experience.
 */
final class Meditation {
	private static final class Session {
		Vec3 anchor;
		Level level;
		int stillTicks;
		int startTick;
		boolean active;
		boolean toldRested;

		Session(ServerPlayer player) {
			reset(player);
		}

		void reset(ServerPlayer player) {
			anchor = player.position();
			level = player.level();
			stillTicks = 0;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	static void tick(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		boolean eligible = isEligible(player);

		if (session == null) {
			if (eligible) {
				SESSIONS.put(player.getUUID(), new Session(player));
			}

			return;
		}

		if (!session.active) {
			if (!eligible) {
				SESSIONS.remove(player.getUUID());
			} else if (hasMoved(session, player)) {
				session.reset(player);
			} else if (++session.stillTicks >= QuestFeature.balance().meditationStartDelayTicks) {
				tryStart(player, session);
			}

			return;
		}

		if (!eligible || hasMoved(session, player) || player.swinging) {
			interrupt(player);
		} else if (player.tickCount - session.startTick >= QuestFeature.balance().meditationTicks) {
			complete(player);
		}
	}

	private static boolean isEligible(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isPassenger()) {
			return false;
		}

		return player.isShiftKeyDown() && player.onGround() && QuestItems.holdsJournal(player) && Progression.get(player).isAwakened();
	}

	private static boolean hasMoved(Session session, ServerPlayer player) {
		if (session.level != player.level()) {
			return true;
		}

		Vec3 delta = player.position().subtract(session.anchor);
		double drift = QuestFeature.balance().meditationMaxDrift;
		return delta.horizontalDistanceSqr() > drift * drift || Math.abs(delta.y) > 0.3;
	}

	private static void tryStart(ServerPlayer player, Session session) {
		ForceData data = Progression.get(player);

		if (!QuestRules.meditationAvailable(data, player.level().getGameTime())) {
			if (!session.toldRested) {
				session.toldRested = true;
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.meditation_rested"));
			}

			return;
		}

		session.active = true;
		session.startTick = player.tickCount;
		session.anchor = player.position();
		int duration = QuestFeature.balance().meditationTicks;
		EffectEvents.toTracking(player, QuestEffects.MEDITATION, player.position(), null, player.getId(), duration, QuestEffects.MEDITATION_START);
	}

	/** Breaks an active meditation (movement, attack, damage). Does nothing if none is active. */
	static void interrupt(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());

		if (session != null && session.active) {
			EffectEvents.toTracking(player, QuestEffects.MEDITATION, player.position(), null, player.getId(), 0, QuestEffects.MEDITATION_BREAK);
		}
	}

	private static void complete(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		EffectEvents.toTracking(player, QuestEffects.MEDITATION, player.position(), null, player.getId(), 0, QuestEffects.MEDITATION_COMPLETE);

		QuestBalance b = QuestFeature.balance();
		long gameTime = player.level().getGameTime();
		ForceData data = Progression.get(player);
		QuestRules.markMeditated(data, gameTime);
		boolean firstMeditation = QuestRules.advance(data, QuestStage.PRACTICE, QuestStage.FOCUS);
		Progression.commit(player, data);

		if (firstMeditation) {
			ForcePush.grant(player);
			Progression.addXp(player, b.meditationFirstXp, "meditation");
			Journal.unlockEntry(player, JournalEntries.STILLNESS);
			Journal.unlockCodex(player, JournalEntries.CODEX_COLD_PEACE);
		} else {
			Progression.addXp(player, b.meditationDailyXp, "meditation");
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.meditation_trained", b.meditationDailyXp));
		}
	}

	static void forget(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
	}

	static void clear() {
		SESSIONS.clear();
	}

	private Meditation() {
	}
}

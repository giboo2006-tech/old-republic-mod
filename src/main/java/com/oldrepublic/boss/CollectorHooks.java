package com.oldrepublic.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.server.level.ServerPlayer;

/**
 * Where an {@link ArenaSession} reports the fight's story moments (spec 8.3, 8.7, 8.9). The default
 * {@link #QUEST} sink calls {@link QuestHooks}, so W2 grants every reward; a game test gives its
 * session a {@link Recorder} instead and asserts on the calls, without touching any player's story.
 * The boss id is always {@link BossArenas#COLLECTOR}.
 */
public final class CollectorHooks {
	/** The three boss hooks of {@link QuestHooks}. */
	public interface Sink {
		/** A participant's fight entered {@code phase} (1 to 3). */
		void onBossPhase(ServerPlayer p, String bossId, int phase);

		/** The boss yielded; the owner decides. */
		void onBossYielded(ServerPlayer owner, String bossId);

		/** The fight ended with {@code outcome} for {@code p}; once per participant. */
		void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear);
	}

	/** The production sink: straight to {@link QuestHooks}. */
	public static final Sink QUEST = new Sink() {
		@Override
		public void onBossPhase(ServerPlayer p, String bossId, int phase) {
			QuestHooks.onBossPhase(p, bossId, phase);
		}

		@Override
		public void onBossYielded(ServerPlayer owner, String bossId) {
			QuestHooks.onBossYielded(owner, bossId);
		}

		@Override
		public void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
			QuestHooks.onBossDefeated(p, bossId, outcome, owner, firstClear);
		}
	};

	/** What kind of hook a {@link Call} was. */
	public enum Kind {
		PHASE,
		YIELDED,
		DEFEATED
	}

	/**
	 * One recorded hook call.
	 *
	 * @param phase      the phase of a {@link Kind#PHASE} call, else 0
	 * @param outcome    the outcome of a {@link Kind#DEFEATED} call, else null
	 * @param owner      the fight's owner of a {@link Kind#DEFEATED} call, else null
	 * @param unchained  what {@link BossArenas#isUnchained} answered for the player during the call
	 */
	public record Call(Kind kind, UUID player, String bossId, int phase, @Nullable BossOutcome outcome, @Nullable UUID owner, boolean firstClear,
			boolean unchained) {
	}

	/** A sink that records every call (game tests). Never registered globally. */
	public static final class Recorder implements Sink {
		private final List<Call> calls = new ArrayList<>();

		@Override
		public void onBossPhase(ServerPlayer p, String bossId, int phase) {
			calls.add(new Call(Kind.PHASE, p.getUUID(), bossId, phase, null, null, false, BossArenas.isUnchained(p)));
		}

		@Override
		public void onBossYielded(ServerPlayer owner, String bossId) {
			calls.add(new Call(Kind.YIELDED, owner.getUUID(), bossId, 0, null, null, false, BossArenas.isUnchained(owner)));
		}

		@Override
		public void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
			calls.add(new Call(Kind.DEFEATED, p.getUUID(), bossId, 0, outcome, owner, firstClear, BossArenas.isUnchained(p)));
		}

		/** Every call so far, oldest first. */
		public List<Call> calls() {
			return List.copyOf(calls);
		}

		/** The calls of {@code kind}. */
		public List<Call> calls(Kind kind) {
			return calls.stream().filter(c -> c.kind() == kind).toList();
		}

		/** How many {@link Kind#DEFEATED} calls {@code player} got. */
		public long defeatedCount(UUID player) {
			return calls.stream().filter(c -> c.kind() == Kind.DEFEATED && c.player().equals(player)).count();
		}

		public void clear() {
			calls.clear();
		}
	}

	private CollectorHooks() {
	}
}

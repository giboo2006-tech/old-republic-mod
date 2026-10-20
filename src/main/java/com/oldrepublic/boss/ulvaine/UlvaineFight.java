package com.oldrepublic.boss.ulvaine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.CollectorHooks;
import com.oldrepublic.boss.raid.RaidData;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.M5Items;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Darth Ulvaine's fights in the Heart of the Hymnal (spec MILESTONE_5.md 12.2): one {@link UlvaineSession}
 * per owner, in memory only. The owner in the Heart at {@code THE_HYMNAL} starts the fight; a yield still
 * waiting for its choice after a restart brings her back knelt; after the chapter the owner calls her
 * daily echo at the Heart's lectern. W4's {@code ulvaine:resolve} ends the fight through {@link #resolve};
 * W4 pays the fight's and the choice's rewards through the boss hooks, W8 the echo's ({@link #payEcho}).
 */
public final class UlvaineFight {
	/** The boss id the quest hooks carry (ids starting with {@code ulvaine} reach Chapter Five, 12.6). */
	public static final String BOSS_ID = "ulvaine";
	/** {@code StoryData.Chapter5.boss}: W4's "yielded, the choice open" value (6.7). */
	private static final int STORY_BOSS_YIELDED = 1;
	/** {@code StoryData.Chapter5.boss}: W4's "resolved" value. */
	private static final int STORY_BOSS_RESOLVED = 2;
	/** Ticks between the checks for owners entering the Heart. */
	private static final int START_CHECK_TICKS = 20;
	/** A use this close to the Heart's lectern anchor is a use of the lectern (blocks, squared). */
	private static final double LECTERN_REACH_SQR = 2.0 * 2.0;

	private static final Map<UUID, UlvaineSession> SESSIONS = new HashMap<>();
	/**
	 * Owners whose Ulvaine knelt and whose choice is still open. W4 keeps the same fact in the story
	 * ({@code chapter5.boss}); this copy covers a session that ends (a chunk unload, a reset command)
	 * before the story has it. Not saved: a restart reads the story alone.
	 */
	private static final Set<UUID> YIELDED = new HashSet<>();

	/**
	 * The owner's choice at the yield ({@code SPARED}, {@code BOUND} or {@code EXECUTED}, from W4's
	 * {@code ulvaine:resolve} action): plays the scene and reports the defeat once per participant
	 * (12.2). With no fight running (she was never brought back), the owner's own defeat hook still fires.
	 */
	public static void resolve(ServerPlayer owner, BossOutcome outcome) {
		resolve(owner, outcome, CollectorHooks.QUEST);
	}

	/**
	 * {@link #resolve(ServerPlayer, BossOutcome)}, with {@code fallback} taking the owner's defeat hook
	 * when no fight runs (the game tests pass a recorder). A running fight reports through its own
	 * session's sink, the one it began with.
	 */
	public static void resolve(ServerPlayer owner, BossOutcome outcome, CollectorHooks.Sink fallback) {
		UlvaineSession session = SESSIONS.get(owner.getUUID());

		if (session != null && !session.isEcho() && !session.isOver()) {
			session.resolve(outcome);
			return;
		}

		forgetYield(owner.getUUID());
		fallback.onBossDefeated(owner, BOSS_ID, outcome, owner.getUUID(), true);
	}

	/** The running fight or echo of {@code owner}, or null. */
	public static @Nullable UlvaineSession session(UUID owner) {
		UlvaineSession session = SESSIONS.get(owner);
		return session == null || session.isOver() ? null : session;
	}

	/** The running fight or echo whose Heart holds {@code pos} in {@code level}, or null. */
	public static @Nullable UlvaineSession sessionAt(ServerLevel level, BlockPos pos) {
		for (UlvaineSession session : SESSIONS.values()) {
			if (!session.isOver() && session.level() == level && session.layout().inside(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
				return session;
			}
		}

		return null;
	}

	/** The running fight or echo in the Heart centred on {@code heart}, or null. */
	private static @Nullable UlvaineSession sessionIn(ServerLevel level, BlockPos heart) {
		for (UlvaineSession session : SESSIONS.values()) {
			if (!session.isOver() && session.level() == level && session.layout().heart().equals(heart)) {
				return session;
			}
		}

		return null;
	}

	/**
	 * Starts a fight ({@code echo} false) or an echo for {@code owner} in the Heart laid out by
	 * {@code layout} (the server's own start, the lectern, the commands and the tests). {@code knelt}
	 * brings her back already yielded.
	 *
	 * @return the session, or null when she could not spawn, or a fight already runs for the owner or in that Heart
	 */
	public static @Nullable UlvaineSession begin(UUID owner, ServerLevel level, HeartLayout layout, boolean echo, boolean knelt) {
		return begin(owner, level, layout, echo, knelt, CollectorHooks.QUEST);
	}

	/**
	 * {@link #begin(UUID, ServerLevel, HeartLayout, boolean, boolean)} with the sink the fight reports its
	 * phases, yield and defeats to: {@link CollectorHooks#QUEST} in play, a recorder in the game tests.
	 */
	public static @Nullable UlvaineSession begin(UUID owner, ServerLevel level, HeartLayout layout, boolean echo, boolean knelt,
			CollectorHooks.Sink hooks) {
		if (session(owner) != null || sessionIn(level, layout.heart()) != null) {
			return null;
		}

		UlvaineSession session = new UlvaineSession(owner, level, layout, echo, hooks);

		if (!session.start(knelt)) {
			return null;
		}

		SESSIONS.put(owner, session);
		return session;
	}

	/** Every server tick: the sessions; every second, owners entering the Heart. */
	public static void tick(MinecraftServer server) {
		for (UlvaineSession session : new ArrayList<>(SESSIONS.values())) {
			session.tick();
		}

		SESSIONS.values().removeIf(UlvaineSession::isOver);

		if (server.getTickCount() % START_CHECK_TICKS == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				checkStart(player);
			}
		}
	}

	/** The owner alive in the Heart at {@code THE_HYMNAL}, before the choice: the fight (or, the yield still open, Ulvaine knelt). */
	private static void checkStart(ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID()) || player.isSpectator() || !player.isAlive()) {
			return;
		}

		StoryData.Chapter5 chapter = Story.get(player).chapter5;

		if (chapter.stage() != ChapterFiveStage.THE_HYMNAL || chapter.ulvaine() >= 0 || chapter.boss() == STORY_BOSS_RESOLVED) {
			return;
		}

		ServerLevel level = player.level();
		HeartLayout layout = HeartLayout.at(level, player.blockPosition());

		if (layout == null || !layout.inside(player.position()) || sessionIn(level, layout.heart()) != null) {
			return;
		}

		boolean knelt = chapter.boss() == STORY_BOSS_YIELDED || YIELDED.contains(player.getUUID());
		begin(player.getUUID(), level, layout, false, knelt);
	}

	// ---------------------------------------------------------------- the lectern and the echo

	/**
	 * Fabric {@code UseBlockCallback}: a lectern in the Heart of the Hymnal. After the owner's Chapter
	 * Five is {@code COMPLETE}, using it calls Ulvaine's echo (once a day per player, never on Peaceful).
	 * Lecterns elsewhere pass.
	 */
	static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || p.isSpectator()
				|| !(level instanceof ServerLevel server) || !level.getBlockState(hit.getBlockPos()).is(Blocks.LECTERN)) {
			return InteractionResult.PASS;
		}

		return onLecternUsed(server, hit.getBlockPos(), p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/**
	 * {@code player} used the lectern at {@code pos}: the echo, or the reason there is none.
	 *
	 * @return whether this lectern is the Heart's (the use is handled)
	 */
	public static boolean onLecternUsed(ServerLevel level, BlockPos pos, ServerPlayer player) {
		HeartLayout layout = HeartLayout.at(level, pos);

		if (layout == null || layout.lectern().distSqr(pos) > LECTERN_REACH_SQR) {
			return false;
		}

		if (!ChapterFive.atLeast(player, ChapterFiveStage.COMPLETE)) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.ulvaine.lectern_silent"));
		} else if (sessionIn(level, layout.heart()) != null || session(player.getUUID()) != null) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.ulvaine.echo_busy"));
		} else if (level.getDifficulty() == Difficulty.PEACEFUL) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.ulvaine.echo_peaceful"));
		} else if (RaidData.of(player).echoOn(Clocks.day(level.getServer()))) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.ulvaine.echo_done_today"));
		} else if (begin(player.getUUID(), level, layout, true, false) != null) {
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.ulvaine.echo_called"));
		}

		return true;
	}

	/**
	 * A participant helped defeat the echo on {@code today}: once a day, {@code echoXp}, a
	 * {@code echoShardChance} chance of a hymnstone shard, and one more echo for the rank 4 deed
	 * (12.2, {@link RaidData#echoes}). A second defeat that day pays nothing. Public for the game tests,
	 * which pay a given day.
	 */
	public static void payEcho(ServerPlayer player, long today) {
		RaidData data = RaidData.of(player);

		if (data.echoOn(today)) {
			player.sendSystemMessage(Component.translatable("boss.oldrepublic.ulvaine.echo_no_reward"));
			return;
		}

		UlvaineBalance b = UlvaineBalance.get();
		RaidData.set(player, data.withEcho(today));
		Progression.addXp(player, b.echoXp, "ulvaine_echo");
		// The same player on the same day always rolls the same, so the roll cannot be tried again.
		boolean shard = RandomSource.create(Rewards.seed(player, "ulvaine_echo", today)).nextFloat() < b.echoShardChance;

		if (shard) {
			Rewards.give(player, new ItemStack(M5Items.HYMNSTONE_SHARD));
		}

		player.sendSystemMessage(Component.translatable(shard ? "boss.oldrepublic.ulvaine.echo_reward_shard" : "boss.oldrepublic.ulvaine.echo_reward",
				b.echoXp));
	}

	// ---------------------------------------------------------------- the yield, kept in memory

	/** {@code owner}'s Ulvaine knelt; the choice is open. */
	static void rememberYield(UUID owner) {
		YIELDED.add(owner);
	}

	/** {@code owner}'s choice was made. */
	static void forgetYield(UUID owner) {
		YIELDED.remove(owner);
	}

	/** Whether {@code owner}'s Ulvaine knelt and the choice is still open (this server run). */
	public static boolean yieldOpen(UUID owner) {
		return YIELDED.contains(owner);
	}

	// ---------------------------------------------------------------- operators

	/** {@code /oldrepublic ulvaine phase}: jumps the owner's running fight to {@code phase}. False when none runs. */
	public static boolean phase(ServerPlayer owner, int phase) {
		UlvaineSession session = session(owner.getUUID());

		if (session == null) {
			return false;
		}

		session.forcePhase(phase);
		return true;
	}

	/** {@code /oldrepublic ulvaine yield}: the owner's running fight ends in her yield. False when none runs or it is an echo. */
	public static boolean forceYield(ServerPlayer owner) {
		UlvaineSession session = session(owner.getUUID());

		if (session == null || session.isEcho()) {
			return false;
		}

		session.forceYield();
		return true;
	}

	/** {@code /oldrepublic ulvaine reset}: the owner's fight resets as the 200-tick rule does. False when none runs. */
	public static boolean reset(ServerPlayer owner) {
		UlvaineSession session = session(owner.getUUID());

		if (session == null) {
			return false;
		}

		session.reset();
		SESSIONS.remove(owner.getUUID());
		return true;
	}

	/** Server stop: every fight ends (nothing of it is saved). */
	public static void clear() {
		for (UlvaineSession session : SESSIONS.values()) {
			session.reset();
		}

		SESSIONS.clear();
		YIELDED.clear();
	}

	private UlvaineFight() {
	}
}

package com.oldrepublic.quest.story;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.CaptiveOutcome;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestBalance;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestFeature;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.QuestRules;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Chapter Two, "Two Voices": the entry point the quest feature's hooks and events call. Each part of
 * the chapter lives in its own class ({@link TwoVoices}, {@link Crossing}, {@link Lessons},
 * {@link Reckoning}, {@link Trials}, {@link StoryMentors}); this class dispatches to them and holds
 * the small helpers they share. It also ticks and routes Chapter Three ({@link ChapterThreeService}).
 * Server only.
 */
public final class StoryService {
	/** Heavier checks (distances, entity searches) run this often per player. */
	static final int SLOW_TICK = 10;

	public static StoryBalance balance() {
		return BalanceConfig.section("story", StoryBalance.class, StoryBalance::new);
	}

	/**
	 * The story and mission sites of every other online player (meeting points, camps, raider camps,
	 * Courier's Fall, Echo Shrines, the Reliquary and active mission camps), which a new runtime site
	 * keeps its separation from (M3 3.6).
	 */
	public static List<BlockPos> otherSites(ServerPlayer player) {
		return Crossing.otherSites(player);
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}

			StoryData data = Story.get(player);
			TwoVoices.tick(player, data);
			data = Story.get(player);
			Crossing.tickCalling(player, data);
			Reckoning.tickCalling(player, data);
			// Chapter Three runs every tick (the dream, the journal's Calling) and does its own slow checks.
			ChapterThreeService.tick(player);
			// Chapter Four (Milestone 4) and Chapter Five (Milestone 5) do their own slow checks inside.
			ChapterFourService.tick(player);
			ChapterFiveService.tick(player);

			if ((player.tickCount + player.getId()) % SLOW_TICK != 0) {
				continue;
			}

			// Each part reads the data fresh: an earlier part may have committed a new copy.
			Crossing.tick(player, Story.get(player));
			Lessons.tick(player);
			Reckoning.tick(player, Story.get(player));
			StoryMentors.tick(player, Story.get(player));
			checkLedger(player, Story.get(player));
		}

		StoryMentors.tickPending(server);
	}

	/**
	 * The journal's pull toward a story site: only a direction and a closeness value are sent, never
	 * the position. Runs at the Calling's interval while the journal is held in the overworld.
	 */
	static void calling(ServerPlayer player, BlockPos target, int variant) {
		if (!callingDue(player)) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 flat = Vec3.atCenterOf(target).subtract(eye).horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, -1, 0) : flat.normalize();
		float closeness = (float) (1.0 - Math.min(1.0, flat.length() / balance().callingFarDistance));
		EffectEvents.toPlayer(player, QuestEffects.CALLING, eye, dir, player.getId(), closeness, variant);
	}

	/** Whether the journal's pull is due now: the Calling's interval, in the overworld, with the journal held. */
	static boolean callingDue(ServerPlayer player) {
		QuestBalance qb = QuestFeature.balance();
		return player.tickCount % Math.max(1, qb.callingIntervalTicks) == 0 && inOverworld(player) && QuestItems.holdsJournal(player);
	}

	// --- Hooks (from QuestHooks through QuestService)

	public static void onAbilityHit(ServerPlayer player, String abilityId, @Nullable Entity target) {
		Lessons.onAbilityHit(player, abilityId, target);
	}

	public static void onSaberTechnique(ServerPlayer player, String technique, @Nullable LivingEntity target, boolean hit) {
		Lessons.onSaberTechnique(player, technique, target, hit);
	}

	public static void onYieldResolved(ServerPlayer player, LivingEntity yielded, YieldOutcome outcome) {
		if (StoryTags.isCaptain(yielded) && StoryTags.isOwnedBy(yielded, player.getUUID())) {
			Reckoning.onCaptainResolved(player, yielded, outcome);
		} else if (StoryTags.isRival(yielded) && StoryTags.isOwnedBy(yielded, player.getUUID())) {
			Trials.onRivalYieldResolved(player, yielded, outcome);
		}
	}

	public static void onCaptivesResolved(ServerPlayer player, String groupId, CaptiveOutcome outcome) {
		Reckoning.onCaptivesResolved(player, groupId, outcome);
	}

	/**
	 * The identity path changed (after it was stored). Story choices store the story's path before
	 * they call {@link Identity#setPath}, so this only acts on outside changes: an operator forcing a
	 * path, which restarts the story on that path from its first lesson.
	 */
	public static void onPathChanged(ServerPlayer player, Path path) {
		StoryData data = Story.get(player);

		if (data.storyPath() == path || data.crossing() != CrossingStage.DECIDED) {
			return;
		}

		data.path = path.id();
		data.pathStage = path == Path.JEDI || path == Path.SITH ? PathStage.LESSON_ONE.ordinal() : PathStage.NONE.ordinal();
		data.flags.remove(StoryRules.LESSON_ONE_BEGUN);
		data.flags.remove(StoryRules.LESSON_TWO_BEGUN);
		Story.commit(player, data);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.story.path_forced", Component.translatable("path.oldrepublic." + path.id())));
	}

	// --- Events

	/** Any living entity died (Fabric AFTER_DEATH). */
	public static void onEntityDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		Lessons.onEntityDeath(entity, source);
		UUID owner = StoryTags.owner(entity);

		if (owner == null) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

		if (StoryTags.isCaptain(entity)) {
			Reckoning.onCaptainDied(player, entity, level);
		} else if (player != null && StoryTags.isCrew(entity)) {
			Reckoning.onCrewDied(player);
		} else if (player != null && StoryTags.isRival(entity)) {
			Trials.onRivalDied(player, entity);
		}
	}

	/**
	 * An entity was loaded into a level: removes story entities that no longer belong there. Fabric
	 * fires this for a fresh spawn too, so a spawn puts a chapter's leftover tag on only once the
	 * entity is in the level (and points the story data at a new mentor or rival before it).
	 */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		if (ChapterThreeService.isLeftover(entity) || ChapterFourService.isLeftover(entity) || ChapterFiveService.isLeftover(entity)) {
			entity.discard();
			return;
		}

		UUID owner = StoryTags.owner(entity);

		if (owner == null) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

		if (player == null) {
			return;
		}

		StoryData data = Story.get(player);
		boolean stale = (entity instanceof MentorEntity mentor && !StoryMentors.stillWanted(mentor))
				|| (StoryTags.isRival(entity) && !entity.getUUID().equals(data.rival))
				|| Reckoning.isLeftover(entity, player, data);

		if (stale) {
			entity.discard();
		}
	}

	/** Using Brenn opens his dialogue (owner only). Returns true when the use was handled. */
	public static boolean onEntityUsed(ServerPlayer player, Entity entity) {
		return StoryTags.isRival(entity) && entity instanceof Mob rival && StoryMentors.onRivalUsed(player, rival);
	}

	public static void onPlayerDamaged(ServerPlayer player) {
		TwoVoices.onDamaged(player);
		ChapterThreeService.onDamaged(player);
	}

	public static void onStartSleeping(ServerPlayer player) {
		TwoVoices.onStartSleeping(player);
		ChapterThreeService.onStartSleeping(player);
		ChapterFourService.onStartSleeping(player);
		ChapterFiveService.onStartSleeping(player);
	}

	public static void onStopSleeping(ServerPlayer player) {
		TwoVoices.onStopSleeping(player);
		ChapterThreeService.onStopSleeping(player);
		ChapterFourService.onStopSleeping(player);
		ChapterFiveService.onStopSleeping(player);
	}

	public static void forget(ServerPlayer player) {
		TwoVoices.forget(player);
		Lessons.forget(player);
		StoryMentors.forget(player);
		Reckoning.forget(player);
		ChapterThreeService.forget(player);
		ChapterFourService.forget(player);
		ChapterFiveService.forget(player);
	}

	public static void clear() {
		TwoVoices.clear();
		Lessons.clear();
		StoryMentors.clear();
		Reckoning.clear();
		ChapterThreeService.clear();
		ChapterFourService.clear();
		ChapterFiveService.clear();
	}

	// --- Meditation (the quest feature's meditation asks the story first)

	/** Whether a meditation may start even though the solo daily one was already done today. */
	public static boolean meditationAllowed(ServerPlayer player) {
		return Trials.guidedSessionReady(player) || StoryMentors.reachOutAvailable(player);
	}

	/**
	 * A meditation was completed. Returns true if the story used it (a guided session, atonement
	 * or reaching out), so the solo daily reward is not given as well.
	 */
	public static boolean onMeditationComplete(ServerPlayer player) {
		if (Trials.completeGuidedSession(player)) {
			return true;
		}

		return StoryMentors.reachOut(player);
	}

	// --- Shared helpers

	/** A spoken line in chat: "Speaker: line". */
	static void say(ServerPlayer player, Component speaker, String lineKey, Object... args) {
		player.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", speaker, Component.translatable(lineKey, args)));
	}

	/** A line spoken by {@code role}'s mentor, with the line's arguments. */
	static void mentorSays(ServerPlayer player, MentorRole role, String line, Object... args) {
		say(player, mentorName(role), role.line(line), args);
	}

	static Component mentorName(MentorRole role) {
		return Component.translatable("npc.oldrepublic." + role.id() + ".name");
	}

	static long now(ServerPlayer player) {
		return player.level().getGameTime();
	}

	static int day(ServerPlayer player) {
		return QuestRules.dayIndex(now(player));
	}

	static boolean inOverworld(ServerPlayer player) {
		return player.level().dimension() == Level.OVERWORLD;
	}

	/** Sends the chapter milestone effect (banner and chime) to the player. */
	static void milestone(ServerPlayer player, StoryData data, int variant) {
		EffectEvents.toPlayer(player, QuestEffects.LESSON_COMPLETE, player.position(), null, player.getId(), data.storyPath().ordinal(), variant);
	}

	/** A one-time major alignment shift from a story choice. */
	static void shift(ServerPlayer player, String sourceId, int amount, String reasonKey) {
		if (amount != 0) {
			Identity.shift(player, new AlignmentShift(sourceId, amount, AlignmentShift.Kind.MAJOR, reasonKey));
		}
	}

	static void trust(ServerPlayer player, MentorRole role, int amount, String reasonKey) {
		if (amount != 0) {
			Identity.addTrust(player, role.id(), amount, reasonKey);
		}
	}

	/** Unlocks "The Ledger" the first time the player carries the Poachers' Ledger. */
	private static void checkLedger(ServerPlayer player, StoryData data) {
		if (data.encounterState() == EncounterState.NOT_PLACED || data.hasFlag(StoryRules.LEDGER_READ)) {
			return;
		}

		if (QuestItems.ownsLedger(player)) {
			data.flags.add(StoryRules.LEDGER_READ);
			Story.commit(player, data);
			Journal.unlockEntry(player, JournalEntries.THE_LEDGER);
		}
	}

	private StoryService() {
	}
}

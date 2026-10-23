package com.oldrepublic.quest.turning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * What the Milestone 5 quest hooks mean for the Fall, the Return and rank 4 (spec MILESTONE_5.md 8), and the turning's
 * slow tick. Owned by W5 paths and ranks: {@code QuestHooks} forwards every Milestone 5 hook here; the signatures of those
 * hooks are frozen.
 *
 * <p>Every player is looked at every {@value #SLOW_TICKS} ticks (offset by their entity id): the offer's band and the
 * offer itself ({@link TurningOffers}), the step under way ({@link FallTask}, {@link ReturnTask}, {@link Confrontations},
 * {@link Rites}) and rank 4 ({@link RankFour}). Days are game-day indices ({@link Clocks#day}).
 */
public final class TurningService {
	/** The slow tick's period per player. */
	public static final int SLOW_TICKS = 10;
	/** Entity tag of the offer's messenger (an acolyte or an apprentice, 8.2). */
	public static final String MESSENGER_TAG = TurningOffers.MESSENGER_TAG;
	/** Entity tag of the Return's prisoners put down at the Greenwatch, who thank the player and walk off (8.3). */
	public static final String LANDED_TAG = ReturnTask.LANDED_TAG;
	/** Entities to remove on the next server tick (never inside an entity-load callback). */
	private static final List<Entity> DISCARD = new ArrayList<>();
	/** People of the turning walking out of a scene, removed at their time. */
	private static final List<Leaving> LEAVING = new ArrayList<>();

	/** The {@code turning} balance section (16.4). */
	public static TurningBalance balance() {
		return BalanceConfig.section("turning", TurningBalance.class, TurningBalance::new);
	}

	// ---------------------------------------------------------------- the frozen hooks (17.1)

	/** W1: the owner flew their ship by hand. Nothing in the turning waits on the helm. */
	public static void onHelmTaken(ServerPlayer owner) {
	}

	/** W1: the owner's ship entered {@code system}'s orbit; {@code byJump} for a hyperspace jump. Nothing in the turning waits on it. */
	public static void onOrbitEntered(ServerPlayer owner, Planets.Planet system, boolean byJump) {
	}

	/** W2: the owner's blockade over Tyrvann is broken. Chapter Five's business, not the turning's. */
	public static void onBlockadeBroken(ServerPlayer owner) {
	}

	/** W2: a {@code space_run} or {@code space_clear} objective of mission {@code missionId} ended. Counted by W4 for the deeds. */
	public static void onSpaceObjectiveDone(ServerPlayer p, String missionId, boolean success) {
		RankFour.markDirty(p);
	}

	/** W2: the player shot down a starfighter; {@code encounterOwner} is the encounter's owner, if any. Nothing to do here. */
	public static void onShipKill(ServerPlayer shooter, Entity fighter, @Nullable UUID encounterOwner) {
	}

	/** W3: the owner silenced verse stone {@code index}. Chapter Five's business, not the turning's. */
	public static void onVerseSilenced(ServerPlayer owner, int index) {
	}

	/**
	 * W8: the player took part in a clear of the Long Hymn; {@code firstClear} for their first (8.5's deeds). The deed is
	 * read from {@code RaidData} by {@code ChapterFive.deeds}, so the rank 4 checklist only needs a fresh look.
	 */
	public static void onRaidCleared(ServerPlayer p, boolean firstClear) {
		RankFour.markDirty(p);
	}

	/**
	 * W1: the player touched down on {@code planet}; {@code first} marks their first landing there. The Return's prisoners
	 * step off at a Greenwatch pad bay (8.3).
	 */
	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		ReturnTask.onPlanetArrived(p, planet);
	}

	/** W8: the player killed one of the five Milestone 5 foes. Ulvaine's echo counts toward the deeds, so the checklist looks again. */
	public static void onFoeKilled(ServerPlayer killer, LivingEntity victim, String kind, @Nullable Faction faction, boolean wasHostile) {
		RankFour.markDirty(killer);
	}

	// ---------------------------------------------------------------- seams for the commands and the game tests

	/**
	 * Makes the offer of {@code kind} now, whatever the band (the slow tick makes it when it is due): the record goes to
	 * OFFERED and a messenger comes at the next safe moment. Returns false when the player is not between questlines.
	 */
	public static boolean offer(ServerPlayer p, TurningKind kind) {
		return TurningOffers.offer(p, kind);
	}

	/**
	 * Begins the confrontation now with {@code mentor}, as when the old mentor arrives: on {@code peaceful} a talk in which
	 * the mentor yields at once, else the duel; without a mentor the duel is skipped as if the mentor had yielded.
	 */
	public static void beginConfrontation(ServerPlayer p, @Nullable MentorEntity mentor, boolean peaceful) {
		Confrontations.begin(p, mentor, peaceful);
	}

	/** Game time before which the old mentor will not come again after a reset of the confrontation, or -1. */
	public static long confrontationRetryAt(ServerPlayer p) {
		return Confrontations.retryAt(p);
	}

	// ---------------------------------------------------------------- events and the slow tick

	/** Registers the turning's server events. Called once from {@link TurningFeature#init()}. */
	static void registerEvents() {
		ServerTickEvents.END_SERVER_TICK.register(TurningService::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				onPlayerDied(p);
			} else {
				ReturnTask.onEntityDied(entity);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ReturnTask::allowDamage);
		ServerEntityEvents.ENTITY_LOAD.register(TurningService::onEntityLoad);
		ServerPlayerEvents.LEAVE.register(TurningService::forget);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
	}

	/** Every server tick: the deferred removals, the Return's scenes, and each player's slow tick on their turn. */
	static void tick(MinecraftServer server) {
		flushDiscards(server);
		ReturnTask.tickServer(server);
		int now = server.getTickCount();

		for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) {
			if (p.isAlive() && Math.floorMod(now + p.getId(), SLOW_TICKS) == 0) {
				tickPlayer(p);
			}
		}
	}

	/** One player's slow tick (also run by the commands): the offer, the step under way and rank 4. */
	public static void tickPlayer(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		long day = Clocks.day(server);
		long time = Clocks.time(server);
		TurningOffers.tick(p, day, time);
		StoryData.Turning t = Story.get(p).turning;

		switch (t.stage()) {
			case TASK -> {
				if (t.kind() == TurningKind.FALL) {
					FallTask.tick(p);
				} else if (t.kind() == TurningKind.RETURN) {
					ReturnTask.tick(p);
				}
			}
			case CONFRONTATION -> Confrontations.tick(p, time);
			case RITE -> Rites.tick(p);
			default -> {
			}
		}

		Mentors.keepBladeless(p);
		RankFour.tick(p, day);
	}

	/**
	 * The player died (or a game test says they did): the Fall's holocron goes back to Ansa, the Return's prisoners back to
	 * their cells, and a confrontation resets after {@code confrontationResetTicks} (8.3).
	 */
	public static void onPlayerDied(ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;

		switch (t.stage()) {
			case TASK -> {
				if (t.kind() == TurningKind.FALL) {
					FallTask.restart(p);
				} else if (t.kind() == TurningKind.RETURN) {
					ReturnTask.restart(p);
				}
			}
			case CONFRONTATION -> Confrontations.reset(p, Clocks.time(p.level().getServer()));
			default -> {
			}
		}
	}

	/** Leftovers of the turning saved with their chunks (a messenger, the post's people, a dropped holocron) leave on load. */
	private static void onEntityLoad(Entity entity, ServerLevel level) {
		WatchHolocronItem.onEntityLoad(entity);
		TurningOffers.onEntityLoad(entity);
		ReturnTask.onEntityLoad(entity);
	}

	/** The player left the server: everything kept in memory for them goes. */
	private static void forget(ServerPlayer p) {
		TurningOffers.forget(p);
		ReturnTask.forget(p);
		Confrontations.forget(p.getUUID());
		RankFour.forget(p.getUUID());
		FallTask.forget(p.getUUID());
		Mentors.forget(p.getUUID());
	}

	private static void clearAll() {
		DISCARD.clear();
		LEAVING.clear();
		TurningOffers.clear();
		ReturnTask.clear();
		Confrontations.clear();
		RankFour.clear();
		FallTask.clear();
		Mentors.clear();
	}

	// ---------------------------------------------------------------- removals

	/** Removes {@code entity} on the next server tick (safe from inside an entity-load callback). */
	static void discardLater(Entity entity) {
		DISCARD.add(entity);
	}

	/** Sends {@code mob} walking away from {@code from}; it is removed after {@code ticks}, or at once when it cannot walk. */
	static void walkOff(Mob mob, Vec3 from, int ticks) {
		Vec3 away = mob.position().subtract(from);
		Vec3 dir = away.horizontalDistanceSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
		Vec3 target = mob.position().add(dir.scale(24));
		mob.getNavigation().moveTo(target.x, target.y, target.z, 0.9);
		LEAVING.add(new Leaving(mob, mob.level().getGameTime() + ticks));
	}

	private static void flushDiscards(MinecraftServer server) {
		if (!DISCARD.isEmpty()) {
			for (Entity entity : List.copyOf(DISCARD)) {
				if (!entity.isRemoved()) {
					entity.discard();
				}
			}

			DISCARD.clear();
		}

		if (LEAVING.isEmpty()) {
			return;
		}

		for (Iterator<Leaving> it = LEAVING.iterator(); it.hasNext();) {
			Leaving leaving = it.next();

			if (leaving.mob().isRemoved()) {
				it.remove();
			} else if (leaving.mob().level().getGameTime() >= leaving.at()) {
				leaving.mob().discard();
				it.remove();
			}
		}
	}

	private record Leaving(Mob mob, long at) {
	}

	// ---------------------------------------------------------------- shared helpers

	/** A line in chat in the story's "Name: line" form. */
	static void say(ServerPlayer p, Component speaker, String lineKey, Object... args) {
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", speaker, Component.translatable(lineKey, args)));
	}

	/** A line spoken by {@code role}'s mentor, by name. */
	static void mentorSays(ServerPlayer p, MentorRole role, String lineKey) {
		say(p, mentorName(role), lineKey);
	}

	/** The mentor's name: Master Ilyra Saelen or Overseer Teyvar Sorn. */
	static Component mentorName(MentorRole role) {
		return Component.translatable("npc.oldrepublic." + role.id() + ".name");
	}

	/** The milestone banner ({@code lesson_complete}, W4's banners 12 and 13) in the new path's colours. */
	static void banner(ServerPlayer p, int variant) {
		EffectEvents.toPlayer(p, QuestEffects.LESSON_COMPLETE, p.position(), null, p.getId(), Story.get(p).storyPath().ordinal(), variant);
	}

	/** Unlocks a journal entry (each once). */
	static void entry(ServerPlayer p, String id) {
		Journal.unlockEntry(p, id);
	}

	/** Whether the player's world is on Peaceful (the confrontations become talks, the post has no guards). */
	static boolean peaceful(ServerPlayer p) {
		return p.level().getDifficulty() == Difficulty.PEACEFUL;
	}

	/** The player's mentor of {@code role} if it is loaded on the home world, else null. */
	static @Nullable MentorEntity mentor(ServerPlayer p, MentorRole role) {
		UUID id = Story.get(p).mentor(role.id());
		Entity entity = id == null ? null : p.level().getServer().overworld().getEntity(id);
		return entity instanceof MentorEntity mentor && mentor.isAlive() ? mentor : null;
	}

	/**
	 * A free spot for a person to stand {@code min} to {@code max} blocks (horizontally) from {@code center}: solid ground,
	 * room for the feet and the head, no liquid. Tries a few seeded directions and heights; null when none fits.
	 */
	static @Nullable BlockPos standNear(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int dist = min + (max > min ? random.nextInt(max - min + 1) : 0);
			int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
			int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);

			for (int dy = 3; dy >= -4; dy--) {
				BlockPos feet = new BlockPos(x, center.getY() + dy, z);

				if (level.hasChunkAt(feet) && standable(level, feet)) {
					return feet;
				}
			}
		}

		return null;
	}

	/** Solid ground under {@code feet}, and no solid block or liquid at the feet or the head. */
	static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos ground = feet.below();
		return level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP) && roomy(level, feet) && roomy(level, feet.above());
	}

	private static boolean roomy(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).blocksMotion() && level.getFluidState(pos).isEmpty();
	}

	private TurningService() {
	}
}

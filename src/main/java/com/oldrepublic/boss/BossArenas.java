package com.oldrepublic.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.block.ReliquaryHeartBlockEntity;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The boss arenas (spec 8): the Reliquaries the server knows (tracked while their heart is loaded),
 * the running {@link ArenaSession}s keyed by heart, and the entry points other features use: who is
 * inside an arena, resolving the Collector's fate, the rematch. The three Phase 0 signatures are
 * frozen (15.1); the rest is W5's (plan 4.4).
 *
 * <p>The heart block entity's ticker calls {@link #tickHeart}: READY while the owner is within
 * {@code readyRange} (Vasch at the lectern), DORMANT when they are away, and Vasch kneeling at the dais
 * when the owner comes back to a YIELDED heart. Sessions themselves are ticked here at the end of each
 * level tick and are dropped when their heart unloads or the server stops (a fight then resets).
 */
public final class BossArenas {
	/** The boss id of every {@code QuestHooks} boss call, rematches included. */
	public static final String COLLECTOR = "collector";
	/** The heart's dialogue once the Collector is resolved: the echo and Unchained (8.9). */
	public static final Identifier HEART_DIALOGUE = OldRepublicMod.id("reliquary_heart");
	/** How often a heart re-checks its owner's range. */
	private static final int HEART_CHECK_TICKS = 10;

	private record Key(ResourceKey<Level> dimension, BlockPos heart) {
		static Key of(Level level, BlockPos heart) {
			return new Key(level.dimension(), heart.immutable());
		}
	}

	/** A tracked Reliquary: its whole hall box and, when known, its owner. */
	private static final class Arena {
		final BoundingBox box;
		@Nullable UUID owner;

		Arena(BoundingBox box, @Nullable UUID owner) {
			this.box = box;
			this.owner = owner;
		}
	}

	private static final Map<Key, Arena> ARENAS = new HashMap<>();
	private static final Map<Key, ArenaSession> SESSIONS = new HashMap<>();
	private static final Map<Key, ArenaLayout> LAYOUTS = new HashMap<>();

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(BossArenas::tickLevel);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof ReliquaryHeartBlockEntity heart && heart.hallBox() != null) {
				trackArena(level, heart.getBlockPos(), heart.hallBox());
				ARENAS.get(Key.of(level, heart.getBlockPos())).owner = heart.owner();
			}
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof ReliquaryHeartBlockEntity heart) {
				Key key = Key.of(level, heart.getBlockPos());
				ArenaSession session = SESSIONS.remove(key);

				if (session != null) {
					session.end();
				}

				ARENAS.remove(key);
				LAYOUTS.remove(key);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ArenaSession session : List.copyOf(SESSIONS.values())) {
				session.end();
			}

			SESSIONS.clear();
			ARENAS.clear();
			LAYOUTS.clear();
		});
		UseBlockCallback.EVENT.register(BossArenas::onUseBlock);
	}

	// ---------------------------------------------------------------- frozen (15.1)

	/** Whether the entity is inside a tracked Reliquary box (the whole hall template). */
	public static boolean isInArena(Entity e) {
		ResourceKey<Level> dimension = e.level().dimension();
		BlockPos pos = e.blockPosition();

		for (Map.Entry<Key, Arena> entry : ARENAS.entrySet()) {
			if (entry.getKey().dimension().equals(dimension) && entry.getValue().box.isInside(pos)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Resolves the owner's yielded Collector with {@code outcome} and plays its scene (8.7): the heart
	 * becomes RESOLVED, the vault opens, and {@code QuestHooks.onBossDefeated} runs once per participant.
	 */
	public static void resolve(ServerPlayer owner, BossOutcome outcome) {
		ArenaSession session = sessionOwnedBy(owner);

		if (session == null || session.stage() != ArenaSession.Stage.YIELDED) {
			// The owner came back to a yielded heart whose session was dropped: kneel him again first.
			session = ensureSession(owner);
		}

		if (session == null || !session.resolve(outcome)) {
			OldRepublicMod.LOGGER.warn("BossArenas.resolve({}, {}): no yielded Collector for this player", owner.getName().getString(), outcome);
		}
	}

	/** Whether the owner's Collector has been resolved (their heart is RESOLVED; false while it is not loaded). */
	public static boolean isResolved(ServerPlayer owner) {
		BlockPos heart = heartOf(owner);

		if (heart == null) {
			return false;
		}

		ArenaSession session = SESSIONS.get(Key.of(owner.level(), heart));

		if (session != null && session.heartState() == HeartState.RESOLVED) {
			return true;
		}

		return owner.level().getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity && entity.state() == HeartState.RESOLVED;
	}

	// ---------------------------------------------------------------- tracking (plan 4.4)

	/** Marks a Reliquary box as an arena: {@link #isInArena} is true inside it while it is tracked. */
	public static void trackArena(ServerLevel level, BlockPos heart, BoundingBox reliquary) {
		Key key = Key.of(level, heart);
		Arena old = ARENAS.get(key);
		BoundingBox box = new BoundingBox(reliquary.minX(), reliquary.minY(), reliquary.minZ(), reliquary.maxX(), reliquary.maxY(), reliquary.maxZ());
		ARENAS.put(key, new Arena(box, old == null ? null : old.owner));
	}

	public static void untrackArena(ServerLevel level, BlockPos heart) {
		Key key = Key.of(level, heart);
		ARENAS.remove(key);
		LAYOUTS.remove(key);
	}

	/** B's build set the heart (its last block): the heart learns its owner and hall, and the arena is tracked. */
	public static void onReliquaryPlaced(ServerLevel level, BlockPos heart, UUID owner, SitePiece hall) {
		if (level.getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity) {
			entity.setOwner(owner);
			entity.setHall(hall);
			entity.setState(HeartState.DORMANT);
		} else {
			OldRepublicMod.LOGGER.warn("Reliquary heart at {} has no block entity", heart);
		}

		trackArena(level, heart, hall.box());
		ARENAS.get(Key.of(level, heart)).owner = owner;
		LAYOUTS.remove(Key.of(level, heart));
	}

	/** The owner's tracked heart in their level, or null. */
	public static @Nullable BlockPos heartOf(ServerPlayer owner) {
		ResourceKey<Level> dimension = owner.level().dimension();

		for (Map.Entry<Key, Arena> entry : ARENAS.entrySet()) {
			if (entry.getKey().dimension().equals(dimension) && owner.getUUID().equals(entry.getValue().owner)) {
				return entry.getKey().heart();
			}
		}

		return null;
	}

	/** True for a participant of an Unchained echo session (valid while {@code onBossDefeated} runs; W2 reads it). */
	public static boolean isUnchained(Entity participant) {
		for (ArenaSession session : SESSIONS.values()) {
			if (session.isUnchained() && session.stage() != ArenaSession.Stage.ENDED && session.isParticipant(participant.getUUID())) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------- sessions

	/** The session at {@code heart}, or null. */
	public static @Nullable ArenaSession sessionAt(ServerLevel level, BlockPos heart) {
		return SESSIONS.get(Key.of(level, heart));
	}

	/** The running session whose owner is {@code owner}, in their level, or null. */
	public static @Nullable ArenaSession sessionOwnedBy(ServerPlayer owner) {
		ResourceKey<Level> dimension = owner.level().dimension();

		for (Map.Entry<Key, ArenaSession> entry : SESSIONS.entrySet()) {
			if (entry.getKey().dimension().equals(dimension) && owner.getUUID().equals(entry.getValue().owner())
					&& entry.getValue().stage() != ArenaSession.Stage.ENDED) {
				return entry.getValue();
			}
		}

		return null;
	}

	/**
	 * A session on a given layout with no heart block entity required (game tests, on the lead's
	 * fixtures): Vasch at the lectern (WAITING), or the forming echo. Replaces any session at
	 * {@code heart}; {@link #closeSession} ends it.
	 */
	public static ArenaSession openSession(ServerLevel level, BlockPos heart, ArenaLayout layout, @Nullable UUID owner, CollectorHooks.Sink hooks,
			boolean echo, boolean unchained) {
		Key key = Key.of(level, heart);
		ArenaSession old = SESSIONS.remove(key);

		if (old != null) {
			old.end();
		}

		trackArena(level, heart, layout.reliquary());
		ARENAS.get(key).owner = owner;
		ArenaSession session = new ArenaSession(level, heart, layout, owner, hooks, false, echo, unchained);
		SESSIONS.put(key, session);

		if (echo) {
			session.beginEcho();
		} else {
			session.spawnReady();
		}

		return session;
	}

	/** Ends and forgets the session at {@code heart} and its arena (game tests). */
	public static void closeSession(ServerLevel level, BlockPos heart) {
		Key key = Key.of(level, heart);
		ArenaSession session = SESSIONS.remove(key);

		if (session != null) {
			session.end();
		}

		untrackArena(level, heart);
	}

	/**
	 * The owner's session, created when their heart is loaded and has none yet: Vasch at the lectern
	 * for a DORMANT or READY heart (made READY), kneeling for a YIELDED one; null when resolved or when
	 * the owner has no heart here (commands, a yield choice after a restart).
	 */
	public static @Nullable ArenaSession ensureSession(ServerPlayer owner) {
		ArenaSession session = sessionOwnedBy(owner);

		if (session != null) {
			return session;
		}

		BlockPos heart = heartOf(owner);
		ServerLevel level = (ServerLevel) owner.level();

		if (heart == null || !(level.getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity)) {
			return null;
		}

		return switch (entity.state()) {
			case DORMANT, READY, ACTIVE -> {
				entity.setState(HeartState.READY);
				yield create(level, heart, entity, false, false);
			}
			case YIELDED -> create(level, heart, entity, false, false);
			case RESOLVED -> null;
		};
	}

	private static @Nullable ArenaSession create(ServerLevel level, BlockPos heart, ReliquaryHeartBlockEntity entity, boolean echo, boolean unchained) {
		ArenaLayout layout = layoutOf(level, heart, entity);

		if (layout == null) {
			return null;
		}

		Key key = Key.of(level, heart);
		ArenaSession session = new ArenaSession(level, heart, layout, entity.owner(), CollectorHooks.QUEST, true, echo, unchained);
		SESSIONS.put(key, session);

		if (echo) {
			session.beginEcho();
		} else if (entity.state() == HeartState.YIELDED) {
			session.spawnKneeling();
		} else {
			session.spawnReady();
		}

		return session;
	}

	private static @Nullable ArenaLayout layoutOf(ServerLevel level, BlockPos heart, ReliquaryHeartBlockEntity entity) {
		Key key = Key.of(level, heart);
		ArenaLayout cached = LAYOUTS.get(key);

		if (cached != null) {
			return cached;
		}

		SitePiece hall = entity.hallPiece();

		if (hall == null) {
			return null;
		}

		ArenaLayout layout = ArenaLayout.fromHall(hall, heart, descentTop(level, entity.owner(), hall.box()));
		LAYOUTS.put(key, layout);
		return layout;
	}

	/** The top of the spiral descent, from the owner's surface piece above this hall (for the walk-outs). */
	private static @Nullable BlockPos descentTop(ServerLevel level, @Nullable UUID owner, BoundingBox hall) {
		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if (record.kind() != SiteKind.RELIQUARY || !record.template().equals(ReliquarySites.SURFACE) || !record.dimension().equals(level.dimension())
					|| owner != null && !owner.equals(record.owner())) {
				continue;
			}

			SitePiece surface = RuntimeSites.pieceOf(level, record);

			if (surface != null && surface.box().minX() <= hall.maxX() && surface.box().maxX() >= hall.minX() && surface.box().minZ() <= hall.maxZ()
					&& surface.box().maxZ() >= hall.minZ()) {
				return SiteAnchors.find(surface, SiteAnchors.Anchor.DESCENT_TOP);
			}
		}

		return null;
	}

	/** One heart tick (from its block entity): the owner's range, and the session the state needs. */
	public static void tickHeart(ServerLevel level, BlockPos pos, ReliquaryHeartBlockEntity heart) {
		Key key = Key.of(level, pos);

		if (heart.hallBox() != null && !ARENAS.containsKey(key)) {
			trackArena(level, pos, heart.hallBox());
			ARENAS.get(key).owner = heart.owner();
		}

		if (Math.floorMod(level.getGameTime() + pos.hashCode(), HEART_CHECK_TICKS) != 0 || heart.owner() == null || heart.hallPiece() == null) {
			return;
		}

		CollectorBalance b = CollectorBalance.get();
		boolean ownerNear = ownerNear(level, pos, heart.owner(), b);
		ArenaSession session = SESSIONS.get(key);

		if (session != null && session.stage() == ArenaSession.Stage.ENDED) {
			SESSIONS.remove(key);
			session = null;
		}

		switch (heart.state()) {
			case DORMANT -> {
				if (ownerNear) {
					heart.setState(HeartRules.step(HeartState.DORMANT, HeartRules.Event.OWNER_NEAR));

					if (session == null) {
						create(level, pos, heart, false, false);
					}
				}
			}
			case READY, ACTIVE -> {
				if (session == null) {
					if (ownerNear) {
						heart.setState(HeartState.READY);
						create(level, pos, heart, false, false);
					} else {
						heart.setState(HeartState.DORMANT);
					}
				} else if (!ownerNear && session.stage() == ArenaSession.Stage.WAITING && !anyAwakenedInside(level, session)) {
					session.end();
					SESSIONS.remove(key);
					heart.setState(HeartRules.step(HeartState.READY, HeartRules.Event.OWNER_AWAY));
				}
			}
			case YIELDED -> {
				if (session == null && ownerNear) {
					create(level, pos, heart, false, false);
				} else if (session != null && !ownerNear && session.stage() == ArenaSession.Stage.YIELDED && !anyoneInside(level, session)) {
					session.end();
					SESSIONS.remove(key);
				}
			}
			case RESOLVED -> {
			}
		}
	}

	private static boolean ownerNear(ServerLevel level, BlockPos heart, UUID owner, CollectorBalance b) {
		for (ServerPlayer player : level.players()) {
			if (player.getUUID().equals(owner) && player.isAlive() && HeartRules.ownerNear(player.distanceToSqr(heart.getCenter()), b)) {
				return true;
			}
		}

		return false;
	}

	private static boolean anyAwakenedInside(ServerLevel level, ArenaSession session) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && session.layout().isInHall(player) && Progression.get(player).isAwakened()) {
				return true;
			}
		}

		return false;
	}

	private static boolean anyoneInside(ServerLevel level, ArenaSession session) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && session.layout().isInReliquary(player.position())) {
				return true;
			}
		}

		return false;
	}

	private static void tickLevel(ServerLevel level) {
		if (SESSIONS.isEmpty()) {
			return;
		}

		List<Map.Entry<Key, ArenaSession>> entries = new ArrayList<>(SESSIONS.entrySet());

		for (Map.Entry<Key, ArenaSession> entry : entries) {
			if (!entry.getKey().dimension().equals(level.dimension())) {
				continue;
			}

			ArenaSession session = entry.getValue();

			if (!level.isLoaded(entry.getKey().heart())) {
				session.end();
			} else {
				session.tick();
			}

			if (session.stage() == ArenaSession.Stage.ENDED) {
				SESSIONS.remove(entry.getKey(), session);
			}
		}
	}

	// ---------------------------------------------------------------- the rematch (8.9)

	/**
	 * The owner calls the echo at their resolved heart ({@code boss:call_echo}, or {@code boss:unchained}
	 * with {@code unchained}). Returns false, with a message, when it cannot start now.
	 */
	public static boolean callEcho(ServerPlayer owner, boolean unchained) {
		BlockPos heart = heartOf(owner);
		ServerLevel level = (ServerLevel) owner.level();

		if (heart == null || !(level.getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity) || entity.state() != HeartState.RESOLVED) {
			owner.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.no_echo"));
			return false;
		}

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			owner.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.peaceful"));
			return false;
		}

		if (unchained && !entity.unchainedUnlocked()) {
			owner.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.unchained_locked"));
			return false;
		}

		ArenaSession running = SESSIONS.get(Key.of(level, heart));

		if (running != null && running.stage() != ArenaSession.Stage.ENDED) {
			owner.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.echo_running"));
			return false;
		}

		return create(level, heart, entity, true, unchained) != null;
	}

	/** Whether the owner may call the echo now (the dialogue's condition). */
	static boolean echoAvailable(ServerPlayer owner) {
		BlockPos heart = heartOf(owner);

		if (heart == null || !(owner.level().getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity) || entity.state() != HeartState.RESOLVED) {
			return false;
		}

		ArenaSession running = SESSIONS.get(Key.of(owner.level(), heart));
		return running == null || running.stage() == ArenaSession.Stage.ENDED;
	}

	/** Whether the owner has unlocked Unchained (one echo clear). */
	static boolean unchainedAvailable(ServerPlayer owner) {
		BlockPos heart = heartOf(owner);
		return heart != null && owner.level().getBlockEntity(heart) instanceof ReliquaryHeartBlockEntity entity && entity.unchainedUnlocked();
	}

	// ---------------------------------------------------------------- block callbacks

	/** The heart was used: the rematch dialogue once resolved (owner only), else a word on its state. */
	public static void onHeartUsed(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!(level.getBlockEntity(pos) instanceof ReliquaryHeartBlockEntity entity)) {
			return;
		}

		if (entity.owner() == null) {
			player.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.inert"));
			return;
		}

		boolean isOwner = entity.owner().equals(player.getUUID());

		switch (entity.state()) {
			case RESOLVED -> {
				ArenaSession running = SESSIONS.get(Key.of(level, pos));

				if (!isOwner) {
					player.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.not_owner"));
				} else if (running != null && running.stage() != ArenaSession.Stage.ENDED) {
					player.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.echo_running"));
				} else {
					Dialogues.open(player, HEART_DIALOGUE, null);
				}
			}
			case YIELDED -> player.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.yielded"));
			case ACTIVE -> player.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.fighting"));
			default -> player.sendOverlayMessage(Component.translatable("boss.oldrepublic.heart.listening"));
		}
	}

	/** A player used a closed doorway seal at {@code seal}: a listed participant is let back in. */
	public static boolean letParticipantThrough(ServerLevel level, BlockPos seal, ServerPlayer player) {
		for (Map.Entry<Key, ArenaSession> entry : SESSIONS.entrySet()) {
			if (entry.getKey().dimension().equals(level.dimension()) && entry.getValue().layout().doorSeals().contains(seal)) {
				return entry.getValue().letThrough(player);
			}
		}

		return false;
	}

	/** Whether the vault holding {@code plinth} is open: its Reliquary's heart is RESOLVED. */
	public static boolean isVaultOpen(ServerLevel level, BlockPos plinth) {
		for (Map.Entry<Key, Arena> entry : ARENAS.entrySet()) {
			Key key = entry.getKey();

			if (!key.dimension().equals(level.dimension()) || !entry.getValue().box.isInside(plinth)) {
				continue;
			}

			ArenaSession session = SESSIONS.get(key);

			if (session != null && session.heartState() == HeartState.RESOLVED) {
				return true;
			}

			if (level.getBlockEntity(key.heart()) instanceof ReliquaryHeartBlockEntity entity && entity.state() == HeartState.RESOLVED) {
				return true;
			}
		}

		return false;
	}

	/** The vanilla lectern in a waiting hall starts a later attempt ("Face me."). */
	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND
				|| !level.getBlockState(hit.getBlockPos()).is(Blocks.LECTERN)) {
			return InteractionResult.PASS;
		}

		for (Map.Entry<Key, ArenaSession> entry : SESSIONS.entrySet()) {
			ArenaSession session = entry.getValue();

			if (entry.getKey().dimension().equals(server.dimension()) && session.layout().isInReliquary(hit.getBlockPos().getCenter())
					&& session.onLecternUsed(serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.PASS;
	}

	private BossArenas() {
	}
}

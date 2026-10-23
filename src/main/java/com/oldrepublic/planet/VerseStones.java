package com.oldrepublic.planet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.block.VerseStoneBlock;
import com.oldrepublic.planet.block.VerseStoneBlockEntity;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.world.site.SiteChecks;
import com.oldrepublic.world.site.SpawnerGroups;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The verse stones' rules (spec MILESTONE_5.md 3.3.2, 6.6, 6.9), on the server:
 * <ul>
 * <li><b>Guards.</b> The first time the owner comes within {@code story.encounterSpawnDistance}
 * (48) of an awake stone, it spawns W8's {@code hymnsworn} group at its {@code SPAWNER} anchor,
 * {@code planets.verseGuards} members by the stone's index, owned by the owner. Not on Peaceful,
 * and not while the group is unknown (before W8's part is in): the stone then has no guards.</li>
 * <li><b>The slowing hum.</b> Every {@code planets.humSlowInterval} ticks while any guard lives,
 * Slowness I for the players within {@code planets.humSlowRange}.</li>
 * <li><b>The channel.</b> The owner, or a helper while the owner is online in the same world
 * within {@link #HELPER_RANGE} of the stone (there is no party system, so standing together is the
 * party rule; DECISIONS.md), holds the use key on the stone with no guard alive. Each use keeps
 * the channel alive for {@link #RELEASE_GRACE} ticks (the held key repeats every 4); it fills over
 * {@code planets.silenceTicks}. It breaks when the key is released, the channeller moves more than
 * {@link #CHANNEL_RANGE} blocks away, takes damage, dies, leaves, or a guard comes back. A Push,
 * Pull or Lift cast at the stone during the channel cuts {@code planets.silenceForceCut} ticks,
 * once per channel (the stone "answers" the Force).</li>
 * <li><b>Silence.</b> The stone goes dark ({@link VerseStoneBlock#AWAKE} false) and
 * {@link QuestHooks#onVerseSilenced} fires for the owner (later, when the owner is next online, if
 * they are away). On Peaceful a use silences it at once.</li>
 * </ul>
 * Another player's stone does nothing for them but say so.
 */
public final class VerseStones {
	/** W8's guard group (12.1). */
	public static final String GUARD_GROUP = "hymnsworn";
	/** The channel breaks when the channeller is farther than this from the stone's centre. */
	public static final double CHANNEL_RANGE = 6;
	/** A helper may channel while the owner is this close to the stone. */
	public static final double HELPER_RANGE = 32;
	/** Ticks a use keeps the channel alive (the held key repeats the use every 4 ticks). */
	public static final int RELEASE_GRACE = 10;
	/** Ticks between two progress events of a channel. */
	public static final int PROGRESS_EVENT_TICKS = 5;
	/** Ticks between two guard checks of a stone. */
	public static final int GUARD_CHECK_TICKS = 20;
	/** The slowing hum lasts this many ticks past the next pulse, so it never flickers off between two. */
	private static final int HUM_OVERLAP = 10;
	private static final int REFUSE_COOLDOWN = 20;

	/** Who channels where, for the damage hook. */
	private static final Map<UUID, GlobalPos> CHANNELS = new HashMap<>();
	private static final Map<UUID, Long> LAST_REFUSAL = new HashMap<>();

	/** Registers the Force answer and the damage hook (from {@link PlanetM5Feature}). */
	static void init() {
		ForceBlockInteractions.register(PlanetBlocks.VERSE_STONE, VerseStones::forceAnswer);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (damageTaken > 0 && entity instanceof ServerPlayer player) {
				onDamaged(player);
			}
		});
	}

	/** Forgets one player's channel and refusal clock (logout). */
	static void forget(UUID player) {
		CHANNELS.remove(player);
		LAST_REFUSAL.remove(player);
	}

	/** Forgets everything (server stop). */
	static void clear() {
		CHANNELS.clear();
		LAST_REFUSAL.clear();
	}

	/**
	 * The party rule (pure, for the tests): the owner may always channel their stone; anyone else
	 * only while the owner is online in the same world within {@link #HELPER_RANGE} of it
	 * ({@code ownerPos} null when the owner is not). A stone without an owner sings for no one.
	 */
	public static boolean mayChannel(@Nullable UUID owner, UUID player, @Nullable Vec3 ownerPos, Vec3 stone) {
		if (owner == null) {
			return false;
		}

		return owner.equals(player) || ownerPos != null && ownerPos.distanceToSqr(stone) <= HELPER_RANGE * HELPER_RANGE;
	}

	/**
	 * Whether the slowing hum of a stone at {@code centre} slows {@code player} (for the tests): a
	 * living player in survival or adventure within {@code range} blocks.
	 */
	public static boolean humSlows(Player player, Vec3 centre, double range) {
		return !player.isSpectator() && !player.isCreative() && player.isAlive() && player.position().distanceToSqr(centre) <= range * range;
	}

	/** Guards of the {@code index}th stone (1 to 3): {@code planets.verseGuards}, at least 1. */
	public static int guardsFor(int index) {
		int[] guards = PlanetBalance.get().verseGuards;

		if (guards.length == 0) {
			return 3;
		}

		return Math.max(1, guards[Math.max(0, Math.min(guards.length - 1, index - 1))]);
	}

	/** A use of the stone at {@code pos} by {@code player}: refuses, silences (Peaceful), starts or keeps the channel. */
	public static void use(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!(level.getBlockEntity(pos) instanceof VerseStoneBlockEntity stone) || !stone.awake() || player.isSpectator()) {
			return;
		}

		long now = level.getGameTime();

		if (!eligible(level, stone, player)) {
			refuse(player, "message.oldrepublic.verse_stone.not_yours", now);
			return;
		}

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			silence(level, pos, stone, player);
			return;
		}

		if (!stone.guardsSpawned()) {
			spawnGuards(level, pos, stone);
		}

		if (guardsAlive(level, stone)) {
			refuse(player, "message.oldrepublic.verse_stone.guarded", now);
			return;
		}

		UUID current = stone.channeller();
		boolean running = current != null && now - stone.lastUse() <= RELEASE_GRACE;

		if (running && current.equals(player.getUUID())) {
			stone.refreshChannel(now);
			return;
		}

		if (running) {
			refuse(player, "message.oldrepublic.verse_stone.busy", now);
			return;
		}

		if (!inRange(player, pos)) {
			return;
		}

		endOtherChannel(level.getServer(), player, pos);
		stone.startChannel(player.getUUID(), now);
		CHANNELS.put(player.getUUID(), GlobalPos.of(level.dimension(), pos.immutable()));
		EffectEvents.around(level, PlanetEffects.VERSE_STONE, Vec3.atCenterOf(pos), towards(pos, player), player.getId(), 0f, PlanetEffects.VERSE_CHANNEL_START);
	}

	/** One server tick of a stone: its channel, its guards and its hum; a silent stone only pays an owed hook. */
	public static void tick(ServerLevel level, BlockPos pos, BlockState state, VerseStoneBlockEntity stone) {
		long now = level.getGameTime();
		long phase = now + Math.floorMod(pos.asLong(), GUARD_CHECK_TICKS);

		if (!stone.awake()) {
			if (stone.hookOwed() && phase % GUARD_CHECK_TICKS == 0) {
				payOwedHook(level, stone);
			}

			return;
		}

		UUID channeller = stone.channeller();

		if (channeller != null) {
			tickChannel(level, pos, stone, channeller, now);

			if (!stone.awake()) {
				return;
			}
		}

		if (phase % GUARD_CHECK_TICKS == 0) {
			pruneGuards(level, pos, stone);

			if (!stone.guardsSpawned() && ownerNear(level, pos, stone, StoryService.balance().encounterSpawnDistance)) {
				spawnGuards(level, pos, stone);
			}
		}

		int interval = Math.max(20, PlanetBalance.get().humSlowInterval);

		if ((now + Math.floorMod(pos.asLong(), interval)) % interval == 0 && guardsAlive(level, stone)) {
			hum(level, pos, interval);
		}
	}

	private static void tickChannel(ServerLevel level, BlockPos pos, VerseStoneBlockEntity stone, UUID channeller, long now) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(channeller);
		boolean present = player != null && player.level() == level && player.isAlive() && !player.isSpectator();

		if (!present || now - stone.lastUse() > RELEASE_GRACE || !inRange(player, pos) || !eligible(level, stone, player) || guardsAlive(level, stone)) {
			breakChannel(level, pos, stone, present ? player : null);
			return;
		}

		int total = Math.max(1, PlanetBalance.get().silenceTicks);
		int progress = stone.advance(1);

		if (progress >= total) {
			silence(level, pos, stone, player);
		} else if (progress % PROGRESS_EVENT_TICKS == 0) {
			EffectEvents.around(level, PlanetEffects.VERSE_STONE, Vec3.atCenterOf(pos), towards(pos, player), player.getId(), progress / (float) total,
					PlanetEffects.VERSE_CHANNEL_PROGRESS);
		}
	}

	/** The Force answers (a Push, Pull or Lift on the stone): the running channel loses {@code planets.silenceForceCut} ticks, once. */
	static boolean forceAnswer(ForceBlockContext ctx) {
		ServerLevel level = ctx.level();
		BlockPos pos = ctx.pos();

		if (!(level.getBlockEntity(pos) instanceof VerseStoneBlockEntity stone) || !stone.awake() || stone.channeller() == null || stone.forceCut()
				|| !eligible(level, stone, ctx.caster())) {
			return false;
		}

		stone.markForceCut();
		int total = Math.max(1, PlanetBalance.get().silenceTicks);
		int progress = stone.advance(Math.max(0, PlanetBalance.get().silenceForceCut));
		EffectEvents.around(level, PlanetEffects.VERSE_STONE, Vec3.atCenterOf(pos), towards(pos, ctx.caster()), ctx.caster().getId(), Math.min(1f, progress / (float) total),
				PlanetEffects.VERSE_FORCE);

		if (progress >= total) {
			ServerPlayer channeller = level.getServer().getPlayerList().getPlayer(stone.channeller());
			silence(level, pos, stone, channeller != null ? channeller : ctx.caster());
		}

		return true;
	}

	/** The damage hook: a channeller who takes damage breaks their channel. */
	public static void onDamaged(ServerPlayer player) {
		GlobalPos at = CHANNELS.get(player.getUUID());

		if (at == null) {
			return;
		}

		ServerLevel level = player.level().getServer().getLevel(at.dimension());

		if (level != null && level.getBlockEntity(at.pos()) instanceof VerseStoneBlockEntity stone && player.getUUID().equals(stone.channeller())) {
			breakChannel(level, at.pos(), stone, player);
		} else {
			CHANNELS.remove(player.getUUID());
		}
	}

	/** Silences the stone now: dark, no channel, the hook for its owner (owed when the owner is away). */
	public static void silence(ServerLevel level, BlockPos pos, VerseStoneBlockEntity stone, @Nullable ServerPlayer by) {
		UUID channeller = stone.channeller();

		if (channeller != null) {
			CHANNELS.remove(channeller);
		}

		stone.silence();
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof VerseStoneBlock) {
			level.setBlock(pos, state.setValue(VerseStoneBlock.AWAKE, false), Block.UPDATE_ALL);
		}

		EffectEvents.around(level, PlanetEffects.VERSE_STONE, Vec3.atCenterOf(pos), null, by != null ? by.getId() : -1, 1f, PlanetEffects.VERSE_SILENCED);

		if (by != null) {
			by.sendOverlayMessage(Component.translatable("message.oldrepublic.verse_stone.silenced"));
		}

		ServerPlayer owner = stone.owner() == null ? null : level.getServer().getPlayerList().getPlayer(stone.owner());

		if (owner != null) {
			QuestHooks.onVerseSilenced(owner, stone.index());
		} else if (stone.owner() != null) {
			stone.setHookOwed(true);
		}
	}

	/** Whether a guard spawned for the stone lives; a guard that cannot be found counts as alive until the entities around the stone have loaded. */
	public static boolean guardsAlive(ServerLevel level, VerseStoneBlockEntity stone) {
		List<UUID> guards = stone.guards();

		if (guards.isEmpty()) {
			return false;
		}

		boolean ready = SiteChecks.entitiesReady(level, stone.getBlockPos(), 1);

		for (UUID id : guards) {
			Entity entity = level.getEntity(id);

			if (entity instanceof LivingEntity living ? living.isAlive() : entity == null && !ready) {
				return true;
			}
		}

		return false;
	}

	/** Spawns the stone's guards when it has none yet: not on Peaceful, not without an owner, and not while W8's group is unknown. */
	private static void spawnGuards(ServerLevel level, BlockPos pos, VerseStoneBlockEntity stone) {
		UUID owner = stone.owner();

		if (stone.guardsSpawned() || owner == null || level.getDifficulty() == Difficulty.PEACEFUL || !SpawnerGroups.known(GUARD_GROUP)
				|| !SiteChecks.entitiesReady(level, pos, 1)) {
			return;
		}

		BlockPos at = stone.spawnAt() != null ? stone.spawnAt() : pos.relative(Direction.SOUTH, 4);
		List<UUID> ids = new ArrayList<>();

		for (LivingEntity member : SpawnerGroups.spawn(level, at, GUARD_GROUP, guardsFor(stone.index()), false, owner)) {
			ids.add(member.getUUID());
		}

		stone.setGuards(ids);
	}

	/** Forgets the guards that are dead or gone, once the entities around the stone have loaded. */
	private static void pruneGuards(ServerLevel level, BlockPos pos, VerseStoneBlockEntity stone) {
		if (stone.guards().isEmpty() || !SiteChecks.entitiesReady(level, pos, 1)) {
			return;
		}

		List<UUID> gone = new ArrayList<>();

		for (UUID id : stone.guards()) {
			Entity entity = level.getEntity(id);

			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				gone.add(id);
			}
		}

		stone.pruneGuards(gone);
	}

	/** The slowing hum: Slowness I for the players within {@code planets.humSlowRange}. */
	private static void hum(ServerLevel level, BlockPos pos, int interval) {
		Vec3 centre = Vec3.atCenterOf(pos);
		double range = PlanetBalance.get().humSlowRange;

		for (ServerPlayer player : level.players()) {
			if (humSlows(player, centre, range)) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, interval + HUM_OVERLAP, 0));
			}
		}

		EffectEvents.around(level, PlanetEffects.VERSE_STONE, centre, null, -1, 1f, PlanetEffects.VERSE_HUM);
	}

	private static void breakChannel(ServerLevel level, BlockPos pos, VerseStoneBlockEntity stone, @Nullable ServerPlayer player) {
		UUID channeller = stone.channeller();

		if (channeller != null) {
			CHANNELS.remove(channeller);
		}

		stone.clearChannel();
		EffectEvents.around(level, PlanetEffects.VERSE_STONE, Vec3.atCenterOf(pos), null, player != null ? player.getId() : -1, 0f,
				PlanetEffects.VERSE_CHANNEL_BROKEN);

		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.verse_stone.broken"));
		}
	}

	/** A player starting a channel here ends the one they had at another stone. */
	private static void endOtherChannel(MinecraftServer server, ServerPlayer player, BlockPos here) {
		GlobalPos other = CHANNELS.get(player.getUUID());

		if (other == null || other.pos().equals(here) && other.dimension().equals(player.level().dimension())) {
			return;
		}

		ServerLevel level = server.getLevel(other.dimension());

		if (level != null && level.getBlockEntity(other.pos()) instanceof VerseStoneBlockEntity stone && player.getUUID().equals(stone.channeller())) {
			breakChannel(level, other.pos(), stone, player);
		}
	}

	/** A stone silenced while its owner was away: the hook fires once the owner is online. */
	private static void payOwedHook(ServerLevel level, VerseStoneBlockEntity stone) {
		ServerPlayer owner = stone.owner() == null ? null : level.getServer().getPlayerList().getPlayer(stone.owner());

		if (owner != null) {
			stone.setHookOwed(false);
			QuestHooks.onVerseSilenced(owner, stone.index());
		}
	}

	private static boolean eligible(ServerLevel level, VerseStoneBlockEntity stone, ServerPlayer player) {
		UUID owner = stone.owner();
		ServerPlayer ownerPlayer = owner == null || owner.equals(player.getUUID()) ? null : level.getServer().getPlayerList().getPlayer(owner);
		Vec3 ownerPos = ownerPlayer != null && ownerPlayer.level() == level && ownerPlayer.isAlive() ? ownerPlayer.position() : null;
		return mayChannel(owner, player.getUUID(), ownerPos, Vec3.atCenterOf(stone.getBlockPos()));
	}

	private static boolean ownerNear(ServerLevel level, BlockPos pos, VerseStoneBlockEntity stone, double range) {
		ServerPlayer owner = stone.owner() == null ? null : level.getServer().getPlayerList().getPlayer(stone.owner());
		return owner != null && owner.level() == level && owner.isAlive() && !owner.isSpectator() && owner.position().distanceToSqr(Vec3.atCenterOf(pos)) <= range * range;
	}

	private static boolean inRange(ServerPlayer player, BlockPos pos) {
		return player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= CHANNEL_RANGE * CHANNEL_RANGE;
	}

	/** Unit vector from the stone toward the player's chest: the way the motes stream. */
	private static Vec3 towards(BlockPos pos, ServerPlayer player) {
		Vec3 to = player.position().add(0, player.getBbHeight() * 0.6, 0).subtract(Vec3.atCenterOf(pos));
		return to.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : to.normalize();
	}

	private static void refuse(ServerPlayer player, String key, long now) {
		Long last = LAST_REFUSAL.get(player.getUUID());

		if (last != null && now >= last && now - last < REFUSE_COOLDOWN) {
			return;
		}

		LAST_REFUSAL.put(player.getUUID(), now);

		if (LAST_REFUSAL.size() > 256) {
			for (Iterator<Map.Entry<UUID, Long>> it = LAST_REFUSAL.entrySet().iterator(); it.hasNext();) {
				if (now - it.next().getValue() > REFUSE_COOLDOWN) {
					it.remove();
				}
			}
		}

		player.sendOverlayMessage(Component.translatable(key));
	}

	private VerseStones() {
	}
}

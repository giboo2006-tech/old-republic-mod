package com.oldrepublic.planet.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.VerseStones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A verse stone's state (spec MILESTONE_5.md 3.3.2): its {@code owner} and {@code index} (1 to 3,
 * set when {@code PlanetSites} places the site), whether it is still {@code awake}, the Hymnsworn
 * spawned to guard it and where they gather ({@code spawn_at}, the site's {@code SPAWNER} anchor),
 * and {@code hook_owed} when it fell silent while its owner was away. The channel itself (who
 * channels, how far along, whether the Force has answered) lives only in memory: a restart or an
 * unloaded chunk simply ends it. {@link VerseStones} runs the rules on the server; on the client the
 * ticker only feeds the stone's hum and motes ({@link PlanetBlockAmbience}).
 */
public class VerseStoneBlockEntity extends BlockEntity {
	/** At most this many guard ids are kept (the largest group is 5). */
	public static final int MAX_GUARDS = 8;

	private @Nullable UUID owner;
	private int index = 1;
	private boolean awake = true;
	private boolean guardsSpawned;
	private final List<UUID> guards = new ArrayList<>();
	private @Nullable BlockPos spawnAt;
	private boolean hookOwed;

	private @Nullable UUID channeller;
	private int progress;
	private long lastUse;
	private boolean forceCut;

	public VerseStoneBlockEntity(BlockPos pos, BlockState state) {
		super(PlanetBlocks.VERSE_STONE_ENTITY, pos, state);
	}

	/** The server ticker: the channel, the slowing hum and the guards ({@link VerseStones#tick}). */
	public static void serverTick(Level level, BlockPos pos, BlockState state, VerseStoneBlockEntity stone) {
		if (level instanceof ServerLevel server) {
			VerseStones.tick(server, pos, state, stone);
		}
	}

	/** The client ticker: the stone's hum loop and its drifting motes. */
	public static void clientTick(Level level, BlockPos pos, BlockState state, VerseStoneBlockEntity stone) {
		PlanetBlockAmbience.get().verseStone(state, level, pos);
	}

	/** Makes this the {@code index}th stone (1 to 3) of {@code owner}, its guards gathering at {@code spawnAt}. */
	public void setup(UUID owner, int index, @Nullable BlockPos spawnAt) {
		this.owner = owner;
		this.index = Math.max(1, Math.min(3, index));
		this.spawnAt = spawnAt == null ? null : spawnAt.immutable();
		setChanged();
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public int index() {
		return index;
	}

	public boolean awake() {
		return awake;
	}

	/** Marks the stone silent (the block state is the caller's). */
	public void silence() {
		awake = false;
		clearChannel();
		setChanged();
	}

	public boolean guardsSpawned() {
		return guardsSpawned;
	}

	/** Records the guards just spawned for the stone (also marks them spawned when the list is empty). */
	public void setGuards(List<UUID> spawned) {
		guards.clear();
		spawned.stream().limit(MAX_GUARDS).forEach(guards::add);
		guardsSpawned = true;
		setChanged();
	}

	/** Adds one guard (a test's stand-in, or a late member). */
	public void addGuard(UUID guard) {
		if (guards.size() < MAX_GUARDS && !guards.contains(guard)) {
			guards.add(guard);
		}

		guardsSpawned = true;
		setChanged();
	}

	/** The guards' ids, oldest first (dead ones are pruned by {@link VerseStones}). */
	public List<UUID> guards() {
		return List.copyOf(guards);
	}

	/** Forgets the guards that {@code gone} names; true when any was removed. */
	public boolean pruneGuards(List<UUID> gone) {
		boolean removed = guards.removeAll(gone);

		if (removed) {
			setChanged();
		}

		return removed;
	}

	public @Nullable BlockPos spawnAt() {
		return spawnAt;
	}

	public boolean hookOwed() {
		return hookOwed;
	}

	public void setHookOwed(boolean owed) {
		hookOwed = owed;
		setChanged();
	}

	// ---- the channel (memory only) ----

	public @Nullable UUID channeller() {
		return channeller;
	}

	public int progress() {
		return progress;
	}

	public long lastUse() {
		return lastUse;
	}

	public boolean forceCut() {
		return forceCut;
	}

	/** Starts a channel for {@code player} at game time {@code now}. */
	public void startChannel(UUID player, long now) {
		channeller = player;
		progress = 0;
		lastUse = now;
		forceCut = false;
	}

	/** Another use while the channel runs: it stays alive. */
	public void refreshChannel(long now) {
		lastUse = now;
	}

	/** Moves the channel on by {@code ticks}; returns the new progress. */
	public int advance(int ticks) {
		progress += ticks;
		return progress;
	}

	/** Marks the Force's one cut of this channel as spent. */
	public void markForceCut() {
		forceCut = true;
	}

	public void clearChannel() {
		channeller = null;
		progress = 0;
		lastUse = 0;
		forceCut = false;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		index = Math.max(1, Math.min(3, input.getIntOr("index", 1)));
		awake = input.getBooleanOr("awake", true);
		guardsSpawned = input.getBooleanOr("guards_spawned", false);
		guards.clear();
		input.read("guards", UUIDUtil.CODEC.listOf()).orElse(List.of()).stream().limit(MAX_GUARDS).forEach(guards::add);
		spawnAt = input.read("spawn_at", BlockPos.CODEC).orElse(null);
		hookOwed = input.getBooleanOr("hook_owed", false);
		clearChannel();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.putInt("index", index);
		output.putBoolean("awake", awake);
		output.putBoolean("guards_spawned", guardsSpawned);
		output.store("guards", UUIDUtil.CODEC.listOf(), List.copyOf(guards));
		output.storeNullable("spawn_at", BlockPos.CODEC, spawnAt);
		output.putBoolean("hook_owed", hookOwed);
	}
}

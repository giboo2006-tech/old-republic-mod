package com.oldrepublic.world.site;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.quest.story.StoryService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Data and behaviour of a {@code site_spawner} (spec 3.2, 3.3; NBT of W1 plan 4.8): {@code group},
 * {@code count} (0 = the group's default), {@code bound}, {@code replace_with} (a block state,
 * {@code minecraft:air} by default), {@code done} and the optional {@code owner} (int[4]).
 *
 * <p>Every 20 ticks, once the loading rule holds for its chunk and a player is within
 * {@code sites.spawnerRange}, it spawns its group through {@link SpawnerGroups} (one fresh squad),
 * marks the members persistent and turns its cell into the replace-with block. It never spawns
 * twice: {@code done} is set before the group spawns, and a spawner found done simply finishes its
 * replacement.
 *
 * <p>An owned spawner (an Echo Shrine's guard, 3.6 and 12.3) waits for its owner instead: it fires
 * the first time the owner is within {@code story.encounterSpawnDistance} (48, the M2 rule), and
 * the group spawns owned by them, so kills by anyone count for the owner.
 */
public class SiteSpawnerBlockEntity extends BlockEntity {
	/** Ticks between checks. */
	public static final int INTERVAL = 20;

	private String group = "";
	private int count;
	private boolean bound;
	private BlockState replaceWith = Blocks.AIR.defaultBlockState();
	private boolean done;
	private @Nullable UUID owner;

	public SiteSpawnerBlockEntity(BlockPos pos, BlockState state) {
		super(SiteBlocks.SITE_SPAWNER_ENTITY, pos, state);
	}

	/** The block-entity ticker: checks every {@link #INTERVAL} ticks, staggered by position. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, SiteSpawnerBlockEntity spawner) {
		if (level instanceof ServerLevel server && (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL == 0) {
			spawner.check(server);
		}
	}

	/**
	 * One check: spawns the group when the loading rule holds and a player is within
	 * {@code sites.spawnerRange} (the owner within {@code story.encounterSpawnDistance} for an owned
	 * spawner), and returns the members it spawned (empty when it did not fire).
	 */
	public List<LivingEntity> check(ServerLevel level) {
		if (done) {
			replace(level);
			return List.of();
		}

		BlockPos pos = getBlockPos();

		if (!SiteChecks.entitiesReady(level, pos, 0) || !triggered(level, Vec3.atCenterOf(pos))) {
			return List.of();
		}

		return fire(level);
	}

	/** Any player within {@code sites.spawnerRange}; for an owned spawner, its owner within {@code story.encounterSpawnDistance}. */
	private boolean triggered(ServerLevel level, Vec3 centre) {
		if (owner == null) {
			return SiteChecks.playerWithin(level, centre, SiteBalance.get().spawnerRange);
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		double range = StoryService.balance().encounterSpawnDistance;
		return player != null && player.level() == level && player.isAlive() && !player.isSpectator()
				&& player.position().distanceToSqr(centre) <= range * range;
	}

	/** Spawns the group now, skipping the loading and proximity checks, then replaces the block; empty when it already spawned. */
	public List<LivingEntity> fire(ServerLevel level) {
		if (done) {
			return List.of();
		}

		done = true;
		setChanged();
		BlockPos pos = getBlockPos();

		if (!SpawnerGroups.known(group)) {
			OldRepublicMod.LOGGER.error("Site spawner at {} has an unknown group '{}'; removing it", pos, group);
			replace(level);
			return List.of();
		}

		List<LivingEntity> members = SpawnerGroups.spawn(level, pos, group, count, bound, owner);

		for (LivingEntity member : members) {
			if (member instanceof Mob mob) {
				mob.setPersistenceRequired();
			}
		}

		replace(level);
		return members;
	}

	/** Sets every field the structure generator writes (tests and runtime placements). */
	public void configure(String group, int count, boolean bound, BlockState replaceWith) {
		this.group = group;
		this.count = Math.max(0, count);
		this.bound = bound;
		this.replaceWith = replaceWith.is(SiteBlocks.SITE_SPAWNER) ? Blocks.AIR.defaultBlockState() : replaceWith;
		setChanged();
	}

	/**
	 * Makes the spawner an owned encounter of {@code owner} (an Echo Shrine's guard), or a shared one
	 * again with null.
	 */
	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		setChanged();
	}

	/** Marks the spawner as having spawned (tests of the saved data). */
	public void markDone() {
		done = true;
		setChanged();
	}

	public String group() {
		return group;
	}

	public int count() {
		return count;
	}

	public boolean bound() {
		return bound;
	}

	public BlockState replaceWith() {
		return replaceWith;
	}

	public boolean done() {
		return done;
	}

	public @Nullable UUID owner() {
		return owner;
	}

	private void replace(ServerLevel level) {
		BlockPos pos = getBlockPos();

		if (level.getBlockState(pos).is(SiteBlocks.SITE_SPAWNER)) {
			level.setBlock(pos, replaceWith, Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		group = input.getStringOr("group", "");
		count = Math.max(0, input.getIntOr("count", 0));
		bound = input.getBooleanOr("bound", false);
		// API-CHECK: BlockState.CODEC reads the NbtUtils block state layout ({Name, Properties}).
		BlockState read = input.read("replace_with", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
		replaceWith = read.is(SiteBlocks.SITE_SPAWNER) ? Blocks.AIR.defaultBlockState() : read;
		done = input.getBooleanOr("done", false);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("group", group);
		output.putInt("count", count);
		output.putBoolean("bound", bound);
		output.store("replace_with", BlockState.CODEC, replaceWith);
		output.putBoolean("done", done);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
	}
}

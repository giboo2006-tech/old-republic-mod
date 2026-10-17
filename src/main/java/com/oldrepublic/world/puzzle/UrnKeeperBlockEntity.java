package com.oldrepublic.world.puzzle;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.world.WorldDamage;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The urn keeper's memory (spec 3.3, NBT 4.8): its four urn offsets in the template frame
 * ({@code urns}, {@code facing} north = the frame), the progress of the push sequence
 * ({@code progress}) and its playback times ({@code play_at}, {@code last_play}). The order of
 * the four urns is seeded from the keeper's position ({@link PuzzleRules#urnOrder}). Its ticker
 * only plays the echoes of a running playback.
 */
public class UrnKeeperBlockEntity extends BlockEntity {
	/** How many urns a keeper keeps. */
	public static final int URNS = 4;
	private static final String KEY_URNS = "urns";
	private static final String KEY_PROGRESS = "progress";
	private static final String KEY_PLAY_AT = "play_at";
	private static final String KEY_LAST_PLAY = "last_play";

	/** What one push on one of this keeper's urns did. */
	public enum Outcome {
		/** The urn is not one of this keeper's. */
		NOT_MINE,
		/** The keeper is already solved; nothing changes. */
		ALREADY_SOLVED,
		/** The right urn, more to go. */
		CORRECT,
		/** The right last urn: solved. */
		SOLVED,
		/** The wrong urn: the sequence starts over. */
		WRONG
	}

	/**
	 * The result of a push.
	 *
	 * @param outcome what happened
	 * @param woke    sentinels a wrong push woke ({@link Sentinels#wakeNear}); 0 otherwise
	 */
	public record PushResult(Outcome outcome, int woke) {
	}

	private final List<BlockPos> offsets = new ArrayList<>();
	private int progress;
	private long playAt = -1;
	private long lastPlay = -1;
	/** Idle steps without a player near while solved; transient (a reload only delays a reset). */
	private int idleAge;

	public UrnKeeperBlockEntity(BlockPos pos, BlockState state) {
		super(PuzzleBlocks.URN_KEEPER_ENTITY, pos, state);
	}

	/** Sets the four urn offsets, in the template frame (the generator writes the same values as NBT). */
	public void setUrnOffsets(List<BlockPos> templateFrameOffsets) {
		offsets.clear();

		for (BlockPos offset : templateFrameOffsets) {
			if (offsets.size() < URNS) {
				offsets.add(offset.immutable());
			}
		}

		setChanged();
	}

	/** The urn offsets in the template frame, {@code URN_1} first. */
	public List<BlockPos> urnOffsets() {
		return List.copyOf(offsets);
	}

	/** The urns' world positions, {@code URN_1} first: the offsets rotated by the keeper's {@code facing}. */
	public List<BlockPos> urnPositions() {
		Rotation rotation = rotation(getBlockState().getValue(PuzzleStates.FACING));
		List<BlockPos> positions = new ArrayList<>(offsets.size());

		for (BlockPos offset : offsets) {
			positions.add(worldPosition.offset(StructureTemplate.transform(offset, Mirror.NONE, rotation, BlockPos.ZERO)));
		}

		return positions;
	}

	/** The template rotation of a keeper facing {@code facing} (north is the template frame), as {@code RuntimeSites} maps it. */
	public static Rotation rotation(Direction facing) {
		return switch (facing) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** The push order, as urn indexes 0–3. */
	public int[] order() {
		return PuzzleRules.urnOrder(worldPosition);
	}

	/** Correct pushes so far (0–4). */
	public int progress() {
		return progress;
	}

	/** Whether a playback is running now. */
	public boolean playing() {
		return playAt >= 0;
	}

	/**
	 * Starts the echo sequence (a Force Sense within {@code sites.urnSenseRange}): each urn echoes in
	 * order, {@code sites.urnStepTicks} apart. Refused while solved, without all four urns, or within
	 * {@code sites.urnRetryTicks} of the last playback. Returns true when it started.
	 */
	public boolean startPlayback(ServerLevel level, SiteBalance b) {
		long now = level.getGameTime();

		if (getBlockState().getValue(PuzzleStates.SOLVED) || offsets.size() < URNS || lastPlay >= 0 && now >= lastPlay && now - lastPlay < b.urnRetryTicks) {
			return false;
		}

		playAt = now;
		lastPlay = now;
		setChanged();
		return true;
	}

	/** Ticker: plays the next echo of a running playback on its step. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, UrnKeeperBlockEntity keeper) {
		if (keeper.playAt >= 0 && level instanceof ServerLevel server) {
			keeper.play(server);
		}
	}

	private void play(ServerLevel level) {
		long elapsed = level.getGameTime() - playAt;
		int step = Math.max(1, SiteBalance.get().urnStepTicks);
		List<BlockPos> urns = urnPositions();

		if (elapsed < 0 || elapsed >= (long) step * URNS || urns.size() < URNS) {
			playAt = -1;
			setChanged();
			return;
		}

		if (elapsed % step == 0) {
			int index = (int) (elapsed / step);
			PuzzleEffects.send(level, PuzzleEffects.URN_ECHO, urns.get(order()[index]), 1f, index);
		}
	}

	/**
	 * A Push on the urn at {@code urnPos}. The right urn echoes the note of its step; the last one
	 * solves the keeper (redstone 15, latched). A wrong urn starts the sequence over, drops a dust
	 * curtain that hurts everything within {@code sites.urnWrongRadius} of it and wakes the sentinels
	 * within {@code sites.sentinelWakeRadius} of the keeper (never one that sleeps for the pusher's
	 * side).
	 */
	public PushResult push(ServerLevel level, BlockPos urnPos, @Nullable ServerPlayer by) {
		List<BlockPos> urns = urnPositions();
		int urn = urns.indexOf(urnPos);

		if (urn < 0 || urns.size() < URNS) {
			return new PushResult(Outcome.NOT_MINE, 0);
		}

		BlockState state = getBlockState();

		if (state.getValue(PuzzleStates.SOLVED)) {
			return new PushResult(Outcome.ALREADY_SOLVED, 0);
		}

		SiteBalance b = SiteBalance.get();
		int expected = order()[Math.clamp(progress, 0, URNS - 1)];

		if (urn != expected) {
			progress = 0;
			playAt = -1;
			setChanged();
			return new PushResult(Outcome.WRONG, wrong(level, urnPos, by, b));
		}

		progress++;
		setChanged();
		PuzzleEffects.send(level, PuzzleEffects.URN_ECHO, urnPos, 1f, progress - 1);

		if (progress < URNS) {
			return new PushResult(Outcome.CORRECT, 0);
		}

		playAt = -1;
		idleAge = 0;
		level.setBlock(worldPosition, state.setValue(PuzzleStates.SOLVED, true), Block.UPDATE_ALL);
		level.updateNeighborsAt(worldPosition, state.getBlock());
		PuzzleWorld.scheduleReset(level, worldPosition, state.getBlock(), b);
		PuzzleEvents.solved(level, worldPosition, PuzzleEvents.URNS);
		return new PushResult(Outcome.SOLVED, 0);
	}

	private int wrong(ServerLevel level, BlockPos urnPos, @Nullable ServerPlayer by, SiteBalance b) {
		PuzzleEffects.send(level, PuzzleEffects.URN_WRONG, urnPos, (float) b.urnWrongRadius, 0);
		Vec3 centre = Vec3.atCenterOf(urnPos);
		double radius = b.urnWrongRadius;
		DamageSource source = level.damageSources().source(WorldDamage.URN_DUST);

		for (Entity entity : level.getEntities((Entity) null, new AABB(centre, centre).inflate(radius),
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			if (entity.getBoundingBox().getCenter().distanceToSqr(centre) <= radius * radius) {
				entity.hurtServer(level, source, b.urnWrongDamage);
			}
		}

		return Sentinels.wakeNear(level, worldPosition, b.sentinelWakeRadius, by);
	}

	/**
	 * One idle step of a solved keeper (from its block's scheduled tick). Returns true when the keeper
	 * resets now: it then forgets its progress, and the block clears {@code solved}.
	 */
	boolean idleStep(boolean playerNear, SiteBalance b) {
		PuzzleRules.ResetStep step = PuzzleRules.resetStep(idleAge, playerNear, b);
		idleAge = step.reset() ? 0 : step.age();

		if (step.reset()) {
			progress = 0;
			playAt = -1;
			setChanged();
		}

		return step.reset();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		offsets.clear();
		int[] raw = input.getIntArray(KEY_URNS).orElse(new int[0]);

		for (int i = 0; i + 2 < raw.length && offsets.size() < URNS; i += 3) {
			offsets.add(new BlockPos(raw[i], raw[i + 1], raw[i + 2]));
		}

		progress = Math.clamp(input.getIntOr(KEY_PROGRESS, 0), 0, URNS);
		playAt = input.getLongOr(KEY_PLAY_AT, -1L);
		lastPlay = input.getLongOr(KEY_LAST_PLAY, -1L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		int[] raw = new int[offsets.size() * 3];

		for (int i = 0; i < offsets.size(); i++) {
			BlockPos offset = offsets.get(i);
			raw[i * 3] = offset.getX();
			raw[i * 3 + 1] = offset.getY();
			raw[i * 3 + 2] = offset.getZ();
		}

		output.putIntArray(KEY_URNS, raw);
		output.putInt(KEY_PROGRESS, progress);
		output.putLong(KEY_PLAY_AT, playAt);
		output.putLong(KEY_LAST_PLAY, lastPlay);
	}
}

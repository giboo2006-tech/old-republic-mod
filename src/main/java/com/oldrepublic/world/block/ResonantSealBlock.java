package com.oldrepublic.world.block;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.world.WorldBalance;
import com.oldrepublic.world.WorldEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shimmering wall in front of the trial chamber. It opens only for an awakened player who
 * has learned Force Push: the whole connected seal (up to {@link #MAX_GROUP} blocks) opens, stays
 * open while anything stands in the doorway and closes again after {@code sealOpenTicks}.
 * Anyone else gets clear feedback that it refuses them. Open is a world state, so companions
 * can follow through while it is open.
 */
public class ResonantSealBlock extends Block {
	public static final MapCodec<ResonantSealBlock> CODEC = simpleCodec(ResonantSealBlock::new);
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final int MAX_GROUP = 27;
	private static final int REFUSE_COOLDOWN_TICKS = 20;
	private static final int OCCUPIED_RECHECK_TICKS = 20;
	private static final Map<UUID, Long> LAST_REFUSAL = new HashMap<>();

	public ResonantSealBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPEN, false));
	}

	@Override
	protected MapCodec<ResonantSealBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
	}

	@Override
	protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
		return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPEN)) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		String refusal = refusalKey(serverPlayer);

		if (refusal != null) {
			refuse(serverPlayer, pos, refusal);
			return InteractionResult.SUCCESS;
		}

		open(server, pos);
		serverPlayer.sendOverlayMessage(Component.translatable("message.oldrepublic.seal.open"));
		return InteractionResult.SUCCESS;
	}

	/** Null when the player may open the seal, else the translation key of the reason. */
	public static @Nullable String refusalKey(ServerPlayer player) {
		return refusalKey(Progression.get(player), player.isCreative());
	}

	/** Pure rule behind {@link #refusalKey(ServerPlayer)} (also used by the game tests). */
	public static @Nullable String refusalKey(ForceData data, boolean creative) {
		if (creative) {
			return null;
		}

		if (!data.isAwakened()) {
			return "message.oldrepublic.seal.refuse_unawakened";
		}

		if (!data.hasAbility("force_push")) {
			return "message.oldrepublic.seal.refuse_no_push";
		}

		return null;
	}

	/** Overlay text plus the refusal effect, at most once per second per player. */
	public static void refuse(ServerPlayer player, BlockPos pos, String messageKey) {
		long now = player.level().getGameTime();
		Long last = LAST_REFUSAL.get(player.getUUID());

		if (last != null && now - last < REFUSE_COOLDOWN_TICKS && now >= last) {
			return;
		}

		LAST_REFUSAL.put(player.getUUID(), now);

		if (LAST_REFUSAL.size() > 256) {
			LAST_REFUSAL.entrySet().removeIf(e -> now - e.getValue() > REFUSE_COOLDOWN_TICKS);
		}

		player.sendOverlayMessage(Component.translatable(messageKey));
		EffectEvents.around(player.level(), WorldEffects.SEAL_REFUSE, Vec3.atCenterOf(pos), player.getEyePosition().subtract(Vec3.atCenterOf(pos)).normalize(),
				player.getId(), 1f, 0);
	}

	public static void forget(UUID player) {
		LAST_REFUSAL.remove(player);
	}

	/** Opens the whole connected seal around {@code pos}. */
	public void open(ServerLevel level, BlockPos pos) {
		Set<BlockPos> group = group(level, pos);
		int delay = Math.max(20, WorldBalance.get().sealOpenTicks);

		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);
			level.setBlock(member, memberState.setValue(OPEN, true), Block.UPDATE_ALL);
			level.scheduleTick(member, this, delay);
		}

		EffectEvents.around(level, WorldEffects.SEAL_OPEN, Vec3.atCenterOf(pos), null, -1, group.size(), 0);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(OPEN)) {
			return;
		}

		Set<BlockPos> group = group(level, pos);

		if (occupied(level, group)) {
			level.scheduleTick(pos, this, OCCUPIED_RECHECK_TICKS);
			return;
		}

		level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
	}

	private static boolean occupied(ServerLevel level, Set<BlockPos> group) {
		for (BlockPos member : group) {
			if (!level.getEntities((Entity) null, new AABB(member), Entity::isAlive).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** Connected seal blocks (6-neighbour flood fill), at most {@link #MAX_GROUP}. */
	private Set<BlockPos> group(Level level, BlockPos start) {
		Set<BlockPos> found = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());

		while (!queue.isEmpty() && found.size() < MAX_GROUP) {
			BlockPos next = queue.poll();

			if (found.contains(next) || !level.getBlockState(next).is(this)) {
				continue;
			}

			found.add(next);

			for (Direction direction : Direction.values()) {
				queue.add(next.relative(direction));
			}
		}

		return found;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		WorldBlockAmbience.get().seal(state, level, pos, random);
	}
}

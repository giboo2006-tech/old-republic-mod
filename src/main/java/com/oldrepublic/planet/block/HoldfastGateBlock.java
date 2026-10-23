package com.oldrepublic.planet.block;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.PlanetEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The holdfast's gate (spec MILESTONE_5.md 3.3.1, 6.6): heavy clan-iron plates hung in the camp's
 * wall, shut by default. A player at Neutral clan standing or higher who uses it opens the whole
 * gate (every connected gate block, at most {@link #MAX_GROUP}); it stays open while anyone stands
 * in it and shuts {@link #OPEN_TICKS} after it was last occupied, so whoever came with them passes
 * too. A player below Neutral is refused (with the overlay, at most once a second). From the inside
 * (behind the gate's {@code facing}, which points out of the camp) it always opens, so nobody is
 * shut in. Creative players always pass. Open is a world state saved with the block, and the shutting
 * is a scheduled block tick, so a restart keeps both. Unbreakable; no block entity.
 */
public class HoldfastGateBlock extends Block {
	public static final MapCodec<HoldfastGateBlock> CODEC = simpleCodec(HoldfastGateBlock::new);
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/** Points out of the holdfast: a player on the other side is inside. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** The largest gate one use opens. */
	public static final int MAX_GROUP = 27;
	/** Ticks the gate stays open after the doorway was last occupied. */
	public static final int OPEN_TICKS = 60;
	private static final int OCCUPIED_RECHECK_TICKS = 20;
	private static final int REFUSE_COOLDOWN_TICKS = 20;
	private static final Map<UUID, Long> LAST_REFUSAL = new HashMap<>();

	public HoldfastGateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<HoldfastGateBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OPEN, FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
		return adjacent.is(this) && adjacent.getValue(OPEN) == state.getValue(OPEN) || super.skipRendering(state, adjacent, direction);
	}

	/**
	 * The rule (pure, for the tests): the gate opens for a creative player, from the inside, and for
	 * a clan standing of Neutral or higher.
	 */
	public static boolean mayOpen(ReputationTier clan, boolean inside, boolean creative) {
		return creative || inside || clan.ordinal() >= ReputationTier.NEUTRAL.ordinal();
	}

	/** Whether {@code position} is on the camp's side of the gate cell {@code pos} facing {@code facing}. */
	public static boolean inside(BlockPos pos, Direction facing, Vec3 position) {
		Vec3 offset = position.subtract(Vec3.atCenterOf(pos));
		return offset.x * facing.getStepX() + offset.z * facing.getStepZ() < 0;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPEN)) {
			return InteractionResult.PASS;
		}

		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		boolean inside = inside(pos, state.getValue(FACING), serverPlayer.position());

		if (!mayOpen(Identity.tier(serverPlayer, Faction.CLAN_HASK), inside, serverPlayer.isCreative())) {
			refuse(serverPlayer);
			return InteractionResult.SUCCESS;
		}

		open(server, pos);
		return InteractionResult.SUCCESS;
	}

	/** Opens the whole gate around {@code pos} (a test seam too); false when it is not a shut gate. */
	public boolean open(ServerLevel level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);

		if (!here.is(this) || here.getValue(OPEN)) {
			return false;
		}

		Set<BlockPos> group = group(level, pos);

		for (BlockPos member : group) {
			level.setBlock(member, level.getBlockState(member).setValue(OPEN, true), Block.UPDATE_ALL);
			level.scheduleTick(member, this, OPEN_TICKS);
		}

		EffectEvents.around(level, PlanetEffects.SITE_DOOR, centre(group), null, -1, group.size(), PlanetEffects.DOOR_GATE_OPEN);
		return true;
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

		// The first member to come due shuts the whole gate; the others find it shut.
		for (BlockPos member : group) {
			BlockState memberState = level.getBlockState(member);

			if (memberState.getValue(OPEN)) {
				level.setBlock(member, memberState.setValue(OPEN, false), Block.UPDATE_ALL);
			}
		}

		EffectEvents.around(level, PlanetEffects.SITE_DOOR, centre(group), null, -1, group.size(), PlanetEffects.DOOR_GATE_SHUT);
	}

	/** Overlay text, at most once per second per player. */
	private static void refuse(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_REFUSAL.get(player.getUUID());

		if (last != null && now >= last && now - last < REFUSE_COOLDOWN_TICKS) {
			return;
		}

		LAST_REFUSAL.put(player.getUUID(), now);

		if (LAST_REFUSAL.size() > 256) {
			LAST_REFUSAL.entrySet().removeIf(e -> now - e.getValue() > REFUSE_COOLDOWN_TICKS);
		}

		player.sendOverlayMessage(Component.translatable("message.oldrepublic.holdfast_gate.refused"));
	}

	/** Forgets a player's refusal clock (logout). */
	public static void forget(UUID player) {
		LAST_REFUSAL.remove(player);
	}

	private static boolean occupied(ServerLevel level, Set<BlockPos> group) {
		for (BlockPos member : group) {
			if (!level.getEntities((Entity) null, new AABB(member), Entity::isAlive).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private static Vec3 centre(Set<BlockPos> group) {
		Vec3 sum = Vec3.ZERO;

		for (BlockPos member : group) {
			sum = sum.add(Vec3.atCenterOf(member));
		}

		return group.isEmpty() ? sum : sum.scale(1.0 / group.size());
	}

	/** Connected gate blocks (6-neighbour flood fill), at most {@link #MAX_GROUP}. */
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
}

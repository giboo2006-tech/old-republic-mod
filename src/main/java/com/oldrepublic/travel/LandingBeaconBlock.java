package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.travel.ShipRules.Recall;
import com.oldrepublic.world.site.RuntimeSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The landing beacon (spec MILESTONE_4.md 4.8): a squat post with a blinking lamp that marks the
 * owner's home pad, the centre of a clear 11 × 11 area beside it. {@code facing} points from the
 * post to the pad.
 * <ul>
 * <li>Placing it checks the four sides (the placer's side first) with the {@link MeetingSites}
 * rules: home world, natural ground with at most 2 blocks of slope, nothing built, open sky. If no
 * side passes it says why, plays the refusal and comes back as an item.</li>
 * <li>A beacon that is set lays the {@code ship/beacon_pad} template over its area: the ground
 * fitted level and tamped to stone, with natural ground blocks only, so the area passes the check
 * again when a beacon is set there anew.</li>
 * <li>One per player: a new beacon moves the home pad, and the old one comes back as an item (at
 * once when its chunk is loaded, else on its own next check).</li>
 * <li>Only its owner can break it (others make no progress; explosions and pistons cannot).</li>
 * <li>The owner uses it with a hyperfuel cell to recall the ship (a tow when it is {@code DISABLED});
 * without one it reports the ship and puts a missing body back at its last pad (4.9).</li>
 * </ul>
 */
public class LandingBeaconBlock extends Block implements EntityBlock {
	public static final MapCodec<LandingBeaconBlock> CODEC = simpleCodec(LandingBeaconBlock::new);
	private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);
	/** At least this share of the area must see the sky. */
	private static final double MIN_OPENNESS = 0.9;
	/** The pad's ground may sit this far above or below the beacon's (so the pad finds its beacon). */
	private static final int MAX_STEP = 1;
	/** The tamped ground laid over a new home pad (spec 16.1 templates). */
	private static final Identifier PAD_TEMPLATE = OldRepublicMod.id("ship/beacon_pad");
	/** The template's width; it is laid only when the checked area is at least this wide. */
	private static final int PAD_SIZE = 11;
	/** The template's ground layer replaces the ground. */
	private static final int PAD_BURIED = 1;

	public LandingBeaconBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<LandingBeaconBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	// ---------------------------------------------------------------- placing

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);

		if (!(level instanceof ServerLevel server) || !(placer instanceof ServerPlayer player)) {
			return;
		}

		// The placer faces the post, so their side is behind them.
		Site site = findSite(server, pos, player.getDirection().getOpposite());

		if (site.pad() == null) {
			server.removeBlock(pos, false);

			if (!player.isCreative()) {
				Block.popResource(server, pos, new ItemStack(this));
			}

			TravelEffects.beacon(server, pos, TravelEffects.BEACON_REFUSE);
			player.sendOverlayMessage(Component.translatable("ship.oldrepublic.beacon." + site.reason()));
			return;
		}

		server.setBlock(pos, state.setValue(BlockStateProperties.HORIZONTAL_FACING, site.side()), Block.UPDATE_ALL);

		if (server.getBlockEntity(pos) instanceof LandingBeaconBlockEntity beacon) {
			beacon.bind(player.getUUID(), site.pad());
		}

		layPad(server, site.pad());

		TravelData data = TravelAttachments.get(player);
		Optional<GlobalPos> old = data.homePad();
		GlobalPos pad = GlobalPos.of(server.dimension(), site.pad());
		TravelAttachments.set(player, data.withHomePad(Optional.of(pad)));
		old.filter(o -> !o.equals(pad)).ifPresent(o -> popOldBeacon(server.getServer(), o, pos));
		TravelEffects.beacon(server, pos, TravelEffects.BEACON_SET);
		player.sendOverlayMessage(Component.translatable(old.isPresent() ? "ship.oldrepublic.beacon.moved" : "ship.oldrepublic.beacon.set"));
	}

	/**
	 * The pad a beacon at {@code pos} marks: the first side (from {@code first}, then its opposite,
	 * then the others) whose area passes; else the first side's reason.
	 *
	 * @return the pad and its side, or a null pad and the lang suffix of why not
	 */
	static Site findSite(ServerLevel level, BlockPos pos, Direction first) {
		if (!Planets.isHomeWorld(level)) {
			return new Site(null, first, "not_home");
		}

		ShipBalance.Travel t = ShipBalance.travel();
		int reach = t.beaconArea / 2 + 1;
		String reason = null;

		for (Direction side : List.of(first, first.getOpposite(), first.getClockWise(), first.getCounterClockWise())) {
			BlockPos centre = pos.relative(side, reach);
			String why = areaRefusal(level, centre.getX(), centre.getZ(), pos.getY(), t);

			if (why == null) {
				MeetingSites.Candidate site = MeetingSites.evaluate(level, centre.getX(), centre.getZ(), rules(t.beaconArea, t.beaconArea, t.beaconMaxSlope));
				return new Site(site == null ? centre : site.stand(), side, "");
			}

			if (reason == null) {
				reason = why;
			}
		}

		return new Site(null, first, reason == null ? "blocked" : reason);
	}

	/**
	 * Why the area centred on ({@code cx}, {@code cz}) cannot be a pad, or null when it can: each
	 * rule is tried on its own so the beacon can say which one failed.
	 */
	public static @Nullable String areaRefusal(Level level, int cx, int cz, int beaconY, ShipBalance.Travel t) {
		int area = t.beaconArea;
		// Natural, dry, free ground (slope and built blocks ignored).
		MeetingSites.Candidate ground = MeetingSites.evaluate(level, cx, cz, rules(area, 1, Integer.MAX_VALUE));

		if (ground == null) {
			return "ground";
		}

		MeetingSites.Candidate flat = MeetingSites.evaluate(level, cx, cz, rules(area, 1, t.beaconMaxSlope));

		if (flat == null || Math.abs(flat.stand().getY() - beaconY) > MAX_STEP) {
			return "slope";
		}

		MeetingSites.Candidate clear = MeetingSites.evaluate(level, cx, cz, rules(area, area, t.beaconMaxSlope));

		if (clear == null) {
			return "built";
		}

		return clear.openness() < MIN_OPENNESS ? "sky" : null;
	}

	/**
	 * Lays the pad's template centred on {@code stand}: the ground fitted level first (holes filled,
	 * plants and bumps cleared, as for any runtime site), then the tamped ground layer. Nothing is
	 * laid when the configured area is narrower than the template, since only the checked area is
	 * known to hold nothing built.
	 */
	static void layPad(ServerLevel level, BlockPos stand) {
		if (ShipBalance.travel().beaconArea < PAD_SIZE) {
			return;
		}

		StructureTemplate template = level.getStructureManager().get(PAD_TEMPLATE).orElse(null);

		if (template == null) {
			OldRepublicMod.LOGGER.error("Missing beacon pad template {}", PAD_TEMPLATE);
			return;
		}

		StructurePlaceSettings settings = new StructurePlaceSettings().setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
		BlockPos origin = RuntimeSites.templateOrigin(template, stand, Rotation.NONE, PAD_BURIED);
		RuntimeSites.adaptTerrain(level, template.getBoundingBox(settings, origin), stand.getY() - 1);
		template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
	}

	private static MeetingSites.Rules rules(int footprint, int clearance, int maxSlope) {
		return new MeetingSites.Rules(footprint, clearance, maxSlope, p -> false, List.of(), 0);
	}

	/**
	 * The result of the area check.
	 *
	 * @param pad    the pad's stand position, or null when no side passed
	 * @param side   the side of the post the pad lies on
	 * @param reason the lang suffix of the refusal when {@code pad} is null
	 */
	record Site(@Nullable BlockPos pad, Direction side, String reason) {
	}

	/** Returns the beacon that marked {@code oldPad} as an item when its chunk is loaded; else it pops itself later. */
	private static void popOldBeacon(MinecraftServer server, GlobalPos oldPad, BlockPos keep) {
		ServerLevel level = server.getLevel(oldPad.dimension());

		if (level == null || !level.isLoaded(oldPad.pos())) {
			return;
		}

		for (BlockPos at : around(oldPad.pos())) {
			if (!at.equals(keep) && level.isLoaded(at) && level.getBlockState(at).is(TravelBlocks.LANDING_BEACON)
					&& level.getBlockEntity(at) instanceof LandingBeaconBlockEntity beacon && oldPad.equals(beacon.padPos())) {
				popOff(level, at);
			}
		}
	}

	/** Where a beacon marking {@code pad} can stand: one area's reach away on each side, a block up or down. */
	static List<BlockPos> around(BlockPos pad) {
		int reach = ShipBalance.travel().beaconArea / 2 + 1;
		List<BlockPos> spots = new ArrayList<>();

		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int dy = -MAX_STEP; dy <= MAX_STEP; dy++) {
				spots.add(pad.relative(side, reach).above(dy));
			}
		}

		return spots;
	}

	/** Removes a superseded beacon and drops it as an item. */
	static void popOff(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).is(TravelBlocks.LANDING_BEACON)) {
			level.removeBlock(pos, false);
			Block.popResource(level, pos, new ItemStack(TravelBlocks.LANDING_BEACON));
			TravelEffects.beacon(level, pos, TravelEffects.BEACON_REFUSE);
		}
	}

	// ---------------------------------------------------------------- breaking

	/** Only the owner makes progress breaking it (the M3 rule for others; a creative player always can). */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof LandingBeaconBlockEntity beacon && beacon.owner() != null && !beacon.owner().equals(player.getUUID())) {
			return 0f;
		}

		return super.getDestroyProgress(state, player, level, pos);
	}

	/** The owner's home pad goes with the beacon (it falls back to the cradle). */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof LandingBeaconBlockEntity beacon && beacon.owner() != null) {
			ServerPlayer owner = server.getServer().getPlayerList().getPlayer(beacon.owner());

			if (owner != null && beacon.isHomeOf(owner)) {
				TravelAttachments.set(owner, TravelAttachments.get(owner).withHomePad(Optional.empty()));
				owner.sendOverlayMessage(Component.translatable("ship.oldrepublic.beacon.removed"));
			}
		}

		return super.playerWillDestroy(level, pos, state, player);
	}

	// ---------------------------------------------------------------- using

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(M4Items.HYPERFUEL_CELL)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (level instanceof ServerLevel server && player instanceof ServerPlayer owner && mine(server, pos, owner)) {
			Recall result = TravelService.recall(owner, Pads.home(owner), TravelService.Payment.CELL);

			if (result.allowed()) {
				TravelEffects.beacon(server, pos, TravelEffects.BEACON_RECALL);
			}

			owner.sendSystemMessage(Component.translatable(result.langKey()));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer owner && mine(server, pos, owner)) {
			MinecraftServer mc = server.getServer();
			ShipRecord record = ShipRegistry.of(mc, owner.getUUID()).orElse(null);

			if (record == null) {
				owner.sendSystemMessage(Component.translatable(Recall.NO_SHIP.langKey()));
				return InteractionResult.SUCCESS;
			}

			ShipBalance b = ShipBalance.get();
			owner.sendSystemMessage(Component.translatable("ship.oldrepublic.beacon.status",
					Component.translatable("ship.oldrepublic.state." + record.state().name().toLowerCase(Locale.ROOT)),
					record.hull(), record.fuel(), ShipRules.tankSize(record, b)));
			Ships.restore(mc, owner.getUUID(), true);
		}

		return InteractionResult.SUCCESS;
	}

	/** Whether {@code player} owns this beacon (and it still marks their home); tells anyone else whose it is. */
	private static boolean mine(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!(level.getBlockEntity(pos) instanceof LandingBeaconBlockEntity beacon) || beacon.owner() == null) {
			return false;
		}

		if (!beacon.owner().equals(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("ship.oldrepublic.beacon.not_yours", ShipActions.nameOf(level.getServer(), beacon.owner())));
			return false;
		}

		if (!beacon.isHomeOf(player)) {
			popOff(level, pos);
			return false;
		}

		return true;
	}

	// ---------------------------------------------------------------- block entity

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LandingBeaconBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != TravelBlocks.LANDING_BEACON_ENTITY) {
			return null;
		}

		return (BlockEntityTicker<T>) (BlockEntityTicker<LandingBeaconBlockEntity>) LandingBeaconBlockEntity::serverTick;
	}
}

package com.oldrepublic.travel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A landing beacon's owner and the pad it marks (spec MILESTONE_4.md 4.8; NBT {@code owner} int[4],
 * {@code pad} block position). Every few seconds it checks lazily whether it has been superseded: an
 * online owner whose home pad is no longer this one gets the beacon back as an item. Nothing ever
 * writes into an unloaded chunk to remove an old beacon.
 */
public class LandingBeaconBlockEntity extends BlockEntity {
	/** Ticks between checks. */
	public static final int INTERVAL = 100;

	private @Nullable UUID owner;
	private @Nullable BlockPos pad;

	public LandingBeaconBlockEntity(BlockPos pos, BlockState state) {
		super(TravelBlocks.LANDING_BEACON_ENTITY, pos, state);
	}

	/** The block-entity ticker: checks every {@link #INTERVAL} ticks, staggered by position. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, LandingBeaconBlockEntity beacon) {
		if (level instanceof ServerLevel server && (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL == 0) {
			beacon.check(server);
		}
	}

	private void check(ServerLevel level) {
		ServerPlayer online = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

		if (online != null && !isHomeOf(online)) {
			LandingBeaconBlock.popOff(level, getBlockPos());
		}
	}

	/** Whether this beacon's pad is {@code player}'s home pad. */
	public boolean isHomeOf(ServerPlayer player) {
		GlobalPos home = padPos();
		return home != null && TravelAttachments.get(player).homePad().filter(home::equals).isPresent();
	}

	/** Binds the beacon to its owner and its pad (the centre of the clear area). */
	public void bind(UUID owner, BlockPos pad) {
		this.owner = owner;
		this.pad = pad.immutable();
		setChanged();
	}

	public @Nullable UUID owner() {
		return owner;
	}

	/** The pad in this beacon's level, or null when unbound. */
	public @Nullable GlobalPos padPos() {
		return pad == null || level == null ? null : GlobalPos.of(level.dimension(), pad);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		pad = input.read("pad", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.storeNullable("pad", BlockPos.CODEC, pad);
	}
}

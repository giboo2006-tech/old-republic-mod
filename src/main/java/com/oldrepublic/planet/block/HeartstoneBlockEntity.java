package com.oldrepublic.planet.block;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import com.oldrepublic.planet.Heartstones;
import com.oldrepublic.planet.PlanetBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The players the Heartstone is cleansed for (at most {@link #MAX_CLEANSED}, oldest dropped first).
 * Every {@link #INTERVAL} ticks it sends its ambience ({@link Heartstones#sendAmbience}).
 */
public class HeartstoneBlockEntity extends BlockEntity {
	public static final int MAX_CLEANSED = 64;
	public static final int INTERVAL = 40;

	private final LinkedHashSet<UUID> cleansed = new LinkedHashSet<>();

	public HeartstoneBlockEntity(BlockPos pos, BlockState state) {
		super(PlanetBlocks.HEARTSTONE_ENTITY, pos, state);
	}

	/** The block-entity ticker, staggered by position. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, HeartstoneBlockEntity heart) {
		if (level instanceof ServerLevel server && (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL == 0) {
			Heartstones.sendAmbience(server, heart);
		}
	}

	public boolean cleansedFor(UUID player) {
		return cleansed.contains(player);
	}

	/** Records the cleansing for {@code player}; false when it was already recorded. */
	public boolean addCleansed(UUID player) {
		if (!cleansed.add(player)) {
			return false;
		}

		Iterator<UUID> oldest = cleansed.iterator();

		while (cleansed.size() > MAX_CLEANSED) {
			oldest.next();
			oldest.remove();
		}

		setChanged();
		return true;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		cleansed.clear();
		input.read("cleansed", UUIDUtil.CODEC.listOf()).orElse(List.of()).forEach(this::addCleansed);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("cleansed", UUIDUtil.CODEC.listOf(), List.copyOf(cleansed));
	}
}

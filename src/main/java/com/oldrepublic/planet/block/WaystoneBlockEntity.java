package com.oldrepublic.planet.block;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.Waystones;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A waystone's data (spec MILESTONE_4.md 12.1): whether its corruption is still to be decided
 * ({@code pending}, written by the clean template), the stone's template-local position
 * ({@code local}) and the players it is cleansed for ({@code cleansed}, at most
 * {@link #MAX_CLEANSED}, oldest dropped first).
 *
 * <p>Every {@link #INTERVAL} ticks: a pending stone decides once ({@link Waystones#decide}); a
 * corrupted stone sends its ambience to the players near it it is not cleansed for
 * ({@link Waystones#sendAmbience}).
 */
public class WaystoneBlockEntity extends BlockEntity {
	public static final int MAX_CLEANSED = 64;
	public static final int INTERVAL = 20;

	private boolean pending;
	private @Nullable BlockPos local;
	private final LinkedHashSet<UUID> cleansed = new LinkedHashSet<>();

	public WaystoneBlockEntity(BlockPos pos, BlockState state) {
		super(PlanetBlocks.WAYSTONE_ENTITY, pos, state);
	}

	/** The block-entity ticker, staggered by position. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, WaystoneBlockEntity stone) {
		if (!(level instanceof ServerLevel server) || (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL != 0) {
			return;
		}

		if (stone.pending) {
			Waystones.decide(server, stone);
		} else if (stone.corrupted() && (server.getGameTime() / INTERVAL) % 2 == 0) {
			Waystones.sendAmbience(server, stone);
		}
	}

	/** Whether the stone is a corrupted one (its block). */
	public boolean corrupted() {
		return getBlockState().getBlock() instanceof WaystoneBlock block && block.corrupted();
	}

	public boolean pending() {
		return pending;
	}

	/** Marks the corruption as decided (or, with true, as still to be decided: tests and templates). */
	public void setPending(boolean pending) {
		this.pending = pending;
		setChanged();
	}

	/** The stone's template-local position, or null for a stone placed by hand. */
	public @Nullable BlockPos local() {
		return local;
	}

	public void setLocal(@Nullable BlockPos local) {
		this.local = local == null ? null : local.immutable();
		setChanged();
	}

	/** Whether the stone is cleansed for {@code player}. */
	public boolean cleansedFor(UUID player) {
		return cleansed.contains(player);
	}

	/** The players it is cleansed for, oldest first (read only). */
	public Set<UUID> cleansed() {
		return Set.copyOf(cleansed);
	}

	/**
	 * Records the stone as cleansed for {@code player}; false when it already was. Past
	 * {@link #MAX_CLEANSED} the oldest entry is dropped.
	 */
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
		pending = input.getBooleanOr("pending", false);
		local = input.read("local", BlockPos.CODEC).orElse(null);
		cleansed.clear();
		input.read("cleansed", UUIDUtil.CODEC.listOf()).orElse(List.of()).forEach(this::addCleansed);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean("pending", pending);
		output.storeNullable("local", BlockPos.CODEC, local);
		output.store("cleansed", UUIDUtil.CODEC.listOf(), List.copyOf(cleansed));
	}
}

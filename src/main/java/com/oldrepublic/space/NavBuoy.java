package com.oldrepublic.space;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A nav buoy, a {@code space_run}'s destination (spec MILESTONE_5.md 5.9): a drifting marker whose
 * light blinks (steady with flashing off, on the client) and pings every two seconds
 * ({@code nav_buoy}). {@link SpaceMissions} places it when the runner's ship comes near its point and
 * removes it when the run ends; a buoy whose run is over removes itself. It cannot be hurt and is
 * never saved.
 */
public class NavBuoy extends Entity {
	private static final EntityDataAccessor<Boolean> DATA_REACHED = SynchedEntityData.defineId(NavBuoy.class, EntityDataSerializers.BOOLEAN);
	/** Ticks between two pings. */
	private static final int BLINK_INTERVAL = 40;

	/** The player whose run it marks (server side); null for a buoy placed by a command. */
	private @Nullable UUID runner;

	public NavBuoy(EntityType<? extends NavBuoy> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_REACHED, false);
	}

	/** Marks the run of {@code runner}. */
	void mark(@Nullable UUID runner) {
		this.runner = runner;
	}

	public @Nullable UUID runner() {
		return runner;
	}

	/** Whether the run reached it (synced: its light turns steady green). */
	public boolean reached() {
		return entityData.get(DATA_REACHED);
	}

	void setReached() {
		entityData.set(DATA_REACHED, true);
	}

	@Override
	public void tick() {
		super.tick();

		if (level() instanceof ServerLevel level && (tickCount + getId()) % BLINK_INTERVAL == 0) {
			if (runner != null && !SpaceMissions.buoyWanted(runner, getUUID())) {
				discard();
				return;
			}

			if (!reached()) {
				EffectEvents.around(level, SpaceEffects.NAV_BUOY, getBoundingBox().getCenter(), null, getId(), 1f, SpaceEffects.BUOY_BLINK);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// Never saved (noSave): the run places it again.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		// Never saved (noSave).
	}
}

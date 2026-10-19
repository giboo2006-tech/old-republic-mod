package com.oldrepublic.travel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A ship's body of any class (spec MILESTONE_5.md 4.9): the courier ({@link CourierShuttle}) and,
 * from Milestone 5, the interceptor. Owned by W1 flight, who moves the shared behaviour here (the
 * seats, the registry link, the piloted flag, the free-flight tick, {@code locksRiders} and the hull
 * effects) and changes the uses of {@code CourierShuttle} as a type outside the courier's own
 * classes to this one. Phase 0 only adds the class under the courier, with no behaviour moved and
 * no synced data of its own, so the courier's synced data ids stay as they were.
 */
public abstract class ShipBody extends Entity {
	protected ShipBody(EntityType<? extends ShipBody> type, Level level) {
		super(type, level);
	}

	/** The owner's UUID as synced to clients, or null before the body has read its record. */
	public abstract @Nullable UUID ownerId();

	/** Whether the owner flies the body by hand (the synced {@code DATA_PILOTED} flag, 4.2). */
	public abstract boolean piloted();

	/** Whether the body is an interceptor (4.9); false for the courier. */
	public abstract boolean interceptor();
}

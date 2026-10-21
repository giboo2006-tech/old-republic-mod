package com.oldrepublic.client.force.energy;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A point light is fastened to, found again every frame (with the partial tick) so it stays on a moving hand or body: a
 * fixed spot, a creature's hand, or a place on its body. Null once it cannot be found (the creature is gone).
 */
@FunctionalInterface
public interface Anchor {
	/** Where the point is at {@code partial} between the last tick and the next, or null. */
	@Nullable Vec3 at(float partial);

	/** A fixed spot in the world. */
	static Anchor fixed(Vec3 pos) {
		return partial -> pos;
	}

	/** The hand of creature {@code entityId}, reaching {@code reach} along its look (see {@link EnergyKit#hand}). */
	static Anchor hand(int entityId, boolean left, float reach) {
		return partial -> entity(entityId) instanceof LivingEntity living ? EnergyKit.hand(living, left, reach, partial) : null;
	}

	/**
	 * A place on the body of {@code entityId}: {@code fx} and {@code fz} across its box (0.5 the middle), {@code fy} up it (0 the
	 * feet, 1 the top).
	 */
	static Anchor body(int entityId, float fx, float fy, float fz) {
		return partial -> {
			Entity entity = entity(entityId);

			if (entity == null) {
				return null;
			}

			double width = entity.getBbWidth();
			return entity.getPosition(partial).add((fx - 0.5) * width, fy * entity.getBbHeight(), (fz - 0.5) * width);
		};
	}

	/** This point moved by {@code offset}. */
	default Anchor offset(Vec3 offset) {
		return partial -> {
			Vec3 at = at(partial);
			return at == null ? null : at.add(offset);
		};
	}

	/** The live creature with that id in the client level, or null. */
	static @Nullable Entity entity(int entityId) {
		ClientLevel level = Minecraft.getInstance().level;
		Entity entity = level == null || entityId < 0 ? null : level.getEntity(entityId);
		return entity == null || entity.isRemoved() ? null : entity;
	}
}

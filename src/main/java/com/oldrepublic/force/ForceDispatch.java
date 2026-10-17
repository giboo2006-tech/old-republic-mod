package com.oldrepublic.force;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The order in which Push, Pull and Lift reach things (spec 10.6): a {@link ForceReactiveEntity} on
 * the aim first, then a living target, then a Force-reactive block on the aim, then (Push and Pull)
 * loose items. The finders are injectable so game tests can put a stand-in on the aim.
 */
public final class ForceDispatch {
	/** What a power ended up acting on. */
	public enum Step {
		/** A reactive entity consumed the act. */
		ENTITY,
		/** A living creature (or player) was moved or held. */
		LIVING,
		/** A reactive block consumed the act. */
		BLOCK,
		/** Loose items were moved. */
		ITEMS,
		/** Nothing was reached. */
		NONE
	}

	/** How a power finds the reactive things on its aim. */
	public interface Finders {
		/** The nearest reactive entity on the caster's aim ray within {@code range}, or null. */
		@Nullable Entity reactiveEntity(ServerPlayer caster, double range);

		/** The first reactive block on the caster's aim within {@code range}, or null. */
		@Nullable BlockHitResult reactiveBlock(ServerPlayer caster, double range);
	}

	/** The world: {@link ForceReactiveEntity#findOnAim} and {@link ForceBlockInteractions#findOnAim}. */
	public static final Finders WORLD = new Finders() {
		@Override
		public @Nullable Entity reactiveEntity(ServerPlayer caster, double range) {
			return ForceReactiveEntity.findOnAim(caster, range);
		}

		@Override
		public @Nullable BlockHitResult reactiveBlock(ServerPlayer caster, double range) {
			return ForceBlockInteractions.findOnAim(caster, range);
		}
	};

	/**
	 * Hands {@code act} to the block {@code hit} names. Returns true when its handler consumed it.
	 *
	 * @param direction unit vector of the act (away from the caster for a push, toward it for a pull, up for a lift)
	 * @param strength  the power's strength (its knockback, or 1)
	 */
	public static boolean block(ServerPlayer caster, BlockHitResult hit, ForceAct act, Vec3 direction, float strength, String abilityId) {
		ServerLevel level = caster.level();
		BlockPos pos = hit.getBlockPos();
		return ForceBlockInteractions.dispatch(new ForceBlockContext(caster, level, pos, level.getBlockState(pos), act, direction, strength, abilityId));
	}

	private ForceDispatch() {
	}
}

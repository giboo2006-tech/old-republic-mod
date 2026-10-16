package com.oldrepublic.force;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Force-reactive blocks (spec 10.6): puzzle blocks that answer Push, Pull and Lift. The world
 * feature registers one {@link ForceBlockHandler} per block and lists the block in
 * {@link #REACTIVE}; the powers find a block with {@link #findOnAim} and hand it the act with
 * {@link #dispatch}. Handlers change only their own block's state. Server side.
 */
public final class ForceBlockInteractions {
	/** {@code #oldrepublic:force_reactive}: the blocks {@link #findOnAim} stops at. */
	public static final TagKey<Block> REACTIVE = TagKey.create(Registries.BLOCK, OldRepublicMod.id("force_reactive"));

	private static final Map<Block, ForceBlockHandler> HANDLERS = new IdentityHashMap<>();

	/** Registers the handler of {@code block}. Throws if the block already has one. */
	public static void register(Block block, ForceBlockHandler handler) {
		if (HANDLERS.putIfAbsent(block, handler) != null) {
			throw new IllegalStateException("Force block handler registered twice for " + BuiltInRegistries.BLOCK.getKey(block));
		}
	}

	/**
	 * The first reactive block on the caster's eye ray within {@code range} blocks, or null when the
	 * ray meets something else first. A reactive block without collision (seen through its outline)
	 * counts, and so does a solid one behind grass or another block without collision.
	 */
	public static @Nullable BlockHitResult findOnAim(ServerPlayer caster, double range) {
		ServerLevel level = caster.level();
		Vec3 eye = caster.getEyePosition();
		Vec3 end = eye.add(caster.getLookAngle().scale(range));
		BlockHitResult outline = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));

		if (isReactive(level, outline)) {
			return outline;
		}

		BlockHitResult solid = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return isReactive(level, solid) ? solid : null;
	}

	/** Hands the act to the block's handler. Returns true when it consumed the act, false without a handler. */
	public static boolean dispatch(ForceBlockContext ctx) {
		ForceBlockHandler handler = HANDLERS.get(ctx.state().getBlock());
		return handler != null && handler.handle(ctx);
	}

	private static boolean isReactive(ServerLevel level, BlockHitResult hit) {
		return hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).is(REACTIVE);
	}

	private ForceBlockInteractions() {
	}
}

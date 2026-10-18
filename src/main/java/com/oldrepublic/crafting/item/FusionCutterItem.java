package com.oldrepublic.crafting.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.block.SealedPlatingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The fusion cutter (spec MILESTONE_4.md 7.6): a tool that mines wreck salvage and durasteel blocks
 * quickly, and cuts through W1's {@code sealed_plating} when its use is held on it for
 * {@code cutterSealTicks}, throwing sparks at the cut. Looking away or letting go stops the cut.
 */
public class FusionCutterItem extends Item {
	/** The blocks the cutter mines fast: the wreck and durasteel blocks. */
	private static final Set<Identifier> DURASTEEL = Set.of(
			OldRepublicMod.id("wreck_salvage"), OldRepublicMod.id("wreck_plating"), OldRepublicMod.id("wreck_plating_scorched"),
			OldRepublicMod.id("wreck_conduit"), OldRepublicMod.id("republic_panel"), OldRepublicMod.id("imperial_panel"));
	private static final Identifier SEALED_PLATING = OldRepublicMod.id("sealed_plating");
	private static final float FAST_SPEED = 12f;
	private static final int CUT_DURABILITY = 5;
	private static final int SPARK_INTERVAL = 6;

	/** The plating each player is cutting (server side). */
	private static final Map<UUID, BlockPos> CUTS = new HashMap<>();

	public FusionCutterItem(Properties properties) {
		super(properties);
	}

	private static boolean isDurasteel(BlockState state) {
		return DURASTEEL.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	/** Whether {@code state} is W1's sealed plating. */
	public static boolean isSealedPlating(BlockState state) {
		return SEALED_PLATING.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		return isDurasteel(state) ? FAST_SPEED : 1f;
	}

	@Override
	public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
		return isDurasteel(state);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0f) {
			stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
		}

		return true;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();

		if (player == null || !isSealedPlating(level.getBlockState(context.getClickedPos()))) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer) {
			CUTS.put(player.getUUID(), context.getClickedPos().immutable());
		}

		player.startUsingItem(context.getHand());
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer p)) {
			return;
		}

		BlockPos target = CUTS.get(p.getUUID());
		BlockHitResult hit = getPlayerPOVHitResult(level, p, ClipContext.Fluid.NONE);

		if (target == null || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target) || !isSealedPlating(level.getBlockState(target))) {
			CUTS.remove(p.getUUID());
			p.stopUsingItem();
			return;
		}

		int elapsed = getUseDuration(stack, entity) - remainingTicks;
		Vec3 at = hit.getLocation();

		if (elapsed % SPARK_INTERVAL == 0) {
			EffectEvents.around(server, CraftingEffects.BENCH_CRAFT, at, new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ()), p.getId(), 1f, CraftingEffects.TOOL_CUT);
		}

		if (elapsed >= CraftingBalance.get().cutterSealTicks) {
			CUTS.remove(p.getUUID());
			// Opens the whole connected sheet with the seal-open effect (W1).
			SealedPlatingBlock.cut(server, target);
			stack.hurtAndBreak(CUT_DURABILITY, p, EquipmentSlot.MAINHAND);
			p.stopUsingItem();
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTicks) {
		CUTS.remove(entity.getUUID());
		return false;
	}
}

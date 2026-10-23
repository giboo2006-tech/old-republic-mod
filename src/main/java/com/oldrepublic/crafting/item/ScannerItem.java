package com.oldrepublic.crafting.item;

import java.util.List;

import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.mission.MissionHooks;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The survey scanner (spec MILESTONE_4.md 7.6): a 48-block pulse that marks planet resources and
 * creatures for 200 ticks with small hovering motes and HUD-edge arrows (the client finds and
 * draws them from the {@code scanner_pulse} event). A contract's {@code survey} objective counts it
 * like a Sense cast. Cooldown 400 ticks.
 */
public class ScannerItem extends Item {
	public ScannerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (player instanceof ServerPlayer p) {
			pulse(p);
			p.getCooldowns().addCooldown(stack, CraftingBalance.get().scannerCooldown);
		}

		return InteractionResult.SUCCESS;
	}

	/** One survey pulse from {@code p}: the client effect, and the survey objective. Returns the creatures found. */
	public static int pulse(ServerPlayer p) {
		CraftingBalance b = CraftingBalance.get();
		AABB box = p.getBoundingBox().inflate(b.scannerRange);
		List<LivingEntity> creatures = p.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != p && e.isAlive() && !(e instanceof Player) && e.distanceToSqr(p) <= b.scannerRange * b.scannerRange);
		EffectEvents.toPlayer(p, CraftingEffects.SCANNER_PULSE, p.getEyePosition(), p.getLookAngle(), p.getId(), (float) b.scannerRange, b.scannerTicks);
		MissionHooks.onScanned(p);
		return creatures.size();
	}
}

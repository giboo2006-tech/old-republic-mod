package com.oldrepublic.crafting.item;

import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A player grenade (spec MILESTONE_4.md 7.6), thrown with use: {@code frag_grenade} lobs the M3
 * {@code frag_grenade} entity (with its fuse and push rules) at the point the player looks at, up to
 * {@link #FRAG_RANGE} blocks; {@code stun_grenade} throws a {@link StunGrenade}. Both share a
 * {@code grenadeCooldown}-tick cooldown and stack to 4.
 */
public class GrenadeItem extends Item {
	/** The farthest a frag is lobbed. */
	public static final double FRAG_RANGE = 16;

	private final boolean stun;

	public GrenadeItem(Properties properties, boolean stun) {
		super(properties);
		this.stun = stun;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
			if (stun) {
				StunGrenade grenade = new StunGrenade(server, p);
				grenade.shootFromRotation(p, p.getXRot(), p.getYRot(), 0f, 1.1f, 1f);
				server.addFreshEntity(grenade);
			} else {
				CraftingBalance b = CraftingBalance.get();
				FragGrenade.throwAt(server, p, aimPoint(p), b.playerGrenadeDamage, b.playerGrenadeRadius);
			}

			EffectEvents.around(server, CraftingEffects.GRENADE_PLAYER, p.getEyePosition(), p.getLookAngle(), p.getId(), 1f, CraftingEffects.GRENADE_THROW);
			p.getCooldowns().addCooldown(stack, CraftingBalance.get().grenadeCooldown);
			stack.consume(1, p);
		}

		return InteractionResult.SUCCESS;
	}

	/** Where the player looks, up to {@link #FRAG_RANGE} blocks (the first block in the way). */
	static Vec3 aimPoint(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(FRAG_RANGE));
		HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}
}

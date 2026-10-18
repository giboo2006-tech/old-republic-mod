package com.oldrepublic.crafting.item;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.crafting.CraftingItems;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.network.EffectEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown {@code stun_grenade} (spec MILESTONE_4.md 7.6): bursts where it first hits, dealing no
 * damage, staggering every living thing within {@code stunRadius} for {@code stunTicks}
 * ({@code stunDroidTicks} for droids) and stripping invisibility. The thrower and their allies are
 * spared by the M3 grenade rule ({@link FragGrenade#spares}). Never saved.
 */
public class StunGrenade extends ThrowableItemProjectile {
	/** The lead's {@code oldrepublic:droids} entity tag (spec 12.5). */
	public static final TagKey<EntityType<?>> DROIDS = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("droids"));
	private static final int MAX_FLIGHT_TICKS = 100;

	public StunGrenade(EntityType<? extends StunGrenade> type, Level level) {
		super(type, level);
	}

	public StunGrenade(Level level, LivingEntity thrower) {
		super(CraftingItems.STUN_GRENADE_ENTITY, thrower, level, new ItemStack(CraftingItems.STUN_GRENADE));
	}

	@Override
	protected Item getDefaultItem() {
		return CraftingItems.STUN_GRENADE;
	}

	@Override
	public void tick() {
		super.tick();

		if (level() instanceof ServerLevel level && !isRemoved() && tickCount > MAX_FLIGHT_TICKS) {
			burst(level);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (level() instanceof ServerLevel level && !isRemoved()) {
			burst(level);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);

		if (level() instanceof ServerLevel level && !isRemoved()) {
			burst(level);
		}
	}

	/** The burst: the flash and dust event, then the stagger on everything in range. */
	public void burst(ServerLevel level) {
		CraftingBalance b = CraftingBalance.get();
		Vec3 centre = position();
		Entity owner = getOwner();
		EffectEvents.around(level, CraftingEffects.GRENADE_PLAYER, centre, null, getId(), (float) b.stunRadius, CraftingEffects.GRENADE_STUN);

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(b.stunRadius),
				e -> e.isAlive() && e.position().distanceToSqr(centre) <= b.stunRadius * b.stunRadius)) {
			if (FragGrenade.spares(owner, target, false)) {
				continue;
			}

			target.removeEffect(MobEffects.INVISIBILITY);
			ControlEffects.stagger(owner, target, isDroid(target) ? b.stunDroidTicks : b.stunTicks, 0);
		}

		discard();
	}

	/** Whether {@code entity} is a droid (the {@code droids} tag, or a droid entity id). */
	public static boolean isDroid(Entity entity) {
		// API-CHECK: Entity#is(TagKey) via TypedInstance (as ForcePowers).
		return entity.is(DROIDS) || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().contains("droid");
	}
}

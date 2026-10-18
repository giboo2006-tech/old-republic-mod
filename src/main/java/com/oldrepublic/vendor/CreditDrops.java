package com.oldrepublic.vendor;

import java.util.Set;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Enemy credit drops (spec MILESTONE_4.md 7.7): a trooper, a Mandalorian or a security droid killed
 * by a player in a fight that counts as hostile drops 1 to 6 credit chits at 50%
 * ({@code vendors.creditDropChance}, {@code vendors.creditDrop}). Never from natural patrols (the
 * M3 anti-farm rule: {@link TrooperEntity#isPatrol}), from hunters that may not drop loot (called
 * hunters, {@link MandalorianHunter#mayDropLoot}) or from anything a hub spawned (its patrol droid).
 */
public final class CreditDrops {
	/** W6's enemies that drop credits, by id (they register after this class may load). */
	private static final Set<Identifier> DROPPERS = Set.of(OldRepublicMod.id("mandalorian_hunter"), OldRepublicMod.id("security_droid"));

	/** From {@code ServerLivingEntityEvents.AFTER_DEATH}. */
	static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer killer) || !drops(entity)
				|| !Attitudes.hostileSnapshot(entity, killer)) {
			return;
		}

		if (entity.entityTags().contains(HubNpcs.HUB_TAG) || entity instanceof MandalorianHunter hunter && !hunter.mayDropLoot(level)) {
			return;
		}

		VendorBalance b = VendorFeature.balance();

		if (entity.getRandom().nextDouble() >= b.creditDropChance) {
			return;
		}

		int chits = VendorRules.creditDrop(entity.getRandom().nextDouble(), b);

		if (chits > 0) {
			ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(M4Items.CREDIT_CHIT, chits));
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}
	}

	/** Whether this kind of enemy carries credits: troopers off patrol, Mandalorians and droids. */
	public static boolean drops(LivingEntity entity) {
		if (entity instanceof TrooperEntity trooper) {
			return !trooper.isPatrol();
		}

		return DROPPERS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
	}

	private CreditDrops() {
	}
}

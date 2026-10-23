package com.oldrepublic.force.sense;

import java.util.function.Consumer;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.force.ForcePowers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Force Sense's own provider (spec 10.1): every creature within the creature range, by entity
 * lookup (never a block scan), tinted by how it regards the caster. Other players only when
 * {@code force_sense.pvpEffects} is on.
 */
public final class SenseCreatures implements SenseTargets.Provider {
	@Override
	public void collect(ServerPlayer caster, int blockRange, int creatureRange, Consumer<SenseTargets.Point> out) {
		if (creatureRange <= 0) {
			return;
		}

		boolean players = ForceSense.balance().pvpEffects;
		double rangeSqr = (double) creatureRange * creatureRange;
		AABB box = caster.getBoundingBox().inflate(creatureRange);

		for (LivingEntity entity : caster.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && e.isAlive() && !e.isSpectator()
				&& (e instanceof Mob || players && e instanceof Player) && e.distanceToSqr(caster) <= rangeSqr)) {
			out.accept(new SenseTargets.Point(SenseTargets.Kind.CREATURE, ForcePowers.chest(entity), entity.getId(), attitude(caster, entity)));
		}
	}

	/**
	 * How {@code entity} regards {@code caster} for the tint: an owned tame animal is Allied; a
	 * creature engaged with or provoked by the caster is Hostile; otherwise {@link Attitudes#toward}
	 * (another player: Allied when on the caster's team, else Neutral).
	 */
	public static Attitude attitude(ServerPlayer caster, LivingEntity entity) {
		// API-CHECK: TamableAnimal#isOwnedBy(LivingEntity) in 26.2 (TamableAnimal#isTame compiles in ForceHeal)
		if (entity instanceof TamableAnimal tame && tame.isTame() && tame.isOwnedBy(caster)) {
			return Attitude.ALLIED;
		}

		if (entity instanceof Player player) {
			return caster.isAlliedTo(player) ? Attitude.ALLIED : Attitude.NEUTRAL;
		}

		if (Attitudes.countsAsHostile(caster, entity) || Attitudes.provokedBy(entity, caster)) {
			return Attitude.HOSTILE;
		}

		return Attitudes.toward(entity, caster);
	}
}

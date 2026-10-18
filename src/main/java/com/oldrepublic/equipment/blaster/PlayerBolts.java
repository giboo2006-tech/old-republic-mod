package com.oldrepublic.equipment.blaster;

import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.BlasterBolt;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The player-bolt rules of spec MILESTONE_4.md 5.1 that {@code enemy/BlasterBolt} (a lead file)
 * asks for: whom a player's bolt passes through, and whether damage came from a player's blaster
 * (which never refunds Force Energy). Server side.
 */
public final class PlayerBolts {
	/**
	 * Whether a bolt fired by {@code shooter} passes through {@code target}: the shooter's side
	 * ({@link Allegiances#friendly}), a mob that is {@link Attitude#ALLIED} to the shooter, the
	 * shooter's own tamed animals, and players on the shooter's team (vanilla team alliance). Other
	 * players follow the server's PvP and team rules in {@code hurtServer} (the M2 rule).
	 */
	public static boolean passesThrough(Player shooter, Entity target) {
		if (target == shooter) {
			return false;
		}

		if (Allegiances.friendly(shooter, target) || shooter.isAlliedTo(target)) {
			return true;
		}

		// API-CHECK: OwnableEntity#getOwner() (a default method returning the owner when it is loaded).
		if (target instanceof OwnableEntity ownable && ownable.getOwner() == shooter) {
			return true;
		}

		return target instanceof LivingEntity living && !(target instanceof Player) && Attitudes.toward(living, shooter) == Attitude.ALLIED;
	}

	/**
	 * Whether {@code source} is a bolt from a player's blaster (style {@link BlasterBolt.Style#PLAYER}):
	 * its hits and kills never refund Force Energy and are never a power hit or a technique.
	 */
	public static boolean isPlayerBlaster(DamageSource source) {
		return source.getDirectEntity() instanceof BlasterBolt bolt && bolt.style() == BlasterBolt.Style.PLAYER;
	}

	private PlayerBolts() {
	}
}

package com.oldrepublic.saber;

import java.util.Map;
import java.util.WeakHashMap;

import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.duel.NpcGuard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What other features (mainly the Force powers) can ask of or tell saber combat, beyond the
 * {@code force.ForceReactions} handler the saber feature registers. Server side unless noted.
 */
public final class SaberInteractions {
	private static final Map<LivingEntity, Long> LIGHTNING_SOUND = new WeakHashMap<>();

	/** Whether {@code target} (a player or a saber NPC) is guarding with a lit saber against something at {@code from}. */
	public static boolean isGuardingAgainst(LivingEntity target, Vec3 from) {
		if (ForceStatus.isGuardSuppressed(target)) {
			return false;
		}

		if (target instanceof Player player) {
			return SaberGuard.isGuardingAgainst(player, from);
		}

		return target instanceof Mob mob && NpcGuard.isGuardingAgainst(mob, from);
	}

	/** Drains guard stamina from a player or saber NPC. Returns true if the guard broke. */
	public static boolean drainGuard(LivingEntity target, float amount) {
		if (target instanceof ServerPlayer player) {
			return SaberGuard.drainGuard(player, amount);
		}

		return target instanceof Mob mob && NpcGuard.drain(mob, amount);
	}

	/** Gives back guard stamina (Serene Ward). */
	public static void restoreGuard(LivingEntity target, float amount) {
		if (target instanceof ServerPlayer player) {
			SaberGuard.restore(player, amount);
		} else if (target instanceof Mob mob) {
			NpcGuard.restore(mob, amount);
		}
	}

	/**
	 * Force Lightning hit a guarding blade: plays {@code saber.lightning_block} at the guard, at most
	 * once per {@code lightningBlockSoundInterval} ticks per entity. The force feature calls this on
	 * each absorbed damage tick.
	 */
	public static void onLightningBlocked(LivingEntity target) {
		long now = target.level().getGameTime();
		Long last = LIGHTNING_SOUND.get(target);

		if (last != null && now - last < SaberToggle.balance().lightningBlockSoundInterval) {
			return;
		}

		LIGHTNING_SOUND.put(target, now);
		target.level().playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), ModSounds.SABER_LIGHTNING_BLOCK,
				SoundSource.PLAYERS, 0.9f, 0.9f + target.getRandom().nextFloat() * 0.2f);
	}

	/** The player has just leapt with Force Jump (the Ataru aerial strike may follow). */
	public static void markLeap(ServerPlayer player) {
		SaberCombat.markLeap(player);
	}

	/** Reeling Pull: the player's next saber hit within {@code ticks} deals {@code mult} times the damage. */
	public static void grantNextHitBonus(ServerPlayer player, float mult, int ticks) {
		SaberCombat.grantNextHitBonus(player, mult, ticks);
	}

	/** Ataru removes Force Jump's landing recovery. Works on both sides. */
	public static boolean skipsLandingRecovery(Player player) {
		return SaberPassives.form(player) == SaberForm.ATARU;
	}

	private SaberInteractions() {
	}
}

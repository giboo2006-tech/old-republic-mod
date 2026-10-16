package com.oldrepublic.enemy.construct;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

/**
 * Ruin Sentinels for other features (spec section 7.7): waking dormant sentinels and spawning
 * them. Signatures are frozen (spec section 15.1).
 */
public final class Sentinels {
	/**
	 * Wakes the dormant sentinels within {@code radius} of {@code pos}, never one dormant for
	 * {@code cause}'s side, and returns how many woke. Phase 0 stub: W5 fills this in.
	 */
	public static int wakeNear(ServerLevel l, BlockPos pos, int radius, @Nullable Player cause) {
		return 0;
	}

	/**
	 * Spawns a sentinel of {@code v} at {@code at}; {@code bound} marks one re-bound to guard a trail,
	 * owned by {@code owner} when not null. Phase 0 stub: W5 fills this in.
	 */
	public static LivingEntity spawn(ServerLevel l, BlockPos at, SentinelVariant v, boolean bound, @Nullable UUID owner) {
		ArmorStand stand = new ArmorStand(l, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stand.setInvulnerable(true);
		stand.setCustomName(Component.literal("Sentinel stub: " + v.name().toLowerCase(Locale.ROOT) + (bound ? " bound" : "")));
		stand.setCustomNameVisible(true);
		l.addFreshEntity(stand);
		return stand;
	}

	private Sentinels() {
	}
}

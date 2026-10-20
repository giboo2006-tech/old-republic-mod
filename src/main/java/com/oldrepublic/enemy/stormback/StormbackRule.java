package com.oldrepublic.enemy.stormback;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The stormback as the shared hooks see it (spec MILESTONE_5.md 12.1, 5.2): neutral; its anger at a
 * player who struck it is provocation (the fight does not count as hostile), its anger at a player
 * who crowded it is engagement (it charged first, so the fight counts).
 */
public final class StormbackRule implements Attitudes.Rule {
	@Override
	public @Nullable Attitude toward(LivingEntity self, Player p) {
		return self instanceof Stormback ? Attitude.NEUTRAL : null;
	}

	@Override
	public boolean engaged(LivingEntity self, Player p) {
		return self instanceof Stormback stormback && stormback.isAngryAt(p) && !stormback.isProvokedBy(p);
	}

	@Override
	public boolean provokedBy(LivingEntity self, Player p) {
		return self instanceof Stormback stormback && stormback.isProvokedBy(p);
	}
}

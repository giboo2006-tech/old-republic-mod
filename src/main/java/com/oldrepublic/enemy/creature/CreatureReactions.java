package com.oldrepublic.enemy.creature;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The beasts as the shared hooks see them (spec MILESTONE_4.md 10.3, 10.4):
 * <ul>
 * <li>{@link ForceReactions.Handler}: an interrupt (a Barrier pulse, a Push, a stun) turns an ash
 * hound's lunge aside and staggers it.</li>
 * <li>{@link Attitudes.Rule}: a hollowhorn is neutral, a corrupted one hostile; its anger at a player
 * who struck it is provocation, its anger over a calf is engagement (it charged first).</li>
 * </ul>
 */
public final class CreatureReactions implements ForceReactions.Handler, Attitudes.Rule {
	@Override
	public void interruptWindup(LivingEntity target, @Nullable ServerPlayer caster, String cause) {
		if (target instanceof AshHound hound) {
			hound.interrupt(caster);
		}
	}

	@Override
	public @Nullable Attitude toward(LivingEntity self, Player p) {
		if (!(self instanceof Hollowhorn hollowhorn)) {
			return null;
		}

		return hollowhorn.isCorrupted() ? Attitude.HOSTILE : Attitude.NEUTRAL;
	}

	@Override
	public boolean engaged(LivingEntity self, Player p) {
		return self instanceof Hollowhorn hollowhorn && hollowhorn.isAngryAt(p) && !hollowhorn.isProvokedBy(p);
	}

	@Override
	public boolean provokedBy(LivingEntity self, Player p) {
		return self instanceof Hollowhorn hollowhorn && hollowhorn.isProvokedBy(p);
	}
}

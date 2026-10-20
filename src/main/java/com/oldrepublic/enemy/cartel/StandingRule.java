package com.oldrepublic.enemy.cartel;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.trooper.TrooperAttitudeRule;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.quest.story.StoryTags;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The {@link Attitudes} rule of the faction foes (spec MILESTONE_5.md 7, 12.1): the player's tier with
 * the foe's faction sets the standing attitude with the M3 trooper table (Hostile attacks on sight,
 * Distrusted is Wary, Neutral ignores, Trusted and Honoured are Allied); an owned foe takes its
 * {@link FactionFoe#ownedAttitude} toward its owner's party; its own engagement and provocation add
 * the overrides. Peaceful caps every answer at Neutral and turns the overrides off.
 */
public final class StandingRule implements Attitudes.Rule {
	@Override
	public @Nullable Attitude toward(LivingEntity self, Player p) {
		return self instanceof FactionFoe foe ? attitude(self, foe, p) : null;
	}

	@Override
	public boolean engaged(LivingEntity self, Player p) {
		return self instanceof FactionFoe foe && !TrooperAttitudeRule.peaceful(self) && foe.standing().engaged(p.getUUID(), self.level().getGameTime());
	}

	@Override
	public boolean provokedBy(LivingEntity self, Player p) {
		return self instanceof FactionFoe foe && !TrooperAttitudeRule.peaceful(self) && foe.standing().provoked(p.getUUID(), self.level().getGameTime());
	}

	/** The standing attitude of {@code foe} toward {@code p}, before engagement and provocation. */
	public static Attitude attitude(LivingEntity self, FactionFoe foe, Player p) {
		boolean peaceful = TrooperAttitudeRule.peaceful(self);
		UUID owner = StoryTags.owner(self);

		if (owner != null && inParty(self, owner, p, foe.partyRange())) {
			Attitude owned = foe.ownedAttitude();
			return peaceful && owned == Attitude.HOSTILE ? Attitude.NEUTRAL : owned;
		}

		return TrooperAttitudeRule.attitude(Identity.tier(p, foe.faction()), peaceful);
	}

	/** Whether {@code p} is the owner, or a player within {@code range} of the online owner in the same level. */
	public static boolean inParty(LivingEntity self, UUID owner, Player p, double range) {
		if (p.getUUID().equals(owner)) {
			return true;
		}

		if (!(self.level() instanceof ServerLevel level)) {
			return false;
		}

		ServerPlayer leader = level.getServer().getPlayerList().getPlayer(owner);
		return leader != null && leader.level() == p.level() && leader.distanceToSqr(p) <= range * range;
	}
}

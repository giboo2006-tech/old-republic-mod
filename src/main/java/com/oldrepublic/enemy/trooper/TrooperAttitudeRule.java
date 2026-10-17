package com.oldrepublic.enemy.trooper;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The trooper rule of {@link Attitudes} (spec 5.2): the reputation tier with the trooper's army
 * sets the standing attitude, and the trooper's own provoked, engaged and incident state add the
 * overrides. Peaceful caps every answer at NEUTRAL and turns the overrides off for players. Has no
 * opinion about anything that is not a trooper, so rules registered later still apply to it.
 */
public final class TrooperAttitudeRule implements Attitudes.Rule {
	/**
	 * The standing attitude for a reputation tier (spec 5.2): HOSTILE attacks on sight, DISTRUSTED
	 * is WARY, NEUTRAL ignores, TRUSTED and HONOURED are ALLIED. Peaceful caps it at NEUTRAL.
	 */
	public static Attitude attitude(ReputationTier tier, boolean peaceful) {
		Attitude attitude = switch (tier) {
			case HOSTILE -> Attitude.HOSTILE;
			case DISTRUSTED -> Attitude.WARY;
			case NEUTRAL -> Attitude.NEUTRAL;
			case TRUSTED, HONOURED -> Attitude.ALLIED;
		};

		return peaceful && attitude.ordinal() < Attitude.NEUTRAL.ordinal() ? Attitude.NEUTRAL : attitude;
	}

	/** Whether {@code self}'s level is on Peaceful. */
	public static boolean peaceful(LivingEntity self) {
		return self.level().getDifficulty() == Difficulty.PEACEFUL;
	}

	@Override
	public @Nullable Attitude toward(LivingEntity self, Player p) {
		if (!(self instanceof TrooperEntity trooper)) {
			return null;
		}

		return attitude(Identity.tier(p, trooper.army()), peaceful(self));
	}

	@Override
	public boolean engaged(LivingEntity self, Player p) {
		if (!(self instanceof TrooperEntity trooper) || trooper.isDowned() || peaceful(self)) {
			return false;
		}

		long now = self.level().getGameTime();
		return trooper.isEngagedWith(p.getUUID(), now) || trooper.incidentIncludes(p.getUUID(), now);
	}

	@Override
	public boolean provokedBy(LivingEntity self, Player p) {
		return self instanceof TrooperEntity trooper && !trooper.isDowned() && !peaceful(self)
				&& trooper.isProvokedBy(p.getUUID(), self.level().getGameTime());
	}
}

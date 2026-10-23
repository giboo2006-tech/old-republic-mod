package com.oldrepublic.boss.kaast;

import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Torvin Kaast as the shared Force hooks see him (spec MILESTONE_4.md 10.5, 9.3):
 * <ul>
 * <li>{@link ForceReactions.Handler}: a Push on a nest knocks him off (phase 1); Lift is refused
 * except {@code liftFallBlocks} during that fall's stagger; Choke holds him only once, in phase 3,
 * for {@code chokeTicks}. Other interrupts are not his (the hunters' handler reaches his no-op).</li>
 * <li>{@link ControlEffects.DurationModifier}: control effects on him are {@code controlShorten}
 * shorter, except the Choke's hold, which is already his own rule.</li>
 * </ul>
 */
public final class KaastReactions implements ForceReactions.Handler, ControlEffects.DurationModifier {
	@Override
	public float onPushHit(ServerPlayer caster, LivingEntity target) {
		if (target instanceof TorvinKaast kaast) {
			kaast.onPushed();
		}

		return 1f;
	}

	@Override
	public float liftHeight(LivingEntity target, float requested) {
		return target instanceof TorvinKaast kaast ? KaastRules.liftHeight(kaast.isFallStaggered(), requested, KaastBalance.get()) : requested;
	}

	@Override
	public int chokeTicks(LivingEntity target, int requested) {
		return target instanceof TorvinKaast kaast ? kaast.allowChoke(requested) : requested;
	}

	@Override
	public float multiplier(LivingEntity target, String kind) {
		return target instanceof TorvinKaast && !ControlEffects.HOLD.equals(kind) ? 1f - KaastBalance.get().controlShorten : 1f;
	}
}

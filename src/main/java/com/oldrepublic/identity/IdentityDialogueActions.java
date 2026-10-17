package com.oldrepublic.identity;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Dialogue actions of the identity feature, used by {@code yield.json}, {@code captives.json} and
 * the mission yields {@code yield_intercept.json} and {@code yield_border.json}. Each re-checks on
 * the server that the speaker is still waiting for this player's decision.
 */
final class IdentityDialogueActions {
	static final String YIELD_SPARE = "identity:yield_spare";
	static final String YIELD_EXECUTE = "identity:yield_execute";
	static final String CAPTIVES_FREE = "identity:captives_free";
	static final String CAPTIVES_PAY = "identity:captives_pay";
	static final String CAPTIVES_LEAVE = "identity:captives_leave";
	/** Questions a yielded intercept sergeant instead of judging him (spec 5.5). */
	static final String YIELD_INTERROGATE = "yield:interrogate";
	/** Hands the yielded I2 sergeant over to his enemies' escort (spec 5.4). */
	static final String YIELD_HAND_OVER = "yield:hand_over";

	static void register() {
		DialogueActions.register(YIELD_SPARE, (p, speaker) -> decideYield(p, speaker, YieldOutcome.SPARED));
		DialogueActions.register(YIELD_EXECUTE, (p, speaker) -> decideYield(p, speaker, YieldOutcome.EXECUTED));
		DialogueActions.register(YIELD_INTERROGATE, (p, speaker) -> decideYield(p, speaker, YieldOutcome.INTERROGATED));
		DialogueActions.register(YIELD_HAND_OVER, (p, speaker) -> decideYield(p, speaker, YieldOutcome.HANDED_OVER));
		DialogueActions.register(CAPTIVES_FREE, (p, speaker) -> decideCaptives(p, speaker, CaptiveOutcome.FREED));
		DialogueActions.register(CAPTIVES_PAY, (p, speaker) -> decideCaptives(p, speaker, CaptiveOutcome.EXPLOITED));
		DialogueActions.register(CAPTIVES_LEAVE, (p, speaker) -> decideCaptives(p, speaker, CaptiveOutcome.ABANDONED));
	}

	private static void decideYield(ServerPlayer p, @Nullable Entity speaker, YieldOutcome outcome) {
		if (speaker instanceof LivingEntity living && speaker instanceof Yieldable yieldable && yieldable.isYielded()
				&& Yielding.isPending(speaker) && Yielding.mayDecide(yieldable, p)) {
			Yielding.resolve(living, outcome, p);
		}
	}

	private static void decideCaptives(ServerPlayer p, @Nullable Entity speaker, CaptiveOutcome outcome) {
		if (!(speaker instanceof FrontierSettler settler) || settler.state() != FrontierSettler.State.CAPTIVE) {
			return;
		}

		if (settler.owner() != null && !settler.owner().equals(p.getUUID())) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.captive_not_yours"));
			return;
		}

		Settlers.resolveCaptives(p, settler.groupId(), outcome, settler);
	}

	private IdentityDialogueActions() {
	}
}

package com.oldrepublic.saber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.skill.CombatTracker;
import com.oldrepublic.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Saber form changes (spec section 9.4). A change is allowed only with no combat for
 * {@code formCombatLockTicks}; it takes {@code formSwitchTicks}, during which the player cannot
 * attack or guard and shows the salute pose. The Force screen's "Set active form" (through
 * {@code skill.FormChanges}) and the Saber Form key both come here. Also keeps Ataru's attack-speed
 * bonus, and the Milestone 5 forms' attack-speed percentage (Djem So, Jar'Kai while dual-wielding,
 * MILESTONE_5.md 10.1), on the player while the form is in effect and a lit saber is held. Server side.
 */
public final class SaberForms {
	/** Ataru's attack speed bonus. */
	public static final Identifier ATARU_SPEED_ID = OldRepublicMod.id("ataru_speed");
	/** The Milestone 5 forms' attack speed change, a multiplier on the total (10.1). */
	public static final Identifier FORM_SPEED_ID = OldRepublicMod.id("form_speed");

	private record Change(String formNodeId, long doneAt) {
	}

	private static final Map<UUID, Change> CHANGING = new HashMap<>();

	/**
	 * Requests a change to {@code formNodeId} ({@code ""} = Unformed). Returns true when the change
	 * started (it completes {@code formSwitchTicks} later); sends the reason and returns false
	 * otherwise.
	 */
	public static boolean request(ServerPlayer player, String formNodeId) {
		SaberBalance b = SaberToggle.balance();
		SaberForm target = SaberForm.byNode(formNodeId);

		if (!target.nodeId().equals(formNodeId) || (target != SaberForm.UNFORMED && !SaberPassives.has(player, target.nodeId()))) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.form_not_owned"));
			return false;
		}

		if (target == SaberPassives.selectedForm(player) || isSwitching(player)) {
			return false;
		}

		if (CombatTracker.inCombat(player, b.formCombatLockTicks) || SaberTechniques.isBusy(player)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.form_in_combat"));
			return false;
		}

		player.stopUsingItem();
		CHANGING.put(player.getUUID(), new Change(formNodeId, player.level().getGameTime() + b.formSwitchTicks));
		EffectEvents.toTracking(player, SaberEffects.FORM, player.getEyePosition(), player.getLookAngle(), player.getId(), b.formSwitchTicks, target.ordinal());
		return true;
	}

	/** The Saber Form key: Unformed, then each owned form in order, then Unformed again. */
	public static boolean cycle(ServerPlayer player) {
		List<SaberForm> owned = new ArrayList<>();

		for (SaberForm form : SaberForm.values()) {
			if (form == SaberForm.UNFORMED || SaberPassives.has(player, form.nodeId())) {
				owned.add(form);
			}
		}

		if (owned.size() < 2) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.form_none"));
			return false;
		}

		int index = owned.indexOf(SaberPassives.selectedForm(player));
		return request(player, owned.get((index + 1) % owned.size()).nodeId());
	}

	/** True during the change: no attacking or guarding. */
	public static boolean isSwitching(ServerPlayer player) {
		return CHANGING.containsKey(player.getUUID());
	}

	/** Once per server tick per player: finishes changes and keeps the forms' speed modifiers current. */
	static void tick(ServerPlayer player) {
		Change change = CHANGING.get(player.getUUID());

		if (change != null && player.level().getGameTime() >= change.doneAt) {
			CHANGING.remove(player.getUUID());
			Skills.setActiveForm(player, change.formNodeId);
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.form_set", SaberForm.byNode(change.formNodeId).displayName()));
		}

		// The stance others see (MILESTONE_5.md 10.1): only a change is synced.
		SaberStances.update(player);
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);

		if (speed == null) {
			return;
		}

		SaberForm form = SaberPassives.form(player);
		boolean lit = SaberComponents.isUsable(player.getMainHandItem());
		boolean ataru = form == SaberForm.ATARU && lit;
		if (ataru && !speed.hasModifier(ATARU_SPEED_ID)) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ATARU_SPEED_ID, SaberToggle.balance().ataruSpeed, AttributeModifier.Operation.ADD_VALUE));
		} else if (!ataru && speed.hasModifier(ATARU_SPEED_ID)) {
			speed.removeModifier(ATARU_SPEED_ID);
		}

		float pct = lit ? form.modifiers(SaberToggle.balance()).traits().speedPct() : 0f;
		AttributeModifier current = speed.getModifier(FORM_SPEED_ID);

		if (pct == 0f) {
			if (current != null) {
				speed.removeModifier(FORM_SPEED_ID);
			}
		} else if (current == null || current.amount() != pct / 100.0) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(FORM_SPEED_ID, pct / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	static void forget(ServerPlayer player) {
		CHANGING.remove(player.getUUID());
	}

	private SaberForms() {
	}
}

package com.oldrepublic.client.ability;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.hud.HudClient;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Turns the four ability slot keys into {@code ability_input} intents (START on press, RELEASE on
 * release, CANCEL when a screen opens or the player dies) and runs the slot's
 * {@link ClientAbilityHook} for local prediction. The server stays authoritative and reports back
 * with {@link AbilityStatePayload} whether a charge or channel really runs; the HUD reads both.
 *
 * <p>Rules (spec 10.1): only one power charges or channels at a time; pressing another slot while
 * one is active cancels the first and is otherwise ignored. A release while a screen is open is
 * always a cancel, never a cast (vanilla releases every key when a screen opens).
 */
public final class AbilityInput {
	/** A press the server has not confirmed as a charge or channel within this long is dropped locally. */
	private static final int CONFIRM_TICKS = 20;

	private static final boolean[] WAS_DOWN = new boolean[4];
	/** Slot whose key started the charge or channel the client is tracking, or -1. */
	private static int heldSlot = -1;
	private static String heldAbility = "";
	private static InputStyle heldStyle = InputStyle.TAP;
	private static int pressedAt;
	/** True once the server confirmed the held power is active. */
	private static boolean confirmed;
	private static int confirmedAt;
	/** Slot and start tick of the power the server last said is active (also after the key is up). */
	private static int activeSlot = -1;
	private static int activeSince;
	/** The last hold-and-release power released, whose cooldown the crosshair brackets drain with. */
	private static String lastCharged = "";

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;

		if (player == null) {
			reset();
			return;
		}

		boolean screenOpen = mc.gui.screen() != null;
		boolean dead = !player.isAlive() || player.isSpectator();

		if (heldSlot >= 0 && (screenOpen || dead)) {
			cancelHeld(mc, true);
		}

		int now = QuestClient.ticks();

		if (heldSlot >= 0 && !confirmed && heldStyle != InputStyle.TAP && now - pressedAt > CONFIRM_TICKS) {
			// The server refused the start (no energy, cooldown, refused): stop predicting.
			cancelHeld(mc, false);
		}

		for (int slot = 0; slot < WAS_DOWN.length; slot++) {
			boolean down = ForceKeys.SLOTS.get(slot).isDown() && !screenOpen && !dead;

			if (down && !WAS_DOWN[slot]) {
				press(mc, player, slot, now);
			} else if (!down && WAS_DOWN[slot] && slot == heldSlot) {
				release(mc);
			}

			WAS_DOWN[slot] = down;
		}
	}

	private static void press(Minecraft mc, LocalPlayer player, int slot, int now) {
		if (heldSlot >= 0 || activeSlot >= 0) {
			// Another power is charging or channelling: this press cancels it (the server does the same
			// when it sees the START) and does nothing else.
			ClientPlayNetworking.send(new AbilityInputPayload(slot, AbilityAction.START));

			if (heldSlot >= 0) {
				cancelHeld(mc, false);
			}

			activeSlot = -1;
			return;
		}

		String ability = Skills.slot(player, slot);
		ClientAbilityHook hook = ability.isEmpty() ? null : ClientAbilityHooks.get(ability);

		if (hook != null && !hook.precheck(mc)) {
			return;
		}

		// The server answers every press itself (locked or empty slot messages included).
		ClientPlayNetworking.send(new AbilityInputPayload(slot, AbilityAction.START));

		if (ability.isEmpty()) {
			return;
		}

		warnLowEnergy(player, ability);
		InputStyle style = ClientAbilityInfo.inputStyle(ability);

		if (style != InputStyle.TAP) {
			heldSlot = slot;
			heldAbility = ability;
			heldStyle = style;
			pressedAt = now;
			confirmed = false;
		}

		if (hook != null) {
			hook.onPress(mc);
		}
	}

	private static void release(Minecraft mc) {
		ClientPlayNetworking.send(new AbilityInputPayload(heldSlot, AbilityAction.RELEASE));
		ClientAbilityHook hook = ClientAbilityHooks.get(heldAbility);

		if (heldStyle == InputStyle.HOLD_RELEASE) {
			lastCharged = heldAbility;
		}

		clearHeld();

		if (hook != null) {
			hook.onRelease(mc);
		}
	}

	/** Gives up the held power locally, telling the server when {@code send} is set. */
	private static void cancelHeld(Minecraft mc, boolean send) {
		if (send) {
			ClientPlayNetworking.send(new AbilityInputPayload(heldSlot, AbilityAction.CANCEL));
		}

		ClientAbilityHook hook = ClientAbilityHooks.get(heldAbility);
		clearHeld();

		if (hook != null) {
			hook.onCancel(mc);
		}
	}

	private static void clearHeld() {
		heldSlot = -1;
		heldAbility = "";
		heldStyle = InputStyle.TAP;
		confirmed = false;
	}

	/** Flashes the energy bar at once when the press cannot be paid (the server plays the sound). */
	private static void warnLowEnergy(LocalPlayer player, String ability) {
		ForceData data = player.getAttached(ForceAttachments.FORCE);

		if (data == null || player.isCreative() || data.cooldown(ability) > 0) {
			return;
		}

		if (data.energy < cost(player, data, ability)) {
			HudClient.onEnergyFail();
		}
	}

	/** The energy the player needs to start {@code ability}: base cost, mastery, skills and alignment. */
	public static float cost(LocalPlayer player, ForceData data, String ability) {
		Float masteryPct = ClientAbilityInfo.value(ability, "mastery_cost_pct");
		float mastery = masteryPct == null ? 1f : Math.max(0f, 1f - masteryPct / 100f * Progression.masteryRank(data.mastery(ability)));
		return ClientAbilityInfo.startCost(ability) * mastery * ClientAbilityInfo.costMultiplier(player, ability);
	}

	/** The server's word on whether a power runs ({@code ability_state}). */
	public static void onState(AbilityStatePayload payload) {
		int now = QuestClient.ticks();

		if (payload.active()) {
			activeSlot = payload.slot();
			activeSince = now;

			if (payload.slot() == heldSlot) {
				confirmed = true;
				confirmedAt = now;
			}

			return;
		}

		if (payload.slot() == activeSlot) {
			activeSlot = -1;
		}

		if (payload.slot() == heldSlot && confirmed) {
			// The server ended the charge or channel while the key is still down (timeout, interrupt).
			Minecraft mc = Minecraft.getInstance();
			cancelHeld(mc, false);
		}
	}

	// ---------------------------------------------------------------- HUD reads

	/** Slot of the power that is charging or channelling (held or confirmed), or -1. */
	public static int activeSlot() {
		return heldSlot >= 0 ? heldSlot : activeSlot;
	}

	/**
	 * Charge (0..1) of a held hold-and-release power in {@code slot}, or -1 when none: drives the
	 * HUD's charge arc and the crosshair brackets.
	 */
	public static float charge(int slot, float partialTick) {
		if (slot != heldSlot || heldStyle != InputStyle.HOLD_RELEASE) {
			return -1f;
		}

		int full = ClientAbilityInfo.fullChargeTicks(heldAbility);
		return full <= 0 ? 1f : Math.min(1f, (QuestClient.ticks() - pressedAt + partialTick) / full);
	}

	/** Channel progress (0..1) of the power running in {@code slot}, or -1 when none. */
	public static float channel(int slot, float partialTick, @Nullable String ability) {
		if (ability == null || slot != activeSlot() || ClientAbilityInfo.inputStyle(ability) != InputStyle.CHANNEL) {
			return -1f;
		}

		int since = slot == heldSlot && confirmed ? confirmedAt : activeSince;

		if (slot == heldSlot && !confirmed) {
			return 0f;
		}

		int max = ClientAbilityInfo.maxChannelTicks(ability);
		return max <= 0 ? 0f : Math.min(1f, (QuestClient.ticks() - since + partialTick) / max);
	}

	/** The last hold-and-release power the player released (or {@code ""}). */
	public static String lastCharged() {
		return lastCharged;
	}

	/** Forgets everything (disconnect, level change). */
	public static void reset() {
		for (int i = 0; i < WAS_DOWN.length; i++) {
			WAS_DOWN[i] = false;
		}

		clearHeld();
		activeSlot = -1;
		lastCharged = "";
	}

	private AbilityInput() {
	}
}

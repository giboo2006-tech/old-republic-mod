package com.oldrepublic.client.ability;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.hud.HudClient;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.force.lift.ForceLift;
import com.oldrepublic.force.sense.ForceSense;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Turns the four ability slot keys into {@code ability_input} intents (START on press, RELEASE on
 * release, CANCEL when a screen opens or the player dies) and runs the slot's
 * {@link ClientAbilityHook} for local prediction. The server stays authoritative and reports back
 * with {@link AbilityStatePayload} whether a charge or channel really runs; the HUD reads both.
 * The Force Sense key is a tap of its own, sent as {@link AbilityInputPayload#SENSE_SLOT}; it is
 * drained every tick and ignored while a screen is open, the player is dead or held.
 *
 * <p>The second bar (MILESTONE_5.md 9.1): while Focus is held, the four slot keys send slots 5 to 8
 * (indices 4 to 7) and nothing of the first bar. The bar is chosen when a key goes down and kept for
 * that press, so a held hold-and-release or channel power keeps its slot until its key comes up,
 * whatever Focus does in between. Pressing Focus plays a soft click for the local player only.
 *
 * <p>Rules (spec 10.1): only one power charges or channels at a time; pressing another slot while
 * one is active cancels the first and is otherwise ignored. A release while a screen is open is
 * always a cancel, never a cast (vanilla releases every key when a screen opens).
 */
public final class AbilityInput {
	/** A press the server has not confirmed as a charge or channel within this long is dropped locally. */
	private static final int CONFIRM_TICKS = 20;
	private static final float FOCUS_VOLUME = 0.45f;

	/** Per slot key (R, Z, V, B): down last tick, and the slot (0 to 7) its current press was sent for. */
	private static final boolean[] WAS_DOWN = new boolean[SkillData.BAR_SLOTS];
	private static final int[] PRESSED_SLOT = {-1, -1, -1, -1};
	private static boolean senseWasDown;
	/** Focus held this tick (the second bar), and last tick. */
	private static boolean focus;
	private static boolean focusWasDown;
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

		focusTick(screenOpen || dead);

		for (int key = 0; key < WAS_DOWN.length; key++) {
			KeyMapping mapping = ForceKeys.SLOTS.get(key);
			boolean clicked = false;

			while (mapping.consumeClick()) {
				clicked = true;
			}

			boolean down = mapping.isDown() && !screenOpen && !dead;

			if (down && !WAS_DOWN[key]) {
				// The bar is chosen now, at key-down, and kept until this key comes up.
				PRESSED_SLOT[key] = slotFor(key);
				press(mc, player, PRESSED_SLOT[key], now);
			} else if (!down && WAS_DOWN[key]) {
				if (PRESSED_SLOT[key] >= 0 && PRESSED_SLOT[key] == heldSlot) {
					release(mc);
				}

				PRESSED_SLOT[key] = -1;
			} else if (clicked && !down && !WAS_DOWN[key] && !screenOpen && !dead) {
				// A quick tap pressed and released the key between two ticks, so isDown() never saw
				// it: still a press (and, for a held power, its release).
				int slot = slotFor(key);
				press(mc, player, slot, now);

				if (slot == heldSlot) {
					release(mc);
				}
			}

			WAS_DOWN[key] = down;
		}

		senseTick(mc, player, screenOpen || dead);
	}

	/** The slot a press of slot key {@code key} (0 to 3) reaches now: the second bar's while Focus is held. */
	private static int slotFor(int key) {
		return focus ? SkillData.BAR_SLOTS + key : key;
	}

	/** Focus (MILESTONE_5.md 9.1): drained every tick; a press plays the soft click for this player only. */
	private static void focusTick(boolean blocked) {
		KeyMapping key = ForceKeys.FOCUS;

		while (key.consumeClick()) {
			// Only whether it is held matters.
		}

		focus = key.isDown() && !blocked;

		if (focus && !focusWasDown) {
			QuestSounds.ui(ModSounds.FORCE_FOCUS, 1f, FOCUS_VOLUME);
		}

		focusWasDown = focus;
	}

	/**
	 * The Force Sense key (spec 10.1, 11.1): one START on each press, also for a tap between two ticks.
	 * Clicks are always drained, so nothing queued while blocked fires later.
	 */
	private static void senseTick(Minecraft mc, LocalPlayer player, boolean blockedByScreen) {
		KeyMapping key = ForceKeys.FORCE_SENSE;
		boolean clicked = false;

		while (key.consumeClick()) {
			clicked = true;
		}

		// Held by the Grip or a PvP Lift: the server refuses Sense too; say nothing locally.
		boolean blocked = blockedByScreen || Holds.isHeld(player);
		boolean down = key.isDown() && !blocked;

		if (!blocked && !senseWasDown && (down || clicked)) {
			pressSense(mc, player);
		}

		senseWasDown = down;
	}

	private static void pressSense(Minecraft mc, LocalPlayer player) {
		ClientAbilityHook hook = ClientAbilityHooks.get(ForceSense.ID);

		if (hook != null && !hook.precheck(mc)) {
			return;
		}

		// The server answers busy, locked and refused casts itself, with the usual failure feedback.
		ClientPlayNetworking.send(new AbilityInputPayload(AbilityInputPayload.SENSE_SLOT, AbilityAction.START));
		warnLowEnergy(player, ForceSense.ID);

		if (hook != null) {
			hook.onPress(mc);
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

	/**
	 * The energy the player needs to start {@code ability}: base cost, mastery (a percentage per rank,
	 * or Sense's flat energy per rank), Archivist's Road on Lift, then skills, alignment and the other
	 * features' cost multipliers ({@link ClientAbilityInfo#costMultiplier}).
	 */
	public static float cost(LocalPlayer player, ForceData data, String ability) {
		int rank = Progression.masteryRank(data.mastery(ability));
		float base = ClientAbilityInfo.startCost(ability);
		Float masteryPct = ClientAbilityInfo.value(ability, "mastery_cost_pct");
		Float masteryFlat = ClientAbilityInfo.value(ability, "mastery_sense_cost");
		float mastery = masteryPct == null ? 1f : Math.max(0f, 1f - masteryPct / 100f * rank);

		if (masteryFlat != null) {
			base = Math.max(0f, base - masteryFlat * rank);
		}

		if (ForceLift.ID.equals(ability) && Skills.active(player, SkillIds.ARCHIVISTS_ROAD)) {
			Float road = ClientAbilityInfo.value("skills", "road_lift_cost_pct");
			mastery *= road == null ? 1f : Math.max(0f, 1f - road / 100f);
		}

		return base * mastery * ClientAbilityInfo.costMultiplier(player, ability);
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

	/** Whether Focus is held: the slot keys reach the second bar (the HUD lights it and dims the first). */
	public static boolean focusHeld() {
		return focus;
	}

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
			PRESSED_SLOT[i] = -1;
		}

		clearHeld();
		senseWasDown = false;
		focus = false;
		focusWasDown = false;
		activeSlot = -1;
		lastCharged = "";
	}

	private AbilityInput() {
	}
}

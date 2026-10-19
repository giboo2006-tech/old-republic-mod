package com.oldrepublic.client.force;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * The mod's Force key mappings and the shared {@link #CATEGORY} other client features file their own
 * keys under. Owned by the skills feature: the four ability slot keys (R, Z, V, B by default; slot 1
 * keeps the Milestone 1 binding id so existing bindings carry over), Focus (Left Alt, held: with a slot
 * key it reaches the second bar's slots 5 to 8, MILESTONE_5.md 9.1), the Force Sense key (H, its own
 * key, never a slot), the Force screen (K) and the unbound saber form cycle that the saber feature
 * polls. None of the defaults clash with vanilla 26.2 defaults or with each other (a client test
 * checks this).
 */
public final class ForceKeys {
	/** The mod's key category, shown as "Echoes of the Old Republic" in the controls screen. */
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(OldRepublicMod.id("force"));

	/** Force Power slot 1 (the Milestone 1 "Use Force Power" binding). */
	public static final KeyMapping USE_POWER = new KeyMapping("key.oldrepublic.use_force_power", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
	public static final KeyMapping SLOT_2 = new KeyMapping("key.oldrepublic.force_slot_2", InputConstants.Type.KEYSYM, InputConstants.KEY_Z, CATEGORY);
	public static final KeyMapping SLOT_3 = new KeyMapping("key.oldrepublic.force_slot_3", InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
	public static final KeyMapping SLOT_4 = new KeyMapping("key.oldrepublic.force_slot_4", InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY);
	/** The ability slot keys in slot order: alone they use the first bar (slots 1 to 4), with {@link #FOCUS} the second (5 to 8). */
	public static final List<KeyMapping> SLOTS = List.of(USE_POWER, SLOT_2, SLOT_3, SLOT_4);
	/** Held with a slot key: the second bar (MILESTONE_5.md 9.1, 13.1). */
	public static final KeyMapping FOCUS = new KeyMapping("key.oldrepublic.focus", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY);
	/** Casts Force Sense (spec 10.1, 11.1). Not one of {@link #SLOTS}: Sense never takes an ability slot. */
	public static final KeyMapping FORCE_SENSE = new KeyMapping("key.oldrepublic.force_sense", InputConstants.Type.KEYSYM, InputConstants.KEY_H, CATEGORY);
	/** Opens the Force screen. */
	public static final KeyMapping FORCE_SCREEN = new KeyMapping("key.oldrepublic.force_screen", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);
	/** Cycles the saber form; unbound by default. The saber feature polls it. */
	public static final KeyMapping SABER_FORM = new KeyMapping("key.oldrepublic.saber_form", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

	private static boolean registered;

	/** Registers the keys once (safe to call from more than one feature's client init). */
	public static void init() {
		if (registered) {
			return;
		}

		registered = true;

		for (KeyMapping slot : SLOTS) {
			KeyMappingHelper.registerKeyMapping(slot);
		}

		KeyMappingHelper.registerKeyMapping(FOCUS);
		KeyMappingHelper.registerKeyMapping(FORCE_SENSE);
		KeyMappingHelper.registerKeyMapping(FORCE_SCREEN);
		KeyMappingHelper.registerKeyMapping(SABER_FORM);
	}

	/**
	 * The keys that reach ability slot {@code slot} (0 to 7): the slot key on the first bar, Focus and the slot key on
	 * the second ("Alt+R"; the default Left Alt reads "Alt", a rebound Focus shows its own name).
	 */
	public static Component slotKeyName(int slot) {
		Component key = SLOTS.get(Math.floorMod(slot, SLOTS.size())).getTranslatedKeyMessage();
		return slot < SLOTS.size() ? key : Component.translatable("hud.oldrepublic.focus.combo", focusName(), key);
	}

	/** The Focus key's short name: "Alt" for the default Left Alt, the bound key's own name otherwise. */
	public static Component focusName() {
		return FOCUS.isDefault() ? Component.translatable("hud.oldrepublic.focus.short") : FOCUS.getTranslatedKeyMessage();
	}

	private ForceKeys() {
	}
}

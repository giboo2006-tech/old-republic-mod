package com.oldrepublic.combat;

import java.util.Map;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/**
 * The frozen Danger Sense tell table (spec 15.1): which effect events are enemy attack tells.
 * Danger Sense reacts to them, and their particles go through {@code Vfx.tellCount} so presets never
 * thin them out. Common code so a server game test can check it against the 13.3 rows; the client's
 * {@code DangerTells} delegates here.
 */
public final class DangerTellTable {
	/** Every variant of the event is a tell. */
	private static final int ANY = -1;

	/** Effect id to a mask of its tell variants (bit {@code v} for variant {@code v}; {@link #ANY} for all). */
	private static final Map<Identifier, Integer> TELLS = Map.ofEntries(
			tell("npc_windup", 0),
			tell("captain_heavy", 0),
			tell("acolyte_shove", 0),
			any("trooper_charge"),
			any("grenade_tell"),
			any("grenade_fuse"),
			tell("tomb_vent", 0),
			tell("sentinel_slam", 0),
			tell("sentinel_lance", 0),
			tell("boss_hurl", 0),
			tell("boss_palm", 0),
			tell("boss_lash", 0),
			tell("boss_surge", 0),
			tell("boss_grip", 0),
			tell("boss_lunge", 0),
			// Milestone 4 (spec MILESTONE_4.md 13.3): jet, flamer, rocket and net; the droid's spin-up;
			// the hollowhorn charge and the ash hound lunge; Kaast's mine arming and slam.
			any("hunter_tell"),
			tell("droid_tell", 0),
			tell("beast_tell", 0, 1),
			tell("kaast", 1, 3),
			// Milestone 5 (spec MILESTONE_5.md 15.3): the scour's burst and the matriarch's tail sweep; the storm-caller's
			// call; the stormback's charge and roar; the Hask jet-slam and shield bash; Ulvaine's Hymn step, rod charge,
			// Hymn pulse and Crescendo; the First Cantor's beam, slam, roots, basalt marks and rings.
			tell("scour", 1, 4),
			tell("hymnsworn", 1),
			tell("stormback", 1, 3),
			tell("hask_warrior", 0, 3),
			tell("ulvaine", 1, 4, 6, 7),
			tell("first_cantor", 1, 3, 6, 9, 11));

	/** Whether the effect event {@code id} with {@code variant} is an attack tell. */
	public static boolean isTell(Identifier id, int variant) {
		Integer mask = TELLS.get(id);
		return mask != null && (mask == ANY || (variant >= 0 && variant < Integer.SIZE && (mask & (1 << variant)) != 0));
	}

	private static Map.Entry<Identifier, Integer> tell(String effect, int... variants) {
		int mask = 0;

		for (int v : variants) {
			mask |= 1 << v;
		}

		return Map.entry(OldRepublicMod.id(effect), mask);
	}

	private static Map.Entry<Identifier, Integer> any(String effect) {
		return Map.entry(OldRepublicMod.id(effect), ANY);
	}

	private DangerTellTable() {
	}
}

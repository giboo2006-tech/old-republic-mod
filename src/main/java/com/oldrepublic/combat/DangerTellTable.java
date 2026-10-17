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

	private static final Map<Identifier, Integer> TELLS = Map.ofEntries(
			tell("npc_windup", 0),
			tell("captain_heavy", 0),
			tell("acolyte_shove", 0),
			tell("trooper_charge", ANY),
			tell("grenade_tell", ANY),
			tell("grenade_fuse", ANY),
			tell("tomb_vent", 0),
			tell("sentinel_slam", 0),
			tell("sentinel_lance", 0),
			tell("boss_hurl", 0),
			tell("boss_palm", 0),
			tell("boss_lash", 0),
			tell("boss_surge", 0),
			tell("boss_grip", 0),
			tell("boss_lunge", 0));

	/** Whether the effect event {@code id} with {@code variant} is an attack tell. */
	public static boolean isTell(Identifier id, int variant) {
		Integer tell = TELLS.get(id);
		return tell != null && (tell == ANY || tell == variant);
	}

	private static Map.Entry<Identifier, Integer> tell(String effect, int variant) {
		return Map.entry(OldRepublicMod.id(effect), variant);
	}

	private DangerTellTable() {
	}
}

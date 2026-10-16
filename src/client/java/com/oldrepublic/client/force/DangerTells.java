package com.oldrepublic.client.force;

import com.oldrepublic.combat.DangerTellTable;

import net.minecraft.resources.Identifier;

/**
 * Client entry to the frozen Danger Sense tell table: which received effect events are enemy attack
 * tells. Danger Sense reads only this; tell particles use {@code Vfx.tellCount}.
 */
public final class DangerTells {
	/** Whether the effect event {@code id} with {@code variant} is an attack tell ({@link DangerTellTable#isTell}). */
	public static boolean isTell(Identifier id, int variant) {
		return DangerTellTable.isTell(id, variant);
	}

	private DangerTells() {
	}
}
